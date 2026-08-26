# Milestone 5 Plan — Stitch Onboarding & Settings, Provider Opt-In, and OpenRouter Adapter

**Issue:** #103  
**Parent Milestone:** #38 (Milestone 5)  
**Authority:** #102 / PR #102 (`5a35853`), ADR-0009 (`docs/adr/0009-pluggable-multi-provider-and-openrouter.md`), owner speed authorization (2026-08-27)  
**Baseline Commit:** `5a35853` (head of `main`)  
**Owner:** @reicodes-pixelperfect  
**Reviewers:** Seraph (@seraph-pixelperfect), Sigrid (@sigrid-pixelperfect), Cassie (@cassievale-pixelperfect)  

---

## 0. Executive Summary & Strategy

Milestone 5 delivers the user onboarding experience, first-party settings configuration ("The Brain"), and user-driven provider opt-in, establishing OpenRouter as the sanctioned secondary remote provider alongside Anthropic and the offline `FakeProvider` baseline.

Following the speed authorization granted by the product owner on 2026-08-27, Milestone 5 is structured into **two accelerated work slices**, with the plan and ADR riding directly with the implementation:

```
+----------------------------------------------------------------------------------------------------+
|                                    MILESTONE 5 EXECUTION SLICES                                    |
+----------------------------------------------------------------------------------------------------+
|  SLICE A (Current Slice):                                                                          |
|  - ADR-0009: Pluggable Multi-Provider Architecture & OpenRouter Evaluation                         |
|  - Milestone Plan (this document)                                                                  |
|  - OpenRouterAdapter & OpenRouterModels in :personaspeak-providers (Pure Kotlin, mock-only)        |
|  - Zero-dependency MiniJson parser contract-pinned against real payloads                           |
|  - Comprehensive unit contract tests (200 OK, 401/403 AuthFailure, 429/5xx, timeouts, zeroing)     |
|  - Manifest INTERNET permission + UPSTREAM-MODIFIED.md rent ledger entry                           |
+----------------------------------------------------------------------------------------------------+
|  SLICE B (Next Slice):                                                                             |
|  - Settings UI: "The Brain" configuration screen (provider radio picker, key field, model field)   |
|  - OpenRouter Model Browser: searchable live/catalog model dialog with free-first badges           |
|  - Onboarding Card & On-Ramp guidance (unconfigured setup guide + deep links)                      |
|  - Provider Opt-In Composition wiring (ResolvingProvider in PersonaSpeakComposition)               |
|  - UI tests & integration validation                                                               |
+----------------------------------------------------------------------------------------------------+
```

---

## 1. Architectural Invariants & Non-Negotiables

1. **`m4-proto` is a Port-Source, Not a Merge-Base:**
   - The exploratory prototype branch `m4-proto` (0316dbd) is used solely as a verified behavioral reference.
   - Code is ported onto main's slice-2 interfaces (`ProviderAdapter`, `HttpTransport`) and tested with fresh, rigorous unit tests.
2. **Mock-Only for Merged Code:**
   - No real credentials, API tokens, or live network egress in source code, automated tests, or repository fixtures.
   - Live BYOK testing on physical hardware is performed outside the repository boundary.
3. **Default-Disabled Invariant:**
   - `FakeProvider` remains the active default provider upon app launch.
   - Remote provider adapters execute only after deliberate user opt-in in Settings.
4. **Pure Kotlin Seams:**
   - `:personaspeak-providers` contains zero Android platform imports (`android.*`).
   - Transport is abstracted via `HttpTransport`, allowing hermetic, offline test runs.
5. **Privacy & Zero Secret Leakage:**
   - Raw credentials (`SecretBytes`) are zeroed in memory immediately upon request completion (`finally { secret.value.fill(0) }`).
   - No drafts, user inputs, rewrite prompts, or API keys are logged, persisted in plaintext, or transmitted outside the designated endpoint.
6. **Independence from Milestone 4 Gates:**
   - Milestone 4 gates (#96/#89 — Mode A ART, Mode B live egress, API-27 backup exclusion) remain parked on human credential provisioning. Milestone 5 does not disturb or substitute for these gates.

---

## 2. Slice A: Provider Adapter & Contract Foundation

### 2.1 Component Specifications

| Component | Target Location | Responsibilities |
|---|---|---|
| `OpenRouterAdapter` | `personaspeak-providers/.../providers/OpenRouterAdapter.kt` | Implements `ProviderAdapter` for OpenRouter's OpenAI-compatible completions API (`https://openrouter.ai/api/v1/chat/completions`). |
| `OpenRouterModels` | `personaspeak-providers/.../providers/OpenRouterModels.kt` | Parses and filters OpenRouter's public `/models` catalog, identifying free models (`pricing.prompt == "0"`). |
| `MiniJson` | `personaspeak-providers/.../providers/MiniJson.kt` | Dependency-free recursive-descent JSON parser handling payloads without reflection. |
| `OpenRouterAdapterTest` | `personaspeak-providers/.../providers/OpenRouterAdapterTest.kt` | Contract tests against synthetic `HttpTransport` doubles asserting all status codes and extraction paths. |
| `OpenRouterModelsTest` | `personaspeak-providers/.../providers/OpenRouterModelsTest.kt` | Contract tests asserting free-first sorting and error resilience. |

### 2.2 Endpoint & Header Specifications

- **Completions Endpoint:** `https://openrouter.ai/api/v1/chat/completions` (pinned in `DefaultOpenRouterHttpTransport`).
- **Default Model:** `nvidia/nemotron-3-super-120b-a12b:free` (replaces deprecated `meta-llama/llama-3.3-70b-instruct:free`).
- **Headers:**
  - `Authorization: Bearer <API_KEY>`
  - `HTTP-Referer: https://pixelperfectstudios.biz`
  - `X-Title: PersonaSpeak`
  - `Content-Type: application/json; charset=utf-8`
- **Request Body:**
  ```json
  {
    "model": "nvidia/nemotron-3-super-120b-a12b:free",
    "messages": [
      {"role": "system", "content": "<SYSTEM_PROMPT>"},
      {"role": "user", "content": "<USER_TEXT>"}
    ],
    "temperature": 0.8
  }
  ```

### 2.3 Upstream Modifications & Rent Ledger

1. `android/keyboard/ime/app/src/main/AndroidManifest.xml`: Add `<uses-permission android:name="android.permission.INTERNET" />` to enable keyboard socket communication with remote AI providers upon user opt-in.
2. `android/keyboard/UPSTREAM-MODIFIED.md`: Record the manifest permission addition.

---

## 3. Slice B: Settings UI, Model Browser & Provider Opt-In (Upcoming)

### 3.1 Components & UX Flow

1. **"The Brain" Settings Screen (`ProviderSetupScreen.kt`):**
   - Provider radio group (`Fake / Offline`, `OpenRouter`, `Anthropic`).
   - Obfuscated API key password field with Save/Clear actions bound to Keystore.
   - Model selection text field with validation.
   - "Browse models…" dialog button (for OpenRouter) querying public catalog.
2. **Settings Home & Onboarding Cards (`SettingsHomeScreen.kt`):**
   - "AI Brain" row reflecting active provider and configuration status.
   - "Get Started" onboarding card shown when unconfigured, linking to System IME settings and Brain configuration.
3. **Runtime Composition Binding (`PersonaSpeakComposition.kt`):**
   - Resolves active provider from `ProviderConfigStore` on input view start.
   - Falls back safely to `FakeProvider` if unconfigured.

---

## 4. Verification & Quality Gates

### 4.1 Slice A Acceptance Criteria

- [x] **ADR-0009 Landed:** Architecture, config schema, custom base-URL classification, and privacy disclosures documented.
- [x] **Pure Kotlin Implementation:** `OpenRouterAdapter`, `OpenRouterModels`, and `MiniJson` contain zero `android.*` dependencies.
- [x] **Comprehensive Contract Tests:**
  - 200 OK text extraction with markdown/whitespace formatting.
  - 401/403 mapping to `AdapterResult.AuthFailure`.
  - 429 rate limit mapping to `NetworkErrorCode.HTTP_CLIENT_ERROR`.
  - 500/502/503 server errors mapping to `NetworkErrorCode.HTTP_SERVER_ERROR`.
  - Socket timeout mapping to `NetworkErrorCode.TIMEOUT`.
  - Memory zeroing of `SecretBytes` verified.
  - Pinned HTTPS endpoint verification.
  - MiniJson verified against real captured payloads (`or_chat.json`, `or_models.json`).
- [x] **Zero Secret Logging:** Verified by `NoSecretLoggingTest` and `verify-no-secret-logging.sh`.
- [x] **Upstream Rent Ledgered:** Manifest permission recorded in `UPSTREAM-MODIFIED.md`.
- [x] **Build & CI Clean:** `:personaspeak-providers:testDebugUnitTest`, `:ime:app:compileDebugKotlin`, and `verify-milestone-4.sh` pass.
