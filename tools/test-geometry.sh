#!/usr/bin/env bash
# Host-only pure Java policy validation. No Android SDK or network required.

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || {
    printf 'FAILED: cannot resolve script directory\n' >&2
    exit 3
}
ROOT="$(dirname -- "$SCRIPT_DIR")"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/ts18-statusbar-test.XXXXXX")" || {
    printf 'FAILED: cannot create temporary directory\n' >&2
    exit 3
}
cleanup() {
    rc=$?
    rm -rf -- "$TMP"
    exit "$rc"
}
trap cleanup EXIT HUP INT TERM

if ! command -v javac >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
    printf 'FAILED: javac/java are required\n' >&2
    exit 3
fi

mkdir -p "$TMP/au/com/cb/ts18/statusbar/input" || exit 3
cp -- "$ROOT/lsposed/src/main/java/au/com/cb/ts18/statusbar/input/TouchStripGeometry.java" \
    "$TMP/au/com/cb/ts18/statusbar/input/" || exit 3
cp -- "$ROOT/tools/GeometryPolicySelfTest.java" \
    "$TMP/au/com/cb/ts18/statusbar/input/" || exit 3

if ! javac -d "$TMP/classes" \
    "$TMP/au/com/cb/ts18/statusbar/input/TouchStripGeometry.java" \
    "$TMP/au/com/cb/ts18/statusbar/input/GeometryPolicySelfTest.java"; then
    printf 'FAILED: javac policy compile\n' >&2
    exit 2
fi

if ! java -cp "$TMP/classes" au.com.cb.ts18.statusbar.input.GeometryPolicySelfTest; then
    printf 'FAILED: geometry policy self-test\n' >&2
    exit 2
fi
