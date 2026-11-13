# Pre-purchase first-power feasibility and protection decision

**Document ID:** BENCH-005\
**Status:** Draft for owner design review\
**Owner:** Repository maintainers\
**Last Updated:** 2026-11-13 (America/Los_Angeles)\
**Depends On:** ARCH-001, SPEC-X-001, BENCH-001, BENCH-002, BENCH-003, BENCH-004\
**Supersedes:** None\
**Superseded By:** None

## 1. Decision and acceptance boundary

**Pre-purchase verdict: PAPER FIRST-POWER PATH CONDITIONALLY FEASIBLE — OWNER DESIGN REVIEW REQUIRED.** The conditional paper route is limited to S0 source qualification followed by an unchanged Teensy-only, bench-supply S1 attempt under the defined external fault bounds in §5. It does not promise successful boot or qualify the source/temporary connection already. Physical admission remains closed until every parameter and preflight condition is demonstrated and approved. The fixed-adapter design has a separate overvoltage incompatibility and incomplete fault coordination; it remains held. No initial current, duration or energy limit is selected here.

The acceptance standard is rated normal operation and bounded exposure to defined external source and wiring faults. Internal Teensy failures are assessed as residual risks for owner review. A guarantee against damage from every internal board failure is not required. Unknown startup demand may be an output of an independently bounded S1 attempt; it is not an automatic prerequisite to designing that attempt.

Absent S0 measurements alone do not invalidate a conditional paper route. This paper pass rests on a documented nominal 5 V VIN supply role, a nonempty 4.4–5.5 V internal USB-supply operating window before losses/margins, a selectable source with output/CC/OCP controls, and the independently testable admission constraints in §5. No published limit establishes an impossible window for that limited arrangement. A paper pass requires acceptance limits derived independently of unknown startup demand and a practicable way to demonstrate the remaining conditions before connecting the Teensy. A source feature label or a future instruction to stop if damage appears cannot supply a missing exposure bound.

[Issue #45](https://github.com/whatobiplays/screen-latency-tool/issues/45) owns this decision. [Issue #38](https://github.com/whatobiplays/screen-latency-tool/issues/38) owns subsequent purchase approval. All component, equipment and PCB orders remain paused pending the power decision and explicit owner authorization. Preserve `INITIAL_CURRENT_LIMIT=UNSET`, FABRICATION HOLD and every Stage-B/C gate. This assessment authorizes no physical operation, substitute part or architecture change.

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


## 8. Purchase and owner decisions

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
