# ROADMAP.md

Where this is going, in the order it's going there. Each phase ends with
something you can actually hold (metaphorically — it's software).

## Phase 0 — Foundation ✅ (in progress)

The repo becomes public-ready: git history, monorepo layout, docs with a
pulse, CI that validates personas and smoke-tests the CLI, ADRs recording why
we built a thin keyboard instead of forking a 100k-line one.

- [x] Git init, v0 commit, monorepo restructure
- [x] VOICE.md, README.md, AGENTS.md, CONTRIBUTING.md, ROADMAP.md, GTM.md
- [ ] docs/persona-schema.md + schema validator in CI
- [ ] ADR-0001 (thin IME over fork), ADR-0002 (provider registry)
- [ ] Pick licenses (leaning Apache-2.0 code / CC-BY personas)

## Phase 1 — PersonaBoard MVP 🚧

A thin Android keyboard (Kotlin + Compose, min SDK 26) that does one thing:
turn your words into a persona's words, inside any chat app.

- [ ] Gradle scaffold: `core-personas`, `core-providers`, `keyboard`, `app`
- [ ] Walking skeleton: static panel IME, hardcoded persona, fake provider
- [ ] core-personas: YAML parsing + prompt builder + golden tests
      (must produce byte-identical prompts to `desktop/personaspeak.py`)
- [ ] core-providers: `CompletionProvider` interface; Gemini free tier
      (default), Anthropic, OpenAI, OpenRouter via BYOK; keys in Keystore
- [ ] Keyboard panel: persona chips, tone chips (witty/funny/polite),
      transform current draft (`getExtractedText`), commit reply
      (`commitText`), one-tap return to previous keyboard
- [ ] app: onboarding (enable keyboard → pick provider → try it), persona
      browser, settings
- [ ] CI: assembleDebug + unit tests per PR; APK artifact on tags

**Exit demo:** in WhatsApp, switch to PersonaBoard, type "running late",
tap 🎩, send something Wodehouse would sign off on, switch back to Gboard.
Under 15 seconds.

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
- [ ] F-Droid submission, then Play Store
- [ ] Stretch: Wear OS quick replies — reply as Dr. Schultz from your wrist,
      like a Bond villain
- [ ] Stretch-stretch: the HeliBoard fork, if PersonaBoard outgrows "thin"

## Non-goals (so agents stop suggesting them)

- Ads. Never. See README.
- A chat app of our own. The world has enough.
- iOS (until Android is boringly stable).
- Personas that impersonate rather than pay homage. We're a costume shop,
  not a forgery ring.
