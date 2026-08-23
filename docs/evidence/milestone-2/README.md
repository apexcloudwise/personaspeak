# Milestone 2 — Device Qualification Receipt

**Status: QUALIFIED.** Run `20260823T122133Z` at repo head `0332b74`,
canonical APK `a2acbc2a…` (58,574,002 bytes). The unified PersonaSpeak
ASK build completed the full required journey on the pinned snapshot
fixture `M2_Qual_Fixture` / `m2_pristine`: registration, enablement,
selection, real key taps through four editor sessions, the
Idle/Loading-cancel/Review/Applied/Dismiss/stale paths with their exact
mutation counts, and verified restoration to pristine state.

- **145/145 steps completed**, zero non-completed, zero instrument
  findings. Restoration verified against captured prior state.
- **Media**: 7 structurally valid screenshots + 1 journey video,
  owner-approved (visual/privacy) and bound to the capture and manifest
  digests (`bc3e7733…`). Known limitation, ruled non-blocking by the
  overseer (PR #84 review): `07-settings.png` caught the Settings
  window mid-fade, so the visual restoration proof rests on the
  machine verdict — the `verify_restore` step asserted the pristine
  hierarchy facts (search editor absent, prior identity matched) and
  the receipt records `restoration: verified`.
- **Receipt counts** (the five the machine derives in `receipt.json`):
  journey_steps_completed 132, png 7, mp4 1, release_ok 1,
  verify_release_ok 1. Verdicts: restoration `verified`, media_ok,
  privacy_ok.
- **Mutation and rejection totals** (derived from the capture record,
  per the overseer's receipt-index ruling): Apply exactly **1**
  mutation, single application with no retry; Dismiss **0**; stale
  apply **0**; rewrite-then-cancel **0**; rejected files **0** — the
  manifest was accepted as exactly the canonical set.
- **Evidence of record**: the append-only `evidence` branch — run
  commit `61c71af` (artifacts, ledger, gate log), receipt commit
  `3f0df9a` (approval + this receipt's bytes). `61c71af` is the
  receipt's bound evidence commit and an ancestor of the branch head.
- **Sole-host acceptance**: `verify-milestone-2.sh` PASS 12/12 at the
  exact head (complete log archived with the run).

The finalized machine receipt lives beside this file as `receipt.json`;
its digests are the authority. Replay procedure: fetch the evidence
branch bytes at `61c71af`, recompute every digest named in
`receipt.json`, require exact matches, rerun the structural media and
privacy validators on those bytes, and confirm verdicts identical to
the receipt.
