# One-sensor optical fabrication reference

## 1. Exact design and intended order

Use the unchanged 50 × 44 mm, two-layer optical board with upright SFH 203,
the selected analog values and owner hand assembly. The independent noncontact
holder/shroud is defined in [bench-mount.md](bench-mount.md). This reference
supports a separate owner fabrication decision through
[Issue #38](https://github.com/whatobiplays/screen-latency-tool/issues/38);
it grants no upload, order, print or assembly authority.

| Protected input | SHA-256 |
| --- | --- |
| one-sensor.kicad_pcb | `3f7ee13263f27410885c2ea0af3558b2fb27d6718b94dacfc55aec67fd4bbe88` |
| one-sensor.kicad_sch | `a4b7185e4046e1e98c51c2a31939fae99246ea0aa45d6bf09f145577e745c5d7` |
| ../BOM.csv | `944130ce81ca301c7955ac59ce9b64e0eb15aabdea88f2a58849bc2ae6854559` |
| Retained issue43-fabrication.zip | `312b53282a2be2014daf101d50c063a38e5aee1a2211ef96162ce579b5dcf86b` |

The fabrication ZIP contains eight Gerber/Excellon files. Its retained source
commit is `e34ec363c87545a895e38da4d3bd624b9bc22560`. Keep that package and its
historical qualification manifest intact; the later noncontact construction
reference supplements the historical review ZIP. The optical source hashes
remain identical. Native KiCad 10.0.8 ERC/refilled DRC and source-preservation
evidence can therefore be reused, with their disclosed ignored categories.

Proposed service: one OSH Park standard two-layer set, nominal 1.6 mm/1 oz,
ENIG, purple soldermask, one populated board and two bare spares. The
[published service](https://docs.oshpark.com/services/two-layer/) bills $5/in²
for three copies: 2,200 mm² gives about $17.05 before order-time terms. This is
an estimate, not an uploaded design quote or shipping commitment. Use Gerbers,
not an assumed KiCad-10 native upload path. No PCBA service is proposed.

## 2. Native geometry and process comparison

The 2026-11-14 source comparison uses the native board and retained independent
Gerbonara 1.6.3 CAM measurements bound to the same board hash. These are design
measurements, not observations of manufactured boards.

| Feature | Source/CAM geometry | OSH Park published standard-layer limit | Interpretation |
| --- | --- | --- | --- |
| Routed trace width | 0.20 mm minimum | 0.1524 mm minimum | Above the process minimum |
| Default net-class clearance | 0.20 mm | 0.1524 mm spacing | Native DRC supplies connectivity/clearance evidence under that class |
| Vias | 0.60 mm land / 0.30 mm hole | 0.254 mm minimum hole; 0.127 mm annular ring | 0.15 mm nominal annulus; no undersized drill rounding |
| CAM copper to nominal edge | Top 1.10 mm; bottom 0.5005 mm | 0.381 mm | Above the published nominal keepout |
| Through-hole tools | D1 1.00 mm; RV1 0.80 mm; J1 0.75 mm | 0.254 mm minimum | Compare finished tolerances and part fit separately below |
| Drill coordinate quantization | ≤0.0005 mm in retained CAM check | Not a physical tolerance | Export precision does not establish drilled-hole position |

The [service table](https://docs.oshpark.com/services/two-layer/) and
[drill specification](https://docs.oshpark.com/submitting-orders/drill-specs/)
differ slightly on maximum position tolerance. Use the more conservative
±0.0635 mm for both hole size and position in this prototype fit review.
The minimum finished diameters are therefore D1 0.9365 mm, RV1 0.7365 mm and
J1 0.6865 mm. Supplier process limits do not establish actual part dimensions.

The standard nominal board thickness is 1.6 mm; its published core tolerance
is ±0.1524 mm, with copper, mask and silk layers separately listed. Use a
2.0 mm holder thickness envelope as an engineering allowance, not a claimed
fabricated maximum. Inspect actual thickness, bow, burrs and lead protrusion
after receipt. Do not force a board into the holder or a lead into a hole.

## 3. Explicit evaluation fit and artwork risks

The [JST PH drawing](https://www.jst-mfg.com/product/pdf/eng/ePH.pdf) recommends
0.7–0.8 mm PCB holes and specifies ±0.05 mm nonaccumulating pitch tolerance.
The native 0.75 mm hole is centered in that diameter range, but its calculated
0.6865 mm minimum is 0.0135 mm below the recommendation. The house's worst-case
position tolerance is also broader than JST's layout recommendation. **Do not
claim guaranteed J1 fit across all process corners.** For this evaluation,
present that yield risk explicitly with the unchanged-board fabrication
decision. On receipt, dry-fit the exact B4B-PH-K-S(LF)(SN) without force. A
binding, damaged or unsolderable board/part is rejected; drilling, substituted
headers or footprint changes require an amendment. Do not waive the fit check
because the nominal dimensions match.

Likewise, dry-fit the actual upright SFH 203 and 3296W-1-103LF against their
maker drawings before soldering. Preserve D1's pad 1 anode/SUM and pad 2
cathode/3V3 mapping, seating height and lead support. Nominal footprint and
recommended-pad agreement do not prove incoming pin tolerances or seated fit.

The retained front-silk stroke measurement reaches x=149.945764 mm against
the x=150 mm board edge: only 0.054236 mm nominal margin. It showed complete
EDGE/MON glyphs inside the outline, not guaranteed manufactured legibility.
The [fabricator's outline interpretation](https://docs.oshpark.com/submitting-orders/board-outline/)
does not promise preservation of near-edge text. The owner fabrication packet
must disclose possible clipping and require the assembly drawing, net labels
at both cable ends and independent unpowered continuity/polarity checks.
Never identify J1 pins solely from marginal silkscreen. No copper or outline
change is needed to use those procedural controls for the supervised prototype.

## 4. Authorization and later inspection

Fabrication review combines this source-bound CAD/CAM comparison with the
chosen noncontact mount's design-stage geometry, aperture, access and tolerance
case. The owner must separately authorize the exact PCB service/package and
accept the stated evaluation yield/legend risks. No contact-boot compression,
glass force, calibrated optical response or not-yet-fabricated-board test is a
prerequisite to preparing that decision.

At an authorized order, verify the ZIP hash and every layer/drill/outline in
the fabricator's actual preview, including 50 × 44 mm dimensions and drill
registration. A parser warning or discrepant preview is a stop. After receipt,
inspect board dimensions, holes/plating, shorts, markings, solderability and
unpowered part/holder fit. Preserve failed boards and observations. Source,
rail, thermal, USB/backfeed and optical qualifications remain separate physical
holds. Nothing in this reference is a power-use release.
