# Noncontact optical bench mount

## 1. Geometry and construction

The independent bridge holds the unchanged 50 ×44 mm optical PCB component-face
first toward the display. Keep the upright SFH 203, analog values, placements,
copper and outline. Its nominal detector axis is 6.77 mm from the left board
edge and 10.50 mm from the top. The display rests in its own stable cradle;
no shroud, cable, holder, clamp or fixture part touches the glass.

[bench-mount.scad](bench-mount.scad) defines the proposed parts and hardware
layout; [bench-mount.svg](bench-mount.svg) shows the board/contact and optical
sections. Dimensions are design values. They establish a configuration to
review, not a measured fit or permission to print, assemble or energize it.

| Feature | Design envelope / later admission |
| --- | --- |
| Bridge | 220 mm clear opening, 260 ×40 ×25 mm beam; two outboard 70 ×140 ×12 mm feet; overall 360 ×140 mm footprint |
| Independent DUT/cradle | At most 190 ×100 mm; glass datum 20–38 mm above bench; actual case, grip, vent and cable clearance checked before setup |
| Board plane | 45 mm above glass; 30 mm shroud projection gives **15 mm nominal tip gap**. The former 5 mm allocation is superseded by this proposal. |
| Board support | Four 1 ×4 mm dielectric bearing patches at local x=0–1/49–50, y=24–28/40–44; local origin is native KiCad (100,100). Adjustable retainers take up received board thickness; vertical play ≤0.15 mm. |
| Received board | 1.4–2.0 mm holder envelope; 2.0 mm is a design allowance, not a fabricator guarantee. Backside lead projection ≤1.5 mm, with ≥1.5 mm further clearance to the rear crossbars. |
| Components | RV1 full projection ≤13 mm; diode body ≤9.4 mm including permitted seating offset; support the diode through the board, never through its lens/leads. |
| Cable | Supported outboard tunnel and two saddles; admitted bundle 2.5–4 mm, unforced routing with proposed centreline bend radius ≥10 mm inside the tunnel. The supported service loop and saddle hardware remain above the 30 mm shroud tip. This is a harness fit allocation, not a maker bend-radius specification. |
| Material/load | Ordinary opaque unfilled PETG, solid load-bearing sections; no conductive/CF/ESD filament. Analytical envelope 10 N downward /1 N lateral, fixture ≤35°C and initially ≤1 hour between inspection. These are proposed use bounds requiring actual validation. |

The four patches were screened against native component courtyards and discrete
tracks/vias. The backside includes **masked GND copper**: contact is with smooth
insulating PETG under low load, never metal or abrasive edges. Inspect mask,
burrs, bow, trimmed leads and every retainer before admission. Do not drill the
board, clamp a component, or assume all board edges are clear.

The beam can be placed diagonally on the P1S: its nominal rotated bounding
square is about 212.2 mm before brim. Check the actual slicer, supports,
orientation, layer adhesion and bed clearance before an owner-authorized print.
Print the parameterized spacers/shims for the actual measured board and glass
datum. Fixed brackets, captive stops and shims carry the height load; friction
in a slotted adjustment alone is insufficient. Secure both feet to the bench.
The proposed CM20 clamp is conditional on actual bench thickness/reach; bare
PETG friction is not a substitute. Hardware packages are in
[BENCH-003](../../../docs/bench/PROTOTYPE-PROCUREMENT-READINESS.md).

Fit the received hardware within the modeled head Ø12 ×5 mm, washer OD13
×2 mm and nut AF10.2 ×5.5 mm envelopes. All screws are fully threaded M6-1.0:
10 ×40 mm, 6 ×35 mm and 4 ×30 mm installed, with 20 nuts and 36 washers.
The maximum-thickness stack permits shaft length −0.5 mm and material grip
+0.4 mm and leaves at least 2.5 mm free thread beyond a fully engaged nut.
Check actual head, nut, shaft and thread engagement before assembly release.
The source contains every fastener; the assembly export is a visualization.

Finish structural sections/caps within ±0.15 mm, bores Ø6.8 +0.2/0 mm and
counterbores Ø14 +0.2/0 mm. Nominal retainer spacers are received board thickness
+0.10 mm; the proposed ±0.04 mm take-up method gives 0.06–0.14 mm play. Confirm
that method or verify actual assembled play ≤0.15 mm with suitable borrowed
or existing dimensional tools. Printer settings alone do not establish it.
Inspect all support removal, lips, bores and channels before installing parts.

TP1–TP5 and the trimmer are accessed with the DUT withdrawn or the fixture
parked. Keep all scope bodies, grounds and service loops independently supported
and off the glass. On-display measurement uses the existing offboard MON and
EDGE paths as described in section 5; a conventional probe barrel need not fit
inside the 45 mm board-to-glass space. Check J1 insertion/removal while parked.

Route the service loop behind the right pillar at global Y≥60 while crossing
X110–130. Keep its floor at Z≥58 mm; at the maximum admitted glass datum this
is 20 mm above glass. The modeled carrier saddle head projects 28 mm below
the board plane. The 30 mm shroud is closest in the nominal parallel pose;
under tilt an off-axis surface can be closer, as bounded in section 2.
The proposed centreline to the second saddle uses at most 180 mm of the
nominal 200 mm CABLE-PH04, leaving 20 mm for the far termination. Check actual
length, unforced bend, retention and every cable/probe excursion before use.
The calculator cannot establish a physical cable envelope from nominal inputs.

## 2. Clearance analysis and measurable acceptance

[bench-mount.json](bench-mount.json) is the arithmetic input for
`node scripts/check-bench-mount.mjs`. It uses these conservative allocations:

| Term | mm | Basis and required observation |
| --- | ---: | --- |
| Combined datum, setting and gap-method uncertainty | 2.00 | Proposed coarse noncontact scale/side-alignment method; actual resolution, parallax, reference transfer and repeatability must support this bound. No precision-gauge purchase is presumed. |
| Shroud manufacture | 0.30 | Actual projection tolerance inspected after printing |
| Retainer play | 0.15 | Adjustable take-up, verified after securing the received board |
| Whole-fixture approach from tilt | 4.00 | Carrier plane ≤3° to glass; bound the 28 mm off-axis hardware/harness projection over the full 110 mm radius, not the tip alone |
| Fixture approach | 1.10 | Rounded analytical structure/joint bound below, including supported cable load |
| DUT/cradle upward movement | 1.00 | Explicit admission limit under ordinary edge taps and allowed handling; currently unmeasured |
| Positive reserve | 2.00 | Chosen margin for this configuration; **not a universal minimum glass gap** |

The swept glass/body footprint must remain within global X=−95..95 and
Y=−50..50 relative to the sensing axis, including allowed horizontal movement.
Fixed bracket webs begin at Y=51; actual outline/position uncertainty must not
permit contact with them. The actual case/cradle and glass boundary are checked
before setup. Outside this admitted footprint, revise the arrangement rather
than extrapolating the result.

Limit the **carrier plane to ≤3° relative to glass**. Combined detector/shroud
optical-axis error may total ≤6°; these are different envelopes. Over the glass,
nonshroud parts and supported cables have projection at most 28 mm and radius
at most 110 mm. Their maximum approach relative to the nominal 30 mm shroud is
`28 cos(3°)+110 sin(3°)−30 = 3.71858 mm`. The shroud itself gives
`30 cos(3°)+19 sin(3°)−30 = 0.95327 mm`. Round the larger to **4.0 mm**.
The 0.3 mm manufacturing term remains separate. Checking the tip radius alone
would undercount the off-axis saddle head. No unsupported cable may enter this
space. The 110 mm radius encloses the 107.36 mm rectangle corner plus the approximately 1.47 mm lateral projection shift at 3° and dimensional allowance.

Verify carrier/glass angle separately in two orthogonal views with a supported
combined-angle uncertainty. An indicated combined bound ≤1° plus uncertainty
≤2° is one possible admission method. Use a longer carrier-plane reference
baseline or suitable borrowed angle/dimensional tools if a 30 mm side view
cannot establish it. Matched stops establish nominal parallelism; they do not
prove alignment to the actual glass. A photograph alone is insufficient.

The height-setting uncertainty is already included in the 2 mm combined method
bound. Cable force is already included in the structural load case. Neither is
charged a second time. The conservative design lower bound is
`15 −2.00 −0.30 −0.15 −4.00 −1.10 −1.00 = 6.45 mm`, above the 2 mm reserve.
A static indicated-gap reading must exceed the derived
`2.00 +0.15 +4.00 +1.10 +1.00 +2.00 = 10.25 mm`; round upward to **10.3 mm**.
If a recorded minimum actually exercises all play, tilt, fixture/cable and DUT
movement terms, the threshold is **4.0 mm** (method uncertainty plus reserve).
Measure that minimum over every over-glass holder, fastener, cable and probe
surface, including the shroud.
Record each exercised term; an undocumented assumption cannot reduce the limit.
Do not subtract nominal shroud length/manufacturing again from an actual gap.

A supported finer method with combined U=0.4 mm would give an 8.05 mm conservative
analytical lower bound, a static threshold rounded upward to 8.7 mm and a fully
exercised threshold of 2.4 mm. This optional method needs its own evidence.
If the actual method is worse than either proposal, substitute its uncertainty
and recalculate before use. A ruler's presence alone does not prove U=2 mm.

The structural estimate assumes E=500 N/mm² for solid PETG. This is a derated
engineering assumption, not a guaranteed material property or measured print.
At the full 260 mm span, beam I=24,000 mm⁴ gives `PL³/(48EI)=0.30514 mm`.
Halved post section contributes 0.023232 mm through an 80 mm rotation lever.
Two minimum 17.5 ×15.8 mm arms, each carrying 5 N over a conservative 95 mm
cantilever, contribute 0.49685 mm. An explicit 0.18 mm local/joint allowance
brings the sum to about 1.00522 mm, rounded upward to 1.10 mm. This simple
static analysis does not measure print strength, joints, creep or device motion.
The [Prusament PETG sheet](https://prusament.com/wp-content/uploads/2022/10/PETG_Prusament_TDS_2021_10_EN.pdf)
gives typical specimen moduli around 1.5/1.6 GPa; it does not guarantee a
particular print. The lower assumed modulus and initial time/temperature bounds
need validation against actual creep/deflection. Actual retention, repeated
setup, ordinary cable/edge-tap loads and time/ambient
limits must be checked on the manufactured fixture before physical admission.

Fail closed if the gap can close, a foot/stop/retainer moves, a cable exceeds
its supported envelope, or measurement uncertainty prevents a positive lower
bound. Shut down and withdraw/reposition before adjustment. Preserve failures.
A positive calculated reserve never replaces the assembled minimum-gap record.

## 3. Shroud, aperture and design-stage fit

The stand-carried flare begins 12 mm from the component face and ends at 30 mm.
Its top ID/OD is 16/20 mm and tip ID/OD is 34/38 mm. Maximum detector body
projection 9.4 mm leaves 2.3 mm to the top rim after the 0.3 mm length allowance.
Keep all support arms outside the screened cone. Do not support the shroud on
the diode or introduce a glass-contact boot.

The SFH 203's typical ±20° response angle is an optical plausibility input,
not a guaranteed acceptance cutoff or ambient-rejection result. The screen
uses `r(d)=sqrt(0.5)+1+d tan(20°)+d tan(6°)` for the nominal sensitive-area
corner, 1 mm aggregate lateral registration and combined axis tilt. After a
0.3 mm inward radius allowance, radial margins are about 0.364 mm at d=12 and
0.921 mm at d=30. At glass d=45 the screened patch radius is 22.82 mm. Put the
axis at least 24 mm inside the actual active display boundary.

Public nominal Pocket AIR Mini dimensions (165.9 ×82.5 ×18.7 mm; grip27.6 mm)
and the 4.2-inch 4:3 display make this arrangement plausible. They do not
establish the owned case/cradle, active offsets, glass datum or stiffness.
The 190 ×100 mm admitted cradle and independent adjustable stand are the
bounded design-stage fit case. Record actual owned geometry before clamp
purchase/fixture setting as applicable; a mismatch changes the fixture, not
the protected PCB. For angular admission, compare the combined departure in two orthogonal side
views against the 30 mm shroud axis and glass reference: 6° corresponds to
3.15 mm lateral departure over 30 mm. An indicated bound ≤4° with supported
uncertainty ≤2° is one acceptable method; a photograph alone does not prove
that capability. Improve or borrow the check if needed before physical use.
Actual printed opacity, alignment and optical signal/ambient margin remain
physical checks. No contact-force/compression qualification is
required for this noncontact arrangement.

## 4. Fabrication and assembly boundary

Use [the optical fabrication reference](fabrication-review.md) and retained
native/CAM workflow in [README.md](README.md). Source-bound design-stage fit,
DFM/CAM/artwork review and explicit owner authorization are required before
fabrication. Physical characterization of the not-yet-fabricated board is not
a prerequisite to its evaluation order. Unpowered received-board/holder fit
and all source, rail, thermal, USB and optical holds remain separate.

Dry-fit exact D1/J1/RV1 without force; retain the documented hole-tolerance,
board-thickness and near-edge legend risks. Hand-solder SMD parts first, leave
R4 empty, inspect/clean, then fit tall parts at the reviewed seating height.
Record both faces, markings, support patches and lead clearance. No order,
print, assembly, source operation or test is reported by this construction
reference. Issue #38 carries the separately approvable purchase/PCB packets.

## 5. Manual display session

The USB probe and optical page are separate foreground sessions. First complete
the approved USB/HID/vendor session and shutdown/disconnect sequence. For the
optical session, Android uses its own power and the fixture uses qualified
external-only power. No active vendor session or HID-driven screen change is
required.

1. Serve the optical directory narrowly from the owner's computer on the local
   trusted LAN, for example `python3 -m http.server 8765 --bind <LAN-address>
   --directory hardware/prototype/optical`. Open `manual-patterns.html` in the
   target's existing compatible browser. Record SHA-256 for both HTML and its
   adjacent manual-patterns.js, URL, browser/OS
   version and target model. The server serves this directory only; no fixture
   USB connection is needed. Do not expose a repository root or private files.
2. Lock the chosen orientation and supported refresh setting. Disable adaptive
   brightness, auto-dim, night/eye-comfort filters, HDR and other automatic
   display changes where available. Set a usable fixed manual brightness and
   record its actual value; a midpoint is only a starting choice. This is not
   a 200-nit or colorimetric qualification.
3. Put the device in its stable cradle. Position the sensor over a recorded
   interior patch, away from the edge-tap regions, and verify the approved
   clearance. Confirm the shroud/fixture does not move when the operator taps.
4. Use the page's explicit fullscreen control. Verify actual fullscreen coverage,
   allow browser entry banners to disappear and begin the BLACK segment only
   once the view and measured plateau have settled. A fullscreen request or
   screenshot alone does not establish optical output.
5. Select BLACK with the left edge region and WHITE with the right edge region.
   The center does nothing. Patterns are RGB #000000 and #FFFFFF; changes occur
   only from an accepted manual tap. Hold each state at least two seconds and
   longer if needed to retain at least one second of settled observations.
6. With the DUT withdrawn/fixture parked, acquire direct TP1 TIA_OUT, TP2
   THRESH, TP4 GND and TP5 3V3 using the reviewed probe loading/grounding.
   Record the trimmer position and threshold before the run. For the on-display
   run capture real MON and EDGE at the existing offboard CABLE-PH04 paths
   (pin 4 gray MON, pin 3 blue EDGE, pin 2 red GND; independently verify nets,
   never rely on color alone). MON is TIA_OUT through R6=1 kΩ: record this
   physical proxy, probe loading and any observed difference from direct TP1.
   It is not an ADC or a simulated signal. Retain actual black/white plateau
   ranges, comparator polarity and 3V3/current/temperature context. Do not
   assume white means EDGE high. Adjust THRESH between usable measured levels
   only after shutdown/parking, then start a new identified run.
   After each run, shut down and withdraw the DUT under the approved sequence;
   re-establish the parked measurement and retain direct TIA/THRESH/3V3 again
   at unchanged settings. Identify these observations as **before/after, not
   simultaneous with the on-display acquisition**. Unexpected threshold/rail
   drift or proxy/loading behavior invalidates the claimed stable-threshold
   segment; preserve it and investigate before repeating. A separately reviewed
   supported direct-probe arrangement can be used if truly needed, without
   placing probe bodies or grounds against the glass.
7. Retain steady BLACK/WHITE controls with no operator action, then repeat the
   manual transitions. Twenty cycles is the conservative planning default;
   finalize repetitions from the declared coverage and observed variability
   before the run, without discarding failures.
8. With a pattern fixed, exercise recorded ordinary ambient-light changes
   without touching the stand. Log lamp/room conditions, plateau shift, noise
   and unexpected edges. Edges during a constant pattern are not successful
   display transitions. Record PWM, saturation, touch-induced motion and UI
   interruptions separately; stop or narrow the supported conditions honestly.
9. Exit fullscreen and export the page's diagnostic request log after acquisition.
   Keep it with raw scope files, instrument settings/probes, acquisition IDs,
   display settings, mount photos, minimum-gap readings and operator sequence
   observations. Page event times are intent diagnostics, never photon times.

Fullscreen exit, backgrounding, resize, orientation change or a system overlay
invalidates the affected segment. Re-establish settings/clearance and begin a
new segment. A brightness/threshold/mount change starts a new identified run.

Accept smoke operation only from repeatable real TIA and EDGE responses in both
directions, usable threshold/noise margin, basic ambient rejection and safe
rails/clearance. No software log, synthetic HID input or simulated optical
signal replaces those observations. HID-caused transitions belong to Issue #6;
T50, full luminance/PWM characterization and calibration remain Issue #7.
