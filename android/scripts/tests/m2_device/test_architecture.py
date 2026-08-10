"""Architecture tests: enforce the 6-module set and line-budget limits.

Verifies that only allowed production modules exist in android/scripts/m2_device/
and that their nonblank, non-comment line counts fit within the complexity budget:
- adb_harness.py <= 650 lines
- Total of all 6 modules combined <= 1800 lines
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

        # Ignore __init__.py
        actual_files.discard('__init__.py')

        extra_files = actual_files - self.allowed_modules
        self.assertFalse(
            extra_files,
            f"Unauthorized production modules found: {extra_files}"
        )

    def test_line_budgets(self):
        total_lines = 0
        module_counts = {}

        for name in sorted(self.allowed_modules):
            path = os.path.join(self.base_dir, name)
            if not os.path.exists(path):
                # If a module does not exist yet (e.g. adb_harness during early U-units),
                # we treat it as 0 lines so early tests can pass.
                module_counts[name] = 0
                continue
            count = count_lines(path)
            module_counts[name] = count
            total_lines += count

        # adb_harness.py <= 650 lines
        self.assertLessEqual(
            module_counts.get('adb_harness.py', 0),
            650,
            f"adb_harness.py exceeds 650 lines: {module_counts.get('adb_harness.py', 0)}"
        )

        # total <= 1800 lines
        self.assertLessEqual(
            total_lines,
            1800,
            f"Total production line count exceeds 1800 lines: {total_lines} ({module_counts})"
        )


if __name__ == "__main__":
    unittest.main()
