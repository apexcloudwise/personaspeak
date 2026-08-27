# PersonaSpeak

**You type "can't come to the party". Jeeves regretfully declines on your
behalf, with honor. That's it. That's the app.**

PersonaSpeak turns any text into the voice of a character — the impeccable
valet, the verbose civil servant, the alarmingly polite bounty hunter — and
**PersonaBoard** (in development) puts that power where you actually text
people: an Android keyboard for WhatsApp, Telegram, and anywhere else words
go to be misunderstood.

## What exists today

| Thing | Status | What it does |
|---|---|---|
| `personas/*.yaml` | ✅ Works | The persona library. One YAML per character: speech patterns, vocabulary, sample lines. This is the single source of truth for everything below. |
| `desktop/personaspeak.py` | ✅ Works | CLI. `personaspeak.py --as jeeves "grab me a coffee"` → butler-grade coffee request. Brings your own API key. |
| `.claude/skills/personaspeak/` | ✅ Works | Claude Code skill — same trick, in your editor session, on your subscription, no API bill. |
| `android/` | 🚧 Release candidate | The keyboard. A full daily driver — autocorrect, glide, layouts, the lot — forked from an established open-source keyboard, with a persona strip above the keys. Feature work and release checks landed; the public APK waits on official signing material. |

## The cast

- 🎩 **Jeeves** — "I endeavour to give satisfaction, sir."
- 🏛️ **Sir Humphrey Appleby** — will neither confirm nor deny, at length.
- 🤠 **Dr. King Schultz** — extravagantly courteous, even about the bad news.
- 🎬 **Amitabh Bachchan** — every reply delivered to a full house.

Add your own: drop a YAML in `personas/` (schema in
[docs/persona-schema.md](docs/persona-schema.md)). If it makes us laugh in
review, it ships.

## Quick start (desktop CLI)

```bash
cd desktop
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...
.venv/bin/python personaspeak.py --as sir-humphrey "no, we're not doing that"
```

## The plan

The full roadmap lives in [ROADMAP.md](ROADMAP.md). The short version:

1. **Phase 0** *(you are here)* — repo goes public, docs get funny, CI gets serious.
2. **Phase 1** — PersonaBoard MVP: feature-complete and release-ready in code.
   The public `v0.1.0` APK is deferred pending official release signing material;
   see [handoff.md](handoff.md).
3. **Phase 2** — Suggested replies: opt in, and the keyboard reads the message
   you're replying to (from the notification, on-device, forgotten immediately)
   and drafts three responses before you've typed a word.
4. **Phase 3** — On-device models, community persona packs, F-Droid.

## Privacy, briefly

The Android keyboard is a release candidate, not a published APK. The accepted design encrypts
provider credentials with a key held by Android Keystore and sends a draft only
when the user explicitly requests a rewrite, to the provider they selected.
ASK also keeps normal keyboard data such as learned words on the device; that
data must remain user-clearable and excluded from backup and device transfer
before release. Drafts, prompts, and provider results are not product history.
On-device and static audit evidence is recorded, but public release claims remain
gated on an officially signed release APK. The current release deferral and
resume steps are in [handoff.md](handoff.md); privacy design remains in
[ADR-0005](docs/adr/0005-privacy-posture-fork-audit.md).

## Maintained by robots (supervised)

This repo is a showcase of AI-agent-driven engineering: agents write the
features, the tests, and yes, these very docs — a human reviews and merges.
House rules for agents are in [AGENTS.md](AGENTS.md). Style rules for prose
are in [VOICE.md](VOICE.md). Both are enforced with the seriousness of a
butler inspecting cutlery.

## License & money

[ADR-0003](docs/adr/0003-fork-anysoftkeyboard-apache.md) selected Apache-2.0
for the app and ASK fork; persona content remains CC-BY as recorded in the
roadmap. The full Apache-2.0 text is at [LICENSE](LICENSE);
AnySoftKeyboard's vendored license and upstream provenance are documented in
[android/keyboard/LICENSE](android/keyboard/LICENSE) and
[android/keyboard/UPSTREAM.md](android/keyboard/UPSTREAM.md). If this project
ever earns a coin it'll be through donations and an
optional hosted-key convenience tier — never ads. A keyboard that shows you ads
is a keyboard that reads your texts for a living, and we are not that kind of
establishment.
