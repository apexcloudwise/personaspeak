# Patch Notes

Every merged PR gets an entry here, written the way patch notes should be:
every line true, every line deadpan, delivered in the register of
[VOICE.md](VOICE.md). Strip the jokes and you can still reconstruct the
changelog; that's the contract. The PR author writes the line, in the PR,
while the context is hot.

Newest first, like all respectable patch notes.

---

## 2026-07-20 — The keyboard eats a keyboard (PR #1)

- Reversed ADR-0001 the same day it was accepted, setting a repo speed record
  we would prefer nobody breaks.
- PersonaSpeak will now fork an entire open-source keyboard rather than
  politely coexist with yours. The thin IME could not be typed into, which
  for a keyboard is what doctors call "a finding."
- Fixed the keyboard-switching UX by removing the switching, the second
  keyboard, and the concept of leaving.
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
