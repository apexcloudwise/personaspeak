#!/usr/bin/env python3
"""Text-entry metrics for the typing bake-off.

Implements the Soukoreff & MacKenzie (2003) framework:

    Soukoreff, R. W., & MacKenzie, I. S. (2003). Metrics for text entry
    research: A comparison of WPM and corrected and uncorrected error rate.
    Proceedings of CHI '03, 81-88. DOI: 10.1145/642611.642626

All functions here are pure: presented text, transcribed text, and a few
counts in; numbers out. Nothing touches a device, the filesystem, or the
network. The unit tests in test_metrics.py pin the maths against
hand-computed examples so the calculator can be trusted before any live run
exists.

Notation (from the paper):
    P   presented text (the phrase the user was asked to type)
    T   transcribed text (what actually ended up committed)
    C   keystrokes in the input stream that produced correct characters
    INF incorrect-and-not-fixed: errors that survive into T
    IF  incorrect-and-fixed: errors the user made and then removed
    F   fix keystrokes (backspaces, undos) -- the cleanup tax

With IF approximated by F (each backspace removed one wrong character), and
INF approximated by the Levenshtein distance between P and T, we get:

    C       = |T| - INF
    denom   = C + INF + IF = |T| + IF
    UER     = INF / denom       (uncorrected error rate)
    CER     = IF  / denom       (corrected error rate)
    TER     = (INF + IF) / denom (total error rate; equals UER + CER)
    KSPC    = |input stream| / |T|
    WPM     = (|T| / 5) / (seconds / 60)

These are approximations -- the paper goes deeper (per-keystroke INF, CON,
etc.) -- but they are the standard approximations the text-entry literature
reports, and they are what the bake-off's error-rate tiebreak hangs on.
"""

from __future__ import annotations

from dataclasses import dataclass


def levenshtein(a: str, b: str) -> int:
    """Minimum number of single-char insert/delete/substitute ops to turn `a`
    into `b`. This is the MSD (minimum string distance) of Soukoreff &
    MacKenzie (2003)."""
    if a == b:
        return 0
    if not a:
        return len(b)
    if not b:
        return len(a)
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, start=1):
        curr = [i] + [0] * len(b)
        for j, cb in enumerate(b, start=1):
            cost = 0 if ca == cb else 1
            curr[j] = min(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        prev = curr
    return prev[-1]


def wpm(transcribed_chars: int, elapsed_seconds: float) -> float:
    """Words per minute. The 5 is the conventional average word length in
    characters, including the trailing space. Returns 0.0 for non-positive
    elapsed time so a glitchy timestamp doesn't divide by zero."""
    if elapsed_seconds <= 0:
        return 0.0
    minutes = elapsed_seconds / 60.0
    return (transcribed_chars / 5.0) / minutes


def kspc(input_stream_length: int, transcribed_chars: int) -> float:
    """Keystrokes per character. 1.0 means a clean pass with no fixups; 1.2
    means roughly one backspace for every five correct characters. Returns
    0.0 when there is no transcribed text to divide by."""
    if transcribed_chars <= 0:
        return 0.0
    return input_stream_length / transcribed_chars


@dataclass(frozen=True)
class ErrorRates:
    """The three Soukoreff-MacKenzie error rates plus the raw MSD distance.
    All rates are floats in [0.0, ~1.0]; the total can exceed 1.0 only in
    degenerate hand-computed cases that don't occur on real typing."""

    uncorrected: float
    corrected: float
    total: float
    msd_distance: int

    @property
    def accuracy(self) -> float:
        """1.0 - total error rate, clamped at 0. Convenience for result
        tables that prefer 'percent correct' over 'percent wrong'."""
        return max(0.0, 1.0 - self.total)


def error_rates(
    presented: str,
    transcribed: str,
    fixes: int = 0,
) -> ErrorRates:
    """Compute Soukoreff-MacKenzie error rates for one typed phrase.

    `fixes` is the count of fix keystrokes in the input stream (backspaces,
    undos, and the like). Each is treated as removing one erroneous
    character, which is the standard simplification when per-keystroke
    classifications are not available. Set `fixes=0` (the default) for a
    final-text-only pass; that yields CER=0 and a UER driven purely by the
    MSD between presented and transcribed.
    """
    inf = levenshtein(presented, transcribed)
    ifix = max(0, fixes)
    t_len = len(transcribed)
    denom = t_len + ifix
    if denom == 0:
        return ErrorRates(0.0, 0.0, 0.0, inf)
    uer = inf / denom
    cer = ifix / denom
    return ErrorRates(uncorrected=uer, corrected=cer, total=uer + cer, msd_distance=inf)


def aggregate(records):
    """Reduce a list of per-phrase metric dicts into corpus-level means.

    Each record is a dict shaped like the per-phrase output of
    compute_metrics.run. WPM and the error rates are averaged arithmetically
    across phrases; KSPC is aggregated as total keystrokes / total
    transcribed characters, which is the corpus-level KSPC the paper reports
    (simple averaging of per-phrase KSPC systematically over-weights short
    phrases)."""
    if not records:
        return {
            "wpm": 0.0,
            "uncorrected_error_rate": 0.0,
            "corrected_error_rate": 0.0,
            "total_error_rate": 0.0,
            "kspc": 0.0,
            "phrases": 0,
        }
    n = len(records)
    keys_per = sum(r.get("input_stream_length", 0) for r in records)
    chars_per = sum(r.get("transcribed_chars", 0) for r in records)
    return {
        "wpm": sum(r.get("wpm", 0.0) for r in records) / n,
        "uncorrected_error_rate": sum(r.get("uncorrected_error_rate", 0.0) for r in records) / n,
        "corrected_error_rate": sum(r.get("corrected_error_rate", 0.0) for r in records) / n,
        "total_error_rate": sum(r.get("total_error_rate", 0.0) for r in records) / n,
        "kspc": (keys_per / chars_per) if chars_per else 0.0,
        "phrases": n,
    }
