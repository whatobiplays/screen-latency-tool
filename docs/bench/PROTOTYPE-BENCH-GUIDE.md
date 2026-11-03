# Prototype bench assembly and smoke-test guide

**Document ID:** BENCH-002\
**Status:** Draft\
**Owner:** Repository maintainers\
**Last Updated:** 2026-11-02 (America/Los_Angeles)\
**Depends On:** ARCH-001, ARCH-002, REF-MET-001, REF-WIRE-001, SPEC-X-001, SPEC-X-004, PHASE-001, ADR-0002, BENCH-001\
**Supersedes:** None\
**Superseded By:** None

Readiness note: This is a proposed owner procedure; sourcing and electrical evidence remain unresolved. The Korad KA3005PE+ is `OWNER_SELECTED_MODEL / NOT_PURCHASED / NOT_FIRST_POWER_QUALIFIED`. This guide records no assembly, first energization, backfeed, HID, optical-current, comparator-discrimination, or T0 result. Issue #3 remains open. TAC-1, TAC-2, TAC-3, and TAC-5 remain evidence-bound; this guide records none as passed.

Use this guide with [PROTOTYPE-BENCH-BOM.md](PROTOTYPE-BENCH-BOM.md) and the normative fixture requirements in [SPEC-X-001](../specs/SPEC-X-001-fixture-and-mechanics.md). It describes a one-sensor feasibility bench that must preserve the later five-channel requirements. The bench cannot prove those requirements: independent captures on one monotonic hardware timebase, <=10 µs resolution, <=25 µs calibrated channel skew, and <=0.1 ms complete fixture uncertainty.

The first optical transition is a functional demonstration. It is not publication evidence and does not validate final USB descriptors, capture routing, analog bias, Rf, Cf, hysteresis, or thresholds.

### Prototype CAD prerequisite

Before G1, inspect the editable [one-sensor optical schematic](../../hardware/prototype/optical/one-sensor.kicad_sch), [external power/USB-sense schematic](../../hardware/prototype/power-usb/power-usb.kicad_sch), [native routed two-layer PCB](../../hardware/prototype/optical/one-sensor.kicad_pcb), [component manifest](../../hardware/prototype/BOM.csv), and [engineering notes](../../hardware/prototype/README.md). The schematic nominates **R3=47 kΩ, C2=2.2 pF C0G, an 18 kΩ/10 kΩ VREF divider, a 10 kΩ trimmer and a 3.3 V comparator**. `TLV3501` **SHDN pin 6 is grounded to enable**; `SN74LVC1G17` uses **pin 1 NC, pin 2 A, pin 3 GND, pin 4 Y, pin 5 VCC**. Confirm package footprints against TI and the active **SFH 203** manufacturer drawing. The drawing matches the detector footprint's 2.54 mm pitch, 5.90 mm maximum envelope, and cathode polarity. It gives no land-pattern or drill recommendation, so the 2.0 mm lands and 1.0 mm drills are project-designed.

KiCad 10.0.8 ERC reports on 2026-10-31 returned **0 errors and 0 warnings** for both schematics, and `erc-report-gate.mjs` accepted the complete Errors+Warnings exports. Four ERC checks remain ignored: single-use global labels, four-way junctions, SPICE-model issues, and footprint-filter mismatches. Native PCB DRC with `--schematic-parity` reported **0 violations, 0 unconnected items, 0 schematic-parity issues, and 0 footprint errors**. Five checks remained ignored: “Footprint has no courtyard defined,” “Track endpoint not centered on via,” tuning-profile track geometry, footprint-filter mismatch, and footprint-type mismatch. D1 has a defined 6.40 mm courtyard. The report summary does not identify any footprint references associated with the no-courtyard check. The five ignored checks were not enforced, and the report does not establish that every DRC rule was enabled or passed. DRC does not establish component fit or physical safety.

The EMC review identifies layout risks that ERC and DRC can miss. The implemented `/EDGE` route moves the former bottom-layer span to `F.Cu`, removes its two signal-via transitions, and preserves a continuous `B.Cu` ground plane beneath the route. Revised-board analysis validates this return-path correction. Residual cable-emissions risk remains: `J1` has one ground pin for three slow control signals and no ESD or ferrite protection. The connector is intended for a short bench harness; cable emissions and ESD behavior remain physically unqualified. The plane slots under `/VREF` and `/THRESH` are real topological features with low emissions risk for these DC prototype controls. Noise pickup and comparator jitter have not been measured. The `/SUM` plane void is intentional for the photodiode and TIA summing loop and must remain. The 1.43 to 2.86 GHz board-cavity estimates remain informational because no PDN measurement supports a different conclusion.

The revised-board validation included the implemented `/EDGE` route. Any later route change requires a zone refill, native DRC with schematic parity, generator and structural checks, PCB cross-analysis, and a new EMC analysis. Rerun ERC and Gate A only if the schematic or analog assumptions change.

The Gate A ngspice run passed **12/12 assertions** for the supplied TI models and recorded **35 deck invocations**. It measured 47.000 kΩ nominal transimpedance, 1.905 MHz bandwidth, 14.335% overshoot, 0.801 µs 2% settling, 1.399 µs recovery from a 40 µA overload, and 5.829 mV RMS modeled output noise through 2 MHz. Sweeps across 20–25 pF input capacitance and 2.1–2.3 pF feedback capacitance produced 12.8–19.0% overshoot and 0.61–0.94 µs settling. This is model-based **PASS WITH LIMITATIONS**.

The reviewed invocation requires `--simulator-provenance`. Its provenance JSON is specific to that run and was stored at an absolute path under `/tmp`, outside Git. It is not a portable repository file. A command without this flag is incomplete. The exact path is recorded with the preserved run evidence; the repository has no default provenance file. The summary binds the model and output hashes and all deck invocations. Do not rerun Gate A just to replace the run-specific path with a portable one.

```sh
node hardware/prototype/simulation/gate-a.mjs \
  --ngspice <ngspice-binary> \
  --opa-model <external-OPAx320-Q1.LIB> \
  --tlv-model <external-tlv3501.lib> \
  --simulator-provenance <run-specific-external-provenance.json> \
  --out-dir <external-output-directory>
```

The placeholders identify inputs that must come from the preserved evidence. Raw models, provenance, decks, logs, and outputs remain outside Git.

The 424 MΩ reverse resistance is an assumption, not an SFH 203 specification limit. Actual photocurrent under the display, optical signal-to-noise margin, physical stability, installed package orientation, and comparator discrimination remain unverified. The 5.829 mV RMS value describes modeled output noise only. The OPAx320-Q1 model covers the generic six-pin family; it does not validate the five-pin OPA320 package. The TLV3501 PSpice model was tested only with `ngbehavior=ps`.

The routed silkscreen labels the four header pins `3V3`, `GND`, `EDGE`, and `MON`, and the test pads `TIA`, `THR`, `REF`, `GND`, and `3V3`. D1 is at `(105.5, 110.5)` with zero rotation. Viewed from the component side, anode pad 1, `/SUM`, is left at `x = 105.5`; cathode pad 2, `/3V3`, is right at `x = 108.04`. The schematic, footprint, and PCB agree on polarity. The optical axis is perpendicular to the board and passes through approximately `(106.77, 110.5)`, away from the front-copper face. The revised footprint and PCB use a 5.90 mm maximum flange outline and a 6.40 mm courtyard. These dimensions replace the earlier undersized design envelope, but do not establish installed fit. The board has no mounting holes, and the SFH 203 has no 3D body. Optical-axis clearance, retention, display contact, and actual fit remain physically unverified. Do not send this draft board to fabrication. The result and its scope are recorded in [Issue #3](https://github.com/whatobiplays/screen-latency-tool/issues/3); raw `.rpt` exports are **not kept in the repository**.

Run `node hardware/prototype/generate.mjs --check` to detect generator/source drift and `node hardware/prototype/check.mjs` to run the structural checks on a local checkout. After external edits, reopen each `.kicad_pro` project in KiCad's Project Manager. In each schematic, rerun **Inspect → Electrical Rules Checker** and export both Errors and Warnings. Then run `node hardware/prototype/erc-report-gate.mjs <optical.rpt> <power-usb.rpt>` to reject incomplete reports or nonzero ERC results. Keep raw reports outside the Git checkout and record their results in Issue #3. Do not suppress findings or treat `PWR_FLAG` as a physical power source. ERC and DRC do not waive physical safety checks. Power, USB device-role, and one-sensor analog/calibration tests remain subject to the inspections below.

## Fixed boundaries

1. Use a Teensy 4.1. Keep every GPIO-connected signal within 3.3 V logic levels; never drive a GPIO from 5 V.
2. Power the fixture from a regulated external 5 V source. Android supplies USB data and VBUS presence and must not power the fixture through a backfeed path.
3. Share signal ground among the external supply, Teensy, USB path, and analog board. The prototype requires no galvanic isolation.
4. Low-speed digital, control, LED, and functional experiments may use a solderless breadboard. Keep USB D+/D- and the photodiode-to-TIA high-impedance junction on compact hardware with local decoupling.
5. Claim a completed HID IN correlation only after a nonintrusive protocol trace and a fixture timestamp or marker share a documented time reference.
6. Photos, continuity checks, and a proposed smoke test do not establish physical assembly or qualification. Preserve raw observations and distinguish failed gates from unperformed tests.

## Procurement eligibility boundary

Use the exact-MPN policy in [SPEC-X-001](../specs/SPEC-X-001-fixture-and-mechanics.md) and the row-by-row Stage-A assessment in [BENCH-003](PROTOTYPE-PROCUREMENT-READINESS.md) for PRE_PURCHASE_REVIEW. The owner decision for Issue #3 permits review eligibility for qualifying low-risk commodity parts in this one-sensor feasibility scope ([owner decision](https://github.com/whatobiplays/screen-latency-tool/issues/3#issuecomment-6567929455)); the independent review requires each purchasable MPN to be assessed individually ([review finding](https://github.com/whatobiplays/screen-latency-tool/issues/3#issuecomment-6570169190)). Known obsolete, discontinued, EOL, NRND, and last-time-buy parts remain excluded regardless of stock. A Stage-A REVIEW disposition permits owner purchase review only. It does not authorize an order, substitution, fabrication, or first power.

A physical check that requires a received or assembled part belongs to Stage B or C. Stage A may be held for a concrete design-stage safety, compatibility, lifecycle, sourcing, or risk gap listed for that exact MPN in BENCH-003. Do not use a pending Stage-B or Stage-C test as a Stage-A stop by itself.

## Three sequential readiness stages

1. **PRE_PURCHASE_REVIEW (Stage A):** The owner reviews exact-MPN eligibility and the stated one-sensor use before any order. REVIEW means eligible for owner review, never auto-approved. A separate purchase authorization is required before ordering.
2. **ASSEMBLY_FIRST_POWER_RELEASE (Stage B):** After separate purchase authorization and receipt, verify exact parts, assemble, and complete the reviewed safety checks. First power requires a rated, adjustable, current-limited bench source and every applicable power and signal check. The Korad KA3005PE+ remains OWNER_SELECTED_MODEL / NOT_PURCHASED / NOT_FIRST_POWER_QUALIFIED. Stage B has not cleared any part.
3. **MEASUREMENT_PRODUCTION_QUALIFICATION (Stage C):** Characterization begins only after Stage B release. One-sensor measurements establish feasibility only. Production qualification remains a separate, stricter gate for the five-channel fixture; one-sensor exceptions do not satisfy it.

## Readiness gates (in order)

These gates describe future owner work, not completed results. G1 is the pre-purchase review. G2 through G4 are Stage-B checks after any separately authorized order and receipt. Do not require part identity markings, installed polarity, assembly, continuity, or physical measurements to pass G1 when those checks require receipt or assembly.

| Gate | Stage | Required evidence before advancing | Stop condition |
| --- | --- | --- | --- |
| G1: owner purchase review | Stage A | Review the exact MPN, stated one-sensor use, lifecycle evidence, dated source confidence, design-stage compatibility, and risk disposition in BENCH-003. | Stop for a Stage-A HOLD or an unresolved concrete pre-purchase safety, compatibility, lifecycle, sourcing, or risk gap. Pending Stage-B or Stage-C tests alone do not stop G1. |
| G2: verify received parts and unpowered assembly | Stage B | Match received MPNs and quantities to the BOM and reviewed Stage-A outcome. Verify package, ratings, orientation, pinout, footprint fit, and wiring before power. | Stop for a mismatch, damage, known excluded lifecycle, unsafe substitution, missing rating, uncertain polarity, pin map, fit, bypass, or return path. |
| G3: release first power | Stage B | Complete unpowered VIN/VUSB separation and DMM checks, polarity and protection review, load budget, signal-voltage review, and other applicable safety checks. Use a rated, adjustable, current-limited bench source for first power. | Do not energize if a required check is open, the source cannot provide an adjustable current limit, a rail or signal is out of range, or backfeed, a short, excess current, or heating is found. |
| G4: release the Android connection | Stage B | After G2 and G3 pass, verify external power stability, VIN/VUSB isolation, VBUS sensing, GPIO limits, Android host role, cable path, and enumeration on the actual target. | If VUSB rises from external power, a powered-off pin backfeeds, the host role fails, a rail misbehaves, or GPIO safety is unresolved, disconnect and reject the configuration. |
| Stage C characterization | Stage C | After Stage-B release, perform the per-part measurements listed in BENCH-003. Keep one-sensor feasibility evidence separate from five-channel production qualification. | Stop the affected characterization for an unsafe or out-of-range result, missing raw evidence, or a claim that exceeds the measured one-sensor scope. |

## One-channel assembly and smoke-test sequence

This is a proposed owner procedure based on the bench BOM and BOM.csv, not a test result. Stage A review eligibility is not purchase authorization. After any separate purchase authorization, Stage B begins with received-part verification. No parts were purchased, no board was fabricated or soldered, and no circuit was energized for this documentation update. Resolve the applicable sourcing, design, tool-inventory, and safety blockers before physical work.

| Step | Parts and setup | Expected observations to record | Stop condition |
| --- | --- | --- | --- |
| 1. Verify received parts (Stage B) | Match each received MPN, package, quantity, and Stage-A owner-review outcome to BOM.csv and BENCH-003. | Confirm identity; record markings, pin-1 location, and polarity. | Stop for a known excluded lifecycle, a Stage-A HOLD, mismatch, damage, or unapproved substitution. Unknown lifecycle is acceptable only for a low-risk commodity part covered by the owner-approved one-sensor exception. |
| 2. Fabricate and inspect | Obtain owner approval and a fabrication/assembly plan for the routed 50 x 44 mm optical PCB. The power/USB schematic describes off-board connectivity. | Record the board revision. Inspect pads, copper, labels, edges, orientation, and optical clearance. | Stop while fabrication and interconnect remain unquoted. A breadboard or SOT adapter is not the optical front end. |
| 3. Assemble unpowered | Populate the board according to BOM.csv. Connect SFH 203 pad 1 (anode) to SUM and pad 2 (cathode) to 3V3. Use OPA320 DBV-5, TLV3501 DBV-6, the listed R/C values, trimmer, and header. Leave R4 DNP. | Photograph both sides. Record the board revision, diode cathode mark, IC pin-1 marks, fitted values, and DNP state. | Stop if the pin map, polarity, footprint, bypass, or return path is uncertain. |
| 4. Check wiring | Disconnect USB and all supplies, allow the circuit to discharge, and use a DMM in voltage mode to confirm zero volts on accessible rails. Only then use resistance or continuity mode to check the verified Teensy VIN/VUSB cut and power rails. | Record the meter range and confirm a stable open/OL reading across the cut. Check for rail-to-ground shorts. | Stop for a low or unstable reading, uncertain cut pads, a short, or an ambiguous result. OL only shows resistance above the meter's range. |
| 5. External power only (Stage-B first-power release) | Before energizing, review the expected connected load and component limits and document a defensible initial current-limit method. Use a rated, adjustable current-limited bench source and set its limit from that review. Measure actual startup and load behavior during Stage B. Keep USB and Android disconnected; do not start with the fixed 3 A adapter. | Record polarity, input voltage/current, 3.3 V rails, VREF (nominal calculation 1.1786 V at 3.3 V), and VUSB with USB absent. | Stop for excess current, an out-of-limit rail, VUSB backfeed, heating, odor, oscillation, or an unexplained rail change. Do not choose a current limit before reviewing the load. |
| 6. VBUS sense | After verifying VIN/VUSB isolation, test the 3.3 V-powered SN74LVC1G17DBVRG4 with its output isolated from Teensy GPIO, R1 = 1 kOhm, R3 = 47 kOhm, and 100 nF bypass. | At nominal 5 V VBUS, expect about 4.896 V at the buffer input and 0.104 mA divider current. Measure the buffer input and output, confirm the input stays low with VBUS disconnected, and check for backfeed while power is off. | Stop if device or Teensy GPIO limits are exceeded, the disconnected input is not low, or a rail backfeeds. |
| 7. Slow LED | Use WP7113ID and the listed 1 kOhm R2 after reviewing the polarity and GPIO drive capability. | Record the GPIO state, polarity, measured current, and rail change. First-order current is about 1.4 mA at 3.3 V with a typical 1.9 V forward drop; measure the actual part. | Stop if current limiting is absent, polarity is reversed, current is unexpected, or GPIO voltage exceeds its rating. |
| 8. Optical channel (Stage C characterization) | After Stage-B first-power release, use a controlled display stimulus. Record luminance, distance, ambient light, dark/bright TIA output, VREF, threshold, comparator output, noise, and temperature. | Look for a repeatable response without saturation, oscillation, or unexplained multiple edges. Measure the actual photocurrent; its value at 200 cd/m2 is unknown. | Stop for saturation, oscillation, an unstable threshold, unclassified PWM/noise edges, or unknown optical geometry. Retain failed traces. |
| 9. Android USB enumeration (Stage B) | After the power and GPIO gates pass, connect the 5030 adapter and 592 cable between the actual Android host and the Teensy Micro-B device port. Never use the Teensy host header. | Record the Android model and OS, USB speed, two-interface descriptor, HID behavior, vendor-interface identity, disconnect behavior, and failures. | Stop for a host-role mismatch, unsafe VBUS/VIN behavior, USB resets, an unsafe GPIO level, or an unknown interface. USB completion timing and correlation remain Stage C evidence. |

### What can be tested without dual power?

On an unmodified Teensy, a **USB-only** digital smoke test can support development if external VIN and every other supply path are disconnected. This is not the self-powered target fixture. After cutting the VIN/VUSB trace, USB-only power normally **cannot** run the Teensy. Meaningful USB HID enumeration after that modification requires independently powered VIN and completion of the G2-G4 checks. The feasibility milestone therefore requires verified external power and USB to operate together after those checks pass.

## Power topology and safe staged commissioning

### Candidate circuit boundaries

Select one regulated supply: Mean Well GST18U05-P1J (5 V/3 A) or Phihong PSC15A-050-R. Both are **fixed-output wall adapters with no owner-adjustable current limit**. The protection chain below is a proposal, not an approved schematic. The owner must validate component footprints, wiring, load and thermal effects, and fault behavior at the bench. See [BENCH-001](PROTOTYPE-BENCH-BOM.md) for part prices and stock confidence.

The owner-selected adjustable supply for first-power planning is the Korad KA3005PE+: `OWNER_SELECTED_MODEL / NOT_PURCHASED / NOT_FIRST_POWER_QUALIFIED`. A rated, adjustable, current-limited bench supply is required for first power; the selected unit has not been purchased or qualified for that use. The DigiKey $134.24 figure is a dated planning-price observation, not live stock or a delivered quote. The KA3005D and Siglent SPD3303X-E remain historical alternatives. The DHO814 remains deferred. Confirm the exact supply, leads, mains configuration, protective earth, isolation, and load behavior before first power.

~~~text
Externally regulated 5 V barrel (+) -> Adafruit 368 barrel-to-screw-terminal adapter
  -> Bourns MF-R050 PTC (0.5 A hold / 1 A trip; delayed thermal fault limiter)
  -> Pololu 5383 ideal-diode protector (VIN/GND input; VOUT/GND load)
  -> Teensy VIN and a separately justified local analog-rail regulator if required
Barrel return -> Pololu/Teensy/analog common signal ground (no USB shield-only return)

Android USB-C host -> Adafruit 5030 host adapter -> Adafruit 592 A-to-Micro-B cable
  -> Teensy 4.1 Micro-B USB DEVICE port (D+, D-, USB GND, VUSB)
VUSB and VIN physically SEPARATED at PJRC underside pads
VUSB -> [verified input resistor/filter if required] -> SN74LVC1G17 (input A)
SN74LVC1G17 powered from Teensy 3.3 V with local bypass, Ioff when unpowered
SN74LVC1G17 (output Y) -> verified compatible Teensy digital input

USB-C source is *not* a second fixture power source; its VUSB is observed, never tied to VIN.
~~~

**What each element actually protects:** The regulator in the Teensy generates 3.3 V from VIN. The Pololu 5383 protects the external adapter-to-VIN path from reverse polarity and reverse current but is **not** a USB VUSB isolator, short-circuit breaker, or overvoltage regulator. The MF-R050 is a slowly tripping resettable fuse, **not** a programmable current limiter or a guaranteed safeguard against brief fault spikes. The physical cut of VIN/VUSB is the principal no-backfeed separation. The VBUS buffer's datasheet-specified Ioff feature addresses a different failure mode: live 5 V USB input while the 3.3 V rail is off. Neither Ioff nor a resistor alone excuses checking actual powered-off behavior.

A 3.3 V analog front end is a plausible *one-channel* prototype supply, not proof the Teensy's onboard 3.3 V regulator can support a complete five-channel fixture. PJRC gives **250 mA maximum recommended external load** from its 3.3 V pin. Enumerate the sensor, indicator, buffer, and any other loads; use a separate regulated 3.3 V analog rail if measured consumption or noise warrants. Plan the analog and digital return layout deliberately, and characterize noise before laying out the final PCB.

PJRC's [power notes](https://www.pjrc.com/store/teensy41.html), [Teensy 4.1 schematic](https://www.pjrc.com/teensy/schematic.html), and [pinout warning](https://www.pjrc.com/teensy/card11b_rev4_web.pdf) are authoritative sources for the **actual** VIN/VUSB separation and 3.3 V I/O. Never use the older Teensy USB-host header for the Android-device connection.

### Electrical limits

The external power path runs from an Adafruit 368 barrel-to-screw-terminal adapter through a Bourns MF-R050 PTC and a Pololu 5383 ideal-diode protector to the Teensy VIN pin. The Pololu module accepts 4 to 60 V, is rated for 12 A with less than 5 mΩ path resistance, and blocks reverse current. It does not clamp overvoltage or provide an adjustable current limit. The MF-R050 has a 0.5 A hold and 1 A trip at 23 °C, a 0.41 A hold at 40 °C, and a 60 V maximum. It is a slow thermal fault limiter.

The Teensy uses 3.3 V logic, its digital inputs are not 5 V tolerant, and PJRC recommends at most 250 mA of external load from the 3.3 V pin. No 5 V signal may connect directly to a Teensy input. The SN74LVC1G17DBVRG4 is powered from 3.3 V, accepts inputs up to 5.5 V, and supports Ioff operation. With R1 = 1 kΩ and R3 = 47 kΩ, 5.0 V VBUS produces about 4.896 V at the buffer input and 5.25 V produces about 5.141 V, both below its 5.5 V recommended input limit. With VCC at 0 V, Ioff limits leakage to about ±10 µA. The 5.5 V tolerance belongs to the buffer; its output and every Teensy GPIO must remain within 3.3 V logic levels. The Teensy VIN/VUSB bridge must be cut before external VIN and USB VBUS are used together.

### Stage-B unpowered preparation

Before the first energization, record this unpowered evidence. Confirm zero volts on accessible rails before using resistance mode. Inspect the VIN/VUSB cut and both pads, barrel polarity, PTC series placement, Pololu VIN/VOUT/GND orientation, bypass capacitors, D1 cathode mark, and every IC pin-1 mark. Measure PTC continuity, confirm VIN-to-VUSB is open, check 5 V to ground and 3.3 V to ground, and verify VREF divider-node continuity. Confirm there is no 5 V path to the Teensy logic header. Record the DMM range. Finite semiconductor resistance is expected; identify hard shorts with the range recorded, and treat an OL screen as the limit of that meter range rather than proof of infinite isolation.

1. Disconnect the external adapter, USB cable, LED driver, optical board, and all other power sources. Allow the circuit to discharge, then use a DMM in **voltage** mode to confirm that no voltage remains on accessible nodes. Only then use resistance or continuity mode.
2. Inspect the board under magnification. If firmware-only USB testing is intended, do it **before** cutting the underside VIN/VUSB link and without applying external VIN. For the eventual self-powered fixture, cut only the PJRC-identified **VIN/VUSB bridging trace** and document its condition with before-and-after photos; do not modify D+/D-.
3. With the assembly still unpowered, measure continuity/resistance between the two opposite **metal pads of the actual VIN/VUSB cut** using the DMM's stated range. A stable open/OL result is the expected screening result. A stable low reading, ambiguous pads, any solder bridge, or inability to establish the separation means **stop**. OL only means resistance higher than that meter range, not infinite isolation or measured reverse-current performance.
4. Inspect the Adafruit 368 barrel polarity and the Pololu 5383 VIN, VOUT, and GND orientation **against their manufacturer diagrams**. Verify that the PTC is in series with the positive input and is not bypassed, and that the external supply return connects to signal ground. An isolation test on a **populated circuit** can show capacitive or semiconductor paths; do not classify every finite resistance reading as a short without understanding the circuit. Never use the USB cable shield as the only return.
5. Prepare a **genuinely current-limited bench supply or verified in-line electronic limiter** for initial energization, if one is not already available. The Base and High scenarios include the $134.24 adjustable bench supply; the Low scenario excludes it. Configure its voltage/current limit from the load budget and component datasheets, not a made-up universal threshold. The selected 5 V/3 A wall adapter alone **does not provide an adjustable first-power current limit**.
6. Complete the signal-voltage register in G3, including the comparator rail, all outputs, and the powered-down sense buffer. Verify that no path feeds 5 V directly to a Teensy digital input. A comparator powered at 5 V is **not** safe merely because it has a series resistor.

### Power-on sequence (G2, then G3, then G4)

None of these powered checks has been performed. The first source must be a reviewed current-limited supply; the fixed 5 V adapter is not a first-power instrument. The VBUS-only check must use a controlled source at 5.0 V and 5.25 V, with the buffer output isolated from Teensy GPIO, and must show that the buffer input stays below 5.5 V, the 3.3 V rail does not rise, and no backfeed path exists. Begin the combined-source check only after the isolation, short, polarity, orientation, and partial-power-down checks pass.

1. **External only, without USB:** Use a current-limited bench source through the assembled protection stage. With the analog and LED loads disconnected, confirm that VIN is within the Teensy's permitted range and that the 3.3 V rail is in range. Record idle and startup currents, check for abnormal heat, and measure any reverse voltage across the PTC/Pololu chain. Stop the test for any fault, dropout, brownout, or unusual temperature.
2. **Validate no USB backfeed:** While external VIN is stable and **no USB cable is attached**, measure the **VUSB pad** against common ground and document that the fixture supply is not driving it. An unexpected nonzero level is a stop condition. This test checks an important consequence of the cut but does not establish USB signal integrity.
3. **Independently prove VBUS sense when unpowered:** Remove external VIN and allow the circuit to discharge. With the buffer output initially isolated from Teensy GPIO and a controlled, verified USB VBUS source, confirm that the buffer input voltage stays within the datasheet limits and that the buffer does not energize the 3.3 V rail or GPIO while the device is off. Preserve the measurement. **Do not** wire raw USB 5 V to a Teensy GPIO for this test.
4. **Re-energize external VIN and then attach Android USB data:** After the G2 and G3 inspections pass and an independently reviewed power-path diagram confirms that no rails are joined, connect the host-to-Teensy device path. USB VBUS should come from the Android host; the Teensy should receive operating power from VIN. Monitor VUSB, VIN, 3.3 V, current, and reset behavior during attachment, disconnection, Android reboot, and loss of either supply. Disconnect immediately if the measurements show leakage, backfeed, or unstable operation.
5. **Retire the first-power bench limiter only if justified:** Switching to the selected fixed 5 V adapter after validation requires records of current, load, and fault behavior; confirmation that the protection components are installed correctly; and an inspection. A functioning smoke test does not establish final brownout or publication-grade power qualification.
6. After stable composite enumeration, the later firmware/Android task can test HID controls and, eventually, the separate vendor interface. Do not claim Full-Speed forcing, a measured HID completion, or valid latency without bus and hardware-clock evidence.

### Supply and connection scenarios

| Scenario | Permitted configuration | Evidence/stop |
| --- | --- | --- |
| Fresh Teensy, pre-modification | USB-only powered by host; **external VIN disconnected** | Digital smoke testing only; no independent power claim |
| After VIN/VUSB cut, external-only | Current-limited regulated supply through verified protection; USB disconnected | Rails/current and VUSB-isolation measurements |
| After cut, USB present but VIN absent | **Not an operating mode**: expect Teensy unpowered, VBUS sense buffer in Ioff state | Power-off backfeed observation only; no USB enumeration expected |
| After cut, both USB data/VBUS and external VIN | **Conditional operating goal** after G1-G4 checks | Verify USB host detection and enumeration, confirm zero unintended backfeed and correct rail voltages, and fail closed if any check fails. |
| Android disconnect, restart, DUT power loss, or reverse-polarity fault | Disarm, retain evidence, remove power as appropriate, and restart from a validated state. | Unexpected VUSB-to-VIN current, powered-off GPIO injection, excess voltage, overheating, or brownout stops work. |

The **verification gates authorize a future owner test**, not hardware use based only on this document. No such test has been executed here. Never connect two sources while the default VIN/VUSB bridge remains intact. The design specifically rejects battery/USB ORing and bypassing the cut because they introduce uncharacterized timing and power paths.

## GPIO, comparator and sense compatibility

| Signal | Required technical evidence | Selection / current state |
| --- | --- | --- |
| TLV3501AIDBVR to Teensy capture | Exact SOT-23-6 orientation, rated 3.3 V operation, push-pull VOH/VOL at load, startup/shutdown behavior, bypass, current and input thresholds | Candidate for operation from a **verified 3.3 V rail**. Never connect a comparator output powered at 5 V directly to Teensy GPIO. Circuit and routing remain unverified. |
| OPA320AIDBVR TIA output to comparator | Exact SOT-23-5 orientation, bias/input common-mode and output swing at the relevant load; local bypass, measured stability/saturation, actual Rf/Cf | This is a candidate for an analog 3.3 V rail, selected from the TI datasheet. Its response to optical pulses has not been proven. |
| **SFH 203 only** | Verify active ordering code `Q62702P0955`, **anode pad 1 → SUM / cathode pad 2 → 3V3**, project-designed 1.0 mm drills / 2.0 mm copper lands, installed package marking, opaque boot fit and viewing angle | Manufacturer status: full production. The datasheet gives a 20° half angle, or **40° total acceptance cone**, which requires optical characterization. Discontinued or last-time-buy parts are ineligible even if stocked. |
| VBUS digital sense | Input must be safe with USB VBUS live and the Teensy's 3.3 V rail off, **disconnected VBUS is held low**, and output is 3.3 V logic when powered; require no powered-off leakage path and local bypass | TI **SN74LVC1G17DBVRG4**, with **R1 = 1 kΩ series and R3 = 47 kΩ input pull-down**; nominal connected input ≈4.896 V / divider current ≈0.104 mA. Verify Schmitt thresholds, hotplug, and Ioff under actual test. |
| External power and fault signal | Verify input and output polarity, actual PTC current and voltage drop, Pololu module status and availability, and any future supervisor's signal levels | Pololu 5383 and MF-R050 are **candidates only**. The Pololu module does not isolate USB, and the PTC is not an adjustable limiter. |
| LED optical stimulus | Exact LED forward voltage/current and resistor, 2N7000 gate-drive suitability from 3.3 V, startup and off-state, no accidental 5 V GPIO drive | The slow LED circuit remains provisional until its parts and values are selected. The controller output may drive a MOSFET gate, but never an LED without a current-limiting resistor. |

Place suitable 0.1 µF ceramic bypass capacitors **at the analog IC and logic-buffer supply pins**, with short ground-return paths. Verify each manufacturer's recommended decoupling and load. An adapter PCB can make SOT-23 pins solderable but does **not** validate timing-sensitive TIA stability. The bench guide does not freeze a firmware pin or timer map; the subsequent implementation must measure five hardware capture channels on one timebase.

## Compact one-sensor optical assembly

The one-sensor study now uses **SFH 203** (`Q62702P0955`), a silicon PIN photodiode in active production. The earlier wide-angle **SFH 203 P** (`Q62702P0942`) is not approved because the manufacturer's dated discontinuity notice identifies it as discontinued, regardless of distributor stock. That product page still says ordering and shipping are possible, so this guide preserves the conflicting dated records without claiming that no orders can currently be placed. The selected 20° half angle, or 40° total cone, requires an appropriate opaque contact boot and independent optical dark/bright calibration. It is not optically equivalent to the older 150° design.

### Provisional soldered implementation

1. Confirm the exact OPA320AIDBVR (SOT-23-5) and TLV3501AIDBVR (SOT-23-6) packages against their manufacturer datasheets. Use the manufacturer drawing to orient the active **SFH 203** radial diode, not a generic adapter image. **D1 anode pin 1 is `SUM`, cathode pin 2 is `3V3`; the proposed 2.54 mm footprint has 1.0 mm drills and 2.0 mm copper lands.** Check the installed package orientation before soldering.
2. Use a **small dedicated prototype sensor PCB** or a physically compact soldered daughterboard with a continuous local return and reference path. Place the inverting input, diode, Rf, and Cf close together at the amplifier; a standard DIP adapter on a solderless breadboard **fails** the high-impedance-path boundary. Adafruit 1230 and TI DEM-OPA-SOT-1A are **mechanical/evaluation candidates**; neither is a finished detector, TIA, or comparator, and neither guarantees a ground plane. The owner may solder a small prototype PCB before final professional assembly.
3. Place supply bypasses right beside both analog ICs and the VBUS buffer as applicable. Keep comparator wiring on the same small assembly if stable; route only the comparator's **verified 3.3 V digital output** to a low-speed Teensy test lead. Keep LED and USB return currents out of the sensor input's analog reference.
4. Provide short, ground-referenced test pads for the TIA output, comparator output, local 3.3 V, photodiode reverse-bias node, threshold reference, and ground. Include probe loading and ESD protection in later characterization; **do not** attach an exposed scope ground clip to an unidentified USB conductor.
5. Select initial values for Rf/Cf, reverse bias, threshold/hysteresis, and LED forward current from a documented engineering model or datasheet equations. These values are **not frozen**; acquire exact passives only after reviewing the local schematic and footprint. Simulate or analyze front-end stability before commissioning.
6. Build a small, opaque, nonabrasive contact boot that shields ambient light without adding significant pressure to the display glass. Mechanical repeatability and 200-nit optical characterization belong to later gates.

### Provisional mechanical retention

The 50 mm by 44 mm carrier has no mounting holes, so retention requires an external clamp or bracket indexed to the board edges. A proposed matte black compliant boot would have a 6.0 to 6.2 mm internal bore, giving 0.1 to 0.3 mm radial clearance around the 5.90 mm maximum flange. Its rear shoulder would bear on the flange rather than the solder joints, and its opaque front face would have an aperture centered at approximately `(106.77, 110.5)` before attachment to the bracket.

This is a proposal only. Verify the aperture and the detector's 40° total acceptance cone with a fit coupon before soldering. No bracket, boot, optical-axis clearance, display contact pressure, or mechanical repeatability has been measured.

### Owner assembly and inspection

Solder only after the part list, pin-1 orientation, board layout, pad geometries, diode polarity, local decoupling, and power rails have been checked. Use ESD precautions, suitable temperature and flux, magnification, and cleaning. Photograph each populated revision and record any rework. With all sources disconnected, follow the DMM manufacturer's procedures for voltage and continuity measurements. Check for shorts without treating capacitor charging as a permanent short.

### Staged optical evidence

1. **Before buying an oscilloscope:** Assemble and inspect a low-risk LED/control demonstrator. Only after the analog schematic and protection have been reviewed, perform a **slow** optical response test with a low-current stimulus. Record the LED identity, drive current, bias, dark and illuminated rail levels, comparator states, and temperatures. If an actual 3.3 V hardware capture path already exists, record a functional edge; do not claim latency precision.
2. **After a scope is available:** Use the verified, protected 3.3 V front end to measure the TIA step response, comparator crossing, saturation/clipping, oscillation, and recovery with appropriate probes and an independent optical stimulus. Save the raw waveforms and scope settings. Change threshold/Rf/Cf only in a recorded revision; never make a hidden fix to past evidence.
3. **Before building five sensors:** Repeat the measurements at independently measured relevant luminances, including nominal 200 cd/m² and brighter diagnostics. Verify stability and PWM/noise flags, then design and assemble the matched five-channel fixture after the feasibility gate. An early single transition is not final T50 calibration or proof of <=0.1 ms uncertainty.

**Stop** if the package/polarity/bias or solder geometry is unknown; supply/input voltages are out of range; the TIA saturates or oscillates; the comparator toggles multiple times unexpectedly; or a signal can exceed the Teensy GPIO rating. Do not describe sourced parts as physically tested.

### Unperformed physical evidence

| Gate | Current status |
| --- | --- |
| PCB fabrication, assembly, physical fit and solder inspection | Not performed. The revised 5.90 mm maximum flange outline and 6.40 mm courtyard are design geometry only; installed fit remains unqualified. |
| First external energization and reviewed current limit | Not performed. |
| VIN/VUSB isolation and backfeed behavior | Not measured. |
| Android host role, enumeration and HID behavior | Not measured. |
| Optical current, TIA response and optical noise margin | Not measured. The ngspice output is not a physical result. |
| Comparator threshold and discrimination | Not measured. |
| HID IN completion against a common hardware time reference | Not measured. |

KiCad ERC and DRC, the structural checks, the generator check, and the Gate A model run support design review. They do not fill any row in this table.

## Android USB host and Teensy USB device

Android is the USB host; its USB-C port connects via an **Adafruit 5030 USB-C plug to USB-A socket adapter** and an **Adafruit 592 USB-A to Micro-B data cable** to the Teensy 4.1 **USB device** Micro-B port. The Teensy's separate USB-host header is never used here. The 5030 is an adapter candidate, **not proof** that every Android handheld supports USB host mode or the required composite device: verify actual host role, VBUS, connector retention and device enumeration on each DUT. Do not put a solderless breadboard or long flying probes in D+/D-.

### External T0 correlation: scope-first, analyzer fallback

The [RIGOL DHO800 datasheet](https://www.rigol.com/dam/global/downloads/brochures/en/data-sheet/oscilloscopes/DHO800_DataSheet_EN.pdf) lists the DHO814 as a **100 MHz**, four-channel oscilloscope with a maximum rate of **312.5 MSa/s when all four analog channels are active** (higher rates are available with fewer active channels). This supports investigating Full-Speed USB analog events but **does not guarantee** packet-level protocol interpretation at the required uncertainty; native USB decoding is not established. The [Beagle USB 12](https://www.totalphase.com/products/beagle-usb12/) can decode Full-Speed packets, but [Total Phase states it lacks external I/O/trigger](https://www.totalphase.com/blog/2015/03/beagle-usb-protocol-analyzers-provide-external-trigger-test-usb-response/): its timestamp domain is **not** automatically comparable to Teensy hardware capture ticks. It is not a synchronized T0 purchasing recommendation.

1. Build or source a **short, impedance-conscious USB 2.0 probe fixture** with geometry and grounding supported by manufacturer documentation. Use 10x high-impedance probes or an appropriate differential measurement. Record probe capacitance and loading, then measure any disturbance; do not clip a probe ground to D- or put D+/D- on a breadboard.
2. Capture simultaneous oscilloscope traces for D+, D-, and an **explicit fixture hardware timer marker** (optionally, the comparator or LED on the fourth channel). Document the common scope acquisition clock, the sample rate actually used, the timebase, reference alignment, and any marker propagation or calibration delay. A software interrupt or USB enqueue callback is not a T0 authority.
3. Establish a known **12 Mb/s Full-Speed** IN-token/data/host-ACK sequence, independently validate direction, PID/address/endpoint and the measured button-1 sequence, and preserve the raw analog trace. A guessed waveform transition is not a decoded ACK. Verify whether scope-only manual decoding remains defensible over representative transfers.
4. Associate the **completed, ACKed measured HID IN transfer** with the fixture timer read/capture; measure delay, jitter, and uncertainty against the physical USB trace, not Android time. Repeat across power cycles and relevant load. Record failed or ambiguous packets and distinguish marker timing from the actual USB completion timestamp.
5. If scope-only correlation lacks reliable packet identification, signal integrity or sufficient timing precision, **borrow or rent a synchronized protocol analyzer with documented external I/O**, or comparable externally validated acquisition. Do not buy a non-synchronizable $495 Beagle USB 12 just to make the T0 gate look complete.
6. In a later stage, correlate the measured HID event with actual Android input consumption, native renderer state, and one optical edge. The five-channel hardware timer skew and uncertainty gate is separate and remains unproven here.

The USB stimulus and vendor interface must be tested on actual hardware; the existence of these procedures is not evidence that Full-Speed forcing, packet completion, an optical edge or a common-timebase measurement has passed.

## Downstream issue handoff and evidence matrix

These are open follow-on issues, not results of Issue #3 or this documentation update. Retain raw failures as well as successful captures. Simulation, KiCad reports, host-only tests, and photos do not replace the physical evidence below.

| Issue | Handoff target | Evidence to retain | Completion boundary and stop |
| --- | --- | --- | --- |
| [#4: Android Full-Speed HID plus vendor control](https://github.com/whatobiplays/screen-latency-tool/issues/4) | Enumerate the Teensy as a button-1 gamepad while the test app owns the vendor bulk interface. | Record the Android model, OS, and enumeration result; evidence of genuine USB 2.0 Full-Speed operation; the expected two-interface descriptor; a stable HID report; the measured button-1 press; whether reset release reaches the Android gamepad stack; a GET_FIXTURE_INFO or equivalent vendor smoke operation; interface identity; and any disconnect/backfeed failures. | Keep the descriptor and control interface provisional until T0/capture qualification. Stop if vendor access steals HID, Full-Speed is unverified, host role fails, or disconnect power is unsafe. |
| [#5: five shared-timebase hardware capture inputs](https://github.com/whatobiplays/screen-latency-tool/issues/5) | Prove five simultaneous hardware captures without interrupt-entry timestamps. | Pin/peripheral mapping and actual capture registers; at least 10,000 common electrical edges with no missing captures; raw skew/overflow; monotonic counter extension and cold-power-cycle check; preliminary skew compared with the <=25 us target. | Stop if routing/capture cannot meet target and document an external capture fallback. Breadboard evidence cannot establish production uncertainty. |
| [#7: one reusable photodiode/TIA/comparator prototype](https://github.com/whatobiplays/screen-latency-tool/issues/7) | Measure one active SFH 203 channel and calibrated 50% comparator threshold. | Compact decoupled SFH 203/OPA320/TLV3501 build with analog monitor, comparator output, reference, and revision identity; real 200 cd/m2 SDR black/white amplitude, noise, hysteresis, T50 crossing, response, saturation margin; repeated-edge/PWM and dark/noise tests with ambiguous edges classified; Rf/Cf, diode bias, wiring, scope settings, and raw traces. | Keep values provisional until physical measurements support them. Do not use SFH 203 P or claim simulation establishes optical discrimination. |
| [#8: USB HID completion timestamp against bus evidence](https://github.com/whatobiplays/screen-latency-tool/issues/8) | Associate fixture T0 with successful measured HID IN transaction and ACK, not enqueue time or an unrelated callback. | Scope channels/rate/probe loading; raw D+/D- and adjacent Teensy timer-read GPIO marker; validated IN/DATA/ACK; min/median/P99/worst-case completion-path timing over representative traffic; retries/disconnects; common-clock alignment and complete <=0.1 ms uncertainty budget. | Acquire a DHO814 or demonstrated equivalent only when needed. If waveform/sync cannot bound error, stop and document an externally synchronized capture fallback. Full-Speed forcing alone is not qualification. |

### Raw evidence to retain

| Area | Artifact | Required context |
| --- | --- | --- |
| Power | DMM readings, continuity, supply setpoint/current limit, photos | Board revision, pad condition, wire colors, connector polarity, meter range, timestamp |
| Logic | Pin/pad map, voltage levels, comparator waveform, bypass inspection | Exact IC markings/package, rail, probe loading, firmware/test revision |
| Optical | Board photos, inspection notes, TIA/comparator traces, stimulus state | Diode orientation, measured LED current, distance, ambient light, boot, luminance, scope settings |
| USB | Analyzer setup, negotiation, token/data/ACK trace, marker trace, rejected traces | Adapter/cable identity, Android model/OS, analyzer clock, sync path, transfer sequence |
| Capture/timing | Raw edge captures, scope files, offsets, calibration notes, overflow counters, failures | Shared timebase, sample settings, cold-start/restart conditions, environment |

No physical observations are recorded in this guide. Keep raw files unchanged; do not replace missing or failed measurements with a passing checkbox or simulation output.

## Explicit stop conditions

Stop the bench and leave the affected connection open if any of these is true:

1. VIN and VUSB remain connected while external power and USB would be applied together.
2. The regulated input, current limit, common ground, reverse-polarity protection, reverse-current path, or VBUS sense is unresolved or outside its verified range.
3. A GPIO signal is not shown to remain within the Teensy 3.3 V input limit through startup, shutdown, and fault states.
4. The optical package, polarity, pinout, footprint, decoupling, or high-impedance path is unverified.
5. A comparator output exceeds 3.3 V, has uncontrolled current, oscillates, or changes state unexpectedly at power-up.
6. The cable, OTG adapter, or analyzer cannot preserve Full-Speed operation and provide a usable nonintrusive observation point.
7. The measurement setup cannot identify a completed HID IN transfer and its ACK against a documented fixture time reference.
8. A physical run has no preserved raw evidence, or its evidence is being used to claim a five-channel or publication result.

## Documentation acceptance boundary

This guide records sourcing context, design constraints, and owner procedures. Publishing the guide, routing a PCB, or passing KiCad and model checks does not satisfy sourcing, budget, interconnect, assembly, or physical-test acceptance. Store owner results and reviewer decisions in [Issue #3](https://github.com/whatobiplays/screen-latency-tool/issues/3). Do not transfer evidence from Issues #4, #5, #7, or #8 into Issue #3 acceptance evidence.
