#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

fail() { printf 'FAILED: %s\n' "$*" >&2; exit 2; }
require() {
    grep -F -- "$2" "$1" >/dev/null || fail "$1 lacks required exact XTService contract: $2"
}

EXPECTED_XT_SHA='341af03ccbaeb6a7debe1929153eaadf9ced421d64a4933016010e0e7aa77267'
CONTRACT='reference/exact-ts18-xtservice-contract.json'
OBSERVER='lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactXtServiceObserver.java'
BINDER='lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactXtServiceBinder.java'
NAV='lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactTopwayNavController.java'
MONITOR='lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactTopwayNavVisibilityMonitor.java'
AUX='lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactAuxiliaryRuntime.java'
QUAL='lsposed/src/main/java/au/com/cb/ts18/statusbar/input/TopwayQualificationActivity.java'
DIAG_BRIDGE='lsposed/src/main/java/au/com/cb/ts18/statusbar/input/XtServiceDiagnosticBridge.java'
NAVCFG='lsposed/src/main/java/au/com/cb/ts18/statusbar/input/StockNavConfigObserver.java'
BRDIAG='lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessEventDiagnostics.java'

require "$CONTRACT" "$EXPECTED_XT_SHA"
require reference/SHA256SUMS-supplied.txt "$EXPECTED_XT_SHA  XTService_2022.02.17.apk"
require "$CONTRACT" '"getReverseStatus": 17'
require "$CONTRACT" '"getSleepStatus": 18'
require "$CONTRACT" '"mediaNext": 28'
require "$CONTRACT" '"mediaPause": 29'
require "$CONTRACT" '"mediaPlay": 30'
require "$CONTRACT" '"mediaPre": 31'
require "$CONTRACT" '"registerTWCommandCallback": 49'
require "$CONTRACT" '"unRegisterTWCommandCallback": 56'
require "$CONTRACT" '"onReverseStatus": 5'
require "$CONTRACT" '"onSleepStatus": 6'

require "$BINDER" 'TX_GET_REVERSE_STATUS = IBinder.FIRST_CALL_TRANSACTION + 16'
require "$BINDER" 'TX_GET_SLEEP_STATUS = IBinder.FIRST_CALL_TRANSACTION + 17'
require "$BINDER" 'TX_MEDIA_NEXT = IBinder.FIRST_CALL_TRANSACTION + 27'
require "$BINDER" 'TX_MEDIA_PAUSE = IBinder.FIRST_CALL_TRANSACTION + 28'
require "$BINDER" 'TX_MEDIA_PLAY = IBinder.FIRST_CALL_TRANSACTION + 29'
require "$BINDER" 'TX_MEDIA_PRE = IBinder.FIRST_CALL_TRANSACTION + 30'
require "$BINDER" 'TX_REGISTER_COMMAND_CALLBACK = IBinder.FIRST_CALL_TRANSACTION + 48'
require "$BINDER" 'TX_UNREGISTER_COMMAND_CALLBACK = IBinder.FIRST_CALL_TRANSACTION + 55'

require "$AUX" 'ExactXtServiceObserver.start(source)'
require "$AUX" 'StockNavConfigObserver.start(source)'
require "$OBSERVER" 'ExactXtServiceContract.verifyInstalled(context)'
require "$OBSERVER" 'ExactXtServiceBinder.registerCallback(remote, callbackBinder)'
require "$OBSERVER" 'ExactXtServiceBinder.requestInitialState(remote)'
require "$OBSERVER" 'ExactXtServiceBinder.unregisterCallback(currentRemote, currentCallback)'
require "$OBSERVER" 'reverseKnown = false'
require "$OBSERVER" 'sleepKnown = false'
require "$OBSERVER" 'XtServiceFeatureRuntime.recordFailure'

require "$NAV" 'VehicleStatePolicy.Decision vehicle = ExactXtServiceObserver.vehicleDecision()'
require "$NAV" 'stop("vehicle-" + vehicle.reason'
require "$NAV" 'repository.dispatch(action)'
require "$MONITOR" 'requestVehicleStateReevaluation()'
require "$MONITOR" 'invalidateForStockConfigChange(String reason)'
require "$MONITOR" 'ExactTopwayNavController.failOpen()'

require "$NAVCFG" 'persist.navibar.position'
require "$NAVCFG" 'navigationbar_config'
require "$NAVCFG" 'show_navigationbar'
if grep -Eq 'Settings\.(System|Global|Secure)\.put|SystemProperties.*set' "$NAVCFG"; then
    fail 'stock navigation configuration observer must remain read-only'
fi

require "$DIAG_BRIDGE" 'ACTION_QUALIFY_MEDIA'
require "$DIAG_BRIDGE" 'BuildConfig.TS18_DIAGNOSTIC'
require "$QUAL" 'Normal right-nav playback remains Android MediaController-only.'
require "$OBSERVER" 'Binder call returned without exception; playback effect remains unproven'

# No automatic vendor media fallback is allowed in the normal right-nav path.
if grep -Eq 'ExactXtServiceBinder\.(qualifyMedia|transactNoArgs)|mediaPre\(|mediaPlay\(|mediaPause\(|mediaNext\(' \
        lsposed/src/main/java/au/com/cb/ts18/statusbar/input/NavMediaSessionRepository.java \
        lsposed/src/main/java/au/com/cb/ts18/statusbar/input/ExactTopwayNavController.java; then
    fail 'normal right-nav path contains XTService vendor-media dispatch'
fi

require "$BRDIAG" 'SCREEN_BRIGHTNESS'
require "$BRDIAG" 'BrightnessEventAttribution.classify'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessEventAttribution.java \
    'bounded temporal correlation only'
if grep -Eq 'Settings\.System\.putInt|TWSystemUI.*write' "$BRDIAG"; then
    fail 'brightness chronology diagnostics must be observation-only'
fi

# LSPosed scope must remain exactly SystemUI; XTService is bound, never hooked.
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

p = Path('lsposed/src/main/res/values/arrays.xml')
root = ET.fromstring(p.read_text(encoding='utf-8'))
values = [e.text.strip() for e in root.findall(".//string-array[@name='xposed_scope']/item") if e.text]
if values != ['com.android.systemui']:
    raise SystemExit(f'FAILED: LSPosed scope broadened: {values!r}')
PY

require docs/EXACT-XTSERVICE-VEHICLE-OBSERVATION.md 'There is **no automatic XTService fallback** in this PR.'
require AGENTS.md 'XTService `mediaPre/mediaPlay/mediaPause/mediaNext` are diagnostic qualification'

printf 'SUCCESS: exact XTService vehicle observation and qualification contract\n'
