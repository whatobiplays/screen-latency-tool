# Noncontact optical bench mount

## 1. Arrangement

Keep the 50 × 44 × 1.6 mm optical board, upright SFH 203, all placements, copper
and component values. Orient the component face toward the display, with the
nominal detector axis 6.77 mm from the left board edge and 10.50 mm from the top.
The display rests in its own stable cradle. A freestanding bridge/stand carries
the optical board and an opaque shroud. No shroud, lens, cable, clamp or other
fixture surface touches the glass. This is a supervised functional arrangement,
not a calibrated contact boot.

The [side drawing](bench-mount.svg) uses the glass as z=0. The proposed board
front plane is z=35 mm; the shroud projects 30 mm, leaving a nominal 5 mm gap.
These are design dimensions, not measurements. A 22 mm maximum projection is
allocated to every other component and supported cable under the board. Verify
actual connector bend/removal geometry against that allocation; do not force
the cable under it. RV1's 10.03 mm housing plus 1.52 mm screw and detector axial
length 8.5 ±0.4 mm come from the retained manufacturer drawings, before seating
offsets. Keep these full envelopes in the fit review.

Use independent supports outside the actual device outline, with a 220 mm clear
bridge span as a design allocation. Confirm the chassis/cradle fits that span
before relying on it. The support carries all cable and shroud loads. Locate
insulated holder patches after checking both board faces; no universal PCB edge
is a clamp zone. Never drill the board or support the shroud on diode leads.
Keep TP1–TP5, the trimmer and J1 accessible. Secure the cable to the stand with
a service loop; the connector's solder joints do not provide strain relief.

## 2. Numerical clearance budget

[bench-mount.json](bench-mount.json) contains the proposed geometry and bounded
engineering allocations. `node scripts/check-bench-mount.mjs` checks arithmetic
only. Every allocation below needs evidence from a dimensioned mockup/fixture
or conservative tolerance analysis before fabrication release.

| Allowance | mm | Basis to establish |
| --- | ---: | --- |
| Board-plane height/stop setting | 0.50 | Stop dimensions and repeatable setting against the glass datum |
| Shroud manufacturing | 0.30 | Actual printed/machined length tolerance; this is not an assumed printer specification |
| Setup repeatability | 0.40 | Repeated allowed placement/adjustment with positive stops |
| Fixture/device approach | 0.50 | Maximum ordinary flex, including edge taps used to change patterns |
| Cable approach | 0.50 | Supported service-loop movement through the intended operating envelope |
| Gap measurement uncertainty | 0.20 | Actual method, scale resolution, viewing alignment and repeatability |
| Positive reserve | 0.50 | Engineering separation retained after all allowances; owner reviews this configuration-specific reserve |

For the proposed 5 mm nominal gap, the conservative design lower bound is
`5 −0.50 −0.30 −0.40 −0.50 −0.50 −0.20 = 2.60 mm`, exceeding the 0.50 mm
reserve. The corresponding static indicated-gap threshold is
`0.20 +0.40 +0.50 +0.50 +0.50 = 2.10 mm`: measurement uncertainty, unexercised
repeatability/flex/cable approach and reserve. This is a derived proposal for
this configuration, not a universal 2 mm rule or an observed clearance.

If the minimum measurement actually exercises a movement allowance, do not
subtract it again. For a documented minimum across all three movement terms,
the indicated threshold becomes 0.70 mm (uncertainty plus reserve), provided
the tested envelope truly covers the approved use. Record which terms were
exercised and the raw minima. An undocumented assumption that a term was
exercised cannot lower the threshold.

Before fabrication release, confirm or revise the allocation values and record
the final numerical threshold, evidence and owner approval. If the stand,
cable or method cannot meet an allocation, increase nominal separation or
revise the support and recalculate; do not silently keep the old result.
After assembly, verify actual component/cable projection and minimum gap again.
Stop/reposition whenever clearance could close, the stand shifts, the cable
pulls the holder, or the measurement no longer supports a positive lower bound.

## 3. Shroud and optical plausibility

The 5.90 mm maximum flange does not by itself define a printable boot bore.
Use an 8.0 mm nominal noncontact bore as the initial shroud design and a short
opaque baffle, mounted independently from the diode. Verify bore tolerance,
alignment, lens clearance and the viewed display patch on the mockup. The
detector is recessed from the shroud tip; check that the aperture does not
vignette the intended patch. The SFH 203's 40° total cone is an optical input
to that check, not a guarantee of response or light rejection.

Test the proposed shroud material for opacity and clean edges. Record the
actual inner/outer dimensions and position; no contact-force or compression
test is required because the shroud does not touch the glass. If signal margin
or ambient rejection is insufficient, revise aperture/alignment within reviewed
clearance bounds and retain each failed physical run.

## 4. Manufacturing and assembly boundary

Use the retained native/CAM workflow in [the optical README](README.md).
Fabrication remains on HOLD until design-stage noncontact fit, actual tolerance
allocations, source-bound DFM/CAM/artwork and owner authorization are evidenced.
Post-assembly fit remains separate even if design-stage fabrication is released.
Do not require physical characterization of this not-yet-fabricated board as
a prerequisite to preparing its unchanged CAM package.

Dry-fit D1/J1/RV1 on received boards without forcing leads. Keep the accepted
standard footprints; record hole/board-thickness uncertainty as an evaluation
risk and reject binding, damaged or unsolderable parts. Hand-solder SMD parts
first, leave R4 empty, inspect/clean, then fit tall parts at the reviewed height.
Record both faces, markings, holder contact patches and trimmed-lead clearance.

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
6. Capture TP1 TIA_OUT, TP2 THRESH and EDGE with appropriate loading and a short
   supported ground approach. Retain 3V3/current/temperature context as needed.
   Establish actual black/white plateau ranges and comparator polarity; do not
   assume white means EDGE high. Place THRESH between usable measured levels.
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
