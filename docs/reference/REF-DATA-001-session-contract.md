# REF-DATA-001 — Immutable Session and Raw Trial Contract

**Document ID:** REF-DATA-001  
**Status:** Draft
**Readiness note:** Candidate v1 binary contract pending feasibility gate and schema reconciliation  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
**Depends On:** ARCH-001, REF-MET-001, REF-WIRE-001  
**Supersedes:** Raw/session descriptions in the legacy contracts plan and §6 of the closure amendment  
**Superseded By:** None

## Authority and scope

Every measurement exports a versioned session bundle (`.slt-session.zip`) with a SHA-256 file manifest, unmodified fixture raw bytes, calibration records, Android diagnostic events and optional external evidence. The analyzer is analytical authority; Android `summary.json` is a regenerable convenience artifact. Preserve failed, invalid and engineering-mode sessions; never relabel or overwrite them to appear qualified.

The following candidate layout is inherited from the approved design, **not yet frozen**. Feasibility work may expose the need for new fields—particularly actual elapsed inter-trial intervals, acquisition integrity evidence and ABBA block identity—before v1 version lock. Every change must be reconciled across Rust/Kotlin/C++ golden vectors. A missing required fact cannot be silently manufactured.

## Candidate raw binary encoding

`SLTB` batch header, exactly 48 bytes, little-endian:

```text
magic[4]="SLTB"
schema_major:u16=1; schema_minor:u16=0
record_size:u16=96
sensor_count:u8=5; threshold_percent:u8
batch_id:u32; trial_count:u32
timebase_hz:u64; prng_seed:u64
flags:u32; reserved[8]=0
```

Each fixed 96-byte record, explicit little-endian serialization:

```text
trial_id:u32
hid_sequence:u32
scheduled_delay_ticks:u64
t0_ticks:u64
capture_ticks:[u64;5]            // top,left,center,right,bottom; 0=missing
second_edge_delta_ticks:[u32;5]  // 0=no second edge
extra_edge_count:[u8;5]
status_flags:u16
transition:u8                     // 1=black_to_white; 2=white_to_black
reserved:[u8;4]=0
```

Status bit 0–10: MISSING_TOP, MISSING_LEFT, MISSING_CENTER, MISSING_RIGHT, MISSING_BOTTOM, MULTIPLE_EDGE, PWM_ACTIVITY, EARLY_EDGE, TIMEOUT, USB_TRANSFER_FAILURE, UNEXPECTED_HID_STATE. Bits 11–15 reserved. Raw validity requires `status_flags==0` and five capture timestamps nonzero.

**Unresolved before wire freeze:** candidate trial record only carries `scheduled_delay_ticks`, whereas the measurement contract requires the **actual observed inter-trial delay** for phase-decorrelation diagnostics. Supply a distinct actual elapsed field in a revised binary layout or a separately integrity-bound authoritative batch timing record; do not overwrite the scheduled field or infer actual from PRNG seed. Also ensure the timing representation preserves unmeasured HID release/reset integrity without lying about trial timestamps.

## Session layout

The three-batch tree below is illustrative of a simple condition only. It is not a complete ABBA evidence package and does not replace the requirement for three batches per transition direction in each of four identifiable ABBA blocks.

```text
manifest.json
device.json
fixture.json
calibration.json
display.json
configuration.json
batches/001/trials.bin
batches/001/android-events.json
batches/002/trials.bin
batches/002/android-events.json
batches/003/trials.bin
batches/003/android-events.json
diagnostics/adb/...              // optional, associated by session ID
summary.json                     // derived only
```

ABBA comparison requires four independently identifiable **condition blocks**, each with three batches per transition direction; packaging can be one qualified session per block/direction, grouped via stable experiment ID and explicit sequence index. The exact grouping schema is resolved before session freeze; do not misleadingly compress twelve batches into a three-batch condition without recording block identities.

The manifest includes `schema:{name,major,minor}`, session UUID, creation time, mode (`qualification` or `engineering`), qualified boolean, qualification reasons and `files[]` with relative normalized UTF-8 path, size and lowercase SHA-256. All files except manifest itself are hashed. Reject traversal, absolute path, backslashes, duplicate paths, unknown major, inconsistent versions, unexpected content or digest mismatch. Every JSON has its own name/major/minor schema ID (v1 1/0).

## Required metadata

- `device.json`: manufacturer/model/product/codename, Android release/SDK/build fingerprint/security patch, SoC if available, app version/code/build SHA, battery percentage and charging state.
- `fixture.json`: fixture serial/hardware/firmware/git revision, protocol, Full-Speed negotiation, timebase, record size, sensor IDs/revisions, characterized uncertainty, electrical self-test status/evidence.
- `calibration.json`: Display Pro HL measurement timestamp and nits, brightness override, transition and threshold, per-sensor black/white ADC, actual 10/50/90 DAC codes, gain/sensor rev, separately stored capture correction, optical reference/evidence, stability samples/drift.
- `display.json`: physical active rectangle and panel coordinate reference, logical geometry, selected display mode ID and resolution, requested/effective refresh and fixed-cadence evidence, requested orientation/effective rotation, actual renderer/presentation snapshot, optional ADB references. Preserve separate panel/chassis/logical orientation; one must not be inferred from the other.
- `configuration.json`: engineering/qualification and authoritative mode, input/render path, continuous/event-driven, stimulus, transition, threshold, target workload, refresh, VRR flag, performance preset, warm-up/stability outcomes, trial/delay/timeout settings, PRNG seeds, experiment ID and block order for ABBA where applicable.
- `android-events.json`: array of records with `hid_sequence`, `kind`, `timestamp_ns`, `clock_domain` and `details`. Canonical `kind` values: input_received, stimulus_state_changed, render_sampled, frame_submitted, frame_presented, display_state_changed, renderer_recreated, lifecycle_changed. `kind` is authoritative field name; legacy plan's `event_kind` is superseded. Each timestamp is diagnostic and carries its actual Android clock domain. Not named `t0`.
- `summary.json`: derived, non-authoritative; always reproducible from raw evidence and declared algorithms.

Unknown optional platform facts use `null`, not guessed values. Immutable sessions retain failed qualifier details and reasons. Instrument power, Android lifecycle/rendering, display and sensor integrity flags all contribute; no source may clear another's fault.

## Canonical fixtures and validation

Generate deterministic Rust vectors for wire requests/responses, valid/invalid raw trials, corrupt manifests, engineering-mode violations, failed batches, matched orientation pairs where **physical scanout stays fixed despite logical rotation**, and cases where independently measured physical gradients differ. Golden fixtures must be reproducible byte-for-byte; Kotlin/firmware codecs validate against the same bytes.

Do not reinterpret historical raw data on analyzer evolution. Compatible additions use explicit minor versioning where warranted; incompatible layout and meaning changes require a major version bump.

## Evidence storage

Commit qualification raw archives and SHA-256 to Git while the repository's cumulative raw qualification artifacts stay ≤250 MiB. Reaching that threshold triggers an explicit storage decision; do not silently switch to LFS or external links. Failed sessions remain retained. Separate temporary engineering outputs from qualified immutable evidence.
