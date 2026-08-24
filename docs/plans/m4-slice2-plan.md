# M4 slice 2 — implementation plan: provider adapter(s) and truthful runtime states

**Parent issue:** #93  
**Plan baseline:** PR #92, squash `bda68f08b92e159f7bf87ed84aee759525166ae4`  
**Plan precedent:** PR #91 (`be0e563`)  
**Reviewer assignment:** Seraph, Cassie, Sigrid (same gate as slice 1 — plan reviewed and approved before any implementation)

**Revision r4** — full reviewer convergence (Seraph A4 comment, Cassie r2 review, Sigrid r2 review):
- **A1** Save/clear edges in the state machine are harness/test-driven this slice only (no production UI entry point)
- **A2** `NetworkFailure` carries `NetworkErrorCode` (enum) instead of `Throwable`
- **A3** Drop `ProviderStatus` sealed interface; `SettingsState.providerOutcome: StoreOutcome` direct for store operations
- **A4 (Seraph/Cassie/Sigrid)** Resolve auth-state collision: `StoreOutcome` reflects store operations only (`InvalidCredentials` keeps its wipe postcondition inviolate); adapter request observations live in `SettingsState.lastRewriteResult: AdapterResult?` (cleared on success / config change). Store is NOT wiped on request failure.
- **Anthropic headers (Cassie)**: Concrete provider scheme is `x-api-key` + `anthropic-version: 2023-06-01` (not generic Bearer); §4.1, §4.3, §5 updated; secret byte zeroing in §10 checklist
- **Module arrows & ASK ledger (Sigrid)**: Explicit dependency graph (`:personaspeak-providers -> :core-providers`, `:personaspeak-providers -> :personaspeak-ui`, `:ime:app -> :personaspeak-providers`); `keyboard/ime/app/build.gradle` modification ledgered for `UPSTREAM-MODIFIED.md`
- **Egress & data classification (Sigrid)**: Egress strictly bound to `https://api.anthropic.com/v1/messages`; draft/prompt and response text prohibited from persistence, URLs, logs, telemetry, exceptions, fixtures
- **PATCHNOTES.md (Sigrid)**: Entry added for PR #94

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

A thin seam added to `:personaspeak-providers` (Option B — confirmed, §3):

```kotlin
// proposed — plan only, not production code

/** A2: code-based, not exception-based, so no stack-trace leaks into logs */
enum class NetworkErrorCode {
    TIMEOUT,
    IO_ERROR,
    HTTP_SERVER_ERROR,    // 5xx
    HTTP_CLIENT_ERROR,    // 4xx non-auth (e.g. 400 bad request)
}

interface ProviderAdapter {
    /** Stable provider identifier; matches ProviderConfig.providerId in storage. */
    val providerId: String

    /** Human-readable name for the provider picker. */
    val displayName: String

    /**
     * Executes a rewrite with the credential retrieved from the store.
     * All network errors → AdapterResult.NetworkFailure(code); auth failures → AdapterResult.AuthFailure.
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
    /** A2: carries a code, not a Throwable — no stack trace in the result type */
    data class NetworkFailure(val code: NetworkErrorCode) : AdapterResult
    data object AuthFailure : AdapterResult
}
```

### 2.3 Adapter observation vs. store outcome separation (A4 resolution)

Per Seraph's A4 ruling ([#94 comment](https://github.com/apexcloudwise/personaspeak/pull/94#issuecomment-5401673732)) endorsed by Cassie and Sigrid:

- **`StoreOutcome` remains untouched** as the 4-state domain type for storage/Keystore lifecycle alone (`Unconfigured`, `Configured`, `Unavailable(reasonCode)`, `InvalidCredentials`). `StoreOutcome.InvalidCredentials` retains its strict postcondition: *artifacts were wiped, caller must reconfigure from scratch*.
- **Adapter request execution is a separate observation**: surfaced in `SettingsState.lastRewriteResult: AdapterResult?`.
- **Call site mapping in `SettingsViewModel`**:
  - `AdapterResult.Success(text)` → set `lastRewriteResult = null`, `providerOutcome` remains `Configured`
  - `AdapterResult.NetworkFailure(code)` → set `lastRewriteResult = NetworkFailure(code)`, `providerOutcome` remains `Configured` (no store mutation)
  - `AdapterResult.AuthFailure` → set `lastRewriteResult = AuthFailure`, `providerOutcome` remains `Configured` (store is NOT wiped; UI renders "key rejected", not "invalid credentials")
- `lastRewriteResult` is cleared on successful rewrite, provider re-configuration, or explicit clear.

### 2.4 Module placement & concrete dependency graph

**Option B confirmed (Q2)** — new `:personaspeak-providers` Gradle module.

Concrete dependency arrows:
- `:personaspeak-providers` `implementation(project(":core-providers"))` (implements provider interfaces)
- `:personaspeak-providers` `implementation(project(":personaspeak-ui"))` (consumes `SecretBytes`)
- `:keyboard:ime:app` `implementation(project(":personaspeak-providers"))` (wires concrete provider in app DI graph)

Rent impact:
- One new entry in `android/settings.gradle.kts`: `include(":personaspeak-providers")`
- One new entry in `android/keyboard/ime/app/build.gradle`: `implementation(project(":personaspeak-providers"))` (ASK-owned file — requires `UPSTREAM-MODIFIED.md` entry, see §3)


---

## 3. ASK-tree assessment and upstream-rent ledger

- **ASK-tree modification: one file** — `keyboard/ime/app/build.gradle` is an ASK-owned file. Adding `implementation(project(":personaspeak-providers"))` to it must be ledgered in `UPSTREAM-MODIFIED.md`. **B1 corrected**: the plan previously stated "zero changes to UPSTREAM-MODIFIED.md" — that was wrong.
- **Upstream-rent delta (planned):**
  - One new line in `android/settings.gradle.kts` (`include(":personaspeak-providers")`) — new first-party module, not ASK-owned
  - One `implementation(project(":personaspeak-providers"))` in `android/keyboard/ime/app/build.gradle` — ASK-owned, ledger entry required
  - **`UPSTREAM-MODIFIED.md` entry to add** in the implementation PR: `- ime/app/build.gradle — added implementation(project(":personaspeak-providers")) to wire the PersonaSpeak provider module into the app dependency graph. No behavioral delta to ASK keyboard functionality.`
- Ledger rule from PR #91 plan: all modifications tracked; this slice adds 1 ASK-tree ledger entry.


---

## 4. Anthropic Messages API adapter plan

This slice implements **one** concrete adapter: **Anthropic Messages API** (Q1 resolved — matches desktop CLI reality). `providerId = "anthropic"`.

### 4.1 Permitted persisted values (from #91 data classification, extended)

| Category | Permitted | Prohibited |
|---|---|---|
| Metadata | `providerId`, `configuredAtEpochMs`, `schemaVersion`, `generation` | Provider display name, URL, user-typed API key in plaintext |
| Credential | AES-GCM ciphertext of API key bytes only | Partial keys, key prefixes, prompt fragments, history |
| Network layer | No request/response body logged | URL parameters that encode credentials, `x-api-key` or `authorization` headers in logs |

### 4.2 Network behavior

- HTTPS only; certificate validation not relaxed.
- Egress strictly bound to `https://api.anthropic.com/v1/messages` (endpoint verified at compile-time / test-gate).
- No retry storms: `NetworkFailure` is returned immediately; retry policy is the caller's responsibility.
- Timeouts set at call site (connect/read ≤ 30s); `NetworkFailure(TIMEOUT)` on timeout.
- No telemetry, no analytics callbacks, no third-party SDK beyond a plain HTTP client.
- **Egress proof required**: a test asserting that no class in `:personaspeak-providers` makes a network call except to `https://api.anthropic.com/v1/messages`.

### 4.3 Secret flow

```
AndroidKeyStore
    └─ KeystoreSecretCipher.decrypt(ciphertext: ByteArray) → SecretBytes
            │
            ▼
    ProviderAdapter.rewrite(system, text, secret: SecretBytes)
            │
            ▼
    HTTP client — secret injected as x-api-key header
    (with anthropic-version: 2023-06-01; headers and bodies never logged)
            │
            ▼
    SecretBytes underlying ByteArray zeroed in memory after request
```

`SecretBytes` is `@JvmInline value class` — value does not survive serialization. It must not be placed in a Bundle, Intent extra, shared preference, or log statement.

### 4.4 Error taxonomy mapped to store and observation states

| Source | Condition | State update |
|---|---|---|
| `KeystoreSecretCipher` | `CipherUnavailableException` on fast-path | `providerOutcome = StoreOutcome.Unavailable(KEYSTORE_UNAVAILABLE)` (no mutation) |
| `KeystoreSecretCipher` | Corrupt ciphertext → `null` on decrypt | `providerOutcome = StoreOutcome.InvalidCredentials` (store clears artifacts) |
| `DataStoreProviderConfigStore.load` | meta null + live blob exists | `providerOutcome = StoreOutcome.InvalidCredentials` (store clears artifacts) |
| `ProviderAdapter.rewrite` | `NetworkFailure(TIMEOUT)` or `NetworkFailure(IO_ERROR)` | `lastRewriteResult = NetworkFailure(...)` (`providerOutcome` remains `Configured`, no wipe) |
| `ProviderAdapter.rewrite` | `NetworkFailure(HTTP_SERVER_ERROR)` | `lastRewriteResult = NetworkFailure(HTTP_SERVER_ERROR)` (`providerOutcome` remains `Configured`, no wipe) |
| `ProviderAdapter.rewrite` | `AuthFailure` (HTTP 401/403) | `lastRewriteResult = AdapterResult.AuthFailure` (`providerOutcome` remains `Configured`; **store is NOT wiped**) |

---

## 5. No-secret rules extended to the network layer

Building on PR #91 rules (carried from slice 1):

1. `StoreLog` codes only — no plaintext in any log call anywhere in the adapter.
2. HTTP request headers containing credentials (`x-api-key`, `authorization`, etc.) must not be logged by any interceptor.
3. HTTP response bodies must not be logged at any level (may contain credential echo or user content).
4. Draft/prompt text and rewrite response text are strictly prohibited from persistence, URLs, logcat, telemetry, exception messages, and test fixtures.
5. Test fixtures must not contain real API keys. Fixture secrets are syntactically valid random bytes only.
6. **Build-time check**: a lint rule or `grep`-based CI step asserts no `Log.d/v/i/w/e` call in `:personaspeak-providers` contains `secret`, `key`, `x-api-key`, `bearer`, or `authorization` (case-insensitive).

---

## 6. Truthful runtime state model

Per A4, `StoreOutcome` (4 states) tracks storage lifecycle alone, while `lastRewriteResult` tracks request-time execution observations.

### 6.1 State machine

```
             ┌──────────────────────────────────────────────────────────────────┐
             │                         SettingsViewModel                        │
             │                                                                  │
  start ──→  Unconfigured ──→ [save, harness-driven] ──→ Saving ──→ Configured ──→ [rewrite]
                                                                        │               │
                          ┌─────────────────────────────────────────────┤               ├─ Success ──→ lastRewriteResult = null
                          │                                             │               ├─ NetworkFailure(code) ──→ lastRewriteResult = NetworkFailure(code)
                          ▼                                             ▼               └─ AuthFailure ──→ lastRewriteResult = AuthFailure
                    Unavailable ←── store fault                   [clear, harness]       (store intact; UI renders "key rejected")
                    (no wipe)                                           │
                          │                                             ▼
                          ▼                                       Unconfigured
                    [retry / dismiss]
                          │
                          ▼
                   InvalidCredentials ←── load-time only (artifacts already wiped)
                   (user must re-configure)
```

### 6.2 `SettingsState` additions (slice 2)

```kotlin
// plan only — r4, A3 + A4 applied
data class SettingsState(
    // ... existing fields unchanged ...
    val providerOutcome: StoreOutcome = StoreOutcome.Unconfigured,
    val lastRewriteResult: AdapterResult? = null,
    val isSavingProvider: Boolean = false,
)
```

### 6.3 `SettingsViewModel` additions

- On init: call `providerConfigStore.load()`, set `providerOutcome` from the returned `ProviderConfigSnapshot.outcome`, clear `lastRewriteResult = null`.
- `saveProviderKey(providerId: String, keyBytes: ByteArray)`: wraps in `SecretBytes`, calls `providerConfigStore.save(...)`, sets `isSavingProvider = true` while in flight. Clears `lastRewriteResult = null`. **A1: harness/test-driven only this slice.**
- `clearProvider()`: calls `providerConfigStore.clear()`, clears `lastRewriteResult = null`. **A1: harness/test-driven only this slice.**
- `onRewriteCompleted(result: AdapterResult)`: updates `lastRewriteResult = if (result is AdapterResult.Success) null else result`. `providerOutcome` is unchanged.
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
| `AdapterNetworkFailureTest` | `NetworkFailure(code)` → `lastRewriteResult = NetworkFailure(code)`; store and `providerOutcome` untouched |
| `AdapterAuthFailureTest` | `AuthFailure` → `lastRewriteResult = AuthFailure`; store is NOT wiped and `providerOutcome` remains `Configured` (A4) |
| `SettingsViewModelTruthfulnessTest` | `Configured` + `lastRewriteResult = AuthFailure` renders as "key rejected", never claims `InvalidCredentials` / store wipe |
| `AdapterSecretFlowTest` | Secret injected into `x-api-key` header (with `anthropic-version: 2023-06-01`); no log calls in adapter code path; secret bytes zeroed |
| `AdapterNetworkErrorCodeTest` | `NetworkFailure(TIMEOUT/IO_ERROR/HTTP_SERVER_ERROR/HTTP_CLIENT_ERROR)` carried as enum code; no `Throwable` escapes |
| `AdapterNoEgressTest` | Adapter class does not resolve a real hostname in unit context (mock `HttpClient`) |
| `AdapterEgressBindingTest` | Adapter enforces request destination is strictly `https://api.anthropic.com/v1/messages` |
| `SettingsViewModelStoreOutcomeTest` | State machine transitions using `StoreOutcome` for store operations: `Unconfigured → Configured → Unavailable → Unconfigured` |

### 8.2 Robolectric (`personaspeak-data`)

- Round-trip: `save()` with a known `SecretBytes` → `load()` → `Configured` with matching `providerId`.
- Unavailable path: mock `KeystoreSecretCipher` throwing `CipherUnavailableException` → `Unavailable`, no artifact mutation.
- `InvalidCredentials` path: corrupt ciphertext → store clears → `InvalidCredentials` (wipe postcondition held).

### 8.3 Disposable-device verification (required before merge)

- Adapter round-trip with a real Keystore key and a real (sandboxed) network endpoint.
- Confirm no secret (`x-api-key`, ciphertext, or prompt text) appears in `logcat` at any log level (adb logcat run during save/load/rewrite).
- Confirm `Unavailable` is returned when airplane mode is active.
- Confirm `InvalidCredentials` is returned when a deliberately corrupt key is injected into the keystore blob.

All device receipts must be linked in the implementation PR body.

---

## 9. Data classification — prohibited-category regression

Extension of the PR #91 classification, scoped to the network layer:

| Category | Stored locally | Sent over network | Logged |
|---|---|---|---|
| API key (plaintext) | ❌ | ❌ | ❌ |
| API key (AES-GCM ciphertext) | ✅ (liveBlob only) | ❌ | ❌ |
| Draft / prompt text | ❌ | ✅ (`https://api.anthropic.com/v1/messages` request body, user-initiated only) | ❌ |
| Rewrite result text | ❌ | received only (`api.anthropic.com` response body) | ❌ |
| Provider ID (opaque string) | ✅ | ❌ | ✅ (event code only) |
| `configuredAtEpochMs` | ✅ | ❌ | ❌ |
| Request URL | ❌ | ✅ (HTTPS, `https://api.anthropic.com/v1/messages` only) | ❌ |
| Request body | ❌ | ✅ (system prompt + draft text + model params; no credential) | ❌ |
| Response body | ❌ | received only | ❌ |
| `x-api-key` credential (in-flight header) | ❌ | ✅ (TLS only to `api.anthropic.com`, never logged) | ❌ |

> **Data privacy invariant**: draft/prompt text leaves the device in the request body, user-initiated, to the user's chosen provider (`https://api.anthropic.com/v1/messages`) only — this is the product's stated purpose and consistent with ADR-0005. It is strictly prohibited from local disk persistence, query parameters, logcat, telemetry, exception messages, and test fixtures.

---

## 10. Security review checklist (per provider)

- [ ] API key is extracted from `SecretBytes` at the call site only; never stored in a field that survives the network call scope.
- [ ] Underlying `ByteArray` in `SecretBytes` is zeroed in memory after single use.
- [ ] Injected as `x-api-key` header with `anthropic-version: 2023-06-01`; headers and bodies never logged.
- [ ] HTTP client intercept chain reviewed: no logging interceptor for headers or bodies in non-debug builds.
- [ ] TLS: no custom `TrustManager`, no hostname verifier override.
- [ ] Redirect policy: follow HTTPS→HTTPS only; abort on HTTP downgrade.
- [ ] Timeout: connect + read timeout ≤ 30 s; no infinite wait.
- [ ] Error message from HTTP 4xx/5xx response body is NOT surfaced in logs (may contain user context echoed by APIs).
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

1. The `:personaspeak-providers` module is a pure addition; removing it requires deleting the module directory and removing its entry from `android/settings.gradle.kts` and `android/keyboard/ime/app/build.gradle` (and reverting the `UPSTREAM-MODIFIED.md` entry).
2. The `SettingsState` and `SettingsViewModel` additions are additive. Rollback: delete `providerOutcome`, `lastRewriteResult`, and `isSavingProvider` fields from `SettingsState`; restore the original class. Compilation will flag all callers.
3. No migration needed for `DataStoreMetaStore` because the slice does not change the DataStore schema.

---

## 13. §13 rulings (resolved by Seraph at plan-gate review)

> See [#94 comment](https://github.com/apexcloudwise/personaspeak/pull/94#issuecomment-5401612001) and [A4 comment](https://github.com/apexcloudwise/personaspeak/pull/94#issuecomment-5401673732) for full rationale.

| Q | Question | Ruling |
|---|---|---|
| Q1 | Provider candidate | **Anthropic Messages API**; `providerId = "anthropic"`. Header scheme is `x-api-key` + `anthropic-version: 2023-06-01`. |
| Q2 | Module boundary | **Option B confirmed** — new `:personaspeak-providers` Gradle module. |
| Q3 | `clear()` on port | `clear()` **already exists** on `ProviderConfigStore`. No port change needed. |
| Q4 | AuthFailure wipe policy | **No auto-wipe on request-time auth failure confirmed.** Wipe is load-time only. **A4 resolution**: `StoreOutcome.InvalidCredentials` keeps its wipe postcondition inviolate (tracks store operations only); request observation lives in `SettingsState.lastRewriteResult: AdapterResult?`. |


