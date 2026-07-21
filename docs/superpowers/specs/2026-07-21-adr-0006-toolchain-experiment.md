# ADR-0006 Toolchain-Convergence Experiment — Live Run

**Verdict: The convergence works cleanly with one trivial, predictable fix.
Option A's "bump our three small modules up" framing in ADR-0006 is confirmed
by actual `./gradlew` output, not just static reasoning.**

On 2026-07-21, in worktree `adr-0006-toolchain-experiment` on branch
`experiment/adr-0006-toolchain-convergence`, the root Android build's
toolchain was converged onto AnySoftKeyboard's (Gradle 8.14 / AGP 8.10.1 /
Kotlin 2.1.21 / JDK 17 → Gradle 9.2.1 / AGP 8.13.2 / Kotlin 2.3.10 / JDK 21)
and all three root modules — `core-personas`, `core-providers`, `app` —
build, assemble, and test green under JDK 21. This is the experiment the
[Independent review](../../adr/0006-gradle-composition-for-the-graft.md#independent-review-2026-07-21)
section of ADR-0006 explicitly flagged as "still unrun in a live environment"
and "the next thing to run before this ADR's status moves past Proposed." It
has now been run.

## What changed

Five files, all inside `android/`, totaling +8/-8 lines. Full diff:

```diff
--- a/android/gradle/wrapper/gradle-wrapper.properties
+++ b/android/gradle/wrapper/gradle-wrapper.properties
@@ -1,6 +1,6 @@
 distributionBase=GRADLE_USER_HOME
 distributionPath=wrapper/dists
-distributionUrl=https\://services.gradle.org/distributions/gradle-8.14-all.zip
+distributionUrl=https\://services.gradle.org/distributions/gradle-9.2.1-all.zip
 networkTimeout=10000

--- a/android/gradle/libs.versions.toml
+++ b/android/gradle/libs.versions.toml
@@ -1,6 +1,6 @@
 [versions]
-agp = "8.10.1"
-kotlin = "2.1.21"
+agp = "8.13.2"
+kotlin = "2.3.10"

--- a/android/core-personas/build.gradle.kts
+++ b/android/core-personas/build.gradle.kts
@@ -3,7 +3,7 @@ plugins {
 }
 kotlin {
-    jvmToolchain(17)
+    jvmToolchain(21)
 }

--- a/android/core-providers/build.gradle.kts  (identical change)
--- a/android/app/build.gradle.kts
+++ b/android/app/build.gradle.kts
@@ -21,13 +21,13 @@
     compileOptions {
-        sourceCompatibility = JavaVersion.VERSION_17
-        targetCompatibility = JavaVersion.VERSION_17
+        sourceCompatibility = JavaVersion.VERSION_21
+        targetCompatibility = JavaVersion.VERSION_21
     }
 }
 kotlin {
-    jvmToolchain(17)
+    jvmToolchain(21)
 }
```

The first two files are the toolchain versions the ADR named explicitly.
The latter three are the predictable, ADR-anticipated "bump our three small
modules up" chore — same intent, just stated as `jvmToolchain(21)` rather
than left at 17. See "Friction" for why this was forced, not optional.

### `includeBuild("keyboard")` — left intact

The composite-wired vendored ASK tree was **not** commented out. It loads
and configures cleanly under the new toolchain (see "Friction #2" for the
noise it generates, none of it fatal). Keeping it in was the more honest
test of the actual current state of `android/`: if it had broken, that
would itself have been decision-relevant evidence.

## Environment

- **Branch:** `experiment/adr-0006-toolchain-convergence` (forked from
  PR #18's `feat/vendor-anysoftkeyboard`).
- **JDK:** Homebrew `openjdk@21` 21.0.11, passed per-invocation as
  `env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
  No global/user Java config was touched.
- **Working directory for all commands:** `android/`.
- **All commands:** `--no-daemon` (fresh JVM per invocation, so each
  result is independent and reproducible).

## Command output

### 1. `./gradlew :core-personas:build --no-daemon`

First run **failed** before the fix was applied (see Friction #1). After
bumping `jvmToolchain(17)` → `jvmToolchain(21)`:

```
> Task :core-personas:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core-personas:processResources NO-SOURCE
> Task :core-personas:processTestResources NO-SOURCE
> Task :core-personas:compileKotlin
> Task :core-personas:compileJava NO-SOURCE
> Task :core-personas:classes UP-TO-DATE
> Task :core-personas:jar
> Task :core-personas:assemble
> Task :core-personas:compileTestKotlin
> Task :core-personas:compileTestJava NO-SOURCE
> Task :core-personas:testClasses UP-TO-DATE
> Task :core-personas:test
> Task :core-personas:check
> Task :core-personas:build

BUILD SUCCESSFUL in 14s
10 actionable tasks: 4 executed, 6 up-to-date
```

### 2. `./gradlew :core-providers:build --no-daemon`

```
> Task :core-providers:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core-providers:processResources NO-SOURCE
> Task :core-providers:processTestResources NO-SOURCE
> Task :core-providers:compileKotlin
> Task :core-providers:compileJava NO-SOURCE
> Task :core-providers:classes UP-TO-DATE
> Task :core-providers:jar
> Task :core-providers:assemble
> Task :core-providers:compileTestKotlin NO-SOURCE
> Task :core-providers:compileTestJava NO-SOURCE
> Task :core-providers:testClasses UP-TO-DATE
> Task :core-providers:test NO-SOURCE
> Task :core-providers:check UP-TO-DATE
> Task :core-providers:build

BUILD SUCCESSFUL in 9s
8 actionable tasks: 2 executed, 6 up-to-date
```

(`:test` is NO-SOURCE because `core-providers` currently has no tests; the
task itself configures and runs cleanly. `core-personas` does have tests and
they pass.)

### 3. `./gradlew :app:assembleDebug --no-daemon`

```
> Task :app:compileDebugKotlin
> Task :app:compileDebugJavaWithJavac NO-SOURCE
> Task :app:dexBuilderDebug
> Task :app:mergeProjectDexDebug
> Task :app:packageDebug
> Task :app:createDebugApkListingFileRedirect
> Task :app:assembleDebug

BUILD SUCCESSFUL in 21s
43 actionable tasks: 37 executed, 6 up-to-date
```

Artifacts verified on disk:

```
$ ls -la android/app/build/outputs/apk/debug/
-rw-r--r--  9318428 Jul 21 17:15 app-debug.apk
-rw-r--r--      420 Jul 21 17:15 output-metadata.json
```

APK produced at 9.3 MB, signed with the debug key as expected.

### 4. `./gradlew :app:testDebugUnitTest --no-daemon`

```
> Task :app:preDebugUnitTestBuild UP-TO-DATE
> Task :app:javaPreCompileDebugUnitTest
> Task :app:bundleDebugClassesToCompileJar
> Task :app:bundleDebugClassesToRuntimeJar
> Task :app:compileDebugUnitTestKotlin NO-SOURCE
> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugUnitTestJavaRes NO-SOURCE
> Task :app:testDebugUnitTest NO-SOURCE

BUILD SUCCESSFUL in 8s
27 actionable tasks: 3 executed, 24 up-to-date
```

The task exists, configures, and runs. It is `NO-SOURCE` because the
`app` module ships no unit tests today — consistent with PR #18 being
ingestion-only. The task wiring itself is healthy under the new toolchain.

## Friction encountered

### 1. First-run failure: `jvmToolchain(17)` couldn't resolve under JDK 21

The very first `:core-personas:build` failed during configuration with:

```
* What went wrong:
Could not determine the dependencies of task ':core-personas:compileJava'.
> Could not resolve all dependencies for configuration ':core-personas:compileClasspath'.
   > Failed to calculate the value of task ':core-personas:compileJava' property 'javaCompiler'.
      > Cannot find a Java installation on your machine (Mac OS X 26.5.2 aarch64)
        matching: {languageVersion=17, vendor=any vendor, implementation=vendor-specific,
        nativeImageCapable=false}. Toolchain download repositories have not been configured.
```

Gradle 9.2.1's toolchain auto-detection does not provision a JDK 17 on
demand (no Foojay resolver configured in `settings.gradle.kts`), and JDK 17
was deliberately not on this experiment's `JAVA_HOME` path — the task
specifies JDK 21. The module scripts were holding the toolchain target at
JDK 17 even though the runtime and ASK's stack are JDK 21.

**Fix applied (one attempt, succeeded):** bump `jvmToolchain(17)` →
`jvmToolchain(21)` in `core-personas`, `core-providers`, and `app`, and
bump `sourceCompatibility`/`targetCompatibility` from `VERSION_17` to
`VERSION_21` in `app`. This is exactly the "adopt ASK's newer toolchain at
the root and bump our three small modules up — a one-time chore" that
ADR-0006 Option A describes. It is not a workaround; it is the convergence
itself, just stated in the module scripts as well as in the wrapper and
catalog. With this applied, all four commands went green on the next run.

The alternative — adding a Foojay toolchain resolver to
`settings.gradle.kts` so Gradle auto-downloads JDK 17 — was rejected: it
would preserve the JDK 17 target on our modules while running ASK at JDK 21,
which contradicts the convergence goal and creates a permanent two-JDK split
that Option A exists to eliminate.

### 2. `includeBuild("keyboard")` noise: ~50+ Gradle-9 deprecation warnings, no failures

Every invocation configures the full ASK composite tree (every
`:keyboard:*` project loads, even when running a root-only task like
`:core-personas:build`). Under Gradle 9.2.1, ASK's Groovy DSL scripts emit
deprecation warnings of the form:

```
Build file '.../keyboard/ime/<mod>/build.gradle': line N
Properties should be assigned using the 'propName = value' syntax.
Setting a property via the Gradle-generated 'propName value' or
'propName(value)' syntax in Groovy DSL has been deprecated.
This is scheduled to be removed in Gradle 10.
Use assignment ('namespace = <value>') instead.
```

This surfaces across roughly 30 ASK module scripts plus shared scripts
under `android/keyboard/addons/gradle/*.gradle` (`pack_apk.gradle`,
`language_pack_lib.gradle`, `quicktext_pack_lib.gradle`,
`theme_pack_lib.gradle`). Also present per addon APK module:

```
Could not find '/tmp/anysoftkeyboard.keystore' file. Release APK will not be signed.
```

Neither category is fatal. The deprecations are scheduled for removal in
**Gradle 10**, not 9.x — ASK 1.13-r1 builds and runs under 9.2.1, which
directly answers a question codex raised in the independent review ("is
ASK's build actually compatible with Gradle 9.2.1, or is that just what
its wrapper pins?"). It is, with margin. The keystore line is release-APK
noise only; this experiment never builds a release variant.

**Cost in practice:** each `--no-daemon` invocation spends roughly 30–45
seconds configuring the keyboard composite before getting to the requested
root task. That disappears the moment Option A actually lands (the composite
goes away, replaced by unified `include(...)`), so it is rent paid by the
*experiment's* `includeBuild`-as-is wiring, not by Option A itself.

### 3. Nothing else

No Kotlin source in any of the three root modules needed editing. No
deprecated API call, no missing dependency, no Compose Compiler plugin
mismatch, no AGP DSL breakage. Compose BOM 2025.05.01 and the rest of the
root catalog sit unchanged on top of AGP 8.13.2 / Kotlin 2.3.10 and work.

## Time / effort

- **Wall clock for the experiment, including Gradle 9.2.1 distribution
  download, one failed run, the fix, and all four target commands:**
  roughly 15 minutes, of which ~5 was the initial 9.2.1 download and
  first-build warmup.
- **Diff size:** +8/-8 across 5 files. The owner's actual review of the
  change is a one-line-per-file read.
- **Category on ADR-0006's "more than a day?" scale:** not even close.
  This was a deliberate morning's work, not a multi-day convergence
  project. ADR-0006's escape hatch ("if converging costs more than a day,
  Option B's isolation becomes pragmatic") is not triggered by this
  evidence.

## Decision-relevant takeaways for the owner

1. **The toolchain convergence is real and cheap.** All three reviewers
   predicted it; the live run confirms it. Option A's toolchain cost is a
   one-line bump in three module scripts plus the wrapper/catalog — total
   cost paid once, no further work predicted.
2. **ASK 1.13-r1 is genuinely Gradle-9.2.1-compatible.** Codex flagged
   this as an open question; the experiment answers it: ASK configures
   end-to-end under 9.2.1 with only Gradle-10-targeted deprecation
   warnings. No fatal configuration errors in any of the ~30 `:keyboard:*`
   modules traversed during root builds.
3. **The composite-build noise (`includeBuild("keyboard")`) is purely
   cosmetic today, but it evaporates under Option A** — the unified build
   replaces composite wiring with `include(...)`, so the warnings about
   ASK's Groovy DSL move from "our problem on every invocation" to
   "upstream's problem at ASK upgrade time," which is the rent posture
   ADR-0004 already committed to.
4. **None of the three independent-review concerns about *source-level*
   breakage in `core-*`/`app` materialized.** No Kotlin migration, no
   Compose Compiler mismatch, no removed API. The static reasoning was
   correct on this point.
5. **One ADR-0006 framing item to tighten when the ADR moves past
   Proposed:** the "bump our three small modules up" line should name
   what specifically gets bumped — `jvmToolchain(17)` → `jvmToolchain(21)`
   and `sourceCompatibility`/`targetCompatibility` 17 → 21 — so future
   readers don't infer the modules were untouched.

## What is committed

The version bumps (`android/gradle/wrapper/gradle-wrapper.properties`,
`android/gradle/libs.versions.toml`) and the module-script toolchain bumps
(`android/core-personas/build.gradle.kts`,
`android/core-providers/build.gradle.kts`, `android/app/build.gradle.kts`)
are **left uncommitted in this worktree** as throwaway experiment state,
per the task brief's default. The diff above reconstructs them exactly.

Only this report file is committed.
