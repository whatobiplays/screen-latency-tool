# REF-MET-001 — Authoritative Measurement Methodology

**Document ID:** REF-MET-001  
**Status:** Ready for Implementation  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-30  
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

The same-device signed effect is always signed landscape minus portrait and is named `Delta_rotation`. It has two estimators, defined in **ABBA effect inference** below: the descriptive `Delta_rotation,pooled`, which is the pooled-trial median difference `median(center, logical landscape) - median(center, logical portrait)`, and the inferential `Delta_rotation,adjusted`. Both orientations need their own qualified luminance checks. **Practical relevance is defined a priori as ±1.0 ms**; a statistically detectable smaller effect is reported without treating it as practically material.

Publication-grade orientation comparisons use a physical-fixture-fixed **ABBA** block sequence (portrait, landscape, landscape, portrait), each block containing three independently seeded 1,000-trial batches per measured transition direction. One such four-block sequence is a complete **ABBA cycle**, and a publication-grade comparison uses exactly four accepted cycles, analysed once for each transition direction. Re-check any invalidated luminance, threshold, refresh or thermal gate after orientation changes. Counterbalancing cancels drift that is linear in time but cannot alone eliminate all confounding. Counterbalanced block identity, ordering, UTC start time, and condition-change interventions are preserved.

Inference reports a signed effect estimate and uncertainty interval from a method that accounts for batch, block and cycle correlation and for fixture uncertainty. Trial-count-only confidence intervals treating thousands of samples as independent conditions are forbidden. The interval construction, uncertainty combination, drift adjustment, confidence level and stopping rule are pre-registered in **ABBA effect inference** below and are not chosen after the data are seen. If the data do not support a defensible interval, the comparison is recorded `not_evaluable`. A supported positive or negative material effect, supported practical equivalence, an inference that establishes neither, and a `not_evaluable` comparison are distinct from claims about specific SurfaceFlinger/HWC/TCON root causes.

## ABBA effect inference

This section is the pre-registered analysis method for the same-device landscape-minus-portrait comparison. It fixes the estimator, the drift adjustment, the uncertainty combination, the classification boundaries and the stopping rule before any publication-grade observation is collected. [SPEC-X-003](../specs/SPEC-X-003-reference-analysis.md) implements the method, [SPEC-X-004](../specs/SPEC-X-004-qualification.md) gates acquisition and publication on it, and [GLOSSARY-001](../../GLOSSARY.md) defines the shared vocabulary. The worked examples at the end of this section are arithmetic illustrations of the decision rules. They are not measurement results and they do not qualify any instrument.

### Analysis units

A **trial** is one raw fixture record. A **batch** is 1,000 trials recorded under one seed. A **block** is one logical orientation held constant while three qualified batches are acquired for each measured transition direction. A **complete ABBA cycle** is the four consecutive blocks `A1, B1, B2, A2` in that order, with `A` logical portrait and `B` logical landscape, acquired as one uninterrupted session. One cycle carries both transition directions, because every block holds three batches for each, and a transition direction that fails any qualification gate voids the whole cycle.

The complete ABBA cycle is the only independent replication unit for inference. Trials, batches and adjacent block pairs are never treated as independent orientation replicates. A publication-grade comparison uses exactly four accepted complete cycles, analysed once for each transition direction, for 48,000 scheduled trials per direction and 96,000 per comparison. The two transition directions are analysed separately and are never pooled.

### Estimands

`Delta_rotation` is always signed landscape minus portrait. Two named estimators are reported for the same comparison.

- `Delta_rotation,pooled` — **descriptive**. The difference of the pooled-trial center T50 medians over all valid trials of each orientation across the accepted cycles. This is the headline definition and it makes no inferential claim.
- `Delta_rotation,adjusted` — **inferential**. The mean of the four cycle contrasts. Only this estimate and its final interval support statistical detection or practical classification.

The two are reported together. Their difference is a diagnostic: the estimators answer slightly different questions, and the reported interval is not a test of their agreement. A discrepancy is explained in the report and never by itself invalidates the comparison.

### Cycle contrast and drift adjustment

Block summaries `s_j` are the pooled-trial median center T50 latency over a block's valid trials for the transition direction under analysis. Positions `j = 1..4` are `A1, B1, B2, A2`. Let `d_j` be the condition indicator, 0 for portrait and 1 for landscape, and let `t_j` be the representative block time defined below for the transition direction under analysis.

For balanced block spacing the cycle contrast is the closed form

```text
d_r = (s_B1 + s_B2 - s_A1 - s_A2) / 2
```

which equals the mean of the two portrait-to-landscape transitions and, for this balanced spacing, cancels any drift that is linear in time. With unequal spacing the unadjusted closed form retains a linear-drift bias, and the time-adjusted contrast below is required.

For unequal spacing the cycle contrast is the drift-adjusted regression contrast

```text
d_r = sum_j w_j s_j            w_j = d~_j / sum_j d~_j^2
```

where `d~` is the condition indicator residualized on `[1, t]`. The closed form is the special case `w = (-1/2, +1/2, +1/2, -1/2)`, and the general form reduces to it exactly when the spacing is balanced. The weights are recorded and reused unchanged in the uncertainty propagation.

The **design conditioning diagnostic** `||d~||^2` equals 1.0 for balanced spacing and falls as the block times become collinear with the condition indicator. A cycle with `||d~||^2 < 0.5` — a contrast variance inflation above 2x the balanced design — is reported with the reason `DRIFT_UNIDENTIFIABLE`. This diagnostic limits design collinearity only. It does not validate the linear drift assumption and it does not bound curvature.

Unequal or gapped timing is not policed against an invented duration limit. Gaps and durations are admissible only while they remain inside the conditions covered by the independently justified curvature-bias evidence described below; timing outside that evidence yields `UNBOUNDED_DRIFT_CONFOUNDING`.

### Representative block time

The contrast needs one time coordinate per block and per transition direction. That coordinate is fixed before the comparison is analysed, and no implementation may substitute a block start, midpoint, end or trial-derived alternative after inspecting results.

For block `j` and transition direction `dir`, the representative time `t_j(dir)` is the arithmetic mean of the measured HID-completion `t0_ticks` timestamps of the valid trials that contribute to that block's center T50 summary for that direction, converted to experiment-relative seconds. Each transition direction carries its own representative time, because the two directions are acquired at different moments inside the same block. The pre-registered statistic is the mean; a median, a first or last trial, or a batch-qualified subset is not admissible.

Invalid trials do not contribute, exactly as they do not contribute to the block summary, and their timestamps remain in the raw evidence. A valid trial whose timestamp record is absent, duplicated or unparseable makes that block coordinate unreconstructible and the comparison `UNMATCHED_EVIDENCE`. A block that does not retain three qualified batches of that direction is a qualification failure, not a coordinate question.

All timestamps contributing to one representative time must be reconstructible on one continuous experiment-relative coordinate. A single clock epoch satisfies that requirement; a block may span more than one epoch only when validated offsets establish that continuous coordinate across the whole span. A counter reset inside a block is admissible only with a recorded offset linking the parts, because the mean is taken over one continuous coordinate. Epoch identifiers, reset and boot records and any intra-block offset record are retained with the block so the coordinate can be reproduced.

### Experiment-relative time

Raw fixture `t0_ticks` are comparable only inside one declared clock epoch. Each block records its clock-epoch identifier, any counter reset or boot record, and the mapping that produces experiment-relative seconds.

Inside one cycle the four representative times of a direction must share a valid common coordinate, established directly or through recorded inter-epoch offsets. Recorded offsets are required only for a discontinuity inside that cycle: a counter reset, a reboot between blocks, or an unlinked clock change. An interrupted acquisition voids the cycle.

Independent cycles need no shared epoch and no offset between epochs. A cycle contrast uses only differences of that cycle's four times, and every weight vector sums to zero, so adding any constant to all four times of one cycle leaves its weights, its conditioning diagnostic and its contrast unchanged. A cycle recorded in a fresh epoch with an unknown offset to the other cycles is therefore admissible; requiring an inter-cycle offset would impose evidence the estimator does not use.

A missing epoch identifier, an unlinked reset inside a cycle, a counter discontinuity inside a cycle or a missing intra-cycle offset makes that cycle's coordinate unestablishable and the comparison `DRIFT_UNIDENTIFIABLE`. Experiment-relative time orders blocks and weights the drift adjustment; it never enters the authoritative latency values, which remain the measured HID-completion-to-photon intervals.

### Aggregation and cycle independence

```text
Delta_rotation,adjusted = (1/R) sum_r d_r        R = 4 accepted cycles
u_stat = s_d / sqrt(R)                            nu_stat = R - 1 = 3
```

Here `s_d` is the sample standard deviation of the accepted cycle contrasts.

Cycle independence is an assumption, not a consequence of separate sessions, separate power cycles or separate clock epochs. The variance above treats accepted cycles as uncorrelated. If they share a slow thermal, power or display-state trend, the true sampling uncertainty is larger and the nominal interval is too narrow; a systematic bias common to all cycles is not removed by repeating them. Supporting evidence for the assumption is a distinct acquisition session and clock epoch per cycle, a fresh fixture mount or power cycle between cycles where practical, and a recorded statement of whether cycles were continuous or separated.

When that evidence is absent, incomplete or contradicted — several cycles acquired continuously inside one thermal envelope, an unresolved clock-epoch overlap, or a recorded interruption — the comparison is `INDEPENDENCE_UNSUPPORTED`, and no publication-grade detection or equivalence claim is made from the nominal three degrees of freedom.

### Uncertainty combination

The combination rule follows the form and the level of the evidence supplied. Each entry names its source, its value, its form, its level and its degrees of freedom or bound status. Its level is one of three: a comparison-level quantity identical for every accepted cycle, a cycle-level quantity attached to one accepted cycle contrast, or a per-block quantity attached to one block summary. An entry that mixes forms for one component, omits its degrees of freedom or its level, or cannot be interpreted fails closed as `INSUFFICIENT_FIXTURE_UNCERTAINTY`.

**Standard-uncertainty components.** Each standard component `i` carries `u_i`, its own `nu_i`, a level, and either a weight vector or a sensitivity `kappa_i`. Cycle-level and per-block components are propagated through the actual contrast weights:

```text
u_incremental^2 = (1/R^2) sum_r w_r^T Sigma_r w_r
                + (1/R^2) sum_{r != r'} w_r^T Cov(r, r') w_r'
```

where `Sigma_r` holds the declared per-block standard components of cycle `r`. A comparison-level component enters with `kappa = 1`. The standard term is

```text
u_standard^2 = u_stat^2 + u_incremental^2 + sum_i kappa_i^2 u_i^2
               + 2 sum_{i<k} kappa_i kappa_k Cov(i, k)
```

with the covariance sum taken over the comparison-level components and present only where their covariance is independently justified. `u_stat = s_d / sqrt(R)`. Every fixture term is propagated through the actual contrast weights, so an unequal-spacing design widens its own budget. The coverage factor is fixed by the least-known component:

```text
nu_min = minimum of the finite component degrees of freedom     (nu_stat = 3)
u_c = sqrt(u_standard^2)
k = t(0.975, nu_min)
```

**Bounded components.** Each bounded component is an absolute bound `b_i` with a declared scope and sensitivity coefficient `c_i`. Its scope names the affected block positions, the affected accepted cycles and whether it applies at comparison, cycle or block level; a component whose scope is not stated is inadmissible. Bounds are added arithmetically and are never quadrature-combined, divided by a coverage factor or treated as standard uncertainties.

The expanded standard-uncertainty contribution and the total final half-width are distinct quantities:

```text
U95 = k u_c
H = U95 + sum_i c_i b_i + B
final_interval = [Delta_rotation,adjusted - H, Delta_rotation,adjusted + H]
```

`U95` contains only the coverage-factor expansion of the standard term. Each bounded component and `B` enters `H` exactly once, so `B` is never applied to the interval a second time.

Every sensitivity coefficient is dimensionless, and only the product `c_i b_i` carries the units of the bound. A bound on a comparison-level quantity has `c_i = 1`. A bound on a single named block position `j` in every accepted cycle has `c_i = (1/R) sum_r |w_r,j|`, which is the dimensionless coefficient `0.5` under the balanced four-cycle design and produces a contribution of `0.5 b_i`. A bound that every block position of every cycle can independently reach has `c_i = (1/R) sum_r sum_j |w_r,j|`, which is the dimensionless coefficient `2.0` under the balanced four-cycle design and produces a contribution of `2.0 b_i`. A signed per-block bound with pattern `a_r,j` has `c_i = (1/R) sum_r |sum_j w_r,j a_r,j|`. The pattern coefficients `a_r,j` are dimensionless relative to the declared bound, so `c_i` remains dimensionless. A bound on a quantity common to all four blocks of a cycle has `sum_j w_r,j = 0` and contributes exactly zero; that is the only cancellation admitted without further evidence. `B` is the curvature-bias bound defined below.

**Special cases.** In an all-standard budget every `b_i` and `B` are zero, so `H = U95 = k u_c`. In an all-bounded budget no standard fixture component is declared, so `u_standard^2 = u_stat^2` and `H = k u_stat + sum_i c_i b_i + B`. `u_stat` is present in every budget; it is zero only when the four accepted cycle contrasts are identical, which is a property of the data and never a claim of zero uncertainty.

Fixing the factor at `nu_min` rather than at a Welch–Satterthwaite effective value is deliberate. The effective-value construction is not monotone: with `u_stat = 0.20 ms` and `nu_stat = 3`, adding a 0.10 ms fixture component raises the effective degrees of freedom to 4.6875, lowers the factor from 3.1824 to 2.6230 and narrows the interval from 0.6365 ms to 0.5865 ms. More uncertainty must never buy more confidence. Because an effective value is never below the smallest component `nu`, the fixed-`nu_min` interval also dominates the effective-value interval. For a budget extended by an independent nonnegative variance contribution with the covariance model unchanged, the interval widens strictly; that monotonicity claim is not extended to budgets where a justified negative covariance is introduced or the covariance model otherwise changes. No maximum guard is used and no mathematical coverage guarantee is claimed; the documented limitations are that the construction assumes approximately normal contrasts and does not correct for skew.

**Provenance.** `s_d` estimates the dispersion of the four realized cycle contrasts, so its sampling variance already contains every random effect that actually differs between the accepted cycles: trial and batch noise, fixture remount variation and independent fixture measurement noise. A standard component is admissible only when its entry states why it is not already represented by `s_d`, on one of three grounds:

- the component is identical for every accepted cycle and does not cancel in the contrast, so cycle dispersion cannot sample it;
- the component is declared with a covariance structure that four observed contrasts cannot estimate, and the justification for that structure is recorded;
- the entry is a deterministic bound, which is never sampled.

The four admissible categories are therefore variation sampled through the cycle contrasts, which is `u_stat`; shared or persistent uncertainty that the dispersion cannot sample, which enters with `kappa`; independently justified additional structure, which enters through the covariance term; and deterministic absolute bounds, which enter as `c_i b_i`.

A per-cycle random component whose realizations already appear in the cycle contrasts is rejected as a double count. Cancellation of a fixture term is never assumed without support; where the covariance structure is unjustified, the maximally adverse combination consistent with the declared component magnitudes is used.

### Curvature-bias bound

A four-block ABBA cycle cannot distinguish a purely quadratic drift from a condition effect. For equally spaced block times `t = 0, 1, 2, 3` the identity `t^2 = 3t - 2d` holds exactly, so `d` and a quadratic term are linearly dependent and a cycle has no residual degree of freedom that responds to curvature. Repeating cycles does not remove a bias common to them, and neither the observed endpoint excursion nor the cubic residual can demonstrate its absence.

Publication-grade classification therefore requires an independently justified worst-case absolute curvature-bias bound `B` for the acquisition conditions actually used, supported by drift-control measurement or by a validated physical constraint and never by inspection of the comparison's own results. Each accepted cycle carries its own `B_r` justified for that cycle's device, refresh, thermal and fixture conditions; a bound justified under different conditions is not transferable. The comparison bound is

```text
B = max over accepted cycles of B_r
```

which is deliberately more conservative than the equally legitimate equal-weight average of the per-cycle absolute bounds. Averaging valid bounds is acceptable; the maximum is preferred because the decision concerns one comparison at its least favourable supported condition.

The bound is the last term of the total half-width `H`, so it shifts the interval exactly once:

```text
H = U95 + sum_i c_i b_i + B
final_interval = [Delta_rotation,adjusted - H, Delta_rotation,adjusted + H]
```

`B` is a bias bound, not a distribution and not a standard uncertainty. It is never quadrature-combined and never converted into a type-B component. No numeric value may be assigned without supporting evidence.

When no defensible finite `B` exists, the estimate and its statistical uncertainty are reported descriptively and the comparison is recorded `not_evaluable` with primary reason `UNBOUNDED_DRIFT_CONFOUNDING`. The observed endpoint excursion `|s_A2 - s_A1|` and the cubic residual `-s_A1 + 3 s_B1 - 3 s_B2 + s_A2` are retained as diagnostics only.

### Classification

Two conclusions are produced independently from the final interval.

**Statistical detection** reports whether the interval excludes zero: `positive` when the lower endpoint is above zero, `negative` when the upper endpoint is below zero, otherwise `not_established`.

**Practical classification** reports the position of the interval relative to the fixed practical-effect threshold:

| Final interval | Practical classification |
| --- | --- |
| lower endpoint above +1.0 ms | material increase, landscape slower |
| upper endpoint below -1.0 ms | material decrease, landscape faster |
| interval strictly inside (-1.0, +1.0) ms | supported practical equivalence |
| any other outcome, including an endpoint exactly on a threshold | indeterminate |

The threshold is fixed in advance and is never estimated. The two conclusions overlap by design: an interval of 0.29 to 0.81 ms supports practical equivalence and simultaneously establishes positive detection, and both are reported. An interval of 0.69 to 1.21 ms establishes positive detection while practical materiality remains indeterminate; that pair is the correct output and is not replaced by a composite "detected but submaterial" label. An endpoint exactly on +1.0 ms or -1.0 ms leaves practical classification indeterminate while a separately supported detection conclusion stands.

Both conclusions use one 95% two-sided final interval. Using 95% for equivalence as well as for detection is more conservative than the conventional 5% one-sided TOST equivalence test, whose equivalent reporting interval is 90% two-sided. One interval serves both decisions so that no reporting choice remains open after the data are seen.

### Outcome structure

A comparison reports two independent layers.

**Evidence admissibility.** `evidence_status` is `admissible` or `not_evaluable`. A comparison is `not_evaluable` when any evidence requirement fails. Every failing requirement is retained in `evidence_failures` with its reason code and supporting evidence; none is dropped when several fail together. `primary_reason` is the first code present in this fixed precedence order:

1. `UNMATCHED_EVIDENCE`
2. `QUALIFICATION_GATE_FAILED`
3. `INSUFFICIENT_INDEPENDENT_REPLICATION`
4. `INDEPENDENCE_UNSUPPORTED`
5. `DRIFT_UNIDENTIFIABLE`
6. `UNBOUNDED_DRIFT_CONFOUNDING`
7. `INSUFFICIENT_FIXTURE_UNCERTAINTY`

The order runs from failure of attribution to failure of the uncertainty bound, so the primary reason is the most fundamental failure the evidence supports. A `not_evaluable` comparison publishes no interval, no detection conclusion and no practical classification, and is never reported as no effect.

**Inference result.** When `evidence_status` is `admissible`, the comparison is complete and both conclusions are reported whatever the interval yields. An interval that excludes zero in neither direction and lies across the practical threshold produces `statistical_detection = not_established` and `practical_classification = indeterminate`. That is a valid completed inference: the evidence is admissible and the data support neither conclusion. It carries no evidence-failure reason, is never called inconclusive and is never reported as no effect. The two conclusions remain separate fields; there is no combined outcome enumeration.

### Stopping and retake

A comparison has four **cycle slots**, filled in order. Each slot admits at most three acquisition attempts. An attempt is eligible only when all four blocks are present, each block holds three independently seeded qualified batches per direction, the acquisition was uninterrupted, and the cycle's clock-epoch record is complete. The first eligible attempt fills its slot.

Admission is judged only on qualification gates, recorded append-only by the acquisition process, and fixed before any analysis of the comparison. A failed attempt is retained with its failing criterion and is never deleted, relabelled or reused. No attempt may be set aside because its contrast is inconvenient. The analysis runs once, after the four slots are filled or exhausted. A cycle that invalidates either transition direction is replaced as a whole.

A slot that exhausts its three attempts makes the comparison `not_evaluable` with primary reason `INSUFFICIENT_INDEPENDENT_REPLICATION`. The bound is twelve attempts, which is 144,000 scheduled trials per direction and 288,000 across both directions. A larger effect, a narrower interval or a changed question requires a new pre-registered comparison with its own identifier; results are never pooled with the original.

### Worked examples

Each example fixes the arithmetic of a decision rule and is reproducible from its stated inputs with `t(0.975, 3) = 3.182446`. Bound values used in the examples are inputs chosen to exercise a decision rule and are not proposed instrument bounds. Each example states the standard contribution, any bounded contribution and the curvature bound, then the total half-width `H`.

**E1 — Identical point estimate, narrow and wide intervals.** Four cycle contrasts `0.40, 0.44, 0.41, 0.43` give `Delta_rotation,adjusted = 0.42`, `s_d = 0.018257` and `u_stat = 0.009129`. With a comparison-level standard fixture component of 0.05 ms, `u_c = 0.050827` and `U95 = 0.161753`; with `B = 0.10` the total half-width is `H = 0.261753` ms and the interval is `[0.158, 0.682]` ms. Detection is positive and practical classification is supported equivalence. Four contrasts `2.02, -1.40, 1.18, -0.12` give the same `0.42` estimate with `s_d = 1.499066` and `u_stat = 0.749533`; with the same components and bound the interval is `[-2.071, 2.911]` ms, detection is not established and practical classification is indeterminate. The point estimate alone carries no decision.

**E2 — Zero point estimate is not equivalence.** Contrasts `1.2, -1.1, 0.9, -1.0` give `0.00` with `u_stat = 0.609645` and, with the same components and bound, `[-2.047, 2.047]` ms. Detection is not established and practical classification is indeterminate; a near-zero estimate whose interval spans the threshold supports no equivalence claim.

**E3 — Detection established, practical materiality indeterminate.** Contrasts `0.93, 0.97, 0.94, 0.96` give `0.95` with `u_stat = 0.009129` and, with the same components and bound, `[0.688, 1.212]` ms. Detection is positive; practical classification is indeterminate because the interval crosses +1.0 ms. Neither conclusion substitutes for the other.

**E4 — Exact threshold boundary.** Four identical cycle contrasts of `0.75`, no incremental fixture component, and an independently justified `B = 0.25` give `H = 0.250000` ms and the final interval exactly `[0.500, 1.000]` ms. Detection is positive and practical classification is indeterminate because the upper endpoint lies exactly on the threshold.

**E5 — Mixed budget and provenance.** Contrasts `0.40, 0.44, 0.41, 0.43` with a comparison-level fixture standard uncertainty of 0.04 ms, identical for every accepted cycle, and an independently justified 0.10 ms curvature bound give `u_standard = 0.041028`, `U95 = 0.130571`, `H = 0.230571` and `[0.189, 0.651]` ms. Adding a 0.12 ms per-cycle component that varies independently across the accepted cycles would give `u_c = 0.126820` and `[-0.084, 0.924]` ms, but that variation already appears in `s_d`; entering it again is a double count and is rejected. If the same fixture evidence were supplied as a comparison-level worst-case bound of 0.08 ms, the statistical term `3.182446 x 0.009129 = 0.029053` plus 0.08 plus the 0.10 curvature bound gives `[0.211, 0.629]` ms, and the bound is never quadrature-combined.

**E6 — Unequal spacing propagates through the weights.** Block times `0, 10, 20, 60` minutes give weights `(-0.7432, +0.4595, +0.5405, -0.2568)`, with `sum w = 0`, `sum |w| = 2.000` and `sqrt(sum w^2) = 1.059`. A uniform 0.03 ms per-block fixture standard uncertainty contributes 0.031771 ms in quadrature and 0.060000 ms under the maximally adverse combination. The balanced design contributes 0.030000 ms and 0.060000 ms. The time adjustment widens the quadrature budget by about 5.9%; the adverse-combination budget is unchanged because `sum |w| = 2` in both designs. The same times written as `0, 1, 2, 6` minutes give identical weights and identical `||d~||^2 = 0.8916`, because the conditioning diagnostic and the weights depend on relative spacing rather than on the time unit. The 0.03 ms input is per block. Its cycle-level contribution is 0.030000 ms in quadrature for the balanced design and 0.060000 ms under the maximally adverse combination, and the corresponding comparison-level values for four independent cycles are 0.015000 ms and 0.030000 ms. Those comparison-level terms are admissible only under the provenance rule: when the same per-block noise is realized independently in every cycle, `s_d` already contains it and the separate term is omitted.

**E7 — Drift cancellation and its failure mode.** With block times `0, 10, 20, 30` minutes, a true effect of 0.80 ms and linear drift of 0.05 ms per minute, block summaries `20.00, 21.30, 21.80, 21.50` give the closed-form contrast 0.800 ms, while a single adjacent portrait-to-landscape comparison gives 1.300 ms and would cross the practical threshold by itself. At times `0, 10, 20, 60` minutes with the same drift the summaries are `20.00, 21.30, 21.80, 23.00`; the unadjusted closed form then gives 0.050 ms and the time-adjusted contrast gives 0.800 ms exactly, with `||d~||^2 = 0.8916`.

**E8 — Correlated cycles understate the interval.** With `u_stat = 0.20` ms and `R = 4`, a common correlation of 0.3 between cycle contrasts multiplies the standard error by `sqrt(1 + 3 x 0.3) = 1.3784`, so `U95` grows from 0.6365 ms to 0.8773 ms before any fixture or curvature term. Unresolved cross-cycle correlation is therefore reported as `INDEPENDENCE_UNSUPPORTED` rather than absorbed by the nominal three degrees of freedom.

**E9 — Quadratic drift is invisible to every in-cycle diagnostic.** Take block times `0, 1, 2, 3` and drift `f(t) = t(t - 3)` ms with a true effect of zero. The block drift values are `0, -2, -2, 0`. The closed-form contrast reports -2.0 ms; the endpoint excursion `|s_A2 - s_A1|` is 0; the cubic residual is 0. The identity `t^2 = 3t - 2d` explains the collapse: the whole quadratic drift is representable as a linear trend plus a condition effect. No in-cycle diagnostic and no number of repeated cycles recovers the true zero, which is why an independent `B` is mandatory.

**E10 — Retake bound.** Four slots with three attempts each allow at most twelve attempts and 144,000 scheduled trials per direction, or 288,000 across both directions. Exhausting one slot without an eligible attempt yields `INSUFFICIENT_INDEPENDENT_REPLICATION`; the comparison is not extended, and its failed attempts remain recorded.

**E11 — Independent clock epochs across cycles.** Cycle 1 is recorded in fixture epoch `E1` with representative times 0, 600, 1200 and 1800 s. The fixture is then power-cycled, and cycle 2 is recorded in epoch `E2`, whose counter restarts at zero. No offset between `E2` and `E1` was recorded, and none is required: cycle 2's representative times are 0, 600, 1200 and 1800 s in its own coordinate, both cycles are admissible, and each contrast is computed from its own four blocks.

The invariance is exact. With block summaries 20.00, 21.30, 21.80 and 21.50 ms, either cycle yields the weights `(-0.5, +0.5, +0.5, -0.5)` and a contrast of 0.800 ms. Translating cycle 2 to 3740, 4340, 4940 and 5540 s leaves the weights and the contrast unchanged, because the weights sum to zero and only differences of time enter. The adjusted estimate is the mean of the two cycle contrasts, 0.800 ms.

A missing intra-cycle offset is a different failure. If cycle 2's four blocks spanned an unlinked counter reset with no recorded offset, its four times could not be placed on one coordinate and the comparison would be `DRIFT_UNIDENTIFIABLE`, whether or not any inter-cycle offset existed.


**E12 — Per-direction representative times inside one block.** Four blocks start at 0, 10, 20 and 30 minutes. For one transition direction the valid-trial mean timestamps are 2, 12, 22 and 32 minutes, so the weights are `(-0.5, +0.5, +0.5, -0.5)`. For the other direction the mean timestamps are 4, 14, 24 and 38 minutes, because its batches ran long in the final portrait block; its weights are `(-0.5541, +0.4841, +0.5159, -0.4459)` and its conditioning diagnostic is 0.9937. The same block summaries `20.00, 21.30, 21.80, 21.50` then give 0.889 ms for that direction instead of the 0.800 ms obtained by borrowing the first direction's times. Each direction therefore keeps its own representative times; a shared block time, a block start time or a post hoc midpoint changes the estimate.

**E13 — Mixed standard, bounded and curvature budget.** Contrasts `0.40, 0.44, 0.41, 0.43` give 0.42 ms, `s_d = 0.018257` and `u_stat = 0.009129`. A comparison-level fixture standard uncertainty of 0.04 ms identical for every cycle enters with `kappa = 1`, giving `u_standard = 0.041028` and `U95 = 0.130571` ms. A separate synthetic worst-case fixture bound of 0.10 ms applies to one named block position, `A1`, in every accepted cycle; the other three positions do not carry that component. Its sensitivity is `c = (1/4) x (0.5 + 0.5 + 0.5 + 0.5) = 0.5`, contributing 0.050000 ms. An independently justified curvature bound of 0.10 ms is the last term. The total half-width is `H = 0.130571 + 0.050000 + 0.100000 = 0.280571` ms and the final interval is `[0.139, 0.701]` ms. Subtracting `B` a second time would give `[0.039, 0.801]` ms; that double count is explicitly rejected.

**E14 — Provenance regression.** With the E5 contrasts, a shared comparison-level component of 0.05 ms is admissible because no realized cycle contrast can sample it: `u_standard = sqrt(0.009129^2 + 0.05^2) = 0.050827` and `U95 = 0.161753` ms. A per-cycle component of 0.12 ms is inadmissible because its realizations are the cycle-to-cycle scatter that `s_d` already estimates; adding it would double count and is rejected. The two entries differ in level, not in magnitude.

**E15 — Bound applying to every block position.** With the E13 standard and curvature terms, suppose instead that all four block positions in every accepted cycle can independently exhibit errors of magnitude up to 0.10 ms. The sensitivity is then `c = (1/4) x 4 x (0.5 + 0.5 + 0.5 + 0.5) = 2.000`, contributing 0.200000 ms rather than 0.050000 ms. The total half-width is `H = 0.130571 + 0.200000 + 0.100000 = 0.430571` ms and the final interval is `[0.42 - 0.430571, 0.42 + 0.430571] = [-0.011, 0.851]` ms. The scope, not the magnitude, is what changes: one affected position contributes 0.050000 ms and four independently affected positions contribute 0.200000 ms for the same 0.10 ms bound.

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
