# Settings screens report — six real Compose screens

**Branch:** `feat/settings-screens` (forked from `feat/graft-foundation`)
**Date:** 2026-07-21
**Scope:** the six settings routes in `:app` go from placeholders to real Compose screens: `SettingsHome`, `PersonaBrowser`, `PersonaDetail`, `AiProviders`, `RewriteBehaviour`, `Privacy`. No routes were added, renamed, or re-argued; the contract in `Routes.kt` is untouched. Onboarding routes, `android/keyboard/`, and everything outside `app/` are deliberately untouched.

## What landed

All new code lives under `android/app/src/main/kotlin/.../app/ui/`:

- **`data/SamplePersonas.kt`** — a hardcoded `List<PersonaDisplay>` of the four sample personas (Jeeves default, Sir Humphrey, Dr. Schultz, Bachchan), each pairing a pure-Kotlin `Persona` (from `:core-personas`, left untouched per module law) with UI display metadata: nav id, emoji, tagline, default flag. `byId(id)` powers the detail screen.
- **`data/SampleProviders.kt`** — a hardcoded `List<ProviderDisplay>` (Gemini active, Claude/OpenAI/OpenRouter unconfigured, Local Instance) plus an `active` accessor. UI-facing projection only; the real registry lives in `:core-providers`.
- **`components/PersonaSpeakTopBar.kt`** — the sticky brand bar (⌘ + "PersonaSpeak" wordmark in primary) with optional `leading`/`trailing` slots and a `TopBarCloseAction` glyph.
- **`components/SettingsListRow.kt`** — glass-style settings row: leading emoji on a primary-tinted disc, title + subtitle, trailing chevron.
- **`components/SectionLabel.kt`** — the uppercase tracking-wider section header, plus a reusable `GlassCard` container.
- **`screens/settings/SettingsHomeScreen.kt`** — the four navigable rows (Personas, Rewrite behaviour, AI provider, Privacy), grouped under section labels.
- **`screens/settings/PersonaBrowserScreen.kt`** — two-column bento grid of persona cards with teal glow accents and a "Default" pill on Jeeves; cards navigate to `personaDetail(id)`.
- **`screens/settings/PersonaDetailScreen.kt`** — hero (emoji + name + context), speech-patterns list, vocabulary pills (horizontal scroll), sample lines, fixed footer ("Try this persona" / "Set as default"). Unknown `personaId` renders a not-found panel instead of crashing.
- **`screens/settings/AiProvidersScreen.kt`** — active-provider card, masked API-key field (charcoal `#2D333B`, monospace), available-providers list, two global toggle rows.
- **`screens/settings/RewriteBehaviourScreen.kt`** — radio group: "Ask before replacing" (default) vs "Replace immediately".
- **`screens/settings/PrivacyScreen.kt`** — data-handling disclosure. **Deviation — see below.**

### Wiring

`PersonaSpeakNavHost.kt` — the six settings `composable(...)` entries now call the real screen composables instead of `Placeholder`. The `personaId` arg is read from `entry.arguments?.getString(Screen.PERSONA_ID_ARG)` exactly as the placeholder did; no `SavedStateHandle` introduced. `Placeholder` is retained for the five onboarding routes, which are out of scope.

## Deviations from the mockups

These are intentional and called out so a reviewer can disagree precisely.

1. **Privacy screen (load-bearing).** The mockup headline is "What we store / Nothing." with four crisply-true claims including "Encrypted in Android Keystore. Hardware-level security ensures we can never see or extract your credentials." ADR-0005 (`docs/adr/0005-privacy-posture-fork-audit.md`, Proposed→Accepted on merge) finds that copy unsafe for the fork: AnySoftKeyboard now ships in the tree, a predictive keyboard that stores data by design, and the storage/security posture has not been audited end to end. Per ADR-0005 and VOICE.md rule 6, the privacy screen ships **honest, specific, vaguer-but-true copy** instead:
   - Headline is "Where your text goes", not "Nothing."
   - "Your text" — sent to the chosen provider on request; we run no servers; provider retention is the provider's policy.
   - "Your API key" — stored on-device via standard app storage; we do **not** claim hardware-backed keystore until that is audited.
   - "On-device data" — acknowledges the keyboard keeps learned words/persona/settings locally; stays on device; clearing is not yet wired.
   - "Telemetry & analytics" — off in this build; no stronger "no tracking, ever" claim until the audit completes.
   - A logcat disclosure note covers on-device observability by system/root READ_LOGS holders.

   No jokes on this screen; it is load-bearing plain text.

2. **Settings home scope.** The mockup home shows ~11 rows across five sections. Only four map to routes this slice owns (Personas, AI provider, Rewrite behaviour, Privacy). The rest (default mood, API key, usage, languages, glide typing, autocorrect, theme, height, read-the-source) are keyboard-fork concerns with no nav routes; they are omitted rather than drawn dead. The home banner's mockup line ("PersonaSpeak is your default keyboard") is also softened to a true brand line because default-keyboard status can't be verified from this screen yet.

3. **Icons.** The mockups use Material Symbols Outlined; `:app` depends only on `compose.material3` (no `material-icons-extended`). Where a core icon was unavailable (`Icons.Filled.Close`), the affordance uses a Unicode glyph instead (e.g. `✕`). This matches the emoji-forward mockup aesthetic and avoids a new dependency.

4. **State is local, not persisted.** Toggles (auto-retry, context-window), radio selection (rewrite behaviour), and API-key visibility are held in `remember { mutableStateOf }` only. Wiring them to real prefs/keystore/registry is out of scope for the screens slice.

5. **Persona-detail footer and "Set as default".** "Set as default" renders disabled (no persistence wired); "Try this persona" is a no-op button. The footer is laid out below the scroll rather than Scaffold-pinned, since no Scaffold exists in the activity yet.

## Build proof

From `android/`:

```
./gradlew :app:assembleDebug --no-daemon --console=plain \
    -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

(`jvmToolchain(17)` requires JDK 17; the keg-only Homebrew install is not auto-detected, so the installations path is supplied explicitly.)

Result (final iteration):

```
> Task :app:compileDebugKotlin
> Task :app:assembleDebug
BUILD SUCCESSFUL in 15s
45 actionable tasks: 6 executed, 39 up-to-date
```

Two compile errors were hit and fixed during the build: `Icons.Filled.Close` is not in `material-icons-core` (swapped for a glyph), and `Modifier.clickable(onSelect)` resolved against the wrong overload (changed to `clickable(onClick = onSelect)`).

## Verification not performed

- **No device run.** A green debug build is the agreed proof for this slice; the screens were not exercised on an emulator or via `createAndroidComposeRule`.
- **No UI tests.** The `:app` module has no test infrastructure (`testImplementation`/`androidTestImplementation` are absent from `app/build.gradle.kts`). Adding a Compose UI test harness is a separate concern; flagged as a follow-up.

## Follow-ups (not blocking)

1. **Persisted settings state.** Rewrite-behaviour choice and the two provider toggles want a real prefs store; today they reset on process death.
2. **Real persona loading.** `SamplePersonas` is a placeholder for the `Persona.fromYaml` pipeline; swap when the loader is wired.
3. **Real provider registry + keystore.** `SampleProviders` and the masked key field are UI scaffolding; the API-key field should read/write through `:core-providers` and Android's keystore once audited (ADR-0005).
4. **Scaffold + edge-to-edge.** The status-bar seam noted in the foundation report still applies; adding a `Scaffold` would also let the detail footer pin properly.
5. **Material icons.** If the design wants the real Material Symbols set rather than glyphs, add `material-icons-extended` (it's a large artifact; worth a deliberate decision).
