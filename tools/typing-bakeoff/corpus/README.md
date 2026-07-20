# Bake-off corpus

Two files, two purposes. Both are plain CSV (`id,text[,category]`) so the replay
script can munch either one with no special-casing.

## `domain-messages.csv` — ~50 PersonaSpeak-flavoured messages

Original one-liners written for this bake-off: casual chat, slang, emoji,
punctuation noise. Roughly the kind of thing someone would actually send while
PersonaSpeak is loaded. Not the academic set, not trying to be — that's the
point of having a second corpus. Edit freely; the more it looks like real
traffic, the more useful the bake-off.

## `mackenzie-soukoreff.csv` — the real 500-phrase set

This is the genuine phrase set, sourced 2026-07-21 directly from the authors'
own distribution, not retyped or reconstructed from memory:

> MacKenzie, I. S., & Soukoreff, R. W. (2003). Phrase sets for evaluating
> text entry techniques. *CHI '03 Extended Abstracts on Human Factors in
> Computing Systems*, 754–755. DOI: 10.1145/765891.765971

(Earlier drafts of this doc cited the *metrics* paper — Soukoreff & MacKenzie,
"Metrics for text entry research," CHI '03, 81–88 — for the phrase set. That's
the wrong paper; it's the one `metrics.py` implements, not the one this corpus
comes from. Same two authors, same year, two different CHI '03 papers — easy
mixup, now fixed.)

Retrieved from `http://www.yorku.ca/mack/PhraseSets.zip` (linked directly from
the paper's page at `yorku.ca/mack/chi03b.html`), file `phrases2.txt` inside
the archive — 500 lines, verified against the paper's own description (mean
length 28.6 characters, no punctuation, lowercase). No license file ships with
the archive; the authors' own text-entry-research page states the set is
provided for the community of text entry researchers, and it's the standard
benchmark reproduced across text-entry literature and tooling. If that
provenance ever needs re-verifying, the zip and the two source pages above are
the whole chain — nothing here was invented or transcribed by hand.

## Why two corpora

The MacKenzie-Soukoreff set is the comparable-to-the-literature baseline —
lowercase, no emoji, no punctuation surprises, every published keyboard paper
reports against it. The domain set is the "does it survive the messiness of
actual chat" stress test: emoji, abbreviations (`omw`, `rn`, `ngl`), loose
punctuation, sentence fragments. A keyboard that aces the academic set and
whiffs the casual one is exactly the finding that matters for a chat product.
