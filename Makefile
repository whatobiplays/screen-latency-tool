# Deterministic, offline prototype source checks. Requires Node.js 22+ and make.
# KiCad's native design checks are a separate version-pinned local profile.
NODE ?= node

.PHONY: check check-generated check-structural check-erc-gate check-provenance check-bom check-markdown check-validation-tests check-kicad check-firmware check-android firmware-host-tests firmware-build descriptor-validation capture-candidate-build capture-memory-tests android-test android-build

check: check-generated check-structural check-erc-gate check-provenance check-bom check-markdown check-validation-tests firmware-host-tests

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
	$(NODE) --test scripts/check-markdown.test.mjs scripts/validation.test.mjs scripts/optical-cam.test.mjs

# Requires PlatformIO Core 6.1.18; packages are pinned in firmware/platformio.ini.
PLATFORMIO ?= pio
ifeq ($(strip $(PLATFORMIO_CORE_DIR)),)
PLATFORMIO_CORE_DIR := $(CURDIR)/firmware/.platformio-core
endif
firmware-host-tests:
	python3 firmware/scripts/run_host_tests.py

firmware-build:
	PLATFORMIO_CORE_DIR="$(PLATFORMIO_CORE_DIR)" $(PLATFORMIO) run -d firmware -e teensy41

capture-candidate-build:
	PLATFORMIO_CORE_DIR="$(PLATFORMIO_CORE_DIR)" $(PLATFORMIO) run -d firmware -e teensy41-flexpwm1-candidate
	python3 firmware/scripts/check_capture_memory.py --build-dir firmware/.pio/build/teensy41-flexpwm1-candidate --elf firmware/.pio/build/teensy41-flexpwm1-candidate/firmware.elf

capture-memory-tests:
	python3 firmware/tests/test_capture_memory.py

descriptor-validation: firmware-build
	python3 firmware/scripts/test_compiled_descriptors.py
	python3 firmware/scripts/check_compiled_descriptors.py firmware/.pio/build/teensy41/firmware.elf

check-firmware: firmware-host-tests descriptor-validation capture-candidate-build capture-memory-tests

# Uses the pinned Gradle wrapper and Android/JVM modules.
android-test:
	./android/gradlew -p android test

android-build:
	./android/gradlew -p android assembleDebug

check-android: android-test android-build

# Requires KiCad CLI 10.0.8; copies the project and libraries to a disposable directory.
# A new refilled DRC is not the same evidence as historical non-refill reports.
check-kicad:
	$(NODE) scripts/check-kicad.mjs
