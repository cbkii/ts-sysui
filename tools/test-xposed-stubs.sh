#!/usr/bin/env bash
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/ts18-xposed-stubs.XXXXXX")" || exit 3
cleanup() { rc=$?; rm -rf -- "$TMP"; exit "$rc"; }
trap cleanup EXIT HUP INT TERM
if ! command -v javac >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then printf 'FAILED: javac/java are required\n' >&2; exit 3; fi
mapfile -t STUBS < <(find "$ROOT/xposed-stubs/src/main/java" -type f -name '*.java' -print | sort)
[ "${#STUBS[@]}" -gt 0 ] || { printf 'FAILED: no Xposed stubs found\n' >&2; exit 2; }
javac -d "$TMP/classes" "${STUBS[@]}" "$ROOT/tools/XposedStubContractSelfTest.java" || exit 2
java -cp "$TMP/classes" XposedStubContractSelfTest || exit 2
grep -q 'android:name="xposedminversion" android:value="82"' "$ROOT/lsposed/src/main/AndroidManifest.xml" || { printf 'FAILED: expected legacy xposedminversion=82\n' >&2; exit 2; }
test -s "$ROOT/lsposed/src/main/assets/xposed_init" || { printf 'FAILED: missing legacy assets/xposed_init\n' >&2; exit 2; }
if find "$ROOT/lsposed/src/main" -path '*/META-INF/xposed/*' -print -quit | grep -q .; then printf 'FAILED: mixed modern libxposed metadata found in legacy module\n' >&2; exit 2; fi
printf 'SUCCESS: legacy Xposed metadata/stub contract\n'
