# Android toolchain convergence implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move PersonaSpeak's current Android modules onto the ASK-proven Gradle,
AGP, Kotlin, and JDK baseline and make that baseline a required CI check.

**Architecture:** This is the first independently mergeable slice after the
single-APK design. It changes only the existing root Android build; ASK remains
outside the unified project graph and the current demo application remains
installable. The later ingestion and graft plans consume the exact toolchain
declared here.

**Tech Stack:** Gradle 9.2.1, Android Gradle Plugin 8.13.2, Kotlin 2.3.10,
JDK 21, GitHub Actions, Python 3.12.

## Global Constraints

- Work from the latest `main` after PR #29 merges; do not build this slice on
  PR #18 or PR #23.
- Keep `android/` as the Gradle root.
- Keep `:core-personas` and `:core-providers` free of Android imports.
- Keep the current `:app -> :keyboard` demo graph working; this slice does not
  vendor or include ASK modules.
- Use `JAVA_HOME` pointing at JDK 21 for every local Gradle verification.
- Do not change `minSdk = 26`, application IDs, manifests, package names, or
  product behavior in this slice.
- Do not stage `.superpowers/`, `docs/design/stitch-prompt.md`, the Stitch zip,
  or the exported Stitch directory.
- Add one truthful entry at the top of `PATCHNOTES.md`.

---

## File structure

- `android/gradle/wrapper/gradle-wrapper.properties` — pins Gradle 9.2.1.
- `android/gradle/libs.versions.toml` — pins AGP 8.13.2 and Kotlin 2.3.10.
- `android/core-personas/build.gradle.kts` — selects JVM toolchain 21.
- `android/core-providers/build.gradle.kts` — selects JVM toolchain 21.
- `android/keyboard/build.gradle.kts` — selects Java/Kotlin toolchain 21 for
  the temporary keyboard library.
- `android/app/build.gradle.kts` — selects Java/Kotlin toolchain 21 while
  preserving the current SDK, application, and dependency settings.
- `.github/workflows/ci.yml` — proves the Android modules on JDK 21 for every PR.
- `PATCHNOTES.md` — records the shipped build-system change.

### Task 1: Converge the local Android build

**Files:**

- Modify: `android/gradle/wrapper/gradle-wrapper.properties`
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/core-personas/build.gradle.kts`
- Modify: `android/core-providers/build.gradle.kts`
- Modify: `android/keyboard/build.gradle.kts`
- Modify: `android/app/build.gradle.kts`

**Interfaces:**

- Consumes: the existing project paths `:core-personas`, `:core-providers`,
  `:keyboard`, and `:app` from `android/settings.gradle.kts`.
- Produces: Gradle 9.2.1, AGP 8.13.2, Kotlin 2.3.10, and Java/Kotlin toolchain
  21 for the current root build. Task 2 relies on these exact values.

- [ ] **Step 1: Prove the checkout still has the pre-convergence values**

Run from the repository root:

```bash
rg -n 'gradle-8\.14|agp = "8\.10\.1"|kotlin = "2\.1\.21"|jvmToolchain\(17\)|VERSION_17' \
  android/gradle/wrapper/gradle-wrapper.properties \
  android/gradle/libs.versions.toml \
  android/core-personas/build.gradle.kts \
  android/core-providers/build.gradle.kts \
  android/keyboard/build.gradle.kts \
  android/app/build.gradle.kts
```

Expected: one Gradle 8.14 line, one AGP 8.10.1 line, one Kotlin 2.1.21 line,
four `jvmToolchain(17)` lines, and four `VERSION_17` lines. Stop if the values
have already moved; the plan must be rebased against the new authority instead
of overwriting it.

- [ ] **Step 2: Pin the proven wrapper and plugin versions**

In `android/gradle/wrapper/gradle-wrapper.properties`, replace only the
distribution URL:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.2.1-all.zip
```

In `android/gradle/libs.versions.toml`, make the beginning of `[versions]`:

```toml
[versions]
agp = "8.13.2"
kotlin = "2.3.10"
composeBom = "2025.05.01"
```

Do not move the Compose BOM or other library versions in this task.

- [ ] **Step 3: Move both pure-Kotlin modules to JDK 21**

In both `android/core-personas/build.gradle.kts` and
`android/core-providers/build.gradle.kts`, retain the existing plugin and
dependency blocks and change the Kotlin block to:

```kotlin
kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 4: Move both temporary Android modules to Java/Kotlin 21**

In both `android/keyboard/build.gradle.kts` and
`android/app/build.gradle.kts`, leave namespaces, SDK levels, Compose
configuration, and dependencies unchanged. Also leave the application's
`applicationId` and version values unchanged. Replace each module's toolchain
sections with:

```kotlin
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 5: Verify the wrapper is running on the intended JDK**

Set a task-specific shell variable rather than changing global Java settings:

```bash
PERSONASPEAK_JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
env JAVA_HOME="$PERSONASPEAK_JAVA_HOME" android/gradlew --version
```

Expected output includes:

```text
Gradle 9.2.1
Launcher JVM: 21
Daemon JVM: 21
```

If the Homebrew path does not exist, resolve an installed JDK 21 with
`/usr/libexec/java_home -v 21` and assign that absolute path to
`PERSONASPEAK_JAVA_HOME`.

- [ ] **Step 6: Build and test every current Android module contract**

```bash
PERSONASPEAK_JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd android
env JAVA_HOME="$PERSONASPEAK_JAVA_HOME" ./gradlew \
  :core-personas:build \
  :core-providers:build \
  :keyboard:assembleDebug \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  --no-daemon
```

Expected: `BUILD SUCCESSFUL`. `:core-personas:test` executes its golden tests;
the provider and application test tasks may report `NO-SOURCE`. Return to the
repository root, then confirm one application APK and one keyboard-library AAR:

```bash
cd ..
find android \( -path '*/build/outputs/apk/*/*.apk' -o -path '*/build/outputs/aar/*.aar' \) \
  -type f -print | sort
```

Expected paths:

```text
android/app/build/outputs/apk/debug/app-debug.apk
android/keyboard/build/outputs/aar/keyboard-debug.aar
```

- [ ] **Step 7: Commit the converged build**

```bash
git add \
  android/gradle/wrapper/gradle-wrapper.properties \
  android/gradle/libs.versions.toml \
  android/core-personas/build.gradle.kts \
  android/core-providers/build.gradle.kts \
  android/keyboard/build.gradle.kts \
  android/app/build.gradle.kts
git diff --cached --check
git commit -m "build: converge Android toolchain on ASK"
```

### Task 2: Make convergence a CI contract

**Files:**

- Modify: `.github/workflows/ci.yml`
- Modify: `PATCHNOTES.md`

**Interfaces:**

- Consumes: the exact Gradle/plugin/JDK versions produced by Task 1.
- Produces: a required `Build Android modules` check and a patch-note entry for
  the toolchain slice.

- [ ] **Step 1: Add the Android job after `personas-and-cli`**

Add this sibling job to `.github/workflows/ci.yml`:

```yaml
  android-build:
    name: Build Android modules
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: gradle
      - name: Build and test the current Android graph
        working-directory: android
        run: >-
          ./gradlew
          :core-personas:build
          :core-providers:build
          :keyboard:assembleDebug
          :app:assembleDebug
          :app:testDebugUnitTest
          --no-daemon
      - name: Require the current app APK and keyboard AAR
        working-directory: android
        shell: bash
        run: |
          set -euo pipefail
          mapfile -t artifacts < <(find . \( -path '*/build/outputs/apk/*/*.apk' -o -path '*/build/outputs/aar/*.aar' \) -type f -print | sort)
          printf '%s\n' "${artifacts[@]}"
          test "${#artifacts[@]}" -eq 2
          test "${artifacts[0]}" = "./app/build/outputs/apk/debug/app-debug.apk"
          test "${artifacts[1]}" = "./keyboard/build/outputs/aar/keyboard-debug.aar"
```

This APK-plus-AAR assertion describes the temporary pre-graft graph. The
unified integration plan must replace it with the ASK `:ime:app` one-APK
assertion in the same commit that removes `:app` and `:keyboard-stub`.

- [ ] **Step 2: Add the patch note**

At the top of the current 2026-07-22 section in `PATCHNOTES.md`, add:

```markdown
- The Android build now uses ASK's proven Gradle 9.2.1, AGP 8.13.2, Kotlin
  2.3.10, and JDK 21 baseline. CI checks every current module, because a
  convergence experiment is more useful after it stops being temporary.
```

- [ ] **Step 3: Run the repository and prose checks**

```bash
python3 desktop/validate_personas.py
python3 desktop/personaspeak.py --list
git diff --check
! rg -n '\b(TB''D|TO''DO|FIX''ME)\b' .github/workflows/ci.yml PATCHNOTES.md
! rg -n -i '\b(lever''age|util''ize|seam''less|emp''ower|rob''ust|best-in-''class)\b|please note th''at' PATCHNOTES.md
```

Expected: four personas validate, four persona names print, and every remaining
command exits zero without matches.

- [ ] **Step 4: Re-run the exact Android command CI will run**

```bash
PERSONASPEAK_JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd android
env JAVA_HOME="$PERSONASPEAK_JAVA_HOME" ./gradlew \
  :core-personas:build \
  :core-providers:build \
  :keyboard:assembleDebug \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the CI contract and patch note**

```bash
git add .github/workflows/ci.yml PATCHNOTES.md
git diff --cached --check
git commit -m "ci: verify the Android toolchain"
```

### Task 3: Prepare exact review evidence

**Files:**

- No repository file changes.

**Interfaces:**

- Consumes: Task 1 and Task 2 commits.
- Produces: deterministic evidence and a digest-bound non-author review for the
  pull request; it produces no mergeable code.

- [ ] **Step 1: Verify final scope and commit hygiene**

```bash
git status --short --branch
git diff origin/main...HEAD --check
git diff --name-status origin/main...HEAD
git log --oneline origin/main..HEAD
```

Expected changed files are the eight implementation files listed in this plan
plus this approved plan document. The worktree is otherwise clean, and there
are four conventional commits: two build commits, one CI commit, and one docs
commit that makes the plan durable.

- [ ] **Step 2: Capture deterministic evidence for the PR body**

Record the successful output from:

```bash
python3 desktop/validate_personas.py
python3 desktop/personaspeak.py --list
PERSONASPEAK_JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd android
env JAVA_HOME="$PERSONASPEAK_JAVA_HOME" ./gradlew \
  :core-personas:build \
  :core-providers:build \
  :keyboard:assembleDebug \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  --no-daemon
cd ..
find android \( -path '*/build/outputs/apk/*/*.apk' -o -path '*/build/outputs/aar/*.aar' \) \
  -type f -print | sort
```

Expected: persona validation/list pass, Gradle reports `BUILD SUCCESSFUL`, and
exactly the current app APK and keyboard AAR are listed.

- [ ] **Step 3: Request cross-family review against exact digests**

Resolve and record the immutable range:

```bash
git rev-parse origin/main
git rev-parse HEAD
```

Give a non-author reviewer from a different model family those exact SHAs and
ask it to verify version consistency, JDK 21 source/toolchain settings, CI/local
command parity, the temporary APK-plus-AAR assertion, unchanged SDK/application
identity, and absence of product behavior changes. Post its typed findings and
verdict as a PR comment. Critical or Important findings block readiness; Minor
findings are recorded with an explicit disposition.

- [ ] **Step 4: Confirm GitHub CI before marking ready**

```bash
gh pr checks --watch
```

Expected: `Validate personas & smoke-test CLI`, `Build Android modules`, and
`PATCHNOTES.md touched` all pass. Keep the PR draft if any required check or
independent-review gate is missing.
