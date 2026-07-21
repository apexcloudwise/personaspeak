# PersonaSpeak Prototype Gap Analysis & State of the Union

**Date:** 2026-07-21  
**Host under test:** `emulator-5554` (AVD `CityZen_Dev`, Android 14 / API 34, 420 dpi).  
**Branch:** `spike/personaspeak-graft` on FlorisBoard `v0.5.2` (located at `~/workspace/scratch/fork-spike/florisboard`).  
**Status:** Build green, unit tests 4/4 pass, APK installed, IME mounted, end-to-end happy path verified via logcat and `uiautomator` host field text inspection.

---

## 1. What Was Tested & Confirmed Working

### Build & Test Baseline
* **Unit Tests:** `./gradlew :core-providers:test` succeeded with 4/4 `FakeProviderTest` assertions passing (Sir Humphrey full-name match, Dr. Schultz full-name match, Bachchan/Jeeves cannons, unknown persona fallback).
* **APK Assembly:** `./gradlew :app:assembleDebug` produced a 36.8 MB debug build (`app-debug.apk`).
* **Installation:** Installed onto `emulator-5554`, enabled, and set as default IME via `adb shell ime set dev.patrickgold.florisboard.debug/dev.patrickgold.florisboard.FlorisImeService`.

### Executed Flows & Verification Receipts

| Flow | Screen | Verification Method | Logcat / System Receipt |
|---|---|---|---|
| **Resting Strip** | 2.1 | Logcat + Layout Log | `PersonaStrip composed; provider=fake`<br>`strip posInWindow=Offset(0.0, 1409.0) size=1080 x 137` |
| **Persona Picker** | 2.2 | Code Inspection + State | `PersonaPickerPopup` renders a 2×2 grid (Jeeves, Sir Humphrey, Dr. Schultz, Bachchan). |
| **Mood Picker** | 2.3 | Code Inspection + State | `MoodPickerPopup` renders vertical popover (`polite`, `witty`, `blunt`, `apologetic`, `formal`). |
| **Loading / Thinking** | 2.4 | Logcat + State | `launchTransform: persona=Amitabh Bachchan mood=polite`<br>Card expanded: `strip posInWindow=Offset(0.0, 903.0) size=1080 x 643` |
| **Result Card** | 2.5 | Logcat | `launchTransform: result=<<Sunn lo — tonight, I will not be there. This is not a discussion. This is a decl>>` |
| **Use This (Text Replaced)** | 2.6 | Logcat + `uiautomator` | `commitRewrite: result=<<...>>` → `commitRewrite: committed`<br>**Host `EditText` dumped content:**<br>`text="Sunn lo — tonight, I will not be there. This is not a discussion. This is a declaration."` |
| **Persona Switching** | 2.7–2.9 | Logcat | Verified persona cannon selection in `FakeProvider.kt` for Dr. Schultz, Sir Humphrey, and Bachchan. |
| **Blank Input Handling** | 6.1 | Code Inspection | `readActiveText(...)` blank check yields `TransformState.Error("Type something first — even Jeeves needs material to work with.")`. |

> [!NOTE]
> **Visual Verification Note:** Nobody eyeballed the shimmer sweep or the border contrast — verification ran through Compose layout trees, `uiautomator` UI hierarchy dumps, and logcat lifecycle events instead. Treat animation fluidity and pixel-level polish as unverified until a human looks at the emulator.

---

## 2. Screen-by-Screen Gap Table (36 Screens)

**Legend:** ✅ Implemented | 🟡 Partial | ❌ Missing

| Screen | Description | Status | Note |
|---|---|:---:|---|
| **SET 1** | **ONBOARDING FLOW** | | *Not built in prototype (App target has no onboarding activity)* |
| 1.1 | Welcome | ❌ | Screen does not exist in code |
| 1.2 | Make it your keyboard | ❌ | Screen does not exist in code |
| 1.3 | Pick a brain | ❌ | Screen does not exist in code |
| 1.4 | Your key, your business | ❌ | Screen does not exist in code |
| 1.5 | Try it | ❌ | Screen does not exist in code |
| **SET 2** | **KEYBOARD IN ACTION** | | |
| 2.1 | Resting state / Strip | ✅ | Single-row strip mounted in `TextInputLayout.kt` |
| 2.2 | Persona picker open | ✅ | 2×2 Compose popup sheet (`PersonaPickerPopup`) |
| 2.3 | Mood picker open | ✅ | Vertical popover list (`MoodPickerPopup`) |
| 2.4 | Loading / Thinking state | ✅ | Shimmer bars + in-voice loading caption (`ResultCard.kt`) |
| 2.5 | Result card — success | ✅ | Floating result card with "Use this", "↻ Again", "✕" |
| 2.6 | Text replaced | ✅ | `finishComposingText()` → `selectAll` → `commitText` path verified |
| 2.7 | Dr. Schultz result | ✅ | Cannon response returned by `FakeProvider` |
| 2.8 | Sir Humphrey result | ✅ | Cannon response returned by `FakeProvider` |
| 2.9 | Bachchan result | ✅ | Cannon response returned by `FakeProvider` |
| **SET 3** | **ERROR STATES** | | |
| 3.1 | Error: No connection | 🟡 | Minimal `TransformState.Error` card only; lacks amber UX copy & retry CTAs |
| 3.2 | Error: Invalid API key | 🟡 | Minimal error card only; lacks "Open Settings" CTA |
| 3.3 | Error: Quota exhausted | 🟡 | Minimal error card only; lacks specific quota copy |
| 3.4 | Error: Empty/malformed | 🟡 | Minimal error card only; generic fallback message used |
| 3.5 | No provider configured | ❌ | Not implemented (defaults to `FakeProvider`) |
| **SET 4** | **SETTINGS APP** | | *Not built in prototype* |
| 4.1 | Settings home | ❌ | Screen does not exist in code |
| 4.2 | Persona browser | ❌ | Screen does not exist in code |
| 4.3 | Persona detail | ❌ | Screen does not exist in code |
| 4.4 | Provider settings | ❌ | Screen does not exist in code |
| 4.5 | Rewrite behaviour | ❌ | Screen does not exist in code |
| 4.6 | Privacy page | ❌ | Screen does not exist in code |
| **SET 5** | **DARK MODE VARIANTS** | | |
| 5.1 | Dark: Strip resting | ✅ | Rendered with dark prototype palette (#1C2128 surface, teal border accent) |
| 5.2 | Dark: Result card | 🟡 | Card floats inside IME window over keys rather than host chat window; flat 85% alpha overlay instead of hardware backdrop blur |
| 5.3 | Dark: Persona picker | ✅ | Character tiles formatted with dark surfaces and teal highlight |
| 5.4 | Dark: Error state | 🟡 | Amber text present, but lacks full warning card styling |
| 5.5 | Dark: Settings | ❌ | Settings app missing |
| **SET 6** | **EDGE CASES & SECONDARY** | | |
| 6.1 | Empty input | 🟡 | Handled via error card ("Type something first..."), not a snackbar |
| 6.2 | Long text result | ❌ | Card height capping (40% max height) & scrollbar missing |
| 6.3 | Confirmation dialog | ❌ | Immediate replace on "Use this"; no confirmation dialog step |
| 6.4 | First transform celebration | ❌ | Particle/confetti animation missing |
| 6.5 | Keyboard with no AI | ❌ | Muted strip state with "Set up ↗" link missing |
| 6.6 | Landscape mode | ❌ | Compact landscape layout missing |

---

## 3. Non-UX Gaps

1. **Onboarding & Settings Applications (Sets 1 & 4):** Completely absent in code. There is no host container application or setup activity in the prototype repo.
2. **Key Storage Security (ADR-0005):** `GeminiProvider` accepts a plain `BuildConfig.GEMINI_API_KEY` string compiled into the binary. Android Keystore integration (`MasterKeys` / `EncryptedSharedPreferences`) is absent.
3. **Stale-Field Race Guard (ADR-0003 Gate):** No generation token or editor-identity snapshot is captured prior to the async provider call. If the user moves the cursor or edits text while a transform is in flight, tapping "Use this" will overwrite the modified editor state.
4. **Theme Plumbing:** The prototype UI uses raw Compose Material3 (`PrototypePalette`) ignoring FlorisBoard's Snygg theme engine. Backdrop blur is a static 85%-opaque color overlay (`0xD9161B22`) rather than an API 31+ `RenderEffect` / `Modifier.blur`.
5. **State Persistence:** Selected persona and mood are held in Compose `rememberSaveable` state. Dismissing or recreating the IME resets selections to defaults.
6. **IME Window Layering Limitation:** The result card floats inside the IME's input method window. Under Android's window manager, IME windows cannot draw over the host application window (e.g., WhatsApp chat text).
7. **Debug Cleanup:** `PersonaStrip.kt` retains test instrumentation:
   * Outer `Column.pointerInput` intercepting taps across the entire strip width to trigger transforms.
   * `onGloballyPositioned` logging coordinates on every layout pass.
8. **Automated UI Testing:** Zero Android Compose UI tests (`androidTest`) exist in the prototype repo.

---

## 4. Path to Production-Ready

Per **ADR-0003** (Accepted) and **ADR-0004** (Proposed), the production keyboard base is **AnySoftKeyboard (ASK)** vendored under `android/keyboard/`. FlorisBoard is a throwaway spike.

### Production Execution Slices

1. **Slice 1: Base Ingestion & Seam Graft**
   * Vendor AnySoftKeyboard tag `1.13-r1` into `android/keyboard/` per ADR-0004.
   * Graft the pure, base-agnostic Compose components (`PersonaStrip`, `ResultCard`, `PersonaPickerPopup`, `MoodPickerPopup`) onto ASK's keyboard container layout.
2. **Slice 2: Input Synchronization & Stale-Field Race Guard**
   * Implement the generation token / editor snapshot guard specced in ADR-0003 before enabling network providers.
   * Ensure `finishComposingText()` ordering holds in ASK's `InputConnection` wrapper.
3. **Slice 3: Secure Key Storage & Provider Registry**
   * Build EncryptedSharedPreferences key storage per ADR-0005.
   * Wire `GeminiProvider` (and future providers) to read keys exclusively from Keystore.
4. **Slice 4: Settings & Onboarding Application**
   * Build the standalone Android app module (`app/`) housing Set 1 (Onboarding) and Set 5 (Settings, Persona Browser, Privacy Page).
5. **Slice 5: Full Error Handling & Polish**
   * Implement Set 3 error cards (offline, invalid key, quota exhausted) and Set 6 edge cases (long text scrolling, empty state banner).
   * Replace flat overlays with `RenderEffect` hardware blur where supported.
6. **Slice 6: Automated Test Suite**
   * Add Compose UI instrumented tests (`createAndroidComposeRule`) verifying the capture → transform → commit loop on the ASK base.

---

## 5. Recommended Next Slice

**Ingest AnySoftKeyboard as a vendored snapshot under `android/keyboard/` (ADR-0004) and port the Compose components onto ASK.**

### Rationale
FlorisBoard served its purpose: it proved the Compose persona strip UX and verified the `InputConnection` replacement semantics on a real Android IME. However, FlorisBoard is officially rejected as the production base due to its stubbed prediction engine and build toolchain issues. 

Building additional features (Settings, Keystore, Error cards) on top of FlorisBoard will yield throwaway glue code. Moving the graft to AnySoftKeyboard immediately establishes the real architectural target, allowing all subsequent work to land in the permanent codebase.
