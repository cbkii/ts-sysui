#!/system/bin/sh
# Magisk installer context. Required operations fail closed; no partition writes.

ui_print "- TS18 System UI combined RRO module"
ui_print "- Validating exact target, payloads and legacy-module state"

api="${API:-$(getprop ro.build.version.sdk 2>/dev/null)}"
if [ "$api" != "29" ]; then
    abort "STOP: this module targets Android 10/API 29 only; detected API=${api:-unknown}."
fi

device="$(getprop ro.product.device 2>/dev/null)"
product="$(getprop ro.product.name 2>/dev/null)"
board="$(getprop ro.product.board 2>/dev/null)"
case "$device $product $board" in
    *s9863a1h10*) ;;
    *) abort "STOP: target does not identify as the exact known s9863a1h10 TS18. device=$device product=$product board=$board" ;;
esac

geometry="$MODPATH/system/product/overlay/TS18StatusBarGeometry.apk"
visuals="$MODPATH/system/product/overlay/TS18StatusBarVisuals.apk"
for apk in "$geometry" "$visuals"; do
    if [ ! -s "$apk" ]; then
        abort "STOP: missing or empty combined RRO payload: $apk"
    fi
done

# Never delete another Magisk module automatically. An active legacy module would
# mount the same overlay path alongside this combined module, so require an
# explicit disable/remove decision before proceeding.
for legacy in ts18_statusbar_geometry ts18_statusbar_visuals; do
    legacy_dir="/data/adb/modules/$legacy"
    if [ -d "$legacy_dir" ] && [ ! -e "$legacy_dir/disable" ] && [ ! -e "$legacy_dir/remove" ]; then
        abort "STOP: active legacy Magisk module '$legacy' is still installed. Run the bundled ts18-migrate-magisk-modules.sh, reboot, then install this combined module. No /data/adb state was deleted automatically."
    fi
done

set_perm "$geometry" 0 0 0644
set_perm "$visuals" 0 0 0644
ui_print "- Both RRO APKs validated in one module"
ui_print "- No OEM APK/RRO is deleted or replaced"
ui_print "- Reboot required"
ui_print "- After reboot open TS18 System UI and verify both RRO payloads plus exact SystemUI status"
