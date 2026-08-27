# Milestone 7 — Fresh-Install Journey & Release Evidence

**Status: SOURCE & HARNESS QUALIFIED; EMULATOR JOURNEY PENDING.**

Milestone 7 Slice A provides the verified JVM/Robolectric integration harness for the fresh-install journey across `:ime:app`:
- **Pristine installation baseline**: Clean DataStore, zero leaked credentials in private preferences or disk, bundled character defaults (Jeeves 🎩 with Polite mood), `ResolvingProvider` resolving `FakeProvider` understudy with zero network egress.
- **Onboarding & Settings surface**: "Get started with PersonaBoard" card guiding users through system IME enablement, character choice, and Brain connection with 48dp minimum interactive touch targets.
- **Session handoff & character picking**: Character selection (Dr. King Schultz 🎯 + Witty mood) in settings updates session state and propagates to subsequent keyboard initializations.
- **The Brain provider setup**: Secure credential configuration in DataStore and AES-256-GCM Keystore, with per-request on-demand decryption in `ResolvingProvider` and immediate memory zeroing (`SecretBytes.value.fill(0)`).
- **Full keyboard rewrite cycle in host app editor**:
  - `InputConnection` integration with `"Tea at six."`.
  - State machine progression: Resting -> Loading (with Cancel affordance) -> Review card with candidate rephrasing.
  - `↻ Again` retry triggering fresh rewrite evaluation.
  - `Use this` commit mutating host editor text (exact 1 mutation, verified).
  - `Dismiss` action resetting to resting state with 0 editor mutations.
- **RTL locale & layout pass**: `android:supportsRtl="true"` enabled, `LayoutDirection.Rtl` mirroring, logical `start`/`end` padding discipline.
- **Visual theme fidelity**: `PersonaSpeakTheme` high-contrast dark and light color tokens and surface contrast separation.

## Pending Live Emulator Qualification

Live device-level qualification remains pending a dedicated emulator run:
1. **Fresh AVD Install**: Pinned snapshot cold boot, clean install of release-grade APK.
2. **System IME Enablement**: Interactive enablement in system settings and setting PersonaSpeak as default IME.
3. **Emulator Screencaps**: Visual recording and screenshots of the onboarding, character selection, rewrite in host app editor, and RTL locale pass (`settings put global debug.force_rtl 1`).

## Machine-Derived Receipt

The finalized machine receipt is recorded in `journey-receipt.json`:
- **journey_steps_total**: 8
- **harness_steps_completed**: 8
- **pristine_baseline**: harness_verified
- **session_handoff**: harness_verified
- **editor_mutations**: 1 (on Apply), 0 (on Dismiss)
- **rtl_readiness**: harness_verified
- **theme_contrast**: harness_verified
