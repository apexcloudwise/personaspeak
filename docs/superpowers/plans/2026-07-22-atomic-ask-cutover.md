# Atomic ASK Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the root `android/` Gradle build produce branded PersonaSpeak `:ime:app` as its sole APK, with a real bounded `InputConnection` editor adapter and a lifecycle-correct Compose rewrite surface hosted inside the real AnySoftKeyboard input view.

**Architecture:** Keep the accepted dependency direction `:ime:app -> :personaspeak-ui -> core-*`. Android and ASK types stop in the app-owned adapter/host package; the coordinator and cores remain platform-free. Build the ASK closure alongside the temporary rollback modules, prove all build, license, editor, host, and device gates, then remove `:app` and `:keyboard-stub` together in the final cutover commit.

**Tech Stack:** Gradle 9.2.1, AGP 8.13.2, Kotlin 2.3.10, JDK 21, Android compile SDK 36 / target SDK 35 / minimum SDK 26, AndroidX Compose, Lifecycle, SavedState, ViewModel, Robolectric, JUnit 4, Bash, ADB.

## Global Constraints

- Immutable starting point: `ed1b723088a69998e25d3703eb00e052b49a524f` from `origin/main`.
- Integration worktree: `/Users/devkiran/workspace/personaspeak-workers/issue-47-atomic-ask-cutover`; branch: `feat/issue-47-atomic-ask-cutover`.
- Do not stage, move, edit, or delete anything in the detached primary checkout `/Users/devkiran/workspace/personaspeak`, especially `.superpowers/`, `docs/design/stitch-prompt.md`, `stitch_personaspeak_ui_mockups.zip`, or `stitch_personaspeak_ui_mockups/`.
- The merged graph contains exactly one Android application project, `:ime:app`, and exactly one APK after a clean debug assembly.
- Preserve `:ime:app -> :personaspeak-ui -> :core-personas/:core-providers`; core modules may not gain Android, ASK, Compose, storage, navigation, logging, analytics, or network dependencies.
- Preserve all `CaptureResult`, `ReplaceResult`, and `StaleReason` variants. Stale means no mutation; accepted-but-unverified means `WriteUnconfirmed`; rejected commands mean `WriteRejected`; no automatic retry.
- Do not persist or log draft text, provider output, `EditorSnapshot`, or `RewriteCandidate`. Saved state may hold only non-content UI flags or identifiers.
- Preserve all rejected-topology bans in ADR-0006 and the accepted ASK integration design: no composite build, nested Gradle invocation, published local ASK artifacts, source copying into a second tree, duplicate keyboard shell, or ASK dependency leaking into `:personaspeak-ui` or a core module.
- Every edit to a file present in the pinned ASK snapshot must have exactly one contemporaneous entry in `android/keyboard/UPSTREAM-MODIFIED.md`.
- AgentChattr is the control plane. Every delegated handoff names owner, scope, base SHA, branch/worktree, edit permission and files, artifact/report, commands, commit SHA, open risks, and explicit handback; replies `@mention` the delegator.
- Only one writer may hold a worktree/file-set lease. Claude owns the two bounded journeys below; Codex owns integration, roadmap alignment, evidence, PR state, thread closure, final deletion, and merge readiness.
- `agy`/Gemini is reserved as the current-head independent non-author grader if Claude or OpenCode contributes code.

## File and Responsibility Map

- `android/settings.gradle.kts`: the single root project graph and explicit ASK logical-path-to-directory mappings.
- `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`: one repository policy, reconciled properties, and one catalog.
- `android/buildSrc/`: root-visible ASK dictionary/version build logic without a nested build.
- `android/scripts/verify-ask-closure.sh`: exact project and dependency-closure gate.
- `android/scripts/verify-single-apk.sh`: exact one-application-project and one-APK gate.
- `android/scripts/verify-upstream-ledger.sh`: pinned pristine-snapshot versus rent-ledger gate.
- `android/scripts/verify-dictionary-licenses.sh`: dictionary input-to-notice mapping gate.
- `android/keyboard/addons/languages/english/pack/build.gradle`: disables unlicensed raw corpus inputs while retaining the AOSP combined dictionary and upstream-authored prebuilt XML.
- `android/keyboard/buildSrc/src/main/java/MakeDictionaryPlugin.java`: honors the explicit raw-input switch.
- `android/keyboard/UPSTREAM-MODIFIED.md`: exact upstream-rent ledger.
- `android/keyboard/ime/app/build.gradle`: PersonaSpeak application ID, Kotlin/Compose enablement, minimum SDK 26, and direct `:personaspeak-ui` dependency.
- `android/keyboard/ime/app/src/main/java/com/menny/android/anysoftkeyboard/SoftKeyboard.java`: minimal upstream lifecycle forwarding seam.
- `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/editor/InputConnectionEditorPort.kt`: the only `EditorPort` Android adapter.
- `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/host/PersonaSpeakStripActionProvider.kt`: ASK `StripActionProvider` adapter and Compose attachment.
- `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/host/ImeViewTreeOwners.kt`: input-view lifecycle, saved-state, and ViewModel-store ownership.
- `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/RewritePanelViewModel.kt`: request-scoped, non-persistent candidate state.
- `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/RewritePanel.kt`: small Compose capture/review/apply proof surface.
- `.github/workflows/ci.yml`: root-only build, invariant gates, exact artifact upload.
- `docs/evidence/milestone-2/`: command transcript, package/IME dumps, screenshots, and evidence index tied to one commit SHA.

## Rollback-Safe Atomic Sequence

1. Add invariant scripts and tests while the current modules remain untouched.
2. Add the restricted ASK closure to the root graph and make `:ime:app` build while `:app` and `:keyboard-stub` remain available as rollback anchors.
3. Land dictionary/license and upstream-ledger gates.
4. Land the editor adapter and its host-side tests.
5. Land the Compose host, ViewModel, proof surface, and ASK service seam.
6. Prove the complete root test/build/license/ledger gate and emulator journey with the temporary modules still present.
7. In one final commit, remove `:app` and `:keyboard-stub`, remove their settings entries and CI expectations, run a clean build, and assert `:ime:app` is the only application project and its build-output APK is the only APK.
8. If any gate fails before step 7, revert only the failing feature commit. If any gate fails after step 7, revert the single deletion commit; do not reconstruct either temporary module manually.

## Federated Ownership

- **Claude journey A — graph/provenance:** owns only root Gradle composition, ASK module closure, dictionary licensing, ledger verification, and their tests/scripts. Base `ed1b723...`; write lease granted task-by-task only for Tasks 1–3 after plan approval.
- **Claude journey B — editor/host:** owns only `InputConnectionEditorPort`, Compose/view-host owner, their unit tests, and the minimal `SoftKeyboard` forwarding seam. Base `ed1b723...`; write lease granted task-by-task only for Tasks 4–6 after plan approval.
- Claude may use OpenCode for bounded scans, mechanical edits, or isolated test runs, but must review and consolidate the handoff. `oc-bg` and `agy-bg` have no continuing ownership.
- Codex integrates each commit, reruns its gate, owns Tasks 7–10, and does not merge.

---

### Task 1: Encode the Exact ASK Closure Before Composing It

**Files:**
- Create: `android/scripts/expected-ask-projects.txt`
- Create: `android/scripts/verify-ask-closure.sh`
- Create: `android/scripts/tests/verify-ask-closure-test.sh`

**Interfaces:**
- Consumes: Gradle's `projects` output and project dependency reports.
- Produces: `verify-ask-closure.sh <android-root>`, exit 0 only for the approved 28 ASK logical paths (24 application/build-test projects plus four explicit parent projects) and the three first-party libraries; it rejects every `:addons:*:apk` and unexpected application project.

- [ ] **Step 1: Write the failing shell contract test**

Create a fixture Gradle output containing the approved paths, then create a second fixture with `:addons:languages:english:apk`. The test must require the first fixture to pass and the second to print `unexpected ASK project :addons:languages:english:apk` and exit non-zero. The canonical ASK list is:

```text
:addons
:addons:base
:addons:languages
:addons:languages:english
:addons:languages:english:pack
:api
:ime
:ime:addons
:ime:app
:ime:base
:ime:base-rx
:ime:base-test
:ime:chewbacca
:ime:dictionaries
:ime:dictionaries:jnidictionaryv1
:ime:dictionaries:jnidictionaryv2
:ime:fileprovider
:ime:gesturetyping
:ime:nextword
:ime:notification
:ime:overlay
:ime:permissions
:ime:pixel
:ime:prefs
:ime:releaseinfo
:ime:remote
:ime:voiceime
:junit-sharding
```

The final graph additionally contains `:core-personas`, `:core-providers`, and `:personaspeak-ui`. Parent paths are explicit because their build files are configured even when they are grouping projects.

- [ ] **Step 2: Run the test and observe the missing verifier failure**

Run: `bash android/scripts/tests/verify-ask-closure-test.sh`

Expected: non-zero with `android/scripts/verify-ask-closure.sh: No such file or directory`.

- [ ] **Step 3: Implement the verifier**

Use `set -euo pipefail`, run `./gradlew -p "$root" projects --console=plain`, normalize only lines matching `Project ':…'`, and compare them with a sorted expected file. Then run `:ime:app:dependencies --configuration debugRuntimeClasspath`, reject `project :addons:.*:apk`, and enumerate files applying `com.android.application`; after Task 8 the only allowed path is `keyboard/ime/app/build.gradle`. Every `rg` probe must distinguish exit 1 (no match) from exit 2 (tool/read failure).

- [ ] **Step 4: Prove positive and negative controls**

Run: `bash android/scripts/tests/verify-ask-closure-test.sh`

Expected: `PASS: exact ASK closure accepted; unexpected project rejected`.

- [ ] **Step 5: Commit**

```bash
git add android/scripts/expected-ask-projects.txt android/scripts/verify-ask-closure.sh android/scripts/tests/verify-ask-closure-test.sh
git commit -m "test(android): pin milestone two ASK closure"
```

### Task 2: Compose the Restricted ASK Graph in the Root Build

**Files:**
- Modify: `android/settings.gradle.kts`
- Modify: `android/build.gradle.kts`
- Modify: `android/gradle.properties`
- Modify: `android/gradle/libs.versions.toml`
- Create: `android/buildSrc/settings.gradle.kts`
- Create: `android/buildSrc/build.gradle.kts`
- Modify only if the shim cannot absorb it: ASK files listed in the same commit's `android/keyboard/UPSTREAM-MODIFIED.md` entry.

**Interfaces:**
- Consumes: the exact closure from Task 1 and ASK build logic rooted at `android/keyboard`.
- Produces: one root invocation where `:ime:app:assembleDebug`, all ASK unit tests, and all first-party tests are addressable without `GradleBuild`, shelling into `keyboard/gradlew`, or a composite build.

- [ ] **Step 1: Run the closure verifier against the current graph**

Run: `bash android/scripts/verify-ask-closure.sh android`

Expected: FAIL with missing `:ime:app` and the other ASK paths.

- [ ] **Step 2: Add explicit project mappings**

In `settings.gradle.kts`, define one helper and call it for every path in Task 1:

```kotlin
fun askProject(path: String, directory: String) {
    include(path)
    project(path).projectDir = file("keyboard/$directory")
}

askProject(":api", "api")
askProject(":junit-sharding", "junit-sharding")
askProject(":addons", "addons")
askProject(":addons:base", "addons/base")
askProject(":addons:languages", "addons/languages")
askProject(":addons:languages:english", "addons/languages/english")
askProject(":addons:languages:english:pack", "addons/languages/english/pack")
askProject(":ime", "ime")
askProject(":ime:base", "ime/base")
askProject(":ime:base-rx", "ime/base-rx")
askProject(":ime:base-test", "ime/base-test")
askProject(":ime:prefs", "ime/prefs")
askProject(":ime:notification", "ime/notification")
askProject(":ime:remote", "ime/remote")
askProject(":ime:fileprovider", "ime/fileprovider")
askProject(":ime:addons", "ime/addons")
askProject(":ime:dictionaries", "ime/dictionaries")
askProject(":ime:dictionaries:jnidictionaryv1", "ime/dictionaries/jnidictionaryv1")
askProject(":ime:dictionaries:jnidictionaryv2", "ime/dictionaries/jnidictionaryv2")
askProject(":ime:nextword", "ime/nextword")
askProject(":ime:pixel", "ime/pixel")
askProject(":ime:overlay", "ime/overlay")
askProject(":ime:gesturetyping", "ime/gesturetyping")
askProject(":ime:voiceime", "ime/voiceime")
askProject(":ime:releaseinfo", "ime/releaseinfo")
askProject(":ime:chewbacca", "ime/chewbacca")
askProject(":ime:permissions", "ime/permissions")
askProject(":ime:app", "ime/app")
```

Keep `:app` and `:keyboard-stub` included until Task 8.

- [ ] **Step 3: Reconcile build logic and properties**

Expose ASK's `MakeDictionaryPlugin`, version-generator plugins, task classes, Kotlin sources, and Groovy helpers through root `buildSrc`; merge the ASK catalog aliases into the existing root catalog after mechanically rejecting duplicate aliases with unequal values. Keep root repository policy at `FAIL_ON_PROJECT_REPOS`; add only repositories required by `:ime:app:dependencies`. Set `android.enableJetifier=true`, `android.defaults.buildfeatures.buildconfig=false`, `android.nonTransitiveRClass=false`, `android.nonFinalResIds=false`, `org.gradle.configuration-cache=false`, and `org.gradle.jvmargs=-Xmx8000M -Dfile.encoding=UTF-8`. With that exact configuration-cache flag active, run `:core-personas:build :core-providers:build :personaspeak-ui:testDebugUnitTest` and require all first-party modules to pass before testing ASK.

- [ ] **Step 4: Point root-anchored ASK scripts through one source-root shim**

Define `askSourceRoot = rootProject.file("keyboard")` once in root extras. Apply ASK scripts by absolute file from that root. Mechanically scan every included ASK build file and applied script for `$rootDir`, `${rootDir}`, and `rootDir.absolutePath`; rewrite each reference that targets ASK-owned `config/`, `gradle/`, `addons/`, `checkstyle/`, `spotless/`, or Robolectric-support paths to derive from `rootProject.ext.askSourceRoot`. This explicitly includes the chains beginning at `ime/app/build.gradle`, `gradle/apk_module.gradle`, `gradle/android_general.gradle`, `gradle/android_unit_test.gradle`, and `addons/gradle/language_pack_lib.gradle`. Leave references that intentionally target unified-root output directories explicit and separately named. Do not scatter `../keyboard` literals. Record every modified vendored file in the ledger in the same commit, then rerun the scan and require no unresolved ASK-source `$rootDir` reference.

- [ ] **Step 5: Brand and link the ASK application**

In `:ime:app`, apply Kotlin Android and Compose, set `override_app_id = 'biz.pixelperfectstudios.personaspeak'`, set minimum SDK 26, preserve target 35/compile 36, and add `implementation project(':personaspeak-ui')`. Audit `${applicationId}` authorities and confirm the merged manifest contains no `com.menny.android.anysoftkeyboard` authority owned by PersonaSpeak.

- [ ] **Step 6: Run the unified build while rollback modules remain**

Run:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./android/gradlew -p android --no-daemon \
  :core-personas:test :core-providers:test :personaspeak-ui:testDebugUnitTest \
  :ime:app:testDebugUnitTest :ime:app:assembleDebug \
  :app:testDebugUnitTest :app:assembleDebug :keyboard-stub:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; no nested Gradle process; `:ime:app` resolves `:personaspeak-ui` and only the closure from Task 1.

- [ ] **Step 7: Commit**

Stage only named build files, required shims, and matching ledger entries. Commit: `build(android): compose restricted ASK application graph`.

### Task 3: Make Dictionary Licensing and Upstream Rent Fail Closed

**Files:**
- Create: `android/keyboard/DICTIONARY-LICENSES.md`
- Create: `android/scripts/verify-dictionary-licenses.sh`
- Create: `android/scripts/tests/verify-dictionary-licenses-test.sh`
- Create: `android/scripts/verify-upstream-ledger.sh`
- Create: `android/scripts/tests/verify-upstream-ledger-test.sh`
- Modify: `android/keyboard/buildSrc/src/main/java/MakeDictionaryPlugin.java`
- Modify: `android/keyboard/addons/languages/english/pack/build.gradle`
- Modify: `android/keyboard/UPSTREAM-MODIFIED.md`
- Modify: ASK Additional Software Licenses string/resource page used by `MainSettingsActivity`.

**Interfaces:**
- Consumes: pinned upstream tag `1.13-r1`, SHA `8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d` and the English pack's actual dictionary task inputs.
- Produces: a deterministic source/license manifest surfaced inside settings, and scripts that reject unlicensed build inputs or unledgered upstream modifications.

- [ ] **Step 1: Write negative-control tests**

The dictionary test copies a fixture tree, adds `dictionary/inputs/unmapped.txt`, and requires the verifier to fail with `unlicensed dictionary input: dictionary/inputs/unmapped.txt`. The ledger test mutates one pristine fixture file without a ledger entry, then adds one exact entry and requires pass; duplicate and missing entries must fail.

- [ ] **Step 2: Run tests and observe missing-verifier failures**

Run: `bash android/scripts/tests/verify-dictionary-licenses-test.sh && bash android/scripts/tests/verify-upstream-ledger-test.sh`

Expected: non-zero because both verifier scripts are absent.

- [ ] **Step 3: Exclude the provenance-ambiguous corpus from the built dictionary**

Add a boolean extension `dictionaryTextInputsEnabled` defaulting to true in `MakeDictionaryPlugin`. Guard only registration and merge of `dictionary/inputs/*` with it. Set `ext.dictionaryTextInputsEnabled = false` in the English pack before applying `make-dictionary`. This retains `en_wordlist.combined.gz` from AOSP and `prebuilt/additionals.xml` plus `prebuilt/websites.xml`, while the 144 raw corpus/Wikipedia input files do not enter the generated artifact. Do not delete those vendored source files.

- [ ] **Step 4: Write and surface the exact license mapping**

`DICTIONARY-LICENSES.md` must map:

```text
addons/languages/english/pack/dictionary/en_wordlist.combined.gz | AOSP LatinIME | Apache-2.0
addons/languages/english/pack/dictionary/prebuilt/additionals.xml | AnySoftKeyboard | Apache-2.0
addons/languages/english/pack/dictionary/prebuilt/websites.xml | AnySoftKeyboard | Apache-2.0
```

It must state that `dictionary/inputs/*` is intentionally excluded from generation because the snapshot does not establish adequate per-corpus provenance. Add the same three shipped-source notices to ASK's Additional Software Licenses screen. The verifier reads the Gradle switch, enumerates inputs actually selected by `MakeDictionaryPlugin`, and requires exactly one manifest row for each.

- [ ] **Step 5: Implement the pristine ledger verifier**

Clone/fetch the exact upstream SHA into a cache, verify it byte-for-byte, archive with the exclusions in `UPSTREAM.md`, compare the pristine tree with `android/keyboard`, and require every changed or deleted upstream path exactly once in `UPSTREAM-MODIFIED.md`. PersonaSpeak-only additions are allowed only under `biz/pixelperfectstudios/personaspeak`, the provenance documents, or an explicit allowlist in the verifier. Network failure is failure, not a pass.

- [ ] **Step 6: Record current rent**

For each ASK file modified in Tasks 2–3, add one ledger bullet containing path, reason, behavioral delta, and replay guidance. Verify no `None.` sentinel remains once the first entry exists.

- [ ] **Step 7: Prove both gates and dictionary generation**

Run:

```bash
bash android/scripts/tests/verify-dictionary-licenses-test.sh
bash android/scripts/tests/verify-upstream-ledger-test.sh
bash android/scripts/verify-dictionary-licenses.sh android
bash android/scripts/verify-upstream-ledger.sh android
./android/gradlew -p android :addons:languages:english:pack:makeDictionary --no-daemon
```

Expected: all pass; task inputs contain the combined AOSP file and two prebuilt XMLs, and contain no `dictionary/inputs/` path.

- [ ] **Step 8: Commit**

Commit: `build(android): enforce ASK provenance and dictionary notices`.

### Task 4: Implement the Real InputConnection Editor Adapter with Host-Side Tests

**Files:**
- Create: `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/editor/EditorSessionState.kt`
- Create: `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/editor/InputConnectionEditorPort.kt`
- Create: `android/keyboard/ime/app/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ime/editor/FakeInputConnection.kt`
- Create: `android/keyboard/ime/app/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ime/editor/InputConnectionEditorPortTest.kt`

**Interfaces:**
- Consumes: `biz.pixelperfectstudios.personaspeak.ui.editor.EditorPort`, current `InputConnection?`, current `EditorInfo?`, SDK level, and main-thread executor.
- Produces: `EditorSessionState.start(EditorInfo)`, `selectionChanged(...)`, `finish()`, and `InputConnectionEditorPort.captureSnapshot()/attemptReplace()`; the connection supplier is invoked fresh inside each port call.

- [ ] **Step 1: Write adapter tests first**

Use a recording fake `InputConnection`. Cover: complete capture; empty; password and `IME_FLAG_NO_PERSONALIZED_LEARNING`; non-text editor; null connection/read; non-zero `startOffset`; partial extracted text; invalid selection; exactly 8,000 supplementary code points (16,000 UTF-16 units) accepted; 8,001 rejected; session changed; generation changed; text changed; selection changed; every stale path emits zero mutation calls; API 34+ uses exactly one `replaceText(0, oldUtf16Length, replacement, 1, null)`; older API uses `finishComposingText`, `setSelection(0, oldUtf16Length)`, then exactly one `commitText(replacement, 1)`; every required false return is `WriteRejected`; accepted write plus matching reread is `AppliedVerified`; null/mismatching reread is `WriteUnconfirmed`; no second mutation follows either result; neither `deleteSurroundingText` variant is ever called. Supply connection A for capture and connection B for replace to prove no connection is cached across calls.

- [ ] **Step 2: Run the focused tests and observe missing classes**

Run: `./android/gradlew -p android :ime:app:testDebugUnitTest --tests '*InputConnectionEditorPortTest' --no-daemon`

Expected: compile failure for missing `InputConnectionEditorPort`.

- [ ] **Step 3: Implement session and generation ownership**

`EditorSessionState` is main-thread confined. `start` increments a monotonically increasing session token and resets generation; every capture increments generation; `selectionChanged` updates current selection but does not mutate an issued snapshot; `finish` invalidates the session. No `InputConnection`, draft, or replacement string is stored in this state object.

- [ ] **Step 4: Implement exact complete reads and sensitive-editor refusal**

Call `getExtractedText(ExtractedTextRequest(), 0)`. Accept only `startOffset == 0`, `partialStartOffset < 0`, `partialEndOffset < 0`, non-null text, valid UTF-16 selection, text-class non-password input, and at most 8,000 Unicode code points. Copy text immediately to `String`; never retain `ExtractedText` or log its contents.

- [ ] **Step 5: Implement validate-then-single-write replacement**

On the main thread, compare session and generation, reread full text and selection, then issue the SDK-specific single replacement sequence. Do not suspend or dispatch between validation and the mutation call. Reread once afterward and classify the result; never retry.

- [ ] **Step 6: Run focused and contract tests**

Run:

```bash
./android/gradlew -p android \
  :ime:app:testDebugUnitTest --tests '*InputConnectionEditorPortTest' \
  :personaspeak-ui:testDebugUnitTest --tests '*EditorPortContractTest' --no-daemon
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

Commit: `feat(android): adapt ASK InputConnection to EditorPort`.

### Task 5: Add Lifecycle-Correct Compose Input-View Hosting

**Files:**
- Create: `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/host/ImeViewTreeOwners.kt`
- Create: `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/host/PersonaSpeakStripActionProvider.kt`
- Create: `android/keyboard/ime/app/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ime/host/ImeViewTreeOwnersTest.kt`
- Create: `android/keyboard/ime/app/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ime/host/PersonaSpeakStripActionProviderTest.kt`

**Interfaces:**
- Consumes: ASK's existing `KeyboardViewContainerView.addStripAction(StripActionProvider, highPriority)`, the IME window decor view, and a content lambda supplied by Task 6.
- Produces: one parentless `ComposeView` from a `StripActionProvider` and one session-scoped owner implementing `LifecycleOwner`, `SavedStateRegistryOwner`, and `ViewModelStoreOwner`.

- [ ] **Step 1: Write host tests first**

Robolectric tests must prove owners are installed on both decor and `ComposeView` before `setContent`; lifecycle transitions are `INITIALIZED -> CREATED -> STARTED -> RESUMED` on input start and back through `STARTED -> CREATED -> DESTROYED` on finish/destroy; restore occurs before any saved-state consumer; a recreated view receives only an allowed non-content key; finish clears `ViewModelStore`; repeated starts do not accumulate owners. `PersonaSpeakStripActionProviderTest.inflateActionView_returnsParentlessComposeView` must call the provider's real `inflateActionView` method and assert `view.parent == null` before ASK receives it, matching `KeyboardViewContainerView.addStripAction`'s guard.

- [ ] **Step 2: Run and observe missing host classes**

Run: `./android/gradlew -p android :ime:app:testDebugUnitTest --tests '*ImeViewTreeOwnersTest' --tests '*PersonaSpeakStripActionProviderTest' --no-daemon`

Expected: compile failure for missing host classes.

- [ ] **Step 3: Implement owner installation order**

Create `SavedStateRegistryController`, call `performAttach()` then `performRestore(savedBundle)` before composition, install `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner`, and `ViewTreeViewModelStoreOwner` on the decor and Compose view, set `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`, then call `setContent`. Register no provider containing draft, result, snapshot, or candidate text.

- [ ] **Step 4: Attach without replacing ASK's keyboard**

Implement ASK's `StripActionProvider` and return one parentless, wrap-content `ComposeView`. Mount it through `KeyboardViewContainerView.addStripAction(provider, true)` so ASK's existing measure/layout code budgets the strip and leaves the keyboard and candidate view intact. `finishInput()` disposes session content and clears the store. `destroy()` is idempotent. Do not modify `KeyboardViewContainerView.java`; if the existing extension point cannot pass the focused host test, stop and return to plan review rather than silently widening upstream rent.

- [ ] **Step 5: Run host tests**

Run the focused command from Step 2.

Expected: all pass with no leaked child/store.

- [ ] **Step 6: Commit**

Commit: `feat(android): host Compose in ASK input lifecycle`.

### Task 6: Add the Request-Scoped Rewrite Proof Surface and Wire SoftKeyboard

**Files:**
- Modify: `android/personaspeak-ui/build.gradle.kts`
- Create: `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/RewritePanelState.kt`
- Create: `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/RewritePanelViewModel.kt`
- Create: `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/RewritePanel.kt`
- Create: `android/personaspeak-ui/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/RewritePanelViewModelTest.kt`
- Create: `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/PersonaSpeakComposition.kt`
- Modify: `android/keyboard/ime/app/src/main/java/com/menny/android/anysoftkeyboard/SoftKeyboard.java`
- Modify: `android/keyboard/UPSTREAM-MODIFIED.md`

**Interfaces:**
- Consumes: `RewriteCoordinator`, bundled Jeeves persona, `FakeProvider(0)`, `InputConnectionEditorPort`, host from Task 5.
- Produces: visible `Rewrite`, candidate review, `Use this`, typed failure/status copy, and `Settings` actions inside the real ASK input view.

- [ ] **Step 1: Write ViewModel tests first**

Cover idle -> loading -> review without mutation; review contains only the current in-memory candidate; `Use this` maps all typed apply outcomes; a newer request cancels and discards the older result; editor finish cancels an in-flight provider call and clears candidate; a result arriving after finish cannot mutate UI or editor; `SavedStateHandle` contains neither source nor result; `onCleared` cancels work. Assert user-visible copy never embeds draft/provider text for failure states.

- [ ] **Step 2: Run and observe missing ViewModel**

Run: `./android/gradlew -p android :personaspeak-ui:testDebugUnitTest --tests '*RewritePanelViewModelTest' --no-daemon`

Expected: compile failure for missing `RewritePanelViewModel`.

- [ ] **Step 3: Implement the smallest state machine**

Use sealed states `Idle`, `Loading`, `Review(replacement: String)`, and `Message(kind: RewriteMessage)`. Keep the `RewriteCandidate` in one private nullable field only while `Review` is current; never write it to `SavedStateHandle`. `request()` calls the coordinator; `apply()` clears the candidate before calling the coordinator so double-tap cannot emit a second mutation.

- [ ] **Step 4: Implement the Compose row**

Render a compact surface above the ASK keys: `Rewrite` in idle, progress in loading, candidate text plus `Use this`/`Dismiss` in review, typed message plus retry affordance where safe, and a `Settings` icon. Add semantics/test tags `personaspeak_rewrite`, `personaspeak_candidate`, `personaspeak_apply`, and `personaspeak_settings` for device evidence. Do not add history, navigation stacks, local draft fields, or provider configuration.

- [ ] **Step 5: Wire the real service seam**

In `SoftKeyboard`, create one `PersonaSpeakComposition`; forward `onStartInput`, `onStartInputView`, `onCreateInputView`, `onUpdateSelection`, `onFinishInput`, and `onDestroy` while preserving every superclass call. The composition supplies `this::getCurrentInputConnection` to the adapter, calls `addStripAction` after `super.onCreateInputView()`, installs/rechecks owners before composition, and launches `LauncherSettingsActivity` with `FLAG_ACTIVITY_NEW_TASK`. Add one PersonaSpeak settings entry to the existing ASK settings surface and register only its required app component in the `:ime:app` manifest. These are the only required upstream service/manifest edits; ledger each file exactly.

- [ ] **Step 6: Run all editor, host, UI, and ASK regression tests**

Run:

```bash
./android/gradlew -p android --no-daemon \
  :personaspeak-ui:testDebugUnitTest \
  :ime:app:testDebugUnitTest \
  :ime:app:assembleDebug
```

Expected: all pass; assembled manifest declares `.SoftKeyboard` and `LauncherSettingsActivity` under `biz.pixelperfectstudios.personaspeak`.

- [ ] **Step 7: Commit**

Commit: `feat(android): add PersonaSpeak flow to real ASK view`.

### Task 7: Prove the Complete Gate Before Deleting Rollback Modules

**Files:**
- Create: `android/scripts/verify-milestone-2-precutover.sh`
- Create: `docs/evidence/milestone-2/precutover-commands.txt`
- Modify: `docs/superpowers/plans/2026-07-22-atomic-ask-cutover.md` only to check completed boxes and append exact command results.

**Interfaces:**
- Consumes: Tasks 1–6.
- Produces: one reproducible green pre-cutover command plus device proof that the ASK APK already passes install, registration, real typing, fake capture/replace, and settings launch before either rollback module is deleted.

- [ ] **Step 1: Implement the aggregate gate**

The script checks clean tracked state, JDK 21, exact closure, dictionary licenses, ledger, core purity with failure-aware `rg`, all unit tests, `lintDebug` where configured, and `:ime:app:assembleDebug`. It intentionally does not require one APK yet because rollback modules still exist.

- [ ] **Step 2: Run it from a clean commit**

Run: `bash android/scripts/verify-milestone-2-precutover.sh`

Expected: `PASS: milestone 2 pre-cutover gate`; capture elapsed time and SHA in the plan execution log.

- [ ] **Step 3: Qualify the ASK APK while rollback modules still exist**

Install only `android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk` on `emulator-5554`; verify package `biz.pixelperfectstudios.personaspeak`, enable/select its `.SoftKeyboard`, type `Tea at six` with real ASK keys in an external host, request the fake rewrite, verify the candidate appears before mutation, apply once, and launch settings from the IME surface. Record commands, before/after host text, package/IME dumps, crash-filtered logcat, commit SHA, and APK SHA-256 in `precutover-commands.txt`. Restore the prior IME. Expected: every Milestone 2 runtime journey passes; the only intentionally deferred gate is repository-wide exact-one-APK enumeration because the rollback modules are still present.

- [ ] **Step 4: Commit the gate and pre-cutover receipt**

Commit: `test(android): add milestone two precutover gate`.

### Task 8: Atomically Remove Temporary Modules and Enforce Exactly One APK

**Files:**
- Delete: `android/app/`
- Delete: `android/keyboard-stub/`
- Modify: `android/settings.gradle.kts`
- Create: `android/scripts/verify-single-apk.sh`
- Create: `android/scripts/tests/verify-single-apk-test.sh`
- Modify: `.github/workflows/ci.yml`
- Modify: `android/keyboard/gradle/apk_module.gradle` only if required to stop its duplicate convenience copy; ledger that edit.

**Interfaces:**
- Consumes: green Task 7 SHA.
- Produces: one application project and exactly `android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk` after a clean build; CI uploads only that APK plus non-APK reports.

- [ ] **Step 1: Write the artifact verifier test**

Fixtures must prove zero APKs fails, two APKs fail with both paths printed, a duplicate convenience copy fails, and exactly the expected `:ime:app` build-output path passes. The verifier also scans included project build files and requires only `keyboard/ime/app/build.gradle` to apply `com.android.application`.

- [ ] **Step 2: Run the test and observe the missing verifier failure**

Run: `bash android/scripts/tests/verify-single-apk-test.sh`

Expected: non-zero because the verifier is absent.

- [ ] **Step 3: Implement exact enumeration**

Set one root extra `personaSpeakUnifiedBuild = true`. In `android/keyboard/gradle/apk_module.gradle`, conditionally omit registration/finalization of `copy<Variant>Apk` and `copy<Variant>Aab` only when that exact flag is true; preserve upstream nested-build behavior when it is absent. Ledger the file. Make the root `clean` task delete only the explicit generated directory `android/outputs/` in addition to module build directories. After `clean :ime:app:assembleDebug`, enumerate `android/**/build/outputs/apk/**/*.apk` and `android/outputs/**/*.apk`; reject every count other than one and require the canonical path. Do not weaken the count or exclude a duplicate path. The verifier itself is read-only and fails on any stale or duplicate APK; cleanup belongs to `clean`, not to verification.

- [ ] **Step 4: Perform the atomic deletion**

Delete both temporary module directories and remove both settings entries in the same commit. Remove all CI tasks/artifact expectations for them. Do not delete any ASK or first-party library module.

- [ ] **Step 5: Make CI root-only and exact**

CI runs from the root wrapper only: closure, dictionary-license, upstream-ledger and rejected-topology gates; core/UI/ASK tests; clean `:ime:app:assembleDebug`; exact APK verifier; APK Analyzer checks package `biz.pixelperfectstudios.personaspeak`, IME service, settings activity, min 26, target 35; upload the single canonical APK. Add a boundary scan requiring that upstream `com.anysoftkeyboard`/`com.menny.android.anysoftkeyboard` sources do not import `biz.pixelperfectstudios.personaspeak`; the sole Java leaf seam may reference a first-party composition type only through its explicit allowlisted file. No `continue-on-error` on a gate and no negated `rg` that can convert exit 2 to success.

- [ ] **Step 6: Run clean final build twice**

Run:

```bash
./android/gradlew -p android clean --no-daemon
./android/gradlew -p android \
  :core-personas:test :core-providers:test :personaspeak-ui:testDebugUnitTest \
  :ime:app:testDebugUnitTest :ime:app:lintDebug :ime:app:assembleDebug --no-daemon
bash android/scripts/verify-single-apk.sh android
bash android/scripts/verify-ask-closure.sh android
bash android/scripts/verify-dictionary-licenses.sh android
bash android/scripts/verify-upstream-ledger.sh android
./android/gradlew -p android clean :ime:app:assembleDebug --no-daemon
bash android/scripts/verify-single-apk.sh android
```

Expected: both builds succeed; exactly one APK at the canonical path both times.

- [ ] **Step 7: Commit the cutover as one reversible unit**

```bash
git add -A android/app android/keyboard-stub android/settings.gradle.kts android/scripts .github/workflows/ci.yml android/keyboard/gradle/apk_module.gradle android/keyboard/UPSTREAM-MODIFIED.md
git commit -m "build(android): cut over atomically to ASK APK"
```

### Task 9: Capture Current-Head Emulator Acceptance Evidence

**Files:**
- Create: `docs/evidence/milestone-2/README.md`
- Create: `docs/evidence/milestone-2/commands.txt`
- Create: `docs/evidence/milestone-2/package.txt`
- Create: `docs/evidence/milestone-2/ime-list.txt`
- Create: `docs/evidence/milestone-2/logcat.txt`
- Create: `docs/evidence/milestone-2/typing.png`
- Create: `docs/evidence/milestone-2/review.png`
- Create: `docs/evidence/milestone-2/replaced.png`
- Create: `docs/evidence/milestone-2/settings.png`

**Interfaces:**
- Consumes: the exact APK from Task 8 and available emulator `emulator-5554` / AVD `CityZen_Dev`.
- Produces: reproducible installation, registration, startup, typing, capture/review/replace, and settings evidence tied to the tested commit.

- [ ] **Step 1: Reset only app state and install the exact APK**

Record `git rev-parse HEAD` and APK SHA-256. Run `adb -s emulator-5554 install -r <canonical-apk>`, then `pm path biz.pixelperfectstudios.personaspeak`; expected one installed package path. Do not wipe the emulator.

- [ ] **Step 2: Prove IME registration and selection**

Run `adb shell ime list -a`, locate `biz.pixelperfectstudios.personaspeak/com.menny.android.anysoftkeyboard.SoftKeyboard`, enable it, select it, and record `dumpsys input_method`. Expected: PersonaSpeak component is enabled and current.

- [ ] **Step 3: Prove crash-free real ASK startup and typing**

Clear logcat, open a stock text editor, focus a text field, capture the visible ASK keys plus PersonaSpeak row, type `Tea at six` using real ASK key taps, and save `typing.png`. After 10 seconds, filter logcat for the package and require no `FATAL EXCEPTION`, `AndroidRuntime`, ANR, or process death.

- [ ] **Step 4: Prove fake-provider capture, review, and replacement**

Tap `personaspeak_rewrite`; capture `review.png` showing the fake-provider candidate while the editor still contains `Tea at six`. Tap `personaspeak_apply`; capture `replaced.png` showing one replacement and no duplicate insertion. Record UI hierarchy/test-tag evidence and post-write editor text. Repeat once after editing the field between review and apply; expected typed stale message and zero mutation.

- [ ] **Step 5: Prove settings launch**

Tap `personaspeak_settings`, capture `settings.png`, and record resumed activity from `dumpsys activity activities`. Expected: an activity in package `biz.pixelperfectstudios.personaspeak`; no second APK/package.

- [ ] **Step 6: Write the evidence index and commit**

`README.md` maps each issue #47 acceptance item to a command/output/screenshot, includes device API/build, tested commit, APK hash, timestamps, and any harmless log warnings. Commit: `docs: record milestone two emulator evidence`.

### Task 10: Independent Current-Head Review, PR, Threads, and Merge Readiness

**Files:**
- Modify only files required by concrete review findings.
- Update: `docs/evidence/milestone-2/README.md` if the reviewed head changes.

**Interfaces:**
- Consumes: final code/evidence head.
- Produces: clean PR linked to #47/#38, resolved threads, green CI, and an explicit do-not-merge handoff.

- [ ] **Step 1: Push and open the PR**

Push `feat/issue-47-atomic-ask-cutover`; PR body links `Closes #47` and `Roadmap #38`, lists the exact closure, deletion commit, gates, APK path/hash, emulator evidence, upstream-rent entries, and rollback procedure. Do not claim #38 closes.

- [ ] **Step 2: Assign a non-author, different-model-family reviewer**

If Claude/OpenCode wrote code, assign `agy`/Gemini only as grader. Give exact head SHA, read-only permission, no implementation lease, and require severity-ranked findings with file/line evidence plus an explicit verdict against the design, ADRs, issue #47, privacy rules, one-APK gate, and device evidence.

- [ ] **Step 3: Resolve findings with the receiving-review workflow**

Verify each finding before editing. Fix valid findings with focused tests; rebut invalid findings with evidence. Rerun the full Task 8 gate and all affected emulator steps after any code/build change. Reply to every delegated result with `@reviewer`; close every GitHub review thread only after the resolution commit is pushed.

- [ ] **Step 4: Re-grade the exact current head**

The independent reviewer must state `APPROVE` on the same SHA as PR head. Evidence metadata and APK SHA must match that head; otherwise regenerate them.

- [ ] **Step 5: Verify merge readiness without merging**

Require: plan approved; all plan findings resolved; exact-head independent approval; all review threads resolved; required CI green; clean integration worktree; one APK; current device evidence. Post the readiness summary to AgentChattr with mentions, #47, and the PR. Leave the PR unmerged for the user/authorized merger.

## Final Verification Matrix

| Gate | Exact proof |
|---|---|
| Baseline | branch merge-base and recorded origin SHA are `ed1b723088a69998e25d3703eb00e052b49a524f` |
| Unified graph | root `projects` equals the 28 ASK logical paths plus three first-party libraries |
| Dependency direction | `:ime:app -> :personaspeak-ui -> core-*`; no inverse/ASK/core violation |
| Sole artifact | clean build yields one APK at `android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk` |
| Licensing | every selected dictionary input has exactly one surfaced notice; ambiguous corpus inputs are not selected |
| Upstream rent | pristine SHA comparison equals ledger exactly |
| Editor safety | complete-read bounds, sensitive refusals, all stale reasons/no mutation, rejected/unconfirmed outcomes, one write/no retry |
| Host safety | owners installed before composition, valid lifecycle, saved-state restore order, ViewModel store cleared, no persisted content |
| Device | install, register/select IME, crash-free ASK typing, fake capture/review/replace, stale refusal, settings launch |
| Review | independent non-author approval on exact PR head, threads resolved, CI green |

## Plan Self-Review Checklist

- [ ] Re-read ADR-0006, both July 22 specs, stale-field design, keyboard UX design, issues #38/#47, and map every requirement to a task above.
- [ ] Search this file for placeholder language and replace it with exact paths, signatures, commands, and expected results.
- [ ] Verify every interface/type name matches the merged Milestone 1 contracts.
- [ ] Verify all ASK in-place edits named by Tasks 2, 3, 6, and 8 are ledgered in the same commit.
- [ ] Verify deletion happens only in Task 8 after the Task 7 gate.
- [ ] Record independent plan-review findings and resolutions below before granting any product/build write lease.

## Independent Plan Review Record

Initial review: `agy` / Gemini 3.5 Flash (Medium), read-only, plan SHA-256 `2fc21b1d6d9df737f240a745123d692a3038a48607028287d0a54c97f6a4981b`, base `ed1b723088a69998e25d3703eb00e052b49a524f`. Report: `/tmp/personaspeak-m2-plan-review-agy.md`. No repository edits; commit N/A; explicit handback received.

Findings and resolutions:

1. **Major — rootDir ASK script web:** resolved by Task 2 Step 4's exhaustive included-graph scan, named script chains, single `askSourceRoot`, post-rewrite negative scan, and same-commit ledger rule.
2. **Major — duplicate APK/AAB copy tasks:** resolved by Task 8 Step 3's exact `personaSpeakUnifiedBuild` conditional, preserved nested-build behavior, generated-output cleanup, ledger entry, and read-only fail-closed enumeration.
3. **Minor — configuration cache:** resolved by Task 2 Step 3's explicit false property plus first-party build command under that property.
4. **Minor — adapter interface import:** resolved by Task 4's fully qualified `biz.pixelperfectstudios.personaspeak.ui.editor.EditorPort` contract.
5. **Suggestion — parentless strip view:** resolved by the named `inflateActionView_returnsParentlessComposeView` test in Task 5.

The initial verdict was `APPROVE`, but implementation remains locked because these resolution edits change the plan hash. The follow-up reviewer verdict below must approve the new exact hash before Tasks 1–10 begin.

Follow-up review: `agy` / Gemini 3.5 Flash (Medium), read-only, plan SHA-256 `b130a5ae42d798086e106a0adb6b1c5c9c97156a2342f22850410b7a1a94b4f9`. Report: `/tmp/personaspeak-m2-plan-review-agy-followup.md`. The reviewer verified all five resolutions, found no regression or remaining finding, returned the integration lease, and gave `APPROVE` for that exact hash.

The final plan file differs from the approved substantive hash only by replacing the preceding `pending` sentence with this immutable follow-up receipt. The final committed file hash and the reviewer's metadata-only certification are recorded in AgentChattr and issue #47; no further plan edit is permitted before the plan commit.
