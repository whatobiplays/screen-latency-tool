# Rotation conclusions require uncertainty-aware evidence

Status: Accepted (2026-10-29)

A difference between two center-screen median latencies is a descriptive estimate, not by itself proof of a rotation penalty. Publishable conclusions about same-device logical rotation must account for fixture uncertainty, between-batch/session variation, and possible time-dependent drift. Report an effect estimate and uncertainty interval, distinguish a supported material difference from an equivalence/near-zero finding and from an inconclusive result, and avoid attributing an observed effect to a specific Android or display subsystem without additional diagnostic evidence.

The initial practical-effect threshold is **1.0 ms in either direction**: retain the signed median difference at full precision with an uncertainty interval and distinguish a meaningful difference, practical equivalence, and inconclusive evidence. A detectable sub-1.0-ms effect is reportable without being called practically meaningful. The exact interval estimator, uncertainty treatment, and treatment of correlated observations must be fixed in the methodology specification before publication qualification.

Publication-grade same-device comparison uses a counterbalanced **ABBA** orientation sequence (A = logical portrait; B = logical landscape), with three independently seeded 1,000-trial batches per block, repeated separately for black-to-white and white-to-black. Re-evaluate condition-specific calibration and stability gates after orientation changes. This mitigates approximately monotonic time drift better than one unidirectional A/B comparison, but does not itself eliminate all confounding.

Rejected alternatives: labeling any nonzero center-median difference a causal penalty, treating 3,000 raw trials as independent experiment replications for confidence intervals, and testing A then B only for publication-grade comparisons.
