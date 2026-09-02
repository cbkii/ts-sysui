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
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactTopwayNavController.java '"navbar_home", "navbar_back", "navbar_history"'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactTopwayNavController.java '"navbar_guanping", "navbar_app"'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactTopwayNavController.java 'button.setVisibility(View.VISIBLE)'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactTopwayNavController.java 'nav_direct_children'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/TopwayWeightedNavPolicy.java 'ABSOLUTE_HORIZONTAL_TOUCH_DP = 48'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/TopwayWeightedNavPolicy.java 'PRODUCTION_VERTICAL_TOUCH_DP = 56'

require reference/exact-ts18-carsetting-contract.json '06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71'
require reference/exact-ts18-carsetting-contract.json 'Settings.System.SCREEN_BRIGHTNESS'
require reference/exact-ts18-carsetting-contract.json '"command": 258'
require reference/exact-ts18-carsetting-contract.json '"arg1": 128'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessCompatibility.java 'EXPECTED_CARSETTING_SHA256 = "06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71"'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessLevelMapper.java 'RAW_MIN = 30'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessLevelMapper.java 'RAW_MAX = 255'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessActionTracker.java 'QUERY_CONFIRM_MS'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessActionTracker.java 'NO_258_CALLBACK'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessActionTracker.java 'SCREEN_BRIGHTNESS_READBACK_MISMATCH'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessController.java 'Settings.System.putInt(currentContext.getContentResolver()'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessController.java 'Settings.System.SCREEN_BRIGHTNESS'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessController.java 'BrightnessProtocol.MODE_TRANSACTION_SECOND_VALUE'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessController.java 'Topway 516 retained as observation-only'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessController.java 'READBACK_CONFIRMED'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessSettingsActivity.java 'TS18 System UI'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessSettingsActivity.java 'Save diagnostics to Downloads'
require magisk-combined/module.prop.in 'id=ts18_sysui'
require magisk-combined/customize.sh 'TS18StatusBarGeometry.apk'
require magisk-combined/customize.sh 'TS18StatusBarVisuals.apk'
require tools/package-release.sh 'TS18-SystemUI-Magisk-v'
require tools/test-packaged-artifacts.sh 'single combined Magisk'

# 516 remains query/observation-only. A three-argument 516 write in the module
# controller would reintroduce the disproven physical actuator.
if grep -E 'write3\.invoke\([^\n]*BrightnessProtocol\.COMMAND_BRIGHTNESS|write3\.invoke\([^\n]*516' \
        lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessController.java >/dev/null; then
    echo 'FAILED: controller reintroduced Topway 516 as a physical brightness write.' >&2
    exit 2
fi

# Keep pull-request validation and the manual signed-release path aligned.
require .github/workflows/build.yml 'sh -n magisk-combined/customize.sh'
require .github/workflows/build.yml 'bash tools/test-remediation-contract.sh'
require .github/workflows/release.yml 'sh -n magisk-combined/customize.sh'
require .github/workflows/release.yml 'bash tools/test-remediation-contract.sh'

UPLOAD_ARTIFACT_V6='actions/upload-artifact@b7c566a772e6b6bfb58ed0dc250532a479d7789f # v6.0.0'
require .github/workflows/build.yml "$UPLOAD_ARTIFACT_V6"
require .github/workflows/release.yml "$UPLOAD_ARTIFACT_V6"

if grep -F 'TS18-StatusBar-Geometry-Magisk-v' tools/package-release.sh >/dev/null \
        || grep -F 'TS18-StatusBar-Visuals-Magisk-v' tools/package-release.sh >/dev/null; then
    echo 'FAILED: normal packager still emits split Magisk artifacts.' >&2
    exit 2
fi

if grep -F 'actions/upload-artifact@330a01c490aca151604b8cf639adc76d48f6c5d4' \
        .github/workflows/build.yml .github/workflows/release.yml >/dev/null; then
    echo 'FAILED: Node-20 upload-artifact v5 pin remains in a workflow.' >&2
    exit 2
fi

printf 'SUCCESS: exact APK physical remediation source contract\n'
