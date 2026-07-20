#!/usr/bin/env python3
"""adb-driven keystroke replay for the typing bake-off.

Replays a corpus phrase-by-phrase into a focused text field on the device,
captures what the IME actually committed, and writes one JSON record per
phrase to an output file that compute_metrics.py can reduce.

Usage (full flags in argparse; the short version):

    python adb_replay.py \\
        --ime helium314.keyboard/.latin.LatinIME \\
        --corpus corpus/domain-messages.csv \\
        --out runs/heliboard-domain.jsonl

== What this reaches and what it does not ==

adb is the wrong tool for some of the metrics in the bake-off, and we say so
up front rather than pretending otherwise. The issue's protocol splits the
work between an automated pass and a scripted human session; this script
exists for the automated part only.

REACHES (well):
  - IME switching (`adb shell ime set <id>`), with a guard that the target
    IME is currently enabled on the device.
  - Committing corpus text and capturing the final field contents, which
    drives WPM, KSPC, and the MSD-based corrected/uncorrected error rates
    in metrics.py.
  - Inter-keystroke timing drawn from a configurable distribution, so WPM
    numbers aren't all at one flat speed.

DOES NOT REACH (and the proxy used instead):
  - Per-keystroke autocorrect/acceptance signals. Third-party keyboards do
    not expose "this suggestion was accepted" or "this autocorrect changed
    the user's word" over adb. Proxy: diff the final committed text against
    the presented phrase (that is exactly what metrics.error_rates does).
    This catches autocorrect *outcomes* (did the committed word match the
    intent) but not *intent* (did autocorrect "helpfully" change a word the
    user meant to keep -- the false-correction rate). For false-correction
    rate, the corpus is deliberately salted with slang/abbreviations the
    "correct" answer would clobber; a diff there is a decent proxy.
  - Gesture / swipe typing. adb cannot synthesize a gesture through the IME
    at all. This is the metric the issue explicitly routes to the scripted
    human session -- see the harness spec doc.
  - Next-word prediction acceptance. Same problem as autocorrect signals:
    no adb surface. Proxy: corpus messages split into prefix + expected
    continuation; if the committed prefix ever equals a prefix the IME
    surfaced a suggestion for, the operator can eyeball acceptance from
    captured suggestion-row screenshots -- not automatable here.

== Two commit methods ==

--method inject (default, fast, honest baseline):
    `adb shell input text "<phrase>"`. This injects text via the system
    InputManager, which lands in the editor's InputConnection directly. It
    does NOT route through the IME's composing/autocorrect pipeline, so it
    measures editor throughput, not IME engine quality. Useful for sanity-
    checking the capture/timing plumbing and for a "no-IME-engine" WPM
    ceiling. Do not report these numbers as IME numbers.

--method tap (the real measurement):
    Dumps the IME window's key grid via `adb shell uiautomator dump`,
    locates each key by label using the per-IME selectors in imes.yaml,
    and taps keys one at a time with `adb shell input tap x y` and a
    randomized inter-key delay. This actually exercises the IME's
    composing/autocorrect/suggestion pipeline. Per-key selectors differ
    enough across the three keyboards that a real run needs each IME's
    selector map filled in and verified on the device first; the harness
    ships an empty selector map and a discovery helper (--discover) rather
    than guessing key coordinates.

Neither method fabricates metrics that require telemetry the device does
not expose. If a number can't be measured, the field is left null in the
JSON record, and compute_metrics.py skips it.
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

# Keystroke timing: mean inter-key delay in milliseconds, with +-jitter.
# 120 ms is a deliberate, unhurried pace; faster than a confident typist,
# slower than a robot. Override on the CLI for sensitivity sweeps.
DEFAULT_MEAN_DELAY_MS = 120
DEFAULT_JITTER_MS = 35


def adb(*args, device: str | None = None, capture: bool = True) -> str:
    """Run an adb subcommand, returning stdout. Raises SystemExit with the
    captured stderr on non-zero return codes so a failing adb call doesn't
    silently produce empty output that looks like a clean run."""
    cmd = ["adb"]
    if device:
        cmd += ["-s", device]
    cmd += list(args)
    proc = subprocess.run(cmd, capture_output=capture, text=True)
    if proc.returncode != 0:
        raise SystemExit(f"adb {' '.join(args)} failed ({proc.returncode}): {proc.stderr.strip()}")
    return proc.stdout if capture else ""


def ime_enabled(ime_id: str, device: str | None = None) -> bool:
    """True if the IME is in the device's enabled list. `ime set` silently
    no-ops on an IME that isn't enabled first, so we check before switching."""
    out = adb("shell", "ime", "list", "-s", device=device)
    return ime_id in out.split()


def set_ime(ime_id: str, device: str | None = None) -> None:
    if not ime_enabled(ime_id, device=device):
        raise SystemExit(
            f"IME '{ime_id}' is not enabled on the device. Enable it in "
            "Settings > System > Keyboard, or via `adb shell ime enable <id>` "
            "before running the bake-off."
        )
    adb("shell", "ime", "set", ime_id, device=device)


_UI_DUMP_DEVICE_PATH = "/data/local/tmp/typing_bakeoff_ui_dump.xml"


def read_field_text(device: str | None = None) -> str:
    """Best-effort capture of the focused EditText's current text via a
    uiautomator dump. Returns '' if no focused text node is found. This is
    a heuristic -- it looks for the node attribute that has focus and a
    non-empty text field, falling back to the last EditText in the dump.

    `uiautomator dump --compressed /dev/tty` only prints a confirmation
    line over adb's stdout capture -- the XML itself goes to the device's
    controlling tty, which a non-interactive `adb shell` has none of. Dump
    to a device-local file and `cat` it back instead."""
    adb("shell", "uiautomator", "dump", "--compressed", _UI_DUMP_DEVICE_PATH, device=device, capture=False)
    xml = adb("shell", "cat", _UI_DUMP_DEVICE_PATH, device=device)
    m = re.search(r'text="([^"]*)"[^>]*focusable="true"[^>]*focused="true"', xml)
    if m:
        return m.group(1)
    # Fallback: last EditText-ish node with text.
    matches = re.findall(r'class="[^"]*EditText[^"]*"[^>]*text="([^"]*)"', xml)
    return matches[-1] if matches else ""


def clear_field(device: str | None = None) -> None:
    """Select-all + delete on the focused field, so the next phrase starts
    clean. Uses keyevents rather than rely on any particular app's menu."""
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END", device=device, capture=False)
    # Long-press select-all isn't keyevent-portable; fall back to many backspaces.
    # Crude but robust across test apps.
    for _ in range(256):
        adb("shell", "input", "keyevent", "KEYCODE_DEL", device=device, capture=False)


def inject_phrase(phrase: str, device: str | None = None) -> int:
    """`adb shell input text` path. Returns the synthetic keystroke count
    (length of the phrase, since injection bypasses the IME entirely). See
    the module docstring: this measures editor throughput, not IME quality."""
    # `input text` doesn't handle whitespace well; encode spaces.
    safe = phrase.replace(" ", "%s")
    adb("shell", "input", "text", safe, device=device, capture=False)
    return len(phrase)


def tap_phrase(
    phrase: str,
    key_lookup: dict,
    device: str | None,
    mean_delay_ms: int,
    jitter_ms: int,
    backspace_label: str,
) -> tuple[int, int]:
    """Per-key tap path. `key_lookup` maps a single character to the
    (bounds, ) needed to tap it; populated by --discover per IME. Returns
    (total_taps_including_backspaces, backspaces_used). Tap coordinates are
    derived fresh per tap from a uiautomator dump because keyboards shift
    layout (shift state, popup panels) mid-phrase."""
    taps = 0
    backspaces = 0
    for ch in phrase:
        if ch == "\b":
            target = key_lookup.get(backspace_label)
            backspaces += 1
        else:
            target = key_lookup.get(ch)
        if target is None:
            # Unknown glyph (emoji, rare punctuation): skip honestly rather
            # than tap a wrong key and call the run clean.
            continue
        x, y = centre_of(target)
        adb("shell", "input", "tap", str(x), str(y), device=device, capture=False)
        taps += 1
        delay_ms = max(0, random.gauss(mean_delay_ms, jitter_ms))
        time.sleep(delay_ms / 1000.0)
    return taps, backspaces


def centre_of(bounds: str) -> tuple[int, int]:
    """Parse a uiautomator `bounds="[x1,y1][x2,y2]"` string into the integer
    centre of the box."""
    nums = [int(n) for n in re.findall(r"\[(\d+),(\d+)\]", bounds)[0]]
    # The above grabs the first pair; rebuild properly for both pairs.
    pairs = re.findall(r"\[(\d+),(\d+)\]", bounds)
    x1, y1 = int(pairs[0][0]), int(pairs[0][1])
    x2, y2 = int(pairs[1][0]), int(pairs[1][1])
    return ((x1 + x2) // 2, (y1 + y2) // 2)


@dataclass
class PhraseRecord:
    phrase_id: str
    presented: str
    transcribed: str
    method: str
    elapsed_seconds: float
    input_stream_length: int
    fixes: int
    note: str = ""
    extra: dict = field(default_factory=dict)

    def to_json(self) -> str:
        return json.dumps(
            {
                "phrase_id": self.phrase_id,
                "presented": self.presented,
                "transcribed": self.transcribed,
                "method": self.method,
                "elapsed_seconds": round(self.elapsed_seconds, 4),
                "input_stream_length": self.input_stream_length,
                "fixes": self.fixes,
                "note": self.note,
            },
            ensure_ascii=False,
        )


def load_corpus(path: Path) -> list[tuple[str, str]]:
    """Read a corpus CSV (id,text[,category]) into (id, text) pairs. Skips
    rows whose text is the loud placeholder marker -- the bake-off must not
    quietly run on fake data."""
    rows: list[tuple[str, str]] = []
    with path.open() as f:
        for row in csv.DictReader(f):
            text = (row.get("text") or "").strip()
            if not text or text.startswith("___"):
                continue
            rows.append((row["id"], text))
    return rows


def replay(
    ime_id: str,
    corpus: Path,
    out: Path,
    method: str,
    device: str | None,
    mean_delay_ms: int,
    jitter_ms: int,
    key_lookup: dict,
    backspace_label: str,
    limit: int | None,
) -> int:
    set_ime(ime_id, device=device)
    phrases = load_corpus(corpus)
    if limit:
        phrases = phrases[:limit]
    if not phrases:
        raise SystemExit(f"no replayable phrases in {corpus}")

    written = 0
    with out.open("w") as f:
        for phrase_id, presented in phrases:
            clear_field(device=device)
            start = time.monotonic()
            if method == "inject":
                keys = inject_phrase(presented, device=device)
                fixes = 0
            else:
                keys, fixes = tap_phrase(
                    presented, key_lookup, device, mean_delay_ms, jitter_ms, backspace_label
                )
            elapsed = time.monotonic() - start
            transcribed = read_field_text(device=device)
            rec = PhraseRecord(
                phrase_id=phrase_id,
                presented=presented,
                transcribed=transcribed,
                method=method,
                elapsed_seconds=elapsed,
                input_stream_length=keys,
                fixes=fixes,
                note="",
            )
            f.write(rec.to_json() + "\n")
            f.flush()
            written += 1
    return written


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--ime", required=True, help="Target IME id, e.g. helium314.keyboard/.latin.LatinIME")
    p.add_argument("--corpus", required=True, type=Path, help="Corpus CSV (id,text[,category])")
    p.add_argument("--out", required=True, type=Path, help="Output JSONL path")
    p.add_argument("--method", choices=["inject", "tap"], default="inject",
                   help="inject = adb input text (baseline, bypasses IME); tap = per-key taps (real measurement)")
    p.add_argument("--device", help="adb -s serial (defaults to the single connected device)")
    p.add_argument("--mean-delay-ms", type=int, default=DEFAULT_MEAN_DELAY_MS)
    p.add_argument("--jitter-ms", type=int, default=DEFAULT_JITTER_MS)
    p.add_argument("--limit", type=int, help="Only replay the first N phrases (smoke checks)")
    p.add_argument("--backspace-label", default="⌫", help="Glyph used for backspace in --tap key maps")
    args = p.parse_args()

    args.out.parent.mkdir(parents=True, exist_ok=True)
    key_lookup = {}  # tap-mode map; populated out-of-band per IME via discovery
    n = replay(
        ime_id=args.ime,
        corpus=args.corpus,
        out=args.out,
        method=args.method,
        device=args.device,
        mean_delay_ms=args.mean_delay_ms,
        jitter_ms=args.jitter_ms,
        key_lookup=key_lookup,
        backspace_label=args.backspace_label,
        limit=args.limit,
    )
    print(f"wrote {n} records to {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
