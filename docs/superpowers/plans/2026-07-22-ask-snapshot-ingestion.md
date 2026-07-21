# AnySoftKeyboard snapshot ingestion implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ingest the pinned AnySoftKeyboard source snapshot at
`android/keyboard/` without including ASK in the Gradle graph yet, while moving
the rejected ADR-0001 panel unchanged to an explicitly temporary
`:keyboard-stub` module.

**Architecture:** This is a provenance and path-allocation slice, not the ASK
graft. The root build continues to produce the temporary PersonaSpeak APK and a
stub AAR; the ASK tree is inert source with an empty upstream-rent ledger. The
later unified-build slice makes ASK's `:ime:app` the only application and deletes
both the temporary root app and `:keyboard-stub` atomically.

**Tech Stack:** Git snapshot ingestion, Gradle 9.2.1, AGP 8.13.2, Kotlin
2.3.10, JDK 21, Android SDK/ADB, Bash 5 in GitHub Actions.

## Global constraints

- Pin ASK tag `1.13-r1` at commit
  `8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d`; a tag name without the matching
  commit fails ingestion.
- Preserve upstream source, build logic, tests, resources, `LICENSE`, and
  existing per-file license headers byte-for-byte.
- Do not normalize upstream whitespace or line endings. Run Git's whitespace
  gate only on PersonaSpeak-created or modified files; the pristine-archive
  comparison is the integrity gate for unmodified upstream files.
- Exclude only repository-control material: `.git` (implicit), `.github/`,
  `.claude/`, `.gemini/`, `.jules/`, `.devcontainer/`, root `AGENTS.md`, root
  `CLAUDE.md`, and `fastlane/`.
- The pinned tree contains no root `NOTICE`; record the absence and preserve a
  future upstream `NOTICE` if one appears during re-vendoring.
- The vendored ASK tree is inert in this slice: no `includeBuild`, no ASK
  `projectDir` mappings, no ASK task invocation from the root build, and no
  changes to upstream-tracked ASK files.
- `UPSTREAM-MODIFIED.md` remains empty. The only PersonaSpeak-created files
  beneath `android/keyboard/` are `UPSTREAM.md` and
  `UPSTREAM-MODIFIED.md`.
- `core-personas` and `core-providers` remain pure Kotlin and unchanged.
- The existing ADR-0001 panel is rejected, non-typing scaffolding. Move its six
  files at 100% similarity; do not change its UI, provider, service, copy,
  package names, or behavior.
- The stub supplies no product or typing evidence. Its only acceptance scope is
  build, install, IME registration, and crash-free service startup; do not type
  into it, transform text with it, take product screenshots of it, or present
  its switcher flow as a journey.
- `:keyboard-stub` and the root `:app` are temporary and may not appear in a
  release graph. Their deletion is a mandatory gate of the later unified ASK
  integration slice.
- Keep `applicationId = "biz.pixelperfectstudios.personaspeak"`, `minSdk = 26`,
  `targetSdk = 35`, and the JDK 21 toolchain unchanged.
- This PR produces exactly one temporary app APK and one stub AAR. It does not
  build or emit an ASK APK.
- Do not import portraits. Do not add fonts. Do not change privacy claims,
  provider behavior, onboarding, settings, or PersonaSpeak UI.

---

### Task 1: Quarantine the rejected panel as `:keyboard-stub`

**Files:**

- Rename: `android/keyboard/` → `android/keyboard-stub/` (six files, contents
  unchanged)
- Modify: `android/settings.gradle.kts:19-22`
- Modify: `android/app/build.gradle.kts:33-35`
- Modify: `.github/workflows/ci.yml:56-85`

**Interfaces:**

- Consumes: the current `:app -> :keyboard` dependency and CI artifact
  contract from merge commit `4d80fd671122b53234792a8f37dae222ef1de5c1`.
- Produces: the temporary `:app -> :keyboard-stub` dependency, an unchanged
  merged `PersonaBoardService`, and the path `android/keyboard/` reserved for
  the inert ASK snapshot.

- [ ] **Step 1: Record the immutable base and prove the six-file legacy scope**

```bash
git fetch origin main
BASE_SHA="$(git rev-parse origin/main)"
test "$BASE_SHA" = "$(git merge-base HEAD origin/main)"
rg --files android/keyboard | sort
```

Expected exactly:

```text
android/keyboard/build.gradle.kts
android/keyboard/src/main/AndroidManifest.xml
android/keyboard/src/main/kotlin/biz/pixelperfectstudios/personaspeak/keyboard/PersonaBoardService.kt
android/keyboard/src/main/kotlin/biz/pixelperfectstudios/personaspeak/keyboard/PersonaPanel.kt
android/keyboard/src/main/res/values/strings.xml
android/keyboard/src/main/res/xml/personaboard_method.xml
```

Stop if the base moved after `BASE_SHA` was recorded or the file list differs.

- [ ] **Step 2: Move the legacy module without editing its contents**

```bash
PERSONASPEAK_JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd android
env JAVA_HOME="$PERSONASPEAK_JAVA_HOME" ./gradlew :keyboard:clean --no-daemon
cd ..
test ! -d android/keyboard/build
git mv android/keyboard android/keyboard-stub
```

Do not rename packages, classes, resources, strings, or the service. This is a
directory allocation, not another attempt to make the rejected panel work.

- [ ] **Step 3: Rename the Gradle project and temporary app dependency**

Replace the module includes at the bottom of `android/settings.gradle.kts`
with:

```kotlin
include(":core-personas")
include(":core-providers")
include(":keyboard-stub")
include(":app")
```

Replace the first dependency in `android/app/build.gradle.kts` with:

```kotlin
dependencies {
    implementation(project(":keyboard-stub"))
```

Do not change the application ID, SDK levels, Compose configuration, or any
other dependency.

- [ ] **Step 4: Rename the CI task and artifact contract**

In `.github/workflows/ci.yml`, keep the job name and JDK setup, but make the
build command and assertion read:

```yaml
      - name: Build and test the current Android graph
        working-directory: android
        run: >-
          ./gradlew
          :core-personas:build
          :core-providers:build
          :keyboard-stub:assembleDebug
          :app:assembleDebug
          :app:testDebugUnitTest
          --no-daemon
      - name: Require the temporary app APK and keyboard-stub AAR
        working-directory: android
        shell: bash
        run: |
          set -euo pipefail
          mapfile -t artifacts < <(find . \( -path '*/build/outputs/apk/*/*.apk' -o -path '*/build/outputs/aar/*.aar' \) -type f -print | sort)
          printf '%s\n' "${artifacts[@]}"
          test "${#artifacts[@]}" -eq 2
          test "${artifacts[0]}" = "./app/build/outputs/apk/debug/app-debug.apk"
          test "${artifacts[1]}" = "./keyboard-stub/build/outputs/aar/keyboard-stub-debug.aar"
```

The word `temporary` is deliberate. This job must not imply that the stub is
the selected keyboard architecture.

- [ ] **Step 5: Prove the move is content-identical**

```bash
BASE_SHA="$(git merge-base HEAD origin/main)"
git diff --find-renames=100% --summary "$BASE_SHA" -- \
  android/keyboard android/keyboard-stub
git diff --find-renames=100% "$BASE_SHA" -- \
  android/keyboard android/keyboard-stub
```

Expected: six `rename ... (100%)` summary lines and no content hunks. Any
similarity below 100% fails the task.

- [ ] **Step 6: Build the renamed current graph from `android/`**

```bash
PERSONASPEAK_JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd android
env JAVA_HOME="$PERSONASPEAK_JAVA_HOME" ./gradlew \
  :core-personas:build \
  :core-providers:build \
  :keyboard-stub:assembleDebug \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  --no-daemon
find . \( -path '*/build/outputs/apk/*/*.apk' -o -path '*/build/outputs/aar/*.aar' \) \
  -type f -print | sort
cd ..
```

Expected: `BUILD SUCCESSFUL`, `:core-personas:test` executes,
`:core-providers:test` and `:app:testDebugUnitTest` remain honestly
`NO-SOURCE`, and the only outputs are:

```text
./app/build/outputs/apk/debug/app-debug.apk
./keyboard-stub/build/outputs/aar/keyboard-stub-debug.aar
```

- [ ] **Step 7: Commit the mechanical quarantine**

```bash
git add .github/workflows/ci.yml android/settings.gradle.kts \
  android/app/build.gradle.kts android/keyboard-stub
git diff --cached --check
git diff --cached --find-renames=100% --summary
git commit -m "build: quarantine the legacy keyboard stub"
```

Expected: the commit contains the six 100% renames plus the three build/CI
files. It contains no source-content edit.

### Task 2: Import the pinned ASK tree with reproducible provenance

**Files:**

- Create: `android/keyboard/` (5,957 files from the filtered pristine archive)
- Create: `android/keyboard/UPSTREAM.md`
- Create: `android/keyboard/UPSTREAM-MODIFIED.md`
- Modify: `PATCHNOTES.md`

**Interfaces:**

- Consumes: the free `android/keyboard/` path from Task 1 and the accepted
  ingestion policy in ADR-0004.
- Produces: a 5,959-file inert snapshot directory (5,957 upstream files plus
  two PersonaSpeak provenance files) with zero upstream modifications.

- [ ] **Step 1: Clone and verify the exact upstream object in disposable space**

```bash
ASK_WORKDIR=/tmp/personaspeak-ask-ingestion-workdir
ASK_SOURCE="$ASK_WORKDIR/source"
ASK_PRISTINE="$ASK_WORKDIR/pristine"
test ! -e "$ASK_WORKDIR"
mkdir "$ASK_WORKDIR"

git clone --no-checkout --depth 1 --branch 1.13-r1 \
  https://github.com/AnySoftKeyboard/AnySoftKeyboard.git "$ASK_SOURCE"
test "$(git -C "$ASK_SOURCE" rev-parse '1.13-r1^{commit}')" = \
  "8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d"
```

Expected: the tag resolves exactly to the approved commit. Do not continue on
a different SHA, even if the tag name is unchanged.

- [ ] **Step 2: Produce the filtered pristine archive twice**

```bash
ASK_WORKDIR=/tmp/personaspeak-ask-ingestion-workdir
ASK_SOURCE="$ASK_WORKDIR/source"
ASK_PRISTINE="$ASK_WORKDIR/pristine"
mkdir android/keyboard "$ASK_PRISTINE"
for destination in android/keyboard "$ASK_PRISTINE"; do
  git -C "$ASK_SOURCE" archive --format=tar \
    8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d \
    ':(exclude).github' \
    ':(exclude).claude' \
    ':(exclude).gemini' \
    ':(exclude).jules' \
    ':(exclude).devcontainer' \
    ':(exclude)AGENTS.md' \
    ':(exclude)CLAUDE.md' \
    ':(exclude)fastlane' \
    ':(exclude)fastlane/*' \
    | tar -x -C "$destination"
done
```

The first extraction becomes the vendored tree. The second is throwaway
comparison evidence. `git archive` excludes `.git/` by construction.

- [ ] **Step 3: Verify the filtered upstream inventory before adding our files**

```bash
ASK_WORKDIR=/tmp/personaspeak-ask-ingestion-workdir
ASK_PRISTINE="$ASK_WORKDIR/pristine"
test "$(find android/keyboard -type f | wc -l | tr -d ' ')" = "5957"
test "$(find "$ASK_PRISTINE" -type f | wc -l | tr -d ' ')" = "5957"
diff -rq "$ASK_PRISTINE" android/keyboard
test -f android/keyboard/LICENSE
test ! -e android/keyboard/NOTICE
test ! -e android/keyboard/.github
test ! -e android/keyboard/.claude
test ! -e android/keyboard/.gemini
test ! -e android/keyboard/.jules
test ! -e android/keyboard/.devcontainer
test ! -e android/keyboard/AGENTS.md
test ! -e android/keyboard/CLAUDE.md
test ! -e android/keyboard/fastlane
test -z "$(find android/keyboard -type f -size +99M -print -quit)"
```

Expected: both counts are 5,957, `diff` exits zero, `LICENSE` exists, the
pinned tree has no root `NOTICE`, every repository-control exclusion is absent,
and no individual file approaches GitHub's 100 MiB limit.

- [ ] **Step 4: Create `UPSTREAM.md` with the exact provenance contract**

Create `android/keyboard/UPSTREAM.md` with:

````markdown
# AnySoftKeyboard vendored snapshot provenance

This file is load-bearing attribution and update procedure. Keep it plain and
in sync with the tree it describes.

## Source

- Upstream: https://github.com/AnySoftKeyboard/AnySoftKeyboard
- Tag: `1.13-r1`
- Commit: `8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d`
- Upstream license: Apache-2.0
- Vendored: 2026-07-22

The pinned tree contains `LICENSE` and no root `NOTICE`. Existing source-file
license headers are preserved.

## Reproduce the pristine snapshot

Clone the tag, verify that it resolves to the commit above, then run:

```bash
git archive --format=tar \
  8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d \
  ':(exclude).github' \
  ':(exclude).claude' \
  ':(exclude).gemini' \
  ':(exclude).jules' \
  ':(exclude).devcontainer' \
  ':(exclude)AGENTS.md' \
  ':(exclude)CLAUDE.md' \
  ':(exclude)fastlane' \
  ':(exclude)fastlane/*' \
  | tar -x -C <destination>
```

`git archive` never emits `.git/`. The other exclusions are upstream CI,
agent-control, development-environment, and release-management material; they
are not keyboard source, build logic, tests, resources, or license material.

## Re-vendor procedure

1. Resolve the selected release tag to an immutable commit and record both.
2. Extract a fresh pristine tree with the command and exclusions above.
3. Replay every current entry in `UPSTREAM-MODIFIED.md` against the new tree.
4. Preserve PersonaSpeak-owned files under our own packages.
5. Compare the regenerated pristine tree with `android/keyboard/` using
   `diff -rq`; every file differing on both sides must appear exactly once in
   `UPSTREAM-MODIFIED.md`.
6. Update this source record, the rent ledger, license evidence, tests, and the
   patch note in the same PR.

Files present only on the PersonaSpeak side are our additions and are not
upstream modifications. A file whose upstream edit is reverted to pristine is
removed from the ledger; the ledger describes current rent, not history.
````

- [ ] **Step 5: Create the initially empty rent ledger**

Create `android/keyboard/UPSTREAM-MODIFIED.md` with:

````markdown
# AnySoftKeyboard upstream-modification ledger

This file lists every upstream-tracked file whose contents differ from the
pristine snapshot described in `UPSTREAM.md`. Use one entry per file:

```text
- <path-from-android/keyboard> — <reason for the current modification>
```

## Files modified against pristine

None. This ingestion slice does not modify ASK.
````

- [ ] **Step 6: Prove the rent ledger is genuinely empty**

Run under Bash:

```bash
set -euo pipefail
ASK_WORKDIR=/tmp/personaspeak-ask-ingestion-workdir
ASK_PRISTINE="$ASK_WORKDIR/pristine"
set +e
delta_output="$(diff -rq "$ASK_PRISTINE" android/keyboard)"
diff_status=$?
set -e
test "$diff_status" -eq 1
printf '%s\n' "$delta_output"
test "$(printf '%s\n' "$delta_output" | wc -l | tr -d ' ')" = "2"
printf '%s\n' "$delta_output" \
  | rg -Fx "Only in android/keyboard: UPSTREAM-MODIFIED.md"
printf '%s\n' "$delta_output" \
  | rg -Fx "Only in android/keyboard: UPSTREAM.md"
test "$(find android/keyboard -type f | wc -l | tr -d ' ')" = "5959"
rm -rf "$ASK_WORKDIR"
```

Expected: only the two provenance files differ, and the final directory has
5,959 files. Any differing upstream file blocks the commit and requires either
restoring pristine content or recording an explicitly approved rent entry.

- [ ] **Step 7: Add the ingestion patch note**

Add at the top of the current 2026-07-22 section in `PATCHNOTES.md`:

```markdown
- AnySoftKeyboard `1.13-r1` now occupies `android/keyboard/` as a pinned,
  inert snapshot with an empty upstream-rent ledger. The rejected panel moved
  unchanged to `:keyboard-stub`; it remains scaffolding, not a comeback tour.
```

- [ ] **Step 8: Force-add the faithful snapshot and commit it separately**

```bash
set -euo pipefail
git add PATCHNOTES.md
git add -f android/keyboard
git diff --cached --check -- \
  PATCHNOTES.md \
  android/keyboard/UPSTREAM.md \
  android/keyboard/UPSTREAM-MODIFIED.md
test "$(git diff --cached --name-only | wc -l | tr -d ' ')" = "5960"
git commit -m "chore: vendor AnySoftKeyboard 1.13-r1"
```

Expected: 5,959 snapshot/provenance files plus `PATCHNOTES.md`. Do not include
build outputs, archives, clone metadata, or scratch directories. Do not run an
unscoped `git diff --cached --check`: the pristine upstream tree contains
pre-existing whitespace and line-ending findings which must remain byte-for-byte
identical. Step 6's archive comparison is the upstream-content gate.

### Task 3: Prove ASK is inert and the temporary baseline still installs

**Files:**

- No repository file changes.

**Interfaces:**

- Consumes: Task 1's renamed root graph and Task 2's inert source snapshot.
- Produces: deterministic build, artifact, install, registration, and
  crash-startup evidence. It produces no claim that the legacy panel types or
  represents the product.

- [ ] **Step 1: Prove the root Gradle graph excludes ASK**

```bash
PERSONASPEAK_JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd android
env JAVA_HOME="$PERSONASPEAK_JAVA_HOME" ./gradlew projects --no-daemon
cd ..
rg -n 'includeBuild|android/keyboard|projectDir' android/settings.gradle.kts
```

Expected: Gradle lists only `:app`, `:core-personas`, `:core-providers`, and
`:keyboard-stub`. The `rg` command exits with no matches. No `:ime:*`,
`:addons:*`, or `:api` project is visible yet.

- [ ] **Step 2: Rebuild the exact current graph and enumerate outputs**

```bash
PERSONASPEAK_JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd android
env JAVA_HOME="$PERSONASPEAK_JAVA_HOME" ./gradlew clean \
  :core-personas:build \
  :core-providers:build \
  :keyboard-stub:assembleDebug \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  --no-daemon
find . \( -path '*/build/outputs/apk/*/*.apk' -o -path '*/build/outputs/aar/*.aar' \) \
  -type f -print | sort
cd ..
```

Expected: `BUILD SUCCESSFUL` and exactly:

```text
./app/build/outputs/apk/debug/app-debug.apk
./keyboard-stub/build/outputs/aar/keyboard-stub-debug.aar
```

No path below `android/keyboard/` may contain `build/outputs`.

- [ ] **Step 3: Verify registration and service startup without exercising the rejected UX**

With the repository's emulator running:

```bash
set -euo pipefail
APP_ID='biz.pixelperfectstudios.personaspeak'
LEGACY_IME='biz.pixelperfectstudios.personaspeak/biz.pixelperfectstudios.personaspeak.keyboard.PersonaBoardService'
PREVIOUS_IME="$(adb shell settings get secure default_input_method | tr -d '\r')"
if adb shell ime list -s | tr -d '\r' | rg -Fx "$LEGACY_IME" >/dev/null; then
  LEGACY_WAS_ENABLED=1
else
  LEGACY_WAS_ENABLED=0
fi
LEGACY_ENABLED_BY_TEST=0
restore_ime() {
  if test -n "$PREVIOUS_IME" && test "$PREVIOUS_IME" != "null"; then
    adb shell ime set "$PREVIOUS_IME" >/dev/null
  fi
  if test "$LEGACY_ENABLED_BY_TEST" -eq 1; then
    adb shell ime disable "$LEGACY_IME" >/dev/null
  fi
}
trap restore_ime EXIT

adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell ime list -a -s | tr -d '\r' | rg -Fx "$LEGACY_IME"
adb logcat -c
adb shell ime enable "$LEGACY_IME"
if test "$LEGACY_WAS_ENABLED" -eq 0; then
  LEGACY_ENABLED_BY_TEST=1
fi
adb shell ime set "$LEGACY_IME"
adb shell am start -W -n \
  "$APP_ID/biz.pixelperfectstudios.personaspeak.app.MainActivity"
APP_PID="$(adb shell pidof "$APP_ID" | tr -d '\r')"
test -n "$APP_PID"
if adb logcat --pid="$APP_PID" -d | rg -n 'FATAL EXCEPTION|AndroidRuntime'; then
  exit 1
fi
```

Expected: install succeeds, the service component is registered, switching it
on for startup does not crash, the temporary activity launches, and the trap
restores the previous IME. Do not tap the panel, type into its local field,
invoke Transform, use its back button, record it as a journey, or take product
screenshots. This is a retirement-baseline smoke check, nothing more.

- [ ] **Step 4: Reconfirm product boundaries**

```bash
git grep -n 'biz\.pixelperfectstudios\.personaspeak' HEAD -- android/keyboard \
  ':!android/keyboard/UPSTREAM.md' \
  ':!android/keyboard/UPSTREAM-MODIFIED.md'
git diff origin/main...HEAD -- android/core-personas android/core-providers
test "$(git rev-list --count origin/main..HEAD)" = "2"
STUB_COMMIT="$(git rev-list --reverse origin/main..HEAD | sed -n '1p')"
test "$(git show -s --format=%s "$STUB_COMMIT")" = \
  "build: quarantine the legacy keyboard stub"
STUB_RENAMES="$(git diff-tree --no-commit-id -r --find-renames=100% \
  --name-status "$STUB_COMMIT^" "$STUB_COMMIT" -- \
  android/keyboard android/keyboard-stub)"
printf '%s\n' "$STUB_RENAMES"
test "$(printf '%s\n' "$STUB_RENAMES" | rg -c '^R100\t')" = "6"
test -z "$(printf '%s\n' "$STUB_RENAMES" | rg -v '^R100\t' || true)"
```

Expected: the vendored upstream tree contains no PersonaSpeak package, both
core diffs are empty, and the first implementation commit contains exactly six
`R100` records with no other change under either keyboard path. Checking that
commit directly is required:
after snapshot ingestion reuses `android/keyboard/`, an aggregate range diff
can no longer pair every old path with its stub destination.

### Task 4: Prepare deterministic review and PR evidence

**Files:**

- No repository file changes.

**Interfaces:**

- Consumes: the two implementation commits and all Task 3 receipts.
- Produces: an exact-range non-author verdict and a draft PR whose claims stop
  at ingestion, build/install continuity, and provenance.

- [ ] **Step 1: Verify final scope, hygiene, and forbidden paths**

```bash
git status --short --branch
git diff origin/main...HEAD --check -- \
  .github/workflows/ci.yml PATCHNOTES.md android/settings.gradle.kts \
  android/app/build.gradle.kts android/keyboard-stub \
  android/keyboard/UPSTREAM.md android/keyboard/UPSTREAM-MODIFIED.md
git log --oneline origin/main..HEAD
git diff --name-status origin/main...HEAD -- \
  .github/workflows/ci.yml PATCHNOTES.md android/settings.gradle.kts \
  android/app/build.gradle.kts android/keyboard-stub
git diff --name-only origin/main...HEAD -- android/core-personas android/core-providers
find android/keyboard -type f -size +99M -print
find android/keyboard -type d -name .git -print
find android/keyboard \( -path '*/.github' -o -path '*/.claude' \
  -o -path '*/.gemini' -o -path '*/.jules' -o -path '*/.devcontainer' \) -print
find android/keyboard \( -name AGENTS.md -o -name CLAUDE.md \) -print
```

Expected: clean worktree; two conventional commits; only planned first-party
files outside the 5,959-file snapshot; no core changes; no oversized file,
nested git directory, upstream agent-control directory, or nested agent
instruction file. The scoped whitespace check covers every first-party change;
the Task 2 pristine-archive comparison covers the unchanged upstream snapshot.

- [ ] **Step 2: Run repository documentation gates**

```bash
python3 desktop/validate_personas.py
python3 desktop/personaspeak.py --list
rg -n 'T[B]D|T[O]DO|F[I]XME' \
  android/keyboard/UPSTREAM.md \
  android/keyboard/UPSTREAM-MODIFIED.md \
  PATCHNOTES.md
rg -n 'working demo I[M]E|prove it still type[s]|provide the demo A[P]K' \
  docs/adr/0004-vendored-snapshot-ingestion.md \
  docs/adr/0006-gradle-composition-for-the-graft.md \
  docs/superpowers/specs/2026-07-22-single-apk-ask-integration-design.md
```

Expected: four personas validate and list; both scans exit without matches.
Do not scan the 5,957 pristine upstream files for PersonaSpeak placeholder or
voice rules; they are unmodified third-party source.

- [ ] **Step 3: Request independent review against immutable digests**

```bash
BASE_SHA="$(git rev-parse origin/main)"
HEAD_SHA="$(git rev-parse HEAD)"
printf '%s..%s\n' "$BASE_SHA" "$HEAD_SHA"
```

Give a non-author reviewer the exact range, ADR-0004, ADR-0006, the approved
single-APK design, and this plan. Require it to verify:

- the six stub files are 100% renames with no behavior investment;
- no review or evidence claim calls the stub a working or typing keyboard;
- the filtered archive resolves to the pinned tag and SHA;
- all 5,957 upstream files match a regenerated filtered archive;
- the exclusion list removes upstream agent/repository control but retains
  source, build logic, tests, resources, and license material;
- the rent ledger is genuinely empty;
- ASK is absent from the root Gradle graph;
- the current graph emits only the temporary app APK and stub AAR;
- `core-*`, application identity, SDK levels, and privacy posture are unchanged;
- the import contains no file at or above GitHub's 100 MiB limit;
- the plan leaves unambiguous deletion gates for `:keyboard-stub` and `:app`.

Critical or Important findings block readiness. Verify every finding against
the tree before editing; record Minor findings with an explicit disposition.

- [ ] **Step 4: Open a draft PR and wait for GitHub CI**

The PR body must state:

- this is inert ingestion, not the unified ASK build;
- the ADR-0001 panel remains rejected and was only renamed unchanged;
- the on-device check covers install, registration, and crash-free startup—not
  typing or product UX;
- the snapshot tag, full SHA, exclusions, upstream file count, and empty-ledger
  evidence;
- the exact temporary APK/AAR outputs;
- no ASK APK is built in this slice;
- no screenshots are applicable because no accepted UI changed;
- the independent reviewer, exact range, findings, and verdict.

Create `/tmp/personaspeak-ask-ingestion-pr-body.md` with those literal receipts
and the resolved base/head SHAs before running the commands below. Do not leave
digest placeholders or claims copied from an earlier run.

Run:

```bash
git push -u origin HEAD
gh pr create --draft \
  --title "chore: ingest the pinned AnySoftKeyboard snapshot" \
  --body-file /tmp/personaspeak-ask-ingestion-pr-body.md
gh pr checks --watch
```

Expected: persona/CLI, patch-note, and Android jobs pass on the exact reviewed
head. Mark ready only after review threads are dispositioned and the owner has
approved this plan. Do not merge as part of plan execution without explicit
owner authorization.
