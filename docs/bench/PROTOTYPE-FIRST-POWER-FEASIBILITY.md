# Pre-purchase first-power feasibility and protection decision

**Document ID:** BENCH-005\
**Status:** Draft for owner design review\
**Owner:** Repository maintainers\
**Last Updated:** 2026-11-14 (America/Los_Angeles)\
**Depends On:** ARCH-001, SPEC-X-001, BENCH-001, BENCH-002, BENCH-003, BENCH-004\
**Supersedes:** None\
**Superseded By:** None

**Scope and decision authority:** Sections 1–8 retain Issue #45's historical S0/S1 paper decision; section 9 retains its sources and section 10 the earlier A/B/C comparison and disputes. Section 11 records the owner-selected supervised one-sensor architecture and bounded G0 risk decision. Physical releases still require their own evidence and owner authorization. Historical paper-only scope and continued B/C investigation statements do not limit section 11.

## 1. Decision and acceptance boundary

**Pre-purchase verdict: PAPER FIRST-POWER PATH CONDITIONALLY FEASIBLE — OWNER DESIGN REVIEW REQUIRED.** The conditional paper route is limited to S0 source qualification followed by an unchanged Teensy-only, bench-supply S1 attempt under the defined external fault bounds in §5. It does not promise successful boot or qualify the source/temporary connection already. Physical admission remains closed until every parameter and preflight condition is demonstrated and approved. The fixed-adapter design has a separate overvoltage incompatibility and incomplete fault coordination; it remains held. No initial current, duration or energy limit is selected here.

The acceptance standard is rated normal operation and bounded exposure to defined external source and wiring faults. Internal Teensy failures are assessed as residual risks for owner review. A guarantee against damage from every internal board failure is not required. Unknown startup demand may be an output of an independently bounded S1 attempt; it is not an automatic prerequisite to designing that attempt.

Absent S0 measurements alone do not invalidate a conditional paper route. This paper pass rests on a documented nominal 5 V VIN supply role, a nonempty 4.4–5.5 V internal USB-supply operating window before losses/margins, a selectable source with output/CC/OCP controls, and the independently testable admission constraints in §5. No published limit establishes an impossible window for that limited arrangement. A paper pass requires acceptance limits derived independently of unknown startup demand and a practicable way to demonstrate the remaining conditions before connecting the Teensy. A source feature label or a future instruction to stop if damage appears cannot supply a missing exposure bound.

[Issue #45](https://github.com/whatobiplays/screen-latency-tool/issues/45) recorded and resolved this limited paper decision. [Issue #47](https://github.com/whatobiplays/screen-latency-tool/issues/47) owns the current complete-fixture protection comparison in §10. [Issue #38](https://github.com/whatobiplays/screen-latency-tool/issues/38) owns subsequent purchase approval. All component, equipment and PCB orders remain paused pending the power decision and explicit owner authorization. Preserve `INITIAL_CURRENT_LIMIT=UNSET`, FABRICATION HOLD and every Stage-B/C gate. This assessment authorizes no physical operation, substitute part or architecture change.

## 2. Proposed topology and electrical subjects

The source baseline is `da04a29de04d3b27608c5ba54796271f78e89766`. Native connectivity comes from the [power/USB schematic](../../hardware/prototype/power-usb/power-usb.kicad_sch), [optical schematic](../../hardware/prototype/optical/one-sensor.kicad_sch), [generator/checker](../../hardware/prototype/check.mjs) and [instance BOM](../../hardware/prototype/BOM.csv). They describe an optical PCB and separate offboard power/USB wiring. There is no routed power PCB.

```text
Fixed adapter positive
 GST18U05-P1J -> L721A center, logical pin 1 / 5V_IN
 -> external F1 MF-R050 -> 5V_LIMITED -> M1 Pololu 5383
 -> 5V_SAFE -> XT30U-F J2 pin 1 <-> XT30U-M J6 pin 1 -> Teensy VIN

Published Teensy input path [P1]
 VIN -> onboard F1 (500 mA label) -> L2 ferrite -> Q1 DMG2305UX
     -> internal input rail -> U4 -> 3V3 -> onboard loads
                            |               -> optical J1 pin 1
                            |               -> VBUS-buffer J4 pin 1
                            -> MCU USB1_VBUS/USB2_VBUS and host-power circuitry

Ground: L721A sleeve, logical pin 2 -> common supported signal returns
        J2/J6 pin 2; optical J1 pin 2; J4 pin 2; LED J5 pin 2
L721A logical pin 3 / sleeve shunt: unconnected, individually insulated
LED: Teensy LED_GPIO -> J5 pin 1 -> 1 kohm -> WP7113ID -> GND
```

The onboard F1 and external MF-R050 F1 are different components. PJRC's schematic labels the former 500 mA but does not establish an exact fuse MPN, time-current curve or installed fault-clearing guarantee. Its U4=TLV75733P and R7=100 kΩ drawing predates later regulator and 470 kΩ R7 changes. Record the received board, population, regulator/package and startup revision before applying a revision-specific model [P1].

```text
Separate host source
 Android USB-C -> Adafruit 3878 -> eLabGuy USB-uBM-uBF-V1A -> device Micro-B
                                    VCC CLOSED; shield-to-GND OPEN
 Host VBUS -> physically isolated VUSB_RAW pad -> 1 kohm R1 -> buffer A
                                                              -> 47 kohm R3 -> GND
 Buffer U1: pin 5=3V3, pin 3=GND, pin 4=VBUS_PRESENT, pin 1=NC
 VUSB_RAW and VIN: factory link must be cut and verified open before combined use
 USB D+/D-: intact inline data path, never a solderless-breadboard route
```

The cut separates the external USB VBUS source from VIN. It does not remove the VIN-fed MCU USB supply inputs shown in PJRC's schematic. Host divider current belongs to the USB source, not to the adapter/PTC budget. The eLabGuy maker drawing distinguishes the VCC break from the optional shield-to-ground jumper [P11].

## 3. Manufacturer limits and normal headroom

### 3.1 Board and regulator revisions

| Device / revision | Supported input limits | Current/protection evidence and limitation |
| --- | --- | --- |
| TLV75733P, original U4 | 5.5 V operating maximum; 6.0 V absolute maximum | 1 A output class; specified current-limit range 1.20–1.78 A; short current 755 mA typical. No whole-board input/startup bound follows [P2]. |
| NCV8186AMN330TAG, after March 2022 | 5.5 V operating maximum; 6.0 V absolute maximum | 1 A output class; current-limit/short-current entries 1.10 A minimum, 1.40 A typical, no maximum. A variant has output discharge [P3]. |
| TLV75533P, after March 2023 | 5.5 V operating maximum; 6.0 V absolute maximum | 500 mA output class; specified current-limit range 560–865 mA; short current 355 mA typical [P4]. |
| MCU USB_OTG1_VBUS / USB_OTG2_VBUS | 4.4–5.5 V operating supply; **5.5 V absolute maximum** | Supplied from the internal input rail in the published board drawing. This is a stricter stress ceiling than U4's 6.0 V [P1, P5]. |

Absolute maxima are stress limits. Keep normal and fault peaks below the applicable ceilings with measurement uncertainty and protection tolerance included. Regulator family minimum input voltages do not establish that a 3.3 V variant regulates there, or that the whole Teensy operates there. PJRC's pinout gives a 3.6–5.5 V VIN range; active USB operation additionally needs adequate voltage at its internal supply nodes [P1, P5].

The delivered DEV-20360 regulator, component population and package are unverified. Do not substitute DBV/SOT-23 thermal figures for DRV/WSON or the onsemi DFN-8 package. Manufacturer package/JEDEC thermal metrics are not upper and lower bounds on the actual Teensy assembly [P2–P4].

### 3.2 Voltage, current and heat accounting

1. Mean Well specifies nominal 5 V, ±5% tolerance including setup/line/load terms, with 80 mV peak-to-peak ripple under its stated test arrangement. Do not add line/load tolerances twice or assume that ripple is symmetric. The DC endpoints are 4.75 and 5.25 V; a combined instantaneous bound needs the applicable ripple/transient conditions [P6].
2. At illustrative 0.25 A, MF-R050's initial 23°C resistance of 0.41–0.77 Ω gives 0.1025–0.1925 V drop and 25.6–48.1 mW dissipation. From 4.75 V that leaves only 0.2475–0.1575 V for every remaining series drop before a 4.4 V USB supply threshold. The one-hour post-trip 1.17 Ω maximum leaves 0.0575 V. None of these resistance values bounds installed hot operation [P7].
3. Add Pololu/controller loss, both conductors, contacts, onboard fuse/ferrite/Q1 and dynamic droop. LM74700-Q1 regulates approximately 20 mV forward drop in one operating mode; MOSFET-only `I*RDS(on)` is not a complete module drop bound at 5 V [P8]. At illustrative 0.41 A with 0.77 Ω, the external PTC alone leaves 34.3 mV before the 4.4 V threshold. This demonstrates limited margin, not a chosen load or measured failure.
4. Use `I_VIN = I_intrinsic + I_external_3V3_input_equivalent + I_other_input_loads`. For U4's linear external-load contribution, incremental input current is approximately incremental output current plus ground-current change. The processor's internal conversion and other input loads remain separate. PJRC's 250 mA external allowance and Teensy 4.0 100 mA example are not total Teensy 4.1 input bounds [P1].
5. BENCH-004's ideal divider/trimmer subtotal is 0.447857 mA. OPA320, TLV3501 and buffer test conditions differ; driven outputs and startup add load. The 6.507857 mA illustration is not a maximum. The LED's conditional resistor-only ceiling is 3.333 mA at 3.3 V/990 Ω, below PJRC's 4 mA recommended GPIO drive; actual VOH, LED behavior and faults still need checking [P1, P9].
6. Use `P_U4 = (V_U4,in - V_3V3)*I_U4,out + V_U4,in*I_ground`, plus actual package/board thermal behavior. At illustrative 250 mA extra external load and 5.0 V at U4, added LDO heat is 425 mW before intrinsic loads. A short can dissipate more heat while rail voltage collapses; thermal cycling is not a qualified operating mode.
7. The 1% VBUS resistors and a ±5 µA powered input-leakage sensitivity give 4.8888–5.1476 V across the 5.0/5.25 V cases. The leakage specification's endpoint conditions do not make this a complete hotplug guarantee. The separate `Ioff` limit is ±10 µA at zero VCC under its specified 5.5 V input/output condition. Include off-state rail rise, input thresholds, ground offset, capacitance and transient peaks in S4 [P10].

### 3.3 Contacts, conductors and returns

| Selected element | Manufacturer evidence | Installed restriction |
| --- | --- | --- |
| L721A / GST18U05-P1J | Switchcraft lists 5 A/24 V and 0.01 Ω contact resistance; drawing identifies center/sleeve/shunt and locking 767K/768K mates. Mean Well specifies its standard center-positive P1J plug [P6, P16]. | Owner-selected nonlocking mating is a nominal-fit assumption. The installed pair has no demonstrated contact, retention or short-interruption limit. Insulate and support the pre-PTC lugs and conductors; neither jack nor downstream PTC protects a jack-side short. |
| XT30U-F.G.Y / XT30U-M.G.Y | 2023V2: 15 A maximum at 16 AWG/temperature rise ≤30 K; 20 A maximum at ≤85 K; contact resistance ≤1.5 mΩ; solder termination and friction retention [P16]. | Those test conditions do not rate an unspecified wire gauge, four hand-soldered joints, strain relief or a transient short. The high-rise test is not an acceptable bench temperature target. |
| JST B4B-PH-K-S(LF)(SN) / CABLE-PH04 | JST PH: 2 A with 24 AWG. ADI/Trinamic specifies a 200 mm 24-AWG PHR-04 tail [P16]. | Check received mating, contacts, PCB joints and return path. Label black=3V3, red=GND, blue=EDGE, gray=MON by net; common color assumptions would reverse this design's supply. |
| CAB-14575 / CAB-14574 | SparkFun identifies factory-matched connector pairs with 20-AWG bare-ended leads [P16]. | No exact finished-pair current/short-duration rating or sourced pin-color assignment was established. Assess power and signal faults, not just expected low LED/buffer current. |
| Temporary S1 supply leads and contacts | No exact installed connection is specified by the present source design. | Select and qualify their voltage/current/temperature/exposure limits independently. A meter-lead current rating does not rate an improvised contact at the Teensy header. |

Contact and wire dissipation use actual `I²R`; local hot spots, exposed strands and resistive faults may dominate bulk-wire heating. An open return is benign only when no alternate current path exists. S1 excludes every other connection; S2–S4 must assess USB ground, GPIO and probe/earth return paths separately. No installed conductor or contact thermal result is claimed.

## 4. Defined faults and protection gaps

```text
A: jack + / return fault before external F1 -> only source response is upstream
B: 5V_LIMITED / 5V_SAFE / temporary VIN short -> source and relevant series path
C: external 3V3 short -> U4 current/thermal response; external F1 need not trip
D: source overvoltage -> U4 input AND MCU USB supply inputs before the 3V3 rail
E: open return / live signal with rail off -> alternate GPIO/USB/shield paths
F: short between 5 V and 3V3/signal -> source protection may not protect that load
```

| Fault / subject | Protection supported on paper | Uncontrolled exposure or required boundary |
| --- | --- | --- |
| Reverse input, complete external chain | Pololu 5383 is a reverse-voltage/reverse-current module [P8] | Wrong module wiring or a direct S1 lead reversal bypasses that function. Procedure/continuity checks reduce error; they are not automatic reverse-polarity protection. |
| Wrong-voltage source / adapter OVP | Adapter OVP threshold 110–140%, or 5.5–7.0 V [P6] | No downstream OV disconnect/clamp. The upper threshold exceeds every U4 limit and the MCU USB-input absolute limit. Series drops cannot guarantee protection at light load. |
| 5 V short | MF-R050 maximum trip time 4 s at 2.5 A/23°C; adapter overload hiccup at 110–150% rated power [P6, P7] | No coordinated peak/current/time/energy bound for the full harness and board. The 1 A trip entry does not promise 4 s clearing at 1 A. The pre-F1 jack segment lacks that PTC. |
| 3V3 short | Revision-specific U4 limiting/foldback/thermal behavior [P2–P4] | For TLV755P, 355 mA typical can remain below external PTC hold=410 mA at 40°C before other input draw. Other revisions differ; no blanket clearing claim follows. |
| Charged output capacitance / short | Stored energy is calculable when C and voltage are bounded | Source CC does not intercept discharge already downstream. Source/output capacitance, ESR, peak response and harness limits are not established. |
| Open ground / miswired connector | S1 with only two supply leads removes USB and offboard alternate returns | S2–S4 introduce alternate returns and signal paths. Do not use shield as the only return; assess live-rail cross-pin faults separately. |
| GPIO/EDGE/MON contention | Existing 100 Ω EDGE and 1 kΩ MON/LED resistors bound some circuit impedances | 33 mA from `3.3/100` is only a fault request, not safe GPIO drive. Buffer or comparator outputs can inject into an unpowered peer. |
| Input collapse / prebiased 3V3 | U4 discharge/body-diode behavior is documented [P2–P4] | No board-wide permissible reverse-current envelope established. An independent external 3V3 source would require a new sequencing design. |
| Internal U4 short, MCU failure, internal damaged component | No universal no-damage guarantee asserted | Residual internal-board risk for owner review. Do not automatically turn it into a prerequisite to prove protection against every hypothetical internal failure. |

The weakest exposed element depends on fault location. For OV it includes the MCU USB supply inputs at 5.5 V absolute maximum; for external shorts it may be a temporary contact, solder joint or conductor whose identity/rating is still unspecified. A large connector or Pololu current rating cannot stand in for those missing limits.

## 5. Independent S0–S4 admission and stop records

### 5.1 S0 and S1 paper design

S0 concerns only the selected KA3005PE+, rated dummy load and measurement equipment. S1 concerns only the Teensy and a reviewed VIN/GND connection, with USB, VBAT, optical board, buffer, LED and other peripherals disconnected. The direct temporary lead arrangement is not the full L721A/PTC/Pololu/XT30U chain.

The current original KORAD V1.15 manual explicitly includes KA3005PE+ on its cover and function table. It documents 0–30 V/0–5 A control, 10 mV/1 mA setting resolution, output control, adjustable OVP/OCP and automatic output shutdown above those protection settings. Its ≤100 ms voltage rise/fall entry is specified at 10% rated load, not a Teensy test current or protection-trip bound. The percentage basis and temperature applicability of the setup-accuracy table require care. No maximum output-enable overshoot, CV/CC transition peak, output capacitance/ESR or OVP/OCP trip latency is established by the inspected manual [P12]. Bind the actual unit/firmware/mains variant and delivered instructions; do not substitute an older KA3005D manual.

A candidate S1 admission envelope must satisfy all of the following independently of unknown boot demand:

1. Bound voltage at VIN and the internal exposed nodes, including polarity, overshoot, ground error and measurement uncertainty. Use operating limits with margin; 6.0 V is not an S1 ceiling.
2. Identify and rate the exact temporary conductors, contacts, insulation and mechanical support. Define which short, open-return, reversal and source-transition faults are included; submit any excluded credible fault for owner adjudication.
3. Bound `I_peak`, source current-limit tolerance, response time, stored energy, maximum connected duration and cutoff latency. Demonstrate `integral(i^2*R_element*dt)` and peak exposure against each defined external connection limit. Account for downstream capacitor discharge and repeated attempts.
4. Choose an S1 trial setting from the intersection of that exposure envelope and the qualified source's controllable range. A bounded nonboot result is permitted. Do not select a setting from PTC hold/trip, U4 rated output, Pololu current class, an unrelated processor example or an assumed whole-board demand.
5. Define an automatic or independently bounded cutoff where required. Operator observation is not a microsecond protection response. A failed or incomplete boot ends the attempt; no automatic increase or retry is allowed.

The paper admission equations are `V_USB,settled,min >= 4.4 V`, `V_exposed,peak + U_V + M_V < 5.5 V`, `I_sustained,max <= I_connection,continuous,min`, and `integral(i² dt) <= (I²t)_connection,min` where a justified pulse rating is used. `U_V` is measurement uncertainty and `M_V` is an explicit positive design margin. Include maximum path loss in the lower-voltage inequality. Bound available energy by `0.5*C_effective,max*V_precharge,max² + V_max*I_max*t_max`, without double counting capacitor current already included in an integral. At 5.5 V, capacitor energy is 15.125 µJ per µF; this does not identify the supply capacitance.

Continuous wire/contact ratings must be known before S0 can compare its measured response with them. Pulse ratings may be used only when independently supported. Tie `t_max` to the accepted trial/cutoff mechanism and its uncertainty; if automatic cutoff is needed, the PE+ protection response must demonstrate it. A human reaction time is not an automatic latency guarantee. No current/time/energy number is inferred from the unmeasured board demand.

This is a parametrized conditional design, with every numerical physical-admission parameter still unset. The source and actual connection may pass or fail S0; failure leaves S1 closed and may require a different arrangement. Missing S0 results or a prior boot waveform are not the reason for the remaining physical hold. No universal internal-board no-damage proof is required.

### 5.2 Defined external fault scope for the conditional S1 route

| Case | Required boundary and mitigation | Conditional claim / owner decision |
| --- | --- | --- |
| Output enable, disable, mains/preset recall and permitted CV/CC/OCP transitions | S0 tests the exact source/lead setup, active settings and accepted operating states with a rated dummy fixture. Output-memory state and inactive remote control are documented before connection. | Peak voltage, current, energy and cutoff must satisfy §5.1. The current manual alone does not pass this condition. |
| Short between the intended VIN and ground conductors, including a near-DUT short | Source CC/OCP is qualified with the actual lead arrangement; initial capacitor discharge, continuous or supported pulse ratings and cutoff are included. | No dependence on an assumed Teensy startup waveform or the onboard fuse label. A short test on the prototype itself is not required or authorized here. |
| Opening either of the only two supply conductors | USB, VBAT, GPIO, external 3V3 and all peripherals are absent; support prevents an opened lead touching an adjacent pin. | No alternate powered return is admitted. Inductive/interrupt transients still belong in S0's voltage bound. |
| Reversed leads, wrong header pin, adjacent-pin bridge or live reconnection | De-energized pin/polarity/continuity inspection, supported insulated connections and no live reconnection are procedural prevention. | These faults are **not covered by automatic protection in direct S1**. Owner design review must explicitly accept this limited procedural scope or require a qualified keyed/protected connection before this route is adopted. |
| Arbitrary wrong source setting or catastrophic source failure outside the qualified envelope | Output settings, memory, front-panel lock and inactive remote control reduce error; the built-in OVP is not an independent guarantee against every supply failure. | **Not covered by the bounded source-response claim.** If owner review requires these faults to be controlled automatically, the unchanged route does not qualify and a separately approved protector/source solution is needed. |
| U4/MCU/internal board failure | Preserve observations and stop; assess as residual board risk. | No promise of zero internal damage. This does not widen S1 to external loads or justify raising a failed setting. |

The conditional paper verdict is submitted for this explicit fault-scope adjudication, not a statement that the owner has already accepted the exclusions. It is not a full single-fault-tolerant power design. No source-only success, bounded nonboot or paper verdict releases S2–S4.


### 5.3 Physical preflight and stops

All physical results below are **UNVERIFIED / NOT_RUN**. The conditions are future requirements, not instructions to perform them now.

| Stage | Physical admission conditions | Stop / separate decision |
| --- | --- | --- |
| S0: no DUT | Exact model/revision/manual, mains/earth/isolation, output-disabled and preset behavior; rated dummy-load/leads; adequate voltage/current bandwidth and uncertainty; predefined CV/CC/OVP/OCP and stored-energy tests | Stop if source behavior exceeds the dummy-load/fixture envelope or cannot be measured. A DMM alone does not resolve short transients. S0 does not release a DUT setting. |
| S1: Teensy only | Received board/package identification; zero-energy unpowered inspection; reviewed VIN/GND connection; S0-qualified source; completed voltage/current/time/energy case and owner-approved fault scope | Any missing essential bound blocks admission. Stop for voltage excursions, unexpected current, heating, rail collapse, reset/brownout or expiry of the exposure bound. Preserve a failed attempt and review before changing it. |
| S2: complete input chain, no external 3V3 loads | Independently qualified L721A mating/support/shunt insulation, external F1, Pololu orientation, XT30U wiring/return and onboard path; revised source/drop/fault bounds; VIN/VUSB separation where required | S1 evidence/settings do not transfer automatically. Stop on excessive drop/heating, intermittent contact, unintended VUSB voltage or uncoordinated fault exposure. Fixed adapter remains held. |
| S3: optical, buffer, LED individually | Inspect pin maps and 3V3/ground; assess branch current, output contention and power sequencing before each addition; refresh source and fault envelopes | Stop on branch overcurrent, noise/rail instability, unsafe GPIO levels or back-power. Retain compact optical PCB; no breadboard TIA substitution. |
| S4: controlled VBUS, then Android | Physical VIN/VUSB cut verified; eLabGuy VCC CLOSED/shield-to-GND OPEN; unpowered data/ground/shield checks; buffer output initially isolated for VBUS-only testing; separate combined-source plan | Stop on powered-off rail rise/backfeed, unsafe buffer/GPIO voltage, ground fault, unstable attach/detach or host-role failure. USB-only after the cut is not an operating-power mode. |

Physical release requires completed per-stage parameters and explicit authorization. `INITIAL_CURRENT_LIMIT=UNSET` remains the full-fixture state. No numerical S1 setting is hidden in an example or inherited from another stage.

## 6. Ranked architecture alternatives for owner adjudication

| Rank / candidate | Normal and fault trade-off | Sourcing and scope impact |
| --- | --- | --- |
| 1. Retain design; bench-only S1 investigation; fixed adapter held | Smallest commitment. Can acquire intrinsic startup evidence only after the independent S1 envelope is complete. Source qualification and temporary wiring do not establish full-fixture or USB protection. | Keep KA3005PE+ selected. Identify only S0/S1 measurement and connection shortfalls first. About 1–2 engineering days after necessary rating evidence is available; no new protector approved. |
| 2. LTC4368IMS-2#PBF plus back-to-back FETs/shunt, coordinated branch protection | 2.5–60 V controller, ±1.5% OV/UV references, bidirectional circuit breaker. Lower series-FET loss is possible, but shunt drop, low-voltage gate drive, SOA, latency and inrush must close. This is a disconnecting breaker, not a sustained CC regulator [P13]. | Additional controller, two FETs, shunt and precision passives; reviewed supported layout/evaluation hardware required. SiR870ADP-T1-GE3 is a candidate FET, not a selected replacement. About 3–5 engineering days for a decision-ready design, before physical qualification. |
| 3. TPS26600PWPR integrated main eFuse plus branch protection | 4.2–60 V operation, 0.1–2.23 A nominal adjustable limit, reverse protection and selectable response. OV threshold tolerance and series loss constrain this 5 V application. No drop-in protection claim [P14]. | Fewer external power devices, but precision dividers, ramp/fault configuration, thermally suitable assembly and possibly a new carrier remain necessary. About 2–4 engineering days if the voltage window is feasible. |

At an ideal normal high of 5.25 V, TPS26600's 1.17–1.225 V rising OV threshold span alone forces the highest possible trip above approximately `5.25*1.225/1.17 = 5.4968 V` when its lowest trip must exceed normal input. Divider error, leakage, ripple and turnoff overshoot leave no credible margin below the MCU's 5.5 V stress ceiling. At illustrative 5.29 V normal high the same result is 5.5387 V. Reject this as an unconditional fix for the selected adapter; a narrower source window or different protection approach needs owner adjudication [P14].

LTC4368's reference-only equivalent is 5.4099 V for a 5.25 V normal high, before divider and dynamic errors. With an assumed 5.29 V healthy maximum, two 0.1% divider resistors, 10 kΩ bottom resistance and maximum input leakage, the optimistic highest trip is about 5.4729 V. Only 27 mV remains before 5.5 V, excluding drift, nuisance clearance, measurement uncertainty and overshoot. Adding the full 80 mV ripple as a sensitivity makes the corresponding result 5.5143 V, outside the window. Neither ripple allocation is a guaranteed supplier waveform. This improves the static window only under a narrower accepted source contract and does not prove safe output voltage during a fast step. Verify the complete disconnect waveform and FET SOA, or investigate a suitably bounded regulating/clamping solution. A generic TVS's nominal voltage does not establish a clamp below 5.5 V [P13].

For the external 3V3 branch, investigate TPS2553DBVR-1: 2.5–6.5 V operation; the 75 mA nominal mode connects ILIM to IN and has an achieved 50–100 mA band. Resistor programming is specified over 15–232 kΩ; an arbitrarily larger resistor is not a supported lower-current setting. Latch-off deglitch is 3–11 ms, and the short response is 2 µs typical at the 5 V test condition. These do not guarantee a complete 3.3 V fault waveform. It supplies no forward OVP; reverse detection can require 95–190 mV differential and published turnoff reaches 7 ms under the 5 V test condition. Size to actual branch startup, contacts and allowable exposure rather than copying its headline setting. Keeping live outputs off an unpowered peer remains necessary [P15].

For LTC4368, the forward sense comparator spans 40–60 mV under its normal test conditions and 30–70 mV under the specified zero-output test. These tolerances and actual gate turnoff must enter shunt/startup/fault coordination. TPS26600's 100 mA nominal limit spans 85–115 mA at its stated conditions; its ±5% headline applies at 1 A. Evaluation-board factory settings are not prototype setpoints.

Do not adopt TPS22946 solely for its lower nominal setting: its elevated startup limit reaches 685 mA under the stated 3.3 V test, and moderate-overload detection can take 65.5 ms. Its reverse-current behavior and small package also need separate assessment. No alternative here is approved for CAD, BOM or purchasing. Preserve the selected optical geometry and analyze analog-rail noise, fault retry coupling and timing consequences before adopting any protector.

## 7. Evidence classification and decision matrix

Use PASS only for a demonstrated source/analytical proposition or an executed check with its stated scope. FAIL denotes a requirement contradicted by evidence, not an observed hardware failure. UNVERIFIED includes unperformed physical tests and missing numerical bounds.

| Proposition | Result / evidence class | Consequence |
| --- | --- | --- |
| Native external pin/net design agrees with generator/checker | PASS, source connectivity | Describes proposed topology only. |
| All three documented U4 variants have 5.5 V normal / 6.0 V absolute input limits | PASS, manufacturer specifications | Apply the actual installed variant/package; no delivered-board attestation. |
| Existing adapter OVP keeps all exposed board inputs within limits | FAIL, manufacturer-limit comparison | Fixed-adapter operation needs a protection solution or an explicitly narrower accepted fault claim. |
| External PTC guarantees clearing of all 3V3 shorts | FAIL, unsupported universal coordination claim with a plausible counterexample | Keep branch fault review independent of external F1. |
| Whole-board running/startup envelope and installed thermal margin | UNVERIFIED, future measurement plus incomplete model | No full-fixture current setting. Startup may be obtained from a bounded S1 attempt. |
| S0/S1 paper route under §5 bounds | PASS, conditional design proposition requiring owner fault-scope review | No incompatible nominal supply requirement found. All actual source/connection/exposure parameters and physical admission remain UNVERIFIED. |
| S0/S1 and every S2–S4 physical result | UNVERIFIED / NOT_RUN | No power, USB or purchase release. |
| Internal Teensy failure without a no-damage guarantee | Residual risk requiring owner review | Separate from external-fault admission; no universal zero-damage prerequisite. |

The final qualification record supplies actual native/analyzer/simulation results and source hashes. Analytical or simulated results cannot promote any physical row to PASS. BENCH-004 retains the historical load examples; this report supplies the current fault-boundary interpretation.

### 7.1 Executed checks and their limits

1. Baseline `make check` passed. KiCad 10.0.8 source-profile ERC for both schematics and refilled optical DRC/parity passed. External diagnostic copies enabled all five ignored DRC and four ignored ERC categories and returned zero findings. The first isolated-configuration attempt reported missing standard footprint-library references; copying the existing library tables into the external configuration resolved them without suppressing rules. The failed attempt remains in the evidence.
2. Native XML exports contain 23 optical components/9 nets and 14 power components/12 nets, including two explicit NC nets. Independent connected-pin comparison and all 51 PCB pads agree. Protected source hashes match the approved baseline; no CAD, library, BOM, generator or hardware interface changed.
3. Schematic/PCB/cross-analysis ran. The analyzers have no local per-MPN datasheet-extraction cache, so their electrical/pin judgments are consistency checks. Manufacturer-limit claims in this report come from the separately inspected primary sources. Cross-analysis skipped current/trace checks without regulator estimates; zero findings is not a current-capacity pass.
4. Optical EMC screening retained seven unsuppressed findings: three errors, two warnings and two information items in the raw heuristic output. The summing-node plane void is intentional in the native geometry; filling it could alter input capacitance. VREF/THRESH return-path concerns and J1 filter/ESD coverage remain risks. The generic paired-plane cavity inference is unsupported for this two-layer, single-ground-zone design. No compliance or cable/ESD test occurred.
5. Thermal analysis ran but assessed zero components because quantifiable dissipation inputs were absent. Its null score/empty estimate is UNVERIFIED, not a zero-heat or thermal-safety pass. Conditional hand calculations in §3 do not replace installed-board measurements.
6. ngspice 47 ran five skill-generated passive cases, two isolated-divider reruns supplied with extracted parasitics, and three connected reference/trimmer cases. The runner did not apply parasitics to those dividers, so they are not PCB-aware stability results. At nominal 3.3 V the connected passive model gives VREF=1.17104, 1.18158 and 1.19212 V at low/mid/high wiper positions; active devices, bias currents and tolerances are omitted. Generic ESR/inductance impedance cases do not model complete rail behavior.
7. Nine additional ideal RC cases agree with closed-form arithmetic for an assumed 5-to-7 V input step. Three exceed 5.5 V at a 6 µs sample time. The R/C values and source step are sensitivity assumptions; 6 µs is not a guaranteed protector isolation time. This explains why a typical delay cannot establish an output clamp. It is not a simulated failure of the actual adapter or Teensy.
8. Historical Gate A remains NOT_RERUN / INPUTS_UNAVAILABLE: supplied active-device models and simulator provenance were not located in the bounded search. No failed stability or hardware inference follows. No new Gerber/fabrication work or complete fresh 31-MPN lifecycle audit was performed; prior sourcing records remain dated evidence, and candidate manufacturer support is assessed separately.

Exact commands, initial failures, configuration differences, sources, scripts/decks, raw outputs and hashes are retained in the external Issue #45 qualification record for independent review. Every powered result remains NOT_RUN.

### 7.2 Reproduction and archived evidence identity

Run the repository checks from a checkout of the reviewed source. `make check-kicad` requires KiCad 10.0.8 and valid standard footprint/symbol library tables; it copies the native projects before invoking KiCad. Keep reports outside Git. Its ordinary source profile retains the historical ignores; the separately archived diagnostic copy enables all nine categories.

```sh
make check
KICAD_KEEP_REPORTS=1 make check-kicad
git diff --check
```

For independent connectivity exports, copy `hardware/prototype` to an external directory and export each copied schematic with `kicad-cli sch export netlist --format kicadxml -o <external-output.xml> <copied-schematic.kicad_sch>`. Never run a refill/save on the authoritative PCB. The full analysis record preserves the installed analyzer scripts, arguments/environment, native netlists and comparison scripts. It also preserves `power-corners.py`, `run-rc-sensitivity.py`, connected-passive decks and the candidate-protection corner script with their inputs. A source hash identifies what was analyzed; no physics result depends on the later Markdown-only commit changing those bytes.

| Archived input/result | SHA-256 |
| --- | --- |
| Optical native PCB | `3f7ee13263f27410885c2ea0af3558b2fb27d6718b94dacfc55aec67fd4bbe88` |
| Optical native schematic | `a4b7185e4046e1e98c51c2a31939fae99246ea0aa45d6bf09f145577e745c5d7` |
| Power/USB native schematic | `7821e62ef52b7ab9f884bb14fa4a57be5c1672338fad9f21865c103f2f35d372` |
| BOM.csv | `944130ce81ca301c7955ac59ce9b64e0eb15aabdea88f2a58849bc2ae6854559` |
| Configured native-check result JSON | `840f5084ef5f7a5fe701dea67b3684bfaaca62152b102a1d4fcd539325038dea` |
| Diagnostic all-enabled optical DRC JSON | `35bb16c81af906788616f957b9aa2eb91e2663779cef0b4e80f1628165d97125` |
| Native/analyzer pin-net comparison JSON | `751e97184f75eb709693a4eeb811e8ad09f527f9e796110c01d4d3e399fa118d` |

These output hashes identify the retained run, not a promise that fresh timestamp-bearing reports will be byte-identical. The [Issue #45 review surface](https://github.com/whatobiplays/screen-latency-tool/issues/45) and its implementation PR carry the external evidence location, complete checksum manifest, final source identity, independent reviews and actual CI results. The raw run files are intentionally outside the repository. Their absence from Git is not an unperformed-check PASS; the retained files must be available to the reviewer before accepting the evidence.


## 8. Issue #45 purchase and owner decisions

1. Decide whether to continue the unchanged bench-only S1 candidate by completing its source/temporary-connection envelope, or authorize design investigation of additional protection. Do not change the defined-fault standard into universal internal-board no-damage proof.
2. For the fixed adapter, choose whether to pursue coordinated OV/current/branch protection or retain a bench-only prototype. Stable normal operation cannot cure the source-limit mismatch. Any narrower accepted fault claim must name the excluded fault and residual exposure explicitly.
3. Consider an S0-only acquisition scope after owner review: the selected KA3005PE+, an independently rated dummy-load arrangement, suitable leads, and adequate measurement access, only where inventory shows a shortfall. Exact dummy-load/lead selection depends on its own S0 test envelope. This is a possible equipment investigation, not an orderable full basket or a purchase recommendation under the current HOLD.
4. Do not add the Teensy, adapter chain, optical PCB, sensor parts or USB kit to that S0 scope by implication. After owner acceptance of §5's limited paper route, one exact DEV-20360 and a reviewed S1 connection could be considered as a separate acquisition scope; identifying U4 after receipt is compatible with the common voltage constraints and revision-specific preflight. This is not an orderable shopping list: the temporary connection and inventory shortfalls remain unselected. Avoid committing to new protection parts before an alternative's tolerance/fault case closes.
5. Review residual internal-board failure risks and the scope of source-fault qualification separately. Keep PCB mechanical FABRICATION HOLD and later optical/USB/T0 qualification independent.

## 9. Primary-source register

Sources were reviewed on 2026-11-13. Page numbers below are printed datasheet pages unless identified otherwise. Manufacturer files and tool/source hashes belong in the external qualification package linked from the issue/PR; supplier listings do not establish installed safety.

| ID | Primary source and relevant basis |
| --- | --- |
| P1 | [PJRC Teensy 4.1 power/revision notes](https://www.pjrc.com/store/teensy41.html), [published schematic](https://www.pjrc.com/teensy/schematic.html), [pinout card](https://www.pjrc.com/teensy/card11a_rev4_web.pdf): VIN/VUSB, onboard path, U4/R7 revisions and external-load allowance. |
| P2 | [TI TLV757P Rev C](https://www.ti.com/lit/ds/symlink/tlv757p.pdf), pp. 4–6 and protection/sequencing sections: voltage/current/thermal/discharge limits. |
| P3 | [onsemi NCV8186 Rev 5](https://www.onsemi.com/download/data-sheet/pdf/ncv8186-d.pdf), pp. 2–3, 8–9: exact A variant, voltage/current/thermal/reverse behavior. |
| P4 | [TI TLV755P Rev D](https://www.ti.com/lit/ds/symlink/tlv755p.pdf), pp. 4–6 and protection/sequencing sections. |
| P5 | [NXP IMXRT1060CEC Rev 4](https://www.nxp.com/docs/en/nxp/data-sheets/IMXRT1060CEC.pdf), Tables 7 and 11, pp. 23–26: MCU stress and operating supply limits. |
| P6 | [Mean Well GST18U specification, 2026-04-03](https://www.meanwell.com/Upload/PDF/GST18U/GST18U-SPEC.PDF), pp. 1–3: exact adapter, output tolerance/ripple, overload/OVP and cable. |
| P7 | [Bourns MF-R series](https://www.bourns.com/docs/product-datasheets/mfr.pdf), electrical/derating tables and trip-test conditions. |
| P8 | [Pololu 5383](https://www.pololu.com/product/5383), [TI LM74700-Q1 Rev G](https://www.ti.com/lit/ds/symlink/lm74700-q1.pdf), §§9.4.2.1–9.4.2.3: reverse protection and conduction modes. |
| P9 | [OPA320](https://www.ti.com/lit/ds/symlink/opa320.pdf), [TLV3501](https://www.ti.com/lit/ds/symlink/tlv3501.pdf), [Kingbright WP7113ID](https://www.kingbrightusa.com/images/catalog/SPEC/WP7113ID.pdf): supply, drive/injection and LED conditions. |
| P10 | [TI SN74LVC1G17 Rev Y](https://www.ti.com/lit/ds/symlink/sn74lvc1g17.pdf), pp. 3–6, 10–11: exact DBV pins, input tolerance/leakage and partial-power-down limits. |
| P11 | [eLabGuy USB-uBM-uBF-V1A Rev 1.1](https://www.elabguy.com/datasheet/USB-uBM-uBF-V1A%20Rev1.1.pdf), figures 5–6; [Adafruit 3878](https://www.adafruit.com/product/3878): preserved inline USB configuration. |
| P12 | [KORAD upgraded KA series](https://www.koradtechnology.com/ProductDetail/3.html), [manufacturer manual listing](https://www.koradtechnology.com/companyfile/4/), [original V1.15 manual](https://v4.cecdn.yun300.cn/site_1801250020/KA3005DS%20User%20Manual%20V1.15.pdf): cover/PE+ applicability on PDF p. 5; controls/operation and specification table on imposed PDF pp. 6–8. Unit/firmware/mains applicability remains a receipt check. |
| P13 | [ADI LTC4368 Rev C](https://www.analog.com/media/en/technical-documentation/data-sheets/ltc4368.pdf), pp. 2–4, 11–14; [Vishay SiR870ADP](https://www.vishay.com/docs/63657/sir870adp.pdf): candidate controller/FET limits and test conditions. |
| P14 | [TI TPS2660 Rev G](https://www.ti.com/lit/ds/symlink/tps2660.pdf), pp. 7–9, 19–23: limit/OV thresholds, response and configuration. |
| P15 | [TI TPS2553 Rev G](https://www.ti.com/lit/ds/symlink/tps2553.pdf), pp. 5–7, 14–21: candidate branch-switch range, timing, latch-off and reverse behavior. |
| P16 | [Switchcraft L721A product/rating](https://www.switchcraft.com/dc-power-jack-0-084-2-1mm-pin-solder-lugs-termination-long-bushing/l721a/) and [drawing](https://www.switchcraft.com/assets/1/24/L721A_CD.PDF), [AMASS XT30U 2023V2](https://www.china-amass.com/public/upload/20240608/810c62b3f7ace2287989748538eb2a6b.pdf), [JST PH](https://www.jst-mfg.com/product/pdf/eng/ePH.pdf), [ADI/Trinamic CABLE-PH04 manual](https://mm.digikey.com/Volume0/opasdata/d220001/medias/docus/745/TMC_Cables_Manual_V1.00.pdf), [CAB-14575](https://www.sparkfun.com/led-strip-pigtail-connector-3-pin.html), [CAB-14574](https://www.sparkfun.com/led-strip-pigtail-connector-2-pin.html): connector identity, selected assembly and rating conditions; installed qualification remains separate. |
| P17 | [TI TPS25947 Rev C, May 2026](https://www.ti.com/lit/ds/symlink/tps25947.pdf), pp. 4–7, 25–37: adjustable OVLO, current-limit/circuit-breaker behavior, timing, retry/latch variants and thermal shutdown. |
| P18 | [TI TPS2553 Rev G, October 2026](https://www.ti.com/lit/ds/symlink/tps2553.pdf), pp. 4–7, 14–21: DRV-package switch resistance, current-limit range, short response, reverse behavior and latch-off operation. |
| P19 | [Vishay SiR870ADP Rev C, 2024-11-04](https://www.vishay.com/docs/63657/sir870adp.pdf), pp. 1–4: candidate PowerPAK SO-8 MOSFET voltage, resistance, gate-charge and test conditions. |
| P20 | [Vishay WSL current-sense resistor series](https://www.vishay.com/docs/30100/wsl.pdf): WSL2512 resistance, tolerance, rating and package data. |
| P21 | [TRACO TPP 15-J Series datasheet, 2024-11-26](https://www.tracopower.com/products/tpp15-j.pdf), pp. 1–2: TPP 15-105-J output accuracy, regulation, typical ripple, OVP/current limiting, dimensions and connection details. |
| P22 | [TI TPS25947 product page](https://www.ti.com/product/TPS25947), Simulation Model section: TPS25947x PSpice transient model Rev A (SLVMDJ6A.ZIP). The archived model metadata identifies a February 2021 PSpice 17.2 release; its guide says temperature-dependent behavior is not modeled. |
| P23 | [Mean Well GST18U series datasheet, 2026-04-03](https://www.meanwell.com/Upload/PDF/GST18U/GST18U-SPEC.PDF), p. 2: setup/rise/hold-up row lists a 30 ms rise time at full load for both 115 VAC and 230 VAC input. |

## 10. Issue #47 coordinated-protection investigation

Issue #45 remains the record for the original conditional bench-only S0/S1 paper route. [Issue #47](https://github.com/whatobiplays/screen-latency-tool/issues/47) owns the subsequent complete-fixture protection comparison. [Issue #38](https://github.com/whatobiplays/screen-latency-tool/issues/38) retains procurement decisions. This section evaluates candidates for owner review; it does not select parts, set a current limit, revise CAD or release any physical stage.

### 10.1 Native design boundary and fault domains

The native power/USB schematic is an off-board wiring design; it has no routed power carrier PCB. The optical schematic and two-layer board are a separate assembly. KiCad 10.0.8 exported netlists from disposable copies contain 14 power/USB components on 12 nets and 23 optical components on 9 nets. These counts and connections establish source consistency only. They do not establish the received Teensy revision, assembled wiring or physical electrical behavior.

The exported input path is J1 / 5V_IN → F1 MF-R050 → 5V_LIMITED → M1 Pololu 5383 → 5V_SAFE → XT30U J2/J6 → Teensy VIN. The Pololu module supplies reverse protection; it has no forward overvoltage cutoff or controlled current limit. The source-side segment between J1 and the external PTC remains exposed to source faults.

The Teensy input branches through its onboard fuse, ferrite and MOSFET to U4 and the MCU USB VBUS supply inputs. U4 revisions have a 5.5 V operating input maximum and a 6.0 V absolute maximum. The MCU USB supply inputs have a 5.5 V absolute maximum. The stricter 5.5 V ceiling applies to each exposed node.

The external optical board receives 3V3 directly from power/USB J4 pin 1; optical-board J1 pin 1 feeds its TIA, comparator, reference divider and trimmer. The current design has no separate 3.3 V branch switch. The input MF-R050 does not guarantee clearing every external 3V3 short. TPS2553-1 remains a branch-only candidate; it cannot protect the Teensy U4 output or internal MCU loads.

USB host VBUS remains a separate source: VUSB_RAW feeds the resistor divider and buffer, with the factory VIN/VUSB link required to be physically cut and verified before any future combined-source test. A protector added to VIN does not qualify the host VBUS path, GPIO injection, USB ground return or power-off behavior. No wiring or USB routing is changed by this investigation.

### 10.2 Supply envelope and static voltage screening

The relevant board limits are a 4.4 V minimum at the exposed MCU USB supply node during valid operation and a strict peak below 5.5 V after measurement uncertainty and positive design margin. The source must satisfy both limits after wiring, PTC, reverse-protection module, connector, board and protector losses.

The existing GST18U05-P1J specifies 5 V ±5% and 80 mV peak-to-peak ripple under its stated test setup. Its DC tolerance endpoints are 4.75–5.25 V. If the ripple is centered on the DC setting, adding half the peak-to-peak value gives 5.29 V high and 4.71 V low. Adding the full ripple as a conservative sensitivity gives 5.33 V high and 4.67 V low. These are calculation assumptions, not a guaranteed complete waveform; adapter startup, load steps, OVP latency, output capacitance and wiring transients remain unbounded here. Do not add tolerance terms twice.

The adapter OVP threshold is 5.5–7.0 V. Its maximum can exceed both U4’s operating maximum and the MCU’s 5.5 V absolute maximum. The adapter remains held unless a complete source-plus-protector envelope proves every exposed node stays within limits.

The GST18U datasheet lists a 30 ms output rise time at full load for both 115 VAC and 230 VAC input [P23]. That source figure does not bound startup at the protected fixture nodes or the response of an added protector.

TRACO TPP 15-105-J is a costed candidate for a fixed 5 V source. It is an encapsulated 5 V, 3 A, 15 W AC/DC module and remains unselected. Its datasheet lists ±1% set accuracy, 0.2% maximum line regulation, 0.7% maximum load regulation, and 40 mV peak-to-peak ripple typical with a specified 10 µF capacitor. At nominal AC input, 25 °C and full load, adding the positive set, line and load limits gives an analytical 5.095 V high estimate. Adding half the typical ripple gives a 5.115 V sensitivity, not a guaranteed waveform. At 70 °C, the datasheet’s ±0.02%/K temperature coefficient over 45 K raises the analytical high sensitivity to 5.140 V before ripple, or 5.160 V with the same typical-ripple assumption. Its 115–140% OVP range corresponds to 5.75–7.0 V and exceeds the MCU’s 5.5 V ceiling. The high-end output fault still needs a separately coordinated protector. Its output-current limit is specified at 120–200% of 3 A, with 145% typical; that range does not coordinate the source with the 0.41 A hot PTC. The module uses mains input pins and JST output pins, so it is not a finished desktop adapter. A fixed-source build would need a designed enclosure, mains wiring, mounting, strain relief and connector design. Before any source selection or build, the owner would need to review applicable mains-safety requirements, isolation and clearances, input protection, enclosure and connections. Its listing cost and availability appear in §10.6.

| Candidate | Datasheet-backed limit or timing | Static screening and disposition |
| --- | --- | --- |
| TPS26600PWPR, 16-pin HTSSOP PWP | Input range starts at 4.2 V; rising OVLO threshold is 1.17–1.225 V. | With a divider set so its lowest trip is above normal input, the threshold ratio is 1.225/1.17. The ideal high trip is 5.4968 V at a 5.25 V source maximum and 5.5387 V at 5.29 V. The former leaves about 3 mV before the 5.5 V ceiling; the latter exceeds it. Resistor error, leakage, transient response and layout reduce margin further. It cannot power a 3.3 V branch. It is not a drop-in fix for the existing source. |
| TPS259474ARPWR / TPS259474LRPWR, 10-pin VQFN-HR (2 × 2 mm) | Input range is 2.7–23 V; adjustable rising OVLO threshold is 1.183–1.223 V. OVLO response is 1.2 µs typical with no maximum in the table. The family lists a 0.5 A nominal minimum current setting; at RILM = 6.65 kΩ the electrical threshold table is 0.425/0.500/0.575 A min/typ/max. The A variant auto-retries after circuit-breaker faults; the L variant latches off. | The ideal high/low threshold ratio is 1.223/1.183 = 1.033812. A 5.29 V normal-high assumption implies a 5.4689 V upper trip corner, leaving about 31 mV before the MCU ceiling before resistor tolerance, leakage, overshoot or parasitics. At 5.33 V, the result is 5.5102 V and exceeds the ceiling. The 1.2 µs response is typical, not a maximum. At the 6.65 kΩ setting, the 0.425 A minimum threshold is only 15 mA above the MF-R050’s 0.41 A hold rating at 40 °C; this does not demonstrate coordination over tolerance, temperature or time. Neither variant is selected. |
| LTC4368IMS-2#PBF, 10-lead MSOP, with two external FETs and a shunt | OV comparator threshold is 492.5–507.5 mV. OV propagation maximum is 2 µs at 50 mV overdrive and 12 V input. Fast gate delay maximum is 6 µs with 2.2 nF gate capacitance under the datasheet test conditions. The current comparator and external MOSFETs form a circuit breaker, not a constant-current regulator. | Flexible dividers allow source-specific thresholds, but the exact 5 V divider, tolerance, MOSFET gate charge, source step, output capacitance, SOA and reverse-current case must be calculated together. Existing Issue #45 figures of 5.4729 V and 5.5143 V remain historical sensitivity results for their stated divider/ripple assumptions; they are not a validated protector waveform. The 12 V timing tests do not prove maximum cutoff at 5 V in this assembly. |

For the TPS26600 ratio, the calculation is Vtrip,max = Vnormal,max × 1.225/1.17. For TPS25947 it is Vtrip,max = Vnormal,max × 1.223/1.183. These ideal ratios omit divider tolerance, leakage, hysteresis, temperature, interconnect parasitics and dynamic overshoot. They screen candidates; they do not qualify one.

Normal lower headroom is also narrow. At the centered-ripple lower source estimate of 4.71 V, the prior 0.25 A example and 0.77 Ω maximum initial MF-R050 resistance consume 0.1925 V, leaving 4.5175 V before the Pololu module, added protector, connectors, wiring and the Teensy input path. That is 117.5 mV above the 4.4 V MCU-node minimum. The full-ripple sensitivity lower estimate of 4.67 V leaves 4.4775 V, or 77.5 mV above the minimum. The 0.25 A is illustrative, not a load bound. The unknown installed load and additional drops prevent a normal-operation pass.

### 10.3 Current, energy, timing and thermal screens

The MF-R050 is rated for 0.5 A hold at 23 °C and 0.41 A hold at 40 °C. Its 4 s maximum trip-time entry is for 2.5 A at 23 °C; it does not promise clearing at 1 A or at another temperature. The adapter can source up to 3 A under its rating, and the pre-PTC segment has no downstream fuse. Its short-circuit exposure is not bounded by the present drawings.

As a shunt sensitivity only, WSL2512R1800FEA is a 0.18 Ω, ±1%, 1 W, 2512 candidate. At the LTC4368’s datasheet short-circuit test threshold range of 30–70 mV, the resistor-only current interval is about 0.165–0.393 A. At the 40–60 mV comparator range, it is about 0.220–0.337 A. Those figures use the datasheet’s 12 V and zero-output test conditions, not a 5 V circuit guarantee. The low corner could nuisance-trip an unknown startup load; the high corner, propagation, gate discharge and harness current still require a complete tolerance/SOA analysis. The resistor is not selected.

TPS259474’s nominal 0.5 A minimum current setting cannot be treated as protection for the existing 0.41 A hot PTC. At the datasheet’s 6.65 kΩ setting, its current-threshold table spans 0.425–0.575 A (0.500 A typical), putting the minimum threshold only 15 mA above the PTC hold rating. The PTC trip curve, device threshold tolerances, thermal coupling and relative trip times are not coordinated by that comparison. With 45 mΩ used as a conservative on-resistance sensitivity, 0.5 A gives 22.5 mV and 11.25 mW in steady conduction. If a short collapses the output while the device limits at 0.5 A from 5.5 V, its instantaneous dissipation can approach 2.75 W before the configured timer, circuit-breaker response or thermal shutdown changes the state. That is a power estimate, not an allowed thermal operating point or a guaranteed clearing time. The PCB copper, actual junction rise, retry behavior and fault energy remain unknown.

For TPS2553DRVR-1, the DRV package is a 6-pin 2 × 2 mm WSON. Its 75 mA–1.7 A adjustable current-limit range is typical, and its 2 µs short-response time is also typical; neither guarantees a branch fault limit or maximum isolation time. At 100 mA, 150 mΩ gives a 15 mV drop and 1.5 mW conduction loss under the specified resistance conditions. A 15 mV drop at the optical 3V3 rail would shift the ideal 18 kΩ/10 kΩ VREF divider by about 5.36 mV. This is a static sensitivity calculation; it does not model the TIA, comparator, decoupling, transient response or optical timing. A branch setting must be above the measured load/startup envelope and below the weakest branch conductor/contact exposure.

Bound capacitor energy as Ecap = ½ C V². At 5.5 V this is 15.125 µJ per µF; 100 µF would store 1.5125 mJ and 1 mF would store 15.125 mJ. Installed capacitance and ESR are not established. Total exposure also includes source energy, Esource = integral(V × I × dt), and resistive heating, integral(I² × R × dt). Without a guaranteed maximum current and cutoff time, source-fed fault energy has no finite bound in this record. Repeated retries add repeated source and device heating; the TPS259474A 110 ms retry interval is typical, while the L variant remains latched until reset. No retry or thermal cycling has been physically tested.

### 10.4 Fault coverage and weakest-element exposure

| Fault | Present weakest or exposed element | Candidate protection contribution | Remaining bound |
| --- | --- | --- | --- |
| Short at J1 or before the input protector | Source, plug/jack contact, source lead and pre-PTC conductor | Verified source current/short behavior; an added protector must be placed at the source boundary. | No current, clearing-time, connector-temperature or I²t bound is established for the pre-PTC segment. |
| Short on 5V_LIMITED, 5V_SAFE or VIN | PTC, protection module, XT30 contacts/wires, onboard fuse path and actual board copper | LTC4368 plus FET/shunt or a TPS25947 circuit-breaker variant could interrupt selected faults if setpoints, time and SOA close. | Current thresholds, source/output capacitance, cutoff, repeated-fault energy, path voltage drop and weakest conductor are not yet coordinated. |
| Short on external 3V3 | U4, external 3V3 wiring/contacts, optical PCB trace and output loads | TPS2553-1 could isolate only the external branch after measured load and current-limit margins are defined. | The present input PTC need not trip. A branch switch cannot protect internal U4 or MCU loads. |
| Source overvoltage or wrong voltage | U4 input and the MCU USB supply inputs, each with the stricter 5.5 V ceiling | Adjustable OVLO/disconnect or a source with a guaranteed tighter envelope. | OVP accuracy, resistor corners, source transient, response delay and residual output charge are open. A nominal TVS marking is not a clamp guarantee. |
| 5 V to 3V3 cross-rail, powered-off injection or open return | MCU/logic pins, U4 body paths, buffer and alternate ground paths | Reverse blocking and branch isolation may reduce selected paths. | Pin injection, powered-off rise, GPIO/USB ground return and return interruption need separate circuit-level analysis. |
| Host VBUS coexistence | VUSB_RAW, buffer input/output, factory VIN/VUSB link and USB ground/shield | A VIN protector does not replace the required physical VIN/VUSB separation. | Verify the physical cut and powered-off rail behavior before any future authorized USB test. |
| Hotplug, brownout and repeated fault | Input capacitance, connectors, protection pass element and optical rail | Soft-start, UVLO, latch-off or controlled retry can address defined modes. | Actual waveform, capacitance, contact bounce, controller state and accumulated thermal exposure remain unknown. |
| Reversed S1 leads, wrong header pin, adjacent-pin bridge or live reconnection | Temporary S1 leads and adjacent Teensy header pins | De-energized inspection, mechanical support/insulation and the no-live-reconnection rule. | These remain the narrow S1 procedural exclusions accepted in Issue #47. They are not automatically protected. Do not extend the exclusions to full-fixture wiring or use them to claim hardware protection. |

### 10.5 Architecture comparison and engineering preference

| Option | Architecture and candidate parts | Advantages | Weakest gaps, physical topology, USB, service and cost |
| --- | --- | --- | --- |
| A. Qualified bench-supply-only development | Continue with the selected KORAD KA3005PE+ for the narrowly bounded Teensy-only route. No fixed adapter is admitted. | No new protection circuit or fixed-source BOM is needed for the source investigation. Independent S0 qualification can establish the bench supply’s actual CV/CC/OVP/OCP behavior before the separately approved S1 decision. | The source, temporary conductors, returns, cutoff and stored-energy bounds are still unqualified. This does not qualify the complete optical fixture, USB coexistence or fixed adapter. Incremental hardware cost is zero only if inventory confirms the selected supply, leads, dummy load and measurement equipment are available. |
| B. Fixed 5 V with coordinated discrete input and branch protection | A new fixed source with a documented normal/fault envelope; the cost-screened TRACO TPP 15-105-J is one unselected source candidate. Add LTC4368IMS-2#PBF; two candidate SiR870ADP-T1-GE3 PowerPAK SO-8 FETs; a candidate WSL2512R1800FEA shunt; and TPS2553DRVR-1 on the external optical branch. Each value and package remains provisional. SiR870ADP specifies 10.5 mΩ maximum at 4.5 V gate drive and 15 A test current; this is not a linear-mode SOA rating. | Adjustable OV/UV thresholds, an external bidirectional breaker and an independently controlled external 3V3 branch allow separate coordination. FETs, shunt and fault state can be instrumented on a dedicated, low-inductance PCB. | Divider, sense, gate-drive, inrush, FET SOA, sense error, temperature and source tolerances must close together. The candidate shunt can trade nuisance-trip margin against the 0.41 A PTC limit. The PTC and pre-PTC segment still need coordination. This needs a new power PCB or validated supported assembly, short input/output paths, copper and thermal review. USB remains separately isolated. A device-only retail subtotal for one controller, two FETs, one shunt and one branch switch was about $16.09 at the dated listings; adding the screened source gives $61.06 for those items, before passives, PCB, connectors, enclosure or assembly. |
| C. Tighter fixed source with integrated input eFuse and branch switch | Replace or separately qualify the present adapter; screen the TRACO TPP 15-105-J as an unselected 5 V source candidate with TPS259474LRPWR latch-off or TPS259474ARPWR auto-retry at VIN, and TPS2553DRVR-1 only on the external 3.3 V branch. TPS26600PWPR remains a comparison candidate, not a proposed drop-in. | Fewer external power-path devices and a small input board may reduce assembly count. The L variant avoids automatic restart after a circuit-breaker fault; the A variant automatically retries. A separate branch switch keeps optical loads independently observable. | The TPP 15-105-J’s tighter normal output screen does not solve its 5.75–7.0 V OVP range or its 3.6–6 A output-current-limit range. TPS25947 static OVLO headroom fails the conservative 5.33 V sensitivity and leaves only about 31 mV under the centered-ripple case. Its 0.425–0.575 A threshold range at the nominal 0.5 A setting does not establish coordination with the 0.41 A hot PTC. TPS26600 has only about 3 mV ideal static headroom at 5.25 V and fails at 5.29 V; it also cannot operate on 3.3 V. A new source still needs exact maximum output, ripple, startup, fault energy and thermal data. WSON parts require a designed PCB and thermal land; they are not field-wired protection modules. USB and branch behavior remain separate. One eFuse plus one branch switch totaled about $2.78–$3.01 on observed single-quantity listings; adding the screened source gives $47.75–$47.98 for those items, before passives, PCB, enclosure or assembly. |

**Engineering preference for the next decision:** keep Option A as the only development path to advance through source qualification while the full fixture stays held. If a fixed supply is needed for the one-sensor fixture, evaluate Option B and Option C only with a tighter, explicitly bounded source. The current GST18U05-P1J does not qualify. Option C has a smaller nominal component count, but its static OVLO ratio leaves too little margin for the conservative ripple case; current data does not establish that either fixed-source option passes. This is a proposal for owner review, not an architecture decision.

The existing optical layout is a sensitive analog path. Any future branch protector adds resistance, output capacitance, switching transients and fault/restart edges to the 3V3 rail. Check optical 3V3, VREF, TIA output, comparator threshold, EDGE timing and power-up/brownout behavior against the actual analog model and layout. The existing Gate A optical simulation is historical and its model/provenance inputs are unavailable for rerun; it cannot qualify a new branch protector. No routed power PCB exists for candidate placement, copper, thermal relief or return-path analysis.

### 10.6 Sourcing and cost snapshot

The following DigiKey listing observations were read on 2026-11-13. Prices are USD for one unit, before shipping, tax, tariff, re-reeling, passive components, PCB and assembly. Stock and lead-time fields are supplier snapshots, not manufacturer guarantees, reservations or purchase approval.

| Candidate | Observed single-unit price and availability | Procurement implication |
| --- | --- | --- |
| [GST18U05-P1J](https://www.digikey.com/en/products/detail/mean-well-usa-inc/GST18U05-P1J/7703631) | $14.10; 174 shown in stock; 17-week manufacturer lead-time field. | Existing design source; remain held because its OVP range reaches 7.0 V. Price does not resolve electrical suitability. |
| [TPP 15-105-J](https://www.digikey.com/en/products/detail/traco-power/TPP-15-105-J/9565862) | $44.97; 3 shown in stock, 97 shown as factory stock; 19-week manufacturer lead-time field. | Cost-screened 5 V/3 A module candidate with better normal set accuracy. Its 115–140% OVP, mains/JST-pin integration, and applicable mains-safety requirements remain open. Review isolation, clearances, input protection, enclosure and connections before any selection or build. It is not selected or purchase-approved. |
| [TPS259474ARPWR](https://www.digikey.com/en/products/detail/texas-instruments/TPS259474ARPWR/14124024) | $1.95; 0 shown in stock; 3,000 expected 2026-11-25; 26-week lead-time field. | Auto-retry circuit-breaker variant. Expected date can move; no approval to buy. |
| [TPS259474LRPWR](https://www.digikey.com/en/products/detail/texas-instruments/TPS259474LRPWR/14123976) | Listings showed $1.72–$1.95 and about 39,855–41,665 units; listing lead-time fields differed. | Latch-off variant. Resolve the exact supplier SKU and current terms before any later procurement request. |
| [TPS2553DRVR-1](https://www.digikey.com/en/products/detail/texas-instruments/TPS2553DRVR-1/2047903) | $1.06; 3,383 shown in stock; 26-week lead-time field. | Candidate external-branch switch only. The optical load and minimum safe current setting are unverified. |
| [LTC4368IMS-2#PBF](https://www.digikey.com/en/products/detail/analog-devices-inc/LTC4368IMS-2-PBF/6820038) | $8.05; 717 shown in stock; 25-week lead-time field. | Controller only; it also needs the FET pair, shunt, divider, timer/control passives and board. |
| [SiR870ADP-T1-GE3](https://www.digikey.com/en/products/detail/vishay-siliconix/SIR870ADP-T1-GE3/2813601) | $3.02 each; 22,757 shown in stock; 55-week lead-time field. | Candidate FET only. Two devices are included in the B subtotal; linear SOA and gate-drive behavior still need review. |
| [WSL2512R1800FEA](https://www.digikey.com/en/products/detail/vishay-dale/WSL2512R1800FEA/9758409) | $0.94; 1,589 shown in stock; 53-week lead-time field. | Candidate shunt only. Its calculated current interval is a screening example, not an approved setpoint. |
| [TPS26600PWPR](https://www.digikey.com/en/products/detail/texas-instruments/TPS26600PWPR/6199305) | $3.79; 0 shown in stock in the listing. | Comparison candidate with insufficient headroom on the present source. |

TRACO TPP 15-105-J is the cost-screened fixed-source candidate documented above; it remains unselected, and its OVP range still exceeds the MCU ceiling. Its supplier price and availability are dated listing observations, not an order quote. Installed-system cost remains open because passives, input protection before the board, PCB, enclosure, connectors and assembly are not specified. Recheck lifecycle, seller, exact order code, packaging, lead time, price and availability after the owner selects an investigation path. No BOM MPN or purchasing state changed.

### 10.7 PAC/TAC evidence matrix

Issue #47 owns acceptance status and readiness. This crosswalk points to the technical evidence and unresolved engineering bounds recorded in §§10.1–10.6; it does not track issue completion.

| PAC | Technical acceptance checks (TAC) | Evidence recorded in this section | Remaining engineering evidence or decision |
| --- | --- | --- | --- |
| PAC-1: baseline and choice space | TAC-01: compare the native KiCad source, exported nets, current BOM/generator/checker, Teensy revision-dependent VIN/U4/MCU path, optical loads and separate USB VBUS domain. | KiCad 10.0.8 disposable-copy exports show 14 power/USB components on 12 nets and 23 optical components on 9 nets. Manufacturer limits are source-backed; the received Teensy revision and assembled path are unknown. | Use the received board revision and assembled path for any future physical assessment. Whether a separate fixed adapter is needed for one-sensor feasibility remains an owner decision. |
| PAC-2: bounded normal/fault envelopes | TAC-02: calculate tolerance and ripple corners, path drops, OVLO/circuit-breaker thresholds, current ranges, interruption times, source/capacitor energy, thermal dissipation and weakest conductors for all Issue #47 faults. | §§10.2–10.4 record source tolerance/ripple sensitivities, candidate threshold/current calculations, capacitor-energy and branch-drop estimates, and a map of the required fault groups. The existing GST18U05-P1J adapter path exceeds the 5.5 V board ceiling at its 7.0 V maximum OVP threshold. Its datasheet lists a 30 ms rise time under stated full-load conditions; this does not bound the protected fixture startup waveform. TPS26600 and TPS25947 static screens leave inadequate margin at the stated ripple sensitivities. | No B/C path has complete quantitative normal/transient/current/cutoff/source-energy/repeated-fault/thermal/SOA or weakest-device/conductor bounds. Candidate-source startup, load-step, installed capacitance and physical behavior remain unbounded for the protected path. |
| PAC-3: compare real alternatives | TAC-03: compare A/B/C using exact candidate MPN/package, branch limits, source availability, cost, layout, service, optical noise/timing and USB coexistence. | §§10.5–10.6 compare A/B/C, exact candidate packages and sourceability. TRACO TPP 15-105-J is costed as an unselected source candidate; its OVP still exceeds the MCU ceiling and its mains/module integration is not qualified. | No architecture, part or BOM row is selected. Installed-system cost, lifecycle and availability need a fresh source check; optical noise, USB coexistence and serviceability remain unmeasured. |
| PAC-4: decision-ready evidence | TAC-04: maintain separate guaranteed, typical, analytical, modeled, simulated and unknown-physical rows; run only supported models; show coverage and unperformed checks. | §§10.2–10.4 distinguish datasheet limits, typical values, calculations and unknown physical behavior. TI’s TPS25947x PSpice model Rev A targets PSpice 17.2 and does not model temperature dependence. The ngspice 47 compatibility probe failed before transient analysis because of unsupported directives and unresolved references; no candidate SPICE result is available. Thermal and EMC analyses have not been run because no candidate circuit or routed protection PCB exists. | A compatible simulator/model and a defined candidate circuit are needed for protector simulation. Thermal and EMC analysis needs an actual PCB and stackup. Physical claims require separately authorized measurement. |
| PAC-5: durable record and preserved history | TAC-05: retain the limited S1 procedural-fault exclusions and rationale, the Issue #45 verdict, disputed review evidence, and prior model assumptions. | §10.8 records the exclusions, Issue #45 conditional verdict, disputed Cubic/Charizard reports and model limitations. Issue #45 and PR #46 historical pins are unchanged; no Issue #45, PR #46, SPEC-X-004, CAD, BOM or generator content was changed. | Preserve those boundaries and provenance in any later architecture decision or physical qualification record. |
| PAC-6: owner handoff | TAC-06: present the conditional Option A preference, B/C comparison, smallest later procurement scope, physical prerequisites and owner decision points. | §10.8 records the owner decision questions, the proposed next paper work, the smallest possible later S0 equipment-only request and the conditions for any future S1 or fixed-source path. No purchase or physical release is authorized here. | Architecture, procurement, fabrication and physical admission require their separate owner decisions. |

### 10.8 Owner decisions, procurement boundary and preserved evidence

The owner decision package asks whether the bench-supply route remains the only development path; whether a fixed source is needed for one-sensor feasibility; which defined external faults must be contained by hardware; and whether the source envelope can be met with a discrete LTC4368 design or an integrated eFuse and separate 3V3 branch. TRACO TPP 15-105-J is costed for comparison, but its OVP range reaches 7.0 V and its mains-input module needs a separate enclosure and connector design. Applicable mains-safety requirements, isolation, clearances, input protection and connection details would need owner review before any fixed-source selection or build. The suggested next step is paper qualification of Option A and continued source screening for B/C. No circuit or MPN is approved by this recommendation.

The smallest possible later procurement review is an inventory-based S0 equipment-only request for a qualified dummy load, leads and measurement access if the owner finds a real shortfall. S1 would require a separate decision for one identified DEV-20360, exact rated and supported temporary conductors/contacts, completed S0 evidence, a bounded source/exposure envelope and separate owner admission. A fixed-source route would first require an owner-selected source and closed circuit, fault, thermal and optical review. None of these lists is an order authorization. Full Basket A, the fixed-adapter chain, PCB fabrication, assembly and physical use remain held.

Issue #47 retains the narrow accepted S1 procedural exclusions: reversed temporary leads, wrong header pin or adjacent-pin bridge, and live reconnection are controlled by de-energized inspection, supported insulated connections and a no-live-reconnection rule. They are not automatically protected faults. Catastrophic source failures outside the characterized envelope and internal Teensy failures remain disclosed residual risks. These exclusions do not apply to the full fixture and do not weaken the unchanged physical admission holds.

The original Issue #45 conditional paper verdict and its qualifications remain intact. The missing numerical physical exposure envelope remains a real S1 blocker. The archived Cubic v4 objection to the conditional paper verdict remains disputed by the author and the non-authoring Charizard review; Issue #47 does not resolve that dispute. Preserve the reports unchanged: Cubic full v4 SHA-256 3a9a94817b55549360688f08bb25e7413c7b108b8e40bed1bdf3537d0c3d7ae3 and Charizard full review v4 SHA-256 bf018f3f9a71ea76e7b7a64ede428bb7685ab5e829846373d421f567220a3022. The [Issue #45 post-merge adjudication](https://github.com/whatobiplays/screen-latency-tool/issues/45#issuecomment-6868762284) records the owner’s S1 decision and links the [PR #46 review discussion](https://github.com/whatobiplays/screen-latency-tool/pull/46#issuecomment-6868609880). Full review files remain in the owner-held Issue #45 archive and are not stored in this repository.

No candidate simulation is physical qualification. The ngspice compatibility probe stopped before transient analysis, so no active-protector simulation result is available. Any later model must identify the exact MPN/package, manufacturer model revision, circuit and testbench assumptions, source/capacitance corners and simulator version. Thermal and EMC conclusions require the actual designed PCB and applicable models. The unchanged physical state is INITIAL_CURRENT_LIMIT=UNSET; S0–S4 NOT_RUN/UNVERIFIED; FABRICATION HOLD; all procurement, fabrication, soldering, connection and energization holds remain in force.

## 11. Supervised one-sensor bench release

The expanded Issue #47 coordinates the selected one-sensor build through owner
assembly and genuine smoke observations. The owner-selected noncontact mount
and manual optical/USB session separation preserve the existing optical PCB.
The owner-selected electrical architecture is KA3005PE+ → supported terminal/carrier →
MF-R050 → Pololu 5383 → exact XT30U pair → Teensy VIN. The barrel jack and fixed
adapter are not fitted. The source remains the sole operating VIN supply;
Android VBUS remains isolated sensing, with a physically verified VIN/VUSB cut.

The [construction and admission procedure](../../hardware/prototype/power-usb/bench-assembly.md)
defines the physical carrier/harness, source-backed limits, proposed screens,
measurement capability and G0–G4/S0–S4 dependency order. The
[noncontact mount record](../../hardware/prototype/optical/bench-mount.md)
defines geometry, a numerical tolerance/movement budget and the manual-pattern
session. Its allocation arithmetic is analysis, not measured fit. No universal
2 mm gap, scope bandwidth or thermal threshold is imposed.

### 11.1 Decision and fault scope

The owner [accepted G0 on 2026-11-14](https://github.com/whatobiplays/screen-latency-tool/issues/47#issuecomment-6874347712),
selecting the direct-bench construction, supported/insulated connections,
narrow procedural exclusions and residual component-damage risk. This includes
Pomona 73093-2/-0 solderless push-tab plugs with the selected 18 AWG stranded
leads, at most 1 m per lead, and separate supported WAGO potentials. The plug's
18–22 AWG / 4–6 mm strip procedure, retention and unpowered verification are
defined in the construction record. G0 accepts a design and risk model; it does
not establish a qualified source envelope or authorize physical use.
De-energized inspection and no-live-rewiring control reversed temporary leads,
wrong/adjacent header connections and preventable cross-rail assembly faults.
Normal-operation displaced wires, sustained shorts, unsafe local heating,
powered-off injection and host backfeed still require a defensible control.
If that control is missing, stop for a specific amendment, not a blanket waiver.

B/C are deferred alternatives outside this selected bench-only build. Their old incomplete
envelopes and failed simulation probes stay recorded. The GST18U05-P1J OVP
failure is unchanged. MF-R050 still cannot be credited with clearing every 3V3
fault and Pololu 5383 adds no forward OVP/CC. Do not substitute another module
without an exact owner-reviewed amendment; Pololu 5382 is only a named fallback
candidate if sourceability of 5383 is insufficient.

### 11.2 Evidence and release boundaries

Use the [commissioning record template](../../hardware/prototype/power-usb/commissioning.template.json)
for actual configurations, raw files/hashes, approvals, numerical settings and
observations. Missing receipt, instrument identity, source response and fit
measurements remain unknown. INITIAL_CURRENT_LIMIT remains UNSET until actual
source qualification and independent setting review. Source-only S0 approval
never releases a DUT by itself.

Exact evaluation procurement can be considered before physical characterization;
Issue #38 remains the exact basket/fabrication authority. The independent mount
removes contact-force/compression as first-prototype requirements. Source-bound
CAD/CAM plus justified design fit may support fabrication approval while
post-assembly verified fit stays held. The earlier contact-boot hold is not a
requirement to reopen that rejected arrangement.

USB smoke uses the existing foreground probe, genuine Full-Speed/identity,
real HID press/release and vendor exchange, with rediscovery between operations.
The separate manual-pattern session uses actual display light and SFH 203/TIA/
EDGE observations. HID does not have to cause the display transition. Neither
session establishes calibrated latency, five-channel capture, bus T0 or Issue #7
optical calibration. Actual issue/PAC progress and owner decisions belong in
Issue #47 and PR #48; this record contains durable rationale and controls.
