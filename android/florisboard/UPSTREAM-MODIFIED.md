# FlorisBoard upstream-modification ledger

This file lists every upstream-tracked file whose contents differ from the
pristine snapshot described in `UPSTREAM.md`. Use one entry per file:

```text
- <path-from-android/florisboard> — <reason for the current modification>
```

PersonaSpeak additions live under
`app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/floris/` and
`app/src/main/res/xml/personaspeak_host_*.xml` and are our own files, not
modifications. The pristine baseline additionally requires two host-level
facts recorded in `UPSTREAM.md`: a Rust toolchain (fatal CMake gate) and the
jetpref dependency repoint below — the pinned v0.5.2 tag cannot resolve its
own `20251119T222500Z-SNAPSHOT` jetpref pin because the Sonatype snapshot
was purged; stable `0.3.0` is API-compatible (verified by full build and
on-device run).

## Files modified against pristine

- gradle/libs.versions.toml — jetpref `20251119T222500Z-SNAPSHOT` → stable `0.3.0` (the snapshot is purged from Sonatype; the tag does not build without this). Plus the PersonaSpeak second-host alias block (`personaspeak-*` versions, `compose-*`/`coroutines-*`/`lifecycle-*`/`datastore-preferences`/`androidx-annotation`/`androidx-test-core`/`junit`/`kotlin-test`/`robolectric`/`snakeyaml` library aliases and `android-library`/`compose-compiler` plugin aliases) so the first-party modules included from `../` resolve their `libs.*` references under this root. Replay: re-apply the version swap and re-append the alias block.
- settings.gradle.kts — `personaspeakProject()` includes mapping the six first-party libraries (`../core-personas`, `../core-providers`, `../personaspeak-ui`, `../personaspeak-data`, `../personaspeak-providers`, `../personaspeak-ime`) into this Gradle root. No upstream module changed. Replay: re-append the include block.
- build.gradle.kts — `allprojects` redirect of the six first-party modules' build directories under `build/personaspeak-build/` so the ASK unified root and this root never share build output state. No upstream module affected. Replay: re-append the block.
- app/build.gradle.kts — `applicationId` reads the `personaspeakFlorisAppId` Gradle property (default stays `dev.patrickgold.florisboard`, so a property-less build is pristine-identical in identity); added `implementation(project(":personaspeak-ime"))`, `:personaspeak-ui`, `:core-personas`, `:core-providers`, `:personaspeak-providers` for the second-host layer; release signing reads the `PERSONASPEAK_FLORIS_RELEASE_*` env vars only (conditional `signingConfigs` block plus conditional `signingConfig` wiring in the release type — distinct names so the ASK release keystore can never sign a Floris build) and the release type appends the first-party-owned `proguard-personaspeak.pro` (snakeyaml's JVM-only `java.beans` references) to the upstream proguard files. Replay: re-apply the property read, the five dependency lines, the signing block, and the one proguardFiles entry.
- app/src/main/AndroidManifest.xml — added `<uses-permission android:name="android.permission.INTERNET"/>` (remote providers on opt-in; mirrors the ASK host's M5 change), repointed `android:dataExtractionRules`/`android:fullBackupContent` to the merged `personaspeak_host_*` rules (FlorisBoard's includes + PersonaSpeak's provider-credential exclusions), and registered the non-exported `FlorisPersonaSpeakSettingsActivity`. No upstream component changed. Replay: re-apply the three edits.
- app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt — one `by lazy` field `personaspeakHost` (lazy because the service context is not attached at construction; the graph resolves an application context) plus five one-line forwarders (`onStartInput`, `onStartInputView`, `onUpdateSelection`, `onFinishInput`, `onDestroy`) into the PersonaSpeak host glue. All upstream bodies unchanged. Replay: re-add the field and the five forwarder lines.
- app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/TextInputLayout.kt — one guarded insertion between `Smartbar()` and the keyboard `Box`: `(context as? FlorisImeService)?.personaspeakHost?.let { FlorisPersonaSpeakRow(it) }` plus its two imports. Fail-closed: without the host the row is absent, never a crash. This is the ADR-0007 dedicated-row contract (host suggestions and keys stay visible in every PersonaSpeak state). Replay: re-apply the insertion and imports.
- app/src/main/res/values/strings.xml — one string, `ext__update_box__internet_permission_hint`: upstream tells users the app "does not have Internet permission," which is false in this build (the PersonaSpeak layer adds `INTERNET` for opt-in provider calls). The string now states the true, permission-independent fact: extension updates are always checked manually in the browser. Privacy-copy alignment per the second-host audit (ADR-0005/0009 disclosure rules). Replay: re-apply the one-string edit.
