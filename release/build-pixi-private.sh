#!/usr/bin/env bash
set -euo pipefail

PROJECT="/mnt/TerraDrive/Null Protocol/Apps/NullGate/source"
PAIRED_KEYS="/mnt/TerraDrive/Null Protocol/Apps/NullGate/nullgate-prototype/keys"
KEYPASS="/home/wolf/.local/share/nullgate-secrets/pixi-paired.pass"

[[ -d "$PROJECT" && -s "$PAIRED_KEYS/nullgate-local.keystore" ]] || {
  echo "NullGate paired source or keystore is unavailable." >&2
  exit 1
}
[[ -f "$KEYPASS" && ! -L "$KEYPASS" ]] || {
  echo "NullGate protected signing credential is unavailable." >&2
  exit 1
}
[[ "$(stat -c %a "$KEYPASS")" == 600 ]] || {
  echo "NullGate signing credential must remain mode 0600." >&2
  exit 1
}

NULLGATE_KEY_DIR="$PAIRED_KEYS" NULLGATE_KEYPASS_FILE="$KEYPASS" \
  exec "$PROJECT/build.sh"
