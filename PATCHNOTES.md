# Patch Notes

Every merged PR gets an entry here, written the way patch notes should be:
every line true, every line deadpan, delivered in the register of
[VOICE.md](VOICE.md). Strip the jokes and you can still reconstruct the
changelog; that's the contract. The PR author writes the line, in the PR,
while the context is hot.

Newest first, like all respectable patch notes.

---

## 2026-08-10 — The inspector stops hallucinating and starts taking pictures

- The M2 device-qualification harness gained a real evidence-capture
  path: seven screenshots and one video via `screencap`/`screenrecord`,
  validated structurally (PNG CRC + IHDR/IEND; MP4 now requires both
  `ftyp` and `moov`/`mdat`, not an empty `ftyp`-only shell) before the
  journey record is sealed. The journey verifies PersonaSpeak keyboard
  states — Loading, Review, Apply — by reading the panel hierarchy, not
  by substituting a Settings search-field demo. XML parse errors stop
  the journey; the search-field selector matches the exact pinned
  resource-id. Every `keyevent` return code is checked. `verify_release`
  discriminates `ConnectionRefused` (released) from timeout and other
  socket errors (inconclusive, fail-closed). `capture_prior_state`
  returns `None` on any unparseable property instead of defaulting to
  pinned values. All tool invocations use resolved paths from preflight,
  never bare PATH-resolved names. `SIGINT`/`SIGTERM` converge on
  restoration and a decodable failure receipt. The CLI verifies
  `--apk-sha256` against the on-disk APK before mutation.
  `CaptureContext` lives at the orchestration boundary; monkey-patching
  is gone. The fake adb simulates keyboard state transitions; forbidden
  `apksigner`/`keytool` canaries guard the ledger. 169 tests, no device
  contacted (#59).

## 2026-08-10 — The Loading row learns the word "Cancel"

- While PersonaSpeak is thinking, the row now shows a Cancel button next
  to the spinner. This is not a second cancellation path; it is the
  existing one (`onDismiss` → `RewritePanelViewModel.dismiss()`, which
  drops the in-flight request) finally given somewhere to be tapped.
  Idle, Message, and Review are unchanged — Review keeps its own
  Dismiss. The control clears the 48dp touch minimum, carries the
  `personaspeak_cancel` test tag, and exists only in Loading. Closes
  the M2 contradiction where the qualification journey required a
  Loading/cancel control the row did not render (#60).

## 2026-08-06 — The inspector learns to read exit codes without reading minds

- `RemoteResult.remote_rc` is no longer set to None by default and left
  for someone else to figure out. `AdbRemoteStatusReader` populates it
  from the adb shell_v2 transport result, using a structural discriminator
  (stderr emptiness at rc=1) rather than pattern-matching adb's English
  error text. The probe observed that transport failures always write to
  stderr and always exit 1; remote exit codes pass through at every
  other value. Where the two signals collide — rc=1 with non-empty
  stderr — the adapter declines to guess and returns None, letting
  `_rc_of` fail closed to 1. A real remote exit code lost to caution
  is an acceptable trade; a transport failure reported as remote success
  is not.
- `run_remote` no longer defaults every caller to `UnavailableReader`.
  The concrete reader is the default; the old unavailable behavior is
  still available for callers who want to opt out.
- `_rc_of` for `RemoteResult` retains its transport backstop (the B-1
  correction from the previous PR). The adapter populates `remote_rc`;
  the dispatch remains correct regardless of which reader produced
  the record. The redundancy is the guarantee.
- A coverage-matrix test parses the orchestrator's AST and asserts the
  set of `_rc_of` / `_timed_out` call sites matches an enumerated list.
  It was demonstrated failing against a stray call before it was
  trusted to guard against one. 138 tests, no device contacted.

## 2026-08-06 — The inspector rebuilds, in a language that doesn't eat its own status

- Five Python modules (898 lines) implement the complete device-free
  qualification machinery: typed records with deterministic JSON codec, local
  process runner with remote-result interface, phase-ordered orchestrator with
  prior-state capture and restoration, evidence validation (privacy scan, PNG
  and MP4 structural checks, manifest digests), and a capture/finalize/approve
  CLI. 96 tests exercise every record round-trip, failure path, and adversarial
  fake-tool boundary. No device was contacted; no keyboard code was changed.

## 2026-08-06 — The inspector receives an exit interview

- The three failed Milestone 2 capture attempts now have one retrospective and
  one replacement design. Bash retires from device orchestration; a bounded
  Python instrument, adversarial fake-tool run, snapshot-backed emulator, typed
  receipts, and external evidence archive take its place. The keyboard remains
  unchanged. It has already spent quite enough time watching its clipboard
  learn about itself.

## 2026-08-05 — One keyboard, one APK, one row of its own

- PersonaSpeak now rides in a dedicated row above AnySoftKeyboard's
  suggestions and keys, instead of borrowing a seat on the candidate strip.
  Review scrolls inside a frozen `min(320dp, 40%)` bound sampled before the
  row expands — sampling afterwards feeds the row's growth back into its own
  limit, which ends with the keyboard covered by the thing meant to sit above
  it. Every control clears 48dp, including Dismiss, which until now had no
  test tag and therefore no opinion about its own size.
- The two rollback modules are gone. `android/app` and `android/keyboard-stub`
  existed so we could retreat; the retreat is over, and ASK's `:ime:app` is
  now the only project that produces an APK. The tree used to yield three of
  them, one of which was upstream copying the artifact somewhere convenient.
  It no longer does that here, and still does it everywhere else.
- New gates: exactly one APK at exactly one path from exactly one application
  project, and one aggregate Milestone 2 verifier that CI now calls instead of
  keeping its own parallel list of steps to drift out of sync with. Each
  verifier has a fixture suite that runs before the verifier is trusted,
  because a green gate built on an unchecked tool is worse than no gate.
- What this entry does not claim: none of the above was verified on a real
  device. The cutover is proven by the host gate and the unit suites. Mutation
  against a real `InputConnection`, the height cap on a real screen, and
  restoration after real mutation are pending, and land in a follow-up PR under
  issue #47 along with the orchestrator that runs them. The earlier device
  receipt was not accepted and is no longer tracked here; a receipt nobody
  signed is not evidence, it is filing.

## 2026-08-05 — The repo files its license

- Root `/LICENSE` now carries the official Apache-2.0 text. The app code has
  been Apache-2.0 since ADR-0003; the paperwork had simply not reached the
  front door. AnySoftKeyboard's vendored license stays at
  `android/keyboard/LICENSE`, and persona content remains CC-BY — a license
  file is not a personality transplant.

## 2026-07-29 — Milestone two acquires written instructions

- The accepted ASK cutover now has an executable completion plan: preserve the
  reviewed history, add the dedicated PersonaSpeak row in serial slices, delete
  both rollback applications atomically, prove exactly one APK, restore the
  qualification device, and merge only exact-head evidence. The keyboard is
  unchanged; the paperwork has become unusually competent.

## 2026-07-29 — The keyboard receives a route to becoming installable

- The production route (commit `f773aff`) now carries PersonaSpeak from the atomic ASK cutover
  through real keyboard flow, encrypted provider state, onboarding, visual and
  privacy qualification, and one reproducible signed APK. Eight milestones now
  have explicit evidence, restoration, review, and stop gates; store publication
  remains outside the velvet rope until it acquires a design of its own.

## 2026-07-24 — Suggestions keep their chair

- PersonaSpeak now has an architectural seating plan: its own measured row
  above AnySoftKeyboard's suggestion row and keys. The shared-strip prototypes
  were cheaper only if we ignored the suggestions, the keys, or occasionally
  both. ADR-0007 declines the discount.

## 2026-07-22 — The keyboard and the product become the same application

- The first-party UI boundary is now a contract, not a vibe. CI builds the
  validated persona catalog/repository, the pure editor contract, and the
  guarded two-stage rewrite coordinator, then fails if anything first-party
  reaches for an Android import it shouldn't or an AnySoftKeyboard symbol it
  hasn't earned. Exactly three artifacts leave the current graph — the
  temporary app APK and the two first-party AARs — and the vendored ASK
  snapshot stays inert, outside Gradle, producing nothing.
- The UI-foundation plan has caught up with `main`: malformed provider
  successes, all six validation fixtures, and the rejected panel now have
  deterministic gates instead of relying on optimism.
- The Stitch exports have acquired adult supervision: every requested screen
  now has an explicit disposition, with truthful privacy copy and your words
  still requiring one deliberate `Use this` before they move.
- The post-ingestion roadmap now has an address: one tested UI boundary, one
  atomic ASK cutover, then the complete Stitch journey. The accepted plan also
  routes persona sources through a repository seam, so a future marketplace can
  add plumbing without teaching the keyboard where the parcels came from.
- AnySoftKeyboard `1.13-r1` now occupies `android/keyboard/` as a pinned,
  inert snapshot with an empty upstream-rent ledger. The rejected panel moved
  unchanged to `:keyboard-stub`; it remains scaffolding, not a comeback tour.
- The old ADR-0001 panel has been promoted from “demo keyboard” to its accurate
  title: disposable, non-typing build scaffolding. ASK ingestion may move it
  unchanged; no journey may endorse it, and ASK integration deletes it.
- The Android build now uses ASK's proven Gradle 9.2.1, AGP 8.13.2, Kotlin
  2.3.10, and JDK 21 baseline. CI checks every current module, because a
  convergence experiment is more useful after it stops being temporary.
- ADR-0006 settles the part Gradle had been enjoying as an interpretive dance:
  PersonaSpeak ships one ASK-based APK from one unified build. ASK owns the real
  keyboard; first-party modules own the manners; a thin editor adapter validates
  immediately before asking the host to replace text, without pretending
  Android supplied a cross-process transaction or a second UI.
- The discarded switcher model stays discarded. No flip to a keyless panel, no
  flip back, and no local draft field waiting patiently for input an IME window
  cannot receive.
- Stitch exports become acceptance targets backed by screenshots and real
  journeys. The portraits remain detained at customs until their redistribution
  papers exist.

## 2026-07-21 — The paperwork catches up again

- ADR-0004 and ADR-0005 flip from Proposed to Accepted. Both decisions were
  already load-bearing — #18 is vendoring against ADR-0004's ingestion
  mechanism and #17 is the audit ADR-0005 mandated — the status line was just
  the last one to find out.

## 2026-07-21 — The FlorisBoard spike files its exit interview

- Ran the full capture-to-commit loop on a real IME (FlorisBoard, throwaway
  per ADR-0003/0004) and wrote down the receipts: the in-keyboard flow (strip,
  pickers, loading, result, replace) works end to end, onboarding and settings
  don't exist yet, and the stale-field race guard is still a spec, not code.
  The recommendation lines up with ADR-0004 exactly — move the graft to
  AnySoftKeyboard next.

## 2026-07-21 — Auditing the keyboard we're about to move in with

- Static privacy inventory of AnySoftKeyboard `1.13-r1` (commit `8c1db51`) is
  in `docs/privacy/anysoftkeyboard-1.13-r1-inventory.md`. Three kinds, kept
  separate: on-device local state, anything that leaves the device, and
  on-device disclosure surfaces. Every finding cites `path:line` against the
  pinned ASK tree.
- What *provisionally* holds against the README's "nothing is logged" and "not
  used to improve our services" clauses — clear from static reading, not yet
  proven: the release build's `NullLogProvider` gates every `Logger.*` call and
  the in-memory ring buffer, and no analytics/crash-upload SDK appears in source
  or Gradle config (no Firebase, Crashlytics, Fabric, Flurry, Sentry, Amplitude),
  the crash path being a user-tapped email. A source grep cannot see
  shaded/transitive deps, native code, or runtime egress, so neither clause is
  signed off until the release APK and per-UID network capture confirm it — a
  false all-clear is the one thing a privacy claim cannot afford.
- What does not survive as-shipped: "nothing is stored" — a predictive
  keyboard keeps a learned-words DB, an auto-learn DB, and per-locale
  next-word files, all default-on, all user-clearable. The honest restatement
  is ADR-0005's "stays on your phone, user-clearable," not "does not exist."
- The one fact the privacy copy did not account for: Android Auto Backup is
  default-on (`allowBackup="true"`, no `fullBackupContent` or
  `dataExtractionRules`), so the local state above is eligible for the device's
  configured backup transport (commonly Google Drive). Not a server we run — but
  also not "nothing leaves your phone." The inventory lists this as the headline
  neutralization item.
- Static analysis is necessary but not sufficient. On-device capture (per-UID
  packet capture, `bmgr`, resolved dependency graph, decompiled release APK) is
  the gating step before the privacy copy unfreezes; the inventory's last
  section is that checklist.

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
