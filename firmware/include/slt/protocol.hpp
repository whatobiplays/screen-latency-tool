#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

#include "slt/pulse_engine.hpp"

namespace slt {

constexpr uint16_t kEngineeringInfoCommand = 0xE001;
constexpr uint16_t kEngineeringPressPulseCommand = 0xE002;
constexpr size_t kEngineeringInfoPayloadSize = 32;
constexpr size_t kEngineeringPressRequestPayloadSize = 8;
constexpr size_t kEngineeringPressAckPayloadSize = 12;
constexpr size_t kProtocolHeaderSize = 20;
constexpr size_t kMaximumEngineeringFrameSize = 64;
constexpr size_t kRequestReplayCacheEntries = 4;
constexpr uint8_t kEngineeringProtocolMajor = 1;
constexpr uint8_t kEngineeringProtocolMinor = 0;
constexpr uint8_t kEngineeringFirmwareMajor = 0;
constexpr uint8_t kEngineeringFirmwareMinor = 1;

class Protocol {
 public:
  explicit Protocol(PulseEngine& engine, uint64_t fixture_serial = 0)
      : engine_(engine), fixture_serial_(fixture_serial) {}

  void setFixtureSerial(uint64_t fixture_serial) {
    fixture_serial_ = fixture_serial;
  }

  // Returns the command status encoded in the response. Invalid input never
  // mutates the pulse engine. Request IDs are ordered within one USB session.
  Status process(const uint8_t* request, size_t request_size, uint8_t* response,
                 size_t response_capacity, size_t& response_size,
                 uint32_t now_tick = 0);
  // Call only after a confirmed USB detach. Bus resets and endpoint recovery
  // retain replay state so a retried request cannot execute twice.
  void resetSession();

 private:
  struct CachedExchange {
    bool valid = false;
    uint32_t request_id = 0;
    size_t request_size = 0;
    std::array<uint8_t, kMaximumEngineeringFrameSize> request{};
    Status status = Status::BadRequest;
    size_t response_size = 0;
    std::array<uint8_t, kMaximumEngineeringFrameSize> response{};
  };

  void serializeInfo(uint8_t* payload, const Snapshot& snapshot) const;
  void writeResponse(uint8_t* response, uint32_t request_id, uint16_t command,
                     Status status, const uint8_t* payload, size_t payload_size,
                     size_t& response_size) const;
  void remember(uint32_t request_id, const uint8_t* request,
                size_t request_size, Status status, const uint8_t* response,
                size_t response_size);
  PulseEngine& engine_;
  uint64_t fixture_serial_ = 0;
  uint32_t last_request_id_ = 0;
  bool has_request_id_ = false;
  size_t replay_next_index_ = 0;
  std::array<CachedExchange, kRequestReplayCacheEntries> replay_cache_{};
};

}  // namespace slt
