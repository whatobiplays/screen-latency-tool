# PHASE-004 — Publication-grade Instrument Qualification

**Document ID:** PHASE-004  
**Status:** Draft
**Readiness note:** Depends on PHASE-003 complete measurement pipeline  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
**Depends On:** PHASE-003, ARCH-001, REF-MET-001  
**Supersedes:** End-to-end legacy qualification checkbox plan  
**Superseded By:** None

## Observable outcome

The instrument's accuracy, repeatability, remount behavior, measured fixed-refresh operation and interpretation of same-device orientation are independently evidenced by retained raw physical measurements. Published review claims are traceable to instrument revisions, test conditions, and versioned reference analysis.

## Required specification

SPEC-X-004, which owns the precise evidence gates; SPEC-X-003 owns conservative statistical interpretation and reporting.

## In scope

- Scope-based USB HID completion `T0` characterization and proof of ≤0.1 ms total timing uncertainty; ≤25 µs corrected inter-channel skew, optical calibration/self-test and PWM/saturation resilience.
- Complete Android fixed-refresh and 200 ±5 nit confirmation/thermal stability qualification under real DUT conditions.
- Qualified black/white 1,000-trial triplets, ≤0.5 ms batch median repeatability, ≤0.5 ms remount center-median reproducibility across three cycles.
- Physical-panel-fixed ABBA orientation experiments with independently confirmed brightness, matched refresh/presentation and block-aware uncertainty intervals relative to ±1 ms practical-effect bound.
- Matched genuine fixed-refresh cross-device comparison with no cross-device-only causal claims, diagnostic escalation, independent reproducible reports and archived raw data.

## Exclusions

No test failure waiver, fabricated sensor measurements, assumption that logical rotation changes physical scanout, or publication-grade declaration from software-only tests.

## Exit criteria

All applicable SPEC-X-004 physical/electrical/optical/Android/analysis gates have preserved evidence paths; the v1 qualification report accurately marks PASS/FAIL/BLOCKED and declares **publication-grade v1 qualified** only if all mandatory gates pass. Otherwise the project remains **not publication-grade** with specific GitHub-tracked blockers.
