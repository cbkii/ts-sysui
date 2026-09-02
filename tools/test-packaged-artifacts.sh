#!/usr/bin/env bash
# Validate the combined user-facing package set produced by package-release.sh.

set -euo pipefail

MODE="${1:-debug}"
case "$MODE" in debug|diagnostic|release) ;; *) printf 'FAILED: mode must be debug, diagnostic or release\n' >&2; exit 3 ;; esac

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

MAGISK="TS18-SystemUI-Magisk-v$VERSION-$MODE.zip"
LSPOSED="TS18-SystemUI-LSPosed-v$VERSION-$MODE.apk"
BUNDLE="TS18-SystemUI-Bundle-v$VERSION-$MODE.zip"

for name in "$MAGISK" "$LSPOSED" "$BUNDLE" SHA256SUMS.txt; do
    [ -s "$DIST/$name" ] || {
        printf 'FAILED: missing or empty packaged artifact: %s\n' "$DIST/$name" >&2
        exit 2
    }
done
if [ "$MODE" = "diagnostic" ] && [ ! -s "$DIST/BUILD-INFO.txt" ]; then
    printf 'FAILED: diagnostic package lacks BUILD-INFO.txt\n' >&2
    exit 2
fi

# Normal output must no longer require two independently installed RRO modules.
if find "$DIST" -maxdepth 1 -type f \( \
        -name 'TS18-StatusBar-Geometry-Magisk-*' -o \
        -name 'TS18-StatusBar-Visuals-Magisk-*' \) | grep . >/dev/null 2>&1; then
    printf 'FAILED: legacy split Magisk release artifacts were produced\n' >&2
    exit 2
fi

require_zip_entry() {
    archive="$1"
    entry="$2"
    unzip -Z1 "$archive" | grep -Fx "$entry" >/dev/null 2>&1 || {
        printf 'FAILED: %s lacks %s\n' "$archive" "$entry" >&2
        exit 2
    }
}

require_zip_entry "$DIST/$MAGISK" system/product/overlay/TS18StatusBarGeometry.apk
require_zip_entry "$DIST/$MAGISK" system/product/overlay/TS18StatusBarVisuals.apk
require_zip_entry "$DIST/$MAGISK" module.prop
require_zip_entry "$DIST/$MAGISK" customize.sh

if unzip -Z1 "$DIST/$MAGISK" 2>/dev/null \
        | grep -E '(^|/)module\.prop\.in$' >/dev/null 2>&1; then
    printf 'FAILED: template module.prop.in leaked into combined Magisk artifact\n' >&2
    exit 2
fi

module_prop="$(unzip -p "$DIST/$MAGISK" module.prop)"
printf '%s\n' "$module_prop" | grep -Fx 'id=ts18_sysui' >/dev/null || {
    printf 'FAILED: combined Magisk artifact has wrong module ID\n' >&2
    exit 2
}
if [ "$MODE" = "diagnostic" ]; then
    printf '%s\n' "$module_prop" | grep -Fx "version=$VERSION-diagnostic" >/dev/null || {
        printf 'FAILED: diagnostic Magisk module is not visibly marked diagnostic\n' >&2
        exit 2
    }
fi

for entry in \
    "$MAGISK" \
    "$LSPOSED" \
    INSTALL.md \
    RECOVERY.md \
    VALIDATION.md \
    PHYSICAL-0.5.1-REMEDIATION.md \
    DIAGNOSTIC-BUILD-POLICY.md \
    ts18-statusbar-config.sh \
    ts18-systemui-contract.sh \
    ts18-statusbar-validate.sh \
    ts18-migrate-magisk-modules.sh
do
    require_zip_entry "$DIST/$BUNDLE" "$entry"
done
if [ "$MODE" = "diagnostic" ]; then
    require_zip_entry "$DIST/$BUNDLE" BUILD-INFO.txt
    grep -Fx 'mode=diagnostic' "$DIST/BUILD-INFO.txt" >/dev/null || {
        printf 'FAILED: diagnostic BUILD-INFO.txt lacks mode marker\n' >&2
        exit 2
    }
    grep -Fx 'diagnosticConsole=enabled' "$DIST/BUILD-INFO.txt" >/dev/null || {
        printf 'FAILED: diagnostic BUILD-INFO.txt lacks console marker\n' >&2
        exit 2
    }
fi

(cd "$DIST" && sha256sum -c SHA256SUMS.txt) >/dev/null || {
    printf 'FAILED: packaged SHA256SUMS validation failed\n' >&2
    exit 2
}

printf 'SUCCESS: single combined Magisk, LSPosed and recovery bundle %s package contract\n' "$MODE"
