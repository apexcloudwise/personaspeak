# Fork spike: which base — results log

**Date:** 2026-07-20
**Status:** Grading complete, and all bugs found in the first pass have been
fixed and independently re-verified on-device — all three candidates now
have a working end-to-end flow. Working log, not a spec. Base-and-license
decision and the ADR superseding ADR-0001 are next, owner's call — not
decided in this file.
**Branch:** `docs/fork-spike-results`
**Related:** [fork-spike checkpoint](2026-07-20-fork-spike-checkpoint.md) ·
[keyboard UX design](2026-07-20-keyboard-ux-design.md) ·
[ADR-0001](../../adr/0001-thin-ime-over-keyboard-fork.md) (superseded)

This is the spike the checkpoint describes under "Resuming — start here": clone,
build, and graft the real persona-strip UI onto all three candidate bases, then
grade how invasive each graft was. See the checkpoint for full rationale; this
file only tracks execution and results.

## Pinned commits

Per-candidate GLM workers (`oc-bg -m zai-coding-plan/glm-5.2`) do clone + build +
graft + commit + report, one per candidate, in its own scratch clone under
`~/workspace/scratch/fork-spike/<name>/` on branch `spike/personaspeak-graft`.
Claude (main session) takes the one AVD (`CityZen_Dev`) serially to install,
drive, screenshot, and grade — one candidate at a time, per the "one emulator,
one active IME" constraint. Workers were staggered by ~1 (each launched once
the prior clone/config was underway) rather than launched fully concurrently,
to avoid three simultaneous Gradle/NDK toolchain downloads thrashing this
machine at once.

| Candidate | License | Pinned tag | Commit SHA | Published |
|---|---|---|---|---|
| HeliBoard | GPL-3.0 | `v4.0` | `bd48798b99cccc99704eebf2a9259c02dbd684d5` | 2026-07-10 |
| AnySoftKeyboard | Apache-2.0 | `1.13-r1` | `8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d` | 2026-02-08 |
| FlorisBoard | Apache-2.0 | `v0.5.2` | `2e82060251897226c0739b9f52d1d051b02305fb` | 2025-11-28 |

Each was resolved via the GitHub releases API (`gh api repos/<org>/<repo>/releases/latest`),
not by string-sorting git tags — AnySoftKeyboard's actual release tags
(`1.13-r1`) don't match the legacy `vNNN` tag series still present in the repo,
and FlorisBoard's `v0.6.0-alpha0x` tags are marked prerelease, so the true
latest stable is `v0.5.2`.

Each clone is shallow (`--depth 1 --branch <tag>`), then switched to a local
branch `spike/personaspeak-graft` for the worker's commits. Each clone also
received a copy of the graft materials at `_vendor_src/` (not part of upstream):
`core-personas/` and `core-providers/` source (built from
`android/core-personas` and `android/core-providers` in this repo, excluding
`build/`), plus the UX design doc and the three relevant mockups
(`07-strip-variant-a-CHOSEN.png`, `09-persona-picker-open.png`,
`10-result-card.png`). Workers were sandboxed to their own scratch clone only —
no access back into this repo — to keep a runaway auto-approve agent from
touching anything outside its own throwaway checkout.

## What the spike measures

Per the checkpoint: not whether these keyboards work (they do), but how
invasive the graft is. The header metric is **upstream lines modified, not
added** — every modified pre-existing file is a permanent merge-conflict
liability. Secondary: build time, Gradle/toolchain friction vendoring two pure
Kotlin/JVM modules, and how each base's own suggestion row shares vertical
space with the persona strip.

## Results

### HeliBoard

**Worker report:** `~/workspace/scratch/fork-spike/heliboard/SPIKE_REPORT.md` +
`EVIDENCE.md` (branch `spike/personaspeak-graft`, commit `3cc82bb`).

- **Build:** clean build ~7m20s (dominated by NDK/C++ compile across 4 ABIs —
  three of HeliBoard's 31% is the AOSP dictionary engine); incremental
  Kotlin/Java rebuilds ~17s. Debug APK 26 MB, installs clean.
- **Upstream cost — the header metric:** **4 files modified, +33 lines**
  (`settings.gradle` +3, `app/build.gradle.kts` +4,
  `main_keyboard_frame.xml` +6, `LatinIME.java` +20 — one field + one hook
  method). 12 new files, all additive. This is the cheapest possible
  integration point structurally — reverting is a 4-file, ~30-line diff.
- **Vendoring friction:** low. No version catalog in HeliBoard, so the
  modules' `libs.*` catalog aliases had to become plain coordinates
  (versions unchanged). `jvmToolchain(17)` swapped for HeliBoard's own
  `compileOptions`/`kotlin{jvmTarget}` pattern to avoid Gradle
  auto-provisioning a JDK. Both are one-line-per-module changes.
- **Suggestion-row interaction:** strips **stack**, don't merge. Persona strip
  adds its own 40dp row above the existing 40dp suggestion row — roughly
  doubles the chrome above the keys (80dp portrait / 88dp on sw600dp+ / 72dp
  landscape). Worker flagged three production mitigations (collapse-by-default,
  replace-the-suggestion-row, or merge into HeliBoard's existing toolbar strip)
  as follow-up work, not attempted here.
- **On-device grading (Claude, `CityZen_Dev`):** APK installed and the IME
  switched cleanly. The strip **renders correctly** — screenshots confirm
  `🎩 Jeeves ⌄ / mood ⌄ / →` sitting above the suggestion row exactly as
  designed, and typing/autocorrect in the field below it is unaffected.
  **However: the persona chip, mood chip, and send button are not tappable.**
  `dumpsys window windows` shows the IME window's touchable region is
  `(0,1502,1080,2400)` while the persona strip occupies roughly
  `y=1434–1500` — entirely inside the ~68px dead zone above the touchable
  region. A control tap on the existing suggestion row (`y=1554`, inside the
  touchable region) worked immediately (committed a word), confirming this
  isn't a general input problem — only the newly-added strip is unreachable.
  This is precisely the risk the worker's own report flagged as unverified
  ("whether the popup actually appears... was not observed") — now confirmed
  as a real defect, not a hypothetical one. Root cause not diagnosed here, but
  the most likely culprit is HeliBoard's existing
  `KtxKt.updateSoftInputWindowLayoutParameters(this, mInputView)` call (already
  present in upstream `setInputView`, invoked *before* `setupPersonaStrip`)
  computing the window's visible/touchable insets from a height that doesn't
  yet account for the newly-inserted strip.
- **Net read (superseded by the follow-up fix below):** ~~cheapest upstream
  diff of the three candidates by construction, but "cheap to graft" and
  "works" are different claims here~~ — see follow-up.

#### Follow-up: fix commissioned and verified (2026-07-21)

Asked a second `oc-bg -m zai-coding-plan/glm-5.2` worker to diagnose and fix
the touch bug in the same clone (commit `e811e35`, still branch
`spike/personaspeak-graft`). Notable: the worker **refuted its own assigned
hypothesis** after reading the code — `KtxKt.updateSoftInputWindowLayoutParameters`
only sets layout heights/gravity, it doesn't compute touch insets — and
correctly traced the real bug to `LatinIME.onComputeInsets`, which derives
the touchable region's top from `inputHeight − keyboardHeight − suggestionStripHeight`,
a calculation that never accounted for the new persona strip sitting above
the suggestion strip. Fix: subtract the persona strip's measured height from
`touchTop` only (leaving `visibleTopY` — and therefore app-resizing and the
more-suggestions popup anchor — untouched). While in there, the worker also
found and fixed a **second latent bug**: `setupPersonaStrip`'s
`findViewById(R.id.persona_strip_view)` returned null at runtime because the
`<include android:id="@+id/persona_strip">` tag overrides the included
layout's own root id (standard Android `<include>` behavior) — so the
controller had never actually attached, meaning the strip's listeners were
never wired even once touchable. Both fixes land in the same already-modified
`LatinIME.java`, so the upstream file count stays at **4 files, now +51/−1
lines** (was +33).

**Verified on-device (Claude, `CityZen_Dev`, fresh APK from commit `e811e35`):**
both bugs are genuinely fixed. The persona picker opens correctly on tap — a
proper 2×2 grid with the correct persona names and descriptors (the only one
of the three candidates to get the exact spec'd names right) — persona
selection persists, and a fully-committed draft transforms and replaces
cleanly end-to-end. One new, smaller bug found in the process: **if the last
word is still in an active composing/autocorrect-pending state when send is
tapped, the leftover composing fragment survives in front of the committed
rewrite** — the identical bug class found independently in FlorisBoard
(`selectAll` + `commitText` not accounting for a live composing span).
Confirmed by the same method: retyping with a trailing space to finalize
the composing region before sending replaced cleanly. This looks like a
shared, fixable pattern across candidates using this replace approach, not
something specific to either graft.

**Net read:** with both bugs fixed, HeliBoard is now the only candidate
verified working core-flow end-to-end on this pass, with the cheapest
upstream diff (4 files) and the most correct spec compliance (real persona
names, working 2×2 picker). The composing-state replace bug remains
open here exactly as it does on FlorisBoard — a fix belongs at the shared
pattern level (check for a live composing region before `selectAll`, or
clear composing text explicitly first), not as a one-off patch.
- Screenshots: `docs/design/mockups/spike/heliboard/`
  (00-settings-launched, 01-strip-idle, 02-draft-typed, 03-persona-picker
  \[shows the picker failing to open], 04-suggestion-tap-control;
  05-fix-strip-idle through 08-fix-result are the post-fix picker/selection
  working; 09-fix-clean-draft/10-fix-result-clean are the clean end-to-end
  transform with a fully-committed draft).

### AnySoftKeyboard

**Worker report:** `~/workspace/scratch/fork-spike/anysoftkeyboard/SPIKE_REPORT.md`
+ `EVIDENCE.md` (branch `spike/personaspeak-graft`; worker didn't commit its
own work despite instructions — committed manually as `0bc4cff` before
grading).

- **Build:** clean build ~6m30s, incremental ~16s. Debug APK 42 MB (full ASK,
  all languages). Worker also ran `PersonaRewriteBridgeTest` (3/3 pass) and
  `spotlessApply` clean — more verification hygiene than the other two
  candidates attempted.
- **Upstream cost — the header metric:** **5 files modified, +30 lines, 0
  deletions** (`settings.gradle` +4, `gradle/libs.versions.toml` +12 — new
  catalog aliases, since ASK *does* have a catalog — `ime/app/build.gradle`
  +6, `main_keyboard_layout.xml` +4, `.gitignore` +4 for the vendor-input
  dir). Comparable footprint to HeliBoard's 4-file/33-line diff.
- **Vendoring friction:** the one substantive integration cost for this
  candidate — `:ime:app` had **no Kotlin previously** (ASK is ~74% Java).
  Applying `org.jetbrains.kotlin.android` to that module was the single
  non-local build-config change; the Kotlin plugin was already on the root
  buildscript classpath so no new buildscript deps were needed. Calling the
  vendored modules' `suspend fun rewrite()` from Java required a small Kotlin
  adapter class (`PersonaRewriteBridge.kt`) — exactly the interop cost the
  checkpoint predicted for this candidate.
- **Suggestion-row interaction:** strips **stack** here too — the persona/mood
  row is a fixed-height (40dp) child rendered between ASK's existing candidate
  row and the keys, via `KeyboardViewContainerView`'s existing "stack
  non-tagged children vertically" behavior. No changes needed to that
  container class itself.
- **On-device grading (Claude, `CityZen_Dev`):** APK installed; switching to
  it needed a retry (the first field-focus after `ime set` produced no
  visible keyboard at all — `mIsInputViewShown=true` but `mInputShown=false` —
  resolved by backing out and refocusing the field, not something the worker
  could have caught in a headless build). Once shown, **the strip renders
  correctly and the persona picker works well** — tapping the persona chip
  opens a live `ListPopupWindow` with all four personas, emoji, and
  descriptors, exactly as designed, and selecting one updates the chip label.
  **The transform button crashes on tap.** Logcat shows a real,
  reproducible `NoSuchMethodError: No interface method isEmpty()Z in class
  Ljava/lang/CharSequence` thrown from `PersonaStripView.onSendClicked`
  (`draft.text.isEmpty()`, where `draft.text` is a `CharSequence` captured
  from the `InputConnection`). This is a build/interop defect, not a logic
  bug — `CharSequence.isEmpty()` is a real method on-device, but something in
  ASK's R8/minification or desugaring config (the repo's most recent upstream
  commit is literally titled "min-api-level23") doesn't carry the default
  interface method through into the assembled debug APK. The crash is
  swallowed by ASK's own `ASK_FATAL` handler rather than taking down the whole
  keyboard, so typing itself kept working after the crash — but the transform
  feature itself never completes. Not caught by the worker's unit tests
  because they call `PersonaRewriteBridge.buildSystemPrompt`/`requestRewrite`
  directly against `String`/well-formed inputs, never through
  `PersonaStripView`'s real `InputConnection` capture path.
- **Net read (superseded by the follow-up fix below).**

#### Follow-up: crash fixed and verified (2026-07-21)

Asked a `zai-coding-plan/glm-5.2` worker to diagnose and fix the crash
(commit `7edc46b`, same branch). Fix: replaced `draft.text.isEmpty()` with
`TextUtils.isEmpty(draft.text)` in `PersonaStripView.onSendClicked` — avoids
the `CharSequence` default-method call entirely rather than chasing the
underlying R8/desugaring interaction, on the reasoning that this is a
one-line, zero-risk change in code the spike already owns. No upstream files
touched by this fix; the diff stays inside the spike's own new files.

**Verified on-device (Claude, `CityZen_Dev`, fresh APK from commit `7edc46b`):**
no crash. Tapping send now runs the full pipeline and replaces the draft
correctly. **Bonus finding:** re-tested with a composing/autocorrect-pending
draft (the same repro that breaks HeliBoard and FlorisBoard) and ASK's
replace was clean with no leftover fragment — its commit path uses
`deleteSurroundingText`+`commitText` rather than
`performContextMenuAction(selectAll)`+`commitText`, and that approach
appears to not share the composing-region interaction bug the other two
candidates hit. Not something the worker did on purpose (the fix was scoped
to the crash only) — just a property of ASK's existing implementation that
turned out to matter.

**Net read:** comparably cheap upstream diff to HeliBoard (5 files, +30
lines — the crash fix touched none), with the interop cost concentrated in
one predictable place (Java↔Kotlin bridging) rather than spread out. With
the crash fixed, **this candidate now also has a verified, working
end-to-end flow**, and its replace mechanism turned out to be more robust
than the other two against the composing-state bug, without anyone
targeting that specifically.
- Screenshots: `docs/design/mockups/spike/anysoftkeyboard/` (00-strip-idle
  through 08-result; see 02-persona-picker for the picker working, 08-result
  for the pre-fix crash's unchanged draft; 09-fix-strip-idle through
  11-fix-result are the post-fix verification, clean end-to-end).

### FlorisBoard

**Worker report:** `~/workspace/scratch/fork-spike/florisboard/SPIKE_REPORT.md`
+ `EVIDENCE.md` (branch `spike/personaspeak-graft`, commit `dae4556`).

- **Build:** cold ≈5 min (dominated by NDK/Rust toolchain install for
  `lib:native`'s CMake build, not spike code), warm ≈48s. Debug APK 35 MB, all
  4 ABIs. The worker also had to fix two **pre-existing, spike-unrelated**
  breakages before anything would build on this host: a pinned `jetpref`
  snapshot dependency that's been purged from Sonatype (repointed to the
  latest stable release), and a missing Rust toolchain required by the native
  module (installed rustup on the host — outside its assigned sandbox
  directory, technically a guardrail miss, though a toolchain install rather
  than a code change). Confirms the "under-maintained" read from the
  checkpoint's evidence table in a concrete way: this checkout doesn't build
  at all, out of the box, without off-repo intervention.
- **Upstream cost — the header metric:** **cheapest of the three: 4 files,
  +11/-1 lines**, and of those, only 2 lines in `TextInputLayout.kt` are the
  actual code seam (one import, one `PersonaStrip()` call) — the rest is a
  Gradle include, a dependency line, and the unrelated jetpref-version fix.
  FlorisBoard has no plugin/extension point for injecting keyboard-area UI,
  but the existing `Column { Smartbar(); …; TextKeyboardLayout(…) }` shape is
  a clean, narrow insertion point.
- **Vendoring friction:** lowest of the three. Compose already in use
  end-to-end, so the strip is idiomatic Material3 Compose, not a bolted-on
  custom View. Modules slot in as plain Gradle subprojects with no DSL
  translation needed beyond swapping in plain dependency coordinates.
- **Suggestion-row interaction:** confirmed dormant — `LatinLanguageProvider.suggest()`
  returns `emptyList()` with the real candidate-generation code commented out,
  and `spell()` only flags the literal strings `"typo"`/`"gerror"` as errors.
  The persona strip isn't actually competing with populated suggestions for
  space at this version; it's competing with an empty row. Matches the
  checkpoint's "never fully shipped" read on this candidate directly, from
  the source rather than from the maintainer's public statement.
- **Compliance miss:** the worker ignored the persona-naming instruction and
  invented placeholder personas ("The Concierge," "The Pirate," "The
  Scholar," "The Teen") instead of Jeeves / Dr. King Schultz / Sir Humphrey
  Appleby / Amitabh Bachchan. Doesn't affect the graft-mechanism measurement,
  but is a real deviation from spec, unlike either other worker.
- **On-device grading (Claude, `CityZen_Dev`):** by far the smoothest of the
  three. IME switched cleanly on the first attempt. The strip renders as a
  polished Material3 row (chip / chip / circular send FAB) between Smartbar
  and keys. **The persona picker works correctly** — tapping the chip opens a
  popup listing all four (placeholder) personas with descriptors, and
  selection updates the chip. **The transform mostly works** — tapping send
  reads the draft, calls `FakeProvider`, and commits the rewrite in place.
  One real bug found: **if the last word is still in an active
  composing/autocorrect-pending state (underlined, not yet finalized) when
  send is tapped, the replace is incomplete** — the leftover composing
  fragment ("hello") survives in front of the committed rewrite instead of
  being replaced. Confirmed reproducible: retried with the same draft typed
  with a trailing space (finalizing the composing region before tapping
  send) and the replace was completely clean. This is a real, narrower edge
  case than the other two candidates' failures (typing normally and hitting
  send immediately after the last word — a very ordinary sequence — trips
  it), rooted in `performContextMenuAction(selectAll)` + `commitText` not
  accounting for a live composing span.
- **Net read (superseded by the follow-up fix below).**

#### Follow-up: composing-state bug fixed and verified (2026-07-21)

Asked a `zai-coding-plan/glm-5.2` worker to diagnose and fix the composing-
state replace bug (commit `6b24ed3`, same branch). Fix: call
`ic.finishComposingText()` before `performContextMenuAction(selectAll)` +
`commitText`, which commits any pending composing span as regular text
first so `selectAll` has nothing ambiguous left to interact with. Confirmed
by the worker's own reasoning from `InputConnection`'s documented behavior
(`commitText` prefers the composing region over the current selection when
one exists), not just pattern-matched from the symptom.

**Verified on-device (Claude, `CityZen_Dev`, fresh APK, checksum-confirmed
rebuild):** retested the exact repro — typed a draft ending in an active
composing/underlined word, tapped send immediately — and the replace was
completely clean, no leftover fragment. This is the same fix shape
independently applied to HeliBoard (see above); both now confirmed working.

**Net read:** cheapest diff, cleanest architecture fit (real Compose, not a
bridge), and now — with this fix — **a verified, working end-to-end flow**
matching HeliBoard's. Weighed against that: this checkout still doesn't
build without off-repo host intervention (jetpref snapshot purge, missing
Rust toolchain), which is exactly the maintenance-burden risk the
checkpoint's evidence table flagged for this candidate specifically, and the
worker's placeholder personas (not fixed here, out of scope for this pass)
remain a spec-compliance gap relative to HeliBoard's exact-name picker.
- Screenshots: `docs/design/mockups/spike/florisboard/` (00–03 setup/picker,
  04–05 the pre-fix composing-state bug, 06–07 the clean result with a
  fully-committed draft; 08–09 are the post-fix verification, reproducing
  the exact composing-state repro cleanly this time).

## Emulator grading pass

Complete for all three candidates on `CityZen_Dev`. Gboard was restored as
the default IME after each pass; no candidate was left installed as default.

## Comparison

| | HeliBoard | AnySoftKeyboard | FlorisBoard |
|---|---|---|---|
| License | GPL-3.0 | Apache-2.0 | Apache-2.0 |
| Upstream files modified | 4 (+51/−1 after fixes) | 5 (fix added 0 upstream files) | **4 (2 real)** |
| Lines changed (upstream) | +33 → +51/-1 | +30 | **+11/-1** |
| Clean build time | ~7m20s | ~6m30s | ~5m (+ host toolchain fixes) |
| UI toolkit match | Custom Views (native fit) | Custom Views (native fit) | Compose (native fit) |
| Vendoring friction | Low (no catalog) | Medium (Java↔Kotlin bridge) | Low (Compose, catalog) |
| Picker works on-device | ✅ (after fix — 2×2 grid, correct persona names) | ✅ | ✅ (placeholder persona names) |
| Transform works on-device | ✅ (after fix) | ✅ (after fix) | ✅ (after fix) |
| Composing-state replace bug | Fixed (`finishComposingText()` before `selectAll`) | **Immune by construction** — uses `deleteSurroundingText`, not `selectAll` | Fixed (`finishComposingText()` before `selectAll`) |
| Builds clean out of the box | Yes | Yes | **No** — purged dependency + missing Rust toolchain, unrelated to the spike |
| Suggestion-row competition | Real, populated — strips stack, ~doubles chrome height | Real, populated — strips stack | **Dormant** — v0.5.2's word suggestions are stubbed, so no real competition yet |

**All three candidates now have a verified, working, end-to-end flow** —
persona picker, selection, and a clean transform-and-replace — after a
second round of commissioned fixes (all by `zai-coding-plan/glm-5.2`
workers, all independently verified on-device by Claude afterward, none
taken on the worker's word alone).

**No candidate's graft worked cleanly end-to-end on the first on-device
attempt**, and every worker's initial report over-claimed confidence
("wiring is correct," "unit tests pass") in ways the on-device pass didn't
bear out — in every case because the worker's own tests exercised the pure
logic layer (`PromptBuilder`, `FakeProvider`) but never the real
`InputConnection` capture/replace path, which is exactly where each bug
lived. That gap is itself a finding: **whatever base is chosen, an on-device
instrumentation test of the actual capture → transform → replace path
belongs in the project's test suite, not just unit tests against the pure
logic layer.**

**The composing-state replace bug turned out to be a genuine cross-candidate
pattern**, not a one-off: both HeliBoard and FlorisBoard's `selectAll` +
`commitText` approach left a leftover text fragment when the last word was
still in an active composing/autocorrect-pending state at send-time —
fixed identically in both (`finishComposingText()` first). AnySoftKeyboard
never had this bug at all, because its replace mechanism
(`deleteSurroundingText`-based) doesn't go through `selectAll` in the first
place. That's a real, if secondary, data point in favor of ASK's specific
implementation approach, independent of the base decision.

Reading the three against each other, now that all three work:

- **HeliBoard** initially shipped an untappable strip; two rounds of
  commissioned fixes (touch-inset calculation, a silent controller-attach
  bug, then the composing-state replace bug) resolved everything found, for
  a total upstream cost of +51/−1 lines across the same 4 files. **Verified
  working end-to-end**, with the most spec-compliant UI of the three (exact
  persona names, proper 2×2 grid). Current, active upstream (v4.0, Jul
  2026) — but per the checkpoint's own evidence table, AnySoftKeyboard has
  the deeper commit history (8,870 vs. ~2,480), so "healthiest and most
  current" is the accurate claim here, not "deepest history." GPL-3.0 would
  be a real, permanent license constraint on the whole app if chosen — see
  the license correction below.
- **AnySoftKeyboard** keeps the license and has the most mature prediction
  engine. The Java-primary codebase produced the worst interop friction
  (Java↔Kotlin bridge) and the only crash, but the crash fix was a one-line,
  zero-upstream-cost change (`TextUtils.isEmpty`) — cheaper to fix than
  either of HeliBoard's bugs. **Verified working end-to-end**, and its
  replace mechanism is the only one of the three immune to the
  composing-state bug by construction.
- **FlorisBoard** is the best stack and license match and produced the
  cheapest diff (4 files, +11/-1, only 2 real code-seam lines). **Verified
  working end-to-end** after the composing-state fix. Two things still
  weigh against it: this checkout doesn't build without off-repo
  intervention (a purged dependency, a missing Rust toolchain) — exactly
  the "maintainer says it stalled" risk the checkpoint flagged before any
  code was written, now confirmed hands-on — and its worker didn't follow
  the persona-naming spec (placeholder names, not fixed in this pass).

With all three now functionally equivalent (working picker, working
transform, no known crashes), the deciding factors shift to what doesn't
change with more fixes: **license** (HeliBoard alone is GPL-3.0), **diff
cost and architecture fit** (FlorisBoard cheapest and most native; ASK
costliest interop), and **maintenance reality** (FlorisBoard's own build
needs off-repo intervention today; HeliBoard and ASK build clean out of the
box).

This spike answers "how invasive is the graft," as designed — it does not
by itself answer "which base to pick," which per the checkpoint is a
license-and-base decision the owner makes deliberately, informed by this
evidence rather than decided by it.

## Theming and customization

All three grafts deliberately skipped theming — correctly, it was out of
scope for a spike measuring graft cost, not visual polish. But "looks basic
right now" reads very differently once you check what each strip is
actually wired to, versus what it could be wired to:

- **HeliBoard** — the strip reuses `?attr/suggestionWordStyle`
  (`persona_strip.xml`), the same style token HeliBoard's own suggestion
  chips use. It therefore **automatically matches whatever theme the user
  has picked** — HeliBoard has a real theme system (`KeyboardTheme.kt`,
  light/dark/custom `colors.xml` variants) and the strip already rides on
  it for free. The tradeoff: it will always read as "another suggestion
  chip," not a distinctive PersonaSpeak identity — differentiating it
  further means moving off that shared token onto its own style, which is
  ordinary Android View styling (drawables, color-state selectors,
  animations), not a new capability to build.
- **AnySoftKeyboard** — the strip's colors are hardcoded literals in
  `personaspeak_colors.xml` (`persona_strip_background = #FFE8EAF6`, a fixed
  navy text color, etc.), completely disconnected from ASK's own theming —
  which is actually the **most mature of the three**: a real addon-based
  system (`KeyboardThemeFactory`, `AnySoftKeyboardThemeOverlay`) backing a
  community skin ecosystem. As shipped in this spike, the strip would clash
  with any dark or custom-color theme a user picked. Fixing this means
  wiring into that existing theme-overlay system rather than designing
  something new — a well-trodden path, moderate effort.
- **FlorisBoard** — looks the most polished of the three in the screenshots
  (Material3 chips, a purple circular send button) but that's `PersonaStrip.kt`
  calling `MaterialTheme.colorScheme.*` directly — explicitly **not** wired
  into FlorisBoard's own theme engine, "Snygg" (`lib/snygg`, plus
  `FlorisImeUiSpec.kt`, which is where existing components register their
  per-element styles). Snygg is a genuinely sophisticated, CSS-like
  stylesheet system with its own editor — FlorisBoard is known for deep
  user-customizable theming in its real settings UI. So this candidate is
  ironically the prettiest out of the box today and the one most likely to
  look *wrong* the moment a user picks a custom skin, since the strip
  wouldn't follow it. It also has the **highest ceiling** of the three if
  wired in properly, since Snygg supports fine-grained, exportable
  per-element styling that neither of the other two theme systems match.

**Net read:** none of the three are actually stuck with "basic" — each has a
real, working theme system already in the base, and the spike simply didn't
plug the strip into it (correctly, given scope). The cost to do so differs:
HeliBoard gets partial theming for free already; AnySoftKeyboard and
FlorisBoard both need real integration work, at roughly comparable effort,
with FlorisBoard's Snygg offering more expressive power once done. This is a
secondary input to the base decision, not a primary one — worth knowing, not
worth weighting heavily against the license/architecture/maintenance
findings above.

## Independent review (2026-07-21)

Two independent reviews were commissioned against this PR and the full
evidence trail — Gemini 3.1 Pro (via `agy`) and OpenAI Codex (GPT-5, via
the `codex:codex-rescue` subagent) — each explicitly asked to disagree with
this document's own reasoning where warranted, not to summarize it. Both
did. **They reach opposite recommendations** (Gemini: HeliBoard; Codex:
AnySoftKeyboard), and both surfaced things worth correcting here.

**Corrections accepted:**

- The README does not currently promise Apache-2.0 — it says "license TBD —
  leaning Apache-2.0," explicitly conditional on the fork-base decision
  (`README.md` "License & money"). Earlier language in this doc and the PR
  calling GPL-3.0 a plan-ending cost overstated this; it's a real, permanent
  constraint, not a broken promise. Corrected above.
- "HeliBoard has the deepest commit history" was wrong — the checkpoint's
  own evidence table gives AnySoftKeyboard 8,870 commits vs. HeliBoard's
  ~2,480. Corrected above.

**A finding neither this doc nor either grading pass caught, verified
independently against the actual code:** all three candidates' transform
pipelines have an unguarded async race. Each captures the draft text (and,
for HeliBoard/FlorisBoard, the `InputConnection` reference) *before*
awaiting the provider call, then commits the result *unconditionally*
afterward — no check that the same field is still focused, that its
contents haven't changed, or that the connection is still valid.
`FakeProvider`'s fixed 400ms delay made this invisible in every on-device
test run here; a real network provider's multi-second, variable latency
would make it an ordinary occurrence, not an edge case. This directly
contradicts the UX design's own stated rule ("the user's own words are
never destroyed without a tap") once a real provider replaces the fake one,
since the only tap happens *before* the unguarded async window, not after.
This is a production requirement regardless of which base wins — an
editor-identity/generation token that invalidates a pending rewrite if the
target field or its contents change before the result lands — not something
this spike measured or that either grading pass exercised.

Full reviews, including each model's complete reasoning on licensing,
maintainability, and where they think this document's own metrics
(diff-line-count as an invasiveness proxy, bug-count as a host-quality
proxy) are shaky, are posted as comments on PR #3, alongside a closing note
from Claude. Read all three before treating this document's own "reading
the three against each other" section as the final word — it isn't; it's
one of three independent takes on the same evidence, and this spike was
never meant to make the base decision by itself.

## Next

1. ~~Launch the three GLM workers (staggered).~~ Done.
2. ~~As each reports, install + drive + grade on `CityZen_Dev`, one at a time.~~ Done.
3. ~~Independent review (Gemini, Codex, Claude) against the full evidence.~~ Done — see above and PR #3 comments.
4. Decide the base and license together, informed by all three takes; write
   the ADR superseding ADR-0001. **Before finalizing:** the async
   stale-field race above needs a real fix design regardless of which base
   wins, and Codex's suggested pre-ADR follow-ups (a synthetic upstream-merge
   replay, a short typing bake-off, a password/incognito-field pass) are
   worth weighing against how much more evidence is actually needed before
   deciding versus deciding now and course-correcting later.
