# Screen Latency Measurement Tool — v1 Design

Date: 2026-08-31
Status: Approved design, pending implementation plan

## 1. Purpose

Build a repeatable measurement instrument for Android gaming handhelds that can answer two separate questions without conflating them:

1. **Does rotating a portrait-native Android display into landscape add real presentation/input latency?**
2. **How does a portrait-native handheld compare with a landscape-native handheld in practical button-to-photon latency and scanout behavior?**

The instrument must distinguish a genuine pipeline penalty from a simple 90-degree rotation of physical scanout direction.

The authoritative result is measured entirely in fixture hardware from a completed synthetic USB HID input transaction to a physical luminance transition on the display. Android timestamps are diagnostic evidence only.

## 2. Core Measurement Definitions

### 2.1 Authoritative input timestamp

`T0` is the timestamp of **successful USB HID IN transaction completion** for the synthetic gamepad report.

The fixture must not use the time at which firmware merely queues the HID report. USB host polling delay before transfer is intentionally excluded from the authoritative metric because the primary experiment isolates Android input processing, rendering, composition/rotation, and display scanout rather than controller polling behavior.

### 2.2 Photon timestamps

Five photodiode channels measure luminance transitions at normalized screen positions:

- Top: `(0.50, 0.10)`
- Left: `(0.10, 0.50)`
- Center: `(0.50, 0.50)`
- Right: `(0.90, 0.50)`
- Bottom: `(0.50, 0.90)`

Each comparator edge is latched by hardware input capture in the same monotonic hardware time domain as `T0`.

For sensor `i`:

`L_i = T_sensor_i - T0`

### 2.3 Authoritative luminance crossing

The standard latency metric uses the **50% luminance crossing (`T50`)** between that sensor's calibrated black and white levels.

Diagnostic runs may also measure `T10` and `T90` so pixel transition behavior can be separated from presentation latency.

For black-to-white:

- `T10`: 10% of calibrated transition amplitude
- `T50`: 50%
- `T90`: 90%

For white-to-black, the equivalent descending thresholds apply.

Pixel response can then be characterized independently, for example:

`T10_to_90 = T90 - T10`

### 2.4 Headline latency metric

The published headline for a condition is:

**Center-screen median HID-completion-to-T50 button-to-photon latency.**

Minimum latency is retained but is not the headline result.

### 2.5 Spatial scanout metrics

Retain signed values:

`Delta_H = T_right - T_left`

`Delta_V = T_bottom - T_top`

The signs identify physical scan direction. The magnitudes quantify the spatial scanout interval.

A horizontal latency gradient on a sideways portrait panel is **not by itself an input-latency penalty**. It may only indicate that physical scanout has rotated 90 degrees.

### 2.6 Rotation penalty

On a portrait-native device, run matched native-portrait and rotated-landscape configurations.

The primary same-device rotation metric is:

`Delta_rotation = median(L_center_landscape) - median(L_center_portrait)`

A meaningful center-distribution shift is evidence of an added presentation/composition penalty. A change only in `Delta_H` versus `Delta_V` is evidence of rotated scanout geometry, not added center latency.

## 3. Measurement Invariants

The implementation must preserve these invariants:

1. `T0` and all five photon timestamps come from one fixture-controlled monotonic hardware time domain.
2. Android clocks never participate in the authoritative latency calculation.
3. USB host polling wait before the successful HID transaction is excluded from the authoritative metric.
4. Raw fixture timestamps are immutable evidence; calibration corrections and derived values are stored separately.
5. All raw trials are retained, including invalid trials.
6. Invalid trials are explicitly classified rather than silently dropped.
7. Cross-device authoritative comparisons normalize display luminance to a measured 200 +/- 5 cd/m^2.
8. Portrait-vs-landscape conclusions come from fixed-refresh measurements; VRR is diagnostic only.
9. Same-device orientation testing is the strongest evidence for a true rotation penalty.
10. A rotated spatial scanout gradient must never be described as an inherent input-latency penalty without a corresponding center-latency shift.
11. Acquisition and analysis are separable so analysis can evolve without rerunning hardware measurements.
12. Engineering-mode results can never be mistaken for qualification-mode results.

## 4. System Architecture

The project is a single monorepo containing:

```text
screen-latency-tool/
├── android/        # Android acquisition/test app
├── firmware/       # Teensy 4.1 firmware
├── analyzer/       # Cross-platform reference CLI/analyzer
├── hardware/       # Schematics, PCB, BOM
├── fixture/        # 3D-printable adjustable mechanical fixture
├── protocol/       # USB and dataset contracts
├── qualification/  # Fixture and measurement qualification procedures
└── docs/
```

The USB protocol and exported session schema are explicit versioned contracts shared by firmware, Android, and the analyzer.

### 4.1 Language stack

- Android orchestration/UI: **Kotlin**
- Android native input and rendering: **C++**
- Teensy firmware: **C++**
- Cross-platform analyzer/reference tooling: **Rust**

This intentionally uses the best-fit language per subsystem rather than forcing a single language across the instrument.

## 5. Hardware Fixture

### 5.1 Measurement controller

Use a **Teensy 4.1** as the v1 controller.

The Teensy remains a replaceable/socketed module on a custom carrier PCB rather than integrating the MCU onto a custom board.

Responsibilities:

- synthetic USB HID gamepad
- vendor-specific USB control channel
- batch orchestration
- common hardware timing domain
- HID completion timestamp capture
- five sensor hardware input-capture channels
- programmable comparator threshold control
- slow calibration ADC acquisition
- self-test control
- power supervision
- in-RAM acquisition buffer

### 5.2 USB topology

One self-powered composite USB device presents two independent interfaces:

1. **Standard USB HID gamepad interface**
   - consumed by the normal Android input stack
   - not claimed by the test application
   - carries the synthetic measured input event

2. **Vendor-specific bulk control interface**
   - claimed by the Android test application
   - used for configuration, self-test, calibration, batch arming, status, and post-run dataset retrieval

There must be no control-interface traffic during timing-critical acquisition beyond the HID traffic under measurement.

Representative control operations include:

- `GET_FIXTURE_INFO`
- `RUN_ELECTRICAL_SELF_TEST`
- `RUN_OPTICAL_SELF_TEST`
- `CALIBRATE_SENSORS`
- `SET_THRESHOLDS`
- `CONFIGURE_BATCH`
- `ARM_BATCH`
- `GET_BATCH_STATUS`
- `DOWNLOAD_RESULTS`
- `ABORT_BATCH`

The exact protocol representation is an implementation-plan concern, but it must be versioned and reject unsupported versions explicitly.

### 5.3 Fixture power

The fixture is **externally powered/self-powered**.

Requirements:

- regulated external 5 V input
- local regulated analog/digital rails
- Android VBUS sense for USB attachment behavior
- explicit prevention of 5 V backfeed into the Android handheld
- common USB ground; v1 is not a galvanically isolated USB design
- fixture supply health supervision
- undervoltage/brownout is a batch-level integrity fault

The handheld must not meaningfully power the fixture during authoritative testing.

### 5.4 Sensor topology

Use five identical movable sensor modules.

Each module contains:

- ams OSRAM **SFH 203 P** PIN photodiode as the provisional v1 detector
- modest fixed photodiode reverse bias, provisionally around 2–3 V and finalized by characterization
- **OPA320** transimpedance amplifier
- fixed, identical TIA gain network across all five channels
- **TLV3501** comparator
- small analog comparator hysteresis
- programmable comparator reference input
- TIA analog monitor/test point
- digital comparator output to Teensy capture input
- local decoupling
- opaque optical interface/boot contacting the display glass lightly

Part numbers are provisional architecture choices, not immutable constraints. Bench characterization may replace a component if the replacement preserves or improves the defined measurement behavior.

### 5.5 TIA gain strategy

Use one fixed per-channel gain for v1.

The final `Rf/Cf` network is selected from prototype characterization so that:

- 200-nit full-screen white produces a comfortable mid-range signal
- expected maximum handheld brightness does not saturate the front end during diagnostics
- all five channels share the same nominal transfer function
- the analog bandwidth is comfortably faster than display behavior being measured

Do not add switchable or programmable gain unless prototype evidence proves fixed gain cannot span the required operating range.

### 5.6 Photodiode operating mode

Use modest fixed reverse bias rather than photovoltaic zero-bias mode to reduce detector capacitance and preserve fast, clean threshold crossings.

The bias is not user-adjustable during normal operation.

### 5.7 Sensor placement mechanics

Use an **adjustable bezel-referenced rigid overlay** rather than device-specific overlays or freehand sensor placement.

Requirements:

- adjustable left/right/top/bottom registration stops
- device support underneath so thickness does not change alignment
- sensor carriages positioned from a calibrated screen rectangle
- normalized sensor coordinates defined in Section 2.2
- opaque foam or light-tube boots that lightly contact the cover glass
- no meaningful clamping force through the display panel
- Android alignment markers used as verification, not as the only mechanical reference
- fixture geometry designed to be 3D-printable

### 5.8 Sensor-front-end placement

The photodiode, TIA, and comparator live directly on each movable sensor PCB.

Only digital comparator edges traverse the adjustable fixture to the central carrier. A non-timing-critical analog monitor signal may be brought back for calibration/diagnostics.

This avoids making raw photodiode current or sensitive TIA inputs depend on cable length and fixture position.

## 6. Timing Architecture

### 6.1 Common monotonic timebase

Use one common monotonic hardware timebase for:

- `T0` USB HID completion
- top capture
- left capture
- center capture
- right capture
- bottom capture

If the hardware counter width is insufficient, firmware extends it to an equivalent monotonic 64-bit timeline.

Raw integer ticks are stored. Conversion to microseconds occurs after acquisition.

`T0` must correspond to the USB controller's successful on-bus HID IN completion/ACK event as closely as the Teensy hardware permits. If firmware must read the common timer in response to a USB completion interrupt rather than receiving a hardware-latched timestamp, that interrupt/callback path must be characterized against external bus-level evidence and its worst-case uncertainty included in the fixture's <=0.1 ms budget. A software callback timestamp with uncharacterized scheduling latency is not sufficient for publication-grade operation.

### 6.2 Photodiode edge capture

Comparator transitions use **hardware timer input capture**.

Firmware interrupts may retrieve captured values afterward, but ISR entry latency must not define the timestamp.

If Teensy pin/peripheral routing cannot provide five clean independent hardware capture inputs referenced to the common timebase, the implementation may introduce dedicated external capture logic; it must not silently fall back to jitter-prone software timestamping for authoritative measurements.

### 6.3 Fixture uncertainty target

Design target:

- timestamp resolution: <= 10 us
- calibrated inter-channel skew: <= 25 us
- total fixture timing uncertainty: <= 0.1 ms

The implementation must produce evidence supporting these bounds before publication-grade use.

## 7. Comparator Edge Qualification and PWM Handling

A bare first-edge comparator result is insufficient for all displays, especially PWM-modulated OLED/LCD conditions.

Use three layers:

1. **Analog hysteresis**
   - suppress noise/chatter around the threshold
   - remain much smaller than the 10/50/90% threshold spacing

2. **Armed capture window**
   - sensor channels become valid for a trial only after successful HID transfer establishes `T0`
   - pre-input transitions cannot qualify

3. **Repeated-edge diagnostics**
   - first qualified crossing is the nominal candidate
   - additional crossings within the observation window are counted and optionally timestamped in a bounded diagnostic structure
   - repeated crossings produce explicit status such as `MULTIPLE_EDGE` or `PWM_ACTIVITY`

If PWM makes the selected threshold fundamentally ambiguous for an authoritative condition, the condition is flagged unsuitable rather than forced into a clean-looking latency result.

## 8. Self-Test and Calibration

### 8.1 Electrical timing self-test

The carrier provides an electrical loopback capable of applying the same edge to all five capture channels.

Purpose:

- verify capture operation
- quantify channel offsets/skew
- verify common timebase behavior
- verify firmware timestamp handling

Target after calibration: <=25 us inter-channel skew.

### 8.2 Optical self-test

Provide a controlled fast reference light source capable of stimulating all five complete sensor chains.

Purpose:

- validate photodiode response
- validate TIA behavior
- validate comparator operation
- validate programmable thresholds
- detect channel degradation that electrical loopback cannot see

Electrical self-check runs before publishable sessions. Full optical qualification is periodic and required after relevant hardware changes rather than before every handheld.

### 8.3 Threshold calibration

Thresholds are independently programmable per sensor.

For each sensor, measure calibrated black and white levels and derive at least:

- threshold 10%
- threshold 50%
- threshold 90%
- DAC codes used

Calibration records preserve:

- black level
- white level
- threshold values
- DAC codes
- sensor identity/revision
- TIA gain revision
- calibration timestamp

### 8.4 Calibration cadence

Calibration is tied to the **physical/display condition**, not blindly to every software batch.

Recalibrate when materially relevant conditions change, including:

- brightness/luminance setting
- HDR/SDR mode
- full-screen versus patch stimulus
- major display mode change
- device remount/reposition
- sensor fixture reposition
- explicit 10/50/90 diagnostic threshold mode

Do not require recalibration solely because input path changes between native/framework or renderer changes between Vulkan/GLES when the rendered black/white values and physical display condition remain unchanged.

Before each batch, perform a short sanity check against expected black/white levels. Excessive drift blocks qualification until recalibration.

## 9. Absolute Luminance Calibration

### 9.1 Instrument

Use the owner's **Calibrite Display Pro HL** as an external manual luminance reference for v1.

The colorimeter is a measurement reference only. Do not create or apply ICC profiles or calibration LUTs to the Android handheld as part of this workflow.

Direct Android USB integration with the Calibrite is a **future investigation** and explicitly outside v1 implementation scope.

### 9.2 Authoritative luminance target

Cross-device authoritative testing uses:

**200 +/- 5 cd/m^2 (nits)**

Use actual measured luminance, never Android brightness-slider percentage, as the normalized condition.

Record both:

- requested/app window brightness value
- measured luminance

### 9.3 Calibration stimulus

Use the same electrical/optical load as the authoritative latency test:

- SDR
- full-screen RGB white `255,255,255`
- full-screen black as the paired dark state

This avoids calibrating a small white window while testing a full-screen white condition, which could produce different OLED ABL/power behavior.

### 9.4 OLED handling

Do not leave OLED panels on full-screen white indefinitely during luminance calibration.

Use a repeating calibration cadence approximately equivalent to:

- black: 2 s
- white: 1 s for measurement
- black: 2 s
- repeat as needed

The exact UI workflow may be refined, but it must limit avoidable thermal/ABL drift and use the same full-screen white state as the latency stimulus.

### 9.5 Calibration workflow

1. Mount the handheld and configure the final refresh, orientation, presentation, and performance state for the condition.
2. Disable adaptive brightness and set brightness near the expected 200-nit value.
3. Enter luminance-calibration mode and run the representative black/white workload until the condition has satisfied the minimum 5-minute warm-up requirement.
4. Display the defined full-screen calibration stimulus.
5. Place the Display Pro HL flat against screen center.
6. Adjust app/window brightness until measured luminance is 200 +/- 5 nits.
7. Record requested brightness and measured luminance as the absolute reference measurement.
8. Remove the colorimeter without changing brightness or display state.
9. Install/verify the five-sensor overlay.
10. Perform sensor threshold calibration.
11. Use the calibrated center sensor's analog white-level response as a **relative drift monitor only** during the subsequent 3-minute stability window.
12. Proceed to acquisition only after the stability gate passes.

A previously saved per-device brightness value may be used as a starting point, but every publication-grade session must re-verify actual luminance with the Display Pro HL. The photodiode fixture is not an absolute luminance meter and must not replace that measurement. If relative luminance drifts beyond the allowed stability bound, brightness/display state changes, or another intervention can invalidate the absolute reference, the Display Pro HL measurement must be repeated and sensor thresholds recalibrated before qualification.

## 10. Android Application

### 10.1 Product scope

v1 is an **internal engineering bench tool**, not a public consumer application.

Consequences:

- sideloaded APK is acceptable
- ADB-assisted qualification is acceptable
- engineering-focused UX is acceptable
- public onboarding, broad hardware support UX, migration polish, and consumer documentation are out of scope

The protocol and dataset contracts should nevertheless be clean enough to support a future public/reproducible version without redesigning the measurement model.

### 10.2 Compatibility floor

- minimum Android version: **Android 11 / API 30**
- target current Android SDK at implementation time

### 10.3 Kotlin responsibilities

- fixture discovery and vendor-interface ownership
- qualification workflow
- engineering/qualification mode UX
- display mode/orientation control
- brightness override and luminance confirmation workflow
- session metadata
- batch orchestration
- result retrieval
- display-state capture through public Android APIs
- export/session packaging
- framework `KeyEvent` path

### 10.4 C++ native engine responsibilities

- native input path
- Vulkan renderer
- OpenGL ES renderer
- shared stimulus state machine
- continuous game-style render loop
- diagnostic event-driven render loop
- lightweight app-side timing annotations

### 10.5 Input/render matrix

Support four diagnostic combinations:

1. Native input + Vulkan
2. Native input + OpenGL ES
3. Framework `KeyEvent` input + Vulkan
4. Framework `KeyEvent` input + OpenGL ES

**Native input + Vulkan** is the authoritative path.

The other three are diagnostic controls for identifying API-specific or managed-dispatch effects.

### 10.6 Stimulus modes

Support:

1. **Full-screen black <-> white**
   - authoritative
   - maximizes sensor signal
   - all five sensors observe the same logical frame transition

2. **Five isolated sensor patches**
   - diagnostic only
   - used to investigate damage-region/compositor/local-response behavior

### 10.7 Render-loop policy

Authoritative gaming latency uses a **continuous game-style render loop**.

The input callback changes shared state only. The normal render loop observes the newest state at its defined sampling point and presents accordingly.

Do not special-case a button press into an immediate render for the authoritative result.

Also support an **event-driven diagnostic mode** that submits in response to input. The difference between continuous and event-driven results can help quantify game-loop opportunity delay.

### 10.8 GPU/CPU workload

Authoritative orientation testing uses intentionally minimal CPU/GPU workload to isolate input/presentation/display architecture rather than SoC performance.

Diagnostic synthetic-load modes may target approximately:

- 25% of frame budget
- 50%
- 75%
- 90%

Calibrate workload per device/frame period rather than using identical shader complexity across unequal SoCs.

### 10.9 Presentation policy

Authoritative rendering uses a controlled low-latency VSync path:

- fixed refresh
- VSync synchronized
- no intentional frame queueing
- minimize frames in flight where supported
- same logical presentation behavior across Vulkan/GLES diagnostics
- record the actual selected swapchain/presentation configuration

Diagnostic modes may vary presentation mode, swapchain image count, GLES swap interval, or deliberate queue depth.

A comparison is not valid if supposedly matched configurations silently use materially different presentation semantics without being identified.

## 11. Display State and Orientation Evidence

Do not infer physical/native orientation from marketing specifications or app-visible dimensions alone.

### 11.1 APK-captured state

Every batch records all relevant public Android display state available to the app, including where available:

- logical width/height
- active display mode
- requested orientation
- effective rotation
- refresh rate
- renderer/presentation configuration

### 11.2 ADB qualification evidence

For orientation investigations, capture and associate relevant ADB diagnostics such as display and SurfaceFlinger state.

The exact parsing is an implementation concern, but qualification evidence must be sufficient to distinguish cases such as:

- hardware-composer rotation
- GPU-composited rotation
- scaling plus rotation
- unexpected display mode changes

ADB capture is required when establishing or investigating orientation behavior, but need not be mandatory for every future routine review run once behavior is characterized.

## 12. Refresh Rate and VRR Methodology

### 12.1 Authoritative fixed-refresh tests

Every handheld receives:

1. **Normalized 60 Hz run**, where 60 Hz is supported.
2. **Native/maximum relevant gaming refresh run**.
3. Additional matched refresh rates when useful for direct device comparisons, e.g. 120 Hz on two devices that both support 120 Hz.

This separates orientation effects from basic refresh-period differences.

### 12.2 VRR

VRR/adaptive refresh is **disabled for authoritative portrait-vs-landscape conclusions**.

VRR is a separate diagnostic mode. When enabled diagnostically, record actual presentation intervals and treat dynamic timing behavior as part of that diagnostic result, not as directly comparable to the fixed-refresh baseline.

## 13. Device Power and Thermal State

### 13.1 Performance state

For same-device orientation comparisons, both orientations must use exactly the same power/performance state.

For cross-device authoritative display comparisons, default to a defined low-variance state:

- balanced/normal OEM performance mode
- no battery saver
- battery powered unless external power is required for the display mode under test
- roughly 50–80% battery when practical
- no thermal throttling
- minimal synthetic workload

Performance/gaming preset runs are diagnostic unless explicitly promoted by a future methodology revision.

Record available state including:

- performance preset
- battery percentage
- charging state
- Android thermal state
- available CPU/GPU frequency information
- available device temperatures before/after batch

### 13.2 Warm-up and stability qualification

Before an authoritative condition:

- minimum warm-up: **5 minutes** before the final Display Pro HL reference measurement
- absolute luminance is then verified at **200 +/- 5 nits** with the Display Pro HL
- after installing/calibrating the five-sensor overlay, evaluate a **3-minute stability window**
- temperature trend: less than 1 C over the stability window for meaningful sensors where available
- relative center-sensor white-level drift during the stability window <=2% from its post-Calibrite calibrated reference
- no thermal-throttling indication
- refresh rate unchanged
- brightness setting unchanged
- performance state unchanged

The fixture's photodiodes monitor relative post-calibration drift only; they do not establish or certify the 200-nit absolute value. If relative drift exceeds 2%, brightness or display state changes, or an intervention can invalidate the absolute reference, the condition returns to Display Pro HL verification and sensor recalibration.

If temperature telemetry is unavailable or unreliable, qualification may rely on the remaining controls plus minimum warm-up; private/vendor temperature APIs are not mandatory.

If the conditions are not satisfied, stabilization continues rather than silently weakening the gate.

## 14. Trial Orchestration

### 14.1 Teensy-autonomous batches

Android configures and arms a batch, then the Teensy owns the timing-critical trial sequence.

Per trial:

1. begin from the configured baseline luminance state for the directional condition
2. wait the randomized inter-trial interval with sensors disarmed
3. queue the measured synthetic HID key-down/state-change report
4. on successful HID transaction completion, latch `T0` and arm/qualify the sensor capture window
5. capture five comparator timestamps for the baseline-to-target transition
6. disarm the measured capture window after the trial completes or times out
7. issue an **unmeasured HID release/reset transition** that returns the app to the configured baseline luminance state
8. wait until the reset has presented and the baseline sensor sanity condition is satisfied
9. record the trial entirely in RAM and proceed to the next randomized interval

Black-to-white and white-to-black are separate authoritative conditions. For black-to-white, measured key-down transitions black to white and the unmeasured release/reset returns to black; for white-to-black, the polarity is inverted. Reset transitions never supply `T0`, never count as measured trials, and occur while authoritative sensor capture is disarmed.

Android does not command each individual trial.

### 14.2 Randomization

Use refresh-phase-decorrelated inter-trial spacing, initially approximately **200–500 ms**.

Requirements:

- PRNG seed stored per batch
- exact actual inter-trial intervals stored
- avoid a fixed cadence that can phase-lock to common refresh periods
- verify post-run that latency observations are not pathologically clustered by synchronization behavior

Do not claim a perfect uniform refresh-phase distribution merely from the intended randomization; preserve the evidence required to inspect it.

### 14.3 Trial count

Each authoritative batch contains **1,000 trials**.

Each authoritative condition uses **three independent qualified batches**, producing 3,000 raw trials before invalid-trial filtering.

The three center-screen T50 medians must provisionally span no more than **0.5 ms**. A larger disagreement means the condition is unstable and requires investigation rather than simple averaging.

## 15. Acquisition Buffering

During acquisition, do not stream per-trial telemetry to Android.

The Teensy stores the complete batch in RAM. Only after the final trial does Android download the dataset over the vendor-specific bulk interface.

The batch record must preserve at least:

- trial ID
- HID sequence/state identity
- scheduled/randomized delay ticks
- `T0` HID completion ticks
- top/left/center/right/bottom capture ticks or missing markers
- capture flags/status
- transition direction
- repeated-edge/PWM diagnostic information in bounded form

This avoids control-interface traffic perturbing subsequent USB scheduling.

## 16. Trial and Batch Validity

### 16.1 Trial preservation

Every trial is retained and explicitly classified.

Representative statuses include:

- `VALID`
- `MISSING_TOP`
- `MISSING_LEFT`
- `MISSING_CENTER`
- `MISSING_RIGHT`
- `MISSING_BOTTOM`
- `MULTIPLE_EDGE`
- `PWM_ACTIVITY`
- `EARLY_EDGE`
- `TIMEOUT`
- `USB_TRANSFER_FAILURE`
- `UNEXPECTED_HID_STATE`

The exact enum may be refined, but invalid evidence must remain inspectable.

### 16.2 Authoritative statistics

Authoritative statistics use only trials that satisfy the defined validity rules.

Invalid trials are never deleted from raw data.

### 16.3 Batch qualification

A 1,000-trial batch qualifies when:

- overall invalid rate <=1%
- center sensor valid rate >=99.5%
- no batch-level integrity fault occurred

### 16.4 Zero-tolerance batch integrity faults

Any of the following fails the complete batch regardless of valid trial count:

- USB re-enumeration/disconnect
- monotonic timebase discontinuity
- fixture undervoltage/brownout
- threshold/calibration state corruption
- display refresh-rate change
- Android app lifecycle/background interruption affecting the test
- renderer/swapchain recreation during acquisition
- unexpected display mode/orientation change
- duplicate/unexpected HID generation for a trial
- firmware FIFO/RAM overflow

The implementation may add additional integrity faults when needed; it must not weaken these without an explicit methodology change.

## 17. Repeatability and Reproducibility

### 17.1 Electronic/session repeatability

For routine authoritative testing:

1. mount fixture once
2. align and calibrate once per applicable display condition
3. run all three 1,000-trial batches without moving the fixture

This measures batch/session repeatability without mixing mechanical repositioning into each batch.

### 17.2 Mechanical remount reproducibility

Qualify mechanical reproducibility separately and periodically:

1. fully remove fixture
2. reinstall using only defined bezel registration
3. re-align/verify and recalibrate
4. acquire reference batch
5. repeat for at least three mount cycles

Provisional v1 target: center-median variation after full remount <=0.5 ms, while spatial scanout deltas remain consistent within characterized uncertainty.

## 18. Authoritative Test Matrix

The routine matrix is intentionally tiered rather than exhaustive.

### 18.1 Required authoritative runs per handheld

At minimum:

#### Normalized 60 Hz

- 200 +/- 5 nits
- fixed 60 Hz where supported
- VRR off
- Native input + Vulkan
- continuous game-style render loop
- minimal CPU/GPU workload
- full-screen transition
- black-to-white condition
- white-to-black condition
- T50 threshold
- 3 x 1,000 trials

#### Native/high refresh

Same configuration at the device's native/maximum relevant gaming refresh rate.

#### Matched comparison rates

Add additional fixed matched rates, such as 120 Hz, when two comparison devices both support them and the result helps isolate display architecture.

### 18.2 Diagnostic matrix

Run targeted diagnostics only when needed:

- Native input + GLES
- Framework input + Vulkan
- Framework input + GLES
- event-driven render loop
- 25/50/75/90% calibrated workload
- T10/T50/T90 threshold variants
- isolated patches
- VRR
- additional refresh rates
- balanced versus performance preset
- optional Perfetto capture
- display brightness sweeps at 25%, 50%, 100% device brightness

The brightness sweep is diagnostic and does not replace the 200-nit normalized authoritative condition.

## 19. Same-Device Portrait vs. Landscape Experiment

For a portrait-native handheld, conduct matched runs under the same physical and software state:

- same device
- same fixture mounting
- same sensor calibration when still valid
- same measured luminance
- same fixed refresh rate
- same renderer/input path
- same performance state
- same thermal/stability qualification

Run:

1. native portrait logical presentation
2. rotated landscape logical presentation

Compare:

- center T50 distribution and median
- left/center/right timings
- top/center/bottom timings
- `Delta_H`
- `Delta_V`
- batch repeatability
- Android display state
- ADB/SurfaceFlinger/HWC evidence

Interpretation rule:

- center median unchanged, spatial gradient rotates: **no demonstrated rotation latency penalty; scanout geometry rotated**
- center median shifts materially under otherwise matched conditions: **candidate rotation/composition/presentation penalty**, requiring diagnostic escalation before attributing root cause

Do not attribute a shift specifically to SurfaceFlinger, HWC, GPU composition, or panel TCON behavior without supporting diagnostic evidence.

## 20. Cross-Device Comparison

Cross-device comparisons answer the practical reviewer question rather than proving causality.

Match where possible:

- measured 200 +/- 5 nits
- fixed 60 Hz normalized condition
- additional matched fixed rates
- same APK build
- same fixture firmware/hardware revision
- same sensor geometry
- same input/render path
- same authoritative presentation policy
- balanced/normal power state
- stable/no-throttle thermal state

Report differences but acknowledge that cross-device results may contain SoC, Android build, display-controller, panel, TCON, driver, and OEM policy differences.

Use same-device orientation testing, not cross-device comparison alone, for causal claims about rotation overhead.

## 21. Android-Side Timing Diagnostics

Record lightweight Android/application timestamps on every run where reliable, including relevant values such as:

- input event timestamp supplied by Android
- app callback receive time
- renderer state-change time
- frame submission timing
- presentation timing where reliably exposed by Vulkan/GLES/platform APIs

These timestamps do **not** replace photodiode timing.

### 21.1 Clock domains

Android and Teensy clocks remain independent.

Routine correlation uses:

- trial ID
- HID sequence/state identity

Do not fabricate sub-millisecond cross-clock precision from USB request/response timing.

Optional clock synchronization or Perfetto correlation may be added for deeper investigation, with its uncertainty explicitly characterized.

### 21.2 Perfetto

Perfetto/system tracing is investigation-only, not part of routine authoritative acquisition. Heavy tracing must not silently become a prerequisite for baseline results.

## 22. Qualification and Engineering Modes

### 22.1 Qualification mode

Strictly gates publication-grade runs.

The application blocks acquisition when a mandatory condition it can observe is not satisfied, including as applicable:

- fixture self-test failure
- sensor calibration failure
- unconfirmed 200-nit luminance calibration
- unstable refresh/orientation
- invalid presentation configuration
- warm-up/stability failure
- thermal throttling indication
- unhealthy fixture power/USB state

Operator-confirmed external measurements, such as Display Pro HL luminance, are explicit recorded qualification items.

Exported sessions are explicitly marked as qualified only after all gates pass.

### 22.2 Engineering mode

Allows intentionally invalid or experimental runs.

All violated qualification conditions are recorded. Engineering-mode sessions must be unambiguously marked and must never be interpreted as qualified publication data.

## 23. Session Data Model and Export

Android owns acquisition/session packaging. Raw data is authoritative; summaries are derived convenience artifacts.

Representative session layout:

```text
session/
├── manifest.json
├── device.json
├── fixture.json
├── calibration.json
├── display.json
├── configuration.json
├── batches/
│   ├── 001/
│   │   ├── trials.bin
│   │   └── android-events.json
│   ├── 002/
│   └── 003/
├── diagnostics/
│   └── adb/
│       ├── dumpsys-display.txt
│       └── surfaceflinger.txt
└── summary.json
```

The implementation plan may refine names and binary encoding, but the exported package must preserve these conceptual categories:

- session identity/schema version
- DUT identity and Android build information
- fixture hardware/firmware revision
- calibration evidence
- display/orientation/refresh evidence
- exact test configuration
- raw trial evidence
- Android diagnostic events
- optional ADB/Perfetto diagnostics
- qualification status and reasons
- derived summary

Raw session data must be sufficient for a future analyzer version to recompute all derived publication metrics without rerunning the device.

## 24. Reference Analyzer

Build a **cross-platform Rust CLI/reference analyzer** for macOS, Windows, and Linux.

Responsibilities:

- validate session/schema integrity
- validate fixture/firmware compatibility metadata
- enforce qualification rules
- decode raw trial data
- apply separately stored calibration/correction metadata
- compute per-batch and combined distributions
- calculate min, median, mean, P95, P99, standard deviation
- calculate five sensor latency distributions
- calculate signed `Delta_H` and `Delta_V`
- infer/report scan direction with evidence
- compare same-device portrait and landscape sessions
- calculate `Delta_rotation`
- identify frame-quantized shifts/clustering where statistically supported
- report invalid-trial rates and reasons
- generate CSV and JSON exports
- generate publication-ready charts/reports

The analyzer is the reference implementation of methodology calculations. Android may show immediate summaries, but those summaries are not the long-term analytical authority.

## 25. Reporting Requirements

For each authoritative condition, report at least:

- center minimum
- center median
- center mean
- center P95
- center P99
- center standard deviation
- top/left/center/right/bottom distributions or summary values
- signed `Delta_H`
- signed `Delta_V`
- inferred physical scan direction
- invalid-trial rate and breakdown
- per-batch medians
- combined 3,000-trial statistics over valid trials
- refresh rate and display mode
- measured luminance
- transition direction
- fixture and firmware revision
- qualification status

For portrait-vs-landscape investigations, additionally report:

- `Delta_rotation`
- display-transform evidence
- whether only scanout geometry changed or center latency also shifted

## 26. Failure Modes and Required Behavior

### 26.1 USB timing ambiguity

Failure mode: timestamping HID queue time rather than actual completed transfer introduces USB polling uncertainty.

Required behavior: authoritative `T0` remains tied to successful HID IN transaction completion.

### 26.2 Software-timestamp jitter

Failure mode: GPIO ISR entry latency or independent timer domains contaminate sub-millisecond measurements.

Required behavior: use hardware input capture on a common timebase; external capture hardware is preferable to weakening the requirement if Teensy routing proves insufficient.

### 26.3 PWM-induced false edge

Failure mode: comparator threshold is crossed repeatedly due to PWM or noise.

Required behavior: hysteresis, armed windows, repeated-edge diagnostics, and explicit invalid/ambiguous classification.

### 26.4 Sensor saturation

Failure mode: fixed TIA gain saturates on bright panels, invalidating threshold interpretation.

Required behavior: characterize v1 gain before qualification; if fixed gain cannot span the intended range, revise the electronics rather than accepting saturated authoritative measurements.

### 26.5 OLED ABL/thermal drift

Failure mode: full-screen white luminance differs depending on dwell time or panel temperature.

Required behavior: measured 200-nit qualification, controlled white dwell during calibration, stability gate, and matched stimulus geometry.

### 26.6 Android display-mode drift

Failure mode: adaptive refresh, VRR, orientation change, or swapchain recreation changes timing during acquisition.

Required behavior: batch integrity failure.

### 26.7 Mechanical misregistration

Failure mode: sensor positions differ materially between devices/runs.

Required behavior: bezel-referenced fixture, normalized coordinates, alignment verification, separate remount reproducibility qualification.

### 26.8 Cross-device causal overclaim

Failure mode: a portrait device measures slower than a landscape device and the difference is incorrectly attributed to orientation alone.

Required behavior: causal rotation claims require same-device matched orientation evidence and diagnostic support.

### 26.9 Analyzer evolution

Failure mode: old sessions become unusable after analysis methodology changes.

Required behavior: raw versioned evidence remains authoritative and analyzer changes must not require rerunning hardware solely to recompute derived statistics.

## 27. Explicit v1 Exclusions

The following are not required for v1:

- high-speed camera measurement
- physical actuation of built-in handheld buttons
- controller-inclusive polling/debounce latency as a headline metric
- direct Android integration with the Calibrite Display Pro HL
- public Play Store distribution
- consumer-grade onboarding/UX
- device-specific fixture overlays
- exhaustive execution of every diagnostic matrix combination on every handheld
- routine Perfetto capture
- mandatory proprietary/vendor thermal APIs
- ICC/profile/LUT calibration of the handheld
- HDR latency methodology unless added by a future design revision

The fixture should preserve extension points for future controller-inclusive testing and direct colorimeter integration, but v1 must not carry their implementation complexity.

## 28. Rationale for Major Rejected Alternatives

### 28.1 High-speed camera

Rejected for v1 because photodiodes plus a hardware HID reference provide higher timing precision, direct electronic timestamps, and automated large-sample acquisition without camera frame-rate quantization.

### 28.2 Built-in controller actuation

Rejected as the authoritative input source because controller MCU scan, debounce, transport, and device-specific input wiring would confound the portrait-vs-landscape display experiment.

### 28.3 Single movable photodiode

Rejected because left/right/top/bottom measurements would come from different trials and refresh phases. Five simultaneous sensors directly expose scanout geometry within the same physical frame transition.

### 28.4 ADC-only sensor capture

Rejected because multiplexed ADC sampling introduces unnecessary channel skew and sampling-interval uncertainty. Comparator plus hardware capture is simpler for precise crossing timestamps.

### 28.5 External simultaneous waveform ADC as v1 default

Rejected because it adds substantial hardware/data complexity. Analog test points preserve a path to future waveform characterization if T10/T50/T90 comparator measurements prove insufficient.

### 28.6 Bus-powered fixture

Rejected because fixture current draw and handheld USB power quality would become measurement variables.

### 28.7 Android-only analysis

Rejected because acquisition and analysis must evolve independently. A portable raw dataset plus reference desktop analyzer keeps old measurements re-analyzable.

### 28.8 Exhaustive mandatory matrix

Rejected because it would make routine handheld testing impractical. The methodology uses a small authoritative matrix and escalates diagnostically only when evidence warrants it.

## 29. Acceptance Criteria for the Implemented Instrument

Before v1 can be treated as publication-grade, the complete system must demonstrate:

1. Synthetic USB HID events are received through the normal Android game-controller stack while the app independently owns the vendor control interface.
2. `T0` is tied to successful HID transaction completion, not merely queue time.
3. Five sensor edges are captured in hardware against a common monotonic timebase.
4. Electrical self-test demonstrates <=25 us calibrated inter-channel skew.
5. The fixture's characterized total timing uncertainty is <=0.1 ms for the authoritative measurement path.
6. Optical self-test validates all five photodiode/TIA/comparator chains.
7. Per-channel 10/50/90% programmable thresholds calibrate successfully from measured black/white levels.
8. Qualified 1,000-trial batches are acquired without per-trial control telemetry and downloaded afterward from RAM.
9. Trial validity and batch integrity failures are preserved and enforced as defined.
10. Three independent 1,000-trial batches meet the provisional <=0.5 ms center-median repeatability span on a stable reference device/condition.
11. Mechanical remount qualification demonstrates <=0.5 ms provisional center-median reproducibility on a reference device/condition.
12. The Android app supports Native/Vulkan, Native/GLES, Framework/Vulkan, and Framework/GLES modes with Native/Vulkan authoritative.
13. The app supports continuous authoritative rendering and event-driven diagnostic rendering.
14. The app records the actual display, refresh, orientation, and presentation configuration associated with every batch.
15. Qualification mode blocks or invalidates runs when required conditions fail; engineering mode records violations without presenting the result as qualified.
16. The Display Pro HL workflow records and verifies 200 +/- 5 nit operation without applying display calibration LUTs/profiles.
17. The exported session package contains sufficient raw evidence for the reference analyzer to recompute results independently.
18. The Rust analyzer validates sessions, calculates the defined statistics and spatial metrics, and compares portrait/landscape runs.
19. Same-device portrait versus landscape testing can distinguish a center-latency shift from a 90-degree rotation of the spatial scanout gradient.
20. No publication claim about rotation overhead depends solely on a cross-device comparison.

## 30. Future Investigations

Potential follow-up work, explicitly not required for v1:

- direct Android USB integration with the Calibrite Display Pro HL
- controller-inclusive latency using electrical contacts, solenoid actuation, or other physical triggers
- analog waveform capture for full pixel-transition curves
- HDR-specific luminance/latency methodology
- automated brightness binary search driven by direct colorimeter readings
- public/reproducible fixture kit and application distribution
- additional platforms beyond Android

These should be evaluated independently rather than weakening the v1 architecture to preemptively accommodate every future use case.
