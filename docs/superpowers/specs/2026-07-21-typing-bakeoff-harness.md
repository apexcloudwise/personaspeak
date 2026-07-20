# Typing bake-off: harness & protocol

**Date:** 2026-07-21
**Status:** Harness complete, unmeasured. This doc describes how to run the
bake-off and what "done" looks like for the real run. It contains no
measured numbers — every numeric cell in the [results
template](2026-07-21-typing-bakeoff-results-template.md) is empty on
purpose. Shipping harness first; numbers come from a human-supervised run.
**Branch:** `worktree-issue-5-typing-bakeoff`
**Related:** [issue #5](https://github.com/apexcloudwise/personaspeak/issues/5)
· [fork-spike results](2026-07-20-fork-spike-results.md) (the
FlorisBoard-stub finding folded in below comes from there) ·
[results template](2026-07-21-typing-bakeoff-results-template.md) ·
[harness README](../../../tools/typing-bakeoff/README.md)

## Why a bake-off at all

The fork spike measured graft cost, license, and maintenance — the three
axes you can grade without typing on the thing. It dodged the one axis that
actually matters for a chat keyboard: **does the inherited engine type
well.** This is the gap issue #5 exists to close, before the base-and-license
ADR finalizes. The graft doesn't touch the engine (we add a strip *above*
the keys), so this can be measured on stock upstream builds from F-Droid —
no graft noise, pure engine comparison.

## Scope of *this* document

This is the harness and the protocol. It does not contain results. A
separate, future run will fill the results template and feed the base
decision. The split is load-bearing: a chat transcript that says "we
measured" is not a measurement, and this repo's definition of done rejects
"decided in a transcript only."

## The harness, in one paragraph

Three pieces, all under `tools/typing-bakeoff/`: a **corpus** (two CSVs),
an **adb replay script** that switches IME, replays phrases, and captures
committed text, and a **metrics calculator** that reduces the captured
records into the Soukoreff-MacKenzie rates. The metrics math is pinned by
hand-computed unit tests in the same PR; the replay and capture plumbing
has to be exercised on a real device before its output is trusted, and
that exercise is out of scope for the harness PR.

## Corpus

Two files, described in detail in
[`tools/typing-bakeoff/corpus/README.md`](../../../tools/typing-bakeoff/corpus/README.md):

1. **`domain-messages.csv`** — ~50 original one-liners written for this
   bake-off: casual chat, slang, emoji, loose punctuation. The realistic-
   traffic stress test. A keyboard that aces the academic set and whiffs
   this one is the exact finding a chat product cares about.
2. **`mackenzie-soukoreff.csv`** — **a placeholder.** Every row is an
   obviously-fake stand-in. The genuine 500-phrase MacKenzie & Soukoreff
   (2003) set must be sourced and pasted in before any real run; the
   README cites the paper and refuses to invent a download URL. We do not
   fabricate phrases and stamp a citation on them.

## Metrics

Soukoreff & MacKenzie (2003), implemented as pure functions in
[`metrics.py`](../../../tools/typing-bakeoff/metrics.py) with hand-computed
unit tests in `test_metrics.py`:

- **WPM** — `(transcribed chars / 5) / (seconds / 60)`.
- **UER** (uncorrected error rate) — `MSD(presented, transcribed) / (|T| + IF)`.
- **CER** (corrected error rate) — `IF / (|T| + IF)`, where `IF` is the
  count of fix keystrokes (backspaces).
- **TER** (total error rate) — `UER + CER`.
- **KSPC** — keystrokes ÷ transcribed chars; corpus-level is pooled
  (`Σkeys / Σchars`), not arithmetic-meaned across phrases.

The two metrics the issue adds beyond the paper — **autocorrect
false-correction rate** and **next-word prediction acceptance rate** —
have no adb telemetry surface on third-party keyboards. They are proxied
(see next section) or routed to the human session. The harness does not
fake them.

## Protocol

### Device and corpus setup

1. **One device, one operator, one session.** Same hardware throughout;
   cross-device variance is a confound we're not paying for.
2. **Install all three keyboards from F-Droid.** Versions pinned to the
   spike's tags: HeliBoard `v4.0`, AnySoftKeyboard `1.13-r1`, FlorisBoard
   `v0.5.2` (see the [fork-spike results](2026-07-20-fork-spike-results.md)
   for SHAs). Enable each in Settings so `adb shell ime set` can switch to
   it, but do not leave any of them as the default IME outside a pass.
3. **Confirm IME ids** with `adb shell ime list -s` against
   [`imes.yaml`](../../../tools/typing-bakeoff/imes.yaml). Service suffixes
   drift between releases; update the YAML if a build moved one.
4. **Replace the placeholder corpus.** See
   [`corpus/README.md`](../../../tools/typing-bakeoff/corpus/README.md).
   No real run on the placeholder file.

### Counterbalanced ordering

Three keyboards × one operator. Order is a real confound (warmup, fatigue,
acclimation to the test rig), so the order is counterbalanced across the
six Latin-square permutations:

| Pass | Order A | Order B | Order C | Order D | Order E | Order F |
|---|---|---|---|---|---|---|
| 1st | Heli | ASK | Floris | Heli | Floris | ASK |
| 2nd | ASK | Floris | Heli | Floris | ASK | Heli |
| 3rd | Floris | Heli | ASK | ASK | Heli | Floris |

Pick one column by dice roll, run that order, record which column was used
in the results doc. One operator suffices because the automated pass
removes the largest human-variance component (keystroke timing); the human
session below carries the subjective component explicitly.

### Automated pass (deterministic metrics)

Per keyboard, in the chosen order:

1. `adb shell ime set <id>` to switch.
2. Open a plain text field (a notes app; the same app for every pass),
   focus it.
3. Run `adb_replay.py --method tap` against the MacKenzie-Soukoreff set,
   then against the domain set. The `tap` method actually exercises the
   IME's composing/autocorrect pipeline; `inject` does not (it bypasses
   the IME) and is for plumbing sanity checks only. See the replay script's
   docstring for the discovery step needed to build the per-IME key map
   for `tap` mode.
4. Between passes, clear the field and restore Gboard (or any non-test
   IME) as the default so a half-finished pass doesn't bleed into the next.

Outputs: two JSONL files per keyboard (academic + domain), reduced by
`compute_metrics.py` into the tables in the results template.

### Human session (subjective + gesture)

One short session per keyboard, immediately after its automated pass so
the feel is fresh. Roughly five minutes each. The script:

1. **Type ten of your own real messages** — anything you'd actually send,
   no constraints. Note gut-feel speed, autocorrect friction, and any
   false correction that made you backspace a word you meant.
2. **Gesture-type ten phrases from the domain set.** adb cannot synthesize
   a swipe through the IME, so this is the only path to a gesture-accuracy
   number. Score a gesture as correct if the committed word matches the
   target with no manual fix.
3. **Eyeball the suggestion row** for the next-word-prediction acceptance
   rate. Acceptance is not exposed over adb; the operator counts, by eye,
   how often the top suggestion was the word they were about to type.

The human session is the one place a metric can land that *requires* a
human; the harness deliberately does not try to automate it.

### Proxies for what adb can't reach

- **Autocorrect false-correction rate.** No telemetry. Proxy: the domain
  set is salted with slang and abbreviations (`omw`, `rn`, `ngl`, `tbh`,
  `lemme`) that a dictionary would "correct." Any committed text where one
  of these was replaced is a false correction. Flagged as a proxy, not a
  direct measurement.
- **Next-word prediction acceptance.** Same problem. Not reported as a
  number from the automated pass; the human session's eyeball count is the
  figure of record.

## Done

The bake-off run is done when, for each of the three keyboards:

- The MacKenzie-Soukoreff automated pass has run cleanly on all 500 phrases
  with the `tap` method, and the JSONL is in the repo under `tools/typing-bakeoff/runs/`.
- The domain set has run cleanly on all ~50 messages, same method.
- The human session is complete and its notes are in the results doc.
- Every table in
  [`2026-07-21-typing-bakeoff-results-template.md`](2026-07-21-typing-bakeoff-results-template.md)
  is filled in, with `n` recorded for each cell.

The bar, per the issue, is **"is the inherited engine at least as good as a
competent stock keyboard,"** with the error-rate numbers as the tiebreak —
not Gboard parity. A future ADR picks the base; this run feeds it.

## Known pre-result, folded in from the spike

**FlorisBoard v0.5.2's suggestion engine is stubbed.** Per the [fork-spike
results](2026-07-20-fork-spike-results.md), `LatinLanguageProvider.suggest()`
returns `emptyList()` with the real candidate-generation code commented out,
and `spell()` only flags the literal strings `"typo"`/`"gerror"` as errors.
That makes FlorisBoard's prediction and autocorrect score **effectively
zero today** — not as a measurement (this harness doesn't need to spend the
phrase set to reconfirm it), but as a documented pre-existing fact from the
earlier spike. It is cited, not re-derived, in the results template.
