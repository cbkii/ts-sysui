#!/usr/bin/env bash
# Host-only pure Java safety-policy validation. No Android SDK or network required.

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

PKG="$TMP/au/com/cb/ts18/statusbar/input"
mkdir -p "$PKG" || exit 3
for file in \
    TouchStripGeometry.java \
    CoordinateSpacePolicy.java \
    TouchableStatePolicy.java \
    NavAction.java \
    TopwayWeightedNavPolicy.java \
    NavMediaSelectionPolicy.java \
    NavMediaDispatchPolicy.java
do
    cp -- "$ROOT/lsposed/src/main/java/au/com/cb/ts18/statusbar/input/$file" "$PKG/" || exit 2
done
cp -- "$ROOT/tools/GeometryPolicySelfTest.java" "$PKG/" || exit 2

if ! javac -d "$TMP/classes" "$PKG"/*.java; then
    printf 'FAILED: javac policy compile\n' >&2
    exit 2
fi

if ! java -cp "$TMP/classes" au.com.cb.ts18.statusbar.input.GeometryPolicySelfTest; then
    printf 'FAILED: runtime policy self-test\n' >&2
    exit 2
fi
