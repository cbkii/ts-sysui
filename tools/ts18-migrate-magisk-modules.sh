#!/system/bin/sh
# Explicit one-time migration helper. Marks only the two known legacy ts-sysui
# module IDs for removal; Magisk performs the removal on reboot.

if [ "$(id -u 2>/dev/null)" != "0" ]; then
    echo "STOP: run this helper under root." >&2
    exit 4
fi

api="$(getprop ro.build.version.sdk 2>/dev/null)"
device="$(getprop ro.product.device 2>/dev/null)"
product="$(getprop ro.product.name 2>/dev/null)"
if [ "$api" != "29" ]; then
    echo "STOP: expected API 29; detected ${api:-unknown}." >&2
    exit 4
fi
case "$device $product" in
    *s9863a1h10*) ;;
    *) echo "STOP: not the exact s9863a1h10 target: device=$device product=$product" >&2; exit 4 ;;
esac

changed=0
for legacy in ts18_statusbar_geometry ts18_statusbar_visuals; do
    path="/data/adb/modules/$legacy"
    if [ -d "$path" ]; then
        if : > "$path/remove"; then
            echo "marked for Magisk removal: $legacy"
            changed=$((changed + 1))
        else
            echo "FAILED: could not mark $legacy for removal." >&2
            exit 2
        fi
    else
        echo "not installed: $legacy"
    fi
done

echo "Marked $changed legacy module(s). Reboot now. After reboot confirm the old IDs are gone, then install the combined ts18_sysui Magisk ZIP."
echo "No module directory or Android partition was deleted directly."
