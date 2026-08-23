"""Architecture tests: enforce the 6-module set and line-budget limits.

Verifies that only allowed production modules exist in android/scripts/m2_device/
and that their nonblank, non-comment line counts fit within the complexity budget:
- adb_harness.py <= 1100 lines (raised to a round number 2026-08-16 at review
  suggestion: 997/1000 left no headroom for the next change; the raise buys
  the #64/#62 stages rather than one amendment each)
- Total of all 6 modules combined <= 2700 lines (same rationale; #64 grows
  evidence.py/cli.py)
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

        # adb_harness.py <= 1100 lines (2026-08-16: round-number raise at
        # review suggestion — 997/1000 had no headroom; buys the #64/#62
        # stages instead of one amendment per change)
        self.assertLessEqual(
            module_counts.get('adb_harness.py', 0),
            1100,
            f"adb_harness.py exceeds 1100 lines: {module_counts.get('adb_harness.py', 0)}"
        )

        # total <= 2750 lines (2026-08-23: round-number raise for the
        # #82 dry-run corrections — engine-log capture, post-restore
        # settle, shift protocol, and diagnostic headless mode consume
        # the 2026-08-16 headroom; stated in the correction PR)
        self.assertLessEqual(
            total_lines,
            2750,
            f"Total production line count exceeds 2750 lines: {total_lines} ({module_counts})"
        )


if __name__ == "__main__":
    unittest.main()
