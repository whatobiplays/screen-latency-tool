#include "slt/protocol.hpp"
#include "slt/pulse_engine.hpp"
#include "slt/hid_slot_pool.hpp"
#include "slt/serial_number.h"
#include "slt/usb_transfer_status.hpp"
#include "slt/usb_endpoint_policy.hpp"

#include <algorithm>
#include <array>
#include <cassert>
#include <cstdlib>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

namespace {

using slt::Action;
using slt::ActionKind;
using slt::Fault;
using slt::PulseEngine;
using slt::PulseState;
using slt::Protocol;
using slt::Status;
using slt::TransferKind;
using slt::TransferResult;

constexpr uint32_t kTicksPerMillisecond = 600000;
constexpr uint32_t kSessionGeneration = 1;
constexpr uint64_t kVectorSerial = 0x0123456789ABCDEFu;

void expect(bool condition, const char* message) {
  if (!condition) {
    std::cerr << "FAIL: " << message << '\n';
    std::abort();
  }
}

void ready(PulseEngine& engine) {
  engine.observeUsbState(true, true, true, 0, 0);
  expect(engine.snapshot().session_generation == kSessionGeneration,
         "first configured session starts at generation one");
}

Action takeTransfer(PulseEngine& engine, TransferKind expected_kind) {
  Action action{};
  expect(engine.takeAction(action), "expected a queued action");
  expect(action.kind == ActionKind::Transfer, "expected a USB transfer action");
  expect(action.transfer_kind == expected_kind, "transfer kind matches state");
  return action;
}

Action takeCancel(PulseEngine& engine, TransferKind expected_kind) {
  Action action{};
  expect(engine.takeAction(action), "expected a cancellation action");
  expect(action.kind == ActionKind::Cancel, "expected a USB cancellation action");
  expect(action.transfer_kind == expected_kind, "cancelled transfer kind matches");
  return action;
}

void finish(PulseEngine& engine, const Action& action, TransferResult result,
            uint32_t tick) {
  engine.onTransferComplete(action.token, result, tick);
}

std::vector<uint8_t> fromHex(const std::string& value) {
  expect((value.size() % 2) == 0, "hex vector has an even number of digits");
  std::vector<uint8_t> bytes;
  bytes.reserve(value.size() / 2);
  for (size_t i = 0; i < value.size(); i += 2) {
    bytes.push_back(static_cast<uint8_t>(std::stoul(value.substr(i, 2), nullptr, 16)));
  }
  return bytes;
}

std::vector<uint8_t> makeRequest(uint32_t request_id, uint16_t command,
                                 const std::vector<uint8_t>& payload = {}) {
  std::vector<uint8_t> request(slt::kProtocolHeaderSize + payload.size(), 0);
  request[0] = 'S';
  request[1] = 'L';
  request[2] = 'T';
  request[3] = '1';
  request[4] = 1;
  request[6] = 1;
  for (size_t i = 0; i < 4; ++i) {
    request[8 + i] = static_cast<uint8_t>(request_id >> (8 * i));
  }
  request[12] = static_cast<uint8_t>(command);
  request[13] = static_cast<uint8_t>(command >> 8);
  request[16] = static_cast<uint8_t>(payload.size());
  request[17] = static_cast<uint8_t>(payload.size() >> 8);
  request[18] = static_cast<uint8_t>(payload.size() >> 16);
  request[19] = static_cast<uint8_t>(payload.size() >> 24);
  std::copy(payload.begin(), payload.end(), request.begin() + 20);
  return request;
}

std::vector<uint8_t> makePulseRequest(uint32_t request_id, uint32_t tag,
                                      uint16_t duration_ms) {
  return makeRequest(request_id, slt::kEngineeringPressPulseCommand,
                     {static_cast<uint8_t>(tag),
                      static_cast<uint8_t>(tag >> 8),
                      static_cast<uint8_t>(tag >> 16),
                      static_cast<uint8_t>(tag >> 24),
                      static_cast<uint8_t>(duration_ms),
                      static_cast<uint8_t>(duration_ms >> 8), 0, 0});
}

std::vector<uint8_t> makePulseRequest(uint32_t request_id,
                                      uint16_t duration_ms) {
  return makePulseRequest(request_id, request_id, duration_ms);
}

uint32_t readU32(const uint8_t* bytes, size_t offset) {
  return static_cast<uint32_t>(bytes[offset]) |
         (static_cast<uint32_t>(bytes[offset + 1]) << 8) |
         (static_cast<uint32_t>(bytes[offset + 2]) << 16) |
         (static_cast<uint32_t>(bytes[offset + 3]) << 24);
}

void common_vectors_match() {
  PulseEngine engine;
  ready(engine);
  Protocol protocol(engine, kVectorSerial);
  std::ifstream vectors("protocol/engineering-golden-vectors.tsv");
  expect(vectors.good(), "shared engineering vectors are readable");

  std::string line;
  std::getline(vectors, line);
  struct Vector {
    std::string request;
    std::string response;
  };
  std::vector<Vector> rows;
  while (std::getline(vectors, line)) {
    std::istringstream row(line);
    std::string name;
    Vector vector;
    std::getline(row, name, '\t');
    std::getline(row, vector.request, '\t');
    std::getline(row, vector.response, '\t');
    rows.push_back(std::move(vector));
  }
  expect(rows.size() == 4, "all shared engineering vectors are loaded");

  auto check_request = [&](size_t index) {
    // Golden rows are independent captures with their own request-ID epochs.
    protocol.resetSession();
    const std::vector<uint8_t> request = fromHex(rows[index].request);
    const std::vector<uint8_t> expected = fromHex(rows[index].response);
    std::array<uint8_t, 64> response{};
    size_t response_size = 0;
    expect(protocol.process(request.data(), request.size(), response.data(),
                            response.size(), response_size) == Status::Ok,
           "golden vector request succeeds");
    expect(response_size == expected.size(), "golden vector response length matches");
    expect(std::equal(expected.begin(), expected.end(), response.begin()),
           "golden vector response bytes match");
  };

  check_request(1);
  check_request(0);
  Action press = takeTransfer(engine, TransferKind::Press);
  finish(engine, press, TransferResult::Success, 1000);
  engine.service(1000 + 10 * kTicksPerMillisecond);
  Action release = takeTransfer(engine, TransferKind::Release);
  finish(engine, release, TransferResult::Success,
         1000 + 10 * kTicksPerMillisecond + 1);
  protocol.resetSession();
  const auto completion_info_request = makeRequest(1, slt::kEngineeringInfoCommand);
  std::array<uint8_t, 64> completion_info_response{};
  size_t completion_info_size = 0;
  expect(protocol.process(completion_info_request.data(), completion_info_request.size(),
                         completion_info_response.data(), completion_info_response.size(),
                         completion_info_size) == Status::Ok &&
             completion_info_response[25] ==
                 (static_cast<uint8_t>(PulseState::Complete) | 0x38) &&
             readU32(completion_info_response.data(), 34) == 1 &&
        readU32(completion_info_response.data(), 46) == 1000u &&
        completion_info_response[50] == static_cast<uint8_t>(10),
         "completion snapshot reports sequence, validity, and press tick before idle");
  check_request(2);

  PulseEngine wrapped_engine;
  ready(wrapped_engine);
  expect(wrapped_engine.acceptPulse(10, 0).status == Status::Ok,
         "wrapped-tick golden vector pulse is accepted");
  Action wrapped_press = takeTransfer(wrapped_engine, TransferKind::Press);
  const uint32_t wrapped_tick = 0xF1234567u;
  finish(wrapped_engine, wrapped_press, TransferResult::Success, wrapped_tick);
  const uint32_t wrapped_deadline = wrapped_tick + 10 * kTicksPerMillisecond;
  wrapped_engine.service(wrapped_deadline);
  Action wrapped_release = takeTransfer(wrapped_engine, TransferKind::Release);
  finish(wrapped_engine, wrapped_release, TransferResult::Success,
         wrapped_deadline + 1);
  Protocol wrapped_protocol(wrapped_engine, kVectorSerial);
  std::array<uint8_t, 64> response{};
  size_t response_size = 0;
  const std::vector<uint8_t> request = fromHex(rows[3].request);
  const std::vector<uint8_t> expected = fromHex(rows[3].response);
  expect(wrapped_protocol.process(request.data(), request.size(), response.data(),
                                  response.size(), response_size) == Status::Ok,
         "wrapped completion snapshot request succeeds");
  expect(response_size == expected.size() &&
             std::equal(expected.begin(), expected.end(), response.begin()),
         "wrapped tick vector bytes match");
}

void usb_serial_uses_sixteen_uppercase_mac_hex_digits() {
  std::array<uint16_t, 16> serial{};
  slt_format_usb_serial_number(0x12345678u, 0x9abcdef0u, serial.data());
  constexpr std::array<uint16_t, 16> expected{{
      '1', '2', '3', '4', '5', '6', '7', '8',
      '9', 'A', 'B', 'C', 'D', 'E', 'F', '0',
  }};
  expect(serial == expected,
         "USB serial encodes both MAC words as uppercase hexadecimal");

  slt_format_usb_serial_number(0x0000000au, 0x0000000fu, serial.data());
  constexpr std::array<uint16_t, 16> zero_padded{{
      '0', '0', '0', '0', '0', '0', '0', 'A',
      '0', '0', '0', '0', '0', '0', '0', 'F',
  }};
  expect(serial == zero_padded,
         "USB serial preserves leading zeros in both MAC words");
}

void duration_boundaries_and_delayed_press_completion() {
  PulseEngine engine;
  ready(engine);
  expect(engine.acceptPulse(9, 0).status == Status::RangeError,
         "duration below ten milliseconds is rejected");
  expect(engine.snapshot().sequence == 0, "rejected duration allocates no sequence");

  expect(engine.acceptPulse(10, 0).status == Status::Ok,
         "ten milliseconds is accepted");
  Action press = takeTransfer(engine, TransferKind::Press);
  engine.service(5 * kTicksPerMillisecond);
  Action unused{};
  expect(!engine.takeAction(unused), "no release is queued before press completion");

  const uint32_t completion_tick = 0xFFF00000u;
  finish(engine, press, TransferResult::Success, completion_tick);
  expect(engine.snapshot().tick_valid, "successful press completion establishes the tick");
  engine.service(completion_tick - 1);
  Action before_completion_clock{};
  expect(!engine.takeAction(before_completion_clock),
         "a stale loop clock cannot release before the press completion time");
  engine.service(completion_tick + 10 * kTicksPerMillisecond - 1);
  Action pending{};
  expect(!engine.takeAction(pending), "release waits the full duration after completion");
  engine.service(completion_tick + 10 * kTicksPerMillisecond);
  Action release = takeTransfer(engine, TransferKind::Release);
  expect(release.report == std::array<uint8_t, 6>{0, 0, 0, 0, 0, 0},
         "release report has six neutral bytes");
  finish(engine, release, TransferResult::Success,
         completion_tick + 10 * kTicksPerMillisecond + 1);
  const auto completed_short_pulse = engine.snapshot();
  expect(completed_short_pulse.state == PulseState::Complete &&
             completed_short_pulse.sequence == 1 &&
             completed_short_pulse.tick_valid &&
             completed_short_pulse.completion_tick == completion_tick,
         "release completion preserves the measured sequence and press tick");

  Protocol protocol(engine);
  const auto completion_info = makeRequest(1, slt::kEngineeringInfoCommand);
  std::array<uint8_t, 64> info_response{};
  size_t info_size = 0;
  expect(protocol.process(completion_info.data(), completion_info.size(),
                          info_response.data(), info_response.size(), info_size) == Status::Ok &&
             info_response[24] == static_cast<uint8_t>(slt::UsbSpeed::Full) &&
             info_response[25] ==
                 (static_cast<uint8_t>(PulseState::Complete) | 0x38) &&
             readU32(info_response.data(), 34) == 1 &&
             readU32(info_response.data(), 46) == completion_tick &&
             info_response[50] == 10,
         "completion info reports the measured tick before returning the engine to idle");

  expect(engine.acceptPulse(1000, 0).status == Status::Ok,
         "one thousand milliseconds is accepted");
  Action long_press = takeTransfer(engine, TransferKind::Press);
  const uint32_t long_press_completion = 0xFFFF0000u;
  finish(engine, long_press, TransferResult::Success, long_press_completion);
  const uint32_t long_deadline =
      long_press_completion + 1000u * kTicksPerMillisecond;
  engine.service(long_deadline - 1);
  Action before_deadline{};
  expect(!engine.takeAction(before_deadline),
         "one thousand millisecond pulse holds until the inclusive boundary");
  engine.service(long_deadline);
  Action long_release = takeTransfer(engine, TransferKind::Release);
  expect(engine.snapshot().sequence == completed_short_pulse.sequence + 1,
         "release keeps the sequence allocated for its measured press");
  finish(engine, long_release, TransferResult::Success, long_deadline + 1);
  expect(engine.snapshot().state == PulseState::Complete,
         "one thousand millisecond pulse completes after release");

  PulseEngine out_of_range;
  ready(out_of_range);
  expect(out_of_range.acceptPulse(1001, 0).status == Status::RangeError,
         "duration above one thousand milliseconds is rejected while idle");
}

void busy_request_does_not_mutate_pulse() {
  PulseEngine engine;
  ready(engine);
  expect(engine.acceptPulse(50, 10).status == Status::Ok,
         "first pulse is accepted");
  const auto before = engine.snapshot();
  expect(engine.acceptPulse(60, 20).status == Status::Busy,
         "second pulse receives busy while press is queued");
  const auto after = engine.snapshot();
  expect(before.sequence == after.sequence && before.state == after.state &&
             before.transfer_generation == after.transfer_generation &&
             before.duration_ms == after.duration_ms,
         "busy response leaves pulse state unchanged");
}

void callback_reordering_and_stale_generation_are_ignored() {
  PulseEngine engine;
  ready(engine);
  expect(engine.acceptPulse(20, 0).status == Status::Ok,
         "pulse is accepted before callback reordering test");
  Action press = takeTransfer(engine, TransferKind::Press);
  Action stale = press;
  stale.token.transfer_generation++;
  engine.onTransferComplete(stale.token, TransferResult::Success, 99);
  expect(engine.snapshot().state == PulseState::PressQueued &&
             !engine.snapshot().tick_valid,
         "wrong transfer generation cannot establish completion evidence");

  finish(engine, press, TransferResult::Success, 100);
  expect(engine.snapshot().state == PulseState::Holding,
         "matching press callback enters holding state");
  engine.service(100 + 20 * kTicksPerMillisecond);
  Action release = takeTransfer(engine, TransferKind::Release);
  const auto before = engine.snapshot();
  finish(engine, press, TransferResult::Success, 101);
  expect(engine.snapshot().state == before.state &&
             engine.snapshot().completion_tick == before.completion_tick,
         "late press callback cannot overwrite release state or tick");
  finish(engine, release, TransferResult::Success, 100 + 20 * kTicksPerMillisecond + 1);
  expect(engine.snapshot().state == PulseState::Complete,
         "release callback completes after reordered stale callback");
}

void cancellation_reset_and_disconnect_invalidate_evidence() {
  PulseEngine engine;
  ready(engine);
  Protocol protocol(engine);
  expect(engine.acceptPulse(25, 0).status == Status::Ok,
         "pulse is accepted before reset test");
  Action press = takeTransfer(engine, TransferKind::Press);
  engine.onUsbReset();
  const auto reset_snapshot = engine.snapshot();
  expect(!reset_snapshot.tick_valid && reset_snapshot.completion_tick == 0,
         "reset clears measured completion evidence");
  expect(reset_snapshot.fault == Fault::SessionInvalidated,
         "reset records session invalidation");
  engine.observeUsbState(true, true, true, 0, 100);
  engine.service(100);
  Action recovery = takeTransfer(engine, TransferKind::Recovery);
  expect(recovery.report == std::array<uint8_t, 6>{0, 0, 0, 0, 0, 0},
         "re-enumerated session sends only an unmeasured neutral recovery");
  finish(engine, recovery, TransferResult::Success, 101);

  expect(engine.acceptPulse(25, 200).status == Status::Busy,
         "reset fault remains non-idle until engineering info reports it");
  const auto reset_info = makeRequest(1, slt::kEngineeringInfoCommand);
  std::array<uint8_t, 64> info_response{};
  size_t info_size = 0;
  expect(protocol.process(reset_info.data(), reset_info.size(), info_response.data(),
                          info_response.size(), info_size) == Status::Ok &&
             info_response[24] == static_cast<uint8_t>(slt::UsbSpeed::Full) &&
             (info_response[25] & 0x07) == static_cast<uint8_t>(PulseState::Fault) &&
             (info_response[25] & 0x08) == 0 &&
             readU32(info_response.data(), 46) == 0,
         "reset recovery reports its fault with invalid completion evidence");

  expect(engine.acceptPulse(25, 200).status == Status::Ok,
         "pulse can start after recovery completes");
  Action second_press = takeTransfer(engine, TransferKind::Press);
  expect(second_press.token.session_generation != press.token.session_generation,
         "re-enumeration advances the USB session generation");
  engine.onTransferComplete(press.token, TransferResult::Success, 250);
  expect(engine.snapshot().state == PulseState::PressQueued &&
             !engine.snapshot().tick_valid,
         "late old-session completion cannot complete a new pulse");
  finish(engine, second_press, TransferResult::Success, 300);
  engine.onDisconnect();
  expect(!engine.snapshot().tick_valid &&
             engine.snapshot().completion_tick == 0,
         "disconnect clears completion evidence");
  engine.onTransferComplete(second_press.token, TransferResult::Success, 301);
  expect(engine.snapshot().state != PulseState::ReleaseQueued,
         "disconnect cannot cause a measured release");
}

void press_failure_timeout_and_failed_recovery_are_fail_closed() {
  PulseEngine undispatched_press;
  ready(undispatched_press);
  const uint32_t undispatched_start = 77;
  expect(undispatched_press.acceptPulse(10, undispatched_start).status ==
             Status::Ok,
         "undispatched press is accepted before its action is serviced");
  undispatched_press.service(undispatched_start + slt::kTransferTimeoutTicks);
  Action undispatched_recovery =
      takeTransfer(undispatched_press, TransferKind::Recovery);
  expect(undispatched_recovery.report ==
             std::array<uint8_t, 6>{0, 0, 0, 0, 0, 0} &&
             undispatched_press.snapshot().fault == Fault::PressTimeout &&
             !undispatched_press.snapshot().tick_valid &&
             undispatched_press.snapshot().completion_tick == 0,
         "timed-out queued press is discarded before only neutral recovery");
  finish(undispatched_press, undispatched_recovery, TransferResult::Success,
         undispatched_start + slt::kTransferTimeoutTicks + 1);
  expect(undispatched_press.snapshot().fault == Fault::PressTimeout &&
             !undispatched_press.snapshot().recovery_required,
         "undispatched press timeout remains diagnostic after safe recovery");

  PulseEngine failed_press;
  ready(failed_press);
  expect(failed_press.acceptPulse(40, 100).status == Status::Ok,
         "pulse is accepted before transfer failure");
  Action press = takeTransfer(failed_press, TransferKind::Press);
  finish(failed_press, press,
         slt::hidTransferResult(1u << 16, 6), 110);
  expect(!failed_press.snapshot().tick_valid &&
             failed_press.snapshot().fault == Fault::PressTransferFailed,
         "failed press has no measured completion tick");
  Action recovery = takeTransfer(failed_press, TransferKind::Recovery);
  expect(recovery.report == std::array<uint8_t, 6>{0, 0, 0, 0, 0, 0},
         "failed measured press can only request neutral unmeasured recovery");
  finish(failed_press, recovery, TransferResult::Error, 120);
  expect(failed_press.snapshot().state == PulseState::Fault &&
             failed_press.snapshot().fault == Fault::RecoveryFailed &&
             failed_press.snapshot().recovery_required,
         "failed unmeasured recovery remains faulted and blocks new pulses");
  expect(failed_press.acceptPulse(50, 130).status == Status::Busy,
         "failed recovery prevents another measured pulse");

  PulseEngine recovery_timeout;
  ready(recovery_timeout);
  expect(recovery_timeout.acceptPulse(10, 0).status == Status::Ok,
         "recovery-timeout pulse is accepted");
  Action recovery_timeout_press = takeTransfer(recovery_timeout, TransferKind::Press);
  const uint32_t press_failure_tick = 100;
  finish(recovery_timeout, recovery_timeout_press, TransferResult::Error,
         press_failure_tick);
  Action slow_recovery = takeTransfer(recovery_timeout, TransferKind::Recovery);
  expect(slow_recovery.token.session_generation ==
             recovery_timeout.snapshot().session_generation,
         "safe recovery is bound to the active USB session");
  recovery_timeout.service(press_failure_tick + slt::kTransferTimeoutTicks - 1);
  Action before_recovery_timeout{};
  expect(!recovery_timeout.takeAction(before_recovery_timeout),
         "safe recovery receives its own full transfer timeout window");
  recovery_timeout.service(press_failure_tick + slt::kTransferTimeoutTicks);
  Action cancel_recovery = takeCancel(recovery_timeout, TransferKind::Recovery);
  finish(recovery_timeout, cancel_recovery, TransferResult::Cancelled,
         press_failure_tick + slt::kTransferTimeoutTicks);
  expect(recovery_timeout.snapshot().fault == Fault::RecoveryTimeout &&
             recovery_timeout.snapshot().recovery_required,
         "recovery timeout remains a pollable fault and blocks measured pulses");

  PulseEngine timeout;
  ready(timeout);
  const uint32_t timeout_start = 0xF0000000u;
  expect(timeout.acceptPulse(40, timeout_start).status == Status::Ok,
         "pulse is accepted before timeout");
  Action timed_press = takeTransfer(timeout, TransferKind::Press);
  timeout.service(timeout_start + slt::kTransferTimeoutTicks - 1);
  Action unused{};
  expect(!timeout.takeAction(unused), "press remains pending before timeout boundary");
  timeout.service(timeout_start + slt::kTransferTimeoutTicks);
  Action cancel = takeCancel(timeout, TransferKind::Press);
  expect(timeout.snapshot().fault == Fault::PressTimeout &&
             !timeout.snapshot().tick_valid &&
             timeout.snapshot().completion_tick == 0,
         "press timeout invalidates evidence before cancellation callback");
  expect(cancel.token.transfer_generation == timed_press.token.transfer_generation,
         "timeout cancellation is bound to the active transfer generation");
  finish(timeout, cancel, TransferResult::Cancelled,
         timeout_start + slt::kTransferTimeoutTicks);
  Action timeout_recovery = takeTransfer(timeout, TransferKind::Recovery);
  finish(timeout, timeout_recovery, TransferResult::Success,
         timeout_start + slt::kTransferTimeoutTicks + 1);
  expect(timeout.snapshot().fault == Fault::PressTimeout &&
             !timeout.snapshot().recovery_required,
         "successful recovery preserves timeout fault evidence");
}

void release_transfer_error_preserves_successful_press_tick() {
  PulseEngine engine;
  ready(engine);
  const uint32_t completion_tick = 100;
  expect(engine.acceptPulse(10, 0).status == Status::Ok,
         "pulse is accepted before release error test");
  Action press = takeTransfer(engine, TransferKind::Press);
  finish(engine, press, TransferResult::Success, completion_tick);
  const uint32_t release_deadline =
      completion_tick + 10 * kTicksPerMillisecond;
  engine.service(release_deadline);
  Action release = takeTransfer(engine, TransferKind::Release);
  finish(engine, release, TransferResult::Error, release_deadline + 1);

  const auto failed_release = engine.snapshot();
  expect(failed_release.state == PulseState::Recovery &&
             failed_release.fault == Fault::ReleaseTransferFailed &&
             failed_release.recovery_required && failed_release.tick_valid &&
             failed_release.completion_tick == completion_tick &&
             failed_release.sequence == 1,
         "release error faults and recovers while retaining successful press evidence");
  Action recovery = takeTransfer(engine, TransferKind::Recovery);
  expect(recovery.report == std::array<uint8_t, 6>{0, 0, 0, 0, 0, 0},
         "release error recovery sends only an unmeasured neutral report");
  finish(engine, recovery, TransferResult::Success, release_deadline + 2);
  const auto recovered = engine.snapshot();
  expect(recovered.state == PulseState::Fault &&
             recovered.fault == Fault::ReleaseTransferFailed &&
             !recovered.recovery_required && recovered.tick_valid &&
             recovered.completion_tick == completion_tick &&
             recovered.sequence == 1,
         "successful release recovery preserves the press tick and sequence");
}

void release_transfer_timeout_preserves_successful_press_tick() {
  PulseEngine engine;
  ready(engine);
  const uint32_t completion_tick = 200;
  expect(engine.acceptPulse(10, 0).status == Status::Ok,
         "pulse is accepted before release timeout test");
  Action press = takeTransfer(engine, TransferKind::Press);
  finish(engine, press, TransferResult::Success, completion_tick);
  const uint32_t release_deadline =
      completion_tick + 10 * kTicksPerMillisecond;
  engine.service(release_deadline);
  takeTransfer(engine, TransferKind::Release);
  const uint32_t release_timeout_tick =
      release_deadline + slt::kTransferTimeoutTicks;
  engine.service(release_timeout_tick);
  Action cancel = takeCancel(engine, TransferKind::Release);
  finish(engine, cancel, TransferResult::Cancelled, release_timeout_tick);

  const auto timed_out_release = engine.snapshot();
  expect(timed_out_release.state == PulseState::Recovery &&
             timed_out_release.fault == Fault::ReleaseTimeout &&
             timed_out_release.recovery_required && timed_out_release.tick_valid &&
             timed_out_release.completion_tick == completion_tick &&
             timed_out_release.sequence == 1,
         "release timeout faults and recovers while retaining successful press evidence");
  Action recovery = takeTransfer(engine, TransferKind::Recovery);
  expect(recovery.report == std::array<uint8_t, 6>{0, 0, 0, 0, 0, 0},
         "release timeout recovery sends only an unmeasured neutral report");
  finish(engine, recovery, TransferResult::Success, release_timeout_tick + 1);
  const auto recovered = engine.snapshot();
  expect(recovered.state == PulseState::Fault &&
             recovered.fault == Fault::ReleaseTimeout &&
             !recovered.recovery_required && recovered.tick_valid &&
             recovered.completion_tick == completion_tick &&
             recovered.sequence == 1,
         "successful timeout recovery preserves the press tick and sequence");
}

void info_is_available_across_pulse_and_fault_states() {
  PulseEngine engine;
  ready(engine);
  Protocol protocol(engine);
  uint32_t request_id = 1;
  auto read_info = [&](PulseState expected_state) {
    const std::vector<uint8_t> info_request = makeRequest(
        request_id++, slt::kEngineeringInfoCommand);
    std::array<uint8_t, 64> response{};
    size_t length = 0;
    const Status status = protocol.process(info_request.data(), info_request.size(),
                                           response.data(), response.size(), length);
    expect(status == Status::Ok, "engineering info is callable in the current state");
  expect(length == 52 && response[14] == 0 && response[15] == 0 &&
             response[24] == static_cast<uint8_t>(slt::UsbSpeed::Full) &&
             (response[25] & 0x07) == static_cast<uint8_t>(expected_state),
           "engineering info response has the fixed 32-byte snapshot payload");
  };

  read_info(PulseState::Idle);
  expect(engine.acceptPulse(10, 0).status == Status::Ok,
         "pulse enters queued state");
  const std::vector<uint8_t> busy_request = makePulseRequest(request_id++, 20);
  std::array<uint8_t, 64> response{};
  size_t length = 0;
  expect(protocol.process(busy_request.data(), busy_request.size(), response.data(),
                          response.size(), length) == Status::Busy,
         "a second press request reports busy while the first is queued");
  read_info(PulseState::PressQueued);
  Action press = takeTransfer(engine, TransferKind::Press);
  finish(engine, press, TransferResult::Success, 100);
  read_info(PulseState::Holding);
  engine.service(100 + 10 * kTicksPerMillisecond);
  read_info(PulseState::ReleaseQueued);
  Action release = takeTransfer(engine, TransferKind::Release);
  read_info(PulseState::ReleaseQueued);
  finish(engine, release, TransferResult::Success, 100 + 10 * kTicksPerMillisecond + 1);
  const std::vector<uint8_t> pulse_before_completion_poll =
      makePulseRequest(request_id++, 20);
  expect(protocol.process(pulse_before_completion_poll.data(),
                          pulse_before_completion_poll.size(), response.data(),
                          response.size(), length) == Status::Busy,
         "completed pulse remains non-idle until engineering info reports it");
  read_info(PulseState::Complete);

  expect(engine.acceptPulse(20, 0).status == Status::Ok,
         "new pulse is accepted after completion");
  read_info(PulseState::PressQueued);
  Action failed = takeTransfer(engine, TransferKind::Press);
  finish(engine, failed, TransferResult::Error, 1);
  read_info(PulseState::Recovery);
  Action recovery = takeTransfer(engine, TransferKind::Recovery);
  finish(engine, recovery, TransferResult::Error, 2);
  read_info(PulseState::Fault);
  expect(engine.snapshot().recovery_required,
         "failed recovery remains a pollable fault and blocks another pulse");

  engine.setAcquisitionState(true, false, 3);
  auto info_during_acquisition = makeRequest(
      request_id++, slt::kEngineeringInfoCommand);
  expect(protocol.process(info_during_acquisition.data(), info_during_acquisition.size(),
                          response.data(), response.size(), length) == Status::InvalidState,
         "engineering info is blocked during autonomous acquisition");
  auto press_during_acquisition = makePulseRequest(request_id++, 10);
  expect(protocol.process(press_during_acquisition.data(), press_during_acquisition.size(),
                          response.data(), response.size(), length) == Status::InvalidState,
         "engineering press is blocked during autonomous acquisition");

  engine.setAcquisitionState(false, true, 4);
  auto info_during_qualification = makeRequest(
      request_id++, slt::kEngineeringInfoCommand);
  expect(protocol.process(info_during_qualification.data(), info_during_qualification.size(),
                          response.data(), response.size(), length) == Status::InvalidState,
         "engineering info is blocked during qualification");
  auto press_during_qualification = makePulseRequest(request_id++, 10);
  expect(protocol.process(press_during_qualification.data(),
                          press_during_qualification.size(), response.data(),
                          response.size(), length) == Status::InvalidState,
         "engineering press is blocked during qualification");

  PulseEngine timeout;
  ready(timeout);
  Protocol timeout_protocol(timeout);
  uint32_t timeout_request_id = 1;
  auto read_timeout_info = [&](PulseState expected_state) {
    const auto request = makeRequest(timeout_request_id++, slt::kEngineeringInfoCommand);
    std::array<uint8_t, 64> output{};
    size_t output_size = 0;
    expect(timeout_protocol.process(request.data(), request.size(), output.data(),
                                    output.size(), output_size) == Status::Ok &&
               output_size == 52 &&
               output[24] == static_cast<uint8_t>(slt::UsbSpeed::Full) &&
               (output[25] & 0x07) == static_cast<uint8_t>(expected_state),
           "engineering info remains available through timeout and recovery");
  };
  expect(timeout.acceptPulse(10, 0).status == Status::Ok,
         "timeout pulse enters press queue");
  read_timeout_info(PulseState::PressQueued);
  Action timed_press = takeTransfer(timeout, TransferKind::Press);
  timeout.service(slt::kTransferTimeoutTicks);
  Action cancel = takeCancel(timeout, TransferKind::Press);
  read_timeout_info(PulseState::Fault);
  expect(cancel.token.transfer_generation == timed_press.token.transfer_generation,
         "timeout cancellation keeps the original transfer identity");
  finish(timeout, cancel, TransferResult::Cancelled, slt::kTransferTimeoutTicks);
  read_timeout_info(PulseState::Recovery);
  Action timeout_recovery = takeTransfer(timeout, TransferKind::Recovery);
  finish(timeout, timeout_recovery, TransferResult::Success,
         slt::kTransferTimeoutTicks + 1);
  const auto pulse_before_fault_info = makePulseRequest(timeout_request_id++, 10);
  expect(timeout_protocol.process(pulse_before_fault_info.data(),
                                  pulse_before_fault_info.size(), response.data(),
                                  response.size(), length) == Status::Busy,
         "recovered timeout fault remains non-idle until its info snapshot");
  read_timeout_info(PulseState::Fault);
  expect(timeout.snapshot().state == PulseState::Idle &&
             timeout.snapshot().sequence == 1 &&
             timeout.snapshot().fault == Fault::PressTimeout,
         "info reports and retires the recovered fault while preserving its sequence");
  const auto next_timeout_pulse = makePulseRequest(timeout_request_id++, 10);
  expect(timeout_protocol.process(next_timeout_pulse.data(), next_timeout_pulse.size(),
                                  response.data(), response.size(), length) == Status::Ok &&
             timeout.snapshot().sequence == 2,
         "another pulse is accepted after the recovered fault was reported");
}

void hid_slots_reuse_only_after_quiescence_and_ignore_stale_events() {
  slt::HidSlotPool<1> slots;
  uint8_t slot_index = 0;
  for (uint32_t generation = 1; generation <= 64; ++generation) {
    const slt::TransferToken cancelled{kSessionGeneration, generation,
                                       TransferKind::Press};
    expect(slots.allocate(cancelled, slot_index) && slot_index == 0,
           "available HID slot accepts a transfer identity");
    slt::TransferToken copied_token{};
    expect(slots.captureCompletionToken(slot_index, copied_token) &&
               copied_token.transfer_generation == generation,
           "USB callback copies the active transfer identity");
    if ((generation % 2) == 0) {
      slots.retireAll();
    } else {
      expect(slots.retire(cancelled), "canceled transfer retires its slot");
    }
    expect(slots.isRetired(slot_index) &&
               !slots.captureCompletionToken(slot_index, copied_token),
           "retired slot ignores callbacks until endpoint quiescence");
    const slt::TransferToken blocked{kSessionGeneration, generation + 1000,
                                     TransferKind::Release};
    expect(!slots.allocate(blocked, slot_index),
           "retired slot cannot be reused before endpoint quiescence");

    slots.reclaimRetiredAfterEndpointFlush();
    const slt::TransferToken next{kSessionGeneration, generation + 1000,
                                  TransferKind::Release};
    expect(slots.allocate(next, slot_index),
           "successful endpoint flush returns the slot to the pool");
    slots.release(slot_index, cancelled);
    expect(!slots.allocate(blocked, slot_index),
           "late event from the previous generation cannot free a reused slot");
    slots.release(slot_index, next);
  }
}

void wire_parser_rejects_invalid_framing_and_fixture_info() {
  PulseEngine engine;
  Protocol protocol(engine);
  std::array<uint8_t, 64> response{};
  size_t length = 0;
  auto request = makePulseRequest(1, 1, 10);
  expect(protocol.process(request.data(), request.size(), response.data(),
                          response.size(), length) == Status::NotReady,
         "pulse without a configured full-speed session is rejected");
  request[0] = 0;
  expect(protocol.process(request.data(), request.size(), response.data(),
                          response.size(), length) == Status::BadRequest,
         "wrong request magic is rejected");
  request = makeRequest(2, 0x0001);
  expect(protocol.process(request.data(), request.size(), response.data(),
                          response.size(), length) == Status::BadRequest,
         "production fixture-info command is not implemented by this slice");

  PulseEngine connected;
  ready(connected);
  Protocol strict_protocol(connected);
  auto valid_info = makeRequest(1, slt::kEngineeringInfoCommand);
  expect(strict_protocol.process(valid_info.data(), valid_info.size(), response.data(),
                                 response.size(), length) == Status::Ok &&
             length == 52,
         "valid engineering info request returns its fixed snapshot");
  expect(strict_protocol.process(valid_info.data(), valid_info.size(), response.data(),
                                 response.size(), length) == Status::Ok &&
             length == 52,
         "an exact duplicate information request replays its cached response");

  auto reserved_flags = makeRequest(2, slt::kEngineeringInfoCommand);
  reserved_flags[7] = 1;
  expect(strict_protocol.process(reserved_flags.data(), reserved_flags.size(),
                                 response.data(), response.size(), length) ==
             Status::BadRequest,
         "nonzero reserved flags are rejected");
  auto future_version = makeRequest(3, slt::kEngineeringInfoCommand);
  future_version[4] = 2;
  expect(strict_protocol.process(future_version.data(), future_version.size(),
                                 response.data(), response.size(), length) ==
             Status::UnsupportedVersion,
         "unsupported major protocol versions are reported");
  auto trailing_byte = makeRequest(4, slt::kEngineeringInfoCommand);
  trailing_byte.push_back(0);
  expect(strict_protocol.process(trailing_byte.data(), trailing_byte.size(),
                                 response.data(), response.size(), length) ==
             Status::BadRequest,
         "trailing bytes that disagree with the declared length are rejected");
  std::vector<uint8_t> oversized(slt::kMaximumEngineeringFrameSize + 1, 0);
  expect(strict_protocol.process(oversized.data(), oversized.size(), response.data(),
                                 response.size(), length) == Status::BadRequest,
         "frames larger than one USB endpoint packet are rejected");
}

void protocol_replays_lost_pulse_ack_without_a_second_execution() {
  PulseEngine engine;
  ready(engine);
  Protocol protocol(engine);
  std::array<uint8_t, 64> first_response{};
  std::array<uint8_t, 64> replayed_response{};
  size_t first_size = 0;
  size_t replayed_size = 0;
  const auto request = makePulseRequest(1, 0x11223344u, 10);

  expect(protocol.process(request.data(), request.size(), first_response.data(),
                          first_response.size(), first_size) == Status::Ok,
         "a valid pulse request is accepted once");
  Action press = takeTransfer(engine, TransferKind::Press);
  expect(protocol.process(request.data(), request.size(), replayed_response.data(),
                          replayed_response.size(), replayed_size) == Status::Ok,
         "an exact retry after a lost acknowledgement is replayed");
  expect(first_size == 32 && replayed_size == first_size &&
             std::equal(first_response.begin(), first_response.begin() + first_size,
                        replayed_response.begin()),
         "the cached pulse acknowledgement is byte-for-byte identical");
  expect(readU32(replayed_response.data(), 20) == 0x11223344u &&
             readU32(replayed_response.data(), 24) == 1 &&
             replayed_response[28] == static_cast<uint8_t>(PulseState::PressQueued) &&
             replayed_response[29] == 0 && replayed_response[30] == 0 &&
             replayed_response[31] == 0,
         "the 12-byte acknowledgement echoes its tag and accepted sequence");
  expect(engine.snapshot().sequence == 1 &&
             engine.snapshot().transfer_generation == press.token.transfer_generation,
         "retrying the request does not allocate another sequence or transfer");
  Action second_action{};
  expect(!engine.takeAction(second_action),
         "duplicate replay does not queue another HID press");

  const auto info_request = makeRequest(2, slt::kEngineeringInfoCommand);
  std::array<uint8_t, 64> blocked_response{};
  size_t blocked_size = 0;
  expect(protocol.process(info_request.data(), info_request.size(),
                          blocked_response.data(), blocked_response.size(),
                          blocked_size) == Status::Ok,
         "engineering info request is cached before autonomous mode");

  engine.setAcquisitionState(true, false, 3);
  blocked_size = 0;
  expect(protocol.process(request.data(), request.size(), blocked_response.data(),
                          blocked_response.size(), blocked_size) ==
             Status::InvalidState,
         "cached pulse acknowledgement is unavailable during acquisition");
  blocked_size = 0;
  expect(protocol.process(info_request.data(), info_request.size(),
                          blocked_response.data(), blocked_response.size(),
                          blocked_size) == Status::InvalidState,
         "cached engineering info is unavailable during acquisition");

  engine.setAcquisitionState(false, true, 4);
  blocked_size = 0;
  expect(protocol.process(request.data(), request.size(), blocked_response.data(),
                          blocked_response.size(), blocked_size) ==
             Status::InvalidState,
         "cached pulse acknowledgement is unavailable during qualification");
  blocked_size = 0;
  expect(protocol.process(info_request.data(), info_request.size(),
                          blocked_response.data(), blocked_response.size(),
                          blocked_size) == Status::InvalidState,
         "cached engineering info is unavailable during qualification");
}

void protocol_replays_lost_pulse_ack_across_usb_reset_recovery() {
  PulseEngine engine;
  ready(engine);
  Protocol protocol(engine);
  std::array<uint8_t, 64> first_response{};
  std::array<uint8_t, 64> replayed_response{};
  size_t first_size = 0;
  size_t replayed_size = 0;
  const auto request = makePulseRequest(1, 0x11223344u, 10);

  expect(protocol.process(request.data(), request.size(), first_response.data(),
                          first_response.size(), first_size) == Status::Ok,
         "a pulse request is accepted before a USB reset");
  expect(engine.snapshot().sequence == 1,
         "the accepted request allocates one HID sequence");

  engine.onUsbReset();
  engine.observeUsbState(true, true, true, 0, 100);
  expect(engine.snapshot().state == PulseState::Recovery,
         "USB reset recovery queues a neutral report in the reconfigured session");

  expect(protocol.process(request.data(), request.size(), replayed_response.data(),
                          replayed_response.size(), replayed_size) == Status::Ok,
         "a lost acknowledgement retry replays across reset recovery");
  expect(first_size == replayed_size &&
             std::equal(first_response.begin(), first_response.begin() + first_size,
                        replayed_response.begin()),
         "reset recovery preserves the exact cached acknowledgement");
  expect(engine.snapshot().sequence == 1,
         "retrying after reset recovery does not allocate another sequence");
  takeTransfer(engine, TransferKind::Recovery);
  Action second_action{};
  expect(!engine.takeAction(second_action),
         "replay after reset recovery does not queue a second pulse");
}

void protocol_rejects_changed_cached_payload_and_evicted_stale_ids() {
  PulseEngine engine;
  ready(engine);
  Protocol protocol(engine);
  std::array<uint8_t, 64> response{};
  size_t response_size = 0;
  const auto original = makePulseRequest(1, 0x11223344u, 10);
  expect(protocol.process(original.data(), original.size(), response.data(),
                          response.size(), response_size) == Status::Ok,
         "the original request ID is accepted");
  const auto changed = makePulseRequest(1, 0x11223344u, 11);
  expect(protocol.process(changed.data(), changed.size(), response.data(),
                          response.size(), response_size) == Status::BadRequest &&
             engine.snapshot().sequence == 1,
         "reusing a cached ID with different request bytes is rejected without execution");

  PulseEngine info_engine;
  ready(info_engine);
  Protocol info_protocol(info_engine);
  const auto first_info = makeRequest(1, slt::kEngineeringInfoCommand);
  for (uint32_t request_id = 1; request_id <= 5; ++request_id) {
    const auto info = makeRequest(request_id, slt::kEngineeringInfoCommand);
    expect(info_protocol.process(info.data(), info.size(), response.data(),
                                 response.size(), response_size) == Status::Ok,
           "new monotonic information request IDs are accepted");
  }
  expect(info_protocol.process(first_info.data(), first_info.size(), response.data(),
                               response.size(), response_size) == Status::BadRequest,
         "a request ID older than the replay cache is rejected as stale");
}

void sequence_exhaustion_never_wraps_to_zero() {
  PulseEngine engine;
  ready(engine);
  engine.setSequenceForTesting(UINT32_MAX);

  const auto result = engine.acceptPulse(10, 0);

  expect(result.status == Status::IntegrityFault && result.sequence == UINT32_MAX &&
             engine.snapshot().sequence == UINT32_MAX,
         "an exhausted HID sequence fails closed without wrapping");
  Action action{};
  expect(!engine.takeAction(action), "sequence exhaustion queues no HID transfer");
}

void full_speed_reverification_restarts_required_recovery() {
  PulseEngine engine;
  ready(engine);
  expect(engine.acceptPulse(10, 0).status == Status::Ok,
         "pulse can establish a pending recovery condition");
  Action press = takeTransfer(engine, TransferKind::Press);
  finish(engine, press, TransferResult::Error, 1);
  Action failed_recovery = takeTransfer(engine, TransferKind::Recovery);
  finish(engine, failed_recovery, TransferResult::Error, 2);
  expect(engine.snapshot().recovery_required,
         "failed neutral recovery remains required");

  engine.observeUsbState(true, true, false, 0, 3);
  engine.observeUsbState(true, true, false, 0, 4);
  expect(engine.snapshot().session_valid &&
             !engine.snapshot().full_speed_verified &&
             engine.snapshot().recovery_required,
         "configuration without PFSC does not authorize recovery");

  engine.observeUsbState(true, true, true, 0, 5);
  expect(engine.snapshot().full_speed_verified &&
             engine.snapshot().state == PulseState::Recovery,
         "PFSC re-verification restarts pending safe recovery");
  takeTransfer(engine, TransferKind::Recovery);
}

void reconnect_starts_a_new_request_id_epoch_without_replaying_old_ack() {
  PulseEngine engine;
  ready(engine);
  Protocol protocol(engine);
  std::array<uint8_t, 64> response{};
  size_t response_size = 0;
  const auto request = makePulseRequest(1, 0x11223344u, 10);
  expect(protocol.process(request.data(), request.size(), response.data(),
                          response.size(), response_size) == Status::Ok &&
             readU32(response.data(), 24) == 1,
         "the first session accepts request ID one as HID sequence one");
  Action press = takeTransfer(engine, TransferKind::Press);
  finish(engine, press, TransferResult::Success, 1000);
  engine.service(1000 + 10 * kTicksPerMillisecond);
  Action release = takeTransfer(engine, TransferKind::Release);
  finish(engine, release, TransferResult::Success,
         1000 + 10 * kTicksPerMillisecond + 1);
  const auto info = makeRequest(2, slt::kEngineeringInfoCommand);
  expect(protocol.process(info.data(), info.size(), response.data(), response.size(),
                          response_size) == Status::Ok &&
             engine.snapshot().state == PulseState::Idle,
         "the terminal snapshot is reported before reconnect");

  const uint32_t previous_session_generation = engine.snapshot().session_generation;
  engine.onUsbReset();
  protocol.resetSession();
  engine.observeUsbState(true, true, true, 0, 0);
  expect(engine.snapshot().session_generation > previous_session_generation,
         "re-enumeration changes the USB-session generation");
  Action recovery = takeTransfer(engine, TransferKind::Recovery);
  finish(engine, recovery, TransferResult::Success, 2000);
  const auto new_session_info = makeRequest(1, slt::kEngineeringInfoCommand);
  expect(protocol.process(new_session_info.data(), new_session_info.size(),
                          response.data(), response.size(), response_size) == Status::Ok &&
             response_size == 52 && response[12] == 0x01 &&
             response[13] == 0xE0,
         "the new USB session reuses request ID one without replaying the old pulse acknowledgement");
  const auto next_session_pulse = makePulseRequest(2, 0x55667788u, 10);
  expect(protocol.process(next_session_pulse.data(), next_session_pulse.size(),
                          response.data(), response.size(), response_size) == Status::Ok &&
             readU32(response.data(), 24) == 2,
         "reconnected pulse preserves the monotonically increasing HID sequence");
}

void full_speed_is_verified_from_pspd_and_not_the_legacy_flag() {
  PulseEngine engine;
  engine.observeUsbState(true, true, true, 1, 0);
  expect(!engine.snapshot().full_speed_verified,
         "non-Full-Speed PSPD fails closed");
  expect(engine.acceptPulse(10, 0).status == Status::NotReady,
         "non-Full-Speed session cannot accept a pulse");
  engine.observeUsbState(true, true, false, 0, 1);
  expect(!engine.snapshot().full_speed_verified,
         "PSPD Full-Speed without PFSC assertion fails closed");
  engine.observeUsbState(false, true, false, 0, 2);
  expect(!engine.snapshot().session_valid,
         "unconnected device is not a valid USB session");
}

void hid_transfer_status_requires_exact_report_completion() {
  expect(slt::transferResultFromStatus(0) == TransferResult::Success,
         "completed transfer without error flags is successful");
  expect(slt::transferResultFromStatus(1u << 16) == TransferResult::Success,
         "variable-length vendor transfers may finish with bytes remaining");
  expect(slt::hidTransferResult(0, 6) == TransferResult::Success,
         "a complete six-byte HID report is successful");
  expect(slt::hidTransferResult(1u << 16, 6) == TransferResult::Error,
         "a short HID report cannot count as a measured press");
  expect(slt::hidTransferResult(7u << 16, 6) == TransferResult::Error,
         "an impossible HID residual count fails closed");
  expect(slt::hidTransferResult(1u << 7, 6) == TransferResult::Error,
         "an active HID transfer is not complete");
  expect(slt::hidTransferResult(1u << 3, 6) == TransferResult::Error,
         "an errored HID transfer fails closed");
  expect(slt::hidTransferResult(0, 0) == TransferResult::Error,
         "zero-length HID transfers cannot satisfy the report contract");
}

void reset_between_state_processing_and_hid_dispatch_blocks_submission() {
  PulseEngine engine;
  ready(engine);
  expect(engine.acceptPulse(25, 0).status == Status::Ok,
         "measured press is queued before the reset race");
  Action pending_press = takeTransfer(engine, TransferKind::Press);
  expect(pending_press.token.kind == TransferKind::Press,
         "the pending endpoint action is a measured press");

  uint32_t processed_lifecycle_generation = 7;
  uint32_t lifecycle_generation = processed_lifecycle_generation;
  expect(slt::maySubmitHidTransfer(processed_lifecycle_generation,
                                   lifecycle_generation, true),
         "a current configured lifecycle permits HID submission");

  ++lifecycle_generation;
  expect(!slt::maySubmitHidTransfer(processed_lifecycle_generation,
                                   lifecycle_generation, true),
         "a changed lifecycle generation blocks a pending HID submission");
  const bool usb_configured = false;
  expect(!slt::maySubmitHidTransfer(processed_lifecycle_generation,
                                    lifecycle_generation, usb_configured),
         "a reset after state processing blocks the pending HID submission");
  expect(!slt::maySubmitHidTransfer(lifecycle_generation,
                                    lifecycle_generation, false),
         "an unconfigured USB device cannot submit a HID action");
  expect(engine.snapshot().state == PulseState::PressQueued &&
             !engine.snapshot().tick_valid,
         "a blocked submission cannot establish measured press completion");
}

void short_vendor_request_rearms_before_the_next_valid_request() {
  PulseEngine engine;
  ready(engine);
  Protocol protocol(engine);
  std::array<uint8_t, 64> response{};
  size_t response_size = 0;
  const std::array<uint8_t, 4> malformed_request{{'S', 'L', 'T', '1'}};
  bool vendor_out_armed = true;
  const auto receive_request = [&](const uint8_t* request,
                                   size_t request_size) {
    expect(vendor_out_armed, "vendor OUT must be armed before receiving data");
    vendor_out_armed = false;
    const Status status = protocol.process(request, request_size, response.data(),
                                           response.size(), response_size);
    if (slt::shouldRearmVendorOut(response_size)) vendor_out_armed = true;
    return status;
  };

  expect(receive_request(malformed_request.data(), malformed_request.size()) ==
             Status::BadRequest,
         "a short vendor request is rejected");
  expect(response_size == 0, "a short vendor request produces no response");
  expect(vendor_out_armed,
         "a zero-response request rearms vendor OUT immediately");

  const auto valid_request = makeRequest(1, slt::kEngineeringInfoCommand);
  response_size = 0;
  expect(receive_request(valid_request.data(), valid_request.size()) ==
             Status::Ok &&
             response_size == 52,
         "a valid request succeeds after the malformed request was rearmed");
}

}  // namespace

int main() {
  usb_serial_uses_sixteen_uppercase_mac_hex_digits();
  common_vectors_match();
  duration_boundaries_and_delayed_press_completion();
  busy_request_does_not_mutate_pulse();
  callback_reordering_and_stale_generation_are_ignored();
  cancellation_reset_and_disconnect_invalidate_evidence();
  press_failure_timeout_and_failed_recovery_are_fail_closed();
  release_transfer_error_preserves_successful_press_tick();
  release_transfer_timeout_preserves_successful_press_tick();
  info_is_available_across_pulse_and_fault_states();
  hid_slots_reuse_only_after_quiescence_and_ignore_stale_events();
  wire_parser_rejects_invalid_framing_and_fixture_info();
  protocol_replays_lost_pulse_ack_without_a_second_execution();
 protocol_replays_lost_pulse_ack_across_usb_reset_recovery();
  protocol_rejects_changed_cached_payload_and_evicted_stale_ids();
  sequence_exhaustion_never_wraps_to_zero();
 full_speed_reverification_restarts_required_recovery();
  reconnect_starts_a_new_request_id_epoch_without_replaying_old_ack();
  full_speed_is_verified_from_pspd_and_not_the_legacy_flag();
  hid_transfer_status_requires_exact_report_completion();
  reset_between_state_processing_and_hid_dispatch_blocks_submission();
  short_vendor_request_rearms_before_the_next_valid_request();
  std::cout << "firmware host tests passed\n";
}
