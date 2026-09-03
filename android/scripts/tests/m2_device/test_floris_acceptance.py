"""Fake-only acceptance test for the Floris second-host journey (P2).

Drives the REAL CLI as a child process with the isolated fake
toolchain and FAKE_ADB_HOST=floris: the full capture pipeline —
fixture transaction, install identity gate, six journey sessions
(idle/cancel, review/apply, dismiss, stale, composing, settings
surface), evidence capture, snapshot restore, and release — must
complete green with exactly the floris canonical artifact set.

The composing-bug variant replays the pre-fix failure shape (the live
composing word surviving the replace) through the fake and asserts the
journey CATCHES it via the editor-text bridge: the ADR-0003 regression
contract, executed device-free.
"""

import hashlib
import json
import os
import shutil
import subprocess
import sys
import unittest

from android.scripts.m2_device import evidence
from android.scripts.m2_device.records import CaptureRecord, TerminalCause, decode

HERE = os.path.abspath(os.path.dirname(__file__))
BIN_DIR = os.path.join(HERE, "fixtures", "bin")
REPO_ROOT_ABS = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))


class TestFlorisAcceptance(unittest.TestCase):

    def setUp(self):
        self.test_dir = os.path.abspath(
            os.path.join(HERE, "fixtures", "scratch_workspace_floris"))
        self.evidence_root = os.path.join(self.test_dir, "evidence")
        self.repo_root = os.path.join(self.test_dir, "repo")
        os.makedirs(self.evidence_root, exist_ok=True)
        os.makedirs(self.repo_root, exist_ok=True)

        self.fixture_root = os.path.join(self.test_dir, "avd")
        self.fixture_digests_path = os.path.join(
            self.test_dir, "fixture_digests.json")
        digests = {}
        for rel in ("M2_Qual_Fixture.avd/snapshots/m2_pristine/hardware.ini",
                    "M2_Qual_Fixture.avd/snapshots/m2_pristine/ram.bin",
                    "M2_Qual_Fixture.avd/snapshots/m2_pristine/textures.bin"):
            path = os.path.join(self.fixture_root, *rel.split("/"))
            os.makedirs(os.path.dirname(path), exist_ok=True)
            content = f"fake-fixture:{rel}".encode()
            with open(path, "wb") as f:
                f.write(content)
            digests[rel] = hashlib.sha256(content).hexdigest()
        with open(self.fixture_digests_path, "w") as f:
            json.dump(digests, f)

        for args in (["git", "init"], ["git", "config", "user.email", "t@t"],
                     ["git", "config", "user.name", "T"], ["git", "add", "-A"],
                     ["git", "commit", "-m", "init"]):
            subprocess.run(args, cwd=self.repo_root, capture_output=True)

        self.apk_path = os.path.join(self.test_dir, "mock_floris.apk")
        with open(self.apk_path, "wb") as f:
            f.write(b"mock_floris_apk_binary")
        self.apk_sha256 = hashlib.sha256(b"mock_floris_apk_binary").hexdigest()

        self.state_files = {
            "FAKE_ADB_STATE": os.path.join(self.test_dir, "edittext.state"),
            "FAKE_ADB_KEYBOARD": os.path.join(self.test_dir, "keyboard.state"),
            "FAKE_ADB_FOCUS": os.path.join(self.test_dir, "focus.state"),
            "FAKE_ADB_SCREEN": os.path.join(self.test_dir, "screen.state"),
            "FAKE_ADB_IME": os.path.join(self.test_dir, "ime.state"),
            "FAKE_ADB_CANDIDATE_SOURCE": os.path.join(
                self.test_dir, "candidate_source.state"),
            "FAKE_ADB_SHIFT": os.path.join(self.test_dir, "shift.state"),
            "FAKE_ADB_BACK": os.path.join(self.test_dir, "back.state"),
            "FAKE_ADB_BOOT_POLLS_LEFT": os.path.join(
                self.test_dir, "boot_polls_left.state"),
        }

    def tearDown(self):
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)

    def _run_cli(self, extra_env=None):
        head = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=self.repo_root, capture_output=True).stdout.decode().strip()
        python_dir = os.path.dirname(sys.executable)
        env = {
            "PATH": BIN_DIR + os.pathsep + python_dir,
            "HOME": os.environ.get("HOME", "/tmp"),
            "PYTHONPATH": REPO_ROOT_ABS,
            "FAKE_GIT_HEAD": head,
            "FAKE_ADB_HOST": "floris",
            **self.state_files,
        }
        for path in self.state_files.values():
            with open(path, "w") as f:
                f.write("")
        if extra_env:
            env.update(extra_env)
        return subprocess.run(
            [sys.executable, "-m", "android.scripts.m2_device.cli",
             "capture",
             "--host", "floris",
             "--evidence-root", self.evidence_root,
             "--repo-root", self.repo_root,
             "--apk-path", self.apk_path,
             "--apk-sha256", self.apk_sha256,
             "--fixture-root", self.fixture_root,
             "--fixture-digests", self.fixture_digests_path],
            env=env, capture_output=True, cwd=REPO_ROOT_ABS, timeout=60,
        )

    def _latest_run_dir(self):
        runs = sorted(os.listdir(self.evidence_root))
        self.assertTrue(runs, "no evidence run produced")
        return os.path.join(self.evidence_root, runs[-1])

    def test_floris_journey_completes_green_with_canonical_set(self):
        result = self._run_cli()
        self.assertEqual(
            result.returncode, 0,
            f"CLI failed: {result.stderr.decode()}")

        run_dir = self._latest_run_dir()
        with open(os.path.join(run_dir, "capture-record.json"), "rb") as f:
            record = decode(f.read())
        self.assertIsInstance(record, CaptureRecord)
        failed = [s for s in record.steps
                  if s.cause != TerminalCause.COMPLETED]
        self.assertEqual(failed, [], f"non-completed steps: {failed}")

        phases = [s.phase for s in record.steps]
        for phase in ("install", "journey", "capture", "restore",
                      "verify_restore", "release_emulator", "verify_release"):
            self.assertIn(phase, phases)

        # The floris host's identity rode the install gate.
        install = [s for s in record.steps if s.phase == "install"][0]
        self.assertIn(
            b"installed and identity verified",
            install.result.stdout.lower())

        # The six sessions' signature steps exist and completed.
        ops = [s.operation for s in record.steps if s.phase == "journey"]
        for op in ("rewrite_and_cancel", "apply_rephrasing",
                   "dismiss_rephrasing", "apply_stale",
                   "apply_composing", "tap_settings_button",
                   "verify_floris_settings"):
            self.assertIn(op, ops)

        # The manifest is exactly the floris canonical set.
        with open(os.path.join(run_dir, "manifest.json")) as f:
            manifest = json.load(f)
        evidence.enforce_canonical_set(
            set(manifest), evidence.FLORIS_CANONICAL_ARTIFACTS)

    def test_composing_bug_is_caught_by_the_editor_text_bridge(self):
        result = self._run_cli(
            extra_env={"FAKE_FLORIS_COMPOSING_BUG": "1"})
        self.assertEqual(result.returncode, 1)
        self.assertIn("qualification failed", result.stderr.decode())

        run_dir = self._latest_run_dir()
        with open(os.path.join(run_dir, "capture-record.json"), "rb") as f:
            record = decode(f.read())
        failed = [s for s in record.steps
                  if s.cause != TerminalCause.COMPLETED]
        self.assertTrue(failed, "expected a failed step under the bug knob")
        # The composing readback — the ADR-0003 regression signal — is
        # the step that fails, with the remnant visible in its record.
        self.assertEqual(failed[0].phase, "journey")
        self.assertIn("after_composing", failed[0].operation)


if __name__ == "__main__":
    unittest.main()
