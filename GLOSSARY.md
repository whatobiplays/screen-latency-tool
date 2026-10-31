# Screen Latency Measurement Glossary

**Document ID:** GLOSSARY-001  
**Status:** Ready for Implementation

This glossary defines measurement and orientation terms used consistently across the fixture, Android application, qualification procedures, and analyzer.

## Display geometry

**Physical-panel coordinates**:
Normalized positions within the active illuminated display rectangle, fixed to the panel's physical reference edges rather than Android's currently rendered orientation.
_Avoid_: Logical screen coordinates, orientation-relative sensor coordinates

**Active display rectangle**:
The physical pixel-emitting display area used to place sensors, excluding non-emitting bezels or chassis geometry.

**Chassis orientation**:
The physical orientation of the handheld body relative to the fixture and laboratory reference axes, independent of Android's requested content orientation.

**Logical display orientation**:
The orientation in which Android presents application content (such as portrait or landscape), which may differ from the physical panel or chassis orientation.

**Physical scanout gradient**:
The signed difference in observed photon-transition times between fixed sensor positions on the panel. A software content rotation does not by itself establish that this gradient must rotate.

## Measurement

**HID-completion-to-photon latency**:
Elapsed time from the fixture's successfully completed measured USB HID IN transaction to the sensor's calibrated display-luminance threshold crossing. It intentionally excludes controller scanning, debounce, and pre-completion USB polling.

**Center T50 latency**:
HID-completion-to-photon latency measured at the physical-panel center using the calibrated 50-percent luminance crossing.

**Same-device rotation effect**:
The difference in center T50 latency between matched logical-orientation conditions on the same device. Attribution of a specific display subsystem as the cause requires independent diagnostic evidence.
_Avoid_: Cross-device rotation overhead

**Practical-effect threshold**:
A predeclared difference magnitude used to distinguish practically meaningful orientation effects from small effects; statistical detectability and practical relevance are separate judgments. The value is fixed at ±1.0 ms, is never estimated from data, and both conclusions use one 95% two-sided final interval, which is more conservative than the 90% two-sided interval equivalent to a 5% TOST equivalence test.

**ABBA orientation comparison**:
A counterbalanced sequence of matched logical portrait, logical landscape, logical landscape, and logical portrait acquisition blocks with sensors fixed to the physical panel, intended to reduce confounding from approximately monotonic time drift. One such sequence is a complete ABBA cycle, and a publication-grade comparison uses exactly four accepted cycles, analysed once for each transition direction.

## Inference and uncertainty

**Complete ABBA cycle**:
One uninterrupted acquisition session holding the four consecutive blocks A1, B1, B2, A2. Each block holds three qualified batches for each transition direction, so one cycle supplies both directions and a direction that fails a gate voids the whole cycle. The cycle, not the trial, the batch or the adjacent block pair, is the independent replication unit for a same-device orientation comparison.
_Avoid_: Batch-level replication, trial-level replication

**ABBA cycle contrast**:
The signed value one complete ABBA cycle contributes to the comparison, signed landscape minus portrait. It uses the closed form (B1 + B2 - A1 - A2) / 2 when block spacing is balanced and the time-adjusted weighted regression contrast when it is not.
_Avoid_: Single-transition difference, headline estimate

**Experiment-relative time**:
A monotonic ordering coordinate for acquisition blocks, derived from recorded fixture clock epochs, reset records and offsets within a cycle. It orders blocks and weights the drift adjustment, and never enters authoritative latency values. Independent cycles may use separate epochs and require no inter-cycle offset.
_Avoid_: Wall-clock timestamp, raw t0_ticks

**Representative block time**:
One time coordinate per block and transition direction: the arithmetic mean of the measured HID-completion timestamps of the valid trials contributing to that block's center T50 summary, expressed in experiment-relative seconds. It is fixed before analysis, and a block start, midpoint or trial-derived alternative is not admissible.
_Avoid_: Block start time, midpoint time

**Standard uncertainty**:
A quantity expressing the dispersion of an uncertainty component as a standard-deviation equivalent, carrying its own degrees of freedom and its level as a comparison-level, per-cycle or per-block input. A fixture component also states why the four-cycle dispersion does not already represent it. Standard components are combined before any coverage factor is applied.
_Avoid_: Expanded uncertainty, worst-case bound

**Worst-case bound**:
An absolute limit on an uncertainty contribution, stated without a probability interpretation. Bounds are added arithmetically after other terms are expanded and are never quadrature-combined. Each bound declares the block positions and cycles it can affect, because one affected position and every affected position carry different sensitivities.
_Avoid_: Standard deviation, type-B standard uncertainty

**Expanded uncertainty**:
The coverage-factor expansion of the standard-uncertainty term, written `U95 = k u_c`. It excludes bounded fixture contributions and the curvature bound, which enter the total half-width.
_Avoid_: Total half-width, final interval

**Total half-width**:
`H = U95 + sum c_i b_i + B`: the distance from the adjusted estimate to either final endpoint. Each bounded contribution and the curvature bound appears in `H` exactly once, and the final interval is the estimate plus or minus `H`.
_Avoid_: Expanded uncertainty, confidence interval width

**Effective degrees of freedom**:
The combined degrees of freedom of an uncertainty budget. It describes a budget; the coverage factor used for a comparison is fixed at the smallest component value, because effective-value combination can narrow an interval when a component is added.
_Avoid_: Coverage factor

**Curvature-bias bound**:
An independently justified worst-case limit on the bias that quadratic or higher-order time drift can introduce into a cycle contrast, which no in-cycle diagnostic can estimate or test. It is added arithmetically to the final interval and never combined in quadrature.
_Avoid_: Drift correction, estimated systematic error

**Statistical detection**:
The conclusion that the final interval excludes zero, reported as positive, negative or not established. Independent of practical significance.
_Avoid_: Material effect, significant difference

**Practical classification**:
The conclusion that the final interval lies wholly beyond, wholly inside, or across the fixed ±1.0 ms practical-effect boundaries, reported as material increase, material decrease, supported practical equivalence, or indeterminate.
_Avoid_: Detected effect, practical significance

**Supported practical equivalence**:
A practical classification in which the whole final interval lies inside (-1.0, +1.0) ms. It can hold at the same time as statistical detection, and a near-zero point estimate alone never establishes it.
_Avoid_: No effect, no difference

**Not-evaluable comparison**:
A comparison withheld from inference because at least one evidence or qualification requirement failed. It retains every failing reason, reports the first code in the fixed precedence order as its primary reason, and publishes no interval, detection conclusion or practical classification.
_Avoid_: No effect, negative result

**Evidence admissibility**:
The status of a comparison's supporting evidence, either `admissible` or `not_evaluable`. Only an admissible comparison proceeds to inference, and only it can be reported as complete.
_Avoid_: Inconclusive result, no effect

**Uninformative inference**:
A completed comparison on admissible evidence whose final interval establishes neither statistical detection nor a practical classification. It is a valid result with no evidence-failure reason, and is never reported as no effect.
_Avoid_: Not-evaluable comparison, no effect
