#!/usr/bin/env bash
# Aggregate Milestone 4 verification gate:
# - No-secret-logging static scan
# - Debug Kotlin compilation (:ime:app:compileDebugKotlin)
# - Exact ASK closure & upstream-rent ledger verification
# - Deterministic harness code & memory-zeroing contract verification
#
# usage: verify-milestone-4.sh [<android-root>]
#
# Exit codes: 0 pass; 1 gate violation; 2 usage or tool failure.
# Success line: PASS: milestone 4 gate
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ $# -ge 2 ]; then
    echo "usage: verify-milestone-4.sh [<android-root>]" >&2
    exit 2
fi
if [ $# -eq 1 ]; then
    root="$1"
else
    root="$(cd "$script_dir/.." && pwd)"
fi
if [ ! -d "$root" ]; then
    echo "verify-milestone-4: not a directory: $root" >&2
    exit 2
fi
root="$(cd "$root" && pwd)"

repo_root="$(git -C "$root" rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "$repo_root" ]; then
    echo "verify-milestone-4: no git repository at or above $root" >&2
    exit 2
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

run_checked() {
    local label="$1" rc=0 raw
    shift
    raw="$("$@" 2>&1)" || rc=$?
    case "$rc" in
        0) return 0 ;;
        1)
            echo "FAIL: $label"
            printf '%s\n' "$raw"
            exit 1
            ;;
        *)
            echo "FAIL: $label tool failure (exit $rc)" >&2
            printf '%s\n' "$raw" >&2
            exit 2
            ;;
    esac
}

echo "=== Milestone 4 Verification Gate ==="
echo "android-root: $root"
echo "repo-root:    $repo_root"
echo ""

# --- 1. No secret logging verifier & Kotlin compilation --------------------
echo "[1/3] verifying zero secret logging and debug compilation..."
run_checked "no-secret-logging" bash "$script_dir/verify-no-secret-logging.sh" "$root"
if [ -f "$root/gradlew" ]; then
    run_checked "compileDebugKotlin" "$root/gradlew" -p "$root" :ime:app:compileDebugKotlin --console=plain --no-daemon
fi
echo "  OK"

# --- 2. Upstream ASK closure & ledger ---------------------------------------
echo "[2/3] verifying ASK closure and upstream ledger..."
run_checked "ASK closure" bash "$script_dir/verify-ask-closure.sh" "$root"
run_checked "upstream ledger" bash "$script_dir/verify-upstream-ledger.sh" "$root"
echo "  OK"

# --- 3. Deterministic Harness & Adapter Contract Assertions -----------------
echo "[3/3] verifying harness invariants and memory zeroing contracts..."

python3 -c "
import os, sys

root = sys.argv[1]

# 1. Verify PersonaspeakAdapterHarnessActivity exists and contains zero intent-key exposure
adapter_harness = os.path.join(root, 'keyboard/ime/app/src/debug/java/biz/pixelperfectstudios/personaspeak/data/harness/PersonaspeakAdapterHarnessActivity.kt')
assert os.path.isfile(adapter_harness), f'Missing adapter harness at {adapter_harness}'
with open(adapter_harness, 'r') as f:
    src = f.read()
assert 'getStringExtra(\"key\")' not in src, 'Forbidden intent key extraction found in adapter harness'
assert 'secret.value.fill(0)' in src or 'fill(0)' in src, 'Missing memory zeroing in adapter harness'
assert 'MockAndroidHttpTransport' in src, 'Missing MockAndroidHttpTransport in adapter harness'

# 2. Verify PersonaspeakStorageHarnessActivity exists and uses SecureRandom
storage_harness = os.path.join(root, 'keyboard/ime/app/src/debug/java/biz/pixelperfectstudios/personaspeak/data/harness/PersonaspeakStorageHarnessActivity.kt')
assert os.path.isfile(storage_harness), f'Missing storage harness at {storage_harness}'
with open(storage_harness, 'r') as f:
    storage_src = f.read()
assert 'SecureRandom' in storage_src, 'Missing SecureRandom in storage harness'
assert 'ACTION_SEED' in storage_src, 'Missing ACTION_SEED in storage harness'

# 3. Verify AnthropicMessagesAdapter memory zeroing
adapter_src_file = os.path.join(root, 'personaspeak-providers/src/main/kotlin/biz/pixelperfectstudios/personaspeak/providers/AnthropicMessagesAdapter.kt')
assert os.path.isfile(adapter_src_file), f'Missing AnthropicMessagesAdapter at {adapter_src_file}'
with open(adapter_src_file, 'r') as f:
    adapter_src = f.read()
assert 'secret.value.fill(0)' in adapter_src, 'Missing SecretBytes.fill(0) in AnthropicMessagesAdapter finally block'
" "$root"

echo "  OK (all deterministic source & compile invariants verified)"
echo ""
echo "PASS: milestone 4 gate"
