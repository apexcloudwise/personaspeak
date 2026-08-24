#!/usr/bin/env bash
# Unit/contract test for verify-milestone-4.sh
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/../verify-milestone-4.sh"
android_root="$(cd "$script_dir/../.." && pwd)"

# Test 1: Real repo root passes cleanly
out="$("$verifier" "$android_root")"
if ! printf '%s' "$out" | grep -q 'PASS: milestone 4 gate'; then
    echo "FAIL: expected PASS: milestone 4 gate on real repo" >&2
    exit 1
fi

echo "PASS: verify-milestone-4 contract verified"
exit 0
