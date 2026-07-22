#!/usr/bin/env bash
# Contract test for verify-dictionary-licenses.sh.
#
# Drives the verifier against copied fixture pack trees through its documented
# DICT_PACK_DIR / DICT_MANIFEST test seam:
#   1. a pack whose selected inputs (AOSP combined + prebuilt XMLs, raw text
#      inputs disabled) are each mapped by exactly one manifest row passes;
#   2. enabling raw text inputs and adding dictionary/inputs/unmapped.txt must
#      fail with "unlicensed dictionary input: dictionary/inputs/unmapped.txt";
#   3. a duplicate manifest row for a selected input must fail;
#   4. a manifest row naming a file that is not a selected input must fail.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/../verify-dictionary-licenses.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# --- Fixture: android root with one language pack -------------------------
make_fixture() {
  local root="$1"
  local pack="$root/keyboard/addons/languages/english/pack"
  mkdir -p "$pack/dictionary/prebuilt" "$pack/dictionary/inputs"
  printf 'fake-gz\n' > "$pack/dictionary/en_wordlist.combined.gz"
  printf '<wordlist/>\n' > "$pack/dictionary/prebuilt/additionals.xml"
  printf '<wordlist/>\n' > "$pack/dictionary/prebuilt/websites.xml"
  printf 'licensed corpus line\n' > "$pack/dictionary/inputs/mapped.txt"
  cat > "$pack/build.gradle" <<'EOF'
ext.status_icon_text = "en"
ext.dictionaryTextInputsEnabled = false
apply from: "${rootProject.ext.askSourceRoot}/addons/gradle/language_pack_lib.gradle"
EOF
  cat > "$root/keyboard/DICTIONARY-LICENSES.md" <<'EOF'
# Dictionary source licenses

addons/languages/english/pack/dictionary/en_wordlist.combined.gz | AOSP LatinIME | Apache-2.0
addons/languages/english/pack/dictionary/prebuilt/additionals.xml | AnySoftKeyboard | Apache-2.0
addons/languages/english/pack/dictionary/prebuilt/websites.xml | AnySoftKeyboard | Apache-2.0
EOF
}

# 1. Positive control: switch off, all selected inputs mapped exactly once.
good_root="$tmp/good/android"
make_fixture "$good_root"
out_good="$tmp/out-good.txt"
if ! bash "$verifier" "$good_root" > "$out_good" 2>&1; then
  echo "FAIL: fully mapped pack with disabled raw inputs was rejected" >&2
  cat "$out_good" >&2
  exit 1
fi

# 2. Negative control: raw inputs enabled, one input file unmapped.
bad_root="$tmp/bad/android"
make_fixture "$bad_root"
bad_pack="$bad_root/keyboard/addons/languages/english/pack"
# POSIX-portable line removal (BSD and GNU sed disagree on in-place flags).
grep -v 'dictionaryTextInputsEnabled' "$bad_pack/build.gradle" \
  > "$bad_pack/build.gradle.tmp"
mv "$bad_pack/build.gradle.tmp" "$bad_pack/build.gradle"
printf 'unlicensed corpus line\n' > "$bad_pack/dictionary/inputs/unmapped.txt"
# the previously mapped input file also needs a row now that inputs are active
cat >> "$bad_root/keyboard/DICTIONARY-LICENSES.md" <<'EOF'
addons/languages/english/pack/dictionary/inputs/mapped.txt | Fixture corpus | Apache-2.0
EOF
out_bad="$tmp/out-bad.txt"
if bash "$verifier" "$bad_root" > "$out_bad" 2>&1; then
  echo "FAIL: unmapped raw input was accepted" >&2
  cat "$out_bad" >&2
  exit 1
fi
if ! grep -q "^unlicensed dictionary input: dictionary/inputs/unmapped.txt$" "$out_bad"; then
  echo "FAIL: rejection message missing for dictionary/inputs/unmapped.txt" >&2
  cat "$out_bad" >&2
  exit 1
fi

# 3. Negative control: duplicate manifest row for a selected input.
dup_root="$tmp/dup/android"
make_fixture "$dup_root"
cat >> "$dup_root/keyboard/DICTIONARY-LICENSES.md" <<'EOF'
addons/languages/english/pack/dictionary/prebuilt/websites.xml | AnySoftKeyboard | Apache-2.0
EOF
out_dup="$tmp/out-dup.txt"
if bash "$verifier" "$dup_root" > "$out_dup" 2>&1; then
  echo "FAIL: duplicate manifest row was accepted" >&2
  cat "$out_dup" >&2
  exit 1
fi
if ! grep -q "^duplicate manifest row: dictionary/prebuilt/websites.xml$" "$out_dup"; then
  echo "FAIL: duplicate-row message missing" >&2
  cat "$out_dup" >&2
  exit 1
fi

# 4. Negative control: manifest row for a file that is not a selected input.
stale_root="$tmp/stale/android"
make_fixture "$stale_root"
cat >> "$stale_root/keyboard/DICTIONARY-LICENSES.md" <<'EOF'
addons/languages/english/pack/dictionary/inputs/mapped.txt | Fixture corpus | Apache-2.0
EOF
out_stale="$tmp/out-stale.txt"
if bash "$verifier" "$stale_root" > "$out_stale" 2>&1; then
  echo "FAIL: stale manifest row was accepted" >&2
  cat "$out_stale" >&2
  exit 1
fi
if ! grep -q "^stale manifest row: dictionary/inputs/mapped.txt$" "$out_stale"; then
  echo "FAIL: stale-row message missing" >&2
  cat "$out_stale" >&2
  exit 1
fi

# 5. Tool-failure control: a grep that exits 2 must abort the verifier with
#    exit 2 and a deterministic tool-error message, never a pass or a
#    violation verdict.
fakebin="$tmp/fakebin"
mkdir -p "$fakebin"
cat > "$fakebin/grep" <<'EOF'
#!/bin/sh
exit 2
EOF
chmod +x "$fakebin/grep"
toolfail_root="$tmp/toolfail/android"
make_fixture "$toolfail_root"
out_toolfail="$tmp/out-toolfail.txt"
set +e
VERIFY_GREP="$fakebin/grep" bash "$verifier" "$toolfail_root" \
  > "$out_toolfail" 2>&1
toolfail_rc=$?
set -e
if [ "$toolfail_rc" -ne 2 ]; then
  echo "FAIL: grep tool failure produced exit $toolfail_rc instead of 2" >&2
  cat "$out_toolfail" >&2
  exit 1
fi
if ! grep -q "verify-dictionary-licenses: grep tool failure" "$out_toolfail"; then
  echo "FAIL: deterministic grep tool-failure message missing" >&2
  cat "$out_toolfail" >&2
  exit 1
fi

echo "PASS: dictionary license mapping exact; unlicensed input rejected"
