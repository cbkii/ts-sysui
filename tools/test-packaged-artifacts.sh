#!/usr/bin/env bash
# Validate the independently recoverable package set produced by package-release.sh.

set -euo pipefail

MODE="${1:-debug}"
case "$MODE" in debug|release) ;; *) printf 'FAILED: mode must be debug or release\n' >&2; exit 3 ;; esac

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(dirname -- "$SCRIPT_DIR")"
DIST="$ROOT/dist"
VERSION="$(sed -n 's/^versionName=//p' "$ROOT/version.properties" | head -n 1)"

for command_name in unzip grep sha256sum; do
    command -v "$command_name" >/dev/null 2>&1 || {
        printf 'FAILED: %s is required\n' "$command_name" >&2
        exit 3
    }
done

GEOMETRY="TS18-StatusBar-Geometry-Magisk-v$VERSION-$MODE.zip"
VISUALS="TS18-StatusBar-Visuals-Magisk-v$VERSION-$MODE.zip"
LSPOSED="TS18-StatusBar-Input-LSPosed-v$VERSION-$MODE.apk"
BUNDLE="TS18-StatusBar-Bundle-v$VERSION-$MODE.zip"

for name in "$GEOMETRY" "$VISUALS" "$LSPOSED" "$BUNDLE" SHA256SUMS.txt; do
    [ -s "$DIST/$name" ] || {
        printf 'FAILED: missing or empty packaged artifact: %s\n' "$DIST/$name" >&2
        exit 2
    }
done

require_zip_entry() {
    archive="$1"
    entry="$2"
    unzip -Z1 "$archive" | grep -Fx "$entry" >/dev/null 2>&1 || {
        printf 'FAILED: %s lacks %s\n' "$archive" "$entry" >&2
        exit 2
    }
}

require_zip_entry "$DIST/$GEOMETRY" system/product/overlay/TS18StatusBarGeometry.apk
require_zip_entry "$DIST/$GEOMETRY" module.prop
require_zip_entry "$DIST/$GEOMETRY" customize.sh
require_zip_entry "$DIST/$VISUALS" system/product/overlay/TS18StatusBarVisuals.apk
require_zip_entry "$DIST/$VISUALS" module.prop
require_zip_entry "$DIST/$VISUALS" customize.sh

if { unzip -Z1 "$DIST/$GEOMETRY"; unzip -Z1 "$DIST/$VISUALS"; } 2>/dev/null \
        | grep -E '(^|/)module\.prop\.in$' >/dev/null 2>&1; then
    printf 'FAILED: template module.prop.in leaked into a Magisk artifact\n' >&2
    exit 2
fi

for entry in \
    "$GEOMETRY" \
    "$VISUALS" \
    "$LSPOSED" \
    INSTALL.md \
    RECOVERY.md \
    VALIDATION.md \
    ts18-statusbar-config.sh \
    ts18-systemui-contract.sh \
    ts18-statusbar-validate.sh
do
    require_zip_entry "$DIST/$BUNDLE" "$entry"
done

(cd "$DIST" && sha256sum -c SHA256SUMS.txt) >/dev/null || {
    printf 'FAILED: packaged SHA256SUMS validation failed\n' >&2
    exit 2
}

printf 'SUCCESS: geometry, visuals, LSPosed and recovery bundle package contract\n'
