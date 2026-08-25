# M4 Device Qualification: Immutable-Provenance Fixture Plan

**Issue:** #99  
**Parent / Closeout Gate:** #96  
**Parent Milestone:** #89  
**Baseline Commit:** `65777629f888a7b56510dedc9bdafd8ff5352ba7` (PR #98)  
**Plan Precedent:** PR #91 (`be0e563`), PR #94 (`c54ad91`), PR #97 (`b0ac6a1`)  
**Owner:** @reicodes-pixelperfect  
**Reviewers:** Seraph (@seraph-pixelperfect), Cassie (@cassievale-pixelperfect), Sigrid (@sigrid-pixelperfect), Ghost (@ghostinprod-pixelperfect)  

---

## 0. Plan-Only Scope Statement & Architectural Baseline

This pull request contains the **implementation and verification plan only** for the dedicated disposable-device qualification run.

- **Strict Plan-Only Invariant:** No device execution is performed, no mutable/hand-authored device receipts are committed, no network configuration is modified, and no cloud provider is enabled in this PR.
- **Scaffolding Preservation:** This plan builds strictly upon the deterministic source and compile scaffolding merged in PR #98 (`6577762`). It does not alter `android/scripts/verify-milestone-4.sh` or any existing deterministic gate verifiers.
- **Gate Governance:** Issue #96 and milestone #89 remain explicitly **OPEN**. Milestone 4 is not complete until real, immutable device-run receipts pass exact-head non-author review in the subsequent device-run PR.
- **Default-Disabled Wiring:** The `AnthropicMessagesAdapter` remains structurally disabled by default (`FakeProvider` remains the active default in the rewrite coordinator).

---

## 1. Context & Operational Readiness

PR #98 successfully qualified the deterministic source contracts and compilation gates in CI:
- On-device cryptographically secure random byte generation in `PersonaspeakStorageHarnessActivity` (`ACTION_SEED`).
- Compiling debug harness `PersonaspeakAdapterHarnessActivity` with `SecretBytes.fill(0)` memory zeroing contracts.
- Deterministic CI checks asserting zero secret logging, ASK tree closure, and upstream ledger compliance.

The remaining qualification requirements demand an external API-27-capable disposable device environment and, for Mode B, an ephemeral credential authority. This plan provides the complete, tamper-evident protocol governing that future execution pass.

---

## 2. Capture-Time Provenance & Immutable Ledger Architecture

All evidence produced during the future device qualification run must adhere to strict immutable provenance standards, preventing backfilling, manual authoring, or post-hoc mutation.

### 2.1 Capture-Time Metadata Tuple
Every qualification run captures and seals a deterministic metadata record:
1. **Application Commit Digest:** SHA-256 / commit hash of the exact git tree on `main` being evaluated.
2. **APK Build Digest:** SHA-256 checksum and exact byte count of the compiled `app-debug.apk`.
3. **Device / AVD Fingerprint:** Output of `getprop ro.build.fingerprint`, `ro.build.version.sdk` (e.g. `27` and `34`), ABI, and system image release ID.
4. **Host Toolchain Identity:** Tool versions and paths (`adb version`, `emulator -version`, `java -version`, `kotlinc -version`).
5. **Execution Timestamps:** UTC ISO-8601 start and finish timestamps.
6. **Command Transcript & Streams:** Full, unedited stdin/stdout/stderr byte captures and process exit codes.
7. **Raw Artifact Digests:** SHA-256 checksums of raw logcat outputs, `/proc/net/tcp` socket dumps, and file listings.

### 2.2 Custody, Retention, and Evidence Sealing Procedure
- **Append-Only Evidence Repository / Branch:** Raw execution logs, socket sampling dumps, and structured transcripts are committed directly to the append-only `evidence` branch (as established in Milestone 2 qualification).
- **Compact Receipt Index:** The implementation PR in `main` commits only a compact, immutable receipt (`docs/evidence/milestone-4/receipt.json` and updated `README.md`) pointing to the specific evidence commit hash and artifact SHA-256 digests.
- **Tamper-Evident Verification Rule:** Reviewers recompute artifact digests against the evidence branch commit; any discrepancy between published digests and raw artifacts causes an immediate rejection.
- **Anti-Backfill Invariant:** Hand-authored, reconstructed, or relabelled receipts are strictly prohibited. Receipts may only be emitted directly by the automated runner at capture time.

---

## 3. Protocol 1: API-27 Legacy `fullBackupContent` Exclusion Pass

### 3.1 Objective
Prove on a physical/AVD Android 8.1 (API 27) device that Android's legacy `fullBackupContent` backup engine (`personaspeak_full_backup_content.xml`) excludes all AES-GCM ciphertext, staging files, and DataStore metadata from backups, while preserving non-excluded positive control files.

### 3.2 Prerequisites & Fixture
- **Target Fixture:** Disposable Android 8.1 x86_64 AVD (`system-images;android-27;default;x86_64`) booted in a clean state.
- **Package ID:** `biz.pixelperfectstudios.personaspeak.debug`
- **Harness Component:** `biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakStorageHarnessActivity`

### 3.3 Executable Verification Protocol (Literal Commands)

```bash
# Step 1: Install debug APK on API 27 fixture
adb wait-for-device
adb install -r android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk

# Step 2: Seed storage via harness (generates 32 SecureRandom bytes on-device; zero intent extras)
adb shell am start -n biz.pixelperfectstudios.personaspeak.debug/biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakStorageHarnessActivity \
    -a biz.pixelperfectstudios.personaspeak.data.harness.SEED
# Observation: Logcat contains "PsStorageHarness: SEED_DONE Configured"

# Step 3: Write positive-control canary file
adb shell am start -n biz.pixelperfectstudios.personaspeak.debug/biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakStorageHarnessActivity \
    -a biz.pixelperfectstudios.personaspeak.data.harness.CANARY
# Observation: Logcat contains "PsStorageHarness: CANARY_WRITTEN"

# Step 4: Verify pre-backup package state via run-as
adb shell "run-as biz.pixelperfectstudios.personaspeak.debug ls -la files/ datastore/"
# Expectation:
#   files/personaspeak_backup_canary.txt (PRESENT)
#   files/personaspeak_secret.bin (PRESENT, 0600)
#   datastore/personaspeak_provider_config.preferences_pb (PRESENT, 0600)

# Step 5: Initialize bmgr local transport and run backup
adb shell bmgr enable true
adb shell bmgr transport com.android.localtransport/.LocalTransport
adb shell bmgr backupnow biz.pixelperfectstudios.personaspeak.debug

# Step 6: Clear package data to simulate a fresh device / reinstall
adb shell pm clear biz.pixelperfectstudios.personaspeak.debug

# Step 7: Discover restore token and execute bmgr restore
TOKEN=$(adb shell dumpsys backup | grep -i "current:" | awk '{print $NF}' || echo "1")
adb shell bmgr restore ${TOKEN:-1} biz.pixelperfectstudios.personaspeak.debug

# Step 8: Inspect post-restore package filesystem
adb shell "run-as biz.pixelperfectstudios.personaspeak.debug ls -la files/ datastore/"
# Expectation:
#   files/personaspeak_backup_canary.txt (PRESENT, NON-EMPTY)
#   files/personaspeak_secret.bin (ABSENT)
#   files/personaspeak_secret.bin.staging (ABSENT)
#   datastore/personaspeak_provider_config.preferences_pb (ABSENT)

# Step 9: Query provider config state via harness
adb shell am start -n biz.pixelperfectstudios.personaspeak.debug/biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakStorageHarnessActivity \
    -a biz.pixelperfectstudios.personaspeak.data.harness.QUERY
# Observation: Logcat contains "PsStorageHarness: QUERY_OUTCOME Unconfigured secret_len=0"
```

### 3.4 Sanitized Observations & Acceptance Criteria
1. `files/personaspeak_backup_canary.txt` is restored and non-empty (positive control valid).
2. `files/personaspeak_secret.bin`, `files/personaspeak_secret.bin.staging`, and `datastore/personaspeak_provider_config.preferences_pb` are completely absent.
3. `ACTION_QUERY` returns `QUERY_OUTCOME Unconfigured secret_len=0` with zero decryption errors or KeyStore crashes.

### 3.5 Abort Conditions
- `bmgr backupnow` or `bmgr restore` fails with transport error.
- Canary file is missing after restore (invalid backup run).
- Any secret or DataStore file is present in restored storage (exclusion failure).
- Query outcome is not `Unconfigured`.

### 3.6 Cleanup & Post-Run Teardown
- Package cleared via `adb shell pm clear biz.pixelperfectstudios.personaspeak.debug`.
- Storage reset via `ACTION_CLEAR`.
- AVD instance wiped and terminated.

---

## 4. Protocol 2: Mode-A Offline ART Parser Validation

### 4.1 Objective
Execute `AnthropicMessagesAdapter` and its JSON parser (`extractTextFromResponse`) on Android ART using `PersonaspeakAdapterHarnessActivity` against synthetic payloads to prove Unicode, newline, and escape-sequence handling, plus memory zeroing (`SecretBytes.fill(0)`), without network egress.

### 4.2 Prerequisites & Fixture
- **Target Fixture:** Disposable Android AVD (API 27 or API 34).
- **Harness Component:** `biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakAdapterHarnessActivity`
- **Harness Log Tag:** `PsRunner` (defined as `private const val TAG = "PsRunner"` in `PersonaspeakAdapterHarnessActivity.kt`)
- **Transport Seam:** `MockAndroidHttpTransport` pre-configured in harness with synthetic JSON payload:
  `{"id":"msg_01","type":"message","role":"assistant","content":[{"type":"text","text":"sanitized rewritten payload with unicode \u2728 and \nescapes"}]}`

### 4.3 Executable Verification Protocol (Literal Commands)

```bash
# Step 1: Clear logcat buffer and start log streaming on the merged PsRunner tag
adb logcat -c
adb logcat -s PsRunner:V > /tmp/mode_a_logcat.log &
LOGCAT_PID=$!

# Step 2: Launch Mode A harness via intent
adb shell am start -n biz.pixelperfectstudios.personaspeak.debug/biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakAdapterHarnessActivity \
    -a biz.pixelperfectstudios.personaspeak.data.harness.MODE_A

# Step 3: Wait for completion and stop logcat capture
sleep 2
kill $LOGCAT_PID 2>/dev/null || true

# Step 4: Verify logcat records against exact baseline outputs
cat /tmp/mode_a_logcat.log
```

### 4.4 Sanitized Observations & Acceptance Criteria
1. Logcat asserts: `PsRunner: Starting Mode A offline parser validation`.
2. Logcat asserts: `PsRunner: Injected MockAndroidHttpTransport synthetic payload (length=124)`.
3. Logcat asserts: `PsRunner: extractTextFromResponse extracted 56 chars`.
4. Logcat asserts: `PsRunner: SecretBytes.fill(0) verified executed`.
5. Logcat asserts: `PsRunner: Mode A complete: SUCCESS`.
6. Zero uncaught exceptions, zero JSON parsing crashes.

### 4.5 Abort Conditions
- Logcat reports `Mode A failed with NetworkFailure` or `AuthFailure`.
- Memory zeroing check fails.
- Activity crashes or hangs.

### 4.6 Cleanup
- Package cleared via `adb shell pm clear biz.pixelperfectstudios.personaspeak.debug`.

---

## 5. Protocol 3: Mode-B Live Egress Smoke Test & Socket Audit

### 5.1 Objective & Bounded Observation Claims
Execute a live, single-request egress smoke test to `https://api.anthropic.com/v1/messages` using an ephemeral credential to verify end-to-end connectivity, response parsing under ART, and memory zeroing.

**Scope of Bounded Socket Audit Claims:**
- The kernel socket sampler (`/proc/net/tcp`, `/proc/net/tcp6`) independently proves **transport-layer endpoint isolation**:
  1. All outbound IP connections from the application UID (`$APP_UID`) connect exclusively to destination IP addresses in the resolved DNS pools (IPv4 A records and IPv6 AAAA records) for `api.anthropic.com`.
  2. All outbound connections target destination port `443` (standard HTTPS).
  3. Zero unencrypted outbound connections (port 80).
  4. Zero auxiliary, telemetry, or third-party connections.
- *Application-layer / TLS protocol guarantees* (TLS 1.3 negotiation, SNI matching, `Host` header formatting) are enforced by the platform `HttpsURLConnection` stack and verified by unit test contracts, while the kernel `/proc/net/tcp` sampler validates host-level network boundary isolation.

### 5.2 Mode-B Ephemeral Credential Authority & Safe Injection Interface

```
[Host Memory Vault] ──(Volatile TCP Stream / adb forward)──► [Debug Abstract Socket] ──► [DataStore / Keystore]
        │                                                                                     │
 (Revoked Post-Run)                                                                    (Zeroed & Purged)
```

1. **Credential Authority:** A dedicated rate-limited, disposable test API key is provisioned out-of-band specifically for this qualification run.
2. **Safe Debug-Only Injection Architecture:**
   - **Dedicated Component:** `PersonaspeakEphemeralSeedService` residing in `android/keyboard/ime/app/src/debug/java/biz/pixelperfectstudios/personaspeak/data/harness/PersonaspeakEphemeralSeedService.kt`.
   - **Manifest Registration:** Registered in `android/keyboard/ime/app/src/debug/AndroidManifest.xml` as `<service android:name=".data.harness.PersonaspeakEphemeralSeedService" android:exported="true" />` (debug build only; ledgered in `UPSTREAM-MODIFIED.md` at implementation).
   - **Lifecycle & Action:** Started via `am startservice -a biz.pixelperfectstudios.personaspeak.data.harness.START_EPHEMERAL_SEED_SERVICE`.
   - **Transport Boundary:** Binds a local abstract Unix domain socket (`localabstract:personaspeak_debug_seed`) forwarded over `adb forward tcp:4242 localabstract:personaspeak_debug_seed`.
   - **Readiness Signal & Bounded Polling:** When socket bind succeeds, service logs `PsStorageHarness: EPHEMERAL_SOCKET_READY` to logcat. The host runner polls logcat for this readiness marker (timeout 5s) before attempting connection, eliminating any startup race.
   - **Single Connection & Validation Guardrails:**
     - The service accepts exactly one connection (`serverSocket.accept()`) and immediately closes the server socket to reject any further connections.
     - Payload bounds: Maximum 512 bytes enforced; payload must start with `sk-ant-` prefix and decode as valid UTF-8.
     - Fail-Closed: If payload is oversize, lacks `sk-ant-` prefix, or contains invalid characters, receiving buffer is immediately zeroed with `fill(0)`, logs `PsStorageHarness: EPHEMERAL_SEED_REJECTED`, and service terminates with `stopSelf()` without writing to DataStore.
   - **Host-Side Volatile Streaming:** A host Python one-liner streams the key directly from the `PERSONASPEAK_TEST_ANTHROPIC_KEY` environment variable over `127.0.0.1:4242` in volatile memory without intermediate file creation, shell argument expansion, or shell history entries.
   - **On-Device Ingestion & Zeroing:** The service wraps bytes in `SecretBytes`, saves `ProviderConfig("anthropic", System.currentTimeMillis())` via `DataStoreProviderConfigStore`, executes `buffer.fill(0)`, logs `PsStorageHarness: EPHEMERAL_SEED_DONE Configured provider=anthropic`, and stops itself via `stopSelf()`.
   - **Strict Leakage Invariant:** No secret may ever appear in source code, shell history, `am start` intent extras (`--es`), logs, logcat, screenshots, markdown fixtures, git commits, or retained receipt files.
3. **Payload Sanitization:** Request uses minimal synthetic prompt token (`"ping"` / `"Respond with ping only"` per ADR-0005).
4. **Immediate Revocation Proof:**
   - On-device: `SecretBytes.fill(0)` zeros key bytes immediately in the `finally` block; `ACTION_CLEAR` purges ciphertext from storage.
   - Host/Cloud: The test key is permanently revoked out-of-band on the Anthropic console immediately upon run completion, with revocation confirmation timestamp recorded.

### 5.3 Deterministic Concurrent Socket Sampling & Verification Protocol (Literal Commands)

```bash
# Step 1: Forward local port to debug abstract socket
adb forward tcp:4242 localabstract:personaspeak_debug_seed

# Step 2: Clear logcat and launch ephemeral seed service
adb logcat -c
adb shell am startservice -n biz.pixelperfectstudios.personaspeak.debug/biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakEphemeralSeedService \
    -a biz.pixelperfectstudios.personaspeak.data.harness.START_EPHEMERAL_SEED_SERVICE

# Step 3: Bounded readiness poll (wait for EPHEMERAL_SOCKET_READY in logcat, max 5s)
python3 -c "
import subprocess, time, sys
start = time.time()
while time.time() - start < 5.0:
    out = subprocess.check_output(['adb', 'logcat', '-d', '-s', 'PsStorageHarness:V']).decode('utf-8', errors='ignore')
    if 'EPHEMERAL_SOCKET_READY' in out:
        print('Socket ready on device')
        sys.exit(0)
    time.sleep(0.1)
print('ERROR: Timed out waiting for EPHEMERAL_SOCKET_READY', file=sys.stderr)
sys.exit(1)
"

# Step 4: Stream credential from host memory (no disk/history/intent exposure)
python3 -c "
import os, socket
key = os.environ.get('PERSONASPEAK_TEST_ANTHROPIC_KEY', '').strip()
assert key.startswith('sk-ant-'), 'Invalid or absent PERSONASPEAK_TEST_ANTHROPIC_KEY'
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.connect(('127.0.0.1', 4242))
s.sendall(key.encode('utf-8'))
s.close()
"
adb forward --remove tcp:4242

# Step 5: Determine Application UID on device
APP_UID=$(adb shell "dumpsys package biz.pixelperfectstudios.personaspeak.debug | grep userId= | head -n 1" | awk -F= '{print $2}' | tr -d ' ')

# Step 6: Snapshot host DNS resolution for IPv4 (A) and IPv6 (AAAA)
dig +short A api.anthropic.com | grep -E '^[0-9.]+$' > /tmp/approved_ipv4.txt
dig +short AAAA api.anthropic.com | grep -E '^[0-9a-fA-F:]+$' > /tmp/approved_ipv6.txt

# Step 7: Launch concurrent background socket sampler polling kernel TCP tables every 100ms
adb shell "while true; do grep -w $APP_UID /proc/net/tcp /proc/net/tcp6 2>/dev/null; sleep 0.1; done" > /tmp/raw_sockets.log &
SAMPLER_PID=$!

# Step 8: Clear logcat and stream PsRunner and PsStorageHarness
adb logcat -c
adb logcat -s PsRunner:V PsStorageHarness:V > /tmp/mode_b_logcat.log &
LOGCAT_PID=$!

# Step 9: Trigger Mode-B execution via harness
adb shell am start -n biz.pixelperfectstudios.personaspeak.debug/biz.pixelperfectstudios.personaspeak.data.harness.PersonaspeakAdapterHarnessActivity \
    -a biz.pixelperfectstudios.personaspeak.data.harness.MODE_B

# Step 10: Wait for completion, then terminate samplers
sleep 5
kill $SAMPLER_PID 2>/dev/null || true
kill $LOGCAT_PID 2>/dev/null || true

# Step 11: Literal socket log decoder & validation script (IPv4 + IPv6 support)
python3 -c "
import sys, socket, struct

approved_v4 = set(line.strip() for line in open('/tmp/approved_ipv4.txt') if line.strip())
approved_v6 = set()
for line in open('/tmp/approved_ipv6.txt'):
    line = line.strip()
    if line:
        try:
            approved_v6.add(socket.inet_ntop(socket.AF_INET6, socket.inet_pton(socket.AF_INET6, line)))
        except Exception:
            pass

violations = []
observed_connections = []

with open('/tmp/raw_sockets.log', 'r') as f:
    for line in f:
        parts = line.strip().split()
        if len(parts) < 8:
            continue
        rem_addr = parts[2]
        if ':' not in rem_addr:
            continue
        hex_ip, hex_port = rem_addr.split(':')
        port = int(hex_port, 16)
        if port == 0:
            continue
        
        # Decode IPv4 (8 hex digits, 32-bit machine word order)
        if len(hex_ip) == 8:
            if hex_ip == '00000000':
                continue
            ip_str = socket.inet_ntoa(struct.pack('<I', int(hex_ip, 16)))
            entry = f'{ip_str}:{port}'
            if entry not in observed_connections:
                observed_connections.append(entry)
            if port != 443:
                violations.append(f'Non-HTTPS IPv4 port observed: {ip_str}:{port}')
            if ip_str not in approved_v4:
                violations.append(f'Unapproved destination IPv4 observed: {ip_str}:{port}')
        
        # Decode IPv6 (32 hex digits, four 32-bit words in little-endian)
        elif len(hex_ip) == 32:
            if hex_ip == '00000000000000000000000000000000':
                continue
            raw_bytes = bytearray()
            for i in range(0, 32, 8):
                raw_bytes.extend(bytes.fromhex(hex_ip[i:i+8])[::-1])
            ip_str = socket.inet_ntop(socket.AF_INET6, bytes(raw_bytes))
            entry = f'[{ip_str}]:{port}'
            if entry not in observed_connections:
                observed_connections.append(entry)
            if port != 443:
                violations.append(f'Non-HTTPS IPv6 port observed: [{ip_str}]:{port}')
            if ip_str not in approved_v6:
                violations.append(f'Unapproved destination IPv6 observed: [{ip_str}]:{port}')

print(f'Observed connections: {observed_connections}')
if violations:
    for v in violations:
        print(f'VIOLATION: {v}', file=sys.stderr)
    sys.exit(1)
print('SOCKET AUDIT PASSED: All outbound sockets strictly bound to approved IPs on port 443')
"
```

### 5.4 Sanitized Observations & Acceptance Criteria
1. Logcat asserts: `PsStorageHarness: EPHEMERAL_SEED_DONE Configured provider=anthropic`.
2. Logcat asserts: `PsRunner: Starting Mode B live egress smoke test`.
3. Logcat asserts: `PsRunner: Ephemeral key loaded from DataStoreProviderConfigStore`.
4. Logcat asserts: `PsRunner: HTTP Status 200 OK received: 4 chars`.
5. Logcat asserts: `PsRunner: SecretBytes.fill(0) executed`.
6. Logcat asserts: `PsStorageHarness: CLEAR_DONE` and `PsRunner: Mode B complete: SUCCESS`.
7. Socket evaluation script confirms 100% of outbound connections from `$APP_UID` were to approved `api.anthropic.com` IPs on destination port 443.
8. Zero third-party, analytics, or unencrypted sockets observed.
9. Logcat privacy scan confirms 0 occurrences of API key prefix, `x-api-key`, header values, or prompt text.

### 5.5 Abort Conditions
- Sampler detects any connection to port 80 or unapproved IP.
- HTTP status != 200 or NetworkFailure/AuthFailure reported.
- Memory zeroing fails or credential persists in storage.
- Key prefix found in logcat scan.

### 5.6 Cleanup & Revocation Proof
- `adb shell pm clear biz.pixelperfectstudios.personaspeak.debug` executed.
- Storage reset verified via `am start -a ...QUERY` returning `Unconfigured`.
- Cloud API key revoked out-of-band; revocation verification timestamp logged.
- Disposable AVD destroyed.

---

## 6. Fail-Closed Result Policy

To maintain absolute security and verification integrity:
- **Unconditional Fail-Closed:** An incomplete run, missing log, unrecognized socket connection, unverified checksum, redaction anomaly, or test failure is an unconditional FAIL.
- **No Provisional Status:** There is no partial pass, soft approval, or provisional qualification.
- **Provider Status on Failure:** Under ANY failure, the provider remains structurally disabled (`FakeProvider` active in rewrite coordinator; real adapter unselected).

---

## 7. Future Evidence PR & Milestone 4 Closeout Checklist

The genuine device run will be submitted in a dedicated follow-up PR:  
`feat(m4): device qualification receipts and milestone 4 closeout (#96)`

### 7.1 Follow-Up PR Artifacts
1. `docs/evidence/milestone-4/README.md` — Updated to `Status: QUALIFIED` referencing the immutable run commit on the `evidence` branch.
2. `docs/evidence/milestone-4/receipt.json` — Sealed machine-readable receipt containing artifact SHA-256 digests and verification verdicts.
3. `ROADMAP.md` — Updated to mark Milestone 4 complete (`- [x]`).
4. `PATCHNOTES.md` — Updated with the final closeout entry.

### 7.2 Issue #96 Closeout Checklist (To be checked off ONLY after genuine run passes)
- [ ] API 26/27 legacy backup exclusion proven on device via `bmgr` (`backup-api27-receipt.json`).
- [ ] ART parser execution proven under Mode A (`adapter-parser-receipt.json`).
- [ ] Live egress socket audit proven under Mode B with zero leakage (`storage-egress-audit-receipt.json`).
- [ ] Key-String §10 checklist resolution verified against device runtime.
- [ ] Durable structural default-disabled provider governance ratified.
- [ ] Milestone 4 marked complete on parent issue #89.

---

## 8. ASK-Tree Rent & UPSTREAM-MODIFIED Ledger Impact

- **Production ASK Code Rent:** 0 lines modified.
- **Debug Harnesses:** Debug harnesses in `keyboard/ime/app/src/debug/` are excluded from release builds.
- **Ledger Invariant:** Zero new modifications to `android/keyboard/UPSTREAM-MODIFIED.md`.
- **Verifier Assertions:** All existing verifier scripts (`verify-milestone-4.sh`, `verify-ask-closure.sh`, `verify-upstream-ledger.sh`) pass unconditionally.

---

## 9. Verification Steps for this Plan PR

1. Run deterministic source and compile gate verifier:
   ```bash
   ./android/scripts/verify-milestone-4.sh
   ```
2. Run verifier test suite:
   ```bash
   ./android/scripts/tests/verify-milestone-4-test.sh
   ```
3. Run secret logging scan:
   ```bash
   ./android/scripts/verify-no-secret-logging.sh
   ```
4. Verify zero changes to production code or upstream ledger.

---

## 10. Non-Goals

- No Milestone 5 settings/onboarding screens, UI routes, or user key-entry flows.
- No production default enablement of cloud adapters.
- No network egress without explicit user configuration.
- No hand-authored, mock, placeholder, or reconstructed device run evidence in `main`.
- No modification of AnySoftKeyboard core keyboard logic.

---

## 11. Reviewer Seats & Sign-Off Matrix

| Reviewer | Seat / Focus Area |
|---|---|
| **@seraph-pixelperfect** | Plan gate authority, API-27 backup protocol, closeout checklist & governance (§3, §6, §7) |
| **@cassievale-pixelperfect** | Secret lifecycle, Mode-B injection boundary, socket audit & redaction review (§2, §5, §6) |
| **@sigrid-pixelperfect** | Verification rigor, harness invariants, fail-closed policy, ASK ledger & non-goals (§0, §1, §4, §8, §10) |
| **@ghostinprod-pixelperfect** | Architecture alignment and milestone overseer sign-off |
