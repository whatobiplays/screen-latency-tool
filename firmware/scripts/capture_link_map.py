"""Write a GNU linker map beside each PlatformIO firmware image."""

from pathlib import Path
import subprocess


Import("env")

repository_root = Path(env.subst("$PROJECT_DIR")).resolve().parent
source_status = subprocess.run(
    ["git", "status", "--porcelain", "--untracked-files=normal"],
    cwd=repository_root,
    check=True,
    capture_output=True,
    text=True,
).stdout
commit_prefix = "0" * 16
if not source_status.strip():
    commit = subprocess.run(
        ["git", "rev-parse", "--verify", "HEAD^{commit}"],
        cwd=repository_root,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    if len(commit) < len(commit_prefix):
        raise RuntimeError("could not read a full source commit identifier")
    commit_prefix = commit[: len(commit_prefix)]

env.Append(
    CPPDEFINES=[
        ("SLT_BUILD_COMMIT_PREFIX_0", int(commit_prefix[:8], 16)),
        ("SLT_BUILD_COMMIT_PREFIX_1", int(commit_prefix[8:], 16)),
    ]
)

map_path = Path(env.subst("$BUILD_DIR")) / "firmware.map"
env.Append(LINKFLAGS=[f"-Wl,-Map,{map_path}"])
