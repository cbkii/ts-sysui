#!/system/bin/sh
# Exact-device fallback configuration helper for the TS18 brightness controller.
# Preferred user UI is the TS18 Brightness launcher activity through the exact-SystemUI configuration bridge.

cmd="${1:-status}"
arg1="${2:-}"
arg2="${3:-}"

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
    echo "STOP: root identity is required for brightness Settings.Global configuration." >&2
    exit 4
fi
if [ "$api" != "29" ]; then
    echo "STOP: expected API 29; detected ${api:-unknown}." >&2
    exit 4
fi
case "$device $product" in
    *s9863a1h10*) ;;
    *) echo "STOP: target is not the exact known s9863a1h10 TS18: device=$device product=$product" >&2; exit 4 ;;
esac

put() {
    key="$1"
    value="$2"
    if run_timeout 10 settings put global "$key" "$value"; then
        echo "set $key=$value"
        return 0
    fi
    rc=$?
    echo "FAILED: settings put global $key status=$rc" >&2
    return "$rc"
}

get() {
    run_timeout 10 settings get global "$1"
}

required_put() {
    put "$1" "$2"
    rc=$?
    if [ "$rc" -ne 0 ]; then
        echo "FAILED: required brightness configuration write did not complete." >&2
        exit 2
    fi
}

show_key() {
    key="$1"
    value="$(get "$key" 2>/dev/null)"
    rc=$?
    if [ "$rc" -eq 0 ]; then
        echo "$key=${value:-null}"
    else
        echo "$key=<read-failed:$rc>"
    fi
}

prepare_generation() {
    current="$(get ts18_brightness_policy_version 2>/dev/null)"
    rc=$?
    if [ "$rc" -ne 0 ]; then
        echo "STOP: cannot read brightness policy generation (status=$rc)." >&2
        exit 4
    fi
    if [ "$current" = "1" ]; then
        return 0
    fi
    # Old/unknown values cannot arm a new policy generation. Publish generation last.
    required_put ts18_brightness_enabled 0
    required_put ts18_brightness_policy_version 1
}

valid_mode() {
    case "$1" in auto|day|night|set_auto) return 0 ;; *) return 1 ;; esac
}

valid_level() {
    [ "$1" = "current" ] && return 0
    case "$1" in ''|*[!0-9]*) return 1 ;; esac
    [ "$1" -ge 1 ] 2>/dev/null && [ "$1" -le 10 ] 2>/dev/null
}

parse_time_portable() {
    value="$1"
    case "$value" in
        [0-9][0-9]:[0-9][0-9]) ;;
        *) return 1 ;;
    esac
    hour=${value%:*}
    minute=${value#*:}
    case "$hour$minute" in *[!0-9]*) return 1 ;; esac
    case "$hour" in 0[0-9]) hour=${hour#0} ;; esac
    case "$minute" in 0[0-9]) minute=${minute#0} ;; esac
    [ -n "$hour" ] || hour=0
    [ -n "$minute" ] || minute=0
    [ "$hour" -ge 0 ] 2>/dev/null && [ "$hour" -le 23 ] 2>/dev/null \
        && [ "$minute" -ge 0 ] 2>/dev/null && [ "$minute" -le 59 ] 2>/dev/null || return 1
    PARSED_MINUTE=$((hour * 60 + minute))
    return 0
}

format_minute() {
    total="$1"
    if command -v awk >/dev/null 2>&1; then
        awk -v m="$total" 'BEGIN { printf "%02d:%02d", int(m/60), m%60 }'
    else
        printf '%02d:%02d' "$((total / 60))" "$((total % 60))"
    fi
}

show_status() {
    for key in \
        ts18_brightness_policy_version \
        ts18_brightness_enabled \
        ts18_brightness_mode \
        ts18_brightness_day_level \
        ts18_brightness_night_level \
        ts18_brightness_day_start_minute \
        ts18_brightness_night_start_minute \
        ts18_brightness_debug
    do
        show_key "$key"
    done
    echo "required_policy_version=1"
    echo "mode values: auto | day | night | set_auto"
    echo "levels: 1..10; -1/current preserves the slot; level 0 is intentionally unsupported"
    echo "set_auto: explicit clock-scheduled Day/Night; does not depend on stock ILL/headlight Auto"
}

case "$cmd" in
    status)
        show_status
        ;;
    observe)
        prepare_generation
        required_put ts18_brightness_enabled 0
        required_put ts18_brightness_debug 1
        echo "Brightness observation configured; mutation remains disabled. Restart SystemUI/reboot before evaluating hook logs."
        ;;
    enable)
        prepare_generation
        required_put ts18_brightness_enabled 1
        ;;
    disable)
        prepare_generation
        required_put ts18_brightness_enabled 0
        ;;
    mode)
        if ! valid_mode "$arg1"; then
            echo "STOP: mode must be auto, day, night or set_auto." >&2
            exit 4
        fi
        prepare_generation
        required_put ts18_brightness_mode "$arg1"
        ;;
    day-level|night-level)
        if ! valid_level "$arg1"; then
            echo "STOP: level must be current or an integer from 1 through 10; level 0 is not enabled." >&2
            exit 4
        fi
        value="$arg1"
        [ "$value" = "current" ] && value=-1
        prepare_generation
        if [ "$cmd" = day-level ]; then
            required_put ts18_brightness_day_level "$value"
        else
            required_put ts18_brightness_night_level "$value"
        fi
        ;;
    day-start|night-start)
        if ! parse_time_portable "$arg1"; then
            echo "STOP: time must be HH:MM in 24-hour local time." >&2
            exit 4
        fi
        prepare_generation
        if [ "$cmd" = day-start ]; then
            required_put ts18_brightness_day_start_minute "$PARSED_MINUTE"
        else
            required_put ts18_brightness_night_start_minute "$PARSED_MINUTE"
        fi
        ;;
    set-auto)
        if ! parse_time_portable "$arg1"; then
            echo "STOP: set-auto day time must be HH:MM." >&2
            exit 4
        fi
        day_value="$PARSED_MINUTE"
        if ! parse_time_portable "$arg2"; then
            echo "STOP: set-auto night time must be HH:MM." >&2
            exit 4
        fi
        night_value="$PARSED_MINUTE"
        if [ "$day_value" -eq "$night_value" ]; then
            echo "STOP: day and night transition times must differ." >&2
            exit 4
        fi
        prepare_generation
        # Disable while publishing a coherent schedule, arm only after all values are set.
        required_put ts18_brightness_enabled 0
        required_put ts18_brightness_day_start_minute "$day_value"
        required_put ts18_brightness_night_start_minute "$night_value"
        required_put ts18_brightness_mode set_auto
        required_put ts18_brightness_enabled 1
        echo "Scheduled auto armed: Day $(format_minute "$day_value"), Night $(format_minute "$night_value")."
        ;;
    debug-on)
        prepare_generation
        required_put ts18_brightness_debug 1
        ;;
    debug-off)
        prepare_generation
        required_put ts18_brightness_debug 0
        ;;
    reset)
        prepare_generation
        required_put ts18_brightness_enabled 0
        required_put ts18_brightness_mode auto
        required_put ts18_brightness_day_level -1
        required_put ts18_brightness_night_level -1
        required_put ts18_brightness_day_start_minute 420
        required_put ts18_brightness_night_start_minute 1140
        required_put ts18_brightness_debug 0
        echo "Brightness policy reset: disabled, stock Auto selected, levels preserved, set-auto defaults 07:00/19:00."
        ;;
    *)
        echo "Usage: $0 {status|observe|enable|disable|mode <auto|day|night|set_auto>|day-level <current|1..10>|night-level <current|1..10>|day-start HH:MM|night-start HH:MM|set-auto DAY_HH:MM NIGHT_HH:MM|debug-on|debug-off|reset}" >&2
        exit 3
        ;;
esac

echo "SUCCESS"
