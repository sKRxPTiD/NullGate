#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SDK_DIR="${ANDROID_HOME:-$HOME/Android/Sdk}"
BUILD_TOOLS="$SDK_DIR/build-tools/36.0.0"
ANDROID_JAR="$SDK_DIR/platforms/android-36/android.jar"
OUT_DIR="$BASE_DIR/build"
DIST_DIR="$BASE_DIR/dist"
KEY_DIR="${NULLGATE_KEY_DIR:-$BASE_DIR/keys}"
KEYSTORE="$KEY_DIR/nullgate-local.keystore"
KEYPASS_FILE="$KEY_DIR/.nullgate-local.pass"

for binary in "$BUILD_TOOLS/aapt2" "$BUILD_TOOLS/d8" "$BUILD_TOOLS/zipalign" "$BUILD_TOOLS/apksigner" "$ANDROID_JAR"; do
  [[ -e "$binary" ]] || { echo "Missing Android 36 build dependency: $binary" >&2; exit 1; }
done
command -v javac >/dev/null
command -v keytool >/dev/null
command -v zip >/dev/null
command -v openssl >/dev/null

bash "$BASE_DIR/client/test.sh"
bash "$BASE_DIR/test-client/test.sh"
bash "$BASE_DIR/broker/test.sh"

rm -rf "$OUT_DIR" "$DIST_DIR"
mkdir -p "$OUT_DIR/compiled-res" "$OUT_DIR/classes" "$OUT_DIR/dex" "$OUT_DIR/broker-classes" \
  "$OUT_DIR/broker-dex" "$OUT_DIR/client-reference" "$OUT_DIR/test-client-classes" \
  "$OUT_DIR/test-client-dex" "$DIST_DIR" "$KEY_DIR"
chmod 0700 "$KEY_DIR"
if [[ -f "$KEYSTORE" && ! -f "$KEYPASS_FILE" || -f "$KEYPASS_FILE" && ! -f "$KEYSTORE" ]]; then
  echo "Incomplete signing identity; restore the existing key pair, do not silently replace it." >&2
  exit 1
fi

"$BUILD_TOOLS/aapt2" compile --dir "$BASE_DIR/res" -o "$OUT_DIR/compiled-res"
"$BUILD_TOOLS/aapt2" link --manifest "$BASE_DIR/AndroidManifest.xml" \
  -I "$ANDROID_JAR" --min-sdk-version 26 --target-sdk-version 36 \
  -o "$OUT_DIR/base.apk" "$OUT_DIR/compiled-res"/*.flat

javac --release 8 -classpath "$ANDROID_JAR" -d "$OUT_DIR/classes" \
  $(find "$BASE_DIR/common/src" -name '*.java' -print) \
  $(find "$BASE_DIR/src" -name '*.java' -print)
"$BUILD_TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$OUT_DIR/dex" \
  $(find "$OUT_DIR/classes" -name '*.class' -print)
(cd "$OUT_DIR/dex" && zip -q -j "$OUT_DIR/base.apk" classes.dex)

if [[ ! -f "$KEYPASS_FILE" ]]; then
  (umask 077 && openssl rand -hex 32 > "$KEYPASS_FILE")
fi
chmod 0600 "$KEYPASS_FILE"
if [[ ! -f "$KEYSTORE" ]]; then
  (umask 077; keytool -genkeypair -keystore "$KEYSTORE" -storepass:file "$KEYPASS_FILE" \
    -keypass:file "$KEYPASS_FILE" -alias nullgate-local \
    -dname "CN=NullGate Local,O=Null Protocol,C=US" \
    -keyalg RSA -keysize 2048 -validity 3650 >/dev/null 2>&1)
fi
chmod 0600 "$KEYSTORE"
chmod 0600 "$KEY_DIR"/*.keystore

# Compile the standalone client reference against the same Android API surface.
javac --release 8 -classpath "$ANDROID_JAR" -d "$OUT_DIR/client-reference" \
  $(find "$BASE_DIR/client/reference" -name '*.java' -print)

# Build the first-party external-app harness as a separate package and UID.
"$BUILD_TOOLS/aapt2" link --manifest "$BASE_DIR/test-client/AndroidManifest.xml" \
  -I "$ANDROID_JAR" --min-sdk-version 26 --target-sdk-version 36 \
  -o "$OUT_DIR/test-client-base.apk"
javac --release 8 -classpath "$ANDROID_JAR" -d "$OUT_DIR/test-client-classes" \
  "$BASE_DIR/common/src/org/nullprotocol/nullgate/protocol/ExternalClientContract.java" \
  $(find "$BASE_DIR/test-client/src" -name '*.java' -print)
"$BUILD_TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$OUT_DIR/test-client-dex" \
  $(find "$OUT_DIR/test-client-classes" -name '*.class' -print)
(cd "$OUT_DIR/test-client-dex" && zip -q -j "$OUT_DIR/test-client-base.apk" classes.dex)

"$BUILD_TOOLS/zipalign" -f 4 "$OUT_DIR/base.apk" "$OUT_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias nullgate-local \
  --ks-pass "file:$KEYPASS_FILE" \
  --out "$DIST_DIR/NullGate-prototype-debug.apk" "$OUT_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$DIST_DIR/NullGate-prototype-debug.apk"

"$BUILD_TOOLS/zipalign" -f 4 "$OUT_DIR/test-client-base.apk" \
  "$OUT_DIR/test-client-aligned.apk"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias nullgate-local \
  --ks-pass "file:$KEYPASS_FILE" \
  --out "$DIST_DIR/NullGate-test-client-debug.apk" "$OUT_DIR/test-client-aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$DIST_DIR/NullGate-test-client-debug.apk"

javac --release 8 -classpath "$ANDROID_JAR" -d "$OUT_DIR/broker-classes" \
  $(find "$BASE_DIR/common/src" "$BASE_DIR/broker/src" "$BASE_DIR/broker/android" -name '*.java' -print)
"$BUILD_TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$OUT_DIR/broker-dex" \
  $(find "$OUT_DIR/broker-classes" -name '*.class' -print)
(cd "$OUT_DIR/broker-dex" && zip -q -j "$DIST_DIR/NullGate-broker.jar" classes.dex)
"$BUILD_TOOLS/apksigner" verify --print-certs "$DIST_DIR/NullGate-prototype-debug.apk" \
  | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' > "$DIST_DIR/controller-cert-sha256.txt"
(cd "$DIST_DIR" && sha256sum NullGate-prototype-debug.apk NullGate-test-client-debug.apk \
  NullGate-broker.jar \
  controller-cert-sha256.txt > SHA256SUMS)
echo "Built: $DIST_DIR/NullGate-prototype-debug.apk"
echo "Built: $DIST_DIR/NullGate-test-client-debug.apk"
echo "Built: $DIST_DIR/NullGate-broker.jar"
bash "$BASE_DIR/device/test.sh"
