"""Digests, privacy checks, media validation, manifests, and finalize."""

from __future__ import annotations

import hashlib
import json
import os
import re
import struct
import tempfile
import zlib
from pathlib import Path

from android.scripts.m2_device.records import (
    ApprovalRecord,
    CaptureRecord,
    FinalReceipt,
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
    manifest: dict[str, str] = {}
    base = Path(dir_path)
    for root, _, files in os.walk(dir_path):
        for f in sorted(files):
            p = os.path.join(root, f)
            rel = str(Path(p).relative_to(base))
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


def finalize(
    capture: CaptureRecord,
    approval: ApprovalRecord,
    manifest: dict[str, str],
    evidence_dir: str,
    *,
    restoration_verdict: str,
    counts: dict[str, int],
    evidence_commit: str,
    artifacts: dict[str, str],
) -> FinalReceipt:
    cap_d = record_digest(capture)
    appr_d = record_digest(approval)
    man_d = manifest_digest(manifest)
    if cap_d != approval.capture_digest:
        raise ValueError("capture-record digest drift since approval")
    if man_d != approval.manifest_digest:
        raise ValueError("manifest digest drift since approval")
    if approval.decision != VisualReview.APPROVED:
        raise ValueError(f"approval decision was {approval.decision!r}, not approved")
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
    return FinalReceipt(
        capture_digest=cap_d,
        approval_digest=appr_d,
        privacy_ok=privacy_ok,
        media_ok=media_ok,
        restoration_verdict=restoration_verdict,
        counts=dict(counts),
        evidence_commit=evidence_commit,
        artifacts=dict(artifacts),
    )
