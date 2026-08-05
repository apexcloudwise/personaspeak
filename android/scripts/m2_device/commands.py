"""Local process execution, remote-result interface, and tool identity."""

from __future__ import annotations

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


def run(
    argv: list[str],
    *,
    timeout: float | None = None,
    env: dict[str, str] | None = None,
    cwd: str | None = None,
) -> CommandResult:
    start = _UTC()
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
        start_utc=start,
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
    r = reader or UnavailableReader()
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
    version_line = cr.stdout.decode("utf-8", errors="replace").strip()
    version = version_line.split("\n")[0] if version_line else f"rc={cr.returncode}"
    digest = None
    if os.path.isfile(resolved) and os.access(resolved, os.R_OK):
        try:
            digest = digest_file(resolved)
        except OSError:
            pass
    return ToolIdentity(
        name=name, path=resolved, version=version, digest=digest
    )
