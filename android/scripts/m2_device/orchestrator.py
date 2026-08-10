"""Phase ordering, stop causes, prior-state capture, and restoration."""

from __future__ import annotations

import signal
from dataclasses import dataclass, field
from enum import StrEnum
from typing import Protocol, runtime_checkable

from android.scripts.m2_device.records import (
    CaptureRecord,
    CommandResult,
    PriorDeviceState,
    RemoteResult,
    StepRecord,
    TerminalCause,
    ToolIdentity,
    VisualReview,
)

@dataclass(frozen=True)
class CaptureContext:
    repo_head: str
    apk_sha256: str
    tools: list[ToolIdentity]
    fixture_receipt_digest: str = ""


_PRE_MUTATION = frozenset({
    "preflight", "emulator_launch", "attach",
    "prior_state", "validate_fixture",
})
_MUTATION = frozenset({"install", "journey", "capture"})


@runtime_checkable
class JourneyHarness(Protocol):
    def preflight(self) -> CommandResult | RemoteResult: ...
    def capture_context(self) -> CaptureContext: ...
    def launch_emulator(self) -> CommandResult | RemoteResult: ...
    def attach(self) -> CommandResult | RemoteResult: ...
    def capture_prior_state(self) -> PriorDeviceState | None: ...
    def validate_fixture(self, prior: PriorDeviceState) -> CommandResult | RemoteResult: ...
    def install_apk(self) -> CommandResult | RemoteResult: ...
    def run_journey(self) -> list[StepRecord]: ...
    def capture_evidence(self) -> CommandResult | RemoteResult: ...
    def restore(self) -> CommandResult | RemoteResult: ...
    def verify_restore(self) -> PriorDeviceState: ...
    def release_emulator(self) -> CommandResult | RemoteResult: ...
    def verify_release(self) -> CommandResult | RemoteResult: ...


def _rc_of(result) -> int:
    if isinstance(result, CommandResult):
        return result.returncode
    if isinstance(result, RemoteResult):
        if result.transport.returncode != 0:
            return result.transport.returncode
        if result.remote_rc is None:
            return 1
        return result.remote_rc

    raise TypeError(f"unknown result type: {type(result).__name__}")


def _timed_out(result) -> bool:
    if isinstance(result, CommandResult):
        return result.timed_out
    if isinstance(result, RemoteResult):
        return result.transport.timed_out
    raise TypeError(f"unknown result type: {type(result).__name__}")


def _make_step(phase, operation, result, cause):
    return StepRecord(
        phase=phase, operation=operation, input_digest=None,
        output_digest=None, result=result, cause=cause)


class Orchestrator:
    def __init__(
        self,
        harness: JourneyHarness,
        *,
        repo_head: str = "",
        apk_sha256: str = "",
        tools: list[ToolIdentity] | None = None,
    ):
        self.harness = harness
        self.repo_head = repo_head
        self._expected_apk_sha256 = apk_sha256
        self.apk_sha256 = apk_sha256
        self.tools = list(tools) if tools is not None else []
        self.steps: list[StepRecord] = []
        self.prior_state: PriorDeviceState | None = None
        self.restoration: StepRecord | None = None
        self.terminal: TerminalCause | None = None
        self._restored = False
        self._emulator_launched = False
        self._reached: str | None = None
        self.fixture_receipt_digest: str = ""

    def _on_signal(self, signum, frame):
        if self.terminal is None:
            self.terminal = TerminalCause.SIGNAL_INTERRUPT

    def execute(self) -> CaptureRecord:
        prev_int = signal.signal(signal.SIGINT, self._on_signal)
        prev_term = signal.signal(signal.SIGTERM, self._on_signal)
        try:
            return self._execute_inner()
        finally:
            signal.signal(signal.SIGINT, prev_int)
            signal.signal(signal.SIGTERM, prev_term)

    def _execute_inner(self) -> CaptureRecord:
        self._run_phase("preflight", "toolchain preflight",
                        lambda: self.harness.preflight())
        if self.terminal: return self._record()

        try:
            ctx = self.harness.capture_context()
            self.repo_head = ctx.repo_head
            self.apk_sha256 = ctx.apk_sha256
            self.tools = list(ctx.tools)
            self.fixture_receipt_digest = ctx.fixture_receipt_digest
        except Exception as e:
            self.steps.append(_make_step(
                "capture_context", "capture context",
                CommandResult(argv=[], start_utc="", end_utc="",
                              returncode=1, stdout=b"",
                              stderr=str(e).encode()),
                TerminalCause.PREFLIGHT_FAILED))
            self.terminal = TerminalCause.PREFLIGHT_FAILED
            return self._record()
        if self._expected_apk_sha256 and ctx.apk_sha256 != self._expected_apk_sha256:
            self.terminal = TerminalCause.FIXTURE_MISMATCH
            return self._record()

        self._run_phase("emulator_launch", "boot pinned snapshot",
                        lambda: self.harness.launch_emulator())
        if self.terminal: return self._record()
        self._emulator_launched = True

        try:
            try:
                self._run_phase("attach", "adb attach serial",
                                lambda: self.harness.attach())
                if not self.terminal:
                    self._capture_prior()
                if not self.terminal and self.prior_state:
                    self._run_phase("validate_fixture", "fixture identity check",
                                    lambda: self.harness.validate_fixture(self.prior_state),
                                    TerminalCause.FIXTURE_MISMATCH)
                if not self.terminal:
                    self._guard_mutation("install")
                    self._run_phase("install", "install exact APK",
                                    lambda: self.harness.install_apk(),
                                    TerminalCause.INSTALL_FAILED)
                if not self.terminal:
                    self._guard_mutation("journey")
                    self._journey()
                if not self.terminal:
                    self._guard_mutation("capture")
                    self._run_phase("capture", "capture evidence",
                                    lambda: self.harness.capture_evidence(),
                                    TerminalCause.CAPTURE_FAILED)
            finally:
                self._restore()
                self._verify()
        finally:
            self._release_emulator()

        return self._record()

    def _run_phase(self, phase, operation, fn,
                   fail_cause=TerminalCause.TOOL_FAILURE):
        if self.terminal:
            return
        self._reached = phase
        result = fn()
        rc = _rc_of(result)
        cause = TerminalCause.COMPLETED if rc == 0 else fail_cause
        if _timed_out(result):
            cause = TerminalCause.TIMEOUT
        self.steps.append(_make_step(phase, operation, result, cause))
        if cause != TerminalCause.COMPLETED:
            self.terminal = cause

    def _capture_prior(self):
        self._reached = "prior_state"
        prior = self.harness.capture_prior_state()
        self.prior_state = prior
        ok = prior is not None
        if not ok:
            self.terminal = TerminalCause.PRIOR_STATE_UNAVAILABLE
        self.steps.append(_make_step(
            "prior_state", "capture prior device state",
            CommandResult(argv=[], start_utc="", end_utc="", returncode=0 if ok else 1,
                          stdout=b"prior-state-captured" if ok else b"", stderr=b""),
            TerminalCause.COMPLETED if ok else TerminalCause.PRIOR_STATE_UNAVAILABLE))

    def _journey(self):
        journey_steps = self.harness.run_journey()
        self.steps.extend(journey_steps)
        for s in journey_steps:
            if s.cause != TerminalCause.COMPLETED:
                self.terminal = s.cause
                return

    def _guard_mutation(self, phase):
        if self.prior_state is None:
            raise RuntimeError(
                f"{phase} attempted before prior-state capture"
            )

    def _restore(self):
        if self._restored:
            return
        self._restored = True
        if not self._emulator_launched:
            return
        try:
            result = self.harness.restore()
            rc = _rc_of(result)
            cause = TerminalCause.COMPLETED if rc == 0 else TerminalCause.CLEANUP_PARTIAL
        except Exception as e:
            result = CommandResult(
                argv=[], start_utc="", end_utc="",
                returncode=1, stdout=b"", stderr=str(e).encode())
            cause = TerminalCause.CLEANUP_PARTIAL
        self.restoration = _make_step(
            "restore", "restore device state", result, cause,
        )
        self.steps.append(self.restoration)
        if cause != TerminalCause.COMPLETED and self.terminal is None:
            self.terminal = cause

    def _verify(self):
        if self.prior_state is None: return
        self._reached = "verify_restore"
        try:
            actual = self.harness.verify_restore()
            if actual != self.prior_state:
                self.steps.append(_make_step(
                    "verify_restore", "compare restored state",
                    CommandResult(argv=[], start_utc="", end_utc="", returncode=1,
                                  stdout=b"mismatch", stderr=b""),
                    TerminalCause.RESTORATION_MISMATCH))
                if self.terminal is None:
                    self.terminal = TerminalCause.RESTORATION_MISMATCH
            else:
                self.steps.append(_make_step(
                    "verify_restore", "compare restored state",
                    CommandResult(argv=[], start_utc="", end_utc="", returncode=0,
                                  stdout=b"verified", stderr=b""),
                    TerminalCause.COMPLETED))
        except Exception as e:
            v_cause = TerminalCause.TOOL_FAILURE
            self.steps.append(_make_step(
                "verify_restore", "compare restored state",
                CommandResult(argv=[], start_utc="", end_utc="", returncode=1,
                              stdout=b"", stderr=str(e).encode()),
                v_cause))
            if self.terminal is None:
                self.terminal = v_cause

    def _release_emulator(self):
        if not self._emulator_launched:
            return
        result = self.harness.release_emulator()
        rc = _rc_of(result)
        cause = TerminalCause.COMPLETED if rc == 0 else TerminalCause.CLEANUP_PARTIAL
        if _timed_out(result):
            cause = TerminalCause.TIMEOUT
        self.steps.append(_make_step(
            "release_emulator", "release owned emulator", result, cause
        ))
        if cause != TerminalCause.COMPLETED and self.terminal is None:
            self.terminal = cause

        v_res = self.harness.verify_release()
        v_rc = _rc_of(v_res)
        v_cause = TerminalCause.COMPLETED if v_rc == 0 else TerminalCause.CLEANUP_PARTIAL
        if _timed_out(v_res):
            v_cause = TerminalCause.TIMEOUT
        self.steps.append(_make_step(
            "verify_release", "verify emulator release", v_res, v_cause
        ))
        if v_cause != TerminalCause.COMPLETED and self.terminal is None:
            self.terminal = v_cause

    def _record(self) -> CaptureRecord:
        return CaptureRecord(
            repo_head=self.repo_head,
            apk_sha256=self.apk_sha256,
            tools=list(self.tools),
            prior_state=self.prior_state,
            steps=list(self.steps),
            restoration=self.restoration,
            manifest_digest=None,
            visual_review=VisualReview.PENDING,
        )
