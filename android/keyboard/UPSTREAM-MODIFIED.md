# AnySoftKeyboard upstream-modification ledger

This file lists every upstream-tracked file whose contents differ from the
pristine snapshot described in `UPSTREAM.md`. Use one entry per file:

```text
- <path-from-android/keyboard> — <reason for the current modification>
```

## Files modified against pristine

All entries below belong to the unified-root cutover (issue #47). The unified
root build at `android/` includes this snapshot's projects directly, so
Gradle's `rootDir` no longer points at `android/keyboard`. The root build
defines `askSourceRoot = rootProject.file("keyboard")` and every ASK-owned
`$rootDir` reference (script applies under `gradle/` and `addons/gradle/`,
`config/lint.xml`, `addons/scripts/` icon generators, the Robolectric jar
cache) was mechanically rewritten to derive from
`rootProject.ext.askSourceRoot`. References that intentionally target
unified-root output directories (`outputs/apk`, `outputs/bundle` in
`gradle/apk_module.gradle`) were left on `rootDir`. Replay guidance for every
rewrite entry: re-run the mechanical `$rootDir` scan/replace against a fresh
pristine copy and re-verify with the closure gate.

- api/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta in module logic.
- junit-sharding/build.gradle — askSourceRoot rewrite of the `gradle/jacoco.gradle` and `gradle/errorprone.gradle` applies. No behavioral delta.
- addons/base/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- addons/languages/build.gradle — askSourceRoot rewrite of the `addons/gradle/add_on_list_generator.gradle` apply. No behavioral delta.
- addons/languages/english/pack/build.gradle — askSourceRoot rewrite of the `addons/gradle/language_pack_lib.gradle` apply; plus `ext.dictionaryTextInputsEnabled = false` set before the pack library apply. Behavioral delta: the 144 provenance-ambiguous `dictionary/inputs/*` corpus files no longer enter the generated dictionary; the AOSP combined list and prebuilt XMLs remain the only inputs (see DICTIONARY-LICENSES.md). Replay: keep the switch false until per-corpus provenance is established.
- addons/gradle/language_pack_lib.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply and both (POSIX/Windows) `addons/scripts/generate_status_icon` invocation paths. No behavioral delta.
- gradle/android_general.gradle — askSourceRoot rewrite of the `errorprone/checkstyle/spotless/android_unit_test` applies and `config/lint.xml`. No behavioral delta.
- gradle/android_unit_test.gradle — askSourceRoot rewrite of the CI Robolectric jar cache path and the `gradle/jacoco.gradle` apply; plus `systemProperty 'robolectric.enabledSdks', '26,27,28,29,30,31,32,33,34,35,36'`. Behavioral delta: Robolectric no longer executes test parameterizations for API levels below PersonaSpeak's declared minSdk 26 (upstream emulated down to 21); API levels the shipped app cannot run on are not meaningful test targets. Replay: keep the enabled-SDK list equal to [minSdk, compileSdk].
- gradle/apk_module.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` and `gradle/versioning_apk.gradle` applies; APK/AAB copy destinations intentionally stay on `rootDir` so convenience copies land under the unified root `outputs/`. No behavioral delta beyond copy destination.
- ime/base/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/base-rx/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/base-test/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/prefs/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/notification/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/remote/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/fileprovider/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/addons/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/dictionaries/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/dictionaries/jnidictionaryv1/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/dictionaries/jnidictionaryv2/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/nextword/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/pixel/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/overlay/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/gesturetyping/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/voiceime/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/releaseinfo/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/chewbacca/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- ime/permissions/build.gradle — askSourceRoot rewrite of the `gradle/android_general.gradle` apply. No behavioral delta.
- buildSrc/src/main/java/MakeDictionaryPlugin.java — added a `dictionaryTextInputsEnabled` boolean extension (default true) guarding only the registration and merge of `dictionary/inputs/*`. Behavioral delta: packs can exclude raw text inputs from dictionary generation; default preserves upstream behavior. Replay: re-add the guard around the `parseTextInputFiles` block.
- ime/app/src/main/res/values/legal_strings_dont_translate.xml — added three shipped-dictionary-source notice string pairs (AOSP LatinIME combined word list, AnySoftKeyboard additionals.xml, AnySoftKeyboard websites.xml, each Apache-2.0). Behavioral delta: the Additional Software Licenses screen surfaces the dictionary sources. Replay: keep the three pairs in sync with DICTIONARY-LICENSES.md.
- ime/app/src/main/res/layout/additional_software_licenses.xml — appended three title/text sections referencing the new dictionary-source notice strings. Behavioral delta: same as above. Replay: same rule.
- ime/app/src/test/java/com/menny/android/anysoftkeyboard/InputMethodManagerShadow.java — the ASK `InputMethodInfo` fixture's hardcoded upstream package replaced with `BuildConfig.APPLICATION_ID`; the shadow's enable/current writers already key on `context.getPackageName()`, so the fixture must carry the branded id for enabled/default-IME checks to be satisfiable. Behavioral delta: test doubles now represent the shipped application id. Replay: keep the fixture on `BuildConfig.APPLICATION_ID`.
- ime/app/src/test/java/com/anysoftkeyboard/ime/AnySoftKeyboardKeyboardSubtypeTest.java — the four hardcoded expected `ComponentName("com.menny.android.anysoftkeyboard", …SoftKeyboard)` constructions now derive their package from `BuildConfig.APPLICATION_ID`; the full `SoftKeyboard` class name is unchanged, all verifications kept. Behavioral delta: expectations follow the branded application id. Replay: same substitution.
- ime/app/src/test/java/com/anysoftkeyboard/ui/settings/AboutAnySoftKeyboardFragmentTest.java — the exact Play-Store URL assertion now composes its `id=` query value from `BuildConfig.APPLICATION_ID`; assertion structure unchanged. Behavioral delta: expectation follows the branded application id. Replay: same substitution.
- ime/app/src/test/java/com/anysoftkeyboard/AnySoftKeyboardStartUpAllSdkTest.java — three shard `@Config` ranges repartitioned from [OLDEST-23][24-28][29-LATEST] to [26-27][28-30][31-LATEST(35)]: contiguous, non-overlapping, exhaustive over supported SDKs (minSdk 26), and each shard passes at least one supported SDK despite the pre-existing `Assume` skips on 30/32/33/34/35; test bodies/assertions unchanged; unused `OLDEST_SDK` import removed. Executed counts post-change: 2/3/5 runs with 2/2/1 passing. Replay: keep the shard union equal to [minSdk, LATEST_STABLE_API_LEVEL] and every shard containing a non-Assumed SDK.
- ime/app/src/test/java/com/menny/android/anysoftkeyboard/AnyApplicationDeviceSpecificAllSdkTest.java — same shard repartition as above ([26-27][28-30][31-LATEST(35)]), bodies/assertions unchanged, unused import removed. Executed counts post-change: 2/3/5 runs, all passing. Replay: same rule.
- ime/app/src/test/java/com/anysoftkeyboard/ime/AnySoftKeyboardMediaInsertionTest.java — three `Uri.EMPTY` fixtures replaced with the stable syntactically valid `content://com.anysoftkeyboard.test/media/1`; API 26+ `InputContentInfo` validates the content scheme at construction. All commit/skip assertions preserved. Replay: any fixed `content://` URI.
- ime/app/src/test/java/com/anysoftkeyboard/TestableAnySoftKeyboard.java — `createEditorInfo` now sets `packageName` from `BuildConfig.APPLICATION_ID` instead of the retired upstream literal; the overlay creator resolves the editor's host package via `createPackageContext`, which requires an existing (our own) package. Behavioral delta: test editors are hosted by the branded package, restoring pre-brand semantics. Replay: same substitution.
- ime/app/src/test/java/com/anysoftkeyboard/quicktextkeys/QuickTextKeyFactoryTest.java — expectations recalibrated to the API-26/v24 resource ground truth (`res/xml-v24/quick_text_keys.xml`, 16 entries): totals 18→16, post-two-disables 16→14, id `085020ea…` position 16→14. All ordering/identity/duplicate-elision/enablement assertions retained. Replay: recount the v24 XML.
- ime/app/src/test/java/com/anysoftkeyboard/quicktextkeys/ui/QuickTextPagerViewTest.java — same v24 recalibration: totals 18→16, enabled-after-one-disable 17→15 (pager 15+history), after-second-disable pager 17→15 (14+history). Assertions retained. Replay: recount the v24 XML.
- ime/app/src/test/java/com/anysoftkeyboard/keyboards/AnyPopupKeyboardTest.java — emoticons key count 98→187 with in-code comment: API 26 selects `res/xml-v24/quick_text_unicode_emoticons.xml` whose literal key count is 187 (base file 98; verified, not double-counting). Name/tag/parser assertions retained. Replay: recount the v24 XML.
- ime/app/src/test/resources/robolectric.properties — `sdk=23` raised to `sdk=26`: Robolectric's package parser rejects the PersonaSpeak test APK below its declared minSdk 26. Behavioral delta: unit tests emulate API 26 instead of 23. Replay: re-pin to the branded application's minSdk.
- ime/app/src/testAllAddOns/resources/robolectric.properties — `sdk=23` raised to `sdk=26`, same reason, delta, and replay guidance as the debug test pin above.
- ime/app/build.gradle — askSourceRoot rewrite of the `gradle/apk_module.gradle` apply, plus PersonaSpeak branding: `override_app_id = 'biz.pixelperfectstudios.personaspeak'`, Kotlin Android + Compose compiler plugins with `buildFeatures.compose`, `minSdkVersion 26` override (ASK-wide minimum stays 23), `implementation project(':personaspeak-ui')`, `implementation project(':core-personas')`, `implementation project(':core-providers')`, and Compose runtime dependencies (`platform(libs.compose.bom)`, `libs.compose.ui`). Behavioral delta: the assembled APK is branded PersonaSpeak with SDK-26 floor and links the first-party UI layer with Compose runtime plus the persona/provider core modules; upstream namespace `com.menny.android.anysoftkeyboard` is unchanged.
- ime/app/src/main/java/com/menny/android/anysoftkeyboard/SoftKeyboard.java — PersonaSpeak lifecycle forwarding: added `PersonaSpeakComposition` field initialized in `onCreate()`, overrides for `onStartInput()`, `onCreateInputView()`, `onStartInputView()`, `onFinishInput()`, `onDestroy()`, and `onUpdateSelection()` to forward lifecycle events to the composition. `onCreateInputView` passes `getWindow()` so the composition can install ViewTree owners on the IME window's decor view (fixes `ViewTreeLifecycleOwner not found from parentPanel`). All existing superclass calls and DELAY_SELECTION_UPDATES behavior preserved. Behavioral delta: the IME now hosts a PersonaSpeak rewrite strip action via the composition; upstream package namespace unchanged.
- ime/app/src/main/java/com/anysoftkeyboard/ui/settings/MainFragment.java — PersonaSpeak settings entry: added `android.provider.Settings` import and click handler in `onViewCreated` wiring the new `personaspeak_settings_card` to `Settings.ACTION_INPUT_METHOD_SETTINGS`. No upstream behavior changed. Replay: re-add the import and click-lambda block after the notification-permission listener.
- ime/app/src/main/res/layout/main_fragment.xml — PersonaSpeak settings entry: appended one CardView (`personaspeak_settings_card`) with a TextView referencing `@string/personaspeak_keyboard_settings`, matching the existing CardView+TextView card pattern. Replay: re-append the CardView block before the closing `</LinearLayout>`.
- ime/app/src/main/res/values/strings.xml — PersonaSpeak settings entry: added `personaspeak_keyboard_settings` string resource. Replay: re-add the single `<string>` element.
