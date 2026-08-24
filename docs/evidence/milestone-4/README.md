# Milestone 4 — Secure Provider Configuration & Persistence Evidence

**Status: SOURCE & COMPILE SCAFFOLDING QUALIFIED; LIVE DEVICE QUALIFICATION PENDING.**

Milestone 4 implementation and verifiable code scaffolding are landed:
- On-device secure random generation in `PersonaspeakStorageHarnessActivity` (`ACTION_SEED`).
- Compiling fail-closed debug harness in `PersonaspeakAdapterHarnessActivity` with `SecretBytes.fill(0)` memory zeroing.
- Static no-secret-logging scan and clean `./gradlew :ime:app:compileDebugKotlin` compilation enforced in CI via `verify-milestone-4.sh`.

## Pending Device Qualification Receipts

Device-level qualification receipts remain pending a dedicated physical device / AVD execution pass outside this sandbox:
1. **API 27 Legacy Backup Exclusion**: Verification of Keystore ciphertext and DataStore metadata exclusion under `fullBackupContent` via `bmgr`.
2. **ART Response Parser Validation (Mode A)**: Execution of `PersonaspeakAdapterHarnessActivity` against synthetic payloads on Android ART.
3. **Live Egress Smoke Test & Socket Audit (Mode B)**: Verified single-endpoint TLS 1.3 network egress to `api.anthropic.com` with zero leaks.

## Architectural & Governance Baseline

- **Provider Enablement**: The Anthropic provider adapter remains **structurally disabled by default** in production builds at Milestone 4 closeout (`FakeProvider` active in rewrite coordinator; adapter registered in DI but unselected).
- **Transport Binding**: `AnthropicMessagesAdapter` connects over HTTPS (`api.anthropic.com/v1/messages`) with closed error taxonomy.
- **Milestone 5 Seam**: Live cloud network egress is strictly gated on explicit user configuration and opt-in in the Milestone 5 Settings & Onboarding UI graph.
