# Android Acquisition App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the internal Android bench application that controls the fixture, renders deterministic latency stimuli through Vulkan/GLES, records display/qualification evidence, and exports complete versioned sessions without participating in authoritative timing.

**Architecture:** Kotlin owns USB host control, qualification state, display configuration, operator workflow, metadata, and export. A C++ GameActivity/native engine owns the native input path, Vulkan/GLES renderers, continuous/event-driven render loops, stimulus state machine, and app-side diagnostic timestamps. A transport interface permits all orchestration and export behavior to be tested against a deterministic fake fixture before physical hardware is available.

**Tech Stack:** Android 11/API 30 minimum, compileSdk/targetSdk 37, AGP 9.3.0, Gradle 9.5+, Kotlin 2.3.21, AndroidX Games Activity 4.4.2, NDK 29.0.14206865, CMake, C++20, Vulkan, OpenGL ES 3.x, JUnit/AndroidX Test.

**Spec:** `docs/superpowers/specs/2026-08-31-screen-latency-measurement-design.md`

## Global Constraints

- Package/application ID: `io.screenlatency.tool` for the internal v1 bench tool.
- Minimum Android version is API 30.
- Native input + Vulkan + continuous game-style loop + minimal workload is authoritative.
- Framework input, GLES, event-driven render, synthetic load, patches, VRR, and performance-preset changes are diagnostic only.
- Android never supplies authoritative `T0` or photon timestamps.
- Vendor USB control traffic is quiescent during firmware acquisition.
- Qualification mode blocks observable violations; engineering mode records them and can never export `qualified=true`.
- 200 +/- 5 nit authority comes from manual Display Pro HL measurement; the app records the operator-confirmed reading but does not profile/calibrate the DUT.
- Authoritative orientation conclusions use fixed refresh with VRR/adaptive-rate behavior disabled where controllable.
- Every exported raw file is preserved and hashed in the v1 session manifest.
- Do not require proprietary thermal APIs or routine Perfetto capture.

---

## File Structure

```text
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/io/screenlatency/tool/
        │   │   ├── MainActivity.kt
        │   │   ├── config/
        │   │   │   └── TestConfiguration.kt
        │   │   ├── fixture/
        │   │   │   ├── FixtureClient.kt
        │   │   │   ├── UsbFixtureClient.kt
        │   │   │   ├── FakeFixtureClient.kt
        │   │   │   └── FixtureProtocol.kt
        │   │   ├── qualification/
        │   │   │   ├── QualificationController.kt
        │   │   │   ├── QualificationGate.kt
        │   │   │   └── StabilityWindow.kt
        │   │   ├── display/
        │   │   │   ├── DisplayController.kt
        │   │   │   └── DisplaySnapshot.kt
        │   │   ├── session/
        │   │   │   ├── SessionModels.kt
        │   │   │   ├── SessionRecorder.kt
        │   │   │   └── SessionExporter.kt
        │   │   └── ui/
        │   │       ├── BenchScreen.kt
        │   │       └── BenchState.kt
        │   ├── cpp/
        │   │   ├── CMakeLists.txt
        │   │   ├── native_engine.cpp
        │   │   ├── native_engine.h
        │   │   ├── input_router.cpp
        │   │   ├── input_router.h
        │   │   ├── renderer.h
        │   │   ├── vulkan_renderer.cpp
        │   │   ├── gles_renderer.cpp
        │   │   ├── render_loop.cpp
        │   │   ├── stimulus.cpp
        │   │   └── timing_events.cpp
        │   └── res/
        │       ├── layout/activity_main.xml
        │       └── xml/device_filter.xml
        ├── test/java/io/screenlatency/tool/
        └── androidTest/java/io/screenlatency/tool/
```

The UI is deliberately View-based rather than Compose-based so the bench controls can coexist with a dedicated native rendering surface without introducing another rendering framework into the measurement activity.

---

### Task 1: Bootstrap the API-30 Android/GameActivity project

**Files:**
- Create: Android Gradle files listed above
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/io/screenlatency/tool/MainActivity.kt`
- Create: `android/app/src/main/java/io/screenlatency/tool/config/TestConfiguration.kt`
- Create: `android/app/src/main/res/layout/activity_main.xml`
- Create: `android/app/src/main/cpp/CMakeLists.txt`
- Create: `android/app/src/main/cpp/native_engine.cpp`

**Interfaces:**
- Produces: launchable `io.screenlatency.tool` debug APK.
- Produces JNI/native library `screen_latency_engine` loaded by the activity.
- Produces Kotlin `InputPath`, `RendererKind`, `RenderLoopKind`, `StimulusMode`, `TransitionDirection`, and `TestConfiguration`, with `TestConfiguration.authoritative()` returning Native/Vulkan/Continuous/Fullscreen/T50/minimal-workload defaults.

- [ ] **Step 1: Write a failing JVM configuration test**

```kotlin
@Test
fun authoritativeDefaultsAreNativeVulkanContinuous() {
    assertEquals(InputPath.NATIVE, TestConfiguration.authoritative().inputPath)
    assertEquals(RendererKind.VULKAN, TestConfiguration.authoritative().renderer)
    assertEquals(RenderLoopKind.CONTINUOUS, TestConfiguration.authoritative().renderLoop)
}
```

- [ ] **Step 2: Verify failure**

Run: `cd android && ./gradlew testDebugUnitTest`

Expected: FAIL before models/project are complete.

- [ ] **Step 3: Configure exact platform/tooling values**

Use:

```kotlin
android {
    namespace = "io.screenlatency.tool"
    compileSdk = 37
    ndkVersion = "29.0.14206865"
    defaultConfig {
        applicationId = "io.screenlatency.tool"
        minSdk = 30
        targetSdk = 37
    }
}
```

Pin AGP `9.3.0`, Kotlin `2.3.21`, and `androidx.games:games-activity:4.4.2`. Configure CMake for C++20 and link `android`, `log`, `vulkan`, `GLESv3`, and the Games Activity native dependency.

- [ ] **Step 4: Build and launch**

Run:

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.screenlatency.tool/.MainActivity
```

Expected: app opens full-screen with an empty native rendering surface and no crash on API 30+.

- [ ] **Step 5: Commit**

```bash
git add android
git commit -m "build: bootstrap Android latency bench app"
```

---

### Task 2: Implement the v1 fixture protocol and transport abstraction

**Files:**
- Create: `fixture/FixtureClient.kt`
- Create: `fixture/FixtureProtocol.kt`
- Create: `fixture/FakeFixtureClient.kt`
- Create: unit tests under `android/app/src/test/.../fixture/`

**Interfaces:**

```kotlin
interface FixtureClient {
    suspend fun getInfo(): FixtureInfo
    suspend fun runElectricalSelfTest(): SelfTestResult
    suspend fun runOpticalSelfTest(): SelfTestResult
    suspend fun calibrateSensors(): CalibrationReading
    suspend fun setThresholds(config: ThresholdConfig)
    suspend fun configureBatch(config: BatchConfig): Unit
    suspend fun armBatch(): Unit
    suspend fun batchStatus(): BatchStatus
    suspend fun downloadResults(): ByteArray
    suspend fun abortBatch(): Unit
}
```

- [ ] **Step 1: Write golden-vector codec tests**

Read `protocol/vectors/wire/*.bin` as test resources and verify Kotlin encode/decode byte equality with Rust golden vectors.

- [ ] **Step 2: Verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*FixtureProtocol*'`

- [ ] **Step 3: Implement strict little-endian codec and fake client**

Reject wrong magic, unsupported major, unknown command/status, length mismatch, and trailing bytes. `FakeFixtureClient` uses deterministic batch bytes copied from `protocol/vectors/trials/` and exposes explicit fault injection for USB disconnect, BUSY, calibration failure, and corrupted result bytes.

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest`

Expected: Kotlin vectors match Rust reference bytes exactly.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/io/screenlatency/tool/fixture android/app/src/test
git commit -m "feat: add Android fixture protocol client"
```

---

### Task 3: Implement the Android USB Host vendor-interface client

**Files:**
- Create: `fixture/UsbFixtureClient.kt`
- Create: `res/xml/device_filter.xml`
- Modify: `AndroidManifest.xml`
- Create: instrumentation tests/fakes under `androidTest`

**Interfaces:**
- Consumes the vendor-specific bulk interface only.
- Must never claim the HID gamepad interface.

- [ ] **Step 1: Write interface-selection tests**

Given a fake composite descriptor containing HID + vendor interfaces, verify selection chooses only class `0xFF` vendor interface and rejects devices lacking exactly one usable bulk IN and bulk OUT endpoint.

- [ ] **Step 2: Verify failure**

Run: `./gradlew connectedDebugAndroidTest`

- [ ] **Step 3: Implement enumeration, permission, claim, transfer, and disconnect handling**

Use `UsbManager`, `UsbDeviceConnection.claimInterface(vendorInterface, true)`, bounded bulk-transfer timeouts, request IDs, and exact response matching. On detach/re-enumeration during a batch, notify qualification/session logic of an integrity fault; do not silently reconnect into the same batch.

- [ ] **Step 4: Verify on physical composite fixture**

Confirm:

- app claims vendor interface;
- Android still receives HID button events through the normal game-controller stack;
- app can run `GET_FIXTURE_INFO` while idle;
- app sends no vendor transfer while fixture reports `Acquiring`.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/io/screenlatency/tool/fixture/UsbFixtureClient.kt android/app/src/main/AndroidManifest.xml android/app/src/main/res/xml/device_filter.xml android/app/src/androidTest
git commit -m "feat: connect Android app to fixture USB interface"
```

---

### Task 4: Build the shared C++ stimulus state machine and input paths

**Files:**
- Create: `cpp/stimulus.cpp`
- Create: `cpp/stimulus.h`
- Create: `cpp/input_router.cpp`
- Create: `cpp/input_router.h`
- Create: `cpp/timing_events.cpp`
- Create: `cpp/timing_events.h`
- Modify: `MainActivity.kt`
- Create: native/JVM bridge tests

**Interfaces:**

```cpp
enum class InputPath { Native, Framework };
enum class StimulusMode { Fullscreen, FivePatches };
enum class TransitionDirection { BlackToWhite, WhiteToBlack };

struct InputEventRecord {
  uint32_t hid_sequence;
  int64_t android_event_time_ns;
  int64_t callback_monotonic_ns;
};
```

The native and framework paths both call one `StimulusController::onMeasuredPress(sequence)`; only the dispatch path differs.

- [ ] **Step 1: Write state-machine tests**

Cover black-to-white measured key-down, unmeasured release resetting to black, white-to-black inverted polarity, duplicate sequence rejection, and five-patch/fullscreen state equivalence.

- [ ] **Step 2: Verify failure**

Run: `./gradlew testDebugUnitTest`

- [ ] **Step 3: Implement native and framework routing**

Use GameActivity native input for the native path. `MainActivity.dispatchKeyEvent()` implements the framework diagnostic path and forwards only configured synthetic gamepad button events to native code. Record Android `eventTime` and callback monotonic time as diagnostics; neither can affect fixture timing.

- [ ] **Step 4: Instrument with physical Teensy HID**

Verify one synthetic measured key-down updates exactly one stimulus sequence in each path and release returns to baseline without creating a second measured sequence.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/cpp android/app/src/main/java/io/screenlatency/tool/MainActivity.kt android/app/src/test
git commit -m "feat: add Android latency stimulus input paths"
```

---

### Task 5: Implement Vulkan and GLES renderers with controlled presentation

**Files:**
- Create: `cpp/renderer.h`
- Create: `cpp/vulkan_renderer.cpp`
- Create: `cpp/gles_renderer.cpp`
- Create: `cpp/render_loop.cpp`
- Modify: `cpp/native_engine.cpp`
- Create: renderer tests/smoke diagnostics

**Interfaces:**

```cpp
enum class RendererKind { Vulkan, Gles };
enum class RenderLoopKind { Continuous, EventDriven };

struct PresentationSnapshot {
  RendererKind renderer;
  uint32_t image_count;
  uint32_t width;
  uint32_t height;
  int64_t refresh_period_ns;
  std::string present_mode;
};
```

- [ ] **Step 1: Write renderer-independent stimulus tests**

Test that both renderers consume the same `StimulusSnapshot` and map black/white to exact SDR RGB 0/255 values. Five-patch coordinates must center on normalized fixture sensor coordinates.

- [ ] **Step 2: Implement Vulkan authoritative renderer**

Use a full-screen swapchain, VSync-compatible FIFO presentation, minimal frames in flight, and the smallest supported image count consistent with stable operation. Record actual selected image count/present mode instead of assuming them. The authoritative frame performs only a clear/fullscreen operation; no texture pipeline is required.

- [ ] **Step 3: Implement GLES diagnostic renderer**

Use EGL/OpenGL ES 3.x with `eglSwapInterval(1)` for matched VSync semantics. Record EGL config, surface dimensions, and actual swap behavior available from the platform.

- [ ] **Step 4: Implement continuous and event-driven loops plus diagnostic presentation controls**

Continuous mode samples shared input state once at a documented point each frame and never wakes specially because of a measured input. Event-driven mode may wake/submit on input and is explicitly diagnostic. Add calibrated synthetic-load hooks that consume approximately 25/50/75/90% of measured frame budget without changing stimulus geometry.

Expose diagnostic presentation configuration for Vulkan FIFO plus MAILBOX when supported, explicit 2/3-image swapchain requests where legal, GLES swap intervals, and deliberate 1/2/3-frame queue-depth experiments. Record requested and actual presentation configuration. Record diagnostic timestamps for stimulus-state consumption, frame submission, and presentation callbacks/timing APIs where reliably available, keyed by HID sequence; these remain in the Android clock domain and never replace fixture timing.

Run physical smoke tests at 60 Hz and one high-refresh mode; confirm no swapchain recreation during a stable batch.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/cpp
git commit -m "feat: add Vulkan and GLES latency renderers"
```

---

### Task 6: Implement display/orientation/brightness control and evidence capture

**Files:**
- Create: `display/DisplayController.kt`
- Create: `display/DisplaySnapshot.kt`
- Create: unit/instrumentation tests under `display/`

**Interfaces:**

```kotlin
data class DisplaySnapshot(
    val logicalWidth: Int,
    val logicalHeight: Int,
    val modeId: Int,
    val refreshRateHz: Double,
    val requestedOrientation: Int,
    val effectiveRotation: Int,
    val brightnessOverride: Float?,
    val rendererPresentation: PresentationSnapshot,
)
```

- [ ] **Step 1: Write mode-selection tests**

Given supported modes, verify exact 60-Hz selection prefers the matching resolution/current display family and high-refresh selection chooses the requested explicit mode rather than simply maxing an unrelated resolution.

- [ ] **Step 2: Verify failure**

Run: `./gradlew testDebugUnitTest --tests '*DisplayController*'`

- [ ] **Step 3: Implement public-API display control**

Use the window's preferred display mode, orientation request, and `screenBrightness`. Disable system UI distractions/fullscreen overlays. Provide explicit diagnostic presets for 25%, 50%, and 100% device brightness while keeping measured 200 +/- 5 nits as the authoritative preset. Provide a VRR/adaptive-refresh diagnostic configuration that records actual presentation intervals; do not claim the app can disable or enable vendor VRR when no public control exists. For authoritative fixed-refresh runs, detect/record unstable effective refresh and fail qualification if the fixed-refresh invariant cannot be demonstrated.

- [ ] **Step 4: Add qualification snapshot sampling**

Sample display state before arm, during stability checks, and immediately after each downloaded batch. Any mode/rotation/presentation recreation mismatch across a batch becomes an integrity failure.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/io/screenlatency/tool/display android/app/src/test android/app/src/androidTest
git commit -m "feat: control and record Android display state"
```

---

### Task 7: Implement qualification and engineering modes

**Files:**
- Create: `qualification/QualificationGate.kt`
- Create: `qualification/QualificationController.kt`
- Create: `qualification/StabilityWindow.kt`
- Create: unit tests under `qualification/`

**Interfaces:**

```kotlin
enum class RunMode { QUALIFICATION, ENGINEERING }

data class GateResult(val id: String, val passed: Boolean, val evidence: String)

data class QualificationState(
    val mode: RunMode,
    val gates: List<GateResult>,
    val canAcquire: Boolean,
    val canMarkQualified: Boolean,
)
```

- [ ] **Step 1: Write strict-gate tests**

Cover required failure/block cases:

- electrical self-test failure;
- sensor calibration failure;
- missing operator Display Pro HL confirmation;
- confirmed luminance outside 195–205 nits;
- relative center-sensor white drift >2%;
- <5-minute warm-up;
- <3-minute post-overlay stability window;
- thermal-throttling indication;
- refresh/orientation change;
- unhealthy USB/power;
- swapchain recreation.

Also verify engineering mode allows acquisition but `canMarkQualified` remains false.

- [ ] **Step 2: Verify failure**

Run: `./gradlew testDebugUnitTest --tests '*Qualification*'`

- [ ] **Step 3: Implement warm-up/stability state machine**

The sequence is exact:

Before entering the sequence, record battery percentage, charging state, Android thermal status, OEM performance-mode label as entered by the operator, and any CPU/GPU frequency/temperature information accessible through public or readable platform interfaces. Unavailable private/vendor telemetry is recorded as unavailable rather than becoming a requirement.

1. final display/performance state selected;
2. representative render workload runs for at least 5 minutes;
3. operator records Display Pro HL reading 195–205 nits;
4. colorimeter removed;
5. overlay installed and thresholds calibrated;
6. 3-minute relative stability window begins;
7. center analog white level drifts <=2%, meaningful temperature sensors trend <1 C where available, and no other gate fails;
8. only then may qualification acquisition arm.

Any intervention that can invalidate absolute luminance resets the flow to step 3.

- [ ] **Step 4: Run tests and fake-fixture workflow**

Use `FakeFixtureClient` to exercise every failure gate and verify exported evidence explains why the run was blocked or engineering-only.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/io/screenlatency/tool/qualification android/app/src/test
git commit -m "feat: add strict latency qualification gates"
```

---

### Task 8: Implement autonomous batch orchestration and qualification checks

**Files:**
- Create: `session/SessionRecorder.kt`
- Create: orchestration tests
- Modify: `qualification/QualificationController.kt`
- Modify: `MainActivity.kt`

**Interfaces:**
- Configures 1,000-trial firmware batch, arms it once, waits without polling traffic during acquisition, then downloads result bytes afterward.
- Executes three independent qualified batches per authoritative condition.

- [ ] **Step 1: Write fake-fixture orchestration tests**

Verify:

- exactly one `CONFIGURE_BATCH` and `ARM_BATCH` before each batch;
- no fixture calls while fake fixture reports `Acquiring` except the completion signal mechanism chosen by the USB client; if completion requires status polling on v1 firmware, polling must begin only after the configured worst-case batch duration, not during timing-critical trial cadence;
- raw download occurs only after complete;
- overall invalid <=1%;
- center valid >=99.5%;
- three center medians span <=0.5 ms before condition qualifies;
- all zero-tolerance integrity faults fail the batch.

- [ ] **Step 2: Verify failure**

Run: `./gradlew testDebugUnitTest --tests '*SessionRecorder*'`

- [ ] **Step 3: Implement condition/batch controller**

Generate independent PRNG seeds per batch and preserve them. Black-to-white and white-to-black are separate conditions. For authoritative sessions, require three qualified batches; never convert a failed batch to engineering data silently.

- [ ] **Step 4: Run fake and physical fixture smoke tests**

Acquire a short engineering batch first, then a full 1,000-trial hardware batch. Confirm no vendor bulk traffic appears between `ARM_BATCH` and firmware completion.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/io/screenlatency/tool/session android/app/src/main/java/io/screenlatency/tool/qualification android/app/src/main/java/io/screenlatency/tool/MainActivity.kt android/app/src/test
git commit -m "feat: orchestrate autonomous latency batches"
```

---

### Task 9: Implement versioned session packaging and export

**Files:**
- Create: `session/SessionModels.kt`
- Create: `session/SessionExporter.kt`
- Create: session export tests
- Modify: `SessionRecorder.kt`

**Interfaces:**
- Produces: `.slt-session.zip` containing the exact v1 conceptual layout and SHA-256 manifest.
- Consumes: `protocol/schema/*.json`, `protocol/session-v1.md`, raw firmware batch bytes.

- [ ] **Step 1: Write golden session tests**

Build a Kotlin session from `FakeFixtureClient` and compare required metadata semantics against `protocol/vectors/sessions/qualified-minimal`. Verify engineering mode can never write `qualified=true`.

- [ ] **Step 2: Verify failure**

Run: `./gradlew testDebugUnitTest --tests '*SessionExporter*'`

- [ ] **Step 3: Implement package writer**

Write:

```text
manifest.json
device.json
fixture.json
calibration.json
display.json
configuration.json
batches/001/trials.bin
batches/001/android-events.json
batches/002/...
batches/003/...
diagnostics/adb/...   // only when imported/available
summary.json
```

Create all non-manifest files first, compute SHA-256 and byte size, then write `manifest.json`. Never normalize/re-encode raw `trials.bin` downloaded from firmware.

- [ ] **Step 4: Validate exported package with Rust reference codec**

Export a fake session, unzip or point the Rust validator at it, and run the `slt-protocol` session validation from the root workspace. Digest and schema validation must pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/io/screenlatency/tool/session android/app/src/test
git commit -m "feat: export versioned latency sessions"
```

---

### Task 10: Build the internal bench UI and ADB evidence workflow

**Files:**
- Create: `ui/BenchState.kt`
- Create: `ui/BenchScreen.kt`
- Modify: `MainActivity.kt`
- Modify: `activity_main.xml`
- Create: `qualification/adb-capture.md`

**Interfaces:**
- Presents two explicit modes: Qualification and Engineering.
- Allows authoritative preset plus diagnostic controls without changing defaults silently.

- [ ] **Step 1: Write state-model tests**

Verify authoritative preset cannot be accidentally modified without making the configuration non-authoritative. UI state must display current input path, renderer, render loop, transition direction, threshold, requested/effective refresh, luminance confirmation, fixture status, and qualification state.

- [ ] **Step 2: Implement bench workflow UI**

Provide actions in this order:

1. Connect fixture
2. Electrical self-test
3. Configure display/orientation/refresh
4. Warm up
5. Confirm Display Pro HL luminance
6. Verify overlay/alignment
7. Calibrate thresholds
8. Stabilize
9. Run three batches
10. Export session

Diagnostic controls are collapsed/separate from the authoritative preset. They must explicitly include Native/GLES, Framework/Vulkan, Framework/GLES, event-driven rendering, T10/T90 threshold presets, five isolated patches, 25/50/75/90% calibrated synthetic load, 25/50/100% device-brightness presets, additional fixed refresh modes, VRR/adaptive-refresh investigation, balanced/performance comparison, and the presentation/queue-depth variants implemented by the native engine. Perfetto is launched/documented externally as an investigation tool rather than embedded into routine acquisition.

- [ ] **Step 3: Add alignment/calibration stimuli**

Render visible markers at all five normalized sensor positions and the active-screen bounds. Add OLED-friendly black 2 s / white 1 s / black 2 s calibration cadence.

- [ ] **Step 4: Document ADB orientation evidence capture**

`adb-capture.md` records the required commands and where outputs are imported into the session:

```bash
adb shell dumpsys display > dumpsys-display.txt
adb shell dumpsys SurfaceFlinger > surfaceflinger.txt
```

Also record the app build/version and current session ID next to imported diagnostics. The scripts accept `--session-id`, capture the files on the host, then `adb push` them into `/sdcard/Android/data/io.screenlatency.tool/files/adb-import/<session-id>/`. Before export, the app imports only the matching session directory into `diagnostics/adb/`, computes its manifest hashes, and leaves unmatched/stale imports out of the package.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/io/screenlatency/tool/ui android/app/src/main/java/io/screenlatency/tool/MainActivity.kt android/app/src/main/res/layout/activity_main.xml qualification/adb-capture.md
git commit -m "feat: add latency bench qualification workflow"
```

---

## Completion Gate

Run:

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest
```

Then on a physical API-30+ handheld and the real fixture verify:

- Android receives Teensy HID button 1 through the normal input stack while the app owns only vendor bulk endpoints;
- Native/Vulkan, Native/GLES, Framework/Vulkan, and Framework/GLES all produce the same logical stimulus transitions;
- authoritative mode is Native/Vulkan/continuous/minimal workload;
- fixed 60-Hz and explicit high-refresh modes are selected/recorded rather than assumed;
- qualification gating follows the exact 5-minute + Display Pro HL + overlay calibration + 3-minute stability workflow;
- a full 1,000-trial batch is acquired without timing-period vendor traffic and raw bytes are downloaded afterward;
- three-batch repeatability logic and invalid-trial thresholds are enforced;
- exported `.slt-session.zip` passes the Rust v1 contract validator;
- engineering exports can never present themselves as qualified.