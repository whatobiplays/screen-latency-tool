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
  std::array<uint8_t, kEngineeringInfoPayloadSize> payload{};
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

  for (const CachedExchange& cached : replay_cache_) {
    if (!cached.valid || cached.request_id != request_id) continue;
    const bool exact_duplicate = cached.request_size == request_size &&
        std::equal(request, request + request_size, cached.request.begin());
    if (!exact_duplicate) {
      writeResponse(response, request_id, command, Status::BadRequest, nullptr,
                    0, response_size);
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
       response_capacity < kProtocolHeaderSize + kEngineeringPressAckPayloadSize)) {
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

  last_request_id_ = request_id;
  has_request_id_ = true;

  if (major != 1 || minor > 0) {
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
      payload_size = payload.size();
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
                status == Status::Ok ? payload_size : 0, response_size);
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
                             size_t& response_size) const {
  response[0] = 'S';
  response[1] = 'L';
  response[2] = 'T';
  response[3] = '1';
  response[4] = 1;
  response[5] = 0;
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
