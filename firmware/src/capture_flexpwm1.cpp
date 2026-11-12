#include "slt/capture_flexpwm1.hpp"

#if SLT_ENABLE_FLEXPWM1_CAPTURE

#include <Arduino.h>
#include <imxrt.h>

#include <algorithm>
#include <array>

namespace slt {
namespace {

constexpr uint16_t kCaptureStatusFlags = FLEXPWM_SMSTS_REF | FLEXPWM_SMSTS_RF |
                                         FLEXPWM_SMSTS_CFA0 | FLEXPWM_SMSTS_CFB0;
constexpr uint8_t kMaximumDrainPerPath = 4;
constexpr uint16_t kMaximumStartupPhaseDifference = 16;

uint32_t reloadDwtCycles() {
  const uint32_t bus_hz = F_BUS_ACTUAL;
  return static_cast<uint32_t>(
      (static_cast<uint64_t>(F_CPU_ACTUAL) * 65536u) / bus_hz);
}

uint32_t maximumReloadGapDwt() {
  const uint32_t reload_cycles = reloadDwtCycles();
  return reload_cycles + reload_cycles / 2u;
}

uint32_t readPrimask() {
  uint32_t primask = 0;
  __asm__ volatile("mrs %0, primask" : "=r"(primask) : : "memory");
  return primask;
}

struct RuntimePath {
  uint8_t channel = 0;
  uint8_t submodule = 0;
  FlexPwmInput input = FlexPwmInput::A;
  volatile uint16_t* control = nullptr;
  volatile uint16_t* compare = nullptr;
  volatile uint16_t* cycle = nullptr;
  volatile uint16_t* value = nullptr;
  uint16_t status_flag = 0;
  uint16_t fifo_count_mask = 0;
  uint8_t fifo_count_shift = 0;
  uint16_t edge_counter_enable = 0;
  uint16_t arm_bit = 0;
  uint16_t edge_select = 0;
  uint16_t interrupt_enable = 0;
};

std::array<RuntimePath, kCaptureChannelCount> g_paths{};
FlexPwm1CaptureHardware* g_active_hardware = nullptr;

volatile uint16_t& submoduleStatus(uint8_t submodule) {
  return IMXRT_FLEXPWM1.SM[submodule].STS;
}

uint8_t readFifoCount(const RuntimePath& path) {
  return static_cast<uint8_t>((*path.control & path.fifo_count_mask) >>
                              path.fifo_count_shift);
}

struct CaptureRegisterReader {
  RuntimePath* path = nullptr;
  volatile uint16_t* status = nullptr;

  uint8_t fifoCount() const { return readFifoCount(*path); }
  bool captureFlag() const { return (*status & path->status_flag) != 0; }
  uint8_t readCycle() const { return static_cast<uint8_t>(*path->cycle); }
  uint8_t readEdgeCounter() const {
    return static_cast<uint8_t>(*path->compare >> 8);
  }
  uint16_t readValue() const { return *path->value; }
  void clearCaptureFlag() const { *status = path->status_flag; }
};

struct CaptureClockReader {
  uint16_t readReloadStatus() const { return IMXRT_FLEXPWM1.SM[0].STS; }
  uint16_t readCounter() const { return IMXRT_FLEXPWM1.SM[0].CNT; }
};

void configureRoutePins() {
  // ALT1 paths: PWM1_B0, PWM1_A1, PWM1_B1, and PWM1_A2.
  IOMUXC_SW_MUX_CTL_PAD_GPIO_EMC_24 = 1;
  IOMUXC_SW_MUX_CTL_PAD_GPIO_EMC_25 = 1;
  IOMUXC_SW_MUX_CTL_PAD_GPIO_EMC_26 = 1;
  IOMUXC_SW_MUX_CTL_PAD_GPIO_EMC_27 = 1;
  IOMUXC_FLEXPWM1_PWMB0_SELECT_INPUT = 0;
  IOMUXC_FLEXPWM1_PWMA1_SELECT_INPUT = 0;
  IOMUXC_FLEXPWM1_PWMB1_SELECT_INPUT = 0;
  IOMUXC_FLEXPWM1_PWMA2_SELECT_INPUT = 0;

  // Pin 8 is GPIO_B1_00 ALT6 / PWM1_A3; daisy value 4 selects that pad.
  IOMUXC_SW_MUX_CTL_PAD_GPIO_B1_00 = 6;
  IOMUXC_FLEXPWM1_PWMA3_SELECT_INPUT = 4;
}

void bindRuntimePaths() {
  for (size_t i = 0; i < kFlexPwm1CaptureRoutes.size(); ++i) {
    const FlexPwm1CaptureRoute& route = kFlexPwm1CaptureRoutes[i];
    RuntimePath& path = g_paths[i];
    path.channel = route.channel;
    path.submodule = route.submodule;
    path.input = route.input;
    if (route.input == FlexPwmInput::A) {
      path.control = &IMXRT_FLEXPWM1.SM[route.submodule].CAPTCTRLA;
      path.compare = &IMXRT_FLEXPWM1.SM[route.submodule].CAPTCOMPA;
      path.cycle = &IMXRT_FLEXPWM1.SM[route.submodule].CVAL2CYC;
      path.value = &IMXRT_FLEXPWM1.SM[route.submodule].CVAL2;
      path.status_flag = FLEXPWM_SMSTS_CFA0;
      path.fifo_count_mask = FLEXPWM_SMCAPTCTRLA_CA0CNT(7);
      path.fifo_count_shift = 10;
      path.edge_counter_enable = FLEXPWM_SMCAPTCTRLA_EDGCNTA_EN;
      path.arm_bit = FLEXPWM_SMCAPTCTRLA_ARMA;
      path.edge_select = FLEXPWM_SMCAPTCTRLA_EDGA0(1);
      path.interrupt_enable = FLEXPWM_SMINTEN_CA0IE;
    } else {
      path.control = &IMXRT_FLEXPWM1.SM[route.submodule].CAPTCTRLB;
      path.compare = &IMXRT_FLEXPWM1.SM[route.submodule].CAPTCOMPB;
      path.cycle = &IMXRT_FLEXPWM1.SM[route.submodule].CVAL4CYC;
      path.value = &IMXRT_FLEXPWM1.SM[route.submodule].CVAL4;
      path.status_flag = FLEXPWM_SMSTS_CFB0;
      path.fifo_count_mask = FLEXPWM_SMCAPTCTRLB_CB0CNT(7);
      path.fifo_count_shift = 10;
      path.edge_counter_enable = FLEXPWM_SMCAPTCTRLB_EDGCNTB_EN;
      path.arm_bit = FLEXPWM_SMCAPTCTRLB_ARMB;
      path.edge_select = FLEXPWM_SMCAPTCTRLB_EDGB0(1);
      path.interrupt_enable = FLEXPWM_SMINTEN_CB0IE;
    }
  }
}

void flexpwm1Submodule0Isr() {
  if (g_active_hardware != nullptr) g_active_hardware->serviceInterrupt(0);
}
void flexpwm1Submodule1Isr() {
  if (g_active_hardware != nullptr) g_active_hardware->serviceInterrupt(1);
}
void flexpwm1Submodule2Isr() {
  if (g_active_hardware != nullptr) g_active_hardware->serviceInterrupt(2);
}
void flexpwm1Submodule3Isr() {
  if (g_active_hardware != nullptr) g_active_hardware->serviceInterrupt(3);
}

void setInterruptPriorityAndHandler() {
  attachInterruptVector(IRQ_FLEXPWM1_0, flexpwm1Submodule0Isr);
  attachInterruptVector(IRQ_FLEXPWM1_1, flexpwm1Submodule1Isr);
  attachInterruptVector(IRQ_FLEXPWM1_2, flexpwm1Submodule2Isr);
  attachInterruptVector(IRQ_FLEXPWM1_3, flexpwm1Submodule3Isr);
  // USB stays higher priority. Capture service time and loss are measured;
  // the physical rate gate decides whether this priority is sufficient.
  NVIC_SET_PRIORITY(IRQ_FLEXPWM1_0, 192);
  NVIC_SET_PRIORITY(IRQ_FLEXPWM1_1, 192);
  NVIC_SET_PRIORITY(IRQ_FLEXPWM1_2, 192);
  NVIC_SET_PRIORITY(IRQ_FLEXPWM1_3, 192);
}

void restoreInterrupts(uint32_t previous_primask) {
  if (previous_primask == 0) __enable_irq();
}

}  // namespace

bool FlexPwm1CaptureHardware::initialize(CaptureEngine& engine) {
  engine_ = &engine;
  if (available_) return true;

  CCM_CCGR4 |= CCM_CCGR4_PWM1(CCM_CCGR_ON);
  configureRoutePins();
  bindRuntimePaths();
  g_active_hardware = this;
  setInterruptPriorityAndHandler();

  const uint32_t previous_primask = readPrimask();
  __disable_irq();
  NVIC_DISABLE_IRQ(IRQ_FLEXPWM1_0);
  NVIC_DISABLE_IRQ(IRQ_FLEXPWM1_1);
  NVIC_DISABLE_IRQ(IRQ_FLEXPWM1_2);
  NVIC_DISABLE_IRQ(IRQ_FLEXPWM1_3);

  // Keep PWM outputs disabled. All submodules use IPBus / 1, a full 16-bit
  // modulo, and identical INIT/VAL1 values. Submodules 1-3 reload from SM0.
  IMXRT_FLEXPWM1.MCTRL = 0;
  for (uint8_t submodule = 0; submodule < 4; ++submodule) {
    auto& sm = IMXRT_FLEXPWM1.SM[submodule];
    sm.INTEN = 0;
    sm.CAPTCTRLA = 0;
    sm.CAPTCTRLB = 0;
    sm.CTRL2 = FLEXPWM_SMCTRL2_CLK_SEL(0) |
        (submodule == 0 ? FLEXPWM_SMCTRL2_INIT_SEL(0)
                        : FLEXPWM_SMCTRL2_INIT_SEL(1) |
                              FLEXPWM_SMCTRL2_RELOAD_SEL);
    sm.CTRL = FLEXPWM_SMCTRL_FULL | FLEXPWM_SMCTRL_PRSC(0) |
              FLEXPWM_SMCTRL_LDMOD;
    sm.INIT = 0;
    sm.VAL0 = 0;
    sm.VAL1 = 0xFFFFu;
    sm.STS = kCaptureStatusFlags;
  }

  // CTRL[LDMOD] applies the buffered setup immediately when LDOK is set.
  // Confirm all four load strobes cleared before the common RUN write.
  IMXRT_FLEXPWM1.MCTRL = FLEXPWM_MCTRL_LDOK(0x0Fu);
  restoreInterrupts(previous_primask);

  // The load and initial reload waits run with USB interrupts enabled. They
  // are bounded by DWT and cannot hold off USB completion service.
  const uint32_t load_started = ARM_DWT_CYCCNT;
  while ((IMXRT_FLEXPWM1.MCTRL & FLEXPWM_MCTRL_LDOK(0x0Fu)) != 0) {
    if (static_cast<uint32_t>(ARM_DWT_CYCCNT - load_started) > 4096u) {
      const uint32_t stop_primask = readPrimask();
      __disable_irq();
      IMXRT_FLEXPWM1.MCTRL = 0;
      restoreInterrupts(stop_primask);
      return false;
    }
  }

  // The same MCTRL write starts all four counters. Each slave is then held to
  // the SM0 master reload. Wait for all four first reload flags before making
  // any capture path available.
  const uint32_t run_primask = readPrimask();
  __disable_irq();
  IMXRT_FLEXPWM1.MCTRL = FLEXPWM_MCTRL_RUN(0x0Fu);
  restoreInterrupts(run_primask);
  const uint32_t startup_started = ARM_DWT_CYCCNT;
  const uint32_t startup_timeout = reloadDwtCycles() * 4u;
  const uint16_t reload_flags = FLEXPWM_SMSTS_RF;
  while (((IMXRT_FLEXPWM1.SM[0].STS & reload_flags) == 0) ||
         ((IMXRT_FLEXPWM1.SM[1].STS & reload_flags) == 0) ||
         ((IMXRT_FLEXPWM1.SM[2].STS & reload_flags) == 0) ||
         ((IMXRT_FLEXPWM1.SM[3].STS & reload_flags) == 0)) {
    if (static_cast<uint32_t>(ARM_DWT_CYCCNT - startup_started) >
        startup_timeout) {
      const uint32_t stop_primask = readPrimask();
      __disable_irq();
      IMXRT_FLEXPWM1.MCTRL = 0;
      restoreInterrupts(stop_primask);
      return false;
    }
  }

  const uint16_t counter0 = IMXRT_FLEXPWM1.SM[0].CNT;
  const uint16_t counter1 = IMXRT_FLEXPWM1.SM[1].CNT;
  const uint16_t counter2 = IMXRT_FLEXPWM1.SM[2].CNT;
  const uint16_t counter3 = IMXRT_FLEXPWM1.SM[3].CNT;
  const auto phaseDifference = [](uint16_t left, uint16_t right) {
    const uint16_t forward = static_cast<uint16_t>(left - right);
    const uint16_t reverse = static_cast<uint16_t>(right - left);
    return std::min(forward, reverse);
  };
  if (phaseDifference(counter0, counter1) > kMaximumStartupPhaseDifference ||
      phaseDifference(counter0, counter2) > kMaximumStartupPhaseDifference ||
      phaseDifference(counter0, counter3) > kMaximumStartupPhaseDifference) {
    const uint32_t stop_primask = readPrimask();
    __disable_irq();
    IMXRT_FLEXPWM1.MCTRL = 0;
    restoreInterrupts(stop_primask);
    return false;
  }

  const uint32_t finalize_primask = readPrimask();
  __disable_irq();

  for (uint8_t submodule = 0; submodule < 4; ++submodule) {
    IMXRT_FLEXPWM1.SM[submodule].STS = FLEXPWM_SMSTS_RF;
    IMXRT_FLEXPWM1.SM[submodule].INTEN = FLEXPWM_SMINTEN_REIE |
                                          FLEXPWM_SMINTEN_RIE;
  }
  const uint32_t dwt_at_first_common_reload = ARM_DWT_CYCCNT;
  if (!timebase_.initialize(1, dwt_at_first_common_reload)) {
    IMXRT_FLEXPWM1.MCTRL = 0;
    restoreInterrupts(finalize_primask);
    return false;
  }

  available_ = true;
  NVIC_ENABLE_IRQ(IRQ_FLEXPWM1_0);
  NVIC_ENABLE_IRQ(IRQ_FLEXPWM1_1);
  NVIC_ENABLE_IRQ(IRQ_FLEXPWM1_2);
  NVIC_ENABLE_IRQ(IRQ_FLEXPWM1_3);
  restoreInterrupts(finalize_primask);
  return true;
}

bool FlexPwm1CaptureHardware::arm(CapturePolarity polarity) {
  if (!available_ || engine_ == nullptr || armed_ ||
      engine_->state() != CaptureState::Capturing) {
    return false;
  }
  const uint16_t edge = selectedCaptureEdge(polarity);
  const uint32_t previous_primask = readPrimask();
  __disable_irq();
  NVIC_DISABLE_IRQ(IRQ_FLEXPWM1_0);
  NVIC_DISABLE_IRQ(IRQ_FLEXPWM1_1);
  NVIC_DISABLE_IRQ(IRQ_FLEXPWM1_2);
  NVIC_DISABLE_IRQ(IRQ_FLEXPWM1_3);

  // Reset each 8-bit edge counter with the enable bit low, then select raw
  // input and the requested active transition. ARMA is set only after all five
  // paths have their counter and polarity programmed.
  for (RuntimePath& path : g_paths) {
    *path.control = 0;
    submoduleStatus(path.submodule) = path.status_flag;
    if (path.input == FlexPwmInput::A) {
      path.edge_select = FLEXPWM_SMCAPTCTRLA_EDGA0(edge);
    } else {
      path.edge_select = FLEXPWM_SMCAPTCTRLB_EDGB0(edge);
    }
    *path.control = path.edge_counter_enable | path.edge_select;
    IMXRT_FLEXPWM1.SM[path.submodule].INTEN |= path.interrupt_enable;
  }
  __asm__ volatile("dmb" ::: "memory");
  for (RuntimePath& path : g_paths) {
    *path.control = path.edge_counter_enable | path.edge_select | path.arm_bit;
  }
  armed_ = true;

  NVIC_ENABLE_IRQ(IRQ_FLEXPWM1_0);
  NVIC_ENABLE_IRQ(IRQ_FLEXPWM1_1);
  NVIC_ENABLE_IRQ(IRQ_FLEXPWM1_2);
  NVIC_ENABLE_IRQ(IRQ_FLEXPWM1_3);
  restoreInterrupts(previous_primask);
  return true;
}

void FlexPwm1CaptureHardware::stopCapture() {
  if (!available_ || !armed_) return;
  const uint32_t previous_primask = readPrimask();
  __disable_irq();
  NVIC_DISABLE_IRQ(IRQ_FLEXPWM1_0);
  NVIC_DISABLE_IRQ(IRQ_FLEXPWM1_1);
  NVIC_DISABLE_IRQ(IRQ_FLEXPWM1_2);
  NVIC_DISABLE_IRQ(IRQ_FLEXPWM1_3);

  for (RuntimePath& path : g_paths) {
    *path.control = 0;
    // Drain the one-entry FIFO in documented CYC-then-CVAL order before the
    // next run can arm it; these terminal entries are not added after a fault.
    if (readFifoCount(path) != 0) {
      (void)*path.cycle;
      (void)*path.value;
    }
    submoduleStatus(path.submodule) = path.status_flag;
    IMXRT_FLEXPWM1.SM[path.submodule].INTEN &=
        static_cast<uint16_t>(~path.interrupt_enable);
  }
  armed_ = false;
  NVIC_ENABLE_IRQ(IRQ_FLEXPWM1_0);
  NVIC_ENABLE_IRQ(IRQ_FLEXPWM1_1);
  NVIC_ENABLE_IRQ(IRQ_FLEXPWM1_2);
  NVIC_ENABLE_IRQ(IRQ_FLEXPWM1_3);
  restoreInterrupts(previous_primask);
}

bool FlexPwm1CaptureHardware::currentTick(uint64_t& tick) const {
  if (!available_) return false;
  const uint32_t previous_primask = readPrimask();
  __disable_irq();
  if (!timebase_.valid()) {
    restoreInterrupts(previous_primask);
    return false;
  }
  const uint32_t now = ARM_DWT_CYCCNT;
  if (!timebase_.canServiceCapture(now, maximumReloadGapDwt())) {
    restoreInterrupts(previous_primask);
    return false;
  }
  CaptureClockReader reader{};
  const bool coherent = composeCaptureClockSnapshot(
      reader, FLEXPWM_SMSTS_RF, timebase_.reloadEpoch(), tick);
  restoreInterrupts(previous_primask);
  return coherent;
}

bool FlexPwm1CaptureHardware::snapshot(uint64_t& tick) const {
  return currentTick(tick);
}

bool FlexPwm1CaptureHardware::serviceMasterReload() {
  const uint16_t master_status = IMXRT_FLEXPWM1.SM[0].STS;
  if ((master_status & FLEXPWM_SMSTS_REF) != 0) return false;
  if ((master_status & FLEXPWM_SMSTS_RF) == 0) return true;

  const uint32_t previous_primask = readPrimask();
  __disable_irq();
  const uint16_t confirmed_status = IMXRT_FLEXPWM1.SM[0].STS;
  if ((confirmed_status & FLEXPWM_SMSTS_REF) != 0) {
    restoreInterrupts(previous_primask);
    return false;
  }
  if ((confirmed_status & FLEXPWM_SMSTS_RF) != 0) {
    const uint32_t now = ARM_DWT_CYCCNT;
    if (!timebase_.observeMasterReload(now, maximumReloadGapDwt())) {
      IMXRT_FLEXPWM1.SM[0].STS = FLEXPWM_SMSTS_RF;
      restoreInterrupts(previous_primask);
      return false;
    }
    IMXRT_FLEXPWM1.SM[0].STS = FLEXPWM_SMSTS_RF;
  }
  restoreInterrupts(previous_primask);
  return true;
}

void FlexPwm1CaptureHardware::fail(uint16_t fault, uint64_t end_tick) {
  if (engine_ != nullptr) engine_->abort(fault, end_tick);
  stopCapture();
}

void FlexPwm1CaptureHardware::serviceInterrupt(uint8_t submodule) {
  if (!available_ || submodule >= 4) return;
  const uint32_t started = ARM_DWT_CYCCNT;
  bool failed = false;
  uint16_t failure_fault = kCaptureFaultCaptureLoss;
  uint64_t end_tick = 0;

  if (!serviceMasterReload() ||
      (submoduleStatus(submodule) & FLEXPWM_SMSTS_REF) != 0) {
    failed = true;
  }
  for (uint8_t index = 0; index < 4; ++index) {
    const uint16_t status = submoduleStatus(index);
    if ((status & FLEXPWM_SMSTS_REF) != 0) failed = true;
    if (index != 0 && (status & FLEXPWM_SMSTS_RF) != 0) {
      submoduleStatus(index) = FLEXPWM_SMSTS_RF;
    }
  }

  if (!failed && armed_ && engine_ != nullptr &&
      engine_->state() == CaptureState::Capturing) {
    std::array<CaptureRegisterReader, kCaptureChannelCount> readers{};
    for (size_t index = 0; index < g_paths.size(); ++index) {
      RuntimePath& path = g_paths[index];
      readers[index] = {&path, &submoduleStatus(path.submodule)};
    }
    const CaptureDrainResult result = drainCaptureFifos(
        readers, kMaximumDrainPerPath,
        [&](size_t index, const CaptureRegisterSample& sample) {
          const RuntimePath& path = g_paths[index];
          uint64_t timestamp = 0;
          uint64_t service_tick = 0;
          if (!timebase_.extend(sample.cycle_nibble, sample.timer_counter,
                                timestamp) ||
              !currentTick(service_tick) || service_tick < timestamp) {
            failed = true;
            failure_fault = kCaptureFaultTimebase;
            return false;
          }
          if (!engine_->observeServiceLatencyTicks(service_tick - timestamp)) {
            failed = true;
            failure_fault = kCaptureFaultServiceDeadline;
            return false;
          }
          if (engine_->state() != CaptureState::Capturing ||
              !engine_->record(path.channel, timestamp, sample.edge_counter)) {
            failed = true;
            return false;
          }
          return !engine_->stopRequested();
        });
    if (result == CaptureDrainResult::Fault) failed = true;
    if (result == CaptureDrainResult::StopRequested && engine_->stopRequested()) {
      stopCapture();
    }
  }

  const uint32_t service_cycles = ARM_DWT_CYCCNT - started;
  if (service_cycles > maximum_service_cycles_) {
    maximum_service_cycles_ = service_cycles;
  }
  if (engine_ != nullptr) {
    engine_->observeServiceCycles(service_cycles);
    if (service_cycles >
        engine_->serviceBudget().simultaneous_five_channel_cycles) {
      failed = true;
      failure_fault = kCaptureFaultServiceDeadline;
    }
  }
  if (failed) {
    if (!currentTick(end_tick)) end_tick = 0;
    fail(failure_fault, end_tick);
  }
}

}  // namespace slt

#else

namespace slt {

bool FlexPwm1CaptureHardware::initialize(CaptureEngine& engine) {
  engine_ = &engine;
  return false;
}

bool FlexPwm1CaptureHardware::arm(CapturePolarity) { return false; }

void FlexPwm1CaptureHardware::stopCapture() {}

bool FlexPwm1CaptureHardware::snapshot(uint64_t&) const { return false; }

void FlexPwm1CaptureHardware::serviceInterrupt(uint8_t) {}

bool FlexPwm1CaptureHardware::currentTick(uint64_t&) const { return false; }

bool FlexPwm1CaptureHardware::serviceMasterReload() { return false; }

void FlexPwm1CaptureHardware::fail(uint16_t, uint64_t) {}

}  // namespace slt

#endif
