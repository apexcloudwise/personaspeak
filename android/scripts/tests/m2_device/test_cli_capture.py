"""Test real CLI entry point with fake toolchain to verify CLI capture boundary."""

import os
import sys
import shutil
import unittest
import subprocess


class TestCliCapture(unittest.TestCase):

    def setUp(self):
        # Set up a non-temp workspace directory to pass check_evidence_root
        self.test_dir = os.path.abspath(
            os.path.join(
                os.path.dirname(__file__),
                "fixtures",
                "scratch_workspace"
            )
        )
        self.evidence_root = os.path.join(self.test_dir, "evidence")
        self.repo_root = os.path.join(self.test_dir, "repo")
        os.makedirs(self.evidence_root, exist_ok=True)
        os.makedirs(self.repo_root, exist_ok=True)

        self.apk_path = os.path.join(self.test_dir, "mock_app.apk")
        with open(self.apk_path, "wb") as f:
            f.write(b"mock_apk_binary")

        # Set up PATH to prepend mock binaries only
        self.bin_dir = os.path.abspath(
            os.path.join(
                os.path.dirname(__file__),
                "fixtures",
                "bin"
            )
        )
        # Ensure we have our workspace root in sys.path
        self.workspace_root = os.path.abspath(
            os.path.join(
                os.path.dirname(__file__),
                "../../../../.."
            )
        )

        # Logger for mock commands
        self.log_path = os.path.join(self.test_dir, "mock_commands.log")
        if os.path.exists(self.log_path):
            os.remove(self.log_path)

    def tearDown(self):
        # Clean up files
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)

    def test_real_cli_with_fake_toolchain(self):
        # Prepend bin_dir and include python's directory to resolve shebangs
        isolated_path = self.bin_dir + os.pathsep + os.path.dirname(sys.executable)
        env = {
            "PATH": isolated_path,
            "PYTHONPATH": self.workspace_root,
            "MOCK_COMMANDS_LOG": self.log_path,
        }

        # Keep minimal environment variables like SYSTEMROOT or PATHEXT for subprocess on some platforms
        for k in ["SYSTEMROOT", "PATHEXT", "TMPDIR", "HOME", "USER"]:
            if k in os.environ:
                env[k] = os.environ[k]

        argv = [
            sys.executable,
            "-m", "android.scripts.m2_device.cli",
            "capture",
            "--evidence-root", self.evidence_root,
            "--repo-root", self.repo_root,
            "--apk-path", self.apk_path,
            "--apk-sha256", "mocksha256"
        ]

        res = subprocess.run(
            argv,
            env=env,
            capture_output=True,
            text=True
        )

        print("\n--- CLI Child Process Output ---")
        print(f"Exit Code: {res.returncode}")
        print("Stdout:")
        print(res.stdout)
        print("Stderr:")
        print(res.stderr)
        print("--------------------------------\n")

        # Read and check the fake argv ledger
        self.assertTrue(
            os.path.exists(self.log_path),
            "Command log was not created by fake tools"
        )
        with open(self.log_path) as f:
            commands = [line.strip() for line in f if line.strip()]

        print("--- Mock Commands Ledger ---")
        for cmd in commands:
            print(cmd)
        print("----------------------------\n")

        # Assert no forbidden tools were contacted
        for cmd in commands:
            self.assertFalse(
                cmd.startswith("FORBIDDEN CONTACT:"),
                f"Forbidden tool contact detected: {cmd}"
            )

        # Assert exact tool identities / expected preflight & orchestrator sequence
        self.assertGreater(len(commands), 5)
        self.assertEqual(commands[0], "adb: --version")
        self.assertEqual(commands[1], "emulator: --version")
        self.assertTrue(any("emulator: -avd M2_Qual_Fixture" in c for c in commands))
        self.assertTrue(any("adb: -s emulator-5554 wait-for-device" in c for c in commands))
        self.assertTrue(any("adb: -s emulator-5554 shell getprop sys.boot_completed" in c for c in commands))
        self.assertTrue(any("adb: -s emulator-5554 install -r" in c for c in commands))
        self.assertTrue(any("adb: -s emulator-5554 emu snapshot load m2_pristine" in c for c in commands))

        # Assert expected AttributeError in child process stderr (true production failure)
        self.assertIn(
            "AttributeError: 'AdbHarness' object has no attribute 'capture_evidence'",
            res.stderr
        )
        self.assertEqual(res.returncode, 1)

        # Explicitly fail the test to keep test suite RED (as required by Lease 59-C)
        self.fail(
            f"RED Verification succeeded. Child process failed with 1 as expected."
        )


if __name__ == "__main__":
    unittest.main()
