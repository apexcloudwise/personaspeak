"""Digests, privacy checks, media validation, manifests, and finalize."""

from __future__ import annotations

import hashlib
import json
import os
import re
import struct
import tempfile
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path

from android.scripts.m2_device.records import (
    ApprovalRecord,
    CaptureRecord,
    FinalReceipt,
    TerminalCause,
    VisualReview,
    encode,
    record_digest,
)

_CREDENTIAL_PATTERNS = [
    re.compile(rb"(?i)(api[_-]?key|secret|password|token|bearer)"
               rb"\s*[=:]\s*\S{8,}"),
    re.compile(rb"sk-[a-zA-Z0-9]{20,}"),
    re.compile(rb"AIza[0-9A-Za-z_\-]{35}"),
]

# One exact flat artifact set (issue #64): seven named screenshots, one
# named video, the journey's required UI hierarchies, and the private
# redacted command ledger — which is also the run's status log: it
# records every production command's argv and status dimensions. Any
# manifest that is not exactly this set is rejected.
CANONICAL_PNG_NAMES = (
    "01-idle-typed", "02-loading-cancel", "03-review",
    "04-applied", "05-dismissed", "06-stale", "07-settings",
)
CANONICAL_MP4_NAME = "journey"
# Four editor sessions (open/verify/cancel, apply, dismiss, stale),
# each with its own home-entry and focus dumps, plus the post-restore
# pristine dump. Labels are inexpressible in the harness if wrong.
CANONICAL_HIERARCHY_LABELS = (
    "home_1", "focus_1", "typed_1", "after_cancel",
    "home_2", "focus_2", "typed_2", "after_apply",
    "home_3", "focus_3", "typed_3", "after_dismiss",
    "home_4", "focus_4", "typed_4", "typed_stale", "after_stale",
    "verify_restore",
)
# The emulator engine's own stdout/stderr, persisted by the harness
# from launch (defect F, #82): without it a launch/attach failure is
# undiagnosable from the run dir alone. Written by the launch step, so
# it is present in every capture record's manifest — completed or not.
CANONICAL_EMULATOR_LOG_NAME = "emulator.log"
CANONICAL_LEDGER_NAME = "command_ledger.json"
CANONICAL_ARTIFACTS = frozenset(
    f"{n}.png" for n in CANONICAL_PNG_NAMES
) | {f"{CANONICAL_MP4_NAME}.mp4"} | {
    f"{label}.xml" for label in CANONICAL_HIERARCHY_LABELS
} | {CANONICAL_LEDGER_NAME, CANONICAL_EMULATOR_LOG_NAME}


def _build_canonical_artifacts(png_names, hierarchy_labels) -> frozenset:
    return (
        frozenset(f"{n}.png" for n in png_names)
        | {f"{CANONICAL_MP4_NAME}.mp4"}
        | {f"{label}.xml" for label in hierarchy_labels}
        | {CANONICAL_LEDGER_NAME, CANONICAL_EMULATOR_LOG_NAME}
    )


# FlorisBoard second-host journey (ADR-0010 P2): four product sessions
# mirroring the ASK journey, plus the composing leg (draft typed
# without the final period, per ADR-0003) and the settings-surface
# session (the row's settings button launching the ported activity).
FLORIS_CANONICAL_PNG_NAMES = (
    "01-idle-typed", "02-loading-cancel", "03-review",
    "04-applied", "05-dismissed", "06-stale",
    "07-composing-typed", "08-composing-applied", "09-floris-settings",
)
FLORIS_CANONICAL_HIERARCHY_LABELS = (
    "home_1", "focus_1", "typed_1", "after_cancel",
    "home_2", "focus_2", "typed_2", "after_apply",
    "home_3", "focus_3", "typed_3", "after_dismiss",
    "home_4", "focus_4", "typed_4", "typed_stale", "after_stale",
    "home_5", "focus_5", "typed_5", "after_composing",
    "home_6", "focus_6", "floris_settings",
    "verify_restore",
)
FLORIS_CANONICAL_ARTIFACTS = _build_canonical_artifacts(
    FLORIS_CANONICAL_PNG_NAMES, FLORIS_CANONICAL_HIERARCHY_LABELS)
# The mirrored sessions keep the ASK artifact names (01-idle-typed and
# friends) so a shared step reads the same in both hosts' records; the
# sets must still differ — a floris manifest carries the composing and
# settings artifacts the ASK journey never produces, and finalize is
# told the host explicitly, so overlap is unambiguous.
assert FLORIS_CANONICAL_ARTIFACTS != CANONICAL_ARTIFACTS


def enforce_canonical_set(
    manifest: dict[str, str],
    artifacts: frozenset = CANONICAL_ARTIFACTS,
) -> None:
    """The manifest must be exactly the given canonical artifact set —
    no missing, extra, renamed, or nested entries. The default is the
    ASK-journey set (byte-frozen by the #62/#64 acceptance matrix); the
    floris journey passes its own."""
    actual = set(manifest)
    missing = sorted(artifacts - actual)
    extra = sorted(actual - artifacts)
    if missing:
        raise ValueError(f"canonical artifacts missing: {missing}")
    if extra:
        raise ValueError(f"non-canonical artifacts rejected: {extra}")


def write_private_atomic(path: str, data: bytes) -> None:
    """Private (0600) atomic write: same-dir temp file, fsync, replace,
    directory fsync. An interrupted write can never leave a truncated
    artifact behind a success code."""
    directory = os.path.dirname(os.path.abspath(path))
    tmp_path = None
    try:
        fd, tmp_path = tempfile.mkstemp(
            prefix="." + os.path.basename(path) + ".", dir=directory)
        with os.fdopen(fd, "wb") as fh:
            fh.write(data)
            fh.flush()
            os.fsync(fh.fileno())
        os.replace(tmp_path, path)
        tmp_path = None
        try:
            dir_fd = os.open(directory, os.O_RDONLY)
            try:
                os.fsync(dir_fd)
            finally:
                os.close(dir_fd)
        except OSError:
            pass  # best-effort: content durability holds via file fsync
    finally:
        if tmp_path is not None:
            try:
                os.unlink(tmp_path)
            except OSError:
                pass


def reject_nonflat(dir_path: str) -> None:
    """The canonical artifact set is flat: any subdirectory — real or
    symlinked, populated or empty — is rejected. File symlinks keep
    their own per-file rejection messages."""
    with os.scandir(dir_path) as entries:
        for entry in entries:
            is_dir_link = (
                entry.is_symlink() and entry.is_dir(follow_symlinks=True))
            if entry.is_dir(follow_symlinks=False) or is_dir_link:
                raise ValueError(
                    "non-flat evidence directory rejected:"
                    f" {entry.name} is a directory, not a flat artifact")


def scan_text(data: bytes) -> list[str]:
    findings = []
    for pat in _CREDENTIAL_PATTERNS:
        for m in pat.finditer(data):
            findings.append(m.group(0).decode("ascii", errors="replace")[:40])
    return findings


def scan_directory(dir_path: str) -> bool:
    for root, _, files in os.walk(dir_path):
        for f in files:
            path = os.path.join(root, f)
            try:
                with open(path, "rb") as fh:
                    if scan_text(fh.read()):
                        return False
            except OSError:
                return False
    return True


_PNG_SIG = b"\x89PNG\r\n\x1a\n"


def validate_png(data: bytes) -> bool:
    if not data.startswith(_PNG_SIG) or len(data) < 24:
        return False
    offset = 8
    saw_ihdr = False
    while offset < len(data):
        if offset + 8 > len(data):
            return False
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        ctype = data[offset + 4:offset + 8]
        chunk_end = offset + 12 + length
        if chunk_end > len(data):
            return False
        if ctype == b"IHDR":
            saw_ihdr = True
        crc_stored = struct.unpack(
            ">I", data[chunk_end - 4:chunk_end],
        )[0]
        crc_calc = zlib.crc32(data[offset + 4:offset + 8 + length]) & 0xFFFFFFFF
        if crc_stored != crc_calc:
            return False
        if ctype == b"IEND":
            return saw_ihdr and chunk_end == len(data)
        offset = chunk_end
    return False


def validate_mp4(data: bytes) -> bool:
    if len(data) < 16:
        return False
    offset = 0
    saw_ftyp = False
    saw_media = False
    while offset < len(data):
        if offset + 8 > len(data):
            return False
        size = struct.unpack(">I", data[offset:offset + 4])[0]
        btype = data[offset + 4:offset + 8]
        if size == 1:
            if offset + 16 > len(data):
                return False
            size = struct.unpack(">Q", data[offset + 8:offset + 16])[0]
        elif size == 0:
            size = len(data) - offset
        if size < 8 or offset + size > len(data):
            return False
        if btype == b"ftyp":
            saw_ftyp = True
        elif btype == b"moov":
            saw_media = True
        elif btype == b"mdat" and size > 8:
            saw_media = True
        offset += size
    return saw_ftyp and saw_media and offset == len(data)


def build_manifest(dir_path: str) -> dict[str, str]:
    reject_nonflat(dir_path)
    manifest: dict[str, str] = {}
    base = os.path.realpath(dir_path)
    for root, _, files in os.walk(dir_path):
        for f in sorted(files):
            p = os.path.join(root, f)
            if os.path.islink(p):
                raise ValueError(f"symlink rejected in evidence dir: {p}")
            if not os.path.isfile(p):
                raise ValueError(f"non-regular file in evidence dir: {p}")
            if os.stat(p).st_nlink > 1:
                raise ValueError(f"hard link rejected in evidence dir: {p}")
            real = os.path.realpath(p)
            if not (real == base or real.startswith(base + os.sep)):
                raise ValueError(f"path escape rejected: {p}")
            rel = str(Path(p).relative_to(Path(dir_path)))
            with open(p, "rb") as fh:
                manifest[rel] = hashlib.sha256(fh.read()).hexdigest()
    return dict(sorted(manifest.items()))


def manifest_digest(manifest: dict[str, str]) -> str:
    raw = json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(raw).hexdigest()


def check_evidence_root(path: str, repo_root: str) -> None:
    ap = os.path.realpath(path)
    repo = os.path.realpath(repo_root)
    if ap == repo or ap.startswith(repo + os.sep):
        raise ValueError("evidence root must be outside the repository")
    for tmp in ["/tmp", "/var/tmp", tempfile.gettempdir()]:
        tmp = os.path.realpath(tmp)
        if ap == tmp or ap.startswith(tmp + os.sep):
            raise ValueError("evidence root must not be in a temp directory")


def _validate_media(path: str, name: str) -> bool:
    try:
        with open(path, "rb") as f:
            data = f.read()
    except OSError:
        return False
    if name.endswith(".png"):
        return validate_png(data)
    if name.endswith(".mp4"):
        return validate_mp4(data)
    return True


_LEDGER_ENTRY_FIELDS = frozenset({
    "argv", "start_utc", "end_utc", "transport_rc",
    "remote_rc", "timed_out", "kind",
})
_LEDGER_KINDS = frozenset({"shell", "host", "launch"})


def _validate_ledger_entry(name: str, idx: int, entry: object) -> None:
    """Exact LedgerEntry serialized shape: field set, types, and known
    kind. Key presence alone is not a schema."""
    def bad(what: str) -> ValueError:
        return ValueError(f"ledger entry {idx} {what}: {name}")

    if not isinstance(entry, dict):
        raise bad("is not an object")
    if set(entry) != _LEDGER_ENTRY_FIELDS:
        raise bad("field set mismatch")
    if (not isinstance(entry["argv"], list)
            or not all(isinstance(a, str) for a in entry["argv"])):
        raise bad("argv is not list[str]")
    if (not isinstance(entry["start_utc"], str)
            or not isinstance(entry["end_utc"], str)):
        raise bad("timestamps are not strings")
    trc = entry["transport_rc"]
    if not isinstance(trc, int) or isinstance(trc, bool):
        raise bad("transport_rc is not an integer")
    rrc = entry["remote_rc"]
    if rrc is not None and (not isinstance(rrc, int)
                            or isinstance(rrc, bool)):
        raise bad("remote_rc is not int|null")
    if not isinstance(entry["timed_out"], bool):
        raise bad("timed_out is not a boolean")
    if entry["kind"] not in _LEDGER_KINDS:
        raise bad("kind is not one of shell/host/launch")


def validate_structural(name: str, data: bytes) -> None:
    """Structural validation for the canonical text artifacts: every
    hierarchy XML must parse with a <hierarchy> root; the command
    ledger must decode as a non-empty JSON list in the exact
    serialized LedgerEntry shape. Digest agreement never excuses
    malformed content."""
    if name.endswith(".xml"):
        try:
            root = ET.fromstring(data)
        except ET.ParseError:
            raise ValueError(f"malformed XML artifact: {name}")
        if root.tag != "hierarchy":
            raise ValueError(f"XML artifact root is not <hierarchy>: {name}")
    elif name == CANONICAL_LEDGER_NAME:
        try:
            entries = json.loads(data.decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            raise ValueError(f"malformed ledger JSON: {name}")
        if not isinstance(entries, list) or not entries:
            raise ValueError(
                f"ledger JSON must be a non-empty list: {name}")
        for idx, entry in enumerate(entries):
            _validate_ledger_entry(name, idx, entry)


def finalize(
    capture: CaptureRecord,
    approval: ApprovalRecord,
    manifest: dict[str, str],
    evidence_dir: str,
    *,
    evidence_commit: str = "",
    canonical_artifacts: frozenset = CANONICAL_ARTIFACTS,
) -> FinalReceipt:
    """Produce the final receipt with every dimension derived from the
    exact bytes and the named capture steps. No caller-supplied verdict,
    count, or artifact list survives into the receipt."""
    cap_d = record_digest(capture)
    appr_d = record_digest(approval)
    man_d = manifest_digest(manifest)
    if cap_d != approval.capture_digest:
        raise ValueError("capture-record digest drift since approval")
    if man_d != approval.manifest_digest:
        raise ValueError("manifest digest drift since approval")
    if approval.decision != VisualReview.APPROVED:
        raise ValueError(f"approval decision was {approval.decision!r}, not approved")
    if capture.manifest_digest is None:
        raise ValueError("capture record has no manifest digest")
    if capture.manifest_digest != man_d:
        raise ValueError("capture-record manifest_digest does not match supplied manifest")
    enforce_canonical_set(manifest, canonical_artifacts)
    reject_nonflat(evidence_dir)
    for name in manifest:
        if "/" in name or ".." in name or os.path.isabs(name):
            raise ValueError(f"manifest key traverses path: {name}")
        path = os.path.join(evidence_dir, name)
        if not os.path.isfile(path) or os.path.islink(path):
            raise ValueError(f"manifest entry is not a regular file: {name}")
        if os.stat(path).st_nlink > 1:
            raise ValueError(f"manifest entry has multiple hard links: {name}")
        with open(path, "rb") as fh:
            actual = hashlib.sha256(fh.read()).hexdigest()
        if actual != manifest[name]:
            raise ValueError(f"manifest file digest mismatch: {name}")
    actual_files: set[str] = set()
    for root, _, files in os.walk(evidence_dir):
        for f in files:
            p = os.path.join(root, f)
            rel = str(Path(p).relative_to(Path(evidence_dir)))
            actual_files.add(rel)
    extras = actual_files - set(manifest.keys())
    if extras:
        raise ValueError(f"unlisted files in evidence dir: {sorted(extras)}")
    if capture.restoration is not None:
        if capture.restoration.cause != TerminalCause.COMPLETED:
            raise ValueError(
                f"restoration step cause was {capture.restoration.cause!r}, not COMPLETED"
            )
    else:
        raise ValueError("capture record has no restoration step")

    # Derived dimensions: counts come from bytes; journey, release, and
    # verification verdicts come from the named capture steps. The
    # verdicts are enforced, not just recorded — a receipt is refused
    # when any of them did not complete.
    def _steps(phase: str):
        return [s for s in capture.steps if s.phase == phase]

    def _step_completed(phase: str) -> bool:
        steps = _steps(phase)
        return bool(steps) and steps[-1].cause == TerminalCause.COMPLETED

    verify_steps = _steps("verify_restore")
    if not verify_steps or verify_steps[-1].cause != TerminalCause.COMPLETED:
        raise ValueError(
            "verify_restore step missing or not COMPLETED — the snapshot "
            "loading is not itself a restoration verdict")
    for phase in ("release_emulator", "verify_release"):
        if not _step_completed(phase):
            raise ValueError(
                f"{phase} did not complete — receipt refused")
    journey_steps = [s for s in capture.steps if s.phase == "journey"]
    if not journey_steps:
        raise ValueError("no journey steps — receipt refused")
    failed_steps = [s for s in capture.steps
                    if s.cause != TerminalCause.COMPLETED]
    if failed_steps:
        first = failed_steps[0]
        raise ValueError(
            f"capture step {first.phase}/{first.operation} was "
            f"{first.cause}, not COMPLETED — receipt refused")

    derived_counts = {
        "png": sum(1 for n in manifest if n.endswith(".png")),
        "mp4": sum(1 for n in manifest if n.endswith(".mp4")),
        "journey_steps_completed": sum(
            1 for s in capture.steps
            if s.phase == "journey" and s.cause == TerminalCause.COMPLETED
        ),
        "release_ok": 1 if _step_completed("release_emulator") else 0,
        "verify_release_ok": 1 if _step_completed("verify_release") else 0,
    }
    privacy_ok = scan_directory(evidence_dir)
    if not privacy_ok:
        raise ValueError("privacy scan failed — credential patterns detected in evidence")
    media_files = [n for n in manifest if n.endswith((".png", ".mp4"))]
    if not media_files:
        raise ValueError("no media files in manifest")
    media_ok = all(
        _validate_media(os.path.join(evidence_dir, name), name)
        for name in media_files
    )
    if not media_ok:
        raise ValueError("media validation failed — one or more files are structurally invalid")
    for name in sorted(manifest):
        with open(os.path.join(evidence_dir, name), "rb") as fh:
            validate_structural(name, fh.read())
    return FinalReceipt(
        capture_digest=cap_d,
        approval_digest=appr_d,
        privacy_ok=privacy_ok,
        media_ok=media_ok,
        restoration_verdict="verified",
        counts=derived_counts,
        evidence_commit=evidence_commit,
        artifacts=dict(sorted(manifest.items())),
    )
