# Single-APK ASK integration design

**Date:** 2026-07-22

**Status:** Owner-approved design (2026-07-22)

**Decision:** [ADR-0006](../../adr/0006-gradle-composition-for-the-graft.md)

**Related:** [keyboard UX design](2026-07-20-keyboard-ux-design.md) ·
[stale-field race design](2026-07-21-stale-field-race-design.md) ·
[Stitch screen contract](2026-07-22-stitch-screen-contract.md) ·
[privacy posture](../../adr/0005-privacy-posture-fork-audit.md)

## Outcome

PersonaSpeak is one full, daily-driver keyboard built from ASK. ASK supplies
the mature typing engine; PersonaSpeak supplies the branded experience and
explicit rewrite flow. Users see the Stitch-designed onboarding, settings, persona
strip, pickers, results, and errors. They do not see an adapter, a second
keyboard, or the superseded keyless panel.

This design is an architectural contract. It does not claim the current PR
stack already implements it.

## Goals

- Ship one APK from ASK's `:ime:app` with PersonaSpeak's application ID.
- Preserve `core-personas` and `core-providers` as portable pure-Kotlin modules.
- Integrate the PersonaSpeak strip with ASK's real editor connection.
- Reuse the current onboarding/settings work without preserving the root app as
  a second product.
- Treat the exported Stitch screens as acceptance targets with device evidence.
- Keep the upstream modification surface small, explicit, and reproducible.
- Preserve the default-private, audit-gated posture from ADR-0005.

## Non-goals

- A second special-purpose PersonaSpeak IME.
- Switching from another keyboard into PersonaSpeak to rewrite, then switching
  back.
- A local Compose text field inside the IME.
- Suggested replies, notification access, or Phase 2 behavior.
- Redesigning ASK's inherited keys, autocorrect, glide typing, dictionaries, or
  language system beyond the PersonaSpeak theme/integration work required by
  the approved mockups.
- Claiming portrait assets are shippable before redistribution rights are
  recorded.

## Module structure

### `core-personas`

Owns persona parsing, schema validation, and prompt construction. It remains
pure Kotlin and knows nothing about Android, ASK, Compose, providers, or editor
state.

### `core-providers`

Owns provider contracts and provider-independent request/result types. It
remains pure Kotlin except for a separately isolated source set if a future
on-device provider genuinely requires platform APIs.

### First-party Android library

The new `:personaspeak-ui` project at `android/personaspeak-ui/` owns:

- the Compose theme, bundled fonts, and approved visual resources;
- onboarding and settings activities/routes;
- persona and mood pickers;
- the permanent strip, loading state, result card, and error surfaces;
- persistent settings and key-storage abstractions;
- rewrite coordination and stale-editor protection;
- the `EditorPort` boundary.

It depends on `core-personas` and `core-providers`. It does not import ASK
classes. The old application module does not remain installable; per migration
step 4, no code or UX is extracted from it into this library.

### Persona source boundary

The first-party library does not read persona YAML files directly from UI or
rewrite code. It consumes validated persona snapshots through a source-neutral
repository interface. The initial implementation exposes only the four bundled
personas and packages the existing root `personas/*.yaml` files as Android
assets without duplicating them.

`core-personas` remains the authority for parsing, schema-version checks,
validation, stable source-qualified persona identity, and prompt construction.
Source and provenance metadata wrap the v1 persona content; they do not change
the YAML schema or prompt goldens. Active selection belongs to the package
settings repository, not to the persona catalog, and editor/provider code never
branches on where a persona came from.

A future owner-approved marketplace can add a repository/store adapter behind
this boundary. Network discovery, downloads, package manifests, signatures,
moderation, trust policy, licensing, updates, and deletion are not implied by
the interface and require a later ADR. No downloaded persona may become active
until it passes the same schema and content validation as bundled personas.

### ASK `:ime:app`

ASK remains the sole Android application and owns:

- the `InputMethodService` and keyboard lifecycle;
- key rendering, layouts, suggestions, autocorrect, glide typing, dictionaries,
  and languages;
- the application ID and merged manifest;
- a thin PersonaSpeak integration adapter.

The adapter lives in a clearly first-party package and implements `EditorPort`
with ASK's current `InputConnection`. ASK receives the PersonaSpeak UI library
as a direct Gradle project dependency. The app constructs and injects the
adapter; the library never reaches back into ASK through a project dependency.
The resulting direction is strictly
`:ime:app -> :personaspeak-ui -> core-*`, with no reverse edge.

ASK also owns the view-host seam. Its input-view hierarchy mounts the
PersonaSpeak Compose surface and installs `ViewTreeLifecycleOwner`,
`ViewTreeSavedStateRegistryOwner`, and `ViewTreeViewModelStoreOwner` on the IME
window decor view before composition. Persona and mood pickers render inside
that same input view; Compose dialogs or popups that create a focus-taking
window are excluded because they can terminate or invalidate the host editor
session. This seam is recorded in `android/keyboard/UPSTREAM-MODIFIED.md` with
the editor adapter.

## The editor boundary

This section is the ASK-specific implementation shape of the earlier
[stale-field race design](2026-07-21-stale-field-race-design.md). Its threat
model, discard behavior, and provider-await race tests remain binding. Its I2/I6
claim that an IME-main-looper turn makes editor validation and mutation atomic
is superseded: `InputConnection` crosses into an independently scheduled host
editor, and Android exposes no generic compare-and-set transaction. Where the
documents otherwise differ, this `EditorPort` contract wins.

`EditorPort` is deliberately smaller than ASK's API surface. It provides:

- `captureSnapshot()` — return an exact, bounded rewrite target plus opaque
  editor-session, selection, and request-generation tokens, or a typed refusal;
- `attemptReplace(snapshot, replacement)` — re-read and compare the target as
  late as possible, attempt the least-destructive replacement sequence only if
  every check passes, and return a typed `ReplaceResult`.

`ReplaceResult` distinguishes:

- `AppliedVerified` — a post-write read observed the expected replacement;
- `Stale` — session, generation, text, or selection changed before any text
  mutation command was sent;
- `WriteRejected` — a required editor command returned failure;
- `WriteUnconfirmed` — a mutation command was accepted but the final editor
  text could not be read or did not match, so success and concurrent host edits
  cannot be distinguished safely.

The first-party library can therefore be tested against a fake port. ASK can be
updated behind the adapter without teaching the product layer about inherited
implementation details.

Android does not provide a universally stable field identifier.
`EditorInfo.fieldId` is optional and may be zero, and editor implementations may
return less text than requested or no text at all. The ASK adapter therefore
allocates its own monotonic editor-session token on `onStartInput`, invalidates
it on every new/finished input session, and treats `EditorInfo` metadata as
supporting evidence rather than sole identity. ASK key events and selection
updates may invalidate work early, but commit-time re-reading remains mandatory
because host applications can mutate an editor without going through ASK.

The initial rewrite target is the entire editable draft, not merely the visible
surrounding window or current selection, and is limited to 8,000 Unicode code
points. Capture fails closed for password variations, editors marked with
`IME_FLAG_NO_PERSONALIZED_LEARNING`, non-text editors, null or non-accepting
connections, incomplete reads, and oversized drafts. Blank drafts map to
`EmptyInput` without a provider call. The snapshot and provider result exist
only in memory for the active request.

`attemptReplace` performs final session/generation/text/selection validation on
the IME main looper with no suspension before issuing the write. That closes the
multi-second provider-await race and prevents IME-side callbacks from entering
the gap. It does not make the cross-process read and write atomic: the host can
still mutate its editor after the read response and before the write executes.
This residual window is an Android platform limit for generic host editors, not
a race the port can honestly declare impossible.

On API 34+, the adapter prefers the single text-mutation command
`InputConnection.replaceText`. On older supported releases it finishes
composing text, requires `setSelection(0, snapshot UTF-16 end offset)` to
succeed, and then uses one `commitText` call; it never deletes the draft and
then attempts a separate insert. Every boolean return is checked. If the text
command is rejected, any selection change is restored on a best-effort basis. A
post-write re-read verifies the outcome where the editor permits it. Batch-edit
bracketing may suppress intermediate callbacks or flicker, but is not treated
as a lock or transaction. `WriteUnconfirmed` never triggers an automatic retry,
because a retry could apply the replacement twice.

Launching onboarding or settings is a separate Android navigation concern. The
ASK application exposes explicit activities/intents from the library inside the
same package; `EditorPort` carries no navigation methods.

## Rewrite data flow

1. The user types normally on ASK's full keyboard.
2. The permanent PersonaSpeak strip shows the selected persona and mood.
3. On an explicit rewrite tap, the coordinator asks `EditorPort` for an exact,
   eligible `EditorSnapshot`. A refusal leaves the field untouched and maps to
   the appropriate empty/unsupported state.
4. The prompt builder combines the captured draft, selected persona, and mood.
5. The configured provider receives the request. The UI enters the Stitch
   loading state without moving the keys.
6. A successful response renders in the floating result card.
7. `Use this` calls `attemptReplace` with the original snapshot.
8. The adapter revalidates immediately before issuing the least-destructive
   available replacement command through the real `InputConnection`.
9. `Stale` sends no text mutation and offers a fresh rewrite against a newly
   captured snapshot. `WriteRejected` reports that the editor refused the
   change. `WriteUnconfirmed` asks the user to inspect the field and never
   retries automatically. Only `AppliedVerified` is shown as confirmed success.

`Again` starts a new request from a fresh snapshot. `Dismiss` changes no editor
text. Moving to another field or closing the IME cancels or invalidates the
pending operation. Cancellation saves provider work; commit-time validation is
still the safety boundary when cancellation races or a provider ignores it.

## UI and navigation contract

### Onboarding

The same ASK-based package presents the approved flow:

1. welcome and product promise;
2. enable PersonaSpeak in Android's keyboard settings;
3. select PersonaSpeak as the active/default keyboard;
4. choose a provider;
5. add a key when the provider requires one;
6. complete the guided rewrite demo.

The onboarding text describes replacing the user's keyboard, not adding a
temporary rewrite tool. Android's standard keyboard chooser remains available
as an escape hatch, but PersonaSpeak provides no flip-to/flip-back workflow.
The guided demo is an Activity-hosted editor with the installed ASK IME active:
the user types with real ASK keys and invokes the real PersonaSpeak strip. It is
not a simulated keyboard, local draft state machine, or alternate commit path.

### Keyboard

- ASK's real keys remain present and usable at all times.
- The compact persona strip is permanent furniture above the keys.
- Persona and mood pickers are temporary overlays.
- Loading and result states render in the IME-owned surface above the strip;
  they do not require overlay permission, cover the keys, or move the key rows.
  Device evidence must verify host insets and small-screen behavior.
- The result card provides `Use this`, `Again`, and `Dismiss`.
- No control returns to the discarded keyless panel.

### Settings

The launcher activity and ASK's settings affordance open PersonaSpeak settings
inside the same package. Persona, provider, privacy, appearance, and inherited
typing settings share one navigation surface and one persistent state store.
Review-before-replace is fixed product behavior, not a settings route.

## Stitch fidelity contract

The raw Stitch screenshots and HTML exports are design inputs, not vague
inspiration. Before a UI slice is complete:

- every exported screen maps to a named route or renderable UI state;
- the implementation spec resolves duplicate variants and identifies exports
  that do not exist;
- Outfit and Inter are bundled with their OFL notices and mapped by weight;
- device screenshots are compared side by side with the corresponding export;
- all hosted raster art and persona portraits are excluded until redistribution
  rights are recorded, or they are replaced with project-owned equivalents;
- Material Symbols ship with their Apache-2.0 notice or are replaced with
  project-owned icons;
- at least one global-font comparison covers onboarding and one settings screen;
- deviations are intentional, documented, and truthful, particularly privacy
  copy.

The accepted screen contracts are transcribed into the committed
[Stitch screen contract](2026-07-22-stitch-screen-contract.md). The local zip
and export directory are transport/reference material, not source artifacts, and
are not staged with architecture or integration work. The older committed
`docs/design/mockups/` set remains historical exploration; the reconciled Stitch
contract governs new fidelity work.

The current Stitch export has no dedicated target for `ProviderFailure`,
`StaleEditor`, `WriteRejected`, `WriteUnconfirmed`, `SensitiveEditor`, or
`UnsupportedEditor`. Those states must receive an approved target or a
documented adaptation of the existing amber error-card system before their UI
slice is graded; absence from the export is not permission to improvise
silently.

## State and persistence

- Persona, mood, active provider, provider configuration, and keyboard
  preferences persist across process death and configuration changes through
  one package-scoped settings repository. Transient Compose state is not a
  substitute for that repository.
- Provider keys are encrypted with a key held by Android Keystore; only the
  ciphertext and required metadata are persisted. Copy must not say the API key
  itself lives inside Keystore or claim hardware-backed storage unless runtime
  verification supports it.
- A provider key necessarily leaves the device as authentication to that
  provider; onboarding states this honestly.
- Drafts, prompts, and provider responses are not persisted as product history.
- Shipping manifest/rules exclude app-private data from Android backup and
  device transfer by default, including key ciphertext, provider configuration,
  typed-text databases, and learned prediction files. Any future allowlist for
  settings backup requires a later owner-approved privacy decision.

## Privacy contract

ADR-0005 remains binding:

- a draft leaves the device only for an explicit rewrite and only to the chosen
  provider;
- password and other policy-defined sensitive editors are never captured or
  sent to a provider;
- release builds never log draft or result text;
- learned words, prediction state, dictionaries, and clipboard behavior are
  disclosed separately from provider traffic;
- local learned data is user-clearable;
- typed-text databases and files are excluded from Android cloud backup and
  device transfer unless a later owner-approved ADR changes that posture;
- contacts integration and unexpected network egress remain off by default;
- crash and analytics upload remain off unless separately disclosed and
  approved;
- public privacy copy remains gated on static and on-device verification.

## Failure handling

The coordinator maps implementation failures into stable product states:

- `NoProvider` — the active provider is absent or disabled; credentials for a
  different provider do not count, and a future local provider requires no
  cloud key;
- `MissingOrInvalidKey` — route to the selected provider's key screen;
- `Offline` — keep the draft untouched and offer retry;
- `RateLimitedOrQuota` — explain the provider limit without blaming the
  keyboard;
- `ProviderFailure` — retain the draft and provide a safe retry/dismiss path;
- `MalformedResponse` — reject an empty or unusable provider result without
  exposing its raw body;
- `StaleEditor` — never commit; recapture before retrying;
- `WriteRejected` — explain that the editor refused the change; do not retry
  automatically;
- `WriteUnconfirmed` — ask the user to inspect the field; do not claim success
  or retry automatically;
- `EmptyInput` — do not call a provider; ask the user to type first;
- `SensitiveEditor` — do not capture or send text from a protected field;
- `UnsupportedEditor` — explain that the current field cannot be rewritten and
  leave it unchanged.

Raw provider errors, secrets, prompts, and draft text do not enter user-visible
diagnostics or logs.

## Build composition

The Gradle root is `android/`; "root" in this section means that build, not the
repository root.

The root build adopts the toolchain proven by the convergence experiment and
includes ASK modules directly. The root `settings.gradle.kts`, wrapper, version
catalog, `gradle.properties`, and root build logic are authoritative. ASK
retains its logical
project paths (`:ime:*`, `:addons:*`, and `:api`) while project directories map
into `android/keyboard/`; this avoids rewriting ASK's internal dependency graph
merely to match its physical nesting. Integration work must explicitly
reconcile:

- ASK's `buildSrc` plugins into root-visible build logic;
- ASK and PersonaSpeak aliases into one root version catalog;
- ASK's build-wide properties into `android/gradle.properties`, resolving both
  directions deliberately: inherited requirements such as Jetifier and
  transitive/non-final `R` behavior remain explicit, while ASK defaults such as
  disabled `BuildConfig` generation must not silently alter PersonaSpeak
  modules;
- root repository/plugin policy, including ASK scripts that currently declare
  repositories from `allprojects` while the root rejects project repositories;
- scripts that assume ASK is `rootDir`, behind one explicit ASK source-root
  value rather than scattered relative-path repairs;
- logical project paths and their physical vendored directories;
- compile, target, and minimum SDK levels plus build variants;
- application ID, manifest merging, resources, and packaging;
- the direct `:ime:app -> :personaspeak-ui -> core-*` dependency graph.

The product settings include the ASK library and pack projects required by
`:ime:app`, but not upstream's standalone language/theme add-on `:apk` projects.
`:ime:app` is the only module applying the Android application plugin in the
shipping graph. Root verification enumerates APK outputs so an inherited or
temporary application cannot hide behind a successful `assemble`.

The nested ASK settings, wrapper, catalog, and root build file remain useful for
provenance and re-vendoring but do not define a second shipping build. The build
is not considered unified merely because all modules compile under the same
wrapper. The dependency graph and runtime seam must execute.

## Migration and merge strategy

The current PR stack is repaired rather than merged in its present order:

1. Commit the reconciled Stitch screen contract and resolve the earlier
   implementation spec's review findings, including its screen counts/size
   contract, selected-provider validation, Keystore wording, privacy copy,
   module map, and simulated onboarding demo.
2. Restack the toolchain experiment onto `main` and narrow its claim to the
   result it proves.
3. Rewrite vendoring as an ingestion slice that names the rejected scaffold
   honestly: move the existing ADR-0001 panel byte-for-byte to
   `android/keyboard-stub/` as `:keyboard-stub`, update `settings.gradle.kts`,
   the temporary root app dependency, and CI in the same slice, and prove only
   that the existing root APK still builds, installs, and registers its legacy
   IME service without crashing. Do not use the panel as a typing or product
   journey. Place the inert ASK snapshot at `android/keyboard/`; ASK modules are
   not included yet.
4. Create `:personaspeak-ui` while the temporary root app and
   `:keyboard-stub` preserve that build/install baseline. The current root app
   contains no design-aligned Compose behavior worth extracting: its local
   try-it field and switcher demonstration belong to the rejected topology.
   Establish the accepted `EditorPort`, source-neutral persona repository, and
   fake-driven coordinator tests as new first-party code. No code or UX is
   extracted from the rejected panel.
5. Land one atomic unified-build integration slice: root-owned build
   composition, ASK's `:ime:app` with PersonaSpeak's application ID, direct
   `:ime:app -> :personaspeak-ui -> core-*` dependencies, a minimal settings
   entry, and the real `EditorPort` adapter. Remove the temporary root
   application and `:keyboard-stub` in the same slice that makes the ASK APK pass
   the typing and editor-seam journey. A release graph containing
   `:keyboard-stub` fails acceptance even if every Gradle task is green.
6. Port onboarding and settings into the first-party Android library one slice
   at a time.
7. Supersede the old panel PR with a real ASK-hosted persona strip and stale-
   editor guard.
8. Land font fidelity with license material; land portraits only with recorded
   rights or replacements.

Sibling UI branches are rebased serially. The single emulator is also used
serially. Independent code review, static analysis, and build preparation may
run in parallel in disposable worktrees.

## Verification

### Deterministic tests

- Existing persona/prompt goldens remain green.
- The first-party library tests rewrite state transitions against a fake
  `EditorPort` and fake providers.
- Stale editor identity, changed text, changed selection, field switch, and
  process/lifecycle cancellation have regression tests.
- Fake-connection tests reject each pre-write operation in turn, verify that no
  delete-then-insert path exists, and exercise every `ReplaceResult`.
- Real-editor tests cover the API-34 single-command path and the older fallback,
  post-write verification, and deliberately scheduled host mutations at the
  final read/write boundary. They verify detected stale states never send a text
  mutation and record `WriteUnconfirmed` where the platform cannot prove the
  outcome; they do not assert nonexistent cross-process atomicity.
- Password/sensitive fields, incomplete editor reads, null connections, and
  oversized targets fail without capture, network traffic, or mutation.
- Provider errors map to the stable UI states above without leaking raw data.
- ASK integration tests exercise the real adapter boundary where practical.

### Visual evidence

- Compose states have screenshot coverage once the selected screenshot harness
  lands.
- Device captures cover onboarding, settings, the resting strip, both pickers,
  loading, success, every error state, long text, and font/portrait fidelity.
- A non-author compares device captures to the committed Stitch target.

### Journey evidence

On a real emulator:

1. install the sole APK;
2. confirm its one IME service is registered;
3. enable and select it;
4. type through ASK keys in an external host app;
5. choose persona and mood;
6. rewrite through a fake provider from the real host editor;
7. verify the host field changes exactly once;
8. reproduce provider-await stale-editor races and verify no mutation command
   is sent; stress the final read/write boundary and record any unconfirmed
   outcome without automatic retry;
9. launch settings from the same package;
10. restore the emulator's prior IME after grading.

The implementer supplies artifacts. A different model family records the
review verdict against the exact commit.

## Definition of done

The architecture is implemented only when:

- one PersonaSpeak APK exists and it is ASK's `:ime:app`;
- no root/demo application is shipped;
- no product switcher or keyless panel exists;
- the unified build and direct module graph are real;
- ASK keys and PersonaSpeak controls work in the same IME window;
- capture-to-commit uses the host field, blocks every detected stale write, and
  exposes rejected or unconfirmed writes without claiming cross-process
  atomicity;
- onboarding, settings, keyboard states, fonts, and approved assets meet the
  Stitch evidence contract;
- privacy copy matches verified behavior;
- automated tests, device evidence, patch notes, CI, and independent review meet
  the repository Definition of Done.
