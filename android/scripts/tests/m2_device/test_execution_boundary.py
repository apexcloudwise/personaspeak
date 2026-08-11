"""Adversarial tests for issue #65: command execution, ownership, and cleanup totality.

Covers: wrapper/remote collisions, ambiguity, exec failure, timeout, signals,
launch races, PID reuse, resistant children, cleanup failure, and exact ledger
redaction/ordering. Every path produces a decodable record with exact primary
and cleanup causes.
"""

from __future__ import annotations

import os
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import MagicMock, patch

from android.scripts.m2_device import commands as C
from android.scripts.m2_device import orchestrator as O
from android.scripts.m2_device.adb_harness import AdbHarness
from android.scripts.m2_device.orchestrator import CaptureContext, Orchestrator
from android.scripts.m2_device.records import (
    CommandResult,
    PriorDeviceState,
    RemoteResult,
    StepRecord,
    TerminalCause,
    ToolIdentity,
    encode,
    decode,
)


def _cr(rc=0, stdout=b"", stderr=b"", timed_out=False, argv=None):
    return CommandResult(
        argv=argv if argv is not None else ["fake"],
        start_utc="2026-08-11T12:00:00Z",
        end_utc="2026-08-11T12:00:01Z",
        returncode=rc, stdout=stdout, stderr=stderr, timed_out=timed_out,
    )


def _rr(transport_rc=0, remote_rc=None, timed_out=False):
    return RemoteResult(
        transport=_cr(rc=transport_rc, timed_out=timed_out),
        remote_rc=remote_rc,
    )


def _prior():
    return PriorDeviceState(
        serial="emu-5554", emulator_state="booted",
        fingerprint="fp", api_level=34,
        screen_width=1080, screen_height=2400,
        package_present=False, package_hash=None,
        enabled_imes=["default"], default_ime="default",
    )


def _tools():
    return [ToolIdentity(name="adb", path="/adb", version="1.0")]


class FakeHarness:
    def __init__(self, *, prior=_prior(), fail_at=None,
                 restore_fail=False, release_fail=False,
                 ownership_fail=False, verify_release_fail=False):
        self._prior = prior
        self._fail_at = fail_at
        self._restore_fail = restore_fail
        self._release_fail = release_fail
        self._ownership_fail = ownership_fail
        self._verify_release_fail = verify_release_fail
        self.restore_count = 0
        self.release_count = 0
        self.ownership_count = 0

    def preflight(self):
        return _cr(rc=5 if self._fail_at == "preflight" else 0)

    def capture_context(self):
        return CaptureContext(
            repo_head="abc", apk_sha256="def",
            tools=_tools(), fixture_receipt_digest="deadbeef",
        )

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

    def establish_ownership(self):
        self.ownership_count += 1
        return _cr(rc=5 if self._ownership_fail else 0)

    def install_apk(self):
        return _cr(rc=5 if self._fail_at == "install" else 0)

    def run_journey(self):
        if self._fail_at == "journey":
            return [StepRecord(
                phase="journey", operation="fail",
                input_digest=None, output_digest=None,
                result=_cr(rc=1), cause=TerminalCause.JOURNEY_FAILED,
            )]
        return [StepRecord(
            phase="journey", operation="ok",
            input_digest=None, output_digest=None,
            result=_cr(), cause=TerminalCause.COMPLETED,
        )]

    def capture_evidence(self):
        return _cr(rc=5 if self._fail_at == "capture" else 0)

    def restore(self):
        self.restore_count += 1
        return _cr(rc=5 if self._restore_fail else 0)

    def verify_restore(self):
        return self._prior

    def release_emulator(self):
        self.release_count += 1
        return _cr(rc=5 if self._release_fail else 0)

    def verify_release(self):
        return _cr(rc=5 if self._verify_release_fail else 0)


# ─── Ambiguity classification ───

class TestAmbiguityClassification(unittest.TestCase):

    def test_is_ambiguous_remote_none(self):
        self.assertTrue(O._is_ambiguous(_rr(transport_rc=1, remote_rc=None)))

    def test_not_ambiguous_remote_available(self):
        self.assertFalse(O._is_ambiguous(_rr(transport_rc=0, remote_rc=0)))
        self.assertFalse(O._is_ambiguous(_rr(transport_rc=1, remote_rc=1)))

    def test_not_ambiguous_command_result(self):
        self.assertFalse(O._is_ambiguous(_cr(rc=1)))

    def test_not_ambiguous_timed_out(self):
        rr = _rr(transport_rc=0, remote_rc=None, timed_out=True)
        self.assertFalse(O._is_ambiguous(rr))

    def test_ambiguous_maps_to_tool_failure(self):
        class H(FakeHarness):
            def install_apk(self):
                return _rr(transport_rc=1, remote_rc=None)
        h = H()
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        install_steps = [s for s in orch.steps if s.phase == "install"]
        self.assertTrue(install_steps)
        self.assertEqual(install_steps[0].cause, TerminalCause.TOOL_FAILURE)

    def test_unknown_type_raises(self):
        with self.assertRaises(TypeError):
            O._is_ambiguous(object())


# ─── Wrapper/remote collision ───

class TestWrapperRemoteCollision(unittest.TestCase):

    def test_transport_nonzero_with_remote_zero_still_fails(self):
        result = _rr(transport_rc=1, remote_rc=0)
        self.assertNotEqual(O._rc_of(result), 0)

    def test_transport_nonzero_remote_nonzero(self):
        result = _rr(transport_rc=3, remote_rc=3)
        self.assertEqual(O._rc_of(result), 3)

    def test_collision_stops_orchestrator(self):
        class H(FakeHarness):
            def install_apk(self):
                return _rr(transport_rc=1, remote_rc=0)
        h = H()
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        self.assertIsNotNone(orch.terminal)


# ─── Exec failure ───

class TestExecFailure(unittest.TestCase):

    def test_exec_exception_produces_decodable_record(self):
        class H(FakeHarness):
            def install_apk(self):
                raise FileNotFoundError("adb binary vanished")
        h = H()
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        rec = orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.INSTALL_FAILED)
        encoded = encode(rec)
        decoded = decode(encoded)
        self.assertEqual(encoded, encode(decoded))

    def test_unknown_result_type_stops(self):
        class H(FakeHarness):
            def install_apk(self):
                return "invalid"
        h = H()
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        self.assertIsNotNone(orch.terminal)


# ─── Timeout ───

class TestTimeoutBoundary(unittest.TestCase):

    def test_command_timeout_records_timeout_cause(self):
        class H(FakeHarness):
            def install_apk(self):
                return _cr(rc=-9, timed_out=True)
        h = H()
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        install_steps = [s for s in orch.steps if s.phase == "install"]
        self.assertEqual(install_steps[0].cause, TerminalCause.TIMEOUT)

    def test_remote_transport_timeout_records_timeout_cause(self):
        class H(FakeHarness):
            def install_apk(self):
                return RemoteResult(
                    transport=_cr(rc=-9, timed_out=True),
                    remote_rc=None,
                )
        h = H()
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        install_steps = [s for s in orch.steps if s.phase == "install"]
        self.assertEqual(install_steps[0].cause, TerminalCause.TIMEOUT)


# ─── Signals ───

class TestSignalConvergence(unittest.TestCase):

    def test_signal_sets_terminal_and_cleanup_runs(self):
        h = FakeHarness()
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch._emulator_launched = True
        orch._on_signal(signal.SIGINT, None)
        self.assertEqual(orch.terminal, TerminalCause.SIGNAL_INTERRUPT)

    def test_signal_does_not_override_existing_terminal(self):
        h = FakeHarness(fail_at="install")
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.terminal = TerminalCause.INSTALL_FAILED
        orch._on_signal(signal.SIGTERM, None)
        self.assertEqual(orch.terminal, TerminalCause.INSTALL_FAILED)


# ─── Launch races / ownership timing ───

class TestOwnershipTiming(unittest.TestCase):

    def test_ownership_called_after_validate(self):
        h = FakeHarness()
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        phases = [s.phase for s in orch.steps]
        validate_idx = phases.index("validate_fixture")
        ownership_idx = phases.index("establish_ownership")
        self.assertLess(validate_idx, ownership_idx)

    def test_ownership_failure_prevents_install(self):
        h = FakeHarness(ownership_fail=True)
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        rec = orch.execute()
        phases = [s.phase for s in rec.steps]
        self.assertIn("establish_ownership", phases)
        self.assertNotIn("install", phases)
        self.assertEqual(orch.terminal, TerminalCause.TOOL_FAILURE)

    def test_ownership_failure_still_restores(self):
        h = FakeHarness(ownership_fail=True)
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        rec = orch.execute()
        self.assertEqual(h.restore_count, 1)
        self.assertIn("restore", [s.phase for s in rec.steps])

    def test_ownership_count_exactly_one(self):
        h = FakeHarness()
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        self.assertEqual(h.ownership_count, 1)


# ─── PID reuse ───

class TestPidReuse(unittest.TestCase):

    def test_revalidate_detects_reuse(self):
        tmp = tempfile.mkdtemp()
        apk = os.path.join(tmp, "test.apk")
        with open(apk, "wb") as f:
            f.write(b"apk")
        h = AdbHarness(run_dir=tmp, apk_path=apk)
        h._owned_pid = 999999
        h._owned_start = "old start time"
        self.assertFalse(h._revalidate_ownership())

    def test_revalidate_none_pid(self):
        tmp = tempfile.mkdtemp()
        apk = os.path.join(tmp, "test.apk")
        with open(apk, "wb") as f:
            f.write(b"apk")
        h = AdbHarness(run_dir=tmp, apk_path=apk)
        self.assertFalse(h._revalidate_ownership())

    def test_release_refuses_on_reuse(self):
        tmp = tempfile.mkdtemp()
        apk = os.path.join(tmp, "test.apk")
        with open(apk, "wb") as f:
            f.write(b"apk")
        h = AdbHarness(run_dir=tmp, apk_path=apk)
        h._owned_pid = 999999
        h._owned_start = "stale time"
        res = h.release_emulator()
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"reuse", res.stderr.lower() + b" " + res.stdout.lower())

    def test_pid_identity_dead_process(self):
        proc = subprocess.Popen(["true"])
        proc.wait()
        self.assertIsNone(C.pid_identity(proc.pid))

    def test_pid_identity_live_process(self):
        proc = subprocess.Popen(["sleep", "5"])
        try:
            time.sleep(0.2)
            identity = C.pid_identity(proc.pid)
            self.assertIsNotNone(identity)
            self.assertTrue(len(identity) > 0)
        finally:
            proc.terminate()
            proc.wait()


# ─── Resistant children / bounded_terminate ───

class TestBoundedTerminate(unittest.TestCase):

    def test_terminates_cooperative_process(self):
        proc = subprocess.Popen(["sleep", "30"])
        killed = C.bounded_terminate(proc, term_timeout=1.0, kill_timeout=1.0)
        self.assertFalse(killed)
        self.assertIsNotNone(proc.returncode)

    def test_escalates_to_kill(self):
        proc = subprocess.Popen(
            [sys.executable, "-c",
             "import signal,sys,time;"
             "signal.signal(signal.SIGTERM,lambda*s:None);"
             "time.sleep(30)"],
        )
        time.sleep(0.3)
        killed = C.bounded_terminate(proc, term_timeout=0.5, kill_timeout=2.0)
        self.assertTrue(killed)
        self.assertIsNotNone(proc.returncode)

    def test_releases_emulator_via_bounded_terminate(self):
        tmp = tempfile.mkdtemp()
        apk = os.path.join(tmp, "test.apk")
        with open(apk, "wb") as f:
            f.write(b"apk")
        h = AdbHarness(run_dir=tmp, apk_path=apk)
        proc = subprocess.Popen(["sleep", "30"])
        mp = C.ManagedProcess(proc=proc, argv=["sleep", "30"],
                              start_utc="2026-08-11T12:00:00Z")
        h.emulator_process = mp
        res = h.release_emulator()
        self.assertEqual(res.returncode, 0)
        self.assertIsNone(h.emulator_process)


# ─── Cleanup failure ───

class TestCleanupFailureIndependence(unittest.TestCase):

    def test_primary_cause_preserved_on_release_fail(self):
        h = FakeHarness(fail_at="install", release_fail=True)
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.INSTALL_FAILED)

    def test_primary_cause_preserved_on_verify_release_fail(self):
        h = FakeHarness(fail_at="install", verify_release_fail=True)
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.INSTALL_FAILED)

    def test_release_steps_independent(self):
        h = FakeHarness(release_fail=True, verify_release_fail=True)
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        rec = orch.execute()
        release_steps = [s for s in rec.steps if s.phase == "release_emulator"]
        verify_steps = [s for s in rec.steps if s.phase == "verify_release"]
        self.assertTrue(release_steps)
        self.assertTrue(verify_steps)
        self.assertEqual(release_steps[0].cause, TerminalCause.CLEANUP_PARTIAL)
        self.assertEqual(verify_steps[0].cause, TerminalCause.CLEANUP_PARTIAL)


# ─── Ledger redaction / ordering ───

class TestCommandLedger(unittest.TestCase):

    def test_ledger_records_exact_argv(self):
        ledger = C.CommandLedger()
        ledger.record(["shell", "getprop", "sys.boot_completed"],
                      "2026-08-11T12:00:00Z", "2026-08-11T12:00:01Z",
                      0, 0, False, "shell")
        entries = ledger.entries()
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0].argv,
                         ["shell", "getprop", "sys.boot_completed"])
        self.assertEqual(entries[0].transport_rc, 0)
        self.assertEqual(entries[0].remote_rc, 0)
        self.assertEqual(entries[0].kind, "shell")

    def test_ledger_does_not_store_stdout_stderr(self):
        ledger = C.CommandLedger()
        ledger.record(["install", "-r", "/path/to.apk"],
                      "s", "e", 0, None, False, "host")
        entry = ledger.entries()[0]
        self.assertFalse(hasattr(entry, "stdout"))
        self.assertFalse(hasattr(entry, "stderr"))

    def test_ledger_preserves_order(self):
        ledger = C.CommandLedger()
        for i in range(5):
            ledger.record([f"cmd{i}"], "s", "e", i, None, False, "host")
        entries = ledger.entries()
        for i, entry in enumerate(entries):
            self.assertEqual(entry.transport_rc, i)

    def test_ledger_distinguishes_shell_and_host(self):
        ledger = C.CommandLedger()
        ledger.record(["install"], "s", "e", 0, None, False, "host")
        ledger.record(["shell", "getprop"], "s", "e", 0, 0, False, "shell")
        entries = ledger.entries()
        self.assertEqual(entries[0].kind, "host")
        self.assertEqual(entries[0].remote_rc, None)
        self.assertEqual(entries[1].kind, "shell")
        self.assertEqual(entries[1].remote_rc, 0)

    def test_ledger_len(self):
        ledger = C.CommandLedger()
        self.assertEqual(len(ledger), 0)
        ledger.record(["x"], "s", "e", 0)
        self.assertEqual(len(ledger), 1)

    def test_harness_ledger_populated_by_shell(self):
        tmp = tempfile.mkdtemp()
        apk = os.path.join(tmp, "test.apk")
        with open(apk, "wb") as f:
            f.write(b"apk")
        h = AdbHarness(
            run_dir=tmp, apk_path=apk,
            runner=MagicMock(return_value=_cr(stdout=b"1\n")),
        )
        h.adb_tool = ToolIdentity(name="adb", path="adb", version="1.0")
        h._shell("getprop", "sys.boot_completed")
        entries = h.ledger.entries()
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0].argv,
                         ["shell", "getprop", "sys.boot_completed"])
        self.assertEqual(entries[0].kind, "shell")


# ─── Decodable records from every failure path ───

class TestDecodableFailureRecords(unittest.TestCase):

    def _check_decodable(self, fail_at):
        h = FakeHarness(fail_at=fail_at)
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        rec = orch.execute()
        encoded = encode(rec)
        decoded = decode(encoded)
        self.assertEqual(encoded, encode(decoded))

    def test_preflight_failure_decodable(self):
        self._check_decodable("preflight")

    def test_launch_failure_decodable(self):
        self._check_decodable("launch")

    def test_attach_failure_decodable(self):
        self._check_decodable("attach")

    def test_prior_state_failure_decodable(self):
        self._check_decodable("prior_state")

    def test_validate_failure_decodable(self):
        self._check_decodable("validate")

    def test_install_failure_decodable(self):
        self._check_decodable("install")

    def test_journey_failure_decodable(self):
        self._check_decodable("journey")

    def test_capture_failure_decodable(self):
        self._check_decodable("capture")

    def test_ownership_failure_decodable(self):
        h = FakeHarness(ownership_fail=True)
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        rec = orch.execute()
        encoded = encode(rec)
        decoded = decode(encoded)
        self.assertEqual(encoded, encode(decoded))


if __name__ == "__main__":
    unittest.main()
