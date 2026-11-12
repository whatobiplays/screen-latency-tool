#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

#include "slt/pulse_engine.hpp"

namespace slt {

constexpr size_t kCaptureChannelCount = 5;
constexpr size_t kCaptureSamplesPerChannel = 10000;
constexpr size_t kCaptureSampleCount = kCaptureChannelCount * kCaptureSamplesPerChannel;
constexpr size_t kCaptureSampleBytes = kCaptureSampleCount * sizeof(uint64_t);
constexpr size_t kCaptureHeaderSize = 128;
constexpr size_t kCaptureArchiveSize = kCaptureHeaderSize + kCaptureSampleBytes;
constexpr size_t kCaptureReadChunkSize = 32;

enum class CapturePolarity : uint8_t { Ascending = 1, Descending = 2 };
enum class CaptureRateStage : uint8_t { QualifiedMaximum = 1, DoubleMaximum = 2 };
enum class FlexPwmInput : uint8_t { A = 0, B = 1 };

constexpr uint8_t selectedCaptureEdge(CapturePolarity polarity) noexcept {
  return polarity == CapturePolarity::Ascending ? 2u : 1u;
}

struct FlexPwm1CaptureRoute {
  uint8_t channel;
  uint8_t teensy_pin;
  uint8_t submodule;
  FlexPwmInput input;
  uint8_t fifo_index;
  uint8_t cval_register_index;
  uint8_t mux_alt;
  uint8_t daisy_select;
};

// Provisional Teensy 4.1 routes: pins 48/49/52/53 share the optional QSPI
// expansion bus; pin 8 supplies the fifth path. Routing stays compile-time off.
constexpr std::array<FlexPwm1CaptureRoute, kCaptureChannelCount>
    kFlexPwm1CaptureRoutes{{
        {0, 48, 0, FlexPwmInput::B, 0, 4, 1, 0},  // PWMB0 / CVAL4
        {1, 53, 1, FlexPwmInput::A, 0, 2, 1, 0},  // PWMA1 / CVAL2
        {2, 52, 1, FlexPwmInput::B, 0, 4, 1, 0},  // PWMB1 / CVAL4
        {3, 49, 2, FlexPwmInput::A, 0, 2, 1, 0},  // PWMA2 / CVAL2
        {4, 8, 3, FlexPwmInput::A, 0, 2, 6, 4},   // PWMA3 / CVAL2
    }};

enum class CaptureState : uint8_t {
  Empty = 0,
  AcknowledgementPending = 1,
  Capturing = 2,
  Finalizing = 3,
  Complete = 4,
  Incomplete = 5,
};

enum CaptureFault : uint16_t {
  kCaptureFaultNone = 0,
  kCaptureFaultTimeout = 1u << 0,
  kCaptureFaultUsbReset = 1u << 1,
  kCaptureFaultDetach = 1u << 2,
  kCaptureFaultUnexpectedVendorTraffic = 1u << 3,
  kCaptureFaultCaptureLoss = 1u << 4,
  kCaptureFaultTimebase = 1u << 5,
  kCaptureFaultAcknowledgement = 1u << 6,
  kCaptureFaultCounter = 1u << 7,
  kCaptureFaultServiceDeadline = 1u << 8,
};

struct CaptureArchive {
  std::array<uint8_t, kCaptureHeaderSize> header{};
  alignas(8) std::array<uint64_t, kCaptureSampleCount> samples{};
};
static_assert(sizeof(CaptureArchive) == kCaptureArchiveSize,
              "capture archive size must match the fixed RAM2 budget");

struct CaptureStatus {
  uint32_t archive_generation = 0;
  CaptureState state = CaptureState::Empty;
  CapturePolarity polarity = CapturePolarity::Ascending;
  uint16_t fault_flags = 0;
  std::array<uint16_t, kCaptureChannelCount> counts{};
  uint32_t archive_length = 0;
};

struct CaptureReadChunk {
  uint32_t archive_generation = 0;
  uint32_t offset = 0;
  uint32_t crc32 = 0;
  uint8_t length = 0;
  std::array<uint8_t, kCaptureReadChunkSize> bytes{};
};

struct CaptureRegisterSample {
  uint8_t cycle_nibble = 0;
  uint16_t timer_counter = 0;
  uint8_t edge_counter = 0;
};

// Call while the hardware reader's reload epoch is stable. A reload flag that
// changes around the counter read means the sample straddled the wrap.
template <typename Reader>
bool composeCaptureClockSnapshot(Reader& reader, uint16_t reload_flag,
                                 uint64_t reload_epoch, uint64_t& tick) {
  const uint16_t before = reader.readReloadStatus();
  const uint16_t counter = reader.readCounter();
  const uint16_t after = reader.readReloadStatus();
  if (((before ^ after) & reload_flag) != 0) return false;
  if ((after & reload_flag) != 0) {
    if (reload_epoch == UINT64_MAX) return false;
    ++reload_epoch;
  }
  tick = (reload_epoch << 16) | counter;
  return true;
}

struct CaptureServiceBudget {
  uint32_t interrupt_mask_cycles = 0;
  uint32_t usb_interference_cycles = 0;
  uint32_t simultaneous_five_channel_cycles = 0;
  uint32_t minimum_edge_interval_cycles = 0;
  uint32_t master_reload_service_cycles = 0;
  uint32_t master_reload_period_cycles = 0;

  bool valid() const {
    const uint64_t total = static_cast<uint64_t>(interrupt_mask_cycles) +
                           usb_interference_cycles +
                           simultaneous_five_channel_cycles;
    return minimum_edge_interval_cycles != 0 &&
           total < minimum_edge_interval_cycles &&
           master_reload_period_cycles != 0 &&
           master_reload_service_cycles < master_reload_period_cycles / 2u;
  }
};

struct CaptureMetadata {
  uint8_t firmware_version[4]{};
  uint8_t git_commit_prefix[8]{};
  uint32_t capture_tick_hz = 0;
  uint32_t core_clock_hz = 600000000u;
  // These values come from a qualified fixture timing profile. Zero leaves
  // E003 unavailable until pulse and burst bounds have been reviewed.
  uint32_t minimum_same_polarity_interval_ticks = 0;
  CaptureServiceBudget service_budget{};
  bool capture_supported = false;
};

enum class CaptureFifoResult : uint8_t { Empty, Captured, MorePending, Fault };
enum class CaptureDrainResult : uint8_t { Empty, Drained, StopRequested, Fault };

// Reader supplies fifoCount(), captureFlag(), readCycle(), readEdgeCounter(),
// readValue(), and clearCaptureFlag(). Edge-counter reads bracket CYC and the
// destructive CVAL read. The capture flag is cleared before CVAL so a newer
// edge cannot be erased by a late W1C write.
template <typename Reader>
CaptureFifoResult popCaptureFifo(Reader& reader,
                                 CaptureRegisterSample& sample) {
  uint8_t count = reader.fifoCount();
  const bool flag = reader.captureFlag();
  if (count == 0 && !flag) return CaptureFifoResult::Empty;
  if (count != 1 || !flag) return CaptureFifoResult::Fault;

  const uint8_t before_cycle = reader.readEdgeCounter();
  sample.cycle_nibble = static_cast<uint8_t>(reader.readCycle() & 0x0Fu);
#if defined(__GNUC__)
  __asm__ volatile("" ::: "memory");
#endif
  const uint8_t after_cycle = reader.readEdgeCounter();
  if (before_cycle != after_cycle) return CaptureFifoResult::Fault;

  reader.clearCaptureFlag();
  const uint8_t before_value = reader.readEdgeCounter();
  if (before_value != after_cycle) return CaptureFifoResult::Fault;
  sample.timer_counter = reader.readValue();
  const uint8_t after_value = reader.readEdgeCounter();
  if (before_value != after_value) return CaptureFifoResult::Fault;
  sample.edge_counter = before_value;
  count = reader.fifoCount();
  const bool after_flag = reader.captureFlag();
  const uint8_t after_clear = reader.readEdgeCounter();
  if (count > 1) return CaptureFifoResult::Fault;
  if (count == 1) {
    // Exactly one same-polarity edge consists of two transitions. Require its
    // FIFO flag and count to agree; a full FIFO overwrite is a hard fault.
    const uint8_t delta = static_cast<uint8_t>(after_clear - after_value);
    return after_flag && delta == 2 ? CaptureFifoResult::MorePending
                                    : CaptureFifoResult::Fault;
  }
  if (after_flag || after_clear != after_value) return CaptureFifoResult::Fault;
  return CaptureFifoResult::Captured;
}

template <typename Readers, typename CaptureHandler>
CaptureDrainResult drainCaptureFifos(Readers& readers,
                                     uint8_t maximum_entries_per_path,
                                     CaptureHandler&& capture) {
  if (maximum_entries_per_path == 0) return CaptureDrainResult::Fault;
  bool captured_any = false;
  for (size_t path = 0; path < readers.size(); ++path) {
    for (uint8_t drained = 0; drained < maximum_entries_per_path; ++drained) {
      CaptureRegisterSample sample{};
      const CaptureFifoResult result = popCaptureFifo(readers[path], sample);
      if (result == CaptureFifoResult::Fault) return CaptureDrainResult::Fault;
      if (result == CaptureFifoResult::Empty) break;
      captured_any = true;
      if (!capture(path, sample)) return CaptureDrainResult::StopRequested;
      if (result == CaptureFifoResult::Captured) break;
      if (drained + 1u == maximum_entries_per_path) {
        return CaptureDrainResult::Fault;
      }
    }
  }
  return captured_any ? CaptureDrainResult::Drained : CaptureDrainResult::Empty;
}

class CaptureTimestampExtender {
 public:
  bool initialize(uint64_t reload_epoch, uint32_t dwt_at_reload);
  bool observeMasterReload(uint32_t dwt_now, uint32_t maximum_reload_gap_dwt);
  bool canServiceCapture(uint32_t dwt_now,
                         uint32_t maximum_reload_gap_dwt) const;
  bool extend(uint8_t captured_cycle_nibble, uint16_t captured_counter,
              uint64_t& timestamp) const;
  uint64_t reloadEpoch() const { return reload_epoch_; }
  bool valid() const { return valid_; }

 private:
  uint64_t reload_epoch_ = 0;
  uint32_t last_reload_dwt_ = 0;
  bool valid_ = false;
};

// Owns one volatile archive for a single powered boot. A BEGIN request consumes
// its nonce before the response is sent; only the matching successful vendor
// IN completion can move the archive from acknowledgement-pending to capture.
class CaptureEngine {
 public:
  explicit CaptureEngine(CaptureArchive& archive) : archive_(archive) {}

  Status begin(uint32_t request_id, uint64_t nonce, uint16_t samples_per_channel,
               CapturePolarity polarity, CaptureRateStage rate_stage,
               uint32_t timeout_ms, uint32_t session_generation,
               const CaptureMetadata& metadata);
  bool bindAcknowledgement(uint32_t session_generation,
                           uint32_t transfer_generation,
                           uint32_t lifecycle_generation);
  bool completeAcknowledgement(uint32_t session_generation,
                               uint32_t transfer_generation,
                               uint32_t lifecycle_generation,
                               bool transfer_succeeded, bool session_valid,
                               bool full_speed_verified, uint32_t now_ms,
                               uint64_t arm_tick);
  bool setArmTick(uint64_t arm_tick);
  bool record(uint8_t channel, uint64_t timestamp, uint8_t edge_counter);
  void observeServiceCycles(uint32_t cycles);
  bool observeServiceLatencyTicks(uint64_t ticks);
  bool abort(uint16_t fault_flags, uint64_t end_tick);
  bool serviceTimeout(uint32_t now_ms, uint64_t end_tick);
  bool finalize(uint64_t end_tick, uint32_t maximum_service_dwt_cycles);
  bool release(uint32_t archive_generation);
  bool status(CaptureStatus& result) const;
  bool read(uint32_t archive_generation, uint32_t offset, uint8_t length,
            CaptureReadChunk& result) const;
  CaptureState state() const { return state_; }
  bool acquisitionActive() const {
    return state_ == CaptureState::AcknowledgementPending ||
           state_ == CaptureState::Capturing ||
           state_ == CaptureState::Finalizing ||
           (state_ == CaptureState::Incomplete && stop_requested_);
  }
  bool stopRequested() const { return stop_requested_; }
  void clearStopRequest() { stop_requested_ = false; }
  uint32_t archiveGeneration() const { return archive_generation_; }
  uint64_t highestNonce() const { return highest_nonce_; }
  uint64_t armTick() const { return arm_tick_; }
  uint32_t maximumServiceCycles() const { return maximum_service_dwt_cycles_; }
  uint64_t maximumServiceLatencyTicks() const { return maximum_service_latency_ticks_; }
  uint32_t timeoutMs() const { return timeout_ms_; }
  uint32_t minimumSamePolarityIntervalTicks() const {
    return metadata_.minimum_same_polarity_interval_ticks;
  }
  const CaptureServiceBudget& serviceBudget() const {
    return metadata_.service_budget;
  }
  CapturePolarity polarity() const { return polarity_; }
  CaptureRateStage rateStage() const { return rate_stage_; }

 private:
  void serializeHeader(uint64_t end_tick, uint32_t maximum_service_dwt_cycles);
  uint8_t archiveByte(uint32_t offset) const;

  CaptureArchive& archive_;
  CaptureMetadata metadata_{};
  std::array<uint16_t, kCaptureChannelCount> counts_{};
  std::array<uint64_t, kCaptureChannelCount> last_timestamps_{};
  std::array<uint8_t, kCaptureChannelCount> last_edge_counters_{};
  std::array<bool, kCaptureChannelCount> have_last_sample_{};
  uint64_t highest_nonce_ = 0;
  uint64_t arm_tick_ = 0;
  uint64_t end_tick_ = 0;
  uint64_t maximum_service_latency_ticks_ = 0;
  uint32_t archive_generation_ = 0;
  uint32_t session_generation_ = 0;
  uint32_t acknowledgement_transfer_generation_ = 0;
  uint32_t acknowledgement_lifecycle_generation_ = 0;
  uint32_t timeout_ms_ = 0;
  uint32_t started_ms_ = 0;
  uint32_t maximum_service_dwt_cycles_ = 0;
  uint16_t fault_flags_ = 0;
  CapturePolarity polarity_ = CapturePolarity::Ascending;
  CaptureRateStage rate_stage_ = CaptureRateStage::QualifiedMaximum;
  CaptureState state_ = CaptureState::Empty;
  bool acknowledgement_bound_ = false;
  bool stop_requested_ = false;
};

uint32_t captureCrc32(const uint8_t* bytes, size_t length);
void captureSha256(const uint8_t* bytes, size_t length, uint8_t digest[32]);

}  // namespace slt
