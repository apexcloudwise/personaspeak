"""Argument parsing and capture/finalize entry points."""

from __future__ import annotations

import argparse
import dataclasses
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
    TerminalCause,
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

    fixture_digests = None
    if args.fixture_digests:
        with open(args.fixture_digests) as f:
            fixture_digests = json.load(f)
    harness = AdbHarness(
        run_dir=run_dir, apk_path=args.apk_path, repo_root=args.repo_root,
        fixture_root=args.fixture_root, fixture_digests=fixture_digests,
        headless=args.headless,
    )
    orchestrator = Orchestrator(
        harness=harness, apk_sha256=args.apk_sha256,
        expected_head=args.expected_head,
    )

    record = orchestrator.execute()

    evidence_dir = os.path.join(run_dir, "artifacts")
    manifest = None
    manifest_error = None
    if os.path.isdir(evidence_dir):
        try:
            manifest = evidence.build_manifest(evidence_dir)
            manifest_d = evidence.manifest_digest(manifest)
            record = dataclasses.replace(record, manifest_digest=manifest_d)
            evidence.write_private_atomic(
                os.path.join(run_dir, "manifest.json"),
                json.dumps(manifest, sort_keys=True, indent=2).encode(),
            )
        except ValueError as e:
            # A failed run may leave partial artifacts; the record is
            # still written — canonical binding gates the success
            # return below, not the failure record. Adversarial
            # manifest findings (links, escapes) surface in the failure
            # text rather than masquerading as "no artifacts".
            manifest_error = e

    evidence.write_private_atomic(
        os.path.join(run_dir, "capture-record.json"), encode(record))

    if orchestrator.terminal is not None:
        print(f"qualification failed: {orchestrator.terminal}", file=sys.stderr)
        return 1

    # Capture-manifest binding before success: the run's artifacts must
    # be exactly the canonical set, or the capture does not succeed.
    if manifest is None:
        reason = manifest_error if manifest_error else "no artifacts produced"
        print(f"qualification failed: {reason}", file=sys.stderr)
        return 1
    try:
        evidence.enforce_canonical_set(manifest)
    except ValueError as e:
        print(f"qualification failed: {e}", file=sys.stderr)
        return 1

    required = {"verify_restore", "release_emulator", "verify_release"}
    found = {s.phase for s in record.steps}
    incomplete = []
    if record.restoration is None or record.restoration.cause != TerminalCause.COMPLETED:
        incomplete.append("restoration")
    for phase in required:
        phase_steps = [s for s in record.steps if s.phase == phase]
        if not phase_steps:
            incomplete.append(f"{phase}(missing)")
        elif phase_steps[-1].cause != TerminalCause.COMPLETED:
            incomplete.append(phase)
    if incomplete:
        print(f"qualification incomplete: {incomplete}", file=sys.stderr)
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
    evidence_subdir = os.path.join(args.run_dir, "artifacts")
    if not os.path.isdir(evidence_subdir):
        evidence_subdir = args.run_dir
    receipt = evidence.finalize(
        capture, approval, manifest, evidence_subdir,
        evidence_commit=args.evidence_commit,
    )
    out = encode(receipt)
    if args.output:
        evidence.write_private_atomic(args.output, out)
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
        evidence.write_private_atomic(args.output, out)
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
    cap.add_argument("--expected-head", default="")
    cap.add_argument("--fixture-root", default="",
                     help="AVD root holding M2_Qual_Fixture.avd "
                          "(default: ~/.android/avd)")
    cap.add_argument("--fixture-digests", default="",
                     help="JSON file of fixture-relative-path → sha256 for "
                          "fake-only runs. Mechanical boundary: providing "
                          "it blanks the recorded fixture receipt digest, "
                          "so the run cannot claim the accepted fixture")
    cap.add_argument("--headless", action="store_true",
                     help="diagnostic only (#82): launch without a window "
                          "when no WindowServer context exists; the "
                          "counted qualification always runs windowed")
    cap.set_defaults(func=cmd_capture)

    fin = sub.add_parser("finalize", help="produce final receipt")
    fin.add_argument("--capture-record", required=True)
    fin.add_argument("--approval", required=True)
    fin.add_argument("--manifest", required=True)
    fin.add_argument("--run-dir", required=True)
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
