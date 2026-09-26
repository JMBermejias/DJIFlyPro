#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/android-sdk-v5-as"

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ROOT_DIR/tools/android-sdk}"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"

if [ -f "$ROOT_DIR/.local/signing.properties" ]; then
  while IFS='=' read -r key value; do
    case "$key" in
      STORE_FILE|STORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD)
        export "ORG_GRADLE_PROJECT_${key}=${value}"
        ;;
    esac
  done < "$ROOT_DIR/.local/signing.properties"
fi

# The release build refuses to run without a DJI App Key, because an APK built
# without one can never register with the SDK. Pass --allow-missing-api-key only
# to exercise the R8/lint/signing pipeline locally; the result is not
# distributable. See docs/BUILD.md.
GRADLE_ARGS=()
MODE="distributable"
for arg in "$@"; do
  case "$arg" in
    --allow-missing-api-key)
      export DJIFLYPRO_ALLOW_MISSING_API_KEY=1
      MODE="local-verification-only"
      ;;
    *)
      GRADLE_ARGS+=("$arg")
      ;;
  esac
done

./gradlew :sample:assembleRelease :sample:bundleRelease --stacktrace "${GRADLE_ARGS[@]}"

# Name artifacts after the product and the version the build actually reports,
# so a downloaded file identifies itself instead of being called
# "sample-release.apk". The version comes from the build, never from a literal
# repeated here.
VERSION_NAME="$(./gradlew -q :sample:printAppVersion --console=plain \
  | sed -n 's/^versionName=//p' | tail -1 | tr -d '[:space:]')"
if [ -z "$VERSION_NAME" ]; then
  echo "Error: could not read versionName from the build." >&2
  exit 1
fi
printf 'Building DJIFlyPro %s (mode: %s)\n' "$VERSION_NAME" "$MODE"

mkdir -p "$ROOT_DIR/artifacts"
OUT="$ROOT_DIR/android-sdk-v5-sample/build/outputs"

stage() { # <source> <destination>
  [ -f "$1" ] || { echo "Error: expected $1 to exist" >&2; exit 1; }
  cp -f "$1" "$2"
}

stage "$OUT/apk/release/sample-release.apk" \
      "$ROOT_DIR/artifacts/DJIFlyPro-$VERSION_NAME.apk"
stage "$OUT/bundle/release/sample-release.aab" \
      "$ROOT_DIR/artifacts/DJIFlyPro-$VERSION_NAME.aab"

if [ -f "$OUT/apk/debug/sample-debug.apk" ]; then
  stage "$OUT/apk/debug/sample-debug.apk" \
        "$ROOT_DIR/artifacts/DJIFlyPro-$VERSION_NAME-debug.apk"
fi

# The hash manifest has to describe exactly the binaries that were just staged.
# SHA256SUMS.txt is tracked in git while the binaries are not, so nothing
# regenerates it on an ordinary rebuild and it silently goes stale: a manifest
# left over from an earlier build ends up next to different files and
# `sha256sum -c` fails for whoever downloads the release. Writing it here, over
# the staged artifacts, is what prevents that. Then verify it immediately, so a
# mistake fails this script instead of the person installing the APK.
(
  cd "$ROOT_DIR/artifacts"
  if ! sha256sum ./*.apk ./*.aab >/dev/null 2>&1; then
    echo "Error: no artifacts staged to hash" >&2
    exit 1
  fi
  sha256sum ./*.apk ./*.aab | sed 's#\./##' > SHA256SUMS.txt
  if ! sha256sum -c SHA256SUMS.txt >/dev/null 2>&1; then
    echo "Error: SHA256SUMS.txt does not match the staged artifacts" >&2
    exit 1
  fi
  cat SHA256SUMS.txt
)

printf 'Artifacts copied to %s/artifacts (mode: %s)\n' "$ROOT_DIR" "$MODE"
ls -l "$ROOT_DIR/artifacts" | grep -E '\.apk$|\.aab$' || true
