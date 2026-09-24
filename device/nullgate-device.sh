#!/usr/bin/env bash
set -euo pipefail

# Canonical entry point for the audited implementation.
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec "$SCRIPT_DIR/nullgate-device-v2.sh" "$@"
