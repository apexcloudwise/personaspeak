"""Local process execution, remote-result interface, and tool identity."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import os
import shutil
import signal
import subprocess
import time
from datetime import datetime, timezone
from typing import Protocol

from android.scripts.m2_device.records import (
    CommandResult,
    RemoteResult,
    ToolIdentity,
)

_UTC = lambda: datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class RemoteStatusReader(Protocol):
    def extract_rc(self, transport: CommandResult) -> int | None:
        ...


class UnavailableReader:
    def extract_rc(self, transport: CommandResult) -> int | None:
        return None


class AdbRemoteStatusReader:
    """Determines remote exit code from adb shell_v2 transport results.

    Under shell_v2, the adb wrapper exit code IS the remote exit code
    (observed at 7, 5, 3, 2, 1, 0). Observed transport failures
    (probe P2: device-not-found and no-devices, five cases) produce
    rc=1 with non-empty stderr.

    Discriminator is structural (stderr emptiness), not text-matching:
    - rc != 1: unambiguous remote exit code
    - rc == 1, stderr empty: remote exit 1 (observed transport failures write stderr)
    - rc == 1, stderr non-empty: ambiguous, return None (fail closed)
    """

    def extract_rc(self, transport: CommandResult) -> int | None:
        if transport.timed_out:
            return None
        rc = transport.returncode
        if rc == 1 and transport.stderr:
            return None
        return rc


@dataclass(frozen=True)
class ManagedProcess:
    proc: subprocess.Popen
    argv: list[str]
    start_utc: str
    new_session: bool = False


@dataclass(frozen=True)
class ProcessIdentity:
    """Observed identity of a running process.

    ``start`` is the lstart string from ps (second-resolution).
    ``command`` is the full command line (executable + arguments).
    Together they detect PID reuse: a different start time or a
    different command means the original process is gone.
    """
    start: str
    command: str


class RemoteAmbiguousError(Exception):
    """Remote command status could not be determined (remote_rc=None)."""


class SignalInterrupt(BaseException):
    """SIGINT/SIGTERM arrived while an active phase was executing.

    Deliberately a BaseException: phase code that catches ``Exception``
    must not convert an interrupt into an ordinary tool failure. The
    orchestrator catches it above the cleanup ``finally`` blocks, so
    restore/release still run. Execution helpers kill and reap the
    interrupted child before re-raising.
    """

    def __init__(self, signum: int):
        super().__init__(f"signal {signum}")
        self.signum = signum


@dataclass(frozen=True)
class LedgerEntry:
    argv: list[str]
    start_utc: str
    end_utc: str
    transport_rc: int
    remote_rc: int | None
    timed_out: bool
    kind: str


class CommandLedger:
    """Private structured/redacted command ledger.

    Records exact argv and status dimensions for every production
    command. Content (stdout/stderr) is never stored — redacted by
    design.
    """

    def __init__(self):
        self._entries: list[LedgerEntry] = []

    def record(
        self, argv, start_utc, end_utc,
        transport_rc, remote_rc=None, timed_out=False, kind="host",
    ):
        self._entries.append(LedgerEntry(
            argv=list(argv), start_utc=start_utc, end_utc=end_utc,
            transport_rc=transport_rc, remote_rc=remote_rc,
            timed_out=timed_out, kind=kind,
        ))

    def entries(self) -> list[LedgerEntry]:
        return list(self._entries)

    def __len__(self):
        return len(self._entries)

    def serialize(self) -> str:
        return json.dumps([
            {"argv": e.argv, "start_utc": e.start_utc, "end_utc": e.end_utc,
             "transport_rc": e.transport_rc, "remote_rc": e.remote_rc,
             "timed_out": e.timed_out, "kind": e.kind}
            for e in self._entries
        ], indent=2)


def _reap_after_kill(proc: subprocess.Popen) -> None:
    try:
        proc.kill()
    except OSError:
        pass
    try:
        proc.communicate(timeout=5.0)
    except (subprocess.TimeoutExpired, OSError):
        pass


def start(
    argv: list[str],
    *,
    env: dict[str, str] | None = None,
    cwd: str | None = None,
    new_session: bool = False,
    stdout: int | None = None,
    stderr: int | None = None,
) -> ManagedProcess:
    start_time = _UTC()
    proc = subprocess.Popen(
        argv,
        stdout=stdout if stdout is not None else subprocess.PIPE,
        stderr=stderr if stderr is not None else subprocess.PIPE,
        env=env,
        cwd=cwd,
        start_new_session=new_session,
    )
    return ManagedProcess(
        proc=proc, argv=list(argv), start_utc=start_time,
        new_session=new_session,
    )


def finish(
    process: ManagedProcess,
    *,
    timeout: float | None = None,
    terminate: bool = True,
) -> CommandResult:
    proc = process.proc
    timed_out = False
    if terminate:
        try:
            proc.terminate()
        except OSError:
            pass

    try:
        stdout, stderr = proc.communicate(timeout=timeout)
        rc = proc.returncode
    except SignalInterrupt:
        _reap_after_kill(proc)
        raise
    except subprocess.TimeoutExpired:
        try:
            proc.kill()
        except OSError:
            pass
        try:
            stdout, stderr = proc.communicate(timeout=5.0)
        except subprocess.TimeoutExpired:
            stdout, stderr = b"", b""
        rc = proc.returncode if proc.returncode is not None else -9
        timed_out = True
    except Exception:
        try:
            proc.kill()
        except OSError:
            pass
        try:
            proc.communicate(timeout=5.0)
        except subprocess.TimeoutExpired:
            pass
        raise

    end = _UTC()
    return CommandResult(
        argv=process.argv,
        start_utc=process.start_utc,
        end_utc=end,
        returncode=rc if rc is not None else -9,
        stdout=stdout or b"",
        stderr=stderr or b"",
        timed_out=timed_out,
    )


@dataclass(frozen=True)
class TerminateOutcome:
    """Result of bounded_terminate.

    ``killed`` is True when SIGKILL was required. ``group_extinct`` is
    True when the stored process group no longer exists (always True
    when group=False); a False value means descendants survived
    escalation and the caller must not report a clean release.
    """
    killed: bool
    group_extinct: bool = True


def _group_alive(pgid: int) -> bool:
    try:
        os.killpg(pgid, 0)
    except ProcessLookupError:
        return False
    except OSError:
        # EPERM on a zombie-held group (macOS) — fall through.
        pass
    # killpg(0) succeeds for unreaped zombies (Linux) and is
    # inconclusive on macOS zombie groups; only a live member keeps
    # the group meaningfully alive. ps decides; on ps failure fail
    # conservative (alive).
    return _group_has_live_member(pgid)


def _group_has_live_member(pgid: int) -> bool:
    for ps_path in ("/bin/ps", "/usr/bin/ps"):
        if not (os.path.isfile(ps_path) and os.access(ps_path, os.X_OK)):
            continue
        try:
            listing = subprocess.run(
                [ps_path, "-eo", "pgid=,stat="],
                capture_output=True, timeout=5,
            )
            if listing.returncode != 0:
                return True
            for line in listing.stdout.decode(
                    "utf-8", errors="replace").splitlines():
                fields = line.split(None, 1)
                if len(fields) == 2 and int(fields[0]) == pgid:
                    if not fields[1].strip().startswith("Z"):
                        return True
            return False
        except (OSError, ValueError, subprocess.TimeoutExpired):
            return True
    return True


def _wait_group_extinct(pgid: int, grace: float, interval: float = 0.05) -> bool:
    deadline = time.monotonic() + grace
    while True:
        if not _group_alive(pgid):
            return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(interval)


def bounded_terminate(
    proc: subprocess.Popen,
    *,
    term_timeout: float = 5.0,
    kill_timeout: float = 5.0,
    group: bool = False,
    extinct_grace: float = 1.0,
) -> TerminateOutcome:
    """SIGTERM → bounded wait → SIGKILL → bounded wait → extinction check.

    When *group* is True, signals the entire process group (the process
    must have been started with ``start_new_session=True``, so the group
    id equals the leader pid). The pgid is captured once, before any
    signal: if the leader exits after SIGTERM while a resistant
    descendant holds the group open, escalation and extinction checks
    still target the stored group instead of a failing re-lookup.
    """
    pid = proc.pid
    pgid: int | None = None
    if group:
        try:
            pgid = os.getpgid(pid)
        except (OSError, ProcessLookupError):
            # New-session leader: pgid == pid even after leader exit.
            pgid = pid
    killed = False

    def _sig(sig: int) -> None:
        if pgid is not None:
            try:
                os.killpg(pgid, sig)
            except (OSError, ProcessLookupError):
                pass
        else:
            try:
                proc.send_signal(sig)
            except (OSError, ProcessLookupError):
                pass

    _sig(signal.SIGTERM)
    try:
        proc.communicate(timeout=term_timeout)
    except subprocess.TimeoutExpired:
        pass
    if (pgid is not None and _group_alive(pgid)) or (
        pgid is None and proc.poll() is None
    ):
        # Leader resisted SIGTERM, or it exited while descendants hold
        # the stored group open — either way, escalate.
        _sig(signal.SIGKILL)
        killed = True
        try:
            proc.communicate(timeout=kill_timeout)
        except subprocess.TimeoutExpired:
            pass
    group_extinct = (
        _wait_group_extinct(pgid, extinct_grace) if pgid is not None else True
    )
    return TerminateOutcome(killed=killed, group_extinct=group_extinct)


def _wait_identity_gone(
    pid: int, identity: "ProcessIdentity", timeout: float,
    interval: float = 0.1,
) -> bool:
    """True when *pid* no longer runs *identity* within *timeout*.

    A pid now carrying a different identity means the original exited
    and the pid was reused — the original is gone, and the new
    occupant must not be signaled. A direct child that exited but was
    never reaped (zombie keeps its identity in ``ps``) is reaped here:
    when we are the parent, ``waitpid`` both detects and collects it.
    """
    deadline = time.monotonic() + timeout
    while True:
        try:
            waited, _ = os.waitpid(pid, os.WNOHANG)
            if waited == pid:
                return True
        except ChildProcessError:
            pass
        except OSError:
            pass
        current = pid_identity(pid)
        if current is None or current != identity:
            # Dead (possibly our own unreaped zombie — ps shows it as
            # defunct with a different command) or the pid was reused.
            # Either way the original process is gone and must not be
            # signaled; reap if it is ours.
            try:
                os.waitpid(pid, os.WNOHANG)
            except (ChildProcessError, OSError):
                pass
            return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(interval)


def bounded_terminate_pid(
    pid: int,
    identity: "ProcessIdentity",
    *,
    term_timeout: float = 3.0,
    kill_timeout: float = 3.0,
) -> bool:
    """Bounded validated lifecycle for a process whose handle was dropped.

    SIGTERM → bounded identity wait → SIGKILL → bounded wait. The
    identity is validated before every signal, including the first; a
    pid reuse is never signaled. Returns True when SIGKILL was required.
    """
    current = pid_identity(pid)
    if current is None or current != identity:
        return False
    try:
        os.kill(pid, signal.SIGTERM)
    except (OSError, ProcessLookupError):
        return False
    if _wait_identity_gone(pid, identity, term_timeout):
        return False
    killed = True
    try:
        os.kill(pid, signal.SIGKILL)
    except (OSError, ProcessLookupError):
        pass
    _wait_identity_gone(pid, identity, kill_timeout)
    return killed


def pid_identity(pid: int) -> ProcessIdentity | None:
    """Observed process identity (start + full command) or None if gone.

    Tries ``/bin/ps`` then ``/usr/bin/ps``. Two queries: ``lstart=``
    for start time (PID-reuse detection) and ``command=`` for the
    full command line (executable + args, read from the process
    itself — not copied from expectations).
    """
    for ps_path in ("/bin/ps", "/usr/bin/ps"):
        if not (os.path.isfile(ps_path) and os.access(ps_path, os.X_OK)):
            continue
        try:
            r_start = subprocess.run(
                [ps_path, "-p", str(pid), "-o", "lstart="],
                capture_output=True, timeout=3,
            )
            if r_start.returncode != 0:
                return None
            r_cmd = subprocess.run(
                [ps_path, "-p", str(pid), "-o", "command="],
                capture_output=True, timeout=3,
            )
            if r_cmd.returncode != 0:
                return None
            return ProcessIdentity(
                start=r_start.stdout.decode("utf-8", errors="replace").strip(),
                command=r_cmd.stdout.decode("utf-8", errors="replace").strip(),
            )
        except (OSError, subprocess.TimeoutExpired):
            pass
    return None


def run(
    argv: list[str],
    *,
    timeout: float | None = None,
    env: dict[str, str] | None = None,
    cwd: str | None = None,
) -> CommandResult:
    start_time = _UTC()
    proc = subprocess.Popen(
        argv,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=env,
        cwd=cwd,
    )
    timed_out = False
    try:
        stdout, stderr = proc.communicate(timeout=timeout)
        rc = proc.returncode
    except SignalInterrupt:
        _reap_after_kill(proc)
        raise
    except subprocess.TimeoutExpired:
        proc.kill()
        try:
            stdout, stderr = proc.communicate(timeout=5.0)
        except subprocess.TimeoutExpired:
            stdout, stderr = b"", b""
        rc = proc.returncode if proc.returncode is not None else -9
        timed_out = True
    end = _UTC()
    return CommandResult(
        argv=list(argv),
        start_utc=start_time,
        end_utc=end,
        returncode=rc,
        stdout=stdout or b"",
        stderr=stderr or b"",
        timed_out=timed_out,
    )


def run_remote(
    argv: list[str],
    *,
    reader: RemoteStatusReader | None = None,
    timeout: float | None = None,
    env: dict[str, str] | None = None,
    cwd: str | None = None,
) -> RemoteResult:
    r = reader or AdbRemoteStatusReader()
    transport = run(argv, timeout=timeout, env=env, cwd=cwd)
    remote_rc = r.extract_rc(transport)
    return RemoteResult(transport=transport, remote_rc=remote_rc)


def to_remote(transport: CommandResult) -> RemoteResult:
    return RemoteResult(
        transport=transport,
        remote_rc=AdbRemoteStatusReader().extract_rc(transport),
    )


def remote_stdout(res: CommandResult | RemoteResult) -> str:
    if isinstance(res, RemoteResult):
        if res.remote_rc is None:
            raise ValueError("remote status ambiguous")
        if res.remote_rc != 0:
            raise ValueError(f"remote rc={res.remote_rc}")
        return res.transport.stdout.decode("utf-8").strip()
    if res.returncode != 0:
        raise ValueError(f"command rc={res.returncode}")
    return res.stdout.decode("utf-8").strip()


def digest_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def resolve_tool(
    name: str,
    *,
    path: str | None = None,
    version_args: list[str] | None = None,
    timeout: float = 10,
) -> ToolIdentity:
    resolved = shutil.which(path or name)
    if resolved is None:
        raise FileNotFoundError(f"tool not found: {name}")
    v_args = version_args if version_args is not None else ["--version"]
    cr = run([resolved] + v_args, timeout=timeout)
    text = cr.stdout.strip() or cr.stderr.strip()
    if text:
        version = text.decode("utf-8", errors="replace").split("\n")[0]
    else:
        raise RuntimeError(f"no version output from {name}")
    digest = None
    if os.path.isfile(resolved) and os.access(resolved, os.R_OK):
        try:
            digest = digest_file(resolved)
        except OSError:
            pass
    return ToolIdentity(
        name=name, path=resolved, version=version, digest=digest
    )
