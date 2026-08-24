# M4 slice 3 — implementation and verification plan: device verification, audit receipts, and closeout

**Parent issue:** #96  
**Parent milestone:** #89  
**Plan baseline:** PR #95, squash `672a8088b90b84c8a2b53f65fe3684a0d927a4d5` (head `9c17845`)  
**Plan precedent:** PR #91 (`be0e563`), PR #94 (`a3f3119`)  
**Reviewer assignment:** Seraph (@seraph-pixelperfect), Cassie (@cassievale-pixelperfect), Sigrid (@sigrid-pixelperfect)  

---

## 0. Plan-only scope statement

This PR contains the **implementation and verification plan document only**. No production code modifications, no network configuration changes, no credential handling alterations, and no cloud provider enablements are introduced in this planning PR. The plan must receive independent exact-head approval from non-author reviewers before the slice-3 implementation and verification PR begins.

---

## 1. Seam survey & carried inputs baseline

### 1.1 Merged slice-1 baseline (`:personaspeak-data`, PR #92 at `bda68f0`)

| Component | Location | Role & Status |
|---|---|---|
| `ProviderConfigStore` | `personaspeak-ui/brain/ProviderConfigStore.kt` | Port defining `load()`, `save()`, `clear()`. |
| `StoreOutcome` | `personaspeak-ui/brain/ProviderConfig.kt` | 4-state domain model: `Unconfigured`, `Configured`, `Unavailable(reasonCode)`, `InvalidCredentials`. |
| `DataStoreProviderConfigStore` | `personaspeak-data/.../DataStoreProviderConfigStore.kt` | Stage/commit/swap protocol with UUID generation matching; 4-state recovery matrix. |
| `KeystoreSecretCipher` | `personaspeak-data/.../KeystoreSecretCipher.kt` | AES-256-GCM authenticated encryption backed by AndroidKeyStore. |
| Backup Rules | `personaspeak-ui/res/xml/` | `personaspeak_data_extraction_rules.xml` (API 31+) & `personaspeak_full_backup_content.xml` (< API 31). |
| Debug Harness | `ime/app/src/debug/.../PersonaspeakStorageHarnessActivity.kt` | Debug-only activity for automated store verification on device. |

### 1.2 Merged slice-2 baseline (`:personaspeak-providers`, PR #95 at `672a808`)

| Component | Location | Role & Status |
|---|---|---|
| `ProviderAdapter` | `personaspeak-providers/.../ProviderAdapter.kt` | Adapter interface consuming `SecretBytes` and returning `AdapterResult`. |
| `AnthropicMessagesAdapter` | `personaspeak-providers/.../AnthropicMessagesAdapter.kt` | Implements Anthropic Messages API (`https://api.anthropic.com/v1/messages`) with `x-api-key` + `anthropic-version: 2023-06-01`. |
| `NetworkErrorCode` | `personaspeak-ui/brain/ProviderConfig.kt` | Closed enum (`TIMEOUT`, `IO_ERROR`, `HTTP_SERVER_ERROR`, `HTTP_CLIENT_ERROR`) preventing raw `Throwable` leaks. |
| A4 State Separation | `personaspeak-ui/settings/SettingsViewModel.kt` | Request failures update `lastRewriteResult: AdapterResult?` without mutating storage or triggering invalid credential wipes. |
| No-Secret Verifier | `android/scripts/verify-no-secret-logging.sh` | Fail-closed CI script asserting zero secret logging in `:personaspeak-providers`. |
| Structural Fixtures | `personaspeak-providers/src/test/...` | Contentless synthetic test fixtures asserting protocol plumbing, not prose. |
| Structural Default-Disabled | App DI / Wiring | `FakeProvider` remains active default in rewrite coordinator; `AnthropicMessagesAdapter` registered but unselected at merge. |

### 1.3 Carried inputs for slice 3

1. **#90 (Slice 1 carry-forward):** API 26/27 legacy-regime behavioral backup-exclusion pass (`fullBackupContent`), previously blocked only by host SDK directory write-permissions during slice 1.
2. **#93 / PR #95 (Slice 2 carry-forward):** End-to-end disposable-device rewrite receipt exercising the real `extractTextFromResponse` parser.
3. **#93 (Review note):** Transient key-String clarification note for the §10 security checklist and adapter documentation.
4. **#89 (Milestone 4 parent):** Formal provider enablement decision, audit receipts sealing, and M4 closeout.

---

## 2. API 26/27 legacy-regime backup exclusion pass

### 2.1 Context & objective

Milestone 4 Slice 1 established and verified backup exclusion on modern Android (API 31+) using `dataExtractionRules`. For Android 8.0/8.1 (API 26/27), Android uses the legacy `fullBackupContent` regime specified in `personaspeak_full_backup_content.xml`:

```xml
<full-backup-content>
    <exclude domain="file" path="personaspeak_secret.bin" />
    <exclude domain="file" path="personaspeak_secret.bin.staging" />
    <exclude domain="file" path="datastore/personaspeak_provider_config.preferences_pb" />
</full-backup-content>
```

This pass executes the behavioral backup-and-restore cycle on an API 27 AVD using `bmgr` to prove that credential ciphertext, staging files, and metadata are excluded from legacy backups.

### 2.2 Prerequisites & test environment

- **AVD Target:** Android 8.1 (API 27), x86_64 or arm64 system image (`system-images;android-27;default;x86_64`).
- **APK Target:** `app-debug.apk` containing `PersonaspeakStorageHarnessActivity`.
- **Package Name:** `biz.pixelperfectstudios.personaspeak.debug` (or active debug package ID).
- **Required Host Tools:** `adb`, `emulator`.

### 2.3 Verification protocol

```bash
# 1. Boot API 27 AVD & install debug APK
adb wait-for-device
adb install -r android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk

# 2. Populate store via harness activity with test credential and config
adb shell am start -n biz.pixelperfectstudios.personaspeak.debug/biz.pixelperfectstudios.personaspeak.ui.debug.PersonaspeakStorageHarnessActivity \
    --es action save --es provider anthropic --es secret "test-key-material"

# 3. Create a positive-control file (MUST be backed up to validate bmgr operation)
adb shell "run-as biz.pixelperfectstudios.personaspeak.debug sh -c 'echo canary-data > files/backup_canary.txt'"

# 4. Verify pre-backup file existence on device
adb shell "run-as biz.pixelperfectstudios.personaspeak.debug ls -la files/ datastore/"
# Expect: backup_canary.txt, personaspeak_secret.bin, datastore/personaspeak_provider_config.preferences_pb present

# 5. Initialize bmgr transport and trigger backup
adb shell bmgr enable true
adb shell bmgr transport com.android.localtransport/.LocalTransport
adb shell bmgr backupnow biz.pixelperfectstudios.personaspeak.debug

# 6. Clear package data to simulate clean restore target
adb shell pm clear biz.pixelperfectstudios.personaspeak.debug

# 7. Execute restore via bmgr
adb shell bmgr restore 1 biz.pixelperfectstudios.personaspeak.debug

# 8. Post-restore storage inspection
adb shell "run-as biz.pixelperfectstudios.personaspeak.debug ls -la files/ datastore/"
```

### 2.4 Expected acceptance criteria

1. **Positive control preserved:** `files/backup_canary.txt` is successfully restored and readable.
2. **Excluded files absent:** `files/personaspeak_secret.bin`, `files/personaspeak_secret.bin.staging`, and `datastore/personaspeak_provider_config.preferences_pb` DO NOT EXIST in the restored package directory.
3. **Honest unconfigured state:** Launching the storage harness after restore returns `StoreOutcome.Unconfigured` with zero decryption errors or crashes.
4. **Receipt recorded:** Saved to `docs/evidence/milestone-4/backup-api27-receipt.json` with command logs and SHA-256 digests.

---

## 3. Disposable-device end-to-end adapter & parser journey

### 3.1 Objective

Prove that `AnthropicMessagesAdapter` and its internal `extractTextFromResponse` JSON parser execute correctly under the Android ART runtime on a real/emulated device, correctly parsing structured LLM responses while strictly upholding data privacy invariants.

### 3.2 Fixture, redaction, & privacy rules

- **Strict Redaction:** No production API keys, user-derived text, drafts, or sensitive prompts may appear in scripts, logs, test fixtures, or receipts.
- **Fixture Design:** Uses a dedicated ephemeral test credential and contentless/minimal prompt token (e.g. `"ping"`) with an expected structural response shape.
- **Egress Binding:** Requests route strictly to `https://api.anthropic.com/v1/messages` with TLS 1.2+.
- **Immediate Cleanup:** The test credential is wiped via harness `clear()` and package data is wiped immediately following the run.

### 3.3 Execution workflow

1. **Launch Test Runner / Harness:** Exercise `AnthropicMessagesAdapter.rewrite(system, text, secret)` on the disposable device.
2. **Parser Validation:** Verify that `extractTextFromResponse` correctly extracts the text payload from Anthropic's response JSON schema (`{"content":[{"type":"text","text":"..."}]}`) and returns `AdapterResult.Success`.
3. **Memory Zeroing:** Verify via step execution that `secret.value.fill(0)` executes in the `finally` block.
4. **Logcat Audit:** Concurrently stream `adb logcat` during the entire execution; assert ZERO occurrences of:
   - Plaintext API key or key fragments
   - Request or response JSON bodies
   - Header values (`x-api-key`)
   - Draft or rewrite text
5. **Durable Receipt:** Generate `docs/evidence/milestone-4/adapter-parser-receipt.json`.

---

## 4. Storage and egress audit receipts

### 4.1 Package-private storage audit

Perform an exhaustive scan of the app's private sandbox directory (`/data/data/biz.pixelperfectstudios.personaspeak.debug/`):

| Path / Domain | Expected State | Security / Privacy Invariant |
|---|---|---|
| `files/personaspeak_secret.bin` | Present only when configured; permissions `0600` | AES-GCM ciphertext only; no plaintext key string. |
| `files/personaspeak_secret.bin.staging` | Absent after commit; permissions `0600` | Atomic swap artifact; cleaned up immediately. |
| `datastore/personaspeak_provider_config.preferences_pb` | Present when configured; permissions `0600` | Non-secret metadata only (`providerId`, timestamp, schema version). |
| `shared_prefs/` | Standard ASK prefs only | No PersonaSpeak prompts, candidates, or credentials. |
| `databases/` | Upstream ASK dictionary DBs only | Zero rewrite text, prompts, or history tables. |
| `cache/`, `no_backup/` | Empty or transient cache only | No cached network bodies or responses. |

**Binary / String Scan:** Run a recursive byte scanner across the entire package directory to assert zero plaintext occurrences of configured test keys or prompt strings.

### 4.2 Egress & network transport audit

- **Transport Inspection:** Verify all outgoing network traffic from `:personaspeak-providers` is HTTPS on port 443 strictly bound to `https://api.anthropic.com/v1/messages`.
- **Header Verification:** Redacted trace confirms headers sent are exactly:
  - `x-api-key: [REDACTED]`
  - `anthropic-version: 2023-06-01`
  - `content-type: application/json; charset=utf-8`
- **Zero Third-Party Egress:** Audit verifies no SDK or background daemon contacts any third-party domain (no telemetry, crash reporting, or analytics).
- **Receipt Output:** Recorded in `docs/evidence/milestone-4/storage-egress-audit-receipt.json`.

---

## 5. Key-String security checklist resolution

### 5.1 Technical analysis

In PR #95 review, Cassie noted:
> `AnthropicMessagesAdapter.rewrite` converts `secret.value` to a `String` (`val apiKeyString = String(secret.value, StandardCharsets.UTF_8)`) to populate `HttpURLConnection`'s request headers before `secret.value.fill(0)` runs in the `finally` block. JVM `String` objects are immutable and cannot be explicitly zeroed; their lifecycle is governed by GC and internal connection pooling.

### 5.2 Resolution & documentation refinement

1. **Adapter Source Comment Refinement:** Update `AnthropicMessagesAdapter.kt` comment to be technically precise:
   ```kotlin
   // Defense-in-depth: zeroes the mutable ByteArray in SecretBytes.
   // The transient String copy required by HttpURLConnection's header API is immutable
   // and reclaimed by JVM garbage collection.
   secret.value.fill(0)
   ```
2. **§10 Security Review Checklist Sign-Off:**
   - [x] **Secret Extraction Scope:** Secret is extracted as a transient `String` solely at the HTTP connection boundary to satisfy `HttpURLConnection.setRequestProperty(String, String)`.
   - [x] **Ephemeral Lifetime:** `apiKeyString` is a local stack variable within `withContext(Dispatchers.IO)` and is never stored in any field, object instance, Bundle, or collection that survives the network call scope.
   - [x] **Mutable Buffer Zeroing:** `SecretBytes` underlying `ByteArray` is explicitly zeroed in memory via `secret.value.fill(0)` in the `finally` block on all execution paths (success, error, timeout).
   - [x] **No Leaks:** Verified by `NoSecretLoggingTest` and `verify-no-secret-logging.sh` that no `String` or header value is logged.

---

## 6. Provider enablement decision & governance

### 6.1 Decision record

> **DECISION: The Anthropic provider adapter REMAINS DISABLED BY DEFAULT in production builds at Milestone 4 closeout.**

### 6.2 Rationale & architectural authority

1. **Privacy-First Invariant (ADR-0005):** Cloud egress must be an explicit, informed user choice. Enabling network egress by default prior to the user configuring an API key and explicitly acknowledging network interactions violates the core PersonaSpeak privacy guarantee.
2. **Truthful Runtime State Model:** Unconfigured installations must truthfully operate using the local walking-skeleton (`FakeProvider`) or local fallback without making unconfigured network attempts.
3. **Milestone Boundary:** Production UI for user key entry, validation, and provider selection is the explicit scope of Milestone 5 (Settings & Onboarding Graph).

### 6.3 Milestone 5 activation path & rollback

- **Activation Path:** In Milestone 5, when the user provides a valid key in Settings, `SettingsViewModel` persists the secret via `DataStoreProviderConfigStore.save()`. The rewrite coordinator switches to `AnthropicMessagesAdapter` only when `ProviderConfigStore.load()` yields `StoreOutcome.Configured`.
- **Emergency Disable / Rollback:** If any cloud provider issue arises, calling `ProviderConfigStore.clear()` immediately reverts the store to `StoreOutcome.Unconfigured` and falls back to `FakeProvider`.

---

## 7. ASK-tree rent & UPSTREAM-MODIFIED.md ledger

### 7.1 Rent assessment

- **New ASK-tree modifications in Slice 3:** **ZERO**.
- **Existing ledger entries verified:**
  1. `android/keyboard/ime/app/src/main/AndroidManifest.xml` (Settings activity declaration & backup exclusion rules)
  2. `android/keyboard/ime/app/build.gradle` (Dependency on `:personaspeak-providers`)
- **Automated Verification:** All 7 verifier fixture suites (`verify-ask-closure-test`, `verify-dictionary-licenses-test`, `verify-upstream-ledger-test`, `verify-single-apk-test`, `verify-unified-build-flag-test`, `verify-no-secret-logging-test`, `verify-milestone-2-test`) pass with zero ledger drift.

---

## 8. Verification ladder

```
Level 4: Disposable AVD Gate (API 27 bmgr backup pass, API 34 parser journey, storage/egress receipts)
   ▲
Level 3: Scripted Verifiers (verify-no-secret-logging.sh, verify-ask-closure.sh, verify-upstream-ledger.sh)
   ▲
Level 2: Robolectric Integration Tests (BackupRuleFilesTest, DataStoreProviderConfigStoreTest)
   ▲
Level 1: JVM Unit Tests (:personaspeak-providers, :personaspeak-data, :personaspeak-ui, :core-*)
```

---

## 9. Non-goals (explicit boundaries)

- No Milestone 5 settings UI graph or user key-entry screens.
- No dynamic marketplace or runtime plugin loader.
- No persistent caching or storage of drafts, prompts, candidates, or rewrite results.
- No default-enabled production cloud network egress.
- No modification or refactoring of AnySoftKeyboard core keyboard logic.

---

## 10. Milestone 4 closeout checklist

- [ ] **M4 Slice 1:** Merged (PR #92, `bda68f0`) — Storage foundation & Keystore cipher.
- [ ] **M4 Slice 2:** Merged (PR #95, `672a808`) — Provider adapter & truthful states.
- [ ] **M4 Slice 3 Plan:** Reviewed and approved by Seraph, Cassie, Sigrid (this PR).
- [ ] **M4 Slice 3 Implementation & Receipts:**
  - [ ] API 26/27 legacy backup exclusion verified and receipt sealed (`backup-api27-receipt.json`).
  - [ ] Disposable-device parser journey verified and receipt sealed (`adapter-parser-receipt.json`).
  - [ ] Storage & egress audit completed and receipt sealed (`storage-egress-audit-receipt.json`).
  - [ ] Key-String §10 checklist note formally resolved and documented.
  - [ ] Provider default-disabled decision recorded in closeout and `ROADMAP.md`.
  - [ ] Milestone 4 marked complete on #89.
