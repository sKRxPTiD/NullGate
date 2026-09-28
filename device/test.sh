#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
bash "$BASE_DIR/device/test-launcher.sh"
exec "$BASE_DIR/device/test-v2.sh" "$@"
