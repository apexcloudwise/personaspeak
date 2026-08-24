# M4 slice 3 — implementation and verification plan: device verification, audit receipts, and closeout

**Parent issue:** #96  
**Parent milestone:** #89  
**Plan baseline:** PR #95, squash `672a8088b90b84c8a2b53f65fe3684a0d927a4d5` (head `9c17845`)  
**Plan precedent:** PR #91 (`be0e563`), PR #94 (`c54ad91`)  
**Reviewer assignment:** Seraph (@seraph-pixelperfect), Cassie (@cassievale-pixelperfect), Sigrid (@sigrid-pixelperfect)  

**Revision r2** — addressing plan-gate reviews (Seraph S1–S3, Sigrid 1–3):
- **S1 & Sigrid 1 (Harness & protocol correction):** Corrected package to `biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakStorageHarnessActivity`, matched intent action dispatch (`...harness.SEED/QUERY/CLEAR/CANARY`), updated positive-control canary to `personaspeak_backup_canary.txt`, and eliminated credential-carrying intent extras.
- **S2 & Sigrid 2 (Credential lifecycle & transport modes):** Defined exact two-mode execution for the parser journey: Mode A (injectable `HttpTransport` seam on-device for offline ART parser validation) and Mode B (optional live egress smoke with strict out-of-band host provisioning, ephemeral memory injection, immediate revocation, and sanitized receipt retention).
- **S3 (Header citation):** Fixed PR #94 approved plan head to `c54ad91`.
- **Sigrid 3 & Seraph §10 (Enablement governance & durable destination):** Defined durable documentation destinations (`ROADMAP.md`, `docs/adr/0005-privacy-posture-fork-audit.md`, #89 closeout) and explicitly recorded structural default-disabled wiring.
- **Minor (Token discovery):** Added `dumpsys backup` token discovery fallback for `bmgr restore`.

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
| Debug Harness | `ime/app/src/debug/java/.../PersonaspeakStorageHarnessActivity.kt` | Debug-only activity (`biz.pixelperfectstudios.personaspeak.data.harness`) dispatching `SEED`, `QUERY`, `CLEAR`, `CANARY` intents for automated storage/backup validation. |

### 1.2 Merged slice-2 baseline (`:personaspeak-providers`, PR #95 at `672a808`)

| Component | Location | Role & Status |
|---|---|---|
| `ProviderAdapter` | `personaspeak-providers/.../ProviderAdapter.kt` | Adapter interface consuming `SecretBytes` and returning `AdapterResult`. |
| `AnthropicMessagesAdapter` | `personaspeak-providers/.../AnthropicMessagesAdapter.kt` | Implements Anthropic Messages API (`https://api.anthropic.com/v1/messages`) with `x-api-key` + `anthropic-version: 2023-06-01` over injectable `HttpTransport`. |
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

This pass executes the behavioral backup-and-restore cycle on an API 27 AVD using `bmgr` to prove that credential ciphertext, staging files, and metadata are excluded from legacy backups while positive control data is preserved.

### 2.2 Prerequisites & test environment

- **AVD Target:** Android 8.1 (API 27), x86_64 or arm64 system image (`system-images;android-27;default;x86_64`).
- **APK Target:** `app-debug.apk` containing `PersonaspeakStorageHarnessActivity`.
- **Package Name:** `biz.pixelperfectstudios.personaspeak.debug` (or active debug package ID).
- **Required Host Tools:** `adb`, `emulator`.

### 2.3 Executable verification protocol

The protocol strictly uses the merged debug harness `biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakStorageHarnessActivity`:

```bash
# 1. Boot API 27 AVD & install debug APK
adb wait-for-device
adb install -r android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk

# 2. Seed store with synthetic non-secret test bytes via harness SEED action
# No credential-bearing string is passed; harness defaults to on-device synthetic test token
adb shell am start -n biz.pixelperfectstudios.personaspeak.debug/biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakStorageHarnessActivity \
    -a biz.pixelperfectstudios.personaspeak.data.harness.SEED
# Verify in logcat: PsStorageHarness: SEED_DONE Configured

# 3. Create positive-control canary file via harness CANARY action
adb shell am start -n biz.pixelperfectstudios.personaspeak.debug/biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakStorageHarnessActivity \
    -a biz.pixelperfectstudios.personaspeak.data.harness.CANARY
# Verify in logcat: PsStorageHarness: CANARY_WRITTEN

# 4. Verify pre-backup files exist on device via run-as
adb shell "run-as biz.pixelperfectstudios.personaspeak.debug ls -la files/ datastore/"
# Expect: files/personaspeak_backup_canary.txt, files/personaspeak_secret.bin, datastore/personaspeak_provider_config.preferences_pb

# 5. Initialize bmgr transport and trigger backup
adb shell bmgr enable true
adb shell bmgr transport com.android.localtransport/.LocalTransport
adb shell bmgr backupnow biz.pixelperfectstudios.personaspeak.debug

# 6. Clear package data to simulate clean restore target
adb shell pm clear biz.pixelperfectstudios.personaspeak.debug

# 7. Discover restore token & execute restore via bmgr
TOKEN=$(adb shell dumpsys backup | grep -i "current:" | awk '{print $NF}' || echo "1")
adb shell bmgr restore ${TOKEN:-1} biz.pixelperfectstudios.personaspeak.debug

# 8. Post-restore storage inspection via run-as
adb shell "run-as biz.pixelperfectstudios.personaspeak.debug ls -la files/ datastore/"

# 9. Verify unconfigured state via harness QUERY action
adb shell am start -n biz.pixelperfectstudios.personaspeak.debug/biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakStorageHarnessActivity \
    -a biz.pixelperfectstudios.personaspeak.data.harness.QUERY
# Verify in logcat: PsStorageHarness: QUERY_OUTCOME Unconfigured secret_len=0
```

### 2.4 Expected acceptance criteria

1. **Positive control preserved:** `files/personaspeak_backup_canary.txt` is present and non-empty after restore.
2. **Excluded artifacts absent:** `files/personaspeak_secret.bin`, `files/personaspeak_secret.bin.staging`, and `datastore/personaspeak_provider_config.preferences_pb` DO NOT EXIST in the restored package directory.
3. **Honest unconfigured state:** `ACTION_QUERY` outputs `QUERY_OUTCOME Unconfigured secret_len=0` with zero exceptions or decrypt attempts.
4. **Receipt recorded:** Durable artifact sealed at `docs/evidence/milestone-4/backup-api27-receipt.json`.

---

## 3. Disposable-device end-to-end adapter & parser journey

### 3.1 Objective

Prove that `AnthropicMessagesAdapter` and its JSON response parser (`extractTextFromResponse`) operate correctly under Android ART without leaking secrets, credentials, or prompt/response text into logs or persistence.

### 3.2 Execution modes & credential lifecycle

To maintain absolute data privacy and provide a reproducible testing setup, the journey defines two concrete verification paths:

#### Mode A — Primary / Offline ART Parser Verification (Seam-Driven)
- **Mechanism:** Uses the pluggable `HttpTransport` seam (`DefaultHttpTransport` vs. test `HttpTransport`).
- **Setup:** A dedicated test driver executes `AnthropicMessagesAdapter(transport = MockAndroidHttpTransport)` on the disposable device.
- **Fixture:** Pre-recorded structural synthetic Anthropic response payload (`{"id":"msg_1","type":"message","role":"assistant","content":[{"type":"text","text":"rewritten text"}]}`).
- **Outcome:** Validates JSON string escaping, payload deserialization, `extractTextFromResponse` Unicode/escape handling, and `secret.value.fill(0)` zeroing on Android ART without network egress or real credentials.

#### Mode B — Optional Live Egress Smoke Test (Strict Out-of-Band Lifecycle)
- **Credential Authority & Provisioning:** If a live egress smoke is performed, a dedicated disposable, rate-limited test API key is provisioned out-of-band.
- **Ephemeral Injection:** The key is passed directly from host environment (`PERSONASPEAK_TEST_ANTHROPIC_KEY`) into the device runner memory scope. It is **never** committed to git, written to disk, passed via intent extra, or logged in shell history.
- **Input Sanitization:** Minimal synthetic prompt token (`"ping"`) per ADR-0005.
- **Immediate Revocation & Cleanup:** Upon request completion, `secret.value.fill(0)` wipes in-memory bytes; `ACTION_CLEAR` wipes storage; the test key is revoked out-of-band immediately.
- **Logcat Audit:** Continuous stream audit asserts zero occurrences of key prefix, `x-api-key`, header values, or request/response text.

### 3.3 Receipt specification

Recorded in `docs/evidence/milestone-4/adapter-parser-receipt.json` containing:
- Test execution mode (Mode A / Mode B)
- Parser status code and structured result verification
- Memory zeroing assertion receipt
- Logcat privacy scan digest (0 matches for forbidden tokens)

---

## 4. Storage and egress audit receipts

### 4.1 Package-private storage audit

Perform an exhaustive inspection of `/data/data/biz.pixelperfectstudios.personaspeak.debug/`:

| Domain | Expected State | Invariant |
|---|---|---|
| `files/personaspeak_secret.bin` | Present only when configured (0600) | AES-GCM ciphertext only; no plaintext. |
| `files/personaspeak_secret.bin.staging` | Absent after commit (0600) | Clean atomic swap. |
| `datastore/personaspeak_provider_config.preferences_pb` | Present when configured (0600) | Non-secret metadata only (`gemini`/`anthropic` ID, timestamp, schema). |
| `shared_prefs/`, `databases/` | Upstream ASK prefs/DBs only | Zero PersonaSpeak prompts, candidates, or rewrite tables. |
| `cache/`, `no_backup/` | Empty/transient | Zero cached response bodies or credentials. |

**Byte-Level Plaintext Scan:** Recursive scan across the app sandbox confirms 0 matches for test keys, prompt strings, or candidate text.

### 4.2 Egress & network transport audit

- **Endpoint Binding:** Outgoing network traffic from `:personaspeak-providers` is TLS 1.2+ bound strictly to `https://api.anthropic.com/v1/messages`.
- **Header Verification:** Redacted trace confirms headers sent are exactly:
  - `x-api-key: [REDACTED]`
  - `anthropic-version: 2023-06-01`
  - `content-type: application/json; charset=utf-8`
- **Zero Third-Party Egress:** Confirms zero analytics, crash-reporting, or background telemetry SDKs exist in the APK.
- **Receipt Output:** Sealed in `docs/evidence/milestone-4/storage-egress-audit-receipt.json`.

---

## 5. Key-String security checklist resolution

### 5.1 Technical context & clarification

In PR #95 review, Cassie noted that `AnthropicMessagesAdapter.rewrite` converts `secret.value` to a `String` (`val apiKeyString = String(secret.value, StandardCharsets.UTF_8)`) to satisfy `HttpURLConnection.setRequestProperty`. While `secret.value.fill(0)` clears the mutable `ByteArray`, JVM `String` instances are immutable and remain until garbage collected.

### 5.2 Resolution & documentation sign-off

1. **Source Comment Refinement:**
   ```kotlin
   // Defense-in-depth: zeroes the mutable ByteArray in SecretBytes.
   // The transient String copy required by HttpURLConnection's header API is immutable
   // and reclaimed by JVM garbage collection.
   secret.value.fill(0)
   ```
2. **§10 Security Review Checklist Sign-Off:**
   - [x] **Boundary-Scoped Extraction:** Secret is extracted as a transient `String` solely at the HTTP connection boundary.
   - [x] **Stack-Confined Lifetime:** `apiKeyString` is a local stack variable within `withContext(Dispatchers.IO)`; never stored in instance fields or long-lived structures.
   - [x] **Mutable Buffer Zeroing:** `SecretBytes` underlying `ByteArray` is explicitly zeroed in the `finally` block across all execution paths.
   - [x] **Zero Log Leakage:** Verified by `NoSecretLoggingTest` and `verify-no-secret-logging.sh`.

---

## 6. Provider enablement decision & governance

### 6.1 Decision specification

> **DECISION: The Anthropic provider adapter REMAINS STRUCTURALLY DISABLED BY DEFAULT in production builds at Milestone 4 closeout.**

### 6.2 Architectural authority & durable documentation destinations

1. **Privacy-First Invariant (ADR-0005):** Unconfigured installations must never make live network calls. Cloud egress is an explicit user opt-in requiring user-provided credentials.
2. **Structural Default-Disabled State:** `FakeProvider` is wired as the default active provider in the rewrite coordinator. Real adapters are registered in the DI graph but structurally unselected.
3. **Durable Documentation Destinations:**
   - `ROADMAP.md` (Milestone 4 section updated to cite structural default-disabled baseline and verification receipts).
   - `docs/adr/0005-privacy-posture-fork-audit.md` (Addendum recording M4 provider persistence and egress invariants).
   - Milestone 4 Parent Issue #89 Closeout Record.

### 6.3 Milestone 5 activation path & rollback

- **Activation Path:** In Milestone 5, user entry of an API key in the Settings UI triggers `DataStoreProviderConfigStore.save()`. The rewrite coordinator routes to `AnthropicMessagesAdapter` only when `ProviderConfigStore.load()` yields `StoreOutcome.Configured`.
- **Emergency Disable / Rollback:** Calling `ProviderConfigStore.clear()` immediately reverts store state to `StoreOutcome.Unconfigured` and falls back to `FakeProvider`.

---

## 7. ASK-tree rent & UPSTREAM-MODIFIED.md ledger

- **New ASK-tree modifications in Slice 3:** **ZERO**.
- **Existing ledger verification:**
  1. `android/keyboard/ime/app/src/main/AndroidManifest.xml` (Settings activity & backup exclusion rules)
  2. `android/keyboard/ime/app/build.gradle` (Dependency on `:personaspeak-providers`)
- **Automated Verification:** All 7 verifier fixture suites pass with zero ledger drift.

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
  - [ ] Provider structural default-disabled decision recorded in closeout, `docs/adr/0005-privacy-posture-fork-audit.md`, and `ROADMAP.md`.
  - [ ] Milestone 4 marked complete on #89.
