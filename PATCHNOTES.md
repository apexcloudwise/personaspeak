# Patch Notes

Every merged PR gets an entry here, written the way patch notes should be:
every line true, every line deadpan, delivered in the register of
[VOICE.md](VOICE.md). Strip the jokes and you can still reconstruct the
changelog; that's the contract. The PR author writes the line, in the PR,
while the context is hot.

Newest first, like all respectable patch notes.

---

## 2026-07-21 — Six rooms furnished, hallway intact

- The six settings routes — home, persona browser, persona detail, AI
  providers, rewrite behaviour, privacy — are no longer placeholders reading
  "not yet built". They are real Compose screens with the dark theme, the
  glass cards, the teal accents, and the ⌘ wordmark bar the mockups drew. The
  route contract in `Routes.kt` is untouched; only the `Placeholder` calls
  were swapped out. The onboarding placeholders stand untouched — that hallway
  belongs to a different worker.
- The persona browser is a two-column bento of four sample personas (Jeeves
  wears the "Default" pill); a card opens its detail screen with the nav arg
  the foundation already promised. Detail renders hero, speech patterns,
  vocabulary pills, and sample lines, with a graceful "not found" panel if the
  id is bogus.
- Icons are mostly glyphs. The mockups use Material Symbols Outlined; the app
  pulls only `compose.material3` and `material-icons-core`, and `Close` lives
  in the extended set we don't ship. Glyphs match the emoji-forward mockup
  aesthetic anyway and skip a chunky new dependency. A `✕` closes a screen as
  well as a vector does.
- State is local and forgetful. Toggles, the rewrite-behaviour radio, and the
  masked API-key field are held in `remember` only — wiring them to real prefs
  and the keystore is a follow-up, not this slice.
- **Privacy screen, plainly:** the mockup's headline — "What we store:
  Nothing." — is not shipped. ADR-0005 finds that copy unsafe for a fork that
  now includes a predictive keyboard; the storage and security posture has not
  been audited end to end. The screen that landed says, specifically, that
  your text goes to the provider you chose (we run no servers), your key is
  stored on-device without a hardware-backed claim we can't yet back, the
  keyboard keeps learned words and persona choice locally, and telemetry is
  off in this build with no stronger claim until the audit finishes. This
  paragraph is intentionally not funny.

## 2026-07-21 — The hallway, before the rooms

- The `:app` module has walls now: a Compose theme built from DESIGN.md's
  tokens (every color val, the type scale, the radii) and a Navigation
  Compose skeleton wiring eleven routes — five onboarding, six settings —
  with the screens themselves pointedly absent. A placeholder reading "not
  yet built" is the whole UI; the foundation is the deliverable, not a demo.
- `navigation-compose:2.9.0` because the lifecycle line was already pinned
  there and the transitive graph lines up without a fight. The start route is
  `onboarding/welcome`; the only arg-bearing route is
  `settings/persona-detail/{personaId}`. IME-window overlays are excluded on
  purpose — those are states inside the keyboard, not destinations.
- Dark-only for now because DESIGN.md is dark-only; the same `darkColorScheme`
  feeds both slots until a light spec arrives. Real Outfit/Inter fonts are a
  follow-up, and a status-bar seam against the dark surface is noted but not
  fixed here.
- `./gradlew :app:assembleDebug` is green. The screen-implementation workers
  now have a route list to build against; this PR is the contract they sign.

## 2026-07-21 — The keyboard returns, from the same drawer we evicted it from

- The vendoring PR moved ASK into `android/keyboard/` and, as a side effect of
  being an ingestion-only PR, left the `app` APK with no `InputMethodService`
  at all — installable, unselectable. ADR-0006's independent review caught it
  and called it a blocker regardless of which Gradle composition wins.
- The old stub `PersonaBoardService` was recovered from `main` (it never made
  it into the vendoring commit) and landed **inside `app`**, not back under
  `android/keyboard/` — that path belongs to ASK now. The package
  `…personaspeak.keyboard` sits beside `…personaspeak.app` in the same source
  root, and the manifest/strings/method-xml came with it. No API drift; the
  stub compiled clean against the current Compose BOM and lifecycle.
- `:app:assembleDebug` is green, the APK installs, and
  `adb shell ime list -a` shows
  `biz.pixelperfectstudios.personaspeak/.keyboard.PersonaBoardService`
  registered with `BIND_INPUT_METHOD`. The app launches without crashing.
  Evidence: `docs/superpowers/specs/2026-07-21-stub-restoration-report.md`.
  Wiring ASK's own `:ime:app` is still the graft PR's problem; this just gives
  `main` a keyboard again in the meantime.

## 2026-07-21 — One build or two, before we cut a hole in the wall

- ADR-0006 settles the question the vendoring PR's review exposed: the clever
  `includeBuild` that vendored ASK with zero rent also walled off the two modules
  the fork exists to inject — a composite build's dependencies flow *inward*, so
  ASK's `:ime:app` cannot depend "up" into our `core-*`. The graft has no seam
  until this is fixed.
- Lays out the options — one unified Gradle build vs. composite-with-`core-*`-
  extracted — and recommends the unified build: the seam becomes a one-line
  `project(...)` dependency instead of coordinate substitution, and we stop
  paying the standing tax of two Gradle versions. Decision is the owner's; the
  one experiment that could flip it (does converging on ASK's newer toolchain
  break our modules?) is named.
- No code moved. This is the amendment that makes ADR-0004's "wire ASK's modules
  into settings.gradle" mean something specific.

## 2026-07-21 — Moved the keyboard in. Left it in its original boxes.

- AnySoftKeyboard `1.13-r1` is now physically present at `android/keyboard/` —
  5,966 upstream files, Apache-2.0 headers and `LICENSE` intact, dropped in via
  `git archive` with `.github/` and the (absent) `fastlane/` dir excluded. The
  stub `:keyboard` module that used to live there has been evicted; it was a
  placeholder and it knew it.
- `UPSTREAM.md` records the source repo, the pinned SHA, the exact archive
  command, and the re-vendor procedure. `UPSTREAM-MODIFIED.md` is the rent
  ledger: it lists every upstream-tracked file we have edited. It is currently
  **empty**, because we used `includeBuild("keyboard")` and changed zero
  upstream source. The plan is to keep that list short; this PR sets the bar
  at zero.
- `./gradlew :app:assembleDebug` from `android/` builds our host app.
  `./gradlew :ime:app:assembleDebug` from `android/keyboard/` builds ASK
  itself, producing a 42 MB debug APK with all 60-odd language packs and four
  ABIs of JNI dictionary. The graft PR — where the persona strip actually
  gets sewn in — is the follow-up. This PR is just the furniture delivery.

## 2026-07-21 — Reading our own privacy promise back to ourselves

- ADR-0005 catches a claim that quietly stopped being true: "Nothing is stored,
  logged" was honest for the thin IME we owned entirely, and is an overclaim for
  a forked predictive keyboard that keeps a learned-words dictionary to do its
  job. The fix is not to store less than a keyboard must — it is to stop
  conflating "stays on your phone" with "does not exist," and to say which is
  which.
- The privacy copy is now frozen until an inventory of what the vendored ASK tree
  actually stores and sends is done on-device — default posture: anything that
  leaves the phone is off, and proven off. No accusation against ASK; it is a
  keyboard doing keyboard things. The overpromise was ours.
- This is load-bearing text, so the entry above is the only joke you get.

## 2026-07-21 — Deciding how to move a keyboard into the house

- ADR-0004 settles how AnySoftKeyboard's tree enters the repo: a **vendored
  snapshot** at a pinned tag, edited in place, with the upstream diff kept as a
  hand-written manifest. Not a submodule (it fights the fact that we edit
  upstream files), and — after a reviewer caught the first draft's reasoning —
  not a subtree either, because our squash-merge plus linear-history policy
  flattens the merge ancestry that `git subtree pull --squash` needs, so its one
  trick works exactly once.
- The point of the manifest is the rent ledger: regenerate the pristine tree,
  diff it against ours, and "lines modified are rent paid forever" becomes a
  checklist instead of an excavation.
- No code moved yet — this decides the mechanism so the graft PR doesn't have to
  argue with itself mid-move.

## 2026-07-21 — The records catch up to the decision

- ADR-0003 merged, so the paperwork stopped lying: its status is now Accepted,
  ROADMAP admits the base is AnySoftKeyboard and the licence is Apache-2.0, and
  the UX doc's fork-base and licence open-questions are struck through.
- Left one gate honestly open — the stale-field race guard still has to be
  implemented before a real provider touches anybody's draft — because closing a
  ticket is not the same as writing the code, and the roadmap should not pretend
  otherwise.
- No behaviour changed; the next agent just stops taking orders from a map that
  predates the territory.

## 2026-07-21 — We picked a keyboard (PR #11)

- ADR-0003 lands the decision the whole fork spike was evidence for: fork
  **AnySoftKeyboard**, license the app **Apache-2.0**. The license did most of
  the picking — GPL-3.0 is a one-way door we chose not to walk through — and the
  rest fell out of facts already in hand (FlorisBoard's prediction engine is
  stubbed; ASK's is the grown-up of the three and actually builds). Adoption is
  provisional pending a typing sanity check and the stale-field race guard, both
  already ticketed. The three-way typing bake-off was retired before it could
  eat a day confirming a real engine beats an empty one.

## 2026-07-21 — The gate that watches the gate's more dangerous cousin (PR #10)

- Speced the editor-identity guard that stops a slow provider from rewriting the
  wrong field — or the right field with somebody else's freshly-typed words. The
  race was always there; `FakeProvider`'s brisk 400ms just kept it politely
  off-stage. No code lands here, and no base is picked — the fork-base ADR still
  owns that — but the contract is now written down where the implementation
  ticket can't misplace it. `FakeProvider`, for its part, remains a committed
  thespian and an unreliable witness.

## 2026-07-21 — The gate that watches the gate (PR #8)

- CI now refuses to merge a PR that leaves `PATCHNOTES.md` untouched — including, with great self-referential ceremony, this one. Carry the `no-patchnote` label for the rare genuine exception; the skip is announced loudly, never silently. The gate checks that the file was *touched*, not that the line was *good*. The last mile stays with the reviewer, where it belongs.

## 2026-07-21 — The bake-off nobody won on purpose (PR #3)

- Grafted the persona strip onto all three fork candidates — HeliBoard,
  AnySoftKeyboard, FlorisBoard — drove each on a real emulator, and graded how
  much of somebody else's keyboard we'd be signing up to maintain. Answer: less
  than feared, in all three cases.
- Every candidate's first graft passed its unit tests and broke on the device,
  in a different place each time. All fixed and re-verified on-device; none
  taken on a worker's word. The moral — an on-device instrumentation test of the
  capture→transform→replace path — is now written down where the next base
  can't miss it.
- Commissioned two independent reviews that reached opposite recommendations and
  told this document where its own reasoning was thin. Both are on the PR; one
  found an async stale-field race in all three pipelines that `FakeProvider` was
  politely hiding. Filed as a pre-ADR blocker.
- Did not pick the base. That's still the owner's call — this is the evidence,
  not the verdict.

## 2026-07-21 — The staff writes its own rulebook (PR #2)

- Reviewed the previous PR after it merged, wrote down the findings, then
  proposed rules that would have forbidden reviewing your own previous PR.
  The irony has been filed.
- The robot staff now has a house voice on duty: docs, guides, PR bodies, and
  conversation all wear the butler. Load-bearing prose — privacy, permissions,
  keys — still comes plain, because nobody wants a joke in a threat model.
- Added a Definition of Done, so "fully complete PR" is a checklist instead of
  a mood. A PR now arrives graded by someone who didn't write it, with its
  patch note already in this file.
- Shipped a PR template that asks for evidence and a patch note, and refuses
  to pretend a line pasted in the description counts as either.
- Ran this branch past a different model family, which found a privacy
  overclaim and made us rewrite it. That is the system working, and also
  slightly embarrassing, which is the system working.

## 2026-07-20 — The keyboard eats a keyboard (PR #1)

- Reversed ADR-0001 the same day it was accepted, setting a repo speed record
  we would prefer nobody breaks.
- PersonaSpeak will now fork an entire open-source keyboard rather than
  politely coexist with yours. The switching model — flip to us, flip back —
  was judged unshippable, so we removed the switching, the second keyboard,
  and the concept of leaving.
- Added 13 UX mockups, including one rejected design and one superseded
  design, retained because failure is evidence and storage is cheap.
- Four persona emoji circles were rendered illegibly twice, independently, and
  have been replaced by one chip with a name on it. The circles fought
  bravely.
- Mood is now an orthogonal prompt modifier. The persona schema remains at
  version 1 and did not have to be involved at all, which it appreciates.
- The result card now floats over the chat and never over the keys. The
  typing surface does not move. This is a rule, not a preference.

## 2026-07-20 — The repo assembles itself

- PersonaSpeak v0: four personas, a Python CLI, and a Claude skill that
  rewrites your messages with more dignity than you sent them with.
- Reorganized into a monorepo. The CLI moved to `desktop/`; the personas
  stayed at the root, where the talent belongs.
- The repo learned to talk: VOICE.md, README, AGENTS.md, CONTRIBUTING,
  ROADMAP, GTM. All prose now passes a "would Valve ship this" check.
- Persona schema formalized, with a validator and CI to enforce it, because a
  schema without a validator is a rumor.
- PersonaBoard walking skeleton: four Android modules that build, with golden
  tests proving the Kotlin prompts match the Python reference byte for byte.
- Fixed a bug where the IME window had no lifecycle owner, and Compose,
  reasonably, refused to compose under those conditions.
- Renamed the package to `biz.pixelperfectstudios.personaspeak`, a domain we
  own, unlike the previous one, which we merely admired.
