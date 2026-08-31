# End-to-End Qualification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the physical fixture, Android acquisition app, and Rust analyzer; characterize total measurement uncertainty; and produce the first publication-grade evidence that the instrument can separate center-latency penalties from rotated scanout geometry.

**Architecture:** Qualification is evidence-driven and does not weaken gates to make a run pass. Electrical/USB timing characterization establishes fixture uncertainty first, then optical and mechanical reproducibility, then full Android 1,000-trial acquisition, then three-batch repeatability, and finally same-device portrait/landscape and cross-device demonstrations analyzed from exported raw sessions.

**Tech Stack:** Assembled Teensy fixture, Display Pro HL, Android/ADB, Rust analyzer, USB timing-analysis equipment used for `T0` characterization, Git-tracked Markdown/JSON qualification evidence.

**Spec:** `docs/superpowers/specs/2026-08-31-screen-latency-measurement-design.md`

## Global Constraints

- No session becomes publication-grade until total fixture uncertainty is characterized at <=0.1 ms.
- Electrical inter-channel skew after correction must be <=25 us.
- Display luminance authority is manual Display Pro HL measurement at 200 +/- 5 cd/m^2.
- Photodiodes may monitor relative drift only; they never replace absolute luminance measurement.
- Authoritative batches are 1,000 trials; authoritative conditions require three independent qualified batches.
- Overall invalid rate must be <=1%; center valid rate >=99.5%; any integrity fault fails the batch.
- Three center T50 medians must span <=0.5 ms.
- Mechanical remount center-median variation target is <=0.5 ms.
- Same-device matched orientation evidence is required for causal rotation-overhead claims.
- VRR, synthetic workload, alternate render/input paths, and Perfetto remain diagnostics unless specifically invoked to explain an anomaly.
- Raw sessions and qualification evidence are retained even when a qualification attempt fails.

---

## File Structure

```text
qualification/
├── README.md
├── procedures/
│   ├── fixture-electrical.md
│   ├── fixture-optical.md
│   ├── luminance-and-stability.md
│   ├── android-authoritative-run.md
│   ├── mechanical-remount.md
│   └── portrait-landscape.md
├── scripts/
│   ├── capture-adb-state.sh
│   └── capture-adb-state.ps1
├── evidence/
│   ├── fixture/
│   ├── reference-device/
│   └── orientation/
└── v1-qualification-report.md
```

Evidence directories contain raw machine outputs and concise reviewed summaries; do not hand-edit exported `.slt-session.zip` packages.

---

### Task 1: Establish a reproducible qualification procedure and evidence manifest

**Files:**
- Create: `qualification/README.md`
- Create: all `qualification/procedures/*.md`
- Create: `qualification/scripts/capture-adb-state.sh`
- Create: `qualification/scripts/capture-adb-state.ps1`
- Create: `qualification/v1-qualification-report.md`

**Interfaces:**
- Produces one evidence index linking fixture hardware/firmware revision, Android APK version, analyzer version, DUT/session IDs, and every acceptance criterion.

- [ ] **Step 1: Write the acceptance matrix before collecting evidence**

`v1-qualification-report.md` must contain rows for all 20 design acceptance criteria with columns:

```text
Criterion | Required evidence | Evidence path | Result | Notes
```

Initial `Result` values are `Not Run`, not `Pass`.

- [ ] **Step 2: Document the authoritative run procedure exactly**

`android-authoritative-run.md` must specify:

1. balanced/normal performance state;
2. battery saver off;
3. fixed refresh selected and verified;
4. 5-minute minimum warm-up;
5. Display Pro HL confirms 195–205 nits using full-screen SDR RGB white workload;
6. remove colorimeter without changing display state;
7. mount/verify five-sensor overlay;
8. calibrate sensor thresholds;
9. 3-minute relative stability gate;
10. acquire three 1,000-trial batches;
11. download/export raw session;
12. capture ADB state for orientation investigations;
13. run Rust analyzer.

- [ ] **Step 3: Implement ADB evidence scripts**

Both scripts accept an output directory and write at least:

```text
dumpsys-display.txt
surfaceflinger.txt
getprop.txt
wm-size.txt
wm-density.txt
```

They fail nonzero if `adb get-state` is not `device` and never overwrite an existing evidence directory silently.

- [ ] **Step 4: Dry-run procedures without claiming qualification**

Walk through every procedure with the fixture disconnected and verify every prerequisite/failure path is explicit. Update ambiguous instructions before physical qualification starts.

- [ ] **Step 5: Commit**

```bash
git add qualification
git commit -m "docs: define v1 latency qualification procedure"
```

---

### Task 2: Characterize electrical capture skew and authoritative USB `T0`

**Files:**
- Create evidence under: `qualification/evidence/fixture/electrical/`
- Update: `qualification/v1-qualification-report.md`
- Reference: `firmware/docs/capture-routing.md`
- Reference: `firmware/docs/usb-t0-characterization.md`

**Interfaces:**
- Produces the uncertainty terms later combined into the <=0.1 ms budget.

- [ ] **Step 1: Run 10,000-edge electrical common-input test**

Record raw five-channel tick captures, timebase frequency, per-channel mean offset, max observed skew, and applied correction coefficients. Keep the uncorrected dataset.

Acceptance: after applying separately stored channel corrections, inter-channel skew <=25 us with zero missing captures.

- [ ] **Step 2: Re-run after cold power cycle**

Repeat the electrical test after powering the fixture fully off/on. Confirm correction behavior remains within the claimed bound; if not, calibration must become per-session rather than persistent.

- [ ] **Step 3: Characterize USB completion timestamp**

Use USB bus-level timing equipment to correlate actual HID IN completion/ACK with the fixture's `T0` read/marker. Capture enough transactions to bound min/median/P99/max timing error and equipment resolution.

Acceptance: no evidence that queue time is being timestamped; worst-case `T0` uncertainty is explicitly bounded and compatible with the complete <=0.1 ms budget.

- [ ] **Step 4: Write the electrical uncertainty subtotal**

Compute and record individual conservative bounds for:

- USB completion timestamp path;
- common timebase quantization;
- channel capture quantization/skew;
- electrical self-test calibration residual.

Do not combine independent terms by simple averaging. For the publication bound, use a conservative worst-case sum unless independence and distribution are actually demonstrated.

- [ ] **Step 5: Commit evidence**

```bash
git add qualification/evidence/fixture/electrical qualification/v1-qualification-report.md
git commit -m "test: characterize fixture electrical timing"
```

---

### Task 3: Characterize optical sensor timing, thresholds, PWM behavior, and saturation margin

**Files:**
- Create evidence under: `qualification/evidence/fixture/optical/`
- Update: `qualification/v1-qualification-report.md`

**Interfaces:**
- Produces optical/front-end uncertainty contribution and qualified operating range.

- [ ] **Step 1: Run full optical self-test on all five channels**

For repeated fast LED transitions record TIA analog levels, comparator thresholds, capture ticks, repeated-edge counts, and channel-to-channel timing. Test 10%, 50%, and 90% thresholds separately.

- [ ] **Step 2: Verify 200-nit and high-brightness sensor range on a real display**

At 200 +/- 5 nits and the brightest intended diagnostic condition, confirm no TIA/comparator saturation. At black, confirm noise/hysteresis do not generate spontaneous qualified edges.

- [ ] **Step 3: Exercise a PWM-prone brightness condition**

Use a display/brightness condition known to show repeated optical modulation if available. Verify the fixture records `MULTIPLE_EDGE`/`PWM_ACTIVITY` rather than silently returning a clean authoritative timestamp. If no such DUT is available, use the optical self-test LED to inject repeated crossings with known spacing.

- [ ] **Step 4: Add optical contribution to uncertainty budget**

Bound detector/TIA/comparator timing variation under the authoritative 50% threshold condition. Add this conservatively to the electrical subtotal. The complete fixture claim must remain <=0.1 ms; otherwise revise hardware/threshold behavior and repeat qualification.

- [ ] **Step 5: Commit evidence**

```bash
git add qualification/evidence/fixture/optical qualification/v1-qualification-report.md
git commit -m "test: qualify fixture optical timing"
```

---

### Task 4: Qualify one complete 1,000-trial Android acquisition batch

**Files:**
- Create evidence under: `qualification/evidence/reference-device/single-batch/`
- Update: `qualification/v1-qualification-report.md`

**Interfaces:**
- Produces first real `.slt-session.zip` batch evidence using Native/Vulkan/continuous/minimal workload.

- [ ] **Step 1: Select a stable reference DUT using explicit criteria**

Use an Android 11+ handheld that supports a fixed 60-Hz mode, can remain thermally stable under the minimal workload, and permits the five-sensor fixture to align without obscuring required controls. Record model, build fingerprint, Android version, physical/native orientation evidence, and active display modes.

- [ ] **Step 2: Run the full luminance/stability workflow**

Record Display Pro HL reading within 195–205 nits, app brightness value, 5-minute warm-up evidence, post-overlay threshold calibration, 3-minute relative center-sensor drift <=2%, thermal status, and fixed refresh evidence.

- [ ] **Step 3: Acquire one full 1,000-trial qualification batch**

Use fixed 60 Hz, black-to-white, T50, Native/Vulkan, continuous loop, minimal workload, VRR off/verified fixed behavior. Capture USB traffic externally or through a non-perturbing monitor to confirm no vendor control transfers occur during timing-critical trial acquisition.

- [ ] **Step 4: Analyze and verify batch gates**

Run:

```bash
cargo run -p slt-analyzer -- validate <session>
cargo run -p slt-analyzer -- analyze <session> --out <analysis-dir>
```

Acceptance:

- invalid <=1%;
- center valid >=99.5%;
- no integrity fault;
- all raw trials retained;
- report includes five-sensor distributions and signed spatial deltas.

- [ ] **Step 5: Commit evidence**

Commit the immutable exported session, analyzer outputs, and procedure notes if repository size is acceptable; otherwise store the raw session in the project's designated large-artifact location and commit its SHA-256 plus retrieval path. Never commit a hand-edited derivative in place of raw evidence.

---

### Task 5: Demonstrate three-batch session repeatability in both transition directions

**Files:**
- Create evidence under: `qualification/evidence/reference-device/repeatability/`
- Update: `qualification/v1-qualification-report.md`

**Interfaces:**
- Produces authoritative black-to-white and white-to-black conditions, each with three independent 1,000-trial batches.

- [ ] **Step 1: Acquire black-to-white condition**

Without remounting, acquire three independently seeded 1,000-trial batches after qualification gates pass.

- [ ] **Step 2: Verify black-to-white repeatability**

Analyzer must report all three batches individually qualified and center T50 median span <=0.5 ms.

- [ ] **Step 3: Recalibrate direction-specific thresholds and acquire white-to-black**

Run the separate descending threshold condition with three independently seeded batches. Preserve reset transitions as unmeasured.

- [ ] **Step 4: Verify white-to-black repeatability**

Same qualification gates and <=0.5 ms median-span requirement apply. Report rise/fall latency separately; do not average transition directions into one headline.

- [ ] **Step 5: Commit evidence**

```bash
git add qualification/evidence/reference-device/repeatability qualification/v1-qualification-report.md
git commit -m "test: demonstrate latency batch repeatability"
```

---

### Task 6: Qualify mechanical remount reproducibility

**Files:**
- Create evidence under: `qualification/evidence/reference-device/remount/`
- Update: `fixture/qualification/remount-reproducibility.md`
- Update: `qualification/v1-qualification-report.md`

**Interfaces:**
- Produces three independent full remount cycles on the same reference condition.

- [ ] **Step 1: Record baseline mount geometry**

Record bezel-stop positions, active screen rectangle, sensor carriage positions, and alignment-marker verification for the reference DUT.

- [ ] **Step 2: Perform three complete remove/reinstall cycles**

For each cycle:

1. remove fixture completely;
2. reinstall using only defined mechanical references;
3. verify alignment markers;
4. repeat absolute luminance verification if the procedure disturbed display state;
5. recalibrate thresholds;
6. pass stability gate;
7. acquire a qualified reference batch.

- [ ] **Step 3: Analyze remount variation**

Acceptance: center-median variation across remount cycles <=0.5 ms and spatial scanout deltas remain consistent within the characterized uncertainty/mechanical position tolerance.

- [ ] **Step 4: Document mechanical failure modes**

Record any chassis/bezel geometry that prevents valid registration, optical boot compression issues, or sensor positioning limitations. Do not generalize fixture support beyond tested geometry.

- [ ] **Step 5: Commit evidence**

```bash
git add qualification/evidence/reference-device/remount fixture/qualification/remount-reproducibility.md qualification/v1-qualification-report.md
git commit -m "test: qualify fixture remount reproducibility"
```

---

### Task 7: Run the same-device portrait versus rotated-landscape experiment

**Files:**
- Create evidence under: `qualification/evidence/orientation/same-device/`
- Update: `qualification/v1-qualification-report.md`

**Interfaces:**
- Produces matched native-portrait and rotated-landscape session pair plus ADB display/SurfaceFlinger evidence.

- [ ] **Step 1: Select a portrait-native DUT that can expose both logical orientations**

The DUT must support a fixed matched refresh rate in both orientations and allow the app to render native portrait plus 90-degree rotated landscape without changing the physical fixture mount. Record evidence establishing physical/native orientation rather than relying on marketing dimensions.

- [ ] **Step 2: Acquire native-portrait sessions**

At the matched fixed refresh and 200 +/- 5 nits, acquire qualified black-to-white and white-to-black conditions. Capture ADB display and SurfaceFlinger evidence associated with the session IDs.

- [ ] **Step 3: Acquire rotated-landscape sessions**

Change only the logical orientation/transform. Re-verify any qualification gate invalidated by the change, including refresh, presentation configuration, luminance if affected, and threshold calibration when required. Keep device, fixture mount, input/render path, workload, and performance state matched.

- [ ] **Step 4: Compare with the analyzer**

Run `slt compare` for each transition direction. The report must explicitly separate:

- center `Delta_rotation`;
- portrait `Delta_H`/`Delta_V`;
- landscape `Delta_H`/`Delta_V`;
- scan-direction interpretation;
- matched-configuration status;
- ADB transform evidence references.

If center latency shifts, escalate diagnostically before naming SurfaceFlinger/HWC/GPU/TCON as the cause. If only the spatial gradient rotates, report no demonstrated center-latency rotation penalty.

- [ ] **Step 5: Commit evidence**

```bash
git add qualification/evidence/orientation/same-device qualification/v1-qualification-report.md
git commit -m "test: qualify same-device orientation measurement"
```

---

### Task 8: Demonstrate a matched cross-device portrait versus landscape comparison

**Files:**
- Create evidence under: `qualification/evidence/orientation/cross-device/`
- Update: `qualification/v1-qualification-report.md`

**Interfaces:**
- Produces practical reviewer comparison without causal overclaim.

- [ ] **Step 1: Select one portrait-native and one landscape-native handheld**

Match at fixed 60 Hz where both support it. Record 200 +/- 5 nit luminance, same APK build, fixture revision, firmware revision, Native/Vulkan continuous minimal configuration, balanced/normal power state, and no-throttle stability.

- [ ] **Step 2: Acquire authoritative conditions on each device**

Run both transition directions with three 1,000-trial batches per condition. Add a matched 120-Hz comparison only if both devices support a valid fixed 120-Hz mode.

- [ ] **Step 3: Analyze practical differences**

Report center latency, distributions, spatial gradients, scan direction, invalid rates, and display evidence. State explicitly that cross-device differences may include SoC, Android build, display controller, panel, TCON, driver, and OEM policy effects.

- [ ] **Step 4: Verify no causal-overclaim language**

The cross-device report must not call the observed difference “rotation overhead” unless supported separately by the same-device orientation experiment.

- [ ] **Step 5: Commit evidence**

```bash
git add qualification/evidence/orientation/cross-device qualification/v1-qualification-report.md
git commit -m "test: demonstrate matched cross-device latency comparison"
```

---

### Task 9: Exercise diagnostic escalation on one known anomaly or injected fault

**Files:**
- Create evidence under: `qualification/evidence/reference-device/diagnostics/`
- Update: `qualification/v1-qualification-report.md`

**Interfaces:**
- Demonstrates that diagnostic modes explain anomalies without contaminating the authoritative path.

- [ ] **Step 1: Choose a reproducible anomaly**

Prefer a naturally observed center shift, repeated PWM edge condition, or presentation-mode difference. If none occurs, inject a deterministic engineering-mode fault such as a deliberate one-frame render queue or repeated optical self-test edge.

- [ ] **Step 2: Run targeted diagnostics only**

Use the minimum relevant subset among Native/GLES, Framework/Vulkan, Framework/GLES, event-driven loop, T10/T90, isolated patches, calibrated synthetic load, performance preset, VRR, or Perfetto.

- [ ] **Step 3: Verify authoritative separation**

Diagnostic sessions must export `mode=engineering` or otherwise be explicitly non-authoritative when they violate baseline methodology. The analyzer must not merge them into the qualified baseline.

- [ ] **Step 4: Document the inference boundary**

Show which evidence narrows the source of the anomaly and which causes remain unproven. Do not convert correlation into a component-specific root cause.

- [ ] **Step 5: Commit evidence**

```bash
git add qualification/evidence/reference-device/diagnostics qualification/v1-qualification-report.md
git commit -m "test: validate latency diagnostic escalation"
```

---

### Task 10: Close the v1 publication-grade qualification report

**Files:**
- Modify: `qualification/v1-qualification-report.md`
- Modify: `qualification/README.md`

**Interfaces:**
- Produces the final traceable statement of which design acceptance criteria are proven, failed, or intentionally remain outside v1.

- [ ] **Step 1: Re-run all automated validation from a clean checkout**

Run:

```bash
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace --all-targets
cd android && ./gradlew testDebugUnitTest assembleDebug
pio test -d ../firmware -e native
pio run -d ../firmware -e teensy41
```

Run Android instrumentation tests on a connected API-30+ device as a separate hardware validation step.

- [ ] **Step 2: Verify every acceptance row has concrete evidence**

No `Not Run` row may be changed to `Pass` without an evidence path. A blocked criterion remains `Blocked` with the exact reason; a failure remains `Fail` until rerun evidence replaces it.

- [ ] **Step 3: Verify the 20 design acceptance criteria**

In particular confirm:

- normal Android HID + separately claimed vendor interface;
- true completion-based `T0`;
- five common-timebase hardware captures;
- <=25 us skew;
- <=0.1 ms total uncertainty;
- optical self-test and 10/50/90 calibration;
- full buffered 1,000-trial acquisition;
- invalid/integrity enforcement;
- three-batch repeatability;
- remount reproducibility;
- four Android input/render combinations;
- continuous/event-driven modes;
- recorded display/presentation configuration;
- qualification/engineering separation;
- Display Pro HL 200-nit workflow;
- raw re-analyzable session package;
- Rust analyzer metrics/comparison;
- same-device distinction between center shift and rotated gradient;
- no causal claim from cross-device evidence alone.

- [ ] **Step 4: Mark instrument status**

Only if all required v1 criteria pass, set the report status to `Publication-grade v1 qualified`. Otherwise leave it `Not publication-grade` and list blocking criteria; do not soften acceptance thresholds.

- [ ] **Step 5: Commit**

```bash
git add qualification

git commit -m "docs: complete v1 measurement qualification"
```

---

## Completion Gate

This plan is complete only when the repository contains traceable evidence for every required v1 acceptance criterion and the final report accurately reflects that evidence. “Works on my device” is insufficient: the fixture uncertainty, session repeatability, remount reproducibility, session integrity, analyzer recomputation, and same-device orientation interpretation must all be demonstrated from preserved raw data.