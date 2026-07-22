#!/usr/bin/env bash
# Verify the pinned pristine ASK snapshot against the upstream-rent ledger
# (fail closed).
#
# usage: verify-upstream-ledger.sh <android-root>
#
# Reconstructs the pristine upstream tree for the pinned tag/commit recorded in
# keyboard/UPSTREAM.md, compares it with the vendored tree, and requires:
#   - every changed pristine-tracked path: exactly one ledger bullet
#       (0 -> "unledgered upstream modification: <path>",
#        2+ -> "duplicate ledger entry: <path>")
#   - every deleted pristine-tracked path: exactly one ledger bullet
#       (0 -> "unledgered upstream deletion: <path>")
#   - every ledger bullet: names a changed or deleted path
#       (otherwise "stale ledger entry: <path>")
#   - target-only additions: allowed only under the PersonaSpeak package
#     namespace, the provenance documents, or the explicit allowlist below
#       (otherwise "unexpected addition outside PersonaSpeak allowlist: <path>")
#
# Live mode fetches the pinned commit into a git cache and extracts it with
# the exact exclusions documented in UPSTREAM.md. Git's content addressing
# guarantees the extracted tree is byte-for-byte the pinned commit; a network
# or fetch failure is a hard failure (exit 2), never a pass.
#
# Test seam: UPSTREAM_PRISTINE_DIR supplies a prepared pristine tree (skipping
# the network entirely); UPSTREAM_TARGET_DIR overrides the vendored tree and
# UPSTREAM_LEDGER the ledger path. UPSTREAM_CACHE_DIR relocates the git cache.
#
# Exit codes: 0 pass; 1 ledger violation; 2 usage, tool, or network failure.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: verify-upstream-ledger.sh <android-root>" >&2
  exit 2
fi
root="$1"

target_dir="${UPSTREAM_TARGET_DIR:-$root/keyboard}"
ledger="${UPSTREAM_LEDGER:-$target_dir/UPSTREAM-MODIFIED.md}"

if [ ! -d "$target_dir" ]; then
  echo "verify-upstream-ledger: not a directory: $target_dir" >&2
  exit 2
fi
if [ ! -f "$ledger" ]; then
  echo "verify-upstream-ledger: missing ledger: $ledger" >&2
  exit 2
fi

# --- grep seam: every probe distinguishes 0 match / 1 no-match / 2+ tool
# failure. A broken tool aborts with exit 2 and a deterministic message; it
# can never read as absence or as a pass. VERIFY_GREP overrides the binary
# for contract tests.
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
      echo "verify-upstream-ledger: grep tool failure (exit $rc)" >&2
      exit 2
      ;;
  esac
}

# grep -c exits 1 when the count is 0 — a valid count, not a tool failure.
grep_count() {
  local rc out
  set +e
  out="$("$GREP_BIN" "$@")"
  rc=$?
  set -e
  case "$rc" in
    0 | 1) printf '%s\n' "${out:-0}" ;;
    *)
      echo "verify-upstream-ledger: grep tool failure (exit $rc)" >&2
      exit 2
      ;;
  esac
}

# Pinned upstream identity (must match keyboard/UPSTREAM.md).
upstream_url="https://github.com/AnySoftKeyboard/AnySoftKeyboard"
upstream_sha="8c1db51c8f23d1923d0eb05f70f1bb41d614fb6d"

# Paths that are generated, tooling state, or PersonaSpeak-owned; never
# treated as upstream content on either side of the comparison.
is_ignored() {
  case "$1" in
    build/* | */build/* | .gradle/* | */.gradle/* | .kotlin/* | */.kotlin/* | \
    outputs/* | node_modules/* | */node_modules/* | .DS_Store | */.DS_Store | \
    *.iml | .generated_pack_version | */.generated_pack_version | \
    .cxx/* | */.cxx/* | .project | .settings/* | \
    addons/languages/*/pack/src/main/res/raw/*_words_*.dict | \
    addons/languages/*/pack/src/main/res/values/*_words_dict_array.xml | \
    UPSTREAM-MODIFIED.md)
      return 0
      ;;
  esac
  return 1
}

# Target-only additions allowed without ledger entries.
is_allowed_addition() {
  case "$1" in
    */biz/pixelperfectstudios/personaspeak/* | \
    UPSTREAM.md | DICTIONARY-LICENSES.md)
      return 0
      ;;
  esac
  return 1
}

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

# --- Obtain the pristine tree ---------------------------------------------
if [ -n "${UPSTREAM_PRISTINE_DIR:-}" ]; then
  pristine_dir="$UPSTREAM_PRISTINE_DIR"
  if [ ! -d "$pristine_dir" ]; then
    echo "verify-upstream-ledger: missing pristine dir: $pristine_dir" >&2
    exit 2
  fi
else
  cache_dir="${UPSTREAM_CACHE_DIR:-$root/build/upstream-cache/ask.git}"
  if ! git -C "$cache_dir" cat-file -e "$upstream_sha^{commit}" 2>/dev/null; then
    mkdir -p "$cache_dir"
    git -C "$cache_dir" init --bare -q 2>/dev/null || true
    if ! git -C "$cache_dir" fetch --depth 1 "$upstream_url" "$upstream_sha"; then
      echo "verify-upstream-ledger: network fetch of $upstream_sha failed" >&2
      exit 2
    fi
  fi
  # Byte-for-byte: the object store must contain exactly the pinned commit.
  if ! git -C "$cache_dir" cat-file -e "$upstream_sha^{commit}"; then
    echo "verify-upstream-ledger: pinned commit missing after fetch" >&2
    exit 2
  fi
  pristine_dir="$workdir/pristine"
  mkdir -p "$pristine_dir"
  # Exclusions mirror the archive command documented in UPSTREAM.md.
  if ! git -C "$cache_dir" archive --format=tar "$upstream_sha" \
      ':(exclude).github' \
      ':(exclude).claude' \
      ':(exclude).gemini' \
      ':(exclude).jules' \
      ':(exclude).devcontainer' \
      ':(exclude)AGENTS.md' \
      ':(exclude)CLAUDE.md' \
      ':(exclude)fastlane' \
      ':(exclude)fastlane/*' \
      | tar -x -C "$pristine_dir"; then
    echo "verify-upstream-ledger: pristine archive extraction failed" >&2
    exit 2
  fi
fi

# --- Enumerate both trees --------------------------------------------------
list_tree() {
  (cd "$1" && find . -type f | sed 's|^\./||' | LC_ALL=C sort)
}
pristine_list="$workdir/pristine.txt"
target_list="$workdir/target.txt"
list_tree "$pristine_dir" > "$pristine_list"
list_tree "$target_dir" > "$target_list"

changed="$workdir/changed.txt"
deleted="$workdir/deleted.txt"
added="$workdir/added.txt"
: > "$changed"; : > "$deleted"; : > "$added"

while IFS= read -r p; do
  is_ignored "$p" && continue
  if [ -f "$target_dir/$p" ]; then
    if ! cmp -s "$pristine_dir/$p" "$target_dir/$p"; then
      echo "$p" >> "$changed"
    fi
  else
    echo "$p" >> "$deleted"
  fi
done < "$pristine_list"

while IFS= read -r p; do
  is_ignored "$p" && continue
  if [ ! -f "$pristine_dir/$p" ]; then
    echo "$p" >> "$added"
  fi
done < "$target_list"

# --- Parse ledger bullets: "- <path> — ..." -------------------------------
# Fenced code blocks (``` ... ```) document the entry format and are not
# entries themselves.
entries="$workdir/entries.txt"
awk '/^```/ { fence = !fence; next } !fence' "$ledger" \
  | sed -n 's/^- \([^ ]*\) .*$/\1/p' > "$entries"

status=0

count_entries() {
  grep_count -cxF -- "$1" "$entries"
}

while IFS= read -r p; do
  [ -z "$p" ] && continue
  n="$(count_entries "$p")"
  if [ "$n" -eq 0 ]; then
    echo "unledgered upstream modification: $p"
    status=1
  elif [ "$n" -gt 1 ]; then
    echo "duplicate ledger entry: $p"
    status=1
  fi
done < "$changed"

while IFS= read -r p; do
  [ -z "$p" ] && continue
  n="$(count_entries "$p")"
  if [ "$n" -eq 0 ]; then
    echo "unledgered upstream deletion: $p"
    status=1
  elif [ "$n" -gt 1 ]; then
    echo "duplicate ledger entry: $p"
    status=1
  fi
done < "$deleted"

while IFS= read -r e; do
  [ -z "$e" ] && continue
  if ! grep_probe -qxF -- "$e" "$changed" && ! grep_probe -qxF -- "$e" "$deleted"; then
    echo "stale ledger entry: $e"
    status=1
  fi
done < <(LC_ALL=C sort -u "$entries")

# Duplicates among entries whose path is valid are caught above; also catch
# duplicated stale entries exactly once each via sort -u.

while IFS= read -r p; do
  [ -z "$p" ] && continue
  if ! is_allowed_addition "$p"; then
    echo "unexpected addition outside PersonaSpeak allowlist: $p"
    status=1
  fi
done < "$added"

if [ "$status" -eq 0 ]; then
  echo "upstream ledger verified: pristine delta equals ledger exactly"
fi
exit "$status"
