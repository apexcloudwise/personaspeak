#!/usr/bin/env python3
"""Run fake capture CLI entry point using fully isolated fake toolchain.

Invokes the real CLI as a child process with an isolated PATH containing
only fake tools. Receipts are preserved in the scratch directory, and the
persisted capture record is decoded and summarized on the way out — the
same contract the #62 acceptance matrix checks mechanically.

Any FAKE_ADB_*/FAKE_EMU_*/FAKE_GIT_* knob present in the calling
environment is passed through, so failure variants can be demoed by hand.
"""

import glob
import hashlib
import json
import os
import subprocess
import sys

_repo_root = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..", "..", "..")
)
sys.path.insert(0, _repo_root)

from android.scripts.m2_device.adb_harness import CANDIDATE_REPHRASING
from android.scripts.m2_device.records import CaptureRecord, decode


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

    # Fake AVD fixture tree with honestly computed digests, so the demo
    # exercises the full journey (the same setup the acceptance matrix
    # uses; the validate_fixture verdict carries the fake-only banner).
    fixture_root = os.path.join(test_dir, "avd")
    digests_path = os.path.join(test_dir, "fixture_digests.json")
    digests = {}
    for rel in ("M2_Qual_Fixture.avd/snapshots/m2_pristine/hardware.ini",
                "M2_Qual_Fixture.avd/snapshots/m2_pristine/ram.bin",
                "M2_Qual_Fixture.avd/snapshots/m2_pristine/textures.bin"):
        path = os.path.join(fixture_root, *rel.split("/"))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        content = f"fake-fixture:{rel}".encode()
        with open(path, "wb") as f:
            f.write(content)
        digests[rel] = hashlib.sha256(content).hexdigest()
    with open(digests_path, "w") as f:
        json.dump(digests, f)

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
        "FAKE_ADB_FOCUS": os.path.join(test_dir, "focus.state"),
        "FAKE_ADB_REPHRASING": CANDIDATE_REPHRASING,
        "FAKE_GIT_HEAD": head,
        "PYTHONPATH": _repo_root,
    }
    # Pass through any failure knobs set in the caller's environment.
    for key, value in os.environ.items():
        if key.startswith(("FAKE_ADB_", "FAKE_EMU_", "FAKE_GIT_")):
            env[key] = value
    for k in ("FAKE_ADB_STATE", "FAKE_ADB_KEYBOARD", "FAKE_ADB_FOCUS"):
        with open(env[k], "w") as f:
            f.write("")

    print(f"Invoking real CLI as subprocess with isolated PATH={bin_dir}")
    result = subprocess.run(
        [sys.executable, "-m", "android.scripts.m2_device.cli", "capture",
         "--evidence-root", evidence_root, "--repo-root", repo_root,
         "--apk-path", apk_path, "--apk-sha256", apk_sha256,
         "--fixture-root", fixture_root,
         "--fixture-digests", digests_path],
        env=env, capture_output=True, cwd=_repo_root, timeout=60,
    )
    print(f"\nCLI capture completed with rc={result.returncode}")
    if result.stderr:
        print(f"stderr: {result.stderr.decode()}")
    if result.stdout:
        print(f"stdout: {result.stdout.decode()[:500]}")

    run_dirs = sorted(glob.glob(
        os.path.join(evidence_root, "????????T??????Z")))
    if run_dirs:
        record_path = os.path.join(run_dirs[-1], "capture-record.json")
        if os.path.isfile(record_path):
            with open(record_path, "rb") as f:
                record = decode(f.read())
            if isinstance(record, CaptureRecord):
                failed = [s for s in record.steps
                          if s.cause.value != "completed"]
                print(f"record: {len(record.steps)} steps, "
                      f"{len(failed)} non-completed")
                for s in failed:
                    print(f"  {s.phase}/{s.operation}: {s.cause.value}")
        print(f"Receipts preserved in: {test_dir}")
    else:
        print("No run directory created.")
    sys.exit(result.returncode)


if __name__ == "__main__":
    main()
