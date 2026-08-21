#!/usr/bin/env bash
# Source-level guardrails for the exact-host right-nav media implementation.

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(dirname -- "$SCRIPT_DIR")"
BASE="$ROOT/lsposed/src/main/java/au/com/cb/ts18/statusbar/input"
RUNTIME="$BASE/NavFeatureRuntime.java"
ADAPTER="$BASE/ExactTopwayNavAdapter.java"
CONTROLLER="$BASE/ExactTopwayNavController.java"
MEDIA="$BASE/NavMediaSessionRepository.java"
WINDOWS="$BASE/WindowHooks.java"

for file in \
    "$RUNTIME" \
    "$ADAPTER" \
    "$CONTROLLER" \
    "$MEDIA" \
    "$BASE/TopwayWeightedNavPolicy.java" \
    "$BASE/NavMediaSelectionPolicy.java" \
    "$BASE/NavMediaDispatchPolicy.java" \
    "$WINDOWS"
do
    if [ ! -s "$file" ]; then
        printf 'FAILED: missing exact right-nav source: %s\n' "$file" >&2
        exit 2
    fi
done

if grep -F 'HookRuntime.deactivate' "$RUNTIME" >/dev/null 2>&1 \
        || grep -F 'CircuitBreaker.recordFailure' "$RUNTIME" >/dev/null 2>&1; then
    printf 'FAILED: right-nav feature breaker must not disable/open compact runtime\n' >&2
    exit 2
fi
if grep -F 'NavFeatureRuntime' "$WINDOWS" >/dev/null 2>&1; then
    printf 'FAILED: exact navbar lifecycle must not depend on the broad WindowManager hook\n' >&2
    exit 2
fi

for token in \
    'new MediaSession(' \
    'MediaSessionCompat' \
    'startService(' \
    'startForegroundService(' \
    'new Notification' \
    'Notification.Builder' \
    'requestAudioFocus' \
    'sendMediaButtonEvent' \
    'dispatchMediaKeyEvent' \
    'TWSystemUI.write' \
    'android.view.KeyEvent'
do
    if grep -F "$token" "$ADAPTER" "$CONTROLLER" "$MEDIA" >/dev/null 2>&1; then
        printf 'FAILED: exact right-nav path contains forbidden authority/fallback token: %s\n' "$token" >&2
        exit 2
    fi
done

for token in \
    'com.android.systemui.statusbar.phone.NavigationBarView' \
    'onFinishInflate'
do
    grep -F "$token" "$ADAPTER" >/dev/null 2>&1 || {
        printf 'FAILED: exact navigation lifecycle token is absent: %s\n' "$token" >&2
        exit 2
    }
done

for token in \
    'navbar_left' \
    'navbar_guanping' \
    'navbar_volume_plus' \
    'navbar_volume_reduce' \
    'ExactSystemUiIdentity.isSupported()' \
    'TopwayWeightedNavPolicy.evaluate' \
    'R.id.ts18_nav_owner_tag' \
    'View.generateViewId()' \
    'removeOwnedGroup()'
do
    grep -F "$token" "$CONTROLLER" >/dev/null 2>&1 || {
        printf 'FAILED: exact host/ownership guard is absent: %s\n' "$token" >&2
        exit 2
    }
done
if grep -F 'child.setLayoutParams' "$CONTROLLER" >/dev/null 2>&1; then
    printf 'FAILED: exact navbar implementation edits an OEM child LayoutParams\n' >&2
    exit 2
fi

for token in \
    'MediaSessionManager' \
    'MediaController' \
    'MediaController.TransportControls' \
    'workerHandler.post(this::startOnWorker)' \
    'workerHandler.post(this::stopOnWorker)' \
    'workerHandler.post(() -> dispatchOnWorker(action))'
do
    grep -F "$token" "$MEDIA" >/dev/null 2>&1 || {
        printf 'FAILED: existing-session exactly-once transport path is absent: %s\n' "$token" >&2
        exit 2
    }
done

for test_file in \
    "$ROOT/lsposed/src/test/java/au/com/cb/ts18/statusbar/input/TopwayWeightedNavPolicyTest.java" \
    "$ROOT/lsposed/src/test/java/au/com/cb/ts18/statusbar/input/NavMediaSelectionPolicyTest.java" \
    "$ROOT/lsposed/src/test/java/au/com/cb/ts18/statusbar/input/NavMediaDispatchPolicyTest.java"
do
    [ -s "$test_file" ] || {
        printf 'FAILED: missing right-nav policy test: %s\n' "$test_file" >&2
        exit 2
    }
done

printf 'SUCCESS: exact-host navbar path is reversible, failure-isolated and uses one existing-session transport command\n'
