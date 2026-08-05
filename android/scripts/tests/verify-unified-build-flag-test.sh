#!/usr/bin/env bash
# Contract test for the unified-build suppression of ASK's APK/AAB copy tasks.
#
# usage: verify-unified-build-flag-test.sh [<android-root>]
#
# Upstream's apk_module.gradle finalizes every assemble with a Copy task that
# duplicates the built artifact into <rootDir>/outputs/. Under the unified
# PersonaSpeak build that second file is a second shippable APK, so the root
# build sets personaSpeakUnifiedBuild and apk_module.gradle skips registering
# the copies.
#
# Two properties matter and both are checked:
#   1. the copies are SUPPRESSED under the unified root build; and
#   2. they are suppressed by a CONDITIONAL, not deleted — a nested or
#      standalone upstream build must keep upstream's behaviour intact. This
#      is an inherited file, and every line we change is a merge conflict we
#      pay for forever, so the change has to be a guard and nothing more.
#
# Property 2's runtime branch cannot be exercised from the command line: the
# root build script's `extra` assignment overrides any -P property, which is
# deliberate (the gate must not be switchable by a build flag). It is checked
# structurally here; the receipt records the mutation experiment that proved
# the branch live.
#
# Exit 0 when every assertion holds; 1 on violation; 2 on usage/tool failure.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ $# -ge 2 ]; then
  echo "usage: verify-unified-build-flag-test.sh [<android-root>]" >&2
  exit 2
fi
if [ $# -eq 1 ]; then
  root="$1"
else
  root="$(cd "$script_dir/../.." && pwd)"
fi
if [ ! -d "$root" ]; then
  echo "verify-unified-build-flag-test: not a directory: $root" >&2
  exit 2
fi

root_build="$root/build.gradle.kts"
apk_module="$root/keyboard/gradle/apk_module.gradle"
for f in "$root_build" "$apk_module"; do
  if [ ! -f "$f" ]; then
    echo "verify-unified-build-flag-test: missing $f" >&2
    exit 2
  fi
done

checks=0
fail() {
  echo "FAIL: $1" >&2
  exit 1
}

# grep wrapper that distinguishes "no match" from "could not read".
has() {
  local pattern="$1" file="$2" rc=0
  grep -Eq "$pattern" "$file" || rc=$?
  case "$rc" in
    0) return 0 ;;
    1) return 1 ;;
    *)
      echo "verify-unified-build-flag-test: grep failed (exit $rc) on $file" >&2
      exit 2
      ;;
  esac
}

# --- 1. The root build declares the flag, as a real boolean true -----------
has 'extra\["personaSpeakUnifiedBuild"\][[:space:]]*=[[:space:]]*true' "$root_build" \
  || fail "root build.gradle.kts does not set personaSpeakUnifiedBuild = true"
checks=$((checks + 1))

# --- 2. apk_module.gradle reads exactly that flag -------------------------
has "rootProject\.hasProperty\('personaSpeakUnifiedBuild'\)" "$apk_module" \
  || fail "apk_module.gradle does not read personaSpeakUnifiedBuild"
checks=$((checks + 1))

# --- 3. Registration is guarded, not deleted ------------------------------
has 'if \(!personaSpeakUnifiedBuild\)' "$apk_module" \
  || fail "apk_module.gradle does not guard copy-task registration on the flag"
checks=$((checks + 1))

for task in 'copy\$\{variant\.name\.capitalize\(\)\}Apk' 'copy\$\{variant\.name\.capitalize\(\)\}Aab'; do
  has "tasks\.register\(\"$task\", Copy\)" "$apk_module" \
    || fail "apk_module.gradle no longer registers $task — suppressed by deletion, not by a guard"
  checks=$((checks + 1))
done

# --- 4. Live: the unified root build registers no copy tasks --------------
# Skippable because it needs a JDK and a Gradle run; the static checks above
# do not. A skip is reported, never silently passed.
if [ "${SKIP_GRADLE:-0}" = "1" ]; then
  echo "note: live Gradle assertion skipped (SKIP_GRADLE=1)" >&2
else
  tasks_out="$(mktemp)"
  trap 'rm -f "$tasks_out"' EXIT
  gradle_rc=0
  "$root/gradlew" -p "$root" :ime:app:tasks --all --console=plain --no-daemon \
    > "$tasks_out" 2>&1 || gradle_rc=$?
  if [ "$gradle_rc" -ne 0 ]; then
    echo "verify-unified-build-flag-test: gradle failed (exit $gradle_rc)" >&2
    tail -40 "$tasks_out" >&2
    exit 2
  fi
  copy_rc=0
  copy_hits="$(grep -cE '^copy(Debug|Release|Canary|AllAddOns)(Apk|Aab)' "$tasks_out")" || copy_rc=$?
  if [ "$copy_rc" -gt 1 ]; then
    echo "verify-unified-build-flag-test: grep failed (exit $copy_rc)" >&2
    exit 2
  fi
  copy_hits="${copy_hits:-0}"
  if [ "$copy_hits" -ne 0 ]; then
    fail "unified root build still registers $copy_hits APK/AAB copy task(s)"
  fi
  checks=$((checks + 1))
fi

echo "PASS: unified-build copy suppression, $checks assertions"
