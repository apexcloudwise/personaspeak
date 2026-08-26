# Milestone 6 Plan: Visual Fidelity, Accessibility, and Asset Rights

**Parent Issue:** [#38](https://github.com/apexcloudwise/personaspeak/issues/38) (Milestone 6)  
**Tracking Issue:** [#106](https://github.com/apexcloudwise/personaspeak/issues/106)  
**Author:** Rei (Pixel Perfect Studios)  
**Date:** 2026-08-27  

---

## 1. Goal & Architecture Overview

Milestone 6 brings PersonaSpeak's visual presentation, accessibility, reach, and legal compliance to production grade across the single-APK architecture. Every shipped asset must carry verified redistribution provenance; fonts and portraits must satisfy non-negotiable legal gates; and the entire UI surface (persona strip, pickers, The Brain settings, and onboarding) must render legibly and interactively across dark mode, landscape, font scaling (up to 200%), RTL environments, and constrained displays.

Following owner authorization for accelerated delivery (one PR per slice, plan doc riding with code, non-author exact-head review):

- **Slice A (Asset Rights & Licensing):**
  - Font licensing audit: Record SIL Open Font License (OFL) 1.1 notices and attribution for Outfit and Inter typography specifications, documenting the fallback behavior to system typography when unbundled.
  - Persona portrait rights audit: Full provenance record for all 4 bundled personas (`jeeves`, `sir-humphrey`, `dr-schultz`, `amitabh-bachchan`), formally establishing the rights-cleared Unicode emoji representation policy ("🎩", "🏛️", "🎯", "🎬") in place of un-cleared third-party actor/character photographs.
  - Raster asset audit: Explicit exclusion of un-cleared prototype/Stitch rasters.
  - Asset Rights Ledger (`docs/design/ASSET-RIGHTS.md`): Central authoritative provenance manifest mapping every visual asset, font, and persona representation to its redistribution status and legal text.
  - Automated Verifier (`verify-asset-rights.sh` + test suite): Fail-closed static verification ensuring 100% manifest coverage, zero unapproved first-party rasters, and intact license notices.

- **Slice B (Visual, Theme, Accessibility & Edge Fidelity):**
  - Dark Mode Fidelity: Contrast optimization across the persona strip, resting pills, active selections, result card, The Brain settings, and onboarding card.
  - Responsive & Landscape Support: Ensure height budgets, candidate row integration, and dialog layouts remain non-overlapping and accessible in landscape orientation.
  - Accessibility Pass: Strict enforcement of Android's 48dp minimum touch target floor, semantic `contentDescription` attributes on all icon/action elements, and screen-reader navigable hierarchies.
  - Edge Cases & Scaling: Long persona name truncation/wrapping ("Sir Humphrey Appleby"), 200% system font scaling layout stability, and RTL layout readiness (`android:supportsRtl="true"`).

---

## 2. Slice A Specifications: Asset Rights & Provenance Ledger

### 2.1 Asset Inventory & Licensing Matrix

| Asset Category | Target / Component | Source & License | Shipped Form & Rights Determination |
|---|---|---|---|
| **Typography (Display)** | Outfit Font Family | OFL-1.1 (The Outfit Project Authors) | Specified in UI design tokens; system font fallback active unless bundled with OFL text. |
| **Typography (Body/UI)** | Inter Font Family | OFL-1.1 (Rasmus Andersson) | Specified in UI design tokens; system font fallback active unless bundled with OFL text. |
| **Icons & Symbols** | UI Action Glyphs (`←`, `✕`, `⌄`, `↻`, `•`) | Unicode / Material (Apache-2.0) | Standard Unicode typographic glyphs and Material Icons with Apache-2.0 compliance. |
| **Persona: Jeeves** | `bundled:jeeves` | P.G. Wodehouse literary canon | Fictional character in public domain; represented by Unicode emoji "🎩" (Top Hat). No copyrighted broadcast media used. |
| **Persona: Sir Humphrey** | `bundled:sir-humphrey` | BBC *Yes Minister* (Jay/Lynn) | Fictional character; broadcast imagery not cleared for commercial redistribution; represented by Unicode emoji "🏛️" (Classical Building). |
| **Persona: Dr. Schultz** | `bundled:dr-schultz` | *Django Unchained* (Tarantino/Weinstein/Columbia) | Fictional character; film stills not cleared for commercial redistribution; represented by Unicode emoji "🎯" (Bullseye). |
| **Persona: Amitabh Bachchan** | `bundled:amitabh-bachchan` | Living public cinema figure | Stylistic homage declared in persona `notes`; photographic likeness not cleared for redistribution; represented by Unicode emoji "🎬" (Clapper Board). |
| **Keyboard Base Assets** | Inherited AnySoftKeyboard drawables | Apache-2.0 / MIT | Pristine upstream assets tracked in `android/keyboard/LICENSE` and `android/keyboard/UPSTREAM.md`. |

### 2.2 Verification Gate

- `android/scripts/verify-asset-rights.sh`: Validates that every persona defined in `personas/*.yaml` has an exact row in `docs/design/ASSET-RIGHTS.md`, checks that zero unauthorized `.png`/`.jpg`/`.webp`/`.svg` rasters exist in first-party packages (`:personaspeak-ui`, `:personaspeak-providers`, `:personaspeak-data`, `:core-personas`, `:core-providers`), and verifies that OFL-1.1 and Apache-2.0 notices are complete and intact.
- `android/scripts/tests/verify-asset-rights-test.sh`: Contract test suite asserting positive control, unrecorded persona detection, unauthorized raster rejection, stale manifest row detection, and grep failure handling.

---

## 3. Slice B Preview: Visual, Theme & Accessibility Fidelity

### 3.1 Dark Mode & Contrast Tokens
- High-contrast selected chip borders in dark theme to avoid low-contrast "dark pill on dark surface" issue noted in Stitch exploration mockup `13-dark-mode-result.png`.
- Surface background tokens matched to Material 3 dark color scheme across `RewritePanel`, `SettingsHomeScreen`, `PersonaBrowserScreen`, `PersonaDetailScreen`, and `ProviderSetupScreen`.

### 3.2 Accessibility & Responsive Geometries
- Explicit `Modifier.semantics { contentDescription = "..." }` on icon-only and emoji buttons.
- Minimum 48dp touch targets verified for all clickable elements (chips, clear buttons, dialog close, browse actions).
- Manifest update: `android:supportsRtl="true"` added to `android/keyboard/ime/app/src/main/AndroidManifest.xml` and ledgered in `UPSTREAM-MODIFIED.md`.
- Layout resilience test against long persona names and 200% font scale.

---

## 4. Acceptance Criteria & Definition of Done

- [ ] **ASSET-RIGHTS.md Landed:** Comprehensive asset provenance, redistribution rights, and full license texts for fonts, icons, and personas.
- [ ] **Automated Verifier:** `verify-asset-rights.sh` and its test suite `verify-asset-rights-test.sh` execute cleanly and fail closed on violations.
- [ ] **Clean Asset Tree:** No unrecorded raster images or un-cleared photographic portraits in first-party modules.
- [ ] **Patch Notes & Upstream Ledger:** Complete entries in `PATCHNOTES.md` and `UPSTREAM-MODIFIED.md` (if upstream files touched).
- [ ] **Full Quality Gates:** Unit tests across all projects pass cleanly; all script verifiers pass.
