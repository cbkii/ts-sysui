#!/usr/bin/env bash
# Validate the repository-safe exact SystemUI contract fixture and exact-touch source shape.

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"
FIXTURE="$ROOT/reference/exact-ts18-systemui-contract.json"
ADAPTER="$ROOT/lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactTs18TouchableRegionAdapter.java"
INSETS="$ROOT/lsposed/src/main/java/au/com/cb/ts18/statusbar/input/InternalInsetsAccess.java"
NAV="$ROOT/lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactTopwayNavController.java"

if ! command -v python3 >/dev/null 2>&1; then
    printf 'FAILED: python3 is required for contract fixture validation\n' >&2
    exit 3
fi

python3 - "$FIXTURE" "$ADAPTER" "$INSETS" "$NAV" <<'PY'
import json
import re
import sys
from pathlib import Path

fixture = Path(sys.argv[1])
adapter_path = Path(sys.argv[2])
insets_path = Path(sys.argv[3])
nav_path = Path(sys.argv[4])
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
    "mShouldAdjustInsets": "boolean",
    "mStatusBar": "com.android.systemui.statusbar.phone.StatusBar",
    "mHeadsUpManager": "com.android.systemui.statusbar.phone.HeadsUpManagerPhone",
    "mBubbleController": "com.android.systemui.bubbles.BubbleController",
}
if fields != expected_fields:
    raise SystemExit(f"FAILED: touch manager fields drifted: {fields!r}")
should = next(item for item in manager["fields"] if item["name"] == "mShouldAdjustInsets")
if "runtime" not in should.get("evidenceClass", "").lower():
    raise SystemExit("FAILED: mShouldAdjustInsets must remain labelled runtime-verified rather than exact-static-proven")

resources = data["resources"]
for forbidden in resources["forbiddenVisualOverrides"]:
    if forbidden in resources["statusVisualAllowList"]:
        raise SystemExit(f"FAILED: forbidden nav/shared resource is allow-listed: {forbidden}")
if resources["navbarHost"] != "com.android.systemui:id/navbar_left":
    raise SystemExit("FAILED: exact navbar host drifted")
if len(resources["knownNavbarChildren"]) != len(set(resources["knownNavbarChildren"])):
    raise SystemExit("FAILED: duplicate known navbar child resource")
expected_nav_children = {
    "com.android.systemui:id/navbar_guanping",
    "com.android.systemui:id/navbar_home",
    "com.android.systemui:id/navbar_back",
    "com.android.systemui:id/navbar_history",
    "com.android.systemui:id/navbar_app",
    "com.android.systemui:id/navbar_volume_plus",
    "com.android.systemui:id/navbar_volume_reduce",
}
if set(resources["knownNavbarChildren"]) != expected_nav_children:
    raise SystemExit("FAILED: exact seven-child navbar topology drifted")

adapter = adapter_path.read_text(encoding="utf-8")
insets = insets_path.read_text(encoding="utf-8")
nav = nav_path.read_text(encoding="utf-8")
if 'Field shouldAdjust = requireField(manager, "mShouldAdjustInsets", boolean.class)' not in adapter:
    raise SystemExit("FAILED: exact adapter no longer runtime/type-checks mShouldAdjustInsets")
if 'InternalInsetsAccess.setTouchableRegion(info' not in adapter:
    raise SystemExit("FAILED: exact adapter no longer establishes REGION for ordinary collapsed state")
if 'resolved.managerClass, "onComputeInternalInsets", resolved.infoClass' in adapter:
    raise SystemExit("FAILED: duplicate direct onComputeInternalInsets mutation hook reintroduced")
if 'getDeclaredMethod("setTouchableInsets", int.class)' not in insets:
    raise SystemExit("FAILED: InternalInsetsAccess no longer resolves setTouchableInsets(int)")
if 'contract.setTouchableInsets.invoke(info, contract.regionMode)' not in insets:
    raise SystemExit("FAILED: InternalInsetsAccess no longer switches FRAME/default state to REGION")
for exact_name in (
        "navbar_home", "navbar_back", "navbar_history", "navbar_app",
        "navbar_volume_plus", "navbar_volume_reduce"):
    if f'"{exact_name}"' not in nav:
        raise SystemExit(f"FAILED: exact navbar controller lacks {exact_name}")
for obsolete in ('"home"', '"back"', '"recent_apps"', '"app"'):
    if obsolete in nav:
        raise SystemExit(f"FAILED: obsolete preflight resource name remains: {obsolete}")
if 'nav_direct_children' not in nav or 'resourceEntryName' not in nav:
    raise SystemExit("FAILED: exact navbar diagnostics no longer report live child resource names")

print("SUCCESS: exact TS18 SystemUI contract fixture and single-path touch/navbar runtime contract")
PY
