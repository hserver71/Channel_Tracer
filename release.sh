#!/usr/bin/env bash
set -euo pipefail

REPO="hserver71/Channel_Tracer"
ASSET_NAME="channel_tracer.apk"
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

usage() {
    cat <<'EOF'
Usage: ./release.sh [version] [commit message]

Examples:
  ./release.sh              # uses versionName from app/build.gradle (e.g. v1.0)
  ./release.sh v1.1         # tag and upload release v1.1
  ./release.sh v1.1 "Fix stream timeout handling"

Token is read from git remote origin URL, .env, or GITHUB_TOKEN (in that order).

Requires: ANDROID_HOME, JAVA_HOME, curl, python3
EOF
}

GIT="/usr/bin/git"
if [[ ! -x "$GIT" ]]; then
    GIT="git"
fi

resolve_github_token() {
    if [[ -n "${GITHUB_TOKEN:-}" ]]; then
        echo "$GITHUB_TOKEN"
        return 0
    fi
    if [[ -f "$ROOT/.env" ]]; then
        # shellcheck disable=SC1091
        source "$ROOT/.env"
        if [[ -n "${GITHUB_TOKEN:-}" ]]; then
            echo "$GITHUB_TOKEN"
            return 0
        fi
    fi
    local url
    url="$($GIT remote get-url origin 2>/dev/null || true)"
    if [[ "$url" =~ ^https://([^@]+)@github.com/ ]]; then
        echo "${BASH_REMATCH[1]}"
        return 0
    fi
    return 1
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

if ! GITHUB_TOKEN="$(resolve_github_token)"; then
    echo "Error: no GitHub token found." >&2
    echo "  Set origin to https://TOKEN@github.com/user/repo.git, or" >&2
    echo "  export GITHUB_TOKEN=ghp_..., or create $ROOT/.env" >&2
    exit 1
fi
export GITHUB_TOKEN

VERSION="${1:-}"
COMMIT_MSG="${2:-}"

if [[ -z "$VERSION" ]]; then
    VERSION="$(grep versionName app/build.gradle | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
fi
if [[ "$VERSION" != v* ]]; then
    VERSION="v${VERSION}"
fi

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"

echo "==> Version: $VERSION"

echo "==> Git status"
if [[ -n "$($GIT status --porcelain)" ]]; then
    if [[ -z "$COMMIT_MSG" ]]; then
        COMMIT_MSG="Release ${VERSION}"
    fi
    export GIT_AUTHOR_NAME="${GIT_AUTHOR_NAME:-hserver71}"
    export GIT_AUTHOR_EMAIL="${GIT_AUTHOR_EMAIL:-hserver71@users.noreply.github.com}"
    export GIT_COMMITTER_NAME="${GIT_COMMITTER_NAME:-hserver71}"
    export GIT_COMMITTER_EMAIL="${GIT_COMMITTER_EMAIL:-hserver71@users.noreply.github.com}"
    $GIT add -A
    $GIT commit --no-verify -m "$COMMIT_MSG"
    echo "    Committed: $COMMIT_MSG"
else
    echo "    No local changes to commit"
fi

echo "==> Push to GitHub"
$GIT push origin main

echo "==> Build APK"
./gradlew assembleDebug --quiet

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
    echo "Error: APK not found at $APK" >&2
    exit 1
fi

RELEASE_APK="$(mktemp /tmp/channel_tracer.XXXXXX.apk)"
cp "$APK" "$RELEASE_APK"
echo "    Built: $APK ($(du -h "$APK" | awk '{print $1}'))"

api() {
    curl -sS -H "Authorization: token ${GITHUB_TOKEN}" \
        -H "Accept: application/vnd.github+json" \
        "$@"
}

echo "==> GitHub Release: $VERSION"
RELEASE_JSON="$(api "https://api.github.com/repos/${REPO}/releases/tags/${VERSION}")"

RELEASE_ID="$(python3 - <<'PY' "$RELEASE_JSON"
import json, sys
data = json.loads(sys.argv[1])
print(data.get("id") or "")
PY
)"

if [[ -z "$RELEASE_ID" ]]; then
    echo "    Creating release ${VERSION}"
    RELEASE_JSON="$(api -X POST "https://api.github.com/repos/${REPO}/releases" \
        -d "{\"tag_name\":\"${VERSION}\",\"target_commitish\":\"main\",\"name\":\"${VERSION}\",\"body\":\"Channel Tracer ${VERSION}\",\"draft\":false,\"prerelease\":false}")"
    RELEASE_ID="$(python3 - <<'PY' "$RELEASE_JSON"
import json, sys
data = json.loads(sys.argv[1])
if "id" not in data:
    raise SystemExit(data.get("message", data))
print(data["id"])
PY
)"
else
    echo "    Release exists (id: $RELEASE_ID)"
fi

echo "==> Remove old ${ASSET_NAME} asset if present"
ASSETS_JSON="$(api "https://api.github.com/repos/${REPO}/releases/${RELEASE_ID}/assets")"
python3 - <<'PY' "$ASSETS_JSON" "$ASSET_NAME" "$GITHUB_TOKEN" "$REPO"
import json, sys, subprocess
assets = json.loads(sys.argv[1])
name = sys.argv[2]
token = sys.argv[3]
repo = sys.argv[4]
for asset in assets:
    if asset.get("name") == name:
        asset_id = asset["id"]
        url = f"https://api.github.com/repos/{repo}/releases/assets/{asset_id}"
        subprocess.run(
            ["curl", "-sS", "-X", "DELETE", "-H", f"Authorization: token {token}", url],
            check=True,
        )
        print(f"    Deleted old asset id {asset_id}")
PY

echo "==> Upload ${ASSET_NAME}"
UPLOAD_JSON="$(curl -sS -X POST \
    -H "Authorization: token ${GITHUB_TOKEN}" \
    -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary @"$RELEASE_APK" \
    "https://uploads.github.com/repos/${REPO}/releases/${RELEASE_ID}/assets?name=${ASSET_NAME}")"

DOWNLOAD_URL="$(python3 - <<'PY' "$UPLOAD_JSON"
import json, sys
data = json.loads(sys.argv[1])
if "browser_download_url" not in data:
    raise SystemExit(data.get("message", data))
print(data["browser_download_url"])
PY
)"

rm -f "$RELEASE_APK"

echo
echo "Done."
echo "  Repo:    https://github.com/${REPO}"
echo "  Release: https://github.com/${REPO}/releases/tag/${VERSION}"
echo "  APK:     ${DOWNLOAD_URL}"
