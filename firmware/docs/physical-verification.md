# Issue #4 physical USB verification

Use this procedure only after separate authorization and release of the safety gates below. Record each run in a dated copy of the evidence template.

## Release conditions

Do not start while any applicable prerequisite is unreleased. The template initializes the Stage-B S2 current limit as `UNSET / PENDING REVIEW` and the other gates as `UNRELEASED`; confirm the current safety review before a run.

- Obtain separate owner authorization for each planned hardware action, including connecting, energizing, or flashing the fixture.
- Release the Stage-B S2 current-limit review and every other applicable Stage-B gate. Keep the release evidence with the run record.
- Use the reviewed, protected external 5 V supply and approved wiring. The VUSB/no-backfeed gate must be released before connecting power.
- Use the Teensy 4.1 device Micro-B port. Do not use its host port or treat USB bus power as the fixture supply.
- Identify the board, firmware image, Android host, and measurement equipment in a copy of [the evidence template](../evidence/physical-smoke.template.json).

If a prerequisite is missing or a result cannot be observed, stop and record `BLOCKED` or `NOT_RUN`. Do not infer a physical result from a build, unit test, descriptor parser, internal Full-Speed status, or Android mock.

## Procedure

1. Record the fixture's physical label, board revision, USB serial descriptor, firmware source revision, and firmware image SHA-256. Record the Android device model, Android version, API level, and each instrument's model, serial number, and calibration status. VID/PID alone does not establish which physical fixture produced an event.
2. With the released power arrangement, attach one fixture to the Android host. Record the current USB device path and establish a unique association between the physical board, USB device, and Android input device. If multiple matching devices are present or an event cannot be tied to the selected fixture, mark the input result ambiguous and stop using that event.
3. Capture the negotiated link speed from the Android host's USB kernel report at `/sys/bus/usb/devices/<bus-port>/speed` when the build exposes it. Record the path and raw value. The kernel ABI defines this value in Mbit/s. If it is unavailable, use a USB protocol analyzer on the Android-to-fixture link and retain its capture. Do not infer speed from descriptors, firmware state, VID/PID, or software tests. The required result is Full-Speed at 12 Mbit/s.
4. Capture the raw device and configuration descriptors from the Android host, preserve the bytes, and record a SHA-256 digest. Android's `UsbDeviceConnection.getRawDescriptors()` returns the raw USB descriptors. Verify that the device descriptor's EP0 `bMaxPacketSize0` is 64, then check the exact two-interface Full-Speed contract: interface 0 is the HID gamepad with interrupt IN endpoint `0x81`; interface 1 is vendor-specific with bulk OUT endpoint `0x02` and bulk IN endpoint `0x83`. All three endpoints have 64-byte maximum packets. Record any extra or missing interface or endpoint as a failure.
5. Leave HID interface 0 unclaimed by the engineering probe. Verify a physical gamepad button press and release through Android's OS input path. Record the input device ID and event source used for the check. Do not accept the event if identity is ambiguous or if more than one matching input device is present.
6. Claim vendor interface 1 only. Exchange the supported engineering version and `ENGINEERING_INFO` messages, record the raw request and response frames, and confirm the decoded sequence, state, validity, and completion tick form one consistent snapshot. The completion tick is diagnostic only; it does not qualify bus-accurate T0. Run a press pulse only when the pulse engine is idle, the session is valid, and Full-Speed has been internally verified. Preserve the pulse's measured sequence through release and record both transfer outcomes.
7. Exercise the approved timeout, fault, reset, detach, and re-enumeration checks. Confirm that a failed or invalidated press has no valid completion evidence and cannot produce a measured release. Confirm safe input-state recovery is unmeasured. Poll `ENGINEERING_INFO` while the pulse is queued, `BUSY`, completed, releasing, timed out, and recovering from a fault; record each response. Keep both engineering commands unavailable during autonomous acquisition and qualification.
8. Complete the separately approved USB/VUSB no-backfeed check using the reviewed method and instruments. Record instrument identity, calibration status, connection state, and raw readings. Do not choose or infer a current limit; the Stage-B S2 current-limit review must already be released.
9. Attach raw descriptors, speed evidence, Android input logs, vendor frames, reset/fault logs, and power-check records to the run. Set each result to `PASS`, `FAIL`, `BLOCKED`, or leave it `NOT_RUN` only when the attached evidence supports that status. Record deviations and stop on any unexpected electrical or USB behavior.

The Android USB API exposes raw descriptors, while negotiated speed is recorded separately from the host kernel report or a protocol-analyzer capture. The [Android API reference](https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection#getRawDescriptors()) documents `getRawDescriptors()`. The [Linux sysfs ABI](https://docs.kernel.org/admin-guide/abi-stable.html) defines the USB device `speed` attribute and its Mbit/s units.

## Evidence handling

Start from the JSON template and preserve it with the run's raw captures. The template initializes every physical check as `NOT_RUN` and every Stage-B prerequisite as `UNRELEASED`. Update those fields only in a dated run copy after authorization and direct observation. Keep the original raw files, their SHA-256 digests, and the completed record together.
