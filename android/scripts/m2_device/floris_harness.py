"""FlorisBoard second-host device journey harness (ADR-0010, P2).

Reuses the M2 machinery end to end: same fixture transaction, same
install signer gate, same binding/window/editor-text channels, same
restore obligations. Only the host facts move — they were all
calibrated live on the M2_Qual_Fixture on 2026-09-03 with the
effect-verified probe method (raw-RGBA cluster scans for geometry,
editor-text bridge for every tap's effect).

The row-tap coordinates below are the panel's LAYOUT positions, not
where the review card paints. The Floris host draws the expanded card
above the IME window's pre-expansion top while its touch targets stay
in the row's layout slot, so a tap on the painted "Use this" is a
no-op and the layout-position tap is the one that applies (verified
both ways on the fixture; tracked in issue #131). When that bug is
fixed, these pins move to the painted positions and the fake
toolchain follows.
"""

from __future__ import annotations

import time

from android.scripts.m2_device.adb_harness import (
    AdbHarness,
    CANDIDATE_REPHRASING,
    REVIEW_SETTLE_SECONDS,
    SOURCE_TEXT,
    STALE_TEXT,
)
from android.scripts.m2_device.evidence import (
    FLORIS_CANONICAL_HIERARCHY_LABELS,
    FLORIS_CANONICAL_PNG_NAMES,
)
from android.scripts.m2_device.records import StepRecord

FLORIS_KEY_COORDS: dict[str, tuple[int, int]] = {
    "E": (273, 1760), "T": (486, 1760), "I": (808, 1760),
    "A": (109, 1904), "S": (218, 1904),
    "X": (330, 2054), "V": (541, 2054), "N": (752, 2054),
    " ": (592, 2200), ".": (853, 2200),
}
FLORIS_SHIFT_TAP = (88, 2054)
# Panel row taps (layout positions — see module docstring).
FLORIS_REWRITE_TAP = (824, 1605)
FLORIS_CANCEL_TAP = (824, 1605)
FLORIS_APPLY_TAP = (120, 1605)
FLORIS_DISMISS_TAP = (560, 1605)
FLORIS_SETTINGS_TAP = (997, 1605)
# InputMethod window touchable-region geometry: compact row tops at
# 1413; the review card expands the window past 1365 (measured 1148).
FLORIS_IME_COMPACT_TOP = 1413
FLORIS_IME_EXPANDED_MAX_TOP = 1365
# Rewrite -> Cancel rides one transport with an embedded sleep: the
# taps must land inside the fixture provider's 400 ms loading window
# but AFTER the row recomposes from Resting to Loading (back-to-back
# taps hit the still-Resting Rewrite twice; 150 ms clears both bounds
# on the fixture).
FLORIS_CANCEL_INTER_TAP_SLEEP = "0.15"

# The composing leg's draft deliberately omits the trailing period so
# the last word is un-finalized when Rewrite captures it (ADR-0003).
FLORIS_COMPOSING_SOURCE = "Tea at six"


def floris_candidate(draft: str) -> str:
    """The fixture FakeProvider's rephrasing contract: it echoes the
    captured draft inside the butler sentence. The composing leg's
    expected candidate differs from SOURCE's only in the echoed text
    (no trailing period)."""
    return (
        "I have taken the liberty, sir, of rephrasing your words: "
        f"\u201c{draft}\u201d \u2014 though I must confess the genuine article is still en route."
    )


FLORIS_COMPOSING_CANDIDATE = floris_candidate(FLORIS_COMPOSING_SOURCE)

# The ported settings activity is a normal app window, so uiautomator
# sees it — unlike every keyboard surface. These exact text facts are
# what the journey requires from the dump.
FLORIS_SETTINGS_TITLE_TEXT = "PersonaSpeak Settings"
FLORIS_SETTINGS_SECTION_TEXT = "THE BRAIN"


class FlorisAdbHarness(AdbHarness):
    KEYBOARD_PACKAGE = "biz.pixelperfectstudios.personaspeak.floris.debug"
    IME_COMPONENT = (
        "biz.pixelperfectstudios.personaspeak.floris.debug"
        "/dev.patrickgold.florisboard.FlorisImeService")
    # Vendored FlorisBoard v0.5.2 debug build (applicationId suffix
    # .debug; BUILD_COMMIT_HASH placeholder because the vendored tree
    # has no .git). Signer is the same local debug certificate as the
    # ASK debug builds, so the cert gate pins the identical digest.
    EXPECTED_VERSION_NAME = "0.5.2-debug+null"
    EXPECTED_VERSION_CODE = "117"
    KEY_COORDS = FLORIS_KEY_COORDS
    SHIFT_TAP = FLORIS_SHIFT_TAP
    IME_COMPACT_TOP = FLORIS_IME_COMPACT_TOP
    IME_EXPANDED_MAX_TOP = FLORIS_IME_EXPANDED_MAX_TOP
    SCREENSHOT_NAMES = list(FLORIS_CANONICAL_PNG_NAMES)
    HIERARCHY_LABELS = FLORIS_CANONICAL_HIERARCHY_LABELS

    def _rewrite_cancel_shell(self) -> list[str]:
        return [
            "input", "tap", *map(str, FLORIS_REWRITE_TAP),
            ";", "sleep", FLORIS_CANCEL_INTER_TAP_SLEEP,
            ";", "input", "tap", *map(str, FLORIS_CANCEL_TAP),
        ]

    def _tap(self, steps, op, coord) -> bool:
        res = self._shell("input", "tap", str(coord[0]), str(coord[1]))
        return self._step(steps, op, res)

    def _verify_floris_settings(self, steps, root) -> bool:
        nodes = [elem for elem in root.iter()
                 if elem.attrib.get("package", "") == self.KEYBOARD_PACKAGE]
        texts = {n.attrib.get("text", "") for n in nodes}
        errors = []
        if not nodes:
            errors.append("no floris-package nodes in dump")
        if FLORIS_SETTINGS_TITLE_TEXT not in texts:
            errors.append(f"title {FLORIS_SETTINGS_TITLE_TEXT!r} absent")
        if FLORIS_SETTINGS_SECTION_TEXT not in texts:
            errors.append(f"section {FLORIS_SETTINGS_SECTION_TEXT!r} absent")
        if errors:
            self._step(steps, "verify_floris_settings",
                       self._fail("settings", "; ".join(errors).encode()))
            return False
        self._step(steps, "verify_floris_settings", self._ok("settings"))
        return True

    def run_journey(self) -> list[StepRecord]:
        steps: list[StepRecord] = []

        self.screenrecord_process = self._shell_start(
            "screenrecord", "--time-limit", "30", "/sdcard/journey.mp4")

        if not self._enable_ime(steps):
            return steps
        if not self._set_ime(steps):
            return steps

        # Session 1 — Idle, Loading/cancel: type through real Floris
        # keys, trigger a rewrite, cancel it while loading. Zero
        # mutations; the row's controls answer at their layout slots.
        if not self._open_search_session(steps, 1):
            return steps
        if not self._type_text(steps, SOURCE_TEXT):
            return steps
        if not self._verify_editor_by_dump(steps, "typed_1", SOURCE_TEXT):
            return steps
        if not self._take_screenshot(steps, "01-idle-typed"):
            return steps
        res = self._shell(*self._rewrite_cancel_shell())
        if not self._step(steps, "rewrite_and_cancel", res):
            return steps
        if not self._verify_editor_by_dump(
                steps, "after_cancel", SOURCE_TEXT):
            return steps
        if not self._verify_window_state(steps, "after_cancel", expanded=False):
            return steps
        if not self._take_screenshot(steps, "02-loading-cancel"):
            return steps
        if not self._exit_session(steps, 1):
            return steps

        # Session 2 — Review, Applied: rewrite, wait out the provider
        # latency, apply, and prove the exactly-one mutation.
        if not self._open_search_session(steps, 2):
            return steps
        if not self._type_text(steps, SOURCE_TEXT):
            return steps
        if not self._verify_editor_by_dump(steps, "typed_2", SOURCE_TEXT):
            return steps
        if not self._tap(steps, "request_rewrite_2", FLORIS_REWRITE_TAP):
            return steps
        time.sleep(REVIEW_SETTLE_SECONDS)
        if not self._verify_window_state(steps, "review_2", expanded=True):
            return steps
        if not self._take_screenshot(steps, "03-review"):
            return steps
        if not self._tap(steps, "apply_rephrasing", FLORIS_APPLY_TAP):
            return steps
        if not self._verify_editor_by_dump(
                steps, "after_apply", CANDIDATE_REPHRASING):
            return steps
        if not self._take_screenshot(steps, "04-applied"):
            return steps
        if not self._exit_session(steps, 2):
            return steps

        # Session 3 — Dismiss: zero mutations, panel back to idle.
        if not self._open_search_session(steps, 3):
            return steps
        if not self._type_text(steps, SOURCE_TEXT):
            return steps
        if not self._verify_editor_by_dump(steps, "typed_3", SOURCE_TEXT):
            return steps
        if not self._tap(steps, "request_rewrite_3", FLORIS_REWRITE_TAP):
            return steps
        time.sleep(REVIEW_SETTLE_SECONDS)
        if not self._verify_window_state(steps, "review_3", expanded=True):
            return steps
        if not self._tap(steps, "dismiss_rephrasing", FLORIS_DISMISS_TAP):
            return steps
        if not self._verify_editor_by_dump(
                steps, "after_dismiss", SOURCE_TEXT):
            return steps
        if not self._verify_window_state(steps, "after_dismiss", expanded=False):
            return steps
        if not self._take_screenshot(steps, "05-dismissed"):
            return steps
        if not self._exit_session(steps, 3):
            return steps

        # Session 4 — Stale: change the source under a pending
        # candidate; the apply must make zero mutations.
        if not self._open_search_session(steps, 4):
            return steps
        if not self._type_text(steps, SOURCE_TEXT):
            return steps
        if not self._verify_editor_by_dump(steps, "typed_4", SOURCE_TEXT):
            return steps
        if not self._tap(steps, "request_rewrite_4", FLORIS_REWRITE_TAP):
            return steps
        time.sleep(REVIEW_SETTLE_SECONDS)
        if not self._verify_window_state(steps, "review_4", expanded=True):
            return steps
        if not self._clear_text(steps, SOURCE_TEXT):
            return steps
        if not self._type_text(steps, STALE_TEXT, "type_stale"):
            return steps
        if not self._verify_editor_by_dump(steps, "typed_stale", STALE_TEXT):
            return steps
        if not self._tap(steps, "apply_stale", FLORIS_APPLY_TAP):
            return steps
        if not self._verify_editor_by_dump(
                steps, "after_stale", STALE_TEXT):
            return steps
        if not self._take_screenshot(steps, "06-stale"):
            return steps
        if not self._exit_session(steps, 4):
            return steps

        # Session 5 — Composing (ADR-0003): the draft omits the final
        # period so the last word is un-finalized when Rewrite captures
        # it; apply must still replace the entire draft. Span liveness
        # itself is proven deterministically by the
        # :personaspeak-ime real-InputConnection instrumentation test;
        # this leg is the product-path proof through real Floris keys.
        if not self._open_search_session(steps, 5):
            return steps
        if not self._type_text(steps, FLORIS_COMPOSING_SOURCE, "type_composing"):
            return steps
        if not self._verify_editor_by_dump(
                steps, "typed_5", FLORIS_COMPOSING_SOURCE):
            return steps
        if not self._take_screenshot(steps, "07-composing-typed"):
            return steps
        if not self._tap(steps, "request_rewrite_5", FLORIS_REWRITE_TAP):
            return steps
        time.sleep(REVIEW_SETTLE_SECONDS)
        if not self._verify_window_state(steps, "review_5", expanded=True):
            return steps
        if not self._tap(steps, "apply_composing", FLORIS_APPLY_TAP):
            return steps
        if not self._verify_editor_by_dump(
                steps, "after_composing", FLORIS_COMPOSING_CANDIDATE):
            return steps
        if not self._take_screenshot(steps, "08-composing-applied"):
            return steps
        if not self._exit_session(steps, 5):
            return steps

        # Session 6 — Settings surface: the row's settings button
        # launches the ported activity; uiautomator sees it (a normal
        # app window) and the journey pins its identity facts.
        if not self._open_search_session(steps, 6):
            return steps
        if not self._tap(steps, "tap_settings_button", FLORIS_SETTINGS_TAP):
            return steps
        res, root = self._dump_hierarchy("floris_settings")
        if root is None:
            self._step(steps, "dump_floris_settings", res)
            return steps
        self._step(steps, "dump_floris_settings", res)
        if not self._verify_floris_settings(steps, root):
            return steps
        if not self._take_screenshot(steps, "09-floris-settings"):
            return steps
        res = self._shell("input", "keyevent", "4")
        if not self._step(steps, "close_settings", res):
            return steps
        if not self._exit_session(steps, 6):
            return steps

        return steps
