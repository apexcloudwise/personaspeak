#!/usr/bin/env bash
# Generate a reproducible THROWAWAY developer release keystore for the
# FlorisBoard second host, out-of-tree. This is NOT the owner's real
# release keystore and never becomes one: it exists so a laptop can
# prove the signed release path (assembleRelease + apksigner verify)
# without any secret entering the repo, the chat, or the logs.
#
# The real release signing run stays a one-command OWNER step:
#   PERSONASPEAK_FLORIS_RELEASE_KEYSTORE=<real keystore path> \
#   PERSONASPEAK_FLORIS_RELEASE_KEYSTORE_PASSWORD=... \
#   PERSONASPEAK_FLORIS_RELEASE_KEY_ALIAS=... \
#   PERSONASPEAK_FLORIS_RELEASE_KEY_PASSWORD=... \
#   ./gradlew :app:assembleRelease \
#     -PpersonaspeakFlorisAppId=biz.pixelperfectstudios.personaspeak.floris
#
# usage: generate-floris-release-keystore.sh [<destination-keystore-path>]
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
dest="${1:-$script_dir/../florisboard/build/personaspeak-floris-release-dev.keystore}"
mkdir -p "$(dirname "$dest")"

store_pass="${PERSONASPEAK_FLORIS_RELEASE_KEYSTORE_PASSWORD:-personaspeak-floris-dev-password}"
key_alias="${PERSONASPEAK_FLORIS_RELEASE_KEY_ALIAS:-personaspeak-floris}"
key_pass="${PERSONASPEAK_FLORIS_RELEASE_KEY_PASSWORD:-personaspeak-floris-dev-password}"

if [ -f "$dest" ]; then
    echo "Keystore already exists at: $dest"
    exit 0
fi

keytool -genkeypair \
    -v \
    -keystore "$dest" \
    -storetype PKCS12 \
    -storepass "$store_pass" \
    -alias "$key_alias" \
    -keypass "$key_pass" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=PersonaSpeak Floris Dev, OU=Engineering, O=Pixel Perfect Studios, C=US"

echo "Generated throwaway developer keystore at: $dest"
echo "Alias: $key_alias"
echo "Certificate SHA-256 fingerprint:"
keytool -list -v -keystore "$dest" -storepass "$store_pass" -alias "$key_alias" | grep "SHA256:" || true
