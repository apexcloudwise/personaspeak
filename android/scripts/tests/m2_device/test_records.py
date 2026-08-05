"""Round-trip and validation tests for the records codec."""

import json
import unittest

from android.scripts.m2_device import records as R


def _rt(record: R.Record) -> R.Record:
    """Encode then decode."""
    return R.decode(R.encode(record))


class TestRoundTrip(unittest.TestCase):
    def assertByteIdentical(self, rec: R.Record):
        encoded = R.encode(rec)
        decoded = R.decode(encoded)
        re_encoded = R.encode(decoded)
        self.assertEqual(encoded, re_encoded, "byte-round-trip failed")
        self.assertEqual(decoded, rec, "semantic equality failed")

    def test_tool_identity_minimal(self):
        self.assertByteIdentical(
            R.ToolIdentity(name="adb", path="/usr/bin/adb", version="1.0.41")
        )

    def test_tool_identity_with_digest(self):
        self.assertByteIdentical(
            R.ToolIdentity(
                name="apksigner",
                path="/sdk/build-tools/36.1.0/apksigner",
                version="0.8",
                digest="abc123",
            )
        )

    def test_command_result_basic(self):
        self.assertByteIdentical(
            R.CommandResult(
                argv=["adb", "devices"],
                start_utc="2026-08-06T12:00:00Z",
                end_utc="2026-08-06T12:00:01Z",
                returncode=0,
                stdout=b"List of devices\n",
                stderr=b"",
            )
        )

    def test_command_result_empty_output(self):
        self.assertByteIdentical(
            R.CommandResult(
                argv=["echo"],
                start_utc="2026-08-06T12:00:00Z",
                end_utc="2026-08-06T12:00:00Z",
                returncode=0,
                stdout=b"",
                stderr=b"",
            )
        )

    def test_command_result_no_trailing_newline(self):
        self.assertByteIdentical(
            R.CommandResult(
                argv=["printf", "hello"],
                start_utc="2026-08-06T12:00:00Z",
                end_utc="2026-08-06T12:00:00Z",
                returncode=0,
                stdout=b"hello",
                stderr=b"",
            )
        )

    def test_command_result_special_punctuation(self):
        payload = b"a/b.C:d;e f-g_h__RC=0\n\t special"
        self.assertByteIdentical(
            R.CommandResult(
                argv=["cat"],
                start_utc="2026-08-06T12:00:00Z",
                end_utc="2026-08-06T12:00:01Z",
                returncode=1,
                stdout=payload,
                stderr=b"error: __RC=0 sentinel text",
            )
        )

    def test_command_result_timeout(self):
        self.assertByteIdentical(
            R.CommandResult(
                argv=["sleep", "999"],
                start_utc="2026-08-06T12:00:00Z",
                end_utc="2026-08-06T12:00:05Z",
                returncode=-9,
                stdout=b"",
                stderr=b"",
                timed_out=True,
            )
        )

    def test_remote_result_unavailable(self):
        self.assertByteIdentical(
            R.RemoteResult(
                transport=R.CommandResult(
                    argv=["adb", "shell", "ls"],
                    start_utc="2026-08-06T12:00:00Z",
                    end_utc="2026-08-06T12:00:01Z",
                    returncode=0,
                    stdout=b"output without newline",
                    stderr=b"",
                ),
                remote_rc=None,
            )
        )

    def test_remote_result_with_rc(self):
        for rc in (0, 1, 2, 127, 255):
            with self.subTest(rc=rc):
                self.assertByteIdentical(
                    R.RemoteResult(
                        transport=R.CommandResult(
                            argv=["adb", "shell", "test"],
                            start_utc="2026-08-06T12:00:00Z",
                            end_utc="2026-08-06T12:00:01Z",
                            returncode=0,
                            stdout=b"ok",
                            stderr=b"",
                        ),
                        remote_rc=rc,
                    )
                )

    def test_remote_unavailable_property(self):
        r = R.RemoteResult(
            transport=R.CommandResult(
                argv=["x"], start_utc="", end_utc="",
                returncode=0, stdout=b"", stderr=b"",
            ),
            remote_rc=None,
        )
        self.assertFalse(r.remote_available)

    def test_prior_device_state(self):
        self.assertByteIdentical(
            R.PriorDeviceState(
                serial="emulator-5554",
                emulator_state="stopped",
                fingerprint="generic_x86/sdk_gphone_x86_64/arm64-v8a",
                api_level=34,
                screen_width=1080,
                screen_height=2400,
                package_present=False,
                package_hash=None,
                enabled_imes=["com.menny.android.anysoftkeyboard/.SoftKeyboard"],
                default_ime="com.menny.android.anysoftkeyboard/.SoftKeyboard",
            )
        )

    def test_prior_device_state_with_package(self):
        self.assertByteIdentical(
            R.PriorDeviceState(
                serial="emulator-5554",
                emulator_state="booted",
                fingerprint="test",
                api_level=34,
                screen_width=1080,
                screen_height=2400,
                package_present=True,
                package_hash="abc",
                enabled_imes=["ime1", "ime2"],
                default_ime="ime1",
            )
        )

    def test_step_record_with_command_result(self):
        self.assertByteIdentical(
            R.StepRecord(
                phase="preflight",
                operation="check adb version",
                input_digest=None,
                output_digest="sha256:abc",
                result=R.CommandResult(
                    argv=["adb", "version"],
                    start_utc="2026-08-06T12:00:00Z",
                    end_utc="2026-08-06T12:00:00Z",
                    returncode=0,
                    stdout=b"1.0.41",
                    stderr=b"",
                ),
                cause=R.TerminalCause.COMPLETED,
            )
        )

    def test_step_record_with_remote_result(self):
        self.assertByteIdentical(
            R.StepRecord(
                phase="journey",
                operation="type hello",
                input_digest="sha256:in",
                output_digest="sha256:out",
                result=R.RemoteResult(
                    transport=R.CommandResult(
                        argv=["adb", "shell", "input", "text", "hello"],
                        start_utc="2026-08-06T12:00:00Z",
                        end_utc="2026-08-06T12:00:01Z",
                        returncode=0,
                        stdout=b"",
                        stderr=b"",
                    ),
                    remote_rc=0,
                ),
                cause=R.TerminalCause.COMPLETED,
            )
        )

    def test_capture_record_full(self):
        step = R.StepRecord(
            phase="preflight",
            operation="adb devices",
            input_digest=None,
            output_digest=None,
            result=R.CommandResult(
                argv=["adb", "devices"],
                start_utc="2026-08-06T12:00:00Z",
                end_utc="2026-08-06T12:00:00Z",
                returncode=0,
                stdout=b"emulator-5554",
                stderr=b"",
            ),
            cause=R.TerminalCause.COMPLETED,
        )
        prior = R.PriorDeviceState(
            serial="emulator-5554",
            emulator_state="booted",
            fingerprint="fp",
            api_level=34,
            screen_width=1080,
            screen_height=2400,
            package_present=False,
            package_hash=None,
            enabled_imes=["default"],
            default_ime="default",
        )
        restoration = R.StepRecord(
            phase="restore",
            operation="uninstall",
            input_digest=None,
            output_digest=None,
            result=step.result,
            cause=R.TerminalCause.COMPLETED,
        )
        self.assertByteIdentical(
            R.CaptureRecord(
                repo_head="abc123",
                apk_sha256="def456",
                tools=[
                    R.ToolIdentity(name="adb", path="/adb", version="1.0"),
                    R.ToolIdentity(
                        name="python3", path="/python3", version="3.13",
                        digest="sha256:py",
                    ),
                ],
                prior_state=prior,
                steps=[step, restoration],
                restoration=restoration,
                manifest_digest="sha256:manifest",
                visual_review=R.VisualReview.PENDING,
            )
        )

    def test_capture_record_no_prior_state(self):
        self.assertByteIdentical(
            R.CaptureRecord(
                repo_head="abc",
                apk_sha256="def",
                tools=[],
                prior_state=None,
                steps=[],
                restoration=None,
                manifest_digest=None,
            )
        )

    def test_approval_record(self):
        self.assertByteIdentical(
            R.ApprovalRecord(
                reviewer="cassie-yolo",
                capture_digest="sha256:cap",
                manifest_digest="sha256:man",
                decision=R.VisualReview.APPROVED,
                approved_utc="2026-08-06T14:00:00Z",
            )
        )

    def test_final_receipt(self):
        self.assertByteIdentical(
            R.FinalReceipt(
                capture_digest="sha256:cap",
                approval_digest="sha256:appr",
                privacy_ok=True,
                media_ok=True,
                restoration_verdict="verified",
                counts={"screenshots": 7, "video": 1, "tests": 1360},
                evidence_commit="evidence_commit_sha",
                artifacts={
                    "journey.mp4": "sha256:video",
                    "screenshot_01.png": "sha256:s1",
                },
            )
        )


class TestValidation(unittest.TestCase):
    def test_unknown_kind_rejected(self):
        bad = json.dumps({"schema": 1, "kind": "nonexistent", "x": 1})
        with self.assertRaises(ValueError, msg="unknown kind"):
            R.decode(bad)

    def test_wrong_schema_rejected(self):
        bad = json.dumps({
            "schema": 99, "kind": "tool_identity",
            "name": "x", "path": "x", "version": "x", "digest": None,
        })
        with self.assertRaises(ValueError):
            R.decode(bad)

    def test_missing_field_rejected(self):
        bad = json.dumps({
            "schema": 1, "kind": "tool_identity",
            "name": "x", "path": "x",
        })
        with self.assertRaises(ValueError):
            R.decode(bad)

    def test_extra_field_rejected(self):
        bad = json.dumps({
            "schema": 1, "kind": "tool_identity",
            "name": "x", "path": "x", "version": "x", "digest": None,
            "bogus": "extra",
        })
        with self.assertRaises(ValueError):
            R.decode(bad)

    def test_duplicate_keys_rejected(self):
        raw = (
            '{"schema":1,"kind":"tool_identity",'
            '"name":"a","name":"b","path":"x","version":"x","digest":null}'
        )
        with self.assertRaises(ValueError):
            R.decode(raw)

    def test_invalid_enum_rejected(self):
        d = {
            "schema": 1, "kind": "step_record",
            "phase": "p", "operation": "o",
            "input_digest": None, "output_digest": None,
            "result": {
                "schema": 1, "kind": "command_result",
                "argv": ["x"], "start_utc": "", "end_utc": "",
                "returncode": 0, "stdout": "", "stderr": "",
            },
            "cause": "not_a_real_cause",
        }
        with self.assertRaises(ValueError):
            R.decode(json.dumps(d))

    def test_malformed_base64_rejected(self):
        d = {
            "schema": 1, "kind": "command_result",
            "argv": ["x"], "start_utc": "", "end_utc": "",
            "returncode": 0, "stdout": "!!!not_valid_b64!!!", "stderr": "",
        }
        with self.assertRaises(ValueError):
            R.decode(json.dumps(d))

    def test_nested_unknown_kind_rejected(self):
        d = {
            "schema": 1, "kind": "step_record",
            "phase": "p", "operation": "o",
            "input_digest": None, "output_digest": None,
            "result": {"schema": 1, "kind": "bogus_nested"},
            "cause": "completed",
        }
        with self.assertRaises(ValueError):
            R.decode(json.dumps(d))

    def test_remote_result_missing_remote_rc(self):
        bad = json.dumps({
            "schema": 1, "kind": "remote_result",
            "transport": {
                "schema": 1, "kind": "command_result",
                "argv": ["x"], "start_utc": "", "end_utc": "",
                "returncode": 0, "stdout": "", "stderr": "",
            },
        })
        with self.assertRaises(ValueError):
            R.decode(bad)


class TestDeterminism(unittest.TestCase):
    def test_same_record_same_bytes(self):
        rec = R.CommandResult(
            argv=["a", "b", "c"],
            start_utc="2026-01-01T00:00:00Z",
            end_utc="2026-01-01T00:00:01Z",
            returncode=0,
            stdout=b"output",
            stderr=b"",
        )
        self.assertEqual(R.encode(rec), R.encode(rec))

    def test_field_order_irrelevant(self):
        d1 = json.dumps({
            "schema": 1, "kind": "tool_identity",
            "name": "x", "path": "y", "version": "z", "digest": None,
        }, sort_keys=True, separators=(",", ":"))
        d2 = json.dumps({
            "digest": None, "version": "z",
            "path": "y", "name": "x",
            "kind": "tool_identity", "schema": 1,
        }, sort_keys=True, separators=(",", ":"))
        self.assertEqual(
            R.decode(d1.encode()),
            R.decode(d2.encode()),
        )

    def test_record_digest_stable(self):
        rec = R.ToolIdentity(name="x", path="y", version="z")
        d1 = R.record_digest(rec)
        d2 = R.record_digest(rec)
        self.assertEqual(d1, d2)
        self.assertEqual(len(d1), 64)


class TestRegistryCompleteness(unittest.TestCase):
    REQUIRED_KINDS = [
        "tool_identity", "command_result", "remote_result",
        "prior_device_state", "step_record", "capture_record",
        "approval_record", "final_receipt",
    ]

    def test_all_kinds_registered(self):
        for kind in self.REQUIRED_KINDS:
            self.assertIn(kind, R._REGISTRY, f"missing {kind} in registry")

    def test_all_causes_exhaustive(self):
        causes = {c for c in R.TerminalCause}
        self.assertGreater(len(causes), 8,
                           "terminal cause enum must be comprehensive")


if __name__ == "__main__":
    unittest.main()
