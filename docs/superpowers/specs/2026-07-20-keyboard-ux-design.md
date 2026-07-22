# PersonaSpeak keyboard UX — design

**Date:** 2026-07-20
**Status:** Superseded in part by the owner-approved
[Stitch screen contract](2026-07-22-stitch-screen-contract.md). The permanent
strip, picker, result-card, and full-keyboard premises remain useful. The newer
contract governs routes, copy, typed failures, accessibility, and evidence;
geometry and window behavior also follow the newer contract;
review-before-replace is fixed product behavior rather than a setting.
**Branch:** `docs/fork-spike-and-ux-brainstorm`
**Mockups:** [`docs/design/mockups/`](../../design/mockups/)
**Related:** [fork-spike checkpoint](2026-07-20-fork-spike-checkpoint.md) ·
supersedes ADR-0001

## What this covers

The three user-facing surfaces of PersonaSpeak as a **full keyboard**:
the persona strip and result card, first-run onboarding, and settings.

It is deliberately **base-agnostic**. The fork base (HeliBoard /
AnySoftKeyboard / FlorisBoard) is unresolved and is being settled by a separate
spike; nothing here depends on which one wins, except where explicitly flagged.

Out of scope: the fork spike itself, the agentic e2e/evidence harness, the
provider implementations, and Phase 2 suggested replies.

## Premise

PersonaSpeak is a keyboard you type on all day, which happens to have
characters living in it. Not a novelty you switch to — the switching model is
dead (ADR-0001 superseded). Two consequences drive every decision below:

1. **The strip is permanent furniture.** It is on screen every time the user
   types anything, forever. It must be legible, calm, and cheap in vertical
   space.
2. **The failure mode is severe.** A crash means the user cannot type. Every
   state below must degrade to "the keyboard still works and your words are
   intact."

## 1. The strip

The resting state. One row, roughly one key-row tall, sitting above the keys.

```
[ 🎩 Jeeves ⌄ ]   [ polite ⌄ ]                    ( → )
```

Three targets, all comfortably tappable:

| Element | Behaviour |
|---|---|
| **Persona chip** | Shows the *current* persona, emoji + name. Tap opens the picker (§2). |
| **Mood chip** | Shows the current mood. Tap opens the mood list. |
| **Send/transform** | Runs the rewrite on the current draft. Becomes a spinner while working. |

**Why one persona and not four.** Four emoji circles were generated twice, in
independent runs, and rendered illegibly both times — surviving example in
`05-onboarding-try-it.png`, where the four circles are near-identical dark
blobs and the selected ring barely reads. At strip scale the emoji are
indistinct. That is the design failing at the size it must live at, not a
rendering artefact. One labelled
chip is legible, leaves room for the mood control, and scales past four
personas, which fixed circles never could. Variant B (labelled scrolling chips,
`08`) was rejected: only ~2.5 chips fit and the mood control was squeezed out
entirely.

**Open — vertical space.** Mockup `07` shows the host keyboard's own suggestion
row (`I'm` / `I` / `I'll`) sitting alongside our strip. Two stacked strips is a
lot of screen. Resolution deferred to the fork spike, when we know what the
base's suggestion row actually costs. Options: merge into one row, make ours
collapsible, or accept both.

**Open — dark mode contrast.** In `13` the persona chip becomes a dark pill on
a dark surface and its selected state reads weakly. The strip needs an explicit
dark-theme selected treatment, not merely an inverted palette.

## 2. The persona picker

Tapping the persona chip expands a card upward over the chat (`09`):

- Header: `CHOOSE A CHARACTER` + close.
- 2×2 grid of tiles: emoji, name, and a one-line descriptor — "the impeccable
  valet", "will neither confirm nor deny". The descriptors come from the
  persona's `context` field.
- Footer: `+ Browse all characters` → the full library in the app.

The descriptors are the point. They convey character in a way a bare emoji
never did, and they justify the extra tap that variant A costs.

## 3. The result card

Appears above the strip inside ASK's input-view hierarchy — never over the keys
(`10`). The IME window may grow and the host may relayout, but ASK's key-row
coordinates remain fixed while the card is open. The typing surface is not
allowed to shift under your fingers.

**States:**

| State | Content |
|---|---|
| **Loading** | Persona · mood label; skeleton bars where text will land; in-voice caption ("Composing something regrettable…"); `Cancel`. Send button shows a spinner. *(Not mocked — Stitch failed three times.)* |
| **Result** | Persona · mood label; the rewritten text; `Use this` / `↻ Again` / `✕`. |
| **Error** | Amber advisory, never red. Icon + short bold cause + in-voice body + `Try again` / `Dismiss`. The draft stays visibly intact. |

**Errors to handle:** offline (mocked, `12`); provider rejects the key; quota
exhausted; response empty or malformed. Each names the cause plainly and states
that the user's text is untouched.

`Use this` is the sole approval to replace the captured draft. There is no
immediate-replace mode and no second confirmation dialog.

## 4. Onboarding

Five screens (`01`–`05`).

1. **Welcome.** "A keyboard with better manners." Keyboard-in-a-top-hat
   illustration. Persona row and truthful typing reassurance. Typing works
   offline; cloud rewrites do not. Gesture typing is not promised until ASK's
   beta feature is deliberately enabled and verified.
2. **Make it your keyboard.** Two steps, both incomplete on true first run:
   enable, then set default. Meets Android's "this keyboard can collect what
   you type" warning head-on rather than hiding it, and states the escape
   hatch: switch back any time.
3. **Pick a brain.** Gemini (recommended), Claude, OpenAI, OpenRouter,
   on-device (disabled, SOON).
4. **Add your key.** Link out to get one, masked field with reveal, validation,
   Keystore reassurance, skip link.
5. **Try it.** An Activity-hosted editor summons the installed ASK IME. The
   user types with real ASK keys and invokes the real strip; no simulated
   keyboard or alternate draft state machine is a product path.

**Known copy issue.** Screen 3 badges Gemini "FREE" and screen 4 then demands
an API key. That is a lie of omission; the copy must own that free-tier still
means "get a key, it just costs nothing."

Screens 3 and 4 are skippable — the keyboard must be fully usable for typing
with no AI configured at all.

## 5. Settings

Five settings groups:

- **CHARACTERS** — personas and default mood. Review before replacement is
  fixed behavior, not a preference.
- **THE BRAIN** — provider and API keys. Usage counters require a separately
  approved and disclosed persistence design.
- **TYPING** — languages and layouts, glide typing, autocorrect, personal
  dictionary.
- **APPEARANCE** — theme, keyboard height.
- **PRIVACY** — separates explicit provider traffic, request-scoped transient
  data, Keystore-protected credentials, and ASK's local typing data. Public
  claims remain gated by ADR-0005's release audit.

The TYPING group is the fork decision made visible: none of those rows could
exist on the thin IME. It is also the group we will largely inherit from the
base rather than design.

## Voice

User-facing prose follows [VOICE.md](../../../VOICE.md) — in-voice and
un-corporate, including error messages ("I am afraid the internet has deserted
us, sir"). Keep the character out of anything load-bearing: permission
explanations, privacy claims, and key handling stay plain, because a user
deciding whether to trust a keyboard deserves a straight answer.

## Testing

- The strip, picker, and result card are Compose components taking plain state
  — renderable in a normal Activity, so they can be driven and recorded without
  fighting IME focus (the friction hit in the previous session).
- Every result-card state (loading / result / each error) is reachable from a
  fake provider with no network and no key.
- `core-personas` and `core-providers` are untouched by this design; their
  golden tests continue to pin the persona→prompt transformation.

## Open questions

1. ~~**Fork base**~~ — **resolved: AnySoftKeyboard**
   ([ADR-0003](../../adr/0003-fork-anysoftkeyboard-apache.md)).
2. **Strip vs the base's suggestion row** — vertical budget. Now *answerable*:
   the spike found ASK's strips **stack** (persona row above the candidate row),
   they don't merge. Deciding merge/collapse/accept is track-A work against the
   real ASK tree.
3. **Dark-mode selected state** for the persona chip.
4. **Long persona names** ("Sir Humphrey Appleby") in a fixed-width chip.
5. ~~**Mood list contents**~~ — **resolved by the Stitch screen contract:**
   stable product-owned IDs `polite`, `witty`, `blunt`, `apologetic`, and
   `formal`, separate from persona schema data.
6. ~~**Licence**~~ — **resolved: Apache-2.0** (follows from 1; ADR-0003).

## Decisions this design records

- Fork over thin IME; ADR-0001 superseded.
- Mood is an orthogonal prompt modifier. `schema_version` stays `1`,
  `personas/*.yaml` untouched, golden tests unaffected.
- One persona chip + expander, not a row of persona buttons.
- The result card lives above the strip; its expansion never moves ASK's key
  rows.
- Onboarding is a migration ("replace your keyboard"), not an addition.
- The keyboard is fully usable with no AI configured.
