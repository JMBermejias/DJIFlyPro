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
mkdir -p "$ROOT_DIR/artifacts"
find "$ROOT_DIR/android-sdk-v5-sample/build/outputs" -type f \( -name '*.apk' -o -name '*.aab' \) -exec cp -f {} "$ROOT_DIR/artifacts/" \;
printf 'Artifacts copied to %s/artifacts (mode: %s)\n' "$ROOT_DIR" "$MODE"
