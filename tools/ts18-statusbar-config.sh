#!/system/bin/sh
# Run under root on the exact TS18, e.g.:
#   su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh status'

cmd="${1:-status}"
arg="${2:-}"

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
    echo "STOP: no timeout implementation; refusing unbounded SettingsProvider operation." >&2
    return 124
}

api="$(getprop ro.build.version.sdk 2>/dev/null)"
device="$(getprop ro.product.device 2>/dev/null)"
product="$(getprop ro.product.name 2>/dev/null)"

if [ "$(id -u 2>/dev/null)" != "0" ]; then
    echo "STOP: root identity is required for Settings.Global mutation/status inspection." >&2
    exit 4
fi
if [ "$api" != "29" ]; then
    echo "STOP: expected API 29; detected ${api:-unknown}." >&2
    exit 4
fi
case "$device $product" in
    *s9863a1h10*) ;;
    *) echo "STOP: target identity is not the exact known s9863a1h10 TS18: device=$device product=$product" >&2; exit 4 ;;
esac

put() {
    key="$1"
    value="$2"
    if run_timeout 10 settings put global "$key" "$value"; then
        echo "set $key=$value"
    else
        rc=$?
        echo "FAILED: settings put global $key status=$rc" >&2
        exit 2
    fi
}
get() {
    key="$1"
    run_timeout 10 settings get global "$key"
}
show() {
    for key in \
        ts18_statusbar_enabled \
        ts18_statusbar_input_enabled \
        ts18_statusbar_touch_fraction \
        ts18_statusbar_corner_gap_px \
        ts18_statusbar_visual_enabled \
        ts18_statusbar_visual_scale \
        ts18_statusbar_right_inset_px \
        ts18_statusbar_window_height_normalise \
        ts18_statusbar_debug
    do
        value="$(get "$key" 2>/dev/null)"
        rc=$?
        if [ "$rc" -eq 0 ]; then
            echo "$key=${value:-null}"
        else
            echo "$key=<read-failed:$rc>"
        fi
    done
}

valid_fraction() {
    case "$1" in
        ''|*[!0-9.]*|*.*.*) return 1 ;;
    esac
    # POSIX sh has no floating comparison. awk is present on the target family;
    # bound the check so malformed input cannot silently exceed the hard cap.
    if command -v awk >/dev/null 2>&1; then
        awk -v v="$1" 'BEGIN { exit !(v >= 0.05 && v <= 0.20) }'
        return $?
    fi
    echo "STOP: awk unavailable; cannot safely validate a floating fraction." >&2
    return 1
}

valid_gap() {
    case "$1" in
        ''|*[!0-9]*) return 1 ;;
    esac
    [ "$1" -ge 64 ] 2>/dev/null && [ "$1" -le 2048 ] 2>/dev/null
}

case "$cmd" in
    status) show ;;
    enable) put ts18_statusbar_enabled 1 ;;
    disable) put ts18_statusbar_enabled 0 ;;
    strict)
        put ts18_statusbar_touch_fraction 0.20
        put ts18_statusbar_corner_gap_px 64
        ;;
    touch-fraction)
        if ! valid_fraction "$arg"; then
            echo "STOP: touch fraction must be between 0.05 and 0.20 inclusive." >&2
            exit 4
        fi
        put ts18_statusbar_touch_fraction "$arg"
        ;;
    corner-gap)
        if ! valid_gap "$arg"; then
            echo "STOP: corner gap must be an integer >=64 and <=2048 px." >&2
            exit 4
        fi
        put ts18_statusbar_corner_gap_px "$arg"
        ;;
    visual-on) put ts18_statusbar_visual_enabled 1 ;;
    visual-off) put ts18_statusbar_visual_enabled 0 ;;
    debug-on) put ts18_statusbar_debug 1 ;;
    debug-off) put ts18_statusbar_debug 0 ;;
    *)
        echo "Usage: $0 {status|enable|disable|strict|touch-fraction 0.05..0.20|corner-gap >=64|visual-on|visual-off|debug-on|debug-off}" >&2
        exit 3
        ;;
esac

echo "SUCCESS"
