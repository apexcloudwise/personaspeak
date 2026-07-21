# ADR-0006: Gradle composition for the ASK graft — one build or two?

**Status:** Proposed (2026-07-21) — surfaced by the non-author review of PR #18
(vendoring, ADR-0004). Decides how the vendored AnySoftKeyboard build and our
own modules compose, because that determines whether the persona-strip graft
has a seam at all. This is the owner's call; the recommendation below is a
recommendation, not a decision. Merging records the choice.

## Context

[ADR-0004](0004-vendored-snapshot-ingestion.md) vendored ASK at `1.13-r1` and
said "wire ASK's modules into `android/settings.gradle.kts`." PR #18 instead
wired ASK in via **`includeBuild("keyboard")`** — a Gradle *composite build* —
which achieved **zero upstream files modified** (an empty rent ledger) and got
both `assembleDebug` paths green. Good result on rent. But the non-author review
(codex, gpt-5.6) found the composite, as built, **cannot host the graft**:

- The graft's whole purpose is to let ASK's `:ime:app` call our
  `CompletionProvider.rewrite()` — i.e. `:ime:app` must depend on
  `core-personas` / `core-providers`.
- In a composite build, dependencies flow **from the including (root) build into
  the included build**, via dependency substitution matching module coordinates.
  They do **not** flow the other way: an included build cannot depend on the root
  build's projects. With ASK as the *included* build and `core-*` in the *root*
  build, `:ime:app → core-*` is exactly the unsupported upward direction.
- Separately, the two builds run on **different Gradle versions** (root 8.14;
  ASK's own wrapper 9.2.1, AGP 8.13.2, JDK 21). The two green `assembleDebug`
  runs were each build compiling *itself*; neither exercised a cross-build
  dependency, so they do not prove a graft seam exists.

So the zero-rent win came from isolation that also walls off the two modules the
fork exists to inject. This ADR picks the structure that reopens that seam.

One fact frames every option: **the graft edits at least one ASK build file no
matter what** — `:ime:app`'s `build.gradle` must gain the `core-*` dependency.
"Zero rent" was always going to end at the graft; the real question is total
rent + complexity + upgrade cost, not zero-vs-nonzero.

## Options

### Option A — one unified Gradle build (ADR-0004's original intent)

Merge ASK's `:ime:*`/`:api`/`:addons` modules into our root
`android/settings.gradle.kts` via `include(...)`, alongside `core-*` and `app`.
One build, one Gradle version. `:ime:app` depends on `project(":core-personas")`
directly; no substitution.

- **Graft seam:** trivial and idiomatic (`project(...)`).
- **Gradle version:** must converge on one. Practically, adopt ASK's newer
  toolchain (9.2.1 / AGP 8.13.2 / JDK 21) at the root and bump our three small
  modules up — a one-time chore, then a single version forever.
- **Rent:** edit ASK's `settings.gradle` (its module includes fold into ours) and
  possibly a few module scripts to match our catalog. Bounded, one-time, tracked
  in `UPSTREAM-MODIFIED.md`. Each is a merge-conflict liability at ASK upgrades.
- **Upgrade cost:** ASK upgrades may reintroduce settings/catalog drift we have
  to re-reconcile — but against *one* build definition.

### Option B — composite builds, with `core-*` extracted as a shared build

Pull `core-personas` / `core-providers` out into their own standalone Gradle
build. Both the root/`app` build and the vendored ASK build `includeBuild` that
`core` build and substitute its coordinates. ASK's `:ime:app` then depends on
`core-*` by coordinate, resolved to the shared included build.

- **Graft seam:** works, but by coordinate substitution, not `project(...)` —
  more indirection, and it is the config people most often get subtly wrong.
- **Gradle version:** ASK's 9.2.1 stays isolated from our root 8.14. This is the
  one real advantage — ASK's build keeps its own toolchain.
- **Rent:** edit ASK's `settings.gradle` to add `includeBuild(core)` **and**
  `:ime:app`'s `build.gradle` for the dependency — same rent as A's seam, plus
  ongoing composite wiring.
- **Structural cost:** three builds (root/app, core, ASK) and a shared included
  build; `core-*` move out of the main tree. More moving parts, permanently.

### Option C — keep `includeBuild` as-is, publish `core-*` as artifacts

`core-*` publish to a local Maven repo; ASK consumes them as external
dependencies. Rejected on sight: it turns two first-party pure-Kotlin modules
into a publish-then-consume loop, breaks single-step builds, and makes the seam
the fork exists for the most ceremonious path in the repo. Listed only to close
it.

## Decision

**Recommended: Option A (one unified build).** Deferred to the owner.

## Because

- **We own this fork; a single build is the honest shape of that.** The reason
  to keep ASK's build isolated (Option B) is to treat it as a foreign dependency
  we barely touch — but the graft means we *do* reach into it, and Phase 1's
  whole thesis is that ASK is now our keyboard, not a library we rent.
- **The seam is the point, and A makes it a one-liner.** `project(":core-personas")`
  is idiomatic, unmissable in review, and cannot silently misconfigure the way
  coordinate substitution can. B pays indirection forever for the module the
  fork is built around.
- **Two Gradle versions is a standing tax, not isolation.** B's headline
  advantage — ASK keeps 9.2.1 — means every ASK upgrade *and* every root change
  must stay compatible across two toolchains, and contributors juggle two
  wrappers. A pays that cost once (converge on ASK's newer toolchain) and is
  done.
- **Rent is comparable.** Both edit `:ime:app` for the dependency; A also folds
  ASK's `settings.gradle` into ours (a bounded, one-time reconciliation), B adds
  permanent composite wiring. Neither is zero once the graft lands; A's is
  simpler to hold in one `UPSTREAM-MODIFIED.md`.

The case *for* B is real and worth stating: if converging the root onto Gradle
9.2.1 / JDK 21 turns out to break our `core-*`/`app` in ways that cost more than
a day, B's version isolation becomes the pragmatic choice, and the recommendation
should flip. That is the one experiment worth running before this ADR is accepted.

## Consequences

- **PR #18 does not merge as-is under either option.** It also has a separate
  blocker the review flagged — it drops the stub's `InputMethodService` from the
  app APK, so `main` loses its keyboard (contra ADR-0004, which keeps the stub
  until the graft). The stub is restored regardless of A/B; that fix is
  independent of this decision.
- **Under A:** the vendoring in #18 is reusable, but the `includeBuild` wiring is
  replaced by unified `include(...)`, the root toolchain bumps to ASK's, and the
  reconciliation edits land in `UPSTREAM-MODIFIED.md` (rent stops being zero, as
  it always would at the graft).
- **Under B:** `core-personas`/`core-providers` move to a standalone build; the
  module-law boundary is unchanged (still pure Kotlin, still ours) but their
  path moves, so ADR references and CI paths update with them.
- **This ADR gates the graft PR, not the vendoring evidence.** The 5,966-file
  snapshot, provenance docs, and empty-until-now ledger from #18 stand on their
  own; what changes is how they are wired.
- **Whichever wins, ADR-0004's "wire ASK's modules into settings.gradle" line is
  now precise:** either literally (A) or via a shared included build (B). This
  ADR is the amendment that makes that sentence mean something.

## Independent review (2026-07-21)

Three different model families reviewed this ADR in parallel, each pointed at
this file plus the live Gradle tree, none shown the others' output. 2 of 3
land on Option A; the dissent surfaced a real Gradle mechanism the ADR's
"never the reverse" wording missed, which the third opinion then verified and
explained why it doesn't change the pick.

- **agy / Gemini — agrees with A.** Confirms the root modules
  (`core-personas`, `core-providers`, `app`) have no source-level obstacle to
  the toolchain bump. Flags two omissions the ADR didn't cover: reconciling
  ASK's own version catalog (`android/keyboard/gradle/libs.versions.toml`)
  into the root's, and ASK's `apply from: "${rootDir}/..."` script imports
  breaking once `rootDir` resolves to `android/` instead of
  `android/keyboard/` under a unified build.
- **codex (gpt-5.6-sol) — dissents.** Found that Gradle composite builds
  support `includeBuild(".")` — a build can self-include itself, making its
  own projects addressable by coordinate from other included builds. That
  contradicts the ADR's "never the reverse" framing and opens a fourth
  option: keep the two builds separate, self-include the root, give `core-*`
  stable coordinates, let `:ime:app` depend on them by coordinate — no
  unified build, no third shared build. Also measured real rent Option A's
  writeup understates: 164 ASK Gradle files reference `rootDir`, 89 use
  ASK-rooted project paths, and ASK carries its own `libs` catalog — folding
  ASK's module tree into the root is not a clean settings-file merge. Flags
  that Kotlin 2.1.21 (root) isn't officially supported on Gradle 9.2.1 (ASK),
  so the toolchain bump is a real upgrade, not a wrapper-version edit. Also
  raised a gap none of the ADR's options address: which module ships as the
  PersonaSpeak APK — root `app` or ASK's `:ime:app` — is still undecided, and
  composition doesn't answer it.
- **opencode/GLM-5.2 — agrees with A, corrects the reasoning.** Independently
  verified `includeBuild(".")` against Gradle's docs (codex's finding holds)
  but shows it doesn't rescue Option B's premise: **a composite build
  executes everything under the *including* build's single Gradle wrapper**
  — the included build's own wrapper version is not used for that
  invocation. Today that's the root's 8.14, and AGP 8.13.2 (ASK's) is not
  reliably compatible with Gradle 8.14. So Option B (or codex's self-include
  variant) either converges the root to 9.2.1 anyway — paying Option A's
  toolchain cost while keeping extra indirection — or downgrades ASK's AGP,
  which is rent paid at every future re-vendor. B's headline advantage
  (`ASK keeps its own toolchain`) does not survive contact with how composite
  builds actually execute. Separately confirms, with a source citation, that
  the PR #18 stub-removal blocker is real: `android/app/build.gradle.kts:34-36`
  drops the `:keyboard` dependency with a comment punting the wiring to "the
  graft PR," so `main` currently has no `InputMethodService` in the APK at
  all. Also sizes Option A's rent more precisely than the ADR does: ASK ships
  a `buildSrc/` with three custom Gradle plugins and roughly 94 module
  scripts that read SDK versions from `gradle/root_all_projects_ext.gradle`
  — folding that into a Kotlin-DSL root is a real port, not "a few module
  scripts."

**Net effect on the decision:** unchanged. The dissenting mechanism
(`includeBuild(".")`) is real, but the third opinion shows it doesn't
preserve toolchain isolation once you account for single-wrapper composite
execution — so it doesn't buy back Option B's one advantage. Recommendation
stands at **Option A**, with two corrections carried into the record: the
"never the reverse" wording above is imprecise (should read "no idiomatic
reverse path without self-inclusion, which doesn't change the version-
convergence requirement"), and Option A's rent includes ASK's `buildSrc` and
~94-script `rootDir` dependency, not just a settings-file merge. The
toolchain-convergence experiment this ADR flagged as decision-relevant is
still unrun in a live environment — all three reviews reasoned from static
inspection, none executed `./gradlew`. That experiment is the next thing to
run before this ADR's status moves past Proposed.
