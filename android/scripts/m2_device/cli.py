"""Argument parsing and capture/finalize entry points."""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone

from android.scripts.m2_device import evidence
from android.scripts.m2_device.adb_harness import AdbHarness
from android.scripts.m2_device.orchestrator import Orchestrator
from android.scripts.m2_device.records import (
    ApprovalRecord,
    CaptureRecord,
    VisualReview,
    decode,
    encode,
    record_digest,
)


def _utc() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def cmd_capture(args: argparse.Namespace) -> int:
    evidence.check_evidence_root(args.evidence_root, args.repo_root)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run_dir = os.path.join(args.evidence_root, run_id)
    os.makedirs(run_dir, exist_ok=False)

    harness = AdbHarness(run_dir=run_dir, apk_path=args.apk_path)
    orchestrator = Orchestrator(harness=harness)

    record = orchestrator.execute()
    record_path = os.path.join(run_dir, "capture-record.json")
    with open(record_path, "wb") as f:
        f.write(encode(record))

    if orchestrator.terminal is not None:
        print(f"qualification failed: {orchestrator.terminal}", file=sys.stderr)
        return 1

    return 0


def cmd_finalize(args: argparse.Namespace) -> int:
    with open(args.capture_record, "rb") as f:
        capture = decode(f.read())
    if not isinstance(capture, CaptureRecord):
        print("error: expected capture_record", file=sys.stderr)
        return 1
    with open(args.approval, "rb") as f:
        approval = decode(f.read())
    if not isinstance(approval, ApprovalRecord):
        print("error: expected approval_record", file=sys.stderr)
        return 1
    with open(args.manifest) as f:
        manifest = json.load(f)
    receipt = evidence.finalize(
        capture, approval, manifest, args.run_dir,
        restoration_verdict=args.restoration_verdict,
        counts=json.loads(args.counts),
        evidence_commit=args.evidence_commit,
        artifacts={name: manifest[name] for name in sorted(manifest)},
    )
    out = encode(receipt)
    if args.output:
        with open(args.output, "wb") as f:
            f.write(out)
    else:
        sys.stdout.buffer.write(out)
    return 0


def cmd_approve(args: argparse.Namespace) -> int:
    with open(args.capture_record, "rb") as f:
        capture = decode(f.read())
    if not isinstance(capture, CaptureRecord):
        print("error: expected capture_record", file=sys.stderr)
        return 1
    cap_digest = record_digest(capture)
    with open(args.manifest) as f:
        man_digest = evidence.manifest_digest(json.load(f))
    rec = ApprovalRecord(
        reviewer=args.reviewer,
        capture_digest=cap_digest,
        manifest_digest=man_digest,
        decision=VisualReview.APPROVED,
        approved_utc=_utc(),
    )
    out = encode(rec)
    if args.output:
        with open(args.output, "wb") as f:
            f.write(out)
    else:
        sys.stdout.buffer.write(out)
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="m2-device",
        description="M2 device qualification orchestrator",
    )
    sub = p.add_subparsers(dest="command", required=True)

    cap = sub.add_parser("capture", help="run device qualification capture")
    cap.add_argument("--evidence-root", required=True)
    cap.add_argument("--repo-root", required=True)
    cap.add_argument("--apk-path", required=True)
    cap.add_argument("--apk-sha256", required=True)
    cap.set_defaults(func=cmd_capture)

    fin = sub.add_parser("finalize", help="produce final receipt")
    fin.add_argument("--capture-record", required=True)
    fin.add_argument("--approval", required=True)
    fin.add_argument("--manifest", required=True)
    fin.add_argument("--run-dir", required=True)
    fin.add_argument("--restoration-verdict", default="verified")
    fin.add_argument("--counts", default="{}")
    fin.add_argument("--evidence-commit", default="")
    fin.add_argument("--output", default=None)
    fin.set_defaults(func=cmd_finalize)

    appr = sub.add_parser("approve", help="create approval record")
    appr.add_argument("--capture-record", required=True)
    appr.add_argument("--manifest", required=True)
    appr.add_argument("--reviewer", required=True)
    appr.add_argument("--output", default=None)
    appr.set_defaults(func=cmd_approve)

    return p


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
