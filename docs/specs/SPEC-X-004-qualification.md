# SPEC-X-004 — Physical Qualification and Publication Evidence

**Document ID:** SPEC-X-004  
**Status:** Ready for Implementation
**Readiness note:** Procedure implementation is ready; physical acceptance is not yet proven  
**Owner:** Repository maintainers\
**Last Updated:** 2026-10-29  
**Depends On:** ARCH-001, REF-MET-001, REF-WIRE-001, REF-DATA-001, SPEC-X-001, SPEC-X-002, SPEC-X-003  
**Supersedes:** Qualification requirements in the legacy spec, closure amendment and end-to-end checkbox plan  
**Superseded By:** None

## Qualification philosophy

Qualification is a **measured evidence gate**, not a checklist an agent can mark complete by code inspection. Keep raw USB waveforms, loopback values, optical traces, sensor voltages, Android exported sessions, mode/thermal evidence, calibration/temperature records, correction coefficients and analyzer version/hash. Failed attempts remain preserved with exact failing criterion, and a rerun adds a new immutable artifact. A blocked physical test remains blocked, not passed through mocks.

Reference external instruments: Calibrite Display Pro HL for independent center-panel absolute luminance; Rigol DHO814 or equivalently qualified oscilloscope for bus-level HID timing/capture, with USB 2.0 analyzer borrow/rent fallback where Full-Speed forcing/probing cannot substantiate T0. USB probe loading must be checked for changed enumeration/retries. The DHO814's maximum 1.25 GSa/s and 25 Mpts are one-channel ratings; four-channel simultaneous operation is lower. Record actual bench acquisition settings and resulting uncertainty rather than advertising peak specification.

## Gate 1 — Pre-fabrication feasibility

Demonstrate with real hardware:
1. Teensy 4.1 forced Full-Speed composite enumerates as HID plus independent vendor bulk interface on Android.
2. A measured button-1 HID press causes a real Android-native-rendered display transition seen by one optical sensor and linked to the corresponding completed HID transfer; components remain reusable in production architecture.
3. Five independent hardware capture registers/signal paths work on a justified common timebase over ≥10,000 shared-edge stimuli without loss; preliminary skew can be corrected to target.
4. An external oscilloscope correlation of actual successful HID completion versus firmware `T0`/marker has known worst-case delay and non-perturbing probing. It leaves credible headroom for ≤0.1 ms total fixture uncertainty.
5. Prototype optical front end has sufficient bandwidth, amplitude and saturation margin for 200-nit SDR black/white.

Pass permits production PCB contract freeze and fabrication decisions; it does **not** qualify a full five-sensor instrument.

## Gate 2 — Electronics and fixture qualification

- Electrical loopback on final assembled hardware ≥10,000 edges, zero misses, post-correction skew ≤25 µs; verify again after a cold power cycle.
- Bound common timebase quantization, USB T0 detection delay/uncertainty, capture residual skew, LED optical response/detector/TIA/comparator delay, thresholds and sensor drift. Conservatively sum worst-case terms unless independence is demonstrated. Complete fixture claimed uncertainty must be ≤0.1 ms.
- Characterize one analog module first and then all five; 10/50/90% DAC/ADC black-white calibration, no comparator saturation at 200 nits or intended diagnostics, no spontaneous dark edges, PWM repeated-edge detection and full optical self-test. The common LED emission delay may cancel in channel-skew comparisons but not automatically from absolute optical uncertainty.
- External regulated power/supervisor/brownout, self-powered USB attach/detach and VBUS anti-backfeed verified.
- PCB source/fab/assembly materials, finished firmware revision and parametric overlay qualified. Mechanical registration across ~3–9-inch target range demonstrated on representative geometries, not assumed universally.
- Three full remount/recalibration cycles on one stable reference DUT: center median span ≤0.5 ms, spatial delta agreement within actual timing/mechanical uncertainty.

## Gate 3 — Android authoritative acquisition

For a representative API30+ reference DUT, apply these criteria at fixed 60 Hz if the DUT genuinely supports it. If that same DUT has no valid fixed 60 Hz mode, mark normalized-60 unsupported and apply the criteria to its genuinely supported fixed native/high-refresh run:
- normal HID input while independently claiming vendor interface;
- Native/Vulkan, continuous, low-latency VSync, minimum workload; both full-screen directions;
- ≥5 min warmup, manually measured 195–205 nits after warmup, overlay sensor calibration, 3-min optical stability ±2%, temperature trend <1°C where available and no throttle;
- fixed-cadence 300-frame test, selected mode stable through acquisition; no renderer or lifecycle interruption;
- full 1,000-trial batch, three independent batches per direction, download only after acquisition and unchanged binary raw bytes;
- overall invalid ≤1%, center valid ≥99.5%, no integrity faults, three median span ≤0.5 ms;
- exported zipped session passes Rust validation, including all invalid/failure records and SHA-256 file hashes;
- framework/Vulkan, native/GLES, framework/GLES and event-driven diagnostic pathways each produce the intended stimulus and remain explicitly non-authoritative.

For each DUT, acquire and report a genuinely supported fixed native/high-refresh run with its selected mode and fixed-cadence evidence. When fixed 60 Hz is supported, this native/high run is a required characterization and reporting condition, but is not a separate publication pass/fail gate. When no valid fixed 60 Hz mode is supported, mark normalized-60 unsupported and use the native/high run as the authoritative Gate 3 qualification condition; it must satisfy the applicable Gate 3 evidence and acceptance criteria. Never synthesize or approximate 60 Hz.

## Gate 4 — Orientation and reviewer inference

- Establish actual panel/chassis/native/logical transform geometry from display evidence and ADB diagnostics; no inference from marketing description or logical width alone.
- Execute same-device counterbalanced ABBA sequence with the physical fixture unchanged. Each A/B block is independently luminance-verified within 195–205 nits, pair difference ≤2.0 nits, fixed refresh, same renderer/input/workload/power, and three qualified 1,000-trial batches **per transition direction**.
- Analyze signed center effect, confidence/uncertainty interval using pre-registered block-aware method, ±1 ms practical interval, spatial gradients, drift, invalid rates and evidence limitations. A near-zero point estimate without sufficiently narrow interval is inconclusive, not equivalence.
- Execute matched cross-device portrait-native vs landscape-native practical comparison at genuine common fixed refresh (60 Hz if available, otherwise another supported matched rate); both transition directions, both luminance qualified, same APK/fixture configuration.
- Demonstrate at least one targeted diagnostic escalation on an anomaly or injected engineering-mode fault; state inference boundaries, never assign a specific system root cause without independent evidence.

## Publication/retention

A traceable qualification report includes a criterion/evidence/result index for all applicable gates, linked immutable raw session archives and SHA-256, benchmark conditions, uncertainty contributions, analyzer version, explanation of exclusions and failures, and a status of **publication-grade v1 qualified** only when all required criteria pass. Otherwise status **not publication-grade** with concrete blockers.

Store qualification archives directly in Git while cumulative raw artifacts ≤250 MiB. If above, approve a dedicated storage strategy before migrating; do not silently use LFS. Keep engineering experiments and failed proof without overwriting their sources. A published result is an analyzer-produced derivative, not authority over raw measurement bytes.

## Automation versus manual proof

CI on Linux/macOS/Windows: Rust fmt/clippy/tests/golden vectors; Linux Android pinned Gradle + JVM, C++ and debug APK; Linux pinned PlatformIO native firmware test + Teensy build; KiCad ERC/DRC and headless OpenSCAD after sources exist. Physical Android instrumentation, Display Pro HL, oscilloscope measurements, LED optics, wiring and remounting are manual/proctored qualification. Issue tracking and execution readiness live solely in GitHub Issues, linked to this specification.
