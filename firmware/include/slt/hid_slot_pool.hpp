#pragma once

#include <cstddef>
#include <cstdint>

#include "slt/pulse_engine.hpp"

namespace slt {

// Each entry binds one DMA slot to its USB session and transfer generation.
// Reuse a retired entry after the endpoint flush completes.
template <size_t Capacity>
class HidSlotPool {
  static_assert(Capacity > 0 && Capacity <= 256,
                "HID slot indices must fit in uint8_t");

 public:
  bool allocate(const TransferToken& token, uint8_t& slot_index) {
    for (size_t index = 0; index < Capacity; ++index) {
      Entry& entry = entries_[index];
      if (entry.retired || entry.in_use) continue;
      entry.session_generation = token.session_generation;
      entry.transfer_generation = token.transfer_generation;
      entry.kind = static_cast<uint8_t>(token.kind);
      memoryBarrier();
      entry.in_use = true;
      slot_index = static_cast<uint8_t>(index);
      return true;
    }
    return false;
  }

  bool captureCompletionToken(uint8_t slot_index, TransferToken& token) const {
    if (slot_index >= Capacity) return false;
    const Entry& entry = entries_[slot_index];
    if (entry.retired || !entry.in_use) return false;
    memoryBarrier();
    token.session_generation = entry.session_generation;
    token.transfer_generation = entry.transfer_generation;
    token.kind = static_cast<TransferKind>(entry.kind);
    return true;
  }

  bool isRetired(uint8_t slot_index) const {
    return slot_index < Capacity && entries_[slot_index].retired;
  }

  bool retire(const TransferToken& token) {
    for (Entry& entry : entries_) {
      if (!entry.in_use || !matches(entry, token)) continue;
      entry.retired = true;
      memoryBarrier();
      entry.in_use = false;
      return true;
    }
    return false;
  }

  void retireAll() {
    for (Entry& entry : entries_) {
      if (!entry.in_use) continue;
      entry.retired = true;
      memoryBarrier();
      entry.in_use = false;
    }
  }

  void release(uint8_t slot_index, const TransferToken& token) {
    if (slot_index >= Capacity) return;
    Entry& entry = entries_[slot_index];
    if (entry.retired || !entry.in_use || !matches(entry, token)) return;
    entry.in_use = false;
  }

  // Call after the USB endpoint flush succeeds.
  void reclaimRetiredAfterEndpointFlush() {
    for (Entry& entry : entries_) {
      if (!entry.retired) continue;
      entry.in_use = false;
      memoryBarrier();
      entry.retired = false;
    }
  }

 private:
  struct Entry {
    volatile uint32_t session_generation = 0;
    volatile uint32_t transfer_generation = 0;
    volatile uint8_t kind = 0;
    volatile bool in_use = false;
    volatile bool retired = false;
  };

  static bool matches(const Entry& entry, const TransferToken& token) {
    return entry.session_generation == token.session_generation &&
           entry.transfer_generation == token.transfer_generation &&
           entry.kind == static_cast<uint8_t>(token.kind);
  }

  static void memoryBarrier() {
#if defined(__arm__) || defined(__thumb__)
    __asm__ volatile("dmb" ::: "memory");
#else
    __asm__ volatile("" ::: "memory");
#endif
  }

  Entry entries_[Capacity]{};
};

}  // namespace slt
