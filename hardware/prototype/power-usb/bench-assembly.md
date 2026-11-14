# Supervised bench assembly

## 1. Configuration and release boundary

The owner selected this one-sensor KORAD KA3005PE+ direct-bench architecture and
its bounded residual-risk model in the [2026-11-14 G0 decision](https://github.com/whatobiplays/screen-latency-tool/issues/47#issuecomment-6874347712).
That decision expressly includes the owner-terminated source leads below.
The supply and assembled path still require qualification. Purchase, assembly,
source-only qualification and staged energization have separate approvals in
[Issue #47](https://github.com/whatobiplays/screen-latency-tool/issues/47) and
[Issue #38](https://github.com/whatobiplays/screen-latency-tool/issues/38).
`INITIAL_CURRENT_LIMIT=UNSET`. No physical result is recorded here.

The native schematic retains its adapter symbols for reference. In this bench
variant, L721A and GST18U05-P1J are not fitted or connected. The bench positive
terminates at `5V_IN`, the same node as external F1 pin 1; negative terminates
at common signal GND. Never parallel an adapter with the bench source.

| Connection | Physical construction |
| --- | --- |
| Supply to carrier | Pomona 73093-2/-0 retractable-shroud plugs and 18UL1007STRRED25/18UL1007STRBLA25 18 AWG stranded wire, ≤1 m per source lead. The owner accepted this prototype-only exception to factory-terminated leads at G0. Attach with the solderless push tab as specified in §1.1; inspect retention and qualify the actual lead's current/thermal envelope. Another lead construction requires an explicit amendment. |
| Input protection | Solder MF-R050 in series from `5V_IN` to `5V_LIMITED`; support and insulate its leads. Solder to Pololu 5383 VIN/GND pads using the maker's pad map. |
| Module mounting | Insulating cradle or spacer supports the module independently of its joints. Cover the underside and provide strain relief outside the pads. No exposed floating module. |
| Protected output | Pololu VOUT is `5V_SAFE`; solder to XT30U-F.G.Y source pole 1 and GND to pole 2 after inspecting the actual molded geometry. |
| Teensy connection | XT30U-M.G.Y load pole 1 to VIN and pole 2 to GND through supported 2.54 mm mating sockets on the carrier. Secure the Teensy in an insulating cradle; no final alligator-clip connection. |
| Carrier | Adafruit 1606 full-size FR4 carrier, two Samtec SSW-124-01-G-S sockets, insulating feet and covered joints. Use WAGO 221-412 ×2 in 221-500 as the mechanically held input pair: each splice is one potential; never put positive and return into the same splice. Never rely on an unrecorded breadboard strip as a power/ground bus. |
| USB sense | SN74LVC1G17DBVRG4 on a pin-verified SOT-23-5 adapter: pin 5=3V3, 3=GND, 2=A, 4=VBUS_PRESENT, 1=NC. Fit local C1; R1=1 kΩ from VUSB_RAW to A and R3=47 kΩ from A to GND. |

The [connection drawing](bench-assembly.svg) identifies electrical boundaries;
the pad/termination map below is tied to Adafruit 1606. Inspect received copper and orientation before soldering. Do not fit an adapter/carrier
whose strips join isolated domains. Keep VIN, 3V3 and VUSB_RAW distinct.
Pololu is reverse-voltage/reverse-current protection, not forward OVP or CC.
The PTC is delayed thermal protection, not guaranteed 3V3 fault clearing.

### 1.1 Pomona 73093 source-lead termination and inspection

This is the procedure for later owner-authorized assembly. The [manufacturer
drawing, revision 001](https://www.pomonaelectronics.com/sites/default/files/d73093_001.pdf)
specifies solderless push-tab attachment for 18–22 AWG wire and a 4–6 mm strip
length. The selected Remington conductors are 18 AWG stranded. The Pomona plug
has no solder cup; do not solder the conductor to it or use the XT30 soldering
instructions for this termination.

1. Disconnect both ends from all equipment. Verify the source is off and the
   relevant rails are discharged. Identify 73093-2/red and 73093-0/black from
   the actual parts; label each end by net, not color alone.
2. Cut each source lead to the documented length, at most 1 m. Strip 4–6 mm
   without nicking or removing strands. Keep the strands together; do not tin
   the end or add solder to change the attachment method.
3. Push and hold the plug's tab. Insert the stripped conductor fully, then
   release the tab to secure it. Inspect for stray or exposed strands and
   confirm that the insulation and conductor seat without obstruction.
4. Apply a gentle manual retention check with the lead still disconnected.
   Any conductor movement, incomplete tab return, damaged insulation or loose
   attachment is a rejection. This check does not establish a numerical pull
   rating or current capacity. Support the leads independently of the plugs.
5. Independently verify continuity/polarity from the positive plug to its
   separate positive WAGO splice and F1 pin 1 (`5V_IN`), and from the negative
   plug to its separate return splice/GND. Verify no positive-to-return short.
   Record meter/lead identity, lead resistance, raw readings and net labels.
   A continuity beep alone does not establish low contact resistance.
6. Inspect seating in the actual KORAD jacks, retractable-shroud operation and
   retention during the separately approved unpowered preflight. Do not stack
   extra connections onto these plugs in the selected configuration. Current,
   voltage-drop and heating qualification remain required before power use;
   the maker's 36 A headline does not rate the completed hand-terminated lead.

Retain both-end photos and the unpowered checklist with the commissioning
record. Re-terminate and repeat the checks after any disturbance. A failed
retention, polarity or continuity check stops release. No assembly or test is
reported by this procedure.

## 2. Ground and signal construction

Route a supported source return to the carrier ground terminal, then separate
short returns for Teensy and the external circuits. Retain a real USB ground;
never use shield as its replacement. Record scope earth and current-shunt paths
before connection. A low-side shunt must not be bypassed through a grounded
scope, computer or USB cable; use an appropriate differential/current probe or
an explicitly reviewed single-return measurement arrangement.

### 2.1 Carrier pad and termination map

View the carrier component side with its maker row/column labels readable.
Place the Teensy with its USB connector toward column 4: VIN-side socket in
C4–C27, opposite socket in G4–G27. The maker CAD gives 15.24 mm row spacing
and 58.42 mm first-to-last pin span, matching PJRC's 2.54 mm ×24 outer rows.
Verify actual DEV-20360 male-post section and insertion depth against the SSW
3.68–6.35 mm requirement; neither nominal geometry nor the Samtec mated-pair
rating proves this particular assembly.

| Termination | Net/function | Wiring |
| --- | --- | --- |
| Positive WAGO splice | 5V_IN | Red source lead to F1 pin 1; no PCB bus credited with power ampacity |
| Negative WAGO splice | GND | Black source lead to explicit module/Teensy/common returns |
| F1 pin 2 | 5V_LIMITED | Insulated wire to Pololu VIN |
| Pololu VOUT | 5V_SAFE | Insulated wire to source XT30 pole 1 |
| Load XT30 pole 1 | Teensy VIN | Direct supported wire to socket C4 tail/pad |
| Load XT30 pole 2 | Teensy GND | Direct supported wire to socket G4 tail/pad; C5 is another labeled GND position |
| C6 | Teensy 3V3 | External optical/buffer branches, each labeled and separately inspected |
| G7 | Teensy pin 2 | Optional bench image LED_GPIO → J5 → R2 → LED; no other load |
| G8 / G9 | Teensy pins 3 / 4 | EDGE / VBUS_PRESENT input proposal; bench image configures high-impedance INPUT |
| Optical MON | Analog monitor | Initially scope only; no assumed ADC pin connection |

At a numbered column A–E are one copper group and F–J another; neighboring
numbered columns and opposite groups are separate. Verify those facts on the
received board. Leave other socket pins and unused group holes isolated; do not
use the long side buses. Locate the separate buffer adapter in the free right
carrier region, with its five terminals mapped by TI pin number and continuity,
not by an assumed adapter header sequence. Use DC0603S adapters for the four
offboard 0603 parts and keep C1 close to U1 VCC/GND; photograph the final small
adapter layout for G2. No plated strip ampacity is inferred for the main path.

The [PJRC pinout card](https://www.pjrc.com/teensy/card11a_rev4_web.pdf),
[dimension drawing](https://www.pjrc.com/teensy/dimensions_teensy41.png) and
[Adafruit carrier CAD](https://github.com/adafruit/Adafruit-Perma-Proto-PCB/blob/master/adafruit%20permaproto%20fullbreadboard.brd)
are the design basis. Compare all power pins with the received board markings;
a mirrored/rotated socket or ambiguous orientation is a stop.

Keep CABLE-PH04 pin 1 black=3V3, 2 red=GND, 3 blue=EDGE, 4 gray=MON.
Label both ends and verify all four paths disconnected. J4 uses CAB-14575,
female recessed contacts at Teensy: 1=3V3, 2=GND, 3=VBUS_PRESENT.
J5 uses CAB-14574, female recessed contacts at Teensy: 1=LED_GPIO, 2=GND.
For the optional `teensy41-bench` image, LED_GPIO is Teensy pin 2 and remains
HIGH after setup. Connect only pin 2 → R2 1 kΩ → WP7113ID → GND; the default
image does not drive this load. Build with the pinned PlatformIO toolchain,
record the image hash and qualify its startup/current separately. The steady
LED is a load, not a display/USB timing marker. It must not illuminate the
detector directly. Never omit R2 or connect this pin to 5 V.
Do not infer either pair's pin map from color. EDGE and MON initially remain
isolated from Teensy inputs while the optical board is checked.

Keep eLabGuy inline: Android host → Adafruit 3878 → breakout female/male →
Teensy device port. VCC is CLOSED, separate shield-to-GND jumper OPEN. Support
the breakout so its weight/cable torque does not load the Teensy receptacle.
Do not route or probe D+/D− with long flying wires.

## 3. Independent safety case

Use the exact received Teensy U4 revision and package. All exposed 5 V nodes
must remain within their operating/stress limits with uncertainty and a positive
margin; the MCU USB supply's 5.5 V ceiling is stricter than the LDO's 6 V stress
rating. Active internal USB supply must remain at least 4.4 V after path loss.
See [BENCH-005 sources](../../../docs/bench/PROTOTYPE-FIRST-POWER-FEASIBILITY.md#9-primary-source-register).

Calculate series drop and I²R at the actual current/ambient; bound source and
downstream capacitor discharge separately from subsequent CC. Use continuously
safe conductor/contact current where possible. A manufacturer's high-current
connector test does not rate the installed wire or hand-soldered joint.

| Fault | Control and required evidence |
| --- | --- |
| Input short, including pre-PTC | Qualified source peak/CC/cutoff; continuously safe leads, terminal and joints, or supported transient exposure. Test on a dummy fixture, not the Teensy. |
| 3V3 short | Revision-specific U4 behavior and safe external branch conductors; bounded local dissipation and shutdown. Upstream PTC trip is not assumed. Sustained unsafe heat requires a protection amendment. |
| Wrong voltage or source transition | Qualify allowed enable/disable, preset and CV/CC/OVP/OCP states; independently read back settings before connection. Lock controls and disable remote control. |
| Reversed leads, adjacent pin or cross-rail assembly error | De-energized pinout/polarity/continuity checks, supported insulated wiring, permanent net labels, no live rail rewiring. Owner accepts the narrowly bounded remaining error risk. |
| Open return | Secure returns and inspect alternate GPIO/USB/probe-earth paths in each stage. Stop if those paths carry unintended power. |
| Live output into unpowered peer | Stage connections and measure Ioff/back-power behavior; initially isolate buffer Y, EDGE and MON from the Teensy. |
| Host backfeed | Physically cut and inspect VIN/VUSB; demonstrate off-state behavior before Android. A paper cut instruction is not evidence of isolation. |
| Internal IC or catastrophic source failure | Disclosed residual component-damage risk; no universal survival claim. This does not excuse a credible serious source/wiring/host hazard. |

The recorded G0 decision accepts this exact topology, the narrow procedural
exclusions and residual component-damage risk. It releases no numerical setting
or physical stage. Sustained unsafe heating, unbounded source/3V3 exposure,
open-return/injection paths and host backfeed remain excluded from that waiver.
Preserve the historical Issue #45/PR #46 disputed findings in BENCH-005.

## 4. Limits, measurements and settings

Every numerical entry carries one of five labels: manufacturer specification
(guaranteed or typical with conditions), engineering margin, proposed screen,
required measurement capability, or physical observation. Never relabel a
calculation or trace simulation as an observation.

| Subject | Numerical design entry | Final admission requirement |
| --- | --- | --- |
| Source/VIN | 5.00 V nominal target; 4.75–5.25 V settled screen | Intersect with exact board limits and measured internal path losses. |
| Exposed input peak | 5.40 V including uncertainty, proposed guard | Demonstrate positive margin below the applicable 5.5 V ceiling, including cutoff overshoot; do not use 6 V as a board limit. |
| Internal active USB supply | ≥4.4 V, source-backed operating minimum | Measure or bound the actual internal node and path drop; VIN alone is insufficient if the remaining loss is unknown. |
| 3V3 | 3.20–3.40 V proposed screen | Intersect with actual U4 and connected IC limits. Logic thresholds/peaks refer to actual MCU supply and pin limits; never admit raw 5 V GPIO. |
| External 3V3 demand | ≤250 mA PJRC recommended allowance | Also satisfy installed U4 thermal, source and branch limits. Not a current-limit setting. |
| Current | INITIAL_CURRENT_LIMIT=UNSET | Independent S0 evidence and review select the stage's CC/OCP interval; no value is inferred from a PTC or LDO headline. |
| Temperature | 45°C accessible terminals, 10°C rise, 70°C board cases are proposed screens | Derive actual limits from component/wire/contact/insulation ratings, ambient, accessibility, uncertainty and margin. Case temperature alone does not bound junction temperature. |
| Leakage | 10 kΩ and 0.10 V off-state screen proposed | Agree the real leakage budget and measurement resolution; no operational back-power or unsafe host source current. |

For each stage record `Vset`, `Iset`, qualified min/max CC, OVP/OCP and cutoff
latency, allowed capacitance, energy, exposure duration, ambient and measuring
points. Until these entries are supported and independently reviewed the stage
is not executable. Unknown boot demand may be obtained by a bounded S1 attempt;
failure stops the attempt and does not authorize raising the limit.

Use E=½CV², ∫vi dt and ∫i²R dt with explicit measurement boundaries; do not
double count a capacitor already included in a current integral. Document the
thermal model's package/PCB limitations. If a required physical bound cannot be
established, obtain suitable borrowed equipment or propose a concrete protection
change; do not replace the bound with a universal no-damage investigation.

## 5. Source-only qualification, S0

Owner approval is required for a source-only protocol identifying the actual
unit/manual, load fixture and instruments. Verify mains configuration, cord,
case and protective-earth requirements from the supplied instructions without
opening the supply or performing improvised mains work.

1. With no DUT or Android, prewire a rated load/current fixture and measurement
   paths. Verify output disabled, remote control inactive and presets before
   enabling. Check polarity and set voltage independently with the DMM.
2. Characterize no-load and representative loads; enable/disable, load steps,
   CV/CC transition and load removal. Test OVP/OCP only into the rated dummy load.
3. Bound a near-load short's initial discharge and sustained current. Choose
   switch, shunt and resistors from their actual pulse and continuous ratings.
   Stop after the first failing capture; never use a GPIO or the prototype as
   a short fixture.
4. Establish actual voltage/current/time/energy uncertainty. Specify bandwidth
   and sampling from the shortest relevant event, including probe and shunt
   response. For suitable response models t_instrument≈0.35/BW is an estimate,
   not proof of absent fast spikes. A 100 MHz/250 MS/s scope is a candidate,
   not a mandatory purchase. Unresolved peaks require an independent bound or
   better measurement before connection.
5. Check contact drop and temperature at qualified continuous current. Ten
   normal captures, five fault captures and a 15-minute thermal observation
   are planning defaults. Finalize coverage/repetition and thermal settling
   criteria before testing; preserve all failures and cool between faults.
6. Compare results with independently derived exposure limits. Publish raw
   waveforms, settings and identities for independent review. S0 success does
   not itself select a DUT current limit.

## 6. Conditional G0–G4/S0–S4 sequence

G0 accepts the design/risk package. G1 approves exact baskets/fabrication.
G2 verifies received identity and unpowered assembly. S0 can run after its own
equipment/procedure approval while PCB preparation proceeds. G3 approves the
completed numerical S1–S4 procedure following source evidence. G4 admits the
actual Android connection after the controlled VBUS checks.

| Stage | Connection and measurement | Expected result / immediate stop |
| --- | --- | --- |
| S1 | Teensy VIN/GND only; no USB, VBAT, optical, buffer or LED. Capture startup/current, VIN/3V3 and temperature. | Bounded successful boot or preserved nonboot. Stop for excursion, reset, heat or expiry of the approved exposure duration. |
| S2 | Complete carrier/PTC/Pololu/XT30 path, external loads absent. Measure source, 5V_LIMITED, 5V_SAFE, VIN and actual internal loss. | Qualified voltage/current/drop envelope; no hot/intermittent termination or unintended VUSB drive. |
| S3a | Optical board only; EDGE/MON initially isolated. Probe TP1 TIA_OUT, TP2 THRESH, TP3 VREF, TP4 GND, TP5 3V3 and EDGE. | Stable rail and bounded branch current, no sustained oscillation/clipping/unsafe signal. |
| S3b | Add buffer and LED separately, then reviewed signal connections. | Safe 3.3 V logic and partial-power states; no unexplained loading or powered-off injection. |
| S4a | No Android. Controlled nominal/high VBUS tests (5.00/5.25 V candidates); VIN off/on, buffer Y initially isolated. | Input below 5.5 V including uncertainty; no unsafe rail rise or leakage. Use final reviewed source/leakage limits. |
| G4/S4b | Android host through the selected cable/module; external VIN stable. Monitor VIN, VUSB, 3V3, current/reset and temperature through allowed attach/detach and source-loss states. | Intended identity, Full-Speed, HID/vendor operation and safe isolation. Stop for backfeed, unstable host role or electrical deviation. |

Disconnect sources, verify discharged rails and inspect polarity/continuity
before rail/harness changes. USB attach/detach is allowed only within its
approved stage procedure. An owner-approved conditional sequence may advance
on successful recorded gates without repeated approvals. A failed prerequisite
or material configuration change stops progression.

Record firmware identity before each powered envelope. A programming computer
is another host/source and must be included after VUSB isolation qualification.
Recheck affected startup/load measurements after flashing. Preserve the normal
USB identity/speed and engineering request-ID/restart safeguards.

## 7. Separate USB and optical smoke sessions

Keep the Android probe foreground for USB information/HID checks. Its operation
closes the vendor session when complete: rediscover/reconnect before another
pulse. Do not reset valid persisted IDs. Initialize missing IDs only after the
approved full firmware restart, never merely from detach or enumeration.

Then follow the approved shutdown/disconnection sequence, remove Android USB,
and re-establish external-only optical power. Use the
[manual pattern procedure](../optical/bench-mount.md#5-manual-display-session).
HID is not required to cause the display transition. USB observations and actual
SFH 203/TIA/comparator observations are independently retained. Synthetic input,
GPIO-only stimulus or a software state log cannot replace physical evidence.
