// Match external TI model files to the recorded digests before using their release labels.
// The model archives stay outside this repository.
const approvedModels = Object.freeze({
  opa320: Object.freeze({
    sha256: "7b69547828f702638d32e2fddc632defd6bb0d66c66cb1d2d2baca61ff471f35",
    version: "Final 1.6 (model file dated 16AUG2021; Generic, suitable for all analysis types); OPAx320-Q1.LIB from SBOMBW3.ZIP",
  }),
  tlv3501: Object.freeze({
    sha256: "4e4345dc2929ee91a99a0b5437369c32b3817e9c8673a7ae90310d5485a81ba9",
    version: "model revision 2.0, file dated 2021-07-26, generated for PSpice 17.4; SBOMBU5.ZIP",
  }),
});

export function identifyModel(name, suppliedSha256) {
  const approved = approvedModels[name];
  const matches = approved && typeof suppliedSha256 === "string" &&
    approved.sha256 === suppliedSha256.toLowerCase();

  return {
    identityStatus: matches ? "verified" : "unverified",
    version: matches ? approved.version : "UNVERIFIED",
    approvedSha256: approved?.sha256 ?? null,
  };
}

/**
 * Reports whether every supplied model file matches a recorded hash identity.
 * Hash verification does not establish electrical, package, lifecycle, or circuit suitability.
 */
export function modelIdentityVerificationStatus(...identities) {
  return identities.length > 0 && identities.every(({ identityStatus }) => identityStatus === "verified")
    ? "verified"
    : "unverified";
}

const simulatorProvenanceFields = Object.freeze([
  "sourceVersion",
  "sourceIdentity",
  "simulatorSha256",
  "configureArguments",
  "compilerFlags",
  "featureEvidence",
  "buildEvidence",
  "limitations",
]);

const isNonEmptyString = (value) => typeof value === "string" && value.trim().length > 0;
const isStringList = (value) => Array.isArray(value) && value.every(isNonEmptyString);
const isNonEmptyStringList = (value) => value.length > 0 && isStringList(value);
const isFeatureEvidenceList = (value) => Array.isArray(value) && value.length > 0 &&
  value.every((entry) => entry && typeof entry === "object" && !Array.isArray(entry) &&
    isNonEmptyString(entry.feature) && typeof entry.enabled === "boolean");

/** Returns documented simulator provenance fields that are missing or malformed. */
export function missingSimulatorProvenanceFields(provenance, currentSimulatorSha256) {
  if (provenance === null || typeof provenance !== "object" || Array.isArray(provenance)) {
    return [...simulatorProvenanceFields];
  }

  const missing = [];
  if (!isNonEmptyString(provenance.sourceVersion)) missing.push("sourceVersion");
  if (!isNonEmptyString(provenance.sourceIdentity)) missing.push("sourceIdentity");
  if (!isNonEmptyString(provenance.simulatorSha256) || !isNonEmptyString(currentSimulatorSha256) ||
      provenance.simulatorSha256.trim().toLowerCase() !== currentSimulatorSha256.toLowerCase()) {
    missing.push("simulatorSha256");
  }
  if (!isStringList(provenance.configureArguments)) missing.push("configureArguments");
  if (!isNonEmptyString(provenance.compilerFlags)) missing.push("compilerFlags");
  if (!isFeatureEvidenceList(provenance.featureEvidence)) {
    missing.push("featureEvidence");
  }
  if (!Array.isArray(provenance.buildEvidence) || provenance.buildEvidence.length === 0 ||
      provenance.buildEvidence.some((entry) => !entry || typeof entry !== "object" ||
        Array.isArray(entry) || !isNonEmptyString(entry.file) || !Array.isArray(entry.observations) ||
        !isNonEmptyStringList(entry.observations))) {
    missing.push("buildEvidence");
  }
  if (!isStringList(provenance.limitations)) missing.push("limitations");
  return missing;
}
