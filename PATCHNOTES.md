# Patch Notes

Every merged PR gets an entry here, written the way patch notes should be:
every line true, every line deadpan, delivered in the register of
[VOICE.md](VOICE.md). Strip the jokes and you can still reconstruct the
changelog; that's the contract. The PR author writes the line, in the PR,
while the context is hot.

Newest first, like all respectable patch notes.

---

## 2026-07-21 — A tape measure for the inherited engines (PR #9)

- Built the harness for the typing bake-off issue #5 asked for — corpus,
  adb replay, Soukoreff-MacKenzie metrics, empty results template — and
  stopped there. No numbers. A measurement nobody ran is not a measurement,
  and the harness is honest about that in every doc it ships.
- The metrics calculator is pinned by hand-computed unit tests, because
  "tests are the spec" applies even to test tooling. The adb replay script
  is pinned by honesty: it says plainly which signals it can't reach
  (autocorrect firing, prediction acceptance, gestures) and routes those to
  a scripted human session rather than inventing telemetry.
- Did not pick the base. Did not even measure the base. Handed the human
  staff a stopwatch and a clipboard; the stopwatch and clipboard are
  checked in.
- Then actually pointed the clipboard at an emulator. Installed real stock
  F-Droid builds of all three keyboards (not the graft-spike debug builds
  already sitting on the AVD — those would have measured our own graft, not
  the base) and ran the harness for real. Found the clipboard's pen didn't
  have ink: `read_field_text()` was dumping UI state to `/dev/tty`, which
  doesn't exist for a non-interactive `adb shell`, so every captured
  "transcribed" phrase had been silently blank. Fixed. HeliBoard's pinned
  IME id was also stale against the current stock build; fixed that too.
  Still no IME-quality numbers — `inject` mode's ~684 WPM is input-injection
  speed, not typing speed — but the pipe from device to metrics table now
  demonstrably carries water.

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
