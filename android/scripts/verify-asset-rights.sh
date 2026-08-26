#!/usr/bin/env bash
# Verify PersonaSpeak asset rights, font license notices, and persona portrait clearance.
#
# usage: verify-asset-rights.sh [<android-root>]
#
# Enforces Milestone 6 asset integrity rules:
#   1. Every persona in personas/*.yaml has an approved clearance row in ASSET-RIGHTS.md
#   2. Zero unapproved raster assets (*.png, *.jpg, *.jpeg, *.webp) exist in first-party modules
#   3. Required OFL-1.1 and Apache-2.0 license texts are present and intact in ASSET-RIGHTS.md
#
# Failure modes (exit 1):
#   "unrecorded persona rights: <slug>"
#   "unauthorized raster asset: <path>"
#   "missing font license notice in <manifest>"
#
# Test seam overrides:
#   ASSET_MANIFEST_PATH    overrides the path to ASSET-RIGHTS.md
#   PERSONAS_DIR_PATH      overrides the path to personas directory
#   FIRST_PARTY_SCAN_PATH  overrides the path for first-party modules
#   VERIFY_GREP            overrides the grep binary for fault injection
#
# Exit codes: 0 pass; 1 verification violation; 2 usage or tool failure.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ $# -ge 2 ]; then
  echo "usage: verify-asset-rights.sh [<android-root>]" >&2
  exit 2
fi

if [ $# -eq 1 ]; then
  root="$1"
else
  root="$(cd "$script_dir/.." && pwd)"
fi

if [ ! -d "$root" ]; then
  echo "verify-asset-rights: not a directory: $root" >&2
  exit 2
fi
root="$(cd "$root" && pwd)"

repo_root="$(cd "$root/.." && pwd)"

manifest="${ASSET_MANIFEST_PATH:-$repo_root/docs/design/ASSET-RIGHTS.md}"
personas_dir="${PERSONAS_DIR_PATH:-$repo_root/personas}"
first_party_dir="${FIRST_PARTY_SCAN_PATH:-$root}"

if [ ! -f "$manifest" ]; then
  echo "verify-asset-rights: missing manifest: $manifest" >&2
  exit 2
fi

if [ ! -d "$personas_dir" ]; then
  echo "verify-asset-rights: missing personas directory: $personas_dir" >&2
  exit 2
fi

# --- grep seam: robust tool error detection
GREP_BIN="${VERIFY_GREP:-grep}"

grep_probe() {
  local rc
  set +e
  "$GREP_BIN" "$@"
  rc=$?
  set -e
  case "$rc" in
    0) return 0 ;;
    1) return 1 ;;
    *)
      echo "verify-asset-rights: grep tool failure (exit $rc)" >&2
      exit 2
      ;;
  esac
}

status=0

# --- 1. Verify every bundled persona has recorded clearance in ASSET-RIGHTS.md
for yaml_file in "$personas_dir"/*.yaml; do
  [ -e "$yaml_file" ] || continue
  slug="$(basename "$yaml_file" .yaml)"
  
  if ! grep_probe -qE "\|\s*\`?$slug\`?\s*\|.*\|\s*\*\*CLEARED\*\*" "$manifest"; then
    echo "unrecorded persona rights: $slug"
    status=1
  fi
done

# --- 2. Verify zero unauthorized raster assets in first-party packages
modules=("personaspeak-ui" "personaspeak-providers" "personaspeak-data" "core-personas" "core-providers")

for mod in "${modules[@]}"; do
  target_dir="$first_party_dir/$mod"
  if [ -d "$target_dir" ]; then
    # find any raster files in src/
    while IFS= read -r raster_file; do
      [ -z "$raster_file" ] && continue
      rel_path="${raster_file#"$root"/}"
      echo "unauthorized raster asset: $rel_path"
      status=1
    done < <(find "$target_dir/src" -type f \( -name "*.png" -o -name "*.jpg" -o -name "*.jpeg" -o -name "*.webp" -o -name "*.gif" \) 2>/dev/null || true)
  fi
done

# --- 3. Verify required font and glyph license notices in ASSET-RIGHTS.md
if ! grep_probe -q "SIL OPEN FONT LICENSE Version 1.1" "$manifest"; then
  echo "missing font license notice in $manifest: SIL Open Font License 1.1"
  status=1
fi

if ! grep_probe -q "Apache License, Version 2.0" "$manifest"; then
  echo "missing license notice in $manifest: Apache License 2.0"
  status=1
fi

if [ "$status" -eq 0 ]; then
  echo "asset rights verified: all personas cleared, typography licensed, zero unauthorized rasters"
fi

exit "$status"
