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
KEYPASS_FILE="${NULLGATE_KEYPASS_FILE:-$KEYPASS_FILE}"
RUN_DEVICE_HARNESS=1
INIT_DEV_SIGNING=0
PREFLIGHT_ONLY=0
REPRODUCIBLE_EPOCH=946684800
export TZ=UTC

usage() {
  cat <<'EOF'
Usage: ./build.sh [--preflight] [--host-only] [--init-dev-signing]

  --preflight          Validate dependencies, metadata, and the selected
                       signing identity without writing build output.
  --host-only          Build and verify host artifacts without the simulated
                       device-helper suite.
  --init-dev-signing   Explicitly create the local development signing
                       identity when neither signing file exists.
EOF
}

while (($#)); do
  case "$1" in
    --preflight)
      PREFLIGHT_ONLY=1
      ;;
    --host-only)
      RUN_DEVICE_HARNESS=0
      ;;
    --init-dev-signing)
      INIT_DEV_SIGNING=1
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown build option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

if ((PREFLIGHT_ONLY && INIT_DEV_SIGNING)); then
  echo "--preflight cannot be combined with --init-dev-signing" >&2
  exit 2
fi

die() {
  echo "NullGate build: $*" >&2
  exit 1
}

normalize_archive_inputs() {
  local directory="$1"
  find "$directory" -type f -exec touch -d "@$REPRODUCIBLE_EPOCH" -- {} +
}

INIT_TMP_DIR=""
cleanup_signing_init() {
  if [[ -n "$INIT_TMP_DIR" && -d "$INIT_TMP_DIR" ]]; then
    rm -rf -- "$INIT_TMP_DIR"
  fi
}
trap cleanup_signing_init EXIT

for binary in "$BUILD_TOOLS/aapt2" "$BUILD_TOOLS/d8" "$BUILD_TOOLS/zipalign" "$BUILD_TOOLS/apksigner" "$ANDROID_JAR"; do
  [[ -e "$binary" ]] || { echo "Missing Android 36 build dependency: $binary" >&2; exit 1; }
done
command -v javac >/dev/null
command -v keytool >/dev/null
command -v zip >/dev/null
command -v openssl >/dev/null

bash "$BASE_DIR/release/check-release-metadata.sh"

if [[ -f "$KEYSTORE" && ! -f "$KEYPASS_FILE" || -f "$KEYPASS_FILE" && ! -f "$KEYSTORE" ]]; then
  die "incomplete signing identity; provide both the existing keystore and password file"
fi
if [[ ! -f "$KEYSTORE" && ! -f "$KEYPASS_FILE" ]]; then
  if ((INIT_DEV_SIGNING)); then
    mkdir -p "$KEY_DIR"
    chmod 0700 "$KEY_DIR"
    INIT_TMP_DIR="$(mktemp -d "$KEY_DIR/.nullgate-signing.XXXXXX")"
    init_keystore="$INIT_TMP_DIR/nullgate-local.keystore"
    init_keypass="$INIT_TMP_DIR/.nullgate-local.pass"
    (umask 077 && openssl rand -hex 32 > "$init_keypass")
    (umask 077; keytool -genkeypair -keystore "$init_keystore" -storepass:file "$init_keypass" \
      -keypass:file "$init_keypass" -alias nullgate-local \
      -dname "CN=NullGate Local,O=Null Protocol,C=US" \
      -keyalg RSA -keysize 2048 -validity 3650 >/dev/null 2>&1)
    keytool -list -keystore "$init_keystore" -storepass:file "$init_keypass" \
      -alias nullgate-local >/dev/null 2>&1 \
      || die "new development signing identity failed validation"
    chmod 0600 "$init_keystore" "$init_keypass"
    [[ ! -e "$KEYSTORE" && ! -e "$KEYPASS_FILE" ]] \
      || die "signing identity appeared during initialization; refusing replacement"
    mv -- "$init_keystore" "$KEYSTORE"
    mv -- "$init_keypass" "$KEYPASS_FILE"
    rmdir -- "$INIT_TMP_DIR"
    INIT_TMP_DIR=""
  else
    die "signing identity is unavailable; provide an existing pair or explicitly use --init-dev-signing"
  fi
fi
[[ ! -L "$KEYSTORE" ]] || die "signing keystore must not be a symbolic link"
[[ ! -L "$KEYPASS_FILE" ]] || die "signing password file must not be a symbolic link"
[[ -s "$KEYSTORE" && -s "$KEYPASS_FILE" ]] || die "signing identity files must be non-empty"
[[ "$(stat -c %a "$KEYPASS_FILE")" == 600 ]] \
  || die "signing password file must have mode 0600"
keytool -list -keystore "$KEYSTORE" -storepass:file "$KEYPASS_FILE" \
  -alias nullgate-local >/dev/null 2>&1 \
  || die "signing password does not unlock the expected nullgate-local alias"

if ((PREFLIGHT_ONLY)); then
  echo "NullGate build preflight passed; no files were changed."
  exit 0
fi

bash "$BASE_DIR/client/test.sh"
bash "$BASE_DIR/test-client/test.sh"
bash "$BASE_DIR/theme-client/test.sh"
bash "$BASE_DIR/broker/test.sh"

rm -rf "$OUT_DIR" "$DIST_DIR"
mkdir -p "$OUT_DIR/compiled-res" "$OUT_DIR/classes" "$OUT_DIR/dex" "$OUT_DIR/broker-classes" \
  "$OUT_DIR/broker-dex" "$OUT_DIR/client-reference" "$OUT_DIR/test-client-classes" \
  "$OUT_DIR/test-client-dex" "$OUT_DIR/theme-client-classes" \
  "$OUT_DIR/theme-client-dex" "$DIST_DIR" "$KEY_DIR"

"$BUILD_TOOLS/aapt2" compile --dir "$BASE_DIR/res" -o "$OUT_DIR/compiled-res"
"$BUILD_TOOLS/aapt2" link --manifest "$BASE_DIR/AndroidManifest.xml" \
  -I "$ANDROID_JAR" --min-sdk-version 26 --target-sdk-version 36 \
  -o "$OUT_DIR/base.apk" "$OUT_DIR/compiled-res"/*.flat

mapfile -d '' -t controller_sources < <(find "$BASE_DIR/common/src" "$BASE_DIR/src" \
  -name '*.java' -print0 | sort -z)
javac --release 8 -classpath "$ANDROID_JAR" -d "$OUT_DIR/classes" "${controller_sources[@]}"
mapfile -d '' -t controller_classes < <(find "$OUT_DIR/classes" -name '*.class' -print0 | sort -z)
"$BUILD_TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$OUT_DIR/dex" \
  "${controller_classes[@]}"
normalize_archive_inputs "$OUT_DIR/dex"
(cd "$OUT_DIR/dex" && zip -X -q -j "$OUT_DIR/base.apk" classes.dex)

# Compile the standalone client reference against the same Android API surface.
mapfile -d '' -t reference_sources < <(find "$BASE_DIR/client/reference" \
  -name '*.java' -print0 | sort -z)
javac --release 8 -classpath "$ANDROID_JAR" -d "$OUT_DIR/client-reference" \
  "${reference_sources[@]}"

# Build the first-party external-app harness as a separate package and UID.
"$BUILD_TOOLS/aapt2" link --manifest "$BASE_DIR/test-client/AndroidManifest.xml" \
  -I "$ANDROID_JAR" --min-sdk-version 26 --target-sdk-version 36 \
  -o "$OUT_DIR/test-client-base.apk"
javac --release 8 -classpath "$ANDROID_JAR" -d "$OUT_DIR/test-client-classes" \
  "$BASE_DIR/common/src/org/nullprotocol/nullgate/protocol/ExternalClientContract.java" \
  "$BASE_DIR/common/src/org/nullprotocol/nullgate/protocol/ExternalResultPolicy.java" \
  "$BASE_DIR/test-client/src/org/nullprotocol/nullgate/testclient/MainActivity.java" \
  "$BASE_DIR/test-client/src/org/nullprotocol/nullgate/testclient/TestClientStatePolicy.java"
mapfile -d '' -t test_client_classes < <(find "$OUT_DIR/test-client-classes" \
  -name '*.class' -print0 | sort -z)
"$BUILD_TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$OUT_DIR/test-client-dex" \
  "${test_client_classes[@]}"
normalize_archive_inputs "$OUT_DIR/test-client-dex"
(cd "$OUT_DIR/test-client-dex" && zip -X -q -j "$OUT_DIR/test-client-base.apk" classes.dex)

# Build the paired first-party theme client as a distinct package and UID.
"$BUILD_TOOLS/aapt2" link --manifest "$BASE_DIR/theme-client/AndroidManifest.xml" \
  -I "$ANDROID_JAR" --min-sdk-version 26 --target-sdk-version 36 \
  -o "$OUT_DIR/theme-client-base.apk"
javac --release 8 -classpath "$ANDROID_JAR" -d "$OUT_DIR/theme-client-classes" \
  "$BASE_DIR/common/src/org/nullprotocol/nullgate/protocol/ExternalClientContract.java" \
  "$BASE_DIR/common/src/org/nullprotocol/nullgate/protocol/ExternalResultPolicy.java" \
  "$BASE_DIR/theme-client/src/org/nullprotocol/nullgate/themeclient/MainActivity.java" \
  "$BASE_DIR/theme-client/src/org/nullprotocol/nullgate/themeclient/ThemeClientStatePolicy.java"
mapfile -d '' -t theme_client_classes < <(find "$OUT_DIR/theme-client-classes" \
  -name '*.class' -print0 | sort -z)
"$BUILD_TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$OUT_DIR/theme-client-dex" \
  "${theme_client_classes[@]}"
normalize_archive_inputs "$OUT_DIR/theme-client-dex"
(cd "$OUT_DIR/theme-client-dex" && zip -X -q -j "$OUT_DIR/theme-client-base.apk" classes.dex)

"$BUILD_TOOLS/zipalign" -f 4 "$OUT_DIR/base.apk" "$OUT_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" sign --v1-signing-enabled false \
  --ks "$KEYSTORE" --ks-key-alias nullgate-local \
  --ks-pass "file:$KEYPASS_FILE" \
  --out "$DIST_DIR/NullGate-prototype-debug.apk" "$OUT_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$DIST_DIR/NullGate-prototype-debug.apk"

"$BUILD_TOOLS/zipalign" -f 4 "$OUT_DIR/test-client-base.apk" \
  "$OUT_DIR/test-client-aligned.apk"
"$BUILD_TOOLS/apksigner" sign --v1-signing-enabled false \
  --ks "$KEYSTORE" --ks-key-alias nullgate-local \
  --ks-pass "file:$KEYPASS_FILE" \
  --out "$DIST_DIR/NullGate-test-client-debug.apk" "$OUT_DIR/test-client-aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$DIST_DIR/NullGate-test-client-debug.apk"

"$BUILD_TOOLS/zipalign" -f 4 "$OUT_DIR/theme-client-base.apk" \
  "$OUT_DIR/theme-client-aligned.apk"
"$BUILD_TOOLS/apksigner" sign --v1-signing-enabled false \
  --ks "$KEYSTORE" --ks-key-alias nullgate-local \
  --ks-pass "file:$KEYPASS_FILE" \
  --out "$DIST_DIR/NullGate-theme-client-debug.apk" "$OUT_DIR/theme-client-aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$DIST_DIR/NullGate-theme-client-debug.apk"

verify_manifest_identity() {
  local apk="$1" expected_package="$2" expected_version="$3" badging
  badging="$("$BUILD_TOOLS/aapt2" dump badging "$apk" | sed -n '1p')"
  [[ "$badging" == *"name='$expected_package'"* && "$badging" == *"versionCode='$expected_version'"* ]] || { echo "Unexpected packaged identity: $apk" >&2; exit 1; }
  "$BUILD_TOOLS/aapt2" dump permissions "$apk" | grep -Fqx "uses-permission: name='android.permission.HIDE_OVERLAY_WINDOWS'" || { echo "Overlay protection permission missing: $apk" >&2; exit 1; }
}
verify_manifest_identity "$DIST_DIR/NullGate-prototype-debug.apk" org.nullprotocol.nullgate 3
verify_manifest_identity "$DIST_DIR/NullGate-test-client-debug.apk" org.nullprotocol.nullgate.testclient 1
verify_manifest_identity "$DIST_DIR/NullGate-theme-client-debug.apk" org.nullprotocol.nullgate.themeclient 1
bash "$BASE_DIR/release/check-release-metadata.sh" --with-apk

mapfile -d '' -t broker_sources < <(find "$BASE_DIR/common/src" "$BASE_DIR/broker/src" \
  "$BASE_DIR/broker/android" -name '*.java' -print0 | sort -z)
javac --release 8 -classpath "$ANDROID_JAR" -d "$OUT_DIR/broker-classes" \
  "${broker_sources[@]}"
mapfile -d '' -t broker_classes < <(find "$OUT_DIR/broker-classes" \
  -name '*.class' -print0 | sort -z)
"$BUILD_TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$OUT_DIR/broker-dex" \
  "${broker_classes[@]}"
normalize_archive_inputs "$OUT_DIR/broker-dex"
(cd "$OUT_DIR/broker-dex" && zip -X -q -j "$DIST_DIR/NullGate-broker.jar" classes.dex)
"$BUILD_TOOLS/apksigner" verify --print-certs "$DIST_DIR/NullGate-prototype-debug.apk" \
  | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' > "$DIST_DIR/controller-cert-sha256.txt"
(cd "$DIST_DIR" && sha256sum NullGate-prototype-debug.apk NullGate-test-client-debug.apk \
  NullGate-theme-client-debug.apk NullGate-broker.jar \
  controller-cert-sha256.txt > SHA256SUMS)
echo "Built: $DIST_DIR/NullGate-prototype-debug.apk"
echo "Built: $DIST_DIR/NullGate-test-client-debug.apk"
echo "Built: $DIST_DIR/NullGate-theme-client-debug.apk"
echo "Built: $DIST_DIR/NullGate-broker.jar"
if ((RUN_DEVICE_HARNESS)); then
  bash "$BASE_DIR/device/test.sh"
else
  echo "Host-only verification complete; simulated device-helper suite skipped."
fi
