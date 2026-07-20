#!/usr/bin/env python3
"""Validate personas/*.yaml against the schema in docs/persona-schema.md."""

import sys
from pathlib import Path

import yaml

PERSONAS_DIR = Path(__file__).parent.parent / "personas"


def validate(path: Path) -> list[str]:
    errors = []
    data = yaml.safe_load(path.read_text())

    if not isinstance(data, dict):
        return [f"{path.name}: not a mapping"]

    if not isinstance(data.get("name"), str) or not data["name"].strip():
        errors.append(f"{path.name}: 'name' is required and must be a non-empty string")

    patterns = data.get("speech_patterns")
    if not isinstance(patterns, list) or not patterns:
        errors.append(f"{path.name}: 'speech_patterns' is required and must be a non-empty list")
    elif not all(isinstance(p, str) for p in patterns):
        errors.append(f"{path.name}: every 'speech_patterns' entry must be a string")

    for optional_list in ("vocabulary", "sample_lines"):
        value = data.get(optional_list)
        if value is not None and (
            not isinstance(value, list) or not all(isinstance(v, str) for v in value)
        ):
            errors.append(f"{path.name}: '{optional_list}' must be a list of strings")

    for optional_str in ("context", "notes"):
        value = data.get(optional_str)
        if value is not None and not isinstance(value, str):
            errors.append(f"{path.name}: '{optional_str}' must be a string")

    if data.get("real_person") is True and not data.get("notes"):
        errors.append(
            f"{path.name}: real_person is true, so 'notes' must declare this a "
            "stylistic homage (see docs/persona-schema.md)"
        )

    version = data.get("schema_version", 1)
    if version != 1:
        errors.append(f"{path.name}: unsupported schema_version {version}")

    return errors


def main() -> None:
    files = sorted(PERSONAS_DIR.glob("*.yaml"))
    if not files:
        raise SystemExit("no persona files found — that can't be right")

    all_errors = [e for f in files for e in validate(f)]
    if all_errors:
        print("Persona validation failed. The butler is disappointed:")
        for e in all_errors:
            print(f"  - {e}")
        sys.exit(1)
    print(f"{len(files)} personas validated. Impeccable, sir.")


if __name__ == "__main__":
    main()
