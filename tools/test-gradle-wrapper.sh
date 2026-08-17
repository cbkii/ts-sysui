#!/usr/bin/env bash
# Verify the committed Gradle 8.9 wrapper and its pinned distribution integrity.

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"
PROPS="$ROOT/gradle/wrapper/gradle-wrapper.properties"
JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
EXPECTED_JAR_SHA256="498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17"
EXPECTED_DIST_SHA256="d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"

for cmd in sha256sum grep awk; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        printf 'FAILED: %s is required\n' "$cmd" >&2
        exit 3
    fi
done
if [ ! -s "$JAR" ]; then
    printf 'FAILED: missing Gradle wrapper JAR: %s\n' "$JAR" >&2
    exit 2
fi
if [ ! -s "$PROPS" ]; then
    printf 'FAILED: missing Gradle wrapper properties: %s\n' "$PROPS" >&2
    exit 2
fi

actual_jar_sha="$(sha256sum "$JAR" | awk '{print $1}')"
if [ "$actual_jar_sha" != "$EXPECTED_JAR_SHA256" ]; then
    printf 'FAILED: Gradle wrapper JAR SHA-256 mismatch: %s\n' "$actual_jar_sha" >&2
    exit 2
fi
if ! grep -Fqx 'distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip' "$PROPS"; then
    printf 'FAILED: Gradle wrapper distribution is not 8.9-bin\n' >&2
    exit 2
fi
if ! grep -Fqx "distributionSha256Sum=$EXPECTED_DIST_SHA256" "$PROPS"; then
    printf 'FAILED: Gradle 8.9 distribution checksum is not pinned as expected\n' >&2
    exit 2
fi

printf 'SUCCESS: Gradle 8.9 wrapper integrity contract\n'
