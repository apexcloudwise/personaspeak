#!/usr/bin/env bash
# Unit/contract test for verify-milestone-4.sh
# Tests positive real tree and deterministic contract violations (fail closed).
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/../verify-milestone-4.sh"
android_root="$(cd "$script_dir/../.." && pwd)"
repo_root="$(git -C "$android_root" rev-parse --show-toplevel)"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Test 1: Real repo root passes cleanly
out="$("$verifier" "$android_root")"
if ! printf '%s' "$out" | grep -q 'PASS: milestone 4 gate'; then
    echo "FAIL: expected PASS: milestone 4 gate on real repo" >&2
    exit 1
fi

# Set up temporary isolated fixture to test failure modes
fixture_repo="$tmp/repo"
mkdir -p "$fixture_repo/android/scripts"
cp -r "$repo_root/android/scripts"/* "$fixture_repo/android/scripts/"
mkdir -p "$fixture_repo/android/personaspeak-providers"
cp -r "$repo_root/android/personaspeak-providers"/* "$fixture_repo/android/personaspeak-providers/"
mkdir -p "$fixture_repo/android/keyboard"
cp -r "$repo_root/android/keyboard"/* "$fixture_repo/android/keyboard/"
git -C "$fixture_repo" init -q

# Test 2: Tampered adapter harness with forbidden intent key exposure fails with exit code 1
adapter_harness="$fixture_repo/android/keyboard/ime/app/src/debug/java/biz/pixelperfectstudios/personaspeak/data/harness/PersonaspeakAdapterHarnessActivity.kt"
echo 'val leak = intent.getStringExtra("key")' >> "$adapter_harness"
tamper_rc=0
"$verifier" "$fixture_repo/android" > /dev/null 2>&1 || tamper_rc=$?
if [ "$tamper_rc" -ne 1 ]; then
    echo "FAIL: expected exit code 1 on forbidden intent key leak, got $tamper_rc" >&2
    exit 1
fi
# Restore
cp "$repo_root/android/keyboard/ime/app/src/debug/java/biz/pixelperfectstudios/personaspeak/data/harness/PersonaspeakAdapterHarnessActivity.kt" "$adapter_harness"

# Test 3: Missing storage harness fails with exit code 1
storage_harness="$fixture_repo/android/keyboard/ime/app/src/debug/java/biz/pixelperfectstudios/personaspeak/data/harness/PersonaspeakStorageHarnessActivity.kt"
rm "$storage_harness"
missing_rc=0
"$verifier" "$fixture_repo/android" > /dev/null 2>&1 || missing_rc=$?
if [ "$missing_rc" -ne 1 ]; then
    echo "FAIL: expected exit code 1 on missing storage harness, got $missing_rc" >&2
    exit 1
fi
cp "$repo_root/android/keyboard/ime/app/src/debug/java/biz/pixelperfectstudios/personaspeak/data/harness/PersonaspeakStorageHarnessActivity.kt" "$storage_harness"

echo "PASS: verify-milestone-4 contract verified (positive, tampered-contract, and missing-file cases)"
exit 0
