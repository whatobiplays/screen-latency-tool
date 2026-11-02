# Issue 3 prototype electronics — engineering draft

**Scope:** one provisional soldered optical sensor and a separately prototyped Teensy 4.1 external-power / USB-presence interface. This is an **editable design proposal**, not a production PCB, qualified electronics assembly, or permission to buy/fabricate/energize hardware. See [Issue #3](https://github.com/whatobiplays/screen-latency-tool/issues/3), [SPEC-X-001](../../docs/specs/SPEC-X-001-fixture-and-mechanics.md), [PHASE-001](../../docs/phases/PHASE-001-hardware-android-feasibility.md) and [ADR-0002](../../docs/adr/0002-prove-hardware-timing-before-contract-freeze.md).

## Deliverable boundaries

| File | Purpose | Readiness |
| --- | --- | --- |
| [Optical schematic](optical/one-sensor.kicad_sch) | Pin-numbered, one-channel photodiode → OPA320 TIA → TLV3501 comparator, bias, threshold, bypass, monitoring and test points | **Draft**; KiCad 10.0.8 ERC passed for this revision with 0 errors and 0 warnings. Model-based analog feasibility is **PASS WITH LIMITATIONS**; physical electrical and optical acceptance remain unverified. |
| [Optical PCB](optical/one-sensor.kicad_pcb) | 50 × 44 mm two-layer routed prototype with a continuous B.Cu ground plane and an input-loop void | **Editable native KiCad source.** KiCad 10.0.8 DRC and schematic parity report 0 violations, 0 unconnected pads and 0 parity issues. `/EDGE` is routed entirely on F.Cu without signal vias. `/VREF` and `/THRESH` retain documented, low-risk B.Cu compromises. The board has no mounting holes or modeled detector body. Its optical axis and physical fit remain unverified, and no Gerbers are approved. |
| [Active SFH 203 footprint](optical/ProtoOpto.pretty/Photodiode_SFH203.kicad_mod) and [library table](optical/fp-lib-table) | 2.54 mm pitch, **pad 1 anode / pad 2 cathode**, 2.0 mm lands, 1.0 mm drills, 5.90 mm maximum flange, 5.85 mm nominal flange and 6.40 mm courtyard | The datasheet drawing matches the footprint's 2.54 mm pitch, 5.90 mm maximum envelope, and cathode polarity. The drawing gives no land-pattern or drill recommendation, so the 2.0 mm lands and 1.0 mm drills are project-designed. The installed part, cathode mark, optical axis and provisional boot fit still require physical review. |
| [Power/USB candidate schematic](power-usb/power-usb.kicad_sch) | Off-board adapter → MF-R050 PTC → Pololu 5383 reverse-current protector → Teensy VIN; isolated VUSB sensing through an Ioff-capable buffer with a 47 kΩ pull-down; diagnostic LED | **Draft wiring logic only.** The design has no routed carrier PCB or complete USB data wiring, and backfeed behavior remains unverified. Do not energize it until the unpowered continuity and isolation checks and the staged input checks below are complete. |
| [Derived components](BOM.csv) | One-channel component instances, candidate MPNs, provisional footprints, snapshot prices when verified, DNP field, offboard items | **Not order-ready**; 41 modeled instances include explicit lifecycle status, with unchecked parts marked UNVERIFIED; fabricated board, non-modeled materials/tools, delivery and physical review remain missing |
| [Generator](generate.mjs) | Regenerates schematic sources, project libraries and CSV; the routed PCB is authoritative native KiCad source and is not emitted or overwritten | `--check` detects generated-file drift without writing. It does **not** invoke ERC/DRC or certify electrical behavior. |
| [KiCad projects](optical/one-sensor.kicad_pro), [power/USB](power-usb/power-usb.kicad_pro) | Open through the KiCad Project Manager rather than standalone editor mode | Project-specific `sym-lib-table` files reference the same [shared `Proto` symbol library](Proto.kicad_sym) via `${KIPRJMOD}/../Proto.kicad_sym` |
| [ERC report gate](erc-report-gate.mjs) | Parses locally generated ERC reports and fails if errors, warnings, or a filtered/incomplete report remain | Does not run KiCad; requires fresh post-edit reports with **both Errors and Warnings** included; **do not commit report exports** |

Existing [bench BOM](../../docs/bench/PROTOTYPE-BENCH-BOM.md) controls **staged purchasing estimates** and the [bench guide](../../docs/bench/PROTOTYPE-BENCH-GUIDE.md) controls physical inspection/evidence gates. The circuit manifest's **41 modeled rows are now individually priced** using dated single-unit vendor listings or $0 built-in PCB/test pads. The selected **ERJ-3EKF1002V** 10 kΩ resistor and **KGM15BR71H104KT** 100 nF 0603 capacitor remain replacements for the original out-of-stock candidates. The 2026-11-01 DigiKey snapshot showed zero on-hand stock for the exact **KGM15BR71H104KT** MPN. **KGM15BR71H104KM** is a documented packaging-code alternative (13-inch versus 7-inch paper tape), but it is not an approved substitution. Manufacturer active-production status remains unverified for both capacitor MPNs. The [bench BOM](../../docs/bench/PROTOTYPE-BENCH-BOM.md) is authoritative for the dated sourcing assessment. Supplier availability observations do not guarantee current inventory or procurement authorization. This does **not** price fabrication, full harness/PCB materials, owner tools, shipping or tariffs and must not be naively added to the stage-one budget subtotal. Neither substitutes for electrical review or actual hardware testing. The `hardware/prototype` directory was added as an owner-approved Issue #3 scope extension, not as a premature production-contract freeze.

## Optical functional design

The first reference population intentionally favors basic functional responsiveness and a **visible, adjustable output threshold**. The values below are starting candidates, **not demonstrated values at 200 cd/m²**, and may be replaced after model-based stability review and scope measurements.

| Instance | Candidate | Intent and restrictions |
| --- | --- | --- |
| D1 | **SFH 203**, active-production through-hole photodiode (`Q62702P0955`) | **Pad 1 anode → `SUM`; pad 2 cathode → `3V3`**. Viewed from the component side, pad 1 is on the left and pad 2 is on the right. Use 2.0 mm lands, 1.0 mm drills and 2.54 mm pitch. The datasheet gives a 20° half angle, or 40° total acceptance cone, and identifies the package flat as the cathode. Page 9 matches the footprint's pitch, maximum envelope and polarity, but provides no manufacturer land-pattern or drill recommendation; the chosen lands and drills are project-designed. Inspect the installed cathode mark before assembly. Check the boot aperture on a fit coupon and measure optical response after assembly. The 2026-03-18 manufacturer discontinuation notice identifies `SFH 203 P` (`Q62702P0942`) as discontinued, although its product page still says ordering and shipping are possible. `Q62702P0955` is the selected replacement; the older part remains ineligible under the lifecycle policy. |
| U1 | OPA320AIDBVR, SOT-23-5 | Pin 1 OUT=`TIA_OUT`, 2 V−=`GND`, 3 non-inverting=`VREF`, 4 inverting=`SUM`, 5 V+=`3V3`. No exposed `SUM` probe pad. |
| R1/R2/C1 | 18 kΩ upper / 10 kΩ lower / 1 µF bypass | DC reference `VREF` with local filtering. Source impedance, noise, ADC reference and rail coupling require later measurement. |
| R3/C2 | 47 kΩ / 2.2 pF C0G, in parallel | Provisional TIA feedback network; place **immediately beside U1**. Alternate 470 kΩ and 1 MΩ footprints/values can be explored only after assessing input capacitance and saturation. |
| U2 | TLV3501AIDBVR, SOT-23-6 | Pin 1 IN−=`TIA_OUT`, 2 V−=`GND`, 3 IN+=`THRESH`, 4 V+=`3V3`, 5 OUT=`CMP_OUT`, 6 **SHDN=`GND` (enabled low)**. 5 V-powered output must never drive the Teensy. |
| RV1/R7 | Bourns 3296W 10 kΩ, 1 MΩ fallback bias | Adjustable comparator `THRESH`, initially set near dark TIA output, not blindly at rail. Check wiper-pin mapping to installed part. |
| R4 | 2.2 MΩ `CMP_OUT`→`THRESH` **DNP** | Optional additional positive feedback/hysteresis, **not populated by default**; TLV3501 has intrinsic hysteresis. Any population and threshold shift require analysis and bench evidence. |
| R5 / R6 | 100 Ω series edge output / 1 kΩ monitor output | `EDGE` is 3.3 V push-pull digital pulse to a **provisional** Teensy capture input; `MON` is a **temporary one-channel bench diagnostic**, not a production multi-sensor cable commitment. |
| C3/C4 and C5/C6 | 100 nF+1 µF (OPA320); 100 nF+2.2 µF (TLV3501) | Bypass each IC to the routed PCB's ground plane with a short return path. |
| J1 | 4-pin 2.54 mm | 1 `3V3`; 2 `GND`; 3 `EDGE`; 4 `MON`. Final timing-only cable is not defined by this test header. |

At nominal 3.3 V and ignoring tolerances/op-amp offsets,

- `VREF = 3.3 × 10 / (18 + 10) = 1.1786 V`.
- `Vreverse,nominal ≈ 3.3 − 1.1786 = 2.1214 V`. This is a **candidate** SFH 203 bias, not validated under panel light, temperature or fault conditions.
- Ideal inverting TIA response is `ΔVout ≈ −Iphoto × 47,000 Ω`: **4.7 mV per 0.1 µA**, **47 mV per µA**. The actual photocurrent on a 200-nit display is **unknown**, so these numbers do not establish comparator signal-to-noise margin.
- The feedback-only RC product is `47 kΩ × 2.2 pF = 103.4 ns`; its ideal pole is `1/(2πRfCf) ≈ 1.54 MHz`. **This is not a measured front-end step time, stable-loop bandwidth, or <100 µs system proof**. Input diode, board and amplifier parasitics, phase margin, full-power bandwidth and optical PWM remain unqualified.

The comparator is intentionally arranged to switch as `TIA_OUT` **falls** below `THRESH` when illumination increases. The final measurements require independent per-sensor dark/bright 10/50/90% thresholds, separate monitor/ADC characterization and calibration revisions. A trimmer and one comparator **do not** fulfill the later five-sensor programmable-DAC design.

### Gate A simulation evidence

[`simulation/gate-a.mjs`](simulation/gate-a.mjs) runs the external TI macro models in ngspice and writes decks, raw data, logs and the provenance manifest outside Git. The 2026-10-31 review run passed all **12/12** assertions. Results are model-based only and do not establish a powered assembly or optical response.

| Check | Result |
| --- | ---: |
| DC transimpedance | 46.999988 kΩ |
| −3 dB bandwidth | 1.905 MHz |
| Nominal 1 µA step overshoot | 14.335% |
| Nominal 2% settling | 0.801 µs |
| 40 µA overload recovery to 1% | 1.399 µs after the pulse |
| Output noise through 2 MHz | 5.829 mV RMS |
| Intrinsic comparator hysteresis | about 6.06 mV |
| 3.3 V comparator high output | 3.280 V |

The input-capacitance sensitivity sweep covers 20 pF to 25 pF and `Cf` from 2.1 pF to 2.3 pF. Overshoot stays between 12.8% and 19.0%, with 2% settling between 0.61 µs and 0.94 µs. The derived second-order phase-margin estimate is roughly 49° to 54°, but that inference is not a measured loop stability result.

The 424 MΩ reverse-leakage resistance is a simulation assumption, not an SFH 203 datasheet limit or measurement. The 20 pF to 25 pF capacitance envelope is a bench-planning range for the package, boot and layout; neither bound has been measured. No defensible photocurrent at 200 cd/m² has been established, so comparator discrimination from a real display remains unaccepted.

Model provenance is recorded in the generated `gate-a-summary.json` and the [reproducibility section](#simulation-provenance-and-reproduction). The OPAx320-Q1 file is a generic six-pin-family model and does not prove the five-pin OPA320 package. The TLV3501 file is a PSpice transient model run with `ngbehavior=ps`. Manufacturer model archives stay outside the repository.

### Package and optical caution

The SFH 203 is placed at board coordinate `(105.5, 110.5)` with zero rotation. Viewed from the component side, pad 1, the anode, is on the left and connects to `/SUM`; pad 2, the cathode, is on the right and connects to `/3V3`. The optical axis runs perpendicular to the board through the body center at approximately `(106.77, 110.5)`, pointing away from the front copper. Check the installed part's small molded cathode mark during assembly.

The ams OSRAM drawing specifies a 5.90 mm maximum flange, a 5.85 mm nominal flange, a 2.54 mm lead pitch and an 8.5 ± 0.4 mm body length from the lead seating plane. The footprint's silkscreen shows the maximum flange, and its fabrication outline shows the nominal flange. The 6.40 mm courtyard leaves 0.25 mm radial clearance beyond the maximum body. KiCad has no 3D model for D1, so these outlines document the detector envelope but do not prove mechanical fit.

The 50 × 44 mm board is an oversized bench characterization carrier, not a production carriage PCB. It has no mounting holes. Use a provisional external clamp that grips the board edges without loading the solder joints. The matte-black boot should have a 6.0 to 6.2 mm internal bore. A rear shoulder should rest on the photodiode flange, and the boot's opaque aperture should be centered on the optical axis. Print a fit coupon and check the installed part before relying on the assembly. This work does not define the final five-sensor fixture.

Keep the B.Cu void below D1, U1 and the direct feedback loop. The void reduces one parallel-capacitance path; it does not establish TIA stability or optical acceptance. As part of the physical review, compare the generic SOT-23-5 and SOT-23-6 pad numbering with the TI package drawings.

### Simulation provenance and reproduction

The review run used **ngspice-45.2**, whose binary reports `ngspice-45`. Its SHA-256 is `a0032f9aad4216ee87ddbc27fab7479d2375f267fef41c5292532df253230d93`. The surviving build records show `--without-x --with-readline=no --with-fftw3=no --disable-openmp --disable-klu --disable-debug`, compiler flags ending in `-std=gnu11`, and `XSPICE features included`. The complete historical `make` command and any `EXTRA_CFLAGS` value are absent from the surviving `make.log`, so this repository does not claim either. The provenance JSON supplied to the runner records these limits and hashes every build record it references.

The external TI model files remain outside Git:

- OPAx320-Q1 model `OPAx320-Q1.LIB`, SHA-256 `a272838427e8988bdc662887238d2698d23feb0d39400a2c4c4a929f10c29460`, from [TI's OPA320-Q1 model page](https://www.ti.com/product/OPA320-Q1#design-tools-simulation), archive `OPAx320_PSpice_RevG.zip`, model Final 1.7 dated 17 June 2022. The model covers the six-pin OPAx320-Q1 family and does not prove the five-pin OPA320 package.
- TLV3501 model `tlv3501.lib`, SHA-256 `4e4345dc2929ee91a99a0b5437369c32b3817e9c8673a7ae90310d5485a81ba9`, from [TI's TLV3501 model page](https://www.ti.com/product/TLV3501#design-tools-simulation), archive `TLV3501_PSpice.zip`, model revision 2.0 dated 26 July 2021 and generated for PSpice 17.4. Gate A ran it with `ngbehavior=ps`; that tests this model and deck, not general PSpice compatibility.

Download the archives from the TI pages and use them under TI's terms. Do not commit or redistribute the archives or extracted model files. Reproduce the recorded sweep from the repository checkout with this form, changing only the external paths:

```sh
node hardware/prototype/simulation/gate-a.mjs   --ngspice /path/to/ngspice   --opa-model /path/to/OPAx320-Q1.LIB   --tlv-model /path/to/tlv3501.lib   --simulator-provenance /path/to/ngspice-build-provenance.json   --out-dir /tmp/slt-issue3-gate-a
```

The provenance JSON needs `sourceVersion`, `sourceIdentity`, `configureArguments`, `compilerFlags`, `featureEvidence`, `buildEvidence`, and `limitations`. Each `buildEvidence` entry names a surviving file and the observations taken from it. `gate-a.mjs` hashes those files and records every deck invocation in `gate-a-summary.json`.

**No instruction here authorizes fabrication of the draft PCB.** First route and validate a revised PCB after schematic review and analog modeling, then obtain explicit prototype-fabrication approval. Final professionally assembled PCB production remains after the Phase-1 feasibility gate.

## Power and VBUS behavior

The power schematic represents **connectivity between off-board modules and a small soldered control assembly**, not a fabricated carrier schematic. The Teensy 4.1 USB device port's D+/D− and ground remain in a standard, intact USB cable; they do not appear as improvised breadboard wires or PCB traces.

- Connect the fixed-output 5 V/3 A adapter to the `MF-R050` resettable PTC, then connect the PTC to the `Pololu #5383` reverse-voltage and reverse-current protector. Connect the protector's VIN, VOUT and GND pins correctly, and connect its output to `Teensy VIN`. The PTC is a **slow-acting thermal fault limiter**. The Pololu protector does **not** provide a precision adjustable current limit or isolate USB VBUS.
- Before external VIN and USB host VBUS are used together, sever and inspect the Teensy's underside **VIN–VUSB trace**. With external VIN applied by itself, verify that the isolated `VUSB` pad has no voltage. After completing the unpowered checks below, perform the staged VBUS sensing and backfeed measurements. Keep USB `VUSB` isolated from VIN.
- Connect isolated `VUSB_RAW` through 1 kΩ resistor `R1` to `VUSB_BUFFER_IN`, which connects to pin 2 (A) of the TI `SN74LVC1G17DBVRG4`. A 47 kΩ resistor, `R3`, connects `VUSB_BUFFER_IN` to GND and pulls the buffer input low when VBUS is disconnected. At nominal 5 V VBUS, the divider predicts about **4.896 V at the buffer input** and **0.104 mA of VBUS draw**, excluding leakage and tolerances. Bypass pin 5 locally at the 3.3 V VCC supply. Connect pin 3 to GND and pin 4 (Y), `VBUS_PRESENT`, to a provisional GPIO; leave pin 1 unconnected. The device specifies a 5.5 V-tolerant input and Ioff power-down behavior. Physical leakage and backfeed, actual input thresholds and hotplug behavior remain unverified.
- The candidate diagnostic LED is a **Kingbright WP7113ID**, a 617 nm red through-hole LED with a listed typical forward voltage of **1.9 V**. From a verified 3.3 V GPIO through the nominated 1 kΩ resistor, its estimated current is `(3.3−1.9)/1000≈1.4 mA`. Check the GPIO drive limit, LED polarity, actual forward voltage and operating range before connecting it. The LED circuit has not been tested on hardware.
- For first energization, use a separate current-limited bench supply or electronic limiter. The fixed 3 A wall adapter alone is not sufficient. Do not connect Android and external VIN together until independent circuit review and the staged measurements in BENCH-002 permit it. Confirm that a DMM, soldering tools and a current limiter are available; an optical meter or 3D printer cannot replace them.

A one-sensor prototype may tentatively use the Teensy's own 3.3 V regulated rail, **subject to the manufacturer's additional-load budget** (PJRC recommends ≤250 mA on 3.3 V for external circuitry). It is **not** a production analog rail or validation of the five-sensor current/noise budget. Reset, brownout, hotplug and fault signaling must be tested before declaring electrical feasibility.

No electrical energization, backfeed measurement or physical power acceptance has been performed. Complete these checks before applying voltage:

1. Disconnect every power source and allow the rails to discharge. Before using resistance mode, verify that accessible rails measure zero volts.
2. Inspect the Teensy's underside VIN–VUSB trace cut and confirm the pads are separated. Verify the barrel-jack polarity, confirm that the PTC is in series, and check the Pololu VIN/VOUT/GND orientation, bypass capacitors, D1 cathode mark and pin-1 marks on all ICs.
3. With all sources disconnected, measure PTC continuity and confirm that VIN is isolated from VUSB. Check that there is no 5 V-to-GND short or hard 3.3 V-to-GND short. Verify continuity through the VBUS divider to `VUSB_BUFFER_IN`, and confirm that no 5 V path reaches the Teensy logic header. Record the meter range. Finite semiconductor resistance is expected; a hard short is not.
4. With USB disconnected, use an adjustable current-limited bench supply set to 5.0 V. Choose the current limit only after reviewing a load budget; this document does not specify a numeric setting. Verify that 3.3 V is stable, VUSB remains near 0 V, the buffer output is low, supply current is expected and no component heats up.
5. With external VIN disconnected, apply controlled VBUS by itself. At nominal 5 V VBUS, verify about 4.896 V at the divider input. Confirm that the unpowered buffer output is high impedance, the 3.3 V rail does not rise and the buffer does not backfeed power.
6. Only after those checks pass, apply external power and USB together while monitoring VUSB, VIN, 3.3 V, current, and reset behavior. Disconnect immediately if backfeed, an unexpected rail rise, overheating or unstable current occurs.

The Pololu 5383 provides reverse-voltage and reverse-current protection and accepts 4 to 60 V input. It does not limit current or clamp overvoltage. The MF-R050 is a slow-acting PTC rated for 0.5 A hold and 1 A trip at 23 °C; its hold current falls to about 0.41 A at 40 °C. PJRC specifies 5 V on VIN when USB is disconnected, 3.3 V logic and digital inputs that are not 5 V tolerant. PJRC recommends an external load of no more than 250 mA on the 3.3 V pin. At 5.25 V VBUS, the buffer input remains below its 5.5 V recommended limit. Physical no-backfeed behavior remains untested.

### Project-local ERC power-source declarations

The revised generated schematics contain **two virtual `Proto:PWR_FLAG` symbols each**. The flags mark `3V3` and `GND` as supplied by external connections: **optical J1 pins 1/2**, and **power/USB J4 pins 1/2 from the Teensy external 3.3 V rail and shared ground**. Their pins have KiCad `power_out` electrical type solely so ERC can recognize externally sourced rails; they are excluded from the physical BOM/PCB and **must not be interpreted as power regulators or electrical validation**. They do not bridge VIN to VUSB or make the host USB connection safe.

A custom, project-local KiCad symbol library now exactly matches the embedded `Proto` symbol definitions and is registered by each project's `sym-lib-table`. This replaces the prior missing-library warnings without ignoring symbol checks. The actual 5 V regulator, protection, startup sequencing and partial-power-down behavior remain subject to independent design and owner bench review.

## Reproducibility and validation commands

Run these commands **on the actual repository checkout with Node.js and KiCad installed**. Native **KiCad 10.0.8** ERC on the one-sensor and power/USB schematics returns 0 errors and 0 warnings. `erc-report-gate.mjs` accepts both complete Errors+Warnings exports. Both reports still list four ignored checks: single-use global labels, four-way junctions, SPICE-model issues and footprint-filter mismatches. These checks remain review obligations.

Native PCB DRC with schematic parity reports 0 violations, 0 unconnected pads and 0 footprint errors. Its report lists five ignored checks: footprint courtyard absent, via endpoint not centered, tuning-profile geometry, footprint-filter mismatch and footprint-type mismatch. The structural checker and generator drift check also pass. These checks do not establish electrical safety or physical fit. Keep raw ERC/DRC reports outside the repository and record their scope, ignored checks and remaining gates in [Issue #3](https://github.com/whatobiplays/screen-latency-tool/issues/3).

```sh
node hardware/prototype/generate.mjs --check
node hardware/prototype/check.mjs
node hardware/prototype/erc-report-gate.mjs --selftest
"$KICAD_CLI" pcb drc --severity-all --schematic-parity --exit-code-violations \
  --format report --output /tmp/slt-optical-drc.rpt \
  hardware/prototype/optical/one-sensor.kicad_pcb

# macOS: KiCad does not normally add its CLI to your shell PATH.
# If installed elsewhere, adjust KICAD_CLI to the executable inside KiCad.app.
KICAD_CLI="/Applications/KiCad/KiCad.app/Contents/MacOS/kicad-cli"
if [ ! -x "$KICAD_CLI" ]; then
  printf 'KiCad CLI not found at %s\n' "$KICAD_CLI"
else
  "$KICAD_CLI" version &&
  "$KICAD_CLI" sch erc --severity-error --severity-warning \
    -o /tmp/slt-optical-erc.rpt hardware/prototype/optical/one-sensor.kicad_sch &&
  "$KICAD_CLI" sch erc --severity-error --severity-warning \
    -o /tmp/slt-power-erc.rpt hardware/prototype/power-usb/power-usb.kicad_sch &&
  node hardware/prototype/erc-report-gate.mjs \
    /tmp/slt-optical-erc.rpt /tmp/slt-power-erc.rpt
fi
```

To deliberately regenerate generated sources and BOM, run `node hardware/prototype/generate.mjs` without `--check`. It does not write `one-sensor.kicad_pcb`; the routed board is native KiCad source and must be edited in KiCad. Inspect the schematic and PCB in KiCad before fabrication. No Gerber or CPL output is approved.

**Expected:** `generate.mjs --check` reports `stale: 0`, `check.mjs` reports zero failures, both ERC reports contain Errors and Warnings and contain zero findings, and native DRC reports zero violations and zero unconnected pads. Do not suppress rules to obtain those results.

The KiCad CLI location above is documented for official macOS installs; check with `ls -l "$KICAD_CLI"` and, if needed, locate a nonstandard install using `find /Applications "$HOME/Applications" -name kicad-cli -type f 2>/dev/null`. Invoke the app-bundled binary directly rather than assuming `kicad-cli` is available on `PATH`. The ERC commands explicitly request both error and warning severities and short-circuit report validation if KiCad fails.

`generate.mjs --check` compares generated CAD outputs with their source files byte for byte. It detects **drift** but does not check KiCad correctness. The independent `check.mjs` structural checker parses the saved S-expressions and verifies project names, selected pin-to-net links, symbol/PCB UUID correspondence, pad numbers, DNP metadata, obvious overlaps between different-net pads, component counts, both copper layers, the ground zone, tracks, vias and routed net names. It also verifies that `/EDGE` remains on F.Cu with no signal vias and that both SFH 203 footprint copies show the maximum flange and 6.40 mm courtyard. The structural checker passed **332 checks with zero errors**, including checks of the custom symbol library and both KiCad project manifests. These checks do not replace native KiCad ERC/DRC, electrical safety checks or 3D visual inspection. After each vendor package revision, verify all symbol and pad numbers against the manufacturer documentation.

SPICE analysis is **PASS WITH LIMITATIONS** for the supplied TI macro models and the stated sensitivity envelope. It does not establish physical stability, the real photocurrent under panel illumination, or comparator discrimination. Keep the external models and evidence with the corresponding design revision. No software calculation substitutes for bench measurements.

### Gate C analysis outcome

Final KiCad 10.0.8 and analyzer results for the revised board:

| Check | Result |
| --- | --- |
| Native DRC and schematic parity | 0 violations, 0 unconnected items, 0 parity issues |
| Full PCB analysis | 0 errors, 0 warnings, 12 informational observations |
| Schematic/PCB cross-analysis | 0 findings |
| EMC pre-compliance | 80.5/100, 9 heuristic findings |
| Structural checker | 332 checks, 0 failures |

The EMC score rose from 73/100. The analyzer is heuristic; a clean DRC does not resolve the remaining findings or establish EMC compliance:

| Net or area | Finding | Disposition |
| --- | --- | --- |
| `/EDGE` | The original route had an 11.1 mm B.Cu span and two signal vias without nearby ground returns. | The route now stays on F.Cu and has no signal vias. The analyzer reports 89% reference-plane coverage along 39.6 mm as a warning. The comparator edge may still emit noise through a connected bench cable. |
| `/VREF` | 60% reference-plane coverage along 83.4 mm; four layer transitions lack ground stitching within 1 mm. | This is an accepted low-risk prototype compromise. The filtered DC reference can still pick up noise through the plane slot. |
| `/THRESH` | 76% reference-plane coverage along 79.1 mm; two layer transitions lack ground stitching within 1 mm. | This is an accepted low-risk prototype compromise. The DC threshold control can still pick up noise through the plane gaps and increase comparator jitter. |
| `/SUM` | 80% reference-plane coverage along 21.4 mm. | This gap is intentional. The B.Cu void preserves capacitance control around the high-impedance photodiode/TIA summing area. |
| `J1` | No local ferrite or ESD protection; one ground pin serves three signals. | This is acceptable only with the short bench harness. Behavior with a long or shielded cable remains unqualified. |
| Board | Cavity resonance estimates range from 1.43 to 2.86 GHz. | These estimates are informational for this analog front end. No PDN measurement supports a stronger conclusion. |

The PCB analyzer reports no opposite-layer copper beneath D1, U1, J1, RV1 or the through-hole test pads. This matches the intentional input void and local drilled-pad clearances. The board has no fiducials; its coarsest SMD pad pitch makes this a low pick-and-place risk. Test pads expose 5 of the 9 nets.

The thermal analyzer could not estimate component temperatures because the design has no trustworthy dissipation data. The lifecycle audit was not run because manufacturer API credentials were unavailable. Gerber analysis does not apply because no fabrication outputs were requested or generated. The detailed Gate A model run supersedes the generic SPICE analyzer for the TIA loop. The generic run passed both voltage-divider checks and the decoupling check, but skipped the ideal-op-amp TIA case because its AC measurement failed.

## Acceptance boundaries

- **Documentation-level readiness:** Source-backed pin map, provisional candidate Rf/Cf, schematic-derived parts manifest, staged budget, correct component labeling, honest missing quantities/costs, exact reproduction and error/warning preservation.
- **Before prototype PCB fabrication:** Native KiCad ERC/DRC clean *for an actually routed board*; TIA/feedback layout and diode geometry independently reviewed, electrical simulation/analytical stability study recorded, manufacturer package/polarity verified, full required BOM and supply protected/available.
- **Before connecting hardware:** Owner confirms current-limited power supply/DMM, soldering/magnification, safe VIN/VUSB alteration, 5 V backfeed/3.3 V limits and measured power-up/down behavior, then performs BENCH-002's staged gates. A design file alone is **never** permission to energize.
- **Later issues (#4/#5/#7/#8):** measured full-speed HID IN successful completion, one actual Android renderer-to-optical response, five shared-hardware-timebase input captures, external USB correlation and complete uncertainty characterization. No such measurement is included in Issue #3.

**Current design sources:** [OPA320](https://www.ti.com/lit/ds/symlink/opa320.pdf), [TLV3501](https://www.ti.com/lit/ds/symlink/tlv3501.pdf), [SN74LVC1G17](https://www.ti.com/lit/gpn/sn74lvc1g17), [SFH 203 active manufacturer listing](https://ams-osram.com/products/photodetectors/photodiodes/osram-radial-t1-34-sfh-203), [SFH 203 package/pad drawing](https://media.digikey.com/pdf/Data%20Sheets/Osram%20PDFs/SFH_203_Ver1.3_12-23-15.pdf), [PJRC power notes](https://www.pjrc.com/store/teensy41.html), [Pololu #5383](https://www.pololu.com/product/5383), [Bourns MF-R050](https://www.digikey.com/en/products/detail/bourns-inc/MF-R050/259965).
