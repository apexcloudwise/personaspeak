# Stub restoration report — give `app` back a working IME

**Date:** 2026-07-21
**Branch:** `feat/graft-option-a-wiring` (disposable, off `feat/vendor-anysoftkeyboard` / PR #18)
**Scope:** Restore a working `InputMethodService` to the `:app` APK. ADR-0006
"Independent review" flagged the post-PR-#18 state (no IME in `app` at all) as a
blocker independent of the ADR-0006 A/B decision. This resolves that blocker.

## What moved where

Before PR #18 the IME lived in a first-party `:keyboard` module. PR #18
vendored AnySoftKeyboard into that same path, deleting the stub's sources, and
dropped `app`'s dependency on `:keyboard`. The stub's sources were recovered
from `main` and landed **inside the `app` module** (not back under
`android/keyboard/`, which belongs to vendored ASK per ADR-0004). The stub's
package `biz.pixelperfectstudios.personaspeak.keyboard` was preserved verbatim,
sitting alongside the existing `…app` package in `app`'s source root.

| Recovered artifact (from `main`) | New location in `app` |
| --- | --- |
| `android/keyboard/.../keyboard/PersonaBoardService.kt` | `android/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/keyboard/PersonaBoardService.kt` |
| `android/keyboard/.../keyboard/PersonaPanel.kt` | `android/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/keyboard/PersonaPanel.kt` |
| `android/keyboard/.../AndroidManifest.xml` `<service>` block | merged into `android/app/src/main/AndroidManifest.xml` (inside the existing `<application>`) |
| `android/keyboard/.../values/strings.xml` `personaboard_ime_label` | merged into `android/app/src/main/res/values/strings.xml` (existing `app_name` retained) |
| `android/keyboard/.../xml/personaboard_method.xml` | `android/app/src/main/res/xml/personaboard_method.xml` (new file) |

The Kotlin sources are byte-for-byte the `main` versions. No API drift fixes
were needed — the stub compiled cleanly against `app`'s current toolchain
(Compose BOM 2025.05.01, lifecycle 2.9.0, savedstate 1.3.0, Kotlin 2.1.21).

## `app/build.gradle.kts` dependency diff

The placeholder comment ("the dependency is dropped for now, wiring the app to
ASK's `:ime:app` is the graft PR's concern") was replaced with the real
dependency set. The added lines mirror what the old `:keyboard` module's
`build.gradle.kts` declared that `app` was missing: the two core modules plus
`lifecycle.runtime` and `savedstate` (both already present as version-catalog
aliases in `android/gradle/libs.versions.toml`). The Compose + activity-compose
dependencies `app` already had were left in place.

```diff
 dependencies {
+    implementation(project(":core-personas"))
+    implementation(project(":core-providers"))
+
     val composeBom = platform(libs.compose.bom)
     implementation(composeBom)
     implementation(libs.compose.ui)
     implementation(libs.compose.foundation)
     implementation(libs.compose.material3)
     implementation(libs.activity.compose)
+    implementation(libs.lifecycle.runtime)
+    implementation(libs.savedstate)
-    // The legacy `:keyboard` stub was replaced by the vendored AnySoftKeyboard
-    // tree (ADR-0004). Wiring the app to ASK's :ime:app is the graft PR's
-    // concern; this PR is ingestion only, so the dependency is dropped for now.
 }
```

Out of scope, untouched: `android/keyboard/` (ASK's vendored tree),
`android/settings.gradle.kts`'s `includeBuild("keyboard")`, and any wiring of
ASK's `:ime:app` (ADR-0006 Option A unification is separate work).

## Build verification

Command (run from `android/`):

```
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :app:assembleDebug --no-daemon \
  -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

> **Environment note (not a committed change):** this machine has no JDK 17 on
> Gradle's default toolchain search path and no auto-provisioning configured,
> so `jvmToolchain(17)` in `app/build.gradle.kts` fails to resolve out of the
> box. Pointing Gradle at the Homebrew `openjdk@17` install via
> `org.gradle.java.installations.paths` satisfies the toolchain requirement
> without modifying any project file. Whoever wires up CI for this branch
> should either install JDK 17 in a discoverable location or enable the
> `foojay-resolver-convention` plugin in `settings.gradle.kts`.

Result:

```
> Task :app:compileDebugKotlin
> Task :app:compileDebugJavaWithJavac NO-SOURCE
> Task :app:packageDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL in 11s
45 actionable tasks: 39 executed, 6 up-to-date
```

`:app:compileDebugKotlin` ran (the recovered stub compiled) and
`:app:packageDebug` produced
`android/app/build/outputs/apk/debug/app-debug.apk` (9.6 MB).

## Install + IME registration verification

`adb devices` showed `emulator-5554`. Install:

```
$ adb install -r android/app/build/outputs/apk/debug/app-debug.apk
Performing Streamed Install
Success
```

`adb shell ime list -a` — the PersonaSpeak entry (abridged to the relevant
fields; the emulator also lists Gboard, ASK, FlorisBoard, HeliBoard, and Voice
IME):

```
biz.pixelperfectstudios.personaspeak/.keyboard.PersonaBoardService:
  mId=biz.pixelperfectstudios.personaspeak/.keyboard.PersonaBoardService
  mSupportsSwitchingToNextInputMethod=true
  Service:
    ServiceInfo:
      name=biz.pixelperfectstudios.personaspeak.keyboard.PersonaBoardService
      packageName=biz.pixelperfectstudios.personaspeak
      enabled=true exported=true
      permission=android.permission.BIND_INPUT_METHOD
      ApplicationInfo:
        packageName=biz.pixelperfectstudios.personaspeak
        sourceDir=/data/app/…/biz.pixelperfectstudios.personaspeak-…/base.apk
        enabled=true minSdkVersion=26 targetSdk=35 versionCode=1
```

The service registers with the correct component id
(`biz.pixelperfectstudios.personaspeak/.keyboard.PersonaBoardService`), the
`BIND_INPUT_METHOD` permission, and the `android.view.InputMethod` intent
filter resolved by the system. The IME is installed and registered; it was not
set as the active/default IME and not typed through (out of scope per task).

To confirm the app doesn't crash on launch post-install, the launcher activity
was started and logcat watched for ~3 seconds:

```
$ adb shell am start -n biz.pixelperfectstudios.personaspeak/.app.MainActivity
Starting: Intent { cmp=biz.pixelperfectstudios.personaspeak/.app.MainActivity }
```

Logcat showed normal startup (`Start proc …:biz.pixelperfectstudios.personaspeak`,
`Displayed biz.pixelperfectstudios.personaspeak/.app.MainActivity for user 0:
+1s866ms`) and **no `FATAL` / `AndroidRuntime` / process crash** for the
`biz.pixelperfectstudios.personaspeak` process. The
`Unable to open '…/base.dm': No such file or directory` lines are benign (no
deck-of-dex `.dm` for a fresh debug install) and the
`ImeTracker … onRequestHide … HIDE_UNSPECIFIED_WINDOW` line is the system
hiding the previous IME as the activity came up, not a crash.

## Result

The `:app` APK once again carries a registered `InputMethodService`. The
ADR-0006 "Independent review" blocker — "app module has no InputMethodService
at all" — is cleared. `main` (once this lands) can install and be selected as a
keyboard again.
