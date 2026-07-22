#!/usr/bin/env bash
# Verify the dictionary input-to-notice mapping (fail closed).
#
# usage: verify-dictionary-licenses.sh <android-root>
#
# Replicates MakeDictionaryPlugin's input selection for the English language
# pack and requires DICTIONARY-LICENSES.md to map each selected build input to
# exactly one manifest row:
#   - dictionary/*.combined*                 (AOSP combined word lists)
#   - dictionary/prebuilt/*.xml              (upstream-authored prebuilt XMLs)
#   - dictionary/inputs/* only when the pack's `dictionaryTextInputsEnabled`
#     Gradle switch is not set to false (the plugin's default is true)
# Failure modes (exit 1):
#   "unlicensed dictionary input: <path>"  selected input without a row
#   "duplicate manifest row: <path>"       more than one row for an input
#   "stale manifest row: <path>"           row for a file that is not selected
#
# Test seam: DICT_PACK_DIR overrides the pack directory and DICT_MANIFEST the
# manifest path. Row paths are always relative to <android-root>/keyboard.
#
# Exit codes: 0 pass; 1 licensing violation; 2 usage or tool failure.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: verify-dictionary-licenses.sh <android-root>" >&2
  exit 2
fi
root="$1"
if [ ! -d "$root" ]; then
  echo "verify-dictionary-licenses: not a directory: $root" >&2
  exit 2
fi

keyboard_root="$root/keyboard"
pack_dir="${DICT_PACK_DIR:-$keyboard_root/addons/languages/english/pack}"
manifest="${DICT_MANIFEST:-$keyboard_root/DICTIONARY-LICENSES.md}"

if [ ! -d "$pack_dir" ]; then
  echo "verify-dictionary-licenses: missing pack dir: $pack_dir" >&2
  exit 2
fi
if [ ! -f "$manifest" ]; then
  echo "verify-dictionary-licenses: missing manifest: $manifest" >&2
  exit 2
fi
build_file="$pack_dir/build.gradle"
if [ ! -f "$build_file" ]; then
  echo "verify-dictionary-licenses: missing pack build file: $build_file" >&2
  exit 2
fi

# Pack path prefix relative to keyboard/ (manifest rows use this prefix).
case "$pack_dir" in
  "$keyboard_root"/*) pack_prefix="${pack_dir#"$keyboard_root"/}" ;;
  *)
    echo "verify-dictionary-licenses: pack dir not under $keyboard_root" >&2
    exit 2
    ;;
esac

# --- Read the Gradle switch (plugin default: true) ------------------------
inputs_enabled=1
if grep -Eq 'dictionaryTextInputsEnabled[[:space:]]*=[[:space:]]*false' "$build_file"; then
  inputs_enabled=0
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

# --- Enumerate the inputs MakeDictionaryPlugin would select ---------------
selected="$workdir/selected.txt"
: > "$selected"
if [ -d "$pack_dir/dictionary" ]; then
  find "$pack_dir/dictionary" -maxdepth 1 -type f -name '*.combined*' \
    | sed "s|^$pack_dir/||" >> "$selected"
  if [ -d "$pack_dir/dictionary/prebuilt" ]; then
    find "$pack_dir/dictionary/prebuilt" -maxdepth 1 -type f -name '*.xml' \
      | sed "s|^$pack_dir/||" >> "$selected"
  fi
  if [ "$inputs_enabled" -eq 1 ] && [ -d "$pack_dir/dictionary/inputs" ]; then
    find "$pack_dir/dictionary/inputs" -maxdepth 1 -type f \
      | sed "s|^$pack_dir/||" >> "$selected"
  fi
fi
LC_ALL=C sort -o "$selected" "$selected"

# --- Parse manifest rows: "<path> | <source> | <license>" -----------------
rows="$workdir/rows.txt"
sed -n 's/^\([^ |][^|]*\) | [^|][^|]* | [^|][^|]*$/\1/p' "$manifest" \
  | sed 's/[[:space:]]*$//' > "$rows"

# Rows scoped to this pack, expressed relative to the pack dir.
pack_rows="$workdir/pack-rows.txt"
sed -n "s|^$pack_prefix/||p" "$rows" > "$pack_rows"

status=0

# 1. Every selected input has exactly one row.
while IFS= read -r input; do
  [ -z "$input" ] && continue
  count="$(grep -cxF -- "$input" "$pack_rows" || true)"
  if [ "$count" -eq 0 ]; then
    echo "unlicensed dictionary input: $input"
    status=1
  elif [ "$count" -gt 1 ]; then
    echo "duplicate manifest row: $input"
    status=1
  fi
done < "$selected"

# 2. Every row for this pack names a selected input.
while IFS= read -r row; do
  [ -z "$row" ] && continue
  if ! grep -qxF -- "$row" "$selected"; then
    echo "stale manifest row: $row"
    status=1
  fi
done < <(LC_ALL=C sort -u "$pack_rows")

if [ "$status" -eq 0 ]; then
  echo "dictionary licenses verified: every selected input has exactly one notice"
fi
exit "$status"
