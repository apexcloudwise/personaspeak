# HANDOFF — M4 throwaway prototype: live providers + model picker

**Branch:** `m4-proto` (scratch worktree off `646fdbf`, the M4 slice-2 plan commit)
**Status:** working prototype — live OpenRouter rewrites and model browser verified
**Owner of this branch:** opencode (ox-alpha session), 2026-08-26

## What this adds (Phase 1 completion, tests waived by owner)

- **Live provider adapters** in `core-providers` (pure Kotlin, zero new deps):
  - `HttpChatCompletionsProvider` — any OpenAI-compatible `/chat/completions`
    endpoint (OpenRouter, OpenAI, Groq, self-hosted). Hand-rolled `MiniJson`
    parser, 30s timeouts, user-presentable failures, keys never logged.
  - `AnthropicProvider` — Messages API (`x-api-key`, `anthropic-version`).
  - `ProviderCatalog` — provider defs (openrouter / openai-compat / anthropic)
    + factory. **OpenRouter default model: `nvidia/nemotron-3-super-120b-a12b:free`**
    (verified live; the original `meta-llama/llama-3.3-70b-instruct:free` went
    paid-only on OpenRouter and 404s).
  - `OpenRouterModels.fetch()` — public `/models` catalog for the picker
    (free-first sort, `pricing.prompt == "0"` ⇒ FREE).
- **`ResolvingProvider` + `PersonaSpeakBrain`** (ime): resolves the configured
  brain from the store on first use, falls back to `FakeProvider` when
  unconfigured, `invalidate()` on each `onStartInputView` so saved keys apply
  without a process restart. Single production injection point was
  `PersonaSpeakComposition` (was `private val provider = FakeProvider()`).
- **Settings UI**: new `ProviderSetup` destination ("The Brain"): provider
  radio list, password API-key field, model field, OpenRouter-only
  "Browse models…" searchable dialog, Save/Remove wired to the slice-1
  Keystore store. Home screen: placeholder rows replaced by one "AI Brain"
  row + a "Get started" onboarding card (shown while Unconfigured; step 1
  deep-links to system IME settings). Model persists as non-secret metadata
  (`ProviderConfig.model`, `ProviderMeta.model`, DataStore key `"model"`;
  `StoreOutcome.Configured.model`).

## Root causes found while debugging (both fixed here)

1. **`:ime:app` manifest had no `INTERNET` permission.** ASK upstream never
   needed it in our closure; every provider call died with
   `SecurityException (missing INTERNET permission?)`, surfaced as
   "service unavailable". Fix: one `uses-permission` line + comment.
2. **Stale OpenRouter default model** (see above). Saved configs that pin the
   old slug explicitly must re-pick a model in The Brain.

## Rent ledger (upstream/ASK-tree changes)

- `keyboard/ime/app/build.gradle`: `debugImplementation` → `implementation`
  for `:personaspeak-data` (1 line).
- `keyboard/ime/app/src/main/AndroidManifest.xml`: +1 `uses-permission` line.
- No other ASK-tree source files touched.

## Verification evidence

- `./gradlew :ime:app:assembleDebug` green (from `android/`, JDK 21,
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools).
- `:core-providers:test` green, incl. `MiniJsonRealPayloadTest` parsing real
  captured payloads (`core-providers/src/test/resources/or_models.json`,
  `or_chat.json` — public API payloads, no secrets).
- Live API verified via curl with an owner-supplied key: nemotron default
  200 OK; Browse models works on emulator after the INTERNET fix.
- APK installed and working on owner's phone (Motorola Edge 50 Pro, Wi-Fi adb).

## Known gaps / cleanup before this could become a real M4 PR

- **Tests waived** by owner for this throwaway (existing suites still pass;
  new code has compile+manual coverage only).
- **Diagnostic logging to remove or formalize**: `PsBrain` tags in
  `ResolvingProvider.rewrite` and `ProviderSetupScreen` (models fetch failure).
  They log exception class/message only — no keys, no draft text — but the
  no-secret-log rules should be re-audited before merge. The
  `PARSE_FAIL ... | head=` instrumentation in `OpenRouterModels.fetch` should
  be reverted to the clean message.
- `needsBaseUrl`/custom base URL is displayed but **not persisted** (slice-1
  data classification forbids URLs; a sanctioned schema extension is needed).
- Onboarding card is minimal (no dedicated flow/screens).
- `ProviderSetupScreen` test tags exist; no new UI tests.
- Owner's OpenRouter key was pasted into a chat session during debugging —
  **rotate it**.

## How to run

```
cd android && ./gradlew :ime:app:assembleDebug
adb install -r keyboard/ime/app/build/outputs/apk/debug/app-debug.apk
```
Enable PersonaBoard as keyboard → 🎩 row → wrench → The Brain → pick
OpenRouter → paste key → (Browse models… to pick) → Save → type anywhere,
tap Rewrite.
