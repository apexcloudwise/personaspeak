"""Adversarial tests for issue #65: command execution, ownership, and cleanup totality.

Covers: wrapper/remote collisions, ambiguity propagation (not collapse),
exec failure, timeout, actual signal delivery, launch races, PID identity
(start+command), group-aware termination with resistant descendants,
cleanup failure independence, ledger completeness/serialization, and
decodable records from every failure path.
"""

from __future__ import annotations

import json
import os
import signal
import subprocess
import sys
import tempfile
import textwrap
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


def _harness(tmp=None, runner_return=None):
    tmp = tmp or tempfile.mkdtemp()
    apk = os.path.join(tmp, "test.apk")
    with open(apk, "wb") as f:
        f.write(b"apk")
    h = AdbHarness(
        run_dir=tmp, apk_path=apk,
        runner=MagicMock(return_value=runner_return or _cr(stdout=b"1\n")),
    )
    h.adb_tool = ToolIdentity(name="adb", path="/usr/bin/adb", version="1.0.41")
    return h, tmp


class FakeHarness:
    def __init__(self, *, prior=_prior(), fail_at=None,
                 restore_fail=False, release_fail=False,
                 ownership_fail=False, verify_release_fail=False,
                 ambiguous_at=None):
        self._prior = prior
        self._fail_at = fail_at
        self._restore_fail = restore_fail
        self._release_fail = release_fail
        self._ownership_fail = ownership_fail
        self._verify_release_fail = verify_release_fail
        self._ambiguous_at = ambiguous_at or set()
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
        if "prior_state" in self._ambiguous_at:
            raise C.RemoteAmbiguousError("getprop ambiguous")
        if self._fail_at == "prior_state":
            return None
        return self._prior

    def validate_fixture(self, prior):
        if "validate" in self._ambiguous_at:
            return _rr(transport_rc=1, remote_rc=None)
        return _cr(rc=5 if self._fail_at == "validate" else 0)

    def establish_ownership(self):
        self.ownership_count += 1
        return _cr(rc=5 if self._ownership_fail else 0)

    def install_apk(self):
        if "install" in self._ambiguous_at:
            return _rr(transport_rc=1, remote_rc=None)
        return _cr(rc=5 if self._fail_at == "install" else 0)

    def run_journey(self):
        if "journey" in self._ambiguous_at:
            return [StepRecord(
                phase="journey", operation="ambiguous_tap",
                input_digest=None, output_digest=None,
                result=_rr(transport_rc=1, remote_rc=None),
                cause=TerminalCause.TOOL_FAILURE,
            )]
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


# --- Ambiguity classification ---

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

    def test_unknown_type_raises(self):
        with self.assertRaises(TypeError):
            O._is_ambiguous(object())


# --- Ambiguity propagation (not collapse) ---

class TestAmbiguityPropagation(unittest.TestCase):

    def test_prior_state_ambiguity_maps_to_tool_failure(self):
        h = FakeHarness(ambiguous_at={"prior_state"})
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        prior_steps = [s for s in orch.steps if s.phase == "prior_state"]
        self.assertTrue(prior_steps)
        self.assertEqual(prior_steps[0].cause, TerminalCause.TOOL_FAILURE)
        self.assertEqual(orch.terminal, TerminalCause.TOOL_FAILURE)

    def test_validate_fixture_ambiguity_maps_to_tool_failure(self):
        h = FakeHarness(ambiguous_at={"validate"})
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        validate_steps = [s for s in orch.steps if s.phase == "validate_fixture"]
        self.assertTrue(validate_steps)
        self.assertEqual(validate_steps[0].cause, TerminalCause.TOOL_FAILURE)
        self.assertNotEqual(validate_steps[0].cause, TerminalCause.FIXTURE_MISMATCH)

    def test_install_ambiguity_maps_to_tool_failure(self):
        h = FakeHarness(ambiguous_at={"install"})
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        install_steps = [s for s in orch.steps if s.phase == "install"]
        self.assertTrue(install_steps)
        self.assertEqual(install_steps[0].cause, TerminalCause.TOOL_FAILURE)
        self.assertNotEqual(install_steps[0].cause, TerminalCause.INSTALL_FAILED)

    def test_journey_ambiguity_maps_to_tool_failure(self):
        h = FakeHarness(ambiguous_at={"journey"})
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        journey_steps = [s for s in orch.steps if s.phase == "journey"]
        self.assertTrue(journey_steps)
        self.assertEqual(journey_steps[0].cause, TerminalCause.TOOL_FAILURE)
        self.assertNotEqual(journey_steps[0].cause, TerminalCause.JOURNEY_FAILED)


# --- Wrapper/remote collision ---

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


# --- Exec failure ---

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


# --- Timeout ---

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

    def test_run_post_kill_communicate_is_bounded(self):
        start = time.monotonic()
        res = C.run(
            [sys.executable, "-c",
             "import signal,time;"
             "signal.signal(signal.SIGTERM,lambda*s:None);"
             "time.sleep(30)"],
            timeout=0.5,
        )
        elapsed = time.monotonic() - start
        self.assertTrue(res.timed_out)
        self.assertLess(elapsed, 10.0,
                        "run() post-kill communicate should be bounded")


# --- Signals (actual delivery) ---

class TestSignalConvergence(unittest.TestCase):

    def test_actual_sigint_sets_terminal(self):
        class H(FakeHarness):
            def install_apk(self_inner):
                os.kill(os.getpid(), signal.SIGINT)
                return _cr(rc=0)
        h = H()
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.SIGNAL_INTERRUPT)

    def test_actual_sigterm_sets_terminal(self):
        class H(FakeHarness):
            def install_apk(self_inner):
                os.kill(os.getpid(), signal.SIGTERM)
                return _cr(rc=0)
        h = H()
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.execute()
        self.assertEqual(orch.terminal, TerminalCause.SIGNAL_INTERRUPT)

    def test_signal_does_not_override_existing_terminal(self):
        h = FakeHarness(fail_at="install")
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        orch.terminal = TerminalCause.INSTALL_FAILED
        orch._on_signal(signal.SIGTERM, None)
        self.assertEqual(orch.terminal, TerminalCause.INSTALL_FAILED)


# --- Launch races / ownership timing ---

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


# --- PID identity (start + command) ---

class TestPidIdentity(unittest.TestCase):

    def test_dead_process_returns_none(self):
        proc = subprocess.Popen(["true"])
        proc.wait()
        self.assertIsNone(C.pid_identity(proc.pid))

    def test_live_process_returns_identity_with_start_and_command(self):
        proc = subprocess.Popen(["sleep", "30"])
        try:
            time.sleep(0.2)
            identity = C.pid_identity(proc.pid)
            self.assertIsNotNone(identity)
            self.assertIsInstance(identity, C.ProcessIdentity)
            self.assertTrue(identity.start)
            self.assertIn("sleep", identity.command)
        finally:
            proc.terminate()
            proc.wait()

    def test_identity_reads_actual_argv_not_expectation(self):
        script = "import time; time.sleep(30)"
        proc = subprocess.Popen([sys.executable, "-c", script])
        try:
            time.sleep(0.3)
            identity = C.pid_identity(proc.pid)
            self.assertIsNotNone(identity)
            self.assertIn(script, identity.command)
        finally:
            proc.terminate()
            proc.wait()

    def test_different_argv_produces_different_identity(self):
        s1 = "import time; time.sleep(30)"
        s2 = "import time; time.sleep(40)"
        p1 = subprocess.Popen([sys.executable, "-c", s1])
        p2 = subprocess.Popen([sys.executable, "-c", s2])
        try:
            time.sleep(0.3)
            id1 = C.pid_identity(p1.pid)
            id2 = C.pid_identity(p2.pid)
            self.assertIsNotNone(id1)
            self.assertIsNotNone(id2)
            self.assertNotEqual(id1.command, id2.command)
            self.assertNotEqual(id1, id2)
        finally:
            p1.terminate(); p1.wait()
            p2.terminate(); p2.wait()


# --- Ownership revalidation ---

class TestOwnershipRevalidation(unittest.TestCase):

    def test_revalidation_succeeds_while_alive(self):
        proc = subprocess.Popen(["sleep", "30"])
        try:
            time.sleep(0.2)
            h, _ = _harness()
            h._owned_pid = proc.pid
            h._owned_identity = C.pid_identity(proc.pid)
            self.assertTrue(h._revalidate_ownership())
        finally:
            proc.terminate(); proc.wait()

    def test_revalidation_fails_after_exit(self):
        proc = subprocess.Popen(["sleep", "1"])
        proc.wait()
        h, _ = _harness()
        h._owned_pid = proc.pid
        h._owned_identity = C.ProcessIdentity(start="old", command="old")
        self.assertFalse(h._revalidate_ownership())

    def test_revalidation_none_pid(self):
        h, _ = _harness()
        self.assertFalse(h._revalidate_ownership())

    def test_release_refuses_on_stale_identity(self):
        h, _ = _harness()
        h._owned_pid = 999999
        h._owned_identity = C.ProcessIdentity(start="stale", command="stale")
        res = h.release_emulator()
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"reuse", res.stderr.lower())

    def test_release_refuses_on_revalidation_failure(self):
        proc = subprocess.Popen(["sleep", "1"])
        proc.wait()
        h, _ = _harness()
        h._owned_pid = proc.pid
        h._owned_identity = C.ProcessIdentity(start="x", command="x")
        h.emulator_process = None
        res = h.release_emulator()
        self.assertEqual(res.returncode, 1)


# --- Bounded termination with resistant descendants ---

class TestBoundedTerminate(unittest.TestCase):

    def test_terminates_cooperative_process(self):
        proc = subprocess.Popen(["sleep", "30"])
        killed = C.bounded_terminate(proc, term_timeout=1.0, kill_timeout=1.0)
        self.assertFalse(killed)
        self.assertIsNotNone(proc.returncode)

    def test_escalates_to_kill(self):
        proc = subprocess.Popen(
            [sys.executable, "-c",
             "import signal,time;"
             "signal.signal(signal.SIGTERM,lambda*s:None);"
             "time.sleep(30)"],
        )
        time.sleep(0.3)
        killed = C.bounded_terminate(proc, term_timeout=0.5, kill_timeout=2.0)
        self.assertTrue(killed)
        self.assertIsNotNone(proc.returncode)

    def test_group_terminate_kills_resistant_descendants(self):
        child_pid_file = tempfile.mktemp()
        parent_script = textwrap.dedent(f"""\
            import os, signal, time
            signal.signal(signal.SIGTERM, signal.SIG_IGN)
            pid = os.fork()
            if pid == 0:
                signal.signal(signal.SIGTERM, signal.SIG_IGN)
                time.sleep(30)
            else:
                with open("{child_pid_file}", "w") as f:
                    f.write(str(pid))
                time.sleep(30)
        """)
        proc = subprocess.Popen(
            [sys.executable, "-c", parent_script],
            start_new_session=True,
        )
        time.sleep(0.5)
        with open(child_pid_file) as f:
            child_pid = int(f.read())
        self.assertIsNotNone(C.pid_identity(child_pid))

        killed = C.bounded_terminate(
            proc, group=True, term_timeout=0.5, kill_timeout=2.0)
        self.assertTrue(killed)
        time.sleep(0.3)
        self.assertIsNone(C.pid_identity(child_pid))
        os.unlink(child_pid_file)

    def test_release_emulator_uses_group_termination(self):
        proc = subprocess.Popen(
            ["sleep", "30"], start_new_session=True)
        time.sleep(0.2)
        h, _ = _harness()
        mp = C.ManagedProcess(
            proc=proc, argv=["sleep", "30"],
            start_utc="2026-08-11T12:00:00Z", new_session=True)
        h.emulator_process = mp
        h._session_launched = True
        res = h.release_emulator()
        self.assertEqual(res.returncode, 0)
        self.assertIsNone(h.emulator_process)


# --- Cleanup failure independence ---

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


# --- Ledger: full argv, serialization, completeness ---

class TestCommandLedger(unittest.TestCase):

    def test_ledger_records_full_absolute_argv(self):
        h, _ = _harness(runner_return=_cr(stdout=b"1\n"))
        h._shell("getprop", "sys.boot_completed")
        entries = h.ledger.entries()
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0].argv,
                         ["/usr/bin/adb", "-s", "emulator-5554", "shell",
                          "getprop", "sys.boot_completed"])
        self.assertEqual(entries[0].kind, "shell")

    def test_host_ledger_records_full_argv(self):
        h, _ = _harness(runner_return=_cr(stdout=b"1\n"))
        h._host("wait-for-device", timeout=30.0)
        entries = h.ledger.entries()
        self.assertEqual(entries[0].argv,
                         ["/usr/bin/adb", "-s", "emulator-5554",
                          "wait-for-device"])
        self.assertEqual(entries[0].kind, "host")
        self.assertIsNone(entries[0].remote_rc)

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

    def test_ledger_serialize_roundtrip(self):
        ledger = C.CommandLedger()
        ledger.record(
            ["/usr/bin/adb", "-s", "emu-5554", "shell", "getprop"],
            "2026-08-11T12:00:00Z", "2026-08-11T12:00:01Z",
            0, 0, False, "shell")
        data = json.loads(ledger.serialize())
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]["argv"],
                         ["/usr/bin/adb", "-s", "emu-5554", "shell", "getprop"])
        self.assertEqual(data[0]["transport_rc"], 0)
        self.assertEqual(data[0]["remote_rc"], 0)
        self.assertEqual(data[0]["kind"], "shell")

    def test_ledger_captures_all_production_commands(self):
        h, _ = _harness(runner_return=_cr(stdout=b"1\n"))
        h._host("wait-for-device", timeout=30.0)
        h._shell("getprop", "sys.boot_completed")
        h._host("install", "-r", "/test.apk", timeout=120.0)
        entries = h.ledger.entries()
        self.assertEqual(len(entries), 3)
        self.assertEqual([e.kind for e in entries], ["host", "shell", "host"])
        for e in entries:
            self.assertTrue(e.argv[0].startswith("/"),
                            f"argv not absolute: {e.argv}")

    def test_dump_ledger_writes_artifact(self):
        h, tmp = _harness(runner_return=_cr(stdout=b"1\n"))
        h._shell("getprop", "sys.boot_completed")
        h._host("wait-for-device")
        res = h.dump_ledger()
        self.assertEqual(res.returncode, 0)
        path = os.path.join(tmp, "artifacts", "command_ledger.json")
        self.assertTrue(os.path.isfile(path))
        with open(path) as f:
            data = json.load(f)
        self.assertEqual(len(data), 2)
        self.assertEqual(data[0]["kind"], "shell")
        self.assertEqual(data[1]["kind"], "host")


# --- Decodable records from every failure path ---

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

    def test_ambiguity_decodable(self):
        h = FakeHarness(ambiguous_at={"prior_state"})
        orch = Orchestrator(h, repo_head="a", apk_sha256="", tools=_tools())
        rec = orch.execute()
        encoded = encode(rec)
        decoded = decode(encoded)
        self.assertEqual(encoded, encode(decoded))


if __name__ == "__main__":
    unittest.main()
