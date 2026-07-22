# ROADMAP.md

Where this is going, in the order it's going there. Each phase ends with
something you can actually hold (metaphorically — it's software).

## Phase 0 — Foundation ✅ (in progress)

The repo becomes public-ready: git history, monorepo layout, docs with a
pulse, CI that validates personas and smoke-tests the CLI, ADRs recording our
decisions — including the one we later reversed, which is the useful kind.

- [x] Git init, v0 commit, monorepo restructure
- [x] VOICE.md, README.md, AGENTS.md, CONTRIBUTING.md, ROADMAP.md, GTM.md
- [x] docs/persona-schema.md + schema validator in CI
- [x] ADR-0001 (thin IME over fork), ADR-0002 (provider registry)
- [x] Pick licenses — **decided in
      [ADR-0003](docs/adr/0003-fork-anysoftkeyboard-apache.md):** the app is
      **Apache-2.0**, following the AnySoftKeyboard base; personas stay CC-BY as
      planned. GPL-3.0 (HeliBoard) was ruled out precisely because it would have
      relicensed the whole app.

## Phase 1 — PersonaBoard MVP 🚧

**Course correction (2026-07-20):** this phase originally built a *thin*
keyboard you switched to and back from. It doesn't any more. An IME window
never gets real input focus, so the keyless panel could not be typed into and
its empty state was a dead end — so PersonaSpeak now **forks a full
open-source keyboard** and lives there permanently. ADR-0001 is superseded.

A full Android keyboard — everything you expect from a daily driver — with a
persona strip above the keys that rewrites what you've typed.

- [x] Gradle scaffold: `core-personas`, `core-providers`, temporary
      `keyboard-stub`, and temporary `app`
- [x] ~~Walking skeleton: static panel IME~~ — superseded by the fork
- [x] core-personas: YAML parsing + prompt builder + golden tests
      (byte-identical prompts to `desktop/personaspeak.py` — verified)
- [x] UX design: strip, persona picker, result card, onboarding, settings
      (`docs/superpowers/specs/2026-07-20-keyboard-ux-design.md`)
- [x] **Fork base decided** — **AnySoftKeyboard**, via the graft spike
      ([ADR-0003](docs/adr/0003-fork-anysoftkeyboard-apache.md)). Decides the
      licence; see Phase 0.
- [x] ADR superseding ADR-0001, with the spike evidence attached — ADR-0003.
- [x] **Ingest ASK** (ADR-0004): pin an upstream revision, reproducible
      unmodified build, package/license baseline, documented update procedure.
      Landed in PR #37; the snapshot is deliberately inert until the unified
      build cutover.
- [x] **Privacy audit** of what an ASK fork stores/sends by default (learned
      words, dictionaries, clipboard, backups, diagnostics) — its own ADR,
      before public privacy copy can ship. The static inventory is complete;
      release-APK and on-device verification remain release gates.
- [ ] Create `:personaspeak-ui` with source-neutral persona access, the accepted
      `EditorPort` contract, fake-port tests, and no ASK implementation imports.
- [ ] **Unify the Android build** (ADR-0006): make ASK's `:ime:app` the only APK,
      delete the temporary `app` and `keyboard-stub`, and prove real ASK typing,
      guarded host-editor replacement, and same-package settings on an emulator.
- [ ] Persona strip grafted onto the chosen base: persona chip + mood chip +
      transform, reading the draft and replacing it in place
- [ ] core-providers: `CompletionProvider` interface; Gemini free tier
      (default), Anthropic, OpenAI, OpenRouter via BYOK; keys in Keystore
- [ ] app: onboarding (enable → set default → pick provider → try it),
      persona browser, settings
- [ ] CI: assembleDebug + unit tests per PR; APK artifact on tags

**Exit demo:** in WhatsApp, type "running late" on your normal keyboard —
which is now ours — tap 🎩, send something Wodehouse would sign off on. No
switching, because there's nothing to switch to.

**Release gate:** the keyboard must not crash. A thin IME that fell over meant
a shrug and a switch back to Gboard; a daily driver that falls over means the
phone can't type. That is the rent the fork charges.

## Phase 2 — Suggested replies 🔮

The wow feature. Opt in to notification access and the keyboard drafts
replies to the message you just received, before you type anything.

- [ ] Opt-in NotificationListenerService; last message per conversation,
      in-memory only, forgotten on reply
- [ ] Panel shows "Replying to: …" + 3 suggestions per active persona/tone
- [ ] Regenerate/cycle button
- [ ] Privacy page (in-app + README): what's read, where it goes, how to
      verify — with the code as the receipts

## Phase 3 — Depth & reach 🔭

- [ ] On-device provider: Gemini Nano via AICore where supported; evaluate
      llama.cpp fallback for everyone else
- [ ] Hybrid routing: local model for tone tweaks, cloud for full persona
- [ ] Community persona packs (PRs; CI schema check + human taste check)
- [ ] Marketplace/distribution design for third-party persona packages. The
      Phase 1 UI consumes a source-neutral repository so this can be additive;
      packaging, identity, provenance, signatures, moderation, downloads, and
      updates require their own owner-approved ADR before implementation.
- [ ] F-Droid submission, then Play Store
- [ ] Stretch: Wear OS quick replies — reply as Dr. Schultz from your wrist,
      like a Bond villain
- [x] ~~Stretch-stretch: the HeliBoard fork, if PersonaBoard outgrows "thin"~~
      — pulled forward to Phase 1. It outgrew "thin" before it shipped.

## Non-goals (so agents stop suggesting them)

- Ads. Never. See README.
- A chat app of our own. The world has enough.
- iOS (until Android is boringly stable).
- Personas that impersonate rather than pay homage. We're a costume shop,
  not a forgery ring.
