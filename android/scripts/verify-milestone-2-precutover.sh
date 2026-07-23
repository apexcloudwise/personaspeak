#!/usr/bin/env bash
# Aggregate pre-cutover gate for Milestone 2.
#
# usage: verify-milestone-2-precutover.sh [<android-root>]
#
# Without <android-root>, defaults to the parent of this script's directory.
#
# Runs every Milestone 2 gate that does not require device access:
#   1. Clean tracked state (git working tree)
#   2. JDK 21
#   3. Exact ASK closure            (verify-ask-closure.sh; tolerates the
#                                    two rollback modules :app and
#                                    :keyboard-stub still present pre-cutover)
#   4. Dictionary licenses          (verify-dictionary-licenses.sh)
#   5. Upstream ledger              (verify-upstream-ledger.sh)
#   6. Core purity                  (no android/androidx imports in core
#                                    modules; failure-aware rg)
#   7. All first-party + IME unit tests
#   8. lintDebug                    (:ime:app)
#   9. :ime:app:assembleDebug
#
# Deliberately excludes exact-one-APK enumeration — the rollback modules
# (:app and :keyboard-stub) are still present at this stage.
#
# Exit codes: 0 pass; 1 gate violation; 2 usage or tool failure.
# Success line: PASS: milestone 2 pre-cutover gate
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ $# -ge 1 ]; then
    root="$1"
else
    root="$(cd "$script_dir/.." && pwd)"
fi

if [ ! -d "$root" ]; then
    echo "verify-milestone-2-precutover: not a directory: $root" >&2
    exit 2
fi

repo_root="$(git -C "$root" rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "$repo_root" ]; then
    echo "verify-milestone-2-precutover: no git repository at or above $root" >&2
    exit 2
fi

# Run a sub-verifier, capture its combined stdout+stderr, and print the
# output only on failure. $1 = verifier path, $2 = android-root.
run_verifier_silent() {
    local verifier="$1"
    local raw rc
    raw=""
    rc=0
    raw="$(bash "$verifier" "$root" 2>&1)" || rc=$?
    if [ "$rc" -ne 0 ]; then
        echo "$raw"
        return "$rc"
    fi
}

echo "=== Milestone 2 Pre-Cutover Gate ==="
echo "android-root: $root"
echo "repo-root:    $repo_root"
echo ""

# --- 1. Clean tracked state -------------------------------------------------
# "Tracked" = staged or unstaged modifications to files git already tracks.
# Untracked build artifacts (build/, .gradle/, outputs/) are tolerated.
echo "[1/9] clean tracked state..."
tracked_changes="$(git -C "$repo_root" status --porcelain | grep -v '^??' || true)"
if [ -n "$tracked_changes" ]; then
    echo "FAIL: tracked files modified:"
    printf '%s\n' "$tracked_changes"
    exit 1
fi
echo "  OK"

# --- 2. JDK 21 --------------------------------------------------------------
echo "[2/9] JDK 21..."
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    java_bin="$JAVA_HOME/bin/java"
else
    java_bin="java"
fi
java_version_line="$("$java_bin" -version 2>&1 | head -1)"
if ! printf '%s' "$java_version_line" | grep -Eq 'version "21\.'; then
    echo "FAIL: JDK 21 required; $java_bin reports: $java_version_line"
    exit 1
fi
echo "  OK ($java_version_line)"

# --- 3. Exact ASK closure (rollback modules tolerated) ---------------------
# verify-ask-closure.sh is designed for the post-cutover graph. In the
# pre-cutover phase :app and :keyboard-stub are intentional rollback modules.
# We call the verifier, then filter its output to tolerate ONLY those two
# known extras. Any other violation (missing project, addon APK, unexpected
# application plugin outside :app) is a real failure.
echo "[3/9] exact ASK closure (rollback modules tolerated)..."
closure_raw=""
closure_rc=0
closure_raw="$(bash "$script_dir/verify-ask-closure.sh" "$root" 2>&1)" || closure_rc=$?
case "$closure_rc" in
    0) ;;
    1)
        # Extract violation lines, dropping the two known rollback-module
        # entries. If anything remains, it is a genuine closure problem.
        bad="$(printf '%s\n' "$closure_raw" \
            | grep -E '^(unexpected|missing|forbidden)' \
            | grep -v -Fx 'unexpected ASK project :app' \
            | grep -v -Fx 'unexpected ASK project :keyboard-stub' \
            | grep -v -Fx 'unexpected application plugin in app/build.gradle.kts' \
            || true)"
        if [ -n "$bad" ]; then
            echo "FAIL: ASK closure violations beyond known rollback modules:"
            printf '%s\n' "$bad"
            exit 1
        fi
        ;;
    *)
        echo "FAIL: verify-ask-closure tool failure (exit $closure_rc):" >&2
        printf '%s\n' "$closure_raw" >&2
        exit 2
        ;;
esac
echo "  OK"

# --- 4. Dictionary licenses -------------------------------------------------
echo "[4/9] dictionary licenses..."
run_verifier_silent "$script_dir/verify-dictionary-licenses.sh" "$root"
echo "  OK"

# --- 5. Upstream ledger -----------------------------------------------------
echo "[5/9] upstream ledger..."
run_verifier_silent "$script_dir/verify-upstream-ledger.sh" "$root"
echo "  OK"

# --- 6. Core purity (failure-aware rg) --------------------------------------
# Exit 1 (no match) = pass; exit 0 (match) = violation; exit 2+ = tool failure.
echo "[6/9] core purity (no android/androidx imports in core modules)..."
purity_rc=0
purity_hits="$(rg -n --no-heading '^\s*import\s+(android|androidx)\.' \
    "$root/core-personas/src" "$root/core-providers/src" 2>&1)" || purity_rc=$?
case "$purity_rc" in
    0)
        echo "FAIL: Android import found in core module:"
        printf '%s\n' "$purity_hits"
        exit 1
        ;;
    1) ;;
    *)
        echo "FAIL: rg tool failure (exit $purity_rc): $purity_hits" >&2
        exit 2
        ;;
esac
echo "  OK"

# --- 7. All first-party + IME unit tests ------------------------------------
echo "[7/9] unit tests (core-personas, core-providers, personaspeak-ui, ime:app)..."
"$root/gradlew" -p "$root" \
    :core-personas:test :core-providers:test \
    :personaspeak-ui:testDebugUnitTest :ime:app:testDebugUnitTest \
    --console=plain --no-daemon --rerun-tasks
echo "  OK"

# --- 8. lintDebug (where configured) ----------------------------------------
echo "[8/9] lintDebug (:ime:app)..."
"$root/gradlew" -p "$root" :ime:app:lintDebug --console=plain --no-daemon --rerun-tasks
echo "  OK"

# --- 9. assembleDebug -------------------------------------------------------
echo "[9/9] :ime:app:assembleDebug..."
"$root/gradlew" -p "$root" :ime:app:assembleDebug --console=plain --no-daemon --rerun-tasks
echo "  OK"

echo ""
echo "PASS: milestone 2 pre-cutover gate"
