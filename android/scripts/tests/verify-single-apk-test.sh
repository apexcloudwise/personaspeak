#!/usr/bin/env bash
# Contract test for verify-single-apk.sh.
#
# The cutover's whole claim is "exactly one APK, at exactly one path". That
# claim is only worth as much as the verifier that enforces it, so this suite
# drives the verifier against synthetic trees covering all eight cases the M2
# plan requires — including the two positives, which are what stop a verifier
# that fails everything from looking rigorous.
#
# Cases:
#   1. zero APKs                                          -> reject (1)
#   2. two APKs                                           -> reject (1), print both
#   3. an android/outputs/ convenience duplicate          -> reject (1)
#   4. one noncanonical APK                               -> reject (1)
#   5. exactly the canonical APK                          -> accept (0)
#   6a. zero com.android.application projects             -> reject (1)
#   6b. two com.android.application projects              -> reject (1)
#   7. only keyboard/ime/app/build.gradle applies it      -> accept (0)
#   8. usage / tool failure                               -> 2, never mistaken
#                                                            for a clean scan
#
# Exit 0 when every case behaves; 1 on any contract violation.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/../verify-single-apk.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

cases_run=0
canonical_rel="keyboard/ime/app/build/outputs/apk/debug/app-debug.apk"

# Build a fixture android root. By default it has the correct single-
# application topology and no APKs at all; callers add what each case needs.
new_fixture() {
  local name="$1"
  local root="$tmp/$name"
  mkdir -p "$root/keyboard/ime/app"
  # The real tree reaches the application plugin through upstream's
  # apk_module.gradle indirection; the fixture mirrors that.
  printf 'apply from: "${rootDir}/gradle/apk_module.gradle"\n' \
    > "$root/keyboard/ime/app/build.gradle"
  # The real root build file makes the application plugin *available* with
  # `apply false`. That is a declaration, not an application, and every
  # fixture carries it so no case can pass by pretending it is absent.
  # ...and it also *talks about* apk_module.gradle in a comment, explaining
  # why it sets the unified-build flag. Prose is not topology; a verifier a
  # sentence can defeat is not a verifier. This bit the real tree once.
  cat > "$root/build.gradle.kts" <<'KTS'
plugins {
    alias(libs.plugins.android.application) apply false
}

// Upstream's apk_module.gradle reads this flag and skips registering its
// com.android.application convenience copy tasks under the unified build.
extra["personaSpeakUnifiedBuild"] = true
KTS
  mkdir -p "$root/core-personas" "$root/core-providers" "$root/personaspeak-ui"
  printf '// inert library fixture\n' > "$root/core-personas/build.gradle.kts"
  printf '// inert library fixture\n' > "$root/core-providers/build.gradle.kts"
  printf '// inert library fixture\n' > "$root/personaspeak-ui/build.gradle.kts"
  printf '%s' "$root"
}

add_apk() {
  local root="$1" rel="$2"
  mkdir -p "$root/$(dirname "$rel")"
  printf 'PK\003\004 synthetic fixture apk\n' > "$root/$rel"
}

# expect_status <expected> <label> <root...>  — runs the verifier, compares the
# exit status, and stores combined output in $last_out.
last_out=""
expect_status() {
  local expected="$1" label="$2"
  shift 2
  local rc=0
  last_out="$(bash "$verifier" "$@" 2>&1)" || rc=$?
  if [ "$rc" -ne "$expected" ]; then
    echo "FAIL: $label: expected exit $expected, got $rc" >&2
    printf '%s\n' "$last_out" >&2
    exit 1
  fi
  cases_run=$((cases_run + 1))
}

# --- 1. Zero APKs ----------------------------------------------------------
root="$(new_fixture zero-apks)"
expect_status 1 "case 1 (zero APKs)" "$root"
if ! printf '%s' "$last_out" | grep -q 'expected exactly 1 APK, found 0'; then
  echo "FAIL: case 1: missing zero-APK diagnosis" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

# --- 2. Two APKs, both paths printed --------------------------------------
root="$(new_fixture two-apks)"
add_apk "$root" "$canonical_rel"
add_apk "$root" "app/build/outputs/apk/debug/app-debug.apk"
expect_status 1 "case 2 (two APKs)" "$root"
if ! printf '%s' "$last_out" | grep -q "$canonical_rel"; then
  echo "FAIL: case 2: canonical path not printed" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi
if ! printf '%s' "$last_out" | grep -q 'app/build/outputs/apk/debug/app-debug.apk'; then
  echo "FAIL: case 2: second APK path not printed" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

# --- 3. android/outputs/ convenience duplicate ----------------------------
# This is the ASK copy task's destination. It is a *copy* of the canonical
# APK, which makes it the easiest one to wave through and the most important
# one to reject: two files means two things to ship.
root="$(new_fixture outputs-duplicate)"
add_apk "$root" "$canonical_rel"
add_apk "$root" "outputs/apk/ime_debug.apk"
expect_status 1 "case 3 (android/outputs duplicate)" "$root"
if ! printf '%s' "$last_out" | grep -q 'outputs/apk/ime_debug.apk'; then
  echo "FAIL: case 3: convenience duplicate not named" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

# --- 4. One APK, wrong path -----------------------------------------------
root="$(new_fixture noncanonical-apk)"
add_apk "$root" "app/build/outputs/apk/debug/app-debug.apk"
expect_status 1 "case 4 (noncanonical APK)" "$root"
if ! printf '%s' "$last_out" | grep -q 'not the canonical APK path'; then
  echo "FAIL: case 4: missing canonical-path diagnosis" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

# --- 5. POSITIVE: exactly the canonical APK -------------------------------
root="$(new_fixture canonical-only)"
add_apk "$root" "$canonical_rel"
expect_status 0 "case 5 (canonical APK accepted)" "$root"
if ! printf '%s' "$last_out" | grep -q 'single APK verified'; then
  echo "FAIL: case 5: missing success line" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

# --- 6a. Zero application projects ----------------------------------------
root="$(new_fixture zero-applications)"
add_apk "$root" "$canonical_rel"
printf '// no application plugin here\n' > "$root/keyboard/ime/app/build.gradle"
expect_status 1 "case 6a (zero application projects)" "$root"
if ! printf '%s' "$last_out" | grep -q 'expected exactly 1 application project, found 0'; then
  echo "FAIL: case 6a: missing zero-application diagnosis" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

# --- 6b. Two application projects -----------------------------------------
root="$(new_fixture two-applications)"
add_apk "$root" "$canonical_rel"
mkdir -p "$root/app"
printf 'plugins { id("com.android.application") }\n' > "$root/app/build.gradle.kts"
expect_status 1 "case 6b (two application projects)" "$root"
if ! printf '%s' "$last_out" | grep -q 'expected exactly 1 application project, found 2'; then
  echo "FAIL: case 6b: missing two-application diagnosis" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi
if ! printf '%s' "$last_out" | grep -q 'app/build.gradle.kts'; then
  echo "FAIL: case 6b: extra application build file not named" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

# --- 7. POSITIVE: only keyboard/ime/app/build.gradle applies the plugin ----
root="$(new_fixture sole-application)"
add_apk "$root" "$canonical_rel"
expect_status 0 "case 7 (sole application project accepted)" "$root"
if ! printf '%s' "$last_out" | grep -q 'keyboard/ime/app/build.gradle'; then
  echo "FAIL: case 7: sole application build file not reported" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

# --- 8. Usage and tool failure return 2 -----------------------------------
# A verifier that returns 1 (or worse, 0) when it could not scan lets a broken
# tool read as a clean tree. Status 2 is the only honest answer, and it must
# be distinguishable from a genuine violation.
expect_status 2 "case 8a (no arguments)"
expect_status 2 "case 8b (too many arguments)" "$tmp" "$tmp" "$tmp"
expect_status 2 "case 8c (nonexistent root)" "$tmp/definitely-not-here"

root="$(new_fixture unreadable-root)"
add_apk "$root" "$canonical_rel"
if [ "$(id -u)" -eq 0 ]; then
  echo "note: case 8d (unreadable tree) skipped — running as root" >&2
  cases_run=$((cases_run + 1))
else
  chmod 000 "$root/keyboard/ime/app/build"
  expect_status 2 "case 8d (unreadable subtree)" "$root"
  chmod 755 "$root/keyboard/ime/app/build"
fi

# --- 9. Floris debug APK tolerated (ADR-0010 P5 scoping) --------------------
root="$(new_fixture floris-debug-apk)"
add_apk "$root" "$canonical_rel"
add_apk "$root" "florisboard/app/build/outputs/apk/debug/app-debug.apk"
expect_status 0 "case 9 (floris debug APK tolerated)" "$root"
if ! printf '%s' "$last_out" | grep -q 'second-root APKs tolerated: 1'; then
  echo "FAIL: case 9: second-root tolerance not reported" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

# --- 10. Both floris build outputs tolerated ---------------------------------
root="$(new_fixture floris-both-apks)"
add_apk "$root" "$canonical_rel"
add_apk "$root" "florisboard/app/build/outputs/apk/debug/app-debug.apk"
add_apk "$root" "florisboard/app/build/outputs/apk/release/app-release.apk"
expect_status 0 "case 10 (floris release APK tolerated)" "$root"

# --- 11. Floris APK cannot stand in for the canonical one --------------------
root="$(new_fixture floris-only)"
add_apk "$root" "florisboard/app/build/outputs/apk/debug/app-debug.apk"
expect_status 1 "case 11 (floris APK without canonical)" "$root"
if ! printf '%s' "$last_out" | grep -q 'expected exactly 1 APK, found 0'; then
  echo "FAIL: case 11: missing zero-canonical diagnosis" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

# --- 12. Lookalike floris path outside the root is still a finding ------------
# florisboard-fake/ shares the name but not the root; the anchored prefix
# must not let it ride the second root's tolerance.
root="$(new_fixture floris-lookalike)"
add_apk "$root" "$canonical_rel"
add_apk "$root" "florisboard-fake/app/build/outputs/apk/debug/app-debug.apk"
expect_status 1 "case 12 (floris-named APK outside the root)" "$root"
if ! printf '%s' "$last_out" | grep -q 'florisboard-fake'; then
  echo "FAIL: case 12: lookalike APK not named" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

# --- 13. Second root's sanctioned application project tolerated ---------------
root="$(new_fixture floris-app-project)"
add_apk "$root" "$canonical_rel"
mkdir -p "$root/florisboard/app"
printf 'plugins {\n    alias(libs.plugins.android.application)\n}\n' \
  > "$root/florisboard/app/build.gradle.kts"
expect_status 0 "case 13 (floris app project tolerated)" "$root"

# --- 14. A second application project under the second root is a finding ------
root="$(new_fixture floris-rogue-app)"
add_apk "$root" "$canonical_rel"
mkdir -p "$root/florisboard/evil"
printf 'plugins { id("com.android.application") }\n' \
  > "$root/florisboard/evil/build.gradle.kts"
expect_status 1 "case 14 (rogue app project under second root)" "$root"
if ! printf '%s' "$last_out" | grep -q 'unexpected application project under the second root'; then
  echo "FAIL: case 14: missing rogue-project diagnosis" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

# --- 15. Library androidTest APK tolerated (test instrument, not an app) -----
# The :personaspeak-ime ADR-0003 instrumentation suite produces one of
# these on any local connectedAndroidTest run; it is a test runner, not
# a shippable app, and must not dirty the unified root's single-APK law.
root="$(new_fixture instrument-apk)"
add_apk "$root" "$canonical_rel"
add_apk "$root" "personaspeak-ime/build/outputs/apk/androidTest/debug/personaspeak-ime-debug-androidTest.apk"
expect_status 0 "case 15 (androidTest APK tolerated)" "$root"
if ! printf '%s' "$last_out" | grep -q 'instrumentation APKs tolerated: 1'; then
  echo "FAIL: case 15: instrumentation tolerance not reported" >&2
  printf '%s\n' "$last_out" >&2
  exit 1
fi

echo "PASS: verify-single-apk contract, $cases_run cases"
