# Prototype 5 V connected-load and fault budget

Document ID: BENCH-004

Status: Draft

Owner: Repository maintainers

Last Updated: 2026-11-02 (America/Los_Angeles)

Depends On: ARCH-001, SPEC-X-001, PHASE-001, BENCH-001, BENCH-002, BENCH-003

Supersedes: None

Superseded By: None

**Disposition: PAPER-ONLY ANALYSIS / NUMERICAL CURRENT LIMIT UNSET / FIRST-POWER RELEASE NOT DEMONSTRATED.** This is the canonical load, fault, and protection-coordination reference for the provisional one-sensor [Issue #3](https://github.com/whatobiplays/screen-latency-tool/issues/3) bench. It is not a design-freeze, order, fabrication, assembly, USB, energization, first-power, or qualification authorization. Actual input/startup currents, cable and contact temperatures, and VIN/VUSB isolation have not been measured. GitHub Issue #3 remains the only operational readiness tracker.

## 1. As-designed nets and accounting boundary

Read-only design evidence: [external power and USB-sense P0 KiCad schematic](../../hardware/prototype/power-usb/power-usb.kicad_sch), [optical P0 schematic](../../hardware/prototype/optical/one-sensor.kicad_sch), [routed optical candidate](../../hardware/prototype/optical/one-sensor.kicad_pcb), [instance BOM.csv](../../hardware/prototype/BOM.csv), and [engineering notes](../../hardware/prototype/README.md). These are two *separate* proposed assemblies, not a completed carrier. Power `J1` (adapter input) is distinct from optical `J1` (3V3/EDGE/MON). The proposed OSH Park set has **one board planned for population and two bare spares**; no purchase or physical assembly is evidenced. The future five-channel fixture is not modeled.

| Segment | Actual net / proposed current path | Accounting and safety interpretation |
| --- | --- | --- |
| Power `J1` Adafruit 368 | Center-positive external DC input → `5V_IN`, return GND | Full 5 V source current; barrel, screw terminal, cable and mating ratings unknown |
| `F1` Bourns MF-R050 | `5V_IN` → `5V_LIMITED` | **Series** thermal PTC; same path as J1, not an independent current sink |
| `M1` Pololu 5383 | `5V_LIMITED` → `5V_SAFE`, GND | Reverse-polarity/reverse-current protection; not current limiting or USB isolation |
| Power `J2` TSW-102 | `5V_SAFE` and GND → Teensy `VIN` and GND | **Entire** Teensy VIN input, not just optical-current demand; exact mate/lead rating unknown |
| Teensy internal power | VIN → onboard 3.3 V regulator, MCU and other board loads | Intrinsic input/startup draw and input-equivalent external 3V3 load **unknown** |
| Optical `J1` TSW-104 | Teensy 3V3/GND → optical `U1`/`U2`/dividers, with `EDGE`/`MON` returns | Optical circuit on 3.3 V, not 5V_SAFE directly; offboard signal/ground loading unknown |
| Power `J4` TSW-103 | 3V3/GND for `U1` buffer and `VBUS_PRESENT` logic output | Count buffer and output load **once** in 3.3 V budget, not again as an additional J4 load |
| Power `J5` TSW-102 | Teensy `LED_GPIO` → `R2` 1 kΩ → `D1` WP7113ID → GND | 3.3 V GPIO-sourced current, not direct 5 V current; GPIO drive limit applies |
| Power `J3` and `R1`/`R3` | Android USB `VUSB_RAW` → 1 kΩ → `VUSB_BUFFER_IN`, 47 kΩ to GND; TI buffer `U1` supplied by Teensy 3V3 | **Separate USB-host VBUS source**; resistor input current must never be counted in MF-R050 or fixed-adapter load; VIN/VUSB trace must be physically severed and tested |

The two schematics' placed symbol references, labels and net endpoints establish **proposed connectivity**, not the installed wiring or current rating. Only the manufacturer sources listed in section 9 support component specifications. No manufacturer document independently verifies the assembled power path.

## 2. 3.3 V loads: source-backed and still unknown

| Load / rail | Supported reference value or first-order derivation | Worst-mode / physical gap | Source |
| --- | --- | --- | --- |
| Teensy 4.1 onboard functions and VIN-to-3.3 V regulation | Total intrinsic `I_Teensy,base(VIN, firmware, clock, temperature)`: **unknown**. PJRC lists multiple U4 regulators across production revisions (see §2.1). | No documented complete-board worst-case VIN startup or 3V3 regulator junction/PCB thermal envelope. **250 mA external 3V3** allowance is not total VIN current. PJRC's 100 mA running example specifically names **Teensy 4.0**, not a certified Teensy 4.1 input budget | [S1], [S11], [S12] |
| Optical `U1` OPA320 | 1.45 mA/ch typical headline; TI `I_Q` at **5.5 V/no output load**: 1.5 mA typical, 1.75 mA max at 25 °C, 1.85 mA max over −40 to 125 °C | Driven TIA and MON current, actual 3.3 V/temp operating point, output transients **unknown** | [S5], electrical table p. 9 |
| Optical `U2` TLV3501, SHDN grounded (enabled) | 3.2 mA typical, 5 mA max **at 5 V, output high** | 3.3 V switched-load and EDGE current/time envelope **unknown**; cannot rename 5 V max as a guaranteed total at 3.3 V | [S6], table p. 5 |
| Optical `R1` 18 kΩ + `R2` 10 kΩ VREF series divider | `3.3 V / 28 kΩ = 0.117857 mA`, ideal static | Input bias, loading and resistor tolerance alter current | optical P0 net labels |
| Optical `RV1` 10 kΩ trimmer 3V3–GND | `3.3 V / 10 kΩ = 0.330000 mA`, ideal static | `R7` 1 MΩ VREF–THRESH redistributes branch current (ideal upper difference `3.3 V/1 MΩ = 0.003300 mA`); **not another independent rail branch** | optical P0 net labels |
| Optical `R3`/`C2` feedback, `R4` DNP, `R5` 100 Ω EDGE and `R6` 1 kΩ MON | No separate independent static 3V3 load in the ideal zero-output-load model | Unknown offboard input impedances, photodiode current, output drive and contention. A shorted EDGE after R5 could *request* nominal `3.3/100 = 33 mA` from comparator: **fault example, not safe/qualified** | optical P0, [S5–S6] |
| Power `U1` SN74LVC1G17 on 3V3 | `I_CC` ≤10 µA **with output unloaded and input at GND or rail** | Switching frequency, CMOS `ΔI_CC`, output drive and VBUS power-off injection unknown. `I_off` ≤±10 µA at 0 V VCC and VI or VO=5.5 V is **IC-level**, not whole-system no-backfeed | [S7], DC table p. 6 and ICC–frequency curve p. 12 |
| Power `J5` LED (WP7113ID plus 1 kΩ R2) | Off: 0 mA ideal. Illustrative on: `(3.3−1.9)/1000 = 1.4 mA` using Kingbright 1.9 V typical Vf **specified at 10 mA**, not a measured operating point near 1.4 mA | Conditional resistor-only ceiling `3.3/(1000×0.99)=3.333 mA` if high output ≤3.3 V and LED forward drop ≥0; PJRC recommends **4 mA maximum GPIO drive**. Physical current, lead state and temperature unknown | [S1], [S8] |
| Optical `J1` / logic `J4` / LED `J5` cables | No additional modeled loads beyond their attached branches | Exact mates/terminals, ESD/EMC, extra attached peripherals, ground/return routing and driven output contention remain **unbounded** | physical circuit not assembled |

**Partial arithmetic only (not system upper bounds):**

- Ideal divider plus trimmer from nominal 3.3 V: `0.117857+0.330000=0.447857 mA`. `R7` is an internal VREF–THRESH load, not a separately fed supply rail.
- Mixing **non-matching test conditions** for illustration gives `1.45+3.2+0.010+0.447857+1.4=6.507857 mA` for two quiescent ICs, unloaded buffer maximum, ideal passives and LED-on *illustration*. This is **not** a validated 3.3 V typical/maximum: OPA/TLV figures have different supplies, unknown output loads and no MCU contribution. It is **prohibited** as a first-power current-limit setting.
- PJRC's **250 mA recommended external 3.3 V load ceiling** applies to *all externally powered 3.3 V components combined*, including optical/LED/buffer and any additional load. It is neither actual external consumption nor a safe adapter current limit. All internal CPU/regulator consumption must be accounted separately.

### 2.1 Teensy 4.1 regulator revisions and LDO heat (conditional, not a board rating)

**Manufacturer revision evidence narrows the physics but not the as-received unit.** [PJRC's Teensy 4.1 design-change log](https://www.pjrc.com/store/teensy41.html) [S11] identifies **TLV75733P** on early U4 boards, **NCV8186AMN330TAG** after March 2022, and **TLV75533P** after March 2023. The model of the selected SparkFun DEV-20360 does **not** attest which regulator, PCB revision or assembly is delivered. Record photos/markings, the **installed regulator's full orderable suffix/package**, and a dated source match **before** using a regulator-specific current, dropout, foldback or thermal limit. The [PJRC product page](https://www.pjrc.com/store/teensy41.html) [S1] says *Teensy 4.0* draws approximately **100 mA** at 600 MHz, despite appearing under the Teensy 4.1 power heading. It is only a **related-board operating reference**: **not a 4.1 worst-case or startup VIN guarantee**, and not to be added to a current-limit interval as if it were a bound.

**If and only if the installed U4 is a TI TLV75533P:** [TI TLV755P Rev. D](https://www.ti.com/lit/ds/symlink/tlv755p.pdf) [S12] supports the following *device-level* statements, not a Teensy-system qualification:

| TI characteristic / source | What may be derived | What it does not establish |
| --- | --- | --- |
| TLV755P-family input operating range **1.45–5.5 V**, fixed **3.3 V** output option, nominal **500 mA** output capacity, and soft-start | Nominal 5.0 V input is a **candidate voltage target** for later approved external-only VIN testing. The specified 3.3 V output also requires sufficient **input-to-output dropout headroom** at actual load, temperature and wiring drop | The 1.45 V family input minimum does **not** mean a 3.3 V variant regulates at 1.45 V. Neither 500 mA output capability nor 1.45 V input minimum is a board-wide safe input-source current/voltage setting |
| Ground/quiescent current up to 40 µA at 0 mA output under specified conditions; output current limit **560–865 mA** at TI test conditions; short-circuit current **355 mA typical** | For a linear regulator, **additional 3.3 V output load draws approximately the same additional input current**, plus incremental ground current (not the 3.3/5 efficiency-scaled input current of an ideal buck converter) | Other VIN-powered circuits, CPU internal conversion, boot transients, input decoupling and actual output-drive loads still contribute; foldback numbers are **not** guaranteed protection of offboard conductors |
| Input-to-output dissipation | At *illustrative* 5.0 V input, incremental external 3.3 V load causes approximately `(5.0−3.3) V × I_3V3,external` of **additional LDO heat**. An illustrative **6.507857 mA** yields **11.063 mW**; the full **250 mA external allowance** would yield **425 mW**, *before the Teensy's own 3.3 V loads* | Neither example establishes complete regulator current or actual junction temperature. The 250 mA is a manufacturer's recommended external load ceiling, **not a design operating point** |
| **DBV package only** (standard SOT-23-5) thermal: **100.8 °C/W** on the TI EVM versus **231.1 °C/W** on the JEDEC standard board ([S12] printed p. 5); the thermally enhanced **DYD** variant has different figures | At the illustrative **425 mW**, these two **DBV-specific** PCB models imply ~**42.84 °C** and **98.22 °C** junction rise respectively. This illustrates PCB/copper dependence **only if actual installed U4 is the DBV package** | The EVM and JEDEC numbers are **not guaranteed lower/upper bounds** on Teensy's assembled U4 thermal resistance. Actual package/suffix, copper, ambient, airflow and MCU demand are unverified; do not apply the DBV table to an unspecified U4 or the DYD version |

**Important 3.3 V fault non-coordination:** TI reports a **355 mA typical LDO short-circuit current** [S12], while the **MF-R050 upstream input PTC has 410 mA hold at 40 °C** [S2]. The comparison `0.355 A < 0.410 A` shows a *plausible* fault where the LDO enters foldback/thermal cycling **without an input PTC trip**. Other board input currents may change the actual PTC current; TI's typical short value is **not** a guaranteed cap or measured behavior. Consequently, **a 3.3 V short is NOT shown to be cleared by F1** and the LDO's thermal cycling is not a qualified system-level protective mode. This finding must remain a separate G3 stop condition. If the installed U4 is the older onsemi or TI variant, re-run the fault/thermal assessment using the exact component's documentation.

## 3. Current and power conservation across both sources

Use physical measurement boundaries rather than adding repeated series-path currents:

`I_3V3,external = I_optical + I_buffer + I_LED + I_unmodeled_external`; include dynamically driven `EDGE`, `MON` and `VBUS_PRESENT` output loading in the originating rail and do not double count at J1/J4/J5.

`I_VIN = I_Teensy,intrinsic + I_3V3,input_equivalent + I_5V,other`. The input-equivalent contribution depends on Teensy regulator topology, efficiency, input voltage, losses and temperature; it is **not automatically equal to** the 3.3 V load current. Power requires `P_VIN ≥ 3.3 V × I_3V3,external` for a VIN-fed 3.3 V rail, but this does not bound overall VIN current without internal data.

In the *ideal steady series path*, `I_5V_IN ≈ I_5V_LIMITED ≈ I_5V_SAFE ≈ I_J2 ≈ I_VIN`, allowing module self-consumption, leakage and wiring losses. These are **one path**, not five additive consumers. `P_5V_IN = V_5V_IN × I_5V_IN`. Only VIN (and grounded return) is a specified J2 load; the draft does not nominate a separate direct 5V_SAFE consumer.

For the **separate USB host source**, the R1=1 kΩ/R3=47 kΩ divider gives `I_USB,divider = VUSB_RAW / 48 kΩ` and `VUSB_BUFFER_IN = VUSB_RAW × 47/48` (ignoring leakage and tolerance). At 5.00 V: **0.104167 mA; 4.895833 V**. At the *proposed controlled Stage-B* 5.25 V check: **0.109375 mA; 5.140625 V**. TI's input 5.5 V limit does not clear tolerance/spikes or powered-off injection. Divider current is **not** part of `I_VIN` or MF-R050 current.

| Mode | Known external 3V3-side evidence | Total 5 V source current and current limit |
| --- | --- | --- |
| LED off, nominal static | Ideal passive subtotal 0.447857 mA plus condition-dependent IC/output draw | **UNKNOWN** (intrinsic Teensy, conversion, I/O loads) |
| LED on, driven signals | LED illustration 1.4 mA; conditional resistor-only ceiling 3.333 mA; other output loading unknown | **UNKNOWN** |
| Worst continuous at 40 °C | All external loads, MCU and thermal margins must be bounded; MF-R050 Ihold **0.41 A** at 40 °C | **NOT_DEMONSTRATED** |
| Startup/inrush/rapid toggling | Boot and capacitor charging time/current not measured or modeled with a safe bound | **NOT_DEMONSTRATED** |
| Short, reverse-injection, rail loss, USB hotplug | Current, duration and damage threshold depend on fault location and source response | **NOT_DEMONSTRATED** |

## 4. Protection and thermal coordination

| Exact element | Verified component-level fact | Missing system-level guarantee |
| --- | --- | --- |
| Bourns `MF-R050` | [S2] electrical table p. 1: `Ihold=0.50 A`, `Itrip=1.00 A` at 23 °C; `Vmax=60 V`, withstand `Imax=40 A`; initial 23 °C `Rmin=0.41 Ω`, `R1max=0.77 Ω`, *one-hour post-trip* max `1.17 Ω`; max trip time **4.0 seconds at 2.5 A, 23 °C**. Thermal table p. 4: hold/trip **0.41/0.82 A at 40 °C**, **0.36/0.72 A at 50 °C**, **0.32/0.64 A at 60 °C** | Hold is not a programmable current ceiling. Four seconds at 2.5 A does **not** imply four seconds at 1 A or elevated ambient. Withstand 40 A is not current regulation. Hot resistance, contact heating and faster downstream damage threshold remain unknown |
| Mean Well `GST18U05-P1J` | [S3] GST18U datasheet dated 2026-04-03, p. 2: **fixed 5 V / 3.0 A / 15 W**, nominal ±5% output tolerance; **110–150% rated output power** overload with automatic hiccup recovery; no owner-adjustable setpoint | 15 W ×1.10–1.50 = **16.5–22.5 W** threshold region, algebraically 3.3–4.5 A *if* output held exactly 5 V; **not** a guaranteed load-current ceiling or safe protection of the 0.41 A hot PTC and unknown contacts. AC inrush is not downstream DC inrush |
| Pololu `5383` with TI LM74700-Q1 controller | [S4] identifies a 4–60 V startup module (3.2 V once running), nominal 12 A-class rating and **<5 mΩ MOSFET on resistance**, **not** a guaranteed module operating-drop limit. [S15] §§9.4.2.1–9.4.2.2: at lighter forward current the controller **actively regulates ANODE–CATHODE drop around 20 mV by adjusting gate drive**; sufficiently large forward current enters a distinct **fully enhanced** MOSFET conduction regime | Actual 5 V module drop is unqualified in either regime; the controller's behavior means bare `I × RDS(on)` is **not** an operating-drop upper bound. No load-current limit, overvoltage regulator, USB VIN/VUSB isolation or connector-rating evidence |
| Adafruit `368` | [S9] 2.1 mm DC barrel female to screw terminal interface | No verified continuous rating, mated plug contact/temperature, screw torque or conductor/gauge fit |
| Samtec TSW headers | [S10] tests **different 100-contact, two-row TSW-150-07-L-D/SSW-150-01-G-D** test pair; real J1/J2/J4/J5 have 2–4 contacts and no selected exact mating socket/wiring | Never transfer that test's per-contact ratings to the proposed headers/harness. Per-contact continuous/transient and return current and temperature remain **unknown** |

**Worked examples are *not* selected load currents.** Using only 23 °C initial `R=0.41–0.77 Ω` at artificial `I=0.250 A` gives PTC `Vdrop=0.1025–0.1925 V` and `P=I²R=0.025625–0.048125 W`. At illustrative `0.410 A` with the **same 23 °C** range, drop `0.1681–0.3157 V` and power `0.068921–0.129437 W`. This R range does **not** bound 40 °C/hot operation or a tripped part. For Pololu 5383, **do not use MOSFET-only `I × RDS(on)` as an assembled operating-drop estimate**: its LM74700-Q1 changes MOSFET effective resistance by varying gate voltage during regulated conduction to target approximately 20 mV forward drop, with full enhancement as a different regime [S4, S15]. Omitting leads and traces cannot fix that model mismatch. TI's 13/20/29 mV min/typ/max threshold is characterized with **V(ANODE) = 12 V, V(EN) = 3.3 V, Tj −40 to 125 °C (typical at 25 °C)** and the stated VCAP conditions [S15, §6.5]; it is **not** an unconditional 5 V module guarantee. For a source near the lower 4.75 V end of the Mean Well ±5% specified output, actual PTC, cable, connector and startup drop must be bounded before claiming Teensy regulator headroom.

The PTC can let large fault current pass while heating. Since adapter capability (3 A) is much higher than the PTC's continuous hot hold rating and J2 contact/wire/fault envelopes are unknown, **time-to-damage, nuisance-trip and energy coordination are NOT_DEMONSTRATED**. No numeric first-power current limit follows from `0.41 A`, `0.50 A`, `1 A`, `3 A`, 12 A protector capacity or PJRC's 250 mA external-rail recommendation.

A **3.3 V short behind the Teensy regulator is a distinct fault path**: TI's **355 mA typical** TLV755P short current (for an actually installed TLV75533P) can be **below** the 40 °C **410 mA upstream PTC hold** before adding other board input draw. Neither the PTC nor the regulator guarantees timely protection of the other branch, because exact input fault current, thermal cycling and damage timing are unverified. See §2.1 [S12].

**Adapter overvoltage does not coordinate with the conditional TLV755P input limits.** Mean Well's GST18U-series [S3, p. 2] specifies **OVP at 110–140% of rated output voltage**, i.e. **5.5–7.0 V** for GST18U05-P1J. If the received Teensy actually uses **TLV75533P** at U4, TI [S12, §5.3] sets **5.5 V maximum recommended input** and [S12, §5.1] sets **6.0 V absolute maximum** (a stress limit, **not** a permitted operating voltage). The adapter's OVP range reaches beyond both; its clamp-by-Zener/output-short behavior and intervening drops cannot be credited with a guaranteed safe voltage at U4. No quantitative overvoltage waveform or downstream voltage-clamping guarantee exists. This is a limitation of the proposed design, **not** a component substitution or redesign decision.

## 5. Contact and harness budget (no inherited Samtec rating)

| Connection / actual role | Current and return | Evidence gate |
| --- | --- | --- |
| Power `J1` Adafruit 368 → F1/M1 interconnect | Entire external 5 V/return current | Exact adapter polarity; mating barrel; continuous/fault current, wire gauge, insulation/retention and thermal/contact limits |
| Power `J2` TSW-102-07-G-S | Entire Teensy **VIN** current through `5V_SAFE` and GND | Specific 2-pin mate, conductor, contact/temp rating, inrush/fault duration; even a low LED current cannot clear J2 |
| Optical `J1` TSW-104-07-G-S | Optical 3V3 and return, `EDGE` comparator drive, `MON` amplifier output | Specific 4-pin mate, 3V3 branch current, return, load and noise/ESD/EMC checks |
| Power `J4` TSW-103-07-G-S | Buffer 3V3/GND and `VBUS_PRESENT` drive/return | Specific 3-pin mate, input/output states, leakage and ground offset |
| Power `J5` TSW-102-07-G-S | LED GPIO/GND, distinct lower-load use | Specific 2-pin mate and current/insulation; shared exact MPN remains HOLD because of J2 |

Do not conflate the current carried by a power header, the GPIO drive on a signal header, and the idle quiescent load of the components connected behind it. No contact-thermal or wire-ampacity calculation is possible without the exact mating contact, number of loaded contacts, conductor gauge, assembly and ambient.

## 6. Fault, power sequencing and USB domains

| Case | Potential hazard | Missing evidence / gate |
| --- | --- | --- |
| Initial external-only power, USB absent | Wrong polarity, J1/F1/M1 orientation, short, MCU startup/dropout, capacitor inrush | Off-line continuity/polarity, rated adjustable source, Stage-B voltage/current/temperature (nothing done) |
| Reversed/wrong-voltage source | Pololu blocks designed negative input but cannot protect all miswiring or overvoltage | Exact nameplate, real wiring, verified input polarity and no short |
| Short at J2/VIN or optical/load output | Fixed adapter may source amperes before thermal PTC trips; contact or IC damaged first | Bounded source short response, contact/wire/IC damage limits, fault energy over time |
| Elevated ambient / long normal load | PTC hold derates to 0.41 A at 40 °C and 0.36 A at 50 °C; resistance rises | True continuous VIN/load, dropout/temperature and nuisance-trip margin |
| GPIO short or EDGE/MON contention | Teensy 4 mA recommended output current, comparator/OPA output drive, 100 Ω EDGE impedance; offboard unknown | Actual boot pin modes, termination, rail sequencing, source/sink limits |
| VIN present, USB absent | Uncut VIN–VUSB trace may drive device USB port/backfeed Android | Physically cut and test trace; later measure VUSB with VIN only; stop for nonzero isolated VUSB |
| USB VBUS present, VIN absent | Buffer input high while 3V3 absent, powered-off GPIO injection | SN74 Ioff conditions not system proof; later controlled 5/5.25 V isolated-output VBUS tests |
| Both sources present | Shared ground, VBUS sensing, cut trace, rail loss/attach transitions | Only after external-only and VBUS-only checks; monitor VIN/VUSB/3V3, current, GPIO and host role |
| USB attach/disconnect, brownout, hotplug | Unexpected GPIO drive, reverse injection, unstable startup/reset | Stage-B time-resolved source and return-current evidence |
| Loose wire/mating contact | Temperature, intermittent drop, contact arc and signal-reference disturbance | Exact mate/gauge/retention, offline checks and separately authorized staged observation |

**Non-negotiable:** Teensy 4.1 pins other than VIN/VUSB are **not 5 V tolerant** [S1]. The isolated VUSB divider must never be tied directly to GPIO. Keep USB D+/D− in an intact data cable, and the photodiode-to-TIA summing node off breadboards.

## 7. Bench-supply current-limit selection method — no present setpoint

**Full-fixture `INITIAL_CURRENT_LIMIT = UNSET / PENDING REVIEW`.** The *complete connected fixture's release* requires a substantiated steady-state/startup envelope and vulnerable-load/contact fault-time margin; those operating bounds have not been established. They are **not a prerequisite to separately reviewing a bounded Teensy-only S1 characterization attempt**: S1 may produce its own startup evidence if its independent current/time/energy protection case is defensible (§7.1). **Owner planning assumption (2026-11-02): the Korad `KA3005PE+` will be owned or acquired and available for Stage-B first-power work.** Plan to use that adjustable CV/CC supply; do not treat its acquisition as a blocking engineering task. No actual purchase, receipt, installation, operational verification, current-limit setting, or permission to energize is evidenced. Until receipt is confirmed its factual status remains `OWNER_SELECTED_MODEL / NOT_PURCHASED / NOT_FIRST_POWER_QUALIFIED`; it must be separately checked against its exact manual, mains configuration, isolation, leads and current-limit behavior before use.

For later **separately approved complete-fixture release** (after any separately qualified characterization), in this order:

1. Lock the as-built revision, ambient range, firmware/clock and connected-load modes; distinguish external-only first power from USB-host VBUS conditions.
2. Obtain the full-fixture Teensy VIN intrinsic and startup envelope **from independently justified manufacturer evidence and/or later approved bounded S1 measurements**; integrate regulator thermal/headroom, optical/LED/buffer loading, exact connector/wire ratings and the supply's verified CC response. S1 is *not* required to know its own as-yet-unmeasured boot waveform in order to be reviewed.
3. For **complete-fixture release only**, calculate and independently check `I_run,upper(T)`, time-varying `I_startup,upper(t,T)`, and the most restrictive downstream `I_safe,lower(t,T)` for damage and fault exposure. The chosen source response must allow justified complete-fixture operation while constraining fault **energy/time** below the weakest part/connection. If there is no demonstrated overlap, **stop for design investigation or redesign rather than guess a full-fixture limit**.
4. With everything de-energized, independently review VIN/VUSB cut, polarity, shorts, grounded return, J1/F1/M1/J2 mapping and isolation; verify source manual/model/mains/earth/leads/fuse/output characteristics. The fixed Mean Well adapter is **not** the initial current-limited source.
5. Only after separate owner release: Stage B external-only commissioning, USB absent, optional external loads disconnected first; log configured and measured CC voltage/current, VIN, 3V3, isolated VUSB, startup envelope, rail stability, temperature and brownout. Stop for unexpected current, heating, voltage or backfeed.
6. Add optical, LED and USB sensing one gated stage at a time using BENCH-002 G2/G3/G4; qualify VBUS-only before combined-source Android use. Stop for powered-off rail rise, unsafe GPIO levels, USB role failure or unstable state.
7. Preserve raw readings including failed attempts, test instrument configuration, operating temperature, wiring photos, exact revision and measurement timestamps. Stage C handles optical and timing performance; no one-channel result qualifies five-channel production.

### 7.1 Incremental, independently gated characterization plan

The lack of a published **complete-device** startup bound does **not** justify inventing a full-fixture setpoint or treating all diagnostics as impossible. Instead split future verification into *separate, smaller electrical subjects*, each requiring its **own** preflight rating and separate owner release. The following is planning only; **none of these energized steps is authorized or performed**:

| Gate / separate subject | What it could establish *after* independent approval | Conditions and what cannot be concluded |
| --- | --- | --- |
| S0: **Unconnected** KA3005PE+ supply and instrument | Confirm the exact model, protective earth/mains selector, output-disabled default, recalled presets, nominal **5.0 V voltage target**, DMM voltage accuracy, and CV/CC/OVP behavior. A separately rated known dummy load can check current-limit response | There is **no prototype or Teensy connected**. A current-limit value for a rated dummy resistor would be calculated from *that resistor alone*, not copied into first-power hardware settings. Check the owner's exact manual; [S13] documents output enable, current/voltage adjustment and memory |
| S1: **Teensy 4.1 alone** (no optical/LED/buffer/USB) | After an **independently reviewed S1-only protection case**, a constrained attempt could measure intrinsic VIN startup/steady current and reveal the actual regulator/board identity. The measured startup envelope is an **output**, not an input prerequisite | **Before** any S1 attempt, independently establish source current-limit response and fault shutoff behavior, rated temporary VIN/GND wire/contact and return paths, applicable Teensy/regulator voltage/current/thermal damage limits, and a justified **duration/energy exposure bound**, without assuming the unknown boot current. Do not casually bypass protection. If evidence for any bound is absent, S1 is **blocked** pending manufacturer/source/fixture proof or design investigation. Success proves only the tested configuration; failed or incomplete boot proves only the bounded attempt, not normal operation or required unrestricted startup current |
| S2: **Proposed full protected input** through Adafruit 368 → F1 → Pololu 5383 → *qualified J2 mating assembly*, **without offboard 3.3 V loads** | Characterize contact/return drop, start/steady current, PTC temperature and Teensy VIN stability in the **actual series path** | Requires exact mates, wire/terminal current and fault ratings, confirmed VIN/VUSB cut, instrument source-limit selection and G2/G3 release. Unqualified J2 and 368 **block S2**, irrespective of S1 success |
| S3: Add **one optical channel**, buffer and indicator **one at a time** | Measure marginal 3.3 V/5 V load, start/edge currents, supply/ground noise and power sequencing | Confirm 3.3 V headroom and safe GPIO connections. Each addition requires refreshed bounds; do not treat the illustrative 6.507857 mA as a guaranteed maximum |
| S4: **USB VBUS-only then simultaneous Android host + external VIN** | Test partial-power injection, VUSB isolation, host operation and boot/hotplug | Distinct G4 release **after** S2/S3 and isolated-source behavior. No VBUS bridging, direct 5 V GPIO, breadboard USB data or physical USB test now |

**Decision rule and non-circular S1 evidence flow:** S0 alone establishes **no** DUT limit. S1 **prerequisites** are independent source response, rated S1 wiring/contacts and return, applicable component stress/thermal constraints, justified *maximum allowed exposure* over time/energy, and a distinct owner-approved test scope. These can be reviewed without first measuring the unknown boot demand; if that independent protection case cannot be made, **S1 stays blocked** pending external ratings or design investigation. The **possible output** of a successful S1 attempt is an observed *Teensy-only* current/startup waveform in that tested setup; a current-limited nonboot or brownout establishes **only** the failed attempt under its selected constraints, **not** unconstrained demand, normal operation or authorization to raise any setting. **Stop → record → review source/protection assumptions → obtain separate approval** before any changed attempt; successful boot also does not legitimate an unsupported setting. S2/S3/S4 each require their **own** approval and connected-load/fault limits and cannot inherit S1's wiring, setting or protection claim. Complete-fixture operating/startup evidence remains necessary only for **complete-fixture release**. The proposed fixture's numeric current, voltage, duration and energy settings remain **unselected** by this document; full-fixture `INITIAL_CURRENT_LIMIT` stays **UNSET / PENDING REVIEW**.

No assembled hardware, protected source, VBUS checks or fault testing was performed by creating this document.

## 8. Electrical consequences for procurement review (not a readiness ledger)

The authoritative **per-MPN** lifecycle, conditional REVIEW/HOLD dispositions, source confidence, and owner purchasing gates live in [BENCH-003's exact-MPN component evidence](PROTOTYPE-PROCUREMENT-READINESS.md); the live blockers, decisions, and evidence for this work live in [GitHub Issue #3](https://github.com/whatobiplays/screen-latency-tool/issues/3). This document does **not** copy the current counts or status table, because a dated electrical calculation cannot update lifecycle status, supplier availability, or owner purchase authorization.

The remaining **durable electrical dependencies** are: an evidence-backed continuous, startup, and fault-time envelope for the *actual* Teensy VIN revision; independently rated Adafruit 368 input terminations and the Pololu/module power interconnect; thermal and fault-time coordination of MF-R050 with the fixed GST18U05 adapter and downstream regulators; and **actual selected Samtec mates, wire gauge, contact/return currents, temperature rise, and harness protection** for J2 (entire Teensy VIN), optical J1, logic J4, and LED J5. The same TSW-102 header's low-current J5 role cannot validate its high-consequence J2 power role. A regulator foldback or controller-regulated forward drop does not qualify system-level fault protection. Physical pin/terminal fit, exact manufacturer lifecycle statements, supplier stock and substitutions remain independent BENCH-003 gates.

No manufacturer inquiry, vendor order, substitute, fab output, board assembly, power-on step, commit/push or Issue #3 closure is authorized.

## 9. Reproducibility and primary-source register

The nominal arithmetic can be independently replicated from these substitutions; all are **conditional examples**, not current-limit selections:

| Quantity | Calculation | Result |
| --- | --- | --- |
| Optical VREF divider | `3.3/28,000 × 1,000` | **0.117857 mA** |
| Optical trimmer | `3.3/10,000 × 1,000` | **0.330000 mA** |
| Independent static passive subtotal | `0.117857+0.330000` | **0.447857 mA** |
| Internal R7 bias limit (not additive rail sink) | `3.3/1,000,000 × 1,000` | **0.003300 mA** |
| Illustrative LED at cited 10 mA Vf | `(3.3−1.9)/1,000 × 1,000` | **1.4 mA** (not actual at 1.4 mA) |
| LED theoretical resistor ceiling | `3.3/(0.99×1,000) × 1,000` | **3.333 mA** |
| USB 5.0 V divider | `5.0×47/48`; `5.0/48,000×1,000` | **4.895833 V / 0.104167 mA** from USB |
| USB 5.25 V divider | `5.25×47/48`; `5.25/48,000×1,000` | **5.140625 V / 0.109375 mA** from USB |
| MF-R050 0.25 A @ 23 °C initial R | `0.25×[0.41,0.77]`, `0.25²×[0.41,0.77]` | **0.1025–0.1925 V; 0.025625–0.048125 W** |
| MF-R050 0.41 A at *illustrative 23 °C R* | `0.41×[0.41,0.77]`, `0.41²×[0.41,0.77]` | **0.1681–0.3157 V; 0.068921–0.129437 W**; NOT 40 °C resistance |
| Pololu LM74700-Q1 operating-mode distinction | Regulated conduction controls ANODE–CATHODE forward voltage by changing gate drive; full MOSFET enhancement occurs separately [S4, S15] | **No numeric Pololu 5383 drop bound selected for the assembled 5 V path**. TI's ~20 mV regulated-conduction target is conditional and is not a guaranteed complete-module value |
| Adapter overload power | `15×1.10`; `15×1.50` | **16.5–22.5 W**; not a downstream current limit |
| Adapter overvoltage trip range | `5.0×1.10`; `5.0×1.40` | **5.5–7.0 V** per [S3]; reaches above the conditional TLV755P 5.5 V recommended maximum and above its 6.0 V absolute maximum [S12] |

**Source register (manufacturer sources cross-checked 2026-11-02; original prototype manufacturer claims remain subject to their stated test conditions; product pages do not assert exact-SKU lifecycle unless explicitly stated):**

- [S1] [PJRC Teensy 4.1](https://www.pjrc.com/store/teensy41.html), **Power**, **Digital Output Pins**: 250 mA recommended external 3.3 V load, 4 mA recommended GPIO output, VIN/VUSB separation. [Teensy 4.1 pin card](https://www.pjrc.com/teensy/card11b_rev3_web.pdf): only VIN/VUSB excepted from 3.3 V GPIO ceiling.
- [S2] [Bourns MF-R series PTC datasheet](https://bourns.com/data/global/pdfs/mfr.pdf), `MF-R050` electrical row printed p. 1 and thermal derating printed p. 4 (document revision not identified in the accessible copy). Neither Ihold nor Itrip is precision protection.
- [S3] [Mean Well GST18U series](https://www.meanwell.com/Upload/PDF/GST18U/GST18U-SPEC.PDF), 2026-04-03 file date, output and protections table printed p. 2: `GST18U05-P1J` 5 V/3 A, 110–150% overload, and 110–140% output-voltage OVP with Zener clamp/output short.
- [S4] [Pololu Ideal Diode Reverse Voltage Protector #5383](https://www.pololu.com/product/5383), manufacturer technical/product page, revision not stated.
- [S5] [TI OPA320](https://www.ti.com/lit/ds/symlink/opa320.pdf), SBOS513F revised December 2016, quiescent table printed p. 9; data measured under specified 5.5 V conditions.
- [S6] [TI TLV3501](https://www.ti.com/lit/ds/symlink/tlv3501.pdf), SBOS321E revised April 2016, quiescent electrical table printed p. 5 (5 V test condition).
- [S7] [TI SN74LVC1G17](https://www.ti.com/lit/ds/symlink/sn74lvc1g17.pdf), SCES351Y revised October 2025, electrical characteristics printed p. 6 and ICC vs frequency printed p. 12.
- [S8] [Kingbright WP7113ID](https://www.kingbrightusa.com/images/catalog/SPEC/WP7113ID.pdf), V.14A dated 2026-01-08, electrical/optical table printed p. 2 (`V_F` at 10 mA).
- [S9] [Adafruit #368](https://www.adafruit.com/product/368), female 2.1 mm barrel to terminal block product page; no verified current rating for this proposed assembled use.
- [S10] [Samtec power report](https://suddendocs.samtec.com/testreports/tc0838-1970_reportrev2_pwr.pdf), tested different 100-contact two-row TSW/SSW pair; **not** the prototype's mated 2-, 3- or 4-position path.

- [S11] [PJRC Teensy 4.1 design-change and power sections](https://www.pjrc.com/store/teensy41.html): U4 regulator substitutions (March 2022, March 2023), VIN/VUSB cut, and the **Teensy 4.0** ~100 mA running reference; [PJRC MKL02 power-up sequence](https://www.pjrc.com/store/ic_mkl02_t4.html) documents staged on-chip rail/PMIC sequencing (not a 4.1 VIN current profile).
- [S12] [Texas Instruments TLV755P Rev. D, SBVS320D, revised September 2024](https://www.ti.com/lit/ds/symlink/tlv755p.pdf): overview/soft-start printed p. 1; **5.5 V maximum recommended VIN** and **6.0 V absolute-maximum stress limit** in §§5.3/5.1 printed p. 4; 500 mA capacity, ICL 560–865 mA and ISC 355 mA typical, and **DBV-versus-DYD** thermal PCB table printed p. 5; foldback and startup/fault/thermal limitations printed p. 13. **Apply only to a verified TLV75533P regulator; apply illustrated DBV thermal rows only if installed package is independently identified as DBV**, not to earlier regulators or other packages.
- [S13] [Korad KA3000/6000 series user manual](https://storage.tameson.com/asset/Products/Manuals/EN/korad_ka3005p_manual.pdf), printed pp. 10–13: output ON/OFF, CV/CC voltage/current setting and saved/recalled presets. This series manual is **not** proof of the particular unit's earth/isolation/CC response.
- [S14] [Samtec TSW-102-07-G-S](https://www.samtec.com/products/tsw-102-07-g-s) and [SSW-102-01-G-S](https://www.samtec.com/products/ssw-102-01-g-s): manufacturer identifies a 2-position 0.025-inch-square-post TSW and an **example board-tail** SSW mating family. This is mechanical-family evidence only; it does **not** choose a wire-harness mate, establish contact ampacity, or authorize changing the BOM.

- [S15] [Texas Instruments LM74700-Q1 Rev. G, SNOSD17G, revised December 2020](https://www.ti.com/lit/ds/symlink/lm74700-q1.pdf): regulated forward V(AK) threshold 13/20/29 mV min/typ/max in §6.5 printed p. 6 at **V(ANODE)=12 V**, **V(EN)=3.3 V**, **C(VCAP)=0.1 µF**, Tj −40…125 °C (typical 25 °C); §§9.4.2.1–9.4.2.2 printed p. 15 distinguish gate-regulated ~20 mV conduction from fully enhanced MOSFET mode. [Pololu 5383](https://www.pololu.com/product/5383) [S4] explicitly names this controller. Neither source supplies a universal 5 V assembled-drop bound.

**Tests and evidence:** read-only placed-symbol/net review and transparent arithmetic are the entirety of the new analytical work. Earlier ERC, DRC, SPICE and EMC outcomes remain historical and were **not rerun or promoted to electrical qualification** here. No actual circuit, USB, current, voltage, short, startup, thermal, photodiode or T0 test was run. Primary unresolved prerequisites: **physical U4 revision identification**, Teensy 4.1 intrinsic and startup envelope, exact J2/other connector mates and rated harnesses, regulator/board thermal behavior, fault-energy thresholds, adjustable-source CC response and physical VIN/VUSB isolation. New evidence is document-only; no native KiCad, EMC, SPICE, physical or firmware tests were executed.
