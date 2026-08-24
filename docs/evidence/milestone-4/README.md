# Milestone 4 — Secure Provider Configuration & Persistence Evidence Receipts

**Status: MODE A QUALIFIED; MODE B FAIL-CLOSED LIVE PROBE RECORDED.** Milestone 4 verification completed across all three slices in accordance with `#89` and the approved plans `#91`, `#94`, `#97`.

## Evidence Manifest & Authority

The authority manifest is [`receipt-manifest.json`](receipt-manifest.json) (`schemaVersion: 1`).

| Receipt File | Scope & Invariants |
|---|---|
| [`backup-api27-receipt.json`](backup-api27-receipt.json) | Android 8.1 (API 27) `bmgr` cycle with `PersonaspeakStorageHarnessActivity`. Positive canary restored; Keystore ciphertext + DataStore metadata excluded under `fullBackupContent`. Runtime query returns `StoreOutcome.Unconfigured` with 0 bytes. |
| [`adapter-parser-receipt.json`](adapter-parser-receipt.json) | `AnthropicMessagesAdapter` & `extractTextFromResponse` on Android ART via `PersonaspeakAdapterHarnessActivity`. Mode A (offline `HttpTransport` seam: PASS) + Mode B (live probe with ephemeral on-device key: AUTH_REJECTED_AS_EXPECTED). Continuous stream logcat audit confirms 0 forbidden token leaks. |
| [`storage-egress-audit-receipt.json`](storage-egress-audit-receipt.json) | Sandbox storage mode checks (`0600`/`0700`) + byte scan showing 0 plaintext leaks. UID-scoped kernel socket sampler (`/proc/net/tcp{,6}` @ 100ms) proving all live outgoing traffic is TLS 1.3 on port 443 bound to `api.anthropic.com` (`160.79.104.10`). 0 third-party, 0 unencrypted connections. |

## Architectural & Governance Baseline

- **Provider Enablement**: The Anthropic provider adapter remains **structurally disabled by default** in production builds at Milestone 4 closeout (`FakeProvider` active in rewrite coordinator; adapter registered in DI but unselected).
- **Transport Binding**: `AnthropicMessagesAdapter` connects over HTTPS (`api.anthropic.com/v1/messages`), with TLS 1.3 observed in the recorded device run.
- **Milestone 5 Seam**: Live cloud network egress is strictly gated on explicit user configuration and opt-in in the Milestone 5 Settings & Onboarding UI graph.
