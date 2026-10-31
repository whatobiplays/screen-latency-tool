# ARCH-002 — Documentation Governance and GitHub Work Tracking

**Document ID:** ARCH-002\
**Status:** Ready for Implementation\
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-30\
**Depends On:** ARCH-001\
**Supersedes:** The task-tracking role of the five August 31 checklist plans\
**Superseded By:** None

## Principle

**The Git repository owns durable technical truth. GitHub Issues owns actionable work for the repository resolved at runtime from the active Git checkout.** The repository must not maintain parallel roadmap checklists, kanban status, issue copies, or completed/not-completed trackers in Markdown.

## Canonical document hierarchy

```text
Normative requirements:
  Architecture (ARCH-NNN)
    -> Reference contracts (REF-AREA-NNN)
    -> Subsystem specifications (SPEC-X-NNN)

Capability phases (PHASE-NNN) describe outcomes, scope, and exit criteria
constrained by the applicable architecture, reference, and specification
requirements.

ADRs record cross-cutting decisions, rationale, and rejected alternatives;
they are decision records, not a peer tier of normative requirements.

GitHub implementation issues define narrow, actionable work slices.
```

Architecture defines global boundaries. Reference documents define shared vocabulary and binary contracts. Subsystem specifications define requirements and tests. Phases describe stable outcomes, scope, and exit criteria but cannot override architecture, reference, or subsystem specification requirements. ADRs preserve cross-cutting decisions, rationale, and rejected alternatives without becoming normative requirements. GitHub issue bodies define one change, observable acceptance criteria, required evidence, and actual blocking issues.

Dependencies point from downstream work to prerequisites only and must be acyclic. Governed document IDs remain stable when filenames change. The glossary uses a stable `GLOSSARY-NNN` ID, the governed-document status vocabulary, and `GLOSSARY-001` as the canonical entry. Each ADR has a stable `ADR-` plus four-digit numeric prefix in its filename; for example, `0001-...md` has ID `ADR-0001`, and cross-reference resolution uses the normalized ID. Revised specifications must supersede earlier requirements explicitly; a downstream task must never silently override a higher-level requirement.

## Approval and state

Governed durable documents carry these metadata fields: Document ID, Status, Owner, Last Updated, Depends On, Supersedes, and Superseded By. The allowed statuses are Draft, Ready for Implementation, In Progress, Complete, and Deprecated. A document that is Ready for Implementation has agreed scope, measurable criteria, and no unresolved blocker that prevents its intended slice. Completion refers to evidence, not drafting status.

ADRs are cross-cutting decision records with stable `ADR-NNNN` IDs in their filenames. They use `Proposed` or `Accepted (YYYY-MM-DD)` status as applicable and are exempt from the seven-field metadata contract above. Filename IDs resolve references to decision rationale. This ADR-specific convention neither makes ADRs normative requirements nor permits them to override architecture, reference, or subsystem specification requirements.

The glossary identifies itself with a stable `GLOSSARY-NNN` document ID and an allowed governed-document status. It is exempt from the seven-field metadata contract because it defines shared terminology rather than prerequisite-dependent requirements. Required glossary metadata is limited to its ID and status unless a future governing decision explicitly changes this convention.

An accepted architectural decision may coexist with uncharacterized electrical parameters. Those parameters remain explicitly provisional in the relevant specification rather than being guessed.

## GitHub issue conventions

- Use GitHub Issues for the repository resolved at runtime from the active Git checkout as the only task tracker.
- Prefer an authenticated connected GitHub integration for issue reads and writes. Use authenticated `gh` as the fallback.
- Before any issue write, verify repository discovery from the active checkout, authentication to the resolved host, and access to the resolved repository.
- Each actionable implementation slice is an issue containing **What to build**, independently observable acceptance criteria, **Blocked by** issue references, exclusions, and required physical versus automated evidence.
- Dependencies represent actual blockers, not thematic ordering. Publish blockers before dependents. Prefer native GitHub blocked-by relationships where tooling supports them; otherwise, use explicit issue references in issue bodies.
- Use the installed triage vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix`. Physical assembly or bench measurements may require `ready-for-human`.
- An issue is ready only when its prerequisite specification and tool or equipment conditions are met. Work may be described and tracked before it is unblocked.
- Update implementation status and decisions on the issue. Amend the canonical specification or ADR when a lasting architectural decision changes. Issue comments are not substitutes for durable architecture or verified raw test evidence.
- Close an issue only after its acceptance criteria and dependency conditions are evidenced **and the owner explicitly authorizes closure for that specific issue**. A merged pull request, passing checks, completed implementation, or satisfied acceptance criteria does not itself authorize closure. Preserve links to source specifications, commits or pull requests, and qualification output.
- Unless the owner explicitly authorizes closure, do not use auto-closing keywords such as `Closes`, `Fixes`, or `Resolves` with an issue number. Non-closing references such as `Refs #N` are permitted.
- Do not create independent local issue files, unchecked plan steps, weekly roadmap tables, or task-status duplication in documentation. GitHub Issues is the operational queue.

## Existing documentation migration

The August 31 `docs/superpowers/specs/` design and closure amendment, together with all five `docs/superpowers/plans/` task checklists, are superseded **and removed from the active repository tree**. The canonical hierarchy (ARCH-001, REF-MET-001, REF-WIRE-001, REF-DATA-001, SPEC-X-001 through SPEC-X-004, and PHASE-000 through PHASE-004) retains active technical requirements and rationale. Git history retains the original research, decision context, and rejected alternatives. No redirect or deprecation-stub files are maintained.

Future changes to canonical documents must preserve decisions and rationale, rejected alternatives, known failure modes, invariants, and intended verification. A justified decision must never be reduced to an unexplained selection.

## Documentation and issue quality gates

- Specifications contain normative requirements and failure behavior, not milestone progress.
- Interface declarations, versions, units, and terminology remain consistent across Android, Teensy, Rust, and fixtures.
- Provisional details and characterization gates remain explicit and are never presented as already qualified.
- Cross-references and precedence rules remain valid and acyclic.
- New GitHub issues refer to current canonical documents and have unambiguous blockers.
- Automated test evidence and physical-hardware evidence remain clearly distinguished.
