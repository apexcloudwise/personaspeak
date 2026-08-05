#!/usr/bin/env bash
# Contract test for verify-ask-closure.sh.
#
# Drives the verifier with fixture Gradle output through its documented
# ASK_CLOSURE_PROJECTS_OUTPUT / ASK_CLOSURE_DEPS_OUTPUT test seam:
#   1. the approved closure must be accepted (exit 0);
#   2. a graph containing :addons:languages:english:apk must be rejected
#      with "unexpected ASK project :addons:languages:english:apk";
#   3. the committed expected-ask-projects.txt must equal the canonical
#      list pinned below, so the test and the verifier cannot drift apart.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/../verify-ask-closure.sh"
expected_file="$script_dir/../expected-ask-projects.txt"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Canonical Milestone 2 graph: 28 ASK logical paths (24 application/library/
# build-test projects plus the four explicit parent projects) and the three
# first-party libraries. LC_ALL=C sorted.
canonical="$tmp/canonical.txt"
cat > "$canonical" <<'EOF'
:addons
:addons:base
:addons:languages
:addons:languages:english
:addons:languages:english:pack
:api
:core-personas
:core-providers
:ime
:ime:addons
:ime:app
:ime:base
:ime:base-rx
:ime:base-test
:ime:chewbacca
:ime:dictionaries
:ime:dictionaries:jnidictionaryv1
:ime:dictionaries:jnidictionaryv2
:ime:fileprovider
:ime:gesturetyping
:ime:nextword
:ime:notification
:ime:overlay
:ime:permissions
:ime:pixel
:ime:prefs
:ime:releaseinfo
:ime:remote
:ime:voiceime
:junit-sharding
:personaspeak-ui
EOF

# Fixture android root: every canonical project gets a directory and an inert
# build file; only keyboard/ime/app/build.gradle applies an application
# plugin (via the upstream apk_module.gradle indirection, as in the real tree).
fixture_root="$tmp/android"
while IFS= read -r p; do
  [ -z "$p" ] && continue
  rel="${p#:}"
  rel="${rel//://}"
  case "$p" in
    :core-personas | :core-providers | :personaspeak-ui) dir="$fixture_root/$rel" ;;
    *) dir="$fixture_root/keyboard/$rel" ;;
  esac
  mkdir -p "$dir"
  printf '// inert fixture project\n' > "$dir/build.gradle"
done < "$canonical"
printf 'apply from: "%s/gradle/apk_module.gradle"\n' '${rootDir}' \
  > "$fixture_root/keyboard/ime/app/build.gradle"

# Fixture `gradlew projects --console=plain` output.
good_projects="$tmp/projects-good.txt"
{
  echo "Root project 'personaboard'"
  while IFS= read -r p; do
    [ -z "$p" ] && continue
    echo "+--- Project '$p'"
  done < "$canonical"
} > "$good_projects"

bad_projects="$tmp/projects-bad.txt"
cp "$good_projects" "$bad_projects"
echo "+--- Project ':addons:languages:english:apk'" >> "$bad_projects"

# Fixture `:ime:app:dependencies --configuration debugRuntimeClasspath` output
# with no :addons:*:apk project.
deps_clean="$tmp/deps-clean.txt"
cat > "$deps_clean" <<'EOF'
debugRuntimeClasspath - Runtime classpath of compilation 'debug' (target  (androidJvm)).
+--- project :personaspeak-ui
+--- project :addons:base
+--- project :addons:languages:english:pack
\--- project :ime:base
EOF

# 1. Positive control: the approved closure passes.
out_good="$tmp/out-good.txt"
if ! ASK_CLOSURE_PROJECTS_OUTPUT="$good_projects" \
     ASK_CLOSURE_DEPS_OUTPUT="$deps_clean" \
     bash "$verifier" "$fixture_root" > "$out_good" 2>&1; then
  echo "FAIL: approved ASK closure was rejected" >&2
  cat "$out_good" >&2
  exit 1
fi

# 2. Negative control: the extra :apk project is rejected with the exact message.
out_bad="$tmp/out-bad.txt"
if ASK_CLOSURE_PROJECTS_OUTPUT="$bad_projects" \
   ASK_CLOSURE_DEPS_OUTPUT="$deps_clean" \
   bash "$verifier" "$fixture_root" > "$out_bad" 2>&1; then
  echo "FAIL: unexpected :apk project was accepted" >&2
  cat "$out_bad" >&2
  exit 1
fi
if ! grep -q "^unexpected ASK project :addons:languages:english:apk$" "$out_bad"; then
  echo "FAIL: rejection message missing for :addons:languages:english:apk" >&2
  cat "$out_bad" >&2
  exit 1
fi

# 3. The committed expected list must equal the canonical list exactly.
if ! diff -u "$canonical" <(LC_ALL=C sort "$expected_file"); then
  echo "FAIL: expected-ask-projects.txt drifted from the canonical closure" >&2
  exit 1
fi

echo "PASS: exact ASK closure accepted; unexpected project rejected"
