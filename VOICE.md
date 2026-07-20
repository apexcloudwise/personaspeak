# VOICE.md — How We Write Around Here

Every document, changelog, release note, and error message in this repo is
written in one voice. This file defines it. If you're an AI agent about to
write a doc: read this first, then write, then re-read what you wrote and ask
"would Valve ship this?" If the answer is "it reads like a bank's privacy
policy," start over.

## The voice, in one paragraph

Confident, funny, and slightly self-deprecating — the tone of the classic
Team Fortress 2 update pages and the Dota "New Frontiers" notes. We describe
real work with a straight face and let the absurdity speak for itself. We
address the reader directly. We never hide behind passive voice, corporate
hedging, or the word "leverage."

Picture the writer as an overworked but brilliant engineer: proud of the
machine, unbothered by its madness, professional about ridiculous things and
nonchalant about enormous ones. That gap — treating a one-line fix like a
world-historical event and a structural overhaul like an ordinary Tuesday — is
where the humor lives. Lean into the chaos rather than apologizing for it: a
bug gets acknowledged with a smirk ("this was driving us crazy too"), not a
stiff regret notice.

## Rules

1. **Say the true thing, funnily.** Humor is seasoning on top of accurate
   information, never a substitute for it. A user must be able to strip every
   joke from a doc and still configure the app correctly.
2. **Direct address.** "You" and "we." Never "the user" or "one may."
3. **Deadpan over wacky.** The joke is delivered like a fact. No exclamation
   avalanches, no "LOL", maximum one emoji per document and it better earn it.
4. **Self-deprecate, don't self-destruct.** We joke about our ambitions and
   our bugs, not about the product being bad. "The keyboard has one job and
   we gave it three" — yes. "lol this probably doesn't work" — no.
5. **Short sentences win.** If a sentence has three commas, it's two sentences.
6. **Technical sections stay technical.** API tables, schema specs, and
   security/privacy docs get at most a light garnish. Nobody wants a bit
   in the middle of a threat model.
7. **Never punch down.** We mock bureaucracy, corporate speak, and ourselves.
   Not users, not other projects, not contributors.
8. **The absurd-professional contrast.** Match the tone to the *voice*, not the
   stakes. A trivial fix can be reported with grave ceremony; a massive
   refactor can be waved off as routine. The deadpan is in the mismatch.
9. **Punchy over exhaustive.** Prefer a satirical one-line summary to a
   thorough boring one. "The data pipeline was hoarding memory like a dragon;
   we evicted it" beats a paragraph on allocation lifecycles — and still tells
   the reader exactly what changed (rule 1 always wins).

## Calibration examples

| Situation | ❌ Corporate | ✅ Us |
|---|---|---|
| README intro | "PersonaSpeak leverages cutting-edge LLM technology to empower users to communicate authentically." | "You type 'can't come to the party'. Jeeves regretfully declines on your behalf, with honor. That's it. That's the app." |
| Changelog | "Various stability improvements were implemented." | "Fixed a bug where Sir Humphrey's replies were so evasive the keyboard itself couldn't decide whether to send them." |
| Error message | "An unexpected error occurred. Please try again later." | "The persona engine has stepped out, presumably for tea. Try again in a moment." |
| Scary permission | "Notification access is required for functionality." | "To suggest replies, we read the message you're replying to — from the notification, on your device, and we forget it immediately. Don't take our word for it: the code is right there. Sir Humphrey would be appalled at this level of transparency." |
| Roadmap item | "Explore synergies with wearable platforms." | "Wear OS support: reply as Dr. Schultz from your wrist, like a Bond villain." |
| Bugfix note | "Fixed a memory leak in the data pipeline." | "The data pipeline was hoarding memory like a dragon. We evicted it; it now consumes a reasonable amount of resources." |
| Release note | "API version 2.0 has been deployed with improved latency." | "Providers 2.0 is live. Faster, shinier, and 40% less likely to burst into flames under heavy load." |

## The test

Before committing any prose, check:
- [ ] Could a stranger do the task using only this doc? (information intact)
- [ ] Did you smile at least once writing it? (voice intact)
- [ ] Zero instances of: leverage, utilize, seamless, empower, robust,
      best-in-class, "please note that". (dignity intact)
