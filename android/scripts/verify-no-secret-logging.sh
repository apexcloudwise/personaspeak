#!/usr/bin/env bash
# Verify that no secret, key, credential, or token logging exists in :personaspeak-providers.
#
# usage: verify-no-secret-logging.sh [android-root]
#
# Exit codes: 0 pass; 1 violation found; 2 usage or tool failure (including missing source directory).

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
default_root="$(cd "$script_dir/.." && pwd)"
root="${1:-$default_root}"

if [ ! -d "$root" ]; then
  echo "verify-no-secret-logging: not a directory: $root" >&2
  exit 2
fi

target_dir="$root/personaspeak-providers/src/main/kotlin"
if [ ! -d "$target_dir" ]; then
  echo "verify-no-secret-logging: required source directory missing: $target_dir" >&2
  exit 2
fi

# Forbidden keywords in any logging statement (case-insensitive)
pattern='(?i)(Log\.[vdiew]|println).*(secret|key|x-api-key|bearer|authorization|token|credential|password)'

findings=0
while IFS= read -r file; do
  [ -z "$file" ] && continue
  if grep -Eiq 'Log\.[vdiew]|println' "$file"; then
    if grep -Ei 'secret|key|x-api-key|bearer|authorization|token|credential|password' "$file" > /dev/null; then
      echo "FAIL: forbidden secret logging detected in $file:" >&2
      grep -Ein 'Log\.[vdiew]|println' "$file" >&2
      findings=$((findings + 1))
    fi
  fi
done < <(find "$target_dir" -type f -name '*.kt')

if [ "$findings" -gt 0 ]; then
  echo "verify-no-secret-logging: $findings violation(s) found" >&2
  exit 1
fi

echo "PASS: no secret logging detected in :personaspeak-providers"
exit 0
