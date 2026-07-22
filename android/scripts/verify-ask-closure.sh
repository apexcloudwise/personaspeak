#!/usr/bin/env bash
# Verify the exact Milestone 2 ASK closure.
#
# usage: verify-ask-closure.sh <android-root>
#
# Exit 0 only when:
#   1. the Gradle graph contains exactly the projects listed in
#      expected-ask-projects.txt (28 approved ASK logical paths plus the
#      three first-party libraries) — every extra project is reported as
#      "unexpected ASK project <path>", every absent one as
#      "missing ASK project <path>";
#   2. the :ime:app debugRuntimeClasspath resolves no :addons:*:apk project;
#   3. among the graph's project build files, exactly
#      keyboard/ime/app/build.gradle applies an Android application plugin
#      (directly or via upstream's apk_module.gradle indirection).
#
# Test seam: when ASK_CLOSURE_PROJECTS_OUTPUT / ASK_CLOSURE_DEPS_OUTPUT name
# files, those pre-captured Gradle reports are used instead of invoking
# Gradle. Live runs invoke the wrapper inside <android-root>.
#
# Exit codes: 0 pass; 1 closure violation; 2 usage or tool failure. Every rg
# probe distinguishes exit 1 (no match) from exit 2+ (tool/read failure).
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: verify-ask-closure.sh <android-root>" >&2
  exit 2
fi
root="$1"
if [ ! -d "$root" ]; then
  echo "verify-ask-closure: not a directory: $root" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
expected_file="$script_dir/expected-ask-projects.txt"
if [ ! -f "$expected_file" ]; then
  echo "verify-ask-closure: missing $expected_file" >&2
  exit 2
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

projects_output="${ASK_CLOSURE_PROJECTS_OUTPUT:-}"
if [ -z "$projects_output" ]; then
  projects_output="$workdir/projects.txt"
  "$root/gradlew" -p "$root" projects --console=plain --no-daemon > "$projects_output"
fi
deps_output="${ASK_CLOSURE_DEPS_OUTPUT:-}"
if [ -z "$deps_output" ]; then
  deps_output="$workdir/deps.txt"
  "$root/gradlew" -p "$root" :ime:app:dependencies \
    --configuration debugRuntimeClasspath --console=plain --no-daemon \
    > "$deps_output"
fi

status=0

# --- 1. Exact project set -------------------------------------------------
# Normalize only lines naming a project path: Project ':...'.
actual_sorted="$workdir/actual.txt"
sed -n "s/^.*Project '\(:[^']*\)'.*$/\1/p" "$projects_output" \
  | LC_ALL=C sort -u > "$actual_sorted"
expected_sorted="$workdir/expected.txt"
LC_ALL=C sort -u "$expected_file" > "$expected_sorted"

unexpected="$(LC_ALL=C comm -13 "$expected_sorted" "$actual_sorted")"
missing="$(LC_ALL=C comm -23 "$expected_sorted" "$actual_sorted")"
if [ -n "$unexpected" ]; then
  while IFS= read -r p; do
    echo "unexpected ASK project $p"
  done <<< "$unexpected"
  status=1
fi
if [ -n "$missing" ]; then
  while IFS= read -r p; do
    echo "missing ASK project $p"
  done <<< "$missing"
  status=1
fi

# rg probe helper: $1 pattern, $2 file. Echoes matching lines; returns 0 on
# match, 1 on no match; exits 2 on rg/read failure so a broken tool can never
# masquerade as a pass.
probe() {
  local rc
  set +e
  rg -n --no-heading -e "$1" "$2"
  rc=$?
  set -e
  case "$rc" in
    0) return 0 ;;
    1) return 1 ;;
    *)
      echo "verify-ask-closure: rg failed (exit $rc) probing $2" >&2
      exit 2
      ;;
  esac
}

# --- 2. No add-on APK project on the app runtime classpath ----------------
if probe 'project :addons:[^ ]+:apk( |$)' "$deps_output" > "$workdir/apk-hits.txt"; then
  echo "forbidden add-on APK project on :ime:app debugRuntimeClasspath:"
  cat "$workdir/apk-hits.txt"
  status=1
fi

# --- 3. Exactly one application build file --------------------------------
# Map each graph project to its physical directory (first-party libraries at
# the root, ASK logical paths under keyboard/) and inspect its build file.
# Allowed application build file: keyboard/ime/app/build.gradle only.
app_plugin_pattern='com\.android\.application|libs\.plugins\.android\.application|apk_module\.gradle'
ime_app_has_plugin=0
while IFS= read -r p; do
  [ -z "$p" ] && continue
  rel="${p#:}"
  rel="${rel//://}"
  case "$p" in
    :core-personas | :core-providers | :personaspeak-ui) dir="$root/$rel" ;;
    *) dir="$root/keyboard/$rel" ;;
  esac
  for build_file in "$dir/build.gradle" "$dir/build.gradle.kts"; do
    [ -f "$build_file" ] || continue
    if probe "$app_plugin_pattern" "$build_file" > /dev/null; then
      if [ "$p" = ":ime:app" ] && [[ "$build_file" == */keyboard/ime/app/build.gradle ]]; then
        ime_app_has_plugin=1
      else
        echo "unexpected application plugin in ${build_file#"$root/"}"
        status=1
      fi
    fi
  done
done < "$actual_sorted"

# If :ime:app is in the graph its build file must actually be the application.
if grep -qx ':ime:app' "$actual_sorted" && [ "$ime_app_has_plugin" -ne 1 ]; then
  echo "missing application plugin in keyboard/ime/app/build.gradle"
  status=1
fi

if [ "$status" -eq 0 ]; then
  echo "ASK closure verified: exact project set, no add-on APKs, single application"
fi
exit "$status"
