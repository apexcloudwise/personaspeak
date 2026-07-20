# ADR-0004: Ingest AnySoftKeyboard as a vendored snapshot

**Status:** Proposed (2026-07-21) — the first build-phase ADR after
[ADR-0003](0003-fork-anysoftkeyboard-apache.md) picked the base. Decides *how*
AnySoftKeyboard's source physically enters this repo, before a single line of it
is grafted. Merging this PR records it as Accepted.

## Context

ADR-0003 chose the base (AnySoftKeyboard) and the license (Apache-2.0). It did
not say how ASK's ~8,870-commit, Java-primary tree gets into `apexcloudwise/personaspeak`
so our Gradle can build it and our persona strip can live in it. That is this
ADR, and it is a one-way-ish door: the ingestion mechanism sets how much the
"upstream-merge subscription" costs for the life of the fork, so it is worth
ten minutes now rather than a reorg later.

The Android build is under `android/` (Gradle project `personaboard`); the
`:keyboard` module is a thin stub the fork replaces. Two facts from the
[fork spike](../superpowers/specs/2026-07-20-fork-spike-results.md) constrain the
choice:

1. **We modify upstream files in place.** The ASK graft touched **5 pre-existing
   files** (`settings.gradle`, `gradle/libs.versions.toml`, `ime/app/build.gradle`,
   `main_keyboard_layout.xml`, `.gitignore`) to wire in the vendored modules and
   the strip. A mechanism that forbids editing upstream-tracked files does not
   fit what the graft actually does.
2. **ASK moves slowly.** Latest stable is `1.13-r1` (Feb 2026). The upstream we
   are subscribing to publishes on the order of once a year, not once a week.

Mechanisms considered:

- **A. git submodule** — ASK stays its own repo at a pinned SHA; our changes
  live as an overlay/patches.
- **B. git subtree** — ASK merged into a subdirectory, upstream history preserved,
  `git subtree pull` to update.
- **C. Vendored snapshot** — ASK's source copied in at a pinned revision, its
  history dropped, tracked thereafter as files in our tree.

## Decision

**Vendor a snapshot of AnySoftKeyboard at a pinned upstream revision, edited in
place, with the upstream diff tracked in a manifest.**

Concretely:

- The ASK tree lands under `android/keyboard/` — the module slot the stub
  already reserves — at tag `1.13-r1`, commit
  `8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d` (the spike's pin).
- The snapshot is copied in as a single, message-tagged import commit. Upstream
  git history is **not** imported.
- ASK's `LICENSE`, `NOTICE`, and per-file Apache headers are preserved verbatim.
  A top-level `android/keyboard/UPSTREAM.md` records the source repo, the pinned
  tag + SHA, the date, and the re-vendor procedure.
- Our modifications to upstream-tracked files are listed in
  `android/keyboard/UPSTREAM-MODIFIED.md` — one line per touched file, with the
  reason. This is the rent ledger, kept by hand and small on purpose.
- PersonaSpeak code lives in **new** files under
  `biz.pixelperfectstudios.personaspeak.*` packages, never edited into ASK's own
  files beyond the wiring seam the manifest tracks.

The exact Gradle module composition (nesting ASK's `:ime:*` modules under our
`settings.gradle`, the Java↔Kotlin adapter) is left to the graft PR that follows;
this ADR fixes the mechanism and the discipline, not the build wiring.

## Because

- **A submodule fights fact 1.** The graft edits upstream-tracked files, and you
  cannot commit changes to a submodule's files from the superproject without
  maintaining a *separate* hard fork of ASK as the submodule — which is a
  vendored snapshot again, plus a second repo to run and a `--recursive` clone
  every contributor must remember. It buys clean separation we do not need and
  charges ergonomics we do.
- **A subtree overpays for fact 2.** `git subtree pull` is genuinely nicer *when
  you update often*. We update roughly yearly, and importing 8,870 commits of
  someone else's history to save a once-a-year manual merge is a bad trade —
  permanent repo bloat for occasional convenience, plus subtree's own
  merge-conflict sharp edges.
- **A snapshot makes the rent visible.** Our entire diff from upstream is
  `git diff` against one import commit, and the modified-file manifest states it
  in prose. "Upstream lines modified are rent paid forever" (ADR-0003) is only
  enforceable if the modified lines are easy to count; a snapshot plus a manifest
  makes them a checklist, not an archaeology dig.
- **It is the simplest thing that builds.** Normal clone, normal Gradle, no
  submodule init, no subtree tooling. The house rule prefers 30 lines over a
  dependency; here it prefers a documented copy over a git subsystem.

We reject a patch-series-on-pristine-snapshot variant (keep the vendored tree
byte-identical to upstream, carry our changes as applied patches) for the same
reason: a 5-file diff does not earn a patch toolchain and a build-time apply
step. If the modified-file count ever grows past what a manifest can hold
legibly, that is the signal to revisit — and also a signal the graft has gone
wrong.

## Consequences

- **Re-vendoring is a manual, documented procedure, not a command.** Updating to
  a future ASK release means: fetch the new tag, replay the manifest's file list,
  re-resolve each, bump `UPSTREAM.md`. The slow cadence makes this rare; the
  small manifest makes it bounded. This cost was accepted in ADR-0003 when we
  took on the "upstream-merge subscription."
- **The manifest is load-bearing and reviewed like code.** A PR that modifies an
  upstream ASK file without adding a `UPSTREAM-MODIFIED.md` line has hidden rent,
  and gets returned. Keeping our code in new files under our own packages is what
  keeps that list short.
- **License compliance is a checklist item, not a vibe.** Apache-2.0 requires the
  retained `LICENSE`/`NOTICE` and preserved headers; the app's own license
  (ADR-0003) and ASK's attribution coexist, and `UPSTREAM.md` is where a
  downstream reader finds the provenance. This is plain, load-bearing text — no
  jokes in the attribution.
- **`core-personas` and `core-providers` stay outside the vendored tree.** They
  are ours and pure; the vendored snapshot depends on them, never the reverse.
  Platform seams the graft needs — editor identity, commit authority, Keystore —
  are ports in our modules with adapters in the vendored/Android layer, so
  vendoring ASK does not leak `android.*` back into the pure core.
- **The stub `:keyboard` module is deleted in the graft PR, not this one.** This
  ADR is docs-only; the code lands next, behind the walking-skeleton discipline
  (ADR-0003's release gate: the keyboard must not crash).
