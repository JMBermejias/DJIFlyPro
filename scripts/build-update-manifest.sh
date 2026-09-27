#!/usr/bin/env bash
#
# Writes artifacts/update.json for a release: what the installed app reads to
# decide whether a newer build exists, and what it must check before handing a
# downloaded APK to the system installer.
#
# The manifest exists because the GitHub API says nothing about the version of
# an APK, and a tag name is a string two different builds could share. The
# versionCode here is read from the build, and both digests from the artefacts
# that were just built, so nothing in the file can drift from what is published.
#
# Usage: scripts/build-update-manifest.sh [tag]

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

VERSION_NAME="$(cd android-sdk-v5-as && ./gradlew -q :sample:printAppVersion --console=plain \
  | sed -n 's/^versionName=//p' | tail -1 | tr -d '[:space:]')"
VERSION_CODE="$(cd android-sdk-v5-as && ./gradlew -q :sample:printAppVersion --console=plain \
  | sed -n 's/^versionCode=//p' | tail -1 | tr -d '[:space:]')"
if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
  echo "Error: no se ha podido leer la versión del build." >&2
  exit 1
fi

TAG="${1:-}"
if [ -z "$TAG" ]; then
  TAG="v${VERSION_NAME}"
fi
REPO="JMBermejias/DJIFlyPro"
APK="artifacts/DJIFlyPro-${VERSION_NAME}.apk"
if [ ! -f "$APK" ]; then
  echo "Error: $APK no existe. Ejecuta antes scripts/build-release.sh." >&2
  exit 1
fi

APK_SIZE="$(stat -c %s "$APK")"
APK_SHA="$(sha256sum "$APK" | cut -d' ' -f1)"
RELEASE_URL="https://github.com/${REPO}/releases/tag/${TAG}"
# The asset URL is NOT derived from RELEASE_URL. /releases/tag/<tag>/download/<asset>
# answers with the release's HTML page, not with the asset: it returns 206 for a
# range request, so a status check passes, and what arrives where 205 MB of APK
# should be is ~218 KB of HTML. The app then fails the size check and refuses to
# install, with a mismatch error that points at tampering rather than at a URL.
# /releases/download/<tag>/<asset> is the only form that serves the file itself.
APK_URL="https://github.com/${REPO}/releases/download/${TAG}/DJIFlyPro-${VERSION_NAME}.apk"

# The certificate digest of the release signing key. The updater compares it
# with the one of the installed app and refuses to install when they differ, so
# it has to be the real digest of the real key, not a copy-paste from a
# previous build.
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ROOT_DIR/tools/android-sdk}"
BT="$(ls -d "$ANDROID_SDK_ROOT"/build-tools/* 2>/dev/null | sort -V | tail -1)"
if [ -z "$BT" ] || [ ! -x "$BT/apksigner" ]; then
  echo "Error: no se encuentra apksigner en $ANDROID_SDK_ROOT/build-tools." >&2
  exit 1
fi
export JAVA_HOME="${JAVA_HOME:-$ROOT_DIR/tools/jdk/17}"
export PATH="$JAVA_HOME/bin:$PATH"
CERT_SHA="$("$BT/apksigner" verify --print-certs "$APK" 2>/dev/null \
  | sed -n 's/.*certificate SHA-256 digest: //p' | tr -d '[:space:]' | tr 'A-Z' 'a-z' | head -1)"
if [ -z "$CERT_SHA" ]; then
  echo "Error: no se ha podido leer el resumen del certificado de firma de $APK." >&2
  exit 1
fi

# Notes are a short digest of the release notes, so the in-app notice has
# something to say without shipping the whole file.
NOTES="$(awk '/^## /{section=$0} /^## Novedad/{print section; exit}' artifacts/release-notes.md 2>/dev/null | sed 's/^## //')"
[ -z "$NOTES" ] && NOTES="Actualización publicada"

json_escape() { printf '%s' "$1" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read())[1:-1])'; }

cat > artifacts/update.json <<EOF
{
  "schema": "djiflypro.update/v1",
  "versionCode": ${VERSION_CODE},
  "versionName": "$(json_escape "$VERSION_NAME")",
  "tag": "$(json_escape "$TAG")",
  "apkUrl": "$(json_escape "$APK_URL")",
  "apkSize": ${APK_SIZE},
  "sha256": "$(json_escape "$APK_SHA")",
  "signingCertificateSha256": "$(json_escape "$CERT_SHA")",
  "releaseUrl": "$(json_escape "$RELEASE_URL")",
  "notes": "$(json_escape "$NOTES")",
  "publishedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

python3 -c "import json; json.load(open('artifacts/update.json'))" \
  || { echo "Error: el manifiesto generado no es JSON válido." >&2; exit 1; }

echo "Escrito artifacts/update.json para ${TAG} (versionCode ${VERSION_CODE})"
sed 's/^/  /' artifacts/update.json
