#!/usr/bin/env bash
# Provision the official Gradle 8.9 wrapper JAR without trusting an unverified repository binary.

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"
JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
EXPECTED_JAR_SHA256="498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar"

for cmd in curl sha256sum awk mktemp; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        printf 'FAILED: %s is required\n' "$cmd" >&2
        exit 3
    fi
done

if [ -s "$JAR" ]; then
    existing_sha="$(sha256sum "$JAR" | awk '{print $1}')"
    if [ "$existing_sha" = "$EXPECTED_JAR_SHA256" ]; then
        printf 'SUCCESS: official Gradle 8.9 wrapper JAR already present\n'
        exit 0
    fi
    printf 'FAILED: refusing to execute or overwrite an unverified wrapper JAR: %s\n' "$existing_sha" >&2
    printf 'Delete %s manually, then rerun this bootstrap.\n' "$JAR" >&2
    exit 4
fi

TMP="$(mktemp "${TMPDIR:-/tmp}/gradle-wrapper-8.9.XXXXXX.jar")" || {
    printf 'FAILED: cannot create temporary wrapper file\n' >&2
    exit 3
}
cleanup() {
    rm -f -- "$TMP"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

if ! curl --fail --location --silent --show-error \
    --proto '=https' --tlsv1.2 \
    --output "$TMP" "$WRAPPER_URL"; then
    printf 'FAILED: could not download the official Gradle 8.9 wrapper JAR\n' >&2
    exit 2
fi

actual_sha="$(sha256sum "$TMP" | awk '{print $1}')"
if [ "$actual_sha" != "$EXPECTED_JAR_SHA256" ]; then
    printf 'FAILED: downloaded Gradle wrapper JAR checksum mismatch: %s\n' "$actual_sha" >&2
    exit 2
fi

mkdir -p -- "$(dirname -- "$JAR")" || exit 3
chmod 0644 "$TMP" || exit 3
mv -- "$TMP" "$JAR" || {
    printf 'FAILED: cannot install verified wrapper JAR at %s\n' "$JAR" >&2
    exit 2
}
trap - EXIT HUP INT TERM
printf 'SUCCESS: provisioned official Gradle 8.9 wrapper JAR\n'
