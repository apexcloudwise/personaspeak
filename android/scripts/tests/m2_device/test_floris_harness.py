"""Unit tests for the FlorisBoard second-host journey harness pins.

The pins were calibrated live on the M2_Qual_Fixture (2026-09-03) with
effect-verified taps; these tests pin the structural contract — the
host identity facts, the provider echo formula shared with the fake
toolchain, and the canonical artifact set separation — so drift fails
device-free.
"""

from __future__ import annotations

import os
import unittest

from android.scripts.m2_device.adb_harness import (
    AdbHarness,
    ASK_KEY_COORDS,
    CANDIDATE_REPHRASING,
    EXPECTED_VERSION_CODE as ASK_VC,
    EXPECTED_VERSION_NAME as ASK_VN,
    IME_COMPACT_TOP as ASK_COMPACT,
    IME_COMPONENT as ASK_COMPONENT,
    KEYBOARD_PACKAGE as ASK_PACKAGE,
)
from android.scripts.m2_device import evidence
from android.scripts.m2_device.floris_harness import (
    FLORIS_APPLY_TAP,
    FLORIS_CANCEL_TAP,
    FLORIS_COMPOSING_CANDIDATE,
    FLORIS_COMPOSING_SOURCE,
    FLORIS_DISMISS_TAP,
    FLORIS_IME_COMPACT_TOP,
    FLORIS_IME_EXPANDED_MAX_TOP,
    FLORIS_KEY_COORDS,
    FLORIS_REWRITE_TAP,
    FLORIS_SETTINGS_TAP,
    FLORIS_SHIFT_TAP,
    FlorisAdbHarness,
    floris_candidate,
)


class TestFlorisHostFacts(unittest.TestCase):

    def setUp(self):
        self.h = FlorisAdbHarness(run_dir="/tmp/x", apk_path="/tmp/a.apk")

    def test_identity_is_the_floris_debug_build(self):
        self.assertEqual(
            self.h.KEYBOARD_PACKAGE,
            "biz.pixelperfectstudios.personaspeak.floris.debug")
        self.assertEqual(
            self.h.IME_COMPONENT,
            "biz.pixelperfectstudios.personaspeak.floris.debug"
            "/dev.patrickgold.florisboard.FlorisImeService")
        self.assertNotEqual(self.h.KEYBOARD_PACKAGE, ASK_PACKAGE)
        self.assertNotEqual(self.h.IME_COMPONENT, ASK_COMPONENT)

    def test_version_pins_are_florisboard_v052_debug(self):
        self.assertEqual(self.h.EXPECTED_VERSION_NAME, "0.5.2-debug+null")
        self.assertEqual(self.h.EXPECTED_VERSION_CODE, "117")
        self.assertNotEqual(self.h.EXPECTED_VERSION_NAME, ASK_VN)
        self.assertNotEqual(self.h.EXPECTED_VERSION_CODE, ASK_VC)

    def test_signer_gate_pins_the_shared_debug_certificate(self):
        # Both hosts' debug builds sign with the same local debug
        # certificate (verified on the fixture 2026-09-03), so the
        # floris gate inherits the ASK digest byte-for-byte.
        self.assertEqual(
            self.h.EXPECTED_SIGNER_CERT_SHA256,
            AdbHarness.EXPECTED_SIGNER_CERT_SHA256)

    def test_geometry_pins_differ_from_ask(self):
        self.assertNotEqual(FLORIS_KEY_COORDS, ASK_KEY_COORDS)
        self.assertNotEqual(FLORIS_IME_COMPACT_TOP, ASK_COMPACT)
        # Every needed glyph for both drafts and the composing variant.
        for ch in "TEASIXVN .":
            self.assertIn(ch, FLORIS_KEY_COORDS, f"missing key {ch!r}")
        # Compact/expanded discrimination window: the measured expanded
        # top (1148) must sit well inside the bound, and the bound must
        # stay clear of the compact top.
        self.assertLess(FLORIS_IME_EXPANDED_MAX_TOP, FLORIS_IME_COMPACT_TOP)
        self.assertGreater(FLORIS_IME_EXPANDED_MAX_TOP, 1200)

    def test_row_taps_are_layout_positions(self):
        # Rewrite and Cancel share the compact row slot (state-first
        # dispatch, like the redesigned ASK strip); Apply and Dismiss
        # are separate slots.
        self.assertEqual(FLORIS_REWRITE_TAP, FLORIS_CANCEL_TAP)
        self.assertNotEqual(FLORIS_APPLY_TAP, FLORIS_DISMISS_TAP)
        for tap in (FLORIS_REWRITE_TAP, FLORIS_APPLY_TAP,
                    FLORIS_DISMISS_TAP, FLORIS_SETTINGS_TAP,
                    FLORIS_SHIFT_TAP):
            self.assertEqual(len(tap), 2)

    def test_rewrite_cancel_shell_embeds_inter_tap_sleep(self):
        argv = self.h._rewrite_cancel_shell()
        joined = " ".join(argv)
        self.assertIn("sleep", joined)
        # Two taps, three shell segments, one transport.
        self.assertEqual(argv.count(";"), 2)
        self.assertEqual(sum(1 for a in argv if a == "input"), 2)


class TestProviderEchoFormula(unittest.TestCase):

    def test_source_draft_reproduces_the_ask_candidate(self):
        # The fixture FakeProvider echoes the captured draft; the ASK
        # constant is the "Tea at six." instance of that contract, so
        # the floris fake and harness stay byte-consistent with it.
        self.assertEqual(floris_candidate("Tea at six."), CANDIDATE_REPHRASING)

    def test_composing_candidate_quotes_the_unfinalized_draft(self):
        self.assertEqual(FLORIS_COMPOSING_SOURCE, "Tea at six")
        self.assertNotIn("\u201cTea at six.\u201d", FLORIS_COMPOSING_CANDIDATE)
        self.assertIn("\u201cTea at six\u201d", FLORIS_COMPOSING_CANDIDATE)


class TestFlorisCanonicalSet(unittest.TestCase):

    def test_counts_match_the_journey_design(self):
        self.assertEqual(len(evidence.FLORIS_CANONICAL_PNG_NAMES), 9)
        self.assertEqual(len(evidence.FLORIS_CANONICAL_HIERARCHY_LABELS), 25)
        self.assertEqual(
            len(FlorisAdbHarness.SCREENSHOT_NAMES), 9)
        self.assertEqual(
            len(FlorisAdbHarness.HIERARCHY_LABELS), 25)

    def test_floris_manifest_enforced_against_its_own_set(self):
        evidence.enforce_canonical_set(
            set(evidence.FLORIS_CANONICAL_ARTIFACTS),
            evidence.FLORIS_CANONICAL_ARTIFACTS)

    def test_floris_manifest_rejected_by_the_ask_default(self):
        with self.assertRaises(ValueError):
            evidence.enforce_canonical_set(
                set(evidence.FLORIS_CANONICAL_ARTIFACTS))

    def test_ask_manifest_rejected_by_the_floris_set(self):
        with self.assertRaises(ValueError):
            evidence.enforce_canonical_set(
                set(evidence.CANONICAL_ARTIFACTS),
                evidence.FLORIS_CANONICAL_ARTIFACTS)

    def test_composing_and_settings_artifacts_present(self):
        floris = set(evidence.FLORIS_CANONICAL_ARTIFACTS)
        for name in ("07-composing-typed.png", "08-composing-applied.png",
                     "09-floris-settings.png", "after_composing.xml",
                     "floris_settings.xml"):
            self.assertIn(name, floris)


if __name__ == "__main__":
    unittest.main()
