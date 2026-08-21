#!/usr/bin/env bash
# Package already-built APKs into independent Magisk and LSPosed deliverables.
# Required inputs are validated; the script never signs or mutates APK binaries.

MODE="${1:-release}"
case "$MODE" in debug|release) ;; *) printf 'FAILED: mode must be debug or release\n' >&2; exit 3 ;; esac

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"
VERSION_FILE="$ROOT/version.properties"
DIST="$ROOT/dist"

read_property() {
    key="$1"
    sed -n "s/^${key}=//p" "$VERSION_FILE" | head -n 1
}
if [ ! -s "$VERSION_FILE" ]; then
    printf 'FAILED: missing version.properties\n' >&2
    exit 2
fi
VERSION="$(read_property versionName)"
VERSION_CODE="$(read_property versionCode)"
if ! printf '%s\n' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
    printf 'FAILED: invalid versionName in version.properties: %s\n' "$VERSION" >&2
    exit 2
fi
case "$VERSION_CODE" in
    ''|*[!0-9]*) printf 'FAILED: invalid versionCode in version.properties\n' >&2; exit 2 ;;
esac

TMP="$(mktemp -d "${TMPDIR:-/tmp}/ts18-statusbar-package.XXXXXX")" || {
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

for cmd in zip sha256sum sed grep; do
    command -v "$cmd" >/dev/null 2>&1 || {
        printf 'FAILED: %s is required\n' "$cmd" >&2
        exit 3
    }
done

if [ "$MODE" = "debug" ] && [ "${ALLOW_DEBUG_SIGNING:-0}" != "1" ]; then
    printf 'STOPPED FOR SAFETY: debug packaging changes signer between clean build environments. Set ALLOW_DEBUG_SIGNING=1 only for first-device development.\n' >&2
    exit 4
fi

overlay="$ROOT/overlay/build/outputs/apk/$MODE/overlay-$MODE.apk"
visual_overlay="$ROOT/visual-overlay/build/outputs/apk/$MODE/visual-overlay-$MODE.apk"
lsposed="$ROOT/lsposed/build/outputs/apk/$MODE/lsposed-$MODE.apk"
if [ ! -s "$overlay" ]; then printf 'FAILED: missing built overlay APK: %s\n' "$overlay" >&2; exit 2; fi
if [ ! -s "$visual_overlay" ]; then printf 'FAILED: missing built visual overlay APK: %s\n' "$visual_overlay" >&2; exit 2; fi
if [ ! -s "$lsposed" ]; then printf 'FAILED: missing built LSPosed APK: %s\n' "$lsposed" >&2; exit 2; fi

if [ "$MODE" = "release" ]; then
    APKSIGNER=""
    SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if command -v apksigner >/dev/null 2>&1; then
        APKSIGNER="$(command -v apksigner)"
    fi
    if [ -z "$APKSIGNER" ] && [ -n "$SDK_ROOT" ] && [ -x "$SDK_ROOT/build-tools/35.0.0/apksigner" ]; then
        APKSIGNER="$SDK_ROOT/build-tools/35.0.0/apksigner"
    fi
    if [ -z "$APKSIGNER" ]; then
        printf 'FAILED: apksigner is required to verify release APK signatures\n' >&2
        exit 3
    fi
    "$APKSIGNER" verify "$overlay" >/dev/null || { printf 'FAILED: overlay release APK is not validly signed\n' >&2; exit 2; }
    "$APKSIGNER" verify "$visual_overlay" >/dev/null || { printf 'FAILED: visual overlay release APK is not validly signed\n' >&2; exit 2; }
    "$APKSIGNER" verify "$lsposed" >/dev/null || { printf 'FAILED: LSPosed release APK is not validly signed\n' >&2; exit 2; }
fi

rm -rf -- "$DIST"
mkdir -p -- \
    "$DIST" \
    "$TMP/geometry-magisk/system/product/overlay" \
    "$TMP/visual-magisk/system/product/overlay" \
    "$TMP/bundle" || exit 3
cp -R -- "$ROOT/magisk/." "$TMP/geometry-magisk/" || exit 2
cp -R -- "$ROOT/magisk-visual/." "$TMP/visual-magisk/" || exit 2
if [ ! -s "$TMP/geometry-magisk/module.prop.in" ]; then
    printf 'FAILED: missing magisk/module.prop.in\n' >&2
    exit 2
fi
if [ ! -s "$TMP/visual-magisk/module.prop.in" ]; then
    printf 'FAILED: missing magisk-visual/module.prop.in\n' >&2
    exit 2
fi
sed -e "s/@VERSION_NAME@/$VERSION/g" -e "s/@VERSION_CODE@/$VERSION_CODE/g" \
    "$TMP/geometry-magisk/module.prop.in" > "$TMP/geometry-magisk/module.prop" || exit 2
sed -e "s/@VERSION_NAME@/$VERSION/g" -e "s/@VERSION_CODE@/$VERSION_CODE/g" \
    "$TMP/visual-magisk/module.prop.in" > "$TMP/visual-magisk/module.prop" || exit 2
rm -f -- "$TMP/geometry-magisk/module.prop.in" "$TMP/visual-magisk/module.prop.in"
cp -- "$overlay" "$TMP/geometry-magisk/system/product/overlay/TS18StatusBarGeometry.apk" || exit 2
cp -- "$visual_overlay" "$TMP/visual-magisk/system/product/overlay/TS18StatusBarVisuals.apk" || exit 2

(
    cd "$TMP/geometry-magisk" || exit 3
    zip -qr "$DIST/TS18-StatusBar-Geometry-Magisk-v$VERSION-$MODE.zip" .
) || { printf 'FAILED: Magisk zip creation\n' >&2; exit 2; }
(
    cd "$TMP/visual-magisk" || exit 3
    zip -qr "$DIST/TS18-StatusBar-Visuals-Magisk-v$VERSION-$MODE.zip" .
) || { printf 'FAILED: visual Magisk zip creation\n' >&2; exit 2; }

cp -- "$lsposed" "$DIST/TS18-StatusBar-Input-LSPosed-v$VERSION-$MODE.apk" || exit 2
cp -- "$DIST/TS18-StatusBar-Geometry-Magisk-v$VERSION-$MODE.zip" "$TMP/bundle/" || exit 2
cp -- "$DIST/TS18-StatusBar-Visuals-Magisk-v$VERSION-$MODE.zip" "$TMP/bundle/" || exit 2
cp -- "$DIST/TS18-StatusBar-Input-LSPosed-v$VERSION-$MODE.apk" "$TMP/bundle/" || exit 2
cp -- "$ROOT/docs/INSTALL.md" "$ROOT/docs/RECOVERY.md" "$ROOT/docs/VALIDATION.md" \
    "$ROOT/docs/ROADMAP.md" "$ROOT/docs/RIGHT-NAV-MEDIA-ROADMAP.md" \
    "$ROOT/docs/LSPOSED-COMPATIBILITY.md" "$TMP/bundle/" || exit 2
cp -- "$ROOT/tools/ts18-statusbar-config.sh" \
    "$ROOT/tools/ts18-systemui-contract.sh" \
    "$ROOT/tools/ts18-statusbar-validate.sh" \
    "$ROOT/tools/ts18-right-nav-evidence.sh" "$TMP/bundle/" || exit 2
(
    cd "$TMP/bundle" || exit 3
    zip -qr "$DIST/TS18-StatusBar-Bundle-v$VERSION-$MODE.zip" .
) || { printf 'FAILED: bundle zip creation\n' >&2; exit 2; }

(
    cd "$DIST" || exit 3
    sha256sum ./* > SHA256SUMS.txt
) || { printf 'FAILED: checksums\n' >&2; exit 2; }

printf 'SUCCESS: packaged %s v%s artefacts under %s\n' "$MODE" "$VERSION" "$DIST"
