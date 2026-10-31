# PHASE-001 — Hardware and Android Feasibility Proof

**Document ID:** PHASE-001  
**Status:** Draft  
**Readiness note:** Method and scope approved; physical execution remains blocked by GitHub Phase 0 dependencies and prototype equipment availability  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
**Depends On:** PHASE-000, ARCH-001, REF-MET-001  
**Supersedes:** Early integration portions of the firmware/Android checkbox plans  
**Superseded By:** None

## Observable outcome

A real Teensy USB HID press completes on an Android handheld, a small reusable native Vulkan test app displays the resulting black/white transition, and one actual optical sensor produces a hardware-side crossing correlated to that measured press. Five hardware capture inputs work and real USB bus measurements establish timing feasibility before production board orders.

## Required technical contracts

SPEC-X-001 and SPEC-X-002 define prototype boundaries; REF-WIRE-001 and REF-DATA-001 are candidate interfaces under test. REF-MET-001 governs timing meaning and coordinate identity.

## In scope

- Low-cost prototype firmware and reusable Android entry point, normal gamepad + separately claimable vendor USB interface, deterministic Full-Speed operation verified on hardware.
- One compact soldered or PCB optical prototype (no solderless breadboard high-impedance TIA); low-risk digital breadboard and owner soldering allowed.
- Actual five-channel input capture/clock routing test ≥10,000 common edges with no loss; external `T0` correlation against USB transaction completion; explicit jitter/uncertainty characterization.
- Real brightness/threshold amplitude checks sufficient to justify a production analog design.
- Failure and fallback evidence when an assumption cannot be proven, before locking production protocol/PCB layout.

## Exclusions

Full 1,000-trial qualification, 200-nit publication proof, completed five-PCB overlay, completed Rust reporting and 0.1 ms final end-to-end uncertainty qualification.

## Exit criteria

Measured proof of all five hardware conditions in SPEC-X-004 Gate 1, with raw timestamps/oscilloscope settings and failing cases preserved. A single optical transition is a feasibility demonstration only. Production interface freeze and PCB manufacture remain blocked until evidence supports them.
