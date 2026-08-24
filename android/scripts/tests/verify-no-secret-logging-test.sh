#!/usr/bin/env bash
# Unit test for verify-no-secret-logging.sh

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/../verify-no-secret-logging.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Test 1: Real project directory passes
"$verifier" "$script_dir/../.." > /dev/null

# Test 2: Missing source directory fails with exit code 2
empty_dir="$tmp/empty"
mkdir -p "$empty_dir"
missing_rc=0
"$verifier" "$empty_dir" > /dev/null 2>&1 || missing_rc=$?
if [ "$missing_rc" -ne 2 ]; then
  echo "FAIL: expected exit code 2 for missing source dir, got $missing_rc" >&2
  exit 1
fi

# Test 3: Fixture with secret logging fails with exit code 1
fixture_root="$tmp/android_fixture"
fixture_src="$fixture_root/personaspeak-providers/src/main/kotlin/biz/pixelperfectstudios/personaspeak"
mkdir -p "$fixture_src"

cat << 'KOTLIN' > "$fixture_src/Leaky.kt"
package biz.pixelperfectstudios.personaspeak

import android.util.Log

class Leaky {
    fun doBad(key: String) {
        Log.d("TAG", "the key is " + key)
    }
}
KOTLIN

leaky_rc=0
"$verifier" "$fixture_root" > /dev/null 2>&1 || leaky_rc=$?
if [ "$leaky_rc" -ne 1 ]; then
  echo "FAIL: expected exit code 1 for leaky code, got $leaky_rc" >&2
  exit 1
fi

# Test 4: Clean fixture passes with exit code 0
rm "$fixture_src/Leaky.kt"
cat << 'KOTLIN' > "$fixture_src/Clean.kt"
package biz.pixelperfectstudios.personaspeak

class Clean {
    fun doGood() {
        // No logging of credentials
    }
}
KOTLIN

clean_rc=0
"$verifier" "$fixture_root" > /dev/null 2>&1 || clean_rc=$?
if [ "$clean_rc" -ne 0 ]; then
  echo "FAIL: expected exit code 0 for clean fixture, got $clean_rc" >&2
  exit 1
fi

echo "PASS: verify-no-secret-logging contract verified (positive, negative, and missing-path cases)"
exit 0
