#include "slt/capture.hpp"
#include "slt/protocol.hpp"
#include "slt/usb_endpoint_policy.hpp"

#include <algorithm>
#include <array>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

namespace {

void expectCapture(bool condition, const char* message) {
  if (!condition) {
    std::cerr << "FAIL capture: " << message << '\n';
    std::abort();
  }
}

void setQualifiedSoftwareProfile(slt::CaptureMetadata& metadata) {
  metadata.capture_tick_hz = 150000000u;
  metadata.minimum_same_polarity_interval_ticks = 1000u;
  metadata.service_budget = {100u, 200u, 300u, 601u, 200u, 262144u};
  metadata.capture_supported = true;
}

std::vector<uint8_t> fromHex(const std::string& hex) {
  expectCapture((hex.size() % 2u) == 0, "golden vector hex has whole bytes");
  std::vector<uint8_t> bytes;
  bytes.reserve(hex.size() / 2u);
  for (size_t i = 0; i < hex.size(); i += 2) {
    bytes.push_back(static_cast<uint8_t>(std::stoul(hex.substr(i, 2), nullptr, 16)));
  }
  return bytes;
}

void setU16(uint8_t* bytes, size_t offset, uint16_t value) {
  bytes[offset] = static_cast<uint8_t>(value);
  bytes[offset + 1] = static_cast<uint8_t>(value >> 8);
}

void setU32(uint8_t* bytes, size_t offset, uint32_t value) {
  for (size_t i = 0; i < 4; ++i) {
    bytes[offset + i] = static_cast<uint8_t>(value >> (i * 8));
  }
}

void setU64(uint8_t* bytes, size_t offset, uint64_t value) {
  for (size_t i = 0; i < 8; ++i) {
    bytes[offset + i] = static_cast<uint8_t>(value >> (i * 8));
  }
}

uint32_t getU32(const uint8_t* bytes, size_t offset) {
  return static_cast<uint32_t>(bytes[offset]) |
         (static_cast<uint32_t>(bytes[offset + 1]) << 8) |
         (static_cast<uint32_t>(bytes[offset + 2]) << 16) |
         (static_cast<uint32_t>(bytes[offset + 3]) << 24);
}

std::array<uint8_t, 64> captureRequest(uint32_t request_id, uint16_t command,
                                      uint8_t minor, const uint8_t* payload,
                                      size_t payload_size, size_t& frame_size) {
  std::array<uint8_t, 64> frame{};
  frame[0] = 'S';
  frame[1] = 'L';
  frame[2] = 'T';
  frame[3] = '1';
  frame[4] = 1;
  frame[5] = minor;
  frame[6] = 1;
  setU32(frame.data(), 8, request_id);
  setU16(frame.data(), 12, command);
  setU32(frame.data(), 16, static_cast<uint32_t>(payload_size));
  if (payload_size != 0) {
    std::copy_n(payload, payload_size, frame.begin() + slt::kProtocolHeaderSize);
  }
  frame_size = slt::kProtocolHeaderSize + payload_size;
  return frame;
}

std::array<uint8_t, 64> beginCaptureFrame(uint32_t request_id, uint8_t minor,
                                         uint8_t polarity, uint8_t rate_stage,
                                         uint32_t timeout_ms,
                                         size_t& frame_size) {
  std::array<uint8_t, slt::kEngineeringBeginCaptureRequestPayloadSize> payload{};
  setU64(payload.data(), 0, request_id);
  setU16(payload.data(), 8, 10000);
  payload[10] = polarity;
  payload[11] = rate_stage;
  setU32(payload.data(), 12, timeout_ms);
  return captureRequest(request_id, slt::kEngineeringBeginCaptureCommand, minor,
                        payload.data(), payload.size(), frame_size);
}

std::array<uint8_t, 64> archiveRequest(uint32_t request_id, uint8_t operation,
                                       uint32_t generation, uint32_t offset,
                                       uint8_t length, size_t& frame_size) {
  std::array<uint8_t, slt::kEngineeringCaptureArchiveRequestPayloadSize> payload{};
  payload[0] = operation;
  setU32(payload.data(), 2, generation);
  setU32(payload.data(), 6, offset);
  payload[10] = length;
  return captureRequest(request_id, slt::kEngineeringCaptureArchiveCommand, 1,
                        payload.data(), payload.size(), frame_size);
}

struct OneEntryFifoModel {
  bool full = false;
  bool flag = false;
  uint16_t value = 0;
  uint8_t cycle = 0;
  uint8_t edge_counter = 0;
  bool edge_during_value = false;
  bool edge_after_clear_before_value = false;
  bool selected_edge_after_pop = false;
  bool order_ok = true;
  bool cycle_read = false;
  bool clear_before_value = false;
  bool edge_on_cycle_read = false;
  bool value_read = false;

  void edge(uint8_t next_cycle, uint16_t next_value) {
    ++edge_counter;
    if (full) return;
    full = true;
    flag = true;
    cycle = next_cycle;
    value = next_value;
  }

  void selectedEdge(uint8_t next_cycle, uint16_t next_value) {
    ++edge_counter;
    edge(next_cycle, next_value);
  }

  uint8_t fifoCount() {
    if (selected_edge_after_pop && value_read) {
      selected_edge_after_pop = false;
      selectedEdge(static_cast<uint8_t>(cycle + 1),
                   static_cast<uint16_t>(value + 1));
    }
    return full ? 1 : 0;
  }
  bool captureFlag() const { return flag; }

  uint8_t readCycle() {
    cycle_read = true;
    if (edge_on_cycle_read) {
      edge_on_cycle_read = false;
      selectedEdge(static_cast<uint8_t>(cycle + 1),
                   static_cast<uint16_t>(value + 1));
    }
    return cycle;
  }

  uint8_t readEdgeCounter() const { return edge_counter; }

  uint16_t readValue() {
    order_ok = order_ok && cycle_read && clear_before_value;
    if (edge_during_value) {
      edge_during_value = false;
      ++edge_counter;
    }
    const uint16_t result = value;
    full = false;
    value_read = true;
    return result;
  }

  void clearCaptureFlag() {
    flag = false;
    clear_before_value = true;
    if (edge_after_clear_before_value) {
      edge_after_clear_before_value = false;
      selectedEdge(static_cast<uint8_t>(cycle + 1),
                   static_cast<uint16_t>(value + 1));
    }
  }
};

struct CaptureClockSnapshotModel {
  uint16_t status = 0;
  uint16_t counter = 0;
  bool change_reload_on_counter = false;
  uint8_t status_reads = 0;

  uint16_t readReloadStatus() {
    ++status_reads;
    return status;
  }
  uint16_t readCounter() {
    if (change_reload_on_counter) {
      change_reload_on_counter = false;
      status ^= 0x10u;
    }
    return counter;
  }
};

void testShaAndCrcVectors() {
  const uint8_t abc[] = {'a', 'b', 'c'};
  std::array<uint8_t, 32> digest{};
  slt::captureSha256(abc, sizeof(abc), digest.data());
  constexpr std::array<uint8_t, 32> expected{{
      0xba, 0x78, 0x16, 0xbf, 0x8f, 0x01, 0xcf, 0xea,
      0x41, 0x41, 0x40, 0xde, 0x5d, 0xae, 0x22, 0x23,
      0xb0, 0x03, 0x61, 0xa3, 0x96, 0x17, 0x7a, 0x9c,
      0xb4, 0x10, 0xff, 0x61, 0xf2, 0x00, 0x15, 0xad,
  }};
  expectCapture(digest == expected, "SHA-256 matches the standard abc vector");
  constexpr uint8_t digits[] = "123456789";
  expectCapture(slt::captureCrc32(digits, 9) == 0xCBF43926u,
                "CRC-32/ISO-HDLC matches the standard vector");
}

void testTimestampExtensionAndReloadFaults() {
  slt::CaptureTimestampExtender clock;
  expectCapture(clock.initialize(15, 0xFFFFFF00u),
                "shared clock initializes near DWT rollover");
  uint64_t before_reload = 0;
  expectCapture(clock.extend(15, 0xFFFEu, before_reload) &&
                    before_reload == ((uint64_t{15} << 16) | 0xFFFEu),
                "16-bit counter and four-bit cycle value form one timestamp");
  expectCapture(clock.observeMasterReload(0x00000020u, 0x200u),
                "DWT rollover is handled by unsigned reload-gap arithmetic");
  uint64_t after_reload = 0;
  expectCapture(clock.extend(0, 0x0001u, after_reload) &&
                    after_reload == (uint64_t{16} << 16) + 1,
                "four-bit cycle rollover extends through a master reload");
  uint64_t previous_cycle = 0;
  expectCapture(clock.extend(15, 0xFFFFu, previous_cycle) &&
                    previous_cycle < after_reload,
                "a capture latched just before reload maps to the prior epoch");
  expectCapture(!clock.extend(14, 0x1234u, previous_cycle),
                "a capture older than one reload is rejected as ambiguous");
  expectCapture(!clock.observeMasterReload(0x00000230u, 0x200u) && !clock.valid(),
                "a missed reload-service deadline invalidates the timebase");

  CaptureClockSnapshotModel snapshot{0, 0x4321u};
  uint64_t current_tick = 0;
  expectCapture(slt::composeCaptureClockSnapshot(snapshot, 0x10u, 9,
                                                  current_tick) &&
                    current_tick == ((uint64_t{9} << 16) | 0x4321u),
                "atomic current-time read combines one stable counter and epoch");
  snapshot = CaptureClockSnapshotModel{0x10u, 0x0002u};
  expectCapture(slt::composeCaptureClockSnapshot(snapshot, 0x10u, 9,
                                                  current_tick) &&
                    current_tick == ((uint64_t{10} << 16) | 0x0002u),
                "a stable pending reload flag advances the snapshot epoch once");
  snapshot = CaptureClockSnapshotModel{0, 0xFFFFu, true};
  expectCapture(!slt::composeCaptureClockSnapshot(snapshot, 0x10u, 9,
                                                   current_tick),
                "a reload racing the counter read invalidates the snapshot");
  snapshot = CaptureClockSnapshotModel{0x10u, 0, false};
  expectCapture(!slt::composeCaptureClockSnapshot(snapshot, 0x10u, UINT64_MAX,
                                                   current_tick),
                "the shared snapshot epoch refuses 64-bit overflow");
}

void testProvisionalFlexPwm1PinMuxRoutes() {
  constexpr std::array<slt::FlexPwm1CaptureRoute, 5> expected{{
      {0, 48, 0, slt::FlexPwmInput::B, 0, 4, 1, 0},
      {1, 53, 1, slt::FlexPwmInput::A, 0, 2, 1, 0},
      {2, 52, 1, slt::FlexPwmInput::B, 0, 4, 1, 0},
      {3, 49, 2, slt::FlexPwmInput::A, 0, 2, 1, 0},
      {4, 8, 3, slt::FlexPwmInput::A, 0, 2, 6, 4},
  }};
  bool matches = true;
  for (size_t i = 0; i < expected.size(); ++i) {
    const auto& actual = slt::kFlexPwm1CaptureRoutes[i];
    const auto& wanted = expected[i];
    matches = matches && actual.channel == wanted.channel &&
              actual.teensy_pin == wanted.teensy_pin &&
              actual.submodule == wanted.submodule &&
              actual.input == wanted.input &&
              actual.fifo_index == wanted.fifo_index &&
              actual.cval_register_index == wanted.cval_register_index &&
              actual.mux_alt == wanted.mux_alt &&
              actual.daisy_select == wanted.daisy_select;
  }
  expectCapture(matches,
                "five provisional routes map to independent FlexPWM1 A/B capture FIFOs");
  expectCapture(slt::selectedCaptureEdge(slt::CapturePolarity::Ascending) == 2u &&
                    slt::selectedCaptureEdge(slt::CapturePolarity::Descending) == 1u,
                "ascending and descending runs select rising and falling edges");
}

void testCvalCycOrderingAndOneEntryRaces() {
  OneEntryFifoModel fifo;
  fifo.edge(3, 0xFFFEu);
  slt::CaptureRegisterSample sample{};
  expectCapture(slt::popCaptureFifo(fifo, sample) ==
                    slt::CaptureFifoResult::Captured &&
                    sample.cycle_nibble == 3 && sample.timer_counter == 0xFFFEu &&
                    sample.edge_counter == 1 && fifo.order_ok && !fifo.full,
                "CVALxCYC precedes the destructive CVALx read and pairs one entry");

  fifo = OneEntryFifoModel{};
  fifo.edge(2, 0x0100u);
  fifo.edge_on_cycle_read = true;
  expectCapture(slt::popCaptureFifo(fifo, sample) ==
                    slt::CaptureFifoResult::Fault,
                "an edge racing between CYC and its counter bracket faults pairing");
  slt::CaptureArchive race_archive{};
  slt::CaptureEngine race_engine(race_archive);
  slt::CaptureMetadata race_metadata{};
  setQualifiedSoftwareProfile(race_metadata);
  expectCapture(race_engine.begin(1, 1, 10000,
                                  slt::CapturePolarity::Ascending,
                                  slt::CaptureRateStage::QualifiedMaximum,
                                  1000, 1, race_metadata) == slt::Status::Ok &&
                    race_engine.bindAcknowledgement(1, 1, 1) &&
                    race_engine.completeAcknowledgement(1, 1, 1, true,
                                                        true, true, 0, 0),
                "race detector test arms a fresh capture state");
  expectCapture(!race_engine.record(0, 0x0100u, fifo.edge_counter) &&
                    race_engine.state() == slt::CaptureState::Incomplete,
                "an impossible first edge-counter count faults before recording");

  slt::CaptureArchive unqualified_archive{};
  slt::CaptureEngine unqualified_engine(unqualified_archive);
  slt::CaptureMetadata unqualified_metadata{};
  unqualified_metadata.capture_supported = true;
  expectCapture(unqualified_engine.begin(1, 1, 10000,
                                         slt::CapturePolarity::Ascending,
                                         slt::CaptureRateStage::QualifiedMaximum,
                                         1000, 1, unqualified_metadata) ==
                    slt::Status::UnsupportedVersion,
                "missing physical interval and service profile leaves capture disabled");

  fifo = OneEntryFifoModel{};
  fifo.edge(3, 0x1000u);
  fifo.edge_during_value = true;
  expectCapture(slt::popCaptureFifo(fifo, sample) == slt::CaptureFifoResult::Fault,
                "an edge during the CVAL read cannot be paired to the old CYC");

  fifo = OneEntryFifoModel{};
  fifo.edge(4, 0xFFFEu);
  fifo.selected_edge_after_pop = true;
  expectCapture(slt::popCaptureFifo(fifo, sample) ==
                    slt::CaptureFifoResult::MorePending &&
                    sample.cycle_nibble == 4 && sample.timer_counter == 0xFFFEu &&
                    fifo.full && fifo.flag,
                "a post-pop selected edge survives the pre-pop W1C and remains pending");
  expectCapture(slt::popCaptureFifo(fifo, sample) ==
                    slt::CaptureFifoResult::Captured &&
                    sample.cycle_nibble == 5 && sample.timer_counter == 0xFFFFu &&
                    sample.edge_counter == 3 && !fifo.full,
                "the post-pop CYC/CVAL pair drains without stale status");

  fifo = OneEntryFifoModel{};
  fifo.edge(4, 0x2345u);
  fifo.edge_after_clear_before_value = true;
  expectCapture(slt::popCaptureFifo(fifo, sample) ==
                    slt::CaptureFifoResult::Fault,
                "an edge after W1C but before destructive CVAL cannot be hidden");

  fifo = OneEntryFifoModel{};
  fifo.full = true;
  fifo.flag = false;
  expectCapture(slt::popCaptureFifo(fifo, sample) ==
                    slt::CaptureFifoResult::Fault,
                "a FIFO entry without its capture flag is an impossible state");
  fifo.full = false;
  fifo.flag = true;
  expectCapture(slt::popCaptureFifo(fifo, sample) ==
                    slt::CaptureFifoResult::Fault,
                "a capture flag without its FIFO entry is an impossible state");

  fifo = OneEntryFifoModel{};
  fifo.edge(7, 7);
  fifo.selectedEdge(8, 8);  // Interrupt masking leaves the one-entry FIFO full.
  expectCapture(slt::popCaptureFifo(fifo, sample) ==
                    slt::CaptureFifoResult::Captured && sample.edge_counter == 3,
                "masked service exposes overwritten-edge evidence in the counter");
  expectCapture(slt::CaptureServiceBudget{100, 200, 300, 601, 200, 262144}.valid() &&
                    !slt::CaptureServiceBudget{100, 200, 301, 601, 200, 262144}.valid() &&
                    !slt::CaptureServiceBudget{100, 200, 300, 600, 200, 200}.valid() &&
                    !slt::CaptureServiceBudget{100, 200, 300, 601, 131072, 262144}.valid(),
                "service bound includes masking, USB interference, aggregate five-channel drain, and unambiguous reload service");

  std::array<OneEntryFifoModel, slt::kCaptureChannelCount> simultaneous{};
  std::array<uint64_t, slt::kCaptureChannelCount> simultaneous_ticks{};
  slt::CaptureTimestampExtender shared_clock;
  expectCapture(shared_clock.initialize(8, 100),
                "simultaneous-edge model starts from one shared reload epoch");
  for (size_t channel = 0; channel < simultaneous.size(); ++channel) {
    simultaneous[channel].edge(8, 0x4321u);
  }
  size_t drained_samples = 0;
  const slt::CaptureDrainResult drain_result = slt::drainCaptureFifos(
      simultaneous, 4, [&](size_t path, const slt::CaptureRegisterSample& value) {
        ++drained_samples;
        return path < simultaneous_ticks.size() &&
               shared_clock.extend(value.cycle_nibble, value.timer_counter,
                                   simultaneous_ticks[path]);
      });
  expectCapture(drain_result == slt::CaptureDrainResult::Drained &&
                    drained_samples == slt::kCaptureChannelCount &&
                    std::all_of(simultaneous_ticks.begin(),
                                simultaneous_ticks.end(),
                                [&](uint64_t tick) {
                                  return tick == simultaneous_ticks[0];
                                }),
                "one bounded drain services five simultaneous FIFOs in the shared tick domain");

  std::array<OneEntryFifoModel, slt::kCaptureChannelCount> raced{};
  raced[0].edge(8, 0x4321u);
  raced[0].selected_edge_after_pop = true;
  size_t raced_samples = 0;
  expectCapture(slt::drainCaptureFifos(
                    raced, 2, [&](size_t, const slt::CaptureRegisterSample&) {
                      ++raced_samples;
                      return true;
                    }) == slt::CaptureDrainResult::Drained &&
                    raced_samples == 2 && !raced[0].full,
                "the aggregate drain consumes a raced second entry within its fixed bound");
  raced[0] = OneEntryFifoModel{};
  raced[0].edge(8, 0x4321u);
  raced[0].selected_edge_after_pop = true;
  expectCapture(slt::drainCaptureFifos(
                    raced, 1, [](size_t, const slt::CaptureRegisterSample&) {
                      return true;
                    }) == slt::CaptureDrainResult::Fault,
                "the aggregate drain faults when another FIFO entry exceeds its service bound");
}

void testProductionCommandsRemainCompatibleAcrossCaptureMinors() {
  std::array<std::array<uint8_t, 64>, 2> info_responses{};
  for (uint8_t minor = 0; minor <= 1; ++minor) {
    slt::PulseEngine pulse;
    pulse.observeUsbState(true, true, true, 0, 0);
    slt::Protocol protocol(pulse, 0x0123456789ABCDEFu);
    size_t request_size = 0;
    const auto request = captureRequest(1, slt::kEngineeringInfoCommand,
                                        minor, nullptr, 0, request_size);
    size_t response_size = 0;
    expectCapture(protocol.process(request.data(), request_size,
                                   info_responses[minor].data(),
                                   info_responses[minor].size(),
                                   response_size) == slt::Status::Ok &&
                      response_size == 52 &&
                      info_responses[minor][5] == minor &&
                      info_responses[minor][21] == minor,
                  "E001 keeps its 32-byte payload at both supported protocol minors");
  }
  expectCapture(std::equal(info_responses[0].begin() + 22,
                           info_responses[0].begin() + 52,
                           info_responses[1].begin() + 22),
                "E001 1.0 and 1.1 preserve every non-version payload field");

  std::array<std::array<uint8_t, 64>, 2> pulse_responses{};
  for (uint8_t minor = 0; minor <= 1; ++minor) {
    slt::PulseEngine pulse;
    pulse.observeUsbState(true, true, true, 0, 0);
    slt::Protocol protocol(pulse);
    std::array<uint8_t, slt::kEngineeringPressRequestPayloadSize> payload{};
    setU32(payload.data(), 0, 77);
    setU16(payload.data(), 4, 10);
    size_t request_size = 0;
    const auto request = captureRequest(
        2, slt::kEngineeringPressPulseCommand, minor, payload.data(),
        payload.size(), request_size);
    size_t response_size = 0;
    expectCapture(protocol.process(request.data(), request_size,
                                   pulse_responses[minor].data(),
                                   pulse_responses[minor].size(),
                                   response_size) == slt::Status::Ok &&
                      response_size == 32 && pulse_responses[minor][5] == minor,
                  "E002 keeps its accepted 12-byte acknowledgement at both minors");
  }
  expectCapture(std::equal(pulse_responses[0].begin() + 20,
                           pulse_responses[0].begin() + 32,
                           pulse_responses[1].begin() + 20),
                "E002 request and acknowledgement payloads stay version-compatible");
}

void testOnlySuccessfulCaptureBeginResponseBindsArming() {
  std::array<uint8_t, 40> acknowledgement{};
  acknowledgement[0] = 'S';
  acknowledgement[1] = 'L';
  acknowledgement[2] = 'T';
  acknowledgement[3] = '1';
  acknowledgement[4] = slt::kEngineeringProtocolMajor;
  acknowledgement[5] = slt::kEngineeringProtocolMinorCapture;
  acknowledgement[6] = 2;
  setU32(acknowledgement.data(), 8, 7);
  setU16(acknowledgement.data(), 12,
         slt::kEngineeringBeginCaptureCommand);
  setU16(acknowledgement.data(), 14,
         static_cast<uint16_t>(slt::Status::Ok));
  setU32(acknowledgement.data(), 16,
         slt::kEngineeringBeginCaptureAckPayloadSize);
  expectCapture(slt::isSuccessfulCaptureBeginAcknowledgement(
                    acknowledgement.data(), acknowledgement.size()),
                "a well-formed successful E003 acknowledgement binds arming");

  auto rejected = acknowledgement;
  setU16(rejected.data(), 14, static_cast<uint16_t>(slt::Status::Busy));
  expectCapture(!slt::isSuccessfulCaptureBeginAcknowledgement(
                    rejected.data(), rejected.size()),
                "an E003 error response cannot bind or arm capture");
  rejected = acknowledgement;
  setU16(rejected.data(), 12, slt::kEngineeringInfoCommand);
  expectCapture(!slt::isSuccessfulCaptureBeginAcknowledgement(
                    rejected.data(), rejected.size()),
                "an unrelated successful response cannot bind capture");
  rejected = acknowledgement;
  rejected[5] = 0;
  expectCapture(!slt::isSuccessfulCaptureBeginAcknowledgement(
                    rejected.data(), rejected.size()),
                "a legacy minor response cannot bind capture arming");
  expectCapture(!slt::isSuccessfulCaptureBeginAcknowledgement(
                    acknowledgement.data(), acknowledgement.size() - 1),
                "a truncated acknowledgement cannot bind capture arming");
}

void testUnexpectedVendorOutGuardAbortsCaptureWithoutAResponse() {
  static slt::CaptureArchive archive;
  slt::CaptureEngine capture(archive);
  slt::CaptureMetadata metadata{};
  setQualifiedSoftwareProfile(metadata);
  constexpr uint32_t session_generation = 7;
  constexpr uint32_t lifecycle_generation = 3;
  expectCapture(capture.begin(1, 1, 10000, slt::CapturePolarity::Ascending,
                              slt::CaptureRateStage::QualifiedMaximum, 1000,
                              session_generation, metadata) == slt::Status::Ok,
                "capture guard test reserves the engineering archive");

  slt::UsbEndpointLifecycle endpoints;
  endpoints.observeUsbState(true, true, true);
  expectCapture(endpoints.shouldConfigureEndpoints() &&
                    endpoints.markEndpointsConfigured(),
                "capture guard starts from configured Full-Speed endpoints");
  slt::VendorTransferIdentity acknowledgement{};
  expectCapture(endpoints.beginVendorTransfer(
                    slt::VendorEndpoint::In, session_generation,
                    lifecycle_generation, lifecycle_generation, true,
                    acknowledgement) &&
                    capture.bindAcknowledgement(session_generation,
                                                acknowledgement.transfer_generation,
                                                lifecycle_generation),
                "the successful BEGIN response retains its exact IN identity");
  slt::VendorTransferIdentity premature_guard{};
  expectCapture(!endpoints.beginVendorTransfer(
                    slt::VendorEndpoint::Out, session_generation,
                    lifecycle_generation, lifecycle_generation, true,
                    premature_guard),
                "the OUT guard cannot arm before the acknowledgement completes");
  expectCapture(endpoints.completeVendorTransfer(slt::VendorEndpoint::In,
                                                acknowledgement) &&
                    capture.completeAcknowledgement(
                        session_generation, acknowledgement.transfer_generation,
                        lifecycle_generation, true, true, true, 10, 100),
                "capture enters the armed lifecycle after matching IN completion");
  slt::VendorTransferIdentity guard{};
  expectCapture(endpoints.beginVendorTransfer(
                    slt::VendorEndpoint::Out, session_generation,
                    lifecycle_generation, lifecycle_generation, true, guard) &&
                    endpoints.vendorTransferPending(slt::VendorEndpoint::Out),
                "one receive-only OUT guard remains pending during acquisition");

  slt::PulseEngine pulse;
  slt::Protocol protocol(pulse, capture);
  size_t request_size = 0;
  const auto request = archiveRequest(2, 1, 0, 0, 0, request_size);
  std::array<uint8_t, 64> response{};
  size_t response_size = 0;
  expectCapture(protocol.process(request.data(), request_size, response.data(),
                                 response.size(), response_size) ==
                    slt::Status::InvalidState &&
                    response_size == 0,
                "a vendor command during acquisition is not serialized or answered");
  expectCapture(endpoints.completeVendorTransfer(slt::VendorEndpoint::Out,
                                                 guard) &&
                    capture.abort(slt::kCaptureFaultUnexpectedVendorTraffic, 200) &&
                    capture.finalize(200, 10),
                "the unexpected OUT completion aborts and retains its archive");
  slt::CaptureStatus status{};
  expectCapture(capture.status(status) &&
                    status.state == slt::CaptureState::Incomplete &&
                    (status.fault_flags &
                     slt::kCaptureFaultUnexpectedVendorTraffic) != 0,
                "the retained archive identifies the unexpected traffic fault");
}

void testCaptureAckBoundaryPolarityAndNonceLifetime() {
  static slt::CaptureArchive archive;
  slt::CaptureEngine engine(archive);
  slt::CaptureMetadata metadata{};
  setQualifiedSoftwareProfile(metadata);
  expectCapture(engine.begin(1, 1, 10000, slt::CapturePolarity::Ascending,
                             slt::CaptureRateStage::QualifiedMaximum, 60'000,
                             7, metadata) == slt::Status::Ok,
                "ascending capture reserves one archive and nonce");
  expectCapture(engine.acquisitionActive(),
                "vendor traffic remains gated while the BEGIN acknowledgement is pending");
  expectCapture(!engine.record(0, 1, 2),
                "capture cannot record before the BEGIN IN acknowledgement");
  expectCapture(!engine.bindAcknowledgement(8, 19, 4) &&
                    !engine.bindAcknowledgement(7, 0, 4),
                "an acknowledgement cannot bind another session or zero transfer");
  expectCapture(engine.bindAcknowledgement(7, 19, 5),
                "the exact IN transfer identity is retained");
  expectCapture(!engine.completeAcknowledgement(7, 18, 5, true, true, true,
                                                 100, 1),
                "a stale IN completion cannot arm the capture");
  expectCapture(engine.completeAcknowledgement(7, 19, 5, true, true, true,
                                               100, 0x123456789u) &&
                    engine.state() == slt::CaptureState::Capturing,
                "a matching successful IN completion establishes the arm boundary");
  expectCapture(engine.setArmTick(0x12345678Au) &&
                    engine.armTick() == 0x12345678Au &&
                    !engine.setArmTick(0x123456789u),
                "the acknowledged hardware arm boundary can advance but never regress");
  expectCapture(engine.acquisitionActive(),
                "vendor traffic remains gated during physical acquisition");
  expectCapture(engine.abort(slt::kCaptureFaultUnexpectedVendorTraffic, 0),
                "unexpected vendor traffic aborts active capture");
  expectCapture(engine.acquisitionActive(),
                "vendor traffic remains gated until the stopped archive is finalized");
  expectCapture(engine.finalize(0, 88),
                "an interrupted run is finalized as an incomplete archive");
  expectCapture(!engine.acquisitionActive(),
                "archive retrieval begins only after acquisition finalization");
  expectCapture(engine.release(engine.archiveGeneration()),
                "the completed incomplete archive can be released explicitly");
  expectCapture(engine.begin(1, 1, 10000, slt::CapturePolarity::Descending,
                             slt::CaptureRateStage::DoubleMaximum, 60000,
                             8, metadata) == slt::Status::BadRequest,
                "release does not allow a duplicate nonce in the same boot");
  expectCapture(engine.begin(2, 2, 10000, slt::CapturePolarity::Descending,
                             slt::CaptureRateStage::DoubleMaximum, 60000,
                             8, metadata) == slt::Status::Ok,
                "descending runs remain available with a fresh shared request ID");
}

void testAllChannelsCompleteAndArchiveFraming() {
  static slt::CaptureArchive archive;
  slt::CaptureEngine engine(archive);
  slt::CaptureMetadata metadata{};
  setQualifiedSoftwareProfile(metadata);
  metadata.firmware_version[0] = 0;
  metadata.firmware_version[1] = 1;
  for (size_t i = 0; i < sizeof(metadata.git_commit_prefix); ++i) {
    metadata.git_commit_prefix[i] = static_cast<uint8_t>(0xA0u + i);
  }
  expectCapture(engine.begin(17, 17, 10000, slt::CapturePolarity::Descending,
                             slt::CaptureRateStage::DoubleMaximum, 1000,
                             9, metadata) == slt::Status::Ok,
                "five-channel descending run begins");
  expectCapture(engine.bindAcknowledgement(9, 21, 3) &&
                    engine.completeAcknowledgement(9, 21, 3, true, true, true,
                                                   0xFFFFFF00u, 0x1000u),
                "capture starts only after the accepted IN completion");

  for (uint8_t channel = 0; channel < slt::kCaptureChannelCount; ++channel) {
    uint8_t edge_counter = 255;
    for (uint16_t sample = 0; sample < slt::kCaptureSamplesPerChannel; ++sample) {
      if (sample == 0) {
        edge_counter = 1;
      } else {
        edge_counter = static_cast<uint8_t>(edge_counter + 2u);
      }
      const uint64_t timestamp = (uint64_t{channel} << 40) + sample;
      expectCapture(engine.record(channel, timestamp, edge_counter),
                    "each channel records 10,000 monotonic edge timestamps");
    }
  }
  expectCapture(engine.state() == slt::CaptureState::Finalizing &&
                    engine.stopRequested(),
                "the fiftieth thousandth edge stops acquisition before hashing");
  expectCapture(engine.finalize(0, 3210) &&
                    engine.state() == slt::CaptureState::Complete,
                "archive hashing and header serialization finish in main-loop work");
  expectCapture(archive.header[24] == 0 && archive.header[25] == 1 &&
                    archive.header[26] == 0 && archive.header[27] == 0 &&
                    std::equal(metadata.git_commit_prefix,
                               metadata.git_commit_prefix +
                                   sizeof(metadata.git_commit_prefix),
                               archive.header.begin() + 28),
                "the raw archive retains firmware version and source identity");

  slt::CaptureStatus status{};
  expectCapture(engine.status(status) && status.archive_length == 400128u &&
                    status.counts == std::array<uint16_t, 5>{{10000, 10000, 10000, 10000, 10000}},
                "status reports the fixed archive size and all channel counts");
  slt::CaptureReadChunk first{};
  expectCapture(engine.read(status.archive_generation, 0, 32, first) &&
                    first.length == 32 && first.bytes[0] == 'S' &&
                    first.bytes[1] == 'L' && first.bytes[2] == 'A' &&
                    first.bytes[3] == '1',
                "the first 32-byte chunk contains the archive header");
  slt::CaptureReadChunk last{};
  expectCapture(engine.read(status.archive_generation, 400096u, 32, last) &&
                    last.length == 32,
                "the final data chunk reaches the exact 400,128-byte boundary");
  slt::CaptureReadChunk eof{};
  expectCapture(engine.read(status.archive_generation, 400128u, 0, eof) &&
                    eof.length == 0,
                "the zero-length terminal chunk is explicit and valid");
  expectCapture(!engine.read(status.archive_generation, 400128u, 32, eof) &&
                    !engine.read(status.archive_generation, 1, 32, eof) &&
                    !engine.read(status.archive_generation, 0, 44, eof),
                "unaligned offsets and invalid lengths are rejected");
}

void testCounterLossAndTimeoutRetainEvidence() {
  static slt::CaptureArchive archive;
  slt::CaptureEngine engine(archive);
  slt::CaptureMetadata metadata{};
  setQualifiedSoftwareProfile(metadata);
  expectCapture(engine.begin(40, 40, 10000, slt::CapturePolarity::Ascending,
                             slt::CaptureRateStage::QualifiedMaximum, 1000,
                             12, metadata) == slt::Status::Ok &&
                    engine.bindAcknowledgement(12, 9, 4) &&
                    engine.completeAcknowledgement(12, 9, 4, true, true, true,
                                                   0xFFFFFFF0u, 0),
                "timeout case begins after the matching completion");
  expectCapture(engine.record(2, 100, 1), "first counter sample is retained");
  expectCapture(!engine.record(2, 200, 5) &&
                    engine.state() == slt::CaptureState::Incomplete,
                "a non-two edge-counter delta faults and retains the partial archive");
  expectCapture(engine.finalize(200, 44), "counter-fault archive is finalized");
  slt::CaptureStatus status{};
  expectCapture(engine.status(status) &&
                    (status.fault_flags & slt::kCaptureFaultCaptureLoss) != 0 &&
                    status.counts[2] == 1,
                "the partial archive reports the loss fault without truncation");
  expectCapture(engine.release(status.archive_generation),
                "loss archive can be released after host retrieval");

  expectCapture(engine.begin(41, 41, 10000, slt::CapturePolarity::Ascending,
                             slt::CaptureRateStage::QualifiedMaximum, 1000,
                             12, metadata) == slt::Status::Ok &&
                    engine.bindAcknowledgement(12, 10, 4) &&
                    engine.completeAcknowledgement(12, 10, 4, true, true, true,
                                                   0xFFFFFF00u, 0),
                "timeout wraparound case starts");
  expectCapture(!engine.serviceTimeout(0x000002E7u, 5),
                "elapsed time before the timeout remains active across millis wrap");
  expectCapture(engine.serviceTimeout(0x000002E8u, 6) &&
                    engine.state() == slt::CaptureState::Incomplete,
                "timeout after millis wrap aborts and retains the archive");
  expectCapture(engine.finalize(6, 51), "timed-out archive is finalized");
  expectCapture(engine.status(status) &&
                    (status.fault_flags & slt::kCaptureFaultTimeout) != 0,
                "timeout remains explicit in archive status");
  expectCapture(engine.release(status.archive_generation),
                "timeout evidence is released before the next case");

  expectCapture(engine.begin(42, 42, 10000,
                             slt::CapturePolarity::Ascending,
                             slt::CaptureRateStage::QualifiedMaximum, 1000,
                             12, metadata) == slt::Status::Ok &&
                    engine.bindAcknowledgement(12, 11, 4) &&
                    engine.completeAcknowledgement(12, 11, 4, true, true,
                                                   true, 7, 0),
                "service-deadline test starts a third independent run");
  engine.observeServiceLatencyTicks(
      engine.minimumSamePolarityIntervalTicks());
  expectCapture(engine.state() == slt::CaptureState::Incomplete &&
                    engine.finalize(0, 52) && engine.status(status) &&
                    (status.fault_flags &
                     slt::kCaptureFaultServiceDeadline) != 0,
                "an ISR service delay at the configured edge interval faults capture");
}

void testFirstSelectedEdgeSupportsBothPolaritiesAndInputLevels() {
  static slt::CaptureArchive archive;
  slt::CaptureMetadata metadata{};
  setQualifiedSoftwareProfile(metadata);
  uint32_t nonce = 200;
  for (slt::CapturePolarity polarity : {slt::CapturePolarity::Ascending,
                                        slt::CapturePolarity::Descending}) {
    for (uint8_t first_edge_counter : {uint8_t{1}, uint8_t{2}}) {
      slt::CaptureEngine engine(archive);
      expectCapture(engine.begin(nonce, nonce, 10000, polarity,
                                 slt::CaptureRateStage::QualifiedMaximum, 1000,
                                 nonce, metadata) == slt::Status::Ok &&
                        engine.bindAcknowledgement(nonce, nonce, nonce) &&
                        engine.completeAcknowledgement(nonce, nonce, nonce,
                                                       true, true, true, 0, 0),
                    "each polarity starts after its matching successful acknowledgement");
      expectCapture(engine.record(0, 1, first_edge_counter),
                    "the first selected edge accepts either stable input phase");
      const uint8_t after_one_lost_selected_edge =
          static_cast<uint8_t>(first_edge_counter + 4u);
      expectCapture(!engine.record(0, 2, after_one_lost_selected_edge) &&
                        engine.state() == slt::CaptureState::Incomplete,
                    "a skipped selected transition still fails the two-edge delta");
      expectCapture(engine.finalize(2, 0) &&
                        engine.release(engine.archiveGeneration()),
                    "the polarity loss case retains and releases a bounded archive");
      ++nonce;
    }
  }
}

void testServiceFaultAfterLastSampleCannotBecomeComplete() {
  static slt::CaptureArchive archive;
  slt::CaptureEngine engine(archive);
  slt::CaptureMetadata metadata{};
  setQualifiedSoftwareProfile(metadata);
  expectCapture(engine.begin(30, 30, 10000,
                             slt::CapturePolarity::Ascending,
                             slt::CaptureRateStage::QualifiedMaximum, 1000,
                             12, metadata) == slt::Status::Ok &&
                    engine.bindAcknowledgement(12, 13, 5) &&
                    engine.completeAcknowledgement(12, 13, 5, true, true,
                                                   true, 10, 0),
                "final-sample service-deadline case begins");
  for (uint8_t channel = 0; channel < slt::kCaptureChannelCount; ++channel) {
    uint8_t edge_counter = 1;
    for (uint16_t sample = 0; sample < slt::kCaptureSamplesPerChannel;
         ++sample) {
      if (sample != 0) edge_counter = static_cast<uint8_t>(edge_counter + 2u);
      expectCapture(engine.record(channel,
                                  static_cast<uint64_t>(channel) * 20000u + sample,
                                  edge_counter),
                    "final-sample deadline fixture records every channel");
    }
  }
  expectCapture(engine.state() == slt::CaptureState::Finalizing &&
                    engine.abort(slt::kCaptureFaultServiceDeadline, 200001),
                "a service failure in the finalization window overrides success");
  slt::CaptureStatus status{};
  expectCapture(engine.state() == slt::CaptureState::Incomplete &&
                    engine.finalize(200002, 777) && engine.status(status) &&
                    status.state == slt::CaptureState::Incomplete &&
                    (status.fault_flags & slt::kCaptureFaultServiceDeadline) != 0,
                "a late service fault remains incomplete in retained archive evidence");
}

void prepareCompleteCapture(slt::CaptureEngine& capture,
                            slt::CaptureMetadata& metadata) {
  expectCapture(capture.begin(1, 1, 10000,
                              slt::CapturePolarity::Ascending,
                              slt::CaptureRateStage::QualifiedMaximum, 60000,
                              1, metadata) == slt::Status::Ok &&
                    capture.bindAcknowledgement(1, 1, 1) &&
                    capture.completeAcknowledgement(1, 1, 1, true, true,
                                                    true, 10, 0),
                "golden capture archive is initialized");
  for (uint8_t channel = 0; channel < slt::kCaptureChannelCount; ++channel) {
    uint8_t edge_counter = 1;
    for (uint16_t sample = 0; sample < slt::kCaptureSamplesPerChannel;
         ++sample) {
      if (sample != 0) edge_counter = static_cast<uint8_t>(edge_counter + 2u);
      expectCapture(capture.record(channel, sample, edge_counter),
                    "golden capture archive contains all five channels");
    }
  }
  expectCapture(capture.finalize(10000, 3210),
                "golden capture archive is finalized");
}

void testCaptureGoldenVectors() {
  std::ifstream vectors("protocol/engineering-golden-vectors.tsv");
  expectCapture(vectors.good(), "shared engineering vectors are readable");
  std::string line;
  std::getline(vectors, line);
  size_t capture_vectors = 0;
  while (std::getline(vectors, line)) {
    std::istringstream row(line);
    std::string name;
    std::string request_hex;
    std::string response_hex;
    std::getline(row, name, '\t');
    std::getline(row, request_hex, '\t');
    std::getline(row, response_hex, '\t');
    if (name.rfind("engineering_capture_", 0) != 0) continue;
    ++capture_vectors;

    const std::vector<uint8_t> request = fromHex(request_hex);
    const std::vector<uint8_t> expected = fromHex(response_hex);
    expectCapture(request.size() <= slt::kMaximumEngineeringFrameSize &&
                      expected.size() <= slt::kMaximumEngineeringFrameSize,
                  "capture golden frames stay within 64 bytes");

    slt::CaptureArchive archive{};
    slt::CaptureEngine capture(archive);
    slt::PulseEngine pulse;
    pulse.observeUsbState(true, true, true, 0, 0);
    slt::Protocol protocol(pulse, capture, 0x0123456789ABCDEFu);
    slt::CaptureMetadata metadata{};
    setQualifiedSoftwareProfile(metadata);
    protocol.setCaptureMetadata(metadata);

    if (name == "engineering_capture_status_v11" ||
        name == "engineering_capture_read_32_v11" ||
        name == "engineering_capture_read_eof_v11" ||
        name == "engineering_capture_release_v11") {
      prepareCompleteCapture(capture, metadata);
      if (name == "engineering_capture_read_32_v11") {
        for (size_t i = 0; i < 32; ++i) {
          archive.header[i] = static_cast<uint8_t>(i);
        }
      }
    }

    std::array<uint8_t, slt::kMaximumEngineeringFrameSize> response{};
    size_t response_size = 0;
    expectCapture(protocol.process(request.data(), request.size(),
                                   response.data(), response.size(),
                                   response_size) == slt::Status::Ok,
                  "capture golden vector request is accepted");
    expectCapture(response_size == expected.size() &&
                      std::equal(expected.begin(), expected.end(),
                                 response.begin()),
                  "capture response matches its byte-exact golden vector");
  }
  expectCapture(capture_vectors == 6,
                "all six engineering capture golden vectors are checked");
}

void testEngineeringCaptureProtocolAndReplayFences() {
  static slt::CaptureArchive archive;
  slt::CaptureEngine capture(archive);
  slt::PulseEngine pulse;
  pulse.observeUsbState(true, true, true, 0, 0);
  slt::Protocol protocol(pulse, capture, 0x0123456789ABCDEFu);
  slt::CaptureMetadata metadata{};
  setQualifiedSoftwareProfile(metadata);
  protocol.setCaptureMetadata(metadata);

  std::array<uint8_t, 64> response{};
  size_t response_size = 0;
  size_t request_size = 0;
  const auto old_minor = beginCaptureFrame(
      1, 0, 1, 1, 60000, request_size);
  expectCapture(protocol.process(old_minor.data(), request_size, response.data(),
                                 response.size(), response_size) ==
                    slt::Status::UnsupportedVersion && response[5] == 0,
                "v1.0 clients receive an explicit unsupported-version result for E003");

  const auto begin = beginCaptureFrame(2, 1, 2, 2, 60000, request_size);
  expectCapture(protocol.process(begin.data(), request_size, response.data(),
                                 response.size(), response_size) == slt::Status::Ok &&
                    response_size == 40 && response[5] == 1 &&
                    getU32(response.data(), 20) == 2 &&
                    response[36] == static_cast<uint8_t>(slt::CaptureState::AcknowledgementPending) &&
                    response[37] == 2,
                "v1.1 E003 returns the exact 20-byte pending-ack payload");
  expectCapture(capture.bindAcknowledgement(1, 7, 3) &&
                    capture.completeAcknowledgement(1, 7, 3, true, true, true,
                                                    10, 0x1122334455667788u),
                "only the successful matching IN transfer arms the capture");
  expectCapture(capture.record(0, 100, 1), "active capture retains a first edge");

  size_t status_size = 0;
  const auto forbidden_status = archiveRequest(3, 1, 0, 0, 0, status_size);
  expectCapture(protocol.process(forbidden_status.data(), status_size,
                                 response.data(), response.size(), response_size) ==
                    slt::Status::InvalidState && response_size == 0,
                "archive requests produce no vendor response during acquisition");
  expectCapture(capture.abort(slt::kCaptureFaultUsbReset, 101) &&
                    capture.finalize(101, 211),
                "reset retains an incomplete archive for later retrieval");

  const auto status_request = archiveRequest(4, 1, 0, 0, 0, request_size);
  expectCapture(protocol.process(status_request.data(), request_size,
                                 response.data(), response.size(), response_size) ==
                    slt::Status::Ok && response_size == 44 &&
                    getU32(response.data(), 20) == 1 && response[24] == 5 &&
                    response[25] == 2 && getU32(response.data(), 38) == 400128u,
                "E004 status names the retained incomplete archive and its fixed size");
  const uint32_t generation = getU32(response.data(), 20);

  const auto read_request = archiveRequest(5, 2, generation, 0, 32, request_size);
  expectCapture(protocol.process(read_request.data(), request_size,
                                 response.data(), response.size(), response_size) ==
                    slt::Status::Ok && response_size == 64 &&
                    getU32(response.data(), 20) == generation &&
                    response[32] == 'S' && response[33] == 'L',
                "E004 READ returns 32 archive bytes plus the fixed metadata");
  std::array<uint8_t, 41> crc_input{};
  setU32(crc_input.data(), 0, generation);
  setU32(crc_input.data(), 4, 0);
  crc_input[8] = 32;
  std::copy_n(response.begin() + 32, 32, crc_input.begin() + 9);
  expectCapture(getU32(response.data(), 28) ==
                    slt::captureCrc32(crc_input.data(), crc_input.size()),
                "READ CRC covers generation, offset, length, and payload bytes");

  const auto eof_request = archiveRequest(6, 2, generation, 400128u, 0,
                                          request_size);
  expectCapture(protocol.process(eof_request.data(), request_size,
                                 response.data(), response.size(), response_size) ==
                    slt::Status::Ok && response_size == 32 &&
                    getU32(response.data(), 16) == 12 &&
                    getU32(response.data(), 24) == 400128u,
                "zero-length EOF response fits the frame and remains explicit");
  const auto release_request = archiveRequest(7, 3, generation, 0, 0,
                                              request_size);
  expectCapture(protocol.process(release_request.data(), request_size,
                                 response.data(), response.size(), response_size) ==
                    slt::Status::Ok && response_size == 28 &&
                    response[24] == static_cast<uint8_t>(slt::CaptureState::Empty),
                "E004 RELEASE clears the archive after explicit retrieval");

  protocol.resetSession();
  expectCapture(protocol.process(begin.data(), slt::kProtocolHeaderSize +
                                                 slt::kEngineeringBeginCaptureRequestPayloadSize,
                                 response.data(), response.size(), response_size) ==
                    slt::Status::BadRequest && capture.state() == slt::CaptureState::Empty &&
                    capture.highestNonce() == 2,
                "resetting the USB request epoch cannot execute a consumed nonce again");
}

}  // namespace

void runCaptureTests() {
  testShaAndCrcVectors();
  testTimestampExtensionAndReloadFaults();
  testProvisionalFlexPwm1PinMuxRoutes();
  testCvalCycOrderingAndOneEntryRaces();
  testProductionCommandsRemainCompatibleAcrossCaptureMinors();
  testOnlySuccessfulCaptureBeginResponseBindsArming();
  testUnexpectedVendorOutGuardAbortsCaptureWithoutAResponse();
  testCaptureAckBoundaryPolarityAndNonceLifetime();
  testAllChannelsCompleteAndArchiveFraming();
  testServiceFaultAfterLastSampleCannotBecomeComplete();
  testCounterLossAndTimeoutRetainEvidence();
  testFirstSelectedEdgeSupportsBothPolaritiesAndInputLevels();
  testEngineeringCaptureProtocolAndReplayFences();
  testCaptureGoldenVectors();
}
