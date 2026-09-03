#!/usr/bin/env bash
# Contract test for verify-floris-release.sh.
#
# Drives the verifier against fixture trees through its documented
# FLORIS_ROOT / FLORIS_SCRIPTS test seams:
#   1. a contract-clean fixture must be accepted (exit 0);
#   2. each P3 invariant, broken one at a time, must be rejected with
#      its named finding: lost env-var keystore read, unconditional
#      signing, hardcoded keystore path, committed keystore file, lost
#      R8 minify/shrink/owned-rules posture, personaspeak lines leaking
#      into upstream proguard-rules.pro, missing throwaway generator,
#      vanished provider-credential backup exclude, missing lint-ignore
#      annotation.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/../verify-floris-release.sh"

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

# build_fixture <dir>: a contract-clean minimal floris tree + scripts dir.
build_fixture() {
  local dir="$1"
  rm -rf "$dir"
  mkdir -p "$dir/app/src/main/res/xml" "$dir/scripts"

  cat > "$dir/app/build.gradle.kts" <<'EOF'
android {
    val florisKeystorePath = System.getenv("PERSONASPEAK_FLORIS_RELEASE_KEYSTORE")
    if (florisKeystorePath != null && file(florisKeystorePath).exists()) {
        signingConfigs.maybeCreate("release").apply {
            storeFile = file(florisKeystorePath)
            storePassword = System.getenv("PERSONASPEAK_FLORIS_RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("PERSONASPEAK_FLORIS_RELEASE_KEY_ALIAS")
                ?: "personaspeak-floris"
            keyPassword = System.getenv("PERSONASPEAK_FLORIS_RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        named("release") {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-personaspeak.pro",
            )
            isMinifyEnabled = true
            isShrinkResources = true
            if (florisKeystorePath != null && file(florisKeystorePath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("benchmark") {
            initWith(getByName("release"))
        }
    }
}
EOF

  cat > "$dir/app/proguard-personaspeak.pro" <<'EOF'
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
EOF

  cat > "$dir/app/proguard-rules.pro" <<'EOF'
# upstream rules, nothing first-party in here
-dontobfuscate
EOF

  cat > "$dir/scripts/generate-floris-release-keystore.sh" <<'EOF'
# THROWAWAY developer keystore generator, default out-of-tree under
# florisboard/build/
dest="$script_dir/../florisboard/build/personaspeak-floris-release-dev.keystore"
EOF

  for rules in personaspeak_host_full_backup_content.xml \
               personaspeak_host_data_extraction_rules.xml; do
    cat > "$dir/app/src/main/res/xml/$rules" <<'EOF'
<rules-root xmlns:tools="http://schemas.android.com/tools">
    <include domain="file" path="ime" />
    <exclude domain="file" path="personaspeak_secret.bin" tools:ignore="FullBackupContent" />
    <exclude domain="file" path="personaspeak_secret.bin.staging" tools:ignore="FullBackupContent" />
    <exclude domain="file" path="datastore/personaspeak_provider_config.preferences_pb" tools:ignore="FullBackupContent" />
</rules-root>
EOF
  done
}

run_verifier() { # run_verifier <floris-root> <scripts-dir>; sets RV_OUT/RV_RC
  RV_OUT="$(FLORIS_ROOT="$1" FLORIS_SCRIPTS="$2" \
    bash "$verifier" "$tmp/anchor" 2>&1)" && RV_RC=0 || RV_RC=$?
  return 0
}

anchor="$tmp/anchor"
mkdir -p "$anchor"

# --- 1. Clean fixture accepted ---------------------------------------------
build_fixture "$tmp/clean"
run_verifier "$tmp/clean" "$tmp/clean/scripts"; out="$RV_OUT"; rc="$RV_RC"
check "clean fixture accepted" pass "$out" $rc ""

# --- 2. Lost env-var keystore read ------------------------------------------
build_fixture "$tmp/no-env"
sed -i.bak 's/PERSONASPEAK_FLORIS_RELEASE_KEYSTORE_PASSWORD/OTHER_PASSWORD_ENV/' \
  "$tmp/no-env/app/build.gradle.kts"
run_verifier "$tmp/no-env" "$tmp/no-env/scripts"; out="$RV_OUT"; rc="$RV_RC"
check "missing keystore-password env read rejected" fail "$out" $rc \
  "does not read the keystore password env"

# --- 3. Unconditional signing ------------------------------------------------
build_fixture "$tmp/unconditional"
python3 - "$tmp/unconditional/app/build.gradle.kts" <<'EOF'
import re, sys
p = sys.argv[1]
src = open(p).read()
src2 = re.sub(
    r'if \(florisKeystorePath != null[^}]*\) \{\s*'
    r'signingConfig = signingConfigs\.getByName\("release"\)\s*\}',
    'signingConfig = signingConfigs.getByName("release")', src, count=1)
assert src2 != src, "fixture edit did not apply"
open(p, 'w').write(src2)
EOF
run_verifier "$tmp/unconditional" "$tmp/unconditional/scripts"; out="$RV_OUT"; rc="$RV_RC"
check "unconditional signingConfig rejected" fail "$out" $rc \
  "conditional on the keystore file existing"

# --- 4. Hardcoded keystore path ----------------------------------------------
build_fixture "$tmp/hardcoded"
cat >> "$tmp/hardcoded/app/build.gradle.kts" <<'EOF'
// storeFile = file("release.keystore")
val leak = "prod.keystore"
EOF
run_verifier "$tmp/hardcoded" "$tmp/hardcoded/scripts"; out="$RV_OUT"; rc="$RV_RC"
check "hardcoded keystore path rejected" fail "$out" $rc \
  "appears to hardcode a keystore path"

# --- 5. Committed keystore file ----------------------------------------------
build_fixture "$tmp/committed"
touch "$tmp/committed/app/release-keys.jks"
run_verifier "$tmp/committed" "$tmp/committed/scripts"; out="$RV_OUT"; rc="$RV_RC"
check "committed keystore file rejected" fail "$out" $rc \
  "keystore-like file inside the vendored tree"

# --- 6. Lost R8 posture -------------------------------------------------------
build_fixture "$tmp/no-r8"
sed -i.bak 's/isMinifyEnabled = true/isMinifyEnabled = false/' \
  "$tmp/no-r8/app/build.gradle.kts"
run_verifier "$tmp/no-r8" "$tmp/no-r8/scripts"; out="$RV_OUT"; rc="$RV_RC"
check "minify disabled rejected" fail "$out" $rc \
  "must set isMinifyEnabled"

# --- 7. Personaspeak lines in upstream rules ---------------------------------
build_fixture "$tmp/leak-rules"
echo "# personaspeak keep" >> "$tmp/leak-rules/app/proguard-rules.pro"
run_verifier "$tmp/leak-rules" "$tmp/leak-rules/scripts"; out="$RV_OUT"; rc="$RV_RC"
check "personaspeak leak into upstream rules rejected" fail "$out" $rc \
  "must stay free of personaspeak lines"

# --- 8. Missing generator ------------------------------------------------------
build_fixture "$tmp/no-gen"
rm "$tmp/no-gen/scripts/generate-floris-release-keystore.sh"
run_verifier "$tmp/no-gen" "$tmp/no-gen/scripts"; out="$RV_OUT"; rc="$RV_RC"
check "missing throwaway generator rejected" fail "$out" $rc \
  "missing file"

# --- 9. Vanished backup exclude ------------------------------------------------
build_fixture "$tmp/no-exclude"
sed -i.bak '/personaspeak_secret/d' \
  "$tmp/no-exclude/app/src/main/res/xml/personaspeak_host_full_backup_content.xml"
run_verifier "$tmp/no-exclude" "$tmp/no-exclude/scripts"; out="$RV_OUT"; rc="$RV_RC"
check "vanished credential exclude rejected" fail "$out" $rc \
  "no longer excludes the provider credential blob"

# --- 10. Missing lint-ignore annotation -----------------------------------------
build_fixture "$tmp/no-ignore"
sed -i.bak 's/ tools:ignore="FullBackupContent"//' \
  "$tmp/no-ignore/app/src/main/res/xml/personaspeak_host_data_extraction_rules.xml"
run_verifier "$tmp/no-ignore" "$tmp/no-ignore/scripts"; out="$RV_OUT"; rc="$RV_RC"
check "missing lint-ignore annotation rejected" fail "$out" $rc \
  "must carry the lint ignore"

if [ "$failures" -ne 0 ]; then
  echo "verify-floris-release-test: $failures failure(s)" >&2
  exit 1
fi
echo "verify-floris-release-test: all cases passed"
