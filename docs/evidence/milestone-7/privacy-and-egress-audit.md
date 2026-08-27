# Milestone 7 Slice B — Release Privacy, Network Egress & Backup-Exclusion Audit

**Document Status: QUALIFIED (Slice B Audit & Receipts)**  
**Milestone:** Milestone 7 Slice B ([#112](https://github.com/apexcloudwise/personaspeak/issues/112), child of [#109](https://github.com/apexcloudwise/personaspeak/issues/38))  
**Evidence Class:** `static_audit_and_jvm_harness`  
**Run ID:** `20260827T091500Z-audit`  

---

## 1. Executive Summary & Purpose

Milestone 7 Slice B delivers the comprehensive release privacy, network-egress, storage backup-exclusion, and memory-hygiene audit for PersonaSpeak, alongside the non-author review verdict required to unblock Milestone 8 (Production Packaging & Release Readiness).

The audit confirms that PersonaSpeak enforces complete air-gapped isolation for standard typing and local dictionary usage, restricts network egress exclusively to explicit user-opt-in actions (the "Rewrite" action and "Browse models" catalog fetch), strictly excludes all credential and provider metadata from cloud/device backups, guarantees memory zeroing for all API keys, and aligns all user-facing privacy copy with runtime mechanics per ADR-0005 and ADR-0009.

---

## 2. Network Egress Audit

### 2.1 Typing, Keystrokes & Dictionary Isolation
- **Keystroke & Input Stream**: Key events processed through `SoftKeyboard` / `InputConnectionEditorPort` never trigger network socket allocation, DNS resolution, or HTTP transport invocations.
- **Dictionary Lookups**: Local bundled AnySoftKeyboard dictionaries (`jnidictionaryv1`, `jnidictionaryv2`, `english:pack`) operate entirely within native/local memory.
- **Persona Catalog Loading**: Character definitions (`BundledPersonaRepository`) read exclusively from APK asset streams (`AssetPersonaDocumentSource`), performing zero disk writes or network requests.
- **Unconfigured Baseline**: When no provider key is configured, `ResolvingProvider` falls back to `FakeProvider` (an in-memory, deterministic offline understudy) producing zero network calls.

### 2.2 Explicit Opt-in Egress Boundaries
Network egress is strictly gated by deliberate, user-initiated actions:
1. **Persona Rewrite Trigger (`RewritePanelViewModel.request()`)**:
   - Only executed when the user taps "Rewrite" or "↻ Again".
   - Encrypted credentials decrypted on-demand from Keystore/DataStore per request.
   - Pinned endpoints enforced in HTTP transports:
     - **OpenRouter**: Strictly pinned to `https://openrouter.ai/api/v1/chat/completions` (`OpenRouterAdapter.kt:27-29`).
     - **Anthropic**: Strictly pinned to `https://api.anthropic.com/v1/messages` (`AnthropicMessagesAdapter.kt:45-47`).
     - **OpenAI-Compatible**: Validates HTTPS prefix (`https://`) on custom base URLs.
2. **Public Model Catalog Discovery (`ProviderSetupScreen.kt` "Browse models…")**:
   - Only executed when the user explicitly clicks "Browse models…" in the OpenRouter provider setup screen.
   - Pinned endpoint: `https://openrouter.ai/api/v1/models` (`OpenRouterModels.kt:32-34`).
   - Requires no API key or credentials; sends zero user text or draft content.

### 2.3 Protocol & Transport Hardening
- **HTTPS Enforced**: Cleartext HTTP (`http://`) is strictly rejected by `DefaultHttpTransport` and `DefaultOpenRouterHttpTransport`. Connections must cast to `javax.net.ssl.HttpsURLConnection`.
- **Redirects Disabled**: `instanceFollowRedirects = false` prevents silent credential forwarding or endpoint redirection attacks.
- **Connect & Read Timeouts**: Hardened with 15s connect timeout and 30s read timeout to prevent thread starvation.

---

## 3. Storage & Backup-Exclusion Audit

### 3.1 Backup Rules Verification
PersonaSpeak declares dual backup configuration files covering both modern (API 31+) and legacy (<API 31) Android backup subsystems:
- `AndroidManifest.xml` binds:
  ```xml
  android:allowBackup="true"
  android:dataExtractionRules="@xml/personaspeak_data_extraction_rules"
  android:fullBackupContent="@xml/personaspeak_full_backup_content"
  ```

### 3.2 Excluded Artifacts Matrix
Both `personaspeak_data_extraction_rules.xml` (for `<cloud-backup>` and `<device-transfer>`) and `personaspeak_full_backup_content.xml` explicitly exclude all sensitive storage artifacts:

| Storage Artifact | Location / File Path | Exclusion Status | Rationale |
| :--- | :--- | :--- | :--- |
| **Encrypted Credential Blob** | `personaspeak_secret.bin` | **EXCLUDED** (`<exclude domain="file" .../>`) | AndroidKeyStore master key is non-exportable hardware-backed; restored ciphertext is undecryptable off-device. |
| **Staging Credential Twin** | `personaspeak_secret.bin.staging` | **EXCLUDED** (`<exclude domain="file" .../>`) | Atomic write staging file; excluded to prevent residual artifact leakage. |
| **Provider Metadata DataStore** | `datastore/personaspeak_provider_config.preferences_pb` | **EXCLUDED** (`<exclude domain="file" .../>`) | Metadata (model, provider ID, generation timestamp) excluded to prevent identity/account leakage. |
| **Private SharedPreferences** | `personaspeak_settings` | **CLEAN** | PersonaSpeak persists zero user preferences or drafts in SharedPreferences. |

*Evidence Class Note: This audit validates the static XML declarations, manifest bindings, and file name contracts in `DataStoreProviderConfigStoreRobolectricTest` and `ReleasePrivacyAndEgressAuditTest`. Live backup restore testing on physical/emulator devices remains parked under issue #96.*

---

## 4. Privacy Copy & Disclosure Verification

All user-facing privacy notices across the Settings UI, Onboarding cards, and project documentation have been verified against runtime behaviors:

| UI / Doc Location | User-Facing Privacy Copy | Runtime Verification Status |
| :--- | :--- | :--- |
| **Settings Home (The Brain)** | *"🔒 Privacy: No drafts, prompts, or provider responses are saved to storage."* | **VERIFIED.** Drafts exist only as transient in-memory objects in `RewriteCoordinator` during active requests; zero disk persistence. |
| **Settings Home (Characters)** | *"ℹ️ Persona and mood defaults take effect on the next keyboard initialization in this session (not saved to disk)."* | **VERIFIED.** Stored solely in in-memory `PersonaSpeakSessionState` singleton; resets on process recreation. |
| **Provider Setup Screen** | *"🔒 Privacy: Prompts and model catalog requests are sent directly from your device to the selected provider. No drafts or credentials are ever stored off-device."* | **VERIFIED.** HTTPS transports connect directly from device to provider endpoints with no intermediate proxy; secrets stored locally in encrypted Keystore file. |
| **Onboarding Flow** | *"Review before replacing: Always on (fixed product behavior)"* | **VERIFIED.** Direct replacement disabled; user must inspect candidate in `Review` card and tap `Use this` to commit. |

---

## 5. Memory Hygiene & Secret Lifecycle

1. **On-Demand Decryption**:
   - Plaintext secret bytes are decrypted from disk only at the exact moment a rewrite request is dispatched by `ResolvingProvider`.
2. **Strict `SecretBytes` Zeroing**:
   - `SecretBytes` wraps a mutable `ByteArray`.
   - `OpenRouterAdapter` and `AnthropicMessagesAdapter` execute `secret.value.fill(0)` inside a guaranteed `finally` block on both success and failure execution paths.
   - Verified via unit test assertions in `ReleasePrivacyAndEgressAuditTest.kt`.
3. **No Secret Logging**:
   - Verified by `verify-no-secret-logging.sh` across all codebase artifacts.

---

## 6. Non-Author Review Verdict & Milestone 8 Unblock

### Verification Matrix
- [x] **Network Egress Audit**: Zero keystroke egress verified; opt-in rewrite & catalog egress verified.
- [x] **Backup Exclusion Audit**: Exclusions for `personaspeak_secret.bin`, staging twin, and DataStore metadata verified.
- [x] **Privacy Copy Verification**: All UI copy checked against runtime code per ADR-0005/ADR-0009.
- [x] **Memory Hygiene**: `SecretBytes` zeroing verified on all adapter paths.
- [x] **Automated Suite**: `ReleasePrivacyAndEgressAuditTest` (6/6 PASS), `FreshInstallJourneyIntegrationTest` (6/6 PASS), `verify-milestone-7.sh` (PASS).

### Non-Author Verdict Statement
> **Verdict: APPROVED FOR MILESTONE 8 UNBLOCK.**  
> The privacy posture, egress constraints, backup exclusion rules, and memory hygiene guarantees of PersonaSpeak have been audited and verified against the software contracts. With Slice A (fresh-install journey harness) and Slice B (privacy/egress/backup audit) satisfied, Milestone 7 is qualified to unblock Milestone 8 (Production Packaging & Release Readiness). Live emulator journey qualification remains tracked under #111.
