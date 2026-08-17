#!/usr/bin/env bash
# Source-level guardrails for the v0.4 right-nav observation milestone.

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"
BASE="$ROOT/lsposed/src/main/java/au/com/cb/ts18/statusbar/input"
RUNTIME="$BASE/NavFeatureRuntime.java"
PROBE="$BASE/NavHierarchyProbe.java"
WINDOWS="$BASE/WindowHooks.java"
NAV_SOURCES=("$BASE"/Nav*.java "$WINDOWS")

for file in "$RUNTIME" "$PROBE" "$WINDOWS"; do
    if [ ! -s "$file" ]; then
        printf 'FAILED: missing right-nav observation source: %s\n' "$file" >&2
        exit 2
    fi
done
for file in "${NAV_SOURCES[@]}"; do
    if [ ! -s "$file" ]; then
        printf 'FAILED: expected right-nav source glob produced missing path: %s\n' "$file" >&2
        exit 2
    fi
done

if grep -F 'HookRuntime.deactivate' "$RUNTIME" >/dev/null 2>&1 \
        || grep -F 'CircuitBreaker.recordFailure' "$RUNTIME" >/dev/null 2>&1; then
    printf 'FAILED: right-nav feature breaker must not disable/open compact runtime\n' >&2
    exit 2
fi

for forbidden in \
    'setOnClickListener' \
    'setOnTouchListener' \
    'setClickable' \
    'setLongClickable' \
    'setVisibility' \
    'setLayoutParams' \
    '.addView(' \
    '.removeView(' \
    'MediaSessionManager' \
    'MediaController' \
    'TransportControls'
do
    if grep -F "$forbidden" "${NAV_SOURCES[@]}" >/dev/null 2>&1; then
        printf 'FAILED: right-nav observation path contains forbidden mutation/control token: %s\n' "$forbidden" >&2
        exit 2
    fi
done

if ! grep -F 'NavFeatureRuntime.recordFailure' "$WINDOWS" >/dev/null 2>&1; then
    printf 'FAILED: navigation WindowManager path is not isolated by NavFeatureRuntime\n' >&2
    exit 2
fi
if ! grep -F 'TYPE_NAVIGATION_BAR' "$BASE/NavBarState.java" >/dev/null 2>&1; then
    printf 'FAILED: right-nav root recogniser does not target TYPE_NAVIGATION_BAR\n' >&2
    exit 2
fi

printf 'SUCCESS: complete right-nav path remains observation-only and failure-isolated\n'
