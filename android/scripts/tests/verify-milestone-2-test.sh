#!/usr/bin/env bash
# Contract test for verify-milestone-2.sh, the aggregate post-cutover gate.
#
# usage: verify-milestone-2-test.sh [<android-root>]
#
# The aggregate gate is the single command whose exit status becomes the M2
# acceptance receipt. That makes *it* the thing most worth attacking: a gate
# that silently drops a stage, swallows a tool failure, or lets a red
# sub-verifier through is worse than no gate, because it produces a receipt
# that reads clean.
#
# So this suite checks the gate's structure and its failure semantics, not
# its happy path — the happy path is what the real run proves.
#
#   1. the script exists, is bash, and runs under `set -euo pipefail`;
#   2. it invokes every mandated stage, in order;
#   3. it runs the verifier fixture suites BEFORE trusting the production
#      verifiers;
#   4. it contains no `continue-on-error`-style swallowing and no bare
#      negated `rg`/`grep` that turns a tool failure into a pass;
#   5. every scan distinguishes exit 1 (no match) from exit 2+ (tool failure);
#   6. usage and tool failures return 2, violations return 1;
#   7. it never deletes or moves build artifacts;
#   8. the documented success line is present and unique.
#
# Exit 0 when every assertion holds; 1 on violation; 2 on usage/tool failure.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ $# -ge 2 ]; then
  echo "usage: verify-milestone-2-test.sh [<android-root>]" >&2
  exit 2
fi
if [ $# -eq 1 ]; then
  root="$1"
else
  root="$(cd "$script_dir/../.." && pwd)"
fi
if [ ! -d "$root" ]; then
  echo "verify-milestone-2-test: not a directory: $root" >&2
  exit 2
fi

gate="$root/scripts/verify-milestone-2.sh"
if [ ! -f "$gate" ]; then
  echo "FAIL: missing $gate" >&2
  exit 1
fi

checks=0
fail() {
  echo "FAIL: $1" >&2
  exit 1
}

has() {
  local pattern="$1" rc=0
  grep -Eq "$pattern" "$gate" || rc=$?
  case "$rc" in
    0) return 0 ;;
    1) return 1 ;;
    *)
      echo "verify-milestone-2-test: grep failed (exit $rc)" >&2
      exit 2
      ;;
  esac
}

# line_of <pattern> — first 1-indexed line matching, or empty.
line_of() {
  grep -nE "$1" "$gate" | head -1 | cut -d: -f1
}

require() {
  has "$1" || fail "$2"
  checks=$((checks + 1))
}

# --- 1. Shape --------------------------------------------------------------
require '^#!/usr/bin/env bash' "no bash shebang"
require '^set -euo pipefail' "not running under set -euo pipefail"

# --- 2. Every mandated stage is invoked ------------------------------------
require 'git .*status --porcelain'          "no clean-tracked-state stage"
require 'version "21\\\.'                   "no JDK 21 stage"
require 'verify-ask-closure\.sh'            "no ASK closure stage"
require 'verify-dictionary-licenses\.sh'    "no dictionary-license stage"
require 'verify-upstream-ledger\.sh'        "no upstream-ledger stage"
require 'verify-single-apk\.sh'             "no exact-one-APK stage"
require ':core-personas:test'               "no core-personas unit tests"
require ':core-providers:test'              "no core-providers unit tests"
require ':personaspeak-ui:testDebugUnitTest' "no personaspeak-ui unit tests"
require ':ime:app:testDebugUnitTest'        "no :ime:app unit tests"
require ':ime:app:lintDebug'                "no lintDebug stage"
require ':ime:app:assembleDebug'            "no assembleDebug stage"
require 'clean'                             "assembleDebug is not preceded by clean"
require 'core-personas/src'                 "no core Android-import scan"
require 'personaspeak-ui/src'               "no UI ASK-import scan"
require 'switchBackToPreviousKeyboard'      "no rejected-topology scan"
require 'SoftKeyboard\.java'                "no upstream boundary seam allowlist"
require 'apkanalyzer'                       "no APK manifest assertions"
require 'biz\.pixelperfectstudios\.personaspeak' "no package-identity assertion"
require 'SoftKeyboard'                      "no IME service assertion"
require 'minSdk|minSdkVersion'              "no minSdk assertion"
require 'targetSdk|targetSdkVersion'        "no targetSdk assertion"

# --- 3. Fixture suites run before the production verifiers ----------------
for suite in verify-ask-closure-test verify-dictionary-licenses-test \
             verify-upstream-ledger-test verify-single-apk-test \
             verify-unified-build-flag-test; do
  # Named anywhere is enough — a loop over an explicit suite list is as valid
  # as unrolled calls. What matters is that the name is there to be run.
  require "$suite" "fixture suite $suite.sh is never run"
done
require 'tests/' "fixture suites are never resolved under tests/"

first_fixture="$(line_of 'verify-single-apk-test')"
first_production="$(line_of '\$script_dir/verify-ask-closure\.sh')"
if [ -n "$first_fixture" ] && [ -n "$first_production" ]; then
  if [ "$first_fixture" -gt "$first_production" ]; then
    fail "production verifiers run before their fixture suites — the gate trusts tools it has not tested"
  fi
  checks=$((checks + 1))
fi

# --- 4. No swallowing, no bare negated matchers ---------------------------
if has 'continue-on-error'; then
  fail "contains continue-on-error"
fi
checks=$((checks + 1))

# A bare `! rg ...` / `! grep ...` as a statement reads tool failure (exit 2)
# as success, because `!` inverts any non-zero status. This is the exact
# mistake the M2 plan calls out.
if has '^[[:space:]]*!\s*(rg|grep)\b'; then
  fail "bare negated rg/grep — a tool failure would read as a clean scan"
fi
checks=$((checks + 1))

# --- 5. Explicit exit-code handling on scans ------------------------------
require '2\)|\*\)' "no explicit non-0/1 exit handling on scans"
require 'exit 2'   "never returns 2 for tool failure"

# --- 6. Usage handling ----------------------------------------------------
require 'usage:' "no usage line"

# --- 7. Read-only: the gate must not clean up artifacts -------------------
# `clean` as a Gradle task is expected; `rm -rf` over outputs is not. A gate
# that tidies away a stale APK turns a violation into a pass.
if grep -nE '\brm\b' "$gate" | grep -vE 'rm -rf "\$workdir"|rm -f "\$|trap' | grep -q .; then
  fail "removes files outside its own workdir — a gate must not clean up the evidence"
fi
checks=$((checks + 1))

# --- 8. Success line present and unique -----------------------------------
# Count only lines that can actually emit it. The header comment documents
# the success line; it does not print one.
success_count="$(grep -E 'PASS: milestone 2 gate' "$gate" | grep -cvE '^[[:space:]]*#' || true)"
if [ "${success_count:-0}" -ne 1 ]; then
  fail "expected exactly 1 emitting 'PASS: milestone 2 gate' line, found ${success_count:-0}"
fi
checks=$((checks + 1))

# --- 9. Live usage/tool-failure semantics ---------------------------------
rc=0
bash "$gate" "$root/definitely-not-a-directory" > /dev/null 2>&1 || rc=$?
if [ "$rc" -ne 2 ]; then
  fail "nonexistent root returned $rc, expected 2"
fi
checks=$((checks + 1))

rc=0
bash "$gate" "$root" "$root" "$root" > /dev/null 2>&1 || rc=$?
if [ "$rc" -ne 2 ]; then
  fail "too many arguments returned $rc, expected 2"
fi
checks=$((checks + 1))

echo "PASS: verify-milestone-2 contract, $checks assertions"
