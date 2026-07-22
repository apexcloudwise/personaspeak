# Full emulator demo roadmap

**Date:** 2026-07-22

**Status:** Owner-approved sequence (2026-07-22)

**Tracker:** [issue #38](https://github.com/apexcloudwise/personaspeak/issues/38)

**Authority:** [ADR-0006](../../adr/0006-gradle-composition-for-the-graft.md) ·
[single-APK design](2026-07-22-single-apk-ask-integration-design.md)

## Current checkpoint

PR #37 merged the pinned AnySoftKeyboard `1.13-r1` snapshot at
`81835ebd22dad97dd19e4fbef31a240341ba545b`. ASK remains outside the Gradle
graph. The temporary root `:app` and rejected `:keyboard-stub` still provide
the pre-cutover build, install, and IME-registration baseline; they are not
typing or product evidence.

## Sequence

| Milestone | Mergeable result | Required evidence |
|---|---|---|
| 0A — truthful docs | README, ROADMAP, and the older stale-field spec agree with accepted ADRs and current code. | Link and placeholder checks; prose review. |
| 0B — Stitch contract | A committed screen/state contract reconciles the preserved exports without importing the transport zip or export directory wholesale. | Every requested screen maps to a route/state or named adaptation; prior review findings resolved. |
| 1 — first-party boundary | `:personaspeak-ui` contains the accepted `EditorPort`, rewrite coordinator, source-neutral persona repository, bundled-persona adapter, runtime validation, and fake-driven tests. ASK stays inert. | Current APK and stub AAR still build; the new UI AAR and all unit/golden tests pass. |
| 2 — atomic ASK cutover | The root-owned unified build produces ASK `:ime:app` as the only APK. The real editor/view adapters work; temporary `:app` and `:keyboard-stub` are deleted. | Install, registration, real ASK typing, fake-provider capture/replace, settings launch, exact one-APK enumeration, dictionary licenses, and upstream-rent ledger. |
| 3 — keyboard product flow | Permanent strip, in-view persona/mood pickers, loading/cancel, result actions, and typed error/stale/unconfirmed states run over real ASK keys. | State tests, stale-race device tests, screenshots, and external-host journey. |
| 4 — state, security, providers | Package settings, provider configuration, Keystore-held encryption key, ciphertext persistence, backup exclusions, and separately reviewed providers replace demo-only configuration. | Process-death tests, provider contract tests, secret/log scans, backup inspection, and truthful copy. |
| 5 — onboarding and settings | The Stitch welcome, enable/select, provider/key, guided real-ASK demo, persona, provider, privacy, appearance, and inherited typing settings are complete. Review-before-replace remains fixed behavior. | Fresh-install guided-demo recording including `Use this` and verified host-field replacement, navigation tests, and screen captures. |
| 6 — fidelity and edges | Licensed fonts, cleared or replaced portraits, dark/landscape/long-text/accessibility behavior, and missing error adaptations meet the visual contract. | Font notices, asset-rights record, accessibility checks, and side-by-side captures. |
| 7 — acceptance | A release candidate completes the full internal and external-host journey and the runtime privacy audit. | One-APK recording, dependency/network/backup evidence, stale/unconfirmed outcomes, green CI, and non-author review. |

Milestone 2 is intentionally atomic at merge time. Its build-logic and adapter
work may be reviewed as separate commits in an isolated branch, but `main` does
not lose the temporary application until the ASK APK passes the complete
cutover gate.

## Maintainability constraints

- Dependency direction remains `:ime:app -> :personaspeak-ui -> core-*`.
- `core-personas` owns schema parsing, validation, identity, and prompt
  construction; `core-providers` owns provider contracts. Neither imports
  Android or ASK.
- UI, editor coordination, provider coordination, persona catalog access,
  active selection, downloaded content storage, and network transport are
  separate interfaces or components. One class does not acquire the set for
  convenience.
- ASK adapters implement first-party ports. First-party modules never import
  inherited ASK implementation types.
- Drafts, prompts, and provider results are request-scoped in-memory values,
  never catalog or settings data.
- Every externally supplied persona is parsed and validated before it can enter
  the active catalog. Display names are not identities.

## Future persona distribution

Milestone 1 supplies the seam, not the marketplace. Bundled personas are the
first repository implementation. A later marketplace or local-import feature
may add implementations for package discovery and installed content without
changing the keyboard UI, rewrite coordinator, editor adapter, or provider
contracts.

The later design must decide package identity and versioning, provenance,
licenses, signatures, moderation, trust presentation, downloads, updates,
deletion, backup behavior, and failure recovery in its own ADR/spec. Those
decisions do not belong in the v1 YAML content schema merely because a server
might someday carry the file.

## Explicit exclusions

- no switch-to/switch-back workflow;
- no keyless PersonaSpeak IME;
- no IME-local draft field or panel back button;
- no second application APK;
- no focus-taking picker windows;
- no portrait assets without recorded redistribution rights;
- no font files without their license notices;
- no automatic retry after an unconfirmed editor write.
