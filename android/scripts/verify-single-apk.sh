#!/usr/bin/env bash
# Verify that the tree produces exactly one APK per sanctioned Android
# root, at exactly one path each, from exactly one application project
# per root.
#
# usage: verify-single-apk.sh <android-root>
#
# Exit 0 only when all of these hold:
#   1. under the unified (ASK) root, exactly one APK exists and its path
#      is exactly keyboard/ime/app/build/outputs/apk/debug/app-debug.apk;
#   2. APKs under florisboard/ (the ADR-0010 evaluation second root, own
#      Gradle root) are that root's own business — any count, tolerated;
#      an APK that merely LOOKS floris-named but sits outside the second
#      root (florisboard-fake/, android/floris-copy/) is still a finding;
#   3. the unified root declares exactly one application project
#      (keyboard/ime/app/build.gradle) and the second root at most its
#      one sanctioned application project (florisboard/app/build.gradle.kts).
#
# Scope note: enumeration covers `*.apk` under any `outputs/` path segment.
# That is deliberately wider than the canonical directory — it is what catches
# upstream's android/outputs/ convenience copies and a resurrected
# android/app/ — and deliberately narrower than "every .apk in the tree", so
# AGP's own build/intermediates/ scratch copies do not masquerade as shippable
# artifacts. The two-root scoping is the ADR-0010 P5 decision, implemented.
#
# This verifier is read-only. A stale APK is a finding, not something to
# clean up: silently deleting the evidence would turn a dirty tree into a
# passing one, which is the failure this gate exists to prevent.
#
# Exit codes: 0 pass; 1 violation; 2 usage or tool failure. A scan that could
# not complete returns 2 and is never reported as a clean tree.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: verify-single-apk.sh <android-root>" >&2
  exit 2
fi
root="$1"
if [ ! -d "$root" ]; then
  echo "verify-single-apk: not a directory: $root" >&2
  exit 2
fi
root="$(cd "$root" && pwd)"

canonical="keyboard/ime/app/build/outputs/apk/debug/app-debug.apk"
canonical_app="keyboard/ime/app/build.gradle"
second_root_prefix="florisboard/"
second_root_app="florisboard/app/build.gradle.kts"
app_plugin_pattern='com\.android\.application|libs\.plugins\.android\.application|apk_module\.gradle'

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

status=0

# --- 1/2. Artifact enumeration --------------------------------------------
# `find` exits non-zero when it cannot traverse something. Treat that as a
# tool failure rather than an empty result set: "I could not look" and "there
# is nothing there" are different answers.
apks="$workdir/apks.txt"
find_rc=0
find "$root" -type f -name '*.apk' -path '*/outputs/*' \
  > "$workdir/apks.raw" 2> "$workdir/find.err" || find_rc=$?
if [ "$find_rc" -ne 0 ] || [ -s "$workdir/find.err" ]; then
  echo "verify-single-apk: APK scan failed (find exit $find_rc)" >&2
  cat "$workdir/find.err" >&2
  exit 2
fi
sed "s|^$root/||" "$workdir/apks.raw" | LC_ALL=C sort > "$apks"

# Artifact classes outside the unified root's single-APK law, each named:
#   - florisboard/** : the evaluation second root's own outputs (ADR-0010);
#   - */build/outputs/apk/androidTest/** : instrumentation-test APKs of
#     first-party library modules — test runners, never shippable apps
#     (the :personaspeak-ime ADR-0003 suite produces one on any local
#     connectedAndroidTest run). The anchored prefix keeps lookalike
#     directories from riding either class.
unified_apks="$workdir/unified-apks.txt"
floris_apks="$workdir/floris-apks.txt"
instrument_apks="$workdir/instrument-apks.txt"
: > "$unified_apks"; : > "$floris_apks"; : > "$instrument_apks"
while IFS= read -r p; do
  [ -z "$p" ] && continue
  case "$p" in
    "$second_root_prefix"*) printf '%s\n' "$p" >> "$floris_apks" ;;
    */outputs/apk/androidTest/*) printf '%s\n' "$p" >> "$instrument_apks" ;;
    *) printf '%s\n' "$p" >> "$unified_apks" ;;
  esac
done < "$apks"

floris_apk_count="$(wc -l < "$floris_apks" | tr -d ' ')"
instrument_apk_count="$(wc -l < "$instrument_apks" | tr -d ' ')"
unified_apk_count="$(wc -l < "$unified_apks" | tr -d ' ')"
if [ "$unified_apk_count" -ne 1 ]; then
  echo "expected exactly 1 APK, found $unified_apk_count"
  while IFS= read -r p; do
    [ -z "$p" ] && continue
    echo "  apk: $p"
  done < "$unified_apks"
  status=1
else
  found="$(cat "$unified_apks")"
  if [ "$found" != "$canonical" ]; then
    echo "not the canonical APK path"
    echo "  found:    $found"
    echo "  expected: $canonical"
    status=1
  fi
fi

# --- 3. Application-project topology --------------------------------------
# Scan committed project build files only. Anything under a build/ directory
# is generated output, not a declaration of topology.
build_files="$workdir/build-files.txt"
find_rc=0
find "$root" -type f \( -name 'build.gradle' -o -name 'build.gradle.kts' \) \
  -not -path '*/build/*' \
  > "$build_files" 2> "$workdir/find2.err" || find_rc=$?
if [ "$find_rc" -ne 0 ] || [ -s "$workdir/find2.err" ]; then
  echo "verify-single-apk: build-file scan failed (find exit $find_rc)" >&2
  cat "$workdir/find2.err" >&2
  exit 2
fi

app_files="$workdir/app-files.txt"
: > "$app_files"
while IFS= read -r build_file; do
  [ -z "$build_file" ] && continue
  # Read declarations, not prose. A comment that merely names
  # apk_module.gradle or the application plugin — e.g. the root build file
  # explaining why it sets the unified-build flag — is documentation, and a
  # topology verifier that a sentence can defeat is not a topology verifier.
  # Full-line //, #, and block-comment lines are dropped before matching.
  code="$(grep -vE '^[[:space:]]*(//|#|/\*|\*)' "$build_file" || true)"
  grep_rc=0
  hits="$(printf '%s\n' "$code" | grep -E "$app_plugin_pattern")" || grep_rc=$?
  case "$grep_rc" in
    0) ;;
    1) continue ;;
    *)
      echo "verify-single-apk: grep failed (exit $grep_rc) on $build_file" >&2
      exit 2
      ;;
  esac
  # The root build file declares the application plugin with `apply false`,
  # which makes it available to subprojects without making the root an
  # application. Counting that as an application project would leave this
  # gate permanently red for a correct tree.
  applied="$(printf '%s\n' "$hits" | grep -vE 'apply[[:space:]]+false' || true)"
  if [ -n "$applied" ]; then
    printf '%s\n' "${build_file#"$root/"}" >> "$app_files"
  fi
done < "$build_files"
LC_ALL=C sort -o "$app_files" "$app_files"

# Two-root topology: the unified root keeps its exactly-one law; the
# second root may carry its one sanctioned application project and
# nothing more.
unified_apps="$workdir/unified-apps.txt"
floris_apps="$workdir/floris-apps.txt"
: > "$unified_apps"; : > "$floris_apps"
while IFS= read -r p; do
  [ -z "$p" ] && continue
  case "$p" in
    "$second_root_prefix"*) printf '%s\n' "$p" >> "$floris_apps" ;;
    *) printf '%s\n' "$p" >> "$unified_apps" ;;
  esac
done < "$app_files"

unified_app_count="$(wc -l < "$unified_apps" | tr -d ' ')"
if [ "$unified_app_count" -ne 1 ]; then
  echo "expected exactly 1 application project, found $unified_app_count"
  while IFS= read -r p; do
    [ -z "$p" ] && continue
    echo "  application build file: $p"
  done < "$unified_apps"
  status=1
else
  sole="$(cat "$unified_apps")"
  if [ "$sole" != "$canonical_app" ]; then
    echo "sole application project is not ASK :ime:app"
    echo "  found:    $sole"
    echo "  expected: $canonical_app"
    status=1
  fi
fi

while IFS= read -r p; do
  [ -z "$p" ] && continue
  if [ "$p" != "$second_root_app" ]; then
    echo "unexpected application project under the second root"
    echo "  found:    $p"
    echo "  expected: $second_root_app"
    status=1
  fi
done < "$floris_apps"

if [ "$status" -eq 0 ]; then
  echo "single APK verified: $canonical"
  echo "sole application project: $(cat "$unified_apps")"
  echo "second-root APKs tolerated: $floris_apk_count (florisboard/ evaluation root)"
  echo "instrumentation APKs tolerated: $instrument_apk_count (androidTest, non-shippable)"
fi
exit "$status"
