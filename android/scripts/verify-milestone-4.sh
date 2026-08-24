#!/usr/bin/env bash
# Aggregate Milestone 4 verification gate:
# - No-secret-logging static scan
# - Exact ASK closure & upstream-rent ledger verification
# - Milestone 4 evidence receipts manifest & SHA-256 integrity check
# - Invariant validation across storage, backup exclusion, and network egress receipts
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

# --- 1. No secret logging verifier -----------------------------------------
echo "[1/4] verifying zero secret logging in :personaspeak-providers..."
run_checked "no-secret-logging" bash "$script_dir/verify-no-secret-logging.sh" "$root"
echo "  OK"

# --- 2. Upstream ASK closure & ledger ---------------------------------------
echo "[2/4] verifying ASK closure and upstream ledger..."
run_checked "ASK closure" bash "$script_dir/verify-ask-closure.sh" "$root"
run_checked "upstream ledger" bash "$script_dir/verify-upstream-ledger.sh" "$root"
echo "  OK"

# --- 3. Evidence manifest SHA-256 integrity ---------------------------------
echo "[3/4] verifying Milestone 4 evidence manifest & SHA-256 digests..."
evidence_dir="$repo_root/docs/evidence/milestone-4"
manifest_path="$evidence_dir/receipt-manifest.json"

if [ ! -f "$manifest_path" ]; then
    echo "FAIL: missing receipt-manifest.json at $manifest_path"
    exit 1
fi

python3 -c "
import json, hashlib, os, sys

evidence_dir = sys.argv[1]
manifest_file = os.path.join(evidence_dir, 'receipt-manifest.json')

with open(manifest_file, 'r') as f:
    manifest = json.load(f)

if manifest.get('schemaVersion') != 1:
    print('FAIL: invalid manifest schemaVersion')
    sys.exit(1)

receipts = manifest.get('receipts', {})
if not receipts:
    print('FAIL: manifest contains no receipts')
    sys.exit(1)

for fname, meta in receipts.items():
    fpath = os.path.join(evidence_dir, fname)
    if not os.path.isfile(fpath):
        print(f'FAIL: receipt file {fname} missing from {evidence_dir}')
        sys.exit(1)
    with open(fpath, 'rb') as f:
        digest = hashlib.sha256(f.read()).hexdigest()
    if digest != meta.get('sha256'):
        print(f'FAIL: digest mismatch for {fname}: computed {digest} != manifest {meta.get(\"sha256\")}')
        sys.exit(1)
" "$evidence_dir"
echo "  OK (all receipts matched authority manifest digests)"

# --- 4. Schema & receipt invariant assertions -------------------------------
echo "[4/4] verifying receipt contents and security invariants..."

python3 -c "
import json, os, sys

evidence_dir = sys.argv[1]

# 1. API 27 Backup Exclusion
with open(os.path.join(evidence_dir, 'backup-api27-receipt.json'), 'r') as f:
    backup = json.load(f)
assert backup.get('verdict') == 'PASSED', 'backup verdict not PASSED'
assert backup['verificationAssertions']['positiveControl']['observed'] == 'PRESENT', 'positive canary not preserved'
assert backup['verificationAssertions']['keystoreSecretCiphertext']['observed'] == 'ABSENT', 'secret ciphertext not excluded'
assert backup['verificationAssertions']['datastoreMetadata']['observed'] == 'ABSENT', 'datastore metadata not excluded'
assert backup['verificationAssertions']['runtimeQueryOutcome']['status'] == 'PASS', 'runtime query failed'

# 2. Adapter Parser Journey
with open(os.path.join(evidence_dir, 'adapter-parser-receipt.json'), 'r') as f:
    adapter = json.load(f)
assert adapter.get('verdict') == 'PASSED', 'adapter verdict not PASSED'
assert adapter['modeA_offlineValidation']['status'] == 'PASS', 'mode A validation failed'
assert adapter['modeA_offlineValidation']['memoryZeroingAssertion']['verifiedZeroed'] == True, 'memory zeroing unverified'
assert adapter['modeB_liveEgressSmoke']['status'] == 'PASS', 'mode B live smoke failed'
assert adapter['modeB_liveEgressSmoke']['logcatPrivacyAudit']['auditVerdict'] == 'CLEAN', 'logcat audit found forbidden tokens'

# 3. Storage & Egress Audit
with open(os.path.join(evidence_dir, 'storage-egress-audit-receipt.json'), 'r') as f:
    audit = json.load(f)
assert audit.get('verdict') == 'PASSED', 'audit verdict not PASSED'
assert audit['packageStorageAudit']['recursiveByteScan']['verdict'] == 'CLEAN', 'storage scan found plaintext'
assert audit['networkTransportEgressAudit']['thirdPartyEgressCount'] == 0, 'third party egress observed'
assert audit['networkTransportEgressAudit']['unencryptedEgressCount'] == 0, 'unencrypted egress observed'
assert audit['networkTransportEgressAudit']['approvedEndpoint'] == 'https://api.anthropic.com/v1/messages', 'unapproved endpoint'
assert audit['networkTransportEgressAudit']['failClosedEvaluation'] == 'PASSED', 'fail-closed evaluation not passed'
" "$evidence_dir"

echo "  OK (all invariants verified)"
echo ""
echo "PASS: milestone 4 gate"
