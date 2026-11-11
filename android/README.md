# Android USB probe

This build contains the reusable USB vendor-interface probe for the engineering fixture. It targets Android 11 and later. The probe keeps HID interface 0 available to Android and claims only vendor interface 1.

The app module uses AGP's built-in Kotlin support and does not apply `org.jetbrains.kotlin.android`. The independent `:probe` JVM module uses the pinned Kotlin/JVM plugin for protocol, descriptor, session, and identity rules. Its unit tests run without an Android device or emulator.

## Pinned toolchain

The wrapper uses Gradle 9.7.1 and verifies its binary distribution against the SHA-256 in Gradle's [release checksum list](https://gradle.org/release-checksums/). The app uses AGP 9.5.1, JDK 21, and Android SDK 36. The feasibility candidate pins these versions.

## Compatibility caveat

Kotlin's published compatibility table lists Kotlin Gradle Plugin 2.4.20–2.4.21 as fully supported through Gradle 9.7.0 and AGP 9.3.1. The `:probe` JVM module uses Kotlin/JVM 2.4.21 with Gradle 9.7.1, one patch above the documented Gradle maximum. The Android app uses AGP 9.5.1's built-in Kotlin support and does not apply that plugin. Gradle 9.7.1 meets AGP 9.5's documented minimum of 9.6.0.

A successful local build or unit-test run shows only that the pinned configuration worked on this machine; it does not extend or replace the published compatibility ranges. Keep the pinned versions until the owner changes them.

Sources: [Kotlin Gradle project configuration](https://kotlinlang.org/docs/gradle-configure-project.html), [Android Gradle plugin compatibility](https://developer.android.com/build/releases/about-agp), and [AGP built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin).

## Manual smoke screen

Launch Screen Latency USB Probe from the Android app list. Discover proceeds only when one matching USB device is present. After Android grants permission, the probe validates descriptors and protected strings, then claims vendor interface 1 while Android keeps HID interface 0.

Before sending `ENGINEERING_PRESS_PULSE`, the app requests `ENGINEERING_INFO` and compares its fixture serial with the protected USB serial. The pulse request carries an eight-byte tag/duration/reserved payload, and its 12-byte acknowledgement echoes the tag and returns the assigned sequence and accepted state. An identical retry after a lost acknowledgement replays the cached response without issuing another pulse. During the operation, the screen displays progress and 32-byte `ENGINEERING_INFO` snapshots with protocol and firmware versions, USB speed, fixture serial, sequence and pulse state, USB-session and transfer generations, validity flags, duration, and the DWT cycle tick. The snapshot reports a generic fault state without a detailed fault code. The USB session closes when the operation ends; discover and reconnect before another pulse.

Gamepad input appears as diagnostic status. The Activity consumes gamepad events so controller buttons cannot activate the screen's buttons. Non-key `MotionEvent`s from gamepad or joystick sources are ignored by the button press/release matcher; source membership checks accept composite Android source flags. Ambiguous devices and invalid key order remain rejected. These events and firmware ticks do not set T0.

Before connecting physical hardware, the owner must release the current-limit, protected-supply, wiring, and VUSB isolation gates documented in [REF-WIRE-001](../docs/reference/REF-WIRE-001-usb-and-control-contract.md). The screen cannot determine whether those gates have been released. This software run did not include physical testing.

## Request-ID persistence

`UsbProbeCoordinator` requires a request-ID provider; it has no in-memory default. The Android adapter supplies a provider backed by app-private preferences keyed by the validated fixture serial. It commits the next ID before sending a command. The in-memory provider lives in probe test sources only. Valid counters continue across attach and detach notifications, coordinator and process recreation, USB resets, endpoint reconfiguration, and physical reconnection. Firmware clears its replay cache when `PORTSC1.CCS` reports zero. Android's detach event and an absent device-list entry only invalidate the logical session; neither initializes nor resets request-ID state.

If a fixture's stored counter is missing or corrupt, the probe sends no vendor command. The app cannot infer from USB removal and re-enumeration whether firmware cleared its replay cache. After connecting to the validated fixture, the engineering screen offers **Initialize IDs after full firmware restart**. Use this action only after separate owner authorization, Stage-B electrical safety release, and a complete firmware restart that clears the fixture's replay cache or a full power cycle. A USB reset, detach, or re-enumeration alone is insufficient. The confirmation preserves any valid stored counter and sends no USB command. It records the owner's attestation; the app cannot verify the restart.

An owner-confirmed restart initializes only missing or corrupt state to ID 1 for the currently connected fixture. A valid counter is never reset, and each replacement fixture has separate state keyed by its validated serial. If the owner cannot confirm a complete firmware restart or power cycle, the app stays fail-closed. The current 32-byte `ENGINEERING_INFO` response does not expose the firmware request-ID epoch or high-water mark, so automatic recovery after host-state loss would require a separately approved, versioned read-only handshake. No protocol command or packet field was added for this candidate.

Normal process restart preserves the counter while app data remains available. Lost acknowledgements within an active exchange retry the exact ID and request bytes. If the process stops after sending a command, the next process uses the already advanced ID, so an earlier cached acknowledgement cannot count as a new operation; the earlier operation's outcome may remain unknown. Clearing app data or reinstalling removes the host-side counter. Do not initialize it from a detach event.

## Gamepad event provenance

`associateGamepadEvent` inspects events from the host Activity's normal dispatch. A match requires a system `KeyEvent` with a positive device ID, then one current external, non-virtual gamepad with the expected source, VID/PID, and product name. Source membership checks allow composite Android source flags. Multiple matching devices, unknown IDs, key events that cannot be tied to the selected device, and events without system provenance are ambiguous. Non-key `MotionEvent`s from gamepad or joystick sources are ignored by the press/release matcher so motion cannot poison a button sequence.

Call `observeGamepadEvent` for each gamepad event while the pulse runs. Events received after the pulse is armed are buffered until `onSequenceAccepted` provides the firmware sequence, because the button-down event can arrive before the USB acknowledgement. Events created before capture was armed are ignored. Firmware Button usage `0x09:0x0001` maps to Android `KEYCODE_BUTTON_A` (96); the matcher accepts one Button A down followed by one up from the same uniquely associated device. It rejects ambiguous identities, repeated keys, other buttons, duplicates, missing events, out-of-order events, and stale events. Actual OS input reception and key mapping still require physical qualification. These results are diagnostic and do not set T0.

`Matched` means a unique logical input-device association. It does not prove that the event came from the selected USB device: Android's public input-device metadata does not expose the USB serial or bus path, and `isExternal()` also includes Bluetooth and other peripheral buses. Unit tests cannot qualify physical input. Physical evidence still requires observing an actual button press from the selected fixture without injected or simulated events, after the hardware safety gates are released.

The event checks use Android's [`InputEvent.getDeviceId`](https://developer.android.com/reference/android/view/InputEvent#getDeviceId()), [`InputDevice.isExternal`](https://developer.android.com/reference/android/view/InputDevice#isExternal()), [`InputDevice.isVirtual`](https://developer.android.com/reference/android/view/InputDevice#isVirtual()), and [`KeyEvent.FLAG_FROM_SYSTEM`](https://developer.android.com/reference/android/view/KeyEvent#FLAG_FROM_SYSTEM). A system-origin flag and a matching VID/PID are diagnostic signals, not proof of the USB fixture's physical identity.

## Scope

The probe implements USB discovery, permission, identity validation, engineering commands, and gamepad event association. It does not provide the Issue #6 renderer or qualification UI. Host checks do not qualify USB timing, physical hardware, or measurement accuracy.
