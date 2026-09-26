#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
MANIFEST="$BASE_DIR/AndroidManifest.xml"
PINS="$BASE_DIR/compat/pixi-private-pins.properties"
HELPER="$BASE_DIR/device/nullgate-device-v2.sh"
BUILD="$BASE_DIR/build.sh"

die() { echo "NullGate release metadata: $*" >&2; exit 1; }
one_value() {
  local value="$1" label="$2"
  [[ -n "$value" && "$(printf '%s\n' "$value" | wc -l)" == 1 ]] \
    || die "$label is missing or ambiguous"
  printf '%s\n' "$value"
}

manifest_code="$(one_value "$(sed -n 's/.*android:versionCode="\([0-9][0-9]*\)".*/\1/p' "$MANIFEST")" "manifest version code")"
manifest_name="$(one_value "$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$MANIFEST")" "manifest version name")"
manifest_label="$(one_value "$(sed -n 's/.*android:label="\([^"]*\)".*/\1/p' "$MANIFEST")" "manifest application label")"
build_code="$(one_value "$(sed -n 's/^verify_manifest_identity .* org\.nullprotocol\.nullgate \([0-9][0-9]*\)$/\1/p' "$BUILD")" "build version pin")"
helper_code="$(one_value "$(sed -n 's/^CONTROLLER_VERSION_CODE="\([0-9][0-9]*\)"$/\1/p' "$HELPER")" "device-helper version pin")"
private_code="$(one_value "$(sed -n 's/^controller\.versionCode=\([0-9][0-9]*\)$/\1/p' "$PINS")" "private launcher version pin")"

[[ "$manifest_code" == "$build_code" && "$manifest_code" == "$helper_code" \
  && "$manifest_code" == "$private_code" ]] \
  || die "controller version mismatch: manifest=$manifest_code build=$build_code helper=$helper_code private=$private_code"
[[ "$manifest_name" == "0.1.0" ]] || die "unexpected release name: $manifest_name"
[[ "$manifest_label" == "NullGate" ]] || die "unexpected application label: $manifest_label"

if [[ "${1:-}" == --with-apk ]]; then
  apk="$BASE_DIR/dist/NullGate-prototype-debug.apk"
  aapt2="${AAPT2_BIN:-${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/36.0.0/aapt2}"
  [[ -s "$apk" && -x "$aapt2" ]] || die "built APK or aapt2 is unavailable"
  badging="$("$aapt2" dump badging "$apk" | sed -n '1p')"
  [[ "$badging" == *"name='org.nullprotocol.nullgate'"* \
    && "$badging" == *"versionCode='$manifest_code'"* \
    && "$badging" == *"versionName='$manifest_name'"* ]] \
    || die "built APK identity does not match the reviewed release metadata"
fi

echo "NullGate release metadata consistent: $manifest_name (code $manifest_code)"
