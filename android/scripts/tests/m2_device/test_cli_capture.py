"""Test real CLI entry point with fake toolchain — full capture pipeline.

R17: Proves the real CLI end-to-end with an isolated fake-only PATH.
The CLI is invoked as a child process using an absolute Python interpreter.
"""

import json
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

        # Fake AVD fixture tree with honestly computed digests; the
        # fixture transaction in validate_fixture verifies these bytes.
        import hashlib
        self.fixture_root = os.path.join(self.test_dir, "avd")
        self.fixture_digests_path = os.path.join(self.test_dir, "fixture_digests.json")
        digests = {}
        for rel in ("M2_Qual_Fixture.avd/hardware.ini",
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

        self.apk_path = os.path.join(self.test_dir, "mock_app.apk")
        with open(self.apk_path, "wb") as f:
            f.write(b"mock_apk_binary")

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
        import subprocess as sp
        head = sp.run(["git", "rev-parse", "HEAD"],
                       cwd=self.repo_root, capture_output=True).stdout.decode().strip()
        python_dir = os.path.dirname(sys.executable)
        env = {
            "PATH": self.bin_dir + os.pathsep + python_dir,
            "HOME": os.environ.get("HOME", "/tmp"),
            "MOCK_COMMANDS_LOG": self.log_path,
            "FAKE_ADB_STATE": os.path.join(self.test_dir, "edittext.state"),
            "FAKE_ADB_KEYBOARD": os.path.join(self.test_dir, "keyboard.state"),
            "FAKE_ADB_FOCUS": os.path.join(self.test_dir, "focus.state"),
            "FAKE_ADB_REPHRASING": CANDIDATE_REPHRASING,
            "FAKE_GIT_HEAD": head,
            "PYTHONPATH": self.repo_root_abs,
        }
        with open(env["FAKE_ADB_STATE"], "w") as f:
            f.write("")
        with open(env["FAKE_ADB_KEYBOARD"], "w") as f:
            f.write("")
        with open(env["FAKE_ADB_FOCUS"], "w") as f:
            f.write("")
        result = subprocess.run(
            [sys.executable, "-m", "android.scripts.m2_device.cli",
             "capture",
             "--evidence-root", self.evidence_root,
             "--repo-root", self.repo_root,
             "--apk-path", self.apk_path,
             "--apk-sha256", self.apk_sha256,
             "--fixture-root", self.fixture_root,
             "--fixture-digests", self.fixture_digests_path],
            env=env, capture_output=True, cwd=self.repo_root_abs, timeout=30,
        )
        return result

    def test_real_cli_with_fake_toolchain(self):
        result = self._run_cli()
        self.assertEqual(result.returncode, 0,
                         f"CLI failed: {result.stderr.decode()}")

        with open(self.log_path) as f:
            ledger = f.read()

        lines = [l.strip() for l in ledger.splitlines() if l.strip()]
        adb_lines = [l for l in lines if l.startswith("adb:")]
        emu_lines = [l for l in lines if l.startswith("emulator:")]

        self.assertTrue(any("screencap" in l for l in adb_lines),
                        "no screencap in ledger")
        self.assertTrue(any("screenrecord" in l for l in adb_lines),
                        "no screenrecord in ledger")
        self.assertTrue(any("uiautomator dump" in l for l in adb_lines),
                        "no uiautomator dump in ledger")
        self.assertTrue(any("input tap" in l for l in adb_lines),
                        "no input tap in ledger")
        self.assertFalse(any("input text" in l for l in adb_lines),
                         "input text found in ledger — forbidden")
        self.assertFalse(any("FORBIDDEN" in l for l in lines),
                         "FORBIDDEN marker in ledger")

        self.assertTrue(any("-list-avds" in l for l in emu_lines),
                        "no -list-avds in emulator ledger")
        self.assertTrue(any("-avd" in l for l in emu_lines),
                        "no -avd launch in emulator ledger")

        launch_lines = [l for l in emu_lines if "-avd" in l and "-list-avds" not in l]
        for l in launch_lines:
            self.assertIn("M2_Qual_Fixture", l,
                          f"emulator launched with wrong AVD: {l}")

    def test_ledger_phase_order(self):
        import glob as _glob
        import json

        result = self._run_cli()
        self.assertEqual(result.returncode, 0,
                         f"CLI failed: {result.stderr.decode()}")

        run_dirs = sorted(_glob.glob(os.path.join(self.evidence_root, "????????T??????Z")))
        self.assertTrue(run_dirs, "no run directory found under evidence root")
        run_dir = run_dirs[-1]
        record_path = os.path.join(run_dir, "capture-record.json")
        self.assertTrue(os.path.isfile(record_path),
                        f"capture-record.json not found at {record_path}")

        with open(record_path) as f:
            record = json.load(f)

        phases = [s["phase"] for s in record["steps"]]

        # End-to-end mechanical boundary: an injected-digest run's
        # persisted record must carry the fake-only verdict in the
        # validate_fixture step itself — indistinguishable-from-pinned
        # is what the boundary exists to prevent.
        import base64
        vf_steps = [s for s in record["steps"]
                    if s["phase"] == "validate_fixture"]
        self.assertTrue(vf_steps, "validate_fixture step missing")
        vf_stdout = base64.b64decode(vf_steps[0]["result"]["stdout"])
        self.assertIn(b"fake-only", vf_stdout)
        self.assertIn(b"not an accepted-fixture qualification", vf_stdout)

        expected = ["preflight", "emulator_launch", "attach", "install",
                     "journey", "capture", "restore"]
        for i in range(len(expected) - 1):
            a, b = expected[i], expected[i + 1]
            self.assertIn(a, phases, f"phase {a} missing from record")
            self.assertIn(b, phases, f"phase {b} missing from record")
            self.assertLess(
                phases.index(a), phases.index(b),
                f"phase {a} must precede {b} in record",
            )

    def test_adversarial_install_failure(self):
        env_override = {"FAKE_ADB_INSTALL_FAIL": "1"}
        result = self._run_cli_with_override(env_override)
        self.assertNotEqual(result.returncode, 0,
                            "CLI should fail when install fails")
        self.assertIn(b"install", result.stderr.lower() +
                      result.stdout.lower())

    def _run_cli_with_override(self, extra_env):
        import subprocess as sp
        head = sp.run(["git", "rev-parse", "HEAD"],
                       cwd=self.repo_root, capture_output=True).stdout.decode().strip()
        python_dir = os.path.dirname(sys.executable)
        env = {
            "PATH": self.bin_dir + os.pathsep + python_dir,
            "HOME": os.environ.get("HOME", "/tmp"),
            "MOCK_COMMANDS_LOG": self.log_path,
            "FAKE_ADB_STATE": os.path.join(self.test_dir, "edittext.state"),
            "FAKE_ADB_KEYBOARD": os.path.join(self.test_dir, "keyboard.state"),
            "FAKE_ADB_FOCUS": os.path.join(self.test_dir, "focus.state"),
            "FAKE_ADB_REPHRASING": CANDIDATE_REPHRASING,
            "FAKE_GIT_HEAD": head,
            "PYTHONPATH": self.repo_root_abs,
        }
        env.update(extra_env)
        for k in ("FAKE_ADB_STATE", "FAKE_ADB_KEYBOARD", "FAKE_ADB_FOCUS"):
            with open(env[k], "w") as f:
                f.write("")
        return subprocess.run(
            [sys.executable, "-m", "android.scripts.m2_device.cli",
             "capture",
             "--evidence-root", self.evidence_root,
             "--repo-root", self.repo_root,
             "--apk-path", self.apk_path,
             "--apk-sha256", self.apk_sha256,
             "--fixture-root", self.fixture_root,
             "--fixture-digests", self.fixture_digests_path],
            env=env, capture_output=True, cwd=self.repo_root_abs, timeout=30,
        )

    def test_adversarial_fixture_drift(self):
        # Corrupt one pinned snapshot file: the fixture transaction must
        # fail closed before any mutation.
        ram = os.path.join(
            self.fixture_root, "M2_Qual_Fixture.avd",
            "snapshots", "m2_pristine", "ram.bin")
        with open(ram, "ab") as f:
            f.write(b"drift")
        result = self._run_cli()
        self.assertNotEqual(result.returncode, 0,
                            "CLI should fail on fixture digest drift")
        combined = result.stderr.decode().lower()
        self.assertIn("fixture", combined)


if __name__ == "__main__":
    unittest.main()
