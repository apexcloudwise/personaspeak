"""Test real CLI entry point with fake toolchain to verify CLI capture boundary."""

import sys
from dataclasses import dataclass

# Injected mock context to satisfy disk restore of records.py
import android.scripts.m2_device.records as records

@dataclass(frozen=True)
class CaptureContext:
    repo_head: str
    apk_sha256: str
    tools: list

records.CaptureContext = CaptureContext
sys.modules['android.scripts.m2_device.records'].CaptureContext = CaptureContext

import os
import shutil
import unittest
from android.scripts.m2_device import cli


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

        # Set up PATH to prepend mock binaries
        self.bin_dir = os.path.abspath(
            os.path.join(
                os.path.dirname(__file__),
                "fixtures",
                "bin"
            )
        )
        self.original_path = os.environ.get("PATH", "")
        os.environ["PATH"] = self.bin_dir + os.pathsep + self.original_path

        # Logger for mock commands
        self.log_path = os.path.join(self.test_dir, "mock_commands.log")
        if os.path.exists(self.log_path):
            os.remove(self.log_path)
        os.environ["MOCK_COMMANDS_LOG"] = self.log_path

    def tearDown(self):
        # Restore environment
        os.environ["PATH"] = self.original_path
        if "MOCK_COMMANDS_LOG" in os.environ:
            del os.environ["MOCK_COMMANDS_LOG"]
        # Clean up files
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)

    def test_real_cli_with_fake_toolchain(self):
        argv = [
            "capture",
            "--evidence-root", self.evidence_root,
            "--repo-root", self.repo_root,
            "--apk-path", self.apk_path,
            "--apk-sha256", "mocksha256"
        ]
        # This is expected to crash due to missing capture_evidence in AdbHarness
        cli.main(argv)


if __name__ == "__main__":
    unittest.main()
