# Contracts and Tooling Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the monorepo build skeleton, versioned USB/session contracts, golden vectors, and a Rust contract library that every later subsystem can implement against independently.

**Architecture:** The wire protocol is a small fixed little-endian binary protocol suitable for Teensy firmware, Kotlin, and Rust without runtime code generation. Session exports use versioned JSON metadata plus a fixed binary trial stream, with SHA-256 file integrity recorded by the manifest. Golden vectors are the cross-language compatibility authority.

**Tech Stack:** Rust/Cargo, JSON Schema 2020-12, fixed little-endian binary encoding, SHA-256, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-31-screen-latency-measurement-design.md`

## Global Constraints

- Preserve all twelve measurement invariants in design Section 3.
- Protocol and session schemas are explicitly versioned; v1 is `major=1`, `minor=0`.
- Unknown protocol major versions must be rejected, never guessed.
- Raw fixture timestamps remain immutable evidence; derived values and corrections are separate.
- All raw trials, including invalid trials, remain representable.
- Android clocks never enter the authoritative latency calculation.
- Engineering-mode exports must be distinguishable from qualification-mode exports in the manifest.
- No direct Calibrite USB integration, high-speed-camera path, controller-inclusive latency, HDR methodology, or public-distribution work belongs in this plan.

---

## File Structure

Create this foundation:

```text
Cargo.toml
rust-toolchain.toml
.github/workflows/ci.yml
protocol/
├── README.md
├── wire-v1.md
├── session-v1.md
├── schema/
│   ├── manifest-v1.schema.json
│   ├── device-v1.schema.json
│   ├── fixture-v1.schema.json
│   ├── calibration-v1.schema.json
│   ├── display-v1.schema.json
│   ├── configuration-v1.schema.json
│   ├── android-events-v1.schema.json
│   └── summary-v1.schema.json
├── vectors/
│   ├── wire/
│   ├── trials/
│   └── sessions/
└── rust/slt-protocol/
    ├── Cargo.toml
    ├── src/lib.rs
    ├── src/wire.rs
    ├── src/trials.rs
    ├── src/session.rs
    └── tests/golden_vectors.rs
tools/session-fixture/
├── Cargo.toml
└── src/main.rs
```

`protocol/rust/slt-protocol` is a reference codec/validator, not a network service. `tools/session-fixture` deterministically emits canonical example sessions for Android/analyzer tests.

---

### Task 1: Create the Rust workspace and deterministic CI baseline

**Files:**
- Create: `Cargo.toml`
- Create: `rust-toolchain.toml`
- Create: `.github/workflows/ci.yml`
- Create: `protocol/rust/slt-protocol/Cargo.toml`
- Create: `protocol/rust/slt-protocol/src/lib.rs`
- Create: `tools/session-fixture/Cargo.toml`
- Create: `tools/session-fixture/src/main.rs`

**Interfaces:**
- Produces: workspace packages `slt-protocol` and `session-fixture`.
- Produces: root validation command `cargo test --workspace --all-targets`.

- [ ] **Step 1: Write a failing workspace smoke test**

Create `protocol/rust/slt-protocol/src/lib.rs`:

```rust
#[cfg(test)]
mod tests {
    #[test]
    fn protocol_version_is_v1_0() {
        assert_eq!(crate::PROTOCOL_MAJOR, 1);
        assert_eq!(crate::PROTOCOL_MINOR, 0);
    }
}
```

- [ ] **Step 2: Run the test and verify the missing constants fail compilation**

Run: `cargo test -p slt-protocol`

Expected: compile failure for undefined `PROTOCOL_MAJOR`/`PROTOCOL_MINOR`.

- [ ] **Step 3: Add the minimal workspace and constants**

Root `Cargo.toml`:

```toml
[workspace]
resolver = "2"
members = [
  "protocol/rust/slt-protocol",
  "tools/session-fixture",
]
```

`protocol/rust/slt-protocol/src/lib.rs`:

```rust
pub const PROTOCOL_MAJOR: u8 = 1;
pub const PROTOCOL_MINOR: u8 = 0;

pub mod session;
pub mod trials;
pub mod wire;
```

Pin the committed toolchain in `rust-toolchain.toml` to the stable Rust release installed when this task is executed; do not use `nightly`. Commit the resulting `Cargo.lock`.

- [ ] **Step 4: Add CI and verify the workspace**

CI must run on Linux, macOS, and Windows:

```yaml
- run: cargo test --workspace --all-targets
- run: cargo fmt --all -- --check
- run: cargo clippy --workspace --all-targets -- -D warnings
```

Run locally: `cargo test --workspace --all-targets`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add Cargo.toml Cargo.lock rust-toolchain.toml .github/workflows/ci.yml protocol/rust/slt-protocol tools/session-fixture
git commit -m "build: establish protocol workspace"
```

---

### Task 2: Define and implement the v1 USB control wire format

**Files:**
- Create: `protocol/README.md`
- Create: `protocol/wire-v1.md`
- Create: `protocol/rust/slt-protocol/src/wire.rs`
- Create: `protocol/rust/slt-protocol/tests/golden_vectors.rs`
- Create: `protocol/vectors/wire/get_fixture_info.request.bin`
- Create: `protocol/vectors/wire/get_fixture_info.response.bin`
- Create: `protocol/vectors/wire/configure_batch.request.bin`

**Interfaces:**
- Produces: `WireHeaderV1`, `MessageV1`, `CommandV1`, `StatusV1`, `WireError`, `encode_message`, `decode_message`.
- Consumes later: firmware bulk endpoint, Android `UsbFixtureClient`, Rust analyzer/session tooling.

Use this exact 20-byte little-endian header:

```text
offset size field
0      4    magic = ASCII "SLT1"
4      1    major = 1
5      1    minor = 0
6      1    message_type: 1=request, 2=response
7      1    flags
8      4    request_id
12     2    command
14     2    status (0 in requests)
16     4    payload_len
```

Command IDs:

```text
0x0001 GET_FIXTURE_INFO
0x0002 RUN_ELECTRICAL_SELF_TEST
0x0003 RUN_OPTICAL_SELF_TEST
0x0004 CALIBRATE_SENSORS
0x0005 SET_THRESHOLDS
0x0006 CONFIGURE_BATCH
0x0007 ARM_BATCH
0x0008 GET_BATCH_STATUS
0x0009 DOWNLOAD_RESULTS
0x000A ABORT_BATCH
```

Status IDs:

```text
0 OK
1 UNSUPPORTED_VERSION
2 BAD_REQUEST
3 INVALID_STATE
4 NOT_READY
5 HARDWARE_FAULT
6 INTEGRITY_FAULT
7 BUSY
8 RANGE_ERROR
```

- [ ] **Step 1: Write codec tests before the implementation**

```rust
#[test]
fn rejects_unknown_major_version() {
    let mut bytes = include_bytes!("../../../vectors/wire/get_fixture_info.request.bin").to_vec();
    bytes[4] = 2;
    assert!(matches!(decode_message(&bytes), Err(WireError::UnsupportedMajor(2))));
}

#[test]
fn round_trips_get_fixture_info_vector() {
    let expected = include_bytes!("../../../vectors/wire/get_fixture_info.request.bin");
    let msg = MessageV1::request(0x11223344, CommandV1::GetFixtureInfo, vec![]);
    assert_eq!(encode_message(&msg), expected);
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cargo test -p slt-protocol --test golden_vectors wire`

Expected: compile/test failure because wire types and vectors do not exist.

- [ ] **Step 3: Implement the codec and specification**

Implement checked parsing with:

- exact magic check
- exact major-version check
- payload length equality check
- command/status enum validation
- no implicit trailing bytes
- 16 MiB hard maximum payload to prevent pathological allocations in desktop tooling; firmware may impose a lower transport chunk size

Document each command's request/response payload in `wire-v1.md`. `DOWNLOAD_RESULTS` uses request payload `{offset:u32, length:u32}` and returns raw result bytes in the response payload so Android can retrieve a large batch in bounded chunks after acquisition.

- [ ] **Step 4: Generate and lock golden vectors**

Make `session-fixture` emit the three canonical wire vectors, then verify byte-for-byte stability:

Run: `cargo run -p session-fixture -- wire-vectors protocol/vectors/wire`

Run: `cargo test -p slt-protocol --test golden_vectors`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add protocol/README.md protocol/wire-v1.md protocol/rust/slt-protocol protocol/vectors/wire tools/session-fixture
git commit -m "feat: define v1 fixture wire protocol"
```

---

### Task 3: Define the immutable v1 raw trial binary format

**Files:**
- Create: `protocol/session-v1.md`
- Create: `protocol/rust/slt-protocol/src/trials.rs`
- Create: `protocol/vectors/trials/black-to-white-1000hz.bin`
- Modify: `protocol/rust/slt-protocol/tests/golden_vectors.rs`
- Modify: `tools/session-fixture/src/main.rs`

**Interfaces:**
- Produces: `TrialFileHeaderV1`, `TrialRecordV1`, `DecodedTrialFileV1`, `TrialStatusFlags`, `TransitionDirection`, `TrialError`, `encode_trial_file`, `decode_trial_file`.
- Consumes later: Teensy in-RAM batch serializer, Android export, Rust analyzer.

Use a fixed 48-byte file header:

```text
magic[4]="SLTB"; schema_major:u16=1; schema_minor:u16=0;
record_size:u16=96; sensor_count:u8=5; threshold_percent:u8;
batch_id:u32; trial_count:u32; timebase_hz:u64; prng_seed:u64;
flags:u32; reserved[8]=0
```

Use a fixed 96-byte trial record:

```text
trial_id:u32
hid_sequence:u32
scheduled_delay_ticks:u64
t0_ticks:u64
capture_ticks:[u64;5]             // order: top,left,center,right,bottom; 0 means missing
second_edge_delta_ticks:[u32;5]   // 0 means no second edge recorded
extra_edge_count:[u8;5]
status_flags:u16
transition:u8                     // 1=black_to_white, 2=white_to_black
reserved:[u8;4]                   // must encode as zero
```

Status bits:

```text
0 MISSING_TOP
1 MISSING_LEFT
2 MISSING_CENTER
3 MISSING_RIGHT
4 MISSING_BOTTOM
5 MULTIPLE_EDGE
6 PWM_ACTIVITY
7 EARLY_EDGE
8 TIMEOUT
9 USB_TRANSFER_FAILURE
10 UNEXPECTED_HID_STATE
11..15 reserved
```

A raw trial is analytically valid only when `status_flags == 0` and all five capture ticks are nonzero.

- [ ] **Step 1: Write binary layout tests**

```rust
#[test]
fn trial_record_v1_is_exactly_96_bytes() {
    assert_eq!(TrialRecordV1::ENCODED_SIZE, 96);
}

#[test]
fn decoder_rejects_nonzero_reserved_bytes() {
    let mut bytes = canonical_trial_file();
    bytes[48 + 95] = 1;
    assert!(matches!(decode_trial_file(&bytes), Err(TrialError::ReservedBitsSet)));
}
```

- [ ] **Step 2: Verify failure**

Run: `cargo test -p slt-protocol trials`

Expected: FAIL because trial codec is absent.

- [ ] **Step 3: Implement strict encoding/decoding**

Use integer ticks only. Do not convert to floating-point microseconds in this crate. Reject:

- wrong record size
- unknown major version
- trial count/file-length mismatch
- unknown transition values
- nonzero reserved bits/bytes
- `sensor_count != 5`

- [ ] **Step 4: Add a deterministic trial vector**

Generate a small vector containing valid, missing-center, multiple-edge, PWM, timeout, and USB-failure records. The filename uses `1000hz` only to make expected tick arithmetic human-readable; it is not an authoritative device dataset.

Run: `cargo run -p session-fixture -- trial-vector protocol/vectors/trials/black-to-white-1000hz.bin`

Run: `cargo test -p slt-protocol`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add protocol/session-v1.md protocol/rust/slt-protocol protocol/vectors/trials tools/session-fixture
git commit -m "feat: define immutable raw trial format"
```

---

### Task 4: Define session metadata schemas and package integrity

**Files:**
- Create: `protocol/schema/manifest-v1.schema.json`
- Create: `protocol/schema/device-v1.schema.json`
- Create: `protocol/schema/fixture-v1.schema.json`
- Create: `protocol/schema/calibration-v1.schema.json`
- Create: `protocol/schema/display-v1.schema.json`
- Create: `protocol/schema/configuration-v1.schema.json`
- Create: `protocol/schema/android-events-v1.schema.json`
- Create: `protocol/schema/summary-v1.schema.json`
- Create: `protocol/rust/slt-protocol/src/session.rs`
- Modify: `protocol/session-v1.md`
- Modify: `protocol/rust/slt-protocol/Cargo.toml`

**Interfaces:**
- Produces: `SessionManifestV1`, `SessionMode`, `FileDigest`, `SessionBundleV1`, `load_session_directory`, `validate_manifest`, `validate_session_directory`, `compute_file_sha256`.
- Consumes later: Android exporter and Rust analyzer.

The manifest is JSON and begins with this contract:

```json
{
  "schema": {"name":"screen-latency-session","major":1,"minor":0},
  "session_id":"00000000-0000-0000-0000-000000000000",
  "created_at":"2026-08-31T00:00:00Z",
  "mode":"qualification",
  "qualified":false,
  "qualification_reasons":[],
  "files":[]
}
```

Each `files[]` entry contains `path`, lowercase hexadecimal `sha256`, and byte `size`. `manifest.json` does not hash itself. All other required files are hashed. Paths are relative, UTF-8, forward-slash separated, and may not contain `..`, absolute roots, or backslashes.

- [ ] **Step 1: Write schema and path-integrity tests**

```rust
#[test]
fn engineering_session_cannot_be_qualified() {
    let mut manifest = minimal_manifest(SessionMode::Engineering);
    manifest.qualified = true;
    assert!(validate_manifest(&manifest).is_err());
}

#[test]
fn rejects_digest_path_traversal() {
    let mut manifest = minimal_manifest(SessionMode::Qualification);
    manifest.files.push(FileDigest::new("../trials.bin", [0; 32], 0));
    assert!(validate_manifest(&manifest).is_err());
}
```

- [ ] **Step 2: Verify failure**

Run: `cargo test -p slt-protocol session`

Expected: FAIL before metadata validation exists.

- [ ] **Step 3: Implement schemas and Rust validation**

Use JSON Schema draft 2020-12 and serde types. Required conceptual files are:

```text
manifest.json
device.json
fixture.json
calibration.json
display.json
configuration.json
batches/001/trials.bin
batches/001/android-events.json
summary.json
```

`android-events.json` is an array of diagnostic records with required `hid_sequence`, `event_kind`, and the timestamp from its declared Android clock domain; it must never contain or masquerade as fixture `T0`. The schema accepts additional diagnostic fields only under a versioned `details` object so later Android instrumentation does not mutate the authoritative raw trial format.

The representative required layout remains:

```text
manifest.json
device.json
fixture.json
calibration.json
display.json
configuration.json
batches/001/trials.bin
batches/001/android-events.json
summary.json
```

`summary.json` is explicitly derived and may be regenerated. Raw `trials.bin` is never rewritten by validation.

- [ ] **Step 4: Verify schema and digest behavior**

Run: `cargo test -p slt-protocol`

Expected: PASS for valid package; FAIL tests cover missing required files, digest mismatch, size mismatch, unsupported major schema, engineering+qualified contradiction, and traversal paths.

- [ ] **Step 5: Commit**

```bash
git add protocol/schema protocol/session-v1.md protocol/rust/slt-protocol
git commit -m "feat: define v1 session package schema"
```

---

### Task 5: Generate canonical session fixtures for downstream development

**Files:**
- Modify: `tools/session-fixture/src/main.rs`
- Create: `protocol/vectors/sessions/qualified-minimal/`
- Create: `protocol/vectors/sessions/engineering-invalid-trials/`
- Create: `protocol/vectors/sessions/portrait-landscape-pair/`
- Modify: `protocol/rust/slt-protocol/tests/golden_vectors.rs`

**Interfaces:**
- Produces: deterministic session fixtures consumed by Android export tests and analyzer tests.
- Produces CLI: `session-fixture sessions <output-dir>`.

- [ ] **Step 1: Write fixture-validation tests**

```rust
#[test]
fn canonical_session_vectors_all_validate() {
    for path in canonical_session_paths() {
        validate_session_directory(path).unwrap();
    }
}
```

Add one intentional negative test where a copied trial file byte is modified and digest validation must fail.

- [ ] **Step 2: Verify failure before vectors exist**

Run: `cargo test -p slt-protocol --test golden_vectors sessions`

Expected: FAIL because fixtures are absent.

- [ ] **Step 3: Implement deterministic generation**

Generate:

1. `qualified-minimal`: three batches with valid trials and a qualified manifest.
2. `engineering-invalid-trials`: invalid trial flags, `mode=engineering`, `qualified=false`, recorded violations.
3. `portrait-landscape-pair/portrait` and `portrait-landscape-pair/landscape`: two matched sessions where center medians are unchanged but the signed spatial gradient rotates from vertical to horizontal. This becomes the canonical test proving that rotated scanout is not automatically a rotation penalty.

Use fixed UUIDs/timestamps/PRNG seeds in golden fixtures so output is byte-for-byte reproducible.

- [ ] **Step 4: Regenerate from scratch and compare**

Run:

```bash
cargo run -p session-fixture -- sessions protocol/vectors/sessions
cargo test --workspace --all-targets
```

Expected: PASS and no vector diff after a second generation.

- [ ] **Step 5: Commit**

```bash
git add tools/session-fixture protocol/vectors/sessions protocol/rust/slt-protocol/tests
git commit -m "test: add canonical latency session fixtures"
```

---

## Completion Gate

Before starting firmware, Android, or analyzer implementation:

```bash
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace --all-targets
```

All must pass on Linux, macOS, and Windows CI. The committed wire vectors, raw-trial vectors, schemas, and canonical sessions become compatibility inputs; later plans may extend v1 only compatibly or introduce a new major/minor contract explicitly.