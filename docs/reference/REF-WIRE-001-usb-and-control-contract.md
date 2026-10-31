# REF-WIRE-001 — USB Transport and Control Contract

**Document ID:** REF-WIRE-001  
**Status:** Draft
**Readiness note:** Provisional v1 binary layout until physical timing feasibility gate  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
**Depends On:** ARCH-001, REF-MET-001  
**Supersedes:** §3–5 of August 31 implementation closure amendment and wire layout in legacy contracts plan  
**Superseded By:** None

## Scope and freeze condition

These are the implementation **candidate** byte layouts, copied from the approved closure design. They become frozen v1 only after proof of Full-Speed composite USB enumeration, measured HID completion correlated with real bus timing, five common-timebase hardware captures, and the one-sensor Android optical integration. Revisions before freeze must be reflected in all golden vectors; revisions after freeze require explicit compatible versioning or major version change.

## USB device identity and endpoints

Teensy 4.1 i.MX RT1062 forces USB 2.0 **Full Speed, 12 Mb/s** before initialization and verifies negotiated speed. Unexpected High Speed is a hardware/configuration qualification fault. Device descriptor: USB 2.0, EP0 MPS 64, class/subclass/protocol 0/0/0, one configuration, exactly two interfaces, `bmAttributes=0xC0` self-powered and `bMaxPower=1` (2 mA declared). External 5V powers the electronics; use VBUS only for attach/PHY/sense and prevent backfeed.

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
| 5 | u8 | minor=0 |
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
