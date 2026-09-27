#!/usr/bin/env bash
# Commits, pushes, tags and publishes a GitHub release in one step.
#
# Release assets are the artifacts produced by scripts/build-release.sh. This
# script does not rebuild them: publishing an artifact that did not come from
# the current commit would make the tag history a lie.
#
# Requires a GitHub token in GH_TOKEN (or GITHUB_TOKEN). The token is read from
# the environment only; it is never written to the repository or to a remote
# URL.
#
# Usage:
#   scripts/publish-release.sh <version> [--prerelease] [--allow-missing-api-key]
#
# Example:
#   GH_TOKEN=... scripts/publish-release.sh v0.1.0-alpha.2 --prerelease
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

VERSION=""
PRERELEASE=0
ALLOW_MISSING_API_KEY=0

for arg in "$@"; do
  case "$arg" in
    --prerelease)            PRERELEASE=1 ;;
    --allow-missing-api-key) ALLOW_MISSING_API_KEY=1 ;;
    -h|--help)               sed -n '2,17p' "$0"; exit 0 ;;
    -*)                      echo "Unknown option: $arg" >&2; exit 2 ;;
    *)                       VERSION="$arg" ;;
  esac
done

if [ -z "$VERSION" ]; then
  echo "Error: hace falta una versión, por ejemplo v0.1.0-alpha.2" >&2
  exit 2
fi

if [ -z "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ]; then
  echo "Error: define GH_TOKEN (o GITHUB_TOKEN) con un token de ámbito repo." >&2
  echo "       Do not put the token in this script or in gradle.properties." >&2
  exit 2
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Error: hay cambios sin commitear. Commitea primero para que la etiqueta coincida con la" >&2
  echo "       source it claims to publish." >&2
  exit 1
fi

# Refuse to publish a tree that contains a keystore or secret-like file.
LEAK="$(git ls-files | grep -iE '\.jks$|\.keystore$|\.p12$|signing\.properties$|(^|/)\.env$' || true)"
if [ -n "$LEAK" ]; then
  echo "Error: hay ficheros de keystore o parecidos a secretos versionados:" >&2
  echo "$LEAK" >&2
  exit 1
fi

echo "== 1/5 building release artifacts =="
if [ "$ALLOW_MISSING_API_KEY" -eq 1 ]; then
  ./scripts/build-release.sh --allow-missing-api-key
else
  ./scripts/build-release.sh
fi

echo
echo "== 2/5 recording checksums =="
mkdir -p "$ROOT_DIR/artifacts"
# Bare file names, so the manifest works for someone who downloaded the
# release assets side by side. A path-prefixed manifest would fail
# `sha256sum -c` for exactly the people it is meant to protect.
( cd "$ROOT_DIR/artifacts" && sha256sum ./*.apk ./*.aab | sed 's#\./##' > SHA256SUMS.txt )
cat artifacts/SHA256SUMS.txt

echo
echo "== 3/5 committing checksum manifest =="
if ! git diff --quiet -- artifacts/SHA256SUMS.txt; then
  git add artifacts/SHA256SUMS.txt
  git commit -m "chore: record artifact checksums for $VERSION"
fi

echo
echo "== 4/5 pushing =="
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
git push origin "$BRANCH"
git push origin "HEAD:refs/tags/$VERSION" 2>/dev/null \
  || echo "   (tag $VERSION already exists remotely; reusing it)"

echo
echo "== 5/5 creating GitHub release =="
if [ "$ALLOW_MISSING_API_KEY" -eq 1 ]; then
  echo
  echo "WARNING: built without a DJI App Key. The published binaries cannot"
  echo "         register with DJI and cannot control an aircraft. The release"
  echo "         notes must say so."
  FLAG="--prerelease"
else
  FLAG=""
  [ "$PRERELEASE" -eq 1 ] && FLAG="--prerelease"
fi

NOTES="artifacts/release-notes.md"
[ -f "$NOTES" ] || { echo "Error: falta $NOTES." >&2; exit 1; }

gh release create "$VERSION" \
  --title "DJIFlyPro $VERSION" \
  --notes-file "$NOTES" \
  $FLAG \
  artifacts/*.apk artifacts/*.aab artifacts/SHA256SUMS.txt

echo
echo "Published: https://github.com/JMBermejias/DJIFlyPro/releases/tag/$VERSION"
