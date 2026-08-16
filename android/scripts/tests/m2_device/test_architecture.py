"""Architecture tests: enforce the 6-module set and line-budget limits.

Verifies that only allowed production modules exist in android/scripts/m2_device/
and that their nonblank, non-comment line counts fit within the complexity budget:
- adb_harness.py <= 1000 lines (raised 2026-08-16 for #63: fixture transaction,
  pristine/editor pins, key-geometry validation, stale retention, private
  restoration facts)
- Total of all 6 modules combined <= 2500 lines (same #63 round)
"""

import os
import unittest


def count_lines(path: str) -> int:
    count = 0
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            stripped = line.strip()
            if stripped and not stripped.startswith('#'):
                count += 1
    return count


class TestArchitecture(unittest.TestCase):

    def setUp(self):
        self.base_dir = os.path.abspath(
            os.path.join(
                os.path.dirname(__file__),
                '../../m2_device'
            )
        )
        self.allowed_modules = {
            'cli.py',
            'commands.py',
            'evidence.py',
            'orchestrator.py',
            'records.py',
            'adb_harness.py',
        }

    def test_allowed_modules_only(self):
        actual_files = set()
        for name in os.listdir(self.base_dir):
            if name.endswith('.py'):
                actual_files.add(name)

        actual_files.discard('__init__.py')

        extra_files = actual_files - self.allowed_modules
        self.assertFalse(
            extra_files,
            f"Unauthorized production modules found: {extra_files}"
        )

        missing = self.allowed_modules - actual_files
        self.assertFalse(
            missing,
            f"Required production modules missing: {missing}"
        )

    def test_no_subpackages(self):
        for name in os.listdir(self.base_dir):
            full = os.path.join(self.base_dir, name)
            if os.path.isdir(full):
                for root, _, files in os.walk(full):
                    for f in files:
                        if f.endswith('.py'):
                            self.fail(
                                f"Unauthorized subpackage .py: {os.path.join(root, f)}"
                            )

    def test_line_budgets(self):
        total_lines = 0
        module_counts = {}

        for name in sorted(self.allowed_modules):
            path = os.path.join(self.base_dir, name)
            self.assertTrue(
                os.path.exists(path),
                f"Required module missing: {name}"
            )
            count = count_lines(path)
            module_counts[name] = count
            total_lines += count

        # adb_harness.py <= 1000 lines (2026-08-16 #63: fixture
        # transaction, pins, key geometry, stale retention)
        self.assertLessEqual(
            module_counts.get('adb_harness.py', 0),
            1000,
            f"adb_harness.py exceeds 1000 lines: {module_counts.get('adb_harness.py', 0)}"
        )

        # total <= 2500 lines (2026-08-16 #63)
        self.assertLessEqual(
            total_lines,
            2500,
            f"Total production line count exceeds 2500 lines: {total_lines} ({module_counts})"
        )


if __name__ == "__main__":
    unittest.main()
