"""Tests for the CLI module."""

from __future__ import annotations

import json
import os
import tempfile
import unittest
from unittest.mock import MagicMock, patch

from android.scripts.m2_device import cli
from android.scripts.m2_device.records import (
    ApprovalRecord,
    CaptureRecord,
    CommandResult,
    PriorDeviceState,
    VisualReview,
)


class TestCli(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.TemporaryDirectory()
        self.run_dir = self.tmp_dir.name
        self.evidence_root = os.path.join(self.run_dir, "evidence")
        self.repo_root = os.path.join(self.run_dir, "repo")
        os.makedirs(self.evidence_root)
        os.makedirs(self.repo_root)

        # Write dummy APK and files
        self.apk_path = os.path.join(self.run_dir, "test.apk")
        with open(self.apk_path, "wb") as f:
            f.write(b"apk content")

    def tearDown(self):
        self.tmp_dir.cleanup()

    @patch("android.scripts.m2_device.evidence.check_evidence_root")
    @patch("android.scripts.m2_device.cli.Orchestrator")
    @patch("android.scripts.m2_device.cli.AdbHarness")
    def test_capture_happy_path(self, mock_harness_cls, mock_orchestrator_cls, mock_check_root):
        mock_orch = MagicMock()
        mock_orch.terminal = None
        mock_record = CaptureRecord(
            repo_head="abc",
            apk_sha256="def",
            tools=[],
            prior_state=None,
            steps=[],
            restoration=None,
            manifest_digest=None,
            visual_review=VisualReview.PENDING,
        )
        mock_orch.execute.return_value = mock_record
        mock_orchestrator_cls.return_value = mock_orch

        argv = [
            "capture",
            "--evidence-root",
            self.evidence_root,
            "--repo-root",
            self.repo_root,
            "--apk-path",
            self.apk_path,
            "--apk-sha256",
            "mocksha256",
        ]
        rc = cli.main(argv)
        self.assertEqual(rc, 0)

        # Check if capture-record.json was created
        runs = os.listdir(self.evidence_root)
        self.assertEqual(len(runs), 1)
        record_file = os.path.join(self.evidence_root, runs[0], "capture-record.json")
        self.assertTrue(os.path.exists(record_file))

    @patch("android.scripts.m2_device.evidence.check_evidence_root")
    @patch("android.scripts.m2_device.cli.Orchestrator")
    @patch("android.scripts.m2_device.cli.AdbHarness")
    def test_capture_failure(self, mock_harness_cls, mock_orchestrator_cls, mock_check_root):
        mock_orch = MagicMock()
        mock_orch.terminal = "install_failed"
        mock_record = CaptureRecord(
            repo_head="abc",
            apk_sha256="def",
            tools=[],
            prior_state=None,
            steps=[],
            restoration=None,
            manifest_digest=None,
            visual_review=VisualReview.PENDING,
        )
        mock_orch.execute.return_value = mock_record
        mock_orchestrator_cls.return_value = mock_orch

        argv = [
            "capture",
            "--evidence-root",
            self.evidence_root,
            "--repo-root",
            self.repo_root,
            "--apk-path",
            self.apk_path,
            "--apk-sha256",
            "mocksha256",
        ]
        rc = cli.main(argv)
        self.assertEqual(rc, 1)


if __name__ == "__main__":
    unittest.main()
