# FlorisBoard Second Host — Privacy, Network-Egress & Backup-Exclusion Audit

**Document Status: QUALIFIED (single-agent execution; owner verdict pending — loud skip, see §7)**  
**Scope:** The FlorisBoard evaluation host (ADR-0010), ASK-M7 audit bar  
**Run ID:** `20260903T170746Z` (device journey) + static audit of the vendored tree @ `docs/evidence` branch  
**Upstream:** FlorisBoard v0.5.2 @ `2e82060` (vendored, `android/florisboard/`)

---

## 1. Executive Summary

The FlorisBoard host's privacy posture is the PersonaSpeak layer's posture (identical shared modules, re-audited by reference and spot-check) **plus** the vendored FlorisBoard host surface. The vendored keyboard code itself performs **zero direct network I/O**: no HTTP client exists anywhere in the tree, no crash reporting, no telemetry. Every egress path is either (a) the PersonaSpeak provider layer's explicit opt-in rewrite/catalog calls — the same pinned-endpoint surface the ASK host's M7 audit qualified — (b) user-tapped browser-delegated links (addon store, docs), or (c) one automatic, library-mediated path inherited from upstream: **EmojiCompat metadata may load from Google Play Services at startup on GMS devices**, reachable because the PersonaSpeak layer added the `INTERNET` permission. That last item is the honest delta versus the ASK host and is disclosed in §2.3.

## 2. Network Egress

### 2.1 The shared PersonaSpeak layer (by reference, ASK M7 §2)

Typing, keystroke, dictionary, and persona-catalog paths perform zero network calls (`InputConnectionEditorPort`, `BundledPersonaRepository` asset reads, `FakeProvider` offline fallback — unchanged from the M7 audit, same code). Opt-in egress remains exactly two actions: **Rewrite** (`RewritePanelViewModel.request()`, pinned `https://openrouter.ai/api/v1/chat/completions` / `https://api.anthropic.com/v1/messages`, HTTPS-only, redirects disabled) and **Browse models** (`OpenRouterModels.fetch()`, pinned `https://openrouter.ai/api/v1/models`, no credentials, no user text). Both are wired in the Floris host only through `FlorisPersonaSpeakHost` / `FlorisPersonaSpeakSettingsActivity` glue; the HTTP code is the same first-party module the ASK audit covered.

### 2.2 The vendored FlorisBoard surface (this audit's new work)

- **No in-app HTTP client.** Grep across `app/src`, `lib`, `libnative`, `utils` for `HttpURLConnection|OkHttp|ktor|Volley|HttpClient|openConnection|Socket`: zero hits. The only `java.net` use is `URI` parsing for theme references (no connection opened).
- **Addon store links are browser-delegated and user-tapped.** `beta.addons.florisboard.org` appears in `generateUpdateUrl()` (`lib/ext/Extension.kt:125-148`) and the store link (`app/ext/AddonBox.kt:65,100`); both open via `ACTION_VIEW` in the system browser on an explicit button tap. Data carried: extension IDs + versions, in a URL fragment. No automatic or background update check exists.
- **Crash reporting: none.** No Sentry/Firebase/Crashlytics/ACRA/analytics. The in-house `CrashUtility` writes stacktraces to internal storage and can copy them to the clipboard for manual sharing; there is no upload path.
- **Language/NLP: fully offline.** The native Rust crate (`lib/native/src/main/rust/Cargo.toml`) has no network dependencies; providers are local (`LatinLanguageProvider`, `HanShapeBasedLanguageProvider`, clipboard/emoji suggesters). Language packs import manually via SAF; there are no downloads.

### 2.3 The one automatic path: EmojiCompat (disclosed, inherited from upstream)

`FlorisApplication` initializes EmojiCompat eagerly (`FlorisEmojiCompat.kt:58-67,108` via `DefaultEmojiCompatConfig.create`). On GMS devices this resolves to Google Play Services' downloadable-font provider and **can fetch emoji font metadata at startup** — no user action, no user data beyond the library's own request, library-mediated. Upstream FlorisBoard 0.5.2 ships without `INTERNET`, so upstream never hits this; the PersonaSpeak layer's `INTERNET` addition (manifest line 8, opt-in provider calls) makes it reachable in this build. The M2_Qual_Fixture is a GMS image, so this path is live on the evaluation fixture. **Decision deferred to the owner:** disable the eager load (an upstream-behavior change, rent) or disclose and keep. This audit discloses; it does not decide.

### 2.4 Egress verdict

Every direct network byte leaving this app either (a) rides the PersonaSpeak provider layer's opt-in, pinned-endpoint calls audited at M7, or (b) is the EmojiCompat library fetch of §2.3. The vendored keyboard code adds no egress of its own.

## 3. Storage & Backup Exclusion

The merged `personaspeak_host_*` rules (P3 posture, `verify-floris-release.sh` invariant 5) keep the PersonaSpeak allowlist-plus-excludes shape: cloud-backup and device-transfer include only `jetpref_datastore`, `ime`, `floris_user_dictionary`, and **explicitly exclude** `personaspeak_secret.bin`, `personaspeak_secret.bin.staging`, and `datastore/personaspeak_provider_config.preferences_pb` — the deliberate-redundancy excludes carry `tools:ignore="FullBackupContent"` with the rationale in-file. Credential ciphertext without its non-exportable AndroidKeyStore key is undecryptable off-device; exclusion is the privacy story and the honest behavior.

**Clipboard history** (host surface): local-only Room DB + `noBackupFilesDir` media files; **not** in the backup allowlist. History and internal clipboard default **off**; sensitive-clip flagging honored; cleanup loop every 60 s enforces limits (`AppPrefs.kt:85-149`). No sync leaves the device — "sync" is on-device between FlorisBoard's internal clipboard and the system clipboard only.

## 4. Permissions

| Permission | Why | Origin |
| :--- | :--- | :--- |
| `VIBRATE` | key-press haptics | upstream |
| `INTERNET` | PersonaSpeak opt-in provider calls (§2.1) | PersonaSpeak addition (ledgered) |
| `POST_NOTIFICATIONS` | crash-utility channel | upstream |

No location, contacts, storage, microphone, or camera permissions.

## 5. Logging Hygiene

All debug logging funnels through `Flog`, installed with `isFloggingEnabled = BuildConfig.DEBUG` — **every log call is a no-op in release builds**. In debug builds only, the Han shape provider logs composing text and the Latin provider logs suggestion candidates (`HanShapeBasedLanguageProvider.kt:182`, `LatinLanguageProvider.kt:126-135`), and `TextKeyboardLayout.kt:417` logs MotionEvents. The evaluation builds are debug-signed — this is disclosed rather than hidden: the qualification journey (run `20260903T170746Z`) ran on a debug build, as does every ASK-host journey; release builds log nothing. Keystroke content is never written to disk by the logging layer in any build (file logging is unimplemented upstream).

## 6. On-Device Evidence

- Device journey `20260903T170746Z` (P2): 180 steps, zero non-completed, editor-text bridge proving the rewrite/apply path on the fixture; evidence branch `evidence` @ `f7ff38f`, path `floris-journey/20260903T170746Z/`.
- ADR-0003 composing instrumentation: 2/2 green on the same fixture (receipt `androidTest/results.xml` on the evidence branch).
- Backup-exclusion posture mechanically pinned by `verify-floris-release.sh` (CI-wired since P3).

## 7. Non-Author Review

Non-author review skipped: no non-author agent available at execution time (owner AFK, single-agent run). Owner review pending. The EmojiCompat disclosure (§2.3) and the update-hint copy fix (§8) are the two items most worth an owner's eyes.

## 8. Privacy-Copy Alignment Fix (this PR)

The upstream update screen told users "Since this app does not have Internet permission, updates for installed extensions must be checked manually" (`ext__update_box__internet_permission_hint`, rendered in `AddonBox.kt:90`). In this build that first clause is **false** — the PersonaSpeak layer added `INTERNET`. The string now states the true thing (extension updates are checked manually via the browser; the keyboard never auto-updates extensions), removing the false permission claim. Ledgered as one line of rent.
