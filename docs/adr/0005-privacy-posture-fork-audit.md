# ADR-0005: Privacy posture for the fork — default-private, audit-gated

**Status:** Proposed (2026-07-21) — moves the privacy posture, so it gets an ADR
(AGENTS.md). Records the decision; the inventory it mandates is follow-up work.
Merging this PR records it as Accepted.

## Context

The README makes a blanket, load-bearing claim:

> Your API keys live in Android's Keystore. Message text goes only to the
> provider *you* configured, only when you ask for a rewrite. **Nothing is
> stored, logged, or "used to improve our services."**

That paragraph was written for [ADR-0001](0001-thin-ime-over-keyboard-fork.md)'s
thin IME, where we owned every line and the keyboard genuinely stored nothing.

[ADR-0003](0003-fork-anysoftkeyboard-apache.md) changed the premise: we now ship
a forked full keyboard (AnySoftKeyboard). A predictive daily-driver keyboard
stores things **by design** — it has to, to predict. The moment the fork ships,
the unqualified "nothing is stored, logged" is at risk of being false, and a
false privacy claim is worse than no claim. This was flagged in an external
strategy review (codex, 2026-07-21) and is exactly the AGENTS.md offense
"storing anything a user typed / making the privacy story more complicated to
explain."

This is not an accusation against AnySoftKeyboard — it is a normal keyboard doing
normal keyboard things. The problem is ours: our copy over-promises against code
we inherited and have not yet audited.

## Decision

**The privacy claim is audit-gated, and the posture is default-private.**

1. **Two categories, stated separately, never conflated:**
   - **On-device local state** the keyboard keeps to function (learned words,
     user dictionary, next-word data, clipboard). This may exist, but it stays
     on the device and is user-clearable. The honest claim is "it never leaves
     your phone," not "it does not exist."
   - **Anything that leaves the device** — network calls, crash/analytics
     telemetry, cloud/settings backup, verbose logs. Default posture: **off, and
     proven off.** Message text leaves only to the user's chosen provider, only
     on request — that half of the claim we keep and must verify still holds
     through the fork.

2. **The current README/onboarding privacy copy does not ship as-is.** It is
   rewritten to be *true and specific* — distinguishing the two categories above
   — only after the inventory below is complete. Until then it is knowingly
   inaccurate for the fork and must not be repeated in new surfaces (onboarding
   screen 2, the settings PRIVACY group).

3. **A privacy inventory of the vendored ASK tree is required**, run against the
   snapshot from [ADR-0004](0004-vendored-snapshot-ingestion.md). It answers, per
   item: does it exist, where does the data live, does anything leave the device,
   is it default-on, and can the user clear/disable it. Minimum categories:
   - learned words / user (personal) dictionary
   - next-word / prediction model state
   - clipboard history, if any
   - Android auto-backup and any settings export/cloud sync
   - crash reporting, analytics, or telemetry (ASK has shipped optional
     reporting historically — verify what the pinned snapshot actually does)
   - any outbound network call from the keyboard process
   - logcat verbosity that could echo typed text in release builds

4. **The verification bar is on-device, not a reading of the code alone.** The
   claim is backed by an on-device check — network capture showing no unexpected
   egress, storage inspection showing where local data sits — consistent with
   ADR-0003's rule that pure-logic tests miss what the device reveals.

## Because

- **A false privacy claim is a worse liability than an honest limitation.** "We
  store nothing" from a keyboard that keeps a learned-words dictionary is the
  kind of overclaim that ends a trust-based project. Better to say precisely what
  stays on the device and what never leaves it.
- **Default-private is the only posture consistent with the project's pitch.**
  "This repo is public so you don't have to trust that paragraph" only works if
  the paragraph is true; the audit is what makes it true rather than aspirational.
- **The audit needs the vendored tree to exist**, which is why this follows
  ADR-0004 and gates the privacy *copy*, not the graft itself. The keyboard can
  be built and demoed with `FakeProvider` before the copy is finalized.

## Consequences

- **The privacy copy is blocked until the inventory lands.** README's "Privacy,
  briefly", onboarding screen 2, and the settings PRIVACY group ("What we store:
  Nothing. Here's the proof.") are frozen in their current wording for the fork
  and rewritten together, once, against the findings. Shipping the fork with the
  old copy unaudited is the failure this ADR exists to prevent.
- **Some inherited defaults will need neutralizing.** If the audit finds any
  default-on egress (telemetry, backup of the dictionary to cloud), turning it
  off is part of satisfying this ADR, and each such change is a tracked upstream
  modification per ADR-0004's manifest.
- **The inventory is executable work, filed separately.** This ADR is the
  decision and the checklist; the audit itself is an issue against the vendored
  snapshot, with the on-device evidence attached, and it gates the privacy copy
  PR.
- **Phase 2 inherits a stricter version of this.** Suggested replies (reading
  notifications) raise the stakes; the same default-private, audit-gated posture
  applies, and the ROADMAP already commits to a Phase 2 privacy page. This ADR is
  the general rule that page will cite.
