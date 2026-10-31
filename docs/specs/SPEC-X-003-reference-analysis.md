# SPEC-X-003 — Cross-platform Reference Analyzer and Reporting

**Document ID:** SPEC-X-003  
**Status:** Draft
**Readiness note:** The orientation-effect method is pre-registered in REF-MET-001. Session schema freeze and analyzer implementation remain open.  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-30  
**Depends On:** ARCH-001, REF-MET-001, REF-DATA-001  
**Supersedes:** Analyzer requirements in legacy design and reporting checkbox plan  
**Superseded By:** None

## Interface

Portable Rust CLI on Linux/macOS/Windows, using the shared canonical protocol library and deterministic golden vectors:

- `slt validate <session-directory-or-zip>`
- `slt analyze <session-directory-or-zip> --out <directory>`
- `slt compare <portrait-session> <landscape-session> --out <directory>` (or a declared ABBA grouping once finalized)

Do not reimplement validation separately in CLI. Zip validation must reject absolute/traversing/duplicate entries, invalid UTF-8 paths, unexpected digest/size, unrecognized schema versions, engineering session claiming qualified or contradictory metadata.

## Processing stages

1. Verify manifest hashes, archive safety, format and version; preserve all raw bytes.
2. Recompute validity and batch integrity from raw trial and Android/fixture evidence; manifest `qualified` is not trusted without recomputation.
3. Convert tick differences using recorded `timebase_hz`, apply independently stored correction parameters without mutating raw ticks.
4. Compute per-batch and combined center/five-channel distributions over **valid** trials, invalid fractions and all reason bits.
5. Compute per-trial signed physical `Delta_H` and `Delta_V`. Infer scan direction only when the dominant signed-delta magnitude is at least 4× the orthogonal median absolute delta and at least 5× the fixture's recorded total uncertainty; otherwise report `Indeterminate`.
6. Require three independently qualified batches and ≤0.5 ms span of their center T50 medians for a condition to qualify.
7. Compare matched same-device orientation blocks or comparable cross-device conditions, respecting luminance/refresh/renderer/input/thermal/evidence requirements.
8. Generate reproducible machine-readable reports, CSV and labeled charts; never hide failed or excluded trials.

Stats: min, median, arithmetic mean, P95, P99, population SD; percentile rank `p*(n-1)` with linear interpolation between adjacent ascending samples. Intermediate conversions are not rounded; displayed milliseconds retain at least µs-equivalent precision. Combined distributions use actual per-trial samples, not a mean of per-batch medians or percentiles. Zero timebase, no valid samples and malformed timestamps are explicit failures.

A 1,000-trial batch qualifies with ≤1% overall invalid, ≥99.5% center valid, and no integrity fault. A condition requires three such batches plus ≤0.5 ms center-median span. Remount reproducibility ≤0.5 ms center-median span across three full mounts is separately required for instrument qualification.

## Same-device ABBA inference

The matched experiment is four consecutive blocks — logical portrait (A), landscape (B), landscape (B), portrait (A) — with panel-fixed sensors. Each block contains three independently seeded 1,000-trial batches for each transition direction. One such sequence is a complete ABBA cycle that supplies both transition directions, and a publication-grade comparison uses exactly four accepted cycles, analysed once per direction; a direction that fails a qualification gate voids the whole cycle. Compare only sessions with matching device/build, fixture hardware/firmware, calibrated 195–205 nits per condition and ≤2 nits difference within the pair, fixed refresh, power preset, input, renderer, render-loop, workload, threshold and direction. Different logical orientation is expected. Missing or unsupported evidence makes the comparison **non-causal and not evaluable**: every failing requirement is retained, and `UNMATCHED_EVIDENCE` heads the fixed precedence order.

[REF-MET-001](../reference/REF-MET-001-measurement-methodology.md) owns the method. The analyzer implements it without discretion:

1. Recompute block summaries as pooled-trial center T50 medians over valid trials, per block and transition direction.
2. Compute the representative time `t_j(dir)` for each block and direction as the arithmetic mean of the measured HID-completion timestamps of the valid trials contributing to that summary, in experiment-relative seconds. Fail `UNMATCHED_EVIDENCE` when a contributing timestamp is absent, duplicated or unparseable.
3. Verify the clock-epoch record. The four representative times of a direction must share one within-cycle coordinate, with recorded offsets for any intra-cycle discontinuity. Independent cycles need no shared epoch and no inter-epoch offset. Record the actual durations between blocks and admit the cycle only while every gap and duration stays inside the conditions covered by that cycle's curvature-bias evidence; the conditioning diagnostic limits collinearity only and does not police elapsed time.
4. Compute each cycle contrast with the closed form when the block spacing is balanced and the weighted time-adjusted form otherwise, recording the weights `w` and the conditioning diagnostic `||d~||^2`. A cycle below 0.5 is reported `DRIFT_UNIDENTIFIABLE`.
5. Aggregate `Delta_rotation,adjusted` as the mean of the accepted cycle contrasts and report `s_d`, `u_stat` and `nu_stat = 3`.
6. Classify every declared fixture component by level and form. Propagate standard components through the recorded weights or their `kappa` sensitivity, include covariance only where independently justified, and refuse any component already represented by `s_d`.
7. Compute `u_standard`, `u_c` and `U95 = k u_c`; add each bounded component with its declared scope and sensitivity coefficient and `B = max B_r` exactly once to form the total half-width `H = U95 + sum c_i b_i + B`. Expanded quantities are never quadrature-combined and `B` is never applied twice.
8. Determine `evidence_status`. A failed requirement yields `not_evaluable`, retains every failing reason in `evidence_failures`, and reports the first code in the fixed precedence order as `primary_reason`.
9. Produce statistical-detection and practical-classification conclusions independently from the final interval whenever the evidence is admissible. A completed comparison that establishes neither conclusion remains a valid inference.
10. Report the descriptive `Delta_rotation,pooled` and the difference `Delta_rotation,adjusted` minus `Delta_rotation,pooled` as a diagnostic that cannot change the classification.

A trial-level bootstrap treating thousands of observations as independent orientation replications remains forbidden, as does any interval derived from trial counts alone.

Interpretation categories are two independent outputs:

- **statistical detection** — positive, negative or not established, from whether the final interval excludes zero;
- **practical classification** — material increase, material decrease, supported practical equivalence, or indeterminate, over the fixed ±1.0 ms boundaries.

The two are independent by construction. An interval crossing +1.0 ms can establish positive detection while practical materiality stays indeterminate, and an interval inside (-1.0, +1.0) establishes supported equivalence while possibly also establishing detection. There is no composite "detected but submaterial" category. Thresholding uses the final interval and never the point estimate alone.

Evidence admissibility is a separate layer. `evidence_status` is `admissible` or `not_evaluable`. A comparison is `not_evaluable` only when an evidence or qualification requirement fails; it then publishes no interval and no conclusion, retains every failing reason in `evidence_failures`, and reports the first code in the precedence order as `primary_reason`. When the evidence is admissible the comparison is complete whatever the interval shows: an interval from -2.0 to +2.0 ms yields `statistical_detection = not_established` and `practical_classification = indeterminate`, which is a valid uninformative inference and carries no evidence-failure reason.

The fixed evidence-failure codes are `UNMATCHED_EVIDENCE`, `QUALIFICATION_GATE_FAILED`, `INSUFFICIENT_INDEPENDENT_REPLICATION`, `INDEPENDENCE_UNSUPPORTED`, `DRIFT_UNIDENTIFIABLE`, `UNBOUNDED_DRIFT_CONFOUNDING` and `INSUFFICIENT_FIXTURE_UNCERTAINTY`, in that precedence order. All failures are retained; only the first present is reported as the primary reason. ABBA does not prove orientation is the only changing subsystem or identify the Android composition stage.

The original canonical sample that required a physical scanout gradient to rotate under logical rotation is invalid as a universal model. Include at least one synthetic same-device matched pair with unchanged physical gradient and center; another with center shift but unchanged physical gradient; and separately genuine measured gradient change. Never infer causality from the gradient axis alone.

## Diagnostics

Use the actual refresh period to flag frame-quantized distributions when ≥2 populated peaks are about integer-frame apart (within 10%) and each contains ≥5% valid samples; otherwise not detected or insufficient evidence. Report scheduled/actual inter-trial delay modulo refresh period and explicitly warn about possible phase locking; do not assert uniform refresh phase merely from random seed. Android clock events are diagnostics keyed by HID sequence only. Do not fuse independent clocks at unvalidated sub-ms precision.

## Outputs

Produce deterministic `analysis.json`, `trials.csv` (one line per raw trial including failures), `batch-summary.csv`, histogram SVG, signed spatial SVG and `report.md`. Comparison adds `comparison.json` and orientation comparison SVG; all label units, valid/invalid sample counts, parameters, confidence and evidence gaps.

`comparison.json` records, per transition direction and per cycle: block summaries, the representative time `t_j(dir)`, the contrast `d_r`, the weights `w`, the conditioning diagnostic, the clock-epoch record and the per-cycle bound `B_r`. It records for the comparison: `Delta_rotation,pooled`, `Delta_rotation,adjusted`, their discrepancy, `s_d`, `u_stat`, `nu_stat`, every uncertainty component with its source, level, scope, affected block positions, form and degrees of freedom or bound, `u_standard`, `u_c`, `nu_min`, `U95`, the bounded contribution `sum c_i b_i`, `B`, the total half-width `H`, the final interval, `evidence_status`, every failing reason with its evidence, `primary_reason`, the detection conclusion and the practical classification. It also records the acquisition ledger: attempts per slot, admission decisions and their timestamps, and failed attempts with their failing criteria.

Publication reports include min/mean/median/P95/P99/SD, all five channel summaries, signed gradients, effective refresh, physical/logical orientation evidence, luminance, direction, per-batch medians, repeatability, fixture uncertainty/version, failed gates and causal interpretation limits. CLI and report output must never silently upgrade an engineering run to qualified.

## Testing

Golden wire/raw/zip vectors, corrupt manifests, traversal, reserved bits and unsupported schema, repeated PWM edges, all invalidity thresholds and zero-integrity faults, exact percentile math, signed gradients, scan-direction threshold boundaries and indeterminate cases, three-batch span thresholds, canonical ABBA/unchanged-gradient counterexamples, mismatched orientation, non-equivalence despite zero point estimate with broad interval, and deterministic snapshots on macOS/Linux/Windows.

Comparison goldens reproduce every worked example in REF-MET-001 and add the case classes that the method turns on:

- identical point estimate with a narrow interval supporting equivalence and a wide interval remaining indeterminate;
- a zero point estimate whose interval spans the threshold, producing indeterminate rather than equivalence;
- positive detection with indeterminate practical materiality, and an interval strictly inside the thresholds that reports both detection and equivalence;
- an endpoint lying exactly on ±1.0 ms, which leaves practical classification indeterminate while detection stands;
- mixed comparison-level standard, per-block bounded and curvature terms in one interval, with the bounded term and `B` each added once to `H` and never quadrature-combined;
- a bound on one named block position contributing 0.050000 ms, and the same bound applying to every position contributing 0.200000 ms;
- the curvature bound counted once in `H`, with the twice-counted interval `[0.039, 0.801]` ms rejected;
- a component already represented by cycle-to-cycle variation being refused as a double count, while a shared comparison-level component is admitted;
- fixture propagation through unequal-spacing weights, with the maximally adverse combination used where covariance is unjustified;
- corrected versus uncorrected drift estimates under equal and unequal spacing, including the conditioning boundary;
- cross-cycle correlation reducing effective evidence, reported as `INDEPENDENCE_UNSUPPORTED`;
- missing or discontinuous within-cycle block timing failing closed, while independent cycles whose epochs have no recorded offset remain admissible;
- a constant translation of one cycle's four representative times leaving the weights, the conditioning diagnostic and the contrast unchanged;
- per-direction representative times derived from the contributing trial timestamps, with a shared block start time producing a different estimate;
- multiple simultaneous evidence failures retained with only the first in the fixed precedence order reported as primary;
- a completed comparison whose interval spans both thresholds reporting detection not established and practical indeterminate without any evidence-failure reason;
- retake exhaustion producing `INSUFFICIENT_INDEPENDENT_REPLICATION`;
- the quadratic-confounding counterexample, where endpoint excursion and cubic residual are both zero and the contrast is exactly wrong;
- absent or invalid curvature evidence producing `UNBOUNDED_DRIFT_CONFOUNDING`.

Every existing committed v1 session must remain re-analyzable after analyzer changes; protect this with backward-compatibility tests over the committed v1 vectors.
