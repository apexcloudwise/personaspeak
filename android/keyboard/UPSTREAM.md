# UPSTREAM.md — AnySoftKeyboard vendored snapshot provenance

This is load-bearing text. Strip every joke elsewhere; this file has none.
Keep it plain, keep it accurate, keep it in sync with the tree it describes.

## Source

- **Upstream repo:** https://github.com/AnySoftKeyboard/AnySoftKeyboard
- **Pinned tag:** `1.13-r1`
- **Pinned commit SHA:** `8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d`
- **Published:** 2026-02-08 (per the GitHub releases API; tag resolved via the
  API, not by string-sorting git tags — see
  `docs/superpowers/specs/2026-07-20-fork-spike-results.md`).
- **Upstream license:** Apache-2.0. `LICENSE` and `NOTICE` (where present) are
  preserved verbatim at the root of this tree. Per-file Apache-2.0 header
  comments are preserved verbatim in every source file that carried them.

## Vendored on

2026-07-21.

## How the pristine tree was produced

Reproducible from the recorded tag and exclusion list. From a fresh clone:

```sh
git clone --depth 1 --branch 1.13-r1 \
    https://github.com/AnySoftKeyboard/AnySoftKeyboard.git ask
cd ask
# Verify the SHA matches the pin above before archiving.
git rev-parse HEAD
# Produce the archive, excluding the dirs below, and extract in place.
git archive --format=tar 1.13-r1 \
    ':(exclude).github' \
    ':(exclude)fastlane' \
    ':(exclude)fastlane/*' \
    | tar -x -C <destination>
```

`git archive` never includes `.git/`, so it is implicitly excluded — no
`:(exclude).git` is needed (and `git archive` rejects it). The shallow clone
exists only so `git archive` has a tree to read; the resulting snapshot
carries no upstream git history.

## Exclusion list

The snapshot excludes upstream repo-management and CI directories only.
Source, build logic, tests, resources, and per-file license headers are
preserved verbatim.

| Path excluded | Reason |
|---|---|
| `.git/` | Implicit — `git archive` never emits it. Upstream git history is not imported. |
| `.github/` | Upstream repo management: GitHub Actions workflows, `CODEOWNERS`, `FUNDING.yml`, `renovate.json`, `actionlint.yaml`, and composite actions under `.github/actions/`. These describe how the upstream repo is run, not how ASK is built or how the app behaves. |
| `fastlane/` | Not present at this tag — recorded for completeness so the exclusion list is stable across future re-vendors. If a future ASK release ships a `fastlane/` metadata dir, exclude it then; it is upstream release-tooling, not source. |

**What was deliberately kept** (it is upstream's, not ours, but it is part of
the source tree, not repo management):

- `.claude/`, `.gemini/`, `.jules/`, `.devcontainer/` — upstream's AI-agent
  and dev-container configs. Inert in our build; kept to keep the snapshot
  faithful and the diff against the pristine archive empty.
- `.bazelrc`, `.bazelversion`, `BUILD.bazel`, `MODULE.bazel`,
  `MODULE.bazel.lock`, `WORKSPACE.bazel`, `WORKSPACE.bzlmod`,
  `maven_install.json`, `NextWord$NextWordComparator.class` — upstream's
  alternate Bazel build (and one stray compiled `.class` at the repo root).
  Our build is Gradle; these files do not participate. Kept pristine.
- `AGENTS.md`, `CLAUDE.md` at this tree's root — upstream's own house-rule
  files for their AI agents. They do not conflict with our repo-root
  `AGENTS.md` (different path).

## Re-vendor procedure

Updating this snapshot to a future ASK release is a manual, documented
procedure, not a command — accepted as the cost of ADR-0003's
upstream-merge subscription.

1. Pick the new tag. Resolve it via the GitHub releases API, not by
   string-sorting git tags — AnySoftKeyboard's release series (`1.13-r1`,
   `1.13-r2`, …) does not sort against its legacy `vNNN` series.
2. Shallow-clone the new tag into a scratch dir and record its SHA.
3. Produce a pristine archive using the exact `git archive` command and
   exclusion list above, into a fresh scratch dir.
4. Run `diff -rq <scratch-pristine> android/keyboard/` and read every line.
   Each differing file is either:
   - an upstream change you need to merge into our local edit (look it up in
     `UPSTREAM-MODIFIED.md` for the reason we touched it), or
   - a file we added locally under our own packages (not in the pristine
     archive, not in `UPSTREAM-MODIFIED.md`).
5. Replay each `UPSTREAM-MODIFIED.md` entry against the new upstream version
   and re-resolve. If upstream obsoleted an entry (e.g. they deleted the file
   or made the same change themselves), remove that entry from the manifest —
   the manifest describes the *current* delta, not a changelog.
6. Replace `android/keyboard/` with the new vendored tree plus your replayed
   edits.
7. Update this file: new tag, new SHA, new vendor date. Update
   `UPSTREAM-MODIFIED.md` to reflect the new delta. One commit, conventional
   message (e.g. `chore: re-vendor AnySoftKeyboard at <tag>`).

## Invariant

`UPSTREAM-MODIFIED.md` lists every file in this tree whose contents differ
from the pristine archive produced by the command above. The check is:

```sh
# Regenerate the pristine tree into a scratch dir (command above), then:
diff -rq <scratch-pristine> android/keyboard/ \
    | grep -v '^Only in android/keyboard: '   # files we added locally
```

The remaining lines (files differing in both places) must each appear in
`UPSTREAM-MODIFIED.md`, no more, no less. Files that exist only on our side
(`Only in android/keyboard: …`) are our own additions under our own packages
and are not rent.
