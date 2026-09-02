#!/usr/bin/env bash
# Aggregate Milestone 2 acceptance gate (post-cutover).
#
# usage: verify-milestone-2.sh [<android-root>]
#
# Without <android-root>, defaults to the parent of this script's directory.
#
# One complete invocation of this script from tracked-clean HEAD is the M2
# acceptance receipt. Nothing here may be run selectively and described as
# that receipt.
#
# Stages, in order:
#    1. clean tracked state
#    2. JDK 21
#    3. verifier fixture suites — the tools are tested before they are trusted
#    4. exact ASK closure
#    5. dictionary licenses
#    6. upstream ledger
#    7. boundary scans: core purity, UI ASK-imports, rejected topology,
#       upstream-to-first-party seam allowlist
#    8. unit tests (core, UI, ASK :ime:app) including the dedicated-row suites
#    9. :ime:app:lintDebug
#   10. clean :ime:app:assembleDebug
#   11. exact-one-APK enumeration and topology
#   12. APK manifest identity assertions
#
# Every scan distinguishes "no match" (pass) from "the tool failed" (exit 2).
# A stage that could not run is never reported as a stage that passed. This
# script is read-only with respect to the tree: a stale artifact is a finding
# to report, never something to tidy away.
#
# Exit codes: 0 pass; 1 gate violation; 2 usage or tool failure.
# Success line: PASS: milestone 2 gate
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ $# -ge 2 ]; then
    echo "usage: verify-milestone-2.sh [<android-root>]" >&2
    exit 2
fi
if [ $# -eq 1 ]; then
    root="$1"
else
    root="$(cd "$script_dir/.." && pwd)"
fi
if [ ! -d "$root" ]; then
    echo "verify-milestone-2: not a directory: $root" >&2
    exit 2
fi
root="$(cd "$root" && pwd)"

repo_root="$(git -C "$root" rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "$repo_root" ]; then
    echo "verify-milestone-2: no git repository at or above $root" >&2
    exit 2
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

# Run a sub-verifier or fixture suite quietly; print its output only if it
# fails. A non-0/1 status is a tool failure and aborts with 2.
run_checked() {
    local label="$1" rc=0 raw
    shift
    raw="$("$@" 2>&1)" || rc=$?
    case "$rc" in
        0) return 0 ;;
        1)
            echo "FAIL: $label"
            printf '%s\n' "$raw"
            exit 1
            ;;
        *)
            echo "FAIL: $label tool failure (exit $rc)" >&2
            printf '%s\n' "$raw" >&2
            exit 2
            ;;
    esac
}

# scan <label> <pattern> <path...> — a forbidden-pattern scan.
# Exit 1 from rg (no match) is the pass. Exit 0 (match) is a violation.
# Anything else is a tool failure and returns 2, never a pass.
scan() {
    local label="$1" pattern="$2"
    shift 2
    local rc=0 hits
    hits="$(rg -n --no-heading -e "$pattern" "$@" 2>&1)" || rc=$?
    case "$rc" in
        0)
            echo "FAIL: $label"
            printf '%s\n' "$hits"
            exit 1
            ;;
        1) return 0 ;;
        *)
            echo "FAIL: $label — rg tool failure (exit $rc)" >&2
            printf '%s\n' "$hits" >&2
            exit 2
            ;;
    esac
}

echo "=== Milestone 2 Gate ==="
echo "android-root: $root"
echo "repo-root:    $repo_root"
echo ""

# --- 1. Clean tracked state -------------------------------------------------
echo "[1/12] clean tracked state..."
tracked_changes="$(git -C "$repo_root" status --porcelain | grep -v '^??' || true)"
if [ -n "$tracked_changes" ]; then
    echo "FAIL: tracked files modified:"
    printf '%s\n' "$tracked_changes"
    exit 1
fi
echo "  OK"

# --- 2. JDK 21 --------------------------------------------------------------
echo "[2/12] JDK 21..."
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

# --- 3. Verifier fixture suites --------------------------------------------
# Before any production verifier's verdict is believed, prove the verifier
# still enforces what it claims. A green gate built on an unchecked tool is
# the failure mode this ordering exists to prevent.
echo "[3/12] verifier fixture suites..."
for suite in verify-ask-closure-test verify-dictionary-licenses-test \
             verify-upstream-ledger-test verify-single-apk-test \
             verify-unified-build-flag-test verify-no-secret-logging-test; do
    echo "  - $suite"
    run_checked "$suite" bash "$script_dir/tests/$suite.sh"
done

echo "  OK"

# --- 4. Exact ASK closure ---------------------------------------------------
echo "[4/12] exact ASK closure..."
run_checked "ASK closure" bash "$script_dir/verify-ask-closure.sh" "$root"
echo "  OK"

# --- 5. Dictionary licenses -------------------------------------------------
echo "[5/12] dictionary licenses..."
run_checked "dictionary licenses" bash "$script_dir/verify-dictionary-licenses.sh" "$root"
echo "  OK"

# --- 6. Upstream ledger -----------------------------------------------------
echo "[6/12] upstream ledger..."
run_checked "upstream ledger" bash "$script_dir/verify-upstream-ledger.sh" "$root"
echo "  OK"

# --- 7. Boundary scans ------------------------------------------------------
echo "[7/12] boundary scans..."

echo "  - core purity (no android/androidx imports in core modules)"
scan "Android import in a core module" '^\s*import\s+(android|androidx)\.' \
    "$root/core-personas/src" "$root/core-providers/src"

echo "  - :personaspeak-ui carries no ASK imports"
scan "ASK import in :personaspeak-ui" 'com\.anysoftkeyboard|com\.menny' \
    "$root/personaspeak-ui/src"

echo "  - :personaspeak-ime carries no ASK imports"
scan "ASK import in :personaspeak-ime" 'com\.anysoftkeyboard|com\.menny' \
    "$root/personaspeak-ime/src"

echo "  - rejected topology absent outside the vendored snapshot"
scan "rejected topology present" \
    'switchBackToPreviousKeyboard|PersonaPanel|fun[[:space:]]+Onboarding[[:space:]]*\(' \
    "$root" --glob '!'"$root"'/keyboard/**' --glob '*.{kt,java}'

# The only upstream-owned file permitted to name first-party code is the
# leaf seam in SoftKeyboard.java. Everything else we own lives under
# biz/pixelperfectstudios/ inside the app module and is not upstream code.
# Every upstream line we touch is a merge conflict paid forever, so the
# allowlist is one file and stays one file.
echo "  - upstream-to-first-party seam allowlist"
seam_rc=0
seam_hits="$(rg -l --no-heading -e 'biz\.pixelperfectstudios' \
    "$root/keyboard" --glob '*.{java,kt}' \
    --glob '!**/biz/pixelperfectstudios/**' 2>&1)" || seam_rc=$?
case "$seam_rc" in
    0)
        unexpected="$(printf '%s\n' "$seam_hits" \
            | grep -v -F 'ime/app/src/main/java/com/menny/android/anysoftkeyboard/SoftKeyboard.java' \
            || true)"
        if [ -n "$unexpected" ]; then
            echo "FAIL: upstream files outside the approved seam reference first-party code:"
            printf '%s\n' "$unexpected"
            exit 1
        fi
        ;;
    1)
        echo "FAIL: the approved SoftKeyboard.java seam has disappeared"
        exit 1
        ;;
    *)
        echo "FAIL: seam scan — rg tool failure (exit $seam_rc)" >&2
        printf '%s\n' "$seam_hits" >&2
        exit 2
        ;;
esac
echo "  OK"

# --- 8. Unit tests ----------------------------------------------------------
echo "[8/12] unit tests (core, UI, ASK :ime:app, dedicated row)..."
"$root/gradlew" -p "$root" \
    :core-personas:test :core-providers:test \
    :personaspeak-ui:testDebugUnitTest :personaspeak-ime:testDebugUnitTest \
    :ime:app:testDebugUnitTest \
    --console=plain --no-daemon --rerun-tasks
echo "  OK"

# --- 9. lintDebug -----------------------------------------------------------
echo "[9/12] lintDebug (:ime:app)..."
"$root/gradlew" -p "$root" :ime:app:lintDebug \
    --console=plain --no-daemon --rerun-tasks
echo "  OK"

# --- 9b. Archive machine-readable results before the clean ------------------
# Stage 10 runs `clean`, which deletes every build directory — including the
# test-result and lint XML that stages 8 and 9 just produced. Acceptance
# requires those counts to be derived mechanically from XML, so the run would
# otherwise destroy its own evidence. Archive first, then clean.
#
# The archive lives OUTSIDE the tree. `clean` removes android/build wholesale,
# so anything stored under it would be deleted by the very step this exists to
# survive — and writing into the repo would dirty the tracked-clean state the
# gate just verified. Set MILESTONE_2_ARTIFACTS to pin a durable location for
# an acceptance run; otherwise a temp directory is used and its path printed.
# Note the default is a fresh temp directory, NOT $workdir — $workdir is
# removed on exit, and a gate that prints a path to evidence it then deletes
# is worse than one that never claimed to keep any.
archive="${MILESTONE_2_ARTIFACTS:-$(mktemp -d)}"
mkdir -p "$archive"
archive="$(cd "$archive" && pwd)"
# Refuse an archive path inside the repository. It would dirty the tracked
# state this gate just verified, and this script would then be deleting
# repository content to make room for its own output — which is precisely the
# behaviour the read-only rule forbids.
case "$archive/" in
    "$repo_root"/*)
        echo "verify-milestone-2: MILESTONE_2_ARTIFACTS must be outside the repository: $archive" >&2
        exit 2
        ;;
esac
# Clear only the subtree this script writes, never the caller's directory.
rm -rf "$archive/test-results"
for src in \
    "core-personas/build/test-results/test" \
    "core-providers/build/test-results/test" \
    "personaspeak-ui/build/test-results/testDebugUnitTest" \
    "personaspeak-ime/build/test-results/testDebugUnitTest" \
    "keyboard/ime/app/build/test-results/testDebugUnitTest"; do
    if [ -d "$root/$src" ]; then
        dest="$archive/test-results/$(printf '%s' "$src" | tr '/' '_')"
        mkdir -p "$dest"
        cp "$root/$src"/TEST-*.xml "$dest"/ 2>/dev/null || true
    fi
done
lint_xml="$root/keyboard/ime/app/build/reports/lint-results-debug.xml"
if [ -f "$lint_xml" ]; then
    cp "$lint_xml" "$archive/"
fi
archived_suites="$(find "$archive/test-results" -name 'TEST-*.xml' 2>/dev/null | wc -l | tr -d ' ')"
if [ "$archived_suites" -eq 0 ]; then
    echo "FAIL: no test-result XML archived; counts could not be derived"
    exit 1
fi
echo "  archived $archived_suites test-result XML files to $archive"

# --- 10. Clean assembleDebug ------------------------------------------------
echo "[10/12] clean :ime:app:assembleDebug..."
"$root/gradlew" -p "$root" clean :ime:app:assembleDebug \
    --console=plain --no-daemon --rerun-tasks
echo "  OK"

# The clean above removes build/ wholesale on some AGP versions; make sure the
# archive survived, because a receipt that quietly lost its evidence is worse
# than one that admits it.
if [ ! -d "$archive/test-results" ]; then
    echo "FAIL: result archive did not survive the clean; counts unavailable"
    exit 1
fi

# --- 11. Exact one APK ------------------------------------------------------
echo "[11/12] exact-one-APK enumeration and topology..."
run_checked "exact-one-APK" bash "$script_dir/verify-single-apk.sh" "$root"
echo "  OK"

# --- 12. APK manifest identity ---------------------------------------------
echo "[12/12] APK manifest identity..."
apk="$root/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$apk" ]; then
    echo "FAIL: canonical APK missing at ${apk#"$root/"}"
    exit 1
fi

apkanalyzer_bin="${APKANALYZER:-}"
if [ -z "$apkanalyzer_bin" ]; then
    for candidate in \
        "${ANDROID_HOME:-}/cmdline-tools/latest/bin/apkanalyzer" \
        "${ANDROID_SDK_ROOT:-}/cmdline-tools/latest/bin/apkanalyzer" \
        "$(command -v apkanalyzer 2>/dev/null || true)"; do
        if [ -n "$candidate" ] && [ -x "$candidate" ]; then
            apkanalyzer_bin="$candidate"
            break
        fi
    done
fi
if [ -z "$apkanalyzer_bin" ]; then
    echo "FAIL: apkanalyzer not found; set APKANALYZER or ANDROID_HOME" >&2
    echo "      (a manifest assertion that cannot run is not a manifest assertion that passed)" >&2
    exit 2
fi

manifest="$workdir/manifest.xml"
manifest_rc=0
"$apkanalyzer_bin" manifest print "$apk" > "$manifest" 2> "$workdir/manifest.err" \
    || manifest_rc=$?
if [ "$manifest_rc" -ne 0 ]; then
    echo "FAIL: apkanalyzer manifest print failed (exit $manifest_rc)" >&2
    cat "$workdir/manifest.err" >&2
    exit 2
fi

# require_in_manifest <label> <extended-regex>
require_in_manifest() {
    local label="$1" pattern="$2" rc=0
    grep -Eq "$pattern" "$manifest" || rc=$?
    case "$rc" in
        0) return 0 ;;
        1)
            echo "FAIL: $label"
            exit 1
            ;;
        *)
            echo "FAIL: $label — grep tool failure (exit $rc)" >&2
            exit 2
            ;;
    esac
}

require_in_manifest "APK package is not biz.pixelperfectstudios.personaspeak" \
    'package="biz\.pixelperfectstudios\.personaspeak"'
require_in_manifest "IME service com.menny.android.anysoftkeyboard.SoftKeyboard missing" \
    'com\.menny\.android\.anysoftkeyboard\.SoftKeyboard'
require_in_manifest "settings activity missing" \
    'LauncherSettingsActivity|MainSettingsActivity'
require_in_manifest "minSdkVersion is not 26" \
    'minSdkVersion[^>]*="?(0x0000001a|26)"?'
require_in_manifest "targetSdkVersion is not 35" \
    'targetSdkVersion[^>]*="?(0x00000023|35)"?'
echo "  OK"

echo ""
echo "PASS: milestone 2 gate"
