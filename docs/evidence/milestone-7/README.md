# Milestone 7 — Fresh-Install Journey & Release Evidence

**Status: QUALIFIED.** Run `20260827T084800Z` at repo head `reicodes-pixelperfect/m7-slice-a`.

The unified PersonaSpeak build has been verified end-to-end on a simulated fresh-install environment:
- Pristine installation state baseline: clean DataStore, zero leaked credentials in private preferences or disk, bundled character defaults (Jeeves 🎩 with Polite mood), `ResolvingProvider` resolving `FakeProvider` understudy with zero network egress.
- Onboarding & Settings surface: "Get started with PersonaBoard" card guiding users through system IME enablement, character choice, and Brain connection with 48dp minimum interactive touch targets.
- Session handoff & character picking: Character selection (Dr. King Schultz 🎯 + Witty mood) in settings updates session state and propagates to subsequent keyboard initializations.
- The Brain provider setup: Secure credential configuration in DataStore and AES-256-GCM Keystore, with per-request on-demand decryption in `ResolvingProvider` and immediate memory zeroing (`SecretBytes.value.fill(0)`).
- Full keyboard rewrite cycle in host app editor:
  - InputConnection integration with `"Tea at six."`.
  - State machine progression: Resting -> Loading (with Cancel affordance) -> Review card with candidate rephrasing.
  - `↻ Again` retry triggering fresh rewrite evaluation.
  - `Use this` commit mutating host editor text (exact 1 mutation, verified).
  - `Dismiss` action resetting to resting state with 0 editor mutations.
- RTL locale & layout pass: `android:supportsRtl="true"`, `LayoutDirection.Rtl` mirroring, logical `start`/`end` padding discipline.
- Visual theme fidelity: `PersonaSpeakTheme` high-contrast dark and light color tokens and surface contrast separation.

## Machine-Derived Receipt

The finalized machine receipt is recorded in `journey-receipt.json`:
- **journey_steps_completed**: 8/8
- **pristine_baseline**: verified
- **session_handoff**: verified
- **editor_mutations**: 1 (on Apply), 0 (on Dismiss)
- **rtl_locale_pass**: verified
- **theme_contrast**: verified
- **privacy_posture**: verified (zero un-gated network egress, memory zeroing confirmed)
