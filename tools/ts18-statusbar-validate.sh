#!/system/bin/sh
# Read-only exact-device validation. Writes only the report under shared Download
# and a short-lived root scratch directory that is always cleaned.

OUT_DIR="/storage/emulated/0/Download/TS18-StatusBar"
TS="$(date '+%Y%m%d-%H%M%S' 2>/dev/null)"
OUT="$OUT_DIR/validation-$TS.txt"
WARNINGS=0

run_timeout() {
    secs="$1"
    shift
    if command -v timeout >/dev/null 2>&1; then
        timeout "$secs" "$@"
        return $?
    fi
    if command -v toybox >/dev/null 2>&1; then
        toybox timeout "$secs" "$@"
        return $?
    fi
    return 124
}

if [ "$(id -u 2>/dev/null)" != "0" ]; then
    echo "STOP: run under su/root so WindowManager/SystemUI inspection is authoritative." >&2
    exit 4
fi
if [ "$(getprop ro.build.version.sdk 2>/dev/null)" != "29" ]; then
    echo "STOP: this validator targets Android 10/API 29." >&2
    exit 4
fi
if ! command -v timeout >/dev/null 2>&1 && ! command -v toybox >/dev/null 2>&1; then
    echo "STOP: no timeout implementation is available; refusing unbounded dumpsys calls." >&2
    exit 4
fi
if ! mkdir -p "$OUT_DIR"; then
    echo "FAILED: cannot create $OUT_DIR" >&2
    exit 3
fi

WORK="$(mktemp -d /data/local/tmp/ts18-statusbar-validate.XXXXXX 2>/dev/null)"
if [ -z "$WORK" ] || [ ! -d "$WORK" ]; then
    echo "FAILED: cannot create bounded scratch directory under /data/local/tmp" >&2
    exit 3
fi
cleanup() {
    rm -rf "$WORK" 2>/dev/null
}
trap cleanup EXIT HUP INT TERM

capture() {
    name="$1"
    secs="$2"
    shift 2
    file="$WORK/$name.txt"
    run_timeout "$secs" "$@" > "$file" 2>&1
    rc=$?
    bytes="$(wc -c < "$file" 2>/dev/null | tr -d ' ')"
    case "$bytes" in ''|*[!0-9]*) bytes=0 ;; esac
    if [ "$bytes" -gt 2097152 ]; then
        head -c 2097152 "$file" > "$file.trim" 2>/dev/null
        mv -f "$file.trim" "$file"
        echo "[truncated at 2097152 bytes]" >> "$file"
    fi
    echo "$rc" > "$WORK/$name.rc"
    if [ "$rc" -ne 0 ]; then WARNINGS=$((WARNINGS + 1)); fi
}

capture overlay 15 cmd overlay list --user 0
capture window 25 dumpsys window windows
capture input 25 dumpsys input
capture package_systemui 15 dumpsys package com.android.systemui
capture display_size 10 wm size
capture display_density 10 wm density

{
    echo "TS18 status-bar validation"
    echo "timestamp=$(date '+%F %T %z' 2>/dev/null)"
    echo "identity=$(id 2>/dev/null)"
    echo "selinux=$(getenforce 2>/dev/null)"
    echo "boot_id=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
    echo "fingerprint=$(getprop ro.build.fingerprint 2>/dev/null)"
    echo "api=$(getprop ro.build.version.sdk 2>/dev/null)"
    echo ""
    echo "=== overlay list (status=$(cat "$WORK/overlay.rc")) ==="
    cat "$WORK/overlay.txt"
    echo ""
    echo "=== display (size status=$(cat "$WORK/display_size.rc"), density status=$(cat "$WORK/display_density.rc")) ==="
    cat "$WORK/display_size.txt"
    cat "$WORK/display_density.txt"
    echo ""
    echo "=== SystemUI package/overlay paths (status=$(cat "$WORK/package_systemui.rc")) ==="
    grep -E -i 'codePath=|versionCode=|versionName=|resourceDirs=|overlay paths:|framework-res_sysbar|userId=|sharedUser=' "$WORK/package_systemui.txt" | head -n 300
    echo ""
    echo "=== status/nav windows (status=$(cat "$WORK/window.rc")) ==="
    grep -E -i -A 18 -B 4 'StatusBar|NavigationBar0|TYPE_STATUS_BAR|mStatusBar|mNavigationBar' "$WORK/window.txt" | head -n 800
    echo ""
    echo "=== input windows/status touch regions (status=$(cat "$WORK/input.rc")) ==="
    grep -E -i -A 18 -B 4 'StatusBar|touchableRegion|NavigationBar0' "$WORK/input.txt" | head -n 800
    echo ""
    echo "=== module settings ==="
    for key in ts18_statusbar_policy_version ts18_statusbar_enabled ts18_statusbar_input_enabled ts18_statusbar_touch_fraction ts18_statusbar_visual_enabled ts18_statusbar_visual_scale ts18_statusbar_corner_gap_px ts18_statusbar_right_inset_px ts18_statusbar_debug; do
        value="$(run_timeout 10 settings get global "$key" 2>/dev/null)"
        rc=$?
        echo "$key=${value:-null} (status=$rc)"
        if [ "$rc" -ne 0 ]; then WARNINGS=$((WARNINGS + 1)); fi
    done
    echo ""
    if [ "$WARNINGS" -eq 0 ]; then
        echo "FINAL_STATUS=SUCCESS"
    else
        echo "FINAL_STATUS=COMPLETED WITH WARNINGS ($WARNINGS)"
    fi
} > "$OUT" 2>&1
rc=$?

if [ "$rc" -ne 0 ]; then
    echo "FAILED: report write status=$rc; partial report may exist: $OUT" >&2
    exit 2
fi
if [ "$WARNINGS" -eq 0 ]; then
    echo "SUCCESS: $OUT"
else
    echo "COMPLETED WITH WARNINGS ($WARNINGS): $OUT"
fi
