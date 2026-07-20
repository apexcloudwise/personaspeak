# Typing bake-off harness

Tooling for issue #5 — a typing-quality bake-off between the three inherited
keyboard candidates (HeliBoard, AnySoftKeyboard, FlorisBoard) before the
fork-base decision finalizes. **This directory is the harness; the numbers
come later, from a real run by a human.**

## What's in here

```
tools/typing-bakeoff/
├── README.md              this file — the five-minute tour
├── imes.yaml              the three candidates' package + IME ids + license
├── metrics.py             Soukoreff & MacKenzie (2003): WPM, KSPC, UER/CER/TER
├── test_metrics.py        hand-computed unit tests for metrics.py
├── adb_replay.py          switch IME, replay a corpus, capture commits
├── compute_metrics.py     reduce a run JSONL into a per-keyboard metric table
└── corpus/
    ├── README.md          what each corpus is (and what the placeholder isn't)
    ├── domain-messages.csv      ~50 original casual-chat messages
    └── mackenzie-soukoreff.csv  PLACEHOLDER — replace before any real run
```

## The harness in one paragraph

You point `adb_replay.py` at a corpus file and an IME id. It switches the
device to that IME, replays each phrase into a focused text field, captures
what actually got committed, and writes one JSON record per phrase. Then
`compute_metrics.py` turns that JSONL into the WPM / error-rate / KSPC table
that goes in the results doc. The metrics math is pinned by hand-computed
unit tests; everything else is plumbing that has to be exercised on a real
device before it's trusted.

## Quickstart (assumed: a clean device or emulator with the three keyboards installed from F-Droid)

```sh
# 1. Sanity-check the metrics math against the hand-computed cases.
python -m unittest test_metrics

# 2. Make sure the corpus is real. The MacKenzie-Soukoreff file is a placeholder.
#    See corpus/README.md for where to source the genuine 500-phrase set.

# 3. Confirm the target IME is enabled on the device.
adb shell ime list -s | grep helium314

# 4. Open a plain text field (any notes app will do) on the device, focus it.

# 5. Run a short smoke pass against one IME -- 5 phrases, baseline method.
python adb_replay.py \
    --ime helium314.keyboard/.LatinIME \
    --corpus corpus/domain-messages.csv \
    --out runs/heliboard-smoke.jsonl \
    --method inject \
    --limit 5

# 6. Compute the metric table from the run.
python compute_metrics.py runs/heliboard-smoke.jsonl --keyboard HeliBoard
```

The smoke pass uses `--method inject`, which bypasses the IME entirely. That
makes it a good plumbing check (does the field capture work? are the
timestamps sane?) but **not** an IME measurement. The real pass uses
`--method tap` and needs per-IME key selectors filled in first — see
`adb_replay.py`'s docstring for why, and the harness spec doc for the
discovery steps.

## What gets measured automatically vs. what doesn't

The honest split, because adb is the wrong tool for some of these:

| Metric | Automated by this harness? | Notes |
|---|---|---|
| WPM | Yes | From per-phrase elapsed time + transcribed length. |
| Corrected error rate (CER) | Yes | Needs fix-keystroke counts from `--method tap`. |
| Uncorrected error rate (UER) | Yes | MSD between presented and transcribed, per Soukoreff-MacKenzie. |
| KSPC | Yes | Keystrokes ÷ transcribed chars. |
| Autocorrect false-correction rate | Partial — proxy only | Diff committed text against the slang-heavy domain set; no adb surface for "autocorrect fired." |
| Next-word prediction acceptance | No | No adb surface. Eyeball from captured suggestion-row screenshots. |
| Gesture / swipe accuracy | No | adb cannot synthesize gestures through the IME. Scripted human session. |

For the two "no" rows, the harness deliberately leaves fields null rather
than fabricating a number. The results template documents where they get
filled in.

## See also

- **Harness spec / protocol:** `docs/superpowers/specs/2026-07-21-typing-bakeoff-harness.md`
  — how to run the full thing end to end, counterbalancing, the human session, the bar.
- **Results template:** `docs/superpowers/specs/2026-07-21-typing-bakeoff-results-template.md`
  — empty tables ready to fill in, plus the FlorisBoard pre-result caveat.
- **Fork spike results:** `docs/superpowers/specs/2026-07-20-fork-spike-results.md`
  — where the FlorisBoard stubbed-`suggest()` finding comes from.
- **Issue:** [#5](https://github.com/apexcloudwise/personaspeak/issues/5).
