#!/usr/bin/env python3
"""Run fake capture CLI entry point using fully isolated fake toolchain.

Invokes the real CLI as a child process with an isolated PATH containing
only fake tools. Receipts are preserved in the scratch directory.
"""

import hashlib
import os
import subprocess
import sys

_repo_root = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..", "..", "..", "..")
)

from android.scripts.m2_device.adb_harness import CANDIDATE_REPHRASING


def main():
    test_dir = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "fixtures", "scratch_workspace_cli"))
    evidence_root = os.path.join(test_dir, "evidence")
    repo_root = os.path.join(test_dir, "repo")
    os.makedirs(evidence_root, exist_ok=True)
    os.makedirs(repo_root, exist_ok=True)

    for args in (["git", "init"], ["git", "config", "user.email", "t@t"],
                 ["git", "config", "user.name", "T"],
                 ["git", "add", "-A"], ["git", "commit", "-m", "init"]):
        subprocess.run(args, cwd=repo_root, capture_output=True)

    head = subprocess.run(["git", "rev-parse", "HEAD"],
                          cwd=repo_root, capture_output=True).stdout.decode().strip()

    apk_path = os.path.join(test_dir, "mock_app.apk")
    with open(apk_path, "wb") as f:
        f.write(b"mock_apk_binary")
    apk_sha256 = hashlib.sha256(b"mock_apk_binary").hexdigest()

    bin_dir = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "fixtures", "bin"))
    log_path = os.path.join(test_dir, "mock_commands.log")
    python_dir = os.path.dirname(sys.executable)

    env = {
        "PATH": bin_dir + os.pathsep + python_dir,
        "HOME": os.environ.get("HOME", "/tmp"),
        "MOCK_COMMANDS_LOG": log_path,
        "FAKE_ADB_STATE": os.path.join(test_dir, "edittext.state"),
        "FAKE_ADB_KEYBOARD": os.path.join(test_dir, "keyboard.state"),
        "FAKE_ADB_REPHRASING": CANDIDATE_REPHRASING,
        "FAKE_GIT_HEAD": head,
        "PYTHONPATH": _repo_root,
    }
    for k in ("FAKE_ADB_STATE", "FAKE_ADB_KEYBOARD"):
        with open(env[k], "w") as f:
            f.write("")

    print(f"Invoking real CLI as subprocess with isolated PATH={bin_dir}")
    result = subprocess.run(
        [sys.executable, "-m", "android.scripts.m2_device.cli", "capture",
         "--evidence-root", evidence_root, "--repo-root", repo_root,
         "--apk-path", apk_path, "--apk-sha256", apk_sha256],
        env=env, capture_output=True, cwd=_repo_root, timeout=30,
    )
    print(f"\nCLI capture completed with rc={result.returncode}")
    if result.stderr:
        print(f"stderr: {result.stderr.decode()}")
    print(f"Receipts preserved in: {test_dir}")
    sys.exit(result.returncode)


if __name__ == "__main__":
    main()
