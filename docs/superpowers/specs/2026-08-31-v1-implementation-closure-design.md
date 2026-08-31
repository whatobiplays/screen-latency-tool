# Screen Latency Measurement Tool — v1 Implementation Closure Design

Date: 2026-08-31
Status: Approved design amendment, pending implementation-plan revision
Parent spec: `docs/superpowers/specs/2026-08-31-screen-latency-measurement-design.md`

## 1. Purpose

This document closes implementation-critical details that were intentionally left open in the approved v1 measurement-methodology design. It does not change the core metric, validity model, five-sensor geometry, Android methodology, or analyzer semantics.

The closure decisions cover:

1. low-cost metrology equipment;
2. USB Full-Speed operation and descriptor identity;
3. exact v1 control-protocol payload semantics;
4. exact session metadata responsibilities;
5. mechanical range for approximately 3–9 inch displays;
6. reproducible CI/toolchain expectations;
7. exact qualification semantics where the parent spec used qualitative wording.

Where this document conflicts with the parent spec, this document controls for v1 implementation details. The parent spec remains authoritative for measurement meaning and methodology.

## 2. Low-Cost Metrology Bench

### 2.1 Purchased equipment target

The v1 project intentionally avoids requiring a dedicated USB protocol analyzer, function generator, or laboratory-grade programmable supply.

Primary purchased instrument:

- **Rigol DHO814** or an equivalent-or-better oscilloscope meeting all of:
  - 4 analog channels;
  - 12-bit vertical resolution;
  - at least 100 MHz analog bandwidth;
  - at least 1 GSa/s real-time sample rate;
  - at least 20 Mpts memory;
  - remote waveform acquisition/control over USB or LAN;
  - four 10x passive probes or equivalent.

The DHO814 is the planned reference instrument because it provides four channels, 100 MHz bandwidth, 1.25 GSa/s, and 25 Mpts while remaining close to the requested roughly-$500 equipment budget.

Additional low-cost equipment/materials:

- passive USB 2.0 Full-Speed breakout/test adapter exposing D+, D-, VBUS, and GND;
- short known-good USB cable;
- DIY fast-LED pulser driven by Teensy GPIO through a MOSFET/transistor stage;
- basic DMM suitable for rail, DAC, bias, and continuity checks;
- adjustable current-limited DC supply capable of clean operation from roughly 3.5–5.5 V for brownout qualification, if the owner does not already have one;
- ordinary BNC leads, spring-ground accessories, test clips, and passive components.

No function generator is required for v1. The Teensy generates deterministic electrical/optical self-test pulses.

### 2.2 Scope role

The oscilloscope is the primary external timing authority for fixture characterization.

USB timing capture uses:

- CH1: USB D+ referenced to USB ground;
- CH2: USB D- referenced to USB ground;
- CH3: Teensy GPIO marker asserted immediately adjacent to the authoritative `T0` timer read;
- CH4: optional queue-time marker, comparator output, or other diagnostic reference.

The two USB channels are interpreted differentially/offline to identify the measured HID transaction and final handshake/EOP timing. Qualification scripts may download waveforms over SCPI and decode/measure them offline; paid scope USB-protocol-decode options are not required.

Use 10x passive probes with short ground springs/leads. Qualification must verify that attaching the USB probes does not introduce retries, disconnects, or altered enumeration behavior. If probing materially perturbs the Full-Speed bus, the USB breakout/probing method must be revised rather than accepting contaminated evidence.

### 2.3 Optical self-test reference

The carrier/qualification fixture includes a fast reference LED driven by a Teensy GPIO through a fast transistor/MOSFET stage.

For optical characterization the scope may observe:

- LED electrical drive;
- one sensor TIA analog monitor;
- comparator output;
- Teensy capture/debug marker.

A common LED pulse is sufficient for inter-channel optical skew because LED emission delay is common-mode. Absolute optical-path uncertainty must conservatively include or independently bound LED turn-on uncertainty; it may not simply be assumed to be zero.

### 2.4 USB-analyzer fallback

A dedicated High-Speed USB analyzer is **not a v1 purchase requirement**.

If firmware cannot force deterministic Full-Speed operation, or scope captures cannot bound `T0` uncertainty inside the complete 0.1 ms fixture budget, stop publication qualification and borrow/rent a USB 2.0 High-Speed analyzer such as a Beagle USB 480 for one-time characterization. Do not weaken the uncertainty requirement.

## 3. USB Transport and Descriptor Contract

### 3.1 Full-Speed-only v1 transport

The Teensy 4.1 device controller must operate the complete v1 composite fixture at **USB 2.0 Full Speed, 12 Mb/s**.

Firmware forces Full-Speed before the USB controller starts after reset by setting the i.MX RT USB controller Full-Speed-force (`PFSC`) behavior. After enumeration, firmware verifies the negotiated port speed. A fixture that enumerates at High Speed is not qualification-ready and must report a hardware/configuration fault.

This choice does not alter the authoritative latency definition because `T0` occurs after successful HID transaction completion; pre-completion USB polling/transmission time remains intentionally excluded.

### 3.2 USB device identity

Development/internal-bench identity:

- VID: `0x1209`
- PID: `0x0001` (pid.codes reserved test PID)
- manufacturer string: `Screen Latency Tool`
- product string: `Screen Latency Fixture v1`
- serial string: uppercase hexadecimal fixture serial, exactly 16 hex characters.

Android must additionally validate the interface signature and product string; VID/PID alone is not sufficient because the test PID is intentionally non-unique.

Before distributing the hardware outside internal development, request a unique PID or otherwise obtain an appropriate production USB identity. v1 does not claim USB-IF certification or USB-logo eligibility.

### 3.3 Device/configuration descriptor

- USB version: 2.0
- EP0 maximum packet size: 64 bytes
- device class/subclass/protocol: `0/0/0` (per-interface classes)
- one configuration
- configuration attributes: self-powered (`bmAttributes = 0xC0`)
- `bMaxPower = 1` (2 mA declaration); fixture electronics are externally powered and USB VBUS is used only for attach/sense/PHY behavior
- exactly two interfaces

Interface 0 — HID gamepad:

- class `0x03` HID;
- subclass `0x00`;
- protocol `0x00`;
- one interrupt IN endpoint;
- endpoint address `0x81`;
- max packet 64 bytes;
- Full-Speed polling interval 1 ms.

Interface 1 — fixture control:

- class `0xFF` vendor-specific;
- subclass `0x00`;
- protocol `0x00`;
- bulk OUT endpoint `0x02`, 64-byte max packet;
- bulk IN endpoint `0x83`, 64-byte max packet.

No CDC, RawHID, MTP, MIDI, or debug USB interface is present in the qualification firmware image.

### 3.4 HID report contract

The HID top-level collection uses Generic Desktop / Game Pad.

Input report is exactly 6 bytes:

```text
byte 0..1  buttons 1..16, little-endian bitset
byte 2     X, signed 8-bit, neutral 0
byte 3     Y, signed 8-bit, neutral 0
byte 4     Rx, signed 8-bit, neutral 0
byte 5     Ry, signed 8-bit, neutral 0
```

All axes remain zero in v1 measured trials.

Measured press report:

```text
01 00 00 00 00 00
```

Unmeasured release/reset report:

```text
00 00 00 00 00 00
```

Only button 1 participates in v1 latency measurement.

For each measured press, firmware binds the current `hid_sequence` to the exact USB transfer descriptor/buffer containing the press report. The authoritative `T0` callback may latch a timestamp only when completion corresponds to that measured press transfer. Release/reset completions and unrelated endpoint completions can never establish `T0`.

Android correlates diagnostic input events to fixture trials by ordered `hid_sequence`; any missing, duplicated, or unexpected press sequence is an integrity fault rather than silently re-numbered.

## 4. v1 Control Wire Contract

### 4.1 Common framing

All control messages use the existing planned 20-byte little-endian `SLT1` header and command/status IDs from the contracts implementation plan. Payloads below are normative v1 definitions.

All reserved fields must encode as zero and decoders must reject nonzero reserved values.

All numeric fields are little-endian. No payload uses native struct packing.

### 4.2 `GET_FIXTURE_INFO`

Request payload: empty.

Response payload, 40 bytes:

```text
u16 hardware_major
u16 hardware_minor
u16 firmware_major
u16 firmware_minor
u16 firmware_patch
u8  protocol_major
u8  protocol_minor
u8  usb_speed              // 1 = Full Speed; other values invalid for v1 qualification
u8  sensor_count           // must be 5
u64 timebase_hz
u16 max_trials
u16 trial_record_size
u32 capability_flags
u64 fixture_serial
u16 reserved
```

Required capability flags:

```text
bit 0 FIVE_CAPTURE_CHANNELS
bit 1 ELECTRICAL_SELF_TEST
bit 2 OPTICAL_SELF_TEST
bit 3 PROGRAMMABLE_THRESHOLDS
bit 4 POWER_SUPERVISION
bit 5 FULL_SPEED_FORCED
bits 6..31 reserved
```

### 4.3 `RUN_ELECTRICAL_SELF_TEST` / `RUN_OPTICAL_SELF_TEST`

Request payload, 4 bytes:

```text
u32 samples
```

Response payload, 60 bytes:

```text
u8  test_kind              // 1 electrical, 2 optical
u8  passed                 // 0/1
u8  channel_count          // 5
u8  reserved
u32 samples
u64 max_skew_ticks
s32 channel_offset_ticks[5]
u32 missed_edges[5]
u32 fault_flags
```

The response is a summary. Raw qualification waveforms/evidence remain external artifacts and are not transported through this payload.

### 4.4 `CALIBRATE_SENSORS`

Request payload, 4 bytes:

```text
u8  stage                  // 1 BLACK_STORE, 2 WHITE_STORE, 3 SAMPLE_ONLY
u8  reserved
u16 samples
```

`BLACK_STORE` and `WHITE_STORE` update the fixture's in-RAM calibration state. `SAMPLE_ONLY` does not mutate stored calibration and is used for stability/sanity measurements.

Response payload, 38 bytes:

```text
u8  stage
u8  sensor_count           // 5
u16 samples
u16 mean_adc[5]
u16 min_adc[5]
u16 max_adc[5]
u32 fault_flags
```

### 4.5 `SET_THRESHOLDS`

Request payload, 16 bytes:

```text
u8  threshold_percent      // exactly 10, 50, or 90
u8  transition             // 1 black_to_white, 2 white_to_black
u8  mode                   // 1 derived, 2 explicit_engineering
u8  reserved
u16 dac_code[5]            // zero in derived mode
u16 reserved2
```

In derived mode firmware computes each sensor threshold from stored black/white calibration. For black-to-white, threshold level is `p%` from black toward white. For white-to-black, threshold level is `(100-p)%` above black, so `T10` corresponds to 90% luminance and `T90` to 10% luminance.

Explicit DAC mode is engineering-only and can never produce a qualified session.

Response payload is the same 16-byte structure with the actual programmed DAC codes returned.

### 4.6 `CONFIGURE_BATCH`

Request payload, 40 bytes:

```text
u32 batch_id
u16 trial_count            // authoritative = 1000
u16 min_delay_ms           // authoritative = 200
u16 max_delay_ms           // authoritative = 500
u8  transition             // 1 black_to_white, 2 white_to_black
u8  threshold_percent      // authoritative = 50
u8  hid_button             // authoritative = 1
u8  sensor_mask            // authoritative = 0x1F
u16 capture_timeout_ms
u16 baseline_timeout_ms
u32 refresh_millihz
u64 prng_seed
u32 flags
u8  reserved[6]
```

Authoritative v1 defaults:

- `capture_timeout_ms = 250`
- `baseline_timeout_ms = 250`
- `flags = 0`

A future implementation may tune these timeout constants only by methodology revision; they are stored in session configuration.

Response payload: empty on success.

### 4.7 `ARM_BATCH`

Request payload:

```text
u32 batch_id
```

Response payload: empty.

Firmware rejects arm unless calibration, threshold, power, Full-Speed, capture routing, and buffer capacity are valid.

### 4.8 `GET_BATCH_STATUS`

Request payload:

```text
u32 batch_id
```

Response payload, 24 bytes:

```text
u32 batch_id
u8  state                  // 0 idle, 1 configured, 2 armed, 3 acquiring, 4 complete, 5 failed
u8  reserved[3]
u32 trials_completed
u32 result_bytes
u32 integrity_flags
u16 supply_mv
u16 reserved2
```

Fixture integrity flags:

```text
bit 0 TIMEBASE_DISCONTINUITY
bit 1 UNDERVOLTAGE
bit 2 CALIBRATION_INVALIDATED
bit 3 DUPLICATE_HID
bit 4 BUFFER_OVERFLOW
bit 5 INTERNAL_STATE_ERROR
bit 6 ABORTED
bits 7..31 reserved
```

Android-owned integrity failures such as lifecycle interruption, display-mode change, swapchain recreation, or USB detach are stored in session qualification evidence rather than forced into fixture flags.

### 4.9 `DOWNLOAD_RESULTS`

Request payload:

```text
u32 offset
u32 length
```

Response payload: exactly the requested raw result bytes, or `RANGE_ERROR`.

Maximum requested chunk in v1: 4096 bytes. Firmware may return a smaller final chunk only when the remaining result length is smaller than requested.

### 4.10 `ABORT_BATCH`

Request payload:

```text
u32 batch_id
```

Response payload: empty.

Abort never rewrites already captured trial records.

## 5. Raw Trial and Batch Completion Semantics

The existing planned 48-byte batch header and 96-byte trial record remain authoritative.

`hid_sequence` increments exactly once per measured press and is never consumed by reset/release traffic.

Android must not generate vendor-interface traffic during timing-critical acquisition.

To know when it is safe to query completion, Android counts the measured HID press/release events it receives through the normal input stack. After the expected final release is observed, Android waits at least:

`capture_timeout_ms + baseline_timeout_ms + 100 ms`

before issuing the first `GET_BATCH_STATUS`.

If Android does not observe the expected final HID sequence, it does not poll during the nominal acquisition window. It waits until the deterministic hard upper bound derived from `trial_count`, `max_delay_ms`, `capture_timeout_ms`, and `baseline_timeout_ms`, then queries status and marks the batch/session with the appropriate HID-sequence integrity failure. This fallback cannot yield a qualified batch if Android observed missing/duplicate measured events.

## 6. Session Metadata Contract

Every JSON metadata file contains its own `{name, major, minor}` schema identifier. v1 major/minor is `1/0` unless separately stated.

### 6.1 `device.json`

Required fields:

- manufacturer;
- model;
- product;
- device codename where available;
- Android release;
- SDK integer;
- build fingerprint;
- security patch string when exposed;
- hardware/SoC model when exposed;
- app version name/code;
- app build Git SHA;
- session-start battery percent;
- charging state.

Unavailable optional platform facts are encoded as `null`, not guessed.

### 6.2 `fixture.json`

Required fields:

- fixture serial;
- hardware revision;
- firmware semantic version;
- firmware build Git SHA;
- protocol version;
- negotiated USB speed (`full_speed_12mbps` required for qualification);
- timebase frequency;
- trial-record size;
- sensor count and sensor identities/revisions;
- characterized fixture uncertainty in microseconds;
- electrical self-test summary and evidence reference.

### 6.3 `calibration.json`

Required fields:

- Display Pro HL model identifier and measurement timestamp;
- measured luminance in nits;
- Android/window brightness value;
- selected transition direction and threshold percentage;
- per sensor: black ADC mean, white ADC mean, 10/50/90 derived threshold codes, selected DAC code, TIA revision, sensor serial/revision;
- per-channel timing correction coefficients kept separate from raw timestamps;
- optical-self-test revision/evidence reference;
- post-Calibrite stability samples and derived drift result.

### 6.4 `display.json`

Required fields:

- logical width/height;
- selected display mode ID;
- selected mode resolution;
- requested refresh rate;
- observed/effective refresh rate;
- requested orientation;
- effective rotation;
- renderer/presentation snapshot;
- fixed-cadence qualification evidence/result;
- ADB evidence references when present.

### 6.5 `configuration.json`

Required fields:

- qualification vs engineering mode;
- authoritative boolean;
- input path;
- renderer;
- render-loop kind;
- stimulus mode;
- transition direction;
- threshold percentage;
- workload target;
- requested refresh;
- VRR diagnostic flag;
- performance preset label;
- warm-up requirement/result;
- stability-window requirement/result;
- trial count;
- random delay bounds;
- capture/baseline timeouts;
- all three batch IDs and PRNG seeds.

### 6.6 `android-events.json`

Array records require:

```text
hid_sequence
kind
timestamp_ns
clock_domain
details
```

`kind` is one of:

- `input_received`
- `stimulus_state_changed`
- `render_sampled`
- `frame_submitted`
- `frame_presented`
- `display_state_changed`
- `renderer_recreated`
- `lifecycle_changed`

`clock_domain` explicitly states the Android clock source, such as `android_uptime_nanos` or `android_elapsed_realtime_nanos`. These values are diagnostic only and never named `t0`.

### 6.7 `summary.json`

Derived-only file containing analyzer-compatible convenience results. It is never evidence authority and may be regenerated from raw session contents.

### 6.8 Qualification artifact storage

v1 qualification `.slt-session.zip` files are expected to be small enough to commit directly with qualification evidence. Commit immutable raw qualification sessions and their SHA-256 values directly to the repository unless the repository's total raw qualification artifacts exceed 250 MiB. Crossing that threshold requires a separate explicit artifact-storage decision; do not silently introduce Git LFS or external links.

## 7. Mechanical Range

The adjustable fixture targets handheld displays from approximately **3 inches through 9 inches diagonal**.

Design envelope for the active display rectangle:

- active width: **55–205 mm**;
- active height: **35–135 mm**.

Design envelope for supported handheld chassis:

- chassis width: **100–350 mm**;
- chassis height: **60–190 mm**;
- chassis thickness: **8–55 mm**.

These ranges are mechanical design targets, not a guarantee that every unusual chassis geometry will register successfully. Sticks, triggers, grips, buttons, vents, and curved housings must not become load-bearing registration references.

The active-screen rectangle is measured/entered per DUT and the five sensor locations are computed from that rectangle. Diagonal screen size is descriptive only; sensor placement is always based on active width/height.

Fixture output includes:

- parametric OpenSCAD sources;
- printable STL or 3MF exports for the qualified revision;
- mechanical hardware BOM;
- assembly drawing/instructions;
- sensor/carrier cable pinout and cable-length limits;
- optical boot material/dimensions;
- alignment and remount procedure.

## 8. Hardware Manufacturing Deliverables

A hardware revision is not considered implementation-complete from `.kicad_sch` / `.kicad_pcb` alone.

For each qualified sensor/carrier revision, repository outputs must include:

- source KiCad project;
- schematic PDF;
- Gerber set;
- drill files;
- fabrication notes/stack-up requirements;
- BOM with manufacturer part numbers;
- position/CPL files when applicable;
- assembly drawing;
- connector/cable pinout;
- firmware flashing instructions;
- hardware revision identification method.

Production BOM values for TIA feedback, photodiode bias, comparator hysteresis, DAC/reference parts, and regulator/supervisor components are locked only after the prototype characterization already required by the parent spec.

## 9. CI and Reproducible Toolchains

The monorepo must have automated non-hardware validation for all software components.

Required CI surfaces:

1. Rust protocol/analyzer on Linux, macOS, Windows:
   - format;
   - clippy with warnings denied;
   - all tests;
   - golden vectors.

2. Android on Linux CI:
   - pinned Gradle wrapper;
   - pinned AGP/Kotlin/NDK versions;
   - JVM/unit tests;
   - native C++ build;
   - debug APK assembly;
   - cross-language protocol-vector tests.

3. Firmware on Linux CI:
   - pinned PlatformIO version;
   - pinned Teensy platform/core version;
   - native host tests;
   - Teensy 4.1 compile;
   - protocol/trial golden vectors.

4. Hardware/mechanical validation after those sources exist:
   - KiCad ERC/DRC through a pinned supported KiCad major version;
   - OpenSCAD headless render of printable parts/assembly.

Hardware-in-loop, scope measurements, Display Pro HL readings, Android instrumentation on a physical DUT, and fixture self-tests remain manual qualification evidence rather than CI.

## 10. Exact Qualification Semantics

### 10.1 Same-device luminance matching

For a causal portrait-versus-landscape pair:

- each condition must independently measure within 195–205 nits;
- absolute difference between the two Display Pro HL center measurements must be **<=2.0 nits**.

If orientation change perturbs luminance enough to exceed this match, re-adjust and repeat absolute luminance verification/calibration.

Cross-device comparisons still require each device within 195–205 nits; a smaller difference is preferred but the parent spec's 200 +/- 5 nit condition remains the publication rule.

### 10.2 Relative luminance stability

During the 3-minute post-overlay stability window:

- every 10 seconds, present a short defined white/black sampling sequence and use `SAMPLE_ONLY` ADC readings;
- compute center-sensor optical amplitude as `white_mean - black_mean`;
- normalize each amplitude to the first accepted post-calibration sample;
- qualification requires every accepted sample to remain within **+/-2.0%** of the reference amplitude;
- any brightness, display-mode, or calibration intervention resets the absolute-luminance workflow.

### 10.3 Temperature trend

Where a meaningful temperature sensor is available, sample it every 10 seconds during the same 3-minute window.

For each sensor, compare the median of the first 30 seconds with the median of the final 30 seconds. Absolute difference must be **<1.0 C**. No private/vendor sensor is mandatory; unavailable telemetry is recorded as unavailable.

### 10.4 Fixed-refresh evidence

A qualification run is considered effectively fixed-refresh only when all are true:

- requested display mode ID remains unchanged from pre-arm through post-download;
- Android-reported refresh remains within **+/-0.1 Hz** of the selected nominal value;
- over at least 300 presentation intervals immediately before acquisition, median frame interval is within **0.5%** of the nominal frame period;
- at least **99%** of those intervals are within **+/-10%** of the nominal frame period;
- no renderer/swapchain recreation occurs during acquisition.

If the platform exposes no reliable way to disable VRR but satisfies these observed fixed-cadence criteria, the session may qualify as fixed-cadence. The session records that VRR control itself was unavailable rather than claiming a vendor VRR switch was disabled.

### 10.5 Devices without 60 Hz

Do not synthesize or approximate a 60 Hz mode.

If a DUT exposes no valid fixed 60 Hz mode:

- mark normalized-60 as unsupported;
- run the native/high-refresh authoritative condition;
- add a direct cross-device comparison only at a fixed refresh rate genuinely supported by both DUTs.

### 10.6 Evidence retention

Failed qualification attempts remain retained as failed/engineering evidence. A rerun creates new evidence and does not overwrite the failed raw session.

## 11. Remaining Intentional Characterization Decisions

The following remain measurements, not missing design decisions:

- final sensor `Rf/Cf`;
- final photodiode reverse-bias voltage within the characterized design range;
- final comparator hysteresis;
- exact per-channel timing correction values;
- exact fixture total uncertainty after characterization;
- final PCB placement/routing details;
- exact optical-boot foam/material selected after mechanical testing.

These values must be chosen from measured prototype evidence and recorded in the qualified hardware revision. They should not be guessed in the specification.

## 12. Closure Gate

Implementation plans may be revised and execution may begin only when they incorporate this amendment and preserve the parent measurement design.

Before PCB fabrication, firmware must prove:

1. deterministic Full-Speed enumeration on the Teensy 4.1 device port;
2. the exact two-interface descriptor contract;
3. measured-press transfer completion can be associated unambiguously with `hid_sequence`;
4. scope-based USB transaction completion versus `T0` correlation can be captured without material bus perturbation;
5. five hardware-capture channels still meet the common-timebase design.

Failure of Full-Speed forcing or scope-based `T0` characterization triggers the analyzer-rental/borrow fallback rather than a methodology downgrade.
