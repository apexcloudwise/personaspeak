#!/usr/bin/env bash
# Unit/contract test for verify-milestone-7.sh
# Tests positive real tree and deterministic contract violations (fail closed).
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/../verify-milestone-7.sh"
android_root="$(cd "$script_dir/../.." && pwd)"
repo_root="$(git -C "$android_root" rev-parse --show-toplevel)"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Test 1: Real repo root passes cleanly
out="$("$verifier" "$android_root")"
if ! printf '%s' "$out" | grep -q 'PASS: milestone 7 gate'; then
    echo "FAIL: expected PASS: milestone 7 gate on real repo" >&2
    exit 1
fi

# Set up temporary isolated fixture to test failure modes
fixture_repo="$tmp/repo"
mkdir -p "$fixture_repo"
cp -r "$repo_root/android" "$fixture_repo/android"
mkdir -p "$fixture_repo/docs/plans"
mkdir -p "$fixture_repo/docs/evidence/milestone-7"
cp "$repo_root/docs/plans/m7-fresh-install-journey-and-release-audit-plan.md" "$fixture_repo/docs/plans/"
cp "$repo_root/docs/evidence/milestone-7/README.md" "$fixture_repo/docs/evidence/milestone-7/"
cp "$repo_root/docs/evidence/milestone-7/journey-receipt.json" "$fixture_repo/docs/evidence/milestone-7/"
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
receipt_file="$fixture_repo/docs/evidence/milestone-7/journey-receipt.json"
sed -i '' 's/"rtl_locale_pass": "harness_verified"/"rtl_locale_pass": "unverified"/' "$receipt_file" 2>/dev/null || \
sed -i 's/"rtl_locale_pass": "harness_verified"/"rtl_locale_pass": "unverified"/' "$receipt_file"

tamper_rc=0
"$verifier" "$fixture_repo/android" > /dev/null 2>&1 || tamper_rc=$?
if [ "$tamper_rc" -ne 1 ]; then
    echo "FAIL: expected exit code 1 on unverified receipt verdict, got $tamper_rc" >&2
    exit 1
fi
# Restore
cp "$repo_root/docs/evidence/milestone-7/journey-receipt.json" "$receipt_file"

# Test 3: Missing plan file fails with exit code 1
plan_file="$fixture_repo/docs/plans/m7-fresh-install-journey-and-release-audit-plan.md"
rm "$plan_file"
missing_rc=0
"$verifier" "$fixture_repo/android" > /dev/null 2>&1 || missing_rc=$?
if [ "$missing_rc" -ne 1 ]; then
    echo "FAIL: expected exit code 1 on missing plan file, got $missing_rc" >&2
    exit 1
fi
cp "$repo_root/docs/plans/m7-fresh-install-journey-and-release-audit-plan.md" "$plan_file"

echo "PASS: verify-milestone-7 contract verified (positive, tampered-verdict, and missing-plan cases)"
exit 0
