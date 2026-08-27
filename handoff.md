# Handoff — Release Deferred After M8

**Date:** 2026-08-27  
**Main head:** `c453f0f` (`feat(m8): slice B — production usefulness proof, sanitized error surfacing & CI required checks (#114) (#117)`)  
**Product state:** feature-complete release candidate. No `v0.1.0` tag, GitHub release, or official APK exists.

## What Landed

- M2: unified AnySoftKeyboard APK and device qualification receipt (PR #84).
- M3: strip, pickers, settings, typed rewrite states (PR #87/#88).
- M4: secure provider persistence and disabled adapters landed; credential-dependent device gates were formally deferred by the owner (issues #89/#90/#96 closed with that record).
- M5: BYOK provider setup, OpenRouter model browser, onboarding, and per-use secret resolution (PR #104/#105).
- M6: asset-rights manifest, dark/light theme, accessibility, landscape, and RTL readiness (PR #107/#108).
- M7: JVM harness, privacy/egress/backup audit, and the pinned M2-fixture emulator journey (PR #110/#113/#115).
- M8: version `0.1.0`/`versionCode 1000`, release signing configuration, R8 release build proof, usefulness/error harnesses, dependency snapshot, and CI aggregation (PR #116/#117).

## Release Deferral

The owner deferred the public release because no official release signing keystore is provisioned. The repository contains only the signing configuration and a developer test-keystore generator. Without `PERSONASPEAK_RELEASE_KEYSTORE` and its password/alias environment variables, `:ime:app:assembleRelease` produces an unsigned APK. Do not tag a developer or unsigned build as `v0.1.0`.

The CI merge gate is active: GitHub ruleset `main required CI` (id `21630771`) requires strict, up-to-date success for `PATCHNOTES.md touched`, `Validate personas & smoke-test CLI`, `M2 device qualification suite`, and `Milestone 2 gate`. Issues #15 and #16 are closed with API receipts.

## Resume Release

1. Provision the official keystore outside the repository and set `PERSONASPEAK_RELEASE_KEYSTORE`, `PERSONASPEAK_RELEASE_KEYSTORE_PASSWORD`, `PERSONASPEAK_RELEASE_KEY_ALIAS`, and `PERSONASPEAK_RELEASE_KEY_PASSWORD` locally or in protected CI secrets. Never put values in chat, git, issues, screenshots, or receipts.
2. Build `cd android && ./gradlew :ime:app:assembleRelease` from current `main` with those variables.
3. Verify the signed APK and record its SHA-256 certificate fingerprint with `apksigner verify --print-certs <apk>`.
4. Attach the signed APK and provenance to a GitHub release, then create annotated immutable tag `v0.1.0` at the reviewed `main` head. The required CI checks must be green on that exact head.
5. Reopen/close issue #114 only to record the final release artifact, tag, and fingerprint. Update `ROADMAP.md` M8 to complete only after step 4.

## Carry-Forwards

- `personaspeak-proto/` stays on disk as owner-requested reference only; it is not a merge base.
- M4 live credential/device gates remain deferred, not satisfied. Revisit requires explicit owner approval and a new reviewed scope.
- If release copy changes, retain ADR-0009's accurate OpenRouter proxy disclosure: prompts transit OpenRouter before its downstream provider.
