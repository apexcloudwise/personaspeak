#!/usr/bin/env bash
# Generate a reproducible local/developer test release keystore out-of-tree.
#
# usage: generate-release-keystore.sh [<destination-keystore-path>]
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
dest="${1:-$script_dir/../build/personaspeak-release.keystore}"
mkdir -p "$(dirname "$dest")"

store_pass="${PERSONASPEAK_RELEASE_KEYSTORE_PASSWORD:-personaspeak-dev-password}"
key_alias="${PERSONASPEAK_RELEASE_KEY_ALIAS:-personaspeak}"
key_pass="${PERSONASPEAK_RELEASE_KEY_PASSWORD:-personaspeak-dev-password}"

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
    -dname "CN=PersonaSpeak Release, OU=Engineering, O=Pixel Perfect Studios, C=US"

echo "Generated developer release keystore at: $dest"
echo "Alias: $key_alias"
echo "Certificate SHA-256 fingerprint:"
keytool -list -v -keystore "$dest" -storepass "$store_pass" -alias "$key_alias" | grep "SHA256:" || true
