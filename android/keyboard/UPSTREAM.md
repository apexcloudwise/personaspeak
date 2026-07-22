# AnySoftKeyboard vendored snapshot provenance

This file is load-bearing attribution and update procedure. Keep it plain and
in sync with the tree it describes.

## Source

- Upstream: https://github.com/AnySoftKeyboard/AnySoftKeyboard
- Tag: `1.13-r1`
- Commit: `8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d`
- Upstream license: Apache-2.0
- Vendored: 2026-07-22

The pinned tree contains `LICENSE` and no root `NOTICE`. Existing source-file
license headers are preserved.

## Reproduce the pristine snapshot

Clone the tag, verify that it resolves to the commit above, then run:

```bash
git archive --format=tar \
  8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d \
  ':(exclude).github' \
  ':(exclude).claude' \
  ':(exclude).gemini' \
  ':(exclude).jules' \
  ':(exclude).devcontainer' \
  ':(exclude)AGENTS.md' \
  ':(exclude)CLAUDE.md' \
  ':(exclude)fastlane' \
  ':(exclude)fastlane/*' \
  | tar -x -C <destination>
```

`git archive` never emits `.git/`. The other exclusions are upstream CI,
agent-control, development-environment, and release-management material; they
are not keyboard source, build logic, tests, resources, or license material.

## Re-vendor procedure

1. Resolve the selected release tag to an immutable commit and record both.
2. Extract a fresh pristine tree with the command and exclusions above.
3. Replay every current entry in `UPSTREAM-MODIFIED.md` against the new tree.
4. Preserve PersonaSpeak-owned files under our own packages.
5. Compare the regenerated pristine tree with `android/keyboard/` using
   `diff -rq`; every file differing on both sides must appear exactly once in
   `UPSTREAM-MODIFIED.md`.
6. Update this source record, the rent ledger, license evidence, tests, and the
   patch note in the same PR.

Files present only on the PersonaSpeak side are our additions and are not
upstream modifications. A file whose upstream edit is reverted to pristine is
removed from the ledger; the ledger describes current rent, not history.
