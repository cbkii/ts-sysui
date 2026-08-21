#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

require() {
    file="$1"
    pattern="$2"
    grep -F -- "$pattern" "$file" >/dev/null || {
        printf 'FAILED: %s lacks required remediation contract: %s\n' "$file" "$pattern" >&2
        exit 2
    }
}

require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/SystemUiBridge.java 'ACTION_QUERY_STATUS'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/SystemUiBridge.java 'ExactTopwayNavController.requestReconcile()'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/NavConfig.java 'static void invalidate()'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/NavConfig.java 'persistFromSystemUi'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactTopwayNavController.java 'OPTIONAL_STOCK_IDS'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactTopwayNavController.java 'button.setVisibility(View.VISIBLE)'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/TopwayWeightedNavPolicy.java 'ABSOLUTE_HORIZONTAL_TOUCH_DP = 48'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/TopwayWeightedNavPolicy.java 'PRODUCTION_VERTICAL_TOUCH_DP = 56'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessActionTracker.java 'QUERY_CONFIRM_MS'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessController.java 'CALLBACK_CONFIRMED'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessController.java 'NO_258_CALLBACK'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessSettingsActivity.java 'TS18 System UI'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessSettingsActivity.java 'Save diagnostics to Downloads'
require magisk-combined/module.prop.in 'id=ts18_sysui'
require magisk-combined/customize.sh 'TS18StatusBarGeometry.apk'
require magisk-combined/customize.sh 'TS18StatusBarVisuals.apk'
require tools/package-release.sh 'TS18-SystemUI-Magisk-v'
require tools/test-packaged-artifacts.sh 'single combined Magisk'

if grep -F 'TS18-StatusBar-Geometry-Magisk-v' tools/package-release.sh >/dev/null \
        || grep -F 'TS18-StatusBar-Visuals-Magisk-v' tools/package-release.sh >/dev/null; then
    echo 'FAILED: normal packager still emits split Magisk artifacts.' >&2
    exit 2
fi

printf 'SUCCESS: physical 0.5.1 remediation source contract\n'
