#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# Load functions without dispatching a command. Every device-facing entry point
# below is replaced before any launcher function is exercised.
source <(sed '/^case "${1:-menu}" in/,$d' "$BASE_DIR/device/nullgate-pixi")
ADB_BIN=SIMULATED_ADB_ONLY
SERIAL=PIXI_TEST_SERIAL
test_log="$(mktemp)"
trap 'rm -f -- "$test_log"' EXIT
fail() { printf '%b\n' "$1" >&2; return 1; }
info() { printf 'SUCCESS: %b\n' "$1"; }
require_connection() { return 0; }
activate_root() { return 0; }
runtime_state() { printf 'ABSENT\n'; }
helper_read() {
  case "$*" in
    preflight) return 0 ;;
    verify-clean)
      [[ "$scenario" != broker-present ]] || { printf 'broker still running\n'; return 1; } ;;
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

printf 'NullGate launcher regressions: 8 passed (simulated ADB only)\n'
