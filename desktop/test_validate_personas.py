import unittest
from pathlib import Path

from desktop.validate_personas import validate


FIXTURES = Path(__file__).parent.parent / "tests" / "persona-validation"


class PersonaValidationTest(unittest.TestCase):
    def test_valid_minimal(self) -> None:
        self.assertEqual([], validate(FIXTURES / "valid-minimal.yaml"))

    def test_real_person_requires_notes(self) -> None:
        errors = validate(FIXTURES / "invalid-real-person-no-notes.yaml")
        self.assertTrue(any("notes" in error for error in errors), errors)

    def test_pattern_entries_are_strings(self) -> None:
        errors = validate(FIXTURES / "invalid-pattern-entry.yaml")
        self.assertTrue(any("entry must be a string" in error for error in errors), errors)

    def test_real_person_is_boolean(self) -> None:
        errors = validate(FIXTURES / "invalid-real-person-type.yaml")
        self.assertTrue(any("real_person" in error for error in errors), errors)

    def test_schema_version_is_rejected(self) -> None:
        errors = validate(FIXTURES / "unsupported-version.yaml")
        self.assertTrue(any("unsupported schema_version 2" in error for error in errors), errors)

    def test_schema_version_must_be_an_integer(self) -> None:
        errors = validate(FIXTURES / "invalid-fractional-version.yaml")
        self.assertTrue(any("schema_version' must be an integer" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
