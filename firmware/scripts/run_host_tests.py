"""Compile and execute deterministic firmware state-machine tests on the host."""

from __future__ import annotations

import os
import re
import shlex
import subprocess
import tempfile
from pathlib import Path


FIRMWARE_DIR = Path(__file__).resolve().parents[1]


def verify_replay_epoch_reset_policy() -> None:
    """Require request replay state to reset only after a confirmed detach."""
    source = (FIRMWARE_DIR / "src" / "main.cpp").read_text(encoding="utf-8")
    reset_call = "protocol.resetSession();"
    if source.count(reset_call) != 1:
        raise AssertionError(
            "protocol replay state must have one reset call, on confirmed detach"
        )

    update_match = re.search(
        r"void updateUsbState\(\)\s*\{(?P<body>[\s\S]*?)\n\}", source
    )
    if update_match is None:
        raise AssertionError("could not locate updateUsbState()")
    if not re.search(
        r"const bool connected\s*=\s*\(portsc\s*&\s*USB_PORTSC1_CCS\)\s*!=\s*0\s*;",
        update_match.group("body"),
    ):
        raise AssertionError(
            "request epoch must use CCS connection status independently of PE"
        )
    if not re.search(
        r"const bool attached\s*=\s*connected\s*&&\s*"
        r"\(portsc\s*&\s*USB_PORTSC1_PE\)\s*!=\s*0\s*;",
        update_match.group("body"),
    ):
        raise AssertionError("USB endpoint use must still require PE")
    detach_match = re.search(
        r"if\s*\(!connected\)\s*\{(?P<body>[\s\S]*?)\n  \}",
        update_match.group("body"),
    )
    if detach_match is None or reset_call not in detach_match.group("body"):
        raise AssertionError(
            "protocol replay state must reset only when CCS confirms disconnect"
        )


def main() -> int:
    verify_replay_epoch_reset_policy()
    compiler = shlex.split(os.environ.get("CXX", "c++"))
    if not compiler:
        raise RuntimeError("CXX must name a C++17 compiler")

    with tempfile.TemporaryDirectory(prefix="slt-firmware-host-") as temporary:
        executable = Path(temporary) / "test_firmware"
        command = [
            *compiler,
            "-std=c++17",
            "-O2",
            "-Wall",
            "-Wextra",
            "-Werror",
            "-pedantic",
            "-DSLT_HOST_TEST",
            "-Iinclude",
            "src/pulse_engine.cpp",
            "src/protocol.cpp",
            "src/serial_number.cpp",
            "tests/host/test_firmware.cpp",
            "-o",
            str(executable),
        ]
        subprocess.run(command, cwd=FIRMWARE_DIR, check=True)
        subprocess.run([str(executable)], cwd=FIRMWARE_DIR, check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
