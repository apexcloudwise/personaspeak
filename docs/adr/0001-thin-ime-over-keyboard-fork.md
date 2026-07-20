# ADR-0001: A thin persona keyboard, not a HeliBoard fork

**Status:** **Superseded (2026-07-20)** — the decision below was reversed the
same day it was accepted. PersonaSpeak forks a full keyboard; the thin IME is
scrapped. The replacement ADR is pending the fork-base spike; the reasoning and
evidence live in
[`docs/superpowers/specs/2026-07-20-fork-spike-checkpoint.md`](../superpowers/specs/2026-07-20-fork-spike-checkpoint.md).

Kept because its arguments did not stop being true — we chose to pay them:

- "Its worst failure mode is *the phone can't type*" is now **our** failure
  mode, which is why crash-freedom becomes a release gate.
- The escape hatch it designed for worked exactly as intended:
  `core-personas` and `core-providers` are pure Kotlin and transplant into any
  host untouched.

What broke it: an IME window gets no real input focus, so the keyless panel
could never be typed into, and its empty state was a dead end. Rather than work
around both, the switching model itself was abandoned.

## Context

We want persona/tone replies inside WhatsApp/Telegram/anything. Options
considered:

- **A. Fork HeliBoard** — become the user's daily-driver keyboard (typing,
  autocorrect, swipe — all inherited) with an AI strip bolted on top.
- **B. Thin persona IME** — a second, special-purpose keyboard. Gboard keeps
  the typing job; users flip to us for the two taps it takes to reply in
  style, then flip back.
- **C. No IME** — `ACTION_PROCESS_TEXT` selection-menu transformer only.

## Decision

**B.** We build a small, fully-owned Kotlin IME (~3-5k lines) whose
"keyboard" is a reply panel: persona chips, tone chips, suggestion cards.

## Because

- A keyboard fork means inheriting ~100k lines of AOSP-descended code, GPLv3
  obligations, and an eternal upstream-merge subscription. Its worst failure
  mode is "the phone can't type" — the most severe regression an app can
  have, and we'd be exposing ourselves to it in code we didn't write.
- The showcase value of this repo is the persona engine and the
  agent-maintained engineering, not re-owning autocorrect.
- C can't deliver the headline experience (suggested replies before you
  type), so it's a demo, not the product.
- The ladder stays open: if PersonaBoard outgrows "thin", the fork remains
  available as Phase 3+ ambition, and everything we build (core-personas,
  core-providers) transplants into it unchanged.

## Consequences

- Users keep two keyboards enabled; the flip-to-us gesture must be fast and
  the flip-back automatic, or the UX dies. This is the design's load-bearing
  wall — treat panel latency as a release blocker.
- We never need typing features, so any PR adding autocorrect, swipe, or
  layouts to `keyboard/` has misread this document and shall be returned
  with a raised eyebrow.
