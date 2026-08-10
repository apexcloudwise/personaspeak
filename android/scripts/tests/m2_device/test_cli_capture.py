"""Test real CLI entry point with fake toolchain — full capture pipeline.

R17: Proves the real CLI end-to-end with an isolated fake-only PATH.
The CLI is invoked as a child process using an absolute Python interpreter.
"""

import os
import shutil
import subprocess
import sys
import unittest

from android.scripts.m2_device.adb_harness import CANDIDATE_REPHRASING


class TestCliCapture(unittest.TestCase):

    def setUp(self):
        self.test_dir = os.path.abspath(
            os.path.join(os.path.dirname(__file__), "fixtures", "scratch_workspace"))
        self.evidence_root = os.path.join(self.test_dir, "evidence")
        self.repo_root = os.path.join(self.test_dir, "repo")
        os.makedirs(self.evidence_root, exist_ok=True)
        os.makedirs(self.repo_root, exist_ok=True)

        for args in (["git", "init"], ["git", "config", "user.email", "t@t"],
                      ["git", "config", "user.name", "T"], ["git", "add", "-A"],
                      ["git", "commit", "-m", "init"]):
            subprocess.run(args, cwd=self.repo_root, capture_output=True)

        self.apk_path = os.path.join(self.test_dir, "mock_app.apk")
        with open(self.apk_path, "wb") as f:
            f.write(b"mock_apk_binary")

        import hashlib
        self.apk_sha256 = hashlib.sha256(b"mock_apk_binary").hexdigest()

        self.bin_dir = os.path.abspath(
            os.path.join(os.path.dirname(__file__), "fixtures", "bin"))

        self.log_path = os.path.join(self.test_dir, "mock_commands.log")
        if os.path.exists(self.log_path):
            os.remove(self.log_path)

        self.repo_root_abs = os.path.abspath(
            os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))

    def tearDown(self):
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)

    def _run_cli(self):
        env = {
            "PATH": self.bin_dir + os.pathsep + os.environ.get("PATH", ""),
            "HOME": os.environ.get("HOME", "/tmp"),
            "MOCK_COMMANDS_LOG": self.log_path,
            "FAKE_ADB_STATE": os.path.join(self.test_dir, "edittext.state"),
            "FAKE_ADB_KEYBOARD": os.path.join(self.test_dir, "keyboard.state"),
            "FAKE_ADB_REPHRASING": CANDIDATE_REPHRASING,
            "PYTHONPATH": self.repo_root_abs,
        }
        with open(env["FAKE_ADB_STATE"], "w") as f:
            f.write("")
        with open(env["FAKE_ADB_KEYBOARD"], "w") as f:
            f.write("")
        result = subprocess.run(
            [sys.executable, "-m", "android.scripts.m2_device.cli",
             "capture",
             "--evidence-root", self.evidence_root,
             "--repo-root", self.repo_root,
             "--apk-path", self.apk_path,
             "--apk-sha256", self.apk_sha256],
            env=env, capture_output=True, cwd=self.repo_root_abs, timeout=30,
        )
        return result

    def test_real_cli_with_fake_toolchain(self):
        result = self._run_cli()
        self.assertEqual(result.returncode, 0,
                         f"CLI failed: {result.stderr.decode()}")

        with open(self.log_path) as f:
            ledger = f.read()

        self.assertIn("screencap", ledger)
        self.assertIn("screenrecord", ledger)
        self.assertIn("uiautomator dump", ledger)
        self.assertIn("input tap", ledger)
        self.assertNotIn("input text", ledger)
        self.assertNotIn("FORBIDDEN", ledger)

    def test_ledger_phase_order(self):
        result = self._run_cli()
        self.assertEqual(result.returncode, 0,
                         f"CLI failed: {result.stderr.decode()}")

        with open(self.log_path) as f:
            lines = [l.strip() for l in f if l.strip()]

        phases = []
        for line in lines:
            if line.startswith("emulator:") and "-list-avds" in line:
                phases.append("preflight")
            elif line.startswith("emulator:") and "-avd" in line:
                phases.append("launch")
            elif "wait-for-device" in line:
                phases.append("attach")
            elif "install" in line and "pull" not in line:
                phases.append("install")
            elif "dumpsys" in line:
                phases.append("verify_package")
            elif "am start" in line:
                phases.append("journey_start")
            elif "screencap" in line:
                phases.append("evidence_capture")
            elif "screenrecord" in line:
                phases.append("evidence_video")
            elif "snapshot load" in line:
                phases.append("restore")

        expected = ["preflight", "launch", "attach", "install", "verify_package",
                     "journey_start", "evidence_capture", "restore"]
        for i in range(len(expected) - 1):
            a, b = expected[i], expected[i + 1]
            self.assertIn(a, phases, f"phase {a} missing from ledger")
            self.assertIn(b, phases, f"phase {b} missing from ledger")
            self.assertLess(
                phases.index(a), phases.index(b),
                f"phase {a} must precede {b} in ledger",
            )


if __name__ == "__main__":
    unittest.main()
