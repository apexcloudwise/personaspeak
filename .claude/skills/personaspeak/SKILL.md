---
name: personaspeak
description: Rewrite a piece of text in the voice of a specific persona (Jeeves, Dr. King Schultz, Sir Humphrey Appleby, Amitabh Bachchan, or any persona added to personas/*.yaml). Use whenever the user asks to convert/translate/rewrite text into someone's speaking style, says "make this sound like X", or invokes /personaspeak.
---

# personaspeak

Rewrite text in a persona's voice **directly in this conversation** — do not shell out to the Anthropic API or run `personaspeak.py`. You are the rewriting engine; this skill just tells you how.

## Inputs

The user will supply, in any order/format:
- a **persona name** (e.g. `jeeves`, `dr-schultz`, `sir-humphrey`, `amitabh-bachchan`, or a persona not yet defined)
- the **text** to rewrite

If the persona is ambiguous or missing, list the available personas (see below) and ask which one before proceeding. If the text is missing, ask for it.

## Steps

1. **List available personas** by running `ls personas/*.yaml` (relative to this project's root) or via Glob. Match the user's requested persona against the filenames/`name:` fields — be forgiving of case and spacing ("dr schultz", "Dr. Schultz" → `dr-schultz.yaml`).

2. **If the persona exists:** Read the YAML file. It has this schema:
   - `name` — the persona's display name
   - `context` — a short parenthetical description of who they are
   - `speech_patterns` — a list of stylistic traits (sentence rhythm, tone, quirks)
   - `vocabulary` — characteristic words/phrases to draw on
   - `sample_lines` — a few example quotes for tone/rhythm reference (don't copy verbatim)
   - `notes` — optional caveats (e.g. "this is a stylistic homage, not a claim about the real person's private speech")

3. **If the persona doesn't exist yet:** Offer to create it. Ask the user for (or infer from context) enough detail to fill the same schema — a couple of speech patterns, some characteristic vocabulary, and 2-4 sample lines are usually enough. Write the new file to `personas/<slug>.yaml` following the exact structure of the existing files, then proceed to rewrite using it. This is how the persona library grows over time.

4. **Rewrite the text** fully in that voice:
   - Preserve the original meaning and rough length — this is style transfer, not summarization or embellishment.
   - Lean on the persona's `speech_patterns` and `vocabulary`; use `sample_lines` only as a rhythm/tone reference, never copy them verbatim into the output.
   - Respect any `notes` (e.g. keep a real public figure's persona to a performance-style homage, not a claim about what they actually said or believe).
   - Output **only the rewritten text** — no preamble like "Here's the rewrite:", no meta-commentary, unless the user asked for an explanation too.

## Example

> User: `/personaspeak jeeves: hey can you grab me a coffee, I'm swamped right now`

Read `personas/jeeves.yaml`, then respond with something like:

> If I may say so, one does hate to impose upon one's own time, but might I trouble you to fetch a coffee at your earliest convenience? I find myself rather thoroughly swamped at present.

No tool calls to an external API are needed — the rewrite is produced directly as your response.
