"""Tests for evidence validation, privacy scans, media checks, and CLI."""

import io
import hashlib
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


def _valid_png_bytes():
    import struct, zlib
    sig = b'\x89PNG\r\n\x1a\n'
    ihdr_data = struct.pack('>IIBBBBB', 1, 1, 8, 2, 0, 0, 0)
    ihdr = struct.pack('>I', 13) + b'IHDR' + ihdr_data + struct.pack('>I', zlib.crc32(b'IHDR' + ihdr_data) & 0xFFFFFFFF)
    comp = zlib.compress(b'\x00\xff\x00\x00')
    idat = struct.pack('>I', len(comp)) + b'IDAT' + comp + struct.pack('>I', zlib.crc32(b'IDAT' + comp) & 0xFFFFFFFF)
    iend = struct.pack('>I', 0) + b'IEND' + struct.pack('>I', zlib.crc32(b'IEND') & 0xFFFFFFFF)
    return sig + ihdr + idat + iend


def _valid_mp4_bytes():
    import struct
    payload = b'isom' + b'\x00\x00\x00\x00' + b'isom'
    ftyp = struct.pack('>I', 8 + len(payload)) + b'ftyp' + payload
    mdat = struct.pack('>I', 12) + b'mdat' + b'\x00\x00\x00\x00'
    return ftyp + mdat


class TestFinalize(unittest.TestCase):
    def _capture(self, manifest_digest=None, with_steps=False):
        steps = []
        if with_steps:
            for phase in ("journey", "release_emulator", "verify_release",
                          "verify_restore"):
                steps.append(StepRecord(
                    phase=phase, operation=phase, input_digest=None,
                    output_digest=None,
                    result=CommandResult(argv=[], start_utc="", end_utc="",
                                         returncode=0, stdout=b"", stderr=b""),
                    cause=TerminalCause.COMPLETED,
                ))
        return CaptureRecord(
            repo_head="abc", apk_sha256="def", tools=[],
            prior_state=None, steps=steps,
            restoration=StepRecord(
                phase="restore", operation="restore device state",
                input_digest=None, output_digest=None,
                result=CommandResult(argv=[], start_utc="", end_utc="",
                                     returncode=0, stdout=b"ok", stderr=b""),
                cause=TerminalCause.COMPLETED,
            ),
            manifest_digest=manifest_digest,
        )

    def _make_approval(self, cap, man, decision=VisualReview.APPROVED):
        return ApprovalRecord(
            reviewer="reviewer", capture_digest=record_digest(cap),
            manifest_digest=evidence.manifest_digest(man), decision=decision,
            approved_utc="2026-08-06T14:00:00Z",
        )

    def _ledger_bytes(self):
        """A real CommandLedger serialization — binding the canonical
        fixture to the production entry shape (rename a key in
        commands.py and every canonical test fails, not just one)."""
        from android.scripts.m2_device import commands as _C
        ledger = _C.CommandLedger()
        ledger.record(
            ["adb", "-s", "emulator-5554", "shell", "getprop",
             "sys.boot_completed"],
            "2026-08-16T12:00:00Z", "2026-08-16T12:00:01Z",
            0, 0, False, "shell")
        return ledger.serialize().encode()

    def _canonical(self, d, journey_xml=b"<hierarchy/>",
                   ledger_bytes=None):
        """Materialize the exact canonical artifact set and its manifest."""
        files = {}
        for n in evidence.CANONICAL_PNG_NAMES:
            files[f"{n}.png"] = _valid_png_bytes()
        files[f"{evidence.CANONICAL_MP4_NAME}.mp4"] = _valid_mp4_bytes()
        for label in evidence.CANONICAL_HIERARCHY_LABELS:
            files[f"{label}.xml"] = (
                journey_xml if label == "journey" else b"<hierarchy/>")
        files[evidence.CANONICAL_LEDGER_NAME] = (
            ledger_bytes if ledger_bytes is not None else self._ledger_bytes())
        man = {}
        for name, data in files.items():
            with open(os.path.join(d, name), "wb") as f:
                f.write(data)
            man[name] = hashlib.sha256(data).hexdigest()
        return man

    def test_successful_finalize_derives_all_dimensions(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            appr = self._make_approval(cap, man)
            receipt = evidence.finalize(
                cap, appr, man, d, evidence_commit="sha")
        self.assertTrue(receipt.privacy_ok)
        self.assertTrue(receipt.media_ok)
        self.assertEqual(receipt.restoration_verdict, "verified")
        self.assertEqual(receipt.counts["png"], 7)
        self.assertEqual(receipt.counts["mp4"], 1)
        self.assertEqual(receipt.counts["journey_steps_completed"], 1)
        self.assertEqual(receipt.counts["release_ok"], 1)
        self.assertEqual(receipt.counts["verify_release_ok"], 1)
        self.assertEqual(receipt.evidence_commit, "sha")

    def test_drift_blocks_finalize(self):
        cap = self._capture()
        man = {"x": "y"}
        appr = ApprovalRecord(
            reviewer="r", capture_digest="WRONG",
            manifest_digest=evidence.manifest_digest(man),
            decision=VisualReview.APPROVED,
            approved_utc="2026-08-06T14:00:00Z",
        )
        with self.assertRaises(ValueError):
            evidence.finalize(cap, appr, man, "/nonexistent")

    def test_manifest_drift_blocks_finalize(self):
        cap = self._capture()
        appr = ApprovalRecord(
            reviewer="r", capture_digest=record_digest(cap),
            manifest_digest="WRONG", decision=VisualReview.APPROVED,
            approved_utc="2026-08-06T14:00:00Z",
        )
        with self.assertRaises(ValueError):
            evidence.finalize(cap, appr, {"x": "y"}, "/nonexistent")

    def test_rejected_approval_blocks_finalize(self):
        cap = self._capture()
        man = {"screenshot.png": "abc"}
        appr = self._make_approval(cap, man, decision=VisualReview.REJECTED)
        with self.assertRaises(ValueError):
            evidence.finalize(cap, appr, man, "/nonexistent")

    def test_finalize_rejects_missing_canonical_artifact(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            partial = {k: v for k, v in man.items() if k != "06-stale.png"}
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(partial))
            appr = self._make_approval(cap, partial)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, partial, d)
            self.assertIn("canonical artifacts missing", str(cm.exception))

    def test_finalize_rejects_extra_artifact_key(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            with_extra = dict(man)
            with_extra["notes.txt"] = hashlib.sha256(b"x").hexdigest()
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(with_extra))
            appr = self._make_approval(cap, with_extra)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, with_extra, d)
            self.assertIn("non-canonical", str(cm.exception))

    def test_finalize_rejects_swapped_artifact_bytes(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            # Same canonical name, different bytes than approved.
            with open(os.path.join(d, "after_apply.xml"), "wb") as f:
                f.write(b"<swapped/>")
            cap = self._capture(manifest_digest=evidence.manifest_digest(man))
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("digest mismatch", str(cm.exception))

    def test_finalize_rejects_unlisted_files(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            with open(os.path.join(d, "extra.txt"), "w") as f:
                f.write("unlisted")
            cap = self._capture(manifest_digest=evidence.manifest_digest(man))
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("unlisted", str(cm.exception))

    def test_finalize_detects_privacy_violation(self):
        dirty = b"api_key=sk-1234567890abcdef\n"
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d, journey_xml=dirty)
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("privacy", str(cm.exception))

    def test_finalize_rejects_invalid_media_bytes(self):
        # Bytes match the manifest (no drift) but are structurally
        # invalid media — structural validation is its own fail-closed
        # dimension, not just drift detection.
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            corrupt = b"not a png"
            with open(os.path.join(d, "04-applied.png"), "wb") as f:
                f.write(corrupt)
            man["04-applied.png"] = hashlib.sha256(corrupt).hexdigest()
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("media validation", str(cm.exception))

    def test_finalize_requires_verify_restore_step(self):
        # A COMPLETED snapshot-load is not itself a restoration verdict;
        # the verify_restore step must exist and be COMPLETED.
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            cap = CaptureRecord(
                repo_head="a", apk_sha256="b", tools=[],
                prior_state=None, steps=[], restoration=StepRecord(
                    phase="restore", operation="restore device state",
                    input_digest=None, output_digest=None,
                    result=CommandResult(argv=[], start_utc="", end_utc="",
                                         returncode=0, stdout=b"", stderr=b""),
                    cause=TerminalCause.COMPLETED,
                ),
                manifest_digest=evidence.manifest_digest(man),
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("verify_restore", str(cm.exception))

    def test_finalize_refuses_failed_release(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            steps = []
            for phase in ("journey", "verify_restore"):
                steps.append(StepRecord(
                    phase=phase, operation=phase, input_digest=None,
                    output_digest=None,
                    result=CommandResult(argv=[], start_utc="", end_utc="",
                                         returncode=0, stdout=b"", stderr=b""),
                    cause=TerminalCause.COMPLETED,
                ))
            steps.append(StepRecord(
                phase="release_emulator", operation="release",
                input_digest=None, output_digest=None,
                result=CommandResult(argv=[], start_utc="", end_utc="",
                                     returncode=1, stdout=b"", stderr=b"x"),
                cause=TerminalCause.CLEANUP_PARTIAL,
            ))
            steps.append(StepRecord(
                phase="verify_release", operation="verify release",
                input_digest=None, output_digest=None,
                result=CommandResult(argv=[], start_utc="", end_utc="",
                                     returncode=0, stdout=b"", stderr=b""),
                cause=TerminalCause.COMPLETED,
            ))
            cap = CaptureRecord(
                repo_head="a", apk_sha256="b", tools=[],
                prior_state=None, steps=steps,
                restoration=StepRecord(
                    phase="restore", operation="restore device state",
                    input_digest=None, output_digest=None,
                    result=CommandResult(argv=[], start_utc="", end_utc="",
                                         returncode=0, stdout=b"", stderr=b""),
                    cause=TerminalCause.COMPLETED,
                ),
                manifest_digest=evidence.manifest_digest(man),
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("release_emulator did not complete", str(cm.exception))

    def test_finalize_refuses_failed_verify_release(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            steps = []
            for phase in ("journey", "verify_restore", "release_emulator"):
                steps.append(StepRecord(
                    phase=phase, operation=phase, input_digest=None,
                    output_digest=None,
                    result=CommandResult(argv=[], start_utc="", end_utc="",
                                         returncode=0, stdout=b"", stderr=b""),
                    cause=TerminalCause.COMPLETED,
                ))
            steps.append(StepRecord(
                phase="verify_release", operation="verify release",
                input_digest=None, output_digest=None,
                result=CommandResult(argv=[], start_utc="", end_utc="",
                                     returncode=1, stdout=b"", stderr=b"x"),
                cause=TerminalCause.CLEANUP_PARTIAL,
            ))
            cap = CaptureRecord(
                repo_head="a", apk_sha256="b", tools=[],
                prior_state=None, steps=steps,
                restoration=StepRecord(
                    phase="restore", operation="restore device state",
                    input_digest=None, output_digest=None,
                    result=CommandResult(argv=[], start_utc="", end_utc="",
                                         returncode=0, stdout=b"", stderr=b""),
                    cause=TerminalCause.COMPLETED,
                ),
                manifest_digest=evidence.manifest_digest(man),
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("verify_release did not complete", str(cm.exception))

    def test_finalize_rejects_symlinked_directory(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            elsewhere = tempfile.mkdtemp()
            os.symlink(elsewhere, os.path.join(d, "extra_dir"))
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("non-flat", str(cm.exception))

    def test_finalize_rejects_nested_empty_directory(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            os.makedirs(os.path.join(d, "nested"))
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("non-flat", str(cm.exception))

    def test_build_manifest_rejects_nested_directory(self):
        with tempfile.TemporaryDirectory() as d:
            self._canonical(d)
            os.makedirs(os.path.join(d, "nested"))
            with self.assertRaises(ValueError) as cm:
                evidence.build_manifest(d)
            self.assertIn("non-flat", str(cm.exception))

    def test_finalize_rejects_malformed_xml_with_matching_digest(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            malformed = b"<hierarchy"
            with open(os.path.join(d, "journey.xml"), "wb") as f:
                f.write(malformed)
            man["journey.xml"] = hashlib.sha256(malformed).hexdigest()
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("malformed XML", str(cm.exception))

    def test_finalize_rejects_wrong_rooted_xml(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            wrong = b"<notahierarchy/>"
            with open(os.path.join(d, "clear.xml"), "wb") as f:
                f.write(wrong)
            man["clear.xml"] = hashlib.sha256(wrong).hexdigest()
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("not <hierarchy>", str(cm.exception))

    def test_finalize_rejects_malformed_ledger_json(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            malformed = b"{not json"
            with open(os.path.join(d, evidence.CANONICAL_LEDGER_NAME), "wb") as f:
                f.write(malformed)
            man[evidence.CANONICAL_LEDGER_NAME] = (
                hashlib.sha256(malformed).hexdigest())
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("malformed ledger", str(cm.exception))

    def test_finalize_rejects_wrong_shape_ledger_entries(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            malformed = b'[{"argv": ["adb"], "start_utc": "t"}]'
            with open(os.path.join(d, evidence.CANONICAL_LEDGER_NAME), "wb") as f:
                f.write(malformed)
            man[evidence.CANONICAL_LEDGER_NAME] = (
                hashlib.sha256(malformed).hexdigest())
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("field set mismatch", str(cm.exception))

    def test_finalize_rejects_wrong_typed_ledger_entry(self):
        # ghostinprod reproduction: keys present, types interpretive.
        wrong = json.dumps([{
            "argv": "adb shell getprop", "start_utc": 0, "end_utc": None,
            "transport_rc": "success", "remote_rc": [],
            "timed_out": "false", "kind": 7,
        }]).encode()
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d, ledger_bytes=wrong)
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("ledger entry 0", str(cm.exception))

    def test_finalize_rejects_extra_ledger_field(self):
        import json as _json
        base = _json.loads(self._ledger_bytes())
        base[0]["note"] = "extra"
        wrong = _json.dumps(base).encode()
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d, ledger_bytes=wrong)
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("field set mismatch", str(cm.exception))

    def test_finalize_rejects_empty_ledger(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d, ledger_bytes=b"[]")
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("non-empty list", str(cm.exception))

    def test_real_ledger_serialization_round_trips(self):
        # Cassie coupling close: the production serialize() output must
        # always satisfy validate_structural.
        evidence.validate_structural(
            evidence.CANONICAL_LEDGER_NAME, self._ledger_bytes())

    def test_finalize_rejects_failed_journey_step(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            steps = []
            for phase in ("verify_restore", "release_emulator",
                          "verify_release"):
                steps.append(StepRecord(
                    phase=phase, operation=phase, input_digest=None,
                    output_digest=None,
                    result=CommandResult(argv=[], start_utc="", end_utc="",
                                         returncode=0, stdout=b"", stderr=b""),
                    cause=TerminalCause.COMPLETED,
                ))
            steps.append(StepRecord(
                phase="journey", operation="apply_rephrasing",
                input_digest=None, output_digest=None,
                result=CommandResult(argv=[], start_utc="", end_utc="",
                                     returncode=1, stdout=b"", stderr=b"x"),
                cause=TerminalCause.JOURNEY_FAILED,
            ))
            cap = CaptureRecord(
                repo_head="a", apk_sha256="b", tools=[],
                prior_state=None, steps=steps,
                restoration=StepRecord(
                    phase="restore", operation="restore device state",
                    input_digest=None, output_digest=None,
                    result=CommandResult(argv=[], start_utc="", end_utc="",
                                         returncode=0, stdout=b"", stderr=b""),
                    cause=TerminalCause.COMPLETED,
                ),
                manifest_digest=evidence.manifest_digest(man),
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("journey/apply_rephrasing", str(cm.exception))

    def test_finalize_rejects_missing_journey_steps(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            cap = self._capture(
                manifest_digest=evidence.manifest_digest(man),
                with_steps=True,
            )
            # with_steps includes one journey step; strip it.
            cap = CaptureRecord(
                repo_head=cap.repo_head, apk_sha256=cap.apk_sha256,
                tools=cap.tools, prior_state=cap.prior_state,
                steps=[s for s in cap.steps if s.phase != "journey"],
                restoration=cap.restoration,
                manifest_digest=cap.manifest_digest,
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("no journey steps", str(cm.exception))

    def test_finalize_rejects_missing_restoration(self):
        with tempfile.TemporaryDirectory() as d:
            man = self._canonical(d)
            cap = CaptureRecord(
                repo_head="a", apk_sha256="b", tools=[],
                prior_state=None, steps=[], restoration=None,
                manifest_digest=evidence.manifest_digest(man),
            )
            appr = self._make_approval(cap, man)
            with self.assertRaises(ValueError) as cm:
                evidence.finalize(cap, appr, man, d)
            self.assertIn("no restoration", str(cm.exception))


class TestCanonicalSetAndAtomicWrites(unittest.TestCase):

    def test_canonical_set_rejects_nested_entry(self):
        # Full canonical set plus a nested key: the nested entry itself
        # must be what triggers rejection, not absent canonical names.
        man = {name: "h" for name in evidence.CANONICAL_ARTIFACTS}
        man["sub/dir/x.png"] = "h"
        with self.assertRaises(ValueError) as cm:
            evidence.enforce_canonical_set(man)
        self.assertIn("non-canonical", str(cm.exception))

    def test_canonical_set_rejects_extra_entry(self):
        man = {name: "h" for name in evidence.CANONICAL_ARTIFACTS}
        man["notes.txt"] = "h"
        with self.assertRaises(ValueError) as cm:
            evidence.enforce_canonical_set(man)
        self.assertIn("non-canonical", str(cm.exception))

    def test_canonical_set_exact_passes(self):
        man = {name: "h" for name in evidence.CANONICAL_ARTIFACTS}
        evidence.enforce_canonical_set(man)  # must not raise

    def test_write_private_atomic_private_and_clean(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "capture-record.json")
            evidence.write_private_atomic(path, b"payload")
            with open(path, "rb") as f:
                self.assertEqual(f.read(), b"payload")
            self.assertEqual(
                __import__("stat").S_IMODE(os.stat(path).st_mode), 0o600)
            leftovers = [f for f in os.listdir(d)
                         if f != "capture-record.json"]
            self.assertEqual(leftovers, [])


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
