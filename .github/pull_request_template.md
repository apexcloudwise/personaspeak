<!-- One concern per PR. A PR doing three things is three PRs in a trench coat. -->

## What

<!-- The change, plainly. -->

## Why

<!-- The reason, or a link to the ADR/spec/issue that is the reason. -->

## Evidence

<!-- Per the evidence ladder (docs/superpowers/specs/2026-07-21-agent-ops-recommendations.md).
     Delete rungs that genuinely don't apply; say why if it's not obvious. -->

- **Commands run + output:** <!-- golden tests, --list, validators -->
- **Screenshots / goldens:** <!-- Paparazzi diffs, mockup comparisons -->
- **Journey recording:** <!-- mp4/link on the evidence branch, for flow changes -->
- **Graded by:** <!-- who/what produced the verdict — must not be the author.
     "Review skipped: <reason>" is allowed; silence is not. -->

## Patch note

<!-- One line for PATCHNOTES.md, in VOICE.md register. True first, funny second. -->

## Checklist

- [ ] Tests ride with the code (regression test, if this fixes a bug)
- [ ] Goldens updated and explained (never silently changed)
- [ ] Docs in this PR (ADR if architectural; schema consumers if schema moved)
- [ ] `desktop/personaspeak.py --list` + golden tests pass, if `personas/` or prompt code was touched
- [ ] Nothing here stores what a user typed
