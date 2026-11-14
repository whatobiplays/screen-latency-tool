# One-sensor optical manufacturing files

## 1. Purpose and release boundary

For the supervised first prototype use the independent
[noncontact mount and manual display session](bench-mount.md). Its numerical
clearance limit comes from the configuration's tolerance/movement budget, not
a universal glass gap. Contact-boot compression/force is not a requirement for
that variant. Owner fit evidence and exact fabrication authorization remain
necessary. Preserve the existing optical PCB and the retained fabrication ZIP;
current assembly instructions supplement its historical review package.

This workflow prepares provisional files for the existing 50 × 44 mm, two-layer,
nominal 1.6 mm optical evaluation board. It preserves the upright SFH 203,
selected MPNs, footprints, placements, outline, routing, analog values and absence
of mounting holes. It does not export a power/USB carrier PCB.

An export is not a fabrication release. Resolve material package-fit and
manufacturing findings, obtain independent review, then request owner approval
through [Issue #38](https://github.com/whatobiplays/screen-latency-tool/issues/38).
Keep `FABRICATION HOLD` whenever a defensible physical arrangement cannot be
established. Physical optical characterization belongs to
[Issue #7](https://github.com/whatobiplays/screen-latency-tool/issues/7).
Missing historical Gate A inputs are a verification gap, not a demonstrated
stability failure.

## 2. Export from a verified checkout

Use Node.js 22 or newer, KiCad CLI **10.0.8**, and the `zip` command. Run from
the repository root. Set `KICAD_CLI` for a nonstandard installation; the default
is `/Applications/KiCad/KiCad.app/Contents/MacOS/kicad-cli`.

```sh
make check
KICAD_KEEP_REPORTS=1 make check-kicad
node scripts/export-optical-cam.mjs --out-dir /absolute/external/path/run-1
node scripts/export-optical-cam.mjs --out-dir /absolute/external/path/run-2
node scripts/export-optical-cam.mjs --compare /absolute/external/path/run-1 /absolute/external/path/run-2
git diff --check
```

Choose new output directories outside the checkout. Existing destinations are
rejected. A failed run may retain partial files for investigation. The exporter
attempts to remove archives and the checksum sidecar after failure; if the
filesystem refuses removal, it reports those errors alongside the original
failure. Any remaining files are failed artifacts and must not be fabricated.
Do not reuse a failed run's directory for another attempt.
The board hash is pinned to the approved input. A different board requires
design review before deliberately updating that pin.

Each run copies the native projects, required libraries, source BOM and assembly
guides into `review/native`. KiCad performs DRC with schematic parity,
all severities and exclusions, `--refill-zones` and `--save-board` on that
disposable board. A second DRC reads the saved board without refilling it.
Gerbers, drills and reference drawings use that same saved snapshot. The
workflow checks its hash after validation and export and verifies that
authoritative input hashes did not change.

Both schematics receive fresh ERC with error, warning and exclusion reporting.
The five historical DRC ignores and four historical ERC ignores remain visible
in the manifest. A separate diagnostic project enables the five ignored DRC
categories and raises permissive rules to the OSH Park manufacturing floors.
It checks the saved copper without another fill. Its findings require individual
disposition; it does not silently change the source project or confer approval.

## 3. CAM settings and contents

The fabrication ZIP contains only these eight files:

| File | Purpose |
| --- | --- |
| `one-sensor-F_Cu.gbr`, `one-sensor-B_Cu.gbr` | Front and back copper |
| `one-sensor-F_Mask.gbr`, `one-sensor-B_Mask.gbr` | Front and back solder-mask openings |
| `one-sensor-F_Silkscreen.gbr`, `one-sensor-B_Silkscreen.gbr` | Front and back legend; an empty back legend is valid |
| `one-sensor-Edge_Cuts.gbr` | Closed board outline |
| `one-sensor.drl` | Combined Excellon drill file |

Gerbers use X2 and net attributes, millimetres, 4.6 coordinate precision,
absolute origin, no mirroring, no border and no common layers. Mask openings
are subtracted from silk. Drills use millimetres, decimal coordinates,
absolute origin, alternate slot mode and no mirroring. The current design
contains 25 plated vias and nine plated component holes, with no NPTH.
No paste layers or stencil are needed for the selected iron assembly method.
These settings follow [OSH Park's KiCad export guidance](https://docs.oshpark.com/design-tools/kicad/generating-kicad-gerbers/).

`review.zip` contains the manifest, original board, saved export snapshot,
native projects/libraries, complete source BOM, optical-only BOM, assembly
guides, schematic PDF, front/back reference assembly PDFs, placement CSV,
Gerber job file when emitted, commands and native reports. The optical BOM
retains all 23 positions: 17 populated components, R4 DNP and five bare test
pads. Placement CSV is a reference, not a vendor-approved automated assembly
file; inspect its DNP and bare-pad entries before any later use.

`manifest.json` records source commit and dirty state, tool version, input and
saved-board hashes, ignored checks, diagnostic profile, settings and payload
inventory. `SHA256SUMS` covers the manifest and both ZIPs. Preserve the original
files and hashes. Final evidence must identify the clean reviewed commit;
uncommitted exports remain provisional.

## 4. Independent validation and fit

Check actual Gerber and Excellon data with an independent parser and rendered
artwork. Verify layer roles, the outline centreline dimensions, pad and drill
registration, drill tools, copper-edge spacing, annular rings, masks and readable
polarity labels. A stroked outline's bounding box is larger than its routed
centreline; do not mistake the 0.05 mm outline stroke for a board-size change.

Compare repeated exports before accepting the package. The comparison permits
only recognized Gerber/Excellon creation-time fields and comments to differ.
It neither edits the CAM files nor ignores geometry, tool, layer or net changes.
Keep raw hashes for each run; timestamps can prevent byte-identical exports.

Use [OSH Park's two-layer specifications](https://docs.oshpark.com/services/two-layer/)
for actual DFM: 0.1524 mm copper width/spacing, 0.254 mm minimum drill,
0.127 mm annular ring, 0.381 mm copper-to-edge clearance, mask webs and
registration, legend limits and standard construction. Larger existing
clearances stay in force. The public approximately $17.05 three-board estimate
for this outline is not a design-specific quote. No fabricator upload or
preview acceptance is implied.

Complete the dimensioned fit review and
[BENCH-002 assembly and unpowered inspection](../../../docs/bench/PROTOTYPE-BENCH-GUIDE.md#provisional-soldered-implementation).
The boot, detector stand-off, clamp, cable bend/retention, display contact force,
trimmer adjustment and underside lead clearance require evidence beyond a
2D board plot. Keep R4 DNP and CABLE-PH04 black=`3V3`, red=`GND`.
No instructions here authorize assembly, connection or energization.
`INITIAL_CURRENT_LIMIT=UNSET` and all Stage-B/C gates remain unchanged.
