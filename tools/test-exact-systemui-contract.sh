#!/usr/bin/env bash
# Validate the repository-safe exact SystemUI contract fixture.

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"
FIXTURE="$ROOT/reference/exact-ts18-systemui-contract.json"

if ! command -v python3 >/dev/null 2>&1; then
    printf 'FAILED: python3 is required for contract fixture validation\n' >&2
    exit 3
fi

python3 - "$FIXTURE" <<'PY'
import json
import re
import sys
from pathlib import Path

fixture = Path(sys.argv[1])
data = json.loads(fixture.read_text(encoding="utf-8"))
target = data["target"]
expected = {
    "packageName": "com.android.systemui",
    "installedPath": "/system/priv-app/SystemUI/SystemUI.apk",
    "versionCode": 29,
    "minSdk": 29,
    "targetSdk": 29,
    "sharedUserId": "android.uid.systemui",
    "apkSha256": "668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f",
    "requiredPermission": "android.permission.MEDIA_CONTENT_CONTROL",
}
for key, value in expected.items():
    if target.get(key) != value:
        raise SystemExit(f"FAILED: contract target {key}={target.get(key)!r}, expected {value!r}")
if not re.fullmatch(r"(?:[0-9A-F]{2}:){31}[0-9A-F]{2}", target["signerSha256"]):
    raise SystemExit("FAILED: signerSha256 must be a colon-separated 32-byte uppercase digest")

classes = {item["name"]: item for item in data["classes"]}
required_classes = {
    "com.android.systemui.SystemUIApplication",
    "com.android.systemui.statusbar.phone.StatusBarTouchableRegionManager",
    "com.android.systemui.statusbar.phone.StatusBar",
    "com.android.systemui.statusbar.phone.HeadsUpManagerPhone",
    "com.android.systemui.bubbles.BubbleController",
    "com.android.systemui.statusbar.phone.PhoneStatusBarView",
    "com.android.systemui.statusbar.phone.NavigationBarView",
    "com.android.systemui.tw.TWSystemUI",
    "com.android.systemui.tw.StatusBarViewInit",
}
missing = sorted(required_classes - classes.keys())
if missing:
    raise SystemExit(f"FAILED: missing required contract classes: {missing}")

manager = classes["com.android.systemui.statusbar.phone.StatusBarTouchableRegionManager"]
fields = {item["name"]: item["type"] for item in manager["fields"] if item.get("required")}
expected_fields = {
    "mStatusBarWindowView": "android.view.View",
    "mStatusBarHeight": "int",
    "mIsStatusBarExpanded": "boolean",
    "mForceCollapsedUntilLayout": "boolean",
    "mStatusBar": "com.android.systemui.statusbar.phone.StatusBar",
    "mHeadsUpManager": "com.android.systemui.statusbar.phone.HeadsUpManagerPhone",
    "mBubbleController": "com.android.systemui.bubbles.BubbleController",
}
if fields != expected_fields:
    raise SystemExit(f"FAILED: touch manager fields drifted: {fields!r}")

resources = data["resources"]
for forbidden in resources["forbiddenVisualOverrides"]:
    if forbidden in resources["statusVisualAllowList"]:
        raise SystemExit(f"FAILED: forbidden nav/shared resource is allow-listed: {forbidden}")
if resources["navbarHost"] != "com.android.systemui:id/navbar_left":
    raise SystemExit("FAILED: exact navbar host drifted")
if len(resources["knownNavbarChildren"]) != len(set(resources["knownNavbarChildren"])):
    raise SystemExit("FAILED: duplicate known navbar child resource")

print("SUCCESS: exact TS18 SystemUI contract fixture")
PY
