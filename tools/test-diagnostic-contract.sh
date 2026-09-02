#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

require() {
    file="$1"
    pattern="$2"
    grep -F -- "$pattern" "$file" >/dev/null || {
        printf 'FAILED: %s lacks diagnostic contract: %s\n' "$file" "$pattern" >&2
        exit 2
    }
}

require lsposed/build.gradle.kts 'create("diagnostic")'
require lsposed/build.gradle.kts 'buildConfigField("boolean", "TS18_DIAGNOSTIC", "true")'
require lsposed/build.gradle.kts 'buildConfigField("boolean", "TS18_DIAGNOSTIC", "false")'
require lsposed/build.gradle.kts 'buildConfigField("String", "TS18_BUILD_KIND", "\"diagnostic\"")'
require overlay/build.gradle.kts 'create("diagnostic")'
require visual-overlay/build.gradle.kts 'create("diagnostic")'

if grep -F 'applicationIdSuffix' lsposed/build.gradle.kts >/dev/null; then
    echo 'FAILED: diagnostic variant must retain the release application ID.' >&2
    exit 2
fi

require lsposed/src/diagnostic/AndroidManifest.xml 'TS18 Diagnostic Console'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticJournal.java 'MAX_ENTRIES = BuildConfig.TS18_DIAGNOSTIC ? 512 : 96'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticJournal.java 'MAX_SNAPSHOT_CHARS = 96 * 1024'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticJournal.java 'long entrySequence = ++sequence'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticJournal.java 'appendTruncationMarker(out)'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/RateLimitedLog.java 'ANDROID_TAG = "TS18SysUI"'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/RateLimitedLog.java 'DIAGNOSTIC_INTERVAL_MS = 250L'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/RateLimitedLog.java '? DIAGNOSTIC_INTERVAL_MS : RELEASE_INTERVAL_MS'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/Ts18StatusBarModule.java 'DiagnosticJournal.state("module-entry", "ENTER"'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/Ts18StatusBarModule.java 'installRequired("compat-touch-hook"'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/Ts18StatusBarModule.java 'installRequired("window-hooks"'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/Ts18StatusBarModule.java 'DiagnosticJournal.state(stage, "INSTALLING", "")'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/Ts18StatusBarModule.java 'total registrations='
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/SystemUiBridge.java 'DiagnosticJournal.appendStatus(out)'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/SystemUiBridge.java 'resolved_status_bar_height_px'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/LocalSelfDiagnostics.java 'xposed-init'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticSettingsActivity.java 'Read-only diagnostic console'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticSettingsActivity.java 'Refresh full diagnostic status'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticSettingsActivity.java 'Executors.newSingleThreadExecutor'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticSettingsActivity.java 'copyButton.setEnabled(ready)'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticSettingsActivity.java 'saveButton.setEnabled(ready)'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessHooks.java 'RateLimitedLog.debug(true, "brightness-write-observed"'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessHooks.java 'DiagnosticJournal.state(stage, "ROLLED_BACK"'
require docs/DIAGNOSTIC-BUILD-POLICY.md 'Mandatory runtime event model'
require AGENTS.md 'docs/DIAGNOSTIC-BUILD-POLICY.md'
require tools/package-release.sh 'debug|diagnostic|release'
require tools/package-release.sh 'EXPECTED_SIGNER_SHA256'
require tools/package-release.sh 'apk_signer_sha256'
require tools/package-release.sh 'diagnostic APK signer mismatch'
require tools/test-packaged-artifacts.sh 'debug|diagnostic|release'
require tools/test-packaged-artifacts.sh 'unzip -p "$DIST/$BUNDLE" BUILD-INFO.txt'
require tools/test-diagnostic-apk-contract.sh 'DiagnosticSettingsActivity'
require .github/workflows/build.yml ':lsposed:lintDiagnostic'
require .github/workflows/build.yml ':lsposed:assembleDiagnostic'
require .github/workflows/build.yml 'test-diagnostic-apk-contract.sh diagnostic'
require .github/workflows/diagnostic.yml 'Manual Diagnostic Build'
require .github/workflows/diagnostic.yml 'environment: release'
require .github/workflows/diagnostic.yml 'bash tools/package-release.sh diagnostic'

if grep -E 'softprops/action-gh-release|git tag|git push|resolve-release-version\.sh --.*--apply' \
        .github/workflows/diagnostic.yml >/dev/null; then
    echo 'FAILED: diagnostic workflow must not tag, publish, push, or mutate release metadata.' >&2
    exit 2
fi

if grep -F 'import de.robv.android.xposed.XposedBridge' \
        lsposed/src/main/java/au/com/cb/ts18/statusbar/input/RateLimitedLog.java >/dev/null; then
    echo 'FAILED: shared logging helper must not hard-link XposedBridge in the normal APK process.' >&2
    exit 2
fi

# Diagnostic Console is read-only: it may query status but must not use the apply action.
if grep -F 'ACTION_APPLY' \
        lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticSettingsActivity.java >/dev/null; then
    echo 'FAILED: Diagnostic Console must not issue mutation/apply requests.' >&2
    exit 2
fi

# Export actions must stay disabled/guarded until a complete report is ready, and
# every new refresh or stopped Activity lifetime must invalidate old workers.
activity='lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticSettingsActivity.java'
report_guard_count="$(grep -F -c 'if (!reportReady)' "$activity" || true)"
if [ "$report_guard_count" -lt 2 ]; then
    echo 'FAILED: Diagnostic Console copy/save paths must both reject incomplete reports.' >&2
    exit 2
fi
refresh_block="$(grep -A12 -F 'private void refresh()' "$activity")"
if ! printf '%s\n' "$refresh_block" | grep -F 'renderGeneration++;' >/dev/null; then
    echo 'FAILED: Diagnostic Console refresh must invalidate an in-flight report before requesting new state.' >&2
    exit 2
fi
if ! printf '%s\n' "$refresh_block" | grep -F 'setReportReady(false);' >/dev/null; then
    echo 'FAILED: Diagnostic Console refresh must disable report export before collecting new state.' >&2
    exit 2
fi
if ! grep -A10 -F '@Override protected void onStop()' "$activity" | grep -F 'renderGeneration++;' >/dev/null; then
    echo 'FAILED: Diagnostic Console onStop must invalidate stale report workers.' >&2
    exit 2
fi

# Forced build diagnostics must never persist into normal feature policy debug flags.
if grep -F '|| BuildConfig.TS18_DIAGNOSTIC' \
        lsposed/src/main/java/au/com/cb/ts18/statusbar/input/SystemUiBridge.java >/dev/null; then
    echo 'FAILED: SystemUiBridge must not persist forced diagnostic logging into feature debug policy.' >&2
    exit 2
fi

# Topway write observation is a hot hook path; it must be rate-limited rather than
# synchronously appended directly to the diagnostic journal on every write.
if grep -A18 -F '"write",' \
        lsposed/src/main/java/au/com/cb/ts18/statusbar/input/BrightnessHooks.java \
        | grep -F 'DiagnosticJournal.record' >/dev/null; then
    echo 'FAILED: brightness write hot path directly journals every diagnostic write.' >&2
    exit 2
fi

# Keep privacy policy concrete by preventing obvious sensitive-field logging terms
# in the journal implementation itself.
if grep -E -i 'media[_ -]?title|artist|credential|password|token|contact|message.body' \
        lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticJournal.java >/dev/null; then
    echo 'FAILED: DiagnosticJournal contains a prohibited sensitive-data logging term.' >&2
    exit 2
fi

# Source-manifest drift is cheap to detect and must fail before the expensive
# Android compile/lint matrix.
manifest_line="$(grep -n -F 'bash tools/test-source-manifest.sh' .github/workflows/build.yml | head -n1 | cut -d: -f1)"
compile_line="$(grep -n -F 'Android compile, unit tests and lint' .github/workflows/build.yml | head -n1 | cut -d: -f1)"
if [ -z "$manifest_line" ] || [ -z "$compile_line" ] || [ "$manifest_line" -ge "$compile_line" ]; then
    echo 'FAILED: source manifest validation must run before Android compile/lint.' >&2
    exit 2
fi

printf 'SUCCESS: diagnostic build, logging, console, packaging and workflow contract\n'
