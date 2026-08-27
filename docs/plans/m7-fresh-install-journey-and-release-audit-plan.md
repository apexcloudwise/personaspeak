# Milestone 7 Plan: Fresh-Install Journey Recording, Release Audit, and Non-Author Verdict

**Parent Issue:** [#38](https://github.com/apexcloudwise/personaspeak/issues/38) (Milestone 7)  
**Tracking Issue:** [#109](https://github.com/apexcloudwise/personaspeak/issues/109)  
**Author:** Rei (Pixel Perfect Studios)  
**Date:** 2026-08-27  

---

## 1. Goal & Architecture Overview

Milestone 7 delivers the end-to-end operational proof of PersonaSpeak across a complete fresh install. Milestone 7 closes the loop on product functionality: taking a user from first launch through onboarding, keyboard enablement, default selection, character picking, brain configuration, and live text rewriting in a host application, while auditing privacy posture, network egress, and backup safety.

Following owner authorization for accelerated delivery (one PR per slice, plan doc riding with code, non-author exact-head review):

- **Slice A (Fresh-Install Journey Recording & RTL / Visual Fidelity):**
  - **Full User Journey Execution:** Verify end-to-end flow from pristine installation:
    1. Pristine baseline: Empty DataStore, unconfigured provider falling back cleanly to `FakeProvider`, default persona Jeeves (🎩), default mood Polite, zero disk writes or network calls.
    2. Onboarding card: "Get started with PersonaBoard" card on Settings Home guiding user through system IME enablement, character choice, and Brain connection.
    3. System IME enablement & default selection: Registration of `com.menny.android.anysoftkeyboard.SoftKeyboard` and IME lifecycle handoff.
    4. Character selection: Selection of character (e.g. Dr. King Schultz 🎯) and mood (Witty) in Persona Browser and in-memory session handoff.
    5. The Brain configuration: Secure provider setup in `ProviderSetupScreen` saving encrypted credentials to Keystore and non-secret config to DataStore, with on-demand runtime decryption in `ResolvingProvider`.
    6. Host app rewrite interaction: Active `InputConnection` on host editor with `"Tea at six."`, rewrite trigger, `Loading` state with `Cancel` affordance, `Review` state with candidate text, `↻ Again` retry, `Use this` guarded apply (exact single mutation), and `Dismiss` (zero mutation).
  - **RTL Locale Pass (Carried from #108 Review):** Complete RTL layout pass ensuring `LayoutDirection.Rtl`, `start`/`end` padding discipline, back button mirroring, and horizontal chip flow integrity under RTL environments (e.g. Arabic/Hebrew).
  - **Visual & Theme Verification:** Verification of high-contrast tokens and surface separation across dark and light themes in `PersonaSpeakTheme`.
  - **Machine-Readable Journey Receipt:** Machine-derived `journey-receipt.json` and human-readable `docs/evidence/milestone-7/README.md`.
  - **Automated Verifier:** Fail-closed `verify-milestone-7.sh` script and test suite `verify-milestone-7-test.sh`.

- **Slice B (Release Privacy, Network Egress & Backup Audit):**
  - **Network Egress Audit:** Exhaustive code and socket-level audit proving zero network egress on keystrokes/typing, with network egress strictly gated on explicit user opt-in in The Brain settings.
  - **Backup & Storage Exclusion Audit:** Verification of AES-256-GCM ciphertext and DataStore exclusion under Android backup rules (`fullBackupContent` and `dataExtractionRules`).
  - **Privacy Copy Verification:** Verification that privacy claims in UI copy match runtime code behavior (ADR-0005, ADR-0009).
  - **Non-Author Verdict:** Formal non-author review and acceptance unblocking Milestone 8.

---

## 2. Slice A Specification: Fresh-Install Journey & Fidelity

### 2.1 Journey Matrix & State Transitions

| Journey Stage | Initial State | User Action / Trigger | Final State / Result | Verification Gate |
|---|---|---|---|---|
| **1. Fresh Launch** | Pristine / uninitialized | App installed | DataStore empty, default persona Jeeves (🎩), `FakeProvider` fallback | `FreshInstallJourneyIntegrationTest` |
| **2. Onboarding** | Settings Home | View "Get started" card | Step 1 (Enable), Step 2 (Pick character), Step 3 (Connect brain) visible | Touch floor 48dp, navigation links verified |
| **3. IME Enablement** | Disabled in system | User enables in system settings | `SoftKeyboard` component enabled and selected as active IME | Manifest identity & IME component bound |
| **4. Character Choice** | Active: Jeeves (🎩) | Select Dr. King Schultz (🎯) + Witty | Session state updated, Settings Home reflects new character | `PersonaSpeakSessionState` handoff verified |
| **5. The Brain Setup** | Unconfigured (`FakeProvider`) | Enter key + select model | DataStore + Keystore saved (`StoreOutcome.Success`), `ResolvingProvider` wired | AES-GCM encryption & on-demand decryption |
| **6. Keyboard Strip** | IME active on host app | Host editor text: `"Tea at six."` | Resting strip shows 🎯 Dr. Schultz, Witty, Settings button | 48dp floor, high-contrast borders |
| **7. Rewrite Cycle** | Resting | Tap "Rewrite" | `Loading` (with Cancel) -> `Review` card with candidate rephrasing | PromptBuilder golden contract verified |
| **8. Mutation & Dismiss** | Review card | Tap `Use this` / `Dismiss` | `Use this`: host text replaced (1 mutation). `Dismiss`: host text unchanged (0 mutations). | `InputConnection` text assertions |

### 2.2 RTL Locale & Accessibility Contract

- `android:supportsRtl="true"` enabled in `AndroidManifest.xml` (ledgered in `UPSTREAM-MODIFIED.md`).
- All padding, margins, and alignments use `start`/`end` rather than `left`/`right`.
- Under `LayoutDirection.Rtl`:
  - Navigation back arrows (`←` / `Icons.AutoMirrored.Filled.ArrowBack`) mirror to point rightward (`→`).
  - Horizontal chip rows, picker grids, and action buttons layout start-to-end matching locale reading direction.
  - Dialog dismiss/close buttons position correctly at logical start/end.

---

## 3. Slice B Preview: Release Audit & Privacy Posture

- **Egress Boundary:** Network requests are strictly constrained to user-configured endpoints (`api.anthropic.com`, `openrouter.ai/api/v1`, or user-specified custom Base URL).
- **Zero-Keystroke Egress:** Keystrokes, dictionary queries, and text inputs never trigger network calls.
- **Memory Hygiene:** API keys zeroed in memory via `SecretBytes.value.fill(0)` and decrypted only per-request.
- **Backup Safety:** Keystore ciphertext and DataStore preferences excluded from cloud backup to prevent orphan ciphertext restoration.

---

## 4. Acceptance Criteria & Definition of Done

- [ ] **Milestone 7 Plan Landed:** Full specification of Slice A and Slice B.
- [ ] **Fresh-Install Integration Test:** Comprehensive end-to-end test suite passing in CI.
- [ ] **RTL Locale Pass & Theme Fidelity Evidenced:** RTL rendering, dark/light contrast verified.
- [ ] **Milestone 7 Evidence & Receipt:** `docs/evidence/milestone-7/README.md` and `journey-receipt.json` minted and valid.
- [ ] **Automated Verifier:** `verify-milestone-7.sh` and its test suite `verify-milestone-7-test.sh` execute cleanly and fail closed on violations.
- [ ] **Clean Git Tree & Patch Notes:** `PATCHNOTES.md` updated, upstream ledger intact, CI checks passing green.
