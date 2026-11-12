#include "slt/protocol.hpp"

#include <algorithm>
#include <array>

namespace slt {
namespace {

constexpr uint8_t kMagic[] = {'S', 'L', 'T', '1'};

uint8_t readU8(const uint8_t* data, size_t offset) { return data[offset]; }

uint16_t readU16(const uint8_t* data, size_t offset) {
  return static_cast<uint16_t>(data[offset]) |
         static_cast<uint16_t>(static_cast<uint16_t>(data[offset + 1]) << 8);
}

uint32_t readU32(const uint8_t* data, size_t offset) {
  return static_cast<uint32_t>(data[offset]) |
         (static_cast<uint32_t>(data[offset + 1]) << 8) |
         (static_cast<uint32_t>(data[offset + 2]) << 16) |
         (static_cast<uint32_t>(data[offset + 3]) << 24);
}

uint64_t readU64(const uint8_t* data, size_t offset) {
  uint64_t value = 0;
  for (size_t i = 0; i < 8; ++i) {
    value |= static_cast<uint64_t>(data[offset + i]) << (8 * i);
  }
  return value;
}

void writeU16(uint8_t* data, size_t offset, uint16_t value) {
  data[offset] = static_cast<uint8_t>(value);
  data[offset + 1] = static_cast<uint8_t>(value >> 8);
}

void writeU32(uint8_t* data, size_t offset, uint32_t value) {
  for (size_t i = 0; i < 4; ++i) {
    data[offset + i] = static_cast<uint8_t>(value >> (8 * i));
  }
}

void writeU64(uint8_t* data, size_t offset, uint64_t value) {
  for (size_t i = 0; i < 8; ++i) {
    data[offset + i] = static_cast<uint8_t>(value >> (8 * i));
  }
}

}  // namespace

bool isSuccessfulCaptureBeginAcknowledgement(const uint8_t* response,
                                             size_t response_size) {
  return response != nullptr &&
         response_size == kProtocolHeaderSize +
                              kEngineeringBeginCaptureAckPayloadSize &&
         response[0] == 'S' && response[1] == 'L' && response[2] == 'T' &&
         response[3] == '1' && response[4] == kEngineeringProtocolMajor &&
         response[5] == kEngineeringProtocolMinorCapture && response[6] == 2 &&
         response[7] == 0 && readU32(response, 8) != 0 &&
         readU16(response, 12) == kEngineeringBeginCaptureCommand &&
         readU16(response, 14) == static_cast<uint16_t>(Status::Ok) &&
         readU32(response, 16) == kEngineeringBeginCaptureAckPayloadSize;
}

Status Protocol::process(const uint8_t* request, size_t request_size,
                         uint8_t* response, size_t response_capacity,
                         size_t& response_size, uint32_t now_tick) {
  response_size = 0;
  if (request == nullptr || response == nullptr ||
      response_capacity < kProtocolHeaderSize ||
      request_size < kProtocolHeaderSize ||
      request_size > kMaximumEngineeringFrameSize) {
    return Status::BadRequest;
  }

  uint32_t request_id = readU32(request, 8);
  uint16_t command = readU16(request, 12);
  Status status = Status::BadRequest;
  std::array<uint8_t, kMaximumEngineeringFrameSize - kProtocolHeaderSize> payload{};
  size_t payload_size = 0;

  bool magic_matches = true;
  for (size_t i = 0; i < sizeof(kMagic); ++i) {
    if (request[i] != kMagic[i]) magic_matches = false;
  }
  if (!magic_matches) {
    writeResponse(response, request_id, command, status, nullptr, 0, response_size);
    return status;
  }

  if (request_id == 0) {
    writeResponse(response, request_id, command, Status::BadRequest, nullptr, 0,
                  response_size);
    return Status::BadRequest;
  }

  if (capture_ != nullptr && capture_->acquisitionActive()) {
    // Acquisition has exclusive vendor ownership. The endpoint owner aborts
    // unexpected traffic without sending a response during the capture window.
    return Status::InvalidState;
  }

  for (const CachedExchange& cached : replay_cache_) {
    if (!cached.valid || cached.request_id != request_id) continue;
    const bool exact_duplicate = cached.request_size == request_size &&
        std::equal(request, request + request_size, cached.request.begin());
    if (!exact_duplicate) {
      writeResponse(response, request_id, command, Status::BadRequest, nullptr,
                    0, response_size);
      return Status::BadRequest;
    }
    if (command == kEngineeringBeginCaptureCommand &&
        (capture_ == nullptr || capture_->state() == CaptureState::Empty)) {
      writeResponse(response, request_id, command, Status::BadRequest, nullptr,
                    0, response_size,
                    readU8(request, 5) == kEngineeringProtocolMinorCapture ? 1 : 0);
      return Status::BadRequest;
    }
    const bool engineering_command =
        command == kEngineeringInfoCommand ||
        command == kEngineeringPressPulseCommand;
    if (engineering_command && !engine_.engineeringAvailable()) {
      engine_.setLastCommandStatus(Status::InvalidState);
      writeResponse(response, request_id, command, Status::InvalidState,
                    nullptr, 0, response_size);
      return Status::InvalidState;
    }
    if (response_capacity < cached.response_size) return Status::HardwareFault;
    std::copy_n(cached.response.begin(), cached.response_size, response);
    response_size = cached.response_size;
    return cached.status;
  }

  if (has_request_id_ && request_id <= last_request_id_) {
    writeResponse(response, request_id, command, Status::BadRequest, nullptr, 0,
                  response_size);
    return Status::BadRequest;
  }

  if ((command == kEngineeringInfoCommand &&
       response_capacity < kProtocolHeaderSize + kEngineeringInfoPayloadSize) ||
      (command == kEngineeringPressPulseCommand &&
       response_capacity < kProtocolHeaderSize + kEngineeringPressAckPayloadSize) ||
      (command == kEngineeringBeginCaptureCommand &&
       response_capacity < kProtocolHeaderSize + kEngineeringBeginCaptureAckPayloadSize) ||
      (command == kEngineeringCaptureArchiveCommand &&
       response_capacity < kMaximumEngineeringFrameSize)) {
    engine_.setLastCommandStatus(Status::HardwareFault);
    return Status::HardwareFault;
  }

  const uint32_t declared_size = readU32(request, 16);
  const bool exact_length = declared_size == request_size - kProtocolHeaderSize;
  const uint8_t major = readU8(request, 4);
  const uint8_t minor = readU8(request, 5);
  const uint8_t message_type = readU8(request, 6);
  const uint8_t flags = readU8(request, 7);
  const uint16_t request_status = readU16(request, 14);
  const uint8_t response_minor =
      major == kEngineeringProtocolMajor &&
              minor <= kEngineeringProtocolMinorCapture
          ? minor
          : kEngineeringProtocolMinor;

  last_request_id_ = request_id;
  has_request_id_ = true;

  if (major != kEngineeringProtocolMajor ||
      minor > kEngineeringProtocolMinorCapture) {
    status = Status::UnsupportedVersion;
  } else if (message_type != 1 || flags != 0 || request_status != 0 ||
             !exact_length) {
    status = Status::BadRequest;
  } else if (!engine_.engineeringAvailable()) {
    status = Status::InvalidState;
  } else if (command == kEngineeringInfoCommand) {
    if (declared_size != 0) {
      status = Status::BadRequest;
    } else if (response_capacity < kProtocolHeaderSize +
                                      kEngineeringInfoPayloadSize) {
      engine_.setLastCommandStatus(Status::HardwareFault);
      return Status::HardwareFault;
    } else {
      serializeInfo(payload.data(), engine_.snapshotForEngineeringInfo());
      payload[1] = minor;
      payload_size = kEngineeringInfoPayloadSize;
      status = Status::Ok;
    }
  } else if (command == kEngineeringPressPulseCommand) {
    if (declared_size != kEngineeringPressRequestPayloadSize ||
        readU16(request, kProtocolHeaderSize + 6) != 0) {
      status = Status::BadRequest;
    } else {
      const uint32_t tag = readU32(request, kProtocolHeaderSize);
      const uint16_t duration = readU16(request, kProtocolHeaderSize + 4);
      const PulseResult result = engine_.acceptPulse(duration, now_tick);
      status = result.status;
      if (status == Status::Ok) {
        writeU32(payload.data(), 0, tag);
        writeU32(payload.data(), 4, result.sequence);
        payload[8] = static_cast<uint8_t>(result.state);
        payload[9] = payload[10] = payload[11] = 0;
        payload_size = kEngineeringPressAckPayloadSize;
      }
    }
  } else if (command == kEngineeringBeginCaptureCommand) {
    if (minor != kEngineeringProtocolMinorCapture) {
      status = Status::UnsupportedVersion;
    } else if (capture_ == nullptr) {
      status = Status::UnsupportedVersion;
    } else if (declared_size != kEngineeringBeginCaptureRequestPayloadSize) {
      status = Status::BadRequest;
    } else if (engine_.isBusy()) {
      status = Status::Busy;
    } else {
      const size_t offset = kProtocolHeaderSize;
      const uint64_t nonce = readU64(request, offset);
      const uint16_t samples = readU16(request, offset + 8);
      const uint8_t polarity_value = readU8(request, offset + 10);
      const uint8_t rate_stage_value = readU8(request, offset + 11);
      const uint32_t timeout_ms = readU32(request, offset + 12);
      if ((polarity_value != static_cast<uint8_t>(CapturePolarity::Ascending) &&
           polarity_value != static_cast<uint8_t>(CapturePolarity::Descending)) ||
          (rate_stage_value != static_cast<uint8_t>(CaptureRateStage::QualifiedMaximum) &&
           rate_stage_value != static_cast<uint8_t>(CaptureRateStage::DoubleMaximum))) {
        status = Status::BadRequest;
      } else {
        const Snapshot snapshot = engine_.snapshot();
        if (!snapshot.session_valid || !snapshot.full_speed_verified) {
          status = Status::NotReady;
        } else {
          status = capture_->begin(
              request_id, nonce, samples,
              static_cast<CapturePolarity>(polarity_value),
              static_cast<CaptureRateStage>(rate_stage_value), timeout_ms,
              snapshot.session_generation, capture_metadata_);
        }
        if (status == Status::Ok) {
          writeU64(payload.data(), 0, nonce);
          writeU32(payload.data(), 8, capture_->archiveGeneration());
          writeU32(payload.data(), 12, snapshot.session_generation);
          payload[16] = static_cast<uint8_t>(CaptureState::AcknowledgementPending);
          payload[17] = polarity_value;
          writeU16(payload.data(), 18, samples);
          payload_size = kEngineeringBeginCaptureAckPayloadSize;
        }
      }
    }
  } else if (command == kEngineeringCaptureArchiveCommand) {
    if (minor != kEngineeringProtocolMinorCapture) {
      status = Status::UnsupportedVersion;
    } else if (capture_ == nullptr) {
      status = Status::UnsupportedVersion;
    } else if (declared_size != kEngineeringCaptureArchiveRequestPayloadSize ||
               readU8(request, kProtocolHeaderSize + 1) != 0 ||
               readU8(request, kProtocolHeaderSize + 11) != 0) {
      status = Status::BadRequest;
    } else {
      const uint8_t operation = readU8(request, kProtocolHeaderSize);
      const uint32_t generation = readU32(request, kProtocolHeaderSize + 2);
      const uint32_t offset = readU32(request, kProtocolHeaderSize + 6);
      const uint8_t length = readU8(request, kProtocolHeaderSize + 10);
      if (capture_->acquisitionActive() && operation != 0) {
        status = Status::Busy;
      } else if (operation == 1) {
        CaptureStatus capture_status{};
        if (offset != 0 || length != 0 ||
            (generation != 0 && generation != capture_->archiveGeneration()) ||
            !capture_->status(capture_status)) {
          status = Status::NotReady;
        } else {
          writeU32(payload.data(), 0, capture_status.archive_generation);
          payload[4] = static_cast<uint8_t>(capture_status.state);
          payload[5] = static_cast<uint8_t>(capture_status.polarity);
          writeU16(payload.data(), 6, capture_status.fault_flags);
          for (size_t i = 0; i < capture_status.counts.size(); ++i) {
            writeU16(payload.data(), 8 + i * 2, capture_status.counts[i]);
          }
          writeU32(payload.data(), 18, capture_status.archive_length);
          writeU16(payload.data(), 22, 0);
          payload_size = kEngineeringCaptureStatusPayloadSize;
          status = Status::Ok;
        }
      } else if (operation == 2) {
        CaptureReadChunk chunk{};
        if (capture_->read(generation, offset, length, chunk)) {
          writeU32(payload.data(), 0, chunk.archive_generation);
          writeU32(payload.data(), 4, chunk.offset);
          writeU32(payload.data(), 8, chunk.crc32);
          std::copy_n(chunk.bytes.begin(), chunk.length,
                      payload.begin() + kEngineeringCaptureReadMetadataSize);
          payload_size = kEngineeringCaptureReadMetadataSize + chunk.length;
          status = Status::Ok;
        } else {
          status = capture_->state() == CaptureState::Empty
                       ? Status::NotReady
                       : Status::RangeError;
        }
      } else if (operation == 3) {
        if (offset != 0 || length != 0) {
          status = Status::BadRequest;
        } else if (capture_->release(generation)) {
          writeU32(payload.data(), 0, generation);
          payload[4] = static_cast<uint8_t>(CaptureState::Empty);
          payload[5] = payload[6] = payload[7] = 0;
          payload_size = kEngineeringCaptureReleasePayloadSize;
          status = Status::Ok;
        } else {
          status = Status::InvalidState;
        }
      } else {
        status = Status::BadRequest;
      }
    }
  } else {
    status = Status::BadRequest;
  }

  if (response_capacity < kProtocolHeaderSize + payload_size) {
    engine_.setLastCommandStatus(Status::HardwareFault);
    return Status::HardwareFault;
  }
  engine_.setLastCommandStatus(status);
  writeResponse(response, request_id, command, status,
                status == Status::Ok ? payload.data() : nullptr,
                status == Status::Ok ? payload_size : 0, response_size,
                response_minor);
  remember(request_id, request, request_size, status, response, response_size);
  return status;
}

void Protocol::resetSession() {
  last_request_id_ = 0;
  has_request_id_ = false;
  replay_next_index_ = 0;
  for (CachedExchange& cached : replay_cache_) cached = CachedExchange{};
}

void Protocol::serializeInfo(uint8_t* payload, const Snapshot& snapshot) const {
  payload[0] = kEngineeringProtocolMajor;
  payload[1] = kEngineeringProtocolMinor;
  payload[2] = kEngineeringFirmwareMajor;
  payload[3] = kEngineeringFirmwareMinor;
  payload[4] = static_cast<uint8_t>(snapshot.usb_speed);
  payload[5] = static_cast<uint8_t>(snapshot.state) |
               (snapshot.tick_valid ? 0x08 : 0) |
               (snapshot.full_speed_verified ? 0x10 : 0) |
               (snapshot.session_valid ? 0x20 : 0) |
               (snapshot.recovery_required ? 0x40 : 0);
  writeU64(payload, 6, fixture_serial_);
  writeU32(payload, 14, snapshot.sequence);
  writeU32(payload, 18, snapshot.session_generation);
  writeU32(payload, 22, snapshot.transfer_generation);
  writeU32(payload, 26, snapshot.tick_valid ? snapshot.completion_tick : 0);
  writeU16(payload, 30, snapshot.duration_ms);
}

void Protocol::remember(uint32_t request_id, const uint8_t* request,
                        size_t request_size, Status status,
                        const uint8_t* response, size_t response_size) {
  CachedExchange& cached = replay_cache_[replay_next_index_];
  cached.valid = true;
  cached.request_id = request_id;
  cached.request_size = request_size;
  std::copy_n(request, request_size, cached.request.begin());
  cached.status = status;
  cached.response_size = response_size;
  std::copy_n(response, response_size, cached.response.begin());
  replay_next_index_ = (replay_next_index_ + 1) % replay_cache_.size();
}

void Protocol::writeResponse(uint8_t* response, uint32_t request_id,
                             uint16_t command, Status status,
                             const uint8_t* payload, size_t payload_size,
                             size_t& response_size,
                             uint8_t protocol_minor) const {
  response[0] = 'S';
  response[1] = 'L';
  response[2] = 'T';
  response[3] = '1';
  response[4] = 1;
  response[5] = protocol_minor;
  response[6] = 2;
  response[7] = 0;
  writeU32(response, 8, request_id);
  writeU16(response, 12, command);
  writeU16(response, 14, static_cast<uint16_t>(status));
  writeU32(response, 16, static_cast<uint32_t>(payload_size));
  if (payload_size != 0 && payload != nullptr) {
    for (size_t i = 0; i < payload_size; ++i) {
      response[kProtocolHeaderSize + i] = payload[i];
    }
  }
  response_size = kProtocolHeaderSize + payload_size;
}

}  // namespace slt
