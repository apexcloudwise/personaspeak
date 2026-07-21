# UPSTREAM-MODIFIED.md — rent ledger for the vendored ASK tree

This is the rent ledger. One line per upstream-tracked file we have edited to
integrate AnySoftKeyboard into this repo's build. It describes the **current**
delta from the pristine vendored base — not a changelog. A file edited several
times has one entry (its net diff); a file whose edit we later revert to
pristine has its entry removed.

The invariant: regenerate the pristine tree (see
[`UPSTREAM.md`](UPSTREAM.md)) into a scratch dir and
`diff -rq <scratch-pristine> android/keyboard/`. The set of files that differ
in both places must be exactly the set listed below — no more, no less.
Files that exist only on our side are our own additions under our own
packages and are not rent.

The format of each line:

```
- <path-from-this-tree> — <reason, one sentence>
```

## Files modified against pristine

_(none yet — this is the initial vendored snapshot at `1.13-r1`. Build wiring
edits, if any are required to land `./gradlew assembleDebug`, will be added
here in the same PR that introduces them.)_
