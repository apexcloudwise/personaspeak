# M4 Slice 1 design: secure provider-configuration persistence foundation

**Status:** Draft plan for review — no production code in this PR (issue #90).
**Baseline:** main `5760ee2` (PR #88). **Tracker:** #89 (M4 master), #90 (this slice).

## Objective

Make the Settings THE BRAIN surface real enough to persist **provider
configuration and credentials** securely, package-scoped, while persisting
**zero** user text or rewrite artifacts. This slice delivers the **storage
foundation only**: the store, the cipher, the backup posture, and their tests —
not live cloud providers (M4 later slices), not a configuration entry flow, not
onboarding (M5), not usage counters (separately approved design per UX spec §5).
See "Scope ruling" below: no product-reachable `Configured` state in this slice.

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
  - `KeystoreSecretCipher` — AndroidKeyStore AES-256-GCM key, random IV per
    write; IV‖generation‖ciphertext stored as base64 in a **separate** file
    `files/personaspeak_secret.bin`, which is a backup-excluded artifact.
    **StrongBox is SDK-gated:** on API ≥ 28, request
    `setIsStrongBoxBacked(true)` inside a try/catch for
    `StrongBoxUnavailableException` (and generic `ProviderException`), falling
    back to a plain TEE-backed key; on API 26/27 (`setIsStrongBoxBacked` does
    not exist there) go straight to the TEE key. The gate is a pure
    `KeyStrengthPolicy(minSdkInt)` function over an injected
    `KeyGenParameterSpec.Builder` seam so both branches are unit-tested
    without hardware: assert API 26/27 never see the StrongBox call, and
    API 28+ falls back cleanly when the exception fires.
  - Rationale for hand-rolled cipher over `security-crypto`/
    `EncryptedSharedPreferences`: androidx.security-crypto is in maintenance
    (deprecation announced 2025) and pulls Jetpack Tink; ~60 lines of
    KeyStore + AES-GCM is auditable in review, matches the AGENTS.md rule
    against dependencies where 30 lines of code would do, and gives us exact
    control over what bytes hit disk.

### Atomic two-artifact writes and crash recovery

The store owns two files: metadata (DataStore:
`personaspeak_provider_config.preferences_pb`) and ciphertext
(`files/personaspeak_secret.bin`). They can diverge under crash, restore, or
partial clear, so every artifact carries a **generation marker**: a random
UUID written into the DataStore payload *and* into the blob header
(`magic ‖ version ‖ generation-uuid ‖ IV ‖ ciphertext`).

**Save ordering (stage → commit → swap; never destroys the old credential):**

1. **Stage:** serialize blob with new generation UUID → write to
   `files/personaspeak_secret.bin.staging` → fsync. The live
   `personaspeak_secret.bin` is untouched, so a crash here costs nothing.
2. **Commit:** update the DataStore entry (provider id, timestamp, schema
   version, new generation UUID). DataStore writes are atomic internally.
3. **Swap:** atomic rename `personaspeak_secret.bin.staging` over
   `personaspeak_secret.bin`; delete any stale staging file on load.

The old working credential stays intact until the metadata that names the new
generation has committed — a mid-save crash can always be resolved to either
the old or the new state, never to nothing. `clear()` removes both files plus
metadata in the reverse order (meta first, then bytes).

**Load-time mismatch matrix (all fail-closed about trust, none destructive
of a recoverable state):**

| Observed state | Outcome | Recovery action |
|---|---|---|
| meta gen matches live blob | healthy | delete stale staging if present |
| meta = new gen, live blob = old gen, staging matches meta (crash between steps 2 and 3 of a re-save) | healthy (new state) | complete the swap: rename staging → live |
| meta present, live blob absent, staging matches meta (crash between steps 2 and 3 of a **first** save) | healthy (new state) | complete the swap: rename staging → live |
| meta = old gen, live blob = old gen, stray staging present (crash inside step 1) | healthy (old state) | delete orphan staging |
| meta absent, blob present | `InvalidCredentials` | delete blob + staging |
| meta present, blob absent or gen-mismatched, staging absent or also mismatched (restore/partial clear — nothing recoverable) | `InvalidCredentials` | clear meta, delete staging |
| both absent | `Unconfigured` | none |
| KeyStore/IO failure during load | `Unavailable` | retry once, then report |

Every row of this table gets a contract-test case that simulates the failure
by writing artifacts directly to the seam.

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
| `android/personaspeak-ui/src/main/.../ui/settings/SettingsHomeScreen.kt` | **not modified** | per scope ruling: disabled-but-honest copy stays; it remains true |
| `android/personaspeak-ui/src/main/.../ui/settings/SettingsViewModel.kt` | **not modified** | ViewModel wiring deferred to the slice-2 configuration-flow PR |
| `android/keyboard/ime/app/src/main/AndroidManifest.xml` | modify (**rent**) | add `android:dataExtractionRules="@xml/personaspeak_data_extraction_rules"` and `android:fullBackupContent="@xml/personaspeak_full_backup_content"`; leave `allowBackup="true"` untouched (ASK's own settings behavior is ADR-0005 audit scope, not this slice) |
| `android/keyboard/ime/app/src/main/res/xml/personaspeak_data_extraction_rules.xml` | add | API 31+ regime: exclude **both** artifacts from `<cloud-backup>` and `<device-transfer>` (sketch below; `backup-in-cloud` is not a valid child element and is not used) |
| `android/keyboard/ime/app/src/main/res/xml/personaspeak_full_backup_content.xml` | add | legacy (<API 31) regime mirror: exclude the same two paths |
| `android/keyboard/UPSTREAM-MODIFIED.md` | append 1 line | ledger entry for the manifest change |
| `PATCHNOTES.md` | append 1 line | house rule |

No ASK source file other than the manifest and the two new XML resources is
touched. `core-personas` untouched entirely; `core-providers` untouched.

### Backup policy — one rule per artifact, stated once

**Both artifacts are excluded from every backup/transfer regime** (cloud
backup, device transfer, legacy full backup) — as is the transient
`.staging` file, which holds the same kind of bytes mid-save. Rationale:
restoring the
ciphertext to another device is useless by construction — the AndroidKeyStore
key is non-exportable, so restored bytes decrypt to `AEADBadTagException` →
`InvalidCredentials` → auto-delete anyway. Excluding them up front avoids
shipping dead secrets into a cloud we don't control and keeps the privacy
story one sentence: "provider configuration never leaves your phone."

API 31+ (`personaspeak_data_extraction_rules.xml`) — valid children only:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="personaspeak_secret.bin" />
        <exclude domain="file" path="personaspeak_secret.bin.staging" />
        <exclude domain="file" path="datastore/personaspeak_provider_config.preferences_pb" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="personaspeak_secret.bin" />
        <exclude domain="file" path="personaspeak_secret.bin.staging" />
        <exclude domain="file" path="datastore/personaspeak_provider_config.preferences_pb" />
    </device-transfer>
</data-extraction-rules>
```

Legacy (<API 31, `personaspeak_full_backup_content.xml`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="file" path="personaspeak_secret.bin" />
    <exclude domain="file" path="personaspeak_secret.bin.staging" />
    <exclude domain="file" path="datastore/personaspeak_provider_config.preferences_pb" />
</full-backup-content>
```

ASK's own inherited settings/dictionaries keep upstream's current backup
behavior; changing that is ADR-0005 inventory scope, not this slice.

### Scope ruling: storage-foundation-only (no product-reachable Configured state)

With FakeProvider as the only provider and no key-entry flow defined in this
slice, a product-visible "Configured" state would be a lie with no way to
reach it honestly. **Chosen path:** slice 1 is storage-foundation-only.

- The THE BRAIN rows in `SettingsHomeScreen.kt` are **not modified** — they
  keep the current disabled-but-honest copy ("cloud providers and Keystore
  arrive in Milestone 4"), which stays true.
- The `BrainUiState` machine, port interface, and adapter land fully
  implemented but exercised **only by tests** driving the store directly.
  The ViewModel wiring is deliberately left for the slice-2 PR that adds the
  provider-neutral configuration flow, where the UI copy can finally name a
  real configured provider.
- Consequently there is **no process-death UI proof in this slice**; the
  equivalent guarantee is a contract test: save → new store instance over the
  same seam files → load returns identical config (persistence across
  process recreation), plus the mismatch-matrix rows above.

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

### Truthful runtime state model (adapter/test-scope this slice)

Sealed `StoreOutcome` / `BrainUiState` in `personaspeak-ui`, fully defined now
and consumed by the UI only from slice 2 onward:

| State | Meaning | Slice-2 UI copy direction (honest) |
|---|---|---|
| `Unconfigured` | no stored config | FakeProvider active, cloud arrives with configuration flow |
| `Configured(providerId)` | valid config + readable credential | names the provider; key stored in device Keystore |
| `Unavailable(reason)` | storage/Keystore broken | "Secure storage is unavailable on this device; settings were not changed" |
| `InvalidCredentials` | decryption/generation mismatch, artifacts cleared | "Stored key could not be read and was removed. Enter it again." |

No state claims network reachability or account validity — this slice makes
**zero** network calls; `rewrite()` continues to route to `FakeProvider`
until a later M4 slice lands real providers *and* their routing.

### Verification plan

1. **Unit (JVM, `personaspeak-data`):** store contract tests against an
   injected cipher + file seam; corruption injection (flip ciphertext byte) →
   `InvalidCredentials`; every mismatch-matrix row simulated by writing
   artifacts directly; prohibited-content regression: after `save()` with a
   sample secret, raw file bytes asserted to contain zero occurrences of the
   plaintext.
2. **Unit (`KeyStrengthPolicy`):** API 26/27 → no StrongBox request;
   API 28+ → StrongBox requested, `StrongBoxUnavailableException` → clean
   TEE fallback.
3. **Robolectric:** real AndroidKeyStore is absent; Robolectric verifies
   DataStore round-trip, atomic-rename save path, and that the merged app
   manifest carries both backup attributes pointing at the two rule files.
4. **Disposable-AVD pass (slice gate, same protocol as M3 slices):**
   - `adb shell run-as biz.pixelperfectstudios.personaspeak cat files/personaspeak_secret.bin`
     → bytes shown to be ciphertext-only (entropy + absence of entered key substring),
     driven by a test harness activity in the debug build, not product UI.
   - **Backup-exclusion proof, restore-based (stream-parsing is not reliably
     available on ordinary devices), with a positive control:** the test
     harness writes `files/personaspeak_backup_canary.txt` — deliberately
     *not* excluded — before the backup. Seed artifacts via the store →
     `adb shell bmgr backupnow <pkg>` → `pm clear` (or uninstall/reinstall) →
     `adb shell bmgr restore <token> <pkg>` → assert via `run-as ls` that
     (a) the canary **does** reappear — proving the backup/restore round-trip
     actually ran, so the negative assertion cannot pass vacuously — and
     (b) neither `personaspeak_secret.bin`, its `.staging` file, nor
     `datastore/personaspeak_provider_config.preferences_pb` reappears.
     Repeated on one API 26/27 emulator (legacy regime) and one API 31+
     emulator (extraction-rules regime).
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
