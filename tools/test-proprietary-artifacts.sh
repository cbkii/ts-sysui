#!/usr/bin/env bash
# Reject proprietary firmware/decompiler/build artefacts from tracked source.

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"
cd "$ROOT" || exit 3

failed=0
while IFS= read -r -d '' path; do
    lower="$(printf '%s' "$path" | tr '[:upper:]' '[:lower:]')"
    case "$lower" in
        *.apk|*.apks|*.aab|*.dex|*.odex|*.vdex|*.oat|*.jks|*.keystore|*.pem|*.p12|*.pfx|*.der)
            printf 'FAILED: prohibited proprietary/signing/build artefact is tracked: %s\n' "$path" >&2
            failed=1
            ;;
        reference/apks/*|*/decoded/*|*/decompiled/*|*/jadx-output/*|*/apktool-output/*)
            printf 'FAILED: decoded/proprietary working directory is tracked: %s\n' "$path" >&2
            failed=1
            ;;
    esac
done < <(git ls-files -z)

if [ "$failed" -ne 0 ]; then
    exit 2
fi
printf 'SUCCESS: no proprietary APK, decoded firmware, signing material or packaged Android binary is tracked\n'
