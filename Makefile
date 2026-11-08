# Deterministic, offline prototype source checks. Requires Node.js 22+ and make.
# KiCad's native design checks are a separate version-pinned local profile.
NODE ?= node

.PHONY: check check-generated check-structural check-erc-gate check-provenance check-bom check-markdown check-validation-tests check-kicad

check: check-generated check-structural check-erc-gate check-provenance check-bom check-markdown check-validation-tests

check-generated:
	$(NODE) hardware/prototype/generate.mjs --check

check-structural:
	$(NODE) hardware/prototype/check.mjs

check-erc-gate:
	$(NODE) hardware/prototype/erc-report-gate.mjs --selftest

check-provenance:
	$(NODE) --test hardware/prototype/simulation/model-provenance.test.mjs

check-bom:
	$(NODE) scripts/check-bom.mjs

check-markdown:
	$(NODE) scripts/check-markdown.mjs

check-validation-tests:
	$(NODE) --test scripts/validation.test.mjs

# Requires KiCad CLI 10.0.8; copies the project and libraries to a disposable directory.
# A new refilled DRC is not the same evidence as historical non-refill reports.
check-kicad:
	$(NODE) scripts/check-kicad.mjs
