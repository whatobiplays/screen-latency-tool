# PHASE-000 — Specification and Feasibility Preparation

**Document ID:** PHASE-000  
**Status:** Ready for Implementation  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
**Depends On:** ARCH-001, ARCH-002, REF-MET-001  
**Supersedes:** Preimplementation planning in the August 31 plans  
**Superseded By:** None

## Outcome

There is one consistent technical source of truth, zero active local task checklists, and an independently testable hardware feasibility contract. GitHub Issues owns every actionable work item and blocker. The system architecture and measurement meaning are agreed, while unverified USB implementation details are clearly provisional.

## Scope and deliverables

- Consolidated ARCH/REF/SPEC vocabulary, constraints, rationale and metadata; reconcile conflicting event field names, trial schedule versus actual elapsed interval, and physical-panel versus logical orientation.
- A bounded Phase 1 hardware proof: reusable real Teensy + Android native renderer + single optical transition, five independently proven hardware captures and externally measured `T0`.
- Cross-language candidate fixture/session contracts and explicit freeze criteria, not premature final byte-compatibility guarantees.
- A measurable hardware prototype bill of materials and purchase sequence under $1,100 target / ~$1,300 flexibility.
- GitHub implementation issues with real blocking relationships and evidence criteria; old checklist files deleted from the active tree, with original content preserved in Git history.

## Exclusions

No manufacturing purchase, hardware qualification, full Android app, completed five-sensor fixture, synthetic publication-grade results or changes to unrelated projects.

## Exit criteria

Every issue links to its canonical spec and has explicit blockers; the feasibility proof has acceptance criteria for real hardware/Android/USB/optical evidence. No active project-management checklist remains in repository Markdown. Remaining unknowns are identified as characterization questions rather than silently filled in. Progress is tracked in GitHub, not this phase document.
