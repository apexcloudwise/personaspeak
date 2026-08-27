#!/usr/bin/env bash
# Aggregate Milestone 7 verification gate:
# - Upstream ASK closure & ledger verification
# - Fresh-install integration test execution (:ime:app:testDebugUnitTest)
# - Release privacy & network egress audit test execution
# - Milestone 7 journey & privacy receipts & evidence validation
#
# usage: verify-milestone-7.sh [<android-root>]
#
# Exit codes: 0 pass; 1 gate violation; 2 usage or tool failure.
# Success line: PASS: milestone 7 gate
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ $# -ge 2 ]; then
    echo "usage: verify-milestone-7.sh [<android-root>]" >&2
    exit 2
fi
if [ $# -eq 1 ]; then
    root="$1"
else
    root="$(cd "$script_dir/.." && pwd)"
fi
if [ ! -d "$root" ]; then
    echo "verify-milestone-7: not a directory: $root" >&2
    exit 2
fi
root="$(cd "$root" && pwd)"

repo_root="$(git -C "$root" rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "$repo_root" ]; then
    echo "verify-milestone-7: no git repository at or above $root" >&2
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

echo "=== Milestone 7 Verification Gate ==="
echo "android-root: $root"
echo "repo-root:    $repo_root"
echo ""

# --- 1. Upstream ASK closure & ledger ---------------------------------------
echo "[1/3] verifying ASK closure and upstream ledger..."
run_checked "ASK closure" bash "$script_dir/verify-ask-closure.sh" "$root"
run_checked "upstream ledger" bash "$script_dir/verify-upstream-ledger.sh" "$root"
echo "  OK"

# --- 2. Fresh-install & Privacy Audit integration suite ---------------------
echo "[2/3] running fresh-install journey & privacy audit integration test suite..."
if [ -f "$root/gradlew" ]; then
    run_checked "Milestone7IntegrationSuites" "$root/gradlew" -p "$root" \
        :ime:app:testDebugUnitTest \
        --tests "biz.pixelperfectstudios.personaspeak.ime.FreshInstallJourneyIntegrationTest" \
        --tests "biz.pixelperfectstudios.personaspeak.ime.ReleasePrivacyAndEgressAuditTest" \
        --console=plain --no-daemon
fi
echo "  OK"

# --- 3. Milestone 7 Receipt & Plan Invariants -------------------------------
echo "[3/3] verifying Milestone 7 evidence receipts, audit, and plan invariants..."

python3 -c "
import json, os, sys

repo_root = sys.argv[1]

# 1. Verify Plan Document
plan_path = os.path.join(repo_root, 'docs/plans/m7-fresh-install-journey-and-release-audit-plan.md')
assert os.path.isfile(plan_path), f'Missing Milestone 7 plan at {plan_path}'
with open(plan_path, 'r', encoding='utf-8') as f:
    plan_text = f.read()
assert 'Milestone 7 Plan' in plan_text, 'Invalid plan header'
assert 'Slice A' in plan_text and 'Slice B' in plan_text, 'Plan missing slice definitions'

# 2. Verify Evidence README (Slice A)
evidence_readme = os.path.join(repo_root, 'docs/evidence/milestone-7/README.md')
assert os.path.isfile(evidence_readme), f'Missing evidence README at {evidence_readme}'
with open(evidence_readme, 'r', encoding='utf-8') as f:
    readme_text = f.read()
assert 'Status: SOURCE & HARNESS QUALIFIED' in readme_text, 'Evidence README missing SOURCE & HARNESS QUALIFIED status'

# 3. Verify Journey Machine Receipt JSON (Slice A)
journey_path = os.path.join(repo_root, 'docs/evidence/milestone-7/journey-receipt.json')
assert os.path.isfile(journey_path), f'Missing journey receipt at {journey_path}'
with open(journey_path, 'r', encoding='utf-8') as f:
    receipt = json.load(f)

assert receipt.get('schema') == 1, 'Receipt schema must be 1'
assert receipt.get('kind') == 'journey_receipt', 'Receipt kind must be journey_receipt'
assert receipt.get('milestone') == 'milestone-7', 'Receipt milestone must be milestone-7'
assert receipt.get('evidence_class') == 'jvm_robolectric_harness', 'Evidence class must be jvm_robolectric_harness'
assert receipt.get('run_id'), 'Receipt missing run_id'
assert receipt.get('commit'), 'Receipt missing commit'

counts = receipt.get('counts', {})
assert counts.get('journey_steps_total') == 8, f'Expected 8 total steps, got {counts.get(\"journey_steps_total\")}'
assert counts.get('journey_steps_completed') == 8, f'Expected 8 completed steps, got {counts.get(\"journey_steps_completed\")}'
assert counts.get('apply_mutations') == 1, 'Expected exactly 1 apply mutation'
assert counts.get('dismiss_mutations') == 0, 'Expected exactly 0 dismiss mutations'
assert counts.get('bundled_personas') == 4, 'Expected 4 bundled personas'

verdicts = receipt.get('verdicts', {})
required_verdicts = [
    'pristine_baseline', 'onboarding_flow', 'session_handoff',
    'brain_provider_setup', 'host_editor_rewrite', 'host_editor_mutations',
    'rtl_locale_pass', 'visual_theme_contrast'
]
for v in required_verdicts:
    assert verdicts.get(v) == 'harness_verified', f'Verdict {v} was not harness_verified: {verdicts.get(v)}'

# 4. Verify Privacy Audit Document (Slice B)
privacy_audit_path = os.path.join(repo_root, 'docs/evidence/milestone-7/privacy-and-egress-audit.md')
assert os.path.isfile(privacy_audit_path), f'Missing privacy audit at {privacy_audit_path}'
with open(privacy_audit_path, 'r', encoding='utf-8') as f:
    audit_text = f.read()
assert 'Document Status: QUALIFIED' in audit_text, 'Privacy audit missing QUALIFIED status'
assert 'Verdict: APPROVED FOR MILESTONE 8 UNBLOCK' in audit_text, 'Privacy audit missing non-author verdict statement'

# 5. Verify Privacy Audit Receipt JSON (Slice B)
privacy_receipt_path = os.path.join(repo_root, 'docs/evidence/milestone-7/privacy-audit-receipt.json')
assert os.path.isfile(privacy_receipt_path), f'Missing privacy audit receipt at {privacy_receipt_path}'
with open(privacy_receipt_path, 'r', encoding='utf-8') as f:
    p_receipt = json.load(f)

assert p_receipt.get('schema') == 1, 'Privacy receipt schema must be 1'
assert p_receipt.get('kind') == 'privacy_audit_receipt', 'Privacy receipt kind must be privacy_audit_receipt'
assert p_receipt.get('milestone') == 'milestone-7', 'Privacy receipt milestone must be milestone-7'
assert p_receipt.get('slice') == 'slice-b', 'Privacy receipt slice must be slice-b'

p_verdicts = p_receipt.get('verdicts', {})
required_p_verdicts = [
    'network_egress_typing', 'network_egress_opt_in', 'transport_isolation_https',
    'backup_rules_exclusion', 'memory_hygiene_zeroing', 'privacy_copy_alignment',
    'm8_unblock_verdict'
]
for pv in required_p_verdicts:
    assert p_verdicts.get(pv) in ['audit_verified', 'approved'], f'Privacy verdict {pv} not verified: {p_verdicts.get(pv)}'

exclusions = p_receipt.get('exclusions', [])
expected_exclusions = [
    'personaspeak_secret.bin',
    'personaspeak_secret.bin.staging',
    'datastore/personaspeak_provider_config.preferences_pb'
]
for exc in expected_exclusions:
    assert exc in exclusions, f'Missing expected exclusion {exc} in privacy receipt'
" "$repo_root"

echo "  OK (all Milestone 7 journey & privacy audit invariants verified)"
echo ""
echo "PASS: milestone 7 gate"
