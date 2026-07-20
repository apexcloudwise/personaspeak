# Typing bake-off: results

**Date run:** _(to fill in)_
**Operator:** _(to fill in)_
**Device:** _(to fill in)_
**Order column used (see harness doc):** _(A–F)_
**Status:** Empty. This is a template, not a result. Fill it in after the
real run described in
[`2026-07-21-typing-bakeoff-harness.md`](2026-07-21-typing-bakeoff-harness.md).
No number in this file is a measurement yet.

Every cell below is empty on purpose. `n` is the phrase count that fed each
number; record it alongside. Numbers come from
`tools/typing-bakeoff/compute_metrics.py` reducing the per-keyboard run
JSONLs. If a metric couldn't be measured for a keyboard, write `n/a` and
say why — don't leave a gap that looks like zero.

## Known pre-result (cite, don't re-derive)

**FlorisBoard v0.5.2 ships no working prediction engine.** From the
[fork-spike results](2026-07-20-fork-spike-results.md): `LatinLanguageProvider.suggest()`
returns `emptyList()` with the real candidate-generation code commented out,
and `spell()` only flags the literal strings `"typo"`/`"gerror"`. So
FlorisBoard's **next-word prediction acceptance rate** and **autocorrect
contribution** are expected to score effectively zero on this version —
that's a pre-existing fact from the spike, not something this bake-off
needs to spend phrases re-confirming. The WPM / KSPC / error-rate cells for
FlorisBoard are still measured (raw typing throughput doesn't depend on the
suggestion engine), but any engine-quality comparison on the prediction
axis is ASK vs. HeliBoard on a running start.

## Automated pass — MacKenzie-Soukoreff phrase set

`--method tap`, full 500-phrase set (real corpus, not the placeholder).

| Keyboard | WPM | UER | CER | TER | KSPC | n |
|---|---|---|---|---|---|---|
| HeliBoard |  |  |  |  |  |  |
| AnySoftKeyboard |  |  |  |  |  |  |
| FlorisBoard |  |  |  |  |  |  |

## Automated pass — domain message set

`--method tap`, ~50-message set.

| Keyboard | WPM | UER | CER | TER | KSPC | n |
|---|---|---|---|---|---|---|
| HeliBoard |  |  |  |  |  |  |
| AnySoftKeyboard |  |  |  |  |  |  |
| FlorisBoard |  |  |  |  |  |  |

## Autocorrect false-correction rate

Proxy: committed text where the slang/abbreviation in the domain set was
"corrected" away. Flagged as a proxy — no adb telemetry for autocorrect
firing.

| Keyboard | false corrections | opportunities | rate | n |
|---|---|---|---|---|
| HeliBoard |  |  |  |  |
| AnySoftKeyboard |  |  |  |  |
| FlorisBoard |  |  |  | _(n/a — engine stubbed; see pre-result)_ |

## Gesture / swipe first-pass accuracy

From the scripted human session — adb cannot synthesize gestures through
the IME. Score a gesture correct if the committed word matched the target
with no manual fix.

| Keyboard | correct gestures | gestures | accuracy | n |
|---|---|---|---|---|
| HeliBoard |  |  |  |  |
| AnySoftKeyboard |  |  |  |  |
| FlorisBoard |  |  |  |  |

## Next-word prediction acceptance rate

From the human session's eyeball count — no adb telemetry. Counted when the
top suggestion was the word the operator was about to type.

| Keyboard | accepted | offered | rate | n |
|---|---|---|---|---|
| HeliBoard |  |  |  |  |
| AnySoftKeyboard |  |  |  |  |
| FlorisBoard |  |  |  | _(n/a — engine stubbed; see pre-result)_ |

## Human session notes

One short paragraph per keyboard: gut-feel speed, autocorrect friction,
anything that surprised. Prose here, numbers above.

**HeliBoard:**

**AnySoftKeyboard:**

**FlorisBoard:**

## Operator's read

_(To fill in after the run, not before. Per the issue: the bar is "at
least as good as a competent stock keyboard," with error-rate numbers as
tiebreak. This section feeds — does not replace — the base-and-license
ADR.)_
