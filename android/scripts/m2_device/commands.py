"""Local process execution, remote-result interface, and tool identity."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import os
import shutil
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


def start(
    argv: list[str],
    *,
    env: dict[str, str] | None = None,
    cwd: str | None = None,
) -> ManagedProcess:
    start_time = _UTC()
    proc = subprocess.Popen(
        argv,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=env,
        cwd=cwd,
    )
    return ManagedProcess(proc=proc, argv=list(argv), start_utc=start_time)


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
        stdout, stderr = proc.communicate()
        rc = proc.returncode if proc.returncode is not None else -9
        timed_out = True
    except Exception:
        try:
            proc.kill()
        except OSError:
            pass
        proc.communicate()
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
        stdout, stderr = proc.communicate()
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
