# ADR-0003: Fork AnySoftKeyboard, under Apache-2.0

**Status:** Proposed (2026-07-21) — the replacement ADR that
[ADR-0001](0001-thin-ime-over-keyboard-fork.md) said was pending the fork-base
spike. Base adoption is **provisional**, pending the two conditions in
Consequences. Merging this PR records it as Accepted.

## Context

ADR-0001 (a thin persona IME, not a fork) was superseded the day it was
accepted: an IME window gets no real input focus, so the keyless panel could
never be typed into. PersonaSpeak forks a full open-source keyboard instead. The
fork-base spike cloned, built, and grafted the persona strip onto all three
candidates, then graded how invasive each graft is — full evidence in
[`docs/superpowers/specs/2026-07-20-fork-spike-results.md`](../superpowers/specs/2026-07-20-fork-spike-results.md).

The spike deliberately did **not** pick the base — that was left as the owner's
call, tied to the license. Two commissioned independent reviews reached opposite
recommendations (Gemini: HeliBoard; Codex: AnySoftKeyboard). After a round of
commissioned fixes, all three candidates have a verified, working end-to-end
flow, so the decision turns on what does **not** change with more fixes:
**license**, **architecture / diff fit**, **maintenance reality**, and **typing
quality**.

| | HeliBoard | AnySoftKeyboard | FlorisBoard |
|---|---|---|---|
| License | GPL-3.0 | **Apache-2.0** | **Apache-2.0** |
| Builds clean out of the box | Yes | **Yes** | No — purged dep + missing Rust toolchain |
| Prediction engine | real, populated | **most mature of the three** | stubbed at v0.5.2 (`suggest()` → `emptyList()`) |
| Upstream diff (after fixes) | 4 files, +51/−1 | 5 files, +30 | 4 files, +11/−1 (cheapest) |
| Interop friction | low | Java↔Kotlin bridge | low (Compose) |
| Replace mechanism | `selectAll` (composing bug, fixed) | `deleteSurroundingText` (no composing bug) | `selectAll` (composing bug, fixed) |

## Decision

**Fork AnySoftKeyboard. License the app Apache-2.0.**

## Because

- **License is the one irreversible axis, and Apache is the requirement.**
  GPL-3.0 (HeliBoard) is a one-way door: once distributed, it makes the whole
  app GPL, forecloses a permissive relicense forever, restricts which
  dependencies can be bundled, and undercuts a paid-app model — for a keyboard we
  intend to keep permissively licensed and reusable (README's "leaning
  Apache-2.0"). Committing to Apache-2.0 removes HeliBoard from contention
  outright, however good its picker was. (The provider/key model is unaffected
  either way — copyleft bites the distributed binary, not network use; see
  [ADR-0002](0002-pluggable-provider-registry.md).)
- **Among the two Apache options, ASK wins the axis the bake-off would have
  measured — from facts already in hand.** FlorisBoard's suggestion engine is
  stubbed at v0.5.2; ASK has the most mature prediction engine of the three. A
  full comparative bake-off would spend most of a day confirming that a real
  engine beats a stubbed one. It has been descoped to a provisional ASK-only
  sanity check (#5).
- **ASK builds clean; FlorisBoard doesn't.** FlorisBoard needs off-repo
  intervention (a purged `jetpref` snapshot, a missing Rust toolchain) — the
  "maintainer says it stalled" risk from the checkpoint, confirmed hands-on.
- **ASK's replace mechanism is the most robust of the three.** Its
  `deleteSurroundingText` path never hit the composing-state bug that HeliBoard
  and FlorisBoard both did. Its one crash was a one-line fix in our own code
  (`TextUtils.isEmpty`), not a hostile host.
- **The diff cost is acceptable and concentrated.** ASK's interop tax
  (Java↔Kotlin, since `:ime:app` had no Kotlin) is real but sits in one
  predictable place — a small adapter bridging the vendored `suspend rewrite()`
  — not scattered through inherited files.

## Consequences

- **Base adoption is provisional, pending two conditions:**
  1. **ASK typing sanity check (#5, descoped):** confirm ASK's inherited engine
     clears the bar — not a downgrade users would notice. A fast-follow, **not a
     merge blocker**: the fork is survivable and course-correctable, since
     `core-personas`/`core-providers` transplant into any host unchanged
     (ADR-0001's escape hatch, which held).
  2. **Stale-field race guard (#6 spec):** the editor-identity / generation-token
     guard must be implemented before any real provider replaces `FakeProvider`.
     The async race is base-independent and violates the UX rule "the user's own
     words are never destroyed without a tap." Spec merged
     ([`2026-07-21-stale-field-race-design.md`](../superpowers/specs/2026-07-21-stale-field-race-design.md));
     the implementation ticket rides on this base.
- **Module law is unchanged and non-negotiable.** `core-personas` and
  `core-providers` stay pure Kotlin. PersonaSpeak code in the fork lives in
  clearly separated packages, never scattered through inherited files —
  **upstream lines modified are rent paid forever**, one merge conflict each.
  Hold the diff to the ~5-file seam the spike proved possible.
- **An on-device instrumentation test is now a suite requirement.** The spike's
  cross-candidate finding: every worker's pure-logic tests passed and every
  on-device run found a bug they couldn't see. The capture → transform → replace
  path gets a real-`InputConnection` test, not just unit tests against the pure
  logic layer.
- **FlorisBoard's Snygg theming — the highest ceiling of the three — is
  forgone.** ASK's addon-based theme system is mature enough; wiring the strip
  into it is follow-up work, not a new capability to build.
- **We inherit ASK's ~8,870-commit history and its Java-primary codebase.** The
  upstream-merge subscription is real; the isolated-package discipline above is
  what keeps each merge cheap.
