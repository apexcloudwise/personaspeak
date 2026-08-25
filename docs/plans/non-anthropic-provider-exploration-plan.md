# Non-Anthropic Provider Exploration: Architectural Options, Contract Boundaries, and Feasibility Plan

**Issue:** #101  
**Parent Milestone:** #89  
**Parent / Closeout Gate:** #96 (remains OPEN)  
**Plan Precedent:** PR #91 (`be0e563`), PR #94 (`c54ad91`), PR #97 (`b0ac6a1`), PR #100 (`99e0393`)  
**Baseline Commit:** `99e0393f9c2d1b647db08e1ec20cb126fa9bda85` (head of `main`)  
**Owner:** @reicodes-pixelperfect  
**Reviewers:** Seraph (@seraph-pixelperfect), Cassie (@cassievale-pixelperfect), Sigrid (@sigrid-pixelperfect), Ghost (@ghostinprod-pixelperfect)  

---

## 0. Plan-Only Scope Statement & Architectural Baseline

This pull request contains the **architectural exploration and feasibility plan only** evaluating potential non-Anthropic provider options (specifically OpenRouter and Z.AI) alongside the existing Anthropic baseline.

- **Strict Plan-Only Invariant:** No provider implementation code, no endpoint rewiring, no production egress, no real credentials, and no live network receipts are introduced in this PR.
- **Human Constraint Alignment (Mock-Only):** Per human decision on 2026-08-25, provider exploration is strictly constrained to mock-only/synthetic fixtures. No real credentials will be provisioned or transmitted, and no live egress will be executed.
- **Preservation of Milestone 4 Gate (#96 / #89):** Issue #96 and Milestone #89 remain explicitly **OPEN**. The three pending qualification gates for Anthropic (API-27 `bmgr` backup exclusion, Mode-A offline ART harness execution, and Mode-B live-egress socket audit) remain undisturbed. Mocked or alternate-provider test results must never be represented as Anthropic M4 closeout evidence.
- **Default-Disabled Wiring Invariant:** The existing `AnthropicMessagesAdapter` and any prospective future adapter remain structurally disabled by default. `FakeProvider` remains the active default in the keyboard rewrite coordinator until user-driven opt-in is shipped in Milestone 5.

---

## 1. Seam Survey & Baseline Architecture

### 1.1 Existing `:personaspeak-providers` Architecture (PR #95 at `672a808`)

| Component | Location | Responsibility & State |
|---|---|---|
| `ProviderAdapter` | `personaspeak-providers/.../ProviderAdapter.kt` | Port defining `val providerId: String`, `val displayName: String`, and `suspend fun rewrite(system: String, text: String, secret: SecretBytes): AdapterResult`. |
| `HttpTransport` | `personaspeak-providers/.../AnthropicMessagesAdapter.kt` | Transport abstraction: `fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse`. |
| `DefaultHttpTransport` | `personaspeak-providers/.../AnthropicMessagesAdapter.kt` | Production transport enforcing HTTPS, timeouts (15s connect / 30s read), `instanceFollowRedirects = false`, and endpoint URL equality check. |
| `AnthropicMessagesAdapter` | `personaspeak-providers/.../AnthropicMessagesAdapter.kt` | Direct integration with `https://api.anthropic.com/v1/messages` using `x-api-key` and `anthropic-version: 2023-06-01`. Handcrafted, dependency-free JSON parsing. |
| `NetworkErrorCode` | `personaspeak-ui/brain/ProviderConfig.kt` | Closed taxonomy (`TIMEOUT`, `IO_ERROR`, `HTTP_SERVER_ERROR`, `HTTP_CLIENT_ERROR`) ensuring zero raw `Throwable` instances escape across the adapter boundary. |
| `SecretBytes` | `personaspeak-ui/brain/ProviderConfig.kt` | Memory-zeroable wrapper (`ByteArray.fill(0)` in adapter `finally` block). |

### 1.2 Upstream Isolation & Dependency Ledger

- **Pure Kotlin Standard:** `:personaspeak-providers` depends exclusively on `kotlinx-coroutines-core` and `:personaspeak-ui` (domain models). It contains **zero Android platform imports** (`android.*`).
- **Zero Upstream Modifications:** Upstream AnySoftKeyboard source files (`android/keyboard/`) are completely uncoupled from provider network adapters. `UPSTREAM-MODIFIED.md` incurs zero additional rent.
- **Static Security Gates:** `android/scripts/verify-no-secret-logging.sh` and `verify-milestone-4.sh` deterministically scan `:personaspeak-providers` for secret leakage or forbidden logging.

---

## 2. Comparative Analysis of Candidate Paths

We evaluate four candidate paths for PersonaSpeak's remote provider architecture under the mock-only human constraint:

```
+-----------------------------------------------------------------------------------------+
|                                    CANDIDATE PATHS                                      |
+-----------------------------------------------------------------------------------------+
| Path A: Retain Disabled Anthropic-Only Scaffolding (Defer Live Qualification)           |
| Path B: Add Separately Disabled OpenRouter Adapter (Mock-Only Exploration)              |
| Path C: Add Separately Disabled Z.AI Adapter (Mock-Only Exploration)                   |
| Path D: Decline Additional Providers / Formal Deferral to Milestone 5                   |
+-----------------------------------------------------------------------------------------+
```

### 2.1 Path A: Retain Disabled Anthropic-Only Scaffolding & Defer Live Qualification

- **Description:** Maintain the merged `:personaspeak-providers` baseline as-is. `AnthropicMessagesAdapter` remains the sole implemented remote adapter, structurally dormant behind `FakeProvider`. Live device qualification is deferred until a real Anthropic credential authority and API-27 disposable fixture are provisioned.
- **Endpoint & Auth:** Pinned endpoint `https://api.anthropic.com/v1/messages`, `x-api-key: <RAW_KEY>`, `anthropic-version: 2023-06-01`.
- **Request / Response Format:**
  - Request: `{"model":"claude-3-5-haiku-20241022","max_tokens":1024,"system":"...","messages":[{"role":"user","content":"..."}]}`
  - Response: `{"id":"...","type":"message","role":"assistant","content":[{"type":"text","text":"..."}],"stop_reason":"end_turn"}`
- **Data / Egress & Privacy Boundary:** Direct 1st-party egress to `api.anthropic.com:443`. No intermediate proxy, no third-party telemetry. Anthropic commercial API privacy policy applies (zero model training on API inputs by default).
- **Module Impact:** Zero code changes. Zero new dependencies.
- **Test Seam:** 7 existing contract test files in `:personaspeak-providers` asserting endpoint pinning, auth failure, status code mapping, and secret zeroing against `HttpTransport` fakes.
- **User-Facing Configuration:** Single API key input in Settings (Milestone 5).
- **Rollback & Complexity:** Zero operational risk. Minimal maintenance overhead.

### 2.2 Path B: Add Separately Disabled OpenRouter Adapter (Mock-Only Exploration)

- **Description:** Implement a standalone `OpenRouterAdapter` in `:personaspeak-providers` implementing the `ProviderAdapter` port against OpenRouter's unified OpenAI-compatible completions API. Kept structurally disabled by default.
- **Endpoint & Auth:** Pinned endpoint `https://openrouter.ai/api/v1/chat/completions`, `Authorization: Bearer <OPENROUTER_API_KEY>`, optional attribution headers `HTTP-Referer: https://pixelperfectstudios.biz` and `X-Title: PersonaSpeak`.
- **Request / Response Format:**
  - Request: `{"model":"anthropic/claude-3.5-haiku","messages":[{"role":"system","content":"..."},{"role":"user","content":"..."}]}`
  - Response (OpenAI Chat Completions schema): `{"id":"...","choices":[{"index":0,"message":{"role":"assistant","content":"..."},"finish_reason":"stop"}]}`
- **Data / Egress & Privacy Boundary:**
  - Egress strictly pinned to `openrouter.ai:443`.
  - **Privacy Tradeoff:** OpenRouter acts as an intermediary routing proxy between the client and downstream LLM hosts (Anthropic, OpenAI, Meta, etc.). Egress is subject to OpenRouter's privacy policy and data routing rules. Zero Data Retention (ZDR) endpoints are available on OpenRouter but require model-specific routing prefixes.
- **Module Impact:**
  - New file: `personaspeak-providers/src/main/kotlin/.../OpenRouterAdapter.kt`.
  - Parser: Lightweight handcrafted extraction for `choices[0].message.content`.
  - Storage / Config: `ProviderConfig.kt` requires either a provider discriminator or separate storage key slot if multiple providers coexist in M5.
- **Test Seam:** Synthetic JSON unit tests via `HttpTransport` verifying 200 extraction, 401/403 `AuthFailure`, 429 rate limit mapping to `NetworkErrorCode.HTTP_CLIENT_ERROR`, 502/503 upstream gateway errors mapping to `NetworkErrorCode.HTTP_SERVER_ERROR`, and `SecretBytes.fill(0)` zeroing.
- **User-Facing Configuration:** OpenRouter API key entry; optional model slug selection in advanced settings.
- **Rollback & Complexity:** Low code footprint in pure Kotlin; introduces third-party intermediary dependency.

### 2.3 Path C: Add Separately Disabled Z.AI Adapter (Mock-Only Exploration)

- **Description:** Implement a dedicated `ZaiAdapter` in `:personaspeak-providers` targeting Z.AI's API endpoints (either their Anthropic-compatible gateway or standard OpenAI-compatible completions endpoint).
- **Endpoint & Auth:**
  - Anthropic-compatible path: `https://api.z.ai/api/anthropic/v1/messages` with `x-api-key: <ZAI_TOKEN>` or `Authorization: Bearer <ZAI_TOKEN>`.
  - Standard path: `https://api.z.ai/api/coding/paas/v4/chat/completions`.
- **Request / Response Format:** Mirrors Anthropic Messages schema or OpenAI Chat Completions schema depending on selected gateway path.
- **Data / Egress & Privacy Boundary:**
  - Egress pinned to `api.z.ai:443`.
  - **Privacy Tradeoff:** Z.AI hosting infrastructure and regional routing policies (data handling jurisdictions, cross-border transmission rules). Egress boundaries require explicit legal/privacy review per ADR-0005 ("We are a keyboard, not a diary").
- **Module Impact:**
  - New file: `personaspeak-providers/src/main/kotlin/.../ZaiAdapter.kt`.
  - Transport: Dedicated `DefaultHttpTransport` pinning `api.z.ai`.
- **Test Seam:** Synthetic JSON fixture tests over `HttpTransport`.
- **User-Facing Configuration:** Z.AI token input.
- **Rollback & Complexity:** Moderate complexity; heightened privacy governance overhead due to regional infrastructure questions.

### 2.4 Path D: Decline Additional Providers for Now (Formal Deferral)

- **Description:** Formally decline introducing any non-Anthropic provider adapter code until Milestone 5 (User Settings & Onboarding) is established and the first provider's end-to-end UX is fully proven.
- **Rationale:** PersonaSpeak is currently in Milestone 4 (Storage, Adapter Contracts & Verification). Adding speculative multi-provider adapters before shipping the settings UI adds maintenance drag without advancing user capabilities.

---

## 3. Contract & Architectural Comparison Matrix

| Dimension | Path A: Anthropic (Baseline) | Path B: OpenRouter | Path C: Z.AI | Path D: Deferral |
|---|---|---|---|---|
| **API Endpoint** | `https://api.anthropic.com/v1/messages` | `https://openrouter.ai/api/v1/chat/completions` | `https://api.z.ai/api/anthropic/v1/messages` (or `/paas/v4/...`) | None |
| **Authentication** | `x-api-key: <KEY>` + `anthropic-version` | `Authorization: Bearer <KEY>` | `x-api-key` / `Authorization: Bearer` | None |
| **Payload Schema** | Anthropic Messages (`system` top-level, `messages` array) | OpenAI Chat (`messages` with `system` & `user` roles) | Anthropic Messages or OpenAI Chat | None |
| **Response Extraction** | `content[0].text` | `choices[0].message.content` | `content[0].text` or `choices[0]...` | None |
| **Intermediary Routing** | Direct (Client -> Anthropic) | Proxy (Client -> OpenRouter -> Host) | Direct/Proxy (Client -> Z.AI -> Model) | None |
| **Privacy / Egress Risk** | Low (direct 1st-party API, strict no-train defaults) | Moderate (third-party routing proxy, varying model retention) | High / Unreviewed (regional routing & data sovereignty) | Zero |
| **Module Footprint** | Existing (:personaspeak-providers) | +1 Adapter class (~150 LOC) | +1 Adapter class (~150 LOC) | Zero diff |
| **JSON Parser Complexity** | Low (handcrafted text slicing) | Low (handcrafted content slicing) | Low (mirrors Anthropic or OpenAI) | Zero |
| **Memory Hygiene** | `SecretBytes.fill(0)` enforced | `SecretBytes.fill(0)` enforced | `SecretBytes.fill(0)` enforced | N/A |
| **M4 Gate Status** | #96 Open (Awaits live fixture) | Independent / Non-gating for #96 | Independent / Non-gating for #96 | #96 Open |

---

## 4. Architectural Evaluation & Tradeoffs

### 4.1 Privacy Posture & The Keyboard Contract (ADR-0005)

Under ADR-0005 ("Privacy Posture & Fork Audit") and AGENTS.md Prime Directive:
> *"Storing anything a user typed. We are a keyboard, not a diary. Anything that makes the privacy story more complicated to explain is returned with a raised eyebrow."*

- **Direct Frontier APIs (Path A):** The simplest privacy story. Text leaves the device solely during an explicit user-initiated rewrite invocation directly to the model provider (`api.anthropic.com`).
- **Aggregators & Proxies (Path B - OpenRouter):** OpenRouter enables access to dozens of model providers (Claude, Llama, GPT, Mistral) under one unified credential and billing umbrella. However, the egress boundary now passes through an intermediary proxy (`openrouter.ai`). If PersonaSpeak adopts OpenRouter, the privacy disclosure must transparently state that prompt text transits OpenRouter's routing infrastructure.
- **Regional Providers (Path C - Z.AI):** Involves foreign/regional hosting domains that complicate privacy compliance disclosures for a general-audience Android keyboard.

### 4.2 Parser Simplicity & Zero-Dependency Purity

In accordance with our pure Kotlin rule for `:personaspeak-providers`:
- We do **not** pull in Jackson, Gson, or heavy JSON reflection libraries that bloat the APK and increase startup overhead.
- Handcrafted string/JSON parsers (like `AnthropicMessagesAdapter.extractTextFromResponse`) are blazingly fast, allocate minimal transient memory, and contain zero reflection overhead.
- An OpenRouter adapter parsing `choices[0].message.content` can be implemented with an identical ~30-line deterministic string-slicing state machine.

### 4.3 Error Taxonomy & State Separation (A4 Invariant)

Any new provider adapter must adhere strictly to the established Milestone 4 state architecture:
1. **Zero Throwable Leaks:** All network exceptions are mapped to the closed `NetworkErrorCode` enum (`TIMEOUT`, `IO_ERROR`, `HTTP_SERVER_ERROR`, `HTTP_CLIENT_ERROR`).
2. **A4 Truthful Runtime Separation:** Request-time authentication failures (`401/403`) return `AdapterResult.AuthFailure`, updating `SettingsState.lastRewriteResult` without mutating `ProviderConfigStore` or triggering an unprovoked credential wipe. Keystore wiping remains reserved exclusively for explicit user actions or unrecoverable keystore corruption (`StoreOutcome.InvalidCredentials`).

---

## 5. Bounded Recommendation

### 5.1 Primary Recommendation: Bounded Deferral (Path A / Path D)

**We recommend remaining with the current disabled Anthropic-only scaffolding for Milestone 4 and deferring multi-provider implementation to Milestone 5.**

**Technical Rationale:**
1. **Focus on Milestone Flow:** Milestone 4's explicit purpose is establishing the storage seam, provider adapter interface, and verification contracts. Milestone 5 introduces the user settings UI, onboarding flow, and provider selection controls.
2. **Preventing Dormant Code Accumulation:** Introducing multiple dormant adapters (`OpenRouterAdapter`, `ZaiAdapter`) before a user can even configure or select them in the UI creates dead code rent without user benefit.
3. **Preserving M4 Integrity:** M4 closeout (#96) is strictly blocked on physical/AVD device qualification for Anthropic. Adding more unverified adapters does not accelerate #96; it merely expands the unverified surface area.

### 5.2 Secondary Fallback: OpenRouter (Path B) under Mock-Only Contract

**If the human stakeholder / product direction explicitly mandates prototyping a non-Anthropic provider prior to M5, OpenRouter (Path B) is the recommended technical choice.**

**Technical Merits over Z.AI:**
1. **Universal OpenAI Wire Compatibility:** OpenRouter utilizes the standard OpenAI Chat Completions schema, giving PersonaSpeak an adapter design reusable across OpenAI, Groq, Mistral, and local Ollama instances.
2. **Unified Model Access:** A single OpenRouter adapter allows routing to Claude 3.5 Haiku, GPT-4o-mini, or open-weight models without implementing separate vendor-specific SDK adapters.
3. **Strict Mock-Only Seam:** Contract tests for `OpenRouterAdapter` can be fully executed offline against `HttpTransport` synthetic JSON fixtures without provisioning any real API keys or generating live network traffic.

---

## 6. Governance, ADR Requirement & Future Sequence

### 6.1 ADR Requirement

Merging any non-Anthropic adapter into `main` will require authoring **ADR-0009 ("Pluggable Multi-Provider Architecture & OpenRouter Evaluation")** documenting:
- The multi-provider configuration schema in `ProviderConfig`.
- The egress boundary and privacy disclosures for third-party proxy routing.
- The default-disabled invariant for secondary providers.

### 6.2 Future Phased Execution Sequence

If the team decides to proceed with an OpenRouter prototype in a future slice:

```
[ Phase 0: Plan Review (#101) ]  <-- (Current PR)
               │
               ▼
[ Phase 1: Human Authority & ADR-0009 ]
               │
               ▼
[ Phase 2: Mock-Only Implementation PR ]
  - Add OpenRouterAdapter.kt in :personaspeak-providers
  - Add synthetic JSON unit contract tests
  - Update verify-no-secret-logging.sh
  - Zero modifications to ASK upstream code
               │
               ▼
[ Phase 3: Exact-Head Multi-Model Review ]
  - Review by Seraph, Cassie, Sigrid
               │
               ▼
[ Phase 4: Future M5 UI & Optional Device Qualification ]
```

---

## 7. Acceptance Criteria & Quality Gates

For this planning deliverable to be complete and ready for exact-head review:

- [x] **Plan-Only Scope:** The PR contains solely `docs/plans/non-anthropic-provider-exploration-plan.md` and the required entry in `PATCHNOTES.md`. Zero production code modified.
- [x] **Mock-Only Invariant:** The plan explicitly enforces the human constraint that no real credentials or live egress are used.
- [x] **Preservation of #96 / #89:** The plan explicitly confirms that #96 and #89 remain open and that mock/alternate provider results cannot qualify Anthropic Mode B.
- [x] **Comprehensive Path Analysis:** Paths A, B, C, and D are evaluated across contracts, payloads, privacy, modules, test seams, and risk.
- [x] **Clear Bounded Recommendation:** Recommends bounded deferral (Path A/D) as primary, with OpenRouter (Path B) as the preferred secondary fallback.
- [x] **Governance & ADR Enforced:** Defines ADR-0009 requirement and downstream implementation sequence.
- [x] **CI Compliance:** CI passes all automated checks.
