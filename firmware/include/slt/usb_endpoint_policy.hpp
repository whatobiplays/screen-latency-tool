#pragma once

#include <cstddef>
#include <cstdint>

namespace slt {

// A queued HID action is valid only after the main loop processes every USB
// lifecycle change and confirms that the device remains configured.
constexpr bool maySubmitHidTransfer(uint32_t processed_lifecycle_generation,
                                    uint32_t current_lifecycle_generation,
                                    bool usb_configured) noexcept {
  return usb_configured &&
         processed_lifecycle_generation == current_lifecycle_generation;
}

// A zero-length response has no vendor IN completion that can rearm OUT.
constexpr bool shouldRearmVendorOut(size_t response_size) noexcept {
  return response_size == 0;
}

}  // namespace slt
