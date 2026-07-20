# Bake-off corpus

Two files, two purposes. Both are plain CSV (`id,text[,category]`) so the replay
script can munch either one with no special-casing.

## `domain-messages.csv` — ~50 PersonaSpeak-flavoured messages

Original one-liners written for this bake-off: casual chat, slang, emoji,
punctuation noise. Roughly the kind of thing someone would actually send while
PersonaSpeak is loaded. Not the academic set, not trying to be — that's the
point of having a second corpus. Edit freely; the more it looks like real
traffic, the more useful the bake-off.

## `mackenzie-soukoreff.csv` — PLACEHOLDER, replace before running

**This file does NOT contain the real MacKenzie & Soukoreff (2003) phrase set.**
Every row is an obviously-fake placeholder. We did not fabricate phrases and
stamp a citation on them — that would be the worst kind of made-up number, the
one that looks real.

Before any real bake-off run, replace this file with the genuine 500-phrase
set from:

> Soukoreff, R. W., & MacKenzie, I. S. (2003). Metrics for text entry
> research: A comparison of WPM and corrected and uncorrected error rate.
> *Proceedings of the SIGCHI Conference on Human Factors in Computing
> Systems (CHI '03)*, 81–88. DOI: 10.1145/642611.642626

The phrase set is a well-known HCI text-entry benchmark and is reproduced in
several open text-entry toolkits and course materials. Do not guess a download
URL — verify the source against the paper or a textbook reproduction before
pasting. The harness only cares that the file is shaped `id,text`.

## Why two corpora

The MacKenzie-Soukoreff set is the comparable-to-the-literature baseline —
lowercase, no emoji, no punctuation surprises, every published keyboard paper
reports against it. The domain set is the "does it survive the messiness of
actual chat" stress test: emoji, abbreviations (`omw`, `rn`, `ngl`), loose
punctuation, sentence fragments. A keyboard that aces the academic set and
whiffs the casual one is exactly the finding that matters for a chat product.
