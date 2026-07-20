# Agent ops: the voice, the evidence, and the definition of done

**Date:** 2026-07-21
**Status:** Recommendations — for owner review; nothing here is law until it
lands in AGENTS.md or an ADR
**Related:** [PR #1 review notes](2026-07-21-pr1-review-notes.md) ·
borrows heavily from `~/workspace/ext/darkmill/docs/adr/` (cited inline)

The owner asked for three things: a repo persona that writes patch notes
instead of press releases, a maintenance practice where agents prove their
work instead of describing it, and PRs that arrive *finished*. This doc
recommends how. The short version: the voice already exists (VOICE.md), the
evidence discipline already exists next door (darkmill), and the missing piece
is wiring both into AGENTS.md so a fresh agent inherits them without being
told.

## Part 1 — The persona: patch notes, not press releases

VOICE.md already defines the register (TF2 update pages, Dota's New Frontiers
notes: deadpan, accurate, jokes delivered like facts). What's missing is a
**place the voice performs regularly**, so it stays a habit instead of a
README novelty. Recommendations:

1. **`PATCHNOTES.md` at the repo root** — created on this branch with the
   history so far, as a demonstration. One dated entry per merged PR, TF2
   style: every line is a true change, described with a straight face. Rules
   ride VOICE.md rule 1 — strip the jokes and the entry still tells you
   exactly what merged.
2. **The PR author writes the patch note, in the PR.** The PR template (also
   on this branch) has a "Patch note" field. Reviewing the joke is part of
   reviewing the PR. This is deliberately cheap: one line, already in voice,
   written while the context is hot.
3. **Patch notes feed GTM.** GTM.md wants one post draft per day; a good
   patch-note entry *is* the post draft, or at least its first line. One
   artifact, two jobs.
4. **Release notes are hosted by a persona.** When a tagged release ships,
   one of the cast announces it — Jeeves regrets to inform you that v0.2
   contains a keyboard. Rotate the cast. This is the product's own gimmick
   pointed at its own changelog, and it generates exactly the kind of asset
   the GTM ladder needs. Keep VOICE.md's guardrail: anything load-bearing
   (permissions, privacy, key handling) stays plain.
5. **One new calibration row in VOICE.md** for patch notes specifically, so
   the register is pinned by example, not vibes.

What we do **not** recommend: a named repo-narrator character separate from
the product cast. The cast is the brand; a second fictional entity dilutes it
and doubles the voice-maintenance surface.

## Part 2 — AGENTS.md upgrades: the definition of a fully complete PR

The owner's phrase was "I want to review fully complete PRs." Today AGENTS.md
says what gets a PR *returned*; it never says what makes one *done*. darkmill
solved this with "done is spec-declared" (ADR-0002) and it is the single
highest-leverage line to steal. Proposed AGENTS.md section, ready to lift:

> ### Definition of done (what "fully complete PR" means)
>
> A PR is reviewable when every box below is true. A PR missing one is a
> draft, whatever its label says.
>
> - [ ] **Code + tests ride together.** New behaviour has tests; a bugfix has
>   its regression test in the same PR.
> - [ ] **Goldens updated, not deleted.** Prompt goldens and screenshot
>   goldens both. A golden changed without an explanation in the PR body is a
>   red flag, not a diff.
> - [ ] **Evidence attached** per the evidence ladder (Part 3): commands run
>   with their output, screenshots for UI, a journey recording for flows.
> - [ ] **Nothing graded by its author.** The agent that wrote the code did
>   not produce the verdict that it works (see separation of duties, below).
> - [ ] **Docs in the same PR:** ADR if the change is architectural, schema
>   docs if the schema moved, VOICE.md-compliant prose throughout.
> - [ ] **Patch note written** (one line, in voice, in the PR template).
> - [ ] **CI green.**

Two practices to codify alongside it, both proven elsewhere:

- **Separation of duties.** darkmill ADR-0016: implementers never write
  verification records; self-reports are advisory, not evidence. The fork
  spike already adopted this shape (GLM workers build, Claude grades). Make it
  a house rule: *the agent that writes the code never grades its own work* —
  a different session, model, or at minimum a fresh context produces the
  evidence verdict. An author's screenshot proves the author took a
  screenshot.
- **Cross-family review.** darkmill ADR-0031 calls this the single
  highest-yield quality practice in that repo's history: a different model
  family reads every diff before merge, and it catches criticals in both
  directions. The machinery already exists here (`oc-bg` with GLM, the Codex
  plugin, `/code-review`). Recommendation: every non-trivial PR gets one
  cross-family review pass; the review lands as a PR comment so it's part of
  the record. Skips are allowed but stated in the PR body — loud skips, never
  silent ones (ADR-0031's rule, worth keeping intact).

And one inherited principle to write down because it is already true in
practice: **a decision that lives only in chat does not exist** (darkmill
ADR-0020). The fork-spike checkpoint is this rule working. AGENTS.md should
state it so the next session doesn't need the example.

## Part 3 — The evidence ladder: goldens → screenshots → journeys → film

The owner wants e2e journey coverage with evidence: goldens, screenshots, mp4,
and agents that can verify "no regressions" without a human replaying flows.
Recommendation: four rungs, cheapest first, each with a defined home in CI.
The design doc's Testing section set this up already — the strip, picker, and
result card are plain-state Compose components, and every result-card state is
reachable from a fake provider with no network and no key. Build on exactly
that seam.

| Rung | What | Tool | Where it runs |
|---|---|---|---|
| 1 | Prompt goldens (exist) | pytest / Kotlin golden tests | Every PR, seconds |
| 2 | **Screenshot goldens** of every Compose state — strip (light/dark/long names), picker, result card (loading/result/each error) | Paparazzi (JVM render, no emulator) | Every PR, no device needed |
| 3 | **Journey flows** — onboarding e2e, type→tap→rewrite→replace, error recovery | Maestro flows and/or scripted `adb` + `uiautomator dump` | Emulator: nightly + on a `journey` label; not every PR |
| 4 | **The film** — `adb screenrecord` mp4 of each journey | same emulator run | Attached as evidence; doubles as the GTM demo asset |

Notes that make this actually work:

- **Rung 2 is the regression backbone.** Paparazzi renders Compose on the JVM
  and diffs against committed PNGs — hermetic, fast, CI-runnable on every PR,
  and it directly covers the review's known gaps (dark-mode chip contrast,
  "Sir Humphrey Appleby" in a fixed-width chip) as *pinned test cases* instead
  of open questions. Small PNGs live in the repo like any golden.
- **Rung 3 must produce machine-checkable evidence,** not vibes. The spike
  spec already has the right rules — keep them for all journey tests: the APK
  installs; `uiautomator dump` XML contains the expected nodes; a scripted
  before/after shows the field text changed. **No agent may conclude "it
  works" from a screenshot it took itself** (rungs 3–4 produce artifacts; the
  *verdict* comes from the XML asserts plus a non-author viewing pass).
- **IME caveat, stated honestly:** Maestro is excellent for the app surfaces
  (onboarding, settings) but drives the IME window less reliably than plain
  `adb`/uiautomator, which does see IME windows. Expect journeys to be a mix.
  One emulator, one active IME: journey runs serialize; builds parallelize.
- **Evidence storage:** borrow darkmill ADR-0026 — an orphan, append-only
  `evidence` branch, path per PR (`pr-<n>/<artifact>`), never force-pushed.
  mp4s and full-page screenshots go there and get linked from the PR body;
  `main`'s history stays lean. Committed Paparazzi goldens are the exception:
  they're small and belong with the code they pin.
- **CI staging (makes the crash gate real):** enable the Android jobs now —
  `assembleDebug` + unit tests + Paparazzi verify per PR; emulator journeys
  nightly and on-label; APK artifact on tags. The fork will make PR builds
  slower; that is a price of the fork, not a reason to keep CI commented out.
- **Chore already on the books:** `personaspeak.py` still has no
  print-a-prompt flag, so the "fixtures are generated by the Python
  reference" claim in the schema doc is aspirational. Small, independent,
  do-any-time.

## Part 4 — The dev loop, end to end

The loop that ties it together, per task:

1. **Spec → plan → implement** (the superpowers cycle already in use; the
   checkpoint docs prove it survives session boundaries).
2. **Implement on a branch/worktree** with tests and goldens riding along.
3. **Evidence pass by a non-author:** run the ladder rungs the change
   touches; artifacts to the evidence branch; verdict into the PR body.
4. **Cross-family review** as a PR comment; findings fixed or answered.
5. **Patch note + PR template filled**; merge; the patch note becomes the
   day's GTM post draft.

Suggested first slice (demoable, per prime directive #1): enable the Android
CI jobs, add Paparazzi with goldens for the result-card states from the fake
provider, and land the PR template + PATCHNOTES.md. That is one PR that makes
every future PR more trustworthy — infrastructure that pays rent immediately.
