"""Local process execution, remote-result interface, and tool identity."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import os
import shutil
import signal
import subprocess
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


def start(
    argv: list[str],
    *,
    env: dict[str, str] | None = None,
    cwd: str | None = None,
    new_session: bool = False,
) -> ManagedProcess:
    start_time = _UTC()
    proc = subprocess.Popen(
        argv,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
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


def bounded_terminate(
    proc: subprocess.Popen,
    *,
    term_timeout: float = 5.0,
    kill_timeout: float = 5.0,
    group: bool = False,
) -> bool:
    """SIGTERM → bounded wait → SIGKILL → bounded wait.

    When *group* is True, signals the entire process group (the
    process must have been started with ``start_new_session=True``).
    Returns True if SIGKILL was required.
    """
    pid = proc.pid
    killed = False

    def _sig(sig: int) -> None:
        if group:
            os.killpg(os.getpgid(pid), sig)
        else:
            proc.send_signal(sig)

    try:
        _sig(signal.SIGTERM)
    except (OSError, ProcessLookupError):
        pass
    try:
        proc.communicate(timeout=term_timeout)
    except subprocess.TimeoutExpired:
        try:
            _sig(signal.SIGKILL)
            killed = True
        except (OSError, ProcessLookupError):
            pass
        try:
            proc.communicate(timeout=kill_timeout)
        except subprocess.TimeoutExpired:
            pass
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
