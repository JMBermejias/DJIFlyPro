#!/usr/bin/env bash
# Checks whether the prerequisites for building the Legacy (MSDK V4) variant
# are satisfied, and reports exactly which ones are missing.
#
# The V4 SDK is distributed by DJI as an archive to registered developer
# accounts. It is not published to a public Maven repository, so it cannot be
# resolved automatically. This script never downloads or executes anything from
# the network.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LEGACY_DIR="$ROOT_DIR/legacy"
LIB_DIR="$LEGACY_DIR/libs"

fail=0
ok()   { printf '  [ok]   %s\n' "$1"; }
bad()  { printf '  [FAIL] %s\n' "$1"; fail=1; }
warn() { printf '  [warn] %s\n' "$1"; }

echo "Legacy (MSDK V4) preflight"
echo

echo "1. V4 SDK archive"
if [ -d "$LIB_DIR" ] && compgen -G "$LIB_DIR/*.aar" >/dev/null 2>&1; then
  ok "AAR files present in legacy/libs"
  ls -1 "$LIB_DIR" | sed 's/^/         /'
else
  bad "No AAR files in legacy/libs"
  echo "         Download 'DJI MSDK V4.x' from the DJI Developer portal with an"
  echo "         account that has access, then extract the AAR/JAR files into:"
  echo "           $LIB_DIR"
fi

echo
echo "2. Required V4 artifacts"
for artifact in dji-sdk-v4 dji-sdk-v4-aircraft-provided; do
  if compgen -G "$LIB_DIR/*${artifact}*" >/dev/null 2>&1; then
    ok "$artifact present"
  else
    bad "$artifact missing (expected a file matching *${artifact}*)"
  fi
done

echo
echo "3. Gradle module"
if [ -f "$ROOT_DIR/android-sdk-v5-as/settings.gradle" ] \
  && grep -q "legacy" "$ROOT_DIR/android-sdk-v5-as/settings.gradle"; then
  ok "Legacy module registered in settings.gradle"
else
  bad "Legacy module not registered in android-sdk-v5-as/settings.gradle"
  echo "         It must be a separate module with its own applicationId."
  echo "         MSDK V4 and V5 must never be combined in one APK."
fi

echo
echo "4. Separate applicationId"
if grep -rq "com.djiflypro.legacy" "$LEGACY_DIR" 2>/dev/null; then
  ok "Legacy applicationId com.djiflypro.legacy referenced"
else
  warn "No com.djiflypro.legacy applicationId found yet"
  echo "         Register it separately in the DJI Developer console; a V4 key is"
  echo "         not interchangeable with the V5 key of com.djiflypro.app."
fi

echo
echo "5. Hardware identification"
if [ -f "$LEGACY_DIR/hardware.md" ] && grep -qi 'variant' "$LEGACY_DIR/hardware.md"; then
  ok "Phantom 4 variant recorded in legacy/hardware.md"
  sed -n '1,20p' "$LEGACY_DIR/hardware.md" | sed 's/^/         /'
else
  bad "No Phantom 4 variant recorded"
  echo "         Record the exact model (P4, P4P, P4A, P4R) and remote controller"
  echo "         in legacy/hardware.md before writing any device-specific code."
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "Result: prerequisites satisfied. Physical flight testing is still required."
else
  echo "Result: NOT ready to build. The items above must be resolved first."
fi
exit "$fail"
