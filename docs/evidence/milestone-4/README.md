# Milestone 4 — Secure Provider Configuration & Persistence Evidence Receipts

**Status: MODE A QUALIFIED; LIVE DEVICE QUALIFICATION PENDING.** Slice 3 Mode A offline ART parser validation and compile gates are qualified. Device-level qualification (API 27 legacy backup exclusion and Mode B live cloud egress) remains pending a dedicated device fixture run outside this sandbox.

## Evidence Manifest & Authority

The authority manifest is [`receipt-manifest.json`](receipt-manifest.json) (`schemaVersion: 1`).

| Receipt File | Scope & Invariants |
|---|---|
| [`adapter-parser-receipt.json`](adapter-parser-receipt.json) | `AnthropicMessagesAdapter` & `extractTextFromResponse` on Android ART via `PersonaspeakAdapterHarnessActivity`. Mode A (offline `HttpTransport` seam validation, JSON escaping/Unicode handling, and `SecretBytes.fill(0)` zeroing: PASS). |

## Architectural & Governance Baseline

- **Provider Enablement**: The Anthropic provider adapter remains **structurally disabled by default** in production builds at Milestone 4 closeout (`FakeProvider` active in rewrite coordinator; adapter registered in DI but unselected).
- **Transport Binding**: `AnthropicMessagesAdapter` connects over HTTPS (`api.anthropic.com/v1/messages`).
- **Milestone 5 Seam**: Live cloud network egress is strictly gated on explicit user configuration and opt-in in the Milestone 5 Settings & Onboarding UI graph.
