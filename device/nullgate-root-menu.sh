#!/usr/bin/env bash
set -euo pipefail

PROJECT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
HELPER="$PROJECT/device/nullgate-root-session.sh"
SERIAL="${NULLGATE_SERIAL:-54110DLAQ0043W}"
ADB_BIN="${ADB_BIN:-adb}"
CONTROLLER="${NULLGATE_ROOT_PACKAGE:-org.nullprotocol.nullgate}"
if [[ "$CONTROLLER" == org.nullprotocol.nullgate ]]; then
  HELPER="$PROJECT/device/nullgate-root-session.sh"
elif [[ "$CONTROLLER" == org.nullprotocol.nullgate.rootcandidate ]]; then
  HELPER="$PROJECT/device/nullgate-root-candidate.sh"
else
  echo "Unsupported NullGate controller package." >&2
  exit 2
fi
[[ "$SERIAL" =~ ^[A-Za-z0-9._:-]+$ ]] || exit 2

open_controller() {
  "$ADB_BIN" -s "$SERIAL" shell am start -W -n \
    "$CONTROLLER/org.nullprotocol.nullgate.RootControlActivity"
}

case "${1:-menu}" in
  --help|-h)
    printf '%s\n' 'NullGate root switch: menu | start | open | status | off | stop | guide'
    exit ;;
  start|status|off|stop) exec bash "$HELPER" "$1" ;;
  open) open_controller; exit ;;
  guide) exec less "$PROJECT/root-client/USAGE.md" ;;
  menu) ;;
  *) echo 'Unknown root-switch action.' >&2; exit 2 ;;
esac

while true; do
  printf '\n%s\n' 'NullGate · PiXi — changes stay after root is OFF' \
    '1. Start broker after reboot (requires Rooted debugging)' \
    '2. Open NullGate on PiXi (broker must already be started)' \
    '3. Show root-switch status' '4. Switch app root OFF' \
    '5. Stop broker completely' '6. Read simple directions' '0. Exit'
  read -r -p 'Choose: ' action || exit 0
  case "$action" in
    1) if ! bash "$HELPER" start; then echo 'Start failed; existing runtime was preserved. Inspect status.'; fi ;;
    2) if ! open_controller; then echo 'Controller could not be opened.'; fi ;;
    3) if ! bash "$HELPER" status; then echo 'Status is unavailable, not confirmed OFF.'; fi ;;
    4) if ! bash "$HELPER" off; then echo 'OFF was not confirmed. Preserve evidence and retry.'; fi ;;
    5) if bash "$HELPER" stop; then echo 'Broker stopped. Turn Rooted debugging off on PiXi.';
       else echo 'Shutdown was not confirmed. Preserve evidence.'; fi ;;
    6) less "$PROJECT/root-client/USAGE.md" ;;
    0) exit 0 ;;
    *) echo 'Choose one of the listed actions.' ;;
  esac
done
