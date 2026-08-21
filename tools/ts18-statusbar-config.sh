#!/system/bin/sh
# Run under root on the exact TS18, e.g.:
#   su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh status'

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
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
show_compact() {
    for key in \
        ts18_statusbar_policy_version \
        ts18_statusbar_enabled \
        ts18_statusbar_input_enabled \
        ts18_statusbar_touch_adapter_mode \
        ts18_statusbar_touch_fraction \
        ts18_statusbar_corner_gap_px \
        ts18_statusbar_right_inset_px \
        ts18_statusbar_debug
    do
        show_key "$key"
    done
    echo "required_policy_version=4; without it compact runtime defaults remain observation-only"
    echo "compact defaults: master=off input=off adapter=exact fraction=0.20 corner_gap=64"
    echo "status visuals are owned by the independently disableable Magisk RRO, not LSPosed settings"
}
show_visual_rro() {
    echo "visual_overlay_package=au.com.cb.ts18.statusbar.visual.overlay"
    output="$(run_timeout 10 cmd overlay list 2>/dev/null)"
    rc=$?
    if [ "$rc" -ne 0 ]; then
        echo "visual_overlay_state=<inspection-failed:$rc>"
        return 0
    fi
    match="$(printf '%s\n' "$output" | grep 'au.com.cb.ts18.statusbar.visual.overlay' | sed -n '1p')"
    if [ -n "$match" ]; then
        echo "visual_overlay_state=$match"
    else
        echo "visual_overlay_state=not-found-in-cmd-overlay-list"
    fi
}
show_nav() {
    for key in \
        ts18_statusbar_nav_policy_version \
        ts18_statusbar_nav_enabled \
        ts18_statusbar_nav_probe_enabled \
        ts18_statusbar_nav_actions \
        ts18_statusbar_nav_min_touch_dp \
        ts18_statusbar_nav_debug
    do
        show_key "$key"
    done
    echo "required_nav_policy_version=2"
    echo "nav defaults: mutation=off probe=off actions=previous,play_pause,next min_touch_dp=56 debug=off"
    echo "nav mutation status: optional exact-host implementation; exact APK verification and runtime preflight required"
}

prepare_policy_generation() {
    current="$(get ts18_statusbar_policy_version 2>/dev/null)"
    rc=$?
    if [ "$rc" -ne 0 ]; then
        echo "STOP: cannot read current TS18 status-bar policy generation (status=$rc)." >&2
        exit 4
    fi
    if [ "$current" = "4" ]; then
        return 0
    fi

    # Migration guard: clear mutation flags first and publish generation LAST.
    put ts18_statusbar_enabled 0
    put ts18_statusbar_input_enabled 0
    # Clear the retired recursive visual setting before publishing generation 4.
    put ts18_statusbar_visual_enabled 0
    put ts18_statusbar_touch_adapter_mode exact
    put ts18_statusbar_policy_version 4
}

require_exact_contract() {
    verifier="$SCRIPT_DIR/ts18-systemui-contract.sh"
    if [ ! -r "$verifier" ]; then
        verifier="/storage/emulated/0/Download/ts18-systemui-contract.sh"
    fi
    if [ ! -r "$verifier" ]; then
        echo "STOP: exact contract verifier is missing; copy ts18-systemui-contract.sh beside this tool." >&2
        exit 4
    fi
    if ! sh "$verifier"; then
        echo "STOP: exact SystemUI verification failed; requested SystemUI mutation remains off." >&2
        exit 4
    fi
}

prepare_nav_policy_generation() {
    current="$(get ts18_statusbar_nav_policy_version 2>/dev/null)"
    rc=$?
    if [ "$rc" -ne 0 ]; then
        echo "STOP: cannot read current right-nav policy generation (status=$rc)." >&2
        exit 4
    fi
    if [ "$current" = "2" ]; then
        return 0
    fi

    # Publish the nav generation only after all mutation/observation flags are safe.
    put ts18_statusbar_nav_enabled 0
    put ts18_statusbar_nav_probe_enabled 0
    put ts18_statusbar_nav_policy_version 2
}

valid_fraction() {
    case "$1" in
        ''|*[!0-9.]*|*.*.*) return 1 ;;
    esac
    if command -v awk >/dev/null 2>&1; then
        awk -v v="$1" 'BEGIN { exit !(v >= 0.01 && v <= 0.20) }'
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

valid_nav_touch_dp() {
    case "$1" in
        ''|*[!0-9]*) return 1 ;;
    esac
    [ "$1" -ge 48 ] 2>/dev/null && [ "$1" -le 96 ] 2>/dev/null
}

valid_nav_actions() {
    value="$1"
    [ "$value" = "none" ] && return 0
    case "$value" in
        ''|,*|*,|*,,*|*[!a-z_,]*) return 1 ;;
    esac

    old_ifs="$IFS"
    IFS=,
    set -- $value
    IFS="$old_ifs"
    seen=","
    for token in "$@"; do
        case "$token" in
            previous|play_pause|next) ;;
            *) return 1 ;;
        esac
        case "$seen" in
            *,"$token",*) return 1 ;;
        esac
        seen="${seen}${token},"
    done
    return 0
}

case "$cmd" in
    status)
        show_compact
        show_visual_rro
        show_nav
        ;;
    observe)
        prepare_policy_generation
        put ts18_statusbar_enabled 0
        put ts18_statusbar_input_enabled 0
        put ts18_statusbar_debug 1
        echo "Compact observation-only mode configured. Restart SystemUI/reboot to confirm hook load before arming input."
        ;;
    enable) prepare_policy_generation; put ts18_statusbar_enabled 1 ;;
    disable) prepare_policy_generation; put ts18_statusbar_enabled 0 ;;
    disarm)
        prepare_policy_generation
        put ts18_statusbar_input_enabled 0
        put ts18_statusbar_enabled 0
        echo "All compact LSPosed mutations disarmed. Disable the visual Magisk module separately if installed."
        ;;
    input-on)
        prepare_policy_generation
        adapter="$(get ts18_statusbar_touch_adapter_mode 2>/dev/null)"
        case "$adapter" in
            exact|null|'') require_exact_contract ;;
            compatibility|compat) ;;
            *) echo "STOP: touch adapter is off or invalid; choose exact or compatibility first." >&2; exit 4 ;;
        esac
        put ts18_statusbar_enabled 1
        put ts18_statusbar_input_enabled 1
        ;;
    input-off) prepare_policy_generation; put ts18_statusbar_input_enabled 0 ;;
    touch-adapter)
        case "$arg" in
            exact|compatibility|off) ;;
            *) echo "STOP: touch adapter must be exact, compatibility or off." >&2; exit 4 ;;
        esac
        prepare_policy_generation
        put ts18_statusbar_input_enabled 0
        put ts18_statusbar_touch_adapter_mode "$arg"
        echo "Touch adapter changed with compact input disarmed. Run input-on explicitly after validation."
        ;;
    strict)
        prepare_policy_generation
        put ts18_statusbar_touch_fraction 0.20
        put ts18_statusbar_corner_gap_px 64
        ;;
    touch-fraction)
        if ! valid_fraction "$arg"; then
            echo "STOP: touch fraction must be between 0.01 and 0.20 inclusive." >&2
            exit 4
        fi
        prepare_policy_generation
        put ts18_statusbar_touch_fraction "$arg"
        ;;
    corner-gap)
        if ! valid_gap "$arg"; then
            echo "STOP: corner gap must be an integer >=64 and <=2048 px." >&2
            exit 4
        fi
        prepare_policy_generation
        put ts18_statusbar_corner_gap_px "$arg"
        ;;
    visual-status) show_visual_rro ;;
    debug-on) prepare_policy_generation; put ts18_statusbar_debug 1 ;;
    debug-off) prepare_policy_generation; put ts18_statusbar_debug 0 ;;
    nav-status)
        show_nav
        ;;
    nav-observe)
        prepare_nav_policy_generation
        put ts18_statusbar_nav_enabled 0
        put ts18_statusbar_nav_probe_enabled 1
        put ts18_statusbar_nav_debug 1
        echo "Right-nav observation armed with mutation off. Restart SystemUI/reboot, collect the exact-host snapshot, then nav-probe-off."
        ;;
    nav-probe-off)
        prepare_nav_policy_generation
        put ts18_statusbar_nav_probe_enabled 0
        echo "Right-nav hierarchy probe disabled. Restart SystemUI/reboot if an immediate listener teardown cannot be observed."
        ;;
    nav-enable)
        prepare_nav_policy_generation
        require_exact_contract
        actions="$(get ts18_statusbar_nav_actions 2>/dev/null)"
        [ "$actions" = "null" ] && actions="previous,play_pause,next"
        if ! valid_nav_actions "$actions" || [ "$actions" = "none" ]; then
            echo "STOP: choose at least one valid nav action before nav-enable." >&2
            exit 4
        fi
        min_touch_dp="$(get ts18_statusbar_nav_min_touch_dp 2>/dev/null)"
        [ "$min_touch_dp" = "null" ] && min_touch_dp=56
        if ! valid_nav_touch_dp "$min_touch_dp" || [ "$min_touch_dp" -lt 56 ]; then
            echo "STOP: production nav-enable requires min_touch_dp >=56; 48dp is a STOP floor, not a fallback." >&2
            exit 4
        fi
        put ts18_statusbar_nav_enabled 1
        echo "Exact right-nav media controls armed. Runtime hash, topology and measured touch-size gates may still keep the navbar stock. Restart SystemUI/reboot, then validate physically."
        ;;
    nav-disable)
        prepare_nav_policy_generation
        put ts18_statusbar_nav_enabled 0
        ;;
    nav-actions)
        if ! valid_nav_actions "$arg"; then
            echo "STOP: nav actions must be a unique comma-separated subset/order of previous,play_pause,next, or 'none'." >&2
            exit 4
        fi
        prepare_nav_policy_generation
        put ts18_statusbar_nav_actions "$arg"
        ;;
    nav-min-touch-dp)
        if ! valid_nav_touch_dp "$arg"; then
            echo "STOP: right-nav touch target must be an integer from 48 through 96 dp; production target is 56 dp." >&2
            exit 4
        fi
        prepare_nav_policy_generation
        put ts18_statusbar_nav_min_touch_dp "$arg"
        ;;
    nav-debug-on)
        prepare_nav_policy_generation
        put ts18_statusbar_nav_debug 1
        ;;
    nav-debug-off)
        prepare_nav_policy_generation
        put ts18_statusbar_nav_debug 0
        ;;
    nav-reset)
        prepare_nav_policy_generation
        put ts18_statusbar_nav_enabled 0
        put ts18_statusbar_nav_probe_enabled 0
        put ts18_statusbar_nav_actions previous,play_pause,next
        put ts18_statusbar_nav_min_touch_dp 56
        put ts18_statusbar_nav_debug 0
        echo "Right-nav settings reset to observation/mutation off defaults."
        ;;
    *)
        echo "Usage: $0 {status|observe|enable|disable|disarm|input-on|input-off|touch-adapter exact|compatibility|off|strict|touch-fraction 0.01..0.20|corner-gap >=64|visual-status|debug-on|debug-off|nav-status|nav-observe|nav-probe-off|nav-enable|nav-disable|nav-actions <list|none>|nav-min-touch-dp 48..96|nav-debug-on|nav-debug-off|nav-reset}" >&2
        exit 3
        ;;
esac

echo "SUCCESS"
