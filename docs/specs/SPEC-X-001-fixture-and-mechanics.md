# SPEC-X-001 — Fixture Electronics, Firmware and Mechanics

**Document ID:** SPEC-X-001  
**Status:** Ready for Implementation
**Readiness note:** Feasibility and prototype implementation are approved; final analog values, PCB design and USB/capture routing require physical characterization before production freeze  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
**Depends On:** ARCH-001, REF-MET-001, REF-WIRE-001, REF-DATA-001  
**Supersedes:** The fixture hardware/firmware/mechanics requirements in the legacy spec and checkbox plan  
**Superseded By:** None

## Product boundary

A replaceable, socketed Teensy 4.1 on a self-powered carrier drives a normal USB HID gamepad to Android and a vendor-specific control/results channel. It timestamps each **successfully completed** measured input transfer and five simultaneous display optical edges on one monotonic hardware timebase. It is the measurement authority. Android USB commands must not affect acquisition timing.

Nominal modules: five movable identical sensor PCBs with SFH 203 P reverse-biased photodiode (~2–3 V provisional), OPA320 TIA, TLV3501 comparator with small hysteresis, programmable threshold reference, analog monitor, digital capture output, power/local decoupling, opaque contact boot. Parts and TIA `Rf/Cf`, diode bias, hysteresis and optical material are **provisional** until measured. Sensor front-end stays at the detector; only digital edges traverse adjustable cabling to the carrier. A multichannel DAC (e.g. DAC7578 or demonstrated equivalent) programs five references independently; slow ADC reads five analog monitors for calibration/drift.

## Electrical/timing architecture

- External regulated 5 V input, locally regulated analog/digital rails, power health/brownout supervision, Android VBUS sense and enforced no 5 V backfeed; USB ground is shared (no isolation requirement v1).
- Five actual timer capture registers/peripheral paths must share a justified common monotonic tick domain; if Teensy routing cannot satisfy that, introduce dedicated external capture hardware. Interrupt-entry timestamping is not acceptable for publication.
- `T0` associates the exact measured button-1 HID IN transfer completion with a hardware timer read or capture path characterized against external USB bus evidence. Track queue time only as non-authoritative diagnostic.
- Resolution ≤10 µs, calibrated five-channel skew ≤25 µs, total quantified uncertainty ≤0.1 ms. Test over 10,000 common edges, cold power cycles, and LED optical stimulus; preserve both uncorrected captures and separately stored correction coefficients.
- Comparator captures arm only after successful `T0`; hysteresis and repeated-edge counting flag PWM/noise; multiple/early/timeout/saturation are not silently converted into valid T50.
- Run an electrical common-edge self-test before each publishable session; run full optical self-test periodically and after relevant changes. Failure blocks qualification.
- Black/white per-sensor ADC calibration yields independent 10/50/90% thresholds; sensor calibration is bound to real optical conditions, not necessarily each application software mode change.

## Autonomous trial engine

State progression: boot → idle → self-test/calibration → configured → armed → acquiring → complete or faulted. Invalid arm/configuration/unknown protocol version is rejected. Firmware generates randomized measured button press, latches `T0` on success, captures five edges, disarms, releases/resets without generating another measured record, verifies baseline, then schedules the next trial. Firmware acquires 1,000 records without dynamic allocation or vendor traffic and retains all raw records until download; no buffer truncation permitted.

Preserve seed, trial/sequence ID, scheduled and actual inter-trial delay, all capture ticks, bounded repeated-edge information, failure flags, timebase, config and the calibration revision. A lost/duplicate HID report, missing baseline, power fault or discontinuity fails the appropriate trial/batch. Release/reset traffic never advances `hid_sequence` and cannot set `T0`.

Device enumerates exactly two USB interfaces, HID for Android OS and a separately claimable vendor bulk pair for the test app, with Full-Speed forcing/verification. Detailed candidate descriptors and command framing live in REF-WIRE-001 and remain provisional until hardware feasibility evidence.

## Development and manufacturing strategy

**Prototype before production.** The first milestone must show Teensy measured HID press → actual Android native rendering → physical full-screen transition → one optical sensor crossing correlated with measured completion. Separately prove five common-timebase captures and verify USB completion relative to on-bus transactions. Build a minimal reusable Android path; do not create a throwaway fake app that obscures integration.

Solderless breadboards, headers, jumper wires, and owner-soldered breakout/prototyping materials are acceptable for low-speed GPIO/control, LED pulses, supply, and functional digital capture. USB D+/D- stay on appropriate cable/breakout/probe hardware, not long breadboard stubs. Keep the photodiode-to-TIA high-impedance junction short on a properly decoupled compact breakout/evaluation/prototype PCB, not a solderless breadboard. A functional demonstration is not evidence for final optical timing uncertainty.

Purchasing: Teensy and inexpensive prototype parts first; procure Rigol DHO814 or qualifying alternative when bus-level `T0` characterization becomes blocking; commission professionally fabricated/assembled final sensor/carrier boards only after the feasibility gate. Owner may solder prototype connectors, parts, wires or small SMT boards; final production PCB assembly is professional. Target total external purchases $1,100 with discretion to ~$1,300, including scope, electronics, PCB and mechanics; exclude the already-owned Display Pro HL and printer from this budget. A costed BOM must distinguish estimates, tax/shipping and actuals. Do not weaken precision to meet a spending number.

Failure of Full-Speed forcing or `T0` bounds triggers the external analyzer borrow/rent fallback or hardware redesign, not a downgrade. Electrical routing and optical response must be measured before finalizing PCB pin maps or analog values.

## Physical fixture

A parametric, adjustable, bezel-referenced overlay supports ~3–9-inch diagonal displays. Active screen envelope: 55–205 mm width, 35–135 mm height; chassis envelope: 100–350 mm width, 60–190 mm height, 8–55 mm thickness. These are design targets, not guarantees for every grip/vent/button geometry. Supports under the DUT prevent glass-loading; stops align to repeatable chassis/bezel references rather than joysticks; five sensor carriages register at REF-MET-001 normalized physical-panel coordinates. Opaque boots lightly touch cover glass, with controlled compression and no significant display pressure.

Use parametric OpenSCAD sources and reproducible printable STL/3MF exports, dimensioned assembly, mechanical BOM, sensor/carrier cable pinout, length limits and optical boot specification. Alignment markers are an independent verification aid; do not treat Android logical coordinates as the sensor reference. Mechanical reproducibility is tested after three complete remove/reinstall cycles, with re-alignment/recalibration.

## Manufacturing deliverables and proof

Each qualified PCB revision has: KiCad source, schematic PDF, ERC/DRC, Gerbers, drill files, stack-up/fab notes, locked MPN BOM, CPL/position files, assembly drawing, cable/pinout, revision identifier and flashing instructions. Each analog value and replacement part is locked only from measured characterization. Simulate before first sensor PCB; final proof includes no saturation at 200 nits or relevant bright diagnostic condition, front-end step response comfortably <100 µs, PWM rejection/flags, <25 µs calibrated skew, optical self-test on all five, power fault behavior, and ≤0.1 ms complete fixture uncertainty.

Automated firmware host tests cover codec vectors, allocation/buffer, sequences, reset, calibration, edge flags and fault cases; Teensy firmware build reproducibly. Real USB/HID, scope, rail and optical results are manual physical evidence, not substitutes for CI.
