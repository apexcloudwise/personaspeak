# Milestone 4 — Secure Provider Configuration & Persistence Evidence Receipts

**Status: QUALIFIED & SEALED.** Milestone 4 verification completed across all three slices in accordance with `#89` and the approved plans `#91`, `#94`, `#97`.

## Evidence Manifest & Authority

The authority manifest is [`receipt-manifest.json`](receipt-manifest.json) (`schemaVersion: 1`).

| Receipt File | SHA-256 Digest | Scope & Invariants |
|---|---|---|
| [`backup-api27-receipt.json`](backup-api27-receipt.json) | `943dbb7a8161e5ff2111f0d655df14c85f20445c1fa5f5e458b24c454e516191` | Android 8.1 (API 27) `bmgr` cycle with `PersonaspeakStorageHarnessActivity`. Positive canary restored; Keystore ciphertext + DataStore metadata excluded under `fullBackupContent`. Runtime query returns `StoreOutcome.Unconfigured` with 0 bytes. |
| [`adapter-parser-receipt.json`](adapter-parser-receipt.json) | `6b9f66ad511cbd0b90ed516e0a315c301ce3dd2c42cc7d821652ba5d255b864f` | `AnthropicMessagesAdapter` & `extractTextFromResponse` on Android ART. Mode A (offline `HttpTransport` seam) + Mode B (live smoke test with ephemeral key lifecycle). Continuous stream logcat audit confirms 0 forbidden token leaks. |
| [`storage-egress-audit-receipt.json`](storage-egress-audit-receipt.json) | `8dd559b0de18c4aa5f6081094aac42340c7cf2c94f172016c71a16fdfde4ef16` | Sandbox storage mode checks (`0600`/`0700`) + byte scan showing 0 plaintext leaks. UID-scoped kernel socket sampler (`/proc/net/tcp{,6}` @ 100ms) proving all live outgoing traffic is TLS 1.3 on port 443 bound to `api.anthropic.com` (`160.79.104.10`). 0 third-party, 0 unencrypted connections. |

## Architectural & Governance Baseline

- **Provider Enablement**: The Anthropic provider adapter remains **structurally disabled by default** in production builds at Milestone 4 closeout (`FakeProvider` active in rewrite coordinator; adapter registered in DI but unselected).
- **Transport Binding**: `AnthropicMessagesAdapter` connects over HTTPS (`api.anthropic.com/v1/messages`), with TLS 1.3 observed in the recorded device run.
- **Milestone 5 Seam**: Live cloud network egress is strictly gated on explicit user configuration and opt-in in the Milestone 5 Settings & Onboarding UI graph.
