#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# Load functions without dispatching a command. Every device-facing entry point
# below is replaced before any launcher function is exercised.
source <(sed '/^case "${1:-menu}" in/,$d' "$BASE_DIR/device/nullgate-pixi")
ADB_BIN=SIMULATED_ADB_ONLY
SERIAL=PIXI_TEST_SERIAL
fail() { printf '%b\n' "$1" >&2; return 1; }
info() { printf 'SUCCESS: %b\n' "$1"; }
require_connection() { return 0; }
activate_root() { return 0; }
runtime_state() { printf 'ABSENT\n'; }
helper_read() {
  [[ "$*" == verify-clean ]] || return 64
  [[ "$scenario" != broker-present ]] || { printf 'broker still running\n'; return 1; }
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
printf 'NullGate launcher shutdown regressions: 6 passed (simulated ADB only)\n'
