"""Tests for evidence validation, privacy scans, media checks, and CLI."""

import io
import os
import struct
import sys
import tempfile
import unittest
import zlib
from contextlib import redirect_stdout

from android.scripts.m2_device import cli, evidence
from android.scripts.m2_device.records import (
    ApprovalRecord, CaptureRecord, CommandResult, FinalReceipt,
    PriorDeviceState, StepRecord, TerminalCause, ToolIdentity,
    VisualReview, decode, encode, record_digest,
)


def _png(width=1, height=1):
    sig = b"\x89PNG\r\n\x1a\n"
    def chunk(ctype, data):
        c = ctype + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", b"") + chunk(b"IEND", b"")


def _mp4():
    def box(btype, data=b""):
        return struct.pack(">I", len(data) + 8) + btype + data
    return box(b"ftyp", b"isom\x00\x00\x02\x00isomiso2") + box(b"mdat", b"\x00" * 4)


class TestPrivacyScan(unittest.TestCase):
    def test_clean_text(self):
        self.assertEqual(evidence.scan_text(b"hello world"), [])

    def test_finds_api_key(self):
        findings = evidence.scan_text(b"api_key=sk-1234567890abcdef")
        self.assertTrue(findings)

    def test_finds_password(self):
        findings = evidence.scan_text(b"password=secretvalue123")
        self.assertTrue(findings)

    def test_finds_google_key(self):
        findings = evidence.scan_text(b"AIzaSyA1234567890ABCDEFGHIJKLMNOPQRSTUVWXY")
        self.assertTrue(findings)

    def test_clean_directory(self):
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "log.txt"), "w") as f:
                f.write("just normal log text\n")
            self.assertTrue(evidence.scan_directory(d))

    def test_dirty_directory(self):
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "config.txt"), "w") as f:
                f.write("api_key=sk-1234567890abcdef\n")
            self.assertFalse(evidence.scan_directory(d))

    def test_unreadable_file_fails_closed(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "unreadable.txt")
            with open(path, "w") as f:
                f.write("clean")
            os.chmod(path, 0o000)
            try:
                self.assertFalse(evidence.scan_directory(d))
            finally:
                os.chmod(path, 0o644)


class TestPNGValidation(unittest.TestCase):
    def test_valid_png(self):
        self.assertTrue(evidence.validate_png(_png()))

    def test_bad_signature(self):
        self.assertFalse(evidence.validate_png(b"NOTPNG" + _png()[6:]))

    def test_truncated(self):
        self.assertFalse(evidence.validate_png(_png()[:-10]))

    def test_empty(self):
        self.assertFalse(evidence.validate_png(b""))

    def test_crc_corruption(self):
        data = bytearray(_png())
        data[-5] ^= 0xFF
        self.assertFalse(evidence.validate_png(bytes(data)))


class TestMP4Validation(unittest.TestCase):
    def test_valid_mp4(self):
        self.assertTrue(evidence.validate_mp4(_mp4()))

    def test_missing_ftyp(self):
        data = struct.pack(">I", 12) + b"mdat" + b"\x00" * 4
        self.assertFalse(evidence.validate_mp4(data))

    def test_truncated(self):
        self.assertFalse(evidence.validate_mp4(_mp4()[:-2]))

    def test_empty(self):
        self.assertFalse(evidence.validate_mp4(b""))


class TestManifest(unittest.TestCase):
    def test_build_manifest(self):
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "a.txt"), "w") as f:
                f.write("hello")
            with open(os.path.join(d, "b.txt"), "w") as f:
                f.write("world")
            m = evidence.build_manifest(d)
            self.assertIn("a.txt", m)
            self.assertIn("b.txt", m)
            self.assertEqual(len(m["a.txt"]), 64)

    def test_manifest_digest_stable(self):
        m = {"a": "abc", "b": "def"}
        self.assertEqual(
            evidence.manifest_digest(m),
            evidence.manifest_digest(dict(reversed(list(m.items())))),
        )

    def test_manifest_rejects_symlink(self):
        with tempfile.TemporaryDirectory() as d:
            target = os.path.join(d, "real.txt")
            with open(target, "w") as f:
                f.write("data")
            link = os.path.join(d, "link.txt")
            os.symlink(target, link)
            with self.assertRaises(ValueError):
                evidence.build_manifest(d)

    def test_manifest_rejects_path_escape(self):
        with tempfile.TemporaryDirectory() as d:
            outside = os.path.join(os.path.dirname(d), "outside.txt")
            with open(outside, "w") as f:
                f.write("escaped")
            link = os.path.join(d, "escape.txt")
            try:
                os.symlink(outside, link)
                with self.assertRaises(ValueError):
                    evidence.build_manifest(d)
            finally:
                os.remove(outside)


class TestEvidenceRoot(unittest.TestCase):
    def test_rejects_repo_path(self):
        with self.assertRaises(ValueError):
            evidence.check_evidence_root("/repo", "/repo")

    def test_rejects_repo_subpath(self):
        with self.assertRaises(ValueError):
            evidence.check_evidence_root("/repo/evidence", "/repo")

    def test_rejects_temp(self):
        import tempfile
        with self.assertRaises(ValueError):
            evidence.check_evidence_root(tempfile.gettempdir(), "/repo")

    def test_rejects_vartmp(self):
        with self.assertRaises(ValueError):
            evidence.check_evidence_root("/var/tmp", "/repo")

    def test_resolves_symlink(self):
        with tempfile.TemporaryDirectory() as repo:
            link = os.path.join(repo, "link_to_repo")
            os.symlink(repo, link)
            with self.assertRaises(ValueError):
                evidence.check_evidence_root(link, repo)

    def test_accepts_external(self):
        evidence.check_evidence_root("/opt/evidence", "/repo")


class TestFinalize(unittest.TestCase):
    def _capture(self):
        return CaptureRecord(
            repo_head="abc", apk_sha256="def", tools=[],
            prior_state=None, steps=[], restoration=None, manifest_digest=None,
        )

    def _make_approval(self, cap, man, decision=VisualReview.APPROVED):
        return ApprovalRecord(
            reviewer="reviewer", capture_digest=record_digest(cap),
            manifest_digest=evidence.manifest_digest(man), decision=decision,
            approved_utc="2026-08-06T14:00:00Z",
        )

    def test_successful_finalize(self):
        cap = self._capture()
        with tempfile.TemporaryDirectory() as d:
            import struct, zlib
            sig = b'\x89PNG\r\n\x1a\n'
            ihdr_data = struct.pack('>IIBBBBB', 1, 1, 8, 2, 0, 0, 0)
            ihdr = struct.pack('>I', 13) + b'IHDR' + ihdr_data + struct.pack('>I', zlib.crc32(b'IHDR' + ihdr_data) & 0xFFFFFFFF)
            comp = zlib.compress(b'\x00\xff\x00\x00')
            idat = struct.pack('>I', len(comp)) + b'IDAT' + comp + struct.pack('>I', zlib.crc32(b'IDAT' + comp) & 0xFFFFFFFF)
            iend = struct.pack('>I', 0) + b'IEND' + struct.pack('>I', zlib.crc32(b'IEND') & 0xFFFFFFFF)
            png = sig + ihdr + idat + iend
            with open(os.path.join(d, "screenshot.png"), "wb") as f:
                f.write(png)
            import hashlib
            man = {"screenshot.png": hashlib.sha256(png).hexdigest()}
            appr = self._make_approval(cap, man)
            with open(os.path.join(d, "log.txt"), "w") as f:
                f.write("clean log\n")
            receipt = evidence.finalize(
                cap, appr, man, d,
                restoration_verdict="verified", counts={"screenshots": 7},
                evidence_commit="sha", artifacts=man,
            )
        self.assertTrue(receipt.privacy_ok)
        self.assertTrue(receipt.media_ok)

    def test_drift_blocks_finalize(self):
        cap = self._capture()
        man = {"x": "y"}
        appr = ApprovalRecord(
            reviewer="r", capture_digest="WRONG",
            manifest_digest=evidence.manifest_digest(man), decision=VisualReview.APPROVED,
            approved_utc="2026-08-06T14:00:00Z",
        )
        with self.assertRaises(ValueError):
            evidence.finalize(
                cap, appr, man, "/nonexistent",
                restoration_verdict="verified", counts={},
                evidence_commit="", artifacts={},
            )

    def test_manifest_drift_blocks_finalize(self):
        cap = self._capture()
        appr = ApprovalRecord(
            reviewer="r", capture_digest=record_digest(cap),
            manifest_digest="WRONG", decision=VisualReview.APPROVED,
            approved_utc="2026-08-06T14:00:00Z",
        )
        with self.assertRaises(ValueError):
            evidence.finalize(
                cap, appr, {"x": "y"}, "/nonexistent",
                restoration_verdict="verified", counts={},
                evidence_commit="", artifacts={},
            )

    def test_rejected_approval_blocks_finalize(self):
        cap = self._capture()
        man = {"screenshot.png": "abc"}
        appr = self._make_approval(cap, man, decision=VisualReview.REJECTED)
        with self.assertRaises(ValueError):
            evidence.finalize(
                cap, appr, man, "/nonexistent",
                restoration_verdict="verified", counts={},
                evidence_commit="", artifacts={},
            )

    def test_finalize_detects_privacy_violation(self):
        cap = self._capture()
        man = {"log.txt": "abc"}
        appr = self._make_approval(cap, man)
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "log.txt"), "w") as f:
                f.write("api_key=sk-1234567890abcdef\n")
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(
                    cap, appr, man, d,
                    restoration_verdict="verified", counts={},
                    evidence_commit="", artifacts=man,
                )
            self.assertIn("privacy", str(cm.exception))

    def test_empty_manifest_media_fails_closed(self):
        cap = self._capture()
        man = {"log.txt": "abc"}
        appr = self._make_approval(cap, man)
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "log.txt"), "w") as f:
                f.write("clean text\n")
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(
                    cap, appr, man, d,
                    restoration_verdict="verified", counts={},
                    evidence_commit="", artifacts={},
                )
            self.assertIn("no media", str(cm.exception))


class TestCLI(unittest.TestCase):
    def test_parser_capture(self):
        p = cli.build_parser()
        args = p.parse_args(["capture", "--evidence-root", "/opt/e",
                            "--repo-root", "/repo", "--apk-path", "/opt/apk",
                            "--apk-sha256", "abc"])
        self.assertEqual(args.evidence_root, "/opt/e")
        self.assertEqual(args.apk_path, "/opt/apk")

    def test_parser_finalize(self):
        p = cli.build_parser()
        args = p.parse_args(["finalize", "--capture-record", "cap.json",
                            "--approval", "appr.json", "--manifest", "man.json",
                            "--run-dir", "/run"])
        self.assertEqual(args.capture_record, "cap.json")

    def test_cli_approve_round_trip(self):
        cap = CaptureRecord(
            repo_head="a", apk_sha256="b", tools=[],
            prior_state=None, steps=[], restoration=None, manifest_digest=None,
        )
        with tempfile.TemporaryDirectory() as d:
            cap_path = os.path.join(d, "cap.bin")
            man_path = os.path.join(d, "man.json")
            out_path = os.path.join(d, "appr.bin")
            with open(cap_path, "wb") as f:
                f.write(encode(cap))
            with open(man_path, "w") as f:
                json.dump({"s.png": "abc"}, f)
            rc = cli.main(["approve", "--capture-record", cap_path,
                          "--manifest", man_path, "--reviewer", "test",
                          "--output", out_path])
            self.assertEqual(rc, 0)
            with open(out_path, "rb") as f:
                appr = decode(f.read())
            self.assertIsInstance(appr, ApprovalRecord)
            self.assertEqual(appr.reviewer, "test")


import json


if __name__ == "__main__":
    unittest.main()
