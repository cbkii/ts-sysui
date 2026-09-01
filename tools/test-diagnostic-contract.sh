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
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/RateLimitedLog.java 'ANDROID_TAG = "TS18SysUI"'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/RateLimitedLog.java 'DIAGNOSTIC_INTERVAL_MS = 250L'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/Ts18StatusBarModule.java 'DiagnosticJournal.state("module-entry", "ENTER"'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/Ts18StatusBarModule.java 'installRequired("compat-touch-hook"'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/Ts18StatusBarModule.java 'installRequired("window-hooks"'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/SystemUiBridge.java 'DiagnosticJournal.appendStatus(out)'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/SystemUiBridge.java 'resolved_status_bar_height_px'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/LocalSelfDiagnostics.java 'xposed-init'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticSettingsActivity.java 'Read-only diagnostic console'
require lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticSettingsActivity.java 'Refresh full diagnostic status'
require docs/DIAGNOSTIC-BUILD-POLICY.md 'Mandatory runtime event model'
require AGENTS.md 'docs/DIAGNOSTIC-BUILD-POLICY.md'
require tools/package-release.sh 'debug|diagnostic|release'
require tools/test-packaged-artifacts.sh 'debug|diagnostic|release'
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

# Keep privacy policy concrete by preventing obvious sensitive-field logging terms
# in the journal implementation itself.
if grep -E -i 'media[_ -]?title|artist|credential|password|token|contact|message.body' \
        lsposed/src/main/java/au/com/cb/ts18/statusbar/input/DiagnosticJournal.java >/dev/null; then
    echo 'FAILED: DiagnosticJournal contains a prohibited sensitive-data logging term.' >&2
    exit 2
fi

printf 'SUCCESS: diagnostic build, logging, console, packaging and workflow contract\n'
