#!/usr/bin/env bash
# Verify the release-posture contract of the FlorisBoard second host
# (ADR-0010, P3). Read-only; the secret-bearing release run stays a
# one-command owner step and is never executed here.
#
# usage: verify-floris-release.sh <android-root>
#
# Invariants:
#   1. Env-only signing: app/build.gradle.kts reads the release keystore
#      from PERSONASPEAK_FLORIS_RELEASE_* env vars (distinct names, so
#      the ASK host's release keystore can never sign a Floris build),
#      wires signingConfig only when the keystore file exists, and
#      hardcodes no keystore path.
#   2. No committed keystores: no *.keystore or *.jks file lives in the
#      vendored tree outside build outputs.
#   3. R8 posture: the release build type minifies and shrinks, carries
#      the owned proguard-personaspeak.pro (snakeyaml's JVM-only
#      java.beans references), and upstream proguard-rules.pro stays
#      free of personaspeak lines.
#   4. Throwaway generator: generate-floris-release-keystore.sh exists,
#      defaults to an out-of-tree destination under build/, and says it
#      is a throwaway developer keystore, not the owner's real one.
#   5. Backup-rule posture: the provider-credential excludes remain
#      named in both personaspeak_host_* rule files (guarded with
#      tools:ignore against the allowlist-redundancy lint).
#
# Test seam: FLORIS_ROOT overrides the vendored tree.
#
# Exit codes: 0 pass; 1 contract violation; 2 usage or tool failure.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: verify-floris-release.sh <android-root>" >&2
  exit 2
fi
root="$1"
if [ ! -d "$root" ]; then
  echo "verify-floris-release: not a directory: $root" >&2
  exit 2
fi
root="$(cd "$root" && pwd)"
floris="${FLORIS_ROOT:-$root/florisboard}"
scripts="${FLORIS_SCRIPTS:-$root/scripts}"

fail=0
violation() {
  echo "verify-floris-release: $1" >&2
  fail=1
}
need_file() {
  if [ ! -f "$1" ]; then
    violation "missing file: ${1#"$root"/}"
    return 1
  fi
  return 0
}

grep_probes() {
  local pattern="$1" file="$2" what="$3"
  local rc=0
  grep -qE "$pattern" "$file" 2>/dev/null || rc=$?
  if [ "$rc" -ge 2 ]; then
    echo "verify-floris-release: grep failed on ${file#"$root"/}" >&2
    exit 2
  fi
  if [ "$rc" -eq 1 ]; then
    violation "$what"
  fi
}

if [ ! -d "$floris" ]; then
  echo "verify-floris-release: not a directory: $floris" >&2
  exit 2
fi

gradle_app="$floris/app/build.gradle.kts"

# --- 1. Env-only signing ---------------------------------------------------
if need_file "$gradle_app"; then
  grep_probes 'PERSONASPEAK_FLORIS_RELEASE_KEYSTORE' "$gradle_app" \
    "app/build.gradle.kts does not read PERSONASPEAK_FLORIS_RELEASE_KEYSTORE"
  grep_probes 'PERSONASPEAK_FLORIS_RELEASE_KEYSTORE_PASSWORD' "$gradle_app" \
    "app/build.gradle.kts does not read the keystore password env"
  grep_probes 'PERSONASPEAK_FLORIS_RELEASE_KEY_ALIAS' "$gradle_app" \
    "app/build.gradle.kts does not read the key alias env"
  grep_probes 'PERSONASPEAK_FLORIS_RELEASE_KEY_PASSWORD' "$gradle_app" \
    "app/build.gradle.kts does not read the key password env"
  # The signingConfig wiring must sit inside the keystore-existence
  # conditional: every release-signing assignment needs the guard line
  # within two lines above it (the signingConfigs declaration block
  # carries the same condition text, so a plain grep cannot tell them
  # apart — this is exactly what the unconditional-signature case
  # probes).
  awk '
    /florisKeystorePath/ {guarded=NR}
    /signingConfig *= *signingConfigs\.getByName\("release"\)/ {
      if (NR - guarded > 2) unguarded=1; else wired=1
    }
    END {
      if (!wired || unguarded) exit 1
    }
  ' "$gradle_app" || violation \
    "release signing must be conditional on the keystore file existing"
  if grep -nE '"[^"]*\.(keystore|jks)"' "$gradle_app" 2>/dev/null; then
    violation "app/build.gradle.kts appears to hardcode a keystore path"
  fi
fi

# --- 2. No committed keystores ---------------------------------------------
while IFS= read -r -d '' ks; do
  case "$ks" in
    */build/*) continue ;;
    *) violation "keystore-like file inside the vendored tree: ${ks#"$root"/}" ;;
  esac
done < <(find "$floris" \( -name '*.keystore' -o -name '*.jks' \) -print0 2>/dev/null)

# --- 3. R8 posture -----------------------------------------------------------
if need_file "$gradle_app"; then
  awk '
    /named\("release"\)/ {in_release=1}
    in_release && /isMinifyEnabled = true/ {minify=1}
    in_release && /isShrinkResources = true/ {shrink=1}
    in_release && /proguard-personaspeak\.pro/ {owned_rules=1}
    /create\("benchmark"\)/ {in_release=0}
    END {
      exit !(minify && shrink && owned_rules)
    }
  ' "$gradle_app" || violation \
    "release build type must set isMinifyEnabled + isShrinkResources + proguard-personaspeak.pro"
fi
if need_file "$floris/app/proguard-personaspeak.pro"; then
  grep_probes '^-dontwarn java\.beans\.' "$floris/app/proguard-personaspeak.pro" \
    "proguard-personaspeak.pro must carry the snakeyaml java.beans dontwarn lines"
fi
if [ -f "$floris/app/proguard-rules.pro" ] \
    && grep -qi 'personaspeak' "$floris/app/proguard-rules.pro" 2>/dev/null; then
  violation "upstream proguard-rules.pro must stay free of personaspeak lines"
fi

# --- 4. Throwaway generator ---------------------------------------------------
if need_file "$scripts/generate-floris-release-keystore.sh"; then
  grep_probes 'THROWAWAY|throwaway' "$scripts/generate-floris-release-keystore.sh" \
    "keystore generator must say it produces a throwaway developer keystore"
  grep_probes 'florisboard/build/' "$scripts/generate-floris-release-keystore.sh" \
    "keystore generator must default to an out-of-tree build/ destination"
fi

# --- 5. Backup-rule posture ---------------------------------------------------
for rules in personaspeak_host_full_backup_content.xml \
             personaspeak_host_data_extraction_rules.xml; do
  f="$floris/app/src/main/res/xml/$rules"
  if need_file "$f"; then
    grep_probes 'personaspeak_secret\.bin' "$f" \
      "$rules no longer excludes the provider credential blob"
    grep_probes 'datastore/personaspeak_provider_config\.preferences_pb' "$f" \
      "$rules no longer excludes the provider config datastore file"
    grep_probes 'tools:ignore="FullBackupContent"' "$f" \
      "$rules must carry the lint ignore that documents the deliberate allowlist redundancy"
  fi
done

if [ "$fail" -ne 0 ]; then
  exit 1
fi
echo "verify-floris-release: release posture verified (env-only signing, no committed keystores, R8 owned rules, backup excludes intact)"
exit 0
