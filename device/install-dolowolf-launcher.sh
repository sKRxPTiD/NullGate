#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_DIR="$(CDPATH= cd -- "$SOURCE_DIR/.." && pwd)"
HOME_DIR="${HOME:?HOME must be set to install the DoloWOLF launcher}"
LAUNCHER_TARGET="${NULLGATE_LAUNCHER_TARGET:-$HOME_DIR/.local/bin/nullgate-pixi}"
DESKTOP_DIR="${NULLGATE_DESKTOP_DIR:-$HOME_DIR/Desktop/All Appz}"
DESKTOP_TARGET="$DESKTOP_DIR/NullGate PiXi.desktop"
ICON_TARGET="$HOME_DIR/.local/share/icons/hicolor/256x256/apps/nullgate.png"
DESKTOP_SOURCE="$SOURCE_DIR/NullGate PiXi.desktop"
ICON_SOURCE="$PROJECT_DIR/res/drawable-nodpi/nullgate_launcher_icon.png"
LAUNCHER_SOURCE="$SOURCE_DIR/nullgate-pixi"
HELPER_SOURCE="$SOURCE_DIR/nullgate-device-v2.sh"
ADB_BIN="${ADB_BIN:-adb}"
ANDROID_SDK="${ANDROID_HOME:-$HOME_DIR/Android/Sdk}"
BUILD_TOOLS="$ANDROID_SDK/build-tools/36.0.0"
TEMP_DIR=""

usage() {
  printf 'Usage: %s [--check]\n\n' "$0"
  printf '  --check   Validate prerequisites and generated desktop entry without installing.\n'
}

case "${1:-}" in
  "") ;;
  --check) ;;
  --help|-h) usage; exit 0 ;;
  *) usage >&2; exit 2 ;;
esac

die() { printf 'NullGate launcher setup: %s\n' "$*" >&2; exit 1; }

[[ -f "$LAUNCHER_SOURCE" && -f "$HELPER_SOURCE" && -f "$DESKTOP_SOURCE" && -f "$ICON_SOURCE" ]] \
  || die "launcher, device helper, desktop entry, or icon is missing from the active source tree"
command -v desktop-file-validate >/dev/null 2>&1 \
  || die "desktop-file-validate is unavailable; install the desktop-file-utils package"
command -v "$ADB_BIN" >/dev/null 2>&1 || [[ -x "${ADB_BIN:-}" ]] \
  || die "ADB is unavailable; install Android platform-tools or set ADB_BIN"
APKSIGNER_BIN="${APKSIGNER_BIN:-$BUILD_TOOLS/apksigner}"
AAPT2_BIN="${AAPT2_BIN:-$BUILD_TOOLS/aapt2}"
[[ -x "$APKSIGNER_BIN" ]] || die "apksigner is unavailable at $APKSIGNER_BIN"
[[ -x "$AAPT2_BIN" ]] || die "aapt2 is unavailable at $AAPT2_BIN"
bash -n "$LAUNCHER_SOURCE" || die "launcher shell syntax validation failed"
bash -n "$HELPER_SOURCE" || die "device-helper shell syntax validation failed"

TEMP_DIR="$(mktemp -d)"
GENERATED_DESKTOP="$TEMP_DIR/NullGate PiXi.desktop"
trap 'if [[ -n "$GENERATED_DESKTOP" ]]; then rm -f -- "$GENERATED_DESKTOP"; fi; if [[ -n "$TEMP_DIR" && -d "$TEMP_DIR" ]]; then rmdir -- "$TEMP_DIR" 2>/dev/null || true; fi' EXIT
DESKTOP_EXEC="$(printf '%s' "$LAUNCHER_TARGET" | sed 's/[\\"]/\\&/g; s/[&|]/\\&/g')"
sed "s|@NULLGATE_LAUNCHER@|\"$DESKTOP_EXEC\"|g" \
  "$DESKTOP_SOURCE" > "$GENERATED_DESKTOP"
desktop-file-validate "$GENERATED_DESKTOP" \
  || die "generated desktop entry is invalid"

if [[ "${1:-}" == --check ]]; then
  printf 'NullGate launcher setup check passed.\n'
  printf 'Launcher: %s\nDesktop entry: %s\nIcon: %s\n' \
    "$LAUNCHER_TARGET" "$DESKTOP_TARGET" "$ICON_TARGET"
  exit 0
fi

install -Dm755 "$LAUNCHER_SOURCE" "$LAUNCHER_TARGET"
install -Dm644 "$GENERATED_DESKTOP" "$DESKTOP_TARGET"
install -Dm644 "$ICON_SOURCE" "$ICON_TARGET"
desktop-file-validate "$DESKTOP_TARGET"
bash -n "$LAUNCHER_TARGET"

printf 'NullGate DoloWOLF launcher installed and validated.\n'
printf 'Launcher: %s\nDesktop entry: %s\nIcon: %s\n' \
  "$LAUNCHER_TARGET" "$DESKTOP_TARGET" "$ICON_TARGET"
