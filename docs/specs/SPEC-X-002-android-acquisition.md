# SPEC-X-002 — Android Acquisition and Display Stimulus

**Document ID:** SPEC-X-002  
**Status:** Ready for Implementation
**Readiness note:** Implementation must respect provisional transport/session interfaces until contract freeze  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
**Depends On:** ARCH-001, REF-MET-001, REF-WIRE-001, REF-DATA-001  
**Supersedes:** Android requirements in the legacy design and acquisition checkbox plan  
**Superseded By:** None

## Scope and platform

Internal sideloaded Android engineering bench app, package `io.screenlatency.tool`, minimum Android 11/API 30, pinned Android/Gradle/Kotlin/NDK/GameActivity toolchain at implementation time. An ergonomic public app, Play Store deployment and broad OEM permission compatibility work are not v1 requirements.

Kotlin owns permission and vendor USB interface selection/claim, fixture connection, display/orientation/brightness controls, qualification workflow, battery/thermal state, ADB evidence attachment, session export and UI. C++ owns shared stimulus state, native input, Vulkan and OpenGL ES renderers, continuous and event-driven loops, and Android-clock diagnostic event hooks. UI must not perturb the timed native presentation path.

## Input and rendering

The standard Android OS receives button-1 from USB HID gamepad interface 0; the app claims only vendor interface 1. Reject ambiguous product/descriptor identities even if VID/PID match. Do not claim HID or inject software input to substitute for the physical benchmark.

Support native-input/Vulkan (authoritative), native/GLES, framework KeyEvent/Vulkan, framework/GLES (diagnostic); same stimulus state machine for every path. Only measured keydown changes to target black/white; unmeasured release resets to baseline without registering an additional measured input. Correlate diagnostic Android events by ordered HID sequence, not by pretending Android and Teensy clocks are synchronized. Missing, duplicated or unexpected sequences fail integrity.

Authoritative renderer: fullscreen SDR RGB black ↔ white, low-latency VSync-compatible Vulkan FIFO presentation where supported, smallest practical queue depth and image count, **continuous** game-style frame loop. Input modifies shared state; the normal loop samples it once at a documented point. Never special-case input into immediate authoritative render. Record actual present mode, image count, selected mode, swapchain config and available timestamps. Android/GLES diagnostics should match logical stimulus, document EGL swap interval/config and actual behavior. Event-triggered render, MAILBOX (where available), deliberate queue depth, alternate swap intervals, isolated five patches and frame-budget synthetic workloads 25/50/75/90% are engineering-only.

## Display and geometry evidence

Store physical-panel sensor rectangle and orientation, logical viewport width/height, requested/effective Android rotation, display mode and resolution, requested/effective refresh, renderer presentation state and transform evidence references separately. A requested portrait/landscape change does not imply physical panel scanout rotated.

Select genuinely supported 60 Hz and native/high fixed refresh modes. If 60 Hz is unavailable, flag unsupported rather than approximate. Fixed-cadence qualification: mode ID unchanged before arm to after download; reported refresh within ±0.1 Hz of target; at least 300 pre-acquisition frame intervals with median within 0.5% of nominal and ≥99% within ±10%, no renderer/swapchain recreation. If VRR cannot be controlled but cadence meets these criteria, record VRR control unavailable rather than claim VRR disabled. VRR testing outside qualification.

## Operator flow and gates

1. Connect fixture and validate info, firmware, power, control interface and self-tests.
2. Select physical rectangle, orientation, display mode, performance state and authoritative or diagnostic preset.
3. Warm up ≥5 minutes with representative black/white rendering and no saver/throttle.
4. Measure actual full-screen white center brightness using owner's Display Pro HL (195–205 nits), store reading/time and Android brightness request, without ICC/LUT profile.
5. Remove colorimeter; install/register five sensor overlay and verify alignment markers.
6. Calibrate black and white sensors independently, derive actual T50 DAC values (and 10/90 diagnostic values).
7. Pass ≥3-minute post-calibration stability: ADC `SAMPLE_ONLY` black/white every 10s, optical amplitude ±2%; meaningful temperature sensors <1°C trend; no configuration drift/throttle.
8. Independently arm each 1,000-trial firmware batch, let fixture operate without any vendor control request during acquisition, then download raw results and append a stable session.
9. Require three qualified batches per authoritative condition and run the repeatability gate; export complete immutable session with qualification reasons.
10. For orientation investigation, repeat required gates upon orientation intervention, associate ADB dumpsys display/SurfaceFlinger evidence and ABBA block identity, then analyze in desktop Rust CLI.

An external luminance value is explicitly operator-confirmed evidence; the app cannot itself measure 200 nits and cannot silently reuse a past reading after a relevant change. OLED reference cadence nominally black 2s / white 1s / black 2s.

App mode has two states: **Qualification** (observable failed gates prevent arm/qualified marking) and **Engineering** (may intentionally override gates but can never mark qualified). Every failed/aborted attempt produces retained evidence; faults do not disappear on retry.

## Autonomous sequencing and integrity

A configured batch creates one `ARM_BATCH`; Android must remain quiescent on the vendor interface while firmware reports/acquires the trial sequence. Determine completion conservatively from final observed HID press/release plus timeout guard as REF-WIRE-001 specifies; on missing sequence wait deterministic maximum, then query, mark failure. No poll loops during timing-critical acquisition. Power, USB detach/re-enumeration, lifecycle/background, renderer recreation, mode/rotation/refresh drift or duplicate/missing HID are zero-tolerance integrity faults. Sample display state before/after and ensure runtime mode-change/recreation observers cover the acquisition window. Any limited telemetry coverage must be disclosed instead of claiming a continuous observation it cannot provide.

The app packages raw firmware `trials.bin` bytes without normalization/re-encoding. Each session includes hashes, display/calibration/fixture/device/config records, Android diagnostics with explicit clock domain, engineering/qualification state and externally captured ADB evidence tied to session ID. `summary.json` is derived convenience only.

## Test surfaces and failures

- Host/JVM: deterministic fake fixture, control codec golden vectors, states, brightness confirmation, 5+3 minute gate, threshold invalidation, stability/reset, orientation and fixed-refresh criteria, instrument faults, all four input/render mode dispatch classifications, unaltered raw export and SHA-256.
- Native tests: stimulus change, button release reset, duplicate input, renderer-independent black/white state, continuous-loop input sampling.
- Android physical: GameActivity and framework paths with actual HID, Vulkan/GLES at 60 Hz and high refresh, separate bulk control claim without HID loss, no vendor traffic during acquisition, lifecycle/USB/rotation faults, export accepted by Rust parser.
- Physical sensor/Display Pro HL and external scope evidence remain manual; no host test may claim those gates passed.
