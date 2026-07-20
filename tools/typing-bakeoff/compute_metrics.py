#!/usr/bin/env python3
"""Reduce bake-off run records into per-keyboard metric tables.

Reads one or more JSONL files produced by adb_replay.py and emits either
human-readable Markdown tables (default) or JSON. Pairs with the empty
results template in
docs/superpowers/specs/2026-07-21-typing-bakeoff-results-template.md.

Usage:

    python compute_metrics.py runs/heliboard-domain.jsonl \\
        --keyboard HeliBoard \\
        --label "domain set"
    python compute_metrics.py runs/*-domain.jsonl --format markdown

Each input record has the shape:

    {"phrase_id", "presented", "transcribed", "method",
     "elapsed_seconds", "input_stream_length", "fixes", "note"}

The per-phrase block emits the Soukoreff-MacKenzie rates (via
metrics.error_rates) plus WPM and KSPC. The aggregate block pools KSPC
across phrases and arithmetic-means the rest.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import metrics as m  # noqa: E402


def load_records(path: Path) -> list[dict]:
    records = []
    with path.open() as f:
        for line_no, line in enumerate(f, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                records.append(json.loads(line))
            except json.JSONDecodeError as e:
                raise SystemExit(f"{path}:{line_no}: bad JSON record ({e})")
    return records


def enrich(rec: dict) -> dict:
    """Add derived metric fields to a single record."""
    rates = m.error_rates(rec.get("presented", ""), rec.get("transcribed", ""),
                          fixes=rec.get("fixes", 0) or 0)
    chars = len(rec.get("transcribed", "") or "")
    enriched = dict(rec)
    enriched.update(
        {
            "transcribed_chars": chars,
            "wpm": m.wpm(chars, float(rec.get("elapsed_seconds", 0.0) or 0.0)),
            "uncorrected_error_rate": rates.uncorrected,
            "corrected_error_rate": rates.corrected,
            "total_error_rate": rates.total,
            "msd_distance": rates.msd_distance,
            "kspc": m.kspc(int(rec.get("input_stream_length", 0) or 0), chars),
        }
    )
    return enriched


def render_markdown(label: str, per_phrase: list[dict], aggregate: dict) -> str:
    lines = [f"### {label}", ""]
    lines.append("| metric | value |")
    lines.append("|---|---|")
    for key in ("wpm", "uncorrected_error_rate", "corrected_error_rate",
                "total_error_rate", "kspc", "phrases"):
        v = aggregate.get(key, 0.0)
        if isinstance(v, float):
            lines.append(f"| {key} | {v:.4f} |")
        else:
            lines.append(f"| {key} | {v} |")
    lines.append("")
    if per_phrase:
        lines.append("Per-phrase (first 10 shown, full set in the JSONL):")
        lines.append("")
        lines.append("| phrase_id | presented | transcribed | WPM | UER | CER | TER | KSPC |")
        lines.append("|---|---|---|---|---|---|---|---|")
        for r in per_phrase[:10]:
            lines.append(
                f"| {r['phrase_id']} | {r['presented']!r} | {r['transcribed']!r} "
                f"| {r['wpm']:.2f} | {r['uncorrected_error_rate']:.3f} "
                f"| {r['corrected_error_rate']:.3f} | {r['total_error_rate']:.3f} "
                f"| {r['kspc']:.3f} |"
            )
        lines.append("")
    return "\n".join(lines)


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("inputs", nargs="+", type=Path, help="One or more run JSONL files")
    p.add_argument("--keyboard", default=None, help="Label for the keyboard (Markdown heading)")
    p.add_argument("--label", default=None, help="Label for the corpus/set")
    p.add_argument("--format", choices=["markdown", "json"], default="markdown")
    args = p.parse_args()

    for path in args.inputs:
        records = [enrich(r) for r in load_records(path)]
        agg = m.aggregate(records)
        label_parts = [s for s in (args.keyboard, args.label, path.stem) if s]
        label = " / ".join(label_parts) or str(path)
        if args.format == "markdown":
            print(render_markdown(label, records, agg))
        else:
            print(json.dumps({"label": label, "aggregate": agg, "per_phrase": records},
                             ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
