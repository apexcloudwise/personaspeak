# PersonaSpeak production route design

**Date:** 2026-07-29

**Status:** Owner-approved design

**Public milestone authorities:** [ROADMAP.md](../../../ROADMAP.md) ·
[issue #38](https://github.com/apexcloudwise/personaspeak/issues/38)

**Existing architecture:** [ADR-0006](../../adr/0006-gradle-composition-for-the-graft.md) ·
[single-APK design](2026-07-22-single-apk-ask-integration-design.md) ·
[full emulator roadmap](2026-07-22-full-emulator-demo-roadmap.md)

## Outcome

PersonaSpeak reaches an **Installable M8**: one signed, reproducible,
provenance-bearing ASK-based APK that can be installed and upgraded, types
normally without AI, rewrites through a selected real provider, fails safely,
and has exact-device, privacy, accessibility, and release-workflow evidence.

This is not store distribution. Play, F-Droid, store metadata, update
infrastructure, and public maturity claims remain outside this route. Phase 2
suggested replies and Phase 3 marketplace work remain outside it too. We are
finishing one keyboard before inviting it to acquire hobbies.

This document records the approved route and its gates. `ROADMAP.md` remains
the sole public Phase-to-milestone map, and issue #38 remains the live tracker.
Neither is duplicated here.

## Provenance and current truth

The design was reconciled against:

- baseline `origin/main` at
  `ed1b723088a69998e25d3703eb00e052b49a524f`;
- accepted M2 integration head
  `ba2b1b2118744a1db04cacace0437ab9e04eca9a` on
  `feat/issue-47-atomic-ask-cutover`;
- accepted corrective plan
  `docs/superpowers/plans/2026-07-24-dedicated-personaspeak-row.md`, SHA-256
  `3a28cf0cfb31690d305bdd0baad1806fd68e8812705fe1ae4db8f99078cff7e2`;
- pinned ASK `1.13-r1` source revision
  `8c1db51c...`, recorded in `android/keyboard/UPSTREAM.md`;
- the static ASK privacy inventory in
  `docs/privacy/anysoftkeyboard-1.13-r1-inventory.md`;
- all 24 preserved Stitch exports and their committed screen contract.

Stage 0A/0B independently re-established the repository references before this
document was written. The canonical repository is attached to
`docs/personaspeak-production-route`, based directly on `ed1b723`. The
integration branch is tracked-clean at `ba2b1b2`, and its remote ref is equal
to that full SHA.

Repository facts that later milestones must not mistake for completed work:

- the merged manifest does not yet request `android.permission.INTERNET`;
- the ASK manifest does not yet declare `android:supportsRtl="true"`;
- current production composition constructs `FakeProvider` unconditionally;
- `core-providers` contains only `CompletionProvider` and a success-only
  `FakeProvider`, with no real adapter or injected HTTP transport;
- launcher routing still belongs to inherited ASK activities and must preserve
  `SetupSupport`'s pre-34/API 34 enable/default checks;
- ASK's optional version machinery still carries ASK 1.13 build-counter and
  F-Droid lineage;
- release Gradle configuration is formed by merged `buildTypes` blocks, has no
  first-party PersonaSpeak R8 keep rules, and can discover shared `/tmp`
  signing or publishing credentials;
- dependency versions are pinned in places, but dependency locking does not
  exist.

## Delivery shape

The route is a serial gate-and-spine sequence: Stage 0, then M2 through M8.
Planning may run at most one milestone ahead. Implementation does not. A later
milestone may discover a defect in an earlier one; it may not quietly absorb
the repair and rename the result foresight.

Each mergeable slice must leave `main` demoable, carry its tests and patch note,
preserve the pure-module direction, and receive a different-model-family
non-author review. M2 remains one atomic pull request even though its internal
commits and leases are reviewed serially.

## Governance and evidence contract

Sigrid is overseer and gate-acceptance seat. Cassie is architectural
concurrence. Seraph coordinates worker leases. Every merge panel has a third,
distinct, different-family non-author reviewer whose eligibility is recalculated
from the actual authorship at lease time. One agent never occupies two seats.

Every worker lease includes this sentence verbatim:

> Preflight: ask your delegator about ambiguity, blockers, scope expansion,
> conflicts, or unverifiable assumptions; else proceed.

Delegated handbacks mention the delegating agent. No next serial lease begins
until the exact SHA, raw evidence, and non-author verdict for the preceding
lease are accepted.

Acceptance uses one final clean-HEAD rerun as its sole evidence. Earlier runs
are diagnostic. The final receipt contains complete raw logs and mechanically
derived counts; screenshots and films are artifacts, not verdicts. The existing
evidence ladder remains authoritative:

1. Prompt goldens.
2. Committed, deliberately regenerated Paparazzi screenshot goldens.
3. Machine-asserted external-host journeys with UI hierarchy and field-change
   evidence.
4. Film as review and demonstration material.

The older orphan evidence-branch recommendation must be reconciled with the
newer private scan, quarantine, and promotion policy before large artifacts are
published. Small reviewed goldens stay in-tree. There will not be two evidence
standards conducting parallel investigations into the same APK.

If a runtime cause is unproven, the next lease is confirmation-only and adds
behavior-neutral instrumentation. Confirmation and correction never share a
lease. Any contradiction, drift, dirty surprise, count mismatch, scope
expansion, or unverifiable assumption stops the route.

Hosted GitHub Actions is attempted and its exact result captured. Quota or
billing denial is `unavailable`, never passed or failed. Stage 0 through M7 may
use the owner-approved local equivalent on the exact clean HEAD: every workflow
command, complete logs, mechanical counts, and workflow syntax validation.
`act` is supplemental unless event/toolchain parity and secret safety are
proved. Installable M8 cannot complete without its tag/release workflow running
in a genuine GitHub event environment, hosted or owner-approved self-hosted.

## Stage 0 — governance and workspace integrity

Stage 0 makes the repository and authority chain truthful before product work
continues.

### 0A — authority and session records

- Create fresh, separate overseer and co-designer sessions through the
  agent-operations protocol. Closed sessions remain closed.
- Record Sigrid as overseer, Cassie as co-designer, and Seraph as coordinator.
- Record Installable M8 as the terminal state, the three-seat panel, and zero
  other active leases.
- Record the prior quarantine artifact as preserve/ignore and prohibited from
  inspection, copying, promotion, deletion, or reliance. Its later absence
  means only that the previously recorded path is presently absent; it does
  not prove an authorised disposition.

### 0B — workspace repair

- Mechanically inventory every worktree with path, branch, HEAD, tracked and
  untracked state, upstream, and unique commits.
- Leave dirty or unverifiable worktrees untouched. Preserve all non-target
  worktrees.
- Remove only `ask-plan-gate-fix` after proving it clean, untracked-free, and
  zero commits ahead of its base.
- Attach canonical `main` and fast-forward only to live `origin/main`, while
  preserving the canonical untracked Stitch/reference artifacts.
- Prove the integration worktree tracked-clean at `ba2b1b2`, verify the plan
  hash, prove remote `0d3202b` is an ancestor, push the already accepted four
  commits without force, and verify exact local/remote equality.
- Create `docs/personaspeak-production-route` directly from repaired
  `ed1b723`.

### 0C — one governance transaction

The docs branch carries one serially authored governance pull request. It adds
this design and its patch note, then synchronizes `ROADMAP.md`, issue #38, the
handoff, governance truth, and final receipts. `ROADMAP.md` is the only public
Phase-to-milestone mapping artifact. Issue #38 gains M8 and the same accepted
gates. Stale checklist items are corrected rather than ceremonially preserved.

The PR is docs-only, uses a real patch-note entry, receives a different-family
non-author grade, and has green CI. A red check blocks and escalates. Only
demonstrable quota/billing unavailability invokes the approved local fallback.

## M2 — complete the atomic ASK cutover

Issue #47 remains the M2 authority. Tasks 1 through 6 are accepted. Task 7 is
reopened only for the separately measured PersonaSpeak row above untouched ASK
suggestions and keys. Tasks 8 through 10 remain blocked until Task 7 passes.

The accepted corrective plan remains immutable except for its binding Task 4
amendment: verbose ASK content-log lines are filtered from device evidence.
Hard-coded reviewer-family wording in Task 10 is superseded by actual
authorship eligibility.

### M2.7 — dedicated row, four serial commits

1. Add the generic ASK extension-row seam, tests, and same-commit
   `UPSTREAM-MODIFIED.md` ledger entry.
2. Migrate the PersonaSpeak host and add lifecycle tests.
3. Add accessible per-state geometry, review-height policy, and state tests.
4. Run the preliminary full gate, then a separately leased device
   qualification, receipt commit, and final clean-HEAD corrective rerun.

Implementation and device authority never coexist. The measured row may grow
upward for pickers and review, but it does not cover or displace ASK candidate
suggestions or keys.

### M2.8 — atomic topology cutover

Delete the root `android/app` and `keyboard-stub`. ASK `:ime:app` becomes the
sole application. Root CI covers ASK, core, and UI tests; lint; assemble;
dependency closure; licenses; upstream ledger; topology; static analysis;
exactly one APK; exact canonical APK path; and artifact upload. This is a
blocking M2 exit gate, not CI work borrowed early from M8.

Deleting the temporary app removes the only first-party `MainActivity`.
ASK's `LauncherSettingsActivity` keeps settings launch satisfiable during M2;
M5 deliberately adds the owned host.

### M2.9 — shared-device proof

Use the shared `CityZen_Dev` resource only under an exclusive lease. Record
serial and initial stopped/running state; prove no concurrent claim; never wipe
the device. Evidence covers install, IME registration, ASK typing, untouched
suggestions and keys, every PersonaSpeak state, fake-provider capture and
replacement, settings launch, package identity, and APK hash.

The lease installs its restoration trap before mutation. Every success,
failure, signal, abort, and contradiction restores the prior IME, prior
accepted APK/state on-device, the accepted APK/state at the canonical output
path, and the emulator's initial stopped/running state. Logs use synthetic
content, priority/tag filtering, and retain no typed content.

### M2.10 — qualify and merge

After Stage 0 establishes the baseline, push new M2 commits by normal
fast-forward. Run local gates, open a draft PR, and use the first converted PR
CI run as qualification. Exact-head green CI precedes final acceptance,
ready-for-review, panel consensus, and merge.

The PR carries a real `PATCHNOTES.md` entry. Its body says `Closes #47`,
references roadmap issue #38, and never closes #38.

## M3 — keyboard product flow

`:personaspeak-ui` owns a plain-state UI and state machine. ASK supplies only
first-party editor and view-host adapters. PersonaSpeak remains its separately
measured row above untouched ASK candidates and keys. Pickers expand inside the
IME-owned input view, never through focus-taking windows. Drafts, prompts,
responses, and snapshots remain request-scoped memory.

### M3A — typed state machine

Model idle and picker states; cancellable loading; review with `Use this`,
`Again`, and `Dismiss`; applying and `AppliedVerified`; capture refusals;
offline, authentication, quota, timeout, malformed, and safe unknown failures;
`Stale`; `WriteRejected`; and `WriteUnconfirmed`.

Cancellation wins cancellation/deadline races. Once a timeout wins, late
provider results are discarded. Controlled interleavings prove exactly one
terminal outcome and no late state mutation.

### M3B — accessible surfaces

M3B is three serial, one-commit reviewed sub-slices:

1. Add the library resource root and copy ownership, a structural
   `PersonaSpeakTheme`, explicit host-supplied light/dark input, and theme every
   composition wrapper.
2. Add semantics, traversal, announcements, and touch-target assertions.
3. Add the strip, in-view 2x2 persona and mood pickers, loading/cancel, review,
   and typed failure surfaces using those foundations.

Before M3B, inventory all 24 preserved Stitch screens and map each to reuse,
adapt, reject, or missing. Existing exports are a visual north star, not an
executable requirement when they conflict with ASK geometry, privacy,
accessibility, licensing, or state safety. Create only the missing delta
targets: Loading/Cancel, Timeout, stale, write-rejected, write-unconfirmed, and
post-Use confirmation.

### M3C — visual backbone

Paparazzi exists only in `:personaspeak-ui`; it never enters `:ime:app` or the
APK. Pin Paparazzi/LayoutLib, AGP, compile SDK, Kotlin, Compose BOM, JDK,
locale, density, theme, and dimensions. No production font exists yet, so M3
keeps a small system/LayoutLib-font golden set. M6 deliberately regenerates it
after licensed typography lands. Goldens never regenerate automatically in CI.

### M3D — integrated journey

A restoration-trapped external-host journey proves real ASK keys, every
interactive state, race handling, host-field replacement, and no-AI typing.
The final clean-HEAD evidence rules and device restoration contract apply.

M3 mechanically rejects HTTP/network dependencies in both `core-providers` and
`:personaspeak-ui`. Providers arrive in M4, not disguised as convenient test
fixtures.

## M4 — state, security, and real providers

M4 produces durable package settings, Keystore-backed credential encryption,
selectable Gemini/Anthropic/OpenAI/OpenRouter adapters, and a real active
composition. `core-providers` remains pure Kotlin with typed outcomes,
provider descriptors, and an injected transport. Android persistence,
encryption, and composition live in new first-party packages under `:ime:app`.
The UI never receives key material.

### M4A — contract and dependency posture

Add the typed provider contract, injected transport seam, and ADR for the
provider egress/privacy transition. Pin and audit the approved HTTP and JSON
dependencies; OkHttp plus kotlinx.serialization is the recommended bounded
choice. JSON may ignore unknown fields but must require and validate every
field the product uses.

M4A does not add `INTERNET`. Deterministic adapter acceptance uses controlled
transports through the production path.

### M4B — secure package state

One repository persists persona, mood, active provider, provider configuration,
and ordinary preferences. It never persists drafts, prompts, results, or editor
snapshots.

Credentials use a non-exportable Keystore key and authenticated encryption.
Storage contains ciphertext, nonce, version, and minimal metadata. Copy says
the credential is encrypted using a key held by Android Keystore; it does not
claim the credential itself is in Keystore or hardware-backed without proof.
Corruption and key invalidation fail closed.

Neutralize inherited `allowBackup=true` and `fullBackupOnly=true`. Supply both
legacy `fullBackupContent` and API 31+ `dataExtractionRules` exclusions, deny
cloud backup and device-to-device transfer for sensitive state, inspect
inactive backup-agent metadata, and assert actual paths. Wildcard-looking
exclusions are not evidence.

### M4C–M4F — provider adapters

Implement Gemini, Anthropic, OpenAI, and OpenRouter serially. Each adapter gets
controlled transport contract tests for request shape, success parsing,
authentication, quota, timeout, malformed payload, cancellation, and redacted
failure handling.

All egress is HTTPS to the selected provider's allowlisted host. Redirects are
validated. Deadlines are bounded. Request/response bodies, keys, and typed text
never enter logs or telemetry.

### M4G — active composition and qualification

Activate a selected real provider in production composition and only then add
`android.permission.INTERNET`. Assert the merged manifest, cleartext denial,
and selected-host socket behavior. `FakeProvider` remains available for tests
and demos but is never a release-eligible active composition.

Evidence keeps three things distinct:

1. Real adapter plus controlled transport: repeatable production-path contract
   proof and deterministic gate.
2. `FakeProvider`: tests and demos only.
3. Real adapter plus live transport: separately leased, sanitized
   credential/quota/network exercise, never a deterministic gate substitute.

The live environment is selected before its lease. Prefer a
restoration-trapped emulator with safe interactive credential entry. A genuine
GitHub event may be used only with approved encrypted secrets and proven
redaction. PersonaSpeak never reads or transmits contacts.

## M5 — onboarding and settings host

Add one first-party Compose Activity in `:ime:app` as the launcher, onboarding,
and PersonaSpeak settings host. It consumes the M4 repository. Inherited ASK
typing and appearance settings remain available behind owned routes.

### Launcher and navigation contract

The PersonaSpeak host deliberately subsumes ASK's launcher first-run policy.
Move the launcher intent filter and shortcut metadata to it. Keep
`SetupWizardActivity` and its five internal referrers for inherited/internal
compatibility, but not as the product launcher. Reuse
`SetupSupport.isThisKeyboardEnabled(Context)` and
`isThisKeyboardSetAsDefaultIME(Context)`, including their pre-34/API 34
behavior.

`LauncherSettingsActivity` loses its launcher role and becomes non-exported.
Four static ASK shortcuts retarget to typed host deep links behind the
onboarding/system-truth gate and then reach the inherited destinations. Tests
cover all shortcuts, cold start, warm re-entry, process recreation, saved
navigation, deep links, Back, settings/chooser return, obsolete external
explicit-intent failure, cached-component failure, and internal navigation.
Every inherited manifest and shortcut edit enters the upstream ledger.

### Onboarding and settings

Onboarding presents a truthful welcome, Android enable settings, the system IME
chooser, provider choice, masked key set/test/replace/delete, and a real
Activity-hosted editor demo using the installed ASK keys, real strip, and real
provider. It never simulates the keyboard or uses `FakeProvider`. Provider and
key setup are skippable; normal typing remains usable.

Completion is derived from live system state on every resume, not from a
ceremonial boolean that becomes false the moment Android changes its mind.

Settings covers persona, mood, provider, credential management, inherited
typing and appearance, privacy, and local data. It rejects Stitch's immediate
replacement and shake undo, fake usage counter, absolute “store nothing,”
unqualified offline/free claims, and persisted-key re-reveal. Privacy and key
copy map to accepted contracts in a copy-to-claim receipt.

M5 uses a bounded Paparazzi set. M6 owns complete visual fidelity.

## M6 — fidelity and edge conditions

PersonaSpeak copy ships in English with explicit fallback disclosure. Real RTL
and pseudolocale correctness are required; mass machine translation is not.
ASK has 85 locale directories, including four RTL directories, but RTL evidence
does not become valid until the manifest opts in.

### M6A — provenance and licenses

Record redistribution rights and license notices for Outfit 600, Inter 400/500,
icons, and every consumed asset before adding them. Persona portraits ship only
with recorded rights or rights-clear replacements. Otherwise use accessible
text/emoji and document the deliberate divergence.

### M6B — stable visual tokens

Refine stable theme-token values and licensed typography. Perform one bounded,
reviewed golden transition from the M3 system-font baseline. M6 qualifies M3's
theme and semantics; it does not backfill foundations that should already
exist.

### M6C — responsive, accessible, and RTL geometry

Add `android:supportsRtl="true"` and its same-commit upstream ledger entry.
Qualify light/dark, portrait/landscape, long persona names, large text, touch
targets, traversal, announcements, contrast, RTL, pseudolocale, and the ASK
routes reached through M5. Review and picker surfaces never cover or shift the
keys.

### M6D–M6E — visual matrix and device proof

Render the complete edge-state matrix. Celebration particles are explicitly
rejected; a successful verified write does not require confetti to become
true. Then run restoration-trapped exact-device qualification with machine
verdict inputs and a non-author side-by-side visual comparison.

## M7 — privacy and whole-product acceptance

M7 extends the existing pinned 344-line ASK privacy inventory. It does not
duplicate or re-audit unchanged ASK source for the pleasure of producing a
second clipboard. Reconcile `UPSTREAM-MODIFIED.md`, the integrated dependency
graph, and additive M4/M5 code; close the inventory's pending neutralizations
and questions. Create the missing audit issue linking ADR-0005, PR #17, the
inventory, issue #38, and the pinned ASK SHA.

### M7A–M7C — protocol, defaults, and claims

- Preapprove a privacy-safe capture protocol before device work.
- Retain ASK's auto-learning threshold of 9 and prove its disable route.
- Retain the private fallback dictionary.
- Disclose exactly: promoted words are deletable; next-word data has a
  separate clear action; pre-promotion auto-dictionary data has no dedicated
  clear and clears only with app data.
- Do not invent a silent unified clear control. M5 Privacy/Local Data may route
  to inherited editors and Android App Info with explicit scope.
- Remove contacts permission and its entrance unless a concrete requirement is
  approved.
- Disable release crash-file/report flow without an owned redacted destination.
- Replace the inherited external privacy-policy URL with an internal typed
  route to M5's evidence-backed Privacy screen. A stable HTTPS policy remains
  store-submission work.
- Add lint and no-sensitive-log gates. The reconciled baseline has zero direct
  first-party `android.util.Log` calls.

One evidence-mapped truthful claim set governs onboarding, settings, README,
and release copy.

### M7D — runtime privacy qualification

Use restoration-trapped per-UID network metadata, storage, backup, APK,
dependency, native-library, and log evidence with synthetic canaries. Capture
no payloads, keys, contacts, clipboard content, drafts, or prompts. A capture
blind spot is `unverified`, never “zero egress.”

### M7E — whole-product acceptance

Run a bounded matrix across internal and external hosts, states, lifecycle,
races, accessibility, geometry, and stress. The sole final clean-HEAD rerun,
complete logs, mechanical counts, and three-seat panel rules apply. M8 later
repeats release-scoped claims against the exact signed APK.

## M8 — installable release readiness

M8 produces one reproducible, signed, provenance-bearing APK. It does not
publish to a store or call a local bundle a release because it felt official.

PersonaSpeak uses stable tags `vMAJOR.MINOR.PATCH`, beginning with `v0.1.0`.
`versionName` is `MAJOR.MINOR.PATCH`; `versionCode` is
`MAJOR * 1,000,000 + MINOR * 1,000 + PATCH`. Each component is an integer from
0 through 999. Suffixes, malformed or out-of-range components, collisions,
non-increasing versions, and overflow fail closed. A pushed tag is immutable;
a defect requiring a new commit consumes a new version. An exact-SHA genuine
`workflow_dispatch` qualification precedes the stable tag.

### M8A — owned release identity

Add tested version/tag mapping and a clear development identity for debug
builds. Release requires a validated tag. Disable ASK counter/version lineage,
F-Droid generation, Play activation and `/tmp/apk_upload_key.json`, and
canonical AAB copy/finalization. An ordinary local `bundleRelease` remains an
explicitly unqualified capability, not a deliverable. Ledger inherited edits
and add first-party PersonaSpeak R8 keep rules.

### M8B — fail-closed signing

Signing inputs are explicit. No ambient or shared `/tmp` discovery exists. The
repository pins the public signing-certificate SHA-256. A local keystore path
is explicit and outside the repository. The protected Actions environment
materializes key material only under per-run runner temp with narrow
permissions and unconditional cleanup. Secrets never appear in arguments,
Gradle properties, artifacts, caches, or logs. Missing inputs, certificate
mismatch, or multiple signers block the release. Provisioning and rotation
require separate owner approval and may legitimately block M8.

### M8C — reproducibility and provenance

Two isolated clean builds of the exact tag run signed `assembleRelease` and
must produce byte-identical APKs. A mismatch triggers behavior-neutral
diagnosis before a separately reviewed fix.

Exactly one APK exists at
`android/keyboard/ime/app/build/outputs/apk/release/app-release.apk`. Verify
signer, package, version, min/target SDK, non-debuggable state, components,
exported surfaces, permissions, cleartext denial, absence of publishing
payload, and real active provider composition. The `FakeProvider` class may
remain in test/demo code; release composition selecting fake or stub fails.

Provenance binds tag, commit, repository, genuine run/event, toolchains,
commands, APK path/size/hash, certificate hash, manifest, dependency
lock/resolution, and every gate result. It contains no sensitive data and no
mutable `latest`. Dependency locking is a prerequisite. The release Gradle
blocks merge: inherited `debuggable=false`, `zipAlignEnabled=true`, and
conditional signing remain relevant while app release configuration adds
`minifyEnabled=true` and its four ProGuard files. First-party R8 reachability
and reproducibility are explicit risks. A canary build is release-shaped, never
canonical.

### M8D — genuine event workflow

One pinned-action workflow serves guarded exact-SHA `workflow_dispatch` and
stable tag push. The tag must match the version and exact qualified `main`
commit. The protected environment runs all gates and uploads only the APK,
provenance, and sanitized complete logs. A genuine tag event is mandatory;
quota/billing denial is `unavailable`, and local or `act` runs remain
supplemental.

Close CI issues #15 and #16 with exact-head required checks, pull-request
`synchronize` semantics, stable check names, concurrency/current-SHA
protection, and repository-ruleset evidence.

### M8E — signed-APK device proof

Install the exact workflow APK under the restoration contract. Prove fresh
install, signer/version, IME registration, M5 host, ASK typing without AI, and
upgrade. The first release may use a clearly labelled lower-version fixture
built from the same source and signer; later releases use the actual prior
accepted artifact. Prove downgrade and wrong-signer rejection.

The exact signed APK must perform one successful rewrite through active
production composition and a real provider/live transport, plus one controlled
failure rendered as a safe user-presentable message. Raw exceptions and stack
traces never appear. Interactive credentials are not captured; logs are
synthetic and filtered. Any uncertain cause stops for a confirmation-only,
behavior-neutral instrumentation lease.

Every exit restores the prior IME, prior accepted APK/state on-device, accepted
APK/state at the canonical output path, and the emulator's initial running
state.

### M8F — terminal acceptance

Run the sole final clean-HEAD/tag evidence pass. The receipt binds the genuine
tag artifact to the exact device-installed hash and includes complete raw logs
and mechanical counts. Sigrid, Cassie, and a separate eligible different-family
non-author reviewer must agree on identity, green checks, reproducibility,
install/upgrade, usefulness, privacy, and restoration.

Only then may ROADMAP, issue #38, and patch notes mark Installable M8 accepted.
They must not claim store availability. Signing, tag, reproducibility, artifact,
fake-composition, workflow, privacy, restoration, or panel failures stop the
route at M7.

## Route-wide stop conditions

Stop before mutation or acceptance on:

- branch, HEAD, plan-hash, or remote-ref drift;
- dirty or conflicting state outside an explicitly accepted artifact set;
- lease overlap, scope expansion, or reviewer ineligibility;
- raw-log incompleteness or count/hash mismatch;
- upstream-ledger, license, dependency-closure, or topology mismatch;
- sensitive logging, payload capture, credential exposure, or backup leakage;
- device exclusivity or restoration failure;
- unproven runtime explanation presented as fact;
- hosted CI red status, or M8 without a genuine event run;
- unsigned, multiply signed, non-reproducible, ambiguously versioned, or
  fake-provider release composition;
- any attempt to close issue #38 before the terminal gate actually passes.

The safe state is the last accepted milestone. Progress is useful. A keyboard
that merely has the paperwork for progress is still just paperwork.
