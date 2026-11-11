#pragma once

#include <cstdint>

#include "slt/pulse_engine.hpp"

namespace slt {

// The Teensy transfer status stores active/error flags in the low bits and the
// remaining-byte count in bits 16 through 30.
constexpr TransferResult transferResultFromStatus(uint32_t status) noexcept {
  constexpr uint32_t kActiveMask = 1u << 7;
  constexpr uint32_t kErrorMask = (1u << 6) | (1u << 5) | (1u << 4) |
                                  (1u << 3);
  return (status & (kActiveMask | kErrorMask)) == 0
             ? TransferResult::Success
             : TransferResult::Error;
}

constexpr uint32_t transferBytesRemaining(uint32_t status) noexcept {
  return (status >> 16) & 0x7FFFu;
}

constexpr TransferResult hidTransferResult(uint32_t status,
                                           uint32_t expected_bytes) noexcept {
  if (transferResultFromStatus(status) != TransferResult::Success ||
      expected_bytes == 0 || transferBytesRemaining(status) != 0) {
    return TransferResult::Error;
  }
  return TransferResult::Success;
}

}  // namespace slt
