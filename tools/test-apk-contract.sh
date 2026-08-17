#!/usr/bin/env bash
# Verify the assembled LSPosed APK keeps the local Xposed bridge stubs compile-only.

APK="${1:-}"
if [ -z "$APK" ] || [ ! -s "$APK" ]; then
    printf 'FAILED: pass an assembled LSPosed APK path\n' >&2
    exit 3
fi
for cmd in unzip grep mktemp; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        printf 'FAILED: %s is required\n' "$cmd" >&2
        exit 3
    fi
done
if ! unzip -Z1 "$APK" | grep -Fqx 'assets/xposed_init'; then
    printf 'FAILED: legacy assets/xposed_init missing from APK\n' >&2
    exit 2
fi

DEXDUMP=""
if command -v dexdump >/dev/null 2>&1; then
    DEXDUMP="$(command -v dexdump)"
elif [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/build-tools/35.0.0/dexdump" ]; then
    DEXDUMP="$ANDROID_HOME/build-tools/35.0.0/dexdump"
elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "$ANDROID_SDK_ROOT/build-tools/35.0.0/dexdump" ]; then
    DEXDUMP="$ANDROID_SDK_ROOT/build-tools/35.0.0/dexdump"
fi
if [ -z "$DEXDUMP" ]; then
    printf 'FAILED: dexdump is required to distinguish defined classes from external Xposed references\n' >&2
    exit 3
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/ts18-apk-contract.XXXXXX")" || {
    printf 'FAILED: cannot create temporary directory\n' >&2
    exit 3
}
cleanup() {
    rm -rf -- "$TMP"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

dex_list="$(unzip -Z1 "$APK" 'classes*.dex' 2>/dev/null)"
if [ -z "$dex_list" ]; then
    printf 'FAILED: APK contains no classes*.dex\n' >&2
    exit 2
fi

index=0
for entry in $dex_list; do
    index=$((index + 1))
    dex="$TMP/classes-$index.dex"
    dump="$TMP/classes-$index.txt"
    if ! unzip -p "$APK" "$entry" > "$dex"; then
        printf 'FAILED: cannot extract %s\n' "$entry" >&2
        exit 2
    fi
    if ! "$DEXDUMP" "$dex" > "$dump" 2>&1; then
        printf 'FAILED: dexdump failed for %s\n' "$entry" >&2
        exit 2
    fi
    if grep -E "Class descriptor.*'Lde/robv/android/xposed/" "$dump" >/dev/null 2>&1; then
        printf 'FAILED: compile-only Xposed bridge class definitions were packaged in %s\n' "$entry" >&2
        exit 2
    fi
done

printf 'SUCCESS: LSPosed APK keeps Xposed bridge class definitions compile-only\n'
