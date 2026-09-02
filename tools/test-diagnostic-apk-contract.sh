#!/usr/bin/env bash
# Validate that the installable diagnostic APK is release-compatible but visibly
# diagnostic, and that production release does not expose its console.

set -euo pipefail

MODE="${1:-}"
APK="${2:-}"
case "$MODE" in diagnostic|release) ;; *) printf 'FAILED: mode must be diagnostic or release\n' >&2; exit 3 ;; esac
[ -n "$APK" ] && [ -s "$APK" ] || { printf 'FAILED: pass an assembled APK path\n' >&2; exit 3; }

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
VERSION="$(sed -n 's/^versionName=//p' "$ROOT/version.properties" | head -n1)"
VERSION_CODE="$(sed -n 's/^versionCode=//p' "$ROOT/version.properties" | head -n1)"

AAPT=""
if command -v aapt >/dev/null 2>&1; then
    AAPT="$(command -v aapt)"
else
    SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [ -n "$SDK_ROOT" ] && [ -x "$SDK_ROOT/build-tools/35.0.0/aapt" ]; then
        AAPT="$SDK_ROOT/build-tools/35.0.0/aapt"
    fi
fi
[ -n "$AAPT" ] || { printf 'FAILED: aapt from Android build-tools 35.0.0 is required\n' >&2; exit 3; }

BADGING="$($AAPT dump badging "$APK")" || { printf 'FAILED: aapt badging failed\n' >&2; exit 2; }
XML="$($AAPT dump xmltree "$APK" AndroidManifest.xml)" || { printf 'FAILED: aapt manifest dump failed\n' >&2; exit 2; }

printf '%s\n' "$BADGING" | grep -F "package: name='au.com.cb.ts18.statusbar.input'" >/dev/null || {
    printf 'FAILED: APK application ID drifted\n' >&2
    exit 2
}
printf '%s\n' "$BADGING" | grep -F "versionCode='$VERSION_CODE'" >/dev/null || {
    printf 'FAILED: APK versionCode does not match version.properties\n' >&2
    exit 2
}

if [ "$MODE" = diagnostic ]; then
    printf '%s\n' "$BADGING" | grep -F "versionName='$VERSION-diagnostic'" >/dev/null || {
        printf 'FAILED: diagnostic APK lacks -diagnostic versionName suffix\n' >&2
        exit 2
    }
    printf '%s\n' "$BADGING" | grep -F 'application-debuggable' >/dev/null || {
        printf 'FAILED: diagnostic APK is not debuggable\n' >&2
        exit 2
    }
    printf '%s\n' "$XML" | grep -F 'au.com.cb.ts18.statusbar.input.DiagnosticSettingsActivity' >/dev/null || {
        printf 'FAILED: diagnostic APK does not expose DiagnosticSettingsActivity\n' >&2
        exit 2
    }
    printf '%s\n' "$XML" | grep -F 'TS18 Diagnostic Console' >/dev/null || {
        printf 'FAILED: diagnostic console label missing\n' >&2
        exit 2
    }
else
    printf '%s\n' "$BADGING" | grep -F "versionName='$VERSION'" >/dev/null || {
        printf 'FAILED: release APK versionName drifted\n' >&2
        exit 2
    }
    if printf '%s\n' "$BADGING" | grep -F 'application-debuggable' >/dev/null; then
        printf 'FAILED: production release APK is debuggable\n' >&2
        exit 2
    fi
    if printf '%s\n' "$XML" | grep -F 'au.com.cb.ts18.statusbar.input.DiagnosticSettingsActivity' >/dev/null; then
        printf 'FAILED: production release exposes DiagnosticSettingsActivity\n' >&2
        exit 2
    fi
fi

printf 'SUCCESS: %s APK identity/debuggability/diagnostic-console contract\n' "$MODE"
