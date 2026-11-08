import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, mkdirSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import {
  identifyModel,
  missingSimulatorProvenanceFields,
  modelIdentityVerificationStatus,
} from "./model-provenance.mjs";

const opaSha256 = "7b69547828f702638d32e2fddc632defd6bb0d66c66cb1d2d2baca61ff471f35";
const tlvSha256 = "4e4345dc2929ee91a99a0b5437369c32b3817e9c8673a7ae90310d5485a81ba9";
const gateAPath = fileURLToPath(new URL("./gate-a.mjs", import.meta.url));

function runGateAWithPriorSummaries(outputDirectoryNames, argsFor) {
  const testRoot = mkdtempSync(join(tmpdir(), "gate-a-arguments-"));
  const outputDirectories = outputDirectoryNames.map((name) => join(testRoot, name));
  for (const outputDirectory of outputDirectories) {
    mkdirSync(outputDirectory);
    writeFileSync(join(outputDirectory, "gate-a-summary.json"), JSON.stringify({ status: "passed" }));
  }

  try {
    const result = spawnSync(process.execPath, [gateAPath, ...argsFor(outputDirectories)], { encoding: "utf8" });
    return {
      result,
      remainingSummaries: outputDirectories.map((outputDirectory) =>
        existsSync(join(outputDirectory, "gate-a-summary.json"))),
    };
  } finally {
    rmSync(testRoot, { recursive: true, force: true });
  }
}

test("recognizes the approved OPAx320-Q1 model digest", () => {
  const identity = identifyModel("opa320", opaSha256);

  assert.equal(identity.identityStatus, "verified");
  assert.match(identity.version, /Final 1\.6/);
  assert.equal(identity.approvedSha256, opaSha256);
});

test("marks a mismatched OPAx320-Q1 model digest unverified", () => {
  const identity = identifyModel("opa320", "0".repeat(64));

  assert.equal(identity.identityStatus, "unverified");
  assert.equal(identity.version, "UNVERIFIED");
  assert.equal(identity.approvedSha256, opaSha256);
});

test("recognizes the approved TLV3501 model digest", () => {
  const identity = identifyModel("tlv3501", tlvSha256);

  assert.equal(identity.identityStatus, "verified");
  assert.match(identity.version, /revision 2\.0/);
  assert.equal(identity.approvedSha256, tlvSha256);
});

test("marks a mismatched TLV3501 model digest unverified", () => {
  const identity = identifyModel("tlv3501", "0".repeat(64));

  assert.equal(identity.identityStatus, "unverified");
  assert.equal(identity.version, "UNVERIFIED");
  assert.equal(identity.approvedSha256, tlvSha256);
});

test("verifies supplied model identities only when every digest is approved", () => {
  const verifiedOpa = identifyModel("opa320", opaSha256);
  const verifiedTlv = identifyModel("tlv3501", tlvSha256);
  const unverifiedTlv = identifyModel("tlv3501", "0".repeat(64));

  assert.equal(modelIdentityVerificationStatus(verifiedOpa, verifiedTlv), "verified");
  assert.equal(modelIdentityVerificationStatus(verifiedOpa, unverifiedTlv), "unverified");
  assert.equal(modelIdentityVerificationStatus(), "unverified");
});

const currentSimulatorSha256 = "a".repeat(64);
const completeSimulatorProvenance = {
  sourceVersion: "ngspice-47",
  sourceIdentity: "Homebrew arm64 bottle SHA-256 example",
  simulatorSha256: currentSimulatorSha256,
  configureArguments: [],
  compilerFlags: "Not applicable to the prebuilt bottle",
  featureEvidence: [{ feature: "readline", enabled: true }],
  buildEvidence: [{ file: "receipt.json", observations: ["arm64 bottle metadata"] }],
  limitations: [],
};

test("rejects simulator provenance without the documented build record fields", () => {
  const { buildEvidence } = completeSimulatorProvenance;

  assert.deepEqual(missingSimulatorProvenanceFields({ buildEvidence }, currentSimulatorSha256), [
    "sourceVersion",
    "sourceIdentity",
    "simulatorSha256",
    "configureArguments",
    "compilerFlags",
    "featureEvidence",
    "limitations",
  ]);
});

test("rejects blank identity fields and incomplete build evidence", () => {
  const provenance = {
    ...completeSimulatorProvenance,
    sourceVersion: " ",
    sourceIdentity: "",
    simulatorSha256: "b".repeat(64),
    configureArguments: [""],
    compilerFlags: null,
    featureEvidence: [],
    buildEvidence: [{ file: "receipt.json", observations: [] }],
    limitations: null,
  };

  assert.deepEqual(missingSimulatorProvenanceFields(provenance, currentSimulatorSha256), [
    "sourceVersion",
    "sourceIdentity",
    "simulatorSha256",
    "configureArguments",
    "compilerFlags",
    "featureEvidence",
    "buildEvidence",
    "limitations",
  ]);
});

test("rejects a non-object simulator provenance record", () => {
  assert.deepEqual(missingSimulatorProvenanceFields(null, currentSimulatorSha256), [
    "sourceVersion",
    "sourceIdentity",
    "simulatorSha256",
    "configureArguments",
    "compilerFlags",
    "featureEvidence",
    "buildEvidence",
    "limitations",
  ]);
});

test("accepts complete simulator provenance with explicit empty argument and limitation lists", () => {
  assert.deepEqual(missingSimulatorProvenanceFields(completeSimulatorProvenance, currentSimulatorSha256), []);
});

test("rejects malformed simulator feature evidence entries", () => {
  for (const featureEvidence of [
    [null],
    [{}],
    [{ feature: " ", enabled: true }],
    [{ feature: "readline", enabled: "yes" }],
  ]) {
    const provenance = { ...completeSimulatorProvenance, featureEvidence };
    assert.deepEqual(missingSimulatorProvenanceFields(provenance, currentSimulatorSha256), ["featureEvidence"]);
  }
});

test("rejects blank or non-string simulator build observations", () => {
  for (const observations of [[" "], [42]]) {
    const provenance = {
      ...completeSimulatorProvenance,
      buildEvidence: [{ file: "receipt.json", observations }],
    };
    assert.deepEqual(missingSimulatorProvenanceFields(provenance, currentSimulatorSha256), ["buildEvidence"]);
  }
});

test("rejects non-array simulator build observations", () => {
  const provenance = {
    ...completeSimulatorProvenance,
    buildEvidence: [{ file: "receipt.json", observations: "arm64 bottle metadata" }],
  };

  assert.deepEqual(missingSimulatorProvenanceFields(provenance, currentSimulatorSha256), ["buildEvidence"]);
});

test("rejects simulator provenance bound to a different ngspice binary", () => {
  const provenance = {
    ...completeSimulatorProvenance,
    simulatorSha256: "b".repeat(64),
  };

  assert.deepEqual(missingSimulatorProvenanceFields(provenance, currentSimulatorSha256), [
    "simulatorSha256",
  ]);
});

test("removes a previous Gate A summary before validating input files", () => {
  const testRoot = mkdtempSync(join(tmpdir(), "gate-a-summary-"));
  const outDir = join(testRoot, "out");
  mkdirSync(outDir);
  const summaryPath = join(outDir, "gate-a-summary.json");
  writeFileSync(summaryPath, JSON.stringify({ status: "passed" }));
  const missingInput = join(testRoot, "missing-input");
  const gateAPath = fileURLToPath(new URL("./gate-a.mjs", import.meta.url));

  try {
    const result = spawnSync(process.execPath, [
      gateAPath,
      "--opa-model", missingInput,
      "--tlv-model", missingInput,
      "--out-dir", outDir,
      "--simulator-provenance", missingInput,
      "--ngspice", missingInput,
    ], { encoding: "utf8" });

    assert.ifError(result.error);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Required file does not exist:/);
    assert.doesNotMatch(result.stderr, /Arguments must be --name value pairs/);
    assert.equal(existsSync(summaryPath), false);
  } finally {
    rmSync(testRoot, { recursive: true, force: true });
  }
});

test("removes every identifiable prior summary before malformed Gate A arguments fail", async (t) => {
  const cases = [
    {
      name: "dangling option after --out-dir",
      outputDirectories: ["out"],
      args: ([out]) => ["--out-dir", out, "--opa-model"],
    },
    {
      name: "does not infer --out-dir from an option-looking model value",
      outputDirectories: ["out"],
      args: ([out]) => ["--opa-model", "--out-dir", out],
      expectedRemainingSummaries: [true],
    },
    {
      name: "malformed pair before --out-dir",
      outputDirectories: ["out"],
      args: ([out]) => ["bare-token", "value", "--out-dir", out],
    },
    {
      name: "malformed pair after --out-dir",
      outputDirectories: ["out"],
      args: ([out]) => ["--out-dir", out, "bare-token", "value"],
    },
    {
      name: "duplicate output directories",
      outputDirectories: ["first", "second"],
      args: ([first, second]) => ["--out-dir", first, "--out-dir", second],
    },
    {
      name: "does not infer an output directory from an option-looking --out-dir value",
      outputDirectories: ["out"],
      args: ([out]) => ["--out-dir", "--out-dir", out],
      expectedRemainingSummaries: [true],
    },
  ];

  for (const invocation of cases) {
    await t.test(invocation.name, () => {
      const { result, remainingSummaries } = runGateAWithPriorSummaries(
        invocation.outputDirectories,
        invocation.args,
      );

      assert.ifError(result.error);
      assert.notEqual(result.status, 0);
      assert.match(result.stderr, /Arguments must be --name value pairs|Duplicate --out-dir/);
      assert.deepEqual(
        remainingSummaries,
        invocation.expectedRemainingSummaries ?? invocation.outputDirectories.map(() => false),
      );
    });
  }
});

test("removes a previous Gate A summary when a required option is omitted", () => {
  const testRoot = mkdtempSync(join(tmpdir(), "gate-a-missing-option-"));
  const outDir = join(testRoot, "out");
  mkdirSync(outDir);
  const summaryPath = join(outDir, "gate-a-summary.json");
  writeFileSync(summaryPath, JSON.stringify({ status: "passed" }));
  const gateAPath = fileURLToPath(new URL("./gate-a.mjs", import.meta.url));

  try {
    const result = spawnSync(process.execPath, [gateAPath, "--out-dir", outDir], { encoding: "utf8" });

    assert.ifError(result.error);
    assert.notEqual(result.status, 0);
    assert.equal(existsSync(summaryPath), false);
  } finally {
    rmSync(testRoot, { recursive: true, force: true });
  }
});

test("does not remove a summary through a symlinked output directory", () => {
  const testRoot = mkdtempSync(join(tmpdir(), "gate-a-symlink-output-"));
  const targetDirectory = join(testRoot, "target");
  const linkedOutputDirectory = join(testRoot, "linked-output");
  mkdirSync(targetDirectory);
  symlinkSync(targetDirectory, linkedOutputDirectory, "dir");
  const summaryPath = join(targetDirectory, "gate-a-summary.json");
  writeFileSync(summaryPath, JSON.stringify({ status: "passed" }));

  try {
    const result = spawnSync(process.execPath, [
      gateAPath,
      "--out-dir", linkedOutputDirectory,
      "--opa-model",
    ], { encoding: "utf8" });

    assert.ifError(result.error);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Arguments must be --name value pairs/);
    assert.equal(existsSync(summaryPath), true);
  } finally {
    rmSync(testRoot, { recursive: true, force: true });
  }
});
