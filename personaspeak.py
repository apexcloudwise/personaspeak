#!/usr/bin/env python3
"""Rewrite text in the voice of a chosen persona, using the Claude API."""

import argparse
import os
import sys
from pathlib import Path

import anthropic
import yaml

PERSONAS_DIR = Path(__file__).parent / "personas"

MODEL_ALIASES = {
    "haiku": "claude-haiku-4-5",
    "sonnet": "claude-sonnet-5",
    "opus": "claude-opus-4-8",
}
DEFAULT_MODEL = MODEL_ALIASES["haiku"]


def load_persona(name: str) -> dict:
    path = PERSONAS_DIR / f"{name}.yaml"
    if not path.exists():
        available = ", ".join(sorted(p.stem for p in PERSONAS_DIR.glob("*.yaml")))
        raise SystemExit(f"Unknown persona '{name}'. Available: {available}")
    with path.open() as f:
        return yaml.safe_load(f)


def list_personas() -> None:
    for path in sorted(PERSONAS_DIR.glob("*.yaml")):
        persona = yaml.safe_load(path.read_text())
        print(f"{path.stem:20s} {persona.get('name', '')}")


def build_system_prompt(persona: dict) -> str:
    lines = [
        "You are a text style-transfer engine. Rewrite the user's message so it "
        f"sounds like it was spoken by {persona['name']}{persona.get('context', '')}.",
        "",
        "Voice characteristics:",
    ]
    for trait in persona.get("speech_patterns", []):
        lines.append(f"- {trait}")

    if persona.get("vocabulary"):
        lines.append("")
        lines.append("Characteristic vocabulary/phrases to draw on: " + ", ".join(persona["vocabulary"]))

    if persona.get("sample_lines"):
        lines.append("")
        lines.append("Example lines in this voice (for tone/rhythm reference, don't copy them verbatim):")
        for sample in persona["sample_lines"]:
            lines.append(f'- "{sample}"')

    if persona.get("notes"):
        lines.append("")
        lines.append(f"Notes: {persona['notes'].strip()}")

    lines += [
        "",
        "Rewrite the user's message fully in this voice, preserving its original meaning. "
        "Output only the rewritten text — no preamble, no explanation, no quotation marks around it.",
    ]
    return "\n".join(lines)


def rewrite(text: str, persona: dict, model: str) -> str:
    client = anthropic.Anthropic()  # reads ANTHROPIC_API_KEY from env
    system_prompt = build_system_prompt(persona)

    try:
        response = client.messages.create(
            model=model,
            max_tokens=1024,
            system=system_prompt,
            messages=[{"role": "user", "content": text}],
        )
    except anthropic.AuthenticationError:
        raise SystemExit(
            "Authentication failed. Set the ANTHROPIC_API_KEY environment variable "
            "(or run `ant auth login` if you have the Anthropic CLI)."
        )
    except anthropic.APIStatusError as e:
        raise SystemExit(f"API error ({e.status_code}): {e.message}")

    return next((b.text for b in response.content if b.type == "text"), "")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("text", nargs="*", help="Text to rewrite. Reads stdin if omitted.")
    parser.add_argument("--as", "--persona", dest="persona", help="Persona name (see --list).")
    parser.add_argument(
        "--model",
        default=DEFAULT_MODEL,
        help=f"Model alias (haiku/sonnet/opus) or full model ID. Default: {DEFAULT_MODEL}",
    )
    parser.add_argument("--list", action="store_true", help="List available personas and exit.")
    args = parser.parse_args()

    if args.list:
        list_personas()
        return

    if not args.persona:
        parser.error("--as/--persona is required (use --list to see options)")

    text = " ".join(args.text) if args.text else sys.stdin.read().strip()
    if not text:
        parser.error("no input text provided")

    model = MODEL_ALIASES.get(args.model, args.model)
    persona = load_persona(args.persona)
    print(rewrite(text, persona, model))


if __name__ == "__main__":
    main()
