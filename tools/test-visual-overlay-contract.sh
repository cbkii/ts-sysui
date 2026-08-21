#!/usr/bin/env bash
# Enforce the exact SystemUI visual-resource allow-list.

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"

if ! command -v python3 >/dev/null 2>&1; then
    printf 'FAILED: python3 is required for visual overlay contract validation\n' >&2
    exit 3
fi

python3 - \
    "$ROOT/reference/exact-ts18-systemui-contract.json" \
    "$ROOT/visual-overlay/src/main/res/values/dimens.xml" \
    "$ROOT/visual-overlay/src/main/AndroidManifest.xml" <<'PY'
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

contract = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
allow = set(contract["resources"]["statusVisualAllowList"])
forbidden = set(contract["resources"]["forbiddenVisualOverrides"])

resources = ET.parse(sys.argv[2]).getroot()
names = [node.attrib.get("name") for node in resources if node.tag == "dimen"]
if set(names) != allow:
    raise SystemExit(f"FAILED: visual resources {sorted(names)} do not exactly match allow-list {sorted(allow)}")
if set(names) & forbidden:
    raise SystemExit(f"FAILED: visual overlay contains nav/shared resources: {sorted(set(names) & forbidden)}")
if len(names) != len(set(names)):
    raise SystemExit("FAILED: duplicate visual resource override")

android = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(sys.argv[3]).getroot()
overlays = manifest.findall("overlay")
if len(overlays) != 1:
    raise SystemExit("FAILED: visual APK must declare exactly one overlay")
overlay = overlays[0]
if overlay.attrib.get(android + "targetPackage") != "com.android.systemui":
    raise SystemExit("FAILED: visual RRO target must be com.android.systemui")
if overlay.attrib.get(android + "isStatic") != "true":
    raise SystemExit("FAILED: visual RRO must be static for the Android-10 product overlay path")

print("SUCCESS: exact SystemUI visual overlay allow-list")
PY
