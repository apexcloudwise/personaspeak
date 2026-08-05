#!/usr/bin/env bash
# Verify that the tree produces exactly one APK, at exactly one path, from
# exactly one Android application project.
#
# usage: verify-single-apk.sh <android-root>
#
# Exit 0 only when all three hold:
#   1. exactly one APK exists under an outputs/ directory anywhere in the tree;
#   2. its path is exactly
#      keyboard/ime/app/build/outputs/apk/debug/app-debug.apk;
#   3. exactly one project build file applies an Android application plugin,
#      and it is keyboard/ime/app/build.gradle.
#
# Scope note: enumeration covers `*.apk` under any `outputs/` path segment.
# That is deliberately wider than the canonical directory — it is what catches
# upstream's android/outputs/ convenience copies and a resurrected
# android/app/ — and deliberately narrower than "every .apk in the tree", so
# AGP's own build/intermediates/ scratch copies do not masquerade as shippable
# artifacts.
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

apk_count="$(wc -l < "$apks" | tr -d ' ')"
if [ "$apk_count" -ne 1 ]; then
  echo "expected exactly 1 APK, found $apk_count"
  while IFS= read -r p; do
    [ -z "$p" ] && continue
    echo "  apk: $p"
  done < "$apks"
  status=1
else
  found="$(cat "$apks")"
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

app_count="$(wc -l < "$app_files" | tr -d ' ')"
if [ "$app_count" -ne 1 ]; then
  echo "expected exactly 1 application project, found $app_count"
  while IFS= read -r p; do
    [ -z "$p" ] && continue
    echo "  application build file: $p"
  done < "$app_files"
  status=1
else
  sole="$(cat "$app_files")"
  if [ "$sole" != "keyboard/ime/app/build.gradle" ]; then
    echo "sole application project is not ASK :ime:app"
    echo "  found:    $sole"
    echo "  expected: keyboard/ime/app/build.gradle"
    status=1
  fi
fi

if [ "$status" -eq 0 ]; then
  echo "single APK verified: $canonical"
  echo "sole application project: $(cat "$app_files")"
fi
exit "$status"
