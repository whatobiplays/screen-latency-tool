#include "slt/pulse_engine.hpp"

namespace slt {
namespace {

constexpr std::array<uint8_t, 6> kNeutralReport{{0, 0, 0, 0, 0, 0}};
constexpr std::array<uint8_t, 6> kButtonOneReport{{1, 0, 0, 0, 0, 0}};

bool intervalElapsed(uint32_t now, uint32_t then, uint32_t interval) {
  const uint32_t delta = now - then;
  return delta >= interval && delta < 0x80000000u;
}

}  // namespace

void PulseEngine::observeUsbState(bool attached, bool configured,
                                  bool pfsc_asserted, uint8_t pspd,
                                  uint32_t now_tick) {
  const bool was_valid = state_.session_valid;
  const bool was_full_speed = state_.full_speed_verified;
  attached_ = attached;
  configured_ = configured;
  if (!attached || !configured) {
    state_.usb_speed = UsbSpeed::Unknown;
  } else if (pspd == 0) {
    state_.usb_speed = UsbSpeed::Full;
  } else if (pspd == 1) {
    state_.usb_speed = UsbSpeed::Low;
  } else if (pspd == 2) {
    state_.usb_speed = UsbSpeed::High;
  } else {
    state_.usb_speed = UsbSpeed::Unknown;
  }
  const bool now_valid = attached && configured;

  if (was_valid && !now_valid) {
    invalidateSession();
  } else if (!was_valid && now_valid) {
    if (state_.session_generation == UINT32_MAX) {
      exhausted_generation_ = true;
      state_.fault = Fault::SessionInvalidated;
      state_.state = PulseState::Fault;
      state_.session_valid = false;
      state_.full_speed_verified = false;
      return;
    }
    ++state_.session_generation;
    if (state_.transfer_generation == 0) state_.transfer_generation = 1;
    state_.session_valid = true;
  }

  state_.session_valid = now_valid && !exhausted_generation_;
  state_.full_speed_verified = state_.session_valid && pfsc_asserted &&
                               state_.usb_speed == UsbSpeed::Full;

  const bool session_just_became_valid = !was_valid && state_.session_valid;
  const bool full_speed_just_verified =
      !was_full_speed && state_.full_speed_verified;
  if ((session_just_became_valid || full_speed_just_verified) &&
      state_.recovery_required && sessionCanRecover()) {
    startRecovery(now_tick);
  }

  // Any loss of the verified speed condition invalidates an in-flight pulse.
  // Do not attempt an unmeasured report unless the new session is still known
  // to be configured at Full Speed.
  if (was_valid && was_full_speed && !state_.full_speed_verified) {
    invalidateSession();
  }
}

void PulseEngine::onUsbReset() {
  configured_ = false;
  state_.usb_speed = UsbSpeed::Unknown;
  invalidateSession();
}

void PulseEngine::onDisconnect() {
  attached_ = false;
  configured_ = false;
  state_.usb_speed = UsbSpeed::Unknown;
  invalidateSession();
}

void PulseEngine::setAcquisitionState(bool acquiring, bool qualifying,
                                      uint32_t now_tick) {
  const bool was_available = engineeringAvailable();
  acquiring_ = acquiring;
  qualifying_ = qualifying;
  if (was_available && !engineeringAvailable() && isBusy()) {
    failCurrentTransfer(Fault::SessionInvalidated, true);
    state_.recovery_required = true;
    if (action_pending_ && pending_action_.kind == ActionKind::Transfer) {
      action_pending_ = false;
      active_transfer_ = false;
    } else if (active_transfer_) {
      cancel_pending_ = true;
      queueCancel(active_token_);
    }
  } else if (!was_available && engineeringAvailable() &&
             state_.recovery_required && sessionCanRecover()) {
    startRecovery(now_tick);
  }
}

PulseResult PulseEngine::acceptPulse(uint16_t duration_ms, uint32_t now_tick) {
  PulseResult result{};
  result.sequence = state_.sequence;
  result.state = state_.state;

  if (acquiring_ || qualifying_) {
    result.status = Status::InvalidState;
    return result;
  }
  if (state_.state != PulseState::Idle || isBusy() || action_pending_) {
    result.status = Status::Busy;
    return result;
  }
  if (exhausted_generation_ || !state_.session_valid ||
      !state_.full_speed_verified) {
    result.status = Status::NotReady;
    return result;
  }
  if (duration_ms < 10 || duration_ms > 1000) {
    result.status = Status::RangeError;
    return result;
  }
  if (state_.sequence == UINT32_MAX) {
    result.status = Status::IntegrityFault;
    return result;
  }

  ++state_.sequence;
  state_.duration_ms = duration_ms;
  state_.tick_valid = false;
  state_.completion_tick = 0;
  state_.capture_t0_valid = false;
  state_.capture_t0_tick = 0;
  state_.fault = Fault::None;
  state_.recovery_required = false;
  state_.state = PulseState::PressQueued;
  transfer_started_tick_ = now_tick;
  if (!queueTransfer(TransferKind::Press, kButtonOneReport)) {
    state_.state = PulseState::Fault;
    state_.fault = Fault::SessionInvalidated;
    state_.recovery_required = true;
    result.status = Status::IntegrityFault;
    result.state = state_.state;
    return result;
  }

  result.status = Status::Ok;
  result.sequence = state_.sequence;
  result.state = state_.state;
  return result;
}

void PulseEngine::service(uint32_t now_tick) {
  if (state_.state == PulseState::Holding &&
      intervalElapsed(now_tick, hold_started_tick_,
                      static_cast<uint32_t>(state_.duration_ms) *
                          kTicksPerMillisecond)) {
    state_.state = PulseState::ReleaseQueued;
    transfer_started_tick_ = now_tick;
    if (!queueTransfer(TransferKind::Release, kNeutralReport)) {
      state_.state = PulseState::Fault;
      state_.fault = Fault::SessionInvalidated;
      state_.recovery_required = true;
    }
    return;
  }

  if (!active_transfer_ || cancel_pending_ ||
      !intervalElapsed(now_tick, transfer_started_tick_, kTransferTimeoutTicks)) {
    return;
  }

  Fault timeout_fault = Fault::PressTimeout;
  bool invalidate_tick = true;
  if (active_token_.kind == TransferKind::Release) {
    timeout_fault = Fault::ReleaseTimeout;
    invalidate_tick = false;
  } else if (active_token_.kind == TransferKind::Recovery) {
    timeout_fault = Fault::RecoveryTimeout;
    invalidate_tick = false;
  }
  failCurrentTransfer(timeout_fault, invalidate_tick);
  state_.recovery_required = true;

  if (action_pending_ && pending_action_.kind == ActionKind::Transfer &&
      sameToken(pending_action_.token, active_token_)) {
    action_pending_ = false;
    pending_action_ = Action{};
    active_transfer_ = false;
    if (active_token_.kind == TransferKind::Recovery) {
      state_.state = PulseState::Fault;
      state_.fault = Fault::RecoveryTimeout;
    } else if (sessionCanRecover()) {
      startRecovery(now_tick);
    }
    return;
  }

  cancel_pending_ = true;
  queueCancel(active_token_);
}

void PulseEngine::onTransferComplete(const TransferToken& token,
                                     TransferResult result,
                                     uint32_t completion_tick) {
  onTransferComplete(token, result, completion_tick, false, 0);
}

void PulseEngine::onTransferComplete(const TransferToken& token,
                                     TransferResult result,
                                     uint32_t completion_tick,
                                     bool capture_t0_valid,
                                     uint64_t capture_t0_tick) {
  if (!sameToken(token, active_token_)) return;

  if (cancel_pending_) {
    if (result != TransferResult::Cancelled) return;
    cancel_pending_ = false;
    active_transfer_ = false;
    if (token.kind == TransferKind::Recovery) {
      state_.fault = Fault::RecoveryTimeout;
      state_.state = PulseState::Fault;
      state_.recovery_required = true;
    } else if (sessionCanRecover()) {
      startRecovery(completion_tick);
    }
    return;
  }

  if (!active_transfer_) return;
  active_transfer_ = false;

  if (token.session_generation != state_.session_generation ||
      !state_.session_valid || !state_.full_speed_verified) {
    return;
  }

  if (token.kind == TransferKind::Press) {
    if (state_.state != PulseState::PressQueued) return;
    if (result != TransferResult::Success) {
      failCurrentTransfer(Fault::PressTransferFailed, true);
      state_.recovery_required = true;
      startRecovery(completion_tick);
      return;
    }
    state_.tick_valid = true;
    state_.completion_tick = completion_tick;
    if (capture_t0_valid) {
      state_.capture_t0_valid = true;
      state_.capture_t0_tick = capture_t0_tick;
    }
    hold_started_tick_ = completion_tick;
    state_.state = PulseState::Holding;
    return;
  }

  if (token.kind == TransferKind::Release) {
    if (state_.state != PulseState::ReleaseQueued) return;
    if (result == TransferResult::Success) {
      state_.state = PulseState::Complete;
    } else {
      failCurrentTransfer(Fault::ReleaseTransferFailed, false);
      state_.recovery_required = true;
      startRecovery(completion_tick);
    }
    return;
  }

  if (token.kind == TransferKind::Recovery) {
    if (state_.state != PulseState::Recovery) return;
    if (result == TransferResult::Success) {
      state_.recovery_required = false;
      state_.state = PulseState::Fault;
    } else {
      state_.fault = Fault::RecoveryFailed;
      state_.recovery_required = true;
      state_.state = PulseState::Fault;
    }
  }
}

bool PulseEngine::takeAction(Action& action) {
  if (!action_pending_) return false;
  action = pending_action_;
  action_pending_ = false;
  return true;
}

Snapshot PulseEngine::snapshot() const { return state_; }

Snapshot PulseEngine::snapshotForEngineeringInfo() {
  const Snapshot reported = state_;
  const bool terminal = reported.state == PulseState::Complete ||
                        reported.state == PulseState::Fault;
  if (terminal && !isBusy() && !action_pending_) {
    state_.state = PulseState::Idle;
  }
  return reported;
}

bool PulseEngine::isBusy() const {
  return active_transfer_ || cancel_pending_ || state_.recovery_required ||
         state_.state == PulseState::PressQueued ||
         state_.state == PulseState::Holding ||
         state_.state == PulseState::ReleaseQueued ||
         state_.state == PulseState::Recovery;
}

bool PulseEngine::engineeringAvailable() const {
  return !acquiring_ && !qualifying_;
}

void PulseEngine::setLastCommandStatus(Status status) {
  state_.last_command_status = status;
}

bool PulseEngine::sameToken(const TransferToken& left,
                            const TransferToken& right) const {
  return left.session_generation == right.session_generation &&
         left.transfer_generation == right.transfer_generation &&
         left.kind == right.kind;
}

bool PulseEngine::nextTransferToken(TransferKind kind, TransferToken& token) {
  if (state_.transfer_generation == UINT32_MAX) {
    exhausted_generation_ = true;
    state_.fault = Fault::SessionInvalidated;
    state_.state = PulseState::Fault;
    return false;
  }
  ++state_.transfer_generation;
  token.session_generation = state_.session_generation;
  token.transfer_generation = state_.transfer_generation;
  token.kind = kind;
  return true;
}

bool PulseEngine::queueTransfer(TransferKind kind,
                                const std::array<uint8_t, 6>& report) {
  if (action_pending_) return false;
  TransferToken token{};
  if (!nextTransferToken(kind, token)) return false;
  pending_action_ = Action{};
  pending_action_.kind = ActionKind::Transfer;
  pending_action_.transfer_kind = kind;
  pending_action_.token = token;
  pending_action_.report = report;
  action_pending_ = true;
  active_token_ = token;
  active_transfer_ = true;
  cancel_pending_ = false;
  return true;
}

void PulseEngine::queueCancel(const TransferToken& token) {
  if (action_pending_) return;
  pending_action_ = Action{};
  pending_action_.kind = ActionKind::Cancel;
  pending_action_.transfer_kind = token.kind;
  pending_action_.token = token;
  action_pending_ = true;
}

void PulseEngine::startRecovery(uint32_t now_tick) {
  if (!sessionCanRecover()) {
    active_transfer_ = false;
    cancel_pending_ = false;
    state_.recovery_required = true;
    state_.state = PulseState::Fault;
    return;
  }
  if (action_pending_) return;
  state_.recovery_required = true;
  state_.state = PulseState::Recovery;
  transfer_started_tick_ = now_tick;
  if (!queueTransfer(TransferKind::Recovery, kNeutralReport)) {
    state_.state = PulseState::Fault;
    state_.fault = Fault::RecoveryFailed;
    state_.recovery_required = true;
  }
}

void PulseEngine::invalidateSession() {
  if (state_.session_generation == UINT32_MAX) {
    exhausted_generation_ = true;
  } else {
    ++state_.session_generation;
  }
  state_.session_valid = false;
  state_.full_speed_verified = false;
  state_.tick_valid = false;
  state_.completion_tick = 0;
  state_.capture_t0_valid = false;
  state_.capture_t0_tick = 0;
  state_.fault = Fault::SessionInvalidated;
  state_.state = PulseState::Fault;
  state_.recovery_required = true;
  active_transfer_ = false;
  cancel_pending_ = false;
  action_pending_ = false;
  pending_action_ = Action{};
}

void PulseEngine::failCurrentTransfer(Fault fault, bool invalidate_tick) {
  state_.fault = fault;
  state_.state = PulseState::Fault;
  if (invalidate_tick) {
    state_.tick_valid = false;
    state_.completion_tick = 0;
    state_.capture_t0_valid = false;
    state_.capture_t0_tick = 0;
  }
}

bool PulseEngine::sessionCanRecover() const {
  return state_.session_valid && state_.full_speed_verified && !acquiring_ &&
         !qualifying_;
}

}  // namespace slt
