# Dedicated PersonaSpeak Row Corrective Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the overlapping ASK strip action with a separately measured
PersonaSpeak row above ASK's untouched candidate row and key rows, then re-run
the complete Task 7 gate.

**Architecture:** Add one generic extension-row provider seam to ASK's
`KeyboardViewContainerView`; keep existing candidate-row `StripActionProvider`
behavior unchanged. Migrate the first-party Compose host to that seam, give
each rewrite state accessible row/card geometry, and prove non-overlap with
host, Compose, build, and restoration-trapped device gates.

**Tech Stack:** Java 21, Kotlin 2.3.10, Android custom `ViewGroup`, AndroidX
Compose Material 3, Lifecycle/SavedState/ViewModel, Robolectric 4.16.1,
Compose UI Test, JUnit 4, Gradle 9.2.1, Bash, ADB.

**Authority:** [ADR-0007](../../adr/0007-dedicated-personaspeak-row.md) ·
[approved design](../specs/2026-07-24-dedicated-personaspeak-row-design.md) ·
[atomic-cutover plan](2026-07-22-atomic-ask-cutover.md)

## Global Constraints

- Base is the exact reviewed plan commit; every implementation lease records
  that SHA, the integration worktree, exact writable paths, tests, one commit
  boundary, no push authority, and handback.
- Preserve `:ime:app -> :personaspeak-ui -> core-*`; no Android or ASK import
  enters a core module, and no ASK import enters `:personaspeak-ui`.
- ASK candidate content and every key row remain visible, usable, and
  uncovered in Idle, Loading, Review, Message, and outcome states.
- PersonaSpeak stays inside ASK's input-view hierarchy. No overlay permission,
  dialog window, popup window, translation, elevation, or z-order borrowing.
- The resting/loading row has a 48dp minimum interactive height. Candidate and
  key geometry is sequential below it, never intersecting it.
- Review body height is capped at
  `min(320dp, 40% of the pre-expansion IME container height)`; the sampled cap
  is frozen for that Review instance to prevent a measurement feedback loop.
- Existing `EditorPort`, rewrite coordinator, review-before-replace,
  stale/rejected/unconfirmed, no-retry, lifecycle-owner, and request-scoped
  privacy semantics do not change.
- Never persist or log editor, draft, prompt, provider, candidate, result, or
  replacement text. Synthetic test strings stay inside test process memory.
- Every modified file from the pinned ASK snapshot receives one matching
  `android/keyboard/UPSTREAM-MODIFIED.md` entry in the same commit.
- Do not weaken or delete existing assertions. Contradictory evidence stops
  the task.
- No emulator lease is combined with implementation. The device lease starts
  only after the implementation commits pass host/build review.
- Every worker lease contains this exact sentence:
  `Preflight: ask your delegator about ambiguity, blockers, scope expansion, conflicts, or unverifiable assumptions; else proceed.`
- A device lease installs a restoration trap before mutation and verifies the
  prior IME plus accepted APK/state both on-device and at the canonical output
  path on every exit.
- Acceptance comes only from a final clean-HEAD rerun with complete raw logs
  and mechanically derived counts. A different-model-family non-author gives
  the final verdict.
- Tasks 8–10 remain blocked until this plan's Task 4 is accepted.
- After this plan's handoff, `claude-alt` is the successor orchestrator for
  worker delegation, leases, evidence acceptance, stop decisions, and owner
  checkpoints. Workers report to `@claude-alt`; Codex availability is not an
  execution dependency.

## File and Responsibility Map

- `android/keyboard/ime/app/src/main/java/com/anysoftkeyboard/keyboards/views/KeyboardViewContainerView.java`
  — generic one-row extension provider, measurement, layout, and lifecycle.
- `android/keyboard/ime/app/src/test/java/com/anysoftkeyboard/keyboards/views/KeyboardViewContainerViewTest.java`
  — exact extension/candidate/action/keyboard rectangles and lifecycle tests.
- `android/keyboard/UPSTREAM-MODIFIED.md` — one replayable ledger entry for
  the container seam.
- `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/host/PersonaSpeakRowProvider.kt`
  — parentless full-width Compose row provider.
- `android/keyboard/ime/app/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ime/host/PersonaSpeakRowProviderTest.kt`
  — parent/layout/lifecycle contract for that provider.
- `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/PersonaSpeakComposition.kt`
  — add/remove the dedicated row and supply the pre-expansion height sampler.
- `android/keyboard/ime/app/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ime/PersonaSpeakCompositionTest.kt`
  — row registration, no strip registration, and repeated-input idempotence.
- `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/ResultHeightPolicy.kt`
  — pure frozen Review-body cap calculation.
- `android/personaspeak-ui/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/ResultHeightPolicyTest.kt`
  — 40%, 320dp cap, density, and invalid-sample tests.
- `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/RewritePanel.kt`
  — accessible Idle/Loading/Review/Message dedicated-row layouts.
- `android/personaspeak-ui/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/RewritePanelTest.kt`
  — semantics, 48dp bounds, state content, and frozen Review cap.
- `android/personaspeak-ui/build.gradle.kts` and
  `android/gradle/libs.versions.toml` — Compose/Robolectric unit-test support.
- `android/scripts/verify-milestone-2-precutover.sh` — include the new focused
  geometry/host tests in the aggregate Task 7 gate.
- `docs/evidence/milestone-2/precutover-commands.txt` — final clean-head build
  and device receipt with no content-bearing editor/provider text.
- `docs/superpowers/plans/2026-07-22-atomic-ask-cutover.md` — mark the
  dedicated-row corrective and revised Task 7 result; do not advance Task 8.

---

### Task 1: Add the Generic ASK Extension-Row Seam

**Files:**
- Modify: `android/keyboard/ime/app/src/test/java/com/anysoftkeyboard/keyboards/views/KeyboardViewContainerViewTest.java`
- Modify: `android/keyboard/ime/app/src/main/java/com/anysoftkeyboard/keyboards/views/KeyboardViewContainerView.java`
- Modify: `android/keyboard/UPSTREAM-MODIFIED.md`

**Interfaces:**
- Consumes: existing `CandidateView`, standard keyboard child, and
  `StripActionProvider`.
- Produces:
  `ExtensionRowProvider.inflateExtensionRow(ViewGroup): View`,
  `ExtensionRowProvider.onRemoved()`,
  `void addExtensionRow(ExtensionRowProvider)`, and
  `void removeExtensionRow(ExtensionRowProvider)`.

- [ ] **Step 1: Write failing container tests**

Add one fixed-height provider helper and tests with these exact assertions:

```java
private static final class FixedExtensionRowProvider
    implements KeyboardViewContainerView.ExtensionRowProvider {
  final View view;
  int removed;

  FixedExtensionRowProvider(KeyboardViewContainerView parent, int height) {
    view = new View(parent.getContext());
    view.setLayoutParams(
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
  }

  @Override public View inflateExtensionRow(ViewGroup parent) { return view; }
  @Override public void onRemoved() { removed++; }
}

@Test
public void testExtensionRowMeasuresAboveCandidateAndKeyboard() {
  var provider = new FixedExtensionRowProvider(mUnderTest, 60);
  mUnderTest.addExtensionRow(provider);
  mUnderTest.measure(
      View.MeasureSpec.makeMeasureSpec(1024, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST));
  mUnderTest.layout(0, 0, 1024, mUnderTest.getMeasuredHeight());

  Assert.assertEquals(0, provider.view.getTop());
  Assert.assertEquals(60, provider.view.getBottom());
  Assert.assertEquals(60, mUnderTest.getCandidateView().getTop());
  Assert.assertEquals(
      mUnderTest.getCandidateView().getBottom(),
      ((View) mUnderTest.getStandardKeyboardView()).getTop());
  Assert.assertEquals(1024, provider.view.getMeasuredWidth());
}
```

Also add tests named
`testExtensionRowIsIndependentOfCandidateVisibility`,
`testStripActionLayoutsOnCandidateBelowExtensionRow`,
`testExtensionRowRemeasureMovesCandidateWithoutOverlap`,
`testDoubleAddExtensionRowIsIdempotent`,
`testRemoveExtensionRowCallsProviderOnce`, and
`testExtensionRowRejectsInflatedViewWithParent`. Their load-bearing assertions
are:

```java
mUnderTest.setActionsStripVisibility(false);
Assert.assertSame(mUnderTest, provider.view.getParent());
Assert.assertEquals(60, ((View) mUnderTest.getStandardKeyboardView()).getTop());

Assert.assertEquals(mUnderTest.getCandidateView().getTop(), stripAction.getTop());
Assert.assertEquals(mUnderTest.getCandidateView().getBottom(), stripAction.getBottom());

provider.view.getLayoutParams().height = 96;
mUnderTest.requestLayout();
mUnderTest.measure(widthSpec, heightSpec);
mUnderTest.layout(0, 0, 1024, mUnderTest.getMeasuredHeight());
Assert.assertEquals(96, mUnderTest.getCandidateView().getTop());

mUnderTest.addExtensionRow(provider);
mUnderTest.addExtensionRow(provider);
Assert.assertEquals(3, mUnderTest.getChildCount());

mUnderTest.removeExtensionRow(provider);
mUnderTest.removeExtensionRow(provider);
Assert.assertEquals(1, provider.removed);
Assert.assertNull(provider.view.getParent());
```

For the parented-view case, put the provider view in a `FrameLayout` first and
annotate the test `@Test(expected = IllegalStateException.class)`.

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./android/gradlew -p android :ime:app:testDebugUnitTest \
  --tests 'com.anysoftkeyboard.keyboards.views.KeyboardViewContainerViewTest' \
  --rerun-tasks --no-daemon
```

Expected: compilation fails because `ExtensionRowProvider`,
`addExtensionRow`, and `removeExtensionRow` do not exist.

- [ ] **Step 3: Implement the minimal generic seam**

Add one provider/view pair to `KeyboardViewContainerView`:

```java
import androidx.annotation.Nullable;

@Nullable private ExtensionRowProvider mExtensionRowProvider;
@Nullable private View mExtensionRowView;

public void addExtensionRow(@NonNull ExtensionRowProvider provider) {
  if (mExtensionRowProvider == provider) return;
  if (mExtensionRowProvider != null) {
    throw new IllegalStateException("Only one extension row is supported.");
  }
  View view = provider.inflateExtensionRow(this);
  if (view.getParent() != null) {
    throw new IllegalStateException("ExtensionRowProvider inflated a view with a parent!");
  }
  mExtensionRowProvider = provider;
  mExtensionRowView = view;
  addView(view, 0);
  requestLayout();
}

public void removeExtensionRow(@NonNull ExtensionRowProvider provider) {
  if (mExtensionRowProvider != provider) return;
  removeView(mExtensionRowView);
  mExtensionRowView = null;
  mExtensionRowProvider = null;
  provider.onRemoved();
  requestLayout();
}

public interface ExtensionRowProvider {
  @NonNull View inflateExtensionRow(@NonNull ViewGroup parent);
  void onRemoved();
}
```

Keep the existing measurement rule for normal children so the extension
height enters `totalHeight`. Change layout to two passes: first lay out every
non-strip child sequentially; then right-align strip actions at
`mCandidateView.getTop()`. Do not special-case PersonaSpeak and do not change
`setActionsStripVisibility`.

```java
int currentTop = t + getPaddingTop();
for (int i = 0; i < count; i++) {
  View child = getChildAt(i);
  if (child.getVisibility() == View.GONE || child.getTag(PROVIDER_TAG_ID) != null) continue;
  child.layout(left, currentTop, right, currentTop + child.getMeasuredHeight());
  currentTop += child.getMeasuredHeight();
}

int actionRight = r - getPaddingRight();
int actionsTop = mCandidateView.getTop();
for (int i = 0; i < count; i++) {
  View child = getChildAt(i);
  if (child.getVisibility() == View.GONE || child.getTag(PROVIDER_TAG_ID) == null) continue;
  child.layout(
      actionRight - child.getMeasuredWidth(),
      actionsTop,
      actionRight,
      actionsTop + child.getMeasuredHeight());
  actionRight -= child.getMeasuredWidth();
}
```

- [ ] **Step 4: Ledger the upstream edit**

Add exactly one `UPSTREAM-MODIFIED.md` bullet for
`ime/app/src/main/java/com/anysoftkeyboard/keyboards/views/KeyboardViewContainerView.java`
stating: generic single extension-row provider; sequential measurement above
candidate/keyboard; strip actions remain on the candidate row; replay by
reapplying the provider fields/API and two-pass layout.

- [ ] **Step 5: Run focused and ledger tests and verify GREEN**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./android/gradlew -p android :ime:app:testDebugUnitTest \
  --tests 'com.anysoftkeyboard.keyboards.views.KeyboardViewContainerViewTest' \
  --rerun-tasks --no-daemon
bash android/scripts/verify-upstream-ledger.sh android
```

Expected: both commands exit 0; all focused tests pass; ledger verifier prints
`upstream ledger verified: pristine delta equals ledger exactly`.

- [ ] **Step 6: Commit the independently testable seam**

```bash
git add \
  android/keyboard/ime/app/src/main/java/com/anysoftkeyboard/keyboards/views/KeyboardViewContainerView.java \
  android/keyboard/ime/app/src/test/java/com/anysoftkeyboard/keyboards/views/KeyboardViewContainerViewTest.java \
  android/keyboard/UPSTREAM-MODIFIED.md
git commit -m "feat(android): add ASK extension row seam"
```

Stop for non-author review of this commit before Task 2.

---

### Task 2: Migrate the PersonaSpeak Host to the Dedicated Row

**Files:**
- Delete: `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/host/PersonaSpeakStripActionProvider.kt`
- Delete: `android/keyboard/ime/app/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ime/host/PersonaSpeakStripActionProviderTest.kt`
- Create: `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/host/PersonaSpeakRowProvider.kt`
- Create: `android/keyboard/ime/app/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ime/host/PersonaSpeakRowProviderTest.kt`
- Modify: `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/PersonaSpeakComposition.kt`
- Create: `android/keyboard/ime/app/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ime/PersonaSpeakCompositionTest.kt`

**Interfaces:**
- Consumes: Task 1's `ExtensionRowProvider`, `addExtensionRow`, and
  `removeExtensionRow`.
- Produces: `PersonaSpeakRowProvider.lastComposeView: ComposeView?` and one
  full-width, wrap-content Compose row per active input session.

- [ ] **Step 1: Write failing row-provider tests**

Create `PersonaSpeakRowProviderTest` with these exact geometry assertions:

```kotlin
@Test
fun `inflateExtensionRow returns parentless match-width wrap-height ComposeView`() {
    val provider = PersonaSpeakRowProvider(ImeViewTreeOwners())
    val parent = FrameLayout(context)

    val view = provider.inflateExtensionRow(parent)

    assertNull(view.parent)
    assertTrue(view is ComposeView)
    assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, view.layoutParams.width)
    assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, view.layoutParams.height)
}
```

Port the existing idempotent `onRemoved` and `destroy` tests. Add
`onRemoved clears lastComposeView` and `destroy clears lastComposeView`.

```kotlin
@Test
fun `onRemoved clears view and remains idempotent`() {
    val owners = ImeViewTreeOwners()
    val provider = PersonaSpeakRowProvider(owners)
    provider.inflateExtensionRow(FrameLayout(context))
    owners.startInput()

    provider.onRemoved()
    provider.onRemoved()

    assertNull(provider.lastComposeView)
}

@Test
fun `destroy clears view and remains idempotent`() {
    val provider = PersonaSpeakRowProvider(ImeViewTreeOwners())
    provider.inflateExtensionRow(FrameLayout(context))

    provider.destroy()
    provider.destroy()

    assertNull(provider.lastComposeView)
}
```

- [ ] **Step 2: Write failing composition registration tests**

Create a fixture `RecordingKeyboardViewContainerView` that overrides
`addExtensionRow`, `removeExtensionRow`, and `addStripAction`. Assert one
extension-row add, zero strip-action adds, one removal, and no duplicate add
across repeated `onStartInputView()` calls.

```kotlin
private class RecordingKeyboardViewContainerView(context: Context) :
    KeyboardViewContainerView(context) {
    var extensionAdds = 0
    var extensionRemoves = 0
    var stripAdds = 0

    override fun addExtensionRow(provider: ExtensionRowProvider) {
        extensionAdds++
    }

    override fun removeExtensionRow(provider: ExtensionRowProvider) {
        extensionRemoves++
    }

    override fun addStripAction(provider: StripActionProvider, highPriority: Boolean) {
        stripAdds++
    }
}

@Test
fun `input view uses one extension row and never a strip action`() {
    val container = RecordingKeyboardViewContainerView(context)
    val composition = PersonaSpeakComposition(context, { null }, { EditorInfo() })
    composition.onCreateInputView(container, null)
    composition.onStartInput(EditorInfo(), false)

    composition.onStartInputView()
    composition.onStartInputView()
    composition.onFinishInput()

    assertEquals(1, container.extensionAdds)
    assertEquals(1, container.extensionRemoves)
    assertEquals(0, container.stripAdds)
}
```

- [ ] **Step 3: Run the host tests and verify RED**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./android/gradlew -p android :ime:app:testDebugUnitTest \
  --tests '*PersonaSpeakRowProviderTest' \
  --tests '*PersonaSpeakCompositionTest' \
  --tests '*ImeViewTreeOwnersTest' \
  --rerun-tasks --no-daemon
```

Expected: compilation fails because `PersonaSpeakRowProvider` and the
extension-row calls do not exist.

- [ ] **Step 4: Implement the row provider**

Implement `PersonaSpeakRowProvider` as
`KeyboardViewContainerView.ExtensionRowProvider`. Its inflated `ComposeView`
sets:

```kotlin
layoutParams = ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.WRAP_CONTENT,
)
setViewCompositionStrategy(
    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
)
```

`onRemoved()` disposes, clears `lastComposeView`, and finishes owners.
`destroy()` disposes and clears idempotently. Delete the strip-provider source
and test rather than retaining an alias.

- [ ] **Step 5: Migrate composition registration**

Replace the provider field and only these host calls:

```kotlin
private val rowProvider = PersonaSpeakRowProvider(owners)

// onStartInputView
c.addExtensionRow(rowProvider)
val composeView = rowProvider.lastComposeView ?: return

// onFinishInput
c.removeExtensionRow(rowProvider)

// onDestroy
rowProvider.destroy()
```

Keep ViewModel creation, lifecycle installation, settings launch, editor
session transitions, and content callbacks unchanged. Guard repeated
`onStartInputView()` so content is installed once per provider view.

- [ ] **Step 6: Run focused host tests and verify GREEN**

Run the Step 3 command again.

Expected: exit 0; row-provider, composition, and owner tests all pass.

- [ ] **Step 7: Prove the old attachment path is gone**

Run:

```bash
if rg -n 'PersonaSpeakStripActionProvider|addStripAction\\(.*PersonaSpeak|stripProvider' \
  android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak; then
  exit 1
fi
```

Expected: exit 0 with no matches.

- [ ] **Step 8: Commit the host migration**

```bash
git add -A \
  android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime \
  android/keyboard/ime/app/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ime
git commit -m "fix(android): host PersonaSpeak in dedicated row"
```

Stop for non-author review of this commit before Task 3.

---

### Task 3: Make Every Rewrite State Fit the Dedicated Row Contract

**Files:**
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/personaspeak-ui/build.gradle.kts`
- Create: `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/ResultHeightPolicy.kt`
- Create: `android/personaspeak-ui/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/ResultHeightPolicyTest.kt`
- Modify: `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/RewritePanel.kt`
- Create: `android/personaspeak-ui/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/RewritePanelTest.kt`
- Modify: `android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/PersonaSpeakComposition.kt`

**Interfaces:**
- Consumes: `RewritePanelState` and a pre-expansion IME-container height
  sample from the host.
- Produces:
  `resultBodyMaxHeightPx(preExpansionHeightPx: Int, density: Float): Int` and
  `RewritePanel(state: RewritePanelState, onRewrite: () -> Unit, onApply: () -> Unit, onDismiss: () -> Unit, onSettings: () -> Unit, preExpansionImeHeightPx: () -> Int, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Add Compose/Robolectric unit-test aliases and configuration**

Add version-catalog libraries using the existing Compose BOM:

```toml
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
```

Add to `:personaspeak-ui`:

```kotlin
android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

- [ ] **Step 2: Write the failing pure height-policy tests**

```kotlin
@Test fun `uses forty percent below hard cap`() {
    assertEquals(400, resultBodyMaxHeightPx(1000, density = 2f))
}

@Test fun `caps body at 320dp`() {
    assertEquals(640, resultBodyMaxHeightPx(2400, density = 2f))
}

@Test fun `returns zero for an invalid pre-expansion sample`() {
    assertEquals(0, resultBodyMaxHeightPx(0, density = 2f))
}
```

- [ ] **Step 3: Write failing Compose state and accessibility tests**

Under Robolectric API 34, use `createComposeRule()` and render Idle, Loading,
Review, and Message with `preExpansionImeHeightPx = { 1000 }`. Assert:

```kotlin
composeRule.onNodeWithTag("personaspeak_rewrite")
    .assertIsDisplayed()
    .assertHeightIsAtLeast(48.dp)
composeRule.onNodeWithTag("personaspeak_settings")
    .assertIsDisplayed()
    .assertHeightIsAtLeast(48.dp)
composeRule.onNodeWithTag("personaspeak_candidate").assertIsDisplayed()
composeRule.onNodeWithTag("personaspeak_apply")
    .assertIsDisplayed()
    .assertHeightIsAtLeast(48.dp)
composeRule.onNodeWithTag("personaspeak_dismiss")
    .assertIsDisplayed()
    .assertHeightIsAtLeast(48.dp)
```

Add separate assertions that Loading exposes
`personaspeak_loading`, Message leaves Settings reachable, and Review displays
the synthetic candidate. Tag the scroll body
`personaspeak_candidate_body`; with a pre-expansion sample equal to 300dp,
assert:

```kotlin
composeRule.onNodeWithTag("personaspeak_candidate_body")
    .assertHeightIsAtMost(120.dp)
```

- [ ] **Step 4: Run focused UI tests and verify RED**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./android/gradlew -p android :personaspeak-ui:testDebugUnitTest \
  --tests '*ResultHeightPolicyTest' \
  --tests '*RewritePanelTest' \
  --rerun-tasks --no-daemon
```

Expected: compilation/test failure because the policy, parameter, dismiss tag,
and dedicated-row layout do not exist.

- [ ] **Step 5: Implement the pure frozen height policy**

```kotlin
internal fun resultBodyMaxHeightPx(
    preExpansionHeightPx: Int,
    density: Float,
): Int {
    if (preExpansionHeightPx <= 0 || density <= 0f) return 0
    val fortyPercent = (preExpansionHeightPx * 0.4f).roundToInt()
    val hardCap = (320f * density).roundToInt()
    return minOf(fortyPercent, hardCap)
}
```

The Compose panel samples `preExpansionImeHeightPx()` once per candidate:

```kotlin
val density = LocalDensity.current
val reviewBodyMaxHeightPx =
    if (state is RewritePanelState.Review) {
        remember(state.candidate) {
            resultBodyMaxHeightPx(
                preExpansionHeightPx = preExpansionImeHeightPx(),
                density = density.density,
            )
        }
    } else {
        0
    }
```

The existing candidate identity keeps the cap stable when its outcome changes.
A new candidate samples again. Use `remember`, never `rememberSaveable`;
content dimensions do not enter saved state.

- [ ] **Step 6: Split compact and expanded panel layouts**

Keep one full-width `Surface`. Render Idle, Loading, and Message in a
`Row(Modifier.heightIn(min = 48.dp))`. Render Review in a `Column`: candidate
inside a vertically scrollable body capped by the frozen policy, followed by a
48dp-minimum action row containing tagged `Use this`, `Dismiss`, and Settings.
Add:

```kotlin
Modifier.testTag("personaspeak_dismiss")
```

Do not introduce horizontal candidate scrolling, overlay/popup APIs, local
draft fields, history, or navigation state.

- [ ] **Step 7: Supply the pre-expansion container-height sampler**

In `PersonaSpeakComposition`, pass a callback that reads only geometry:

```kotlin
preExpansionImeHeightPx = {
    (composeView.parent as? View)?.height ?: 0
},
```

The panel freezes this value before Review layout changes it. No text or
provider data enters the callback.

- [ ] **Step 8: Run focused UI and host tests and verify GREEN**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./android/gradlew -p android \
  :personaspeak-ui:testDebugUnitTest \
  :ime:app:testDebugUnitTest \
  --tests '*ResultHeightPolicyTest' \
  --tests '*RewritePanelTest' \
  --tests '*PersonaSpeakCompositionTest' \
  --tests '*PersonaSpeakRowProviderTest' \
  --tests 'com.anysoftkeyboard.keyboards.views.KeyboardViewContainerViewTest' \
  --rerun-tasks --no-daemon
```

Expected: exit 0; every named suite passes.

- [ ] **Step 9: Commit the accessible state geometry**

```bash
git add \
  android/gradle/libs.versions.toml \
  android/personaspeak-ui/build.gradle.kts \
  android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite \
  android/personaspeak-ui/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite \
  android/keyboard/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ime/PersonaSpeakComposition.kt
git commit -m "fix(android): fit rewrite states in dedicated row"
```

Stop for non-author review before Task 4.

---

### Task 4: Re-run and Accept the Complete Task 7 Gate

**Files:**
- Modify: `android/scripts/verify-milestone-2-precutover.sh`
- Modify: `docs/evidence/milestone-2/precutover-commands.txt`
- Modify: `docs/superpowers/plans/2026-07-22-atomic-ask-cutover.md`

**Interfaces:**
- Consumes: the reviewed Tasks 1–3 commits.
- Produces: one final clean-head build receipt and one separately leased
  restoration-trapped emulator receipt accepted by a different-family
  non-author.

- [ ] **Step 1: Add focused geometry tests to the aggregate gate**

Require the Task 1–3 suites by exact pattern before the existing complete
tests, lint, assemble, closure, license, ledger, and core-purity checks. Keep
`--rerun-tasks`; do not permit cached acceptance.

Add this invocation immediately before the existing all-tests invocation:

```bash
echo "[7a/9] dedicated-row focused tests..."
"$root/gradlew" -p "$root" \
    :personaspeak-ui:testDebugUnitTest :ime:app:testDebugUnitTest \
    --tests '*ResultHeightPolicyTest' \
    --tests '*RewritePanelTest' \
    --tests '*PersonaSpeakCompositionTest' \
    --tests '*PersonaSpeakRowProviderTest' \
    --tests 'com.anysoftkeyboard.keyboards.views.KeyboardViewContainerViewTest' \
    --console=plain --no-daemon --rerun-tasks
echo "  OK"
```

- [ ] **Step 2: Commit the gate amendment before qualification**

```bash
git add \
  android/scripts/verify-milestone-2-precutover.sh \
  docs/superpowers/plans/2026-07-22-atomic-ask-cutover.md
git commit -m "test(android): gate dedicated row geometry"
```

- [ ] **Step 3: Run a preliminary clean-tree host/build gate**

From tracked-clean final HEAD, with JDK 21:

```bash
bash android/scripts/verify-milestone-2-precutover.sh
```

This is a qualification checkpoint, not final acceptance. Preserve its
complete raw log and stop on any non-zero status or contradictory count.

- [ ] **Step 4: Independently review the exact implementation head**

The reviewer verifies the full diff from `e186183`, container geometry,
upstream ledger, no privacy regression, and preliminary build log. The author
does not issue the verdict.

- [ ] **Step 5: Grant a separate device-only lease**

The lease contains the mandated preflight sentence, exact final SHA,
worktree, canonical APK path/hash, emulator identity, prior-IME capture,
restoration trap, privacy-safe evidence rules, no source edits, no Task 8,
no push, and explicit handback. It must verify restoration of the prior IME
and accepted APK/state on-device and at the canonical output path.

- [ ] **Step 6: Run the privacy-safe device matrix**

On `emulator-5554`, prove with synthetic content only:

1. PersonaSpeak Idle row, ASK suggestions, and all key rows are simultaneously
   visible and non-overlapping.
2. Real ASK keys type into an external host.
3. Loading keeps suggestions and keys visible.
4. Review shows candidate and actions before mutation while suggestions and
   keys remain visible.
5. Dismiss mutates zero times.
6. Apply mutates exactly once.
7. Message and stale/rejected/unconfirmed outcomes retain usable ASK geometry
   and the required no-retry behavior.
8. Settings launches inside the single PersonaSpeak package.
9. Crash-filtered logs contain no fatal package crash, ANR, or process death.

Persist no editor, prompt, provider, candidate, result, or replacement text in
logs or screenshots. Promote complete approved raw evidence before clearing
worker context.

- [ ] **Step 7: Restore, verify, and record the receipt**

On every success/stop/failure path, verify exact prior IME plus accepted
APK/state on-device and accepted APK hash at the canonical local output path.
Record the final SHA, APK hash, device build/API, exit statuses, restoration
proof, and deviations in `precutover-commands.txt`.

- [ ] **Step 8: Commit the qualification receipt**

Only after the device handback and restoration proof are independently
verified:

```bash
git add \
  docs/evidence/milestone-2/precutover-commands.txt \
  docs/superpowers/plans/2026-07-22-atomic-ask-cutover.md
git commit -m "test(android): accept dedicated row precutover gate"
```

- [ ] **Step 9: Run the sole final clean-HEAD acceptance**

From the tracked-clean receipt commit, run:

```bash
bash android/scripts/verify-milestone-2-precutover.sh
```

Preserve the complete raw log in the durable session evidence directory.
Record actual exit status, elapsed time, exact tested SHA, and mechanically
derive tests/failures/errors/skips from XML plus lint counts from
`lint-results-debug.xml`. Verify the canonical APK hash is identical to the
device-qualified hash. Any non-zero status, hash drift, or contradictory count
stops.

- [ ] **Step 10: Obtain the final exact-head verdict**

A different-model-family non-author verifies the receipt commit, complete
final raw log, mechanical counts, device/restoration evidence, catalog, and
canonical APK hash, then issues the verdict on that exact SHA.

Task 8 remains blocked until that verdict is APPROVE and all raw evidence is
cataloged.

## Plan Self-Review Checklist

- [x] Every dedicated-row design requirement maps to Tasks 1–4.
- [x] No incomplete marker, unnamed error handling, or unspecified test
  remains.
- [x] `ExtensionRowProvider`, `PersonaSpeakRowProvider`,
  `resultBodyMaxHeightPx`, and the `RewritePanel` sampler signature are
  consistent across producer and consumer tasks.
- [x] The container seam remains generic and existing strip actions stay on
  the candidate row.
- [x] The 48dp minimum and frozen 40%/320dp Review cap are tested.
- [x] All upstream edits are ledgered in the same commit.
- [x] Implementation and device mutation remain separate leases.
- [x] Final acceptance is clean-head, raw-log, mechanically counted,
  restoration-verified, and non-author/different-family graded.
- [x] Tasks 8–10 remain blocked.

## Estimate and Milestone Effect

- Task 1: 2–3 focused hours.
- Task 2: 1–2 focused hours.
- Task 3: 2–3 focused hours.
- Task 4 build/device/review gate: 3–4 focused hours.
- Expected total: 8–12 focused engineering hours, normally one working day.
- Contingency: one additional day if custom `ViewGroup` relayout,
  landscape/font-scale, or IME-height sampling contradicts host tests.

Milestone 2 gains one corrective cycle inside Task 7. Tasks 1–6 remain
accepted historical work; Tasks 8–10 shift but do not change scope. No
Milestone 3 product feature moves forward.
