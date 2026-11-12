# Issue #5 engineering capture candidate

## Status and scope

The default build leaves FlexPWM1 routing and the service profile disabled. Both `SLT_ENABLE_FLEXPWM1_CAPTURE` and `SLT_CAPTURE_PIN_ROUTE_ACCEPTED` must be enabled before firmware changes the pin mux or initializes capture. The candidate has passed host models and a routing-disabled firmware build. No Teensy board revision or fitted QSPI flash/PSRAM has been inspected, so this note records no physical qualification.

The candidate captures one selected optical transition per channel. `Ascending` selects rising edges; `Descending` selects falling edges. The archive stores 10,000 selected edges per channel as 64-bit values. The electrical test must begin from a stable idle level and measure both high and low pulse widths. Depending on that initial level, the first selected edge-counter value is 1 or 2.

## Provisional pin and register map

The pin crosswalk uses the Teensy 4.1 pin definitions in the pinned Teensy core and the PJRC Teensy 4.1 pinout card. The pad mux and eFlexPWM register definitions follow the [NXP i.MX RT1060 Processor Reference Manual, Rev. 3](https://www.pjrc.com/teensy/IMXRT1060RM_rev3.pdf), Chapter 55 and Chapter 11. The [Teensy 4.1 feature and pinout card](https://www.pjrc.com/teensy/card11b_rev4_web.pdf) marks pins 48, 49, 52, and 53 in the QSPI memory expansion area.

FlexPWM1 is based at `0x403DC000`; each submodule advances by `0x60`. Register offsets below are relative to that base. Capture A0 uses CVAL2. Capture B0 uses CVAL4.

| Channel | Teensy pin / pad | Mux | FlexPWM path | Control / compare | Captured value / cycle |
| --- | --- | --- | --- | --- | --- |
| 0 | 48 / GPIO_EMC_24 | ALT1 | SM0 B0 | CAPTCTRLB `0x38` / CAPTCOMPB `0x3A` | CVAL4 `0x50` / CVAL4CYC `0x52` |
| 1 | 53 / GPIO_EMC_27 | ALT1 | SM1 A0 | CAPTCTRLA `0x94` / CAPTCOMPA `0x96` | CVAL2 `0xA8` / CVAL2CYC `0xAA` |
| 2 | 52 / GPIO_EMC_26 | ALT1 | SM1 B0 | CAPTCTRLB `0x98` / CAPTCOMPB `0x9A` | CVAL4 `0xB0` / CVAL4CYC `0xB2` |
| 3 | 49 / GPIO_EMC_25 | ALT1 | SM2 A0 | CAPTCTRLA `0xF4` / CAPTCOMPA `0xF6` | CVAL2 `0x108` / CVAL2CYC `0x10A` |
| 4 | 8 / GPIO_B1_00 | ALT6, daisy 4 | SM3 A0 | CAPTCTRLA `0x154` / CAPTCOMPA `0x156` | CVAL2 `0x168` / CVAL2CYC `0x16A` |

Each path selects the raw PWM input (`INP_SEL=0`), continuous capture, FIFO 0, and one polarity in `EDGA0` or `EDGB0`; it arms only after all five paths are configured. The selected-edge values are `2` for rising and `1` for falling. The edge counter is enabled separately and counts both transitions.

Pins 48/49/52/53 are provisional because they share the QSPI expansion bus. Their use may conflict with fitted flash, PSRAM, or an owner-required QSPI function. Pin 8 avoids that bus for one channel. No SDIO or storage capability is assumed expendable. Before any physical pin-mux release, the owner must identify the actual board revision, inspect fitted memory and required storage use, and accept the exact pin trade-off. The archive itself uses internal RAM2 and never relies on optional PSRAM.

FlexPWM X inputs are not selected. The reference manual describes them as limited-use inputs; this design has not established that the caveat is satisfied for five independent sensor channels. X inputs are not a fallback unless a later hardware review demonstrates all required routing, capture semantics, and timing properties.

## FIFO pairing and loss detection

The eFlexPWM A0 and B0 capture FIFO word counts are `CAPTCTRLA[CA0CNT]` and `CAPTCTRLB[CB0CNT]`, bits 12:10. The manual specifies depth one. `STS.CFA0` and `STS.CFB0` report capture availability and are write-one-to-clear; they are not overflow indicators. `CAPTCOMPA[EDGCNTA]` and `CAPTCOMPB[EDGCNTB]` expose the live 8-bit edge counters. Those counters increment on both input polarities while enabled.

Any FlexPWM1 submodule interrupt runs one fixed aggregate service pass over all five capture paths. This places five already-latched simultaneous edges in one measured ISR budget instead of measuring only five separate per-submodule handlers. The ISR performs this order for each available entry:

1. Read the FIFO count and capture flag; require exactly one entry and an asserted flag.
2. Read the live edge counter, then `CVALxCYC`, then the edge counter again. Reject any change across the cycle-register read.
3. Clear only the matching capture flag with a W1C write before the destructive value read. Read the edge counter again and reject any change since the `CVALxCYC` bracket.
4. Read `CVALx` as a 16-bit access. This read consumes one FIFO entry and decrements the reported count. Read the edge counter immediately after it and reject any change across the destructive read.
5. Recheck FIFO count, capture flag, and edge counter. A newly pending entry must have count one, an asserted flag, and exactly two transition-count increments. An empty FIFO must have a clear flag and unchanged counter.

The reference manual specifies that each `CVALx` read decrements the corresponding FIFO word count; the cycle register is a separate read-only value register. The counter brackets preserve the `CVALxCYC`/`CVALx` pair. Clearing the old W1C flag before reading `CVALx` avoids clearing a newer edge's flag after the FIFO pop. A live edge-counter change across either register read, an impossible count/flag combination, an unexpected counter delta, or a non-draining FIFO faults capture. The aggregate ISR drains at most four entries per path, then faults if work remains.

After arming from a stable electrical idle level, the first selected edge may produce edge-counter value 1 or 2, depending on the input's initial level. Every later selected edge must advance it by exactly 2, representing one active and one return transition. A skipped selected edge, an overwritten FIFO entry, or counter ambiguity faults the run. Counter differences use modulo-256 arithmetic. The qualified service bound must prevent an unobserved interval long enough to make a wrap ambiguous. The physical common-edge test independently counts source edges and must show zero mismatches.

## Shared capture timebase and T0 candidate

All four submodules select the same IPBus clock, prescaler zero, counter direction, `INIT=0`, and full 16-bit modulo. `CTRL[LDMOD]` makes each `MCTRL[LDOK]` write apply the buffered setup immediately. After the four load strobes clear, one common RUN write starts all submodules. SM1 through SM3 select SM0 as their reload source. Startup waits for all four reload flags and rejects phase differences over 16 counter ticks before enabling capture interrupts. This is register-level design evidence; hardware phase alignment still requires physical measurement.

SM0 master reload advances a 64-bit software epoch. `CVALxCYC` contributes the low four bits of the captured PWM modulo-cycle number, and `CVALx` contributes the low 16 bits. The extended timestamp is `(epoch << 16) | counter`; a captured cycle may be assigned to the current or immediately previous epoch. Older or ambiguous cycle values fault. Reload service uses unsigned DWT subtraction only to detect an overdue or missed reload interrupt. DWT values remain diagnostics and are never converted into or substituted for FlexPWM timestamps.

The current-time snapshot reads the master reload flag, counter, then reload flag under a short interrupt mask. A changed reload flag invalidates the snapshot. The code combines the counter and epoch into 64 bits before leaving the critical section. Event-queue publication uses a memory barrier. Deterministic models cover the read ordering; board testing must establish submodule phase, latch behavior, bus ordering, and capture skew.

Firmware accepts BEGIN_CAPTURE only while the Full-Speed session and request identity remain valid. It arms the capture paths only after the matching vendor IN acknowledgement completes successfully. Android then waits 500 ms without vendor traffic and emits `onCaptureReadyForStimulus`; the fixture must gate stimulus on that callback, not on the acknowledgement. During acquisition, one vendor OUT receive remains armed only to detect an unexpected host transfer. A completed unexpected transfer aborts capture, retains an incomplete archive with a fault flag, and receives no vendor response. Normal host operation sends no vendor request until the capture window has ended.

At HID completion, `hidComplete()` preserves the existing DWT diagnostic tick and snapshots the FlexPWM candidate tick only for a successful press-token callback. The main loop must still accept the matching session and transfer generations, current Full-Speed state, active press token, and successful completion before `PulseEngine::Snapshot` promotes the candidate. Releases, reset events, failed transfers, and stale callbacks cannot create T0. E001 keeps its existing 32-byte production payload and continues to report diagnostic DWT ticks; this Issue #5 candidate T0 is retained in firmware state and is not promoted to bus-accurate authority. External USB-analyzer correlation remains unverified.

## Service bound and failure behavior

The qualification profile must provide nonzero values measured against a defined comparator-output envelope. Capture remains unsupported until that profile exists. It must establish:

`maximum interrupt masking + maximum USB interference + maximum aggregate five-path ISR service < minimum same-polarity edge interval`

All terms in that inequality are DWT/core-cycle bounds. The profile must also bound master-reload service latency to less than half the 16-bit counter period. The reload flag is sticky rather than a missed-reload counter; the implementation accepts no more than 1.5 reload periods between serviced flags, which separates one delayed service from a coalesced missed reload only under that half-period latency bound. Separately, each measured aggregate capture ISR must remain within the configured five-path service budget, and capture-clock service latency must remain below the minimum same-polarity capture interval and within the representable interval. The runtime faults on a violated service or reload bound. USB remains at higher interrupt priority; the capture ISR performs fixed work, allocates no memory, and never calls PulseEngine APIs that mutate HID transfer state.

The edge counter increments on both input transitions while each run captures one selected polarity. The first captured sample can therefore report count 1 or 2, depending on the stable input level when the path is armed; later selected samples must differ by exactly two transitions. Any other first count or later delta faults the archive. Host tests cover both polarities and both possible starting levels. The profile limits have not been measured. The default profile has zero edge-rate and service limits, so `E003` returns unsupported. The simulated 1.905 MHz analog bandwidth does not define `R_sensor_max`. Physical qualification must measure the maximum comparator-output rate over a written optical and electrical envelope, including amplitude and threshold conditions, pulse widths, and burst patterns. It must test at `R_sensor_max` and `2 × R_sensor_max`. An archive can be marked incomplete even when every channel reached 10,000 samples if a service, timing, or integrity fault occurs before finalization; retrieval preserves those samples with the fault flags.

The internal design is reconsidered only if a source-level or physical finding establishes an impossible five-input route, an incompatible owner-required QSPI/storage use, irreconcilable CVAL/CYC pairing, an unprovable shared timebase, no defensible capture-loss detector, or service capacity below the qualified rate. A missing `R_sensor_max` remains a physical hold and does not by itself trigger an external design. Any fallback architecture requires a separate decision; no FPGA substitution is authorized here.

## RAM allocation

The candidate linker map places the complete archive in internal RAM2 at `0x20200000`. The archive contains a 128-byte header and 50,000 64-bit samples, for `400,128` bytes total. The map checker also finds the existing USB DMA buffers, event ring, RAM1 stack headroom, and RAM2 heap headroom. It requires at least 32 KiB for each runtime stack or heap reserve and rejects an archive outside internal RAM2.

| Allocation | Linked size / headroom |
| --- | ---: |
| Five-channel samples | 400,000 B |
| Archive header | 128 B |
| Existing USB DMA slots | 2,304 B (16 HID slots and vendor IN/OUT slots) |
| Other DMA data and alignment | 64 B |
| RAM2 static allocation | 402,496 B of 524,288 B |
| RAM2 heap headroom | 121,792 B; 89,024 B after the 32 KiB reserve |
| Fixed event ring in RAM1 | 960 B |
| RAM1 stack headroom | 485,824 B; 453,056 B after the 32 KiB reserve |

These values come from the candidate link map. They do not measure runtime peaks. The archive keeps its fixed size; allocation or profile failure leaves capture unsupported. Runtime peak stack and heap use must be profiled before physical release.

A clean firmware build stores the protocol firmware version and the first eight source-commit bytes in the archive header. A build from a dirty working tree writes zeroes to the commit-prefix field, so its archive cannot be mistaken for one tied to a committed source revision.

## Software and physical evidence boundary

Host tests exercise FIFO count/flag races, CYC/CVAL order, counter changes during and after a pop, W1C behavior, masked service, five simultaneous equal-time samples, 16-bit and 4-bit rollover, reload gaps, both polarities, duplicate nonce rejection, acknowledgement loss, timeout, reset, archive CRC/SHA checks, and protocol frame limits. These deterministic models do not qualify hardware timing or prove that physical capture loses no events.

Physical results remain `NOT_RUN / UNVERIFIED`. After separate owner authorization and Stage-B release, the physical procedure requires at least 10,000 common edges on all five inputs at both `R_sensor_max` and `2 × R_sensor_max`, using each selected polarity in a separate run. Record independently counted source edges, actual high/low pulse widths and bursts, raw 64-bit timestamps, per-channel counts, all fault flags, chunk CRCs, archive SHA-256, measured skew, and at least three cold-power-cycle repeats per rate/polarity case. A pass requires zero missing captures and intact raw evidence. Board identity, QSPI population, capture arming order, electrical thresholds, and cold-cycle repeatability are still open.
