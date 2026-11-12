#pragma once

#include <cstdint>

#include "slt/capture.hpp"

#ifndef SLT_ENABLE_FLEXPWM1_CAPTURE
#define SLT_ENABLE_FLEXPWM1_CAPTURE 0
#endif

#ifndef SLT_CAPTURE_PIN_ROUTE_ACCEPTED
#define SLT_CAPTURE_PIN_ROUTE_ACCEPTED 0
#endif

#if SLT_ENABLE_FLEXPWM1_CAPTURE && !defined(ARDUINO_TEENSY41)
#error "FlexPWM1 capture routing is only defined for Teensy 4.1"
#endif

#if SLT_CAPTURE_PIN_ROUTE_ACCEPTED && !SLT_ENABLE_FLEXPWM1_CAPTURE
#error "Pin-route acceptance requires the FlexPWM1 capture implementation"
#endif

namespace slt {

// Hardware-latched FlexPWM1 capture for the five provisional Teensy 4.1 routes.
// The default build leaves the pin mux and peripheral disabled.
class FlexPwm1CaptureHardware {
 public:
  bool initialize(CaptureEngine& engine);
  bool available() const { return available_; }
  bool arm(CapturePolarity polarity);
  void stopCapture();
  bool snapshot(uint64_t& tick) const;
  void serviceInterrupt(uint8_t submodule);
  uint32_t maximumServiceCycles() const { return maximum_service_cycles_; }

 private:
  bool currentTick(uint64_t& tick) const;
  bool serviceMasterReload();
  void fail(uint16_t fault, uint64_t end_tick);

  CaptureEngine* engine_ = nullptr;
  CaptureTimestampExtender timebase_{};
  uint32_t maximum_service_cycles_ = 0;
  bool available_ = false;
  bool armed_ = false;
};

}  // namespace slt
