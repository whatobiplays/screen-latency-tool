#pragma once

#include <cstddef>
#include <cstdint>

namespace slt {

// A queued USB transfer is valid only after the main loop processes every USB
// lifecycle change and confirms that the device remains configured.
constexpr bool maySubmitUsbTransfer(uint32_t processed_lifecycle_generation,
                                    uint32_t current_lifecycle_generation,
                                    bool usb_configured) noexcept {
  return usb_configured &&
         processed_lifecycle_generation == current_lifecycle_generation;
}

constexpr bool isCurrentUsbLifecycleEvent(uint32_t event_generation,
                                          uint32_t current_generation) noexcept {
  return event_generation == current_generation;
}

constexpr bool mayAcceptHidCompletion(uint32_t token_session_generation,
                                      uint32_t token_transfer_generation,
                                      uint32_t current_session_generation,
                                      uint32_t current_transfer_generation,
                                      bool session_valid,
                                      bool full_speed_verified) noexcept {
  return session_valid && full_speed_verified &&
         token_session_generation == current_session_generation &&
         token_transfer_generation == current_transfer_generation;
}

// A zero-length response has no vendor IN completion that can rearm OUT.
constexpr bool shouldRearmVendorOut(size_t response_size) noexcept {
  return response_size == 0;
}

// The replay epoch ends only after the USB port reports CCS=0.
constexpr bool shouldResetProtocolSession(bool ccs_asserted) noexcept {
  return !ccs_asserted;
}

enum class VendorEndpoint : uint8_t { Out, In };

struct VendorTransferIdentity {
  uint32_t session_generation = 0;
  uint32_t transfer_generation = 0;
};

// Keep endpoint buffers and descriptors unavailable until every flush succeeds.
// A failed cancellation can run again after Full-Speed verifies or a USB reset.
class UsbEndpointLifecycle {
 public:
  void observeUsbState(bool attached, bool configured,
                       bool full_speed_verified) noexcept {
    const bool was_ready = full_speed_ready_;
    full_speed_ready_ = attached && configured && full_speed_verified;
    if (!has_observed_state_) {
      has_observed_state_ = true;
      return;
    }
    if (was_ready && !full_speed_ready_) {
      requestCancellation();
    } else if (!was_ready && full_speed_ready_ &&
               reconfiguration_blocked_) {
      requestCancellation();
    }
  }

  void onUsbReset() noexcept {
    const bool needs_flush = endpoints_configured_ || reconfiguration_blocked_ ||
                             vendor_out_pending_ || vendor_in_pending_;
    full_speed_ready_ = false;
    has_observed_state_ = true;
    endpoints_configured_ = false;
    if (needs_flush) requestCancellation();
  }

  void requestCancellation() noexcept {
    if (cancellation_in_progress_) {
      cancellation_requested_ = true;
      reconfiguration_blocked_ = true;
      endpoints_configured_ = false;
      return;
    }
    if (cancellation_requested_) return;
    cancellation_requested_ = true;
    reconfiguration_blocked_ = true;
    endpoints_configured_ = false;
  }

  bool takeCancellationRequest() noexcept {
    if (!cancellation_requested_ || cancellation_in_progress_) return false;
    cancellation_requested_ = false;
    cancellation_in_progress_ = true;
    return true;
  }

  void finishCancellation(bool hid_flushed, bool vendor_out_flushed,
                          bool vendor_in_flushed) noexcept {
    if (!cancellation_in_progress_) return;
    cancellation_in_progress_ = false;
    if (vendor_out_flushed) vendor_out_pending_ = false;
    if (vendor_in_flushed) vendor_in_pending_ = false;
    reconfiguration_blocked_ =
        cancellation_requested_ || !hid_flushed || !vendor_out_flushed ||
        !vendor_in_flushed;
  }

  bool shouldConfigureEndpoints() const noexcept {
    return full_speed_ready_ && !reconfiguration_blocked_ &&
           !cancellation_requested_ && !cancellation_in_progress_ &&
           !endpoints_configured_ && !vendor_out_pending_ &&
           !vendor_in_pending_ && !transfer_identity_exhausted_;
  }

  bool markEndpointsConfigured() noexcept {
    if (!shouldConfigureEndpoints()) return false;
    endpoints_configured_ = true;
    return true;
  }

  bool endpointsConfigured() const noexcept { return endpoints_configured_; }
  bool reconfigurationBlocked() const noexcept {
    return reconfiguration_blocked_;
  }

  bool beginVendorTransfer(VendorEndpoint endpoint, uint32_t session_generation,
                           VendorTransferIdentity& identity) noexcept {
    if (!full_speed_ready_ || !endpoints_configured_ ||
        reconfiguration_blocked_ || cancellation_requested_ ||
        cancellation_in_progress_ || session_generation == 0 ||
        vendor_out_pending_ || vendor_in_pending_ ||
        transfer_identity_exhausted_) {
      return false;
    }

    uint32_t& next_generation = generationFor(endpoint);
    if (next_generation == UINT32_MAX) {
      transfer_identity_exhausted_ = true;
      reconfiguration_blocked_ = true;
      endpoints_configured_ = false;
      return false;
    }
    ++next_generation;
    identity = {session_generation, next_generation};
    identityFor(endpoint) = identity;
    pendingFor(endpoint) = true;
    return true;
  }

  bool beginVendorTransfer(VendorEndpoint endpoint, uint32_t session_generation,
                           uint32_t processed_lifecycle_generation,
                           uint32_t current_lifecycle_generation,
                           bool usb_ready,
                           VendorTransferIdentity& identity) noexcept {
    return maySubmitUsbTransfer(processed_lifecycle_generation,
                                current_lifecycle_generation, usb_ready) &&
           beginVendorTransfer(endpoint, session_generation, identity);
  }

  bool completeVendorTransfer(VendorEndpoint endpoint,
                              const VendorTransferIdentity& identity) noexcept {
    if (!matchesPending(endpoint, identity)) return false;
    pendingFor(endpoint) = false;
    return true;
  }

  bool confirmVendorTransferCancelled(
      VendorEndpoint endpoint,
      const VendorTransferIdentity& identity) noexcept {
    return completeVendorTransfer(endpoint, identity);
  }

  bool vendorTransferPending(VendorEndpoint endpoint) const noexcept {
    return pendingFor(endpoint);
  }

 private:
  uint32_t& generationFor(VendorEndpoint endpoint) noexcept {
    return endpoint == VendorEndpoint::Out ? vendor_out_generation_
                                            : vendor_in_generation_;
  }

  VendorTransferIdentity& identityFor(VendorEndpoint endpoint) noexcept {
    return endpoint == VendorEndpoint::Out ? vendor_out_identity_
                                            : vendor_in_identity_;
  }

  bool& pendingFor(VendorEndpoint endpoint) noexcept {
    return endpoint == VendorEndpoint::Out ? vendor_out_pending_
                                            : vendor_in_pending_;
  }

  bool pendingFor(VendorEndpoint endpoint) const noexcept {
    return endpoint == VendorEndpoint::Out ? vendor_out_pending_
                                            : vendor_in_pending_;
  }

  bool matchesPending(VendorEndpoint endpoint,
                      const VendorTransferIdentity& identity) const noexcept {
    const VendorTransferIdentity& current =
        endpoint == VendorEndpoint::Out ? vendor_out_identity_
                                        : vendor_in_identity_;
    return pendingFor(endpoint) &&
           current.session_generation == identity.session_generation &&
           current.transfer_generation == identity.transfer_generation;
  }

  bool has_observed_state_ = false;
  bool full_speed_ready_ = false;
  bool endpoints_configured_ = false;
  bool reconfiguration_blocked_ = false;
  bool cancellation_requested_ = false;
  bool cancellation_in_progress_ = false;
  bool vendor_out_pending_ = false;
  bool vendor_in_pending_ = false;
  bool transfer_identity_exhausted_ = false;
  uint32_t vendor_out_generation_ = 0;
  uint32_t vendor_in_generation_ = 0;
  VendorTransferIdentity vendor_out_identity_{};
  VendorTransferIdentity vendor_in_identity_{};
};

}  // namespace slt
