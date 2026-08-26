# ADR-0009: Pluggable Multi-Provider Architecture and OpenRouter Evaluation

**Status:** Accepted (2026-08-27, Milestone 5 kickoff #103)

## Context

In Milestone 4 (PR #95 at `672a808`), we introduced the `:personaspeak-providers` module with the `ProviderAdapter` port and the `AnthropicMessagesAdapter` targeting `api.anthropic.com`. Under PR #102 (`5a35853`), the team completed an architectural exploration of non-Anthropic provider paths and recommended evaluating OpenRouter as the sanctioned secondary provider for Milestone 5.

On 2026-08-26, an exploratory prototype (`m4-proto`, commit `0316dbd`) verified on a physical device (Motorola Edge 50 Pro) and emulator that OpenRouter's OpenAI-compatible completions endpoint provides viable multi-model rewriting (with free default model `nvidia/nemotron-3-super-120b-a12b:free`, after OpenRouter retired the older `meta-llama/llama-3.3-70b-instruct:free` slug).

Per Milestone 5 kickoff (#103), this ADR formally establishes the multi-provider architecture, configuration schema, data classification, privacy boundary, and default-disabled invariants required before merging non-Anthropic provider implementations.

## Decisions

### 1. Multi-Provider Interface and Registry Seam

We retain and standardize on the Milestone 4 `ProviderAdapter` interface in `:personaspeak-providers`:

```kotlin
interface ProviderAdapter {
    val providerId: String
    val displayName: String

    suspend fun rewrite(
        system: String,
        text: String,
        secret: SecretBytes,
    ): AdapterResult
}
```

- **Pure Kotlin Standard:** `:personaspeak-providers` remains pure Kotlin with zero Android platform dependencies (`android.*`).
- **Transport Abstraction:** All HTTP adapters execute over `HttpTransport` with deterministic status code mapping (`AdapterResult.Success`, `AdapterResult.AuthFailure`, `AdapterResult.NetworkFailure` with closed `NetworkErrorCode`), allowing contract verification against synthetic HTTP fixtures without network egress.
- **Zero-Dependency JSON Parsing:** Response parsing uses a pure Kotlin, reflection-free parser (`MiniJson` / deterministic string parsing) pinned by contract tests against captured payloads. Heavy reflection libraries (Jackson, Gson) remain prohibited.

### 2. Provider Identification & Configuration Schema

Providers are discriminated by stable string identifiers:
- `"anthropic"` — Direct 1st-party Anthropic Messages API (`https://api.anthropic.com/v1/messages`).
- `"openrouter"` — OpenRouter Chat Completions gateway (`https://openrouter.ai/api/v1/chat/completions`).
- `"fake"` / `"offline"` — Offline canned persona preview (`FakeProvider`), requiring no network or credentials.

The configuration schema across storage boundaries is defined as:
1. **Secret Credential Blob (AndroidKeyStore AES-256-GCM):**
   - Stores exclusively the raw API key / auth bearer token as `SecretBytes`.
   - Wiped on memory zeroing (`SecretBytes.value.fill(0)`) and explicitly excluded from Android cloud/device backups (`fullBackupContent` and `dataExtractionRules`).
2. **Non-Secret Metadata (Preferences DataStore):**
   - `providerId: String` (default: `"fake"`)
   - `model: String` (e.g. `"nvidia/nemotron-3-super-120b-a12b:free"` for OpenRouter, `"claude-3-5-haiku-20241022"` for Anthropic)
   - `customBaseUrl: String?` (optional, for custom OpenAI-compatible endpoints)

### 3. Data Classification: Custom Base URL

The exploratory prototype surfaced an ambiguity regarding custom base URLs. We classify `customBaseUrl` as follows:
- **Classification:** Non-Secret Configuration Metadata (stored in Preferences DataStore, NOT Keystore).
- **Security & Integrity Constraints:**
  - Must strictly use the `https://` scheme in production builds. Cleartext `http://` is prohibited.
  - Pinned default for OpenRouter is immutable: `https://openrouter.ai/api/v1`.
  - Stored URLs are validated against URI syntax prior to persistence.
  - Logging custom URLs in diagnostic logs is permissible only if query parameters and user credentials are strip-filtered; never log raw request bodies or auth headers.

### 4. Egress Boundary and Privacy Disclosures for Proxy Routing

Under ADR-0005 ("Privacy Posture & Fork Audit") and the AGENTS.md Prime Directive:
> *"Storing anything a user typed. We are a keyboard, not a diary."*

When a user selects OpenRouter:
- **Egress Path:** HTTPS egress connects directly to `openrouter.ai:443`.
- **Proxy Routing:** OpenRouter acts as an intermediary routing proxy forwarding prompts to downstream model hosts.
- **Data Retention & Privacy Policy:**
  - Request headers include standard attribution (`HTTP-Referer: https://pixelperfectstudios.biz`, `X-Title: PersonaSpeak`).
  - Where supported by downstream endpoints, requests may declare Zero Data Retention flags (`zdr: true`).
  - Settings UI must explicitly disclose that prompt text transits OpenRouter infrastructure subject to OpenRouter's privacy policy and selected model provider terms.
  - No drafts, prompts, candidates, or completions are ever persisted to disk by PersonaSpeak.

### 5. Default-Disabled Invariant & Opt-In Governance

- **Default-Disabled:** PersonaSpeak ships with cloud egress structurally disabled. `FakeProvider` remains the active default provider out of the box.
- **Explicit User Opt-In:** Network calls occur only after the user deliberately navigates to Settings ("The Brain"), selects a remote provider, inputs their API key, and saves configuration.
- **Fail-Closed on Unconfigured/Corrupt State:** If no valid key exists, or if Keystore credentials are unrecoverable, the coordinator seamlessly falls back to `FakeProvider` without blocking user typing or keyboard operation.
- **Truthful Runtime State (A4 Invariant):** Request-time auth failures (HTTP 401/403) update transient runtime state (`AdapterResult.AuthFailure`) to alert the user in the UI, but NEVER silently delete or corrupt stored credentials.

## Consequences

- We introduce `OpenRouterAdapter` and `OpenRouterModels` to `:personaspeak-providers`.
- Contract tests verify OpenRouter JSON parsing, HTTP status mappings, and memory zeroing against synthetic test doubles.
- The Android manifest receives the `INTERNET` permission (`android.permission.INTERNET`) required for remote provider egress, ledgered in `UPSTREAM-MODIFIED.md`.
- Milestone 4 qualification gates (#96 / #89) remain parked on human credential decisions; Milestone 5 proceeds independently under mock-only test contracts for merged code.
