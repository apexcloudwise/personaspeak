#!/usr/bin/env bash
# Contract test for verify-upstream-ledger.sh.
#
# Drives the verifier with fixture pristine/target trees through its documented
# UPSTREAM_PRISTINE_DIR / UPSTREAM_TARGET_DIR / UPSTREAM_LEDGER test seam:
#   1. identical trees with an entry-free ledger pass;
#   2. mutating one pristine-tracked file without a ledger entry must fail
#      with "unledgered upstream modification: <path>";
#   3. adding exactly one entry for that path must pass;
#   4. a duplicate entry for the same path must fail;
#   5. a ledger entry naming an unmodified path must fail;
#   6. deleting a pristine-tracked file requires exactly one entry;
#   7. target-only additions are allowed only under the PersonaSpeak package,
#      the provenance documents, or the verifier's explicit allowlist.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/../verify-upstream-ledger.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

pristine="$tmp/pristine"
mkdir -p "$pristine/gradle" "$pristine/ime/base"
printf 'alpha\n' > "$pristine/gradle/android_general.gradle"
printf 'beta\n' > "$pristine/ime/base/build.gradle"
printf 'gamma\n' > "$pristine/LICENSE"

fresh_target() {
  local target="$1"
  rm -rf "$target"
  cp -R "$pristine" "$target"
  cat > "$target/UPSTREAM-MODIFIED.md" <<'EOF'
# AnySoftKeyboard upstream-modification ledger

Use one entry per file:

```text
- <path-from-android/keyboard> — <reason for the current modification>
```

## Files modified against pristine
EOF
}

run_verifier() {
  # $1 = target dir; stdout/stderr file as $2
  UPSTREAM_PRISTINE_DIR="$pristine" \
  UPSTREAM_TARGET_DIR="$1" \
  UPSTREAM_LEDGER="$1/UPSTREAM-MODIFIED.md" \
    bash "$verifier" "$tmp/android-root-unused" > "$2" 2>&1
}

target="$tmp/target"

# 1. Identical trees, no entries: pass.
fresh_target "$target"
if ! run_verifier "$target" "$tmp/out1.txt"; then
  echo "FAIL: identical trees rejected" >&2
  cat "$tmp/out1.txt" >&2
  exit 1
fi

# 2. Unledgered modification: fail with exact message.
fresh_target "$target"
printf 'alpha CHANGED\n' > "$target/gradle/android_general.gradle"
if run_verifier "$target" "$tmp/out2.txt"; then
  echo "FAIL: unledgered modification accepted" >&2
  cat "$tmp/out2.txt" >&2
  exit 1
fi
if ! grep -q "^unledgered upstream modification: gradle/android_general.gradle$" "$tmp/out2.txt"; then
  echo "FAIL: unledgered-modification message missing" >&2
  cat "$tmp/out2.txt" >&2
  exit 1
fi

# 3. Exactly one entry for the modified path: pass.
cat >> "$target/UPSTREAM-MODIFIED.md" <<'EOF'

- gradle/android_general.gradle — fixture change for contract test.
EOF
if ! run_verifier "$target" "$tmp/out3.txt"; then
  echo "FAIL: exactly-ledgered modification rejected" >&2
  cat "$tmp/out3.txt" >&2
  exit 1
fi

# 4. Duplicate entry: fail.
cat >> "$target/UPSTREAM-MODIFIED.md" <<'EOF'
- gradle/android_general.gradle — duplicate entry.
EOF
if run_verifier "$target" "$tmp/out4.txt"; then
  echo "FAIL: duplicate ledger entry accepted" >&2
  cat "$tmp/out4.txt" >&2
  exit 1
fi
if ! grep -q "^duplicate ledger entry: gradle/android_general.gradle$" "$tmp/out4.txt"; then
  echo "FAIL: duplicate-entry message missing" >&2
  cat "$tmp/out4.txt" >&2
  exit 1
fi

# 5. Stale entry for an unmodified path: fail.
fresh_target "$target"
cat >> "$target/UPSTREAM-MODIFIED.md" <<'EOF'

- ime/base/build.gradle — stale entry, file is pristine.
EOF
if run_verifier "$target" "$tmp/out5.txt"; then
  echo "FAIL: stale ledger entry accepted" >&2
  cat "$tmp/out5.txt" >&2
  exit 1
fi
if ! grep -q "^stale ledger entry: ime/base/build.gradle$" "$tmp/out5.txt"; then
  echo "FAIL: stale-entry message missing" >&2
  cat "$tmp/out5.txt" >&2
  exit 1
fi

# 6. Deletion requires exactly one entry.
fresh_target "$target"
rm "$target/ime/base/build.gradle"
if run_verifier "$target" "$tmp/out6a.txt"; then
  echo "FAIL: unledgered deletion accepted" >&2
  cat "$tmp/out6a.txt" >&2
  exit 1
fi
if ! grep -q "^unledgered upstream deletion: ime/base/build.gradle$" "$tmp/out6a.txt"; then
  echo "FAIL: unledgered-deletion message missing" >&2
  cat "$tmp/out6a.txt" >&2
  exit 1
fi
cat >> "$target/UPSTREAM-MODIFIED.md" <<'EOF'

- ime/base/build.gradle — fixture deletion for contract test.
EOF
if ! run_verifier "$target" "$tmp/out6b.txt"; then
  echo "FAIL: ledgered deletion rejected" >&2
  cat "$tmp/out6b.txt" >&2
  exit 1
fi

# 7. Additions: PersonaSpeak package and provenance docs allowed; others fail.
fresh_target "$target"
mkdir -p "$target/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak"
printf 'ok\n' > "$target/ime/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/Ok.kt"
printf 'notes\n' > "$target/DICTIONARY-LICENSES.md"
if ! run_verifier "$target" "$tmp/out7a.txt"; then
  echo "FAIL: allowed PersonaSpeak/provenance additions rejected" >&2
  cat "$tmp/out7a.txt" >&2
  exit 1
fi
printf 'rogue\n' > "$target/ime/base/Rogue.java"
if run_verifier "$target" "$tmp/out7b.txt"; then
  echo "FAIL: non-allowlisted addition accepted" >&2
  cat "$tmp/out7b.txt" >&2
  exit 1
fi
if ! grep -q "^unexpected addition outside PersonaSpeak allowlist: ime/base/Rogue.java$" "$tmp/out7b.txt"; then
  echo "FAIL: unexpected-addition message missing" >&2
  cat "$tmp/out7b.txt" >&2
  exit 1
fi

echo "PASS: pristine ledger exact; unledgered rent rejected"
