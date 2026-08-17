#!/system/bin/sh
# Collect the minimum exact-device evidence required before right-nav mutation.
# Read-only with respect to Android/SystemUI; writes copies/reports only to Download.

OUT_ROOT="/storage/emulated/0/Download/TS18-StatusBar"
TS="$(date '+%Y%m%d-%H%M%S' 2>/dev/null)"
OUT_DIR="$OUT_ROOT/right-nav-evidence-$TS"
REPORT="$OUT_DIR/report.txt"
WARNINGS=0
APK_COUNT=0

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

warn() {
    WARNINGS=$((WARNINGS + 1))
    echo "WARNING: $*" >&2
}

if [ "$(id -u 2>/dev/null)" != "0" ]; then
    echo "STOP: run under su/root so SystemUI package paths and APK copies are authoritative." >&2
    exit 4
fi
if [ "$(getprop ro.build.version.sdk 2>/dev/null)" != "29" ]; then
    echo "STOP: this collector targets the exact Android 10/API 29 TS18." >&2
    exit 4
fi
device="$(getprop ro.product.device 2>/dev/null)"
product="$(getprop ro.product.name 2>/dev/null)"
case "$device $product" in
    *s9863a1h10*) ;;
    *)
        echo "STOP: target identity is not the exact known s9863a1h10 TS18: device=$device product=$product" >&2
        exit 4
        ;;
esac
if ! command -v timeout >/dev/null 2>&1 && ! command -v toybox >/dev/null 2>&1; then
    echo "STOP: no timeout implementation is available; refusing unbounded package/log operations." >&2
    exit 4
fi
if ! command -v sha256sum >/dev/null 2>&1; then
    echo "STOP: sha256sum is required to establish SystemUI binary identity." >&2
    exit 4
fi
if ! mkdir -p "$OUT_DIR"; then
    echo "FAILED: cannot create $OUT_DIR" >&2
    exit 3
fi

WORK="$(mktemp -d /data/local/tmp/ts18-right-nav-evidence.XXXXXX 2>/dev/null)"
if [ -z "$WORK" ] || [ ! -d "$WORK" ]; then
    echo "FAILED: cannot create bounded scratch directory under /data/local/tmp" >&2
    exit 3
fi
cleanup() {
    rm -rf "$WORK" 2>/dev/null
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

capture() {
    name="$1"
    secs="$2"
    shift 2
    file="$WORK/$name.txt"
    run_timeout "$secs" "$@" > "$file" 2>&1
    rc=$?
    bytes="$(wc -c < "$file" 2>/dev/null | tr -d ' ')"
    case "$bytes" in ''|*[!0-9]*) bytes=0 ;; esac
    if [ "$bytes" -gt 1048576 ]; then
        head -c 1048576 "$file" > "$file.trim" 2>/dev/null
        mv -f "$file.trim" "$file"
        echo "[truncated at 1048576 bytes]" >> "$file"
    fi
    echo "$rc" > "$WORK/$name.rc"
    if [ "$rc" -ne 0 ]; then WARNINGS=$((WARNINGS + 1)); fi
}

capture pm_path 15 pm path com.android.systemui
capture package_systemui 20 dumpsys package com.android.systemui
capture window 25 dumpsys window windows
capture display_size 10 wm size
capture display_density 10 wm density
capture logcat 15 logcat -d -v threadtime -t 2500

if [ "$(cat "$WORK/pm_path.rc" 2>/dev/null)" = "0" ]; then
    index=0
    while IFS= read -r line; do
        case "$line" in
            package:/*.apk)
                apk="${line#package:}"
                index=$((index + 1))
                if [ ! -f "$apk" ]; then
                    warn "SystemUI package path is not a readable file: $apk"
                    continue
                fi
                target="$OUT_DIR/SystemUI-$index.apk"
                if cp -p "$apk" "$target" 2>/dev/null || cp "$apk" "$target" 2>/dev/null; then
                    APK_COUNT=$((APK_COUNT + 1))
                    sha256sum "$target" >> "$OUT_DIR/SHA256SUMS-SystemUI.txt" 2>/dev/null || \
                        warn "could not hash copied APK $target"
                    {
                        echo "copy=$target"
                        echo "source=$apk"
                        ls -lZ "$apk" 2>/dev/null || ls -l "$apk" 2>/dev/null
                    } >> "$OUT_DIR/SystemUI-paths.txt"
                else
                    warn "could not copy SystemUI APK from $apk"
                fi
                ;;
            '') ;;
            *) warn "unexpected pm path output: $line" ;;
        esac
    done < "$WORK/pm_path.txt"
else
    warn "pm path com.android.systemui failed with status $(cat "$WORK/pm_path.rc" 2>/dev/null)"
fi

{
    echo "TS18 right-navigation evidence"
    echo "timestamp=$(date '+%F %T %z' 2>/dev/null)"
    echo "identity=$(id 2>/dev/null)"
    echo "selinux=$(getenforce 2>/dev/null)"
    echo "boot_id=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
    echo "fingerprint=$(getprop ro.build.fingerprint 2>/dev/null)"
    echo "build_display=$(getprop ro.build.display.id 2>/dev/null)"
    echo "api=$(getprop ro.build.version.sdk 2>/dev/null)"
    echo "security_patch=$(getprop ro.build.version.security_patch 2>/dev/null)"
    echo "device=$device"
    echo "product=$product"
    echo "copied_systemui_apks=$APK_COUNT"
    echo ""
    echo "=== pm path com.android.systemui (status=$(cat "$WORK/pm_path.rc")) ==="
    cat "$WORK/pm_path.txt"
    echo ""
    echo "=== SystemUI package summary (status=$(cat "$WORK/package_systemui.rc")) ==="
    grep -E -i 'codePath=|path:|versionCode=|versionName=|userId=|sharedUser=|pkgFlags=|privateFlags=|resourceDirs=|overlay paths:|granted=true|MEDIA_CONTENT_CONTROL' \
        "$WORK/package_systemui.txt" | head -n 500
    echo ""
    echo "=== display (size status=$(cat "$WORK/display_size.rc"), density status=$(cat "$WORK/display_density.rc")) ==="
    cat "$WORK/display_size.txt"
    cat "$WORK/display_density.txt"
    echo ""
    echo "=== status/navigation windows (status=$(cat "$WORK/window.rc")) ==="
    grep -E -i -A 22 -B 5 'StatusBar|NavigationBar|TYPE_STATUS_BAR|TYPE_NAVIGATION_BAR|mStatusBar|mNavigationBar' \
        "$WORK/window.txt" | head -n 1200
    echo ""
    echo "=== right-nav settings ==="
    for key in \
        ts18_statusbar_nav_policy_version \
        ts18_statusbar_nav_enabled \
        ts18_statusbar_nav_probe_enabled \
        ts18_statusbar_nav_actions \
        ts18_statusbar_nav_min_touch_dp \
        ts18_statusbar_nav_debug
    do
        value="$(run_timeout 10 settings get global "$key" 2>/dev/null)"
        rc=$?
        echo "$key=${value:-null} (status=$rc)"
        if [ "$rc" -ne 0 ]; then WARNINGS=$((WARNINGS + 1)); fi
    done
    echo ""
    echo "=== bounded TS18/right-nav logs (status=$(cat "$WORK/logcat.rc")) ==="
    grep -E -i 'TS18StatusBar|right-nav probe|right-nav circuit breaker' "$WORK/logcat.txt" | tail -n 1200
    echo ""
    if [ -f "$OUT_DIR/SHA256SUMS-SystemUI.txt" ]; then
        echo "=== copied SystemUI SHA-256 ==="
        cat "$OUT_DIR/SHA256SUMS-SystemUI.txt"
        echo ""
    fi
    if [ "$APK_COUNT" -eq 0 ]; then
        echo "EVIDENCE_GATE=BLOCKED: no current SystemUI APK was copied"
    else
        echo "EVIDENCE_GATE=PARTIAL: SystemUI identity captured; Stage N0 hierarchy/lifecycle qualification still required"
    fi
    if [ "$WARNINGS" -eq 0 ]; then
        echo "FINAL_STATUS=SUCCESS"
    else
        echo "FINAL_STATUS=COMPLETED WITH WARNINGS ($WARNINGS)"
    fi
} > "$REPORT" 2>&1
rc=$?

if [ "$rc" -ne 0 ]; then
    echo "FAILED: report write status=$rc; partial evidence may exist: $OUT_DIR" >&2
    exit 2
fi

# Zip is optional on the stock target; never fail evidence collection only because
# an archiver is unavailable. APK/report files remain directly accessible.
if command -v zip >/dev/null 2>&1; then
    archive="$OUT_ROOT/right-nav-evidence-$TS.zip"
    if (cd "$OUT_DIR" && zip -qr "$archive" .); then
        echo "ARCHIVE: $archive"
    else
        echo "WARNING: optional zip packaging failed; use the evidence directory directly" >&2
    fi
fi

if [ "$WARNINGS" -eq 0 ]; then
    echo "SUCCESS: $OUT_DIR"
else
    echo "COMPLETED WITH WARNINGS ($WARNINGS): $OUT_DIR"
fi
