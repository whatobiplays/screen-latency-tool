"""PlatformIO pre-build hook for the hash-locked Teensy USB overlay."""

import os
import sys
from pathlib import Path

Import("env")

project_dir = Path(env.subst("$PROJECT_DIR"))


def is_within(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


core_dir_value = os.environ.get("PLATFORMIO_CORE_DIR")
if not core_dir_value:
    raise RuntimeError(
        "Set PLATFORMIO_CORE_DIR to an isolated cache; use make check-firmware "
        "or a checkout-local cache"
    )

core_dir = Path(core_dir_value).resolve()
shared_default_core = (Path.home() / ".platformio").resolve()
runner_temp = os.environ.get("RUNNER_TEMP")
allowed_roots = [project_dir.resolve()]
if runner_temp:
    allowed_roots.append(Path(runner_temp).resolve())
if core_dir == shared_default_core or not any(
    is_within(core_dir, root) for root in allowed_roots
):
    raise RuntimeError(
        "PLATFORMIO_CORE_DIR must be checkout-local or runner-local; "
        "refusing to overlay a shared PlatformIO package"
    )

framework_dir = Path(env.PioPlatform().get_package_dir("framework-arduinoteensy")).resolve()
if not is_within(framework_dir, core_dir):
    raise RuntimeError("Teensy framework package is outside PLATFORMIO_CORE_DIR")
sys.path.insert(0, str(project_dir / "scripts"))
from core_overlay import apply  # noqa: E402

apply(framework_dir, project_dir / "core-overlay")
