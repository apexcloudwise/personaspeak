"""Mechanical check: production status-decision sites match an enumerated set.

Parses orchestrator.py's AST and asserts every call to _rc_of or _timed_out
is at an expected (method, function) location. If someone adds a new
status-decision site without updating EXPECTED_SITES, this test fails
loudly. A check that has never failed is not a check.
"""

import ast
import inspect
import unittest

from android.scripts.m2_device import orchestrator as O


EXPECTED_SITES = frozenset({
    ("_run_phase", "_rc_of"),
    ("_run_phase", "_timed_out"),
    ("_restore", "_rc_of"),
})

_DISPATCH_FUNCS = frozenset({"_rc_of", "_timed_out"})


def _find_dispatch_calls(source: str) -> set[tuple[str, str]]:
    tree = ast.parse(source)
    found: set[tuple[str, str]] = set()

    class Visitor(ast.NodeVisitor):
        def __init__(self):
            self.func_stack: list[str] = []

        def visit_FunctionDef(self, node):
            self.func_stack.append(node.name)
            self.generic_visit(node)
            self.func_stack.pop()

        def visit_Call(self, node):
            func = node.func
            if isinstance(func, ast.Name) and func.id in _DISPATCH_FUNCS:
                enclosing = self.func_stack[-1] if self.func_stack else "<module>"
                found.add((enclosing, func.id))
            self.generic_visit(node)

    Visitor().visit(tree)
    return found


class TestCoverageMatrix(unittest.TestCase):

    def test_dispatch_call_sites_match_expected(self):
        source = inspect.getsource(O)
        actual = _find_dispatch_calls(source)
        self.assertEqual(
            actual, EXPECTED_SITES,
            f"\nExpected: {sorted(EXPECTED_SITES)}\n"
            f"Actual:   {sorted(actual)}\n"
            f"If you added or moved a _rc_of/_timed_out call, "
            f"update EXPECTED_SITES in this test.",
        )

    def test_no_dispatch_at_module_level(self):
        source = inspect.getsource(O)
        actual = _find_dispatch_calls(source)
        module_level = {s for s in actual if s[0] == "<module>"}
        self.assertFalse(
            module_level,
            f"_rc_of/_timed_out called at module level: {module_level}",
        )


if __name__ == "__main__":
    unittest.main()
