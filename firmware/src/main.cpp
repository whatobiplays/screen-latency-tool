#include <Arduino.h>
#include <avr_functions.h>
#include <imxrt.h>
#include <usb_dev.h>

#include <array>
#include <cstddef>
#include <cstdint>

#include "slt/hid_slot_pool.hpp"
#include "slt/protocol.hpp"
#include "slt/usb_endpoint_policy.hpp"
#include "slt/usb_transfer_status.hpp"

extern volatile uint8_t usb_configuration;
extern "C" uint8_t slt_usb_cancel_transfer(uint32_t endpoint, uint8_t transmit);

namespace {

constexpr uint8_t kHidEndpoint = 1;
constexpr uint8_t kVendorOutEndpoint = 2;
constexpr uint8_t kVendorInEndpoint = 3;
constexpr size_t kEndpointBufferSize = 64;
constexpr uint32_t kHidReportSize = 6;
constexpr size_t kEventCapacity = 8;
constexpr size_t kHidSlotCount = 16;
constexpr uint32_t kPortChangeBits = USB_PORTSC1_OCC | USB_PORTSC1_PEC |
                                     USB_PORTSC1_CSC;

struct alignas(32) DmaSlot {
  transfer_t descriptor{};
  alignas(32) uint8_t bytes[kEndpointBufferSize]{};
  volatile uint32_t session_generation = 0;
  volatile uint32_t transfer_generation = 0;
};

enum class EventKind : uint8_t { HidComplete, VendorOutComplete, VendorInComplete, UsbReset };

struct Event {
  EventKind kind = EventKind::UsbReset;
  slt::TransferToken token{};
  slt::TransferResult result = slt::TransferResult::Error;
  uint32_t session_generation = 0;
  uint32_t transfer_generation = 0;
  uint32_t lifecycle_generation = 0;
  uint8_t hid_slot_index = 0;
  uint32_t tick = 0;
  uint8_t length = 0;
  uint8_t bytes[kEndpointBufferSize]{};
};

DMAMEM DmaSlot hid_slots[kHidSlotCount] __attribute__((aligned(32)));
DMAMEM DmaSlot vendor_out_slot __attribute__((aligned(32)));
DMAMEM DmaSlot vendor_in_slot __attribute__((aligned(32)));
slt::HidSlotPool<kHidSlotCount> hid_slot_pool;

slt::PulseEngine pulse_engine;
slt::Protocol protocol(pulse_engine);
slt::UsbEndpointLifecycle endpoint_lifecycle;
Event event_queue[kEventCapacity]{};
volatile uint8_t event_head = 0;
volatile uint8_t event_tail = 0;
volatile bool event_overflow = false;
volatile uint32_t usb_lifecycle_generation = 0;
uint32_t processed_usb_lifecycle_generation = 0;
uint32_t vendor_in_started_tick = 0;

void postEvent(const Event& event) {
  const uint8_t next = static_cast<uint8_t>((event_head + 1) % kEventCapacity);
  if (next == event_tail) {
    event_overflow = true;
    return;
  }
  event_queue[event_head] = event;
  __asm__ volatile("dmb" ::: "memory");
  event_head = next;
}

bool takeEvent(Event& event) {
  const uint8_t tail = event_tail;
  if (tail == event_head) return false;
  __asm__ volatile("dmb" ::: "memory");
  event = event_queue[tail];
  __asm__ volatile("dmb" ::: "memory");
  event_tail = static_cast<uint8_t>((tail + 1) % kEventCapacity);
  return true;
}

bool takeEventOverflow() {
  __disable_irq();
  const bool overflow = event_overflow;
  event_overflow = false;
  __enable_irq();
  return overflow;
}

slt::TransferResult resultFor(const transfer_t* transfer) {
  return slt::transferResultFromStatus(usb_transfer_status(transfer));
}

DmaSlot* findHidSlot(transfer_t* transfer) {
  for (DmaSlot& slot : hid_slots) {
    if (&slot.descriptor == transfer) return &slot;
  }
  return nullptr;
}

void retireHidTransfer(const slt::TransferToken& token) {
  hid_slot_pool.retire(token);
}

void retireInFlightHidSlots() {
  hid_slot_pool.retireAll();
}

DmaSlot* allocateHidSlot(const slt::TransferToken& token, uint8_t& slot_index) {
  if (!hid_slot_pool.allocate(token, slot_index)) return nullptr;
  return &hid_slots[slot_index];
}

void releaseHidSlot(const Event& event) {
  hid_slot_pool.release(event.hid_slot_index, event.token);
}

void hidComplete(transfer_t* transfer) {
  DmaSlot* slot = findHidSlot(transfer);
  if (slot == nullptr) {
    event_overflow = true;
    return;
  }
  const uint8_t slot_index = static_cast<uint8_t>(slot - hid_slots);
  Event event{};
  if (!hid_slot_pool.captureCompletionToken(slot_index, event.token)) {
    if (!hid_slot_pool.isRetired(slot_index)) event_overflow = true;
    return;
  }
  event.kind = EventKind::HidComplete;
  event.result = slt::hidTransferResult(usb_transfer_status(transfer),
                                        kHidReportSize);
  event.hid_slot_index = slot_index;
  event.tick = ARM_DWT_CYCCNT;
  postEvent(event);
}

void vendorOutComplete(transfer_t* transfer) {
  Event event{};
  event.kind = EventKind::VendorOutComplete;
  event.session_generation = vendor_out_slot.session_generation;
  event.transfer_generation = vendor_out_slot.transfer_generation;
  const uint32_t status = usb_transfer_status(transfer);
  event.result = slt::transferResultFromStatus(status);
  const uint32_t remaining = slt::transferBytesRemaining(status);
  if (remaining <= kEndpointBufferSize) {
    event.length = static_cast<uint8_t>(kEndpointBufferSize - remaining);
    arm_dcache_delete(vendor_out_slot.bytes, kEndpointBufferSize);
    for (size_t i = 0; i < event.length; ++i) {
      event.bytes[i] = vendor_out_slot.bytes[i];
    }
  } else {
    event.result = slt::TransferResult::Error;
  }
  postEvent(event);
}

void vendorInComplete(transfer_t* transfer) {
  Event event{};
  event.kind = EventKind::VendorInComplete;
  event.session_generation = vendor_in_slot.session_generation;
  event.transfer_generation = vendor_in_slot.transfer_generation;
  event.result = resultFor(transfer);
  postEvent(event);
}

void configureEndpoints() {
  if (!endpoint_lifecycle.shouldConfigureEndpoints()) return;
  usb_config_tx(kHidEndpoint, 64, 0, hidComplete);
  usb_config_rx(kVendorOutEndpoint, 64, 0, vendorOutComplete);
  usb_config_tx(kVendorInEndpoint, 64, 0, vendorInComplete);
  endpoint_lifecycle.markEndpointsConfigured();
}

void startVendorReceive() {
  const slt::Snapshot snapshot = pulse_engine.snapshot();
  if (!snapshot.session_valid || !snapshot.full_speed_verified) return;

  __disable_irq();
  const bool usb_ready =
      usb_configuration != 0 && endpoint_lifecycle.endpointsConfigured() &&
      !endpoint_lifecycle.reconfigurationBlocked() &&
      snapshot.session_valid && snapshot.full_speed_verified;
  slt::VendorTransferIdentity identity{};
  if (!endpoint_lifecycle.beginVendorTransfer(
          slt::VendorEndpoint::Out, snapshot.session_generation,
          processed_usb_lifecycle_generation, usb_lifecycle_generation,
          usb_ready, identity)) {
    __enable_irq();
    return;
  }
  vendor_out_slot.session_generation = identity.session_generation;
  vendor_out_slot.transfer_generation = identity.transfer_generation;
  arm_dcache_delete(vendor_out_slot.bytes, kEndpointBufferSize);
  usb_prepare_transfer(&vendor_out_slot.descriptor, vendor_out_slot.bytes,
                       kEndpointBufferSize, 0);
  usb_receive(kVendorOutEndpoint, &vendor_out_slot.descriptor);
  __enable_irq();
}

void sendVendorResponse(const uint8_t* bytes, size_t length,
                        uint32_t session_generation) {
  if (length == 0 || length > kEndpointBufferSize) return;

  __disable_irq();
  const slt::Snapshot snapshot = pulse_engine.snapshot();
  const bool usb_ready =
      usb_configuration != 0 && endpoint_lifecycle.endpointsConfigured() &&
      !endpoint_lifecycle.reconfigurationBlocked() &&
      snapshot.session_valid && snapshot.full_speed_verified &&
      snapshot.session_generation == session_generation;
  slt::VendorTransferIdentity identity{};
  if (!endpoint_lifecycle.beginVendorTransfer(
          slt::VendorEndpoint::In, session_generation,
          processed_usb_lifecycle_generation, usb_lifecycle_generation,
          usb_ready, identity)) {
    __enable_irq();
    return;
  }
  vendor_in_slot.session_generation = identity.session_generation;
  vendor_in_slot.transfer_generation = identity.transfer_generation;
  for (size_t i = 0; i < length; ++i) vendor_in_slot.bytes[i] = bytes[i];
  arm_dcache_flush_delete(vendor_in_slot.bytes, kEndpointBufferSize);
  usb_prepare_transfer(&vendor_in_slot.descriptor, vendor_in_slot.bytes,
                       static_cast<uint32_t>(length), 0);
  usb_transmit(kVendorInEndpoint, &vendor_in_slot.descriptor);
  vendor_in_started_tick = ARM_DWT_CYCCNT;
  __enable_irq();
}

bool cancelEndpoints() {
  if (!endpoint_lifecycle.takeCancellationRequest()) return false;
  retireInFlightHidSlots();
  const bool hid_flushed = slt_usb_cancel_transfer(kHidEndpoint, 1) != 0;
  if (hid_flushed) hid_slot_pool.reclaimRetiredAfterEndpointFlush();
  const bool vendor_out_flushed =
      slt_usb_cancel_transfer(kVendorOutEndpoint, 0) != 0;
  const bool vendor_in_flushed =
      slt_usb_cancel_transfer(kVendorInEndpoint, 1) != 0;
  endpoint_lifecycle.finishCancellation(hid_flushed, vendor_out_flushed,
                                        vendor_in_flushed);
  return !endpoint_lifecycle.reconfigurationBlocked();
}

void handleEvent(const Event& event, uint32_t now_tick) {
  if (event.kind == EventKind::UsbReset) {
    if (!endpoint_lifecycle.acceptUsbResetEvent(event.lifecycle_generation,
                                               usb_lifecycle_generation)) {
      return;
    }
    if (!cancelEndpoints()) retireInFlightHidSlots();
    pulse_engine.onUsbReset();
    processed_usb_lifecycle_generation = event.lifecycle_generation;
    return;
  }

  const slt::Snapshot snapshot = pulse_engine.snapshot();
  if (event.kind == EventKind::HidComplete) {
    if (slt::mayAcceptHidCompletion(event.token.session_generation,
                                   event.token.transfer_generation,
                                   snapshot.session_generation,
                                   snapshot.transfer_generation,
                                   snapshot.session_valid,
                                   snapshot.full_speed_verified)) {
      pulse_engine.onTransferComplete(event.token, event.result, event.tick);
    }
    releaseHidSlot(event);
    return;
  }

  if (event.kind == EventKind::VendorOutComplete) {
    const slt::VendorTransferIdentity identity{
        event.session_generation, event.transfer_generation};
    if (!endpoint_lifecycle.completeVendorTransfer(slt::VendorEndpoint::Out,
                                                    identity)) {
      return;
    }
    if (event.session_generation != snapshot.session_generation ||
        !snapshot.session_valid || !snapshot.full_speed_verified) {
      return;
    }
    if (event.result != slt::TransferResult::Success) {
      startVendorReceive();
      return;
    }
    std::array<uint8_t, kEndpointBufferSize> response{};
    size_t response_size = 0;
    protocol.process(event.bytes, event.length, response.data(), response.size(),
                     response_size, now_tick);
    if (slt::shouldRearmVendorOut(response_size)) {
      startVendorReceive();
      return;
    }
    sendVendorResponse(response.data(), response_size,
                       event.session_generation);
    return;
  }

  if (event.kind == EventKind::VendorInComplete) {
    const slt::VendorTransferIdentity identity{
        event.session_generation, event.transfer_generation};
    if (!endpoint_lifecycle.completeVendorTransfer(slt::VendorEndpoint::In,
                                                    identity)) {
      return;
    }
    if (event.session_generation != snapshot.session_generation ||
        !snapshot.session_valid || !snapshot.full_speed_verified) {
      return;
    }
    startVendorReceive();
  }
}

void updateUsbState() {
  const uint32_t portsc = USB1_PORTSC1;
  const bool connected = (portsc & USB_PORTSC1_CCS) != 0;
  const bool attached = connected && (portsc & USB_PORTSC1_PE) != 0;
  const bool configured = usb_configuration != 0;
  const uint8_t pspd = static_cast<uint8_t>((portsc >> 26) & 0x03u);
  const bool full_speed_verified =
      (portsc & USB_PORTSC1_PFSC) != 0 && pspd == 0;
  const bool session_was_valid = pulse_engine.snapshot().session_valid;

  endpoint_lifecycle.observeUsbState(attached, configured,
                                     full_speed_verified);
  if (session_was_valid && !attached) pulse_engine.onDisconnect();

  if (slt::shouldResetProtocolSession(connected)) {
    protocol.resetSession();
  }

  cancelEndpoints();
  if (processed_usb_lifecycle_generation == usb_lifecycle_generation &&
      endpoint_lifecycle.shouldConfigureEndpoints()) {
    configureEndpoints();
  }

  const bool usable_configuration =
      configured && full_speed_verified &&
      endpoint_lifecycle.endpointsConfigured() &&
      !endpoint_lifecycle.reconfigurationBlocked() &&
      processed_usb_lifecycle_generation == usb_lifecycle_generation;
  pulse_engine.observeUsbState(
      attached, usable_configuration, (portsc & USB_PORTSC1_PFSC) != 0, pspd,
      ARM_DWT_CYCCNT);
  if (attached && usable_configuration) startVendorReceive();
}

void dispatchActions() {
  for (uint8_t count = 0; count < 4; ++count) {
    slt::Action action{};
    if (!pulse_engine.takeAction(action)) return;

    if (action.kind == slt::ActionKind::Cancel) {
      retireHidTransfer(action.token);
      if (slt_usb_cancel_transfer(kHidEndpoint, 1) == 0) {
        pulse_engine.onUsbReset();
        endpoint_lifecycle.requestCancellation();
        cancelEndpoints();
        return;
      }
      hid_slot_pool.reclaimRetiredAfterEndpointFlush();
      pulse_engine.onTransferComplete(action.token,
                                     slt::TransferResult::Cancelled,
                                     ARM_DWT_CYCCNT);
      continue;
    }
    uint8_t slot_index = 0;
    if (!endpoint_lifecycle.endpointsConfigured() ||
        endpoint_lifecycle.reconfigurationBlocked()) {
      pulse_engine.onTransferComplete(action.token, slt::TransferResult::Error,
                                      ARM_DWT_CYCCNT);
      continue;
    }
    DmaSlot* allocated = allocateHidSlot(action.token, slot_index);
    if (allocated == nullptr) {
      pulse_engine.onTransferComplete(action.token, slt::TransferResult::Error,
                                      ARM_DWT_CYCCNT);
      continue;
    }

    DmaSlot& slot = *allocated;
    for (size_t i = 0; i < action.report.size(); ++i) {
      slot.bytes[i] = action.report[i];
    }
    arm_dcache_flush_delete(slot.bytes, kEndpointBufferSize);
    usb_prepare_transfer(&slot.descriptor, slot.bytes, kHidReportSize, 0);
    // The reset callback cannot change the generation between this check and enqueue.
    __disable_irq();
    if (!slt::maySubmitUsbTransfer(processed_usb_lifecycle_generation,
                                   usb_lifecycle_generation,
                                   usb_configuration != 0 &&
                                       endpoint_lifecycle.endpointsConfigured() &&
                                       !endpoint_lifecycle.reconfigurationBlocked() &&
                                       pulse_engine.snapshot().full_speed_verified)) {
      __enable_irq();
      retireHidTransfer(action.token);
      return;
    }
    usb_transmit(kHidEndpoint, &slot.descriptor);
    __enable_irq();
  }
}

}  // namespace

extern "C" void slt_usb_bus_reset(void) {
  ++usb_lifecycle_generation;
  Event event{};
  event.kind = EventKind::UsbReset;
  event.lifecycle_generation = usb_lifecycle_generation;
  postEvent(event);
}

void setup() {
  // Teensy startup enables DWT_CYCCNT before calling setup(). The value is
  // diagnostic only; no qualification decision uses this software tick.
  protocol.setFixtureSerial((static_cast<uint64_t>(HW_OCOTP_MAC0) << 32) |
                            static_cast<uint64_t>(HW_OCOTP_MAC1));
}

void loop() {
  if (takeEventOverflow()) {
    endpoint_lifecycle.requestCancellation();
    if (!cancelEndpoints()) retireInFlightHidSlots();
    pulse_engine.onDisconnect();
    processed_usb_lifecycle_generation = usb_lifecycle_generation;
  }

  // Read the port before handling queued vendor callbacks. A speed loss then
  // invalidates the session before an older completion can be processed.
  updateUsbState();

  Event event{};
  while (takeEvent(event)) handleEvent(event, ARM_DWT_CYCCNT);

  const uint32_t now_tick = ARM_DWT_CYCCNT;
  pulse_engine.service(now_tick);
  dispatchActions();

  if (!endpoint_lifecycle.reconfigurationBlocked() &&
      endpoint_lifecycle.vendorTransferPending(slt::VendorEndpoint::In) &&
      static_cast<uint32_t>(now_tick - vendor_in_started_tick) >=
          slt::kTransferTimeoutTicks) {
    if (slt_usb_cancel_transfer(kVendorInEndpoint, 1) == 0) {
      pulse_engine.onUsbReset();
      endpoint_lifecycle.requestCancellation();
      cancelEndpoints();
    } else {
      const slt::VendorTransferIdentity identity{
          vendor_in_slot.session_generation,
          vendor_in_slot.transfer_generation};
      endpoint_lifecycle.confirmVendorTransferCancelled(
          slt::VendorEndpoint::In, identity);
      // UsbEndpointLifecycle serializes vendor OUT and IN transfers.
      startVendorReceive();
    }
  }
}
