#!/usr/bin/env bash
# Regenerate SOURCE_MANIFEST.sha256 from tracked repository files.

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" || exit 3
ROOT="$(dirname -- "$SCRIPT_DIR")"
OUTPUT="$ROOT/SOURCE_MANIFEST.sha256"

for cmd in git sha256sum sort mktemp; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        printf 'FAILED: %s is required\n' "$cmd" >&2
        exit 3
    fi
done

TMP="$(mktemp "${TMPDIR:-/tmp}/ts18-source-manifest.XXXXXX")" || exit 3
cleanup() {
    rm -f -- "$TMP"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

cd "$ROOT" || exit 3
while IFS= read -r -d '' path; do
    [ "$path" = "SOURCE_MANIFEST.sha256" ] && continue
    sha="$(sha256sum -- "$path")" || exit 2
    printf '%s  ./%s\n' "${sha%% *}" "$path"
done < <(git ls-files -z) | LC_ALL=C sort -k2 > "$TMP" || exit 2

mv -- "$TMP" "$OUTPUT" || exit 2
trap - EXIT HUP INT TERM
printf 'SUCCESS: regenerated %s\n' "$OUTPUT"
