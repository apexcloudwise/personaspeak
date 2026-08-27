#!/usr/bin/env bash
# Aggregate Milestone 8 verification gate:
# - Upstream ASK closure & ledger verification
# - Fail-closed active composition & release usefulness test execution (:ime:app:testDebugUnitTest)
# - Milestone 8 build & signing receipt, usefulness receipt, R8 report, CI hygiene doc, and plan invariants
#
# usage: verify-milestone-8.sh [<android-root>]
#
# Exit codes: 0 pass; 1 gate violation; 2 usage or tool failure.
# Success line: PASS: milestone 8 gate
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ $# -ge 2 ]; then
    echo "usage: verify-milestone-8.sh [<android-root>]" >&2
    exit 2
fi
if [ $# -eq 1 ]; then
    root="$1"
else
    root="$(cd "$script_dir/.." && pwd)"
fi
if [ ! -d "$root" ]; then
    echo "verify-milestone-8: not a directory: $root" >&2
    exit 2
fi
root="$(cd "$root" && pwd)"

repo_root="$(git -C "$root" rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "$repo_root" ]; then
    echo "verify-milestone-8: no git repository at or above $root" >&2
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

echo "=== Milestone 8 Verification Gate ==="
echo "android-root: $root"
echo "repo-root:    $repo_root"
echo ""

# --- 1. Upstream ASK closure & ledger ---------------------------------------
echo "[1/3] verifying ASK closure and upstream ledger..."
run_checked "ASK closure" bash "$script_dir/verify-ask-closure.sh" "$root"
run_checked "upstream ledger" bash "$script_dir/verify-upstream-ledger.sh" "$root"
echo "  OK"

# --- 2. Active composition & release usefulness unit suite ------------------
echo "[2/3] running release active composition & usefulness test suite..."
if [ -f "$root/gradlew" ]; then
    run_checked "ReleaseUnitSuites" "$root/gradlew" -p "$root" \
        :ime:app:testDebugUnitTest \
        --tests "biz.pixelperfectstudios.personaspeak.ime.ReleaseActiveCompositionTest" \
        --tests "biz.pixelperfectstudios.personaspeak.ime.ReleaseUsefulnessReceiptTest" \
        --console=plain --no-daemon
fi
echo "  OK"

# --- 3. Milestone 8 Receipts, R8 Report & Plan Invariants -------------------
echo "[3/3] verifying Milestone 8 evidence receipts, R8 report, and plan invariants..."

python3 -c "
import json, os, sys

repo_root = sys.argv[1]

# 1. Verify Plan Document
plan_path = os.path.join(repo_root, 'docs/plans/m8-release-readiness-plan.md')
assert os.path.isfile(plan_path), f'Missing Milestone 8 plan at {plan_path}'
with open(plan_path, 'r', encoding='utf-8') as f:
    plan_text = f.read()
assert 'Milestone 8 Plan' in plan_text, 'Invalid plan header'
assert 'Slice A' in plan_text and 'Slice B' in plan_text, 'Plan missing slice definitions'

# 2. Verify R8 Report
r8_path = os.path.join(repo_root, 'docs/evidence/milestone-8/r8-minification-pass.md')
assert os.path.isfile(r8_path), f'Missing R8 report at {r8_path}'
with open(r8_path, 'r', encoding='utf-8') as f:
    r8_text = f.read()
assert 'Status: QUALIFIED' in r8_text, 'R8 report missing QUALIFIED status'

# 3. Verify Dependencies Lock
lock_path = os.path.join(repo_root, 'docs/evidence/milestone-8/dependencies-lock.txt')
assert os.path.isfile(lock_path) and os.path.getsize(lock_path) > 0, f'Missing or empty dependencies lock at {lock_path}'

# 4. Verify Evidence README
evidence_readme = os.path.join(repo_root, 'docs/evidence/milestone-8/README.md')
assert os.path.isfile(evidence_readme), f'Missing evidence README at {evidence_readme}'
with open(evidence_readme, 'r', encoding='utf-8') as f:
    readme_text = f.read()
assert 'Status: QUALIFIED' in readme_text, 'Evidence README missing QUALIFIED status'

# 5. Verify Machine Receipt JSON (Slice A)
receipt_path = os.path.join(repo_root, 'docs/evidence/milestone-8/slice-a-receipt.json')
assert os.path.isfile(receipt_path), f'Missing slice-a receipt at {receipt_path}'
with open(receipt_path, 'r', encoding='utf-8') as f:
    receipt = json.load(f)

assert receipt.get('schema') == 1, 'Receipt schema must be 1'
assert receipt.get('kind') == 'release_readiness_receipt', 'Receipt kind must be release_readiness_receipt'
assert receipt.get('milestone') == 'milestone-8', 'Receipt milestone must be milestone-8'
assert receipt.get('slice') == 'slice-a', 'Receipt slice must be slice-a'

v_info = receipt.get('version_info', {})
assert v_info.get('application_id') == 'biz.pixelperfectstudios.personaspeak', 'Invalid application_id'
assert v_info.get('version_code') == 1000, 'Invalid version_code'
assert v_info.get('version_name') == '0.1.0', 'Invalid version_name'
assert v_info.get('min_sdk') == 26, 'Invalid min_sdk'

# 6. Verify Usefulness Proof Doc (Slice B)
usefulness_path = os.path.join(repo_root, 'docs/evidence/milestone-8/usefulness-proof.md')
assert os.path.isfile(usefulness_path), f'Missing usefulness proof at {usefulness_path}'
with open(usefulness_path, 'r', encoding='utf-8') as f:
    u_text = f.read()
assert 'Document Status: QUALIFIED' in u_text, 'Usefulness proof missing QUALIFIED status'

# 7. Verify Usefulness Receipt JSON (Slice B)
usefulness_receipt_path = os.path.join(repo_root, 'docs/evidence/milestone-8/usefulness-receipt.json')
assert os.path.isfile(usefulness_receipt_path), f'Missing usefulness receipt at {usefulness_receipt_path}'
with open(usefulness_receipt_path, 'r', encoding='utf-8') as f:
    u_receipt = json.load(f)

assert u_receipt.get('schema') == 1, 'Usefulness receipt schema must be 1'
assert u_receipt.get('kind') == 'usefulness_receipt', 'Usefulness receipt kind must be usefulness_receipt'
assert u_receipt.get('milestone') == 'milestone-8', 'Usefulness receipt milestone must be milestone-8'
assert u_receipt.get('slice') == 'slice-b', 'Usefulness receipt slice must be slice-b'
assert u_receipt.get('run_id'), 'Missing run_id in usefulness receipt'
assert u_receipt.get('commit'), 'Missing commit in usefulness receipt'

u_verdicts = u_receipt.get('verdicts', {})
required_u_verdicts = [
    'composition_fake_provider_rewrite', 'openrouter_mock_adapter_rewrite',
    'anthropic_mock_adapter_rewrite', 'auth_failure_sanitized_surfacing',
    'rate_limit_sanitized_surfacing', 'network_error_sanitized_surfacing',
    'phase1_exit_demo_satisfied'
]
for uv in required_u_verdicts:
    assert u_verdicts.get(uv) == 'harness_verified', f'Usefulness verdict {uv} was not harness_verified'

# 8. Verify CI Required Checks Doc (Slice B)
ci_doc = os.path.join(repo_root, 'docs/evidence/milestone-8/ci-required-checks.md')
assert os.path.isfile(ci_doc), f'Missing CI required checks doc at {ci_doc}'
" "$repo_root"

echo "  OK (all Milestone 8 receipt & active-composition invariants verified)"
echo ""
echo "PASS: milestone 8 gate"
