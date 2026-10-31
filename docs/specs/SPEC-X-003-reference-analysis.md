# SPEC-X-003 — Cross-platform Reference Analyzer and Reporting

**Document ID:** SPEC-X-003  
**Status:** Draft
**Readiness note:** Interval estimator and final session version require explicit technical qualification  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
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

The matched experiment is logical portrait (A), landscape (B), landscape (B), portrait (A), with panel-fixed sensors. Each block contains three 1,000-trial batches for each direction. Compare only sessions with matching device/build, fixture hardware/firmware, calibrated 195–205 nits per condition and ≤2 nits difference within the pair, fixed refresh, power preset, input, renderer, render-loop, workload, threshold and direction. Different logical orientation is expected. Missing or unsupported evidence makes a **non-causal or inconclusive** comparison.

Compute and publish signed center `Delta_rotation` alongside an uncertainty interval and the predefined ±1.0 ms practical-effect interval. Estimator must account for order/time drift, batch/block correlation, fixture uncertainty and repeated sessions; a trial-level bootstrap treating all 12,000 observations as independent orientation replications is forbidden. Pre-register confidence level, contrast/estimation method and retake/stopping criteria before the first publication qualification; if insufficient independent evidence, report inconclusive rather than a false narrow interval. ABBA does not prove orientation is the only changing subsystem or identify the Android composition stage.

Interpretation categories must distinguish:
- supported materially positive or negative rotation effect outside ±1 ms,
- detected but within the practical-effect region,
- supported practical equivalence (interval contained within ±1 ms),
- inconclusive or unmatched/invalid.
Thresholding must respect the uncertainty interval, not just point estimate.

The original canonical sample that required a physical scanout gradient to rotate under logical rotation is invalid as a universal model. Include at least one synthetic same-device matched pair with unchanged physical gradient and center; another with center shift but unchanged physical gradient; and separately genuine measured gradient change. Never infer causality from the gradient axis alone.

## Diagnostics

Use the actual refresh period to flag frame-quantized distributions when ≥2 populated peaks are about integer-frame apart (within 10%) and each contains ≥5% valid samples; otherwise not detected or insufficient evidence. Report scheduled/actual inter-trial delay modulo refresh period and explicitly warn about possible phase locking; do not assert uniform refresh phase merely from random seed. Android clock events are diagnostics keyed by HID sequence only. Do not fuse independent clocks at unvalidated sub-ms precision.

## Outputs

Produce deterministic `analysis.json`, `trials.csv` (one line per raw trial including failures), `batch-summary.csv`, histogram SVG, signed spatial SVG and `report.md`. Comparison adds `comparison.json` and orientation comparison SVG; all label units, valid/invalid sample counts, parameters, confidence and evidence gaps.

Publication reports include min/mean/median/P95/P99/SD, all five channel summaries, signed gradients, effective refresh, physical/logical orientation evidence, luminance, direction, per-batch medians, repeatability, fixture uncertainty/version, failed gates and causal interpretation limits. CLI and report output must never silently upgrade an engineering run to qualified.

## Testing

Golden wire/raw/zip vectors, corrupt manifests, traversal, reserved bits and unsupported schema, repeated PWM edges, all invalidity thresholds and zero-integrity faults, exact percentile math, signed gradients, scan-direction threshold boundaries and indeterminate cases, three-batch span thresholds, canonical ABBA/unchanged-gradient counterexamples, mismatched orientation, non-equivalence despite zero point estimate with broad interval, and deterministic snapshots on macOS/Linux/Windows. Every existing committed v1 session must remain re-analyzable after analyzer changes; protect this with backward-compatibility tests over the committed v1 vectors.
