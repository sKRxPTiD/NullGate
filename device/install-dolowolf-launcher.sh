#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
LAUNCHER_TARGET="/home/wolf/.local/bin/nullgate-pixi"
DESKTOP_TARGET="/home/wolf/Desktop/All Appz/NullGate PiXi.desktop"

install -Dm755 "$SOURCE_DIR/nullgate-pixi" "$LAUNCHER_TARGET"
install -Dm644 "$SOURCE_DIR/NullGate PiXi.desktop" "$DESKTOP_TARGET"
desktop-file-validate "$DESKTOP_TARGET"
bash -n "$LAUNCHER_TARGET"

printf 'NullGate launcher installed and validated.\n'
