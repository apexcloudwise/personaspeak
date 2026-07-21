# Onboarding screens report — five real Compose destinations

**Branch:** `feat/onboarding-screens` (forked from `feat/graft-foundation`)
**Date:** 2026-07-21
**Scope:** replace the five onboarding placeholders in `PersonaSpeakNavHost.kt` with real, navigable Compose screens built from the Stitch mockups. No routes added, renamed, or removed; no `settings/*` route touched; `android/keyboard/` untouched.

The authoritative spec for this work is the Stitch mockups themselves at `/Users/devkiran/workspace/personaspeak/stitch_personaspeak_ui_mockups/` (`onboarding_welcome`, `onboarding_setup`, `onboarding_ai_selection`, `onboarding_api_key`, `onboarding_demo`; `code.html` in each). The mockup-named implementation-spec doc does not exist in this repo; the `code.html` files are the source of truth for copy, color, spacing, and structure.

## What landed

All new code is under `android/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/app/ui/`.

### Shared components — `components/Components.kt`

A handful of atoms, not a library. Anything used only once is kept inside its screen.

- **`PrimaryButton(text, onClick, modifier, enabled)`** — full-width teal→cyan gradient CTA (Primary → SecondaryContainer, `Brush.linearGradient`). Hand-rolled `Box` rather than M3 `Button` because `Button`'s color slots don't take a gradient. 52dp min height (48dp touch target + breathing room), `shapes.small` (12dp) corners, `OnPrimary` text. When disabled, the brush collapses to flat `SurfaceVariant`. This is the button on every onboarding screen.
- **`SecondaryTextButton(text, onClick, modifier)`** — low-emphasis `OnSurfaceVariant` text link for skip paths (Welcome, ApiKey).
- **`PillBadge(text, modifier, leadingIcon, containerColor, contentColor)`** — pill tag. Defaults to PrimaryContainer/OnPrimaryContainer; retinted for the AI-Selection "Soon" badge.
- **`PersonaSpeakTopAppBar(modifier)`** — brand bar (🤖 + "PersonaSpeak" in Primary). Drawn as a plain `Row`; screens own their window insets, and the back-button contract stays explicit per screen.

### Screens — `onboarding/`

| # | File | Public signature | Nav handoff |
|---|------|------------------|-------------|
| 1.1 | `WelcomeScreen.kt` | `WelcomeScreen(onGetStarted, onSkipSetup)` | Get started → `onboarding/setup`; skip → `settings/home` |
| 1.2 | `SetupScreen.kt` | `SetupScreen(onContinue)` | Continue → `onboarding/ai-selection` |
| 1.3 | `AiSelectionScreen.kt` | `AiSelectionScreen(onContinue)` | Continue → `onboarding/api-key` |
| 1.4 | `ApiKeyScreen.kt` | `ApiKeyScreen(onContinue, onSkip)` | Test and continue / Skip → `onboarding/demo` |
| 1.5 | `DemoScreen.kt` | `DemoScreen(onComplete)` | Use this → `settings/home` (back stack cleared) |

### NavHost wiring — `nav/PersonaSpeakNavHost.kt`

The five onboarding `composable(...) { Placeholder(...) }` entries now call the real screen composables with `navController.navigate(...)` lambdas. No route strings changed; the `Screen` sealed class is untouched. The `Placeholder` composable is retained for the six still-unbuilt `settings/*` routes. Demo→`settings/home` uses `popUpTo(graph.startDestinationId) { inclusive = true }` so Back never returns the user into a finished onboarding flow.

## Real vs. stubbed, per screen

The principle: **a screen does the real thing its copy promises, or it says so out loud.**

**1.1 Welcome — all presentational, no stubs.** Hero panel is a glass card with an emoji/keyboard-grid placeholder for the illustration. The illustration asset is not vendored anywhere in the repo; this is called out as a follow-up, not hidden. Copy, persona chips (🎩Jeeves 🏛️Humphrey 🤠Schultz 🎬Bachchan), reassurance badges, and both nav paths are real.

**1.2 Setup — real IME intents, no fakes.** This screen's job is to actually move the user toward an enabled keyboard, so it wires the real platform calls:
- Step 1 button fires `Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)` with `FLAG_ACTIVITY_NEW_TASK`.
- Step 2 button fires `InputMethodManager.showInputMethodPicker()`.
- `imeEnabled` is computed from `imm.getEnabledInputMethodList().any { it.packageName == context.packageName }` and recomputed on every `ON_RESUME` via a `DisposableEffect` + `LifecycleEventObserver`.
- Step 2 is dimmed (alpha 0.6) and its button disabled until step 1 reads as done; the Continue CTA stays enabled regardless so the screen never dead-ends.

**Known honest limitation:** `imeEnabled` matches the *app* package. It becomes correct the moment `:keyboard` ships an `InputMethodService` whose package resolves to the same installed application id. The `PersonaBoardService` stub restored in the prior PR is what makes the system picker non-empty.

**1.3 AI Selection — static provider list, by design.** The five entries (Gemini/Claude/OpenAI/OpenRouter/On this phone) are a hardcoded `data class Provider` list — there is no provider registry in `core-providers` yet to bind against. Gemini is selected by default; "On this phone" is disabled with a "Soon" badge and a 0.6 alpha. Selection state is local UI state. This is the agreed shape until the registry ADR lands.

**1.4 API Key — real field, no persistence.** The key field is a real `OutlinedTextField` with `PasswordVisualTransformation`, a monospace `TechnicalTextStyle`, an eye toggle, and a non-empty "Key looks valid" heuristic. The "Open AI Studio" button fires a real `ACTION_VIEW` intent to `https://aistudio.google.com`. **The key is not persisted** — that is flagged in code as `// TODO(ADR-0005): persist apiKey into Android Keystore once that module lands`. Keystore work is out of scope for this PR; the field is session-only on purpose. The "Test and continue" CTA does not actually call the provider (there is no provider wired at this layer); it advances. That is honest given the copy ("Test and continue" is the button label from the mockup; real validation rides in with the Keystore/provider PR).

**1.5 Demo — fully simulated, no live provider.** A self-contained fake: a messaging-app chrome, a chat thread with the canned `cant make it sorry` draft, a persona strip (🎩 Jeeves / polite), and a decorative 4-row keyboard. Tapping the transform FAB toggles a result overlay with the canned rewrite (`I regret to report, sir, that circumstances have conspired against my attendance this evening.`). "Use this" completes onboarding. The canned text is marked in code as the swap point — `// TODO(onboarding-demo): replace canned REWRITE with a live CompletionProvider.rewrite() call`. No network, no provider; the onUse/onAgain wiring stays as-is when the live call lands.

## Deviations from the mockups (and why)

- **Material icons.** The mockups reference `smart_toy`, `keyboard`, `toggle_on`, `visibility`/`visibility_off`, `warning`, `open_in_new`, `done_all`, `arrow_forward`, `videocam`, `call`. Only a subset of these is in `material-icons-core` (Check, CheckCircle, Info, Lock, ArrowBack, Refresh, MoreVert, etc.). AGENTS.md discourages adding a dependency where code will do, so `material-icons-extended` was **not** added; the missing glyphs are rendered as emoji (🤖 ⌨️ 👁️ 🙈 📹 📞 → ✕). Functional behavior is identical; only the glyph source differs.
- **Button text size.** Mockups use headline-md on the Welcome CTA and label-md elsewhere. `PrimaryButton` uses one consistent style (label-md SemiBold) across all screens for a coherent product voice. Called out, not silent.
- **Welcome hero illustration.** Replaced with an emoji + keyboard-grid glass panel. The real illustration asset is not in the repo; follow-up, not a regression.
- **Setup system warning dialog.** The mockup's system permission dialog is not reproduced (it cannot be triggered from an instrumented test and shouldn't be spoofed). The privacy callout describes the warning instead. This matches the "card describing it is fine" allowance.
- **ApiKey "Test and continue".** The mockup label is kept verbatim; the button advances rather than validating, because there is no provider wired at this layer. Honest copy-promise gap, flagged via the ADR-0005 TODO.

## Build proof

From `android/`:

```
./gradlew :app:assembleDebug --no-daemon --console=plain \
    -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

Result (final, after NavHost wiring):

```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 8s
45 actionable tasks: 45 up-to-date
```

`jvmToolchain(17)` needs JDK 17 and the project's Gradle auto-detection does not find the keg-only Homebrew install, so the installations path is supplied explicitly. A build was run after each screen, not only at the end.

## Device run

Installed on `emulator-5554` (`biz.pixelperfectstudios.personaspeak/.app.MainActivity`) and walked the full chain by UI-automation taps:

Welcome → (Get started) → Setup → (Continue) → AI Selection → (Continue) → API Key → (Test and continue) → Demo → (transform FAB → result overlay) → (Use this) → `settings/home` placeholder.

Each screen rendered its expected copy and no `FATAL EXCEPTION` appeared in logcat at any step. The Demo result overlay showed the canned rewrite with the "JEEVES · POLITE" header. The Demo→`settings/home` transition correctly cleared the back stack (Back from the placeholder did not return to onboarding). No screenshots committed.

## Follow-ups (not blocking)

1. **Keystore persistence (ADR-0005).** The API key field is session-only until the Keystore module lands; the TODO in `ApiKeyScreen.kt` is the swap point.
2. **Live provider in Demo.** Replace the canned `REWRITE` with a real `CompletionProvider.rewrite()` call once the registry and the IME→provider bridge exist. The overlay's onUse/onAgain contract is unchanged.
3. **Illustration assets.** The Welcome hero and the AI-Selection neural-net panel are emoji/grid placeholders. Real art lands when the design system ships it.
4. **Status-bar / edge-to-edge seam.** Inherited from the foundation report; still applies. Screens use `windowInsetsPadding` for status/nav bars locally, but the activity theme seam is a separate fix.
5. **Provider registry.** AI-Selection's hardcoded list folds into the real `core-providers` registry when that ADR lands.
