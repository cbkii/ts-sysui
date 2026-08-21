#!/system/bin/sh
# Read-only exact TS18 SystemUI identity pre-arm verifier.

EXPECTED_SHA256="668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f"
EXPECTED_PACKAGE="com.android.systemui"
EXPECTED_API="29"
EXPECTED_DEVICE_TOKEN="s9863a1h10"
DEFAULT_APK="/system/priv-app/SystemUI/SystemUI.apk"

run_timeout() {
    seconds="$1"
    shift
    if command -v timeout >/dev/null 2>&1; then
        timeout "$seconds" "$@"
        return $?
    fi
    if command -v toybox >/dev/null 2>&1; then
        toybox timeout "$seconds" "$@"
        return $?
    fi
    echo "STOP: no timeout implementation; refusing an unbounded contract check." >&2
    return 124
}

if [ "$(id -u 2>/dev/null)" != "0" ]; then
    echo "STOP: root identity is required to verify the protected SystemUI APK." >&2
    exit 4
fi

api="$(getprop ro.build.version.sdk 2>/dev/null)"
device="$(getprop ro.product.device 2>/dev/null)"
product="$(getprop ro.product.name 2>/dev/null)"
if [ "$api" != "$EXPECTED_API" ]; then
    echo "STOP: unsupported SystemUI contract; expected API $EXPECTED_API, detected ${api:-unknown}." >&2
    exit 4
fi
case "$device $product" in
    *"$EXPECTED_DEVICE_TOKEN"*) ;;
    *)
        echo "STOP: unsupported SystemUI contract; device=$device product=$product." >&2
        exit 4
        ;;
esac

apk_path=""
pm_output="$(run_timeout 10 pm path "$EXPECTED_PACKAGE" 2>/dev/null)"
pm_status=$?
if [ "$pm_status" -eq 0 ]; then
    apk_path="$(printf '%s\n' "$pm_output" | sed -n 's/^package://p' | sed -n '1p')"
fi
if [ -z "$apk_path" ] && [ -f "$DEFAULT_APK" ]; then
    apk_path="$DEFAULT_APK"
fi
if [ -z "$apk_path" ] || [ ! -f "$apk_path" ]; then
    echo "STOP: unsupported SystemUI contract; installed APK path was not resolved (pm status=$pm_status)." >&2
    exit 4
fi

if command -v sha256sum >/dev/null 2>&1; then
    hash_output="$(run_timeout 30 sha256sum "$apk_path" 2>/dev/null)"
    hash_status=$?
elif command -v toybox >/dev/null 2>&1; then
    hash_output="$(run_timeout 30 toybox sha256sum "$apk_path" 2>/dev/null)"
    hash_status=$?
else
    echo "STOP: unsupported SystemUI contract; no SHA-256 implementation is available." >&2
    exit 4
fi
if [ "$hash_status" -ne 0 ]; then
    echo "STOP: unsupported SystemUI contract; SHA-256 read failed (status=$hash_status)." >&2
    exit 4
fi
actual_sha256="${hash_output%% *}"
case "$actual_sha256" in
    *[!0-9a-fA-F]*|'')
        echo "STOP: unsupported SystemUI contract; malformed SHA-256 result." >&2
        exit 4
        ;;
esac
actual_sha256="$(printf '%s' "$actual_sha256" | tr '[:upper:]' '[:lower:]')"
if [ "$actual_sha256" != "$EXPECTED_SHA256" ]; then
    echo "STOP: unsupported SystemUI contract" >&2
    echo "observed_path=$apk_path" >&2
    echo "observed_sha256=$actual_sha256" >&2
    echo "expected_sha256=$EXPECTED_SHA256" >&2
    exit 4
fi

echo "contract=SUPPORTED"
echo "package=$EXPECTED_PACKAGE"
echo "path=$apk_path"
echo "api=$api"
echo "device=$device"
echo "product=$product"
echo "sha256=$actual_sha256"
echo "SUCCESS: exact TS18 SystemUI identity verified read-only; no setting or APK was changed."
