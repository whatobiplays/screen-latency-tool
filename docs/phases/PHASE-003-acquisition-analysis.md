# PHASE-003 — Complete Acquisition and Reference Analysis

**Document ID:** PHASE-003  
**Status:** Draft
**Readiness note:** Depends on PHASE-002 functional instrument  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
**Depends On:** PHASE-002, ARCH-001, REF-MET-001  
**Supersedes:** Remaining Android, contracts, analyzer and reporting checkbox plans  
**Superseded By:** None

## Observable outcome

From a single Android bench application, the operator can qualify luminance/refresh/thermal stability, run repeatable autonomous batches in both transition directions, export unmodified versioned sessions, and re-analyze them on macOS/Windows/Linux into deterministic publication-style reports with clear qualification evidence.

## Required specifications

SPEC-X-002, SPEC-X-003, SPEC-X-004, REF-WIRE-001, REF-DATA-001. Validation of protocol is shared across Rust, Kotlin and firmware.

## In scope

- Final authoritative Native/Vulkan continuous rendering and diagnostic native/framework/GLES paths.
- Display Pro HL manual 200 ±5-nit workflow, physical-panel geometry evidence, 5-minute warm-up/3-minute stability, fixed-refresh qualification, integrity handling and engineering mode.
- Three batches x 1,000 trials per condition with post-completion-only vendor retrieval, export schema and immutable file hashes.
- Rust CLI validation, per-trial stats, signed five-sensor spatial analysis, repeatability, counterbalanced ABBA comparison and published output artifacts.
- Deterministic golden/reference fixtures including constant physical scanout under logical rotation, failed sessions, trial integrity failures and diagnostic overclaim safeguards.

## Exclusions

No public consumer onboarding, camera instrumentation, HDR, built-in button actuation, or claimed qualification based only on simulation.

## Exit criteria

Physical DUT with actual fixture completes a full condition that the reference analyzer independently validates. CI passes across specified surfaces; engineering and failed sessions cannot become qualified; an ABBA grouping can produce conservative uncertainty-aware comparison outputs. Final publication confidence, remounting, and causal proof remain PHASE-004.
