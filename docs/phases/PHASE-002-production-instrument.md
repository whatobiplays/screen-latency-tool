# PHASE-002 — Full Five-Sensor Instrument and Stable Contracts

**Document ID:** PHASE-002  
**Status:** Draft
**Readiness note:** Depends on PHASE-001 physical proof  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
**Depends On:** PHASE-001, ARCH-001, REF-MET-001  
**Supersedes:** Production hardware portions of legacy fixture plan  
**Superseded By:** None

## Observable outcome

The fully assembled self-powered five-channel sensor/Teensy instrument registers on handheld displays across the targeted 3–9-inch range, can electrically and optically self-test, accept calibrated 10/50/90 thresholds, and retain 1,000 autonomous raw trials for post-run download under a stable versioned binary contract.

## Required specifications

SPEC-X-001 and SPEC-X-004; final REF-WIRE-001 and REF-DATA-001 revisions, explicitly locked only after PHASE-001 proof.

## In scope

- Lock versioned HID, control and raw batch contracts and golden vectors.
- Measure one front-end's response/saturation/PWM behavior and lock analog population values, then manufacture professionally assembled carrier/sensor boards with complete KiCad, Gerber, drill, CPL, BOM and assembly deliverables.
- Validate five hardware capture paths, self-tests, programmable DACs, analog ADC monitors, brownout/anti-backfeed and batch fault handling on real assembled hardware.
- Print and verify parametric adjustable overlay, cable/optical boots, alignment and mechanical registration on representative devices.

## Exclusions

Do not claim final publication-grade qualification merely because full electronics pass functional checks. The analyzer and complete Android workflow are independently completed later.

## Exit criteria

All five sensors observe the same physical transition; qualified hardware design/build artifacts and revision identifiers exist; firmware produces a complete 1,000-trial raw stream matching Rust golden vectors with no control traffic during acquisition; physical and host validation prove correct error handling. Complete uncertainty/reproducibility are separately accepted in PHASE-004.
