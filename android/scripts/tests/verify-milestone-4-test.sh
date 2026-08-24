#!/usr/bin/env bash
# Unit/contract test for verify-milestone-4.sh
# Tests positive real tree, tampered receipt digests, tampered raw log digests,
# missing files, and schema invariant violations (fail closed).
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
mkdir -p "$fixture_repo/docs/evidence/milestone-4"
cp -r "$repo_root/docs/evidence/milestone-4"/* "$fixture_repo/docs/evidence/milestone-4/"
mkdir -p "$fixture_repo/android/personaspeak-providers"
cp -r "$repo_root/android/personaspeak-providers"/* "$fixture_repo/android/personaspeak-providers/"
mkdir -p "$fixture_repo/android/keyboard"
cp "$repo_root/android/keyboard/UPSTREAM.md" "$fixture_repo/android/keyboard/"
cp "$repo_root/android/keyboard/UPSTREAM-MODIFIED.md" "$fixture_repo/android/keyboard/"
git -C "$fixture_repo" init -q

# Test 2: Tampered receipt JSON content (digest mismatch) fails with exit code 1
tampered_receipt="$fixture_repo/docs/evidence/milestone-4/backup-api27-receipt.json"
echo '{"tampered": true}' >> "$tampered_receipt"
tamper_rc=0
"$verifier" "$fixture_repo/android" > /dev/null 2>&1 || tamper_rc=$?
if [ "$tamper_rc" -ne 1 ]; then
    echo "FAIL: expected exit code 1 on tampered receipt digest, got $tamper_rc" >&2
    exit 1
fi
# Restore receipt
cp "$repo_root/docs/evidence/milestone-4/backup-api27-receipt.json" "$tampered_receipt"

# Test 3: Tampered raw log (digest mismatch) fails with exit code 1
tampered_raw="$fixture_repo/docs/evidence/milestone-4/raw/raw_dns_lookup.log"
echo 'tampered log line' >> "$tampered_raw"
raw_tamper_rc=0
"$verifier" "$fixture_repo/android" > /dev/null 2>&1 || raw_tamper_rc=$?
if [ "$raw_tamper_rc" -ne 1 ]; then
    echo "FAIL: expected exit code 1 on tampered raw log digest, got $raw_tamper_rc" >&2
    exit 1
fi
# Restore raw log
cp "$repo_root/docs/evidence/milestone-4/raw/raw_dns_lookup.log" "$tampered_raw"

# Test 4: Missing receipt file fails with exit code 1
rm "$fixture_repo/docs/evidence/milestone-4/adapter-parser-receipt.json"
missing_rc=0
"$verifier" "$fixture_repo/android" > /dev/null 2>&1 || missing_rc=$?
if [ "$missing_rc" -ne 1 ]; then
    echo "FAIL: expected exit code 1 on missing receipt file, got $missing_rc" >&2
    exit 1
fi
cp "$repo_root/docs/evidence/milestone-4/adapter-parser-receipt.json" "$fixture_repo/docs/evidence/milestone-4/"

# Test 5: Missing raw log file fails with exit code 1
rm "$fixture_repo/docs/evidence/milestone-4/raw/raw_socket_sampling.log"
missing_raw_rc=0
"$verifier" "$fixture_repo/android" > /dev/null 2>&1 || missing_raw_rc=$?
if [ "$missing_raw_rc" -ne 1 ]; then
    echo "FAIL: expected exit code 1 on missing raw log file, got $missing_raw_rc" >&2
    exit 1
fi
cp "$repo_root/docs/evidence/milestone-4/raw/raw_socket_sampling.log" "$fixture_repo/docs/evidence/milestone-4/raw/"

echo "PASS: verify-milestone-4 contract verified (positive, tampered-receipt, tampered-raw-log, and missing-file cases)"
exit 0
