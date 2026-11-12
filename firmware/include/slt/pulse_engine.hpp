#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace slt {

constexpr uint32_t kCompletionCounterHz = 600000000u;
constexpr uint32_t kTicksPerMillisecond = kCompletionCounterHz / 1000u;
constexpr uint32_t kTransferTimeoutTicks = kCompletionCounterHz / 2u;

enum class Status : uint16_t {
  Ok = 0,
  UnsupportedVersion = 1,
  BadRequest = 2,
  InvalidState = 3,
  NotReady = 4,
  HardwareFault = 5,
  IntegrityFault = 6,
  Busy = 7,
  RangeError = 8,
};

enum class PulseState : uint8_t {
  Idle = 0,
  PressQueued = 1,
  Holding = 2,
  ReleaseQueued = 3,
  Complete = 4,
  Recovery = 5,
  Fault = 6,
};

enum class UsbSpeed : uint8_t {
  Unknown = 0,
  Low = 1,
  Full = 2,
  High = 3,
};

enum class Fault : uint16_t {
  None = 0,
  PressTransferFailed = 1,
  PressTimeout = 2,
  SessionInvalidated = 3,
  ReleaseTransferFailed = 4,
  ReleaseTimeout = 5,
  RecoveryFailed = 6,
  RecoveryTimeout = 7,
};

enum class TransferKind : uint8_t { Press, Release, Recovery };
enum class TransferResult : uint8_t { Success, Error, Cancelled };
enum class ActionKind : uint8_t { Transfer, Cancel };

struct TransferToken {
  uint32_t session_generation = 0;
  uint32_t transfer_generation = 0;
  TransferKind kind = TransferKind::Press;
};

struct Action {
  ActionKind kind = ActionKind::Transfer;
  TransferKind transfer_kind = TransferKind::Press;
  TransferToken token{};
  std::array<uint8_t, 6> report{};
};

struct PulseResult {
  Status status = Status::BadRequest;
  uint32_t sequence = 0;
  PulseState state = PulseState::Idle;
};

struct Snapshot {
  uint32_t sequence = 0;
  PulseState state = PulseState::Idle;
  UsbSpeed usb_speed = UsbSpeed::Unknown;
  bool tick_valid = false;
  bool full_speed_verified = false;
  bool session_valid = false;
  bool recovery_required = false;
  uint16_t duration_ms = 0;
  uint32_t session_generation = 0;
  uint32_t transfer_generation = 0;
  uint32_t completion_tick = 0;
  bool capture_t0_valid = false;
  uint64_t capture_t0_tick = 0;
  Status last_command_status = Status::Ok;
  Fault fault = Fault::None;
  uint32_t completion_counter_hz = kCompletionCounterHz;
};

// Pulse state changes on the main loop. USB callbacks enqueue transfer results,
// and only a matching successful press completion records its diagnostic tick.
class PulseEngine {
 public:
  void observeUsbState(bool attached, bool configured, bool pfsc_asserted,
                       uint8_t pspd, uint32_t now_tick);
  void onUsbReset();
  void onDisconnect();
  void setAcquisitionState(bool acquiring, bool qualifying, uint32_t now_tick);

  PulseResult acceptPulse(uint16_t duration_ms, uint32_t now_tick);
  void service(uint32_t now_tick);
  void onTransferComplete(const TransferToken& token, TransferResult result,
                         uint32_t completion_tick);
  void onTransferComplete(const TransferToken& token, TransferResult result,
                         uint32_t completion_tick, bool capture_t0_valid,
                         uint64_t capture_t0_tick);

  bool takeAction(Action& action);
  Snapshot snapshot() const;
  Snapshot snapshotForEngineeringInfo();
  bool isBusy() const;
  bool engineeringAvailable() const;
  void setLastCommandStatus(Status status);
#if defined(SLT_HOST_TEST)
  void setSequenceForTesting(uint32_t sequence) { state_.sequence = sequence; }
#endif

 private:
  bool sameToken(const TransferToken& left, const TransferToken& right) const;
  bool nextTransferToken(TransferKind kind, TransferToken& token);
  bool queueTransfer(TransferKind kind, const std::array<uint8_t, 6>& report);
  void queueCancel(const TransferToken& token);
  void startRecovery(uint32_t now_tick);
  void invalidateSession();
  void failCurrentTransfer(Fault fault, bool invalidate_tick);
  bool sessionCanRecover() const;
  bool action_pending_ = false;
  bool attached_ = false;
  bool configured_ = false;
  bool acquiring_ = false;
  bool qualifying_ = false;
  bool active_transfer_ = false;
  bool cancel_pending_ = false;
  bool exhausted_generation_ = false;
  Snapshot state_{};
  TransferToken active_token_{};
  Action pending_action_{};
  uint32_t transfer_started_tick_ = 0;
  uint32_t hold_started_tick_ = 0;
};

}  // namespace slt
