# Mockups — PersonaSpeak keyboard UX

Generated 2026-07-20 with Stitch (Gemini 3.1 Pro) during the brainstorm that
scrapped the thin IME in favour of a full keyboard fork. Stitch project:
"PersonaBoard — persona/mood/config flows".

**These are exploration artefacts, not specifications.** Where a mockup and
`../2026-07-20-keyboard-ux-design.md` disagree, the design doc wins. Numbers
below are illustrative; the mockups are pixel-approximate and were rendered at
thumbnail scale.

## Onboarding

| File | What it shows |
|---|---|
| `01-onboarding-welcome.png` | Welcome. Keyboard-wearing-a-top-hat illustration. Headline "A keyboard with better manners". Persona row + the reassurance triple (Autocorrect / Glide typing / Works offline) that answers "will I lose my typing?" |
| `02-onboarding-make-default.png` | Enable + set default. True first-run state: **both** steps incomplete. Meets Android's "this keyboard can collect what you type" warning head-on, and states the escape hatch. |
| `03-onboarding-pick-provider.png` | Provider choice: Gemini (FREE, recommended), Claude, OpenAI, OpenRouter, on-device (SOON, disabled). |
| `04-onboarding-add-key.png` | Key entry. Link to AI Studio, masked field with reveal, validation tick, Keystore reassurance, skip link. |
| `05-onboarding-try-it.png` | The payoff: type, tap, see a rewrite, with a tooltip pointing at the strip. |

**Known gaps:** `03` carries a FREE badge on a provider that still requires a
key — two screens to get one thing working, and the copy should own that.
`04` shows only the happy path; bad key / no network / provider rejection are
unmocked.

## The strip and panel

| File | What it shows |
|---|---|
| `07-strip-variant-a-CHOSEN.png` | **The chosen design.** Persona chip + mood chip + send. Three large legible targets. Renders the strip above the chat input, and shows it coexisting with the keyboard's own suggestion row. |
| `08-strip-variant-b-rejected.png` | Rejected: labelled scrolling chips. One-tap switching and shows the cast, but only ~2.5 chips fit and the mood control was squeezed out entirely. |
| `09-persona-picker-open.png` | Variant A's expander: 2×2 grid with names *and* descriptors, plus "Browse all characters". This is what makes A's extra tap worth paying. |
| `10-result-card.png` | Result card floating above the strip, over the chat rather than over the keys — so the keyboard never moves or reflows. Actions: Use this / Again / dismiss. |
| `11-full-panel-superseded.png` | **Superseded.** The thin-IME full panel, kept as a record of what the fork decision replaced. Its cramping is instructive: so tall it still squeezed the result, and it could not fit a fourth persona chip. |

## Settings

`06-settings-home.png` — five groups: CHARACTERS / THE BRAIN / TYPING /
APPEARANCE / PRIVACY. The TYPING group (languages, glide, autocorrect,
dictionary) is the fork decision made visible; none of those rows could exist
on the thin IME.

## Still unmocked

Loading/thinking state, provider and network errors, dark theme, small-screen
and long-persona-name layouts, and the strip's behaviour when the host
keyboard's suggestion row is also present.
