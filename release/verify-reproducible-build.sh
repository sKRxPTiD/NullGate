#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
BUILD="$BASE_DIR/release/build-pixi-private.sh"
MANIFEST="$BASE_DIR/dist/SHA256SUMS"

"$BUILD" --host-only
[[ -s "$MANIFEST" ]] || {
  echo "NullGate reproducibility check: first checksum manifest is missing." >&2
  exit 1
}
first_manifest="$(<"$MANIFEST")"

"$BUILD" --host-only
[[ -s "$MANIFEST" ]] || {
  echo "NullGate reproducibility check: second checksum manifest is missing." >&2
  exit 1
}
second_manifest="$(<"$MANIFEST")"

if [[ "$first_manifest" != "$second_manifest" ]]; then
  echo "NullGate reproducibility check: repeated artifact hashes differ." >&2
  diff -u <(printf '%s\n' "$first_manifest") \
    <(printf '%s\n' "$second_manifest") >&2 || true
  exit 1
fi

printf '%s\n' "$second_manifest"
echo "NullGate reproducibility check passed: two clean signed builds are byte-identical."
