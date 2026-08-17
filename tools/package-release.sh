#!/usr/bin/env bash
# Package already-built APKs into independent Magisk and LSPosed deliverables.
# Required inputs are validated; the script never signs or mutates binaries.

MODE="${1:-release}"
case "$MODE" in debug|release) ;; *) printf 'FAILED: mode must be debug or release\n' >&2; exit 3 ;; esac

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"
VERSION="0.2.0"
DIST="$ROOT/dist"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/ts18-statusbar-package.XXXXXX")" || {
    printf 'FAILED: cannot create temporary directory\n' >&2
    exit 3
}
cleanup() {
    rc=$?
    rm -rf -- "$TMP"
    exit "$rc"
}
trap cleanup EXIT HUP INT TERM

if ! command -v zip >/dev/null 2>&1; then
    printf 'FAILED: zip is required\n' >&2
    exit 3
fi

if [ "$MODE" = "debug" ] && [ "${ALLOW_DEBUG_SIGNING:-0}" != "1" ]; then
    printf 'STOPPED FOR SAFETY: debug packaging changes signer between clean build environments. Set ALLOW_DEBUG_SIGNING=1 only for first-device development.\n' >&2
    exit 4
fi

variant="${MODE^}"
overlay="$ROOT/overlay/build/outputs/apk/$MODE/overlay-$MODE.apk"
lsposed="$ROOT/lsposed/build/outputs/apk/$MODE/lsposed-$MODE.apk"
if [ ! -s "$overlay" ]; then printf 'FAILED: missing built overlay APK: %s\n' "$overlay" >&2; exit 2; fi
if [ ! -s "$lsposed" ]; then printf 'FAILED: missing built LSPosed APK: %s\n' "$lsposed" >&2; exit 2; fi

rm -rf -- "$DIST"
mkdir -p -- "$DIST" "$TMP/magisk/system/product/overlay" "$TMP/bundle" || exit 3
cp -R -- "$ROOT/magisk/." "$TMP/magisk/" || exit 2
cp -- "$overlay" "$TMP/magisk/system/product/overlay/TS18StatusBarGeometry.apk" || exit 2

(
    cd "$TMP/magisk" || exit 3
    zip -qr "$DIST/TS18-StatusBar-Geometry-Magisk-v$VERSION-$MODE.zip" .
) || { printf 'FAILED: Magisk zip creation\n' >&2; exit 2; }

cp -- "$lsposed" "$DIST/TS18-StatusBar-Input-LSPosed-v$VERSION-$MODE.apk" || exit 2
cp -- "$DIST/TS18-StatusBar-Geometry-Magisk-v$VERSION-$MODE.zip" "$TMP/bundle/" || exit 2
cp -- "$DIST/TS18-StatusBar-Input-LSPosed-v$VERSION-$MODE.apk" "$TMP/bundle/" || exit 2
cp -- "$ROOT/docs/INSTALL.md" "$ROOT/docs/RECOVERY.md" "$ROOT/docs/VALIDATION.md" "$ROOT/docs/ROADMAP.md" "$TMP/bundle/" || exit 2
(
    cd "$TMP/bundle" || exit 3
    zip -qr "$DIST/TS18-StatusBar-Bundle-v$VERSION-$MODE.zip" .
) || { printf 'FAILED: bundle zip creation\n' >&2; exit 2; }

(
    cd "$DIST" || exit 3
    sha256sum ./* > SHA256SUMS.txt
) || { printf 'FAILED: checksums\n' >&2; exit 2; }

printf 'SUCCESS: packaged %s artefacts under %s\n' "$MODE" "$DIST"
