#!/system/bin/sh
# Magisk installer context. Required operations fail closed; no partition writes.

ui_print "- TS18 Compact Status Bar Geometry"
ui_print "- Validating exact target and module payload"

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

apk="$MODPATH/system/product/overlay/TS18StatusBarGeometry.apk"
if [ ! -s "$apk" ]; then
    abort "STOP: missing or empty RRO payload: $apk"
fi

set_perm "$apk" 0 0 0644
ui_print "- No OEM APK/RRO is deleted or replaced"
ui_print "- Reboot required"
ui_print "- Validate overlay state and actual bar height before enabling the LSPosed component"
