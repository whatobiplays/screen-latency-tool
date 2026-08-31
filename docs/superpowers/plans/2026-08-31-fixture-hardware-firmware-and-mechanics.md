# Fixture Hardware, Firmware, and Mechanics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Teensy 4.1 measurement fixture, five-channel sensor electronics, adjustable five-sensor mechanical overlay, and firmware required to produce trustworthy raw button-to-photon timing batches.

**Architecture:** Firmware is split into host-testable acquisition/state-machine code and thin Teensy hardware adapters for USB, capture timers, DAC/ADC, power supervision, and self-test. Electronics are qualified in increasing fidelity—digital timing loopback, one sensor front-end, five-channel carrier/sensors, then the adjustable fixture—before publication-grade claims are allowed.

**Tech Stack:** Teensy 4.1 / i.MX RT1062, C++20 where supported by the host tests, PlatformIO/Teensy Arduino core for reproducible builds, KiCad, ngspice, OpenSCAD, SFH 203 P, OPA320, TLV3501.

**Spec:** `docs/superpowers/specs/2026-08-31-screen-latency-measurement-design.md`

## Global Constraints

- Synthetic USB HID input is authoritative; built-in controller actuation is out of v1 scope.
- `T0` is successful on-bus HID IN completion, never queue time.
- `T0` and all sensor captures must resolve to one monotonic hardware time domain.
- Comparator crossings must use hardware capture; uncharacterized GPIO ISR timestamping is not acceptable for authoritative data.
- Design targets: <=10 us timestamp resolution, <=25 us calibrated inter-channel skew, <=0.1 ms total fixture uncertainty.
- Five sensors occupy normalized positions top `(0.50,0.10)`, left `(0.10,0.50)`, center `(0.50,0.50)`, right `(0.90,0.50)`, bottom `(0.50,0.90)`.
- The fixture is externally/self powered and must prevent 5 V backfeed into the Android DUT.
- During a 1,000-trial batch, no vendor-control traffic is sent until acquisition ends.
- All 1,000 raw trial records remain in RAM until download.
- Electrical self-test is mandatory before publishable sessions; optical qualification is periodic and required after relevant hardware changes.
- Do not fabricate certainty if Teensy USB-completion timing or timer routing cannot meet the spec; use the documented external-capture fallback rather than weakening the metric.

---

## File Structure

```text
firmware/
├── platformio.ini
├── include/
│   ├── acquisition.h
│   ├── batch_buffer.h
│   ├── capture_engine.h
│   ├── fixture_protocol.h
│   ├── fixture_state.h
│   ├── power_supervisor.h
│   ├── threshold_controller.h
│   └── usb_fixture.h
├── src/
│   ├── acquisition.cpp
│   ├── batch_buffer.cpp
│   ├── fixture_protocol.cpp
│   ├── fixture_state.cpp
│   ├── main.cpp
│   └── teensy/
│       ├── capture_engine_teensy.cpp
│       ├── power_supervisor_teensy.cpp
│       ├── threshold_controller_teensy.cpp
│       ├── timebase_teensy.cpp
│       └── usb_fixture_teensy.cpp
├── test/
│   ├── test_acquisition/
│   ├── test_batch_buffer/
│   ├── test_protocol/
│   └── test_trial_classification/
└── docs/
    ├── capture-routing.md
    ├── usb-t0-characterization.md
    └── firmware-state-machine.md
hardware/
├── sensor/
│   ├── sensor.kicad_sch
│   ├── sensor.kicad_pcb
│   └── sensor.kicad_pro
├── carrier/
│   ├── carrier.kicad_sch
│   ├── carrier.kicad_pcb
│   └── carrier.kicad_pro
├── sim/sensor_frontend.cir
├── bom/sensor.csv
├── bom/carrier.csv
└── qualification/
    ├── sensor-characterization.md
    └── electrical-characterization.md
fixture/
├── openscad/
│   ├── dimensions.scad
│   ├── base.scad
│   ├── bezel_stops.scad
│   ├── sensor_carriage.scad
│   ├── sensor_rail.scad
│   └── assembly.scad
├── README.md
└── qualification/remount-reproducibility.md
```

---

### Task 1: Establish host-testable firmware domain logic

**Files:**
- Create: `firmware/platformio.ini`
- Create: `firmware/include/fixture_state.h`
- Create: `firmware/include/acquisition.h`
- Create: `firmware/include/batch_buffer.h`
- Create: `firmware/src/fixture_state.cpp`
- Create: `firmware/src/acquisition.cpp`
- Create: `firmware/src/batch_buffer.cpp`
- Create: `firmware/test/test_acquisition/test_main.cpp`
- Create: `firmware/test/test_batch_buffer/test_main.cpp`

**Interfaces:**
- Consumes: v1 constants and binary layouts from `protocol/wire-v1.md` and `protocol/session-v1.md`.
- Produces: `FixtureState`, `BatchConfigV1`, `TrialCapture`, `BatchBuffer`, `AcquisitionController`.

Use this state model:

```cpp
enum class FixtureState : uint8_t {
  Booting,
  Idle,
  SelfTesting,
  Calibrating,
  Configured,
  Armed,
  Acquiring,
  Complete,
  Faulted,
};
```

- [ ] **Step 1: Write failing state-machine tests**

```cpp
TEST_CASE("cannot arm without configuration") {
  FakePlatform p;
  AcquisitionController c(p);
  CHECK(c.arm() == Error::InvalidState);
}

TEST_CASE("batch does not emit control traffic while acquiring") {
  FakePlatform p;
  AcquisitionController c(p);
  c.configure(validConfig());
  c.arm();
  c.start();
  c.onTrialComplete(validTrial());
  CHECK(p.controlWrites() == 0);
}
```

- [ ] **Step 2: Run native tests and verify failure**

Run: `pio test -d firmware -e native`

Expected: FAIL before domain types exist.

- [ ] **Step 3: Implement minimal state machine and fixed RAM buffer**

`BatchBuffer` must hold 1,000 `TrialRecordV1` equivalents plus batch metadata without dynamic allocation during acquisition. Add a compile-time size assertion and fail configuration if requested trial count exceeds 1,000.

- [ ] **Step 4: Run native tests**

Run: `pio test -d firmware -e native`

Expected: PASS for state transitions, buffer overflow prevention, reset sequencing, and preservation of invalid records.

- [ ] **Step 5: Commit**

```bash
git add firmware
git commit -m "feat: add fixture acquisition state machine"
```

---

### Task 2: Implement v1 control protocol and batch serialization

**Files:**
- Create: `firmware/include/fixture_protocol.h`
- Create: `firmware/src/fixture_protocol.cpp`
- Create: `firmware/test/test_protocol/test_main.cpp`
- Modify: `firmware/src/batch_buffer.cpp`

**Interfaces:**
- Produces: `decodeControlRequest`, `encodeControlResponse`, `serializeBatchV1`.
- Must match byte-for-byte golden vectors under `protocol/vectors/`.

- [ ] **Step 1: Import golden vectors into firmware tests**

```cpp
TEST_CASE("get fixture info request matches v1 vector") {
  auto bytes = loadFixture("protocol/vectors/wire/get_fixture_info.request.bin");
  auto request = decodeControlRequest(bytes);
  REQUIRE(request.command == CommandV1::GetFixtureInfo);
  CHECK(request.requestId == 0x11223344u);
}
```

- [ ] **Step 2: Verify failure**

Run: `pio test -d firmware -e native -f test_protocol`

Expected: FAIL before codec implementation.

- [ ] **Step 3: Implement strict codecs**

Reject unsupported major version, wrong magic, wrong lengths, unknown commands, out-of-range batch configuration, and nonzero reserved fields. Never cast an unaligned byte buffer directly to a C++ struct; use explicit little-endian reads/writes.

- [ ] **Step 4: Verify protocol compatibility**

Run: `pio test -d firmware -e native`

Expected: all protocol and trial serialization vectors match the Rust reference bytes.

- [ ] **Step 5: Commit**

```bash
git add firmware/include/fixture_protocol.h firmware/src/fixture_protocol.cpp firmware/src/batch_buffer.cpp firmware/test/test_protocol
git commit -m "feat: implement fixture protocol codec"
```

---

### Task 3: Prove Teensy timebase, five capture channels, and USB-completion hook before PCB layout

**Files:**
- Create: `firmware/include/capture_engine.h`
- Create: `firmware/src/teensy/timebase_teensy.cpp`
- Create: `firmware/src/teensy/capture_engine_teensy.cpp`
- Create: `firmware/src/teensy/usb_fixture_teensy.cpp`
- Create: `firmware/docs/capture-routing.md`
- Create: `firmware/docs/usb-t0-characterization.md`
- Modify: `firmware/platformio.ini`

**Interfaces:**
- Produces: `uint64_t monotonicTicks()`, five `CaptureChannel` instances, and `onHidInTransferComplete()` that records `T0` from the same timebase.
- Produces a verified Teensy 4.1 pin map consumed by the carrier PCB task.

- [ ] **Step 1: Add a Teensy hardware smoke firmware**

Build a mode that:

1. configures five candidate hardware capture inputs;
2. applies one common electrical edge to all five through jumper wiring;
3. records their latched ticks;
4. prints only after capture completes;
5. reports max-minus-min skew.

- [ ] **Step 2: Verify actual hardware capture routing**

Run: `pio run -d firmware -e teensy41`

Flash to Teensy 4.1 and inject at least 10,000 common edges.

Acceptance:

- every channel uses a hardware capture register, not GPIO ISR time;
- no missed captures in 10,000 edges;
- raw inter-channel skew is stable and calibratable to <=25 us;
- `capture-routing.md` records verified pin/peripheral mapping and timer clock frequency.

If five suitable channels cannot share a defensible common timebase, stop PCB layout and document the external capture hardware fallback before continuing.

- [ ] **Step 3: Hook successful HID IN completion**

Instrument the Teensy USB device transfer-completion path so the measured report's successful endpoint completion records `T0` immediately from the common monotonic timebase. Keep report queue time separately for diagnostics but never expose it as authoritative `T0`.

- [ ] **Step 4: Characterize T0 callback uncertainty**

Use external USB bus-level timing evidence and a GPIO marker emitted immediately adjacent to the `T0` read. Record sample count, min/median/max completion-to-marker delay, equipment resolution, and worst-case bound in `usb-t0-characterization.md`.

Acceptance: the characterized T0 contribution leaves the complete fixture path capable of meeting <=0.1 ms total uncertainty. If it does not, replace this path with external hardware capture rather than changing the specification.

- [ ] **Step 5: Commit**

```bash
git add firmware/src/teensy firmware/include/capture_engine.h firmware/docs firmware/platformio.ini
git commit -m "feat: establish fixture hardware timing path"
```

---

### Task 4: Implement HID gamepad plus vendor-specific bulk USB composite device

**Files:**
- Create: `firmware/include/usb_fixture.h`
- Modify: `firmware/src/teensy/usb_fixture_teensy.cpp`
- Modify: `firmware/src/main.cpp`
- Create: `firmware/docs/firmware-state-machine.md`

**Interfaces:**
- HID interface: standard gamepad consumed by Android system input stack.
- Vendor interface: bulk IN/OUT used for control/results.
- Synthetic measured control: button 1 key-down; release/reset is unmeasured.

The HID descriptor should expose 16 buttons and neutral X/Y/Rx/Ry 8-bit axes so Android recognizes a normal gamepad; only button 1 changes during v1 measured trials.

- [ ] **Step 1: Write host-side descriptor/state tests**

Test descriptor lengths, endpoint identities, and the invariant that `ARM_BATCH` is a vendor request while trial key-down is emitted only through the HID interface.

- [ ] **Step 2: Verify failure**

Run: `pio test -d firmware -e native`

Expected: FAIL until descriptors and USB adapter are implemented.

- [ ] **Step 3: Implement composite enumeration and state guarding**

The vendor interface supports all ten v1 commands. During `Acquiring`, only the HID endpoint may transmit trial traffic; vendor requests return `BUSY` without causing timing-critical transfer traffic.

- [ ] **Step 4: Verify on Android USB host**

With an Android device connected:

- OS enumerates the Teensy as a game controller;
- a generic gamepad tester receives button 1;
- a small USB-host probe can claim only the vendor interface without stealing HID input from Android;
- self-powered VBUS attach/detach behavior is correct.

- [ ] **Step 5: Commit**

```bash
git add firmware/include/usb_fixture.h firmware/src firmware/docs/firmware-state-machine.md
git commit -m "feat: add composite HID and fixture USB device"
```

---

### Task 5: Implement trial scheduler, reset sequencing, edge qualification, and integrity faults

**Files:**
- Modify: `firmware/include/acquisition.h`
- Modify: `firmware/src/acquisition.cpp`
- Create: `firmware/test/test_trial_classification/test_main.cpp`
- Modify: `firmware/src/main.cpp`

**Interfaces:**
- Produces deterministic batch behavior from `BatchConfigV1` and PRNG seed.
- Produces raw status bits matching `TrialStatusFlags`.

- [ ] **Step 1: Write failing behavioral tests**

Cover:

- randomized delays stay within 200–500 ms;
- identical seed produces identical delay sequence;
- measured key-down creates `T0` and arms sensor capture;
- unmeasured release/reset never creates a trial record;
- sensors remain disarmed during reset;
- next trial waits for baseline sanity;
- multiple edges and PWM activity are preserved;
- timeout and missing-sensor bits are preserved;
- buffer overflow, timebase discontinuity, and power fault become batch integrity faults.

- [ ] **Step 2: Verify failure**

Run: `pio test -d firmware -e native -f test_trial_classification`

- [ ] **Step 3: Implement scheduler and classification**

Use a deterministic integer PRNG seeded from `BatchConfigV1`; store the seed and actual selected delay per trial. A record is never erased because it is invalid.

- [ ] **Step 4: Run all firmware host tests**

Run: `pio test -d firmware -e native`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add firmware
git commit -m "feat: implement autonomous fixture trial acquisition"
```

---

### Task 6: Prototype and characterize one complete sensor front-end

**Files:**
- Create: `hardware/sim/sensor_frontend.cir`
- Create: `hardware/sensor/sensor.kicad_pro`
- Create: `hardware/sensor/sensor.kicad_sch`
- Create: `hardware/sensor/sensor.kicad_pcb`
- Create: `hardware/bom/sensor.csv`
- Create: `hardware/qualification/sensor-characterization.md`

**Interfaces:**
- Produces the electrical sensor module envelope and connector pinout used by carrier/mechanical design.
- Produces characterized `Rf/Cf`, fixed reverse-bias voltage, comparator hysteresis, and operating range.

Start from the approved provisional parts: SFH 203 P, OPA320, TLV3501. Use 3.3 V logic-compatible comparator output and a fixed reverse-bias network in the 2–3 V design range.

- [ ] **Step 1: Simulate the candidate TIA/comparator network**

Create an ngspice model with photodiode capacitance, photocurrent steps, OPA320 macromodel, feedback network, comparator threshold source, and parasitic input capacitance.

Acceptance before PCB fabrication:

- stable step response with no sustained oscillation;
- 10–90 electrical front-end response comfortably below 100 us;
- output remains in linear range for the characterized target photocurrent span;
- hysteresis does not materially shift 10/50/90 threshold meaning.

- [ ] **Step 2: Lay out one sensor-board revision**

Keep photodiode-to-TIA summing node extremely short, place decoupling locally, expose TIA analog monitor, DAC threshold input, digital capture output, power/ground, and sensor ID pads.

- [ ] **Step 3: Assemble and bench-test one sensor**

Test against controlled black/white Android screen states including 200-nit full-screen white and the brightest available diagnostic condition. Record measured TIA black/white voltages, saturation margin, comparator edge behavior, noise, and response time.

- [ ] **Step 4: Lock analog values only after measurements**

Update schematic/BOM with the measured final `Rf/Cf`, bias, and hysteresis values. `sensor-characterization.md` must explain why those values satisfy the range/response target; do not leave alternate unqualified population options in the production BOM.

- [ ] **Step 5: Commit**

```bash
git add hardware/sim hardware/sensor hardware/bom/sensor.csv hardware/qualification/sensor-characterization.md
git commit -m "feat: characterize photodiode sensor front end"
```

---

### Task 7: Design the five-channel carrier and self-test circuitry

**Files:**
- Create: `hardware/carrier/carrier.kicad_pro`
- Create: `hardware/carrier/carrier.kicad_sch`
- Create: `hardware/carrier/carrier.kicad_pcb`
- Create: `hardware/bom/carrier.csv`
- Create: `hardware/qualification/electrical-characterization.md`
- Create: `firmware/include/threshold_controller.h`
- Create: `firmware/include/power_supervisor.h`
- Create: `firmware/src/teensy/threshold_controller_teensy.cpp`
- Create: `firmware/src/teensy/power_supervisor_teensy.cpp`

**Interfaces:**
- Five identical sensor connectors: power, ground, analog monitor, threshold reference, digital capture.
- Socketed Teensy 4.1.
- At least five independently programmable threshold channels; use a multichannel DAC such as DAC7578 or a proven equivalent with >=12-bit resolution.
- Teensy ADC reads the five non-timing-critical analog monitor lines.

- [ ] **Step 1: Add firmware fakes/tests for thresholds and power faults**

Verify per-channel DAC codes, threshold 10/50/90 derivation, calibration persistence in RAM, supply-fault latching, and batch abort on undervoltage.

- [ ] **Step 2: Design carrier power and protection**

Requirements:

- external regulated 5 V input;
- local rails sized for Teensy + five sensors + DAC/reference/self-test LED;
- Android VBUS sense only;
- explicit backfeed prevention;
- brownout/undervoltage supervisor visible to firmware;
- common USB ground;
- electrical common-edge self-test distribution to all capture channels;
- fast reference LED drive for optical self-test.

- [ ] **Step 3: Run ERC/DRC and review pin mapping against verified firmware routing**

Run KiCad ERC/DRC with zero unresolved errors. Carrier capture pins must exactly match `firmware/docs/capture-routing.md`.

- [ ] **Step 4: Assemble and characterize the full electronics**

Run at least 10,000 electrical self-test edges and record calibrated inter-channel offsets. Acceptance: <=25 us calibrated skew, no missed channels, no fixture power fault, and stable sensor black/white readings.

- [ ] **Step 5: Commit**

```bash
git add hardware/carrier hardware/bom/carrier.csv hardware/qualification/electrical-characterization.md firmware/include firmware/src/teensy
git commit -m "feat: add five-channel fixture carrier"
```

---

### Task 8: Build the adjustable five-sensor mechanical fixture

**Files:**
- Create: `fixture/openscad/dimensions.scad`
- Create: `fixture/openscad/base.scad`
- Create: `fixture/openscad/bezel_stops.scad`
- Create: `fixture/openscad/sensor_carriage.scad`
- Create: `fixture/openscad/sensor_rail.scad`
- Create: `fixture/openscad/assembly.scad`
- Create: `fixture/README.md`
- Create: `fixture/qualification/remount-reproducibility.md`

**Interfaces:**
- Consumes: final sensor PCB outline/connector location from Task 6.
- Produces: adjustable bezel-referenced screen rectangle and five movable sensor positions at the normalized coordinates.

- [ ] **Step 1: Encode mechanical dimensions parametrically**

`dimensions.scad` must define supported screen/chassis width and height ranges, sensor PCB envelope, optical-boot compression allowance, rail clearances, and printer tolerance. Do not hard-code one handheld.

- [ ] **Step 2: Model base, stops, rails, and sensor carriage**

The fixture must:

- register independently against left/right/top/bottom bezel/chassis references;
- support the device underneath;
- allow sensor carriage coordinates to be computed from the calibrated active-screen rectangle;
- prevent sticks/buttons from being load-bearing fixture references;
- let opaque boots lightly contact glass without panel clamping force.

- [ ] **Step 3: Render all parts and assembly**

Run OpenSCAD CLI to render each printable part and the full assembly. Resolve non-manifold/intersection errors before printing.

- [ ] **Step 4: Print and perform three full remount cycles**

Use one stable reference device. Record setup procedure, alignment-marker verification, sensor coordinate error, and any mechanical revisions in `remount-reproducibility.md`. Latency reproducibility itself is completed in the final qualification plan after the Android/analyzer stack exists.

- [ ] **Step 5: Commit**

```bash
git add fixture
git commit -m "feat: add adjustable five-sensor fixture"
```

---

### Task 9: Complete fixture self-test, threshold calibration, and batch download firmware

**Files:**
- Modify: `firmware/src/main.cpp`
- Modify: `firmware/src/teensy/threshold_controller_teensy.cpp`
- Modify: `firmware/src/acquisition.cpp`
- Modify: `firmware/src/fixture_protocol.cpp`
- Create: `firmware/test/test_calibration/test_main.cpp`

**Interfaces:**
- Fully implements all ten v1 control commands.
- Produces in-RAM batch bytes matching `protocol/session-v1.md`.

- [ ] **Step 1: Write calibration/self-test protocol tests**

Cover:

- black/white acquisition per channel;
- independently derived 10/50/90 threshold DAC codes;
- threshold range rejection;
- pre-batch black/white sanity check;
- electrical self-test result structure;
- optical self-test result structure;
- post-batch chunked result download;
- `ABORT_BATCH` state cleanup.

- [ ] **Step 2: Verify failure**

Run: `pio test -d firmware -e native`

- [ ] **Step 3: Implement commands and calibration state**

Calibration updates are forbidden while `Acquiring`. Result download is forbidden before `Complete`. Raw trial buffer contents are never rewritten by threshold correction.

- [ ] **Step 4: Run host and Teensy smoke validation**

Run:

```bash
pio test -d firmware -e native
pio run -d firmware -e teensy41
```

Then execute electrical and optical self-tests on the assembled fixture and confirm result bytes decode with the Rust `slt-protocol` crate.

- [ ] **Step 5: Commit**

```bash
git add firmware
git commit -m "feat: complete fixture calibration and batch firmware"
```

---

## Completion Gate

Do not call the fixture publication-grade yet. This plan is complete when:

- firmware native tests pass;
- Teensy firmware builds reproducibly;
- Android enumerates HID and vendor interfaces simultaneously;
- five hardware capture channels are proven on real Teensy hardware;
- T0 completion timing has a documented uncertainty bound compatible with <=0.1 ms total fixture uncertainty;
- one complete sensor front-end is characterized and the five-channel electronics pass electrical/optical self-test;
- calibrated inter-channel skew is <=25 us;
- 1,000 raw records fit and remain in RAM until download;
- the adjustable fixture is printable, aligns all five sensors, and survives three remount cycles mechanically.

Full 0.1 ms system uncertainty, 0.5 ms session repeatability, remount latency reproducibility, and actual portrait/landscape measurement are finalized in the end-to-end qualification plan.