#!/usr/bin/env bash
# Verify the FlorisBoard second host's privacy-audit posture (ADR-0010
# P4, ASK-M7 bar): the audit document exists with its required
# disclosures, and the user-facing privacy copy no longer makes the
# false "no Internet permission" claim upstream shipped.
#
# usage: verify-floris-audit.sh <repo-root>
#
# Test seams: AUDIT_DOC and FLORIS_ROOT override the document and the
# vendored tree.
#
# Exit codes: 0 pass; 1 contract violation; 2 usage or tool failure.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: verify-floris-audit.sh <repo-root>" >&2
  exit 2
fi
root="$1"
if [ ! -d "$root" ]; then
  echo "verify-floris-audit: not a directory: $root" >&2
  exit 2
fi
root="$(cd "$root" && pwd)"
audit="${AUDIT_DOC:-$root/docs/evidence/floris-host/privacy-and-egress-audit.md}"
floris="${FLORIS_ROOT:-$root/android/florisboard}"

fail=0
violation() {
  echo "verify-floris-audit: $1" >&2
  fail=1
}

grep_probes() {
  local pattern="$1" file="$2" what="$3"
  local rc=0
  grep -qE "$pattern" "$file" 2>/dev/null || rc=$?
  if [ "$rc" -ge 2 ]; then
    echo "verify-floris-audit: grep failed on $file" >&2
    exit 2
  fi
  if [ "$rc" -eq 1 ]; then
    violation "$what"
  fi
}

# --- 1. The audit document and its load-bearing disclosures -------------
if [ ! -f "$audit" ]; then
  violation "missing audit document: ${audit#"$root"/}"
else
  grep_probes 'Document Status: QUALIFIED' "$audit" \
    "audit document missing QUALIFIED status line"
  grep_probes '## 2\. Network Egress' "$audit" \
    "audit document missing the network-egress section"
  grep_probes 'EmojiCompat' "$audit" \
    "audit document must disclose the EmojiCompat automatic-metadata path"
  grep_probes 'personaspeak_secret\.bin' "$audit" \
    "audit document missing the credential backup-exclusion matrix"
  grep_probes 'Non-author review skipped' "$audit" \
    "audit document must carry the loud non-author-review skip"
  grep_probes '20260903T170746Z' "$audit" \
    "audit document must cite the device-journey run ID"
fi

# --- 2. Privacy copy: the false permission claim stays gone -------------
strings_file="$floris/app/src/main/res/values/strings.xml"
if [ ! -f "$strings_file" ]; then
  violation "missing file: ${strings_file#"$root"/}"
else
  if grep -q 'does not have Internet permission' "$strings_file" 2>/dev/null; then
    violation "update-hint string still claims the app has no Internet permission (false since the PersonaSpeak INTERNET addition)"
  fi
  grep_probes 'ext__update_box__internet_permission_hint' "$strings_file" \
    "update-hint string vanished — keep the (corrected) manual-update copy"
fi

# --- 3. The string edit pays its rent -------------------------------------
ledger="$floris/UPSTREAM-MODIFIED.md"
if [ ! -f "$ledger" ]; then
  violation "missing file: ${ledger#"$root"/}"
else
  grep_probes 'strings\.xml' "$ledger" \
    "rent ledger has no entry for the strings.xml privacy-copy edit"
fi

if [ "$fail" -ne 0 ]; then
  exit 1
fi
echo "verify-floris-audit: privacy audit present, disclosures intact, privacy copy honest"
exit 0
