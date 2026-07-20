# CONTRIBUTING.md — Humans Welcome Too

Most code here is written by AI agents and reviewed by a human, but
contributions from actual people are received with delight and mild surprise.

## The fast path: contribute a persona

The easiest, funnest contribution is a new persona:

1. Copy any file in `personas/` as a template.
2. Fill in the schema (spec: [docs/persona-schema.md](docs/persona-schema.md)):
   ~7 `speech_patterns`, ~7 `vocabulary` phrases, 3-4 `sample_lines`.
3. **Real, living people get a `notes:` field** declaring the persona a
   stylistic homage to their public performance style — we do characters and
   personas, not impersonations. Personas designed to deceive, defame, or
   harass get closed with a polite Jeeves-style refusal.
4. Open a PR. CI checks the schema; a reviewer checks whether it made them
   laugh. Both gates are mandatory.

## Contributing code

- Read [AGENTS.md](AGENTS.md) — the house rules apply to carbon-based
  contributors too, minus the bits about being an AI.
- Read [VOICE.md](VOICE.md) before writing any user-facing prose.
- One concern per PR. Tests ride along. Conventional commits.

## Contributing bug reports

Open an issue with: what you did, what happened, what you expected instead.
Screenshots of keyboards misbehaving are accepted with gratitude and,
depending on severity, an apology in the voice of your choosing.
