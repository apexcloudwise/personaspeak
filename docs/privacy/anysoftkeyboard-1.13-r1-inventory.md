# Privacy inventory — AnySoftKeyboard `1.13-r1`

**Status:** Static source analysis complete; on-device verification pending.
**Pinned source:** tag `1.13-r1`, commit `8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d`
from `https://github.com/AnySoftKeyboard/AnySoftKeyboard`, audited in a scratch
clone (not vendored into this repo).
**Scope of this audit:** the `:ime:app` runtime and every Gradle module it
depends on (`:ime:base`, `:ime:nextword`, `:ime:dictionaries*`, `:ime:chewbacca`,
`:ime:remote`, `:ime:prefs`, `:ime:notification`, `:ime:fileprovider`,
`:ime:voiceime`, `:ime:gesturetyping`, `:api`, `:addons:base`). Build-time only
modules (`buildSrc/`, `addons/gradle/`) are noted where they touch the question
but are not in the shipped APK.
**Method:** static source analysis — grep + reading of manifests, Gradle files,
and the relevant Java/Kotlin classes. Cited as `path:line` against the pinned
ASK tree. No device was run; no network was captured; no storage was inspected.
Per ADR-0005's verification bar, this inventory is a *necessary* input to the
privacy copy but is not by itself *sufficient* to ship the claim.

The three kinds of data named in ADR-0005 are kept separate throughout. Findings
tables carry: **exists / data location / leaves device? / default-on? /
user-clearable? / evidence**. Plain text only — VOICE.md rule 6, load-bearing
content stays technical.

---

## 1. On-device local state

Anything the keyboard writes to its own private storage (`/data/data/<pkg>/`,
SharedPreferences, app-private SQLite) to do its job. Stays on the device by
*intent*; whether any of it leaves via Android Auto Backup is section 2, not
here.

### 1.1 Learned words / user (personal) dictionary

| Property | Value |
|---|---|
| Exists? | **Yes.** |
| Data location | App-private SQLite, file `fallback.db`, table per locale. Written by `FallbackUserDictionary` via `WordsSQLiteConnection`. `ime/app/src/main/java/com/anysoftkeyboard/dictionaries/sqlite/FallbackUserDictionary.java:29`. The `UserDictionary` wrapper tries Android's built-in `android.provider.UserDictionary.Words` first and falls back to `fallback.db` on any error (`ime/app/src/main/java/com/anysoftkeyboard/dictionaries/UserDictionary.java:71-110`). |
| Leaves device? | Not by app code. Section 2 covers Auto Backup. |
| Default-on? | **Yes**, two ways. (a) The `FallbackUserDictionary` path is the *default*: `settings_default_always_use_fallback_user_dictionary = true` (`ime/app/src/main/res/values/settings_defaults_dont_translate.xml:49`), so the app uses its private `fallback.db` rather than the shared system `UserDictionary` unless the user opts out. (b) Words enter the dictionary when promoted out of the auto-dictionary (see 1.2); promotion is the only normal write path. `READ_USER_DICTIONARY`/`WRITE_USER_DICTIONARY` permissions are declared (`ime/app/src/main/AndroidManifest.xml:15-16`) but only exercised on the non-default Android-system path. |
| User-clearable? | **Yes.** In-app words editor (`UserDictionaryEditorFragment`) and per-word delete (`EditableDictionary.deleteWord`). The OS-system path is clearable from Android's Settings → User dictionary. |
| Evidence | `ime/app/src/main/java/com/anysoftkeyboard/dictionaries/UserDictionary.java:37-110`, `ime/app/src/main/java/com/anysoftkeyboard/dictionaries/sqlite/FallbackUserDictionary.java:21-30`, `ime/app/src/main/res/values/settings_defaults_dont_translate.xml:49`. |

### 1.2 Auto-learned words (mid-tier before promotion)

| Property | Value |
|---|---|
| Exists? | **Yes.** |
| Data location | App-private SQLite, file `auto_dict_2.db`. `ime/app/src/main/java/com/anysoftkeyboard/dictionaries/sqlite/AutoDictionary.java:51`. Created and loaded by `SuggestionsProvider` on every locale setup: `ime/app/src/main/java/com/anysoftkeyboard/dictionaries/SuggestionsProvider.java:347-349`. |
| Leaves device? | Not by app code. See Auto Backup (2.1). |
| Default-on? | **Yes.** Threshold pref `settings_key_auto_dictionary_threshold` defaults to `9` (`ime/app/src/main/res/values/settings_defaults_dont_translate.xml:32`, key at `ime/app/src/main/res/values/settings_keys_dont_translate.xml:88`). Setting it to `-1` (the "off" entry in the UI list `auto_dictionary_threshold_type_off`, `ime/app/src/main/res/values/settings_keys_dont_translate.xml:97`) disables learning — `AutoDictionary.java:68` short-circuits when `mLearnWordThreshold == -1`. Until that value is set, every typed word of length 2..MAX is recorded with a frequency count, and at count ≥ threshold the word is promoted to the user dictionary (`AutoDictionary.java:73-77`). |
| User-clearable? | **Indirectly.** Clearing is via the user-dictionary editor (promoted words) plus clearing app data; there is no separate "clear auto-dictionary" button. The next-word clear (1.3) is separate. |
| Evidence | `ime/app/src/main/java/com/anysoftkeyboard/dictionaries/sqlite/AutoDictionary.java:36-84`, `ime/app/src/main/java/com/anysoftkeyboard/dictionaries/SuggestionsProvider.java:347-349,511`, `ime/app/src/main/res/values/settings_defaults_dont_translate.xml:32`. |

### 1.3 Next-word / prediction model state

| Property | Value |
|---|---|
| Exists? | **Yes.** |
| Data location | App-private file `next_words_<locale>.txt`, written with `Context.MODE_PRIVATE` via `openFileOutput`. `ime/nextword/src/main/java/com/anysoftkeyboard/nextword/NextWordsStorage.java:20-21,63`. One file per locale. Format: a versioned text file (parser V1: `NextWordsFileParserV1`). |
| Leaves device? | Not by app code. See Auto Backup (2.1). |
| Default-on? | **Yes.** A `NextWordDictionary` is unconditionally constructed per locale inside `UserDictionary` (`ime/app/src/main/java/com/anysoftkeyboard/dictionaries/UserDictionary.java:42`), loads on `load()` (line 80), and writes on every `close()` (line 76-77 → `NextWordsStorage.storeNextWords`). The user-facing "Next word suggestions" preference (`settings_key_next_word_dictionary_type`, `ime/app/src/main/res/xml/prefs_next_word.xml:10-21`) gates *whether suggestions are surfaced*, not whether storage happens; storage runs regardless of the suggestion-mode setting. Cap: 900 first-word containers (`NextWordDictionary.java:18`). |
| User-clearable? | **Yes.** Dedicated "Clear next-word data" preference: `ime/app/src/main/res/xml/prefs_next_word.xml:33-35` (`clear_next_word_data`), wired to `NextWordDictionary.clearData()` (`ime/nextword/src/main/java/com/anysoftkeyboard/nextword/NextWordDictionary.java:104-107`). |
| Evidence | `ime/nextword/src/main/java/com/anysoftkeyboard/nextword/NextWordsStorage.java:18-87`, `ime/nextword/src/main/java/com/anysoftkeyboard/nextword/NextWordDictionary.java:29-85`, `ime/app/src/main/java/com/anysoftkeyboard/dictionaries/UserDictionary.java:42`, `ime/app/src/main/res/xml/prefs_next_word.xml:10-35`. |

### 1.4 Clipboard history

| Property | Value |
|---|---|
| Exists? | **Yes, but in-memory only.** |
| Data location | A `List<CharSequence> mEntries` ring buffer inside the keyboard process, max 15 entries (`MAX_ENTRIES_INDEX = 15`). `ime/app/src/main/java/com/anysoftkeyboard/devicespecific/ClipboardV11.java:28-30`. Populated by an `OnPrimaryClipChangedListener` that copies the OS clipboard's first `ClipData.Item` into the buffer on every OS clip change (`ClipboardV11.java:92-119`). `ClipboardV16` overrides `getTextFromClipItem` to `coerceToStyledText`; `ClipboardV28` adds `clearPrimaryClip()`. **No disk persistence**: `mEntries` is a plain `ArrayList` with no `SharedPreferences` or file write anywhere in the `Clipboard*` classes (`ime/app/src/main/java/com/anysoftkeyboard/devicespecific/Clipboard.java`, `ClipboardV11.java`, `ClipboardV16.java`, `ClipboardV28.java`). |
| Leaves device? | No. The buffer lives only while the keyboard process lives. |
| Default-on? | The listener is registered only when `setClipboardUpdatedListener` is called with a non-null listener (`ClipboardV11.java:42-53`), i.e. when the clipboard panel is in use. There is no "off" pref because there is no persistence to turn off — closing the keyboard process clears the buffer. Whether the clipboard *panel* is offered is a UI setting, out of scope for data-at-rest. |
| User-clearable? | **Yes.** `deleteEntry(int)` and `deleteAllEntries()` (`Clipboard.java:36-38`, `ClipboardV11.java:71-86`); the latter also calls `ClipboardManager.setPrimaryClip(empty)` / `clearPrimaryClip()`. Process restart also clears it. |
| Evidence | `ime/app/src/main/java/com/anysoftkeyboard/devicespecific/Clipboard.java:22-41`, `ime/app/src/main/java/com/anysoftkeyboard/devicespecific/ClipboardV11.java:27-128`, `ime/app/src/main/java/com/anysoftkeyboard/devicespecific/ClipboardV16.java:22-31`, `ime/app/src/main/java/com/anysoftkeyboard/devicespecific/ClipboardV28.java:23-33`. |

### 1.5 Other on-device state (for completeness)

- **Settings/SharedPreferences.** Used pervasively via `DirectBootAwareSharedPreferences.create(context)` (e.g. `ime/app/src/main/java/com/menny/android/anysoftkeyboard/AnyApplication.java:133,183`). Stored in `/data/data/<pkg>/shared_prefs/`. Contains configuration only — no typed-text content was found in any pref key we inspected, but a complete negative here would need the on-device verification this audit defers. Covered by Auto Backup (2.1).
- **Crash report files** (`new_crash_details.log`, `crash_report_details_<TIME>.log`). On-device, app-private (`mApp.getFilesDir()` / `openFileOutput(..., MODE_PRIVATE)`). `ime/chewbacca/src/main/java/com/anysoftkeyboard/chewbacca/ChewbaccaUncaughtExceptionHandler.java:46-49,192`. Content is exception stack trace, device info, and (in debug builds only — see 3.1) recent log lines. Discussed as a *disclosure surface* in section 3 because that is the more accurate boundary; physically it is also on-device local state. Covered by Auto Backup (2.1).
- **Gesture-typing buffers.** `:ime:gesturetyping` ships JNI libraries; no persistent storage code was found in the module's Java sources, but the native `.so` files are out of scope for static Java analysis. Flagged as an open question in section 6.

---

## 2. Anything that leaves the device

ADR-0005's bar is strict: "any network call from *anywhere in the app's UID* —
not just the keyboard process." Default posture is **off, and proven off.** Two
behaviours in the pinned snapshot do not meet that bar as-shipped.

### 2.1 Android Auto Backup — **default-on, ships local state off-device**

| Property | Value |
|---|---|
| Exists? | **Yes.** |
| Data location leaving device | The app's entire private data dir (`/data/data/<pkg>/`) is eligible: `fallback.db`, `auto_dict_2.db`, `next_words_*.txt`, `shared_prefs/*.xml`, and `new_crash_details.log` / `crash_report_details_*.log`. Android Auto Backup makes this **eligible for whatever backup transport the device has configured** — commonly Google Drive, but the transport and its cloud/OEM behaviour (and any device-to-device transfer path) vary and are outside app control. The app controls neither destination nor timing and cannot read the bytes back itself; nonetheless, **the bytes are eligible to leave the device**, which is the boundary ADR-0005 draws. |
| Leaves device? | **Yes**, when Android's backup transport fires (device idle + charging + network, per OS policy — not under app control). |
| Default-on? | **Yes.** `android:allowBackup="true"` and `android:fullBackupOnly="true"` in `ime/app/src/main/AndroidManifest.xml:32-33`. Crucially, **no `android:fullBackupContent` and no `android:dataExtractionRules` attribute or XML resource exists anywhere in the audited tree** (verified by repo-wide search of `**/*.xml`), so Android's *default* Auto Backup rules apply — which include the whole `files/`, `databases/`, and `shared_prefs/` trees. A commented-out Google Backup `api_key` sits at `ime/app/src/main/AndroidManifest.xml:38-43`; it is inactive, and Auto Backup does not require it. |
| User-clearable? | At the OS level (Settings → Backup), not from inside the app. There is no in-app control that turns Auto Backup off for this app's data. |
| Evidence | `ime/app/src/main/AndroidManifest.xml:25-43`; absence of `fullBackupContent`/`dataExtractionRules` confirmed by repo-wide search; Auto Backup default-include rules are an Android platform behaviour, not in this repo. |
| Fork implication | This is the headline neutralization item (section 5). |

### 2.2 Crash report — user-initiated email send (not auto-telemetry)

| Property | Value |
|---|---|
| Exists? | **Yes.** |
| Data location leaving device | A crash report file (see 1.5 / 3.2) is offered to the user via a notification + `SendBugReportUiActivity` (`ime/app/src/main/AndroidManifest.xml:97-103`, `ime/chewbacca/src/main/java/com/anysoftkeyboard/chewbacca/ChewbaccaUncaughtExceptionHandler.java:108-116,210-241`). The user must tap the notification, review, and explicitly send. The destination address is a build-time env var, `ANYSOFTKEYBOARD_CRASH_REPORT_EMAIL` (`ime/app/build.gradle:19-22`). |
| Leaves device? | **Only on explicit user action**, and only to whatever email client the user picks. There is no background upload path in source. **No crash/analytics SDK appears in source or Gradle config**: repo-wide search for `crashlytics\|firebase\|fabric\|flurry\|googleanalytics\|amplitude\|sentry\|bugsense\|bugsee` returns zero source/manifest hits, there is no `google-services.json`, and no `com.google.gms`/`com.google.firebase`/`io.fabric` Gradle plugin appears in any `*.gradle`/`*.toml`/`*.kts`. The dependency catalog (`gradle/libs.versions.toml`) declares no HTTP/analytics client; the only HTTP libs (`httpclient`/`jsoup`) are confined to `buildSrc/build.gradle:17-21` (build-time dictionary generation, not shipped). **This is a source-level negative, not a proof of absence** — it cannot see transitive/resolved runtime deps, shaded SDKs, or native egress; per section 6, confirmation needs the resolved dependency graph and the decompiled release APK. |
| Default-on? | The handler is installed **default-on** (`settings_default_show_chewbacca = true`, `ime/app/src/main/res/values/settings_defaults_dont_translate.xml:20`), but only the *file write + notification* half is automatic — the *send* half is never automatic. |
| User-clearable? | The crash file lives in app private storage and is removed by the handler when archived (`ChewbaccaUncaughtExceptionHandler.java:104-106`) and by clearing app data. The handler itself can be disabled via `settings_key_show_chewbacca`. |
| Evidence | `ime/app/build.gradle:19-22`, `ime/app/src/main/AndroidManifest.xml:97-103`, `ime/chewbacca/src/main/java/com/anysoftkeyboard/chewbacca/ChewbaccaUncaughtExceptionHandler.java:71-114,116-208`, `ime/app/src/main/res/values/settings_defaults_dont_translate.xml:20`; analytics-SDK absence per the searches cited above. |

### 2.3 User-initiated browser / store handoffs (not silent egress)

The app opens external apps via `Intent(ACTION_VIEW, Uri)` at a handful of
sites: addon-store search (`ime/addons/src/main/java/com/anysoftkeyboard/addons/ui/AddOnStoreSearchController.java:95`),
voice-input install prompt (`ime/app/src/main/java/com/anysoftkeyboard/ui/VoiceInputNotInstalledActivity.java:41`),
About screen's privacy / website / rate-in-store links (`ime/app/src/main/java/com/anysoftkeyboard/ui/settings/AboutAnySoftKeyboardFragment.java:60-75`),
and the changelog "read more" link to
`https://github.com/AnySoftKeyboard/AnySoftKeyboard/milestone/...`
(`ime/releaseinfo/src/main/java/com/anysoftkeyboard/releaseinfo/VersionChangeLogs.java:18-437`).
All targets are hard-coded URLs; all invocations are user-clicked; none pass
typed text in the URI or as an extra. These are listed for completeness — they
do not constitute silent egress — but the hard-coded URLs themselves need
review at graft time (the privacy link points at an AnySoftKeyboard page, not a
PersonaSpeak one).

---

## 3. On-device disclosure surfaces

ADR-0005 distinguishes these from egress: a disclosure surface leaks typed text
where another app, a bug report, or `adb` could read it, even though the bytes
never cross a network.

### 3.1 Logger / logcat

| Property | Value |
|---|---|
| Risk in release builds? | **Low, with one caveat below.** |
| Mechanism | `com.anysoftkeyboard.base.utils.Logger` is the project's logging façade. Each level is gated by `msLogger.supportsX()` *and* the in-memory ring buffer (`msLogs[255]`) is only appended inside the same gate (`ime/base/src/main/java/com/anysoftkeyboard/base/utils/Logger.java:51-201`). The **release** `AnyApplication` installs `NullLogProvider`, for which every `supportsX()` returns `false` (`ime/base/src/main/java/com/anysoftkeyboard/base/utils/NullLogProvider.java:1-61`, installed at `ime/app/src/main/java/com/menny/android/anysoftkeyboard/AnyApplication.java:415`). Result: in release builds, `Logger.*` calls short-circuit before they write to logcat *or* to the ring buffer. Only **debug** builds install `LogCatLogProvider` (`ime/app/src/debug/java/com/anysoftkeyboard/debug/DebugAnyApplication.java:33`, `ime/app/src/debug/java/com/anysoftkeyboard/debug/LogCatLogProvider.java:1-78`), which is exactly the intent stated in that class's javadoc. |
| Caveat | One direct `android.util.Log.d(...)` call exists in main source, bypassing the façade: `ime/app/src/main/java/com/anysoftkeyboard/AnySoftKeyboard.java:929`, with the static string `"handleDeleteLastCharacter will just sendDownUpKeyEvents."`. It carries no typed text, but it does write to logcat in release. A complete negative ("no other direct `android.util.Log` calls in main source") holds for the audited tree: repo-wide search found only this one site in `:ime:app`'s `main` source set (`src/debug/` source sets are excluded from release). |
| User-clearable? | N/A — logcat is an OS surface. |
| Evidence | `ime/base/src/main/java/com/anysoftkeyboard/base/utils/Logger.java:41,47-201`, `ime/base/src/main/java/com/anysoftkeyboard/base/utils/NullLogProvider.java:1-61`, `ime/app/src/main/java/com/menny/android/anysoftkeyboard/AnyApplication.java:415`, `ime/app/src/debug/java/com/anysoftkeyboard/debug/DebugAnyApplication.java:33`, `ime/app/src/main/java/com/anysoftkeyboard/AnySoftKeyboard.java:929`. |

### 3.2 Crash report file contents

| Property | Value |
|---|---|
| Risk? | **A disclosure surface, bounded.** |
| Mechanism | On any uncaught exception (or RxJava error) the handler writes `new_crash_details.log` into app-private storage (`mApp.openFileOutput(NEW_CRASH_FILENAME, MODE_PRIVATE)`) containing: timestamp, app version, exception class + message + stack trace, device build/locale/configuration (`ChewbaccaUtils.getSysInfo` — `ime/chewbacca/src/main/java/com/anysoftkeyboard/chewbacca/ChewbaccaUtils.java:28-45`), and `Logger.getAllLogLines()` (`ChewbaccaUncaughtExceptionHandler.java:182-188`). Per 3.1, that last field is **empty in release builds** because the ring buffer is never populated. The crash file is therefore stack-trace + device-info in release. **Caveat:** it is not guaranteed to be free of user text — an exception *message* can carry user-controlled content (e.g. a string that was being processed when the throw occurred), so "no typed text" is a strong default, not a proof. The file is app-private (MODE_PRIVATE), but is reachable by `adb`, by a Google bug report, and — per 2.1 — by Auto Backup. |
| Default-on? | **Yes** (`settings_default_show_chewbacca = true`). |
| User-clearable? | **Yes** — disabling the handler via `settings_key_show_chewbacca`, dismissing/clearing the notification, or clearing app data. |
| Evidence | `ime/chewbacca/src/main/java/com/anysoftkeyboard/chewbacca/ChewbaccaUncaughtExceptionHandler.java:46-49,71-114,116-208`, `ime/chewbacca/src/main/java/com/anysoftkeyboard/chewbacca/ChewbaccaUtils.java:28-45`. |

### 3.3 OS clipboard (informational)

The `ClipboardV*` classes read the OS clipboard (`ClipboardManager.getPrimaryClip()`)
to populate the in-memory history (1.4). This does not *create* a disclosure —
the OS clipboard is already world-readable by any focused app under Android's
clipboard model. Noted for completeness; no PersonaSpeak-side neutralization
applies beyond what ASK already does (copy-out only, no write-back of typed
text to the OS clip).

---

## 4. Verdict vs the README claim

The README's "Privacy, briefly" (`README.md:53-59`) says:

> Message text goes only to the provider *you* configured, only when you ask
> for a rewrite. **Nothing is stored, logged, or "used to improve our
> services."**

For the fork shipping ASK `1.13-r1` as the keyboard, this claim is
**needs-qualification, not flatly false** — and one specific sub-clause is
**false as-shipped**.

- **"Nothing is stored"** — *false as-shipped.* A predictive keyboard stores by
  design: `fallback.db` (learned words), `auto_dict_2.db` (auto-learned words),
  `next_words_<locale>.txt` (prediction model), all default-on (1.1–1.3). The
  honest restatement is already in ADR-0005: "it stays on the device and is
  user-clearable" — not "it does not exist."
- **"Nothing is logged"** — *provisionally holds in release; NOT confirmed.*
  Static reading shows `NullLogProvider` gating every `Logger.*` call and the
  in-memory ring buffer, with the only direct `Log.d` carrying a static string
  (3.1). But a source read cannot see logging from transitive AAR/JAR
  dependencies, native `.so` code, WebView, or reflection. Treat as provisional
  until the assembled **release APK** is inspected and the graft is held to the
  same rule — not signed off.
- **"Not used to improve our services"** — *provisionally holds; NOT confirmed,
  and this is the riskiest claim in the document.* No analytics / telemetry /
  crash-upload SDK appears in source or the dependency catalog, and crash
  reports are user-initiated email (2.2). But a source grep for named SDKs and
  Gradle plugins cannot catch **transitive or resolved runtime dependencies,
  shaded/renamed SDKs, `java.net`/raw sockets, WebView, DownloadManager or other
  platform services, dynamically loaded code, or native egress**. Asserting this
  clause requires inspecting the *resolved dependency graph* and the *decompiled
  release APK*, plus per-UID network capture (section 6). A false all-clear here
  is the worst possible outcome under ADR-0005, so it stays provisional until
  that evidence exists.
- **"Message text goes only to the provider you configured"** — *out of scope
  for this audit.* That half of the claim concerns PersonaSpeak's own
  provider path, which is *ours*, not ASK's. ASK has no provider path of its
  own; the verification ADR-0005 demands here is that the graft does not open
  one. This inventory cannot sign that off; it can only confirm that ASK
  itself does not.
- **One load-bearing addition the README does not make.** Android Auto Backup
  (2.1) makes the local state above *eligible for the device's configured backup
  transport* (commonly Google Drive, but the transport and its cloud/OEM
  behaviour vary and are outside app control), default-on, with no in-tree
  exclusion rule. That is not "used to improve our services" and it is not a
  server *we* control — but it is also not "nothing leaves your phone."
  ADR-0005's egress boundary is "anywhere in the app's UID," and eligibility for
  the backup transport qualifies. The claim as written does not account for this
  and must, before ship.

Headline verdict the PR body will carry: **"Nothing is stored" is false for the
fork as-shipped (a predictive keyboard stores by design). "Nothing is logged"
and "not used to improve our services" are *provisionally* clear from static
analysis but are NOT confirmed — a source read cannot see transitive/resolved
dependencies, shaded SDKs, native code, or runtime egress, so both require
release-APK inspection and per-UID network capture before they can be asserted.
Auto Backup makes local state eligible to leave the device by default. The
README's blanket sentence must be rewritten per ADR-0005 before the privacy copy
unfreezes.** On-device verification is the gating step (section 6), not a
formality.

---

## 5. Neutralization list

Default-on behaviours that must be turned off or configured before the fork
ships, so the eventual vendored copy's `UPSTREAM-MODIFIED.md` and the
privacy-copy rewrite know what to do. Each line becomes one entry in the
fork's modified-file manifest per ADR-0004.

1. **Auto Backup: exclude user-derived data.** As-shipped, `allowBackup="true"`
   plus the absence of any `fullBackupContent`/`dataExtractionRules` rule means
   the learned-words DBs, next-word files, and crash logs are eligible for the
   configured backup transport (2.1). Two Android-version regimes must **both**
   be covered, or the exclusion has a hole on part of the supported range:
   - **Android 12+ (`targetSdk` 35):** `android:dataExtractionRules` pointing at
     an XML with **separate** `<cloud-backup>` and `<device-transfer>` sections
     — a decision is owed for *each*; excluding cloud backup does not exclude
     device-to-device transfer.
   - **Android ≤ 11:** `android:fullBackupContent` pointing at a `<full-backup-content>`
     XML. Shipping only one of the two files leaves the other regime on defaults.
   - Exclusion paths are **not shell globs.** `<exclude>` matches a `domain` +
     an exact `path` prefix (directory or file), so `files/next_words_*` will not
     match — exclude the containing directory (`files/`) or enumerate exact
     filenames. Validate each rule against the actual on-device paths (section 6).
   - Heaviest alternative: `android:allowBackup="false"` outright (also disables
     settings backup, likely undesirable), and note it is not a universal
     device-transfer guarantee across all Android 12+ OEM implementations.

   Whichever is chosen, it is an upstream-tracked edit to
   `ime/app/src/main/AndroidManifest.xml` plus one or two new XML resources, and
   must appear in `UPSTREAM-MODIFIED.md`.
2. **Confirm the auto-dictionary threshold default.** ASK ships it at `9`
   (1.2). The fork should decide whether default-on auto-learning is part of
   the pitch. If not, change `settings_default_auto_dictionary_add_threshold`
   to `-1` (`ime/app/src/main/res/values/settings_defaults_dont_translate.xml:32`)
   and document it. If yes, leave it and surface the existing "off" UI option
   in onboarding.
3. **Confirm the fallback-user-dictionary default.** ASK ships
   `settings_default_always_use_fallback_user_dictionary = true` (1.1), which
   is the *more* private of its two modes (app-private DB vs. shared system
   `UserDictionary`). Recommend keeping, and stating the choice in the
   privacy copy. If kept, no edit; if changed, manifest entry.
4. **Strip or repoint hard-coded external URLs.** `VersionChangeLogs.java`
   (2.3) and `AboutAnySoftKeyboardFragment` link to AnySoftKeyboard's own
   GitHub milestone pages and (via `R.string.privacy_policy` / `main_site_url`)
   its website. At minimum the privacy URL must point at a PersonaSpeak page
   whose claims match the post-audit copy.
5. **Decide on the crash handler.** The `chewbacca` flow (2.2, 3.2) writes a
   crash file with stack trace + device info to private storage by default.
   Default-on is reasonable for a keyboard the user has to trust, but the
   fork should confirm `ANYSOFTKEYBOARD_CRASH_REPORT_EMAIL` is set to an
   address the fork owns (it is an env var at `ime/app/build.gradle:19-22`)
   and that the disclosure-surface write is acceptable to the privacy copy.
   If the fork ships no crash email at all, the notification path is dead
   but the file still writes — consider disabling
   `settings_default_show_chewbacca`.
6. **Lock in the logcat gate.** Do not regress the release-`NullLogProvider`
   contract (3.1) in the graft. Any PersonaSpeak code added under the app's
   UID must use `com.anysoftkeyboard.base.utils.Logger`, not direct
   `android.util.Log`, for anything that could touch draft text — and must
   not log draft text even through `Logger`. This is an enforced convention
   (code review + lint rule if added), not necessarily a manifest edit.

---

## 6. Open questions (on-device verification pending)

Per ADR-0005's verification bar, the items below are *not* closed by this
static pass. They are the on-device checklist.

1. **Auto Backup transport behaviour on a real device.** Confirm by factory
   backup (or `adb shell bmgr`) that the audited default rules actually back
   up `fallback.db` / `auto_dict_2.db` / `next_words_*.txt` / crash logs, and
   that the chosen neutralization (5.1) excludes them.
2. **Network capture — per-UID, not just a proxy.** No outbound HTTP client
   exists in source (2.2), but the proof is on-device and must use **per-UID
   packet capture / firewall evidence** (e.g. `pcap` filtered on the app's UID),
   not only a TLS-intercepting proxy — a proxy misses pinned TLS, raw sockets,
   DNS/UDP, native traffic, and platform-mediated transfers. Exercise cold
   start, typing, suggestion-accept, language-switch, addon install flow, and
   crash-trigger, and confirm zero unexpected egress. Pair it with inspection of
   the **resolved dependency graph** (`./gradlew :ime:app:dependencies`) and the
   **decompiled release APK**, since those are where a shaded/transitive network
   SDK would hide from source grep. (User-initiated `ACTION_VIEW` handoffs in 2.3
   appear as the browser's traffic, not the app's.)
7. **Addon / dictionary acquisition and `:ime:remote`.** Thinly covered by this
   static pass. Trace on-device whether addon-pack or dictionary acquisition
   uses `DownloadManager`, a background worker/service, a `WebView`, or a
   content provider, and whether any addon package runs under a **separate UID**
   (which would sit outside this audit's per-UID network capture). Confirm no
   typed text or learned data is carried on any such path.
8. **Clipboard listener lifecycle.** Confirm on-device *when* the
   `OnPrimaryClipChangedListener` (1.4) registers and unregisters, what "panel in
   use" means in practice, and whether the keyboard process's persistence makes
   the 15-entry in-memory buffer effectively long-lived across app switches
   rather than cleared promptly.
3. **JNI / native storage.** `:ime:gesturetyping` and the JNI dictionary
   modules ship `.so` files that static Java analysis cannot see. Confirm by
   storage inspection (`adb shell run-as` where debuggable, or filesystem
   dump) that no additional persistent file is written from native code.
4. **`android:allowBackup` vs. `android:fullBackupOnly` interaction on current
   Android.** The manifest sets both (1.x). Confirm the effective behaviour on
   the fork's `targetSdk` (35 per `gradle/libs.versions.toml`), since
   Android's backup semantics have shifted across SDK levels and the
   `dataExtractionRules` attribute is the modern control.
5. **Direct `android.util.Log` audit for the full forked tree.** This audit
   covers the pinned ASK snapshot only. The graft adds PersonaSpeak code;
   that code must be held to the same "no `android.util.Log` of draft text"
   rule (5.6).
6. **Contact dictionary.** `READ_CONTACTS` is declared
   (`ime/app/src/main/AndroidManifest.xml:18`) for an optional contacts
   dictionary (`settings_key_use_contacts_dictionary`,
   `ime/app/src/main/res/values/settings_keys_dont_translate.xml:120`).
   Whether it is default-off in *usage* (the permission is runtime-gated) is
   worth confirming on-device; it is not analytically default-on here.
