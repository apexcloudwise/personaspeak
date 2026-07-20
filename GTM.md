# GTM.md — Go-To-Market, Butler Edition

The plan for making people aware this exists, on a budget of: daily AI
image/video generator credits, an agent workforce, and charm.

## Operating model

Agent-driven cadence. The human's job is directing and merging; the agents'
job is everything else. A "day" below assumes ~30-60 min of human review
time. If a day is missed, shift the ladder — the credits reset daily but the
plan doesn't expire.

## The asset pipeline (spend those daily credits)

Every day the image/video credits produce **at least one promo asset** from
the standing prompt library below. Assets land in `marketing/assets/` **with
the generating prompt saved alongside** (`asset-name.prompt.txt`) — this makes
the pipeline reproducible and doubles as a public prompt-craft showcase.

### Standing prompt library (rotate; add on inspiration)

| Series | Concept |
|---|---|
| *Persona reacts* | Jeeves receiving "wyd" at 2am. Sir Humphrey asked for a simple yes/no. Schultz reading a passive-aggressive landlord text. |
| *Trading cards* | Each persona as a collectible card: portrait, stats (Verbosity, Politeness, Menace), signature move. |
| *The trailer* | 15-30s fake movie trailer: "This summer… one keyboard… refuses to text 'k'." |
| *Before/after* | Split screen: "k" → the full Sir Humphrey paragraph it becomes. |
| *Costume shop* | The keyboard as a tailor's shop fitting your sentence with a top hat. |

## Daily milestone ladder — Weeks 1-2 (bootstrap sprint)

Each day = **1 merged agent task + 1 generated asset + 1 post draft** (post
drafts accumulate in `marketing/posts/`; publishing starts Day 10).

| Day | Agent task (merge target) | Asset (credits) | Post draft |
|---|---|---|---|
| 1 | Phase 0 close-out: schema doc, ADRs, CI green | Persona trading card #1 (Jeeves) | "We're building X" teaser |
| 2 | Android Gradle scaffold + walking-skeleton IME | Trading card #2 (Sir Humphrey) | Thin-IME architecture, funny version |
| 3 | core-personas: YAML parse + prompt builder + golden tests | Trading card #3 (Schultz) | "Our test suite includes a butler" |
| 4 | CompletionProvider iface + Gemini free-tier impl | Trading card #4 (Bachchan) | BYOK explainer |
| 5 | Keyboard panel UI (chips, results, commit) | *Persona reacts* #1 | First screenshot post |
| 6 | Anthropic + OpenAI + OpenRouter providers | *Before/after* #1 | Provider-agnostic pitch |
| 7 | Settings/onboarding activity | *Persona reacts* #2 | Week-1 recap thread |
| 8 | Transform-existing-draft (getExtractedText) | *Before/after* #2 | Demo GIF post |
| 9 | Keystore key storage + provider routing | *Costume shop* still | Privacy-story post |
| 10 | Polish pass + internal APK on GitHub Releases | **The trailer v1** | 🚀 Soft-launch post → r/androidapps, X |
| 11 | Bug fixes from first installs | *Persona reacts* #3 | "Patch notes, TF2 style" |
| 12 | Persona import (user YAMLs) | Trading card template for community | "Make your own persona" call |
| 13 | Instrumented smoke test + CI APK artifacts | *Before/after* #3 | Engineering-showcase post (agents built this) |
| 14 | Release v0.1.0 tag | **Trailer v2** with real screen recordings | Week-2 recap + r/fossdroid |

## Weeks 3+ — cruise cadence

- **Weekly**: one feature milestone from ROADMAP.md Phase 2.
- **Daily**: one asset + queue posts; "Persona of the Week" once community
  YAMLs exist.
- **Launch ladder**: GitHub Releases soft launch (Day 10) → Reddit
  (r/androidapps, r/fossdroid, r/SideProject) → Product Hunt **when Phase 2
  suggested-replies lands** (that's the demo that converts) → F-Droid listing
  → Hacker News "Show HN" once F-Droid approves (credibility stack complete).

## Channels & angles

| Channel | Angle |
|---|---|
| X/Twitter | Daily assets, build-in-public thread, patch notes |
| Reddit | FOSS + privacy angle; the *code is the receipts* pitch |
| LinkedIn | The staff-engineering showcase: AI-agent-maintained repo, module law, golden tests |
| Product Hunt | The wow demo: incoming text → three Jeeves replies |
| Hacker News | Show HN post focused on the agent-maintained-repo angle |

## Money (noted, dormant)

Recorded here so we don't re-litigate it monthly:

1. **Donations from day one** — GitHub Sponsors + Ko-fi in the README footer.
   Costs nothing, offends no one.
2. **Hosted-key tier, only if traction demands** — BYOK stays free forever;
   a $2-4/mo "we run the keys so you don't have to" tier is the
   market-standard freemium shape (see CleverType/WittyKeys). Requires a
   proxy backend — do not build until install numbers justify a server bill.
3. **Never ads.** A keyboard with ads is a keyboard that reads your texts
   for a living. Hard no, forever, also it would ruin the trailer.

## Metrics that matter (keep it honest)

- GitHub stars (showcase KPI), APK downloads / F-Droid installs (product KPI),
  persona-pack PRs from strangers (community KPI — the real one).
- Vanity metrics we will glance at and pretend not to care about: post
  impressions.
