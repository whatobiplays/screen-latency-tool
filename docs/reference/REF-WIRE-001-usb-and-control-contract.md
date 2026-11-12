# REF-WIRE-001 — USB Transport and Control Contract

**Document ID:** REF-WIRE-001  
**Status:** Draft
**Readiness note:** Provisional v1 binary layout until physical timing feasibility gate  
**Owner:** Repository maintainers\
**Last Updated:** 2026-11-09
**Depends On:** ARCH-001, REF-MET-001  
**Supersedes:** §3–5 of August 31 implementation closure amendment and wire layout in legacy contracts plan  
**Superseded By:** None

## Scope and freeze condition

These are the implementation **candidate** byte layouts, copied from the approved closure design. They become frozen v1 only after proof of Full-Speed composite USB enumeration, measured HID completion correlated with real bus timing, five common-timebase hardware captures, and the one-sensor Android optical integration. Revisions before freeze must be reflected in all golden vectors; revisions after freeze require explicit compatible versioning or major version change.

## USB device identity and endpoints

Teensy 4.1 i.MX RT1062 sets `PORTSC1.PFSC` before starting the USB controller and preserves or reapplies it after applicable controller resets. Firmware considers speed internally verified only after the device is configured, the session is attached, PFSC remains asserted, and `PORTSC1.PSPD[27:26]` reads `00` (Full Speed). High Speed, Low Speed, undefined PSPD, or disconnected/unconfigured state fails closed. `HSP==0` alone is not speed evidence. These register fields are defined in the [i.MX RT1060 Processor Reference Manual](https://www.pjrc.com/teensy/IMXRT1060RM_rev3.pdf); NXP's [USB speed-forcing guidance](https://community.nxp.com/t5/i-MX-Processors/IMXRT-USB-full-speed-example/m-p/872133/highlight/true) describes setting PFSC before controller start and after reset. A physical Full-Speed pass still requires independent host or analyzer evidence at 12 Mb/s. Device descriptor: USB 2.0, EP0 MPS 64, class/subclass/protocol 0/0/0, one configuration, exactly two interfaces, `bmAttributes=0xC0` self-powered and `bMaxPower=1` (2 mA declared). External 5V powers the electronics; use VBUS only for attach/PHY/sense and prevent backfeed.

Internal test identity: VID `0x1209`, PID `0x0001`, manufacturer `Screen Latency Tool`, product `Screen Latency Fixture v1`, serial uppercase 16 hex characters. The test PID is not unique; verify product string, serial policy and interface signature. Obtain an appropriate unique production PID before external distribution.

| Interface | Class | Endpoint | Max packet | Purpose |
| --- | --- | --- | --- | --- |
| 0 | HID Game Pad 0x03/0x00/0x00 | interrupt IN 0x81 | 64 B, poll 1 ms | Normal Android gamepad input, never claimed by test app |
| 1 | Vendor 0xFF/0x00/0x00 | bulk OUT 0x02 | 64 B | Configure/status |
| 1 | Vendor 0xFF/0x00/0x00 | bulk IN 0x83 | 64 B | Responses/results |

No CDC, MIDI, RawHID, MTP or debug USB interface in qualification firmware.

HID input is six bytes: `u16 buttons` (little-endian bits 1–16), followed by four signed 8-bit neutral axes X/Y/Rx/Ry. Measured button-1 press bytes `01 00 00 00 00 00`; unmeasured reset/release `00 00 00 00 00 00`. Firmware binds `hid_sequence` once per measured press to the exact report transfer descriptor. Release, unrelated endpoint completions, or queued-but-not-completed transfers never establish `T0`. Android preserves ordered diagnostic matching and fails on unexpected/missing duplicate press events; Android event clocks are never substituted for the fixture timestamp.

## Request framing

All 20-byte headers are little-endian and explicitly byte-serialized (no native struct padding):

| Offset | Type | Field |
| --- | --- | --- |
| 0 | byte[4] | ASCII SLT1 |
| 4 | u8 | protocol major=1 |
| 5 | u8 | protocol minor: 0 for legacy engineering commands; 1 for capture commands |
| 6 | u8 | request=1, response=2 |
| 7 | u8 | flags (reserved=0 v1) |
| 8 | u32 | request_id |
| 12 | u16 | command |
| 14 | u16 | status (0 for request) |
| 16 | u32 | payload_len |

Reject wrong magic, unsupported major, nonzero reserved, invalid lengths, trailing bytes, unexpected statuses and unknown required commands. CLI tooling limits total payload to ≤16 MiB; device transport uses smaller bounded chunks. Status IDs: 0 OK; 1 UNSUPPORTED_VERSION; 2 BAD_REQUEST; 3 INVALID_STATE; 4 NOT_READY; 5 HARDWARE_FAULT; 6 INTEGRITY_FAULT; 7 BUSY; 8 RANGE_ERROR.

## Commands (payload types and exact order)

| ID | Command | Request | Response |
| --- | --- | --- | --- |
| 0x0001 | GET_FIXTURE_INFO | empty | 40 B info |
| 0x0002 | RUN_ELECTRICAL_SELF_TEST | u32 samples | 60 B self-test |
| 0x0003 | RUN_OPTICAL_SELF_TEST | u32 samples | 60 B self-test |
| 0x0004 | CALIBRATE_SENSORS | stage:u8, reserved:u8, samples:u16 | 38 B ADC summary |
| 0x0005 | SET_THRESHOLDS | 16 B threshold config | 16 B actual configuration |
| 0x0006 | CONFIGURE_BATCH | 40 B batch config | empty |
| 0x0007 | ARM_BATCH | batch_id:u32 | empty |
| 0x0008 | GET_BATCH_STATUS | batch_id:u32 | 24 B status |
| 0x0009 | DOWNLOAD_RESULTS | offset:u32,length:u32 | result bytes or RANGE_ERROR |
| 0x000A | ABORT_BATCH | batch_id:u32 | empty |
| 0xE001 | ENGINEERING_INFO | empty | 32 B engineering snapshot |
| 0xE002 | ENGINEERING_PRESS_PULSE | tag:u32, duration_ms:u16, reserved:u16 | 12 B accepted-pulse acknowledgement |

## Engineering smoke commands (provisional)

The `0xE0xx` commands are engineering-only. They do not advertise production readiness. `E001 ENGINEERING_INFO` and `E002 ENGINEERING_PRESS_PULSE` retain their existing payloads; Issue #5 adds capture through `E003 BEGIN_CAPTURE` and `E004 CAPTURE_ARCHIVE`. These engineering commands do not replace `GET_FIXTURE_INFO` or the real five-sensor fixture.

### Engineering capture extension, protocol 1.1 candidate

`E003 BEGIN_CAPTURE` and `E004 CAPTURE_ARCHIVE` are engineering-only additions. Production command IDs and the payloads of `E001 ENGINEERING_INFO` and `E002 ENGINEERING_PRESS_PULSE` stay unchanged. Firmware accepts E001 and E002 at protocol minor 0 or 1. Capture commands require minor 1. A legacy host continues using minor 0. A capture-capable host first sends E001 at minor 1. If a legacy fixture replies `UNSUPPORTED_VERSION` at minor 0, the host spends a new, higher request ID on E001 at minor 0 and does not send E003 or E004.

| Host / fixture | E001 and E002 | E003 and E004 |
| --- | --- | --- |
| 1.0 host / 1.0 fixture | Existing minor-0 frames and responses | Host sends no capture command |
| 1.1 host / 1.0 fixture | Minor-1 probe receives `UNSUPPORTED_VERSION`, then minor-0 fallback preserves normal behavior | Unsupported; host sends no capture command |
| 1.0 host / 1.1 fixture | Existing minor-0 frames and responses | Not used |
| 1.1 host / 1.1 fixture | Minor 0 or 1 is accepted for E001/E002 | Minor 1 only |

The 20-byte frame header leaves 44 payload bytes under the 64-byte engineering frame limit. All values below use little-endian byte order. Firmware and host code reject wrong payload sizes, nonzero reserved fields, out-of-range values, unaligned offsets, and frames larger than 64 bytes.

`E003 BEGIN_CAPTURE` request payload, 16 bytes (36-byte total frame):

| Payload offset | Type | Field |
| --- | --- | --- |
| 0 | u64 | nonce; must equal the nonzero request ID |
| 8 | u16 | samples per channel; exactly 10,000 |
| 10 | u8 | selected optical edge: 1 ascending / rising, 2 descending / falling |
| 11 | u8 | rate stage: 1 qualified `R_sensor_max`, 2 `2 × R_sensor_max` |
| 12 | u32 | capture timeout in milliseconds; 1,000 through 86,400,000 |

The successful `E003` response payload is 20 bytes (40-byte total frame): nonce:u64 at 0, archive generation:u32 at 8, USB session generation:u32 at 12, state:u8 at 16, selected polarity:u8 at 17, and samples-per-channel:u16 at 18. State MUST equal `1` (`AcknowledgementPending`); other values are invalid. This state means firmware accepted the request and is waiting for the successful vendor IN completion before arming capture. Firmware arms capture only after that transfer completes successfully and its transfer, session, and USB lifecycle generations still match. Firmware validates Full-Speed at that boundary, uses the five paths synchronized during initialization, and arms the selected edge. A failed or unobserved acknowledgement, reset, detach, speed loss, timeout, or accepted unexpected vendor OUT completion retains an incomplete archive when the device remains powered. The host does not retry E003 after an empty IN response. It persists the nonce before sending E003, stays silent during acquisition, and can discover the retained generation after reconnect if the acknowledgement was lost. Firmware keeps one vendor OUT receive armed as a guard; it sends no vendor response and accepts no normal command during acquisition. Android announces stimulus readiness after a 500 ms quiet interval from the successful acknowledgement. The callback gates host stimulus. Physical testing must compare stimulus start against the captured arm tick and verify that independent edge counts match all five archive counts.

`E004 CAPTURE_ARCHIVE` uses the same 12-byte request payload for every operation:

| Payload offset | Type | Field |
| --- | --- | --- |
| 0 | u8 | operation: 1 status, 2 read, 3 release |
| 1 | u8 | reserved; zero |
| 2 | u32 | archive generation; zero only for status discovery |
| 6 | u32 | archive byte offset; status and release require zero |
| 10 | u8 | read length; status and release require zero |
| 11 | u8 | reserved; zero |

Status returns 24 payload bytes (44-byte frame): generation:u32 at 0, state:u8 at 4 (`4` complete, `5` incomplete), polarity:u8 at 5, fault flags:u16 at 6, five channel counts:u16 at 8–17, archive length:u32 at 18, and reserved:u16 at 22. Status discovery accepts generation zero. A read request requires the known nonzero generation, a 32-byte-aligned offset, and length 32 while offset is below the archive end. The only zero-length read is EOF at offset 400,128. A full read response has 44 payload bytes (64-byte frame): generation:u32, offset:u32, CRC-32:u32, and 32 data bytes. EOF returns the 12-byte metadata only (32-byte frame). CRC-32/ISO-HDLC is calculated over `generation:u32 || offset:u32 || length:u8 || data`. Release requires the exact current generation, offset zero, and length zero; its successful payload is generation:u32, state:u8 zero, and three reserved zero bytes (28-byte frame).

The raw archive is a fixed 400,128-byte object: a 128-byte SLA1 header followed by 50,000 little-endian u64 timestamps in channel-major order. The header includes archive and session generations, nonce, firmware version, the first eight source-commit bytes, selected polarity and rate stage, capture clock frequency, timeout, arm and end ticks, per-channel counts, service diagnostics, SHA-256 of the 400,000 sample bytes, total length, and a CRC-32 over the first 124 header bytes. The firmware writes the source-commit prefix only for a clean build; dirty builds use zeroes. A complete archive has exactly 10,000 samples on every channel. The header records complete/incomplete state and per-channel counts. `E004 STATUS` carries the fault flags and counts; the Android result object retains this verified status beside the raw bytes. A retrieval tool that saves the raw bytes must save the status metadata with them. The Android retrieval path verifies every chunk CRC, the exact zero-length EOF, header CRC, sample SHA-256, generations, nonce, polarity, rate stage, timeout, status fault flags, and status/header agreement before reporting the archive as complete or incomplete.

One archive owns the fixed RAM2 buffer for the current boot. It remains available after completion, timeout, USB reset, or detach until the host releases the matching generation or power is lost. Releasing clears the archive but keeps the consumed request-ID high-water value and capture nonce in firmware for the remainder of that boot, so a request retry cannot execute the same capture again. Android uses the same persisted, per-fixture request-ID sequence for E001–E004 and E003. The pending reference is synchronously stored before E003 and updated with the accepted generations when that acknowledgement arrives. The reference remains until the caller has retained the verified archive and status metadata and E004 release succeeds. If retrieval stops, resume with that reference and newly allocated increasing request IDs; interrupted chunk retrieval restarts from offset zero and validates the complete archive again. Do not reset a valid counter to recover synchronization. Only one engineering archive may be retained; production batch results remain separate and are never selected or rewritten by E004.

The archive is volatile and independent of optional PSRAM. Firmware rejects capture when the qualified service profile is unavailable or RAM checks fail; it never shortens the sample count. The current build keeps pin routing disabled pending board identity, fitted-memory inspection, and owner acceptance. Missing `R_sensor_max` remains a physical qualification hold. The simulated 1.905 MHz analog bandwidth is not a qualified comparator-edge rate.

`ENGINEERING_INFO` remains available throughout an engineering pulse, including queued, completed, release, busy, timeout, recovery, and fault states. `ENGINEERING_PRESS_PULSE` is accepted only while the pulse engine is idle, the USB session is valid, and firmware has internally verified Full-Speed. A pulse request received while the engine is not idle returns `BUSY` and does not change the current sequence. During Issue #5 capture, the host sends no vendor traffic. After the matching successful BEGIN_CAPTURE IN completion, firmware keeps one vendor OUT receive armed as a guard; it does not send vendor responses or accept normal commands during acquisition. Any completed unexpected OUT transfer aborts capture, retains an incomplete archive, and receives no response. The protocol-level guard returns `INVALID_STATE` without serializing a response if a caller bypasses the endpoint owner. An exact duplicate request is not replayed during capture; after engineering mode resumes, the four-entry cache retains its existing exact-request replay behavior.

After a pulse completes or recovery succeeds, `complete` or a recovered terminal `fault` remains visible until an `ENGINEERING_INFO` response serializes that state. The firmware then sets the engine to `idle` while retaining the sequence, transfer generations, and any valid completion tick. A fault with recovery still required remains faulted and blocks new pulses. The 32-byte information payload carries only the fault state, not a detailed fault code; Android reports `UNSPECIFIED` for that state. The response header carries the status of the current command.

The 8-byte press request is `u32 tag; u16 duration_ms; u16 reserved`, with the reserved field zero and duration in the inclusive range 10–1000 ms. Its successful 12-byte response is `u32 tag; u32 sequence; u8 accepted_state; u8 reserved[3]`; the tag is echoed, the accepted state is `press_queued`, and all reserved bytes are zero. The response confirms acceptance only; it does not mean the HID press completed. Android polls `ENGINEERING_INFO` for transfer state. Firmware allocates the sequence once when it accepts the pulse and preserves it through the measured press, the corresponding release, and recovery. The duration starts only after successful completion of the exact six-byte measured press transfer. Firmware must not queue that release before the matching press-completion callback reports success.

Within a firmware request-ID epoch, IDs are nonzero and strictly increasing. Firmware retains the latest four exact request/response exchanges. Repeating an ID with identical request bytes replays the cached response without executing the command again. This lets Android retry the exact pulse request after a lost acknowledgement without starting a second pulse. Reusing an ID with different bytes, using an ID older than the replay window, or wrapping an ID is rejected. Firmware clears the request-ID sequence and replay cache only after USB port status reports a disconnect (`CCS=0`). A bus reset, endpoint reconfiguration, or `PE=0` while `CCS=1` preserves both.

Android persists the next request ID in app-private state keyed by the fixture's protected serial before sending a command. Logical session invalidation, USB detach and re-enumeration, coordinator recreation, process restart, USB reset, and physical reconnection do not reset a valid high-water mark. Firmware clears its replay cache only when `PORTSC1.CCS` reports zero. Android's detach notification and device-list absence only invalidate the logical session; they do not establish that firmware reset its cache.

If per-fixture state is absent or corrupt, Android sends no vendor command. The 32-byte `ENGINEERING_INFO` response does not expose the firmware request-ID epoch or high-water mark, so the host cannot verify a reset automatically. After the owner completes a full firmware restart that clears the fixture's replay cache or a full power cycle, the engineering screen permits manual initialization of only missing or corrupt state to ID 1. A USB reset, detach, or re-enumeration does not establish this boundary. The owner must have separate authorization and Stage-B electrical safety release before performing the procedure. The confirmation preserves valid stored counters and sends no USB command. This is an owner attestation; the app cannot verify the restart. Replacement fixtures use separate serial-keyed state. A busy pulse request consumes its request ID but does not alter the active pulse.

If a measured press fails, times out, or is canceled by reset or disconnect, its completion tick becomes invalid, no measured release is sent, and a late callback cannot complete the pulse. Firmware may send a zero-button report only as explicitly unmeasured input-state recovery when the current USB session is still configured. Recovery has no sequence and cannot create completion evidence. A stale callback is ignored unless both its USB-session generation and transfer generation match the active transfer.

The pulse engine is idle only when it has no press, hold, release, recovery transfer, or pending action. A `complete` state and a recovered terminal `fault` stay non-idle until `ENGINEERING_INFO` reports them. A release failure records a fault and preserves a valid completion tick from the successful press. Android can poll the information command while a pulse request is busy, a transfer times out, or recovery is in progress.

`ENGINEERING_INFO` returns exactly 32 payload bytes. All integers are little-endian; the payload is serialized byte by byte without native structure padding. Protocol version is 1.0 and this firmware candidate reports firmware version 0.1.

| Offset | Type | Field |
| --- | --- | --- |
| 0 | u8 | protocol major, currently 1 |
| 1 | u8 | protocol minor, currently 0 |
| 2 | u8 | firmware major, currently 0 |
| 3 | u8 | firmware minor, currently 1 |
| 4 | u8 | USB speed: 0 unknown, 1 Low Speed, 2 Full Speed, 3 High Speed |
| 5 | u8 | state in bits 0–2: 0 idle, 1 press queued, 2 holding, 3 release queued, 4 complete, 5 recovery, 6 fault; bit 3 completion tick valid; bit 4 Full-Speed internally verified; bit 5 USB session valid; bit 6 unmeasured recovery required; bit 7 reserved zero |
| 6 | u64 | fixture serial number, little-endian; same 64-bit value represented by the 16-character uppercase USB serial string |
| 14 | u32 | last accepted measured sequence; zero before the first pulse |
| 18 | u32 | USB-session generation |
| 22 | u32 | current or last transfer generation |
| 26 | u32 | diagnostic completion tick; zero when invalid, though a valid tick may numerically wrap to zero |
| 30 | u16 | accepted pulse duration in milliseconds; zero before the first accepted pulse |

The completion tick is read from the Teensy 4.1 Cortex-M7 `DWT_CYCCNT` cycle counter after the USB core reports successful completion of the exact six-byte measured press transfer. The [Armv7-M Architecture Reference Manual](https://www.pjrc.com/teensy/DDI0403Ee_arm_v7m_ref_manual.pdf) defines it as a 32-bit counter that increments each processor clock cycle and wraps to zero. This firmware candidate runs at a nominal 600 MHz core clock: each tick is one core cycle (about 1.667 ns), and the counter wraps every 7.158278827 seconds. The frequency is fixed by the firmware build and is not included in the 32-byte payload. The counter belongs to the CPU core-clock domain; firmware must not change that clock while a pulse is active.

The validity bit becomes true only after a successful six-byte press completion whose USB-session and transfer generations still match. It remains true through the matching release, completion, and release-recovery lifecycle. Firmware clears it before accepting a new pulse and invalidates it after failed or timed-out press transfer, USB reset, detach, re-enumeration, or firmware restart. When invalid, the tick field is zero. The validity bit describes a successful press transfer. A later release-transfer failure or timeout is reported by the pulse state and recovery flag; it does not erase the valid press-completion tick. The hardware counter itself continues through USB reset, detach, and re-enumeration while the CPU remains running, but prior pulse evidence is invalidated. Firmware restart resets the counter; neither the counter nor its value is retained across restart.

USB callbacks copy completion facts into a bounded event queue; they do not mutate pulse state. The main loop applies queued events and processes vendor requests in order, then copies one `ENGINEERING_INFO` snapshot for serialization. The response therefore reports sequence, state, generations, validity flags, and completion tick from one consistent snapshot while callbacks are active. Queue overflow invalidates the session and measured completion evidence. This callback-time tick is diagnostic only; it cannot establish bus-accurate `T0` or qualify timing.

Android discovery first screens the public device model for the provisional VID/PID and expected two-interface shape. It then requests USB permission, validates available raw descriptors and protected manufacturer, product, and serial strings, and cross-checks the public interface and endpoint model. Raw descriptor data is variable length; parsers must honor each descriptor's declared length and reject truncation or contradictory fields. Android claims only vendor interface 1 with `claimInterface(interface, false)`; the operating system retains HID interface 0. Detach, cancellation, close, or re-enumeration releases the vendor claim and connection and invalidates cached `InputDevice` identity. A replacement device needs fresh permission and full identity validation. For button diagnostics, the gamepad guard accepts only a system-flagged `KeyEvent` with a positive device ID that maps to one unique, current, external, non-virtual input device with matching source, VID/PID, and product name. It checks source membership so composite Android source flags remain valid. Duplicate matching devices or a key event that cannot be associated with one device are ambiguous. Joystick-axis `MotionEvent`s are ignored by the button matcher and do not alter its press/release order. This is a logical association only: Android's public input-device metadata does not provide the USB serial or bus path, and `isExternal()` includes Bluetooth and other peripheral buses. A matched event is not proof of physical identity with the selected USB fixture; physical OS-input evidence requires a real bench observation. VID/PID alone is not fixture identity. The probe does not rely on a public Android API for negotiated USB speed.

GET_FIXTURE_INFO: `u16 hardware_major,hardware_minor,firmware_major,firmware_minor,firmware_patch; u8 protocol_major,protocol_minor,usb_speed,sensor_count; u64 timebase_hz; u16 max_trials,trial_record_size; u32 capabilities; u64 fixture_serial; u16 reserved`. Capabilities bits 0–5: FIVE_CAPTURE_CHANNELS, ELECTRICAL_SELF_TEST, OPTICAL_SELF_TEST, PROGRAMMABLE_THRESHOLDS, POWER_SUPERVISION, FULL_SPEED_FORCED. Sensor count=5 and speed=Full Speed mandatory.

Self-test: `u8 kind,passed,channel_count,reserved; u32 samples; u64 max_skew_ticks; s32 channel_offset_ticks[5]; u32 missed_edges[5]; u32 fault_flags`. Kind 1 electrical / 2 optical, five channels. Raw external evidence is stored outside this summary.

CALIBRATE_SENSORS: stages 1 BLACK_STORE, 2 WHITE_STORE, 3 SAMPLE_ONLY (no calibration mutation). Response: `u8 stage,sensor_count; u16 samples; u16 mean_adc[5],min_adc[5],max_adc[5]; u32 fault_flags`. BLACK_STORE and WHITE_STORE update only in-RAM calibration state.

SET_THRESHOLDS: `u8 threshold_percent (10,50,90), transition (1 rising,2 falling), mode (1 derived,2 explicit_engineering), reserved; u16 dac_code[5]; u16 reserved2`. Derived request codes zero. Response echoes actually programmed codes. Explicit DAC mode cannot qualify. Descending `T10` is 90% above black; descending `T90` is 10%.

CONFIGURE_BATCH: `u32 batch_id; u16 trial_count,min_delay_ms,max_delay_ms; u8 transition,threshold_percent,hid_button,sensor_mask; u16 capture_timeout_ms,baseline_timeout_ms; u32 refresh_millihz; u64 prng_seed; u32 flags; u8 reserved[6]`. Authoritative: count 1,000; delays 200–500 ms; threshold 50; button 1; mask 0x1F; capture/baseline timeouts 250 ms each; flags zero. Record these values, not implicit defaults. No arm without valid power, calibration, threshold, routing, speed or buffer capacity.

GET_BATCH_STATUS: `u32 batch_id; u8 state; u8 reserved[3]; u32 trials_completed,result_bytes,integrity_flags; u16 supply_mv,reserved2`. States 0 idle,1 configured,2 armed,3 acquiring,4 complete,5 failed. Integrity bits 0–6: timebase discontinuity, undervoltage, calibration invalidated, duplicate HID, overflow, internal state error, aborted; unknown bits reserved. Android-owned faults are recorded separately and combined at analysis.

DOWNLOAD_RESULTS: maximum request 4,096 bytes; return exactly requested length unless less remains, then smaller final chunk. Never rewrite records on abort/download.

## No control traffic during acquisition

Android configures and arms once. During autonomous acquisition, **no vendor-interface traffic may be sent**. After observing the final expected measured HID release through the OS, wait at least `capture_timeout_ms + baseline_timeout_ms + 100 ms`, then query completion. If final sequence is missing/duplicate, wait until the deterministic upper bound based on count, maximum inter-trial delay and both timeouts; query only afterward, preserve integrity failure and never call that batch qualified. A more robust passive completion signal may be considered before the v1 freeze if supported by validated hardware; it must not inject vendor traffic during acquisition.

## Physical feasibility

External oscilloscope correlation must identify successful HID completion relative to the adjacent firmware timestamp/marker. Pure software callback timing without characterized worst-case uncertainty is unacceptable. If Full-Speed forcing or `T0` verification fails, stop publication qualification and evaluate the documented borrow/rent USB analyzer or external capture logic fallback. Do not weaken timing precision to meet budget.

### Issue #4 physical smoke procedure and evidence

This procedure is a future owner-run checklist, not authorization to connect or power the fixture. Do not begin until the owner has separately authorized physical work and all Stage-B current-limit, protected-supply, VUSB/no-backfeed, and wiring gates are released. In particular, Stage-B S2's initial current limit must be assigned, reviewed, and released; `UNSET` or `PENDING REVIEW` means stop.

After those gates are released, use the Teensy 4.1 device Micro-B port and a known-good USB data cable; do not use its independent host port. Record fixture and firmware identity, host/Android version, cable and adapter path, USB analyzer or host speed evidence, raw device/configuration descriptors, and the source of every observation. Confirm Full-Speed at 12 Mbps from independent host or analyzer evidence. A register value, firmware log, Android device list, or successful build alone is not a physical speed result.

With the fixture safely powered, confirm that Android exposes one gamepad input device and that button 1 produces the expected system-originated press and release from the selected USB fixture. The software event association is diagnostic and cannot prove that mapping by itself. Confirm the vendor probe requests permission, claims interface 1 without forcing kernel-driver detachment, exchanges the engineering information request, runs one bounded pulse, and releases its claim on detach and cancellation. Record ambiguous or unmatched input events as failures; do not infer fixture identity from VID/PID alone. Exercise reset, disconnect, re-enumeration, timeout, and recovery paths without treating software ticks as bus-accurate `T0`.

Record separate pass/fail/not-run results for descriptor identity, independent Full-Speed evidence, OS gamepad input, vendor exchange, disconnect/recovery, and VUSB/no-backfeed checks. Attach raw host logs and analyzer evidence with timestamps and hashes. When those observations have not been collected under the released safety gates, classify physical evidence as `NOT_RUN` and physical acceptance as blocked.
