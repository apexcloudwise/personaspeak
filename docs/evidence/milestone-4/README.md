# Milestone 4 — Secure Provider Configuration & Persistence Evidence Receipts

**Status: MODE A QUALIFIED; MODE B PENDING DEVICE FIXTURE QUALIFICATION.** Milestone 4 verification completed across slices 1 and 2 in accordance with `#89` and the approved plans `#91`, `#94`. Slice 3 Mode A offline ART parser validation and compile gates are qualified. Mode B live cloud egress qualification remains pending a dedicated device fixture run outside this sandbox.

## Evidence Manifest & Authority

The authority manifest is [`receipt-manifest.json`](receipt-manifest.json) (`schemaVersion: 1`).

| Receipt File | Scope & Invariants |
|---|---|
| [`backup-api27-receipt.json`](backup-api27-receipt.json) | Android 8.1 (API 27) `bmgr` cycle with `PersonaspeakStorageHarnessActivity`. Positive canary restored; Keystore ciphertext + DataStore metadata excluded under `fullBackupContent`. Runtime query returns `StoreOutcome.Unconfigured` with 0 bytes. |
| [`adapter-parser-receipt.json`](adapter-parser-receipt.json) | `AnthropicMessagesAdapter` & `extractTextFromResponse` on Android ART via `PersonaspeakAdapterHarnessActivity`. Mode A (offline `HttpTransport` seam validation, JSON escaping/Unicode handling, and `SecretBytes.fill(0)` zeroing: PASS). |

## Architectural & Governance Baseline

- **Provider Enablement**: The Anthropic provider adapter remains **structurally disabled by default** in production builds at Milestone 4 closeout (`FakeProvider` active in rewrite coordinator; adapter registered in DI but unselected).
- **Transport Binding**: `AnthropicMessagesAdapter` connects over HTTPS (`api.anthropic.com/v1/messages`).
- **Milestone 5 Seam**: Live cloud network egress is strictly gated on explicit user configuration and opt-in in the Milestone 5 Settings & Onboarding UI graph.
