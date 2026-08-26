#!/usr/bin/env bash
# Contract test for verify-asset-rights.sh.
#
# Drives the verifier against synthetic fixture trees through test seams:
#   1. a fully mapped manifest + clean persona directory + zero rasters passes;
#   2. an unrecorded persona YAML fails with "unrecorded persona rights: <slug>";
#   3. an unauthorized raster file in a first-party module fails with "unauthorized raster asset: <path>";
#   4. a manifest missing the OFL 1.1 notice fails with "missing font license notice";
#   5. tool failure (grep exit 2) produces exit 2 and deterministic error output.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/../verify-asset-rights.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

make_fixture() {
  local root="$1"
  local manifest="$root/docs/design/ASSET-RIGHTS.md"
  local personas="$root/personas"
  local fp_ui="$root/android/personaspeak-ui/src/main"
  
  mkdir -p "$root/docs/design" "$personas" "$fp_ui"
  
  # Persona YAMLs
  printf 'name: Jeeves\n' > "$personas/jeeves.yaml"
  printf 'name: Sir Humphrey\n' > "$personas/sir-humphrey.yaml"
  
  # Valid manifest
  cat > "$manifest" <<'EOF'
# ASSET RIGHTS

| Persona Slug | Identity | Shipped Representation | Clearance Status |
|---|---|---|---|
| `jeeves` | Reginald Jeeves | Unicode Emoji `🎩` | **CLEARED** |
| `sir-humphrey` | Sir Humphrey | Unicode Emoji `🏛️` | **CLEARED** |

## Fonts
SIL OPEN FONT LICENSE Version 1.1

## Icons
Apache License, Version 2.0
EOF
}

# 1. Positive control: valid manifest and clean modules
good_root="$tmp/good"
make_fixture "$good_root"
out_good="$tmp/out-good.txt"

if ! ASSET_MANIFEST_PATH="$good_root/docs/design/ASSET-RIGHTS.md" \
     PERSONAS_DIR_PATH="$good_root/personas" \
     FIRST_PARTY_SCAN_PATH="$good_root/android" \
     bash "$verifier" "$good_root/android" > "$out_good" 2>&1; then
  echo "FAIL: valid asset rights fixture was rejected" >&2
  cat "$out_good" >&2
  exit 1
fi

# 2. Negative control: unrecorded persona
bad_persona_root="$tmp/bad_persona"
make_fixture "$bad_persona_root"
printf 'name: Mystery\n' > "$bad_persona_root/personas/mystery.yaml"
out_bad_persona="$tmp/out-bad-persona.txt"

if ASSET_MANIFEST_PATH="$bad_persona_root/docs/design/ASSET-RIGHTS.md" \
   PERSONAS_DIR_PATH="$bad_persona_root/personas" \
   FIRST_PARTY_SCAN_PATH="$bad_persona_root/android" \
   bash "$verifier" "$bad_persona_root/android" > "$out_bad_persona" 2>&1; then
  echo "FAIL: unrecorded persona was accepted" >&2
  cat "$out_bad_persona" >&2
  exit 1
fi
if ! grep -q "^unrecorded persona rights: mystery$" "$out_bad_persona"; then
  echo "FAIL: expected 'unrecorded persona rights: mystery' not found in output" >&2
  cat "$out_bad_persona" >&2
  exit 1
fi

# 3. Negative control: unauthorized raster asset in first-party module
bad_raster_root="$tmp/bad_raster"
make_fixture "$bad_raster_root"
mkdir -p "$bad_raster_root/android/personaspeak-ui/src/main/res/drawable"
printf 'fake-png-bytes' > "$bad_raster_root/android/personaspeak-ui/src/main/res/drawable/unauthorized.png"
out_bad_raster="$tmp/out-bad-raster.txt"

if ASSET_MANIFEST_PATH="$bad_raster_root/docs/design/ASSET-RIGHTS.md" \
   PERSONAS_DIR_PATH="$bad_raster_root/personas" \
   FIRST_PARTY_SCAN_PATH="$bad_raster_root/android" \
   bash "$verifier" "$bad_raster_root/android" > "$out_bad_raster" 2>&1; then
  echo "FAIL: unauthorized raster asset was accepted" >&2
  cat "$out_bad_raster" >&2
  exit 1
fi
if ! grep -q "unauthorized raster asset: personaspeak-ui/src/main/res/drawable/unauthorized.png" "$out_bad_raster"; then
  echo "FAIL: expected 'unauthorized raster asset' error missing" >&2
  cat "$out_bad_raster" >&2
  exit 1
fi

# 4. Negative control: missing font license notice in manifest
bad_license_root="$tmp/bad_license"
make_fixture "$bad_license_root"
grep -v "SIL OPEN FONT LICENSE Version 1.1" "$bad_license_root/docs/design/ASSET-RIGHTS.md" > "$bad_license_root/docs/design/ASSET-RIGHTS.md.tmp"
mv "$bad_license_root/docs/design/ASSET-RIGHTS.md.tmp" "$bad_license_root/docs/design/ASSET-RIGHTS.md"
out_bad_license="$tmp/out-bad-license.txt"

if ASSET_MANIFEST_PATH="$bad_license_root/docs/design/ASSET-RIGHTS.md" \
   PERSONAS_DIR_PATH="$bad_license_root/personas" \
   FIRST_PARTY_SCAN_PATH="$bad_license_root/android" \
   bash "$verifier" "$bad_license_root/android" > "$out_bad_license" 2>&1; then
  echo "FAIL: manifest missing font license was accepted" >&2
  cat "$out_bad_license" >&2
  exit 1
fi
if ! grep -q "missing font license notice" "$out_bad_license"; then
  echo "FAIL: expected 'missing font license notice' error missing" >&2
  cat "$out_bad_license" >&2
  exit 1
fi

# 5. Tool failure control: grep binary error aborts cleanly with exit 2
toolfail_root="$tmp/toolfail"
make_fixture "$toolfail_root"
fakebin="$tmp/fakebin"
mkdir -p "$fakebin"
cat > "$fakebin/grep" <<'EOF'
#!/bin/sh
exit 2
EOF
chmod +x "$fakebin/grep"
out_toolfail="$tmp/out-toolfail.txt"

set +e
VERIFY_GREP="$fakebin/grep" \
ASSET_MANIFEST_PATH="$toolfail_root/docs/design/ASSET-RIGHTS.md" \
PERSONAS_DIR_PATH="$toolfail_root/personas" \
FIRST_PARTY_SCAN_PATH="$toolfail_root/android" \
bash "$verifier" "$toolfail_root/android" > "$out_toolfail" 2>&1
toolfail_rc=$?
set -e

if [ "$toolfail_rc" -ne 2 ]; then
  echo "FAIL: tool failure produced exit $toolfail_rc instead of 2" >&2
  cat "$out_toolfail" >&2
  exit 1
fi
if ! grep -q "verify-asset-rights: grep tool failure" "$out_toolfail"; then
  echo "FAIL: deterministic grep tool failure message missing" >&2
  cat "$out_toolfail" >&2
  exit 1
fi

echo "PASS: verify-asset-rights contract (all 5 control suites passed)"
