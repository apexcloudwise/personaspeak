# Patch Notes

Every merged PR gets an entry here, written the way patch notes should be:
every line true, every line deadpan, delivered in the register of
[VOICE.md](VOICE.md). Strip the jokes and you can still reconstruct the
changelog; that's the contract. The PR author writes the line, in the PR,
while the context is hot.

Newest first, like all respectable patch notes.

## 2026-08-27 — The Air-Gap & Vault: Release privacy, network egress, and backup-exclusion audit

- Shipped Milestone 7 Slice B (Release Privacy, Network Egress & Backup-Exclusion Audit + Non-Author Verdict):
  - Published comprehensive privacy & egress audit document (`docs/evidence/milestone-7/privacy-and-egress-audit.md`) and machine receipt (`docs/evidence/milestone-7/privacy-audit-receipt.json`).
  - Implemented `ReleasePrivacyAndEgressAuditTest` in `:ime:app` verifying critical privacy invariants:
    - Zero Keystroke / Typing Egress: Standard typing, dictionary lookups, persona selection, and session operations execute with 0 network operations.
    - Opt-in Network Egress Boundaries: Network egress occurs strictly upon explicit user rewrite or "Browse models" catalog fetch over pinned HTTPS endpoints (`https://openrouter.ai/api/v1/chat/completions`, `https://api.anthropic.com/v1/messages`, `https://openrouter.ai/api/v1/models`).
    - Storage & Backup Exclusions: Verified that `personaspeak_data_extraction_rules.xml` and `personaspeak_full_backup_content.xml` explicitly exclude `personaspeak_secret.bin`, `personaspeak_secret.bin.staging`, and `datastore/personaspeak_provider_config.preferences_pb`.
    - Memory Hygiene: Verified `SecretBytes` zeroing (`secret.value.fill(0)`) inside `finally` blocks across all adapter paths on both success and failure outcomes.
    - Privacy Copy Alignment: Verified that user-facing privacy notices in Settings, Onboarding, and Readme match runtime implementation per ADR-0005/ADR-0009.
  - Updated `android/scripts/verify-milestone-7.sh` and `verify-milestone-7-test.sh` to enforce Slice B audit invariants and unblock Milestone 8. (#112)

---

## 2026-08-27 — The Fresh Start: Fresh-install harness suite, RTL layout pass, and machine receipts

- Shipped Milestone 7 Slice A (Fresh-Install JVM Integration Harness & RTL / Visual Fidelity):
  - Authored the Milestone 7 plan (`docs/plans/m7-fresh-install-journey-and-release-audit-plan.md`) structuring M7 into fresh-install journey proof (Slice A) and release privacy/network/backup audit (Slice B).
  - Implemented `FreshInstallJourneyIntegrationTest` in `:ime:app` exercising the full end-to-end user lifecycle on a pristine fresh-install simulation harness:
    - Step 1: Pristine baseline with empty DataStore, zero disk persistence, default Jeeves (🎩), and unconfigured fallback to `FakeProvider`.
    - Step 2-3: Onboarding presentation, character picking (Dr. King Schultz 🎯 + Witty mood), and Brain provider setup (`openrouter` model configuration saved to DataStore and Keystore).
    - Step 4-5: Full keyboard InputView start on host app editor (`"Tea at six."`), rewrite evaluation, candidate review, and guarded `Use this` commit with exactly 1 verified text mutation.
    - Step 6: `Dismiss` review action leaving editor text unmodified with exactly 0 mutations.
    - Step 7: Carried-forward RTL locale layout pass verifying `LayoutDirection.Rtl`, `start`/`end` alignment, and back button mirroring.
    - Step 8: Visual theme contrast token verification in `PersonaSpeakTheme` across light and dark palettes.
  - Published Milestone 7 evidence documentation (`docs/evidence/milestone-7/README.md`) qualifying source/harness level qualification with live emulator device qualification pending.
  - Recorded machine-derived receipt (`docs/evidence/milestone-7/journey-receipt.json`) bound to commit and run ID with honest `harness_verified` step statuses.
  - Added automated verifier `android/scripts/verify-milestone-7.sh` and contract test suite `android/scripts/tests/verify-milestone-7-test.sh`. (#109)

---

## 2026-08-27 — The Polish & Reach: Dark mode, landscape, accessibility, and RTL fidelity

- Shipped Milestone 6 Slice B (Visual Fidelity, Theme, Accessibility, Landscape, & RTL Readiness):
  - Created Material 3 `PersonaSpeakTheme` with high-contrast dark and light palettes (`Color.kt`, `Theme.kt`) satisfying Stitch tokens.
  - Implemented high-contrast borders and elevated surfaces on persona chips, mood chips, picker tiles, and result cards to resolve dark-mode contrast.
  - Hardened accessibility across all interactive components: enforced Android's 48dp minimum touch target floor and added semantic `contentDescription` attributes to close buttons, back buttons, chips, radio buttons, and action icons.
  - Enhanced layout flexibility and resilience: resting persona chips now gracefully truncate long names ("Sir Humphrey Appleby", "Amitabh Bachchan") without squeezing action buttons; candidate review body obeys landscape pre-expansion height constraints and scrolls smoothly.
  - Added RTL layout support (`android:supportsRtl="true"`) to `AndroidManifest.xml` and ledgered in `UPSTREAM-MODIFIED.md`.
  - Expanded unit test coverage across `RewritePanelTest` and `SettingsScreenTest` covering landscape geometries, long name truncation, 48dp touch floors, and accessibility semantics. (#106)

---

## 2026-08-27 — The Paper Trail: Asset rights, font licenses, and portrait provenance cleared

- Shipped Milestone 6 Slice A (Asset Rights & Licensing):
  - Authored the Milestone 6 plan (`docs/plans/m6-visual-fidelity-and-asset-rights-plan.md`) covering asset rights (Slice A) and visual/reach fidelity across dark mode, landscape, accessibility, and RTL (Slice B).
  - Established the authoritative Asset Rights & Provenance Manifest (`docs/design/ASSET-RIGHTS.md`) fulfilling legal gates for Milestone 6 of #38.
  - Documented font licensing under SIL Open Font License 1.1 for Outfit and Inter typography specifications, recording explicit system font fallback behavior when unbundled.
  - Formally cleared persona portrait representations across all four bundled characters (`jeeves`, `sir-humphrey`, `dr-schultz`, `amitabh-bachchan`) under the rights-cleared Unicode emoji standard ("🎩", "🏛️", "🎯", "🎬"), recording explicit exclusion of un-cleared third-party broadcast/film stills and living actor likenesses.
  - Implemented fail-closed automated verification (`android/scripts/verify-asset-rights.sh`) and contract test suite (`android/scripts/tests/verify-asset-rights-test.sh`) enforcing 100% manifest coverage, zero unauthorized first-party raster assets, and complete license notices. (#106)

---

## 2026-08-27 — The Thinking Keyboard: The Brain settings, model browser, and runtime wiring

- Shipped Milestone 5 Slice B (The Brain Settings, Searchable Model Catalog, Onboarding, & Runtime Provider Resolution):
  - Built "The Brain" settings screen (`ProviderSetupScreen.kt`) with provider radio selector (OpenRouter, Claude, OpenAI-compatible), obfuscated API key entry, model selection, custom base-URL support, and clear Key URL links.
  - Added searchable `OpenRouterModelPickerDialog` querying OpenRouter's live public catalog via `OpenRouterModels.fetch()` with search filter and "FREE" badges.
  - Landed the "Get started" onboarding card on Settings Home guiding users through enabling PersonaBoard in system settings, persona selection, and brain connection.
  - Implemented `ResolvingProvider` in `:ime:app` resolving active provider credentials from Keystore/DataStore on input view start with clean fallback to `FakeProvider` when unconfigured or broken.
  - Promoted `:personaspeak-data` to `implementation` in `:ime:app/build.gradle` and ledgered in `UPSTREAM-MODIFIED.md`.
  - Maintained zero-leak memory invariants and 48dp minimum touch target floor across all newly added UI surfaces. (#103)

---

## 2026-08-27 — The Multi-Brain Bridge: ADR-0009 & OpenRouter adapter foundation lands

- Shipped Milestone 5 Slice A (Kickoff & Provider Adapter Foundation):
  - Authored ADR-0009 ("Pluggable Multi-Provider Architecture and OpenRouter Evaluation") establishing the multi-provider interface standard, configuration schema in DataStore/Keystore, custom base-URL data classification, proxy egress boundaries, and default-disabled invariants.
  - Landed the Milestone 5 plan (`docs/plans/m5-onboarding-settings-openrouter-plan.md`) structuring M5 into two accelerated slices (adapter foundation, followed by Settings UI and onboarding).
  - Implemented `OpenRouterAdapter` and `OpenRouterModels` in `:personaspeak-providers` with pure Kotlin, zero-dependency `MiniJson` payload extraction, and immediate memory zeroing (`SecretBytes.value.fill(0)`).
  - Added comprehensive contract tests for OpenRouter verifying 200 OK text extraction, 401/403 `AuthFailure` mapping, 429 client error mapping, 500/502/503 server error mapping, timeouts, and pinned HTTPS egress.
  - Added `<uses-permission android:name="android.permission.INTERNET" />` to `AndroidManifest.xml` and ledgered the modification in `android/keyboard/UPSTREAM-MODIFIED.md`.
  - Default-disabled baseline preserved: `FakeProvider` remains the active default in composition; remote egress strictly gated on Milestone 5 Settings opt-in. (#103)

---

## 2026-08-25 — The Provider Exploration: non-Anthropic options scouted on paper

- Shipped the plan-only feasibility assessment and contract comparison for non-Anthropic provider options (OpenRouter, Z.AI) alongside the existing Anthropic scaffolding:
  - Evaluated four paths: retaining disabled Anthropic scaffolding (with live qualification deferred), adding a separately disabled OpenRouter adapter, adding a separately disabled Z.AI adapter, and bounded deferral to Milestone 5.
  - Formulated the bounded recommendation: stay with the disabled Anthropic baseline for Milestone 4; prioritize OpenRouter as the secondary candidate if multi-model access is mandated, routed behind a dedicated ADR (ADR-0009).
  - Enforced the mock-only human constraint: zero real credentials, zero live network egress, and strict preservation of Milestone 4 closeout issues #96 and #89.
- Plan-only PR: zero production code, zero network calls, zero keystore modifications. (#101)

---

## 2026-08-25 — The Fixture Blueprint: immutable-provenance device plan lands

- The M4 device qualification fixture plan establishes the tamper-evident execution
  protocol for closing out Milestone 4 on a dedicated external fixture:
  - Literal verification procedures for API-27 legacy `fullBackupContent` exclusion
    via `bmgr` (with restore token discovery), Mode-A offline ART response-parser
    validation on `PersonaspeakAdapterHarnessActivity`, and Mode-B live egress
    smoke testing with concurrent socket sampling of `/proc/net/tcp` against
    `api.anthropic.com:443`.
  - Capture-time immutable provenance requirements: host toolchain versions, device
    fingerprints, application and APK SHA-256 digests, and unredacted command streams
    committed to the append-only `evidence` branch.
  - Ephemeral Mode-B credential authority and strict injection boundaries: no secret
    in source, shell history, intents, logs, screenshots, or retained evidence; immediate
    memory zeroing and cloud revocation verification.
  - Unconditional fail-closed governance: unverified runs leave the provider structurally
    disabled (`FakeProvider` default).
- Plan-only PR: no device execution, no receipts created, no provider enablement,
  no changes to deterministic CI gate verifiers. (#99)

---

## 2026-08-25 — The Brain advances: source contracts and compile gates active

- Shipped Milestone 4 slice 3 verification scaffolding, debug harnesses, and CI gates:
  - Upgraded `PersonaspeakStorageHarnessActivity.ACTION_SEED` to generate 32 on-device
    cryptographically secure random bytes via `SecureRandom`, removing literal seed strings
    and ledgering the change in `android/keyboard/UPSTREAM-MODIFIED.md`.
  - Configured API 26/27 legacy backup-exclusion rules under `fullBackupContent`
    to exclude AES-GCM ciphertext and DataStore metadata (device qualification pending).
  - Added debug-only `PersonaspeakAdapterHarnessActivity` with deterministic memory
    zeroing assertions and wired `verify-milestone-4.sh` compile check into CI.
  - Refined §10 key-String security checklist and source documentation.
  - Recorded structural default-disabled governance in ROADMAP.md and ADR-0005: `FakeProvider`
    remains active in rewrite coordinator; cloud egress strictly gated on Milestone 5
    settings opt-in. Mode B live cloud egress qualification remains pending external fixture execution. (#96)

---

## 2026-08-25 — The Brain prepares for graduation: Milestone 4 closeout plan lands

- The M4 slice-3 plan establishes the verification protocol and closeout path for
  Milestone 4: behavioral API 26/27 legacy backup-exclusion pass via `bmgr` and the
  merged debug harness (`...data.harness.SEED/QUERY/CLEAR/CANARY`) upgraded to
  on-device `SecureRandom` byte generation, disposable-device Anthropic
  response-parser journey across offline and live transport modes (with Mode B
  mandatory for closeout egress evidence), package-private storage and socket-level
  egress audit receipts, and the formal §10 key-String checklist resolution.
- Anthropic adapter remains structurally disabled by default at merge; cloud egress is
  explicitly gated on user opt-in in Milestone 5.
- Plan-only PR: no production code, no network calls, no keystore modifications. (#96)

---

## 2026-08-25 — The Brain talks to Claude: Anthropic Messages API adapter lands

- Shipped `:personaspeak-providers`, the M4 slice-2 provider adapter foundation:
  `ProviderAdapter` interface and `AnthropicMessagesAdapter` targeting the
  Anthropic Messages API (`https://api.anthropic.com/v1/messages`) via
  `x-api-key` and `anthropic-version: 2023-06-01` headers.
- Implemented closed `NetworkErrorCode` taxonomy (`TIMEOUT`, `IO_ERROR`,
  `HTTP_SERVER_ERROR`, `HTTP_CLIENT_ERROR`) ensuring zero raw `Throwable`
  instances cross the adapter boundary.
- Enforced defense-in-depth secret hygiene: `SecretBytes` underlying `ByteArray`
  is zeroed in memory immediately upon request completion.
- Connected truthful runtime state observation to `SettingsViewModel` via
  `SettingsState.lastRewriteResult: AdapterResult?` (A4): request-time auth
  rejections and network failures never wipe keystore artifacts, keeping
  `StoreOutcome.InvalidCredentials`'s wipe postcondition intact.
- Ledgered `android/keyboard/ime/app/build.gradle` dependency in `UPSTREAM-MODIFIED.md`
  and verified exact ASK closure with `verify-ask-closure.sh`.
- Default-disabled baseline preserved: `FakeProvider` remains active in the rewrite
  panel; adapter enablement awaits slice-3 device verification. (#93)

---

## 2026-08-25 — The Brain gets its first real provider: Anthropic adapter plan lands


- The M4 slice-2 plan establishes the Anthropic Messages API adapter behind the
  existing `:personaspeak-data` persistence seam: `ProviderAdapter` contract with
  a closed `NetworkErrorCode` (no `Throwable` escapes the boundary), concrete
  Anthropic `x-api-key` + `anthropic-version: 2023-06-01` header scheme, and
  a dedicated `lastRewriteResult: AdapterResult?` observation field in
  `SettingsState` (A4) so `StoreOutcome.InvalidCredentials` keeps its wipe
  postcondition inviolate while transient request errors never wipe storage.
- Egress strictly bound to `https://api.anthropic.com/v1/messages`; data
  classification corrected to state draft text leaves the device in the request
  body to that endpoint alone.
- `ProviderStatus` sealed interface rejected; `StoreOutcome` used directly for storage.
- `keyboard/ime/app/build.gradle` is ASK-owned; the plan requires a `UPSTREAM-MODIFIED.md`
  ledger entry for the `:personaspeak-providers` dependency.
- Plan-only PR: no production adapter, no network calls, no live provider. (#93)

---

## 2026-08-24 — The Brain gets a vault: provider credentials move into the Keystore

- Shipped `:personaspeak-data`, the M4 slice-1 storage foundation: Preferences
  DataStore for non-secret config, an AndroidKeyStore AES-256-GCM ciphertext
  file for credentials, bound by a generation UUID and saved stage/commit/swap
  so a mid-save crash always leaves either the old or the new credential —
  never nothing.
- Wired backup exclusion for both artifacts (plus the staging twin) into the
  app manifest under both regimes; restored ciphertext would be undecryptable
  off-device anyway, so the rules make the privacy story literal.
- THE BRAIN's honest "arrives in Milestone 4" sign stays up: no configuration
  UI and no network this slice — the store ships with its full recovery-matrix
  test suite and a debug-only harness activity for device verification. (#90)

---

## 2026-08-24 — Settings stops dangling: Persona browser lands and the Brain stays honest

- Shipped `PersonaSpeakSettingsActivity` in `:ime:app` and the first-party settings
  surface in `:personaspeak-ui`: Settings Home (`settings/home`), full Persona
  Browser library (`settings/personas`), and per-character dossiers (`settings/personas/{personaId}`).
- Wired the strip's Settings button, the Persona Picker's `+ Browse all characters`
  action, and the typed error card `Open settings` affordances to launch the real
  same-package settings activity with deep-link intent routing.
- Grouped settings into CHARACTERS (character browser, default mood selector,
  and fixed "review before replacing" notice), THE BRAIN (disabled-but-honest
  FakeProvider baseline stating cloud providers and Keystore arrive in M4), and
  TYPING (direct link to inherited AnySoftKeyboard settings).
- Wired `PersonaSpeakSessionState` in-memory singleton handoff between the Settings
  Activity and the IME strip, ensuring active persona and default mood selections
  take effect on the next keyboard initialization in the session with zero disk writes
  and comprehensive integration test coverage.
- Kept the hard boundaries intact: zero persistence shims or storage writes (M4),
  zero onboarding graph creep (M5), and zero programmatic IME switching in the "Try
  on keyboard" guidance card.

---

## 2026-08-24 — The strip gets its wardrobe: real states, character tiles, and mood chips

- Replaced the placeholder "Rewrite" button with the full dedicated-row state
  machine in `:personaspeak-ui`: Resting chips (emoji + name + mood), in-row
  Persona Picker grid, Mood Picker, Review with `↻ Again` fresh re-capture,
  `Use this` guarded apply, and the complete 14-state typed error card family.
- Plumbed the product-owned Mood catalog (`polite`, `witty`, `blunt`,
  `apologetic`, `formal`) and prompt modifiers through `core-personas` and
  `RewriteCoordinator` while keeping golden prompt tests byte-identical when
  unmodified.
- Enforced Android's 48dp minimum interactive touch target floor across all
  chips, buttons, tiles, and close controls; pinned result body scroll bounding
  to the frozen `min(320dp, 40% pre-expansion height)` cap.

---

## 2026-08-24 — CI supply chain gets adult supervision

- Added the official verified SHA-256 (`distributionSha256Sum`) to
  `android/gradle/wrapper/gradle-wrapper.properties` for Gradle 9.2.1-all, so
  the wrapper actually checks what it downloads instead of trusting the wire.
- Hardened the Android CI job's token posture to explicit `permissions:
  contents: read` with `persist-credentials: false` on checkout.
- Bumped GitHub Actions (`actions/checkout` v5, `actions/setup-python` v6,
  `actions/setup-java` v5, `actions/cache` v5, `actions/upload-artifact` v6)
  to Node 24 majors, banishing the Node 20 deprecation calendar invitations
  from our CI logs.

---

## 2026-08-23 — The roadmap learns what happened

- ROADMAP.md stopped describing milestone 2 as awaiting qualification;
  the qualification receipt is now cited where the caveat used to be.
  Documentation's least glamorous job, done anyway: the map now
  matches the territory.

---

## 2026-08-23 — Milestone 2 qualified on real hardware

- The unified PersonaSpeak build has, at last, been formally qualified
  on the pinned emulator fixture: 145 steps, four editor sessions,
  every journey path, exact mutation counts, restoration verified. The
  receipt is in docs/evidence/milestone-2/; the media and logs are on
  the append-only evidence branch, where nothing can be quietly
  revised. Milestone 2 is done. The keyboard works; the paperwork now
  agrees.

---

## 2026-08-23 — The dry-run findings, shipped properly

- The diagnostic dry-run (#82) enumerated every way the qualification
  instrument disagreed with reality; this PR is that inventory, landed.
  The keyboard's x-row was one key too far left, the editor never
  auto-capitalized (our fake did, which was flattering and useless),
  one BACK closes a keyboard rather than a screen, and a restoring
  device answers its first health checks the way anyone mid-reboot
  does: silence. All fixed, all live-proven by the first complete
  end-to-end journey in M2 history.
- The emulator now leaves its engine log in the evidence, on the
  principle that a silent crash is a rumor and a log is a fact.
- Emulator attach and restore settle budgets grew from 30s to
  host-tolerance bounds, because a busy machine is slow, not broken.
- New `--headless` diagnostic flag for seats without a GUI, per the
  owner's ruling. The counted qualification stays windowed; a
  qualification you cannot see is a different qualification.

---

## 2026-08-20 — The instrument learns how a real device phrases things

- Qualification attempt 1 under the replacement (run 20260819T203941Z)
  got further than any run before it — IME enabled and selected, editor
  focused, binding proven live — and then failed on two defects that
  only real hardware could expose, both since pinned by an
  owner-authorized probe. First: the real API-34 dumpsys window output
  has no mFrame line at all; the keyboard's visible geometry lives in
  the touchable region, which is where the parser now reads it (compact
  top 1378 exact, Review expands it to 1283 — the review signal, in the
  device's own words). Failed window checks now carry the raw block in
  the record, so the next format drift is diagnosable without another
  probe. Second: the emulator 36.x console moved the snapshot family
  under avd, and it announces a rejected load with a KO line while
  reporting success on the exit-code channel; restore now speaks the
  avd grammar and believes stdout over returncode. The fake toolkit
  mirrors both real formats, rejects the dead command form exactly as
  the real console does, and the fake's Review expansion moved to the
  real 1283. Five new regressions pin the parser to the probe's actual
  bytes. The console's parting gift remains on record: it lied about
  the exit code, so now nothing trusts it.

---

## 2026-08-19 — The journey-facts layer, rebuilt on channels that exist

- The overseer-approved replacement (#79, PR #80) is implemented. The
  journey now enables and selects the IME after install (both
  fail-closed), proves binding through dumpsys input_method and the
  InputMethod window through dumpsys window, and reads every typed and
  applied character from the Settings search editor's own hierarchy
  node — a channel that works on real hardware, unlike the hierarchy
  keyboard facts it replaces. Key geometry is recalibrated and pinned
  against the actual layout: letter rows sit ~400px lower than the old
  pins assumed, which is how an eleven-tap sentence once wandered into
  Google Assistant settings. Panel taps (Rewrite, Use this, Dismiss)
  are pinned too, each verified through the editor-text bridge; the
  candidate surface itself stays screenshot-bound for the owner's eyes,
  with the window frame's growth as the machine-visible half of the
  review signal. verify_restore now asserts pristine facts (identity,
  IME baseline, package absence, search screen gone) instead of
  demanding a journey-time editor survive a snapshot restore. The fake
  adb stopped conjuring keyboard nodes into hierarchies and learned the
  new channels honestly, failure knobs included; the matrix and both
  exact goldens were regenerated from what the fake actually does.
  Suite 347/347 twice; budgets 1029/1100 and 2647/2700, limits
  unchanged.

---

## 2026-08-19 — The journey learns to watch what the device actually shows

- After two counted instrument failures, the overseer approved replacing
  the journey-facts layer outright (#79): uiautomator cannot observe an
  IME window on this Android, the fakes had been politely pretending it
  could, and the stored tap geometry matched a keyboard that exists
  nowhere. The replacement design note lands first — enable and select
  the IME for real, read binding and visibility from dumpsys, use the
  host editor's own node as the behavioral bridge for typed and applied
  text, recalibrate geometry against the actual layout, and let
  screenshots plus the owner's eyes carry the candidate surface. The
  proven outer instrument is untouched; the count resets narrowly and
  honestly for the replacement layer.

---

## 2026-08-19 — Absence acquires an exit code the harness can respect

- The first real qualification attempt stopped itself at
  prior_state_unavailable, and the reason was almost philosophical: pm
  path for a missing package exits 1 on a real device, and the pristine
  fixture's entire point is that the package is missing. The harness
  read that exit code as "cannot determine state" — correct instinct,
  wrong target. Absence (exit 1, no output) is now recognized as the
  pristine state it is; exit 1 WITH output remains unknown and still
  stops the run. The fake adb used to exit 0 here, which is how 342
  green tests certified a harness that could never boot its own
  fixture; it now lies no more. Regressions ride along, and the
  acceptance matrix documents its one honest nonzero exit. Review round
  2 caught the present-package path reading a variable that no longer
  existed — absence had been taught so well that presence forgot its
  own name — so a dirty-fixture run would have crashed instead of
  hashing the installed APK. Both paths now have their regression.

---

## 2026-08-19 — The fourth tool finally signs the guest book

- The signer gate gained an apksigner in PR #76's second round, but the
  capture record's tool identities never learned its name — the run
  could prove the APK's certificate while staying mum about which
  binary vouched for it. The recorded tool set now includes apksigner
  alongside adb and emulator, so a qualification record names every
  tool whose verdict it carries.

---

## 2026-08-19 — The pins meet the actual fixture and admit they'd never met before

- Seven instrument constants were fake-echo values — strings the test
  fakes printed at the harness, which then pinned its own echo. The
  2026-08-19 capability probe against the real pinned fixture replaced
  every one of them with receipt- or device-derived facts: the emulator
  pin is 36.6.11 (the version that demonstrably loads the pinned
  snapshot), the launch argv gains `-gpu swiftshader_indirect` (without
  it the 36.x default renderer refuses the snapshot and the emulator
  cold-boots into the wrong state), the density probe reads
  `qemu.sf.lcd_density` because `ro.sf.lcd_density` is empty on this
  image, `versionName` is 1.13.1 (the vendored keyboard's numbering,
  not a PersonaSpeak release number), the signer pin is the real
  on-device PackageSignatures digest, the enabled-IME baseline is the
  receipt's list (Gboard plus the Google TTS voice service), and the
  fixture transaction hashes `hardware.ini` where the receipt says it
  lives — inside the snapshot, not at the AVD top level. Fakes, tests,
  and both exact goldens updated to match reality; the journey is now
  runnable on the actual fixture instead of merely on its admirers.
- Review round 2 caught the signer pin admiring itself: the dumpsys
  digest is a 32-bit hash, collision-trivial and checked as a loose
  substring. The signer gate is now cryptographic — the canonical APK's
  certificate SHA-256, pinned from apksigner verify --print-certs and
  compared as an exact line before the device is ever asked to install.
  The 32-bit value stays on as device-side corroboration, honestly
  labeled. Wrong-certificate and missing-digest regressions ride along;
  a mismatch now stops the install rather than following it.

---

## 2026-08-19 — The except clause signs an affidavit about its own breadth

- The hierarchy reader's `OSError` catch is deliberately wider than
  `FileNotFoundError`, and now says so in a comment: permission errors,
  disk full, any I/O failure on the artifact counts as an unreadable
  artifact, which is the fail-closed verdict the harness is contractually
  obliged to reach. Zero behavior change — the comment is the entire
  diff, landed as the review nit carried from #73 so #55's qualification
  PR stays pure evidence.

---

## 2026-08-19 — The acceptance matrix arrives, and the fake toolkit learns to misbehave on cue

- The fake-only acceptance matrix (issue #62) now drives the real
  qualification CLI end to end through an absolute interpreter and a
  PATH containing nothing but fakes. Twenty-six variants cover the
  happy path, every nonzero terminal class, wrapper/remote status
  collisions (rc=7 intact, rc=1+stderr ambiguous and fail-closed),
  timeouts, SIGINT/SIGTERM convergence into cleanup, malformed
  output/XML/media, fixture drift and prop drift, selector
  duplication, hostile artifacts (extras, symlinks, writes outside
  allowed roots — with a canary positive control proving the
  containment check can actually fire), a mid-journey emulator death,
  and combined cleanup failures. Every variant preserves exactly one
  decodable capture record with exact primary/cleanup outcomes and
  zero real-tool contact, on the honor system of an isolated PATH and
  the evidence of recorded tool identities.
- The happy path is pinned to the exact complete contact ledger and
  the exact ledgered argv sequence (155 contacts, 150 entries), with
  one honest exception documented in the test: the screenrecord start
  is a concurrent sibling, so the moment its log line lands is the
  OS's business, not the harness's.
- The fake toolchain grew failure knobs (rc scripting, sleeps,
  garbage output, prop overrides, silent pulls, malformed media,
  duplicate nodes, hostile writes) — unset knobs leave the honest
  fake untouched. run_fake_capture_cli.py now runs the full fake
  journey to a green receipt, decodes the record on the way out, and
  passes caller knobs through for by-hand failure demos.

---

## 2026-08-19 — The journey stops believing in hierarchies it never read

- A pull that returned rc=0 with unparsable — or entirely absent — XML
  used to record a COMPLETED hierarchy step, quietly truncate the
  journey, and let the failure resurface later dressed as a missing
  screenshot. Every hierarchy read now fails closed as a
  `journey_failed` step ("hierarchy missing or unparsable") instead of
  vouching for facts it could not parse. The absent-file variant is
  worse than it sounds: its exception escaped `run_journey` unwrapped,
  and the run ended with cleanup performed but no capture record at
  all — receipts now survive hostile tools that lie about rc. Found by
  the #62 acceptance matrix doing exactly its job; regression tests
  ride along for both vectors.

---

## 2026-08-16 — Cleanup learns to check the name badge before it swings

- The six remaining execution-boundary findings from the 2026-08-13 review
  are closed. `screenrecord` start/finish now crosses the same boundary as
  every other shell-v2 operation (bounded finish, `RemoteResult` conversion,
  ledger entry — including on the failure path through `restore`).
  Cleanup refuses to signal an emulator whose observed command or AVD does
  not match the launch argv, requires validated provisional ownership before
  any signal, and refuses when a live process is unobservable.
- `bounded_terminate` captures the process-group id once, before signaling;
  escalation and a bounded extinction check target the stored group, so a
  leader that exits mid-terminate can no longer shield its resistant
  descendants (and a release with group members alive now fails closed
  instead of reporting success). SIGINT/SIGTERM during an active phase
  raise `SignalInterrupt` through the blocked command (the child is killed
  and reaped on the way out) and converge into bounded cleanup; signals
  during cleanup are recorded without aborting it. Ledger persistence is
  now a recorded step — a failed ledger write marks cleanup partial while
  preserving the primary failure. The fallback PID path gets the same
  bounded, identity-validated lifecycle (SIGTERM → wait → SIGKILL → reap,
  never signaling a reused pid). Twenty adversarial regressions ride
  along, including a real blocked-child signal test.

---

## 2026-08-16, night — The receipt learns to count for itself

- One exact flat artifact set is now enforced, not assumed: seven named
  screenshots, one named video, the sixteen journey hierarchies, and the
  private redacted command ledger (which is also the run's status log —
  every production command's argv and status dimensions). A manifest
  that is not exactly this set is rejected — missing, extra, renamed,
  or nested entries alike — and the CLI now requires that
  capture-manifest binding before it will report success; failed runs
  still write their record, but partial artifacts can never pass.
- The final receipt derives every dimension itself: media counts from
  the exact bytes, journey/release/verification verdicts from the named
  capture steps, privacy and structural media validation fail-closed.
  The caller-controlled verdict, count, and artifact inputs are gone
  from `finalize` and from the CLI — there is nothing left to vouch
  for. Every run artifact (manifest, capture record, approval, receipt)
  is written privately (0600) and atomically, so an interrupted write
  can no longer leave a truncated file behind a success code. The
  screenshot names are now sourced from the one canonical definition,
  and the two deferred review notes from Stage 2 are documented
  (defense-in-depth receipt blanking; device-only pinned-branch
  coverage). Review round: the receipt now refuses to mint itself
  unless the verify_restore step completed (loading the snapshot is
  not the same as checking it came home) and unless release and
  verify_release completed — recorded-but-failed is no longer
  mintable; subdirectories, symlinked or empty, are rejected from the
  flat set at both manifest and finalize; canonical XML must parse
  with a `<hierarchy>` root and the command ledger must decode in its
  serialized entry shape, digest agreement notwithstanding; manifest
  failures surface their actual diagnostic instead of masquerading as
  "no artifacts"; `dump_ledger` consolidated onto the one atomic-write
  path; and the harness cannot express a non-canonical hierarchy
  label. Second review round: the ledger check now enforces the exact
  LedgerEntry schema — field set, types, known kind, and at least one
  entry; a ledger written in interpretive dance no longer mints a
  receipt, and the canonical fixture binds to the real
  `CommandLedger.serialize()` so a key rename in `commands.py` fails
  every canonical test rather than none — and a receipt is refused
  unless the journey recorded at least one step and every capture step
  completed. A failed journey is no longer countable-and-mintable.

---

## 2026-08-16, evening — The harness stops taking the fixture's word for it

- The qualification journey now proves its preconditions instead of
  assuming them. Before any mutation: the pinned snapshot bytes are
  verified against the accepted fixture digests (missing or drifted
  files fail closed — the CLI grows `--fixture-root` and
  `--fixture-digests` for honest fake-only runs), the animator scale
  joins window/transition as a pinned "unset", and the editor must be
  observed empty and unfocused before the journey touches anything —
  the observed facts become the runtime-private restoration baseline,
  which `verify_restore` re-checks after the snapshot reload. Private
  facts never enter the public record; only the comparison verdict
  does. Review round: an unparsable or undumpable keyboard hierarchy
  now fails closed before the first tap (absent facts never authorize
  one), and injected fake-only digests mechanically blank the recorded
  fixture receipt — a run over arbitrary snapshot bytes can no longer
  present itself as an accepted-fixture qualification. Re-review round:
  the pinned-versus-injected verdict now rides in the validate_fixture
  step's own serialized stdout ("fake-only … not an accepted-fixture
  qualification" vs the pinned receipt prefix), so the boundary
  survives into the persisted CaptureRecord instead of an attribute
  the receipt politely forgot; asserted end-to-end against the decoded
  capture record.
- Every ASK tap coordinate is now validated against uniquely observed
  key geometry: missing, duplicated, malformed, or non-containing key
  facts fail closed before the first tap. The stale path became
  explicit — applying a stale candidate must retain it in REVIEW for an
  explicit dismiss, never silently drop it — and the fake toolchain
  learned to model focus, per-key bounds, animator state, snapshot-load
  reversion, and stale retention, so it matches the product state
  machine rather than a polite fiction. Twelve adversarial regressions
  ride along, including a real CLI fixture-drift run. Budgets amended
  to 1,000/2,500 (actual 979/2,439) in ADR-0008.

---

## 2026-08-16 (later that day) — The corpse is identified, reaped, and given a clean receipt

- Post-merge review round (two independent findings sets, same P1): a
  crashed or failed-boot emulator — the most common non-nominal path —
  was refused by cleanup instead of reaped. `ps` reports an unreaped
  leader as `<defunct>`, which failed the argv match, stamped the run
  `CLEANUP_PARTIAL`, and dropped the only handle. `release_emulator` now
  polls and reaps an already-exited leader first and returns a clean
  release, never group-terminating after the reap (a freed pid's group
  could belong to someone else by then).
- Live-process validation now compares start-time continuity against an
  identity *observed from the process* and retained at launch/ownership —
  launch argv is not a stable runtime identity, because the SDK emulator
  launcher execs its engine and engines rewrite their titles.
  `establish_ownership` rejects unrecognizable commands (a bare
  `<defunct>` among them) via start-continuity plus executable-or-AVD
  tokens, space-tolerant for SDK paths.
- The command ledger gains the one entry it was missing — the emulator
  launch itself (`kind="launch"`). `dump_ledger` now writes privately
  (0600) and atomically (same-dir temp file, fsync, `os.replace`), so an
  interrupted write can no longer report `COMPLETED` over a truncated
  file. A signal arriving during cleanup always leaves a recorded step,
  even when a primary cause already holds the terminal. The fallback PID
  lifecycle validates identity before its *first* signal, group-liveness
  checks no longer count unreaped zombies as survivors (macOS `killpg(0)`
  EPERM quirk included), the launch phase moved inside the cleanup guard
  so a signal in the launch window converges to release, and
  screenrecord is now stopped explicitly (SIGTERM accepted as a normal
  stop, closing the 30-second-limit vs 15-second-finish disagreement).
  Ten new regressions, including a real exec-transformed process and a
  real zombie-held group.

---

## 2026-08-13 — The test suite learns to mind its own environment

- `test_preflight_success` now mocks `socket.socket` (ConnectionRefused) and
  clears `ANDROID_HOME`/`ANDROID_SDK_ROOT` so preflight skips the
  build-tools aapt2 probe — no more env-coupled TypeErrors from resolve_tool
  kwargs, no more port-5554 probe collisions.
- `test_ledger_phase_order` now reads deterministic `capture-record.json`
  step phases instead of parsing `MOCK_COMMANDS_LOG` line order, which was
  racy between `launch_emulator` (Popen background) and `attach` (synchronous
  wait-for-device). Expected phases use the orchestrator's real vocabulary
  (`journey`, `capture`) rather than retired mock-log labels.

---

## 2026-08-11 — The execution boundary becomes total

- The M2 harness now structurally distinguishes wrapper failure, transport
  failure, remote status, timeout, signal, and ambiguity through every
  command path. Every `adb shell` operation — hierarchy dumps, taps,
  screenshots, activity launches, screenrecord, getprops, settings,
  dumpsys — routes through `_shell()` and produces `RemoteResult` with
  ledger recording. `adb install`, `adb pull`, `adb emu`, and
  `wait-for-device` route through `_host()` as host commands. No
  production command bypasses the ledger or the timeout boundary.
- `commands.run()` and `commands.finish()` post-kill `communicate()` are
  now bounded (5 s ceiling). The previous unbounded calls could hang
  indefinitely on a process that ignored SIGKILL (pipe full, kernel
  stuck).
- Ambiguous remote results (`remote_rc=None`) propagate as
  `TOOL_FAILURE` through every path — prior-state, fixture, install,
  journey, restoration — not collapsed into the phase-specific cause.
  `RemoteAmbiguousError` propagates from `capture_prior_state`;
  `validate_fixture` and `install_apk` return the ambiguous
  `RemoteResult` directly.
- The command ledger records full absolute adb/serial argv for every
  production command, serialized to `artifacts/command_ledger.json` at
  release. Content (stdout/stderr) is never stored.
- `ProcessIdentity` reads start time AND full command line from the
  actual process via `ps lstart=` + `ps command=`. `establish_ownership`
  observes the real identity; `_revalidate_ownership` compares both
  dimensions. The process-handle release path also revalidates before
  termination.
- The emulator launches in its own session (`start_new_session=True`).
  `bounded_terminate(group=True)` signals the entire process group
  (SIGTERM → bounded wait → SIGKILL → bounded reap), killing resistant
  descendants. `release_emulator` reports truthfully: whether SIGKILL
  was required, whether group signaling was used, and whether the
  process is still alive after escalation.
- 59 adversarial tests exercise real hazards: actual `os.kill` signal
  delivery, `os.fork` descendants that ignore SIGTERM, PID identity with
  observed argv (not expectations), `ProcessIdentity` comparison on
  command substitution, bounded post-kill communicate, ledger
  serialization to artifact, and ambiguity propagation from every path.
  244 tests total.

---

## 2026-08-10 — The inspector stops hallucinating and starts taking pictures

- The M2 device-qualification harness gained a real evidence-capture
  path: seven screenshots and one video via `screencap`/`screenrecord`,
  validated structurally (PNG CRC + IHDR/IEND; MP4 requires `ftyp`
  plus `moov` or non-empty `mdat`) with exact count enforcement (7+1)
  before the journey record is sealed. The journey verifies PersonaSpeak
  keyboard panel states through a complete ASK cycle: Loading, Review,
  Cancel (text unchanged), Again, Review, Apply (text becomes
  candidate), Review, Dismiss (text unchanged) — with stale-input
  clearing and mutation-proof text verification at each transition.
  Preflight verifies the AVD exists before launch; post-attach
  fixture checks pin ABI, density, and animation scale. XML parse
  errors stop the journey; the search-field selector matches the exact
  pinned resource-id. Every `keyevent` return code is checked.
  `verify_release` discriminates `ConnectionRefused` from timeout and
  other socket errors. `capture_prior_state` checks every command rc
  and returns `None` on any failure. All tool invocations use resolved
  preflight paths. `SIGINT`/`SIGTERM` converge on restoration and a
  decodable failure receipt; `capture_context` exceptions produce a
  `PREFLIGHT_FAILED` record. The CLI verifies `--apk-sha256` against
  the on-disk APK, binds `--repo-root` with clean-head check, builds
  and writes the evidence manifest, and rejects dirty trees.
  `install_apk` verifies version/signer via `dumpsys package`.
  `finalize` raises on privacy/media/restoration failure — no
  approvable false receipt. `build_manifest` rejects symlinks and path
  escapes. `CaptureContext` lives at the orchestration boundary;
  monkey-patching is gone. The fake adb simulates keyboard state
  transitions with Apply/Cancel tap detection; forbidden
  `apksigner`/`keytool` canaries guard the ledger. The direct root
  runner works without `PYTHONPATH`. 169 tests, no device contacted (#59).

## 2026-08-10 — The Loading row learns the word "Cancel"

- While PersonaSpeak is thinking, the row now shows a Cancel button next
  to the spinner. This is not a second cancellation path; it is the
  existing one (`onDismiss` → `RewritePanelViewModel.dismiss()`, which
  drops the in-flight request) finally given somewhere to be tapped.
  Idle, Message, and Review are unchanged — Review keeps its own
  Dismiss. The control clears the 48dp touch minimum, carries the
  `personaspeak_cancel` test tag, and exists only in Loading. Closes
  the M2 contradiction where the qualification journey required a
  Loading/cancel control the row did not render (#60).

## 2026-08-06 — The inspector learns to read exit codes without reading minds

- `RemoteResult.remote_rc` is no longer set to None by default and left
  for someone else to figure out. `AdbRemoteStatusReader` populates it
  from the adb shell_v2 transport result, using a structural discriminator
  (stderr emptiness at rc=1) rather than pattern-matching adb's English
  error text. The probe observed that transport failures always write to
  stderr and always exit 1; remote exit codes pass through at every
  other value. Where the two signals collide — rc=1 with non-empty
  stderr — the adapter declines to guess and returns None, letting
  `_rc_of` fail closed to 1. A real remote exit code lost to caution
  is an acceptable trade; a transport failure reported as remote success
  is not.
- `run_remote` no longer defaults every caller to `UnavailableReader`.
  The concrete reader is the default; the old unavailable behavior is
  still available for callers who want to opt out.
- `_rc_of` for `RemoteResult` retains its transport backstop (the B-1
  correction from the previous PR). The adapter populates `remote_rc`;
  the dispatch remains correct regardless of which reader produced
  the record. The redundancy is the guarantee.
- A coverage-matrix test parses the orchestrator's AST and asserts the
  set of `_rc_of` / `_timed_out` call sites matches an enumerated list.
  It was demonstrated failing against a stray call before it was
  trusted to guard against one. 138 tests, no device contacted.

## 2026-08-06 — The inspector rebuilds, in a language that doesn't eat its own status

- Five Python modules (898 lines) implement the complete device-free
  qualification machinery: typed records with deterministic JSON codec, local
  process runner with remote-result interface, phase-ordered orchestrator with
  prior-state capture and restoration, evidence validation (privacy scan, PNG
  and MP4 structural checks, manifest digests), and a capture/finalize/approve
  CLI. 96 tests exercise every record round-trip, failure path, and adversarial
  fake-tool boundary. No device was contacted; no keyboard code was changed.

## 2026-08-06 — The inspector receives an exit interview

- The three failed Milestone 2 capture attempts now have one retrospective and
  one replacement design. Bash retires from device orchestration; a bounded
  Python instrument, adversarial fake-tool run, snapshot-backed emulator, typed
  receipts, and external evidence archive take its place. The keyboard remains
  unchanged. It has already spent quite enough time watching its clipboard
  learn about itself.

## 2026-08-05 — One keyboard, one APK, one row of its own

- PersonaSpeak now rides in a dedicated row above AnySoftKeyboard's
  suggestions and keys, instead of borrowing a seat on the candidate strip.
  Review scrolls inside a frozen `min(320dp, 40%)` bound sampled before the
  row expands — sampling afterwards feeds the row's growth back into its own
  limit, which ends with the keyboard covered by the thing meant to sit above
  it. Every control clears 48dp, including Dismiss, which until now had no
  test tag and therefore no opinion about its own size.
- The two rollback modules are gone. `android/app` and `android/keyboard-stub`
  existed so we could retreat; the retreat is over, and ASK's `:ime:app` is
  now the only project that produces an APK. The tree used to yield three of
  them, one of which was upstream copying the artifact somewhere convenient.
  It no longer does that here, and still does it everywhere else.
- New gates: exactly one APK at exactly one path from exactly one application
  project, and one aggregate Milestone 2 verifier that CI now calls instead of
  keeping its own parallel list of steps to drift out of sync with. Each
  verifier has a fixture suite that runs before the verifier is trusted,
  because a green gate built on an unchecked tool is worse than no gate.
- What this entry does not claim: none of the above was verified on a real
  device. The cutover is proven by the host gate and the unit suites. Mutation
  against a real `InputConnection`, the height cap on a real screen, and
  restoration after real mutation are pending, and land in a follow-up PR under
  issue #47 along with the orchestrator that runs them. The earlier device
  receipt was not accepted and is no longer tracked here; a receipt nobody
  signed is not evidence, it is filing.

## 2026-08-05 — The repo files its license

- Root `/LICENSE` now carries the official Apache-2.0 text. The app code has
  been Apache-2.0 since ADR-0003; the paperwork had simply not reached the
  front door. AnySoftKeyboard's vendored license stays at
  `android/keyboard/LICENSE`, and persona content remains CC-BY — a license
  file is not a personality transplant.

## 2026-07-29 — Milestone two acquires written instructions

- The accepted ASK cutover now has an executable completion plan: preserve the
  reviewed history, add the dedicated PersonaSpeak row in serial slices, delete
  both rollback applications atomically, prove exactly one APK, restore the
  qualification device, and merge only exact-head evidence. The keyboard is
  unchanged; the paperwork has become unusually competent.

## 2026-07-29 — The keyboard receives a route to becoming installable

- The production route (commit `f773aff`) now carries PersonaSpeak from the atomic ASK cutover
  through real keyboard flow, encrypted provider state, onboarding, visual and
  privacy qualification, and one reproducible signed APK. Eight milestones now
  have explicit evidence, restoration, review, and stop gates; store publication
  remains outside the velvet rope until it acquires a design of its own.

## 2026-07-24 — Suggestions keep their chair

- PersonaSpeak now has an architectural seating plan: its own measured row
  above AnySoftKeyboard's suggestion row and keys. The shared-strip prototypes
  were cheaper only if we ignored the suggestions, the keys, or occasionally
  both. ADR-0007 declines the discount.

## 2026-07-22 — The keyboard and the product become the same application

- The first-party UI boundary is now a contract, not a vibe. CI builds the
  validated persona catalog/repository, the pure editor contract, and the
  guarded two-stage rewrite coordinator, then fails if anything first-party
  reaches for an Android import it shouldn't or an AnySoftKeyboard symbol it
  hasn't earned. Exactly three artifacts leave the current graph — the
  temporary app APK and the two first-party AARs — and the vendored ASK
  snapshot stays inert, outside Gradle, producing nothing.
- The UI-foundation plan has caught up with `main`: malformed provider
  successes, all six validation fixtures, and the rejected panel now have
  deterministic gates instead of relying on optimism.
- The Stitch exports have acquired adult supervision: every requested screen
  now has an explicit disposition, with truthful privacy copy and your words
  still requiring one deliberate `Use this` before they move.
- The post-ingestion roadmap now has an address: one tested UI boundary, one
  atomic ASK cutover, then the complete Stitch journey. The accepted plan also
  routes persona sources through a repository seam, so a future marketplace can
  add plumbing without teaching the keyboard where the parcels came from.
- AnySoftKeyboard `1.13-r1` now occupies `android/keyboard/` as a pinned,
  inert snapshot with an empty upstream-rent ledger. The rejected panel moved
  unchanged to `:keyboard-stub`; it remains scaffolding, not a comeback tour.
- The old ADR-0001 panel has been promoted from “demo keyboard” to its accurate
  title: disposable, non-typing build scaffolding. ASK ingestion may move it
  unchanged; no journey may endorse it, and ASK integration deletes it.
- The Android build now uses ASK's proven Gradle 9.2.1, AGP 8.13.2, Kotlin
  2.3.10, and JDK 21 baseline. CI checks every current module, because a
  convergence experiment is more useful after it stops being temporary.
- ADR-0006 settles the part Gradle had been enjoying as an interpretive dance:
  PersonaSpeak ships one ASK-based APK from one unified build. ASK owns the real
  keyboard; first-party modules own the manners; a thin editor adapter validates
  immediately before asking the host to replace text, without pretending
  Android supplied a cross-process transaction or a second UI.
- The discarded switcher model stays discarded. No flip to a keyless panel, no
  flip back, and no local draft field waiting patiently for input an IME window
  cannot receive.
- Stitch exports become acceptance targets backed by screenshots and real
  journeys. The portraits remain detained at customs until their redistribution
  papers exist.

## 2026-07-21 — The paperwork catches up again

- ADR-0004 and ADR-0005 flip from Proposed to Accepted. Both decisions were
  already load-bearing — #18 is vendoring against ADR-0004's ingestion
  mechanism and #17 is the audit ADR-0005 mandated — the status line was just
  the last one to find out.

## 2026-07-21 — The FlorisBoard spike files its exit interview

- Ran the full capture-to-commit loop on a real IME (FlorisBoard, throwaway
  per ADR-0003/0004) and wrote down the receipts: the in-keyboard flow (strip,
  pickers, loading, result, replace) works end to end, onboarding and settings
  don't exist yet, and the stale-field race guard is still a spec, not code.
  The recommendation lines up with ADR-0004 exactly — move the graft to
  AnySoftKeyboard next.

## 2026-07-21 — Auditing the keyboard we're about to move in with

- Static privacy inventory of AnySoftKeyboard `1.13-r1` (commit `8c1db51`) is
  in `docs/privacy/anysoftkeyboard-1.13-r1-inventory.md`. Three kinds, kept
  separate: on-device local state, anything that leaves the device, and
  on-device disclosure surfaces. Every finding cites `path:line` against the
  pinned ASK tree.
- What *provisionally* holds against the README's "nothing is logged" and "not
  used to improve our services" clauses — clear from static reading, not yet
  proven: the release build's `NullLogProvider` gates every `Logger.*` call and
  the in-memory ring buffer, and no analytics/crash-upload SDK appears in source
  or Gradle config (no Firebase, Crashlytics, Fabric, Flurry, Sentry, Amplitude),
  the crash path being a user-tapped email. A source grep cannot see
  shaded/transitive deps, native code, or runtime egress, so neither clause is
  signed off until the release APK and per-UID network capture confirm it — a
  false all-clear is the one thing a privacy claim cannot afford.
- What does not survive as-shipped: "nothing is stored" — a predictive
  keyboard keeps a learned-words DB, an auto-learn DB, and per-locale
  next-word files, all default-on, all user-clearable. The honest restatement
  is ADR-0005's "stays on your phone, user-clearable," not "does not exist."
- The one fact the privacy copy did not account for: Android Auto Backup is
  default-on (`allowBackup="true"`, no `fullBackupContent` or
  `dataExtractionRules`), so the local state above is eligible for the device's
  configured backup transport (commonly Google Drive). Not a server we run — but
  also not "nothing leaves your phone." The inventory lists this as the headline
  neutralization item.
- Static analysis is necessary but not sufficient. On-device capture (per-UID
  packet capture, `bmgr`, resolved dependency graph, decompiled release APK) is
  the gating step before the privacy copy unfreezes; the inventory's last
  section is that checklist.

## 2026-07-21 — Reading our own privacy promise back to ourselves

- ADR-0005 catches a claim that quietly stopped being true: "Nothing is stored,
  logged" was honest for the thin IME we owned entirely, and is an overclaim for
  a forked predictive keyboard that keeps a learned-words dictionary to do its
  job. The fix is not to store less than a keyboard must — it is to stop
  conflating "stays on your phone" with "does not exist," and to say which is
  which.
- The privacy copy is now frozen until an inventory of what the vendored ASK tree
  actually stores and sends is done on-device — default posture: anything that
  leaves the phone is off, and proven off. No accusation against ASK; it is a
  keyboard doing keyboard things. The overpromise was ours.
- This is load-bearing text, so the entry above is the only joke you get.

## 2026-07-21 — Deciding how to move a keyboard into the house

- ADR-0004 settles how AnySoftKeyboard's tree enters the repo: a **vendored
  snapshot** at a pinned tag, edited in place, with the upstream diff kept as a
  hand-written manifest. Not a submodule (it fights the fact that we edit
  upstream files), and — after a reviewer caught the first draft's reasoning —
  not a subtree either, because our squash-merge plus linear-history policy
  flattens the merge ancestry that `git subtree pull --squash` needs, so its one
  trick works exactly once.
- The point of the manifest is the rent ledger: regenerate the pristine tree,
  diff it against ours, and "lines modified are rent paid forever" becomes a
  checklist instead of an excavation.
- No code moved yet — this decides the mechanism so the graft PR doesn't have to
  argue with itself mid-move.

## 2026-07-21 — The records catch up to the decision

- ADR-0003 merged, so the paperwork stopped lying: its status is now Accepted,
  ROADMAP admits the base is AnySoftKeyboard and the licence is Apache-2.0, and
  the UX doc's fork-base and licence open-questions are struck through.
- Left one gate honestly open — the stale-field race guard still has to be
  implemented before a real provider touches anybody's draft — because closing a
  ticket is not the same as writing the code, and the roadmap should not pretend
  otherwise.
- No behaviour changed; the next agent just stops taking orders from a map that
  predates the territory.

## 2026-07-21 — We picked a keyboard (PR #11)

- ADR-0003 lands the decision the whole fork spike was evidence for: fork
  **AnySoftKeyboard**, license the app **Apache-2.0**. The license did most of
  the picking — GPL-3.0 is a one-way door we chose not to walk through — and the
  rest fell out of facts already in hand (FlorisBoard's prediction engine is
  stubbed; ASK's is the grown-up of the three and actually builds). Adoption is
  provisional pending a typing sanity check and the stale-field race guard, both
  already ticketed. The three-way typing bake-off was retired before it could
  eat a day confirming a real engine beats an empty one.

## 2026-07-21 — The gate that watches the gate's more dangerous cousin (PR #10)

- Speced the editor-identity guard that stops a slow provider from rewriting the
  wrong field — or the right field with somebody else's freshly-typed words. The
  race was always there; `FakeProvider`'s brisk 400ms just kept it politely
  off-stage. No code lands here, and no base is picked — the fork-base ADR still
  owns that — but the contract is now written down where the implementation
  ticket can't misplace it. `FakeProvider`, for its part, remains a committed
  thespian and an unreliable witness.

## 2026-07-21 — The gate that watches the gate (PR #8)

- CI now refuses to merge a PR that leaves `PATCHNOTES.md` untouched — including, with great self-referential ceremony, this one. Carry the `no-patchnote` label for the rare genuine exception; the skip is announced loudly, never silently. The gate checks that the file was *touched*, not that the line was *good*. The last mile stays with the reviewer, where it belongs.

## 2026-07-21 — The bake-off nobody won on purpose (PR #3)

- Grafted the persona strip onto all three fork candidates — HeliBoard,
  AnySoftKeyboard, FlorisBoard — drove each on a real emulator, and graded how
  much of somebody else's keyboard we'd be signing up to maintain. Answer: less
  than feared, in all three cases.
- Every candidate's first graft passed its unit tests and broke on the device,
  in a different place each time. All fixed and re-verified on-device; none
  taken on a worker's word. The moral — an on-device instrumentation test of the
  capture→transform→replace path — is now written down where the next base
  can't miss it.
- Commissioned two independent reviews that reached opposite recommendations and
  told this document where its own reasoning was thin. Both are on the PR; one
  found an async stale-field race in all three pipelines that `FakeProvider` was
  politely hiding. Filed as a pre-ADR blocker.
- Did not pick the base. That's still the owner's call — this is the evidence,
  not the verdict.

## 2026-07-21 — The staff writes its own rulebook (PR #2)

- Reviewed the previous PR after it merged, wrote down the findings, then
  proposed rules that would have forbidden reviewing your own previous PR.
  The irony has been filed.
- The robot staff now has a house voice on duty: docs, guides, PR bodies, and
  conversation all wear the butler. Load-bearing prose — privacy, permissions,
  keys — still comes plain, because nobody wants a joke in a threat model.
- Added a Definition of Done, so "fully complete PR" is a checklist instead of
  a mood. A PR now arrives graded by someone who didn't write it, with its
  patch note already in this file.
- Shipped a PR template that asks for evidence and a patch note, and refuses
  to pretend a line pasted in the description counts as either.
- Ran this branch past a different model family, which found a privacy
  overclaim and made us rewrite it. That is the system working, and also
  slightly embarrassing, which is the system working.

## 2026-07-20 — The keyboard eats a keyboard (PR #1)

- Reversed ADR-0001 the same day it was accepted, setting a repo speed record
  we would prefer nobody breaks.
- PersonaSpeak will now fork an entire open-source keyboard rather than
  politely coexist with yours. The switching model — flip to us, flip back —
  was judged unshippable, so we removed the switching, the second keyboard,
  and the concept of leaving.
- Added 13 UX mockups, including one rejected design and one superseded
  design, retained because failure is evidence and storage is cheap.
- Four persona emoji circles were rendered illegibly twice, independently, and
  have been replaced by one chip with a name on it. The circles fought
  bravely.
- Mood is now an orthogonal prompt modifier. The persona schema remains at
  version 1 and did not have to be involved at all, which it appreciates.
- The result card now floats over the chat and never over the keys. The
  typing surface does not move. This is a rule, not a preference.

## 2026-07-20 — The repo assembles itself

- PersonaSpeak v0: four personas, a Python CLI, and a Claude skill that
  rewrites your messages with more dignity than you sent them with.
- Reorganized into a monorepo. The CLI moved to `desktop/`; the personas
  stayed at the root, where the talent belongs.
- The repo learned to talk: VOICE.md, README, AGENTS.md, CONTRIBUTING,
  ROADMAP, GTM. All prose now passes a "would Valve ship this" check.
- Persona schema formalized, with a validator and CI to enforce it, because a
  schema without a validator is a rumor.
- PersonaBoard walking skeleton: four Android modules that build, with golden
  tests proving the Kotlin prompts match the Python reference byte for byte.
- Fixed a bug where the IME window had no lifecycle owner, and Compose,
  reasonably, refused to compose under those conditions.
- Renamed the package to `biz.pixelperfectstudios.personaspeak`, a domain we
  own, unlike the previous one, which we merely admired.
