# ARCH-001 — Screen Latency Measurement System

**Document ID:** ARCH-001  
**Status:** Ready for Implementation  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
**Depends On:** None  
**Supersedes:** August 31 system-level sections of legacy screen-latency design  
**Superseded By:** None

## Purpose and questions

Build a repeatable Android handheld input-to-photon measurement instrument answering:

1. Does changing the **logical** presentation orientation on one portrait-native device introduce a real additional center-screen latency?
2. How do portrait-native and landscape-native handhelds compare in practical measured latency and physical-panel scanout geometry?

These are distinct. Cross-device differences are descriptive, confounded by SoC, Android version, compositor, display controller, panel, TCON and OEM policy. Causal claims about rotation require controlled same-device comparisons.

## System boundaries

- **Fixture:** externally powered Teensy 4.1 acting as a normal USB gamepad plus separately claimed vendor control interface; autonomous 1,000-trial batches; one common hardware timestamp domain; five independently calibrated optical channels.
- **Android:** API 30+ internal test app. Kotlin owns operator workflow, USB Host control, qualification, display state, and raw export. C++ owns input/render stimulus (native Vulkan authoritative, GLES/framework diagnostic).
- **Reference analyzer:** portable Rust CLI on macOS/Linux/Windows; validates immutable raw evidence, recomputes qualification and reports results independent of Android.
- **Bench:** manual Calibrite Display Pro HL absolute luminance measurement; Rigol DHO814 or equivalent external oscilloscope for bus-level timing qualification; a mechanically registered, adjustable 3–9-inch fixture.

Firmware, Android and analyzer share explicitly versioned transport/session contracts; they never derive one component's timestamps from another component's unrelated clock.

## Twelve non-negotiable measurement invariants

1. Successful measured USB HID IN **completion** defines `T0`; HID enqueue time does not.
2. `T0` and five hardware-captured optical timestamps use one fixture-controlled monotonic timebase.
3. Pre-completion HID polling wait is excluded from the authoritative latency metric.
4. Raw fixture timestamps and invalid records remain immutable; calibrated corrections and analysis are separate.
5. Every raw trial is retained, classified, and re-analyzable.
6. Android clocks and diagnostics do not enter the authoritative metric.
7. Cross-device authoritative luminance is independently verified at 200 ± 5 cd/m².
8. Fixed refresh or proven fixed cadence is required for authoritative rotation conclusions; VRR experiments are diagnostic.
9. Same-device matched orientation controls are the primary evidence for a rotation effect.
10. Software logical rotation does **not** imply physical-panel scanout direction rotates; all five sensor coordinates stay fixed to the panel.
11. Acquisition, calibration evidence, and later analysis are independently reproducible.
12. Engineering measurements cannot be misrepresented as publication-qualified results.

## Timing and architecture targets

- Headline: median physical-center HID-completion-to-T50 latency for a single transition condition (never the minimum).
- Optical sensors: panel-fixed top (0.5,0.1), left (0.1,0.5), center (0.5,0.5), right (0.9,0.5), bottom (0.5,0.9).
- Timestamp resolution ≤10 µs; calibrated channel skew ≤25 µs; characterized full fixture timing uncertainty ≤0.1 ms.
- Fixed-refresh, VSync-synchronized continuous game-like rendering, native input + Vulkan, full-screen black/white, minimal workload are authoritative.
- Three independently seeded 1,000-trial batches per authoritative condition; invalidate batches on integrity faults and retain the failed evidence.
- Same-device publication comparison uses counterbalanced ABBA acquisition blocks, with three batches per block and separate black-to-white / white-to-black analysis.
- Interpretation is effect estimate + uncertainty interval. Initial practical relevance bound is ±1.0 ms, separately from statistical detectability.

## Validation and causal inference

A measured horizontal gradient on a sideways panel is geometry, not proof of added input latency. Neither a shift in center median nor nominal A/B matching alone identifies SurfaceFlinger, HWC, GPU composition, TCON, or another root cause. Independent diagnostics are required for subsystem attribution.

Avoid pseudo-replication: 3,000 trial observations are not 3,000 independent orientations or hardware remounts. The reference analyzer must expose batch/block-level uncertainty and report **material difference**, **practical equivalence**, or **inconclusive** when evidence warrants each category. An inconclusive result must not be converted into a no-penalty conclusion.

## Design rationale and rejected options

- **High-speed camera:** rejected as v1 authority owing to frame-rate quantization and large-sample acquisition overhead; hardware optical capture is the primary metric.
- **Built-in button actuation:** rejected because scan/debounce/transport differs across devices and confounds the rotation question. Future controller-inclusive work is separate.
- **One movable photodiode:** rejected because different locations would be sampled on different refresh phases. Five simultaneous channels resolve signed spatial differences for the same trial.
- **ADC-only timing:** rejected because ADC sample scheduling introduces timing uncertainty; comparator threshold crossings with hardware capture are preferred.
- **External waveform ADC:** rejected as mandatory v1 hardware owing to cost/complexity; analog test points preserve a diagnostic path.
- **Bus-powered fixture:** rejected to avoid the DUT powering or destabilizing the instrument. A regulated external supply and anti-backfeed design are required.
- **Android-only analysis:** rejected so raw sessions can be reprocessed with improved methodology.
- **Exhaustive matrix:** rejected in favor of minimal authoritative conditions and targeted engineering diagnostics.
- **Production PCB first:** rejected; verify USB/HID/T0/capture and a reusable one-sensor Android/optical path before manufacturing.
- **Logical-coordinate sensors:** rejected; physical-panel coordinates are authoritative (ADR-0001).

## Explicit v1 exclusions

High-speed-camera authority, built-in controller actuation, controller-inclusive headline, HDR methodology, direct Android Display Pro HL integration, ICC/LUT profiling, public Play Store UX/distribution, routine Perfetto, private mandatory thermal APIs, and exhaustive all-mode testing. These are future investigations, not v1 promises.

## Requirements and exit criteria

System publication readiness is evidence-driven and governed by SPEC-X-004. Functional prototypes, host tests, firmware compilation, schematic correctness or a single working trial **do not** constitute timing qualification. See REF-MET-001 for measurement definitions and PHASE-000–004 for capability boundaries.
