#!/usr/bin/env bash
# Unit/contract test for verify-milestone-8.sh
# Tests positive real tree and deterministic contract violations (fail closed).
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/../verify-milestone-8.sh"
android_root="$(cd "$script_dir/../.." && pwd)"
repo_root="$(git -C "$android_root" rev-parse --show-toplevel)"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Test 1: Real repo root passes cleanly
out="$("$verifier" "$android_root")"
if ! printf '%s' "$out" | grep -q 'PASS: milestone 8 gate'; then
    echo "FAIL: expected PASS: milestone 8 gate on real repo" >&2
    exit 1
fi

# Set up temporary isolated fixture to test failure modes
fixture_repo="$tmp/repo"
mkdir -p "$fixture_repo"
cp -r "$repo_root/android" "$fixture_repo/android"
mkdir -p "$fixture_repo/docs/plans"
mkdir -p "$fixture_repo/docs/evidence/milestone-8"
cp "$repo_root/docs/plans/m8-release-readiness-plan.md" "$fixture_repo/docs/plans/"
cp "$repo_root/docs/evidence/milestone-8/README.md" "$fixture_repo/docs/evidence/milestone-8/"
cp "$repo_root/docs/evidence/milestone-8/slice-a-receipt.json" "$fixture_repo/docs/evidence/milestone-8/"
cp "$repo_root/docs/evidence/milestone-8/r8-minification-pass.md" "$fixture_repo/docs/evidence/milestone-8/"
cp "$repo_root/docs/evidence/milestone-8/dependencies-lock.txt" "$fixture_repo/docs/evidence/milestone-8/"
git -C "$fixture_repo" init -q

# Pre-captured ASK closure seam outputs
projects_good="$tmp/projects.txt"
{
  echo "Root project 'personaboard'"
  while IFS= read -r p; do
    [ -z "$p" ] && continue
    echo "+--- Project '$p'"
  done < "$repo_root/android/scripts/expected-ask-projects.txt"
} > "$projects_good"

deps_good="$tmp/deps.txt"
{
  echo "debugRuntimeClasspath - Runtime classpath of compilation 'debug' (target  (androidJvm))."
  echo "+--- project :personaspeak-ui"
  echo "+--- project :addons:base"
  echo "+--- project :addons:languages:english:pack"
  echo "\\--- project :ime:base"
} > "$deps_good"

export ASK_CLOSURE_PROJECTS_OUTPUT="$projects_good"
export ASK_CLOSURE_DEPS_OUTPUT="$deps_good"

# Test 2: Tampered receipt with unverified verdict fails with exit code 1
receipt_file="$fixture_repo/docs/evidence/milestone-8/slice-a-receipt.json"
sed -i '' 's/"fail_closed_active_composition": "verified"/"fail_closed_active_composition": "unverified"/' "$receipt_file" 2>/dev/null || \
sed -i 's/"fail_closed_active_composition": "verified"/"fail_closed_active_composition": "unverified"/' "$receipt_file"

tamper_rc=0
"$verifier" "$fixture_repo/android" > /dev/null 2>&1 || tamper_rc=$?
if [ "$tamper_rc" -ne 1 ]; then
    echo "FAIL: expected exit code 1 on unverified receipt verdict, got $tamper_rc" >&2
    exit 1
fi
# Restore
cp "$repo_root/docs/evidence/milestone-8/slice-a-receipt.json" "$receipt_file"

# Test 3: Missing R8 report fails with exit code 1
r8_file="$fixture_repo/docs/evidence/milestone-8/r8-minification-pass.md"
rm "$r8_file"
r8_rc=0
"$verifier" "$fixture_repo/android" > /dev/null 2>&1 || r8_rc=$?
if [ "$r8_rc" -ne 1 ]; then
    echo "FAIL: expected exit code 1 on missing R8 report, got $r8_rc" >&2
    exit 1
fi
cp "$repo_root/docs/evidence/milestone-8/r8-minification-pass.md" "$r8_file"

# Test 4: Missing plan file fails with exit code 1
plan_file="$fixture_repo/docs/plans/m8-release-readiness-plan.md"
rm "$plan_file"
missing_rc=0
"$verifier" "$fixture_repo/android" > /dev/null 2>&1 || missing_rc=$?
if [ "$missing_rc" -ne 1 ]; then
    echo "FAIL: expected exit code 1 on missing plan file, got $missing_rc" >&2
    exit 1
fi
cp "$repo_root/docs/plans/m8-release-readiness-plan.md" "$plan_file"

echo "PASS: verify-milestone-8 contract verified (positive, tampered-verdict, missing-r8, and missing-plan cases)"
exit 0
