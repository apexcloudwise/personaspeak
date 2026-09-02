# FlorisBoard vendored snapshot provenance

This file is load-bearing attribution and update procedure. Keep it plain and
in sync with the tree it describes.

## Source

- Upstream: https://github.com/florisboard/florisboard
- Tag: `v0.5.2`
- Commit: `2e82060251897226c0739b9f52d1d051b02305fb`
- Upstream license: Apache-2.0
- Vendored: 2026-09-01

The pinned tree contains `LICENSE` and no root `NOTICE`. Existing source-file
license headers are preserved.

## Why this snapshot exists

ADR-0010: FlorisBoard is vendored as a **second, evaluation IME host** for
the PersonaSpeak layer, parallel to the AnySoftKeyboard host under
`android/keyboard/`. It is NOT the shipping keyboard. The ASK host remains
the default and the release path; this tree exists so the FlorisBoard option
can be built, installed, and judged on a device. Promotion or removal is a
decision gated on the evidence recorded in ADR-0010, not a default.

## Reproduce the pristine snapshot

Clone the tag, verify that it resolves to the commit above, then run:

```bash
git archive --format=tar \
  2e82060251897226c0739b9f52d1d051b02305fb \
  ':(exclude).github' \
  ':(exclude).claude' \
  ':(exclude).gemini' \
  ':(exclude).jules' \
  ':(exclude).devcontainer' \
  ':(exclude)AGENTS.md' \
  ':(exclude)CLAUDE.md' \
  ':(exclude)AI_POLICY.md' \
  ':(exclude)fastlane' \
  ':(exclude)fastlane/*' \
  | tar -x -C <destination>
```

`git archive` never emits `.git/`. The other exclusions are upstream CI,
agent-control, development-environment, and release-management material; they
are not keyboard source, build logic, tests, resources, or license material.
(`AI_POLICY.md` is upstream's AI-contribution governance — the same category
as agent instruction files; importing it would let upstream workflow rules
govern PersonaSpeak work below this directory.)

## Build (second-host APK)

The FlorisBoard tree keeps its **own Gradle root** — it is deliberately NOT
merged into the unified `android/` root (two `buildSrc`-era build logics and
two AGP pins cannot share one root). From `android/florisboard/`:

```bash
export JAVA_HOME=$( /usr/libexec/java_home -v 21 )
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$HOME/.cargo/bin:$PATH" RUSTUP_TOOLCHAIN=1.83.0
./gradlew :app:assembleDebug \
  -PpersonaspeakFlorisAppId=biz.pixelperfectstudios.personaspeak.floris
```

Toolchain requirements beyond the ASK host's: Rust 1.83.0 (rustup) with the
four Android targets — `lib:native`'s CMake step fatal-errors without it —
and the NDK 26.1.10909125 / CMake 4.0.2 / build-tools 35.0.0 pins recorded in
`gradle/tools.versions.toml`. Without the `personaspeakFlorisAppId` property
the build produces the pristine upstream application id.

The first-party PersonaSpeak modules are included into this root from
`../` (see `settings.gradle.kts`) with their build outputs redirected under
`build/personaspeak-build/`, so the two Gradle roots never share a build
directory.

## Re-vendor procedure

1. Resolve the selected release tag to an immutable commit and record both.
2. Extract a fresh pristine tree with the command and exclusions above.
3. Replay every current entry in `UPSTREAM-MODIFIED.md` against the new tree.
4. Preserve PersonaSpeak-owned files under our own packages.
5. Compare the regenerated pristine tree with `android/florisboard/` using
   `diff -rq`; every file differing on both sides must appear exactly once in
   `UPSTREAM-MODIFIED.md`.
6. Update this source record, the rent ledger, license evidence, tests, and
   the patch note in the same PR.

Files present only on the PersonaSpeak side are our additions and are not
upstream modifications. A file whose upstream edit is reverted to pristine is
removed from the ledger; the ledger describes current rent, not history.
