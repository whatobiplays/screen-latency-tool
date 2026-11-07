# Issue 3 prototype electronics — engineering draft

**Scope:** one provisional soldered optical sensor and a separately prototyped Teensy 4.1 external-power / USB-presence interface. This is an **editable design proposal**, not a production PCB, qualified electronics assembly, or permission to buy/fabricate/energize hardware. See [Issue #3](https://github.com/whatobiplays/screen-latency-tool/issues/3), [SPEC-X-001](../../docs/specs/SPEC-X-001-fixture-and-mechanics.md), [PHASE-001](../../docs/phases/PHASE-001-hardware-android-feasibility.md) and [ADR-0002](../../docs/adr/0002-prove-hardware-timing-before-contract-freeze.md).

## Deliverable boundaries

| File | Purpose | Readiness |
| --- | --- | --- |
| [Optical schematic](optical/one-sensor.kicad_sch) | Pin-numbered, one-channel photodiode → OPA320 TIA → TLV3501 comparator, bias, threshold, bypass, monitoring and test points | **Draft**; KiCad 10.0.8 ERC reports 0 errors and 0 warnings. The previous model-based analog run is not reproducible from the currently available external files; see simulation provenance. Physical electrical and optical acceptance remain unverified. |
| [Optical PCB](optical/one-sensor.kicad_pcb) | 50 × 44 mm two-layer routed prototype B.Cu GND zone, signal clearances, and input-loop void | **Editable native KiCad source.** Current KiCad 10.0.8 DRC (`--severity-all --schematic-parity`) reports 0 violations, 0 unconnected items and 0 schematic-parity issues on board SHA-256 `3f7ee13263f27410885c2ea0af3558b2fb27d6718b94dacfc55aec67fd4bbe88`. Five DRC categories remain `Ignore`; no per-item exclusions were added. `/EDGE` is routed entirely on F.Cu without signal vias. `/VREF` and `/THRESH` retain open plane-gap findings. The board has no mounting holes or modeled detector body. Its optical axis and physical fit remain unverified, and no Gerbers are approved. |
| [Active SFH 203 footprint](optical/ProtoOpto.pretty/Photodiode_SFH203.kicad_mod) and [library table](optical/fp-lib-table) | 2.54 mm lead pitch; pad 1 anode / pad 2 cathode; 2.0 mm lands and 1.0 mm drills; 5.90 mm maximum flange, 5.85 mm nominal flange and 6.40 mm courtyard | The official datasheet drawing confirms 2.54 mm lead spacing, and its recommended-pad artwork shows 2.0 mm pads with 1.0 mm holes. Physical lead fit, installed orientation, optical axis, cathode inspection and provisional boot fit still require review. |
| [Power/USB candidate schematic](power-usb/power-usb.kicad_sch) | Off-board adapter → MF-R050 PTC → Pololu 5383 reverse-current protector → Teensy VIN; isolated VUSB sensing through an Ioff-capable buffer with a 47 kΩ pull-down; diagnostic LED | **Draft wiring logic only.** The design has no routed carrier PCB or complete USB data wiring, and backfeed behavior remains unverified. Do not energize it until the unpowered continuity and isolation checks and the staged input checks below are complete. |
| [Derived components](BOM.csv) | One-channel component instances, candidate MPNs, provisional footprints, dated price snapshots, DNP field, and offboard items | **Not order-ready**; 44 rows represent 34 part identities, 31 purchasing MPNs and 37 populated units, one DNP resistor and six non-purchasable pad rows. Generated BOM subtotal is $138.89; Strategy A is $138.94. Current screen: 24 REVIEW / 7 HOLD. L721A and GST18U05-P1J are conditional REVIEW for owner purchase review; both XT30U J2 MPNs remain HOLD. |
| [Generator](generate.mjs) | Regenerates schematic sources, project libraries and CSV; the routed PCB is authoritative native KiCad source and is not emitted or overwritten | `--check` detects generated-file drift without writing. It does **not** invoke ERC/DRC or certify electrical behavior. |
| [KiCad projects](optical/one-sensor.kicad_pro), [power/USB](power-usb/power-usb.kicad_pro) | Open through the KiCad Project Manager rather than standalone editor mode | Project-specific `sym-lib-table` files reference the same [shared `Proto` symbol library](Proto.kicad_sym) via `${KIPRJMOD}/../Proto.kicad_sym` |
| [ERC report gate](erc-report-gate.mjs) | Parses locally generated ERC reports and fails if errors, warnings, or a filtered/incomplete report remain | Does not run KiCad; requires fresh post-edit reports with **both Errors and Warnings** included; **do not commit report exports** |

The [bench BOM](../../docs/bench/PROTOTYPE-BENCH-BOM.md) controls staged purchasing estimates, and the [bench guide](../../docs/bench/PROTOTYPE-BENCH-GUIDE.md) controls physical inspection and evidence gates. The manifest has 44 rows, 34 part identities, 31 purchasing MPNs and 37 populated units; one resistor is DNP and six PCB/test-pad rows are non-purchasable. The owner-selected connector set remains L721A at power/USB J1 with the direct nonlocking GST18U05-P1J design assumption, JST B4B-PH-K-S(LF)(SN) with CABLE-PH04 at optical J1, and CAB-14575/CAB-14574 at J4/J5; the XT30U J2 pair remains HOLD. L721A/P1J physical fit, retention, and installed fault behavior remain Stage-B checks. Adafruit 3878 replaces Adafruit 5030 and 592 at EXT4; no EXT5 row remains. The future eLabGuy setting is VCC CLOSED and shield-to-GND OPEN. The current procurement screen is **24 REVIEW / 7 HOLD**: Adafruit 3878 is REVIEW; the exact eLabBay product detail has no selected-variant price/stock/shipping or delivery terms (the maker's collection lists this model from $9.28), so its $9.28 Nov 5 quote remains historical and the item stays HOLD; and the Fluke TL75 is REVIEW for disconnected continuity subject to DMM compatibility and existing-lead inventory. J2 remains HOLD, S2 is unreleased, and `INITIAL_CURRENT_LIMIT = UNSET / PENDING REVIEW`. The five connector-related cost rows total $12.56; the unchanged PAC-2 breakout/leads pair is $49.27. The $138.89 generated BOM and $138.94 Strategy A totals are planning figures, not landed quotes. Stock is not reserved. Fabrication, harness materials, owner tools, shipping, taxes, duties, and tariffs remain excluded. `hardware/prototype` remains a provisional Issue #3 scope extension and does not freeze the production contract.


The footprint metadata references IPC-7351 Rev B and a third-party copy of IPC-SM-782A as historical design and library references. This candidate does not claim current IPC conformance.

## Optical functional design

The first reference population intentionally favors basic functional responsiveness and a **visible, adjustable output threshold**. The values below are starting candidates, **not demonstrated values at 200 cd/m²**, and may be replaced after model-based stability review and scope measurements.

| Instance | Candidate | Intent and restrictions |
| --- | --- | --- |
| D1 | **SFH 203**, active-production through-hole photodiode (Q62702P0955) | Pad 1 anode SUM; pad 2 cathode 3V3. Viewed component side, pad 1 is on the left and pad 2 on the right. The package drawing confirms 2.54 mm lead spacing; its recommended-pad artwork shows 2.0 mm lands and 1.0 mm holes, matching the footprint geometry. Physical lead fit, installed orientation, optical axis, boot fit and cathode-mark inspection remain Stage-B checks. The datasheet gives a 20° half angle (40° total acceptance cone) and identifies the package flat as cathode. The manufacturer-issued [OS-PDN-2026-027 notice (DigiKey-hosted copy)](https://mm.digikey.com/Volume0/opasdata/d220001/medias/docus/8902/OS-PDN-2026-027_ext-revised.pdf) dated 2026-03-19 sets LTO 2027-03-15 and LTD 2027-09-15. The distinct [SFH 203 P (Q62702P0942) datasheet, Version 1.5 (2026-03-18)](https://look.ams-osram.com/m/52b343f9b18e032b/original/SFH-203-P.pdf) identifies the discontinued predecessor. Owner policy excludes Q62702P0942 regardless of remaining stock; Q62702P0955 remains selected. |
| U1 | OPA320AIDBVR, SOT-23-5 | Pin 1 OUT=`TIA_OUT`, 2 V−=`GND`, 3 non-inverting=`VREF`, 4 inverting=`SUM`, 5 V+=`3V3`. No exposed `SUM` probe pad. |
| R1/R2/C1 | 18 kΩ upper / 10 kΩ lower / 1 µF bypass | DC reference `VREF` with local filtering. Source impedance, noise, ADC reference and rail coupling require later measurement. |
| R3/C2 | 47 kΩ / 2.2 pF C0G, in parallel | Provisional TIA feedback network; place **immediately beside U1**. Alternate 470 kΩ and 1 MΩ footprints/values can be explored only after assessing input capacitance and saturation. |
| U2 | TLV3501AIDBVR, SOT-23-6 | Pin 1 IN−=`TIA_OUT`, 2 V−=`GND`, 3 IN+=`THRESH`, 4 V+=`3V3`, 5 OUT=`CMP_OUT`, 6 **SHDN=`GND` (enabled low)**. 5 V-powered output must never drive the Teensy. |
| RV1/R7 | Bourns 3296W 10 kΩ, 1 MΩ fallback bias | Adjustable comparator `THRESH`, initially set near dark TIA output, not blindly at rail. Check wiper-pin mapping to installed part. |
| R4 | 2.2 MΩ `CMP_OUT`→`THRESH` **DNP** | Optional additional positive feedback/hysteresis, **not populated by default**; TLV3501 has intrinsic hysteresis. Any population and threshold shift require analysis and bench evidence. |
| R5 / R6 | 100 Ω series edge output / 1 kΩ monitor output | `EDGE` is 3.3 V push-pull digital pulse to a **provisional** Teensy capture input; `MON` is a **temporary one-channel bench diagnostic**, not a production multi-sensor cable commitment. |
| C3/C4 and C5/C6 | 100 nF+1 µF (OPA320); 100 nF+2.2 µF (TLV3501) | Bypass each IC to the routed PCB's ground plane with a short return path. |
| J1 | Owner-approved design selection: JST B4B-PH-K-S(LF)(SN), 4-pin 2.00 mm; mates with CABLE-PH04 | Design map: 1 `3V3`; 2 `GND`; 3 `EDGE`; 4 `MON`. Physical mating, clearance, retention, and cable continuity remain unverified. The final timing-only cable is not defined by this test header. |


J1 uses Connector_JST:JST_PH_B4B-PH-K_1x04_P2.00mm_Vertical. A normalized comparison with the KiCad 10.0.8 library footprint found matching line, rectangle, text, pad, and model geometry after removing UUIDs and pad-net assignments.

Both footprints use four through-hole pads on 2.00 mm pitch, with 0.75 mm drills and 1.2 × 1.75 mm lands. JST specifies 2.00 ±0.05 mm pitch and Ø0.7 +0.1/0 mm holes in its [PH series drawing](https://www.jst-mfg.com/product/pdf/eng/ePH.pdf). The [JST PH product page](https://www.jst-mfg.com/product/index.php?lang=2&search_product=B4B-PH-K-S&type=1) lists the B4B-PH-K-S header selected for the design; physical mating and clearance remain unverified.

The embedded footprint and installed library copy differ in instance and metadata fields: the board has `J1` / `SENSOR_IO` and procurement properties, while the library template has generic reference/value fields and KiCad library metadata. The normalized comparison found matching pad and graphic geometry. The `lib_footprint_mismatch` finding was reported on an earlier board revision; current DRC on board SHA-256 `3f7ee13263f27410885c2ea0af3558b2fb27d6718b94dacfc55aec67fd4bbe88` reports no such finding. Geometry comparison does not verify mating fit, cable clearance, or retention.

This geometry comparison does not verify mating fit, cable clearance, or retention; keep those items on HOLD.
At nominal 3.3 V and ignoring tolerances/op-amp offsets,

- `VREF = 3.3 × 10 / (18 + 10) = 1.1786 V`.
- `Vreverse,nominal ≈ 3.3 − 1.1786 = 2.1214 V`. This is a **candidate** SFH 203 bias, not validated under panel light, temperature or fault conditions.
- Ideal inverting TIA response is `ΔVout ≈ −Iphoto × 47,000 Ω`: **4.7 mV per 0.1 µA**, **47 mV per µA**. The actual photocurrent on a 200-nit display is **unknown**, so these numbers do not establish comparator signal-to-noise margin.
- The feedback-only RC product is `47 kΩ × 2.2 pF = 103.4 ns`; its ideal pole is `1/(2πRfCf) ≈ 1.54 MHz`. **This is not a measured front-end step time, stable-loop bandwidth, or <100 µs system proof**. Input diode, board and amplifier parasitics, phase margin, full-power bandwidth and optical PWM remain unqualified.

The comparator is intentionally arranged to switch as `TIA_OUT` **falls** below `THRESH` when illumination increases. The final measurements require independent per-sensor dark/bright 10/50/90% thresholds, separate monitor/ADC characterization and calibration revisions. A trimmer and one comparator **do not** fulfill the later five-sensor programmable-DAC design.

### Gate A simulation evidence

[`simulation/gate-a.mjs`](simulation/gate-a.mjs) runs the external TI macro models in ngspice and writes decks, raw data, logs and the provenance manifest outside Git.

Gate A clears `gate-a-summary.json` in a supplied `--out-dir` before checking required options or input files. A missing option or file therefore cannot leave an older run's summary behind.

- **Historical run (2026-10-31):** passed all **12/12** assertions. Its simulator and model details are retained below as historical evidence.
- **Latest historical run (2026-11-05):** its recorded summary reports **12/12** assertions (0 failed) across **35** ngspice invocations using the TI models documented below. The summary SHA-256 is `a766833f4096c1ba1cc85f08431261cae3004556c3a8e496f58df4035e5812f6`; the summary, decks, raw data, logs, external models, and simulator-provenance file are no longer available at their recorded temporary paths. This is not a rerun of the current candidate.

Gate A is model-based circuit analysis only. It does **not** validate PCB layout, board assembly, physical electrical behavior, or optical response.

**Historical response figures (2026-10-31):** The following results and sensitivity notes are retained from the historical run; they are not reported as output from the 2026-11-05 run.

| Check | Result |
| --- | ---: |
| DC transimpedance | 46.999988 kΩ |
| −3 dB bandwidth | 1.905 MHz |
| Nominal 1 µA step overshoot | 14.335% |
| Nominal 2% settling | 0.801 µs |
| 40 µA overload recovery 1% | 1.399 µs after pulse |
| Output noise through 2 MHz | 5.829 mV RMS |
| Intrinsic comparator hysteresis | about 6.06 mV |
| 3.3 V comparator high output | 3.280 V |

The input-capacitance sensitivity sweep covers 20 pF to 25 pF and `Cf` from 2.1 pF to 2.3 pF. Overshoot stays between 12.8% and 19.0%, with 2% settling between 0.61 µs and 0.94 µs. The derived second-order phase-margin estimate is roughly 49° to 54°, but that inference is not a measured loop stability result.

The 424 MΩ reverse-leakage resistance is a simulation assumption, not an SFH 203 datasheet limit or measurement. The 20 pF to 25 pF capacitance envelope is a bench-planning range for the package, boot and layout; neither bound has been measured. No defensible photocurrent at 200 cd/m² has been established, so comparator discrimination from a real display remains unaccepted.

The latest model provenance is recorded in `gate-a-summary.json` and the [reproducibility section](#simulation-provenance-and-reproduction). The five-node OPAx320-Q1 subcircuit matches Gate A's call, but this Q1 macro-model's suitability for the board's exact non-Q1 OPA320 orderable/electrical behavior is not proven. The TLV3501 PSpice transient model was run with `ngbehavior=ps`; this records the model/deck setup only. Manufacturer model archives stay outside the repository.

### Package and optical caution

The SFH 203 is placed at board coordinate `(105.5, 110.5)` with zero rotation. Viewed from the component side, pad 1, the anode, is on the left and connects to `/SUM`; pad 2, the cathode, is on the right and connects to `/3V3`. The optical axis runs perpendicular to the board through the body center at approximately `(106.77, 110.5)`, pointing away from the front copper. Check the installed part's small molded cathode mark during assembly.

The official SFH 203 Version 1.4 drawing specifies a 5.90 mm maximum flange, 5.85 mm nominal flange and an 8.5 ± 0.4 mm body length from the lead-seating plane. Printed page 9 confirms 2.54 mm lead spacing; printed page 10 recommends 2.0 mm pads and 1.0 mm holes, matching the footprint geometry. These dimensions do not establish physical lead fit, installed orientation, optical-axis alignment or boot fit. The footprint silkscreen shows the maximum flange, and the fabrication outline shows the nominal flange. Its 6.40 mm courtyard leaves 0.25 mm radial clearance beyond the maximum body. KiCad has no 3D model for D1, so the outlines document the detector envelope but do not prove mechanical fit.

The 50 × 44 mm board is an oversized bench characterization carrier, not a production carriage PCB. It has no mounting holes. Use a provisional external clamp that grips the board edges without loading the solder joints. The matte-black boot should have a 6.0 to 6.2 mm internal bore. A rear shoulder should rest on the photodiode flange, and the boot's opaque aperture should be centered on the optical axis. Print a fit coupon and check the installed part before relying on the assembly. This work does not define the final five-sensor fixture.

Preserve the B.Cu GND-zone clearance below D1, U1 and the direct feedback loop. The clearance reduces one parallel-capacitance path; it does not establish TIA stability or optical acceptance. As part of the physical review, compare the generic SOT-23-5 and SOT-23-6 pad numbering with the TI package drawings.

### Simulation provenance and reproduction

**Last recorded run (2026-11-05; not rerun for this candidate).** Its prior summary reports 12/12 assertions across 35 ngspice invocations and records the hashes below. The summary, deck, data and log files are no longer present at their recorded `/tmp` paths, and the external model and simulator-provenance files were not found in the searched locations. `node hardware/prototype/simulation/gate-a.mjs` cannot run without those required inputs. Gate A is therefore **NOT_RERUN** for this candidate; the previous result is historical evidence, not a current validation result.

- Simulator: `/opt/homebrew/bin/ngspice`, **ngspice-47**, Homebrew/homebrew-core prebuilt arm64 macOS bottle; binary SHA-256 `9ccc39c9b87a717db0244770596eeb01fc51e635f39dc32d77b3dc9ce4e17922`.
- Historical build evidence: `/tmp/slt-ngspice47-provenance/ngspice-build-provenance.json`, SHA-256 `3ad429334a28bb5ff8e5bd1b38eb041c6004cf853ab46f18e92dabbb3c271cfe`. The file is currently unavailable. The recorded Homebrew receipt identifies a prebuilt arm64 bottle; the complete source build command, actual configure arguments and compiler flags were not established, and local SBOM and install-receipt dependency versions disagreed.

The external TI model files remain outside Git:

- OPAx320-Q1 `OPAx320-Q1.LIB`, SHA-256 `7b69547828f702638d32e2fddc632defd6bb0d66c66cb1d2d2baca61ff471f35`, from [TI's OPA320-Q1 model page](https://www.ti.com/product/OPA320-Q1#design-tools-simulation), archive `SBOMBW3.ZIP` (product-page entry “OPAx320-Q1 PSpice Model”), model Final 1.6 dated 16AUG2021; Generic, suitable for all analysis types. Its five-node subcircuit matches Gate A's call, but suitability for the board's exact non-Q1 OPA320 orderable/electrical behavior is not proven.
- TLV3501 `tlv3501.lib`, SHA-256 `4e4345dc2929ee91a99a0b5437369c32b3817e9c8673a7ae90310d5485a81ba9`, from [TI's TLV3501 model page](https://www.ti.com/product/TLV3501#design-tools-simulation), archive `SBOMBU5.ZIP`, model revision 2.0 dated 2021-07-26 and generated for PSpice 17.4. Gate A ran it with `ngbehavior=ps`; this tests this model and deck, not general PSpice compatibility.

**Historical run (2026-10-31; retained for traceability).** This run used ngspice-45.2, whose binary reported `ngspice-45`; its SHA-256 was `a0032f9aad4216ee87ddbc27fab7479d2375f267fef41c5292532df253230d93`. Surviving build records showed `--without-x --with-readline=no --with-fftw3=no --disable-openmp --disable-klu --disable-debug`, compiler flags ending in `-std=gnu11`, and `XSPICE features included`. The complete historical `make` command and any `EXTRA_CFLAGS` value are absent from the surviving `make.log`.

- Historical OPAx320-Q1 model `OPAx320-Q1.LIB`, SHA-256 `a272838427e8988bdc662887238d2698d23feb0d39400a2c4c4a929f10c29460`, archive `OPAx320_PSpice_RevG.zip`, Final 1.7 dated 17 June 2022.
- Historical TLV3501 model `tlv3501.lib`, SHA-256 `4e4345dc2929ee91a99a0b5437369c32b3817e9c8673a7ae90310d5485a81ba9`, archive `TLV3501_PSpice.zip`, revision 2.0 dated 26 July 2021, generated for PSpice 17.4; run with `ngbehavior=ps`.

Download the archives from the TI pages and use them under TI's terms. Do not commit or redistribute the archives or extracted model files. Reproduce the recorded sweep from the repository checkout with this form, changing only the external paths:

```sh
node hardware/prototype/simulation/gate-a.mjs   --ngspice /path/to/ngspice   --opa-model /path/to/OPAx320-Q1.LIB   --tlv-model /path/to/tlv3501.lib   --simulator-provenance /path/to/ngspice-build-provenance.json   --out-dir /tmp/slt-issue3-gate-a
```

The provenance file requires non-empty `sourceVersion`, `sourceIdentity`, and `compilerFlags` strings. `simulatorSha256` must match the ngspice executable. `configureArguments` and `limitations` are string arrays; either may be empty when there is nothing to record. `featureEvidence` must be a non-empty array of objects, each with a non-empty `feature` string and a boolean `enabled` value. Each `buildEvidence` entry must name an existing file and contain a non-empty `observations` array of non-empty strings. Before running ngspice, `gate-a.mjs` validates these fields, hashes the evidence files, and records each deck invocation in `gate-a-summary.json`.

**No instruction here authorizes fabrication of the draft PCB.** First route and validate a revised PCB after schematic review and analog modeling, then obtain explicit prototype-fabrication approval. Final professionally assembled PCB production remains after the Phase-1 feasibility gate.

## Power and VBUS behavior

The power schematic represents connectivity between off-board modules and a small soldered control assembly, not a fabricated carrier schematic. The Teensy 4.1 USB device port D+/D− and ground remain in a standard, intact USB cable; they are not improvised breadboard wires or PCB traces.

The generated offboard power/USB design uses AMASS gold/yellow `XT30U-F.G.Y` female connector at protected **source** J2 and `XT30U-M.G.Y` male connector at Teensy VIN/GND **load** J6. Numeric references avoid KiCad treating alphabetic suffixes as multi-unit connector references. The pair is mechanically polarized and friction-retained without a positive locking latch. Secure mounting and strain relief are required, and polarization does not prevent swapped solder wires. The schematic logical pins do not establish the physical pole map. Before any separately approved Stage-B assembly, verify received molded marks and each lead-to-cup connection with a DMM while de-energized; never infer polarity from lead appearance or color. Insulate all four rear solder joints and provide supported transitions to suitably sized fixed wiring. These cable-mount parts have no PCB footprint in this design. AMASS 2026V1 lists both exact gold/yellow MPNs but gives no explicit manufacturer lifecycle declaration. The 2023V2 current figures apply only under the stated 16-AWG test configurations. Procurement, wire fit, solder joints, support, retention, and fault limits remain unresolved; J2 stays HOLD and S2 unreleased.

**Power connection sequence for separately approved Stage-B work:**


- Connect the fixed-output 5 V/3 A adapter to the `MF-R050` resettable PTC, then connect the PTC to the `Pololu #5383` reverse-voltage and reverse-current protector. Connect the protector's VIN, VOUT and GND pins correctly, and connect its output to `Teensy VIN`. The PTC is a **slow-acting thermal fault limiter**. The Pololu protector does **not** provide a precision adjustable current limit or isolate USB VBUS.
- Before external VIN and USB host VBUS are used together, sever and inspect the Teensy's underside **VIN–VUSB trace**. With external VIN applied by itself, verify that the isolated `VUSB` pad has no voltage. After completing the unpowered checks below, perform the staged VBUS sensing and backfeed measurements. Keep USB `VUSB` isolated from VIN.
- Connect isolated `VUSB_RAW` through 1 kΩ resistor `R1` to `VUSB_BUFFER_IN`, which connects to pin 2 (A) of the TI `SN74LVC1G17DBVRG4`. A 47 kΩ resistor, `R3`, connects `VUSB_BUFFER_IN` to GND and pulls the buffer input low when VBUS is disconnected. At nominal 5 V VBUS, the divider predicts about **4.896 V at the buffer input** and **0.104 mA of VBUS draw**, excluding leakage and tolerances. Bypass pin 5 locally at the 3.3 V VCC supply. Connect pin 3 to GND and pin 4 (Y), `VBUS_PRESENT`, to a provisional GPIO; leave pin 1 unconnected. The device specifies a 5.5 V-tolerant input and Ioff power-down behavior. Physical leakage and backfeed, actual input thresholds and hotplug behavior remain unverified.

**USB chain and compatibility.** The **owner-selected one-sensor design** is Android USB-C host → [Adafruit 3878](https://www.adafruit.com/product/3878) Type-C-to-Micro-B cable → eLabGuy Micro-B female → male pass-through → Teensy 4.1 Micro-B **device** port. Adafruit documents the 0.9 m USB 2.0 cable's 5.1 kΩ CC pull-down. The historical Adafruit 5030 Type-C-to-Standard-A adapter plus Adafruit 592 Standard-A-to-Micro-B cable was superseded as a design selection, not found defective or discontinued. Removing that stack avoids its previously documented USB-IF adapter/cable compatibility caveat, but does not prove Android host role, VBUS delivery, enumeration, data integrity or timing. See the [USB Type-C specification](https://www.usb.org/sites/default/files/USB%20Type-C%20Spec%20R2.0%20-%20August%202019.pdf).

#### USB VBUS topology decision

**Owner-approved Stage-A USB configuration (2026-11-05):** Adafruit 3878 Type-C-to-Micro-B 0.9 m cable (listed $3.50, In stock at Adafruit on 2026-11-05) into the eLabGuy Micro-B female receptacle, then its male plug into the Teensy 4.1 Micro-B device port. The eLabGuy **VCC jumper is specified CLOSED** to convey host VBUS to the physically isolated Teensy `VUSB_RAW` sense pad; the separate **shield-to-GND jumper is specified OPEN**, without assuming shield continuity elsewhere. These are approved *design selections*, not permission to install jumpers, plug USB, apply power or purchase parts. 3878 does not have an explicit manufacturer Active lifecycle declaration; use the prototype-only conditional review in SPEC-X-001, retain the factual lifecycle marker `UNVERIFIED_BEFORE_ORDER` and verify exact sourceability and component fit. The former 5030/592 pair cost $3.90; this cable costs $3.50, a $0.40 reduction before shipping and tax. [Adafruit 3878](https://www.adafruit.com/product/3878).

The [eLabGuy manufacturer datasheet](https://www.elabguy.com/datasheet/USB-uBM-uBF-V1A%20Rev1.1.pdf) shows D+/D− connected directly and VCC open between connectors by default. The owner selected its VCC jumper CLOSED and its optional shield-to-GND jumper OPEN **for the future assembly design only**. Prior to any separately approved S4/G4 connection, verify the Teensy VIN/VUSB physical cut, unpowered VCC and ground mapping, module assembly/retention, actual shield path and the powered VBUS/backfeed limits independently. No connected USB behavior has been measured.

A host-side VBUS tap would sense the source side while leaving device-side VBUS unresolved and would add a new route. Preserve the owner-selected cable and jumper configuration in the BOM and schematic. Do not install jumpers or connect USB before a separate Stage-B release. After that release, inspect the received jumper states; check VBUS, D+, D−, ID, GND, and shield separately while unpowered; verify the VIN–VUSB cut; then run approved backfeed, host-role, device-port, enumeration, speed, fit, and signal-integrity checks. Keep D+/D− on intact cable or breakout hardware, never breadboard wires.

The candidate diagnostic LED is a **Kingbright WP7113ID**, a 617 nm red through-hole LED with a listed typical forward voltage of **1.9 V**. From a verified 3.3 V GPIO through the nominated 1 kΩ resistor, its estimated current is `(3.3−1.9)/1000≈1.4 mA`. Check the GPIO drive limit, LED polarity, actual forward voltage and operating range before connecting it. The LED circuit has not been tested on hardware.

For first energization, use a separate current-limited bench supply or electronic limiter. The fixed 3 A wall adapter alone is not sufficient. Do not connect Android and external VIN together until independent circuit review and the staged measurements in BENCH-002 permit it. Confirm that a DMM, soldering tools and a current limiter are available; an optical meter or 3D printer cannot replace them.

A one-sensor prototype may tentatively use the Teensy's own 3.3 V regulated rail, **subject to the manufacturer's additional-load budget** (PJRC recommends ≤250 mA on 3.3 V for external circuitry). It is **not** a production analog rail or validation of the five-sensor current/noise budget. Reset, brownout, hotplug and fault signaling must be tested before declaring electrical feasibility.

No electrical energization, backfeed measurement or physical power acceptance has been performed. Complete these checks before applying voltage:

1. Disconnect every power source and allow the rails to discharge. Before using resistance mode, verify that accessible rails measure zero volts.
2. Inspect the Teensy's underside VIN–VUSB trace cut and confirm the pads are separated. Verify the barrel-jack polarity, confirm that the PTC is in series, and check the Pololu VIN/VOUT/GND orientation, bypass capacitors, D1 cathode mark and pin-1 marks on all ICs.
3. With all sources disconnected, measure PTC continuity and confirm that VIN is isolated from VUSB. Check that there is no 5 V-to-GND short or hard 3.3 V-to-GND short. Verify continuity through the VBUS divider to `VUSB_BUFFER_IN`, and confirm that no 5 V path reaches the Teensy logic header. Record the meter range. Finite semiconductor resistance is expected; a hard short is not.
4. With USB disconnected, use an adjustable current-limited bench supply set to 5.0 V. Choose the current limit only after reviewing a load budget; this document does not specify a numeric setting. Verify that 3.3 V is stable, VUSB remains near 0 V, the buffer output is low, supply current is expected and no component heats up.
5. After the owner selects a VBUS topology and a separate Stage-B release authorizes it, apply controlled VBUS by itself. At nominal 5 V VBUS, verify about 4.896 V at the divider input; confirm the unpowered buffer output is high impedance, the 3.3 V rail does not rise, and the buffer does not backfeed power.
6. Only after those checks pass, apply external power and USB together while monitoring VUSB, VIN, 3.3 V, current, and reset behavior. Disconnect immediately if backfeed, an unexpected rail rise, overheating or unstable current occurs.

The Pololu 5383 provides reverse-voltage and reverse-current protection and accepts 4 to 60 V input. It does not limit current or clamp overvoltage. The MF-R050 is a slow-acting PTC rated for 0.5 A hold and 1 A trip at 23 °C; its hold current falls to about 0.41 A at 40 °C. PJRC specifies 5 V on VIN when USB is disconnected, 3.3 V logic and digital inputs that are not 5 V tolerant. PJRC recommends an external load of no more than 250 mA on the 3.3 V pin. At 5.25 V VBUS, the buffer input remains below its 5.5 V recommended limit. Physical no-backfeed behavior remains untested.

### Project-local ERC power-source declarations

The revised generated schematics contain **two virtual `Proto:PWR_FLAG` symbols each**. The flags mark `3V3` and `GND` as supplied by external connections: **optical J1 pins 1/2**, and **power/USB J4 pins 1/2 from the Teensy external 3.3 V rail and shared ground**. Their pins have KiCad `power_out` electrical type solely so ERC can recognize externally sourced rails; they are excluded from the physical BOM/PCB and **must not be interpreted as power regulators or electrical validation**. They do not bridge VIN to VUSB or make the host USB connection safe.

A custom, project-local KiCad symbol library now exactly matches the embedded `Proto` symbol definitions and is registered by each project's `sym-lib-table`. This replaces the prior missing-library warnings without ignoring symbol checks. The actual 5 V regulator, protection, startup sequencing and partial-power-down behavior remain subject to independent design and owner bench review.

## Reproducibility and validation commands

Run these commands **on the actual repository checkout with Node.js and KiCad installed**. On 2026-11-07, KiCad 10.0.8 ERC reports were generated from optical schematic SHA-256 `a4b7185e4046e1e98c51c2a31939fae99246ea0aa45d6bf09f145577e745c5d7` and power/USB schematic SHA-256 `7821e62ef52b7ab9f884bb14fa4a57be5c1672338fad9f21865c103f2f35d372`. Each report contains 0 violations. The optical JSON at `/tmp/slt-optical-erc.json` has SHA-256 `4a01d56f8ad3963e487971b04e80cb3a7ca139053048fa3a0af9f6f9a4b4b7e2`; the power/USB JSON at `/tmp/slt-power-erc.json` has SHA-256 `115e2e3a9af4d227cdd886b609b9fcb8a9efd90c75a24ce145671b452193c038`. Each lists four ignored checks: single-use global labels, four-way junctions, SPICE-model issues, and footprint-filter mismatches. These remain review obligations.

Historical saved-board DRC on board SHA-256 `5727e81accea582dbb818c41f6af7d25ebd05c2e0b4c4f1e7f6e51eb9764ab9c` reported one J1 `lib_footprint_mismatch` warning: the 2026-11-04 report SHA-256 was `eac9ffb745a30af64d452a6cacd9eb5d23ca0bbed30c82e7cf8b3036183da74f`, and the 2026-11-05 rerun SHA-256 was `4cfa67db33afb0e25e836b0c7981ffd3a71d2fccd88f1b3423fe92a991c2a87b`. Current KiCad 10.0.8 DRC on optical board SHA-256 `3f7ee13263f27410885c2ea0af3558b2fb27d6718b94dacfc55aec67fd4bbe88`, with `--severity-all --schematic-parity`, reports 0 violations, 0 unconnected items, and 0 schematic-parity findings; JSON report SHA-256 is `16cebc366910f10d2d1f1760f3cefa0ae7b918c6feb9357c27f04eb3d261e5b2`. Five project categories remain `Ignore`: `missing_courtyard`, `track_not_centered_on_via`, `tuning_profile_track_geometries`, `footprint_filters_mismatch`, and `footprint_type_mismatch`; no per-item exclusions were added. The earlier board hash `9b983eee2e39ced97e929a83c3d588c071b794c91568f47c300efb125895acb9` had seven zone-fill errors and the J1 warning before the refill. Current checks pass 389 structural assertions, and `generate.mjs --check` reports `stale: 0`. These checks do not establish electrical safety or physical fit. Keep raw reports outside the repository and record remaining gates in [Issue #3](https://github.com/whatobiplays/screen-latency-tool/issues/3).

A before-and-after structural comparison found no changes to component placement, tracks, or zone boundaries. The refill changed generated zone-fill polygons. KiCad also serialized J1's unchanged 270° rotation as -90°; the structural checker accepts both values.

```sh
node hardware/prototype/generate.mjs --check
node hardware/prototype/check.mjs
node hardware/prototype/erc-report-gate.mjs --selftest

# macOS: KiCad does not normally add its CLI to your shell PATH.
# If installed elsewhere, adjust KICAD_CLI to the executable inside KiCad.app.
KICAD_CLI="/Applications/KiCad/KiCad.app/Contents/MacOS/kicad-cli"
if [ ! -x "$KICAD_CLI" ]; then
  printf 'KiCad CLI not found at %s\n' "$KICAD_CLI"
else
  "$KICAD_CLI" version &&
  # Reproduce the saved-board check without refilling zones.
  "$KICAD_CLI" pcb drc --severity-all --schematic-parity --exit-code-violations \
    --format report --output /tmp/slt-optical-drc.rpt \
    hardware/prototype/optical/one-sensor.kicad_pcb &&
  "$KICAD_CLI" sch erc --severity-error --severity-warning \
    -o /tmp/slt-optical-erc.rpt hardware/prototype/optical/one-sensor.kicad_sch &&
  "$KICAD_CLI" sch erc --severity-error --severity-warning \
    -o /tmp/slt-power-erc.rpt hardware/prototype/power-usb/power-usb.kicad_sch &&
  "$KICAD_CLI" sch erc --severity-all --format json \
    --output /tmp/slt-optical-erc.json hardware/prototype/optical/one-sensor.kicad_sch &&
  "$KICAD_CLI" sch erc --severity-all --format json \
    --output /tmp/slt-power-erc.json hardware/prototype/power-usb/power-usb.kicad_sch &&
  node hardware/prototype/erc-report-gate.mjs \
    /tmp/slt-optical-erc.rpt /tmp/slt-power-erc.rpt &&
  # Export both board sides without refilling zones.
  "$KICAD_CLI" pcb export svg --output /tmp/slt-optical-front.svg \
    --layers F.Cu,F.Silkscreen,Edge.Cuts,F.Fab --page-size-mode 2 \
    --fit-page-to-board --exclude-drawing-sheet --mode-single \
    hardware/prototype/optical/one-sensor.kicad_pcb &&
  "$KICAD_CLI" pcb export svg --output /tmp/slt-optical-back.svg \
    --layers B.Cu,B.Silkscreen,Edge.Cuts,B.Fab --page-size-mode 2 \
    --fit-page-to-board --exclude-drawing-sheet --mode-single \
    hardware/prototype/optical/one-sensor.kicad_pcb
fi
```

The archived DRC reports were generated without `--refill-zones`; the command above preserves that behavior and does not rewrite the native board. An optional new refill check is a separate analysis and cannot reproduce the archived report hash. Run it against a disposable board copy, not the authoritative PCB:

```sh
KICAD_CLI="/Applications/KiCad/KiCad.app/Contents/MacOS/kicad-cli"
if [ ! -x "$KICAD_CLI" ]; then
  printf 'KiCad CLI not found at %s\n' "$KICAD_CLI"
else
  refill_dir="$(mktemp -d /tmp/slt-optical-drc-refill.XXXXXX)"
  cp hardware/prototype/optical/one-sensor.kicad_pcb "$refill_dir/one-sensor.kicad_pcb"
  "$KICAD_CLI" pcb drc --severity-all --exit-code-violations --refill-zones \
    --format report --output "$refill_dir/optical-drc-refilled.rpt" \
    "$refill_dir/one-sensor.kicad_pcb"
fi
```

To deliberately regenerate generated sources and BOM, run `node hardware/prototype/generate.mjs` without `--check`. It does not write `one-sensor.kicad_pcb`; the routed board is native KiCad source and must be edited in KiCad. Inspect the schematic and PCB in KiCad before fabrication. No Gerber or CPL output is approved.

**Current local checks:** `generate.mjs --check` reports `stale: 0`, and `check.mjs` passes 389 checks. KiCad ERC reports no violations in either schematic. Optical PCB DRC and schematic parity report no violations or unconnected items. KiCad XML exports preserve component references, values, footprints, and pin-to-net mappings from the pre-edit snapshots (optical: 23 components and 9 nets; power/USB: 14 components and 12 nets). Reverting the four KM MPN strings, related capacitor source URLs, and J1 datasheet URL to their PR-base values restores the optical PCB byte-for-byte. Copper, pads, nets, placement, and mechanical geometry match the pre-edit board. Five DRC categories and four ERC checks remain ignored. Full-board 2D front and back SVGs were rendered and visually reviewed for board SHA-256 `3f7ee13263f27410885c2ea0af3558b2fb27d6718b94dacfc55aec67fd4bbe88`. This review does not establish package-body or enclosure clearance, connector mating, cable routing, optical-axis alignment, electrical safety, or performance.


### Critical pin and footprint cross-check

I checked the current KiCad XML netlists against the selected package drawings and manufacturer data sheets. The schematic pins match the documented package assignments. This source review does not qualify soldering, orientation, physical fit, or electrical behavior.

| Part | Current schematic mapping and footprint | Manufacturer cross-check | Remaining boundary |
| --- | --- | --- | --- |
| OPA320 U1 | Pin 1 OUT=`TIA_OUT`; 2 V−=`GND`; 3 +IN=`VREF`; 4 −IN=`SUM`; 5 V+=`3V3`. `Package_TO_SOT_SMD:SOT-23-5`. | [TI OPA320 data sheet](https://www.ti.com/lit/ds/symlink/opa320.pdf), DBV-5 pin assignment. | Board assembly and analog behavior remain unverified. |
| TLV3501 U2 | Pin 1 −IN=`TIA_OUT`; 2 V−=`GND`; 3 +IN=`THRESH`; 4 V+=`3V3`; 5 OUT=`CMP_OUT`; 6 SHDN=`GND`. `Package_TO_SOT_SMD:SOT-23-6`. | [TI TLV3501 data sheet](https://www.ti.com/lit/ds/symlink/tlv3501.pdf), DBV-6 pin assignment. | Shutdown, startup, and comparator behavior have not been measured. |
| SN74LVC1G17 U1 | Pin 1 NC; 2 A=`VUSB_BUFFER_IN`; 3 GND; 4 Y=`VBUS_PRESENT`; 5 VCC=`3V3`. No carrier footprint is selected; the BOM marks it offboard/non-PCB. | [TI SN74LVC1G17 data sheet](https://www.ti.com/lit/ds/symlink/sn74lvc1g17.pdf), DBV-5 pin assignment. | Carrier, local bypass placement, leakage, and physical USB behavior remain open. |
| SFH 203 D1 | Pin 1 A=`SUM`; pin 2 K=`3V3`. `ProtoOpto:Photodiode_SFH203`; the custom footprint uses the data sheet's 2.54 mm lead pitch and recommended pad drawing. | [ams OSRAM SFH 203 data sheet](https://look.ams-osram.com/m/67e1a90044b03ee9/original/SFH-203.pdf), exact order code `Q62702P0955`, package and pad drawings. | Received-part fit, flange contact, optical alignment, and physical calibration remain unverified. |
| Bourns 3296W RV1 | Pin 1=`3V3`; 2 wiper=`THRESH`; 3=`GND`. `Potentiometer_THT:Potentiometer_Bourns_3296W_Vertical`. | [Bourns 3296 data sheet](https://www.bourns.com/docs/Product-Datasheets/3296.pdf), terminal order and package. | Mechanical clearance and threshold range require physical review. |

The power/USB netlist now exports 14 uniquely referenced components without an annotation warning after the connector references changed from `J2A`/`J2B` to `J2`/`J6`. The electrical nets are unchanged. No carrier footprint is selected for the SN74LVC1G17, so its assembly remains unresolved.

The default macOS CLI path is `/Applications/KiCad/KiCad.app/Contents/MacOS/kicad-cli`; check it with `ls -l /Applications/KiCad/KiCad.app/Contents/MacOS/kicad-cli`. For a nonstandard installation, locate `kicad-cli` with `find /Applications "$HOME/Applications" -name kicad-cli -type f 2>/dev/null`. Invoke the app-bundled binary directly; do not assume `kicad-cli` is on `PATH`. The ERC commands request both error and warning severities and stop report validation if KiCad fails.

`generate.mjs --check` compares generated CAD outputs with their sources byte for byte. It detects drift but does not check KiCad correctness. The independent `check.mjs` structural checker parses the saved S-expressions and verifies project names, selected pin-to-net links, symbol/PCB UUID correspondence, pad numbers, DNP metadata, obvious overlaps between different-net pads, component counts, both copper layers, ground zone, tracks, vias, and routed net names. It also verifies `/EDGE` remains on F.Cu with no signal vias and both SFH 203 footprint copies show the maximum flange and 6.40 mm courtyard. The current run passed **389 checks with zero failures**, including J1 JST pad geometry and EDGE/MON fanout assertions, J2/J6 logical-pin and offboard-state assertions, and J5 preservation checks. Counts are 23 optical symbols, 14 power/USB symbols, 23 PCB footprints, 51 pads, and 44 BOM rows. It also checks the custom symbol library and both KiCad project manifests. These checks do not replace native KiCad ERC/DRC, electrical safety checks, or 3D visual inspection. Verify the vendor package revision and all symbol and pad numbers against manufacturer documentation.

The structural checker reads BOM identity, source, price, and lifecycle fields. It checks J4 pin 1=`3V3`, pin 2=`GND`, and pin 3=`VBUS_PRESENT`. It rejects `DISCONTINUED`, `EOL`, `NRND`, `LAST_TIME_BUY`, and `OBSOLETE` statuses. BOM comments remain descriptive evidence.

The **historical 2026-11-05** SPICE run was recorded as **PASS WITH LIMITATIONS** for supplied TI macro models and the stated sensitivity envelope. Its model files, simulator-provenance file, and output artifacts are unavailable; Gate A was **NOT_RERUN** for this candidate. It does not establish physical stability, real photocurrent under panel illumination, or comparator discrimination. No software calculation substitutes for bench measurements.

### Gate C analysis outcome

Current KiCad 10.0.8 checks and retained analyzer results:

| Check | Result |
| --- | --- |
| Optical and power/USB ERC | KiCad 10.0.8 ERC JSON reports contain no errors or warnings. Optical `/tmp/issue3-final-optical-erc.json` SHA-256: `b5a12a39d3fec4933807c29113a25d4b52401dfe8f107eeac72e4b30fac50edc`; power/USB `/tmp/issue3-final-power-erc.json` SHA-256: `2f144b16abba5db094683e9c39a270156a12eaa5964f3043f2b4f322d45b5dd8`. Each report lists four ignored checks. |
| Generator and structural checker | Scoped `node hardware/prototype/generate.mjs --write-power-usb-only` followed by `--check`: `stale: 0`. `node hardware/prototype/check.mjs`: 389 checks, 0 failures; 23 optical symbols, 14 power/USB symbols, 23 PCB footprints, 51 pads and 44 BOM rows. |
| Native PCB DRC and schematic parity | KiCad 10.0.8 `--severity-all --schematic-parity` on board SHA-256 `3f7ee13263f27410885c2ea0af3558b2fb27d6718b94dacfc55aec67fd4bbe88`: no violations, unconnected items, or schematic-parity findings. JSON report `/tmp/issue3-final-optical-drc.json` SHA-256: `16cebc366910f10d2d1f1760f3cefa0ae7b918c6feb9357c27f04eb3d261e5b2`. Five project `Ignore` categories remain; no per-item exclusions were added. |
| Full PCB analysis | Historical 2026-11-05 report for predecessor board SHA-256 `9e1d17c113513dc6a83d4a4c4f935cb8eb841978bf7ce502709861c79c1f18fe`: 0 errors, 0 warnings and 12 informational findings; report SHA-256 `d99553d5c397b68729cd5d0e5ea5adbc23ce5b165111f400b230154e1a1505e4`. It was not rerun on the current board. |
| Schematic/PCB cross-analysis | Historical 2026-11-05 result for predecessor board SHA-256 `9e1d17c113513dc6a83d4a4c4f935cb8eb841978bf7ce502709861c79c1f18fe`: 0 findings; report SHA-256 `6e77d4d63b9d542ae3a2d39a61fb3cd480f62d87acc76ad52cc69e7cdffd6f4e`. It was not rerun on the current board. |
| EMC analysis | Historical run `20261105T225212Z-4a83ac`, bound to predecessor board SHA-256 `9e1d17c113513dc6a83d4a4c4f935cb8eb841978bf7ce502709861c79c1f18fe`: 8 findings (2 errors, 3 warnings and 3 informational) and an 80.5 heuristic score. Trust is low: 2 deterministic findings, 6 heuristic findings and 0% provenance coverage. Report SHA-256 `1fa3564ecaceb5fb88edbf548d3a768821356577c1759d9656e8e1a2b75746cb`. No EMC run was made on the current board; the design review is not compliance evidence. |
| Structural checker | `node hardware/prototype/check.mjs` passed 389 checks with 0 failures. |
| Thermal analysis | Skipped because the design has no trustworthy component-dissipation data; report SHA-256 `64a7c30391fb95e443934049b442bfeb6558cbbfab25ca1c7565c65b3c749c32`. |
| Gate A SPICE | Historical 2026-11-05 run was recorded as **PASS WITH LIMITATIONS**; its prior summary reports 12/12 assertions across 35 ngspice invocations. External inputs and output files are unavailable. Gate A is **NOT_RERUN** for this candidate. |
| Native full-board 2D layout review | Front and back KiCad SVGs for board SHA-256 `3f7ee13263f27410885c2ea0af3558b2fb27d6718b94dacfc55aec67fd4bbe88` were reviewed. Package-body and enclosure clearance, connector mating and retention, cable routing, and detector optical alignment remain unverified. |

Fresh KiCad JSON reports are stored outside Git at `/tmp/issue3-final-optical-erc.json`, `/tmp/issue3-final-power-erc.json`, and `/tmp/issue3-final-optical-drc.json`; their hashes are recorded above. KiCad CLI exited 0 for all three commands; the host emitted Fontconfig configuration warnings. The historical analyzer and EMC raw files are not in the repository or the previously cited `/tmp/issue3-stage-a-20261105-final-analysis/` directory; only their recorded hashes, predecessor-board binding, and dispositions remain. The optical board SHA-256 is `3f7ee13263f27410885c2ea0af3558b2fb27d6718b94dacfc55aec67fd4bbe88`. KiCad 10.0.8 DRC used `--severity-all --schematic-parity`; it did not refill zones or write the board file.

The pre-refill report for board hash `9b983eee2e39ced97e929a83c3d588c071b794c91568f47c300efb125895acb9` had three clearance errors, two hole-clearance errors, and two solder-mask-bridge errors. J1 pad 1 (/3V3) had 0.1755 mm copper clearance against the 0.2000 mm rule. J1 pads 3 (/EDGE) and 4 (/MON) each had 0.0000 mm copper clearance and 0.0000 mm hole clearance against the 0.2500 mm rule; both also had a rear solder-mask bridge. The J1 warning says its JST footprint differs from the Connector_JST library copy. The saved-board post-refill report and earlier in-memory refill report both have zero unconnected items and zero schematic-parity findings.

The latest EMC report, run `20261105T225212Z-4a83ac`, is historical and bound to predecessor PCB SHA-256 `9e1d17c113513dc6a83d4a4c4f935cb8eb841978bf7ce502709861c79c1f18fe`. It reported low-trust findings: two deterministic and six heuristic, with 0% provenance coverage. The current candidate changes capacitor MPN/source metadata and J1's datasheet URL; its copper and physical geometry match the predecessor. No EMC analysis was run on the current file hash. Report SHA-256: `1fa3564ecaceb5fb88edbf548d3a768821356577c1759d9656e8e1a2b75746cb`; PCB analysis JSON SHA-256: `d99553d5c397b68729cd5d0e5ea5adbc23ce5b165111f400b230154e1a1505e4`. This report is design-review input, not compliance evidence.

The /EDGE route remains on F.Cu without signal vias; the scan reported no finding for it. The coverage percentages below are analyzer estimates of opposite-layer copper presence. They do not establish that sampled copper is GND, measure return impedance, or demonstrate EMC performance. The board has one copper zone, a B.Cu GND plane; /VREF and /THRESH also route on B.Cu. Confidence below separates saved-board facts from the analyzer's heuristic interpretation and unmeasured physical effect.

| Finding ID | Saved-board evidence | Confidence | Disposition |
| --- | --- | --- | --- |
| emc:GP-001:/vref | The report estimates 59.6% opposite-layer coverage over 83.4 mm. The route has 73.38 mm on B.Cu and 10.01 mm on F.Cu. | High for route layers and length; low for the heuristic percentage and EMC effect. | Stage-A documentation/evidence gap; no defect is established. The route layer split and plane clearances are saved-board facts, while the coverage score is heuristic. Keep the risk open for owner layout review; do not add vias or bridge the GND plane based on this report alone. |
| emc:GP-001:/thresh | The report estimates 75.9% opposite-layer coverage over 79.1 mm. The route has 25.53 mm on B.Cu and 53.59 mm on F.Cu. | High for route layers and length; low for the heuristic percentage and EMC effect. | Stage-A documentation/evidence gap; no defect is established. The route layer split is a saved-board fact, while the coverage score and EMC effect are heuristic. Keep the risk open for owner layout review; do not change routing based on this report alone. |
| emc:GP-001:/sum | The report estimates 80.0% opposite-layer coverage over 21.4 mm. The route is on F.Cu above the documented B.Cu GND-zone clearance for the input loop. | High for the saved route and documented design intent; low for the heuristic percentage and EMC effect. | Stage-A documentation/evidence gap; no defect is established. The GND void is intentional around the high-impedance photodiode/TIA input, and its measured stability/EMC effect is unknown. Preserve the void pending owner review; do not fill or bridge it automatically. |
| emc:RP-001:/vref | Four signal vias cross between B.Cu and F.Cu. Their nearest GND vias are 2.42, 4.12, 4.30, and 7.00 mm away, beyond the report's 1.0 mm threshold. | High for via coordinates and calculated distances; low for EMC impact. | Stage-A documentation/evidence gap; no defect is established. The via locations and distances are exact layout facts, but the 1.0 mm proximity threshold is heuristic and no return-path or emissions measurement supports a correction. Keep open for owner risk disposition; do not add vias solely to satisfy this rule. |
| emc:RP-001:/thresh | Two signal vias cross between B.Cu and F.Cu. Their nearest GND vias are 7.37 and 14.24 mm away, beyond the report's 1.0 mm threshold. | High for via coordinates and calculated distances; low for EMC impact. | Stage-A documentation/evidence gap; no defect is established. The via locations and distances are exact layout facts, but the 1.0 mm proximity threshold is heuristic and no return-path or emissions measurement supports a correction. Keep open for owner risk disposition; do not add vias solely to satisfy this rule. |
| emc:IO-001:j1 | The saved board has no ferrite bead, common-mode choke, or ESD device within 25 mm of J1. | High for board placement; low for the rule's emissions or 5 µA implications. | Stage-A documentation/evidence gap; no defect is established. The board has no nearby filtering/ESD part, but the heuristic does not establish that one is required. Keep open for owner interface-risk disposition; any emissions or ESD measurements are a downstream physical gate. Do not add protection parts without review. |
| emc:IO-002:j1 | J1 pins are 3V3, GND, EDGE, and MON. The report counts the three non-GND pins, including the 3V3 supply. | High for pin assignment and count; low for external cable-return behavior. | Stage-B physical verification gate, deferred by Issue #3. The board has one shared GND pin for EDGE, MON, and 3V3, but source review cannot establish cable-return behavior. Verify that behavior during separately authorized physical testing; no Stage-A defect is established. |
| emc:EE-001:14ba23cdf0dc | The board has two copper layers and one copper zone on B.Cu. The estimate assumes εr=4.4 and reports 1.43–2.86 GHz resonances. | High that the stated paired-plane cavity model is unsupported by this layout; no physical resonance measurement exists. | Supported false positive for the claimed board-cavity/PDN resonances. The layout has two copper layers and one GND zone, not the paired planes required by the report model. No measured-impedance model supports the estimate, so it does not justify a decoupling change or EMC-compliance claim. |

The Stage-A EMC Design Review (EMC-DSR) is complete for this candidate and bound to the current board and report hashes above. All eight findings have explicit dispositions and none is suppressed: six Stage-A documentation/evidence gaps and one Stage-B physical verification gate remain open, none is a proven design defect, and the unsupported paired-plane cavity estimate is a supported false positive. This closes the source-bound review step only; it does not waive those risks or demonstrate EMC compliance. No emissions, ESD, or cable-return test was run.

The PCB analyzer reports no opposite-layer copper beneath D1, U1, J1, RV1 or the through-hole test pads. The board has no separate keepout object; the input-loop void is the intentional GND-zone clearance, alongside local drilled-pad clearances. The board has no fiducials; its coarsest SMD pad pitch makes this a low pick-and-place risk. Test pads expose 5 of the 9 nets.

The thermal analyzer could not estimate component temperatures because the design has no trustworthy dissipation data. The automated lifecycle audit was not run because manufacturer API credentials were unavailable; the manual lifecycle evidence and unresolved exact-MPN declarations are recorded in BENCH-003. Gerber analysis does not apply because no fabrication outputs were requested or generated. The latest model-based Gate A run is recorded above. Front and back KiCad SVGs for the current board were reviewed as a 2D layout. Package-body and enclosure clearance, connector mating and retention, cable routing, and detector optical alignment remain unverified. Gate A does not validate PCB layout or physical behavior; neither it nor DRC establishes electrical correctness, mechanical fit, or physical performance.

## Acceptance boundaries

- **Documentation-level readiness:** Source-backed pin map, provisional candidate Rf/Cf, schematic-derived parts manifest, staged budget, correct component labeling, honest missing quantities/costs, exact reproduction and error/warning preservation.
- **Before prototype PCB fabrication:** Native KiCad ERC/DRC clean *for an actually routed board*; TIA/feedback layout and diode geometry independently reviewed, electrical simulation/analytical stability study recorded, manufacturer package/polarity verified, full required BOM and supply protected/available.
- **Before connecting hardware:** Owner confirms current-limited power supply/DMM, soldering/magnification, safe VIN/VUSB alteration, 5 V backfeed/3.3 V limits and measured power-up/down behavior, then performs BENCH-002's staged gates. A design file alone is **never** permission to energize.
- **Later issues (#4/#5/#7/#8):** measured full-speed HID IN successful completion, one actual Android renderer-to-optical response, five shared-hardware-timebase input captures, external USB correlation and complete uncertainty characterization. No such measurement is included in Issue #3.

**Current design sources:** [OPA320](https://www.ti.com/lit/ds/symlink/opa320.pdf), [TLV3501](https://www.ti.com/lit/ds/symlink/tlv3501.pdf), [SN74LVC1G17](https://www.ti.com/lit/gpn/sn74lvc1g17), [SFH 203 active manufacturer listing](https://ams-osram.com/products/photodetectors/photodiodes/osram-radial-t1-34-sfh-203), [official SFH 203 datasheet, Version 1.4 (2022-08-10; Q62702P0955)](https://look.ams-osram.com/m/67e1a90044b03ee9/original/SFH-203.pdf) (package drawing and recommended-pad artwork, printed pages 9–10), [official SFH 203 P datasheet, Version 1.5 (2026-03-18; Q62702P0942)](https://look.ams-osram.com/m/52b343f9b18e032b/original/SFH-203-P.pdf), [PJRC power notes](https://www.pjrc.com/store/teensy41.html), [Pololu #5383](https://www.pololu.com/product/5383), [Bourns MF-R050](https://www.digikey.com/en/products/detail/bourns-inc/MF-R050/259965).
