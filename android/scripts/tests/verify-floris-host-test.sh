#!/usr/bin/env bash
# Contract test for verify-floris-host.sh.
#
# Drives the verifier against fixture trees through its documented
# FLORIS_ROOT / UNIFIED_SETTINGS test seams:
#   1. a contract-clean fixture must be accepted (exit 0);
#   2. each ADR-0010 invariant, broken one at a time, must be rejected
#      with its named finding: provenance pin drift, stale rent ledger
#      entry, stray PersonaSpeak file outside the recorded locations,
#      missing first-party module mapping, florisboard leakage into the
#      unified root, lost pristine applicationId default, missing ledger.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/../verify-floris-host.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

failures=0
check() { # check <description> <expect-pass|expect-fail> <output> <rc> <must-contain>
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

# build_fixture <dir>: a contract-clean minimal floris tree + unified root.
build_fixture() {
  local dir="$1"
  rm -rf "$dir"
  mkdir -p "$dir/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/floris" \
           "$dir/app/src/main/res/xml"

  cat > "$dir/UPSTREAM.md" <<'EOF'
# FlorisBoard vendored snapshot provenance
- Upstream: https://github.com/florisboard/florisboard
- Tag: `v0.5.2`
- Commit: `2e82060251897226c0739b9f52d1d051b02305fb`
- Upstream license: Apache-2.0
- Vendored: 2026-09-01
EOF

  cat > "$dir/UPSTREAM-MODIFIED.md" <<'EOF'
# FlorisBoard upstream-modification ledger

Format:
```text
- <path-from-android/florisboard> — <reason for the current modification>
```

## Files modified against pristine

- settings.gradle.kts — include mapping (replay recorded).
EOF
  touch "$dir/settings.gradle.kts"

  cat > "$dir/settings.gradle.kts" <<'EOF'
include(":app")
EOF
  local module
  for module in core-personas core-providers personaspeak-ui \
                personaspeak-data personaspeak-providers personaspeak-ime; do
    echo "personaspeakProject(\":$module\", \"$module\")" >> "$dir/settings.gradle.kts"
  done

  cat > "$dir/build.gradle.kts" <<'EOF'
allprojects {
  if (name.startsWith("personaspeak")) buildDir = file("build/personaspeak-build/\$name")
}
EOF

  cat > "$dir/app/build.gradle.kts" <<'EOF'
applicationId = (findProperty("personaspeakFlorisAppId") as String?)
    ?: "dev.patrickgold.florisboard"
EOF

  echo "package biz.pixelperfectstudios.personaspeak.floris" \
    > "$dir/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/floris/Host.kt"
  touch "$dir/app/src/main/res/xml/personaspeak_host_data_extraction_rules.xml"
}

unified="$tmp/settings.gradle.kts"
echo 'include(":keyboard:ime:app")' > "$unified"

run_case() { # run_case <floris-dir> -> stdout+stderr; exits with verifier rc
  FLORIS_ROOT="$1" UNIFIED_SETTINGS="$unified" \
    bash "$verifier" "$tmp" 2>&1
}

# 1. Contract-clean fixture accepted.
good="$tmp/good"
build_fixture "$good"
rc=0; out="$(run_case "$good")" || rc=$?
check "contract-clean floris tree accepted" pass "$out" "$rc" ""

# 2. Provenance pin drift.
pin="$tmp/pin"
build_fixture "$pin"
sed -i.bak 's/- Tag: `v0\.5\.2`/- Tag: `v0.6.0`/' "$pin/UPSTREAM.md"
rc=0; out="$(run_case "$pin")" || rc=$?
check "provenance tag drift rejected" fail "$out" "$rc" "does not pin tag v0.5.2"

# 3. Stale ledger entry.
stale="$tmp/stale"
build_fixture "$stale"
echo '- vanished/file.kt — removed upstream.' >> "$stale/UPSTREAM-MODIFIED.md"
rc=0; out="$(run_case "$stale")" || rc=$?
check "stale ledger entry rejected" fail "$out" "$rc" "stale ledger entry: vanished/file.kt"

# 4. Ledger missing.
noledger="$tmp/noledger"
build_fixture "$noledger"
rm "$noledger/UPSTREAM-MODIFIED.md"
rc=0; out="$(run_case "$noledger")" || rc=$?
check "missing ledger rejected" fail "$out" "$rc" "missing file: "

# 5. Stray PersonaSpeak file outside the recorded locations.
stray="$tmp/stray"
build_fixture "$stray"
mkdir -p "$stray/lib/kotlin/dev/patrickgold/florisboard/personaspeak"
echo "package dev.patrickgold.florisboard.personaspeak" \
  > "$stray/lib/kotlin/dev/patrickgold/florisboard/personaspeak/Leak.kt"
rc=0; out="$(run_case "$stray")" || rc=$?
check "stray personaspeak file rejected" fail "$out" "$rc" \
  "PersonaSpeak files outside the recorded first-party locations"

# 6. Missing first-party module mapping.
nomap="$tmp/nomap"
build_fixture "$nomap"
sed -i.bak '/personaspeakProject(":personaspeak-ime"/d' "$nomap/settings.gradle.kts"
rc=0; out="$(run_case "$nomap")" || rc=$?
check "missing module mapping rejected" fail "$out" "$rc" \
  "does not map ../personaspeak-ime"

# 7. Build-dir redirection lost.
nodivert="$tmp/nodivert"
build_fixture "$nodivert"
sed -i.bak 's/personaspeak-build/elsewhere/' "$nodivert/build.gradle.kts"
rc=0; out="$(run_case "$nodivert")" || rc=$?
check "build-dir redirection loss rejected" fail "$out" "$rc" \
  "does not redirect first-party build dirs"

# 8. florisboard leakage into the unified root.
leak="$tmp/leak"
build_fixture "$leak"
echo 'include(":florisboard:app")' > "$unified"
rc=0; out="$(run_case "$leak")" || rc=$?
check "unified-root leakage rejected" fail "$out" "$rc" \
  "unified root settings.gradle includes a florisboard project"
echo 'include(":keyboard:ime:app")' > "$unified"

# 9. Lost pristine identity default.
identity="$tmp/identity"
build_fixture "$identity"
sed -i.bak 's/dev\.patrickgold\.florisboard/com.example.other/' "$identity/app/build.gradle.kts"
rc=0; out="$(run_case "$identity")" || rc=$?
check "lost pristine applicationId default rejected" fail "$out" "$rc" \
  "pristine upstream applicationId default"

if [ "$failures" -ne 0 ]; then
  echo "verify-floris-host-test: $failures case(s) failed" >&2
  exit 1
fi
echo "verify-floris-host-test: all contract cases passed"
