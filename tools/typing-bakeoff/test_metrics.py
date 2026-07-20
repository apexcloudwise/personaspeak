#!/usr/bin/env python3
"""Unit tests for metrics.py. Hand-computed cases only -- no device data, no
live corpus. Run with:

    python -m unittest tools.typing-bakeoff.test_metrics

or, from inside tools/typing-bakeoff/:

    python -m unittest test_metrics
"""

import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import metrics as m  # noqa: E402


class TestLevenshtein(unittest.TestCase):
    def test_identical_is_zero(self):
        self.assertEqual(m.levenshtein("hello", "hello"), 0)

    def test_empty_string(self):
        self.assertEqual(m.levenshtein("", "abc"), 3)
        self.assertEqual(m.levenshtein("abc", ""), 3)
        self.assertEqual(m.levenshtein("", ""), 0)

    def test_single_substitution(self):
        # "hello" -> "hallo": one substitution (e -> a)
        self.assertEqual(m.levenshtein("hello", "hallo"), 1)

    def test_one_insertion(self):
        # "cat" -> "chat": one insertion
        self.assertEqual(m.levenshtein("cat", "chat"), 1)

    def test_one_deletion(self):
        # "chat" -> "cat": one deletion
        self.assertEqual(m.levenshtein("chat", "cat"), 1)

    def test_transposition_is_two_ops(self):
        # Levenshtein does not have a transposition op; "ab" -> "ba" is 2 subs.
        self.assertEqual(m.levenshtein("ab", "ba"), 2)

    def test_known_phrase_pair(self):
        # "this is a test" -> "thus us a test": two substitutions
        self.assertEqual(m.levenshtein("this is a test", "thus us a test"), 2)


class TestWPM(unittest.TestCase):
    def test_textbook_value(self):
        # 50 chars in 30 seconds: (50/5) / (30/60) = 10 / 0.5 = 20.0 WPM
        self.assertAlmostEqual(m.wpm(50, 30.0), 20.0, places=6)

    def test_short_burst(self):
        # 5 chars in 6 seconds: (5/5) / (6/60) = 1 / 0.1 = 10.0 WPM
        self.assertAlmostEqual(m.wpm(5, 6.0), 10.0, places=6)

    def test_zero_elapsed_returns_zero(self):
        self.assertEqual(m.wpm(50, 0.0), 0.0)

    def test_negative_elapsed_returns_zero(self):
        self.assertEqual(m.wpm(50, -1.0), 0.0)


class TestKSPC(unittest.TestCase):
    def test_clean_pass_is_one(self):
        # 9 keystrokes (no backspaces), 9 transcribed chars -> 1.0
        self.assertAlmostEqual(m.kspc(9, 9), 1.0, places=6)

    def test_one_backspace_every_five(self):
        # 12 keystrokes incl. 2 backspaces, 10 transcribed chars -> 1.2
        self.assertAlmostEqual(m.kspc(12, 10), 1.2, places=6)

    def test_no_output_returns_zero(self):
        self.assertEqual(m.kspc(5, 0), 0.0)


class TestErrorRates(unittest.TestCase):
    def test_perfect_phrase(self):
        # presented == transcribed, no fixes: everything zero.
        r = m.error_rates("hello", "hello", fixes=0)
        self.assertEqual(r.msd_distance, 0)
        self.assertAlmostEqual(r.uncorrected, 0.0)
        self.assertAlmostEqual(r.corrected, 0.0)
        self.assertAlmostEqual(r.total, 0.0)
        self.assertAlmostEqual(r.accuracy, 1.0)

    def test_uncorrected_single_typo(self):
        # "hello" -> "hallo": 1 substitution, no fixes.
        # INF=1, |T|=5, IF=0 -> denom=5; UER=0.2, CER=0, TER=0.2
        r = m.error_rates("hello", "hallo", fixes=0)
        self.assertEqual(r.msd_distance, 1)
        self.assertAlmostEqual(r.uncorrected, 0.2)
        self.assertAlmostEqual(r.corrected, 0.0)
        self.assertAlmostEqual(r.total, 0.2)

    def test_corrected_only(self):
        # Final text is correct but the user backspaced once.
        # INF=0, |T|=5, IF=1 -> denom=6; UER=0, CER=1/6, TER=1/6
        r = m.error_rates("hello", "hello", fixes=1)
        self.assertEqual(r.msd_distance, 0)
        self.assertAlmostEqual(r.uncorrected, 0.0)
        self.assertAlmostEqual(r.corrected, 1.0 / 6.0)
        self.assertAlmostEqual(r.total, 1.0 / 6.0)

    def test_both_corrected_and_uncorrected(self):
        # "hello" -> "hallo" with one fix used elsewhere.
        # INF=1, |T|=5, IF=1 -> denom=6; UER=1/6, CER=1/6, TER=2/6
        r = m.error_rates("hello", "hallo", fixes=1)
        self.assertEqual(r.msd_distance, 1)
        self.assertAlmostEqual(r.uncorrected, 1.0 / 6.0)
        self.assertAlmostEqual(r.corrected, 1.0 / 6.0)
        self.assertAlmostEqual(r.total, 2.0 / 6.0)

    def test_total_equals_sum_of_parts(self):
        # TER must always equal UER + CER exactly.
        r = m.error_rates("the quick brown fox", "teh quik brown fx", fixes=3)
        self.assertAlmostEqual(r.total, r.uncorrected + r.corrected, places=9)

    def test_empty_transcribed_does_not_explode(self):
        # Degenerate: nothing committed. denom=0 -> all rates zero, no crash.
        r = m.error_rates("hello", "", fixes=0)
        self.assertEqual(r.msd_distance, 5)
        self.assertAlmostEqual(r.uncorrected, 0.0)
        self.assertAlmostEqual(r.total, 0.0)

    def test_negative_fixes_treated_as_zero(self):
        # Defensive: a buggy capture shouldn't push CER negative.
        r = m.error_rates("hello", "hello", fixes=-3)
        self.assertAlmostEqual(r.corrected, 0.0)
        self.assertAlmostEqual(r.total, 0.0)


class TestAggregate(unittest.TestCase):
    def test_empty_records(self):
        agg = m.aggregate([])
        self.assertEqual(agg["phrases"], 0)
        self.assertEqual(agg["wpm"], 0.0)
        self.assertEqual(agg["kspc"], 0.0)

    def test_corpus_kspc_pools_keystrokes(self):
        # Two phrases: (keys, chars) = (10, 10) and (10, 5).
        # Pooled KSPC = 20/15, NOT mean(1.0, 2.0) = 1.5
        records = [
            {"input_stream_length": 10, "transcribed_chars": 10, "wpm": 20.0,
             "uncorrected_error_rate": 0.0, "corrected_error_rate": 0.0,
             "total_error_rate": 0.0},
            {"input_stream_length": 10, "transcribed_chars": 5, "wpm": 40.0,
             "uncorrected_error_rate": 0.1, "corrected_error_rate": 0.1,
             "total_error_rate": 0.2},
        ]
        agg = m.aggregate(records)
        self.assertAlmostEqual(agg["kspc"], 20.0 / 15.0, places=6)
        self.assertEqual(agg["phrases"], 2)
        # arithmetic mean of 20.0 and 40.0
        self.assertAlmostEqual(agg["wpm"], 30.0, places=6)


if __name__ == "__main__":
    unittest.main()
