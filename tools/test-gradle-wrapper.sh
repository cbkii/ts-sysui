#!/usr/bin/env bash
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"
PROPS="$ROOT/gradle/wrapper/gradle-wrapper.properties"
JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
EXPECTED_JAR_SHA256="498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17"
EXPECTED_DIST_SHA256="d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"
for cmd in sha256sum grep awk; do command -v "$cmd" >/dev/null 2>&1 || { printf 'FAILED: %s is required\n' "$cmd" >&2; exit 3; }; done
[ -s "$JAR" ] || { printf 'FAILED: missing Gradle wrapper JAR: %s\n' "$JAR" >&2; exit 2; }
[ -s "$PROPS" ] || { printf 'FAILED: missing Gradle wrapper properties: %s\n' "$PROPS" >&2; exit 2; }
actual_jar_sha="$(sha256sum "$JAR" | awk '{print $1}')"
[ "$actual_jar_sha" = "$EXPECTED_JAR_SHA256" ] || { printf 'FAILED: Gradle wrapper JAR SHA-256 mismatch: %s\n' "$actual_jar_sha" >&2; exit 2; }
grep -Fqx 'distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip' "$PROPS" || { printf 'FAILED: Gradle wrapper distribution is not 8.9-bin\n' >&2; exit 2; }
grep -Fqx "distributionSha256Sum=$EXPECTED_DIST_SHA256" "$PROPS" || { printf 'FAILED: Gradle 8.9 distribution checksum is not pinned as expected\n' >&2; exit 2; }
printf 'SUCCESS: Gradle 8.9 wrapper integrity contract\n'
