# Reference Analyzer and Reporting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the cross-platform Rust reference analyzer that validates raw sessions, enforces qualification rules, computes publication metrics, compares portrait/landscape runs, and generates reproducible machine-readable and chart/report outputs.

**Architecture:** The analyzer consumes the canonical `slt-protocol` crate rather than reimplementing binary/session parsing. Analysis proceeds in explicit stages—integrity validation, trial qualification, per-batch statistics, condition aggregation, spatial metrics, comparison, then rendering—so raw evidence is never mutated and every derived result is reproducible.

**Tech Stack:** Rust/Cargo, `clap`, `serde`/`serde_json`, `csv`, `sha2`, `zip`, `plotters`, standard statistical routines implemented against sorted `f64` samples.

**Spec:** `docs/superpowers/specs/2026-08-31-screen-latency-measurement-design.md`

## Global Constraints

- The analyzer is the long-term analytical authority; Android summaries are convenience only.
- Raw trial timestamps are never modified in place.
- Tick-to-time conversion uses each batch header's recorded `timebase_hz`.
- Authoritative statistics include only analytically valid raw trials while invalid raw trials remain reportable.
- Center median is the headline metric; minimum is never substituted as the headline.
- Signed `Delta_H = T_right - T_left` and `Delta_V = T_bottom - T_top` must retain direction.
- Same-device `Delta_rotation` is the causal orientation metric; cross-device differences must not be labeled rotation overhead by themselves.
- Engineering sessions never receive a qualified report regardless of sample quality.
- A failed integrity gate must remain visible; the analyzer must not “repair” a session into qualification.
- Existing v1 sessions must remain re-analyzable after analyzer changes.

---

## File Structure

```text
analyzer/
├── Cargo.toml
├── src/
│   ├── main.rs
│   ├── cli.rs
│   ├── input.rs
│   ├── qualification.rs
│   ├── stats.rs
│   ├── spatial.rs
│   ├── compare.rs
│   ├── report.rs
│   └── chart.rs
└── tests/
    ├── validate_sessions.rs
    ├── statistics.rs
    ├── spatial_metrics.rs
    ├── portrait_landscape.rs
    └── cli.rs
```

Add `analyzer` to the root Cargo workspace.

---

### Task 1: Scaffold the analyzer CLI around the canonical protocol crate

**Files:**
- Create: `analyzer/Cargo.toml`
- Create: `analyzer/src/main.rs`
- Create: `analyzer/src/cli.rs`
- Create: `analyzer/src/input.rs`
- Modify: root `Cargo.toml`
- Create: `analyzer/tests/cli.rs`

**Interfaces:**

Consumes `slt_protocol::SessionBundleV1` and `slt_protocol::DecodedTrialFileV1` from the contracts plan.

```text
slt validate <session-or-zip>
slt analyze <session-or-zip> --out <directory>
slt compare <portrait-session> <landscape-session> --out <directory>
```

- [ ] **Step 1: Write failing CLI tests**

```rust
#[test]
fn validate_accepts_canonical_session() {
    let output = slt().args(["validate", canonical("qualified-minimal")]).output().unwrap();
    assert!(output.status.success());
}

#[test]
fn validate_rejects_corrupt_digest() {
    let path = corrupted_copy();
    let output = slt().args(["validate", path]).output().unwrap();
    assert!(!output.status.success());
}
```

- [ ] **Step 2: Verify failure**

Run: `cargo test -p slt-analyzer --test cli`

- [ ] **Step 3: Implement input loading**

Support either an unpacked session directory or `.slt-session.zip`. Zip extraction/reading must reject absolute paths, `..`, and duplicate member names before reading session files. Pass the resulting virtual/session root to `slt-protocol` validation.

- [ ] **Step 4: Run tests on Linux/macOS/Windows-compatible paths**

Run: `cargo test -p slt-analyzer`

Expected: canonical valid session succeeds; path traversal, duplicate zip members, digest mismatch, unsupported major schema, and missing required files fail with nonzero exit status and actionable messages.

- [ ] **Step 5: Commit**

```bash
git add Cargo.toml Cargo.lock analyzer
git commit -m "feat: scaffold latency analyzer CLI"
```

---

### Task 2: Implement trial qualification and batch integrity evaluation

**Files:**
- Create: `analyzer/src/qualification.rs`
- Create: `analyzer/tests/validate_sessions.rs`
- Modify: `analyzer/src/input.rs`

**Interfaces:**

```rust
pub struct BatchQualification {
    pub total_trials: usize,
    pub valid_trials: usize,
    pub invalid_rate: f64,
    pub center_valid_rate: f64,
    pub integrity_faults: Vec<String>,
    pub qualified: bool,
}

pub fn qualify_batch(
    session: &slt_protocol::SessionBundleV1,
    batch: &slt_protocol::DecodedTrialFileV1,
) -> BatchQualification;
```

- [ ] **Step 1: Write exact threshold tests**

Cover 1,000-trial boundaries:

- 990 valid / 10 invalid can qualify if center validity >=99.5% and no integrity fault;
- 989 valid fails;
- 995 center-valid passes center threshold;
- 994 center-valid fails;
- any USB disconnect/timebase discontinuity/undervoltage/calibration corruption/display mode change/app lifecycle interruption/swapchain recreation/orientation change/duplicate HID/buffer overflow fails regardless of trial count.

- [ ] **Step 2: Verify failure**

Run: `cargo test -p slt-analyzer --test validate_sessions`

- [ ] **Step 3: Implement trial validity and batch gate logic**

Raw trial validity requires `status_flags == 0` and nonzero capture tick for all five sensors. Keep a breakdown of every raw status bit across the batch. Session-level Android integrity flags are combined with firmware integrity flags; neither side can clear the other's fault.

- [ ] **Step 4: Verify engineering/qualification mode semantics**

Tests must prove:

- engineering session always reports `qualified=false`;
- qualification session whose stored manifest says qualified but recomputation fails is reported as inconsistent/failing, not trusted blindly;
- qualification session may be recomputed as passing only when all recorded gates and analyzer rules pass.

- [ ] **Step 5: Commit**

```bash
git add analyzer/src/qualification.rs analyzer/src/input.rs analyzer/tests/validate_sessions.rs
git commit -m "feat: enforce latency batch qualification"
```

---

### Task 3: Implement deterministic descriptive statistics and percentiles

**Files:**
- Create: `analyzer/src/stats.rs`
- Create: `analyzer/tests/statistics.rs`

**Interfaces:**

```rust
#[derive(Debug, Clone, Serialize)]
pub struct DistributionStats {
    pub count: usize,
    pub min_ms: f64,
    pub median_ms: f64,
    pub mean_ms: f64,
    pub p95_ms: f64,
    pub p99_ms: f64,
    pub stddev_ms: f64,
}

pub fn ticks_to_ms(delta_ticks: u64, timebase_hz: u64) -> f64;
pub fn summarize_ms(samples: &[f64]) -> Result<DistributionStats, StatsError>;
```

Percentiles use linear interpolation between adjacent ordered samples with rank `p * (n - 1)`. Standard deviation is population standard deviation over the valid measured population for that batch/combined condition.

- [ ] **Step 1: Write numerical tests with hand-computable vectors**

```rust
#[test]
fn percentile_definition_is_stable() {
    let s = summarize_ms(&[0.0, 10.0, 20.0, 30.0, 40.0]).unwrap();
    assert_eq!(s.median_ms, 20.0);
    assert_eq!(s.p95_ms, 38.0);
    assert_eq!(s.p99_ms, 39.6);
}
```

Also test `ticks_to_ms(8333, 1_000_000) == 8.333` within floating tolerance and reject zero `timebase_hz`/empty sample sets.

- [ ] **Step 2: Verify failure**

Run: `cargo test -p slt-analyzer --test statistics`

- [ ] **Step 3: Implement statistics without rounding intermediate values**

Round only report formatting. JSON/CSV derived outputs preserve at least microsecond-equivalent precision in milliseconds.

- [ ] **Step 4: Add property tests for monotonic percentiles**

Verify `min <= median <= p95 <= p99 <= max` for generated nonempty sample sets and identical input yields zero standard deviation.

- [ ] **Step 5: Commit**

```bash
git add analyzer/src/stats.rs analyzer/tests/statistics.rs
git commit -m "feat: calculate reference latency statistics"
```

---

### Task 4: Implement five-sensor and spatial scanout analysis

**Files:**
- Create: `analyzer/src/spatial.rs`
- Create: `analyzer/tests/spatial_metrics.rs`

**Interfaces:**

```rust
pub enum ScanDirection {
    TopToBottom,
    BottomToTop,
    LeftToRight,
    RightToLeft,
    Indeterminate,
}

pub struct SpatialTrial {
    pub top_ms: f64,
    pub left_ms: f64,
    pub center_ms: f64,
    pub right_ms: f64,
    pub bottom_ms: f64,
    pub delta_h_ms: f64,
    pub delta_v_ms: f64,
}
```

- [ ] **Step 1: Write signed-delta tests**

For a synthetic left-to-right scan:

```rust
assert_eq!(trial.delta_h_ms, 8.0);
assert_eq!(trial.delta_v_ms, 0.0);
```

For right-to-left, `delta_h_ms` must be negative. Do not absolute-value it in the raw derived data.

- [ ] **Step 2: Verify failure**

Run: `cargo test -p slt-analyzer --test spatial_metrics`

- [ ] **Step 3: Implement per-trial and aggregate spatial analysis**

Compute distributions for each of five sensors plus signed `Delta_H`/`Delta_V`. Infer scan direction only when the dominant signed delta magnitude exceeds both:

1. the orthogonal median absolute delta by at least 4x; and
2. the fixture's recorded total uncertainty by at least 5x.

Otherwise report `Indeterminate`. This prevents the analyzer from inventing direction from noise.

- [ ] **Step 4: Verify canonical portrait/landscape fixture**

Use `protocol/vectors/sessions/portrait-landscape-pair` and confirm one session infers vertical scan, the other horizontal scan, while center medians remain equal within fixture precision.

- [ ] **Step 5: Commit**

```bash
git add analyzer/src/spatial.rs analyzer/tests/spatial_metrics.rs
git commit -m "feat: analyze display scanout geometry"
```

---

### Task 5: Aggregate three batches and enforce repeatability

**Files:**
- Modify: `analyzer/src/qualification.rs`
- Modify: `analyzer/src/stats.rs`
- Add tests to `statistics.rs` and `validate_sessions.rs`

**Interfaces:**

```rust
pub struct ConditionAnalysis {
    pub batches: Vec<BatchAnalysis>,
    pub combined_center: DistributionStats,
    pub batch_center_medians_ms: Vec<f64>,
    pub median_span_ms: f64,
    pub repeatable: bool,
}
```

- [ ] **Step 1: Write boundary tests for the 0.5 ms rule**

Three medians `[20.0, 20.3, 20.5]` pass; `[20.0, 20.3, 20.5001]` fail before report rounding.

- [ ] **Step 2: Verify failure**

Run: `cargo test -p slt-analyzer statistics validate_sessions`

- [ ] **Step 3: Implement combined valid-trial aggregation**

Combine only valid raw trials from the three independently qualified batches. Preserve per-batch summaries and compute combined distributions from actual trial samples; do not average medians/percentiles to obtain combined statistics.

- [ ] **Step 4: Verify canonical session behavior**

Analyzer must flag a condition with individually valid batches but >0.5 ms median span as unstable/unqualified for publication.

- [ ] **Step 5: Commit**

```bash
git add analyzer/src analyzer/tests
git commit -m "feat: enforce condition repeatability"
```

---

### Task 6: Implement same-device portrait/landscape comparison

**Files:**
- Create: `analyzer/src/compare.rs`
- Create: `analyzer/tests/portrait_landscape.rs`
- Modify: `analyzer/src/cli.rs`

**Interfaces:**

```rust
pub struct OrientationComparison {
    pub portrait_center_median_ms: f64,
    pub landscape_center_median_ms: f64,
    pub delta_rotation_ms: f64,
    pub portrait_delta_h_ms: f64,
    pub portrait_delta_v_ms: f64,
    pub landscape_delta_h_ms: f64,
    pub landscape_delta_v_ms: f64,
    pub matched_configuration: bool,
    pub interpretation: ComparisonInterpretation,
}
```

- [ ] **Step 1: Write the core interpretation tests**

Test A: equal center median, vertical-to-horizontal spatial change => `ScanoutGeometryRotatedNoCenterPenalty`.

Test B: landscape center shifts +8.3 ms under matched configuration => `CandidateCenterLatencyShift`, never a specific SurfaceFlinger/HWC cause.

Test C: mismatched refresh/luminance/renderer/input/power config => comparison is non-causal and `matched_configuration=false`.

- [ ] **Step 2: Verify failure**

Run: `cargo test -p slt-analyzer --test portrait_landscape`

- [ ] **Step 3: Implement strict match checks**

Same-device causal comparison requires matching:

- DUT identity/build;
- fixture hardware/firmware revision;
- measured luminance inside 195–205 nits and within recorded comparison tolerance;
- fixed refresh rate;
- input path;
- renderer;
- render loop;
- workload;
- threshold;
- transition direction;
- performance state;
- qualified status.

Orientation/rotation fields are expected to differ.

- [ ] **Step 4: Calculate `Delta_rotation`**

Use `landscape median - portrait median` exactly. Report fixture uncertainty beside the value. Do not label a nonzero result causal if match checks fail.

- [ ] **Step 5: Commit**

```bash
git add analyzer/src/compare.rs analyzer/src/cli.rs analyzer/tests/portrait_landscape.rs
git commit -m "feat: compare portrait and landscape latency"
```

---

### Task 7: Add refresh-phase and frame-quantization diagnostics

**Files:**
- Modify: `analyzer/src/stats.rs`
- Create tests in `analyzer/tests/statistics.rs`

**Interfaces:**
- Produces diagnostic clustering evidence; it never changes authoritative raw latency values.

- [ ] **Step 1: Write synthetic clustered-distribution tests**

Create samples centered around `20`, `28.333`, and `36.666` ms for a recorded 120-Hz period. Verify the diagnostic recognizes approximately one-frame-separated clusters. Create a uniform/noisy distribution and verify it reports insufficient evidence rather than forcing a cluster claim.

- [ ] **Step 2: Implement refresh-period-aware diagnostic**

Use the recorded effective refresh period and histogram/local-density peaks. A reported frame-quantized shift requires at least two populated peaks separated within 10% of an integer refresh period and each peak containing >=5% of valid trials. Otherwise output `not_detected` or `insufficient_evidence`.

- [ ] **Step 3: Add PRNG/delay correlation check**

Report whether scheduled inter-trial delay modulo refresh period appears pathologically concentrated. This is a diagnostic warning only; do not claim a mathematically uniform refresh phase from PRNG configuration alone.

- [ ] **Step 4: Run tests**

Run: `cargo test -p slt-analyzer statistics`

- [ ] **Step 5: Commit**

```bash
git add analyzer/src/stats.rs analyzer/tests/statistics.rs
git commit -m "feat: add frame timing diagnostics"
```

---

### Task 8: Generate JSON, CSV, charts, and publication report

**Files:**
- Create: `analyzer/src/report.rs`
- Create: `analyzer/src/chart.rs`
- Modify: `analyzer/src/main.rs`
- Modify: `analyzer/src/cli.rs`
- Create report snapshot tests

**Interfaces:**
- `slt analyze` outputs `analysis.json`, `trials.csv`, `batch-summary.csv`, `latency-histogram.svg`, `spatial-summary.svg`, and `report.md`.
- `slt compare` additionally outputs `comparison.json` and `orientation-comparison.svg`.

- [ ] **Step 1: Write report-content tests**

Assert every authoritative report includes:

- center min/median/mean/P95/P99/stddev;
- five sensor summaries;
- signed `Delta_H` and `Delta_V`;
- inferred direction and evidence state;
- invalid rate/breakdown;
- all three batch medians;
- combined valid-trial count/statistics;
- refresh/display mode;
- measured luminance;
- transition direction;
- fixture/firmware revision;
- qualification status.

Comparison report adds `Delta_rotation`, transform evidence references, matched/non-matched status, and conservative interpretation.

- [ ] **Step 2: Verify failure**

Run: `cargo test -p slt-analyzer report`

- [ ] **Step 3: Implement deterministic outputs**

Sort JSON object-producing collections before serialization where order is not intrinsic. CSV uses one row per raw trial and includes `valid`, raw status flags, raw ticks, converted milliseconds, and batch/session IDs. SVG charts must include units and sample count; do not omit invalid-rate context from the report.

- [ ] **Step 4: Analyze canonical fixtures end-to-end**

Run:

```bash
cargo run -p slt-analyzer -- analyze protocol/vectors/sessions/qualified-minimal --out /tmp/slt-analysis
cargo run -p slt-analyzer -- compare protocol/vectors/sessions/portrait-landscape-pair/portrait protocol/vectors/sessions/portrait-landscape-pair/landscape --out /tmp/slt-compare
```

Expected: deterministic report files and the canonical pair reports no center penalty while identifying rotated spatial scanout.

- [ ] **Step 5: Commit**

```bash
git add analyzer
git commit -m "feat: generate latency analysis reports"
```

---

### Task 9: Add cross-platform regression and backward-compatibility CI

**Files:**
- Modify: `.github/workflows/ci.yml`
- Create: `analyzer/tests/backward_compatibility.rs`

**Interfaces:**
- Protects every committed v1 golden session from accidental analyzer/protocol incompatibility.

- [ ] **Step 1: Add regression tests over every v1 vector**

Enumerate `protocol/vectors/sessions` and require every non-corrupt canonical v1 session to parse/analyze successfully. Keep explicit negative fixtures separate and assert their expected failure class.

- [ ] **Step 2: Add analyzer CLI smoke runs to CI**

On Linux/macOS/Windows execute:

```bash
cargo test --workspace --all-targets
cargo run -p slt-analyzer -- validate protocol/vectors/sessions/qualified-minimal
```

- [ ] **Step 3: Add formatting/lint gates**

```bash
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
```

- [ ] **Step 4: Verify full CI locally where possible**

Run: `cargo test --workspace --all-targets`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ci.yml analyzer/tests
git commit -m "test: protect v1 analyzer compatibility"
```

---

## Completion Gate

The analyzer plan is complete when:

- unpacked and zipped session inputs are safely validated;
- qualification rules reproduce the <=1% invalid, >=99.5% center-valid, zero-integrity-fault, and <=0.5 ms three-batch median-span gates;
- statistics match the documented percentile definition;
- five sensor and signed spatial metrics are computed from valid raw trials;
- scan direction is conservative rather than inferred from noise;
- same-device comparison computes `Delta_rotation` and refuses causal framing when configurations are mismatched;
- canonical rotated-scanout fixture proves unchanged center latency is not called a rotation penalty;
- raw-trial CSV, derived JSON, charts, and Markdown report are generated reproducibly;
- all committed v1 sessions remain analyzable on Linux, macOS, and Windows CI.