#include "slt/capture.hpp"

#include <algorithm>
#include <array>
#include <cstring>

namespace slt {
namespace {

constexpr uint32_t kArchiveLength = static_cast<uint32_t>(kCaptureArchiveSize);
constexpr uint32_t kCaptureTimeoutMinimumMs = 1000u;
constexpr uint32_t kCaptureTimeoutMaximumMs = 86400000u;

void putU16(uint8_t* bytes, size_t offset, uint16_t value) {
  bytes[offset] = static_cast<uint8_t>(value);
  bytes[offset + 1] = static_cast<uint8_t>(value >> 8);
}

void putU32(uint8_t* bytes, size_t offset, uint32_t value) {
  for (size_t i = 0; i < 4; ++i) {
    bytes[offset + i] = static_cast<uint8_t>(value >> (i * 8));
  }
}

void putU64(uint8_t* bytes, size_t offset, uint64_t value) {
  for (size_t i = 0; i < 8; ++i) {
    bytes[offset + i] = static_cast<uint8_t>(value >> (i * 8));
  }
}

class Sha256 {
 public:
  Sha256() {
    state_ = {{0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au,
               0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u}};
  }

  void update(const uint8_t* data, size_t length) {
    total_bytes_ += length;
    while (length != 0) {
      const size_t available = block_.size() - block_size_;
      const size_t copy_size = std::min(length, available);
      std::copy_n(data, copy_size, block_.begin() + block_size_);
      block_size_ += copy_size;
      data += copy_size;
      length -= copy_size;
      if (block_size_ == block_.size()) {
        transform(block_.data());
        block_size_ = 0;
      }
    }
  }

  void finish(uint8_t digest[32]) {
    const uint64_t bit_length = static_cast<uint64_t>(total_bytes_) * 8u;
    block_[block_size_++] = 0x80u;
    if (block_size_ > 56) {
      std::fill(block_.begin() + block_size_, block_.end(), 0);
      transform(block_.data());
      block_size_ = 0;
    }
    std::fill(block_.begin() + block_size_, block_.begin() + 56, 0);
    for (size_t i = 0; i < 8; ++i) {
      block_[63 - i] = static_cast<uint8_t>(bit_length >> (i * 8));
    }
    transform(block_.data());
    for (size_t word = 0; word < state_.size(); ++word) {
      for (size_t byte = 0; byte < 4; ++byte) {
        digest[word * 4 + byte] =
            static_cast<uint8_t>(state_[word] >> (24 - byte * 8));
      }
    }
  }

 private:
  static uint32_t rotateRight(uint32_t value, uint8_t count) {
    return (value >> count) | (value << (32 - count));
  }

  void transform(const uint8_t* block) {
    static constexpr std::array<uint32_t, 64> kRoundConstants{{
        0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u,
        0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
        0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u,
        0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
        0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu,
        0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
        0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u,
        0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
        0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u,
        0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
        0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u,
        0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
        0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u,
        0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
        0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u,
        0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u,
    }};
    std::array<uint32_t, 64> words{};
    for (size_t i = 0; i < 16; ++i) {
      const size_t offset = i * 4;
      words[i] = (static_cast<uint32_t>(block[offset]) << 24) |
                 (static_cast<uint32_t>(block[offset + 1]) << 16) |
                 (static_cast<uint32_t>(block[offset + 2]) << 8) |
                 static_cast<uint32_t>(block[offset + 3]);
    }
    for (size_t i = 16; i < words.size(); ++i) {
      const uint32_t s0 = rotateRight(words[i - 15], 7) ^
                          rotateRight(words[i - 15], 18) ^
                          (words[i - 15] >> 3);
      const uint32_t s1 = rotateRight(words[i - 2], 17) ^
                          rotateRight(words[i - 2], 19) ^
                          (words[i - 2] >> 10);
      words[i] = words[i - 16] + s0 + words[i - 7] + s1;
    }

    uint32_t a = state_[0];
    uint32_t b = state_[1];
    uint32_t c = state_[2];
    uint32_t d = state_[3];
    uint32_t e = state_[4];
    uint32_t f = state_[5];
    uint32_t g = state_[6];
    uint32_t h = state_[7];
    for (size_t i = 0; i < words.size(); ++i) {
      const uint32_t sum1 = rotateRight(e, 6) ^ rotateRight(e, 11) ^
                            rotateRight(e, 25);
      const uint32_t choice = (e & f) ^ (~e & g);
      const uint32_t temp1 = h + sum1 + choice + kRoundConstants[i] + words[i];
      const uint32_t sum0 = rotateRight(a, 2) ^ rotateRight(a, 13) ^
                            rotateRight(a, 22);
      const uint32_t majority = (a & b) ^ (a & c) ^ (b & c);
      const uint32_t temp2 = sum0 + majority;
      h = g;
      g = f;
      f = e;
      e = d + temp1;
      d = c;
      c = b;
      b = a;
      a = temp1 + temp2;
    }
    state_[0] += a;
    state_[1] += b;
    state_[2] += c;
    state_[3] += d;
    state_[4] += e;
    state_[5] += f;
    state_[6] += g;
    state_[7] += h;
  }

  std::array<uint32_t, 8> state_{};
  std::array<uint8_t, 64> block_{};
  size_t block_size_ = 0;
  size_t total_bytes_ = 0;
};

}  // namespace

bool CaptureTimestampExtender::initialize(uint64_t reload_epoch,
                                          uint32_t dwt_at_reload) {
  reload_epoch_ = reload_epoch;
  last_reload_dwt_ = dwt_at_reload;
  valid_ = true;
  return true;
}

bool CaptureTimestampExtender::observeMasterReload(
    uint32_t dwt_now, uint32_t maximum_reload_gap_dwt) {
  if (!valid_) return false;
  const uint32_t elapsed = dwt_now - last_reload_dwt_;
  if (elapsed == 0 || elapsed > maximum_reload_gap_dwt ||
      reload_epoch_ == UINT64_MAX) {
    valid_ = false;
    return false;
  }
  ++reload_epoch_;
  last_reload_dwt_ = dwt_now;
  return true;
}

bool CaptureTimestampExtender::canServiceCapture(
    uint32_t dwt_now, uint32_t maximum_reload_gap_dwt) const {
  return valid_ && (dwt_now - last_reload_dwt_) <= maximum_reload_gap_dwt;
}

bool CaptureTimestampExtender::extend(uint8_t captured_cycle_nibble,
                                      uint16_t captured_counter,
                                      uint64_t& timestamp) const {
  if (!valid_ || captured_cycle_nibble > 0x0Fu) return false;
  const uint8_t current_cycle = static_cast<uint8_t>(reload_epoch_ & 0x0Fu);
  const uint8_t epochs_behind =
      static_cast<uint8_t>((current_cycle - captured_cycle_nibble) & 0x0Fu);
  if (epochs_behind > 1 || reload_epoch_ < epochs_behind) return false;
  const uint64_t event_epoch = reload_epoch_ - epochs_behind;
  timestamp = (event_epoch << 16) | captured_counter;
  return true;
}

Status CaptureEngine::begin(uint32_t request_id, uint64_t nonce,
                            uint16_t samples_per_channel,
                            CapturePolarity polarity,
                            CaptureRateStage rate_stage, uint32_t timeout_ms,
                            uint32_t session_generation,
                            const CaptureMetadata& metadata) {
  if (request_id == 0 || nonce != request_id || nonce <= highest_nonce_) {
    return Status::BadRequest;
  }
  if (state_ != CaptureState::Empty) return Status::Busy;
  if (!metadata.capture_supported ||
      metadata.minimum_same_polarity_interval_ticks == 0 ||
      !metadata.service_budget.valid()) {
    return Status::UnsupportedVersion;
  }
  if (samples_per_channel != kCaptureSamplesPerChannel ||
      timeout_ms < kCaptureTimeoutMinimumMs ||
      timeout_ms > kCaptureTimeoutMaximumMs ||
      (polarity != CapturePolarity::Ascending &&
       polarity != CapturePolarity::Descending) ||
      (rate_stage != CaptureRateStage::QualifiedMaximum &&
       rate_stage != CaptureRateStage::DoubleMaximum)) {
    return Status::RangeError;
  }
  if (archive_generation_ == UINT32_MAX) return Status::IntegrityFault;

  highest_nonce_ = nonce;
  ++archive_generation_;
  archive_.header.fill(0);
  archive_.samples.fill(0);
  counts_.fill(0);
  last_timestamps_.fill(0);
  last_edge_counters_.fill(0);
  have_last_sample_.fill(false);
  metadata_ = metadata;
  polarity_ = polarity;
  rate_stage_ = rate_stage;
  timeout_ms_ = timeout_ms;
  session_generation_ = session_generation;
  acknowledgement_transfer_generation_ = 0;
  acknowledgement_lifecycle_generation_ = 0;
  acknowledgement_bound_ = false;
  fault_flags_ = 0;
  arm_tick_ = 0;
  end_tick_ = 0;
  maximum_service_dwt_cycles_ = 0;
  maximum_service_latency_ticks_ = 0;
  stop_requested_ = false;
  state_ = CaptureState::AcknowledgementPending;
  return Status::Ok;
}

bool CaptureEngine::bindAcknowledgement(uint32_t session_generation,
                                        uint32_t transfer_generation,
                                        uint32_t lifecycle_generation) {
  if (state_ != CaptureState::AcknowledgementPending ||
      acknowledgement_bound_ || session_generation != session_generation_ ||
      transfer_generation == 0) {
    return false;
  }
  acknowledgement_transfer_generation_ = transfer_generation;
  acknowledgement_lifecycle_generation_ = lifecycle_generation;
  acknowledgement_bound_ = true;
  return true;
}

bool CaptureEngine::completeAcknowledgement(
    uint32_t session_generation, uint32_t transfer_generation,
    uint32_t lifecycle_generation, bool transfer_succeeded, bool session_valid,
    bool full_speed_verified, uint32_t now_ms, uint64_t arm_tick) {
  if (state_ != CaptureState::AcknowledgementPending ||
      !acknowledgement_bound_ || session_generation != session_generation_ ||
      transfer_generation != acknowledgement_transfer_generation_ ||
      lifecycle_generation != acknowledgement_lifecycle_generation_) {
    return false;
  }
  acknowledgement_bound_ = false;
  if (!transfer_succeeded || !session_valid || !full_speed_verified) {
    abort(kCaptureFaultAcknowledgement, arm_tick);
    return false;
  }
  started_ms_ = now_ms;
  arm_tick_ = arm_tick;
  state_ = CaptureState::Capturing;
  return true;
}

bool CaptureEngine::setArmTick(uint64_t arm_tick) {
  if (state_ != CaptureState::Capturing || arm_tick < arm_tick_) return false;
  arm_tick_ = arm_tick;
  return true;
}

bool CaptureEngine::record(uint8_t channel, uint64_t timestamp,
                           uint8_t edge_counter) {
  if (state_ != CaptureState::Capturing || channel >= kCaptureChannelCount) {
    return false;
  }
  if (counts_[channel] >= kCaptureSamplesPerChannel ||
      (have_last_sample_[channel] && timestamp < last_timestamps_[channel])) {
    abort(kCaptureFaultCounter, timestamp);
    return false;
  }
  if (!have_last_sample_[channel]) {
    // The peripheral counts both transitions, even though this run captures
    // only one polarity. Depending on the stable input level at arm time, the
    // first selected transition is the first or second edge-counter increment.
    if (edge_counter != 1u && edge_counter != 2u) {
      abort(kCaptureFaultCaptureLoss, timestamp);
      return false;
    }
  } else {
    const uint16_t previous = last_edge_counters_[channel];
    const uint16_t current = edge_counter;
    const uint8_t transitions = static_cast<uint8_t>(current - previous);
    if (transitions != 2u) {
      abort(kCaptureFaultCaptureLoss, timestamp);
      return false;
    }
  }

  const size_t sample_index =
      static_cast<size_t>(channel) * kCaptureSamplesPerChannel + counts_[channel];
  archive_.samples[sample_index] = timestamp;
  ++counts_[channel];
  last_timestamps_[channel] = timestamp;
  last_edge_counters_[channel] = edge_counter;
  have_last_sample_[channel] = true;
  if (timestamp > end_tick_) end_tick_ = timestamp;

  bool complete = true;
  for (uint16_t count : counts_) {
    if (count != kCaptureSamplesPerChannel) complete = false;
  }
  if (complete) {
    state_ = CaptureState::Finalizing;
    stop_requested_ = true;
  }
  return true;
}

void CaptureEngine::observeServiceCycles(uint32_t cycles) {
  if (cycles > maximum_service_dwt_cycles_) maximum_service_dwt_cycles_ = cycles;
}

bool CaptureEngine::observeServiceLatencyTicks(uint64_t ticks) {
  if (ticks > UINT16_MAX) {
    abort(kCaptureFaultTimebase, end_tick_);
    return false;
  }
  if (ticks >= metadata_.minimum_same_polarity_interval_ticks) {
    abort(kCaptureFaultServiceDeadline, end_tick_);
    return false;
  }
  if (ticks > maximum_service_latency_ticks_) maximum_service_latency_ticks_ = ticks;
  return true;
}

bool CaptureEngine::abort(uint16_t fault_flags, uint64_t end_tick) {
  if (state_ != CaptureState::Capturing &&
      state_ != CaptureState::AcknowledgementPending &&
      state_ != CaptureState::Finalizing &&
      !(state_ == CaptureState::Incomplete && stop_requested_)) {
    return false;
  }
  fault_flags_ = static_cast<uint16_t>(fault_flags_ | fault_flags);
  end_tick_ = end_tick;
  acknowledgement_bound_ = false;
  state_ = CaptureState::Incomplete;
  stop_requested_ = true;
  return true;
}

bool CaptureEngine::serviceTimeout(uint32_t now_ms, uint64_t end_tick) {
  if (state_ != CaptureState::Capturing ||
      static_cast<uint32_t>(now_ms - started_ms_) < timeout_ms_) {
    return false;
  }
  return abort(kCaptureFaultTimeout, end_tick);
}

bool CaptureEngine::finalize(uint64_t end_tick,
                             uint32_t maximum_service_dwt_cycles) {
  if (state_ != CaptureState::Finalizing &&
      state_ != CaptureState::Incomplete) {
    return false;
  }
  if (end_tick > end_tick_) end_tick_ = end_tick;
  observeServiceCycles(maximum_service_dwt_cycles);
  // Keep the service latency measured by capture-clock subtraction. It is
  // deliberately separate from the DWT cycle diagnostic.
  serializeHeader(end_tick_, maximum_service_dwt_cycles_);
  if (state_ == CaptureState::Finalizing) state_ = CaptureState::Complete;
  stop_requested_ = false;
  return true;
}

bool CaptureEngine::release(uint32_t archive_generation) {
  if ((state_ != CaptureState::Complete && state_ != CaptureState::Incomplete) ||
      archive_generation != archive_generation_) {
    return false;
  }
  archive_.header.fill(0);
  archive_.samples.fill(0);
  counts_.fill(0);
  last_timestamps_.fill(0);
  last_edge_counters_.fill(0);
  have_last_sample_.fill(false);
  fault_flags_ = 0;
  state_ = CaptureState::Empty;
  return true;
}

bool CaptureEngine::status(CaptureStatus& result) const {
  if (state_ == CaptureState::Empty) return false;
  result.archive_generation = archive_generation_;
  result.state = state_;
  result.polarity = polarity_;
  result.fault_flags = fault_flags_;
  result.counts = counts_;
  result.archive_length = kArchiveLength;
  return true;
}

bool CaptureEngine::read(uint32_t archive_generation, uint32_t offset,
                         uint8_t length, CaptureReadChunk& result) const {
  if ((state_ != CaptureState::Complete && state_ != CaptureState::Incomplete) ||
      archive_generation != archive_generation_ || offset > kArchiveLength ||
      (offset % kCaptureReadChunkSize) != 0) {
    return false;
  }
  if ((offset == kArchiveLength && length != 0) ||
      (offset < kArchiveLength && length != kCaptureReadChunkSize)) {
    return false;
  }
  result = CaptureReadChunk{};
  result.archive_generation = archive_generation_;
  result.offset = offset;
  result.length = length;
  for (size_t i = 0; i < length; ++i) {
    result.bytes[i] = archiveByte(offset + static_cast<uint32_t>(i));
  }

  std::array<uint8_t, 9 + kCaptureReadChunkSize> crc_input{};
  putU32(crc_input.data(), 0, archive_generation_);
  putU32(crc_input.data(), 4, offset);
  crc_input[8] = length;
  std::copy_n(result.bytes.begin(), length, crc_input.begin() + 9);
  result.crc32 = captureCrc32(crc_input.data(), 9 + length);
  return true;
}

void CaptureEngine::serializeHeader(uint64_t end_tick,
                                    uint32_t maximum_service_dwt_cycles) {
  uint8_t* header = archive_.header.data();
  header[0] = 'S';
  header[1] = 'L';
  header[2] = 'A';
  header[3] = '1';
  putU16(header, 4, 1);
  putU16(header, 6, static_cast<uint16_t>(kCaptureHeaderSize));
  putU32(header, 8, archive_generation_);
  putU64(header, 12, highest_nonce_);
  putU32(header, 20, session_generation_);
  std::copy_n(metadata_.firmware_version, 4, header + 24);
  std::copy_n(metadata_.git_commit_prefix, 8, header + 28);
  header[36] = static_cast<uint8_t>(kCaptureChannelCount);
  header[37] = static_cast<uint8_t>(polarity_);
  header[38] = static_cast<uint8_t>(rate_stage_);
  header[39] = state_ == CaptureState::Finalizing ? 0x01u : 0x02u;
  putU16(header, 40, static_cast<uint16_t>(kCaptureSamplesPerChannel));
  putU16(header, 42, static_cast<uint16_t>(sizeof(uint64_t)));
  putU32(header, 44, metadata_.capture_tick_hz);
  putU32(header, 48, metadata_.core_clock_hz);
  putU32(header, 52, timeout_ms_);
  putU32(header, 56, maximum_service_dwt_cycles);
  putU64(header, 60, arm_tick_);
  putU64(header, 68, end_tick);
  for (size_t i = 0; i < counts_.size(); ++i) {
    putU16(header, 76 + i * 2, counts_[i]);
  }
  putU16(header, 86,
         static_cast<uint16_t>(maximum_service_latency_ticks_));

  Sha256 hash;
  std::array<uint8_t, 64> bytes{};
  for (uint32_t offset = 0; offset < kCaptureSampleBytes;
       offset += static_cast<uint32_t>(bytes.size())) {
    const size_t block_size = std::min<size_t>(
        bytes.size(), kCaptureSampleBytes - offset);
    for (size_t i = 0; i < block_size; ++i) {
      bytes[i] = archiveByte(kCaptureHeaderSize + offset +
                             static_cast<uint32_t>(i));
    }
    hash.update(bytes.data(), block_size);
  }
  hash.finish(header + 88);
  putU32(header, 120, kArchiveLength);
  putU32(header, 124, captureCrc32(header, 124));
}

uint8_t CaptureEngine::archiveByte(uint32_t offset) const {
  if (offset < kCaptureHeaderSize) return archive_.header[offset];
  const uint32_t sample_byte = offset - kCaptureHeaderSize;
  const size_t sample_index = sample_byte / sizeof(uint64_t);
  const uint8_t byte_index = static_cast<uint8_t>(sample_byte % sizeof(uint64_t));
  return static_cast<uint8_t>(archive_.samples[sample_index] >> (byte_index * 8));
}

uint32_t captureCrc32(const uint8_t* bytes, size_t length) {
  uint32_t crc = 0xFFFFFFFFu;
  for (size_t i = 0; i < length; ++i) {
    crc ^= bytes[i];
    for (uint8_t bit = 0; bit < 8; ++bit) {
      const uint32_t mask = 0u - (crc & 1u);
      crc = (crc >> 1) ^ (0xEDB88320u & mask);
    }
  }
  return ~crc;
}

void captureSha256(const uint8_t* bytes, size_t length, uint8_t digest[32]) {
  Sha256 hash;
  hash.update(bytes, length);
  hash.finish(digest);
}

}  // namespace slt
