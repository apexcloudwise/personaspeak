#!/usr/bin/env python3
"""Run fake capture CLI entry point using mock toolchain."""

import os
import sys

_repo_root = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..", "..", "..", "..")
)
if _repo_root not in sys.path:
    sys.path.insert(0, _repo_root)

import shutil

from android.scripts.m2_device import cli
from android.scripts.m2_device.adb_harness import CANDIDATE_REPHRASING


def main():
    test_dir = os.path.abspath(
        os.path.join(
            os.path.dirname(__file__),
            "fixtures",
            "scratch_workspace_cli",
        )
    )
    evidence_root = os.path.join(test_dir, "evidence")
    repo_root = os.path.join(test_dir, "repo")
    os.makedirs(evidence_root, exist_ok=True)
    os.makedirs(repo_root, exist_ok=True)

    apk_path = os.path.join(test_dir, "mock_app.apk")
    with open(apk_path, "wb") as f:
        f.write(b"mock_apk_binary")

    import hashlib
    apk_sha256 = hashlib.sha256(b"mock_apk_binary").hexdigest()

    bin_dir = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "fixtures", "bin")
    )
    os.environ["PATH"] = bin_dir + os.pathsep + os.environ.get("PATH", "")
    os.environ["MOCK_COMMANDS_LOG"] = os.path.join(test_dir, "mock_commands.log")
    os.environ["FAKE_ADB_STATE"] = os.path.join(test_dir, "edittext.state")
    os.environ["FAKE_ADB_KEYBOARD"] = os.path.join(test_dir, "keyboard.state")
    os.environ["FAKE_ADB_REPHRASING"] = CANDIDATE_REPHRASING

    argv = [
        "capture",
        "--evidence-root", evidence_root,
        "--repo-root", repo_root,
        "--apk-path", apk_path,
        "--apk-sha256", apk_sha256,
    ]

    try:
        print("Invoking real CLI capture entry point with fake toolchain...")
        rc = cli.main(argv)
        print(f"\nCLI capture completed with rc={rc}")
        sys.exit(rc)
    finally:
        if os.path.exists(test_dir):
            shutil.rmtree(test_dir)


if __name__ == "__main__":
    main()
