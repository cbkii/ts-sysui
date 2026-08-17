#!/usr/bin/env bash
# Creates a local persistent signing key. The key is ignored by Git and must not
# be committed. Use a password manager and GitHub Actions secrets for CI release.

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"
KEY_DIR="$ROOT/.signing"
KEYSTORE="$KEY_DIR/ts18-statusbar-release.jks"

if ! command -v keytool >/dev/null 2>&1; then
    printf 'FAILED: keytool (JDK) is required\n' >&2
    exit 3
fi
if [ -e "$KEYSTORE" ]; then
    printf 'STOPPED FOR SAFETY: keystore already exists: %s\n' "$KEYSTORE" >&2
    exit 4
fi
mkdir -p -- "$KEY_DIR" || exit 3
chmod 700 "$KEY_DIR" || exit 3

printf 'Creating %s. You will be prompted for a persistent password.\n' "$KEYSTORE" >&2
if ! keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias ts18-statusbar \
    -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=TS18 Status Bar, OU=Device Mod, O=CB"; then
    printf 'FAILED: keytool did not create the signing key\n' >&2
    exit 2
fi
chmod 600 "$KEYSTORE" || exit 3
printf 'SUCCESS: %s\n' "$KEYSTORE"
