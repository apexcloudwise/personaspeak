# App foundation report — theme + navigation plumbing

**Branch:** `feat/app-theme-and-nav` (forked from `feat/vendor-anysoftkeyboard`, PR #18)
**Date:** 2026-07-21
**Scope:** the `:app` module's Compose theme and Navigation Compose skeleton. No real screens; this is the foundation that screen-implementation workers build against.

## What landed

### Theme — `android/app/src/main/kotlin/.../app/ui/theme/`

- **`Color.kt`** — every color token from `personaspeak_design_system/DESIGN.md` as a `Color` val, PascalCased to match the token names (`Background`, `Surface`, `SurfaceContainer`, `Primary`, `OnPrimary`, `PrimaryContainer`, `Secondary`, `Tertiary`, `Error`, `Outline`, `OutlineVariant`, plus the container tiers and the `*Fixed*`/`*FixedDim*` tones). ~50 tokens total.
- **`Type.kt`** — a Material3 `Typography` populated from DESIGN.md's type scale:

  | M3 slot        | Design token   | Size / line | Weight   |
  |---------------|----------------|-------------|----------|
  | headlineLarge | `headline-lg`  | 28 / 34     | SemiBold |
  | headlineMedium| `headline-md`  | 22 / 28     | SemiBold |
  | bodyLarge     | `body-md`      | 15 / 22     | Normal   |
  | labelMedium   | `label-md`     | 14 / 20     | Medium   |
  | labelSmall    | `label-sm`     | 13 / 18     | Medium   |

  A standalone `TechnicalTextStyle` (monospace 13/18) is exposed for code/keycap labels — Material3 has no slot for it. `Outfit` and `Inter` map to `FontFamily.Default` for now; the real `.ttf` files are not vendored, which is a follow-up, not a blocker.
- **`Shape.kt`** — `Shapes(small = 12.dp, medium = 16.dp, large = 16.dp, extraLarge = 28.dp)` plus standalone `PillShape` (`RoundedCornerShape(percent = 50)`, for chips) and `KeyShape` (`4.dp`, for keycaps).
- **`Theme.kt`** — `PersonaSpeakTheme` composable wrapping `MaterialTheme(colorScheme, typography, shapes)`. Dark-first: DESIGN.md specifies a dark palette only, so a single `darkColorScheme()` feeds both `darkColorScheme` and `lightColorScheme` slots. This is noted in a comment in `Theme.kt`; a real light scheme is a follow-up if a light spec ever lands.

**Known compile constraint (noted in `Color.kt`):** Compose BOM 2025.05.01 pulls material3 1.3.x, whose `darkColorScheme()` does not accept the `*Fixed*` family of params (those landed in material3 1.4). The 12 fixed-tone `Color` vals are kept for direct screen use but are deliberately not wired into the color scheme.

### Navigation — `android/app/src/main/kotlin/.../app/ui/nav/`

- **`Routes.kt`** — a `Screen` sealed class with one data object per destination, plus a `companion object` exposing `PERSONA_ID_ARG`, `startRoute`, `all`, and a `personaDetail(id)` helper that formats the arg-bearing route.
- **`PersonaSpeakNavHost.kt`** — `PersonaSpeakNavHost(navController: NavHostController)` wiring `NavHost(...)` with one `composable(route) { Placeholder(screen) }` entry per route. The placeholder is intentionally trivial: the screen title and the string "not yet built". Real screens are out of scope.

### Wiring

- **`MainActivity.kt`** — replaced the walking-skeleton body with `PersonaSpeakTheme { PersonaSpeakNavHost(rememberNavController()) }`. Start destination is `onboarding/welcome`.
- **`android/gradle/libs.versions.toml`** — added `navigationCompose = "2.9.0"` and a `navigation-compose` library alias.
- **`android/app/build.gradle.kts`** — added `implementation(libs.navigation.compose)`.
- **`AndroidManifest.xml`** — untouched. The existing `MainActivity` entry hosts the nav graph; no new activity was needed.

## Route list — the contract

Eleven destinations, all Activity-hosted in the `:app` module. IME-window overlays (the candidate strip, persona/mood pickers, the result card, in-window error states) are deliberately excluded — those are overlay states inside the `:keyboard` IME, not nav-graph destinations.

| Route                                   | Screen sealed-object      | Arg          |
|----------------------------------------|---------------------------|--------------|
| `onboarding/welcome`                   | `Screen.OnboardingWelcome`| —            |
| `onboarding/setup`                     | `Screen.OnboardingSetup`  | —            |
| `onboarding/ai-selection`              | `Screen.OnboardingAiSelection` | —       |
| `onboarding/api-key`                   | `Screen.OnboardingApiKey` | —            |
| `onboarding/demo`                      | `Screen.OnboardingDemo`   | —            |
| `settings/home`                        | `Screen.SettingsHome`     | —            |
| `settings/persona-browser`             | `Screen.SettingsPersonaBrowser` | —      |
| `settings/persona-detail/{personaId}`  | `Screen.SettingsPersonaDetail` | `personaId: String` |
| `settings/ai-providers`                | `Screen.SettingsAiProviders` | —         |
| `settings/rewrite-behaviour`           | `Screen.SettingsRewriteBehaviour` | —   |
| `settings/privacy`                     | `Screen.SettingsPrivacy`  | —            |

Start route: `onboarding/welcome`. The only arg-bearing route is `settings/persona-detail/{personaId}`; consumers build it via `Screen.personaDetail(id)`.

Screen-implementation workers: this table is the contract. If you believe a route is missing or should be a dialog instead of a destination, raise it before adding a route unilaterally — the graph is small on purpose.

## navigation-compose version

**`androidx.navigation:navigation-compose:2.9.0`**, chosen because:

1. The version catalog already pins the `androidx.lifecycle` line at `2.9.0` (`lifecycleRuntimeKtx`, `lifecycleViewmodelCompose`, etc.). navigation-compose 2.9.0 is built against the same lifecycle 2.9.0, so the transitive dependency graph lines up exactly with no forced upgrades.
2. Compatible with the rest of the pinned stack: Compose BOM `2025.05.01`, AGP `8.10.1`, Kotlin `2.1.21`, `compileSdk = 36`, `minSdk = 23`.
3. 2.9.0 is the current stable line at the time of writing; no reason to pin older.

## Build proof

From `android/`:

```
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
./gradlew :app:assembleDebug --no-daemon --console=plain \
    -Porg.gradle.java.installations.paths="$JAVA_HOME"
```

(`jvmToolchain(17)` requires JDK 17; the project's Gradle auto-detection does not find the keg-only Homebrew install, so the installations path is supplied explicitly.)

Result:

```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 12s
41 actionable tasks: 8 executed, 33 up-to-date
```

No device run was required or performed; a green debug build is the agreed proof for this task.

## Follow-ups (not blocking, called out for whoever picks them up)

1. **Status-bar / edge-to-edge seam.** The manifest still themes the activity as `Theme.Material.Light.NoActionBar`, so the system bar renders light against the dark surface. Fix is an edge-to-edge setup plus a themed status-bar icon tint, not a color-token change.
2. **Real fonts.** Vendor `Outfit` and `Inter` `.ttf` into `android/app/src/main/res/font/` and swap `FontFamily.Default` in `Type.kt`. The type scale already targets the correct weights/sizes.
3. **Light color scheme.** None exists in DESIGN.md; if a light spec lands, `Theme.kt`'s single-scheme shortcut is the place to revisit.
