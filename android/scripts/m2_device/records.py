"""Immutable typed records and deterministic JSON codec for M2 qualification."""

from __future__ import annotations

import base64
import binascii
import hashlib
import json
import types
import typing
from dataclasses import dataclass, fields as dc_fields
from enum import StrEnum

SCHEMA_VERSION = 1

_REGISTRY: dict[str, type[Record]] = {}


class TerminalCause(StrEnum):
    COMPLETED = "completed"
    PREFLIGHT_FAILED = "preflight_failed"
    PRIOR_STATE_UNAVAILABLE = "prior_state_unavailable"
    FIXTURE_MISMATCH = "fixture_mismatch"
    INSTALL_FAILED = "install_failed"
    JOURNEY_FAILED = "journey_failed"
    CAPTURE_FAILED = "capture_failed"
    RESTORATION_MISMATCH = "restoration_mismatch"
    SIGNAL_INTERRUPT = "signal_interrupt"
    TIMEOUT = "timeout"
    TOOL_FAILURE = "tool_failure"
    CLEANUP_PARTIAL = "cleanup_partial"


class VisualReview(StrEnum):
    PENDING = "pending"
    APPROVED = "approved"
    REJECTED = "rejected"


def _register(kind: str):
    def deco(cls):
        cls._kind = kind
        _REGISTRY[kind] = cls
        return cls
    return deco


def _encode_value(v):
    if isinstance(v, Record):
        return v.to_dict()
    if isinstance(v, bytes):
        return base64.b64encode(v).decode("ascii")
    if isinstance(v, StrEnum):
        return str(v)
    if isinstance(v, (list, tuple)):
        return [_encode_value(x) for x in v]
    if isinstance(v, dict):
        return {k: _encode_value(x) for k, x in v.items()}
    return v


def _decode_value(v, hint):
    origin = typing.get_origin(hint)
    if origin is types.UnionType or origin is typing.Union:
        args = typing.get_args(hint)
        if v is None and type(None) in args:
            return None
        concrete = [a for a in args if a is not type(None)]
        if len(concrete) == 1:
            return _decode_value(v, concrete[0])
        rec_args = [a for a in concrete
                    if isinstance(a, type) and issubclass(a, Record)]
        if len(rec_args) == len(concrete) and isinstance(v, dict):
            kind = v.get("kind")
            actual = _REGISTRY.get(kind)
            if actual is not None and actual in concrete:
                return actual.from_dict(v)
        raise ValueError(f"unsupported union type: {hint}")
    if hint is bytes:
        if not isinstance(v, str):
            raise ValueError(f"expected base64 str for bytes field")
        try:
            return base64.b64decode(v, validate=True)
        except (binascii.Error, ValueError) as exc:
            raise ValueError(f"malformed base64: {exc}")
    if hint in (bool, int, str):
        if not isinstance(v, hint) or (hint is int and isinstance(v, bool)):
            raise ValueError(f"expected {hint.__name__}, got {type(v).__name__}")
        return v
    if isinstance(hint, type) and issubclass(hint, StrEnum):
        try:
            return hint(v)
        except ValueError:
            raise ValueError(f"invalid {hint.__name__} value: {v!r}")
    if origin is list:
        if not isinstance(v, list):
            raise ValueError(f"expected list, got {type(v).__name__}")
        (inner,) = typing.get_args(hint) or (typing.Any,)
        return [_decode_value(x, inner) for x in v]
    if origin is dict:
        if not isinstance(v, dict):
            raise ValueError(f"expected dict, got {type(v).__name__}")
        k_hint, val_hint = typing.get_args(hint) or (str, typing.Any)
        return {
            _decode_value(k, k_hint): _decode_value(x, val_hint)
            for k, x in v.items()
        }
    if isinstance(hint, type) and issubclass(hint, Record):
        if not isinstance(v, dict):
            raise ValueError(f"expected record dict, got {type(v).__name__}")
        kind = v.get("kind")
        actual = _REGISTRY.get(kind)
        if actual is None:
            raise ValueError(f"unknown kind: {kind!r}")
        if hint is not Record and actual is not hint:
            raise ValueError(
                f"kind {kind!r} does not match expected {hint._kind}"
            )
        return actual.from_dict(v)
    return v


def _reject_dupes(pairs):
    result: dict = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate key in JSON: {key!r}")
        result[key] = value
    return result


@dataclass(frozen=True)
class Record:
    _kind: typing.ClassVar[str]

    def to_dict(self) -> dict:
        out: dict = {"schema": SCHEMA_VERSION, "kind": self._kind}
        for f in dc_fields(self):
            out[f.name] = _encode_value(getattr(self, f.name))
        return out

    @classmethod
    def from_dict(cls, d: dict) -> Record:
        if not isinstance(d, dict):
            raise ValueError("record must be a JSON object")
        kind = d.get("kind")
        schema = d.get("schema")
        if schema != SCHEMA_VERSION:
            raise ValueError(
                f"unsupported schema version: {schema!r} (expected {SCHEMA_VERSION})"
            )
        actual = _REGISTRY.get(kind)
        if actual is None:
            raise ValueError(f"unknown record kind: {kind!r}")
        target = actual if cls is Record else cls
        if target is not actual and target is not Record:
            raise ValueError(
                f"kind {kind!r} does not match expected {target._kind}"
            )
        hints = typing.get_type_hints(target)
        expected = {f.name for f in dc_fields(target)} | {"schema", "kind"}
        extra = set(d) - expected
        if extra:
            raise ValueError(f"unexpected keys in {kind!r}: {sorted(extra)}")
        kwargs: dict = {}
        for f in dc_fields(target):
            if f.name not in d:
                raise ValueError(f"missing field in {kind!r}: {f.name}")
            kwargs[f.name] = _decode_value(d[f.name], hints[f.name])
        return target(**kwargs)

def record_digest(record: Record) -> str:
    return hashlib.sha256(encode(record)).hexdigest()


def encode(record: Record) -> bytes:
    return json.dumps(
        record.to_dict(),
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")


def decode(data: bytes | str) -> Record:
    if isinstance(data, bytes):
        data = data.decode("utf-8")
    d = json.loads(data, object_pairs_hook=_reject_dupes)
    return Record.from_dict(d)


@dataclass(frozen=True)
class CaptureContext:
    repo_head: str
    apk_sha256: str
    tools: list[ToolIdentity]


@dataclass(frozen=True)
@_register("tool_identity")
class ToolIdentity(Record):
    name: str
    path: str
    version: str
    digest: str | None = None


@dataclass(frozen=True)
@_register("command_result")
class CommandResult(Record):
    argv: list[str]
    start_utc: str
    end_utc: str
    returncode: int
    stdout: bytes
    stderr: bytes
    timed_out: bool = False


@dataclass(frozen=True)
@_register("remote_result")
class RemoteResult(Record):
    transport: CommandResult
    remote_rc: int | None = None

    @property
    def remote_available(self) -> bool:
        return self.remote_rc is not None


@dataclass(frozen=True)
@_register("prior_device_state")
class PriorDeviceState(Record):
    serial: str
    emulator_state: str
    fingerprint: str
    api_level: int
    screen_width: int
    screen_height: int
    package_present: bool
    package_hash: str | None
    enabled_imes: list[str]
    default_ime: str


@dataclass(frozen=True)
@_register("step_record")
class StepRecord(Record):
    phase: str
    operation: str
    input_digest: str | None
    output_digest: str | None
    result: CommandResult | RemoteResult
    cause: TerminalCause


@dataclass(frozen=True)
@_register("capture_record")
class CaptureRecord(Record):
    repo_head: str
    apk_sha256: str
    tools: list[ToolIdentity]
    prior_state: PriorDeviceState | None
    steps: list[StepRecord]
    restoration: StepRecord | None
    manifest_digest: str | None
    visual_review: VisualReview = VisualReview.PENDING


@dataclass(frozen=True)
@_register("approval_record")
class ApprovalRecord(Record):
    reviewer: str
    capture_digest: str
    manifest_digest: str
    decision: VisualReview
    approved_utc: str


@dataclass(frozen=True)
@_register("final_receipt")
class FinalReceipt(Record):
    capture_digest: str
    approval_digest: str
    privacy_ok: bool
    media_ok: bool
    restoration_verdict: str
    counts: dict[str, int]
    evidence_commit: str
    artifacts: dict[str, str]
