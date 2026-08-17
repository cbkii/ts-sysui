#!/usr/bin/env bash
APK="${1:-}"
if [ -z "$APK" ] || [ ! -s "$APK" ]; then printf 'FAILED: pass an assembled LSPosed APK path\n' >&2; exit 3; fi
for cmd in unzip strings; do command -v "$cmd" >/dev/null 2>&1 || { printf 'FAILED: %s is required\n' "$cmd" >&2; exit 3; }; done
if ! unzip -l "$APK" | grep -q 'assets/xposed_init'; then printf 'FAILED: legacy assets/xposed_init missing from APK\n' >&2; exit 2; fi
TMP="$(mktemp -d "${TMPDIR:-/tmp}/ts18-apk-contract.XXXXXX")" || exit 3
cleanup() { rc=$?; rm -rf -- "$TMP"; exit "$rc"; }
trap cleanup EXIT HUP INT TERM
unzip -p "$APK" classes.dex > "$TMP/classes.dex" || { printf 'FAILED: cannot read classes.dex\n' >&2; exit 2; }
if strings "$TMP/classes.dex" | grep -q 'Lde/robv/android/xposed/'; then printf 'FAILED: compile-only Xposed bridge classes were packaged into the APK\n' >&2; exit 2; fi
printf 'SUCCESS: LSPosed APK keeps Xposed bridge classes compile-only\n'
