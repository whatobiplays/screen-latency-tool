# Screen Latency Tool — documentation index

This repository holds the durable technical contract for the Android screen-latency measurement instrument. **GitHub Issues for the repository resolved at runtime from the active Git checkout is the sole source of truth for actionable work, readiness, blockers, ownership, and completion status.** Specifications and ADRs define behavior and rationale; they do not track task progress.

## Canonical sources

| Layer | Document | Purpose |
| --- | --- | --- |
| Architecture | [ARCH-001](architecture/ARCH-001-system-architecture.md) | Scope, boundaries, invariants, rationale |
| Documentation governance | [ARCH-002](architecture/ARCH-002-documentation-governance.md) | Precedence, approval, GitHub task tracking |
| Measurement reference | [REF-MET-001](reference/REF-MET-001-measurement-methodology.md) | Timing and physical geometry definitions |
| USB/protocol reference | [REF-WIRE-001](reference/REF-WIRE-001-usb-and-control-contract.md) | Versioned wire messages and transport |
| Session reference | [REF-DATA-001](reference/REF-DATA-001-session-contract.md) | Raw evidence and exported schema |
| Fixture specification | [SPEC-X-001](specs/SPEC-X-001-fixture-and-mechanics.md) | Electronics, firmware, calibration, and mechanics |
| Android specification | [SPEC-X-002](specs/SPEC-X-002-android-acquisition.md) | Acquisition, rendering, UX, and state evidence |
| Analyzer specification | [SPEC-X-003](specs/SPEC-X-003-reference-analysis.md) | Validation, statistics, and reporting |
| Qualification specification | [SPEC-X-004](specs/SPEC-X-004-qualification.md) | Gates, physical proof, and publication claims |
| Implementation phases | [PHASE-000](phases/PHASE-000-feasibility-preparation.md), [PHASE-001](phases/PHASE-001-hardware-android-feasibility.md), [PHASE-002](phases/PHASE-002-production-instrument.md), [PHASE-003](phases/PHASE-003-acquisition-analysis.md), [PHASE-004](phases/PHASE-004-publication-qualification.md) | Capability milestones, not tracking checklists |
| Domain vocabulary | [GLOSSARY.md](../GLOSSARY.md) | Canonical measurement language |
| Architecture rationale | [ADR-0001: Panel-fixed measurement coordinates](adr/0001-panel-fixed-measurement-coordinates.md), [ADR-0002: Prove hardware timing before contract freeze](adr/0002-prove-hardware-timing-before-contract-freeze.md), [ADR-0003: Uncertainty-aware rotation inference](adr/0003-uncertainty-aware-rotation-inference.md) | Decisions, rejected alternatives, consequences |
| Issue workflow | [Issue tracker guidance](agents/issue-tracker.md) | Runtime repository checks, implementation slices, dependencies, and status |

## Historical design and implementation plans

The two August 31 design documents and five checkbox implementation plans formerly under `docs/superpowers/` were **deleted from the active repository tree** without deprecation placeholders. Their original contents remain available in Git history at the pre-migration commit `d0e10a95a59d802f366c21f0e68c98d22c788338` and its ancestors. The canonical documents above are the active technical authority; GitHub Issues is the sole operational task tracker for the runtime-resolved repository.

The approved methodology retains its authoritative meaning. Detailed implementation choices that have not been proven remain explicitly marked **provisional** until verified. Physical feasibility proof does not establish publication-grade qualification.

## Current scope

Phase 0 specifies the system and feasibility gate. Phase 1 proves the reusable Teensy, Android, and optical path. Phases 2 through 4 complete the instrument, analytical pipeline, and publication-grade evidence. GitHub issues independently track progress; completing a phase requires evidence meeting its defined exit criteria.
