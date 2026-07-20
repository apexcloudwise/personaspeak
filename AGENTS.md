# AGENTS.md — House Rules for Robot Staff

You are an AI agent working on this repo. Welcome. The pay is nothing, the
standards are high, and the dress code is butler. This file is the contract.

## Prime directives

1. **Ship demoable slices.** Every merged change leaves `main` in a state we
   could show someone. No half-built features parked on main; if it's big,
   land it behind a walking skeleton or a flag.
2. **The persona YAML schema is an API.** `personas/*.yaml` is consumed by the
   desktop CLI, the Claude skill, and the Android app. Changing the schema
   means: bump `schema_version`, update `docs/persona-schema.md`, update every
   consumer, and update the golden tests — in the same PR.
3. **Docs pass VOICE.md.** All prose (README, changelogs, release notes, error
   messages) follows [VOICE.md](VOICE.md). Code comments and API references
   stay plain and technical — the voice is for humans reading docs, not for
   `// here we leverage the whimsy`.
4. **Don't break the butler.** `desktop/personaspeak.py --list` and the golden
   prompt tests must pass before any commit that touches `personas/` or
   prompt-building code.

## Module law (Android)

```
core-personas   → pure Kotlin. No Android imports. Ever.
core-providers  → pure Kotlin + HTTP. No Android imports except where a
                  provider genuinely requires platform APIs (on-device model);
                  isolate those in their own source set.
keyboard        → the IME. In flux — see below.
app             → depends on core-*. Settings/onboarding.
```

Dependency arrows point inward only. If `core-personas` ever imports
`android.*`, the build should fail and so should the PR. **That rule is not
negotiable and does not change with the fork** — those two modules are the
project, and their portability is what makes the fork survivable.

**`keyboard` is being replaced.** This module used to be a thin, keyless
persona panel and the law read "keep it thin." As of 2026-07-20 PersonaSpeak
forks a full open-source keyboard (ADR-0001 superseded), so `keyboard` becomes
a large inherited codebase — the opposite of thin. The base is undecided;
until it is, don't invest in `keyboard/` beyond what the fork spike needs.

The replacement contract lands with the fork ADR. Until then, treat as binding:

- `core-personas` and `core-providers` stay pure and stay ours.
- PersonaSpeak-specific code in the fork lives in clearly separated packages,
  never scattered through inherited files — **upstream lines modified are rent
  paid forever**, one merge conflict each.
- Current state:
  [`docs/superpowers/specs/2026-07-20-fork-spike-checkpoint.md`](docs/superpowers/specs/2026-07-20-fork-spike-checkpoint.md)
  and [`2026-07-20-keyboard-ux-design.md`](docs/superpowers/specs/2026-07-20-keyboard-ux-design.md).

## Workflow

- **Conventional commits** (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`,
  `chore:`). Scope optional, wit optional but appreciated, accuracy mandatory.
- **One concern per PR.** A PR that adds a feature, reformats a module, and
  fixes an unrelated bug is three PRs wearing a trench coat.
- **ADRs for architecture.** Decisions that shape modules, dependencies, data
  formats, or privacy posture get a short ADR in `docs/adr/` (copy the format
  of ADR-0001). Small, funny, real.
- **Tests are the spec.** Golden tests pin the persona→prompt transformation.
  Provider implementations get contract tests against fakes. If you fix a bug,
  the regression test rides in the same PR.

## Things that will get your PR returned with a raised eyebrow

- Adding a dependency where 30 lines of code would do.
- "Various improvements" as a commit message.
- Touching the persona schema without touching its consumers.
- Corporate speak in user-facing prose (see VOICE.md's banned-words list).
- Storing anything a user typed. We are a keyboard, not a diary.
- Anything that makes the privacy story more complicated to explain.

## For Claude Code specifically

- The `/personaspeak` skill in `.claude/skills/` rewrites text in-session —
  use it to taste-test persona changes without spending API tokens.
- When creating a new persona, follow the schema in `docs/persona-schema.md`
  and match the tone-density of existing files: ~7 speech patterns, ~7
  vocabulary items, 3-4 sample lines.

## Agent skills

### Issue tracker

Issues live in GitHub Issues for `apexcloudwise/personaspeak`, via the `gh`
CLI. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context — one `CONTEXT.md` + `docs/adr/` at the repo root. See
`docs/agents/domain.md`.
