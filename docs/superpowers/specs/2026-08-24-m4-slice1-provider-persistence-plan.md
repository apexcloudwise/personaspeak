# M4 Slice 1 design: secure provider-configuration persistence foundation

**Status:** Draft plan for review — no production code in this PR (issue #90).
**Baseline:** main `5760ee2` (PR #88). **Tracker:** #89 (M4 master), #90 (this slice).

## Objective

Make the Settings THE BRAIN surface real enough to persist **provider
configuration and credentials** securely, package-scoped, while persisting
**zero** user text or rewrite artifacts. This slice delivers the storage,
Keystore protection, backup posture, and a truthful runtime state model — not
live cloud providers (M4 later slices), onboarding (M5), or usage counters
(separately approved design per UX spec §5).

## Current-state survey (facts from the baseline)

- **Provider abstraction** — `android/core-providers/src/main/kotlin/biz/pixelperfectstudios/personaspeak/providers/CompletionProvider.kt`
  defines `id`, `displayName`, `suspend rewrite(system, text): Result<String>`.
  Only implementation is `FakeProvider.kt` (`id = "fake"`). `core-providers`
  is pure Kotlin + coroutines; no Android imports; no config/settings types yet.
- **Settings surface** — `android/personaspeak-ui/.../ui/settings/`:
  `SettingsHomeScreen.kt` shows an "AI Provider" row hardcoded to
  `"FakeProvider (In-Memory Baseline)"` (line ~152) and a disabled-but-honest
  "Cloud Providers & API Keys" row whose explanation names Milestone 4
  (line ~162). `SettingsViewModel.kt` persists persona/mood selections into
  `PersonaSpeakSessionState.instance` — an in-memory singleton that survives
  only the process (`PersonaSpeakSessionState.kt:10-31`). Nothing touches disk.
- **Existing persistence in the tree** — all inherited ASK machinery
  (`ime/prefs/RxSharedPrefs.java`, `DirectBootAwareSharedPreferences`,
  `AddOnsFactory`), none of it ours, all of it plain `SharedPreferences`.
  No DataStore, Room, security-crypto, or EncryptedSharedPreferences anywhere.
- **Backup posture** — `android/keyboard/ime/app/src/main/AndroidManifest.xml:33`
  sets `android:allowBackup="true"`, plus `fullBackupOnly="true"` and
  `restoreAnyVersion="true"` (~lines 30-36). No `dataExtractionRules`, no
  `fullBackupContent`, no backup XML files exist. This is an **upstream ASK
  file** — any edit costs a `android/keyboard/UPSTREAM-MODIFIED.md` entry.
- **App identity** — single APK built from `ime/app`; minSdk 26 (per
  UPSTREAM-MODIFIED ledger note), so both the legacy
  `fullBackupContent` (<API 31) and `dataExtractionRules` (API 31+) regimes apply.
- **Governing constraints** — AGENTS.md module law (`core-*` stay pure;
  platform seams are ports in our modules with adapters in the Android layer);
  ADR-0005 (default-private; egress off and proven off; disclosure surfaces
  audited); UX spec §5 (THE BRAIN = provider + keys; usage counters need their
  own approval); "storing anything a user typed" is a listed firing offense.

## Design

### Ownership boundary and dependency direction

One new module keeps every Android-storage fact out of the pure modules and
out of the vendored tree except one manifest seam:

```
core-personas (pure)          core-providers (pure)
        ▲                            ▲
        └──────► personaspeak-ui ◄───┘        personaspeak-data (NEW, Android lib)
                    │   consumes port                ▲ implements port
                    └────────────────────────────────┘
                                     ▲
                              ime/app (APK; wiring + one manifest edit)
```

- **Port (interface)** lives in `personaspeak-ui` next to its consumer:
  - `ui/brain/ProviderConfig.kt` — pure data: `providerId: String`,
    `configuredAtEpochMs: Long?`, `credential: StoredCredential?` where
    `StoredCredential` is an opaque wrapper around an already-decrypted
    in-memory secret (`@JvmInline value class SecretBytes(val value: ByteArray)`),
    never `Parcelable`, never in any StateFlow that outlives use.
  - `ui/brain/ProviderConfigStore.kt` — suspend API:
    `load(): ProviderConfigSnapshot`, `save(config, secret)`, `clear()`;
    results modeled as sealed `StoreOutcome` (see state model below).
- **Adapter (implementation)** is the new module `android/personaspeak-data`:
  - `DataStoreProviderConfigStore` — Jetpack **Preferences DataStore**
    (file `personaspeak_provider_config.preferences_pb`) for non-secret
    config; **no** plaintext secret ever enters DataStore.
  - `KeystoreSecretCipher` — AndroidKeyStore AES-256-GCM key
    (`StrongBoxUnavailableFallback` semantics: `setIsStrongBoxBacked(true)`
    attempted, graceful fallback), random IV per write, IV‖ciphertext stored
    as base64 in a **separate** file `files/personaspeak_secret.bin`, which is
    the backup-excluded artifact.
  - Rationale for hand-rolled cipher over `security-crypto`/
    `EncryptedSharedPreferences`: androidx.security-crypto is in maintenance
    (deprecation announced 2025) and pulls Jetpack Tink; ~60 lines of
    KeyStore + AES-GCM is auditable in review, matches the AGENTS.md rule
    against dependencies where 30 lines of code would do, and gives us exact
    control over what bytes hit disk.

### Exact files to add or modify

| File | Action | Notes |
|---|---|---|
| `android/settings.gradle.kts` | modify (+1 line) | include `:personaspeak-data` |
| `android/personaspeak-data/build.gradle.kts` | add | Android lib; deps: `datastore-preferences`, `kotlinx-coroutines-android`; test deps: junit, robolectric, coroutines-test |
| `android/personaspeak-data/src/main/.../DataStoreProviderConfigStore.kt` | add | store impl; logs outcome codes only |
| `android/personaspeak-data/src/main/.../KeystoreSecretCipher.kt` | add | KeyStore AES-GCM; key alias `personaspeak_provider_credential_v1` |
| `android/personaspeak-data/src/test/...` | add | unit + Robolectric tests (below) |
| `android/personaspeak-ui/build.gradle.kts` | modify | no new deps; ui must not depend on `-data` (dependency inversion: app wires) |
| `android/personaspeak-ui/src/main/.../ui/brain/ProviderConfig.kt` | add | port data types |
| `android/personaspeak-ui/src/main/.../ui/brain/ProviderConfigStore.kt` | add | port interface |
| `android/personaspeak-ui/src/main/.../ui/settings/SettingsHomeScreen.kt` | modify | AI Provider row renders real state (below) |
| `android/personaspeak-ui/src/main/.../ui/settings/SettingsViewModel.kt` | modify | expose `brainState: StateFlow<BrainUiState>` |
| `android/personaspeak-ui/src/test/.../ui/settings/SettingsViewModelTest.kt` | extend | state-machine regressions with fake store |
| `android/keyboard/ime/app/src/main/AndroidManifest.xml` | modify (**rent**) | add `android:dataExtractionRules="@xml/personaspeak_data_extraction_rules"` and `android:fullBackupContent="@xml/personaspeak_full_backup_content"`; leave `allowBackup="true"` untouched (ASK's own settings behavior is ADR-0005 audit scope, not this slice) |
| `android/keyboard/ime/app/src/main/res/xml/personaspeak_data_extraction_rules.xml` | add | `<cloud-backup>`, `<device-transfer>`, `<backup-in-cloud>`: exclude `personaspeak_secret.bin` and the datastore file path |
| `android/keyboard/ime/app/src/main/res/xml/personaspeak_full_backup_content.xml` | add | legacy regime mirror: exclude the same two paths |
| `android/keyboard/UPSTREAM-MODIFIED.md` | append 1 line | ledger entry for the manifest change |
| `PATCHNOTES.md` | append 1 line | house rule |

No ASK source file other than the manifest and the two new XML resources is
touched. `core-personas` untouched entirely; `core-providers` untouched.

### Data classification

**May be persisted (exhaustive list):**

1. `providerId` (e.g. `"fake"`, later `"gemini"`) — non-secret identifier.
2. Configuration timestamp.
3. Config-schema version integer (migration hook).
4. The **encrypted** credential blob (ciphertext + IV) — never plaintext,
   never in DataStore, always in the excluded file.

**Must never be persisted (hard boundaries, regression-tested by string
scanning the store's write paths):** drafts, prompts, candidate rewrites,
results, history, persona display text, mood/session state beyond IDs, usage
counters, any log line containing key material. Session rewrite flow stays
exactly as today: request-scoped transient memory only.

### Keystore design and failure handling

- Key generation: AES256-GCM, `setUserAuthenticationRequired(false)`
  (a keyboard that demands biometrics mid-sentence is a usability bug, not a
  security feature — the threat model is backup exfiltration and other-app
  reads, not device theft, which FDE covers).
- Failure taxonomy mapped to outcomes:
  - KeyStore inaccessible / key deleted (some OEM "security cleanup" tools do
    this): `StoreOutcome.Unavailable` → UI says configuration exists but
    cannot be read; offer re-entry. Never silently fabricate defaults.
  - GCM AEADBadTagException (corrupt/tampered ciphertext):
    `StoreOutcome.InvalidCredentials` → UI says credentials are unreadable and
    must be re-entered; auto-delete the blob (fail closed, don't keep garbage).
  - Disk I/O failure: `Unavailable` (transient) — retry once, then report.
- Logging rule enforced by test: `KeystoreSecretCipher` and
  `DataStoreProviderConfigStore` log only fixed outcome-code strings; a unit
  test asserts no log call site can interpolate secret/config values
  (compile-time shape: logger takes enum, not String).

### Truthful runtime/UI state model

Sealed `BrainUiState` in `personaspeak-ui`, rendered by the THE BRAIN rows:

| State | Meaning | UI copy (honest) |
|---|---|---|
| `Unconfigured` | no stored config | current copy: FakeProvider active, cloud arrives in M4 |
| `Configured(providerId)` | valid config + readable credential | "Gemini configured · key stored in device Keystore" |
| `Unavailable(reason)` | storage/Keystore broken | "Secure storage is unavailable on this device; settings were not changed" |
| `InvalidCredentials` | decryption failed, blob cleared | "Stored key could not be read and was removed. Enter it again." |

No state claims network reachability or account validity — this slice makes
**zero** network calls; `rewrite()` continues to route to `FakeProvider`
until a later M4 slice lands real providers *and* their routing.

### Verification plan

1. **Unit (JVM, `personaspeak-data`):** store contract tests against an
   injected cipher seam; corruption injection (flip ciphertext byte) →
   `InvalidCredentials`; prohibited-content regression: after `save()` with
   sample secret, raw file bytes asserted to contain zero occurrences of the
   plaintext.
2. **Unit (`personaspeak-ui`):** `SettingsViewModelTest` extensions pinning
   all four `BrainUiState` transitions and the no-network property
   (fake store records zero calls during a full settings walkthrough).
3. **Robolectric:** real AndroidKeyStore is absent; Robolectric verifies
   DataStore round-trip and manifest merge carries both backup attributes.
4. **Disposable-AVD pass (slice gate, same protocol as M3 slices):**
   - `adb shell run-as biz.pixelperfectstudios.personaspeak cat files/personaspeak_secret.bin`
     → bytes shown to be ciphertext-only (entropy + absence of entered key substring).
   - **Backup-exclusion proof:** `adb shell bmgr backupnow <pkg>` then extract
     the backup stream and assert the excluded filenames are absent while a
     control file (config datastore) presence is checked per-regime expectation
     (API 31+: excluded; legacy regime: same exclusions via fullBackupContent).
   - Process-death honesty check: force-stop, relaunch → `Configured` state
     restored; session persona/mood still session-scoped (unchanged behavior).
   - Teardown hygiene identical to M3 evidence passes.

### Non-goals (explicit)

- Real cloud providers, HTTP clients, BYOK flows, key validation against
  providers — later M4 slices, each separately reviewed.
- Usage counters (UX §5 requires their own approved persistence design).
- Onboarding, TYPING/APPEARANCE/PRIVACY groups (M5).
- Changes to ASK's inherited SharedPreferences/dictionary/learned-words
  storage or its backup inclusion — owned by the ADR-0005 privacy inventory.
- Any weakening of M2 verification, the evidence branch, or the ledger.

### Rollback / cleanup

The slice is additive: delete the `personaspeak-data` module, the four new
`ui/brain`/XML files, revert the two-line manifest attribute pair, remove the
ledger and patch-note lines — `main` returns byte-identical to `5760ee2`
behavior (session-only state, FakeProvider everywhere). No data migration or
user-visible contract to unwind; stored artifacts die with uninstall.
