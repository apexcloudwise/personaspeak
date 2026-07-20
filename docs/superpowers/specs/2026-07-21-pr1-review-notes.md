# Review notes: PR #1, the day we decided to eat a keyboard

**Date:** 2026-07-21
**Status:** Notes — post-merge review of PR #1 (`a5c4640`), owner-confirmed items
**Related:** [fork-spike checkpoint](2026-07-20-fork-spike-checkpoint.md) ·
[keyboard UX design](2026-07-20-keyboard-ux-design.md) ·
[agent ops recommendations](2026-07-21-agent-ops-recommendations.md)

PR #1 was reviewed after merge. Overall verdict: the decision hygiene is the
best thing in the repo — the superseded ADR kept with its arguments intact, the
rejected mockups kept as evidence, the spike reframed instead of discarded.
These notes record what the review found anyway, because a compliment with no
homework attached is just flattery.

## 1. GTM.md is the un-updated consumer of the fork decision

Every doc that says *what* we build was updated in PR #1. The one that says
*when* was not. GTM.md's ladder — one merged task per day, internal APK on
Day 10 — was calibrated to a 3–5k-line thin IME. The fork inserts, before any
of that: three clones, three builds, three grafts, a base decision, an ADR,
and an implementation plan for a ~100k-line codebase.

**Open — owner's call.** Either the ladder shifts (spike days are ladder days;
each graft report is a perfectly good daily merge + post draft) or Day 10's
deliverable gets redefined. What it can't do is stay as written, because right
now the repo's schedule and its architecture disagree in public.

## 2. The replacement ADR must record the honest rationale

*Owner-confirmed 2026-07-21.*

ROADMAP.md currently compresses the reversal into "an IME window never gets
real input focus, so the keyless panel could not be typed into" — stated like
physics. The checkpoint doc is more honest: reading the draft via
`getExtractedText()` was the *intended* fix (never implemented — the current
IME calls it nowhere, and Android permits it to return `null` when the editor
can't comply, so it was a candidate, not a proven escape), and the empty state
was patchable. What actually killed the thin IME was a judgment call: **the
switching UX was not acceptable.** Flip to a keyless keyboard, flip back,
dead-end on first launch — patchable individually, unshippable as a whole.

That is a *better* reason than impossibility, and the replacement ADR should
say it plainly: "we judged the switching model unacceptable," not "it could
not work." Future readers deciding whether to un-fork deserve to know the
door was closed by taste, not by the platform.

## 3. The crash-freedom gate has no eyes

*Owner-confirmed 2026-07-21.*

The fork's release gate is "the keyboard must not crash," and the privacy
story is "we store nothing." Nobody has yet asked how those two learn about
each other. A crash in the field currently reports itself to no one.

**Recommendation:** decide crash reporting *in the fork ADR*, because it is a
privacy-posture decision (AGENTS.md says those get ADRs). Don't hand-wave it —
the honest version has three constraints, and "we store nothing" does *not*
survive intact:

- **Opt-in, user-initiated, shown-in-full.** The crash is caught locally; the
  full report is shown to the user; nothing leaves the device unless they
  choose to send it. This is the shape ACRA supports, but ACRA is not magic —
  by default it can attach logs, shared-prefs, device identifiers, and more,
  so the report contents must be *deliberately minimized* to stack trace +
  app version + a coarse device model, and that minimization is the actual
  design work.
- **Someone receives it, so someone stores it.** The moment a report reaches
  a mailbox or issue tracker, "we store nothing" is no longer literally true.
  The claim has to narrow to something defensible: "the keyboard stores
  nothing you type; crash reports are opt-in, contain no message text, and are
  kept only as long as it takes to fix the bug." Write the retention answer
  down; don't let it be discovered.
- **Don't overstate the precedent.** AnySoftKeyboard ships a crash-report
  path (email); HeliBoard's public guidance points at GitHub issues and its
  build carries no ACRA. So "the FOSS keyboards all do ACRA" is not a fact to
  lean on — cite what each base actually does once the base is chosen.

The privacy page then gets an honest paragraph, not an asterisk.

Also part of the gate having eyes: the Android CI jobs are still a comment in
`ci.yml`. A release gate that no machine checks is a wish. (Concrete staging
in the [agent ops recommendations](2026-07-21-agent-ops-recommendations.md).)

## 4. Licence nuances the base decision should weigh

Two facts that change the HeliBoard calculus, one in each direction:

- **Softens GPL:** GPL-3.0 relicenses the *app*, not the project. Apache-2.0
  code may be included in a GPL-3.0 work, and `core-personas` /
  `core-providers` are ours — they can remain standalone Apache-2.0 modules
  inside a GPL-3.0 app. The README's promise doesn't die; it shrinks to "the
  portable heart stays Apache-2.0, the keyboard shell is GPL-3.0." That is an
  honest sentence we could ship.
- **Sharpens the glide problem:** onboarding screen 1 promises **Autocorrect ·
  Glide typing · Works offline** before the base is chosen. On HeliBoard,
  glide typing requires a closed-source Google blob the *user* must obtain —
  which is a rough first-run promise and an F-Droid complication (ROADMAP
  Phase 3 targets F-Droid). AnySoftKeyboard has glide built in; FlorisBoard's
  is *unavailable* — its roadmap parks a glide reimplementation at 0.7+,
  behind a predictive-text core that still hasn't shipped, so "planned," not
  "in progress."

**Action for the spike:** alongside diffstat and build time, each graft report
records the candidate's glide story (built-in / blob / absent) and its F-Droid
compatibility. The reassurance triple is writing a check the base has to cash.

## Nits

- `docs/design/mockups/README.md` row for `10-result-card.png` didn't mention
  that the mockup renders the *rejected* four-circle strip below the card.
  The "design doc wins" disclaimer technically covered it; the row now says it
  outright. **Fixed on this branch.**
- "Golden tests unaffected" by the mood modifier is true today with a shelf
  life: the moment the modifier is implemented, the goldens must grow mood
  fixtures in *both* references (Python and Kotlin) in the same PR, or the
  byte-identical guarantee quietly stops covering the thing users actually
  tap. Noting it now so it rides the implementation plan, not a bug report.
