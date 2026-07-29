# PersonaSpeak M2 Completion Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete the accepted atomic ASK cutover by landing the dedicated PersonaSpeak row, deleting the two rollback applications, proving one canonical APK on the shared emulator, and merging one exact-head M2 pull request.

**Architecture:** Preserve the accepted M2 commits and corrective plan, first merging the Stage 0 governance baseline into the integration branch without force or history rewriting. Execute the four corrective row slices exactly as approved, then make ASK `:ime:app` the sole application, qualify the exact APK under a restoration-trapped device lease, and pass a three-seat exact-head acceptance panel.

**Tech Stack:** Git, GitHub Actions, Java 21, Gradle 9.2.1, Kotlin 2.3.10, Android Gradle Plugin 9.0.0, Android SDK 35, ASK 1.13-r1, Compose Material 3, Robolectric 4.16.1, Bash, ADB, UIAutomator.

---

## Authority and Frozen Inputs

- Production-route design:
  `docs/superpowers/specs/2026-07-29-personaspeak-production-route-design.md`.
- Stage 0 accepted baseline:
  `origin/main=39f9cc3669826cb7f37588aa17afa14cf2f7fe29`.
- Integration branch and worktree:
  `feat/issue-47-atomic-ask-cutover` at
  `/Users/devkiran/workspace/personaspeak-workers/issue-47-atomic-ask-cutover`.
- Accepted integration head before this plan:
  `ba2b1b2118744a1db04cacace0437ab9e04eca9a`.
- Accepted pre-corrective implementation head: `e186183`.
- Immutable corrective plan:
  `docs/superpowers/plans/2026-07-24-dedicated-personaspeak-row.md`, SHA-256
  `3a28cf0cfb31690d305bdd0baad1806fd68e8812705fe1ae4db8f99078cff7e2`.
- Historical Tasks 8-10 input:
  `docs/superpowers/plans/2026-07-22-atomic-ask-cutover.md`. Its hard-coded
  reviewer family, direct device procedure, and old PR sequencing are
  superseded by this plan and the production-route design.
- Issue #47 is the M2 authority. Issue #38 is the roadmap and remains open.

Do not copy either integration-only historical plan onto `main` before the M2
branch lands. They already belong to the accepted integration history.

## Lease and Evidence Law

Every worker lease must include this sentence verbatim:

`Preflight: ask your delegator about ambiguity, blockers, scope expansion, conflicts, or unverifiable assumptions; else proceed.`

Every delegated handback must `@mention` its delegator. Each lease names the
exact branch, starting SHA, writable paths, commands, one-commit boundary,
forbidden operations, stop conditions, and whether it has push authority.

- Implementation and device authority never coexist in one lease.
- No lease may rebase, amend, reset, force-push, wipe a device, inspect
  credentials, or modify the quarantined ADR-0006 worktree.
- Every inherited ASK file changed is ledgered in
  `android/keyboard/UPSTREAM-MODIFIED.md` in the same commit.
- Unproven runtime causes receive behavior-neutral, confirmation-only
  instrumentation first. Confirmation and correction are separate leases and
  commits.
- Device/emulator leases install a restoration trap before mutation. Every
  success, failure, signal, abort, and contradiction restores the prior IME,
  accepted APK/state on-device, accepted APK/state at the canonical output
  path, and the emulator's initial stopped/running state.
- Acceptance uses one sole final clean-HEAD rerun. Preserve complete raw logs;
  derive test, lint, artifact, and failure counts mechanically.
- A worker report is advisory. A different-family non-author reviews the exact
  receipt before the next serial lease.
- Hosted Actions quota or billing blockage is reported `unavailable`, never
  passed or failed. Run the complete exact-HEAD local equivalent and report
  hosted semantics not exercised. Any red hosted check stops.

## Serial Commit Map

| Slice | Required commit | Gate before next slice |
|---|---|---|
| 0 | `chore: synchronize M2 branch with production route` | exact Stage 0 tree and clean branch review |
| 1 | `fix(android): add dedicated extension row seam` | corrective Task 1 tests and non-author review |
| 2 | `fix(android): host PersonaSpeak in dedicated row` | corrective Task 2 tests and non-author review |
| 3 | `fix(android): fit rewrite states in dedicated row` | corrective Task 3 tests and non-author review |
| 4a | `test(android): gate dedicated row geometry` | preliminary complete host gate and review |
| 4b | `test(android): accept dedicated row precutover gate` | device restoration receipt, final corrective rerun, exact-head approval |
| 5 | `build(android): cut over atomically to ASK APK` | two clean one-APK builds and exact CI-local gate |
| 6 | `docs: record milestone two device evidence` | restoration receipt and independent evidence verdict |
| 7 | `docs: complete milestone two acceptance` | exact-head CI, panel, resolved threads, merge |

No next slice starts until its predecessor's exact commit and evidence are
accepted. The commits are normal descendants of the accepted branch and are
pushed only by non-force fast-forward.

### Task 0: Synchronize the Stage 0 Baseline Without Rewriting M2

**Files:**
- Resolve only if Git reports conflicts: `PATCHNOTES.md`
- Resolve only if Git reports conflicts: `ROADMAP.md`
- Preserve from `origin/main`:
  `docs/superpowers/specs/2026-07-29-personaspeak-production-route-design.md`

**Step 1: Capture immutable pre-merge facts**

From the integration worktree, run:

```bash
test "$(git branch --show-current)" = "feat/issue-47-atomic-ask-cutover"
test "$(git rev-parse HEAD)" = "ba2b1b2118744a1db04cacace0437ab9e04eca9a"
test -z "$(git status --porcelain | grep -v '^??')"
shasum -a 256 docs/superpowers/plans/2026-07-24-dedicated-personaspeak-row.md
git fetch origin main feat/issue-47-atomic-ask-cutover
test "$(git rev-parse origin/main)" = "39f9cc3669826cb7f37588aa17afa14cf2f7fe29"
test "$(git rev-parse origin/feat/issue-47-atomic-ask-cutover)" = "ba2b1b2118744a1db04cacace0437ab9e04eca9a"
git merge-base --is-ancestor ed1b723088a69998e25d3703eb00e052b49a524f HEAD
git rev-list --left-right --count origin/main...HEAD
```

Expected: tracked-clean branch, exact corrective plan hash, merge base
`ed1b723`, and left/right count `1 12`. Any remote drift or different count
stops before mutation.

**Step 2: Create the reviewed merge, but do not commit automatically**

```bash
git merge --no-ff --no-commit origin/main
git status --short
```

Expected: only the Stage 0 governance changes and any conflicts in the named
governance files. A product-code conflict or any path outside the three named
authorities stops and aborts the merge with `git merge --abort`.

**Step 3: Resolve governance conflicts by accepted-tree equality**

For `PATCHNOTES.md`, retain both the accepted Stage 0 entry and the accepted
dedicated-row entry, newest first. `ROADMAP.md` retains both independent
accepted deltas: Stage 0's completed `:personaspeak-ui` checkbox and M8 line,
plus the integration branch's ADR-0007/dedicated-row wording. The production-
route design must be byte-identical to `origin/main`:

```bash
git checkout origin/main -- \
  docs/superpowers/specs/2026-07-29-personaspeak-production-route-design.md
git add PATCHNOTES.md ROADMAP.md \
  docs/superpowers/specs/2026-07-29-personaspeak-production-route-design.md
git diff --cached --check
git diff --cached --name-status
git diff --exit-code origin/main -- \
  docs/superpowers/specs/2026-07-29-personaspeak-production-route-design.md
git diff origin/main -- ROADMAP.md
git diff ba2b1b2118744a1db04cacace0437ab9e04eca9a -- ROADMAP.md
```

Expected: no unresolved markers and no product-code changes introduced by the
merge. Relative to `origin/main`, ROADMAP differs only by the accepted
ADR-0007/dedicated-row wording. Relative to `ba2b1b2`, it differs only by the
completed `:personaspeak-ui` checkbox and M8 line. The design is exactly Stage
0. If either governance file was not a conflict, do not rewrite it merely for
symmetry.

**Step 4: Commit and verify history preservation**

```bash
git commit -m "chore: synchronize M2 branch with production route"
git rev-parse HEAD^1
git rev-parse HEAD^2
git merge-base --is-ancestor ba2b1b2118744a1db04cacace0437ab9e04eca9a HEAD
git merge-base --is-ancestor 39f9cc3669826cb7f37588aa17afa14cf2f7fe29 HEAD
git diff --check origin/main...HEAD
test -z "$(git status --porcelain | grep -v '^??')"
```

Expected: first parent `ba2b1b2`, second parent `39f9cc3`; both histories are
ancestors; accepted M2 commit identities remain unchanged.

**Step 5: Obtain exact-merge review before push**

A different-family non-author verifies the two parents, exact Stage 0 file
tree, unchanged corrective-plan hash, no product-code conflict resolution,
preserved known untracked paths, and tracked-clean state. Only after APPROVE:

```bash
git push origin feat/issue-47-atomic-ask-cutover
test "$(git ls-remote origin refs/heads/feat/issue-47-atomic-ask-cutover | cut -f1)" = "$(git rev-parse HEAD)"
```

Expected: ordinary fast-forward push and exact remote equality.

### Tasks 1-4: Execute the Immutable Dedicated-Row Corrective Plan

**Files:** The exact file map in
`docs/superpowers/plans/2026-07-24-dedicated-personaspeak-row.md:64-95`.

The corrective plan remains the executable TDD specification. Do not copy its
874 lines here, rename its interfaces, combine its leases, or alter its tests.
Before each task, verify:

```bash
shasum -a 256 docs/superpowers/plans/2026-07-24-dedicated-personaspeak-row.md
```

Expected:
`3a28cf0cfb31690d305bdd0baad1806fd68e8812705fe1ae4db8f99078cff7e2`.

**Step 1: Execute corrective Task 1 exactly**

Follow lines 99-309: write failing `KeyboardViewContainerViewTest` coverage,
observe the named failures, implement the generic `ExtensionRowProvider`, run
the focused and regression tests, ledger the inherited file in the same
commit, and commit:

```bash
git commit -m "fix(android): add dedicated extension row seam"
```

Stop for exact-commit non-author review.

**Step 2: Execute corrective Task 2 exactly**

Follow lines 310-507: write failing host/composition tests, implement
`PersonaSpeakRowProvider`, remove strip registration, run the focused and
lifecycle suites, and commit:

```bash
git commit -m "fix(android): host PersonaSpeak in dedicated row"
```

Stop for exact-commit non-author review.

**Step 3: Execute corrective Task 3 exactly**

Follow lines 508-709: test the 48dp interactive minimum and frozen
`min(320dp, 40%)` Review-body cap, implement only the required policy and
layouts, run Compose/Robolectric and regression tests, and commit:

```bash
git commit -m "fix(android): fit rewrite states in dedicated row"
```

Stop for exact-commit non-author review.

**Step 4: Execute corrective Task 4 exactly, with one binding amendment**

Follow lines 713-843. Add the focused suites to
`android/scripts/verify-milestone-2-precutover.sh`, commit the gate, run the
preliminary complete gate, receive implementation review, then grant a
separate device-only lease. The device lease must filter verbose ASK
content-bearing log lines before evidence retention in addition to the plan's
existing priority/tag and synthetic-content rules.

Required commits:

```bash
git commit -m "test(android): gate dedicated row geometry"
git commit -m "test(android): accept dedicated row precutover gate"
```

The second commit occurs only after verified restoration. From its clean HEAD,
run the sole final corrective acceptance:

```bash
bash android/scripts/verify-milestone-2-precutover.sh
```

Expected: exit 0, `PASS: milestone 2 pre-cutover gate`, mechanical XML/lint
counts, canonical APK hash equal to the device-qualified hash, and an APPROVE
verdict from a different-family non-author. Task 5 remains blocked otherwise.

### Task 5: Make ASK the Sole Application and Gate One APK

**Files:**
- Create: `android/scripts/verify-single-apk.sh`
- Create: `android/scripts/tests/verify-single-apk-test.sh`
- Create: `android/scripts/verify-milestone-2.sh`
- Create: `android/scripts/tests/verify-milestone-2-test.sh`
- Modify: `android/build.gradle.kts`
- Modify: `android/settings.gradle.kts`
- Modify: `.github/workflows/ci.yml`
- Modify: `android/keyboard/gradle/apk_module.gradle`
- Modify: `android/keyboard/UPSTREAM-MODIFIED.md`
- Delete: `android/app/`
- Delete: `android/keyboard-stub/`

**Step 1: Write the failing exact-one-APK fixture test**

Create shell fixtures that prove:

1. zero APKs fails;
2. two APKs fail and print both paths;
3. an `android/outputs/` convenience duplicate fails;
4. one noncanonical APK fails;
5. exactly
   `keyboard/ime/app/build/outputs/apk/debug/app-debug.apk` passes;
6. zero or two `com.android.application` projects fail;
7. only `keyboard/ime/app/build.gradle` applying the plugin passes;
8. verifier usage/tool failures return 2 rather than being mistaken for a
   clean scan.

Run:

```bash
bash android/scripts/tests/verify-single-apk-test.sh
```

Expected: FAIL because `verify-single-apk.sh` does not exist.

**Step 2: Implement fail-closed artifact and topology enumeration**

Implement `verify-single-apk.sh <android-root>` with `set -euo pipefail`,
sorted exact path output, count checks, canonical-path equality, and a
failure-aware scan of included build files. It is read-only: stale outputs
fail and are never removed by the verifier.

Run:

```bash
bash android/scripts/tests/verify-single-apk-test.sh
```

Expected: every fixture passes and the script prints its exact case count.

**Step 3: Test and disable root-build convenience copies**

Add a fixture/assertion proving the root build sets
`personaSpeakUnifiedBuild=true`, while a nested upstream build without the
flag still registers ASK copy tasks. Then set the extra in
`android/build.gradle.kts`. In `apk_module.gradle`, omit registration and
finalization of `copy<Variant>Apk` and `copy<Variant>Aab` only when that exact
flag is true. Add the replayable ledger entry in the same commit.

Run the focused test before and after implementation. Expected: red before;
green after; no `android/outputs/` APK or AAB after the root build.

**Step 4: Delete both rollback modules atomically**

Delete `android/app/` and `android/keyboard-stub/`, then remove their two
`include` entries from `android/settings.gradle.kts`. Do not delete any ASK or
first-party library module. Verify:

```bash
! test -e android/app
! test -e android/keyboard-stub
! rg -n 'include\(":(app|keyboard-stub)"\)' android/settings.gradle.kts
./android/gradlew -p android projects --console=plain --no-daemon
```

Expected: the three first-party libraries plus the exact 28 ASK logical paths;
`:ime:app` is the sole application project.

**Step 5: Write a failing aggregate post-cutover gate test**

The fixture test must require `verify-milestone-2.sh` to invoke, in order:

- clean tracked state and JDK 21 checks;
- ASK closure, dictionary-license, upstream-ledger, and rejected-topology
  verifier tests and production verifiers;
- core/UI/ASK unit tests;
- `:ime:app:lintDebug` and clean `:ime:app:assembleDebug`;
- core Android-import and UI ASK-import scans with explicit exit handling;
- upstream-to-first-party boundary scan with only the approved leaf seam
  allowlist;
- exact-one-APK verification;
- APK Analyzer assertions for package
  `biz.pixelperfectstudios.personaspeak`, IME service, settings activity,
  minSdk 26, targetSdk 35, and non-debug release irrelevance at M2.

Run:

```bash
bash android/scripts/tests/verify-milestone-2-test.sh
```

Expected: FAIL before the aggregate script is complete.

**Step 6: Implement the aggregate gate and root-only CI**

Implement `verify-milestone-2.sh` without `continue-on-error` and without a
bare negated `rg` that could turn tool failure into success. Replace CI's
temporary `:app`/`:keyboard-stub` graph with one call to the aggregate gate.
Upload only:

`android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk`

plus non-APK reports. Pin artifact retention explicitly. CI must also execute
the verifier fixture suites before trusting the production scripts.

Run:

```bash
bash android/scripts/tests/verify-milestone-2-test.sh
git diff --check
```

Expected: fixture suite PASS and no whitespace errors.

**Step 7: Run two clean builds and the complete local gate**

```bash
bash android/scripts/verify-milestone-2.sh
shasum -a 256 android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk
./android/gradlew -p android clean :ime:app:assembleDebug --console=plain --no-daemon --rerun-tasks
bash android/scripts/verify-single-apk.sh android
shasum -a 256 android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk
```

Expected: both clean builds exit 0 and each yields exactly one canonical APK.
Debug APK byte identity is not an M2 gate; record both hashes and investigate
unexpected drift without silently asserting reproducibility.

**Step 8: Commit one reversible topology cutover**

```bash
git add -A android/app android/keyboard-stub android/build.gradle.kts \
  android/settings.gradle.kts android/scripts .github/workflows/ci.yml \
  android/keyboard/gradle/apk_module.gradle \
  android/keyboard/UPSTREAM-MODIFIED.md
git diff --cached --check
git commit -m "build(android): cut over atomically to ASK APK"
```

Stop for a different-family non-author review of the exact commit, complete
raw logs, mechanical counts, application-plugin count, canonical APK path,
CI topology, license/ledger/closure results, and deleted-module boundary.

### Task 6: Qualify the Exact APK on `CityZen_Dev`

**Files:**
- Create: `docs/evidence/milestone-2/README.md`
- Create: `docs/evidence/milestone-2/commands.txt`
- Create: `docs/evidence/milestone-2/package.txt`
- Create: `docs/evidence/milestone-2/ime-list.txt`
- Create: `docs/evidence/milestone-2/logcat.txt`
- Create approved screenshots only after private sensitive-data scan
- Modify: `PATCHNOTES.md`

**Step 1: Run a preliminary host gate and record the candidate identity**

From the tracked-clean Task 5 commit:

```bash
bash android/scripts/verify-milestone-2.sh
git rev-parse HEAD
shasum -a 256 android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk
```

Expected: complete gate PASS and exactly one canonical APK. Preserve the full
raw log. This is qualification, not final acceptance.

**Step 2: Grant an exclusive device-only lease**

The immutable lease names `CityZen_Dev`, its actual serial, exact source SHA,
APK path/hash, evidence paths, synthetic strings, no source edits, no
credentials, no wipe, and no push. Before mutation it must:

- prove exclusive availability and no concurrent claim;
- record whether the emulator was initially stopped or running;
- record build fingerprint/API and disk/package state;
- capture prior default/enabled IME;
- capture the prior accepted PersonaSpeak APK hash and state on-device;
- capture the accepted APK/state at the canonical output path;
- install a trap covering normal exit, error, signal, abort, and contradiction.

If the emulator was stopped, the trap stops it again after restoration.

**Step 3: Install and prove package/IME identity**

Install only the exact canonical APK. Record `pm path`, package version,
signer where available, `ime list -a`, enable/select result, and
`dumpsys input_method`. Expected: one package
`biz.pixelperfectstudios.personaspeak` and current component
`com.menny.android.anysoftkeyboard.SoftKeyboard`.

**Step 4: Prove untouched ASK and every PersonaSpeak state**

With synthetic text in an external host, mechanically assert:

1. real ASK keys type;
2. ASK candidate suggestions and all key rows remain visible and usable;
3. PersonaSpeak Idle, Loading, Review, Message, stale, write-rejected, and
   write-unconfirmed states occupy the separate row without overlap;
4. fake-provider capture shows Review before mutation;
5. Dismiss mutates zero times and Apply mutates exactly once;
6. stale/rejected/unconfirmed paths do not retry or mutate;
7. settings launch resolves inside the single package;
8. UI hierarchy bounds support the geometry verdict rather than screenshots
   supplying their own verdict.

**Step 5: Capture privacy-safe logs and artifacts**

Use synthetic content, priority/tag filtering, and explicit filters excluding
verbose ASK content-bearing lines. Retain no typed, editor, prompt, provider,
candidate, result, or replacement content. Before promotion, scan every text,
XML, image, and recording privately for content and credentials. Promote only
approved artifacts; record rejected artifact count without copying them into
the repository.

Expected: no package fatal exception, ANR, process death, or privacy hit. An
uncertain runtime cause stops; it does not authorize a fix.

**Step 6: Fire the restoration trap and verify both copies**

Restore and verify the prior IME, prior accepted APK/state on-device, accepted
APK/state at the canonical output path, and initial emulator run state. Record
before/after hashes and commands. A restoration mismatch blocks the receipt
commit even when the product journey passed.

**Step 7: Write the machine-indexed evidence receipt**

`README.md` maps every issue #47 M2 acceptance item to command output,
UI-hierarchy assertion, approved artifact, exact commit, APK SHA-256, device
identity, timestamps, restoration result, and deviations. `commands.txt`
contains the complete command transcript and statuses. Add a real
VOICE-compliant `PATCHNOTES.md` entry for the completed M2 cutover.

**Step 8: Commit the receipt after independent restoration review**

```bash
git add docs/evidence/milestone-2 PATCHNOTES.md
git diff --cached --check
git commit -m "docs: record milestone two device evidence"
```

Stop for different-family non-author review of evidence completeness,
privacy scan, mechanically derived counts, exact APK identity, and restoration.

### Task 7: Run Final Acceptance, Qualify the PR, and Merge

**Files:**
- Modify only for verified findings: affected code/test/evidence files
- Update after any head change: `docs/evidence/milestone-2/README.md`
- Update after final acceptance: `ROADMAP.md`
- Update after final acceptance: `PATCHNOTES.md`
- Update completion markers only:
  `docs/superpowers/plans/2026-07-22-atomic-ask-cutover.md`
- Never modify the hash-pinned corrective plan

**Step 1: Run the preliminary clean-HEAD local gate**

From a tracked-clean receipt commit:

```bash
test -z "$(git status --porcelain | grep -v '^??')"
bash android/scripts/verify-milestone-2.sh
shasum -a 256 android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk
git rev-parse HEAD
```

Preserve the complete raw log and compare its APK hash with the device receipt.
This proves the branch is ready to become a draft PR; it is not the sole final
acceptance run because PR qualification and the completion commit remain.

**Step 2: Push normally and open one draft M2 PR**

```bash
git push origin feat/issue-47-atomic-ask-cutover
```

Open a draft PR against `main`. Its body must:

- say `Closes #47`;
- reference roadmap issue #38 without closing it;
- identify the Stage 0 baseline and exact PR head;
- list the dedicated-row, topology, APK, device, privacy, restoration, ledger,
  license, closure, and rollback evidence;
- identify the canonical APK path and hash;
- include the real patch-note entry;
- disclose any hosted CI semantics that remain unverified.

**Step 3: Use the first converted PR run as CI qualification**

The PR creates the first applicable `pull_request` run, including the PR-only
patchnote gate. Wait for all checks on the exact head.

Expected: hosted CI green. If and only if GitHub reports quota/billing
unavailability, record `unavailable`, validate workflow syntax, rerun every
underlying command locally on exact clean HEAD, and enumerate unverified hosted
semantics. `act` is supplemental unless event/toolchain parity and secret
safety are proven. Any red check stops.

**Step 4: Resolve review findings without combining diagnosis and repair**

For each finding, use `receiving-code-review`: reproduce it, write the failing
test, observe red, implement the smallest fix, observe green, commit once, and
push normally. Runtime uncertainty receives a separate behavior-neutral
confirmation lease first. After any head change:

- invalidate prior CI, APK hash, device receipt, and exact-head verdict as
  applicable;
- rerun every affected host/device gate under the same restoration law;
- update evidence to the new exact head;
- rerun the sole final clean-HEAD acceptance;
- obtain new exact-head green CI.

**Step 5: Record M2 completion without closing the roadmap**

After all findings are resolved, update `ROADMAP.md` and the atomic-plan
completion markers truthfully, preserve issue #38 as OPEN, and make any
required final patch-note adjustment. Commit:

```bash
git add ROADMAP.md PATCHNOTES.md \
  docs/superpowers/plans/2026-07-22-atomic-ask-cutover.md \
  docs/evidence/milestone-2
git diff --cached --check
git commit -m "docs: complete milestone two acceptance"
git push origin feat/issue-47-atomic-ask-cutover
```

No repository change is permitted after this commit without invalidating the
remaining gates and returning to Step 3.

**Step 6: Require exact-head green CI**

Wait for every required PR check on the completion commit. Require exact
workflow-head equality and green statuses. The only permitted non-green
classification is demonstrable quota/billing `unavailable`, handled by the
approved complete local fallback and explicit hosted-semantics disclosure.

**Step 7: Run the sole final clean-HEAD acceptance**

From the tracked-clean completion commit, run exactly once:

```bash
test -z "$(git status --porcelain | grep -v '^??')"
bash android/scripts/verify-milestone-2.sh
shasum -a 256 android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk
git rev-parse HEAD
```

Preserve the complete raw log. Mechanically derive unit-test
tests/failures/errors/skips from XML, lint errors/warnings/information from
XML, script case counts from fixture output, project/application counts from
the graph, and APK count/path/hash from the verifier. Any contradiction or
hash mismatch with the device receipt stops. Do not rerun selectively and call
the collection final; one complete clean-HEAD invocation is the evidence.

**Step 8: Complete the three-seat panel**

The seats are distinct and cannot be doubled:

1. Sigrid: overseer acceptance and exact-head countersign;
2. Cassie: architecture concurrence against the production-route design;
3. a separately leased different-family non-author: exact receipt/diff/log
   review, eligibility recalculated from actual authorship at lease time.

The receipt reviewer verifies head/remote equality, all raw logs and mechanical
counts, APK/device/restoration identity, CI, privacy, closure, license, ledger,
one-app topology, and unresolved review-thread count. The reviewer must not be
hard-coded to `agy`; actual authorship controls eligibility.

**Step 9: Convert ready and merge only on exact consensus**

Require all of the following on one exact PR SHA:

- remote/local equality and mergeable/clean state;
- all required checks green, or the narrowly approved hosted-unavailable local
  fallback with every unverified semantic disclosed;
- zero unresolved review threads;
- final clean-HEAD raw log and mechanical counts;
- one canonical APK matching the accepted device receipt;
- verified restoration and privacy-safe evidence;
- Sigrid acceptance, Cassie concurrence, and separate receipt-review APPROVE;
- issue #47 linked for closure and issue #38 still open.

Then convert the draft to ready. Merge only under the owner's standing
merge-on-consensus authority. After merge, verify the merged tree equals the
accepted PR tree, issue #47 is closed, issue #38 remains open, and canonical
`main` fast-forwards to the exact remote merge result while preserving its
known untracked Stitch/reference artifacts.

## Final Stop Conditions

Stop immediately on branch or remote drift, dirty tracked state, unexpected
merge conflict, corrective-plan hash mismatch, unreviewed scope expansion,
upstream ledger mismatch, ambiguous dictionary license, closure/topology
count mismatch, more or fewer than one canonical APK, privacy hit, content log,
device/resource conflict, restoration mismatch, unproven runtime cause, red CI,
unresolved review thread, ineligible/doubled panel seat, or issue #38 closure.

M3 remains blocked until the M2 merge, issue #47 closure, canonical-main
verification, coordinator handoff update, and owner-visible acceptance receipt
are complete.
