#!/usr/bin/env bash
# Verify the structural contract of the FlorisBoard second host (ADR-0010).
#
# usage: verify-floris-host.sh <android-root>
#
# The ASK tree has verify-upstream-ledger.sh (pristine-diff reconstruction,
# scoped to keyboard/); the FlorisBoard host gets this verifier instead.
# Its job is the invariants ADR-0010 promises, checked read-only:
#
#   1. Provenance: florisboard/UPSTREAM.md records the pinned source
#      (tag v0.5.2, commit 2e82060251897226c0739b9f52d1d051b02305fb,
#      Apache-2.0) — the snapshot is attributable or the gate fails.
#   2. Rent ledger: florisboard/UPSTREAM-MODIFIED.md exists, every
#      `- <path> — <reason>` bullet names exactly one existing file under
#      the vendored tree (a ledger line for a vanished file is stale rent),
#      and no path is ledgered twice.
#   3. Separation: PersonaSpeak-owned source inside the vendored tree
#      lives only under the recorded first-party locations (the
#      biz.pixelperfectstudios.personaspeak package under app/src/main and
#      the personaspeak_host_* backup rules). A stray PersonaSpeak file
#      anywhere else in the vendored tree is unbounded rent.
#   4. Two-root isolation: the floris Gradle root maps the six first-party
#      modules from ../ (settings.gradle.kts) and redirects their build
#      directories under its own build/ (build.gradle.kts), and the
#      unified root (settings.gradle) includes no florisboard project.
#   5. Identity guard: app/build.gradle.kts reads applicationId from the
#      personaspeakFlorisAppId property with the pristine upstream default
#      (dev.patrickgold.florisboard) — a property-less build must stay
#      pristine-identical in identity.
#
# Test seams: FLORIS_ROOT overrides the vendored tree, UNIFIED_SETTINGS the
# unified root's settings.gradle, so the contract test can drive fixture
# trees without touching the real ones.
#
# Exit codes: 0 pass; 1 contract violation; 2 usage or tool failure.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: verify-floris-host.sh <android-root>" >&2
  exit 2
fi
root="$1"
if [ ! -d "$root" ]; then
  echo "verify-floris-host: not a directory: $root" >&2
  exit 2
fi
root="$(cd "$root" && pwd)"
floris="${FLORIS_ROOT:-$root/florisboard}"
unified_settings="${UNIFIED_SETTINGS:-$root/settings.gradle.kts}"

fail=0
violation() {
  echo "verify-floris-host: $1" >&2
  fail=1
}
need_file() {
  if [ ! -f "$1" ]; then
    violation "missing file: ${1#"$root"/}"
    return 1
  fi
  return 0
}

# grep seam: 0 match, 1 no-match, 2+ tool failure — never silently pass.
grep_probes() {
  local pattern="$1" file="$2" what="$3"
  local rc=0
  grep -qE "$pattern" "$file" 2>/dev/null || rc=$?
  if [ "$rc" -ge 2 ]; then
    echo "verify-floris-host: grep failed on ${file#"$root"/}" >&2
    exit 2
  fi
  if [ "$rc" -eq 1 ]; then
    violation "$what"
  fi
}

if [ ! -d "$floris" ]; then
  echo "verify-floris-host: not a directory: $floris" >&2
  exit 2
fi

# --- 1. Provenance ---------------------------------------------------------
if need_file "$floris/UPSTREAM.md"; then
  grep_probes '^- Tag: `v0\.5\.2`$' "$floris/UPSTREAM.md" \
    "UPSTREAM.md does not pin tag v0.5.2"
  grep_probes '^- Commit: `2e82060251897226c0739b9f52d1d051b02305fb`$' \
    "$floris/UPSTREAM.md" "UPSTREAM.md does not pin commit 2e82060"
  grep_probes 'Apache-2\.0' "$floris/UPSTREAM.md" \
    "UPSTREAM.md does not record the Apache-2.0 upstream license"
fi

# --- 2. Rent ledger --------------------------------------------------------
ledger="$floris/UPSTREAM-MODIFIED.md"
if need_file "$ledger"; then
  # Bullets look like: "- <path> — <reason>" (em dash, per the file's own
  # format note). The fenced example block inside the note uses a
  # placeholder path in angle brackets — not a real entry — so bullets
  # with '<' are skipped. Ledgered paths must exist exactly once.
  entries="$(grep -E '^- [^ ]+ — ' "$ledger" 2>/dev/null | sed -E 's/^- ([^ ]+) — .*/\1/' | grep -v '<' || true)"
  if [ -z "$entries" ]; then
    violation "UPSTREAM-MODIFIED.md records no ledgered files"
  fi
  while IFS= read -r path; do
    [ -n "$path" ] || continue
    if [ ! -f "$floris/$path" ]; then
      violation "stale ledger entry: $path does not exist in the vendored tree"
    fi
  done <<< "$entries"
  dupes="$(printf '%s\n' "$entries" | sort | uniq -d)"
  if [ -n "$dupes" ]; then
    violation "duplicate ledger entries: $(echo "$dupes" | tr '\n' ' ')"
  fi
fi

# --- 3. Separation ---------------------------------------------------------
# PersonaSpeak-owned files inside the vendored tree: our Kotlin package
# under app/src/main and our merged backup rules. Anything else with a
# personaspeak identity is scattered rent. The find runs RELATIVE to the
# vendored root on purpose: an absolute -path pattern would also match
# the checkout directory's own name (it contains "personaspeak" both in
# this worktree and on a standard GitHub runner).
stray="$(cd "$floris" && find . -type f \( -name '*.kt' -o -name '*.java' -o -name '*.xml' \) \
  -path '*personaspeak*' \
  -not -path './app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/*' \
  -not -path './app/src/main/res/xml/personaspeak_host_*' \
  -not -path '*/build/*' -print 2>/dev/null || true)"
if [ -n "$stray" ]; then
  violation "PersonaSpeak files outside the recorded first-party locations:
$stray"
fi

# --- 4. Two-root isolation -------------------------------------------------
if need_file "$floris/settings.gradle.kts"; then
  for module in core-personas core-providers personaspeak-ui \
                personaspeak-data personaspeak-providers personaspeak-ime; do
    mapping="personaspeakProject(\":$module\", \"$module\")"
    if ! grep -qF "$mapping" "$floris/settings.gradle.kts" 2>/dev/null; then
      violation "settings.gradle.kts does not map ../$module into the floris root (expected: $mapping)"
    fi
  done
fi
if need_file "$floris/build.gradle.kts"; then
  grep_probes 'personaspeak-build' "$floris/build.gradle.kts" \
    "build.gradle.kts does not redirect first-party build dirs under personaspeak-build/"
fi
if [ -f "$unified_settings" ]; then
  if grep -n "florisboard" "$unified_settings" 2>/dev/null; then
    violation "unified root settings.gradle includes a florisboard project — the two roots must stay separate"
  fi
else
  echo "verify-floris-host: missing file: ${unified_settings#"$root"/}" >&2
  fail=1
fi

# --- 5. Identity guard -----------------------------------------------------
if need_file "$floris/app/build.gradle.kts"; then
  grep_probes 'personaspeakFlorisAppId' "$floris/app/build.gradle.kts" \
    "app/build.gradle.kts does not read the personaspeakFlorisAppId property"
  grep_probes 'dev\.patrickgold\.florisboard' "$floris/app/build.gradle.kts" \
    "app/build.gradle.kts lost the pristine upstream applicationId default"
fi

if [ "$fail" -ne 0 ]; then
  exit 1
fi
echo "floris host contract verified: provenance pinned, rent ledgered and present, first-party code separated, roots isolated, identity default pristine"
