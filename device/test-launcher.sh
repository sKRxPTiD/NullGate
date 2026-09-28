#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# Load functions without dispatching a command. Every device-facing entry point
# below is replaced before any launcher function is exercised.
source <(sed '/^case "${1:-menu}" in/,$d' "$BASE_DIR/device/nullgate-pixi")
ADB_BIN=SIMULATED_ADB_ONLY
SERIAL=PIXI_TEST_SERIAL
test_log="$(mktemp)"
test_home=""
trap 'rm -f -- "$test_log"; if [[ -n "$test_home" ]]; then rm -rf -- "$test_home"; fi' EXIT
fail() { printf '%b\n' "$1" >&2; return 1; }
info() { printf 'SUCCESS: %b\n' "$1"; }
require_connection() { return 0; }
activate_root() { return 0; }
runtime_state() { printf 'ABSENT\n'; }
helper_read() {
  case "$*" in
    preflight) return 0 ;;
    verify-clean)
      [[ "$scenario" != broker-present && "$scenario" != recovery-verification-failed ]] || { printf 'broker still running\n'; return 1; } ;;
    *) return 64 ;;
  esac
}
helper_theme() {
  [[ "$*" == deploy-system-theme ]] || return 64
  printf 'deploy-system-theme\n' >> "$test_log"
}
adb_pixi() {
  case "$*" in
    unroot) [[ "$scenario" != unroot-fails ]] ;;
    'shell id')
      case "$scenario" in
        still-root) printf 'uid=0(root)\n' ;;
        identity-fails) return 1 ;;
        *) printf 'uid=2000(shell)\n' ;;
      esac ;;
    'shell am start -W -n org.nullprotocol.nullgate/.MainActivity')
      printf 'controller\n' >> "$test_log" ;;
    'shell am start -W -n org.nullprotocol.nullgate.themeclient/.MainActivity')
      printf 'theme-client\n' >> "$test_log" ;;
    'shell am start -W -n com.drdisagree.colorblendr/.ui.activities.MainActivity')
      printf 'colorblendr\n' >> "$test_log" ;;
    *) printf 'unexpected simulated ADB call: %s\n' "$*" >&2; return 64 ;;
  esac
}
timeout() {
  [[ "$*" == '15s SIMULATED_ADB_ONLY -s PIXI_TEST_SERIAL wait-for-device' ]] || return 64
  [[ "$scenario" != reconnect-times-out ]] || return 124
}

for scenario in unroot-fails reconnect-times-out still-root identity-fails broker-present; do
  if output="$(stop_session 2>&1)"; then
    printf 'Launcher falsely accepted %s: %s\n' "$scenario" "$output" >&2
    exit 1
  fi
  [[ "$output" != *SUCCESS:* ]] || exit 1
done
scenario=ready
output="$(stop_session 2>&1)"
[[ "$output" == *'SUCCESS: NullGate was already clean.'* ]] || exit 1

scenario=start-theme
: > "$test_log"
output="$(start_theme_client_session 2>&1)"
[[ "$output" == *'SUCCESS: NullGate is ready for NullGate Theme Client.'* ]] || exit 1
[[ "$(sed -n '1p' "$test_log")" == deploy-system-theme ]] || exit 1
[[ "$(sed -n '2p' "$test_log")" == controller ]] || exit 1
[[ "$(sed -n '3p' "$test_log")" == theme-client ]] || exit 1
[[ "$(wc -l < "$test_log" | tr -d '[:space:]')" == 3 ]] || exit 1

scenario=start-colorblendr
: > "$test_log"
output="$(start_colorblendr_session 2>&1)"
[[ "$output" == *'SUCCESS: NullGate is ready for ColorBlendr.'* ]] || exit 1
[[ "$(sed -n '1p' "$test_log")" == deploy-system-theme ]] || exit 1
[[ "$(sed -n '2p' "$test_log")" == controller ]] || exit 1
[[ "$(sed -n '3p' "$test_log")" == colorblendr ]] || exit 1
[[ "$(wc -l < "$test_log" | tr -d '[:space:]')" == 3 ]] || exit 1

NULLGATE_NO_DIALOG=1
helper_theme() {
  [[ "$scenario" == recovery-verification-failed ]] && return 0
  printf 'recovery refused\n'; return 1
}
adb_pixi() {
  case "$*" in
    'shell if test -L /data/local/tmp/nullgate/theme.snapshot; then echo SYMLINK; elif test -f /data/local/tmp/nullgate/theme.snapshot; then echo FILE; elif test -e /data/local/tmp/nullgate/theme.snapshot; then echo OTHER; else echo ABSENT; fi')
      if [[ "$scenario" == recovery-unsafe ]]; then echo SYMLINK; else echo FILE; fi ;;
    unroot) printf 'unroot\n' >> "$test_log" ;;
    'shell id') printf 'uid=2000(shell)\n' ;;
    *) return 64 ;;
  esac
}
for scenario in recovery-refused recovery-unsafe recovery-verification-failed; do
  : > "$test_log"
  if output="$(recover_runtime 2>&1)"; then
    printf 'Launcher falsely accepted %s\n' "$scenario" >&2; exit 1
  fi
  [[ "$output" != *SUCCESS:* ]]
  [[ "$(cat "$test_log")" == unroot ]]
done

test_home="$(mktemp -d)"
android_sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
HOME="$test_home" \
ANDROID_HOME="$android_sdk" \
APKSIGNER_BIN="${APKSIGNER_BIN:-$android_sdk/build-tools/36.0.0/apksigner}" \
AAPT2_BIN="${AAPT2_BIN:-$android_sdk/build-tools/36.0.0/aapt2}" \
NULLGATE_LAUNCHER_TARGET="$test_home/bin/nullgate-pixi" \
NULLGATE_DESKTOP_DIR="$test_home/app-folder" \
NULLGATE_DESKTOP_SHORTCUT_TARGET="$test_home/Desktop/NullGate PiXi.desktop" \
  bash "$BASE_DIR/device/install-dolowolf-launcher.sh" >/dev/null
[[ -x "$test_home/bin/nullgate-pixi" \
  && -x "$test_home/app-folder/NullGate PiXi.desktop" \
  && -x "$test_home/Desktop/NullGate PiXi.desktop" ]] || {
  echo "Launcher installer did not create executable app entries." >&2
  exit 1
}
cmp -s "$test_home/app-folder/NullGate PiXi.desktop" \
  "$test_home/Desktop/NullGate PiXi.desktop"
desktop-file-validate "$test_home/app-folder/NullGate PiXi.desktop" \
  "$test_home/Desktop/NullGate PiXi.desktop"

printf 'NullGate launcher regressions: 12 passed (simulated ADB and isolated installer)\n'
