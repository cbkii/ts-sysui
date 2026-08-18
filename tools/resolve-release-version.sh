#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "usage: $0 --tag <tag-or-empty> --ref <branch> [--apply]" >&2
    exit 2
}

TAG_INPUT=""
REF="main"
APPLY=0
while [ "$#" -gt 0 ]; do
    case "$1" in
        --tag) [ "$#" -ge 2 ] || usage; TAG_INPUT=$2; shift 2 ;;
        --ref) [ "$#" -ge 2 ] || usage; REF=$2; shift 2 ;;
        --apply) APPLY=1; shift ;;
        *) usage ;;
    esac
done

case "$REF" in
    *$'\n'*|*$'\r'*) echo "ERROR: ref must not contain line breaks" >&2; exit 1 ;;
esac

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
    echo "ERROR: release version resolution must run inside a git repository" >&2
    exit 1
}
cd "$ROOT"

if [ "$(git rev-parse --is-shallow-repository 2>/dev/null || printf 'unknown\n')" = true ]; then
    echo "ERROR: release version resolution requires a full checkout with all tags" >&2
    exit 1
fi

VERSION_FILE="$ROOT/version.properties"
[ -s "$VERSION_FILE" ] || { echo "ERROR: missing version.properties" >&2; exit 1; }

normalise_tag() {
    local raw=${1#v} major minor patch
    [[ "$raw" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || return 1
    major=${BASH_REMATCH[1]}; minor=${BASH_REMATCH[2]}; patch=${BASH_REMATCH[3]}
    for part in "$major" "$minor" "$patch"; do
        [[ "$part" = 0 || "$part" != 0* ]] || return 1
    done
    [ "${#major}" -le 4 ] && [ "${#minor}" -le 3 ] && [ "${#patch}" -le 3 ] || return 1
    major=$((10#$major)); minor=$((10#$minor)); patch=$((10#$patch))
    [ "$major" -le 2100 ] && [ "$minor" -le 999 ] && [ "$patch" -le 999 ] || return 1
    printf 'v%d.%d.%d\n' "$major" "$minor" "$patch"
}

version_rank() {
    local version=$1 major minor patch
    IFS=. read -r major minor patch <<<"$version"
    printf '%d\n' "$((10#$major * 1000000 + 10#$minor * 1000 + 10#$patch))"
}

increment_patch() {
    local version=$1 major minor patch
    IFS=. read -r major minor patch <<<"$version"
    patch=$((10#$patch + 1))
    if [ "$patch" -gt 999 ]; then
        patch=0
        minor=$((10#$minor + 1))
    fi
    if [ "$minor" -gt 999 ]; then
        minor=0
        major=$((10#$major + 1))
    fi
    normalise_tag "v${major}.${minor}.${patch}"
}

read_property() {
    local file=$1 key=$2
    sed -n "s/^${key}=//p" "$file" | head -n 1 | tr -d '\r'
}

read_tagged_version_code() {
    local tag=$1 contents code
    contents="$(git show "${tag}:version.properties" 2>/dev/null || true)"
    [ -n "$contents" ] || return 1
    code="$(printf '%s\n' "$contents" | sed -n 's/^versionCode=//p' | head -n 1 | tr -d '\r')"
    [[ "$code" =~ ^[0-9]+$ ]] && [ "$code" -gt 0 ] && [ "$code" -le 2147483647 ] || return 1
    printf '%s\n' "$code"
}

CURRENT_VERSION="$(read_property "$VERSION_FILE" versionName)"
CURRENT_CODE="$(read_property "$VERSION_FILE" versionCode)"
CURRENT_TAG="$(normalise_tag "$CURRENT_VERSION" 2>/dev/null || true)"
[[ -n "$CURRENT_TAG" && "$CURRENT_CODE" =~ ^[0-9]+$ ]] || {
    echo "ERROR: version.properties must contain canonical x.y.z versionName and numeric versionCode" >&2
    exit 1
}
[ "$CURRENT_CODE" -gt 0 ] && [ "$CURRENT_CODE" -le 2147483647 ] || {
    echo "ERROR: versionCode must be between 1 and 2147483647" >&2
    exit 1
}
CURRENT_VERSION=${CURRENT_TAG#v}
CURRENT_RANK="$(version_rank "$CURRENT_VERSION")"

HIGHEST_TAG=""
HIGHEST_VERSION=""
HIGHEST_RANK=-1
HIGHEST_TAG_CODE=0
while IFS= read -r existing_tag; do
    normalised="$(normalise_tag "$existing_tag" 2>/dev/null || true)"
    [ -n "$normalised" ] || continue
    candidate=${normalised#v}
    rank="$(version_rank "$candidate")"
    if [ "$rank" -gt "$HIGHEST_RANK" ]; then
        HIGHEST_TAG=$normalised
        HIGHEST_VERSION=$candidate
        HIGHEST_RANK=$rank
    fi
    tagged_code="$(read_tagged_version_code "$existing_tag" 2>/dev/null || true)"
    if [[ "$tagged_code" =~ ^[0-9]+$ ]] && [ "$tagged_code" -gt "$HIGHEST_TAG_CODE" ]; then
        HIGHEST_TAG_CODE=$tagged_code
    fi
done < <(git tag --list)

AUTO=false
RECOVERED_METADATA=false
TAG_EXISTS=false
METADATA_CHANGED=false

if [ -n "$TAG_INPUT" ]; then
    TAG="$(normalise_tag "$TAG_INPUT")" || {
        echo "ERROR: tag must be canonical vMAJOR.MINOR.PATCH with no leading zeroes" >&2
        exit 1
    }
    VERSION=${TAG#v}
    REQUESTED_RANK="$(version_rank "$VERSION")"

    if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
        TAG_EXISTS=true
        TAG_SHA="$(git rev-list -n 1 "$TAG")"
        HEAD_SHA="$(git rev-parse HEAD)"
        [ "$TAG_SHA" = "$HEAD_SHA" ] || {
            echo "ERROR: existing tag $TAG points to $TAG_SHA, not selected source $HEAD_SHA; immutable-tag repair is not automatic" >&2
            exit 1
        }
        TAG_CODE="$(read_tagged_version_code "$TAG")" || {
            echo "ERROR: existing tag $TAG has no valid versionCode in version.properties" >&2
            exit 1
        }
        VERSION_CODE=$TAG_CODE
    else
        if [ "$HIGHEST_RANK" -ge 0 ] && [ "$REQUESTED_RANK" -le "$HIGHEST_RANK" ]; then
            echo "ERROR: new release $TAG must be newer than existing highest tag $HIGHEST_TAG" >&2
            exit 1
        fi
        if [ "$CURRENT_VERSION" = "$VERSION" ] && [ "$CURRENT_CODE" -gt "$HIGHEST_TAG_CODE" ]; then
            VERSION_CODE=$CURRENT_CODE
        else
            max_code=$CURRENT_CODE
            [ "$HIGHEST_TAG_CODE" -gt "$max_code" ] && max_code=$HIGHEST_TAG_CODE
            [ "$max_code" -lt 2147483647 ] || { echo "ERROR: versionCode range exhausted" >&2; exit 1; }
            VERSION_CODE=$((max_code + 1))
        fi
    fi
else
    AUTO=true
    if [ "$HIGHEST_RANK" -lt 0 ]; then
        TAG=$CURRENT_TAG
        VERSION=$CURRENT_VERSION
        VERSION_CODE=$CURRENT_CODE
        RECOVERED_METADATA=true
    elif [ "$CURRENT_RANK" -gt "$HIGHEST_RANK" ] && [ "$CURRENT_CODE" -gt "$HIGHEST_TAG_CODE" ] && \
         ! git rev-parse -q --verify "refs/tags/$CURRENT_TAG" >/dev/null; then
        TAG=$CURRENT_TAG
        VERSION=$CURRENT_VERSION
        VERSION_CODE=$CURRENT_CODE
        RECOVERED_METADATA=true
    else
        TAG="$(increment_patch "$HIGHEST_VERSION")" || {
            echo "ERROR: automatic version exceeds supported semantic version range" >&2
            exit 1
        }
        VERSION=${TAG#v}
        max_code=$CURRENT_CODE
        [ "$HIGHEST_TAG_CODE" -gt "$max_code" ] && max_code=$HIGHEST_TAG_CODE
        [ "$max_code" -lt 2147483647 ] || { echo "ERROR: versionCode range exhausted" >&2; exit 1; }
        VERSION_CODE=$((max_code + 1))
    fi
fi

if [ "$CURRENT_VERSION" != "$VERSION" ] || [ "$CURRENT_CODE" != "$VERSION_CODE" ]; then
    METADATA_CHANGED=true
fi

if [ "$APPLY" = 1 ] && [ "$TAG_EXISTS" = true ] && [ "$METADATA_CHANGED" = true ]; then
    echo "ERROR: existing immutable tag $TAG does not match current source metadata" >&2
    exit 1
fi

if [ "$APPLY" = 1 ] && [ "$METADATA_CHANGED" = true ]; then
    printf 'versionName=%s\nversionCode=%s\n' "$VERSION" "$VERSION_CODE" > "$VERSION_FILE"
fi

if [ "$APPLY" = 1 ]; then
    if [ -x "$ROOT/tools/update-source-manifest.sh" ]; then
        bash "$ROOT/tools/update-source-manifest.sh" >/dev/null
    else
        echo "ERROR: tools/update-source-manifest.sh is required when applying release metadata" >&2
        exit 1
    fi
fi

printf 'tag=%s\n' "$TAG"
printf 'version=%s\n' "$VERSION"
printf 'version_code=%s\n' "$VERSION_CODE"
printf 'auto=%s\n' "$AUTO"
printf 'recovered_metadata=%s\n' "$RECOVERED_METADATA"
printf 'metadata_changed=%s\n' "$METADATA_CHANGED"
printf 'tag_exists=%s\n' "$TAG_EXISTS"
printf 'highest_tag=%s\n' "${HIGHEST_TAG:-none}"
