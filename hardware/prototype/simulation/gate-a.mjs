// Reproduce the Issue 3 analog feasibility sweeps with externally supplied TI models.
//
// The simulation outputs and model files stay outside the repository. The runner records
// model hashes, writes every generated deck and raw result to --out-dir, and prints a
// concise JSON summary suitable for review.
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { basename, resolve } from "node:path";
import { spawnSync } from "node:child_process";

function fail(message) {
  console.error(message);
  process.exit(2);
}

function argumentsFrom(argv) {
  const values = {};
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index];
    const value = argv[index + 1];
    if (!key?.startsWith("--") || value == null) fail("Arguments must be --name value pairs.");
    values[key.slice(2)] = value;
  }
  return values;
}

const args = argumentsFrom(process.argv.slice(2));
for (const required of ["opa-model", "tlv-model", "out-dir", "simulator-provenance"]) {
  if (!args[required]) fail(`Missing --${required}.`);
}

function pathNgspice() {
  const result = spawnSync("which", ["ngspice"], { encoding: "utf8" });
  const found = result.status === 0 ? result.stdout.trim().split(/\r?\n/)[0] : "";
  if (!found) fail("Pass --ngspice or put ngspice on PATH.");
  return found;
}

const paths = {
  ngspice: resolve(args.ngspice ?? pathNgspice()),
  opa: resolve(args["opa-model"]),
  tlv: resolve(args["tlv-model"]),
  out: resolve(args["out-dir"]),
  provenance: resolve(args["simulator-provenance"]),
};
for (const path of [paths.ngspice, paths.opa, paths.tlv, paths.provenance]) {
  if (!existsSync(path)) fail(`Required file does not exist: ${path}`);
}
mkdirSync(paths.out, { recursive: true });
writeFileSync(resolve(paths.out, ".spiceinit"), "set ngbehavior=ps\nset noaskquit\n");

const hash = (path) => createHash("sha256").update(readFileSync(path)).digest("hex");
const simulatorProvenance = JSON.parse(readFileSync(paths.provenance, "utf8"));
if (!Array.isArray(simulatorProvenance.buildEvidence) || simulatorProvenance.buildEvidence.length === 0) {
  fail("Simulator provenance must list at least one buildEvidence file.");
}
const simulatorBuildEvidence = simulatorProvenance.buildEvidence.map((entry) => {
  const evidencePath = resolve(entry.file);
  if (!existsSync(evidencePath)) fail(`Build evidence does not exist: ${evidencePath}`);
  return { file: evidencePath, sha256: hash(evidencePath), observations: entry.observations ?? [] };
});
const versionResult = spawnSync(paths.ngspice, ["--version"], { encoding: "utf8" });
if (versionResult.status !== 0) fail(`Unable to query simulator version: ${paths.ngspice}`);
const simulatorVersion = `${versionResult.stdout ?? ""}${versionResult.stderr ?? ""}`.trim();
const simulatorInvocations = [];
const quote = JSON.stringify;
const rows = (path) => readFileSync(path, "utf8")
  .split(/\r?\n/)
  .filter((line) => line.trim())
  .map((line) => line.trim().split(/\s+/).map(Number));
const nearest = (data, target, column = 0) => data.reduce((best, row) =>
  Math.abs(row[column] - target) < Math.abs(best[column] - target) ? row : best);
function simulate(name, template) {
  const deck = resolve(paths.out, `${name}.cir`);
  const data = resolve(paths.out, `${name}.dat`);
  const log = resolve(paths.out, `${name}.log`);
  const body = template
    .replaceAll("__OPA_MODEL__", paths.opa)
    .replaceAll("__TLV_MODEL__", paths.tlv)
    .replaceAll("__DATA__", data);
  writeFileSync(deck, `${body.trim()}\n`);
  const result = spawnSync(paths.ngspice, ["-b", deck], { cwd: paths.out, encoding: "utf8" });
  simulatorInvocations.push({ command: [paths.ngspice, "-b", deck], cwd: paths.out });
  writeFileSync(log, `${result.stdout ?? ""}${result.stderr ?? ""}`);
  if (result.status !== 0 || !existsSync(data)) {
    throw new Error(`ngspice failed for ${basename(deck)}; see ${log}`);
  }
  return rows(data);
}

const tiaHeader = `
.include "__OPA_MODEL__"
VEE vee 0 0
RDIODE sum vref 424Meg
RF sum out 47k
CF sum out 2.2p
`;

const ac = simulate("gate-a-tia-ac", `
Nominal TIA AC response
${tiaHeader}
.param CINPUT=20p
VCC vcc 0 3.3
VREF vref 0 1.179
IINJ 0 sum AC 1
CIN sum vref {CINPUT}
XU1 vref sum vcc vee out OPAx320-Q1
.ac dec 100 1 100Meg
.control
run
set wr_singlescale
wrdata __DATA__ v(out)
.endc
.end
`);
const acDc = Math.hypot(ac[0][1], ac[0][2]);
const halfPower = nearest(ac, acDc / Math.SQRT2, 0);
const bandwidth = ac.find((row) => Math.hypot(row[1], row[2]) <= acDc / Math.SQRT2)?.[0];

const dc = simulate("gate-a-tia-dc", `
Nominal TIA input envelope
${tiaHeader}
.param CINPUT=20p
VCC vcc 0 3.3
VREF vref 0 1.179
IINJ 0 sum DC 0
CIN sum 0 {CINPUT}
XU1 vref sum vcc vee out OPAx320-Q1
.dc IINJ 0 30u 0.1u
.control
run
set wr_singlescale
wrdata __DATA__ v(out) v(sum)
.endc
.end
`);
const envelope = Object.fromEntries([0, 0.1, 1, 10, 20, 23, 24, 25, 30].map((microamps) => {
  const row = nearest(dc, microamps * 1e-6);
  return [`${microamps}uA`, row[1]];
}));

const noise = simulate("gate-a-tia-noise", `
Nominal TIA output noise
${tiaHeader}
.param CINPUT=20p
VCC vcc 0 3.3
VREF vref 0 1.179
IINJ 0 sum DC 0 AC 1
CIN sum vref {CINPUT}
XU1 vref sum vcc vee out OPAx320-Q1
.noise v(out) IINJ dec 20 1 10Meg
.control
run
setplot noise1
set wr_singlescale
wrdata __DATA__ onoise_spectrum inoise_spectrum
.endc
.end
`);
const integrateNoise = (limit) => {
  const band = noise.filter((row) => row[0] <= limit);
  return Math.sqrt(band.slice(1).reduce((sum, row, index) =>
    sum + ((row[1] ** 2 + band[index][1] ** 2) / 2) * (row[0] - band[index][0]), 0));
};

function stepCase(name, current, cin, cf) {
  const data = simulate(name, `
TIA ${current} A step, CIN=${cin}, CF=${cf}
.include "__OPA_MODEL__"
VCC vcc 0 3.3
VEE vee 0 0
VREF vref 0 1.179
IINJ 0 sum PULSE(0 ${current} 1u 5n 5n 2u 4u)
RDIODE sum vref 424Meg
RF sum out 47k
CF sum out ${cf}
CIN sum vref ${cin}
XU1 vref sum vcc vee out OPAx320-Q1
.tran 0.5n 3u 0 0.25n
.control
run
set wr_singlescale
wrdata __DATA__ v(out) v(sum)
.endc
.end
`);
  const start = nearest(data, 0.9e-6)[1];
  const finish = nearest(data, 2.9e-6)[1];
  const delta = finish - start;
  const after = data.filter((row) => row[0] >= 1e-6);
  const extremum = delta < 0 ? Math.min(...after.map((row) => row[1])) : Math.max(...after.map((row) => row[1]));
  const tolerance = Math.abs(delta) * 0.02;
  let settled = null;
  for (const row of after) {
    const interval = after.filter((candidate) =>
      candidate[0] >= row[0] && candidate[0] <= Math.min(row[0] + 1e-6, 3e-6));
    if (interval.length && interval.every((candidate) => Math.abs(candidate[1] - finish) <= tolerance)) {
      settled = row[0] - 1e-6;
      break;
    }
  }
  return {
    stepMillivolts: delta * 1000,
    overshootPercent: Math.abs(extremum - finish) / Math.abs(delta) * 100,
    settleTo2PercentMicroseconds: settled == null ? null : settled * 1e6,
  };
}

const steps = Object.fromEntries([
  ["0.1uA", 0.1e-6], ["1uA", 1e-6], ["10uA", 10e-6], ["23uA", 23e-6],
].map(([label, current]) => [label, stepCase(`gate-a-step-${label}`, current, "20p", "2.2p")]));
const sensitivity = {};
for (const cin of ["20p", "22p", "25p"]) {
  for (const cf of ["2.1p", "2.2p", "2.3p"]) {
    sensitivity[`CIN=${cin},CF=${cf}`] = stepCase(`gate-a-step-CIN-${cin}-CF-${cf}`, "1u", cin, cf);
  }
}

const rails = {};
for (const rail of [3.0, 3.1, 3.2, 3.3, 3.4, 3.6]) {
  const data = simulate(`gate-a-tia-rail-${rail.toFixed(1)}`, `
TIA rail sweep at ${rail.toFixed(1)} V
.include "__OPA_MODEL__"
VCC vcc 0 ${rail}
VEE vee 0 0
VREF vref 0 ${rail * 10 / 28}
IINJ 0 sum DC 0
RDIODE sum 0 424Meg
RF sum out 47k
CF sum out 2.2p
CIN sum 0 20p
XU1 vref sum vcc vee out OPAx320-Q1
.dc IINJ 0 30u 0.1u
.control
run
set wr_singlescale
wrdata __DATA__ v(out)
.endc
.end
`);
  rails[rail.toFixed(1)] = {
    darkVolts: nearest(data, 0)[1],
    at20MicroampsVolts: nearest(data, 20e-6)[1],
    at23MicroampsVolts: nearest(data, 23e-6)[1],
    at24MicroampsVolts: nearest(data, 24e-6)[1],
    minimumVolts: Math.min(...data.map((row) => row[1])),
  };
}

const overload = simulate("gate-a-tia-overload", `
TIA overload and recovery
.include "__OPA_MODEL__"
VCC vcc 0 3.3
VEE vee 0 0
VREF vref 0 1.179
IINJ 0 sum PULSE(0 40u 1u 20n 20n 1u 100u)
RDIODE sum vref 424Meg
RF sum out 47k
CF sum out 2.2p
CIN sum vref 20p
XU1 vref sum vcc vee out OPAx320-Q1
.tran 5n 100u 0 2.5n
.control
run
set wr_singlescale
wrdata __DATA__ v(out)
.endc
.end
`);
const overloadSaturated = overload.filter((row) => row[0] >= 1.2e-6 && row[0] <= 1.9e-6);
const overloadFinal = nearest(overload, 0.9e-6)[1];
const overloadMinimum = Math.min(...overloadSaturated.map((row) => row[1]));
const recoveryViolations = overload.filter((row) =>
  row[0] >= 2e-6 && Math.abs(row[1] - overloadFinal) > 0.01 * (overloadFinal - overloadMinimum));
const recoveryTime = recoveryViolations.length
  ? Math.max(...recoveryViolations.map((row) => row[0])) - 2e-6
  : 0;
const recoveryFinalSamples = overload.filter((row) => row[0] >= 90e-6);
const recoveryFinalSamplesWithinTolerance = recoveryFinalSamples.length > 0 && recoveryFinalSamples.every((row) =>
  Math.abs(row[1] - overloadFinal) <= 0.01 * (overloadFinal - overloadMinimum));

const startup = simulate("gate-a-tia-startup", `
TIA clean power-up
.include "__OPA_MODEL__"
VCC vcc 0 PWL(0 0 10u 3.3 100u 3.3)
VEE vee 0 0
VREF vref 0 PWL(0 0 10u 1.179 100u 1.179)
IINJ 0 sum DC 0
RDIODE sum vref 424Meg
RF sum out 47k
CF sum out 2.2p
CIN sum vref 20p
XU1 vref sum vcc vee out OPAx320-Q1
RLOAD out 0 10k
.tran 20n 100u 0 10n uic
.control
run
set wr_singlescale
wrdata __DATA__ v(out) v(vcc)
.endc
.end
`);

const comparatorHeader = `
.include "__TLV_MODEL__"
VEE vee 0 0
VSHUT shdn 0 0
RLOAD cmp_out 0 10k
`;
const hysteresis = simulate("gate-a-comparator-hysteresis", `
TLV3501 intrinsic hysteresis
${comparatorHeader}
VCC vcc 0 3.3
VTH threshold 0 PWL(0 0.97 10u 1.03 20u 0.97 30u 1.03 40u 0.97)
VIN tia_out 0 1.0
XU2 threshold tia_out shdn vcc vee cmp_out TLV3501
.tran 2n 40u 0 1n
.control
run
set wr_singlescale
wrdata __DATA__ v(threshold) v(cmp_out)
.endc
.end
`);
const rising = [];
const falling = [];
for (let index = 1; index < hysteresis.length; index++) {
  const before = hysteresis[index - 1];
  const after = hysteresis[index];
  if (before[2] <= 1.64 && after[2] > 1.64) rising.push(after[1]);
  if (before[2] >= 1.64 && after[2] < 1.64) falling.push(after[1]);
}

const noisyComparator = {};
for (const millivolts of [2, 4, 6, 8, 10]) {
  const data = simulate(`gate-a-comparator-noise-${millivolts}mv`, `
TLV3501 repeated crossing probe
${comparatorHeader}
VCC vcc 0 3.3
VTH threshold 0 DC 1.179 SIN(1.179 ${millivolts}m 100k)
VIN tia_out 0 1.179
XU2 threshold tia_out shdn vcc vee cmp_out TLV3501
.tran 2n 100u 0 1n
.control
run
set wr_singlescale
wrdata __DATA__ v(cmp_out)
.endc
.end
`);
  const output = data.map((row) => row[1]);
  noisyComparator[`${millivolts}mV`] = {
    transitions: output.slice(1).filter((value, index) => (value >= 1.64) !== (output[index] >= 1.64)).length,
  };
}

const comparatorRails = {};
for (const rail of [3.0, 3.3, 3.6]) {
  const data = simulate(`gate-a-comparator-rail-${rail.toFixed(1)}`, `
TLV3501 output at ${rail.toFixed(1)} V
${comparatorHeader}
VCC vcc 0 ${rail}
VTH threshold 0 PWL(0 0.95 2u 0.95 3u 1.05 10u 1.05 11u 0.95)
VIN tia_out 0 1.0
XU2 threshold tia_out shdn vcc vee cmp_out TLV3501
.tran 1n 12u 0 0.5n
.control
run
set wr_singlescale
wrdata __DATA__ v(cmp_out)
.endc
.end
`);
  comparatorRails[rail.toFixed(1)] = {
    lowVolts: Math.min(...data.map((row) => row[1])),
    highVolts: Math.max(...data.map((row) => row[1])),
  };
}

const pinMap = simulate("gate-a-tlv-pin-map", `
TLV3501 explicit IN+, IN-, SHDN, V+, V-, OUT mapping
${comparatorHeader}
VCC vcc 0 3.3
VINP inp 0 PWL(0 1.20 2u 1.20 2.1u 0.95 4u 0.95)
VINN inn 0 1.0
XU2 inp inn shdn vcc vee cmp_out TLV3501
.tran 0.5n 4u 0 0.25n
.control
run
set wr_singlescale
wrdata __DATA__ v(cmp_out)
.endc
.end
`);

const comparatorStartup = simulate("gate-a-comparator-startup", `
TLV3501 power-up probe
${comparatorHeader}
VCC vcc 0 PWL(0 0 10u 3.3 100u 3.3)
VTH threshold 0 PWL(0 0 10u 1.179 100u 1.179)
VIN tia_out 0 PWL(0 0 10u 1.200 100u 1.200)
XU2 threshold tia_out shdn vcc vee cmp_out TLV3501
.tran 20n 100u 0 10n uic
.control
run
set wr_singlescale
wrdata __DATA__ v(cmp_out) v(vcc)
.endc
.end
`);

const summary = {
  status: "passed",
  modelProvenance: {
    opa320: {
      file: basename(paths.opa), sha256: hash(paths.opa),
      sourcePage: "https://www.ti.com/product/OPA320-Q1#design-tools-simulation",
      version: "Final 1.6 (model file dated 16AUG2021; Generic, suitable for all analysis types); OPAx320-Q1.LIB from SBOMBW3.ZIP",
      limitation: "The five-node subcircuit matches Gate A’s call, but this Q1 macro-model’s suitability for the board’s exact non-Q1 OPA320 orderable/electrical behavior is not proven",
    },
    tlv3501: {
      file: basename(paths.tlv), sha256: hash(paths.tlv),
      sourcePage: "https://www.ti.com/product/TLV3501#design-tools-simulation",
      version: "model revision 2.0, file dated 2021-07-26, generated for PSpice 17.4; SBOMBU5.ZIP",
      limitation: "TI PSpice transient model executed with ngbehavior=ps",
    },
    simulator: {
      file: basename(paths.ngspice), path: paths.ngspice, sha256: hash(paths.ngspice),
      versionOutput: simulatorVersion,
      buildProvenance: {
        file: paths.provenance,
        sha256: hash(paths.provenance),
        sourceVersion: simulatorProvenance.sourceVersion,
        sourceIdentity: simulatorProvenance.sourceIdentity,
        configureArguments: simulatorProvenance.configureArguments ?? [],
        compilerFlags: simulatorProvenance.compilerFlags ?? null,
        featureEvidence: simulatorProvenance.featureEvidence ?? [],
        evidenceFiles: simulatorBuildEvidence,
        limitations: simulatorProvenance.limitations ?? [],
      },
      invocationTemplate: "<ngspice> -b <generated-deck>",
      invocations: simulatorInvocations,
    },
    acquisitionGuidance: "Pass --ngspice or put ngspice on PATH. Supply external TI model files with --opa-model and --tlv-model and surviving simulator build records with --simulator-provenance. Download model archives from the recorded TI pages under applicable TI terms; do not commit or redistribute them.",
    modelFilesRedistributed: false,
  },
  tia: {
    dcTransimpedanceOhms: acDc,
    minus3dbBandwidthHz: bandwidth,
    inputEnvelopeVoltsAt3p3: envelope,
    outputNoiseRmsTo2MhzVolts: integrateNoise(2e6),
    photodiodeInputBounds: {
      reverseResistanceOhms: 424000000,
      reverseResistanceBasis: "Sensitivity-assumption placeholder used for finite reverse leakage; not extracted from an SFH 203 datasheet and not a measured bound.",
      inputCapacitancePicofarads: [20, 25],
      inputCapacitanceBasis: "Bench-planning envelope for package, boot, and layout capacitance; physical capacitance is unmeasured.",
    },
    steps,
    sensitivity,
    oppositeRailVariation: rails,
    overload: {
      saturatedMinimumVolts: overloadMinimum,
      recoveryTo1PercentMicroseconds: recoveryTime * 1e6,
      saturationSampleCount: overloadSaturated.length,
      recoveryViolationCount: recoveryViolations.length,
      finalSampleCount: recoveryFinalSamples.length,
      finalSamplesWithinTolerance: recoveryFinalSamplesWithinTolerance,
    },
    startupVoltsAt20Microseconds: nearest(startup, 20e-6)[1],
  },
  comparator: {
    intrinsicHysteresisMillivolts: rising.map((value, index) => (value - falling[index]) * 1000),
    repeatedCrossingsAt100Khz: noisyComparator,
    rails: comparatorRails,
    explicitPinMap: {
      highInputVolts: nearest(pinMap, 1.9e-6)[1],
      lowInputVolts: nearest(pinMap, 3e-6)[1],
    },
    startupOutputVoltsAfter20Microseconds: {
      minimum: Math.min(...comparatorStartup.filter((row) => row[0] >= 20e-6).map((row) => row[1])),
      maximum: Math.max(...comparatorStartup.filter((row) => row[0] >= 20e-6).map((row) => row[1])),
    },
  },
  decision: {
    values: { rfOhms: 47000, cfPicofarads: 2.2, externalHysteresis: "DNP" },
    acceptance: "Model-based bench feasibility only; physical optical current, stability, package orientation, and supply behavior remain unverified.",
  },
};

const gates = [
  [Math.abs(acDc - 47000) < 100, "nominal DC transimpedance"],
  [bandwidth > 1e6, "transimpedance bandwidth above 1 MHz"],
  [steps["1uA"].overshootPercent < 20, "nominal step overshoot below 20 percent"],
  [Number.isFinite(steps["1uA"].settleTo2PercentMicroseconds) && steps["1uA"].settleTo2PercentMicroseconds > 0 &&
    steps["1uA"].settleTo2PercentMicroseconds < 1, "nominal 2 percent settling is finite, positive, and below 1 us"],
  [envelope["23uA"] > 0.05, "23 uA output retains 50 mV above ground at 3.3 V"],
  [overloadSaturated.length > 0, "overload has a bounded saturation interval"],
  [recoveryViolations.length > 0, "overload has measured out-of-tolerance recovery samples"],
  [Number.isFinite(recoveryTime) && recoveryTime > 0 && recoveryTime < 1e-3,
    "overload recovery interval is finite, positive, and below 1 ms"],
  [recoveryFinalSamplesWithinTolerance, "overload final samples remain within 1 percent"],
  [rising.length > 0 && falling.length > 0, "comparator hysteresis transitions found"],
  [Math.abs(nearest(pinMap, 1.9e-6)[1] - 3.279666) < 0.01, "TLV pin mapping: IN+ high drives OUT high"],
  [nearest(pinMap, 3e-6)[1] < 0.01, "TLV pin mapping: IN+ low drives OUT low"],
];
const failures = gates.filter(([passed]) => !passed).map(([, name]) => name);
summary.assertions = { passed: gates.length - failures.length, failed: failures };
if (failures.length) summary.status = "failed";

writeFileSync(resolve(paths.out, "gate-a-summary.json"), `${JSON.stringify(summary, null, 2)}\n`);
console.log(JSON.stringify(summary, null, 2));
if (failures.length) process.exitCode = 1;
