"""Tests for orchestrator phase ordering, prior-state capture, and restoration."""

import unittest

from android.scripts.m2_device import orchestrator as O
from android.scripts.m2_device.records import (
    CommandResult, PriorDeviceState, StepRecord, TerminalCause, ToolIdentity,
)


def _cr(rc=0, stdout=b"ok", timed_out=False):
    return CommandResult(
        argv=["fake"], start_utc="2026-08-06T12:00:00Z",
        end_utc="2026-08-06T12:00:01Z", returncode=rc,
        stdout=stdout, stderr=b"", timed_out=timed_out,
    )


def _prior():
    return PriorDeviceState(
        serial="emu-5554", emulator_state="booted",
        fingerprint="fp", api_level=34,
        screen_width=1080, screen_height=2400,
        package_present=False, package_hash=None,
        enabled_imes=["default"], default_ime="default",
    )


class FakeHarness:
    def __init__(self, *, prior=_prior(), fail_at=None,
                 restore_fail=False, verify_mismatch=False):
        self._prior = prior
        self._fail_at = fail_at
        self._restore_fail = restore_fail
        self._verify_mismatch = verify_mismatch
        self.restore_count = 0

    def preflight(self):
        return _cr(rc=5 if self._fail_at == "preflight" else 0)

    def launch_emulator(self):
        return _cr(rc=5 if self._fail_at == "launch" else 0)

    def attach(self):
        return _cr(rc=5 if self._fail_at == "attach" else 0)

    def capture_prior_state(self):
        if self._fail_at == "prior_state":
            return None
        return self._prior

    def validate_fixture(self, prior):
        return _cr(rc=5 if self._fail_at == "validate" else 0)

    def install_apk(self):
        return _cr(rc=5 if self._fail_at == "install" else 0)

    def run_journey(self):
        if self._fail_at == "journey":
            return [StepRecord(
                phase="journey", operation="type",
                input_digest=None, output_digest=None,
                result=_cr(rc=1),
                cause=TerminalCause.JOURNEY_FAILED,
            )]
        return [StepRecord(
            phase="journey", operation="type",
            input_digest=None, output_digest=None,
            result=_cr(),
            cause=TerminalCause.COMPLETED,
        )]

    def capture_evidence(self):
        return _cr(rc=5 if self._fail_at == "capture" else 0)

    def restore(self):
        self.restore_count += 1
        return _cr(rc=5 if self._restore_fail else 0)

    def verify_restore(self):
        if self._verify_mismatch:
            return PriorDeviceState(
                serial="emu-5554", emulator_state="booted",
                fingerprint="DIFFERENT", api_level=34,
                screen_width=1080, screen_height=2400,
                package_present=False, package_hash=None,
                enabled_imes=["default"], default_ime="default",
            )
        return self._prior


def _tools():
    return [ToolIdentity(name="adb", path="/adb", version="1.0")]


class TestHappyPath(unittest.TestCase):
    def test_full_success(self):
        h = FakeHarness()
        orch = O.Orchestrator(h, repo_head="abc", apk_sha256="def", tools=_tools())
        rec = orch.execute()
        self.assertIsNone(orch.terminal)
        self.assertIsNotNone(rec.prior_state)
        phases = [s.phase for s in rec.steps]
        self.assertIn("preflight", phases)
        self.assertIn("prior_state", phases)
        self.assertIn("install", phases)
        self.assertIn("journey", phases)
        self.assertIn("capture", phases)
        self.assertIn("restore", phases)
        self.assertEqual(h.restore_count, 1)


class TestPriorStateFirst(unittest.TestCase):
    def test_prior_state_after_attach_before_mutation(self):
        h = FakeHarness()
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        orch.execute()
        phases = [s.phase for s in orch.steps]
        attach_idx = phases.index("attach")
        prior_idx = phases.index("prior_state")
        install_idx = phases.index("install")
        self.assertLess(attach_idx, prior_idx)
        self.assertLess(prior_idx, install_idx)


class TestPriorStateUnavailable(unittest.TestCase):
    def test_stops_before_mutation(self):
        h = FakeHarness(fail_at="prior_state")
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        rec = orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.PRIOR_STATE_UNAVAILABLE)
        self.assertIsNone(rec.prior_state)
        phases = [s.phase for s in rec.steps]
        self.assertNotIn("install", phases)
        self.assertNotIn("journey", phases)
        self.assertEqual(h.restore_count, 0)


class TestFixtureMismatch(unittest.TestCase):
    def test_stops_before_mutation(self):
        h = FakeHarness(fail_at="validate")
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        rec = orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.FIXTURE_MISMATCH)
        phases = [s.phase for s in rec.steps]
        self.assertNotIn("install", phases)
        self.assertEqual(h.restore_count, 0)


class TestInstallFailure(unittest.TestCase):
    def test_restoration_runs_on_install_fail(self):
        h = FakeHarness(fail_at="install")
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        rec = orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.INSTALL_FAILED)
        self.assertIn("restore", [s.phase for s in rec.steps])
        self.assertEqual(h.restore_count, 1)

    def test_terminal_cause_distinguishes_from_journey(self):
        h = FakeHarness(fail_at="install")
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.INSTALL_FAILED)
        self.assertNotEqual(orch.terminal, TerminalCause.JOURNEY_FAILED)


class TestJourneyFailure(unittest.TestCase):
    def test_restoration_runs_on_journey_fail(self):
        h = FakeHarness(fail_at="journey")
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        rec = orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.JOURNEY_FAILED)
        self.assertIn("restore", [s.phase for s in rec.steps])
        self.assertEqual(h.restore_count, 1)

    def test_terminal_cause_distinguishes_from_capture(self):
        h = FakeHarness(fail_at="journey")
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        orch.execute()
        self.assertNotEqual(orch.terminal, TerminalCause.CAPTURE_FAILED)


class TestCaptureFailure(unittest.TestCase):
    def test_restoration_runs_on_capture_fail(self):
        h = FakeHarness(fail_at="capture")
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        rec = orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.CAPTURE_FAILED)
        self.assertIn("restore", [s.phase for s in rec.steps])
        self.assertEqual(h.restore_count, 1)


class TestRestoration(unittest.TestCase):
    def test_restore_exactly_once(self):
        h = FakeHarness(fail_at="install")
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        orch.execute()
        self.assertEqual(h.restore_count, 1)

    def test_cleanup_partial_when_restore_fails(self):
        h = FakeHarness(fail_at="install", restore_fail=True)
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.INSTALL_FAILED)
        self.assertIsNotNone(orch.restoration)
        self.assertEqual(orch.restoration.cause, TerminalCause.CLEANUP_PARTIAL)

    def test_no_restore_without_prior_state(self):
        h = FakeHarness(fail_at="prior_state")
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        orch.execute()
        self.assertEqual(h.restore_count, 0)
        self.assertIsNone(orch.restoration)


class TestRestorationMismatch(unittest.TestCase):
    def test_mismatch_detected_after_restore(self):
        h = FakeHarness(verify_mismatch=True)
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        rec = orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.RESTORATION_MISMATCH)
        self.assertIn("verify_restore", [s.phase for s in rec.steps])


class TestMutationGuard(unittest.TestCase):
    def test_install_before_prior_raises(self):
        h = FakeHarness()
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        orch.prior_state = None
        with self.assertRaises(RuntimeError):
            orch._guard_mutation("install")


class TestPreflightFailure(unittest.TestCase):
    def test_preflight_fail_no_restore(self):
        h = FakeHarness(fail_at="preflight")
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        rec = orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.TOOL_FAILURE)
        self.assertNotIn("install", [s.phase for s in rec.steps])
        self.assertEqual(h.restore_count, 0)


class TestCaptureRecordDecodable(unittest.TestCase):
    def test_record_round_trips_on_success(self):
        from android.scripts.m2_device.records import encode, decode
        h = FakeHarness()
        orch = O.Orchestrator(h, repo_head="a", apk_sha256="b", tools=_tools())
        rec = orch.execute()
        encoded = encode(rec)
        decoded = decode(encoded)
        self.assertEqual(encoded, encode(decoded))


if __name__ == "__main__":
    unittest.main()
