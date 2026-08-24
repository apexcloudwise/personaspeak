# M4 slice 2 — implementation plan: provider adapter(s) and truthful runtime states

**Parent issue:** #93  
**Plan baseline:** PR #92, squash `bda68f08b92e159f7bf87ed84aee759525166ae4`  
**Plan precedent:** PR #91 (`be0e563`)  
**Reviewer assignment:** Seraph, Cassie, Sigrid (same gate as slice 1 — plan reviewed and approved before any implementation)

---

## 0. Plan-only scope statement

This PR contains **plan documents and stub files only**. No production adapter code, no network calls, no Keystore reads/writes, and no live provider enablement are present. The plan must receive independent approval from two non-author reviewers before implementation begins in a separate PR.

---

## 1. Seam survey — post-`bda68f0` baseline

### 1.1 Port / type surface (`personaspeak-ui`)

| Symbol | Location | Role |
|---|---|---|
| `ProviderConfigStore` | `personaspeak-ui/brain/ProviderConfigStore.kt` | Port (interface) — consumed by settings layer from slice 2 onward |
| `ProviderConfig` | `personaspeak-ui/brain/ProviderConfig.kt` | Non-secret metadata payload |
| `SecretBytes` | `personaspeak-ui/brain/ProviderConfig.kt` | Opaque decrypted credential — never logged, never persisted in plaintext |
| `StoreOutcome` | `personaspeak-ui/brain/ProviderConfig.kt` | Sealed interface: `Unconfigured`, `Configured`, `Unavailable(reasonCode)`, `InvalidCredentials` |
| `StoreFailure` | `personaspeak-ui/brain/ProviderConfig.kt` | `KEYSTORE_UNAVAILABLE`, `IO_ERROR` |
| `ProviderConfigSnapshot` | `personaspeak-ui/brain/ProviderConfig.kt` | Returned by `ProviderConfigStore.load()` — carries decrypted secret when configured |

### 1.2 Storage implementation (`personaspeak-data`)

| Symbol | Location | Role |
|---|---|---|
| `DataStoreProviderConfigStore` | `personaspeak-data/.../data/DataStoreProviderConfigStore.kt` | Concrete `ProviderConfigStore` — stage/commit/swap, four-state recovery matrix |
| `DataStoreMetaStore` | `personaspeak-data/.../data/DataStoreMetaStore.kt` | Non-secret DataStore metadata half of the two-artifact store |
| `ProviderMeta` | same file | `providerId`, `configuredAtEpochMs`, `schemaVersion`, `generation` |
| `KeystoreSecretCipher` | `personaspeak-data/.../data/KeystoreSecretCipher.kt` | AES-GCM via AndroidKeyStore; fast-path guarded (Cassie's round-2 fix at `e37f5b2`) |
| `KeystrengthPolicy` | `personaspeak-data/.../data/KeyStrengthPolicy.kt` | SDK-gated StrongBox requirement |
| `StoreLog` | `personaspeak-data/.../data/StoreLog.kt` | Structured event log — no plaintext secrets by construction |

### 1.3 Provider abstractions (`core-providers`)

| Symbol | Location | Role |
|---|---|---|
| `CompletionProvider` | `core-providers/.../providers/CompletionProvider.kt` | Interface — `id: String`, `displayName: String`, `rewrite(system, text, secret): String` |
| `FakeProvider` | `core-providers/.../providers/FakeProvider.kt` | Walking-skeleton (`id = "fake"`, no key, no network, 400 ms simulated latency) |

### 1.4 Settings layer (`personaspeak-ui/settings`)

| Symbol | Location | Role |
|---|---|---|
| `SettingsViewModel` | `personaspeak-ui/settings/SettingsViewModel.kt` | Owns `SettingsState` flow, consumes `PersonaRepository`, `PersonaSpeakSessionState` |
| `SettingsState` | `personaspeak-ui/settings/SettingsState.kt` | UI state container — slice 2 adds provider status field |
| `SettingsDestination` | `personaspeak-ui/settings/SettingsDestination.kt` | Nav destinations — `Home`, `PersonaBrowser`, `PersonaDetail`; slice 2 adds `ProviderSetup` stub |
| `PersonaSpeakSessionState` | `personaspeak-ui/settings/PersonaSpeakSessionState.kt` | In-memory session state — **persists nothing to disk**; slice 1 didn't touch it |

### 1.5 Debug harness

`PersonaspeakStorageHarnessActivity` — debug-only, introduced in PR #92, resides in `keyboard/ime/app/src/debug`. Slice 2 extends it with adapter round-trip verification.

---

## 2. Adapter contract

### 2.1 Design invariants

1. **`core-*` stays pure.** `CompletionProvider` is in `core-providers`; it has zero Android imports and zero Keystore/DataStore dependencies. Adapters implementing it may live in `personaspeak-data` (if they need Keystore secret access) or a new `:personaspeak-providers` module (preferred — see §2.3 below).
2. **`personaspeak-ui` stays ASK-free.** The UI module must not import anything from the ASK tree or from `personaspeak-data`'s internal classes. It speaks only through `ProviderConfigStore`, `ProviderConfigSnapshot`, and `CompletionProvider`.
3. **`SettingsViewModel` receives a `CompletionProvider` by injection**, not by direct construction — enabling `FakeProvider` substitution in tests without touching real credentials.
4. **Adapter registration is static and exhaustive at compile time.** No runtime classpath discovery; the wiring happens in the DI layer (`personaspeak-data`'s module or the app's graph).

### 2.2 Adapter interface (new, slice 2)

A thin seam will be added in `personaspeak-data` (or a new `:personaspeak-providers` module — decision gated on ASK-tree impact assessment in §3):

```kotlin
// proposed — plan only, not production code
interface ProviderAdapter {
    /** Stable provider identifier; matches ProviderConfig.providerId in storage. */
    val providerId: String

    /** Human-readable name for the provider picker. */
    val displayName: String

    /**
     * Executes a rewrite with the credential retrieved from the store.
     * All network errors → Unavailable; auth failures → InvalidCredentials.
     * Never logs [secret] or any fragment of [text].
     */
    suspend fun rewrite(
        system: String,
        text: String,
        secret: SecretBytes,
    ): AdapterResult
}

sealed interface AdapterResult {
    data class Success(val rewritten: String) : AdapterResult
    data class NetworkFailure(val cause: Throwable) : AdapterResult
    data object AuthFailure : AdapterResult
}
```

`AdapterResult` maps to `StoreOutcome` states at the call site in `SettingsViewModel`:
- `Success` → no store change
- `NetworkFailure` → surface as `StoreOutcome.Unavailable(StoreFailure.IO_ERROR)` in the UI state (no wipe)
- `AuthFailure` → surface as `StoreOutcome.InvalidCredentials` in the UI state (no automatic wipe at this layer — wipe is the store's job on load, not the adapter's job on a request)

### 2.3 Module placement decision

Options (choice gated on ASK-tree assessment per §3):

| Option | Module | Pros | Cons |
|---|---|---|---|
| A | Add adapter classes to `:personaspeak-data` | No new module rent | Mixes network with storage |
| B | New `:personaspeak-providers` Gradle module | Clean separation, testable in isolation | One new `debugImplementation` + `implementation` entry |

**Default recommendation: Option B**, following the same philosophy as the existing `core-providers`/`personaspeak-data` split. The module holds adapter implementations and wires `ProviderAdapter` → `CompletionProvider`. Rent impact: one new module entry in `settings.gradle.kts`, one `implementation(project(":personaspeak-providers"))` in the app `build.gradle`. No upstream ASK-tree modifications.

---

## 3. ASK-tree assessment and upstream-rent ledger

- **No modifications to AnySoftKeyboard core** are required for this slice.
- **Upstream-rent delta (planned):**
  - If Option B: one new line in `android/settings.gradle.kts` (`include(":personaspeak-providers")`)
  - One `implementation(project(":personaspeak-providers"))` in `android/keyboard/ime/app/build.gradle`
  - Zero changes to `UPSTREAM-MODIFIED.md` (no ASK source files touched)
- Ledger rule from PR #91 plan: all modifications tracked; this slice adds ≤ 2 entries.

---

## 4. Per-candidate adapter plan

This slice implements **one** adapter (the candidate is the primary BYOK provider). The exact provider identity is not named in the plan document per #89 sequencing (Seraph to confirm in review if a specific target must be named here or if the plan is candidate-agnostic).

For each candidate adapter:

### 4.1 Permitted persisted values (from #91 data classification, extended)

| Category | Permitted | Prohibited |
|---|---|---|
| Metadata | `providerId`, `configuredAtEpochMs`, `schemaVersion`, `generation` | Provider display name, URL, user-typed API key in plaintext |
| Credential | AES-GCM ciphertext of API key bytes only | Partial keys, key prefixes, prompt fragments, history |
| Network layer | No request/response body logged | URL parameters that encode credentials, bearer tokens in logs |

### 4.2 Network behavior

- HTTPS only; certificate validation not relaxed.
- No retry storms: `NetworkFailure` is returned immediately; retry policy is the caller's responsibility.
- Timeouts set at call site; `NetworkFailure` on timeout.
- No telemetry, no analytics callbacks, no third-party SDK beyond a plain HTTP client.
- **Egress proof required**: a test or build-time lint rule asserting that no class in `:personaspeak-providers` makes a network call except through the single approved call site.

### 4.3 Secret flow

```
AndroidKeyStore
    └─ KeystoreSecretCipher.decrypt(ciphertext: ByteArray) → SecretBytes
            │
            ▼
    ProviderAdapter.rewrite(system, text, secret: SecretBytes)
            │
            ▼
    HTTP client — secret injected as Bearer header only
    (header value never logged; request/response body never logged)
```

`SecretBytes` is `@JvmInline value class` — value does not survive serialization. It must not be placed in a Bundle, Intent extra, shared preference, or log statement.

### 4.4 Error taxonomy mapped to four states

| Source | Condition | StoreOutcome mapping |
|---|---|---|
| `KeystoreSecretCipher` | `CipherUnavailableException` on fast-path | `Unavailable(KEYSTORE_UNAVAILABLE)` — no mutation |
| `KeystoreSecretCipher` | Corrupt ciphertext → `null` on decrypt | `InvalidCredentials` — store clears artifacts |
| `ProviderAdapter.rewrite` | `NetworkFailure` | `Unavailable(IO_ERROR)` — no mutation, no wipe |
| `ProviderAdapter.rewrite` | `AuthFailure` | Surface `InvalidCredentials` in UI; **store does NOT auto-wipe on request failure** — wipe requires explicit user confirmation or re-load |
| `DataStoreProviderConfigStore.load` | meta null + live blob exists | `InvalidCredentials` + wipe (existing behavior) |

---

## 5. No-secret rules extended to the network layer

Building on PR #91 rules (carried from slice 1):

1. `StoreLog` codes only — no plaintext in any log call anywhere in the adapter.
2. HTTP request headers containing credentials must not be logged by any interceptor.
3. HTTP response bodies must not be logged at any level (may contain credential echo or user content).
4. Test fixtures must not contain real API keys. Fixture secrets are syntactically valid random bytes only.
5. **Build-time check**: a lint rule or `grep`-based CI step asserts no `Log.d/v/i/w/e` call in `:personaspeak-providers` contains `secret`, `key`, `bearer`, or `authorization` (case-insensitive).

---

## 6. Truthful runtime state model

The four states already exist in `StoreOutcome`. This slice connects them to the UI:

### 6.1 State machine

```
             ┌────────────────────────────────────────────────────┐
             │                 SettingsViewModel                   │
             │                                                     │
  start ──→  Unconfigured ──→ [user enters key] ──→ Saving ──→ Configured
                                                                    │
                         ┌──────────────────────────────────────────┤
                         │                                          │
                         ▼                                          ▼
                   Unavailable ←── transient fault          [user clears]
                   (no wipe)                                        │
                         │                                          │
                         ▼                                          ▼
                   [retry / dismiss]                         Unconfigured
                         │
                         ▼
                  InvalidCredentials ←── unrecoverable (load-time wipe already done)
                  (user must re-enter key)
```

### 6.2 `SettingsState` additions (slice 2)

```kotlin
// plan only
data class SettingsState(
    // ... existing fields unchanged ...
    val providerStatus: ProviderStatus = ProviderStatus.Unconfigured,
    val isSavingProvider: Boolean = false,
)

sealed interface ProviderStatus {
    data object Unconfigured : ProviderStatus
    data class Configured(val providerId: String, val configuredAtEpochMs: Long) : ProviderStatus
    data class Unavailable(val failure: StoreFailure) : ProviderStatus
    data object InvalidCredentials : ProviderStatus
}
```

`ProviderStatus` maps 1:1 from `StoreOutcome`; it is a UI-layer type so the UI module does not import `StoreOutcome` directly (which lives in `personaspeak-ui/brain`, same module — actually this is fine; direct use is acceptable).

### 6.3 `SettingsViewModel` additions

- On init: call `providerConfigStore.load()`, map to `ProviderStatus`, set in state.
- `saveProviderKey(providerId: String, keyBytes: ByteArray)`: wraps in `SecretBytes`, calls `providerConfigStore.save(...)`, sets `isSavingProvider = true` while in flight.
- `clearProvider()`: calls `providerConfigStore.clear()` (if such a method exists; otherwise calls `save()` with a tombstone — to be decided based on the `ProviderConfigStore` port signature survey).
- All state transitions are pure — no side-effecting code in the `update { }` lambda.

---

## 7. Default-disabled mechanism

- The `ProviderAdapter` registry in the DI graph is populated **without** wiring it to the active `CompletionProvider` used by the rewrite path.
- The active provider selection remains `FakeProvider` at merge. The wire-up of a real adapter to the active selection is explicitly gated on this slice's approval plus the slice-3 API 26/27 device verification.
- No feature flag system is introduced (over-engineering for this scope). The disabling is structural: `FakeProvider` is the default, adapters are registered but not selected.
- **Non-goal**: runtime provider switching via UI. That is M5 / settings graph work.

---

## 8. Verification ladder

### 8.1 JVM unit tests (`:personaspeak-providers` or `:personaspeak-data`)

| Test | What it proves |
|---|---|
| `AdapterNetworkFailureTest` | `NetworkFailure` → `Unavailable` mapping; no mutation of store |
| `AdapterAuthFailureTest` | `AuthFailure` → `InvalidCredentials` UI state; store NOT wiped by adapter |
| `AdapterSecretFlowTest` | Secret injected into header; no log calls in adapter code path |
| `AdapterNoEgressTest` | Adapter class does not resolve a real hostname in unit context (mock `HttpClient`) |
| `SettingsViewModelProviderStatusTest` | State machine transitions: `Unconfigured → Configured → Unavailable → Unconfigured` |

### 8.2 Robolectric (`personaspeak-data`)

- Round-trip: `save()` with a known `SecretBytes` → `load()` → `Configured` with matching `providerId`.
- Unavailable path: mock `KeystoreSecretCipher` throwing `CipherUnavailableException` → `Unavailable`, no artifact mutation.
- `InvalidCredentials` path: corrupt ciphertext → store clears → `InvalidCredentials`.

### 8.3 Disposable-device verification (required before merge)

- Adapter round-trip with a real Keystore key and a real (sandboxed) network endpoint.
- Confirm no secret appears in `logcat` at any log level (adb logcat run during save/load/rewrite).
- Confirm `Unavailable` is returned when airplane mode is active.
- Confirm `InvalidCredentials` is returned when a deliberately corrupt key is injected.

All device receipts must be linked in the implementation PR body.

---

## 9. Data classification — prohibited-category regression

Extension of the PR #91 classification, scoped to the network layer:

| Category | Stored locally | Sent over network | Logged |
|---|---|---|---|
| API key (plaintext) | ❌ | ❌ | ❌ |
| API key (AES-GCM ciphertext) | ✅ (liveBlob only) | ❌ | ❌ |
| Draft / prompt text | ❌ | ❌ | ❌ |
| Rewrite result text | ❌ | ❌ | ❌ |
| Provider ID (opaque string) | ✅ | ❌ | ✅ (event code only) |
| `configuredAtEpochMs` | ✅ | ❌ | ❌ |
| Request URL | ❌ | ✅ (HTTPS only) | ❌ |
| Request body | ❌ | ✅ (system + text, no key) | ❌ |
| Response body | ❌ | received only | ❌ |

---

## 10. Security review checklist (per provider)

- [ ] API key is extracted from `SecretBytes` at the call site only; never stored in a local variable that survives the network call scope.
- [ ] HTTP client intercept chain reviewed: no logging interceptor for headers or bodies in non-debug builds.
- [ ] TLS: no custom `TrustManager`, no hostname verifier override.
- [ ] Redirect policy: follow HTTPS→HTTPS only; abort on HTTP downgrade.
- [ ] Timeout: connect + read timeout ≤ 30 s; no infinite wait.
- [ ] Error message from HTTP 4xx/5xx response body is NOT surfaced in logs (may contain user context echoed by some APIs).
- [ ] No third-party analytics, crash, or telemetry SDK introduced.
- [ ] `SecretBytes` value does not appear in any `toString()`, `equals()`, `hashCode()` that risks logcat emission.

---

## 11. Non-goals (explicit)

- No UI for entering or managing the provider key (M5 onboarding/settings graph).
- No provider switching in the rewrite path (structural default `FakeProvider` until slice 3).
- No marketplace or multi-provider registry beyond the static compile-time list.
- No persistence of text-bearing rewrite data.
- No weakening of M2 verification or the upstream-modification ledger.
- No onboarding screens.
- API 26/27 legacy-regime backup device pass (carried from slice 1, belongs to slice 3).

---

## 12. Rollback / cleanup path

1. The adapter module (`:personaspeak-providers` or additions to `:personaspeak-data`) is a pure addition; removing it requires deleting the module directory and removing its entry from `settings.gradle.kts` and the app `build.gradle`. No existing files are modified in a way that is hard to revert.
2. The `SettingsState` and `SettingsViewModel` additions are additive. Rollback: delete the new fields and the new `ProviderStatus` type; restore the original `SettingsState` class. Compilation will flag all callers.
3. No migration needed for `DataStoreMetaStore` because the slice does not change the DataStore schema.

---

## 13. Open questions for reviewers

1. **Provider candidate identity**: should this plan name a specific provider (e.g. OpenAI, Anthropic, OpenRouter) or remain candidate-agnostic? If agnostic, implementation will pick the simplest BYOK REST provider to minimize adapter surface area.
2. **Module boundary**: confirm Option B (`:personaspeak-providers`) is preferred over adding to `:personaspeak-data`. Seraph to arbitrate if Cassie and Sigrid disagree.
3. **`ProviderConfigStore.clear()` method**: the port as observed does not surface a `clear()` method explicitly in the truncated view. Implementation will add it if missing. Reviewers: confirm the port should expose `clear()` or if clearing is modelled as `save()` with a null secret.
4. **`AuthFailure` wipe policy**: plan says adapter does NOT auto-wipe on a single request failure. Reviewers: confirm this is correct (user must explicitly clear or the next `load()` will re-attempt).
