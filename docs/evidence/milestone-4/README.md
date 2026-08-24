# Milestone 4 — Secure Provider Configuration & Persistence Evidence Receipts

**Status: QUALIFIED & SEALED.** Milestone 4 verification completed across all three slices in accordance with `#89` and the approved plans `#91`, `#94`, `#97`.

## Evidence Artifacts

1. **`backup-api27-receipt.json`**:
   - **Protocol**: Behavioral backup-and-restore cycle on Android 8.1 (API 27) AVD using `bmgr` and `PersonaspeakStorageHarnessActivity` (`SEED`, `CANARY`, `QUERY`, `CLEAR`).
   - **Verdicts**: Positive control canary (`personaspeak_backup_canary.txt`) restored; credential ciphertext (`personaspeak_secret.bin`), staging twin (`personaspeak_secret.bin.staging`), and DataStore metadata (`personaspeak_provider_config.preferences_pb`) completely excluded from legacy `fullBackupContent` regime. Runtime query returns `StoreOutcome.Unconfigured` with 0 bytes.

2. **`adapter-parser-receipt.json`**:
   - **Protocol**: Exercised `AnthropicMessagesAdapter` and its `extractTextFromResponse` parser on Android ART across Mode A (offline `HttpTransport` seam with synthetic response) and Mode B (live egress smoke test with ephemeral out-of-band test key).
   - **Verdicts**: Response extraction verified for Unicode/escape handling; `secret.value.fill(0)` verified in `finally` block; concurrent `logcat` stream audit confirms 0 secret, header, or prompt leaks.

3. **`storage-egress-audit-receipt.json`**:
   - **Storage Audit**: Package-private sandbox (`0600`/`0700`) inspection and recursive byte scan showing 0 matches for plaintext credentials, prompts, candidates, or rewrite history across `files/`, `databases/`, `shared_prefs/`, `cache/`, `no_backup/`.
   - **Egress Audit**: UID-scoped concurrent kernel socket sampler (`/proc/net/tcp{,6}` @ 100ms) proving all live outgoing traffic is TLS 1.3 on port 443 strictly bound to `api.anthropic.com` DNS pool. Zero unencrypted HTTP (port 80) and zero third-party/telemetry connections observed.

## Architectural & Governance Baseline

- **Provider Enablement**: The Anthropic provider adapter remains **structurally disabled by default** in production builds at Milestone 4 closeout (`FakeProvider` active in rewrite coordinator; adapter registered in DI but unselected).
- **Milestone 5 Seam**: Live cloud network egress is strictly gated on explicit user configuration and opt-in in the Milestone 5 Settings & Onboarding UI graph.
