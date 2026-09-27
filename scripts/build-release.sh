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

# The DJI App Key lives in .local/, which is git-ignored, and reaches Gradle as
# an environment variable. gradle.properties is tracked, so a key written there
# would be committed; the build reads the environment first precisely so the
# local file is enough and nothing has to be versioned.
#
# The values are unquoted here on purpose. These files are written in Java
# .properties syntax, where `KEY="value"` is legal and means `value`, and that is
# how people write secrets. But this loop is bash, not a .properties parser, so
# the quotes would survive into the env var, into manifestPlaceholders and into
# the built manifest as literal quote characters: DJI received a 26-character App
# Key with a `"` at each end instead of the real 24, and rejected it with
# INVALID_METADATA, which looks exactly like a wrong key and is not.
strip_surrounding_quotes() {
  local v="$1"
  case "$v" in
    \"*\") v="${v#\"}"; v="${v%\"}" ;;
    \'*\') v="${v#\'}"; v="${v%\'}" ;;
  esac
  printf '%s' "$v"
}

if [ -f "$ROOT_DIR/.local/api-key.properties" ]; then
  while IFS='=' read -r key value; do
    case "$key" in
      AIRCRAFT_API_KEY|MAPLIBRE_TOKEN)
        # MAPLIBRE_TOKEN is a Mapbox token or a MapTiler key, not a DJI key: DJI
        # only provides the manifest slot `com.dji.mapkit.maplibre.apikey`. It
        # was missing from this list, which meant a token written here was
        # dropped on the floor and the APK shipped with a blank map, with no
        # warning anywhere. Every value that reaches the build has to be listed
        # here, or a key is silently ignored.
        value="$(strip_surrounding_quotes "$value")"
        case "$value" in
          \#*|"") ;;
          *) export "${key}=${value}" ;;
        esac
        ;;
    esac
  done < "$ROOT_DIR/.local/api-key.properties"
fi

# The release build refuses to run without a DJI App Key, because an APK built
# without one can never register with the SDK. Pass --allow-missing-api-key only
# to exercise the R8/lint/signing pipeline locally; the result is not
# distributable. See docs/BUILD.md.
GRADLE_ARGS=()
MODE="distributable"
TAG=""
while [ $# -gt 0 ]; do
  case "$1" in
    --allow-missing-api-key)
      export DJIFLYPRO_ALLOW_MISSING_API_KEY=1
      MODE="local-verification-only"
      shift
      ;;
    --tag)
      [ $# -ge 2 ] || { echo "Error: --tag necesita un valor" >&2; exit 1; }
      TAG="$2"
      shift 2
      ;;
    --tag=*)
      TAG="${1#--tag=}"
      shift
      ;;
    *)
      GRADLE_ARGS+=("$1")
      shift
      ;;
  esac
done

# Only the release variant belongs in a release. The debug variant is signed
# with a different certificate ("CN=Android Debug") than the release one
# ("CN=DJIFlyPro Local Test"), so the two cannot be installed over each other:
# whoever installed the release APK first cannot sideload the debug APK without
# uninstalling first, and several OEM installers report that refusal as "the
# app is not valid". The debug APK is also android:debuggable and carries
# unstripped symbols, which is a reason not to hand it to anyone.
#
# It is still buildable on demand for local debugging, just not publishable:
#   ./gradlew :sample:assembleDebug
./gradlew :sample:assembleRelease :sample:bundleRelease --stacktrace "${GRADLE_ARGS[@]}"

# Name artifacts after the product and the version the build actually reports,
# so a downloaded file identifies itself instead of being called
# "sample-release.apk". The version comes from the build, never from a literal
# repeated here.
VERSION_NAME="$(./gradlew -q :sample:printAppVersion --console=plain \
  | sed -n 's/^versionName=//p' | tail -1 | tr -d '[:space:]')"
if [ -z "$VERSION_NAME" ]; then
  echo "Error: no se ha podido leer el versionName del build." >&2
  exit 1
fi
printf 'Building DJIFlyPro %s (mode: %s)\n' "$VERSION_NAME" "$MODE"

mkdir -p "$ROOT_DIR/artifacts"
OUT="$ROOT_DIR/android-sdk-v5-sample/build/outputs"

stage() { # <source> <destination>
  [ -f "$1" ] || { echo "Error: se esperaba que $1 existiera" >&2; exit 1; }
  cp -f "$1" "$2"
}

# Only the release variant is staged. A leftover debug APK from an earlier run
# would otherwise be picked up by the hashing below and published next to a
# release it was not built from.
rm -f "$ROOT_DIR"/artifacts/DJIFlyPro-*-debug.apk

stage "$OUT/apk/release/sample-release.apk" \
      "$ROOT_DIR/artifacts/DJIFlyPro-$VERSION_NAME.apk"
stage "$OUT/bundle/release/sample-release.aab" \
      "$ROOT_DIR/artifacts/DJIFlyPro-$VERSION_NAME.aab"

# The update manifest is written once the binaries it describes exist, so its
# versionCode, size and digests cannot drift from what was just built. It is
# hashed below along with them, because the app verifies that digest before it
# will hand a downloaded APK to the system installer.
"$ROOT_DIR/scripts/build-update-manifest.sh" "$TAG"

# The hash manifest has to describe exactly the binaries that were just staged.
# SHA256SUMS.txt is tracked in git while the binaries are not, so nothing
# regenerates it on an ordinary rebuild and it silently goes stale: a manifest
# left over from an earlier build ends up next to different files and
# `sha256sum -c` fails for whoever downloads the release. Writing it here, over
# the staged artifacts, is what prevents that. Then verify it immediately, so a
# mistake fails this script instead of the person installing the APK.
(
  cd "$ROOT_DIR/artifacts"
  if [ ! -f "DJIFlyPro-$VERSION_NAME.apk" ] || [ ! -f "DJIFlyPro-$VERSION_NAME.aab" ] || [ ! -f update.json ]; then
    echo "Error: los artefactos de la release no están en el índice" >&2
    exit 1
  fi
  # Only the two release artifacts, named explicitly, so the manifest describes
  # this release and nothing else that happens to be lying in the directory.
  # update.json is included: the updater refuses to install anything whose hash
  # is not in it, so it has to travel with the manifest that points at them.
  sha256sum "DJIFlyPro-$VERSION_NAME.apk" "DJIFlyPro-$VERSION_NAME.aab" update.json \
    | sed 's#\./##' > SHA256SUMS.txt
  if ! sha256sum -c SHA256SUMS.txt >/dev/null 2>&1; then
    echo "Error: SHA256SUMS.txt no coincide con los artefactos del índice" >&2
    exit 1
  fi
  cat SHA256SUMS.txt
)

printf 'Artifacts copied to %s/artifacts (mode: %s)\n' "$ROOT_DIR" "$MODE"
ls -l "$ROOT_DIR/artifacts" | grep -E '\.apk$|\.aab$' || true
