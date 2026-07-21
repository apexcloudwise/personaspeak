# ADR-0004: Ingest AnySoftKeyboard as a vendored snapshot

**Status:** Accepted (2026-07-21) — the first build-phase ADR after
[ADR-0003](0003-fork-anysoftkeyboard-apache.md) picked the base. Decides *how*
AnySoftKeyboard's source physically enters this repo, before a single line of it
is grafted.

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
2. **ASK moves slowly.** The selected stable release as of 2026-07-21 is
   `1.13-r1` (published Feb 2026). The upstream we are subscribing to publishes
   on the order of once a year, not once a week.

And one fact about *our* repo, which turns out to decide the question:

3. **`main` enforces `required_linear_history` and we squash-merge PRs.**
   (Confirmed against the `nomain` ruleset.) No merge commit ever survives on
   `main`; every PR lands as one squashed commit.

Mechanisms considered:

- **A. git submodule** — ASK stays its own repo at a pinned SHA; our changes
  live as an overlay/patches.
- **B. git subtree (`--squash`)** — ASK merged into a subdirectory as a single
  squashed commit (not its full history), updated with `git subtree pull
  --squash`, which 3-way-merges new upstream against our edits.
- **C. Vendored snapshot** — ASK's source copied in at a pinned revision, its
  history dropped, tracked thereafter as files in our tree.

## Decision

**Vendor a snapshot of AnySoftKeyboard at a pinned upstream revision, edited in
place, with the upstream diff tracked in a manifest.**

Concretely:

- The ASK tree lands under `android/keyboard/` — the module slot the stub
  already reserves — at tag `1.13-r1`, commit
  `8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d` (the spike's pin).
- The snapshot is produced reproducibly: `git archive` of the pinned tag from
  the upstream repo, extracted into `android/keyboard/`, excluding upstream CI
  and repository-control material. The exact list covers `.git` (implicit),
  `.github/`, `.claude/`, `.gemini/`, `.jules/`, `.devcontainer/`, root
  `AGENTS.md` and `CLAUDE.md`, and upstream `fastlane/` metadata. Nested agent
  instructions are repository management, not keyboard source; importing them
  would let upstream workflow rules govern PersonaSpeak work below the vendored
  directory. The exclusion list itself is recorded in `UPSTREAM.md`. Anyone can
  regenerate the identical pristine tree from the recorded tag + exclusion list
  and `git diff` it against our import to see exactly our changes. Committed as a
  single, message-tagged import commit; upstream git history is **not** imported.
- ASK's `LICENSE`, any upstream `NOTICE`, and per-file Apache headers are
  preserved verbatim. The pinned `1.13-r1` tree contains `LICENSE` but no root
  `NOTICE`; provenance records that absence rather than manufacturing one.
  A top-level `android/keyboard/UPSTREAM.md` records the source repo, the pinned
  tag + SHA, the date, the exclusion list, and the re-vendor procedure.
- Our modifications to upstream-tracked files are listed in
  `android/keyboard/UPSTREAM-MODIFIED.md` — one line per touched file, with the
  reason. It describes the **current** delta from the vendored base, not a
  changelog: a file edited several times still has one entry (its net diff), and
  a file whose edit we later revert to pristine has its entry **removed**. The
  invariant the manifest asserts: regenerate the pristine tree (the recorded
  `git archive` + exclusion list into a scratch dir) and
  `git diff --no-index <scratch> android/keyboard/` touches exactly the files it
  lists, no more. A bare `git diff <tag>` will not do this — upstream's tree is
  rooted where ours is relocated under `android/keyboard/`, so the comparison
  must be prefix-aware (`--no-index` against the regenerated tree, or an
  equivalent). This is the rent ledger, kept by hand and small on purpose.
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
- **A subtree's update model fights our merge policy — and that, not history
  bloat, is the reason.** An earlier draft of this ADR rejected subtree for
  "importing ~8,870 commits of history." That was wrong, and the non-author
  review (codex) caught it: `git subtree add --squash` collapses upstream to a
  single commit, exactly as vendoring does. The genuine problem is downstream.
  `git subtree pull --squash` computes its 3-way merge base by finding the
  *previous* subtree commit in history — and by fact 3, that commit never
  survives. Every subtree merge is flattened when its PR squash-merges into a
  linear `main`, destroying the ancestry the next `pull` needs. subtree would
  work exactly once, at the initial add, then lose its own thread; its headline
  benefit, the auto-merging pull, does not survive our workflow. We would take on
  subtree's arcana (root-only invocation, exact `--prefix`, confusing merge
  conflicts) for none of its payoff. A vendored snapshot depends on no history
  shape at all, so the squash-merge policy costs it nothing.
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
- **License compliance is a checklist item, not a vibe.** Preserve ASK's
  `LICENSE`, any upstream `NOTICE`, and existing per-file headers; the app's own
  license (ADR-0003) and ASK's attribution coexist, and `UPSTREAM.md` is where a
  downstream reader finds the provenance. This is plain, load-bearing text — no
  jokes in the attribution.
- **`core-personas` and `core-providers` stay outside the vendored tree.** They
  are ours and pure; the vendored snapshot depends on them, never the reverse.
  Platform seams the graft needs — editor identity, commit authority, Keystore —
  are ports in our modules with adapters in the vendored/Android layer, so
  vendoring ASK does not leak `android.*` back into the pure core.
- **The superseded ADR-0001 panel is disposable scaffolding, not a product
  demo.** The ingestion slice moves its files byte-for-byte to temporary module
  `:keyboard-stub` only so the current root APK keeps its build, install, and IME
  registration baseline while ASK occupies `android/keyboard/`. The slice does
  not improve, exercise, or advertise the keyless switcher flow, and the stub
  proves no typing behavior. The unified ASK integration deletes it; no release
  may treat it as the product keyboard.
