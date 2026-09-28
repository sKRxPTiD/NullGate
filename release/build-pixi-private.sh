#!/usr/bin/env bash
set -euo pipefail

PROJECT="/mnt/TerraDrive/Null Protocol/Apps/NullGate/source"
PAIRED_KEYS="${NULLGATE_KEY_DIR:-/home/wolf/.local/share/nullgate-secrets/pixi-paired}"
KEYPASS="${NULLGATE_KEYPASS_FILE:-/home/wolf/.local/share/nullgate-secrets/pixi-paired.pass}"
PINS="$PROJECT/compat/pixi-private-pins.properties"

[[ -d "$PROJECT" ]] || {
  echo "NullGate canonical source is unavailable." >&2
  exit 1
}
[[ "$PAIRED_KEYS" == /* && "$KEYPASS" == /* ]] || {
  echo "Set NULLGATE_KEY_DIR and NULLGATE_KEYPASS_FILE to explicit absolute paths." >&2
  exit 1
}
KEYSTORE="$PAIRED_KEYS/nullgate-local.keystore"
[[ -s "$KEYSTORE" && ! -L "$KEYSTORE" ]] || {
  echo "NullGate paired keystore is unavailable or unsafe." >&2
  exit 1
}
[[ -s "$KEYPASS" && ! -L "$KEYPASS" ]] || {
  echo "NullGate protected signing credential is unavailable." >&2
  exit 1
}
[[ "$(stat -c %a "$KEYPASS")" == 600 ]] || {
  echo "NullGate signing credential must remain mode 0600." >&2
  exit 1
}
command -v keytool >/dev/null || {
  echo "NullGate requires keytool to verify the paired signer." >&2
  exit 1
}

expected_signer="$(sed -n 's/^controller\.signer\.sha256=//p' "$PINS")"
actual_signer="$(LC_ALL=C keytool -list -v -keystore "$KEYSTORE" \
  -storepass:file "$KEYPASS" -alias nullgate-local 2>/dev/null \
  | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f')"
[[ "$expected_signer" =~ ^[0-9a-f]{64}$ && "$actual_signer" == "$expected_signer" ]] || {
  echo "NullGate paired signing identity does not match the reviewed PiXi signer pin." >&2
  exit 1
}

NULLGATE_KEY_DIR="$PAIRED_KEYS" NULLGATE_KEYPASS_FILE="$KEYPASS" \
  exec "$PROJECT/build.sh" "$@"
