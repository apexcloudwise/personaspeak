#!/usr/bin/env bash
# Contract test for verify-floris-audit.sh.
#
# Drives the verifier through its AUDIT_DOC / FLORIS_ROOT seams:
#   1. the real-tree posture must be accepted (exit 0);
#   2. each P4 invariant, broken one at a time, must be rejected with
#      its named finding: missing audit document, lost QUALIFIED
#      status, lost EmojiCompat disclosure, missing run-ID citation,
#      restored false permission claim, vanished corrected string,
#      missing rent-ledger entry.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../../.." && pwd)"
verifier="$script_dir/../verify-floris-audit.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

failures=0
check() { # check <description> <expect-pass|expect-fail> <out> <rc> <must-contain>
  local desc="$1" want="$2" out="$3" rc="$4" needle="$5"
  if [ "$want" = pass ] && [ "$rc" -ne 0 ]; then
    echo "FAIL: $desc — expected acceptance, got rc=$rc" >&2
    failures=$((failures + 1)); return
  fi
  if [ "$want" = fail ] && [ "$rc" -eq 0 ]; then
    echo "FAIL: $desc — expected rejection, verifier accepted the fixture" >&2
    failures=$((failures + 1)); return
  fi
  if [ "$want" = fail ] && ! grep -qF "$needle" <<<"$out"; then
    echo "FAIL: $desc — rejection lacked '$needle'; got: $out" >&2
    failures=$((failures + 1)); return
  fi
  echo "PASS: $desc"
}

run_verifier() { # sets RV_OUT/RV_RC
  RV_OUT="$(AUDIT_DOC="${1:-$repo_root/docs/evidence/floris-host/privacy-and-egress-audit.md}" \
    FLORIS_ROOT="${2:-$repo_root/android/florisboard}" \
    bash "$verifier" "$repo_root" 2>&1)" && RV_RC=0 || RV_RC=$?
  return 0
}

# --- 1. Real tree accepted ---------------------------------------------------
run_verifier "$repo_root/docs/evidence/floris-host/privacy-and-egress-audit.md" \
  "$repo_root/android/florisboard"; out="$RV_OUT"; rc="$RV_RC"
check "real-tree posture accepted" pass "$out" $rc ""

# --- 2. Missing audit document ------------------------------------------------
run_verifier "$tmp/nonexistent-audit.md" \
  "$repo_root/android/florisboard"; out="$RV_OUT"; rc="$RV_RC"
check "missing audit document rejected" fail "$out" $rc \
  "missing audit document"

# --- 3. Lost QUALIFIED status ---------------------------------------------------
sed 's/Document Status: QUALIFIED/Document Status: DRAFT/' \
  "$repo_root/docs/evidence/floris-host/privacy-and-egress-audit.md" \
  > "$tmp/draft-audit.md"
run_verifier "$tmp/draft-audit.md" "$repo_root/android/florisboard"
out="$RV_OUT"; rc="$RV_RC"
check "draft-status audit rejected" fail "$out" $rc "QUALIFIED status line"

# --- 4. Lost EmojiCompat disclosure ---------------------------------------------
grep -v 'EmojiCompat' \
  "$repo_root/docs/evidence/floris-host/privacy-and-egress-audit.md" \
  > "$tmp/no-emoji-audit.md"
run_verifier "$tmp/no-emoji-audit.md" "$repo_root/android/florisboard"
out="$RV_OUT"; rc="$RV_RC"
check "lost EmojiCompat disclosure rejected" fail "$out" $rc \
  "must disclose the EmojiCompat"

# --- 5. Missing run-ID citation ---------------------------------------------------
sed 's/20260903T170746Z/REDACTED-RUN/g' \
  "$repo_root/docs/evidence/floris-host/privacy-and-egress-audit.md" \
  > "$tmp/no-runid-audit.md"
run_verifier "$tmp/no-runid-audit.md" "$repo_root/android/florisboard"
out="$RV_OUT"; rc="$RV_RC"
check "missing run-ID citation rejected" fail "$out" $rc "device-journey run ID"

# --- 6. Restored false permission claim ---------------------------------------------
floris_fixture="$tmp/floris"
mkdir -p "$floris_fixture/app/src/main/res/values" "$floris_fixture"
cp "$repo_root/android/florisboard/UPSTREAM-MODIFIED.md" "$floris_fixture/"
cp "$repo_root/android/florisboard/app/src/main/res/values/strings.xml" \
  "$floris_fixture/app/src/main/res/values/strings.xml"
sed -i.bak 's|Extension updates are always checked manually in the browser.*</string>|Since this app does not have Internet permission, updates for installed extensions must be checked manually.</string>|' \
  "$floris_fixture/app/src/main/res/values/strings.xml"
rm -f "$floris_fixture/app/src/main/res/values/strings.xml.bak"
run_verifier "$repo_root/docs/evidence/floris-host/privacy-and-egress-audit.md" \
  "$floris_fixture"; out="$RV_OUT"; rc="$RV_RC"
check "restored false permission claim rejected" fail "$out" $rc \
  "no Internet permission"

# --- 7. Vanished corrected string ------------------------------------------------------
floris_fixture2="$tmp/floris2"
cp -R "$floris_fixture" "$floris_fixture2"
grep -v 'ext__update_box__internet_permission_hint' \
  "$floris_fixture/app/src/main/res/values/strings.xml" \
  > "$floris_fixture2/app/src/main/res/values/strings.xml"
run_verifier "$repo_root/docs/evidence/floris-host/privacy-and-egress-audit.md" \
  "$floris_fixture2"; out="$RV_OUT"; rc="$RV_RC"
check "vanished update-hint string rejected" fail "$out" $rc \
  "keep the (corrected) manual-update copy"

# --- 8. Missing rent-ledger entry ---------------------------------------------------------
floris_fixture3="$tmp/floris3"
cp -R "$floris_fixture" "$floris_fixture3"
grep -v 'strings\.xml' "$floris_fixture/UPSTREAM-MODIFIED.md" \
  > "$floris_fixture3/UPSTREAM-MODIFIED.md"
run_verifier "$repo_root/docs/evidence/floris-host/privacy-and-egress-audit.md" \
  "$floris_fixture3"; out="$RV_OUT"; rc="$RV_RC"
check "missing ledger entry rejected" fail "$out" $rc \
  "rent ledger has no entry"

if [ "$failures" -ne 0 ]; then
  echo "verify-floris-audit-test: $failures failure(s)" >&2
  exit 1
fi
echo "verify-floris-audit-test: all cases passed"
