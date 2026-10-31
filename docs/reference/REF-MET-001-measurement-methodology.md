# REF-MET-001 — Authoritative Measurement Methodology

**Document ID:** REF-MET-001  
**Status:** Ready for Implementation  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
**Depends On:** ARCH-001, ADR-0001, ADR-0003  
**Supersedes:** §§2–3, 7–9, 12–20 and 23–25 of the legacy measurement design  
**Superseded By:** None

## Coordinates and clocks

The optical overlay registers against the physical active display rectangle and never moves when Android changes logical orientation. Five sensors are addressed in immutable order:

| Channel | Normalized physical-panel position |
| --- | --- |
| Top | (0.50, 0.10) |
| Left | (0.10, 0.50) |
| Center | (0.50, 0.50) |
| Right | (0.90, 0.50) |
| Bottom | (0.50, 0.90) |

The fixture also stores the active physical rectangle (mm), chassis-to-panel registration, logical orientation, requested/effective Android rotation and relevant compositor transform evidence separately. A hardware physical scanout gradient can stay fixed when the logical image rotates. No algorithm may presume `Delta_H` and `Delta_V` interchange under 90-degree logical rotation.

`T0` is successful completion of the exact measured HID IN transfer (verified at USB transaction level), not report queue time. Optical edge timestamp `T_i` is a hardware-latched timer capture in the same monotonic timer domain, with raw integer ticks retained. Authoritative per-sensor latency: `L_i = (T_i - T0) / timebase_hz`. Android timestamps are diagnostic with explicit independent clock labels; do not claim sub-millisecond cross-clock alignment from trial ID alone.

Spatial differences are computed **within the same valid trial**: `Delta_H = T_right - T_left`, `Delta_V = T_bottom - T_top`. Preserve signed values; `T0` cancels in these differences. Infer scan direction conservatively: dominance over orthogonal gradient ≥4x and over characterized fixture uncertainty ≥5x are provisional diagnostic thresholds; otherwise label indeterminate. Never label a spatial gradient alone a rotation overhead.

## Optical crossing

Standard reference is `T50`, the first **qualified** crossing of the calibrated 50% amplitude from that sensor's black to white output (or white to black for the descending transition). Diagnostics may measure `T10`/`T90`. A white-to-black `T10` occurs near 90% of black-to-white amplitude; `T90` near 10%. Store sensor ADC black and white levels, actual DAC codes, calibration direction and revision. A comparator first edge is not automatically a valid pixel transition: hysteresis, arms after `T0`, bounded repeated-edge history and PWM/ambiguous flags are required. Flag fundamentally ambiguous PWM conditions as unsuitable for authoritative comparison.

## Headline, repeatability, and comparison

The headline per authoritative condition is **center T50 median HID-completion-to-photon latency**. Minimum is auxiliary; report mean, min, median, P95, P99, population SD, all five sensors, signed deltas and validity breakdown.

Every condition uses three qualified 1,000-trial batches with independently recorded random seeds. Combined distributions are computed from all valid raw trials, never by averaging batch percentiles. Provisionally, the span of the three center medians must be ≤0.5 ms for a repeatable condition.

The same-device signed effect is `Delta_rotation = median(center, logical landscape) - median(center, logical portrait)`. Both orientations need their own qualified luminance checks. **Practical relevance is defined a priori as ±1.0 ms**; a statistically detectable smaller effect is reported without treating it as practically material.

Publication-grade orientation comparisons use a physical-fixture-fixed **ABBA** block sequence (portrait, landscape, landscape, portrait), each block containing three independently seeded 1,000-trial batches per measured transition direction. Re-check any invalidated luminance, threshold, refresh or thermal gate after orientation changes. This counterbalances approximately monotonic time drift but cannot alone eliminate all confounding. Counterbalanced block identity, ordering, UTC start time, and condition-change interventions are preserved.

Inference reports a signed effect estimate and uncertainty interval from a method that accounts for batch/block correlation and fixture uncertainty. Trial-count-only confidence intervals treating thousands of samples as independent conditions are forbidden. Before the first publication report, pre-register the interval construction, uncertainty combination, ABBA drift-adjustment method, confidence level and stopping/retake rule. If data do not support a defensible interval, declare the comparison **inconclusive**. A supported positive or negative material effect, statistical detection within ±1.0 ms, supported practical equivalence, and inconclusive outcomes are distinct from claims about specific SurfaceFlinger/HWC/TCON root causes.

## Operating conditions

- Authoritative: SDR fullscreen black `#000000` ↔ fullscreen RGB white `#FFFFFF`; 50% crossing; native input, Vulkan, continuous VSync game-style loop, minimal workload; no event-triggered special render.
- Run fixed 60 Hz when the device genuinely supports it, plus a genuinely supported fixed native/high refresh mode and any matched fixed comparison rates. Selecting a native/high mode does not by itself establish fixed cadence; verify and record fixed-cadence evidence. Never synthesize 60 Hz. VRR runs remain diagnostic.
- Independent manual Display Pro HL center-screen measurement is 195–205 cd/m² for each authoritative condition; for same-device orientation matched pair absolute measurement difference ≤2.0 nits.
- Warm up ≥5 min under representative workload before the final Display Pro HL reading; after colorimeter removal, fixture installation and sensor calibration, pass a 3-minute stability window.
- Stability window: sensor `SAMPLE_ONLY` black/white optical amplitude every 10 s, each accepted amplitude within ±2.0% of first reference; meaningful temperatures sampled every 10 s with first/last 30-second median difference <1°C; no throttle, refresh/configuration drift.
- Battery/power: same-device pair uses the same power state; cross-device default is balanced/normal OEM mode, no saver, battery power unless needed for mode, roughly 50–80% charge when practical, no throttling.
- Reverify absolute luminance and recalibrate sensor thresholds following any intervention invalidating brightness/display/optical reference. Relative photodiode readings never replace absolute nit measurement.
- OLED calibration stimulus: nominal black 2 s / white 1 s / black 2 s repeat to reduce unnecessary full-white dwell and ABL/thermal effects.

## Trial generation and validity

- Measured HID press triggers configured black-to-white or white-to-black; unmeasured release resets to baseline with captures disarmed.
- Randomized nominal wait 200–500 ms; keep PRNG seed, scheduled ticks and **actual elapsed interval** (exact encoding finalized at hardware feasibility). Avoid phase locking.
- Optical channels arm only after successful measured HID completion; collect five qualified captures or missing markers, bounded repeat-edge diagnostics, then release/reset and verify baseline.
- All 1,000 records remain in RAM until the batch completes. Android makes **no vendor USB requests during acquisition**; only after demonstrable completion may results download.
- A trial is analytically valid only when no status faults and all five required sensor timestamps are present. Invalid records remain in the raw stream.
- A batch qualifies with overall invalid rate ≤1%, center valid rate ≥99.5%, and **zero** batch-level integrity faults. USB disconnect/re-enumeration, timebase discontinuity, undervoltage, corruption, mode/rotation/refresh changes, renderer or lifecycle interruption, duplicate HID and RAM overflow fail the entire batch. A failure is retained, not silently retried or reclassified.

## Diagnostic modes and limits

Alternate native/GLES or framework input paths, event-driven loops, synthetic 25/50/75/90% frame workloads, isolated patches, 10/90% thresholds, VRR, queue-depth/presentation variants, power presets, brightness sweeps, ADB and Perfetto are **investigations**, not interchangeable with the authoritative baseline. Cross-device observations are reviewer-facing comparisons, not causal proof of orientation overhead.
