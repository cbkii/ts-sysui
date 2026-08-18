#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
RESOLVER="$SCRIPT_DIR/resolve-release-version.sh"
UPDATER="$SCRIPT_DIR/update-source-manifest.sh"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/ts18-release-tools.XXXXXX")"
trap 'rm -rf -- "$TMP"' EXIT

assert_line() {
    local file=$1 expected=$2
    grep -Fxq "$expected" "$file" || {
        echo "ERROR: expected '$expected' in $file" >&2
        cat "$file" >&2
        exit 1
    }
}

make_repo() {
    local dir=$1 version=$2 code=$3
    mkdir -p "$dir/tools"
    printf 'versionName=%s\nversionCode=%s\n' "$version" "$code" > "$dir/version.properties"
    cp "$UPDATER" "$dir/tools/update-source-manifest.sh"
    : > "$dir/SOURCE_MANIFEST.sha256"
    (
        cd "$dir"
        git init -q
        git config user.name fixture
        git config user.email fixture@example.invalid
        git add version.properties tools/update-source-manifest.sh SOURCE_MANIFEST.sha256
        bash tools/update-source-manifest.sh >/dev/null
        git add SOURCE_MANIFEST.sha256
        git commit -qm fixture
    )
}

# Initial release reuses coherent codebase metadata rather than inventing a
# different first tag or versionCode.
repo="$TMP/initial"
make_repo "$repo" 0.4.0 4
(
    cd "$repo"
    bash "$RESOLVER" --tag "" --ref main > result
)
assert_line "$repo/result" tag=v0.4.0
assert_line "$repo/result" version_code=4
assert_line "$repo/result" recovered_metadata=true
assert_line "$repo/result" metadata_changed=false

# Once the current version is tagged, blank manual release selects the next patch
# and increments Android versionCode independently by one.
repo="$TMP/increment"
make_repo "$repo" 0.4.0 4
(
    cd "$repo"
    git tag v0.4.0
    bash "$RESOLVER" --tag "" --ref main > result
)
assert_line "$repo/result" tag=v0.4.1
assert_line "$repo/result" version_code=5
assert_line "$repo/result" recovered_metadata=false
assert_line "$repo/result" metadata_changed=true

# Resume coherent metadata left ahead of the highest tag by a failed release;
# do not consume another patch/versionCode.
repo="$TMP/recover"
make_repo "$repo" 0.4.0 4
(
    cd "$repo"
    git tag v0.4.0
    printf 'versionName=0.4.1\nversionCode=5\n' > version.properties
    bash tools/update-source-manifest.sh >/dev/null
    git add version.properties SOURCE_MANIFEST.sha256
    git commit -qm prepared
    bash "$RESOLVER" --tag "" --ref main > result
)
assert_line "$repo/result" tag=v0.4.1
assert_line "$repo/result" version_code=5
assert_line "$repo/result" recovered_metadata=true
assert_line "$repo/result" metadata_changed=false

# Explicit newer version overwrites canonical source metadata and refreshes the
# tracked source manifest. This is the codebase-sync contract of manual release.
repo="$TMP/explicit-apply"
make_repo "$repo" 0.4.0 4
(
    cd "$repo"
    git tag v0.4.0
    bash "$RESOLVER" --tag v0.5.0 --ref main --apply > result
    grep -Fxq 'versionName=0.5.0' version.properties
    grep -Fxq 'versionCode=5' version.properties
    expected="$(sha256sum version.properties | awk '{print $1}')"
    grep -Fxq "$expected  ./version.properties" SOURCE_MANIFEST.sha256
)
assert_line "$repo/result" tag=v0.5.0
assert_line "$repo/result" version_code=5
assert_line "$repo/result" metadata_changed=true

# versionCode remains monotonic even when the working metadata has previously
# consumed codes above the latest tagged source.
repo="$TMP/code-monotonic"
make_repo "$repo" 0.4.0 4
(
    cd "$repo"
    git tag v0.4.0
    printf 'versionName=0.4.0\nversionCode=10\n' > version.properties
    bash tools/update-source-manifest.sh >/dev/null
    git add version.properties SOURCE_MANIFEST.sha256
    git commit -qm code-ahead
    bash "$RESOLVER" --tag "" --ref main > result
)
assert_line "$repo/result" tag=v0.4.1
assert_line "$repo/result" version_code=11

# Malformed or non-forward explicit versions fail closed.
repo="$TMP/reject"
make_repo "$repo" 0.4.0 4
(
    cd "$repo"
    git tag v0.4.0
    if bash "$RESOLVER" --tag v0.04.1 --ref main >/dev/null 2>&1; then
        echo 'ERROR: resolver accepted a tag with leading zeroes' >&2
        exit 1
    fi
    if bash "$RESOLVER" --tag v0.3.9 --ref main >/dev/null 2>&1; then
        echo 'ERROR: resolver accepted a downgrade below the highest tag' >&2
        exit 1
    fi
)

# Existing immutable tags are never silently moved to a newer source commit.
repo="$TMP/immutable-tag"
make_repo "$repo" 0.4.0 4
(
    cd "$repo"
    git tag v0.4.0
    printf 'extra\n' > extra.txt
    git add extra.txt
    git commit -qm later
    if bash "$RESOLVER" --tag v0.4.0 --ref main >/dev/null 2>&1; then
        echo 'ERROR: resolver accepted an existing tag that does not point to HEAD' >&2
        exit 1
    fi
)

printf 'SUCCESS: release version/tool fixture tests\n'
