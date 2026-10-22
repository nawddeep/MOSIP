#!/usr/bin/env bash
# Copies the platform-neutral liveness core (plain Java, no Android imports) and the vendored
# MiniFASNet PAD library from the nawddeep/MOSIP Android repo into this module.
#
#   ./sync-from-android.sh /path/to/MOSIP/android      # the folder that contains livenessmanager/ and minifasnet/
#
# Test-side adjustments only (listed in UPSTREAM.md):
#   * LivenessConfigTest: the default config now lives under src/main/resources/liveness/
#   * MiniFasNetPadTest: the model directory default now points at the bundled resources
#   * ArchitectureDocTest is not copied: it checks the Android repo's docs/liveness/01-architecture.md,
#     which does not exist in this repository.
# Main sources are copied byte for byte. The `android/` package is never copied.
set -euo pipefail

ANDROID_DIR="${1:?usage: sync-from-android.sh <path to MOSIP/android>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
LM="$ANDROID_DIR/livenessmanager/src"
PKG="io/mosip/registration/livenessmanager"
MAIN="$HERE/src/main/java"
TEST="$HERE/src/test/java"
RES="$HERE/src/main/resources/liveness"

[ -d "$LM/main/java/$PKG" ] || { echo "not found: $LM/main/java/$PKG" >&2; exit 1; }

# Start from a clean copy so removed upstream files do not linger.
rm -rf "${MAIN:?}/$PKG" "${MAIN:?}/org" "${TEST:?}/$PKG" "${TEST:?}/org" "${RES:?}"
mkdir -p "$MAIN/$PKG" "$MAIN/org" "$TEST/$PKG" "$TEST/org" "$RES/models/minifasnet"

for d in api model core challenge quality engine config audit eval desktop; do
  cp -R "$LM/main/java/$PKG/$d" "$MAIN/$PKG/$d"
  [ -d "$LM/test/java/$PKG/$d" ] && cp -R "$LM/test/java/$PKG/$d" "$TEST/$PKG/$d"
done
# Shared test helpers sit directly in the test package.
cp "$LM"/test/java/$PKG/*.java "$TEST/$PKG/"

cp -R "$ANDROID_DIR/minifasnet/src/main/java/org/minifasnet" "$MAIN/org/minifasnet"
cp -R "$ANDROID_DIR/minifasnet/src/test/java/org/minifasnet" "$TEST/org/minifasnet"

cp "$LM/main/assets/liveness/default_liveness_config.json" "$RES/"
cp "$LM/main/assets/liveness/models/minifasnet/"*.onnx "$RES/models/minifasnet/"
cp "$LM/main/assets/liveness/models/minifasnet/LICENSE-MiniFASNet-Apache-2.0.txt" "$RES/models/minifasnet/"
cp "$ANDROID_DIR/minifasnet/MODEL_CARD.md" "$ANDROID_DIR/minifasnet/UPSTREAM.md" "$HERE/"

# Android-repo docs check, not an engine test (see header).
rm -f "${TEST:?}/$PKG/core/ArchitectureDocTest.java"

# Test-only path adjustments (see header).
sed -i.bak 's#src/main/assets/liveness/default_liveness_config.json#src/main/resources/liveness/default_liveness_config.json#' \
  "$TEST/$PKG/config/LivenessConfigTest.java"
sed -i.bak 's#System.getProperty("modelDir", "../models")#System.getProperty("modelDir", "src/main/resources/liveness/models/minifasnet")#' \
  "$TEST/org/minifasnet/MiniFasNetPadTest.java"
find "$HERE/src" -name '*.bak' -delete

echo "synced from $ANDROID_DIR"
echo "main files: $(find "$MAIN" -name '*.java' | wc -l)   test files: $(find "$TEST" -name '*.java' | wc -l)"
