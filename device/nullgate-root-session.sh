#!/usr/bin/env bash
set -euo pipefail

PROJECT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
DIST="${NULLGATE_ROOT_DIST:-$PROJECT/dist}"
ADB_BIN="${ADB_BIN:-adb}"
SERIAL="${NULLGATE_SERIAL:-54110DLAQ0043W}"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
CONTROLLER=org.nullprotocol.nullgate
RUNTIME=/data/local/tmp/nullgate-root
BROKER_CLASS=org.nullprotocol.nullgate.broker.RootBrokerMain
APK="$DIST/NullGate-prototype-debug.apk"
die() { echo "NullGate root switch: $*" >&2; exit 1; }
adb_pixi() { "$ADB_BIN" -s "$SERIAL" "$@"; }
[[ "$SERIAL" =~ ^[A-Za-z0-9._:-]+$ ]] || die "invalid device serial"
if [[ "${1:-}" == help || "${1:-}" == --help || "${1:-}" == -h ]]; then
  cat <<'EOF'
Usage: bash device/nullgate-root-session.sh {start|status|off|stop|recover}

Optional: NULLGATE_ROOT_DIST=/absolute/path/to/artifacts
Defaults to the canonical source repository's dist/ directory.
EOF
  exit 0
fi
[[ "${1:-}" == start || "${1:-}" == status || "${1:-}" == off || "${1:-}" == stop || "${1:-}" == recover ]] \
  || die "usage: bash device/nullgate-root-session.sh {start|status|off|stop|recover}"
[[ "$(adb_pixi get-state 2>/dev/null)" == device ]] || die "PiXi is unavailable"

activate_root_adb() {
  local output identity context enforcement
  output="$(adb_pixi root 2>&1)" || die "Rooted debugging is unavailable; enable it in PiXi settings. $output"
  timeout 15s "$ADB_BIN" -s "$SERIAL" wait-for-device >/dev/null 2>&1 || die "PiXi did not reconnect after rooted ADB started"
  identity="$(adb_pixi shell id 2>/dev/null | tr -d '\r')"
  context="$(adb_pixi shell id -Z 2>/dev/null | tr -d '\r')"
  enforcement="$(adb_pixi shell getenforce 2>/dev/null | tr -d '\r')"
  [[ "$identity" == uid=0\(* && "$context" == u:r:su:s0 && "$enforcement" == Enforcing ]] \
    || die "rooted ADB verification failed (identity=$identity context=$context SELinux=$enforcement)"
}
return_adb_to_shell() {
  adb_pixi unroot >/dev/null
  timeout 15s "$ADB_BIN" -s "$SERIAL" wait-for-device >/dev/null 2>&1 || die "PiXi did not reconnect after ADB returned to non-root"
  [[ "$(adb_pixi shell id 2>/dev/null | tr -d '\r')" == uid=2000\(* ]] || die "ordinary ADB shell could not be verified"
}
controller_apk_path() {
  local path
  path="$(adb_pixi shell pm path "$CONTROLLER" | tr -d '\r' | sed -n 's/^package://p')"
  [[ "$path" == /data/app/*/base.apk && "$path" != *"'"* && "$path" != *$'\n'* ]] \
    || die "production NullGate APK is not installed as one base APK"
  printf '%s\n' "$path"
}
control() {
  local path operation="$1"
  activate_root_adb
  path="$(controller_apk_path)"
  adb_pixi shell "CLASSPATH=$path exec /system/bin/app_process /system/bin org.nullprotocol.nullgate.RootControlMain $operation"
}
case "$1" in
  status) control STATUS; exit ;;
  off) control OFF; exit ;;
  stop)
    output="$(control SHUTDOWN)" || die "broker did not confirm shutdown; preserve runtime evidence"
    printf '%s\n' "$output"
    processes="$(adb_pixi shell ps -A -o ARGS)" || die "process inventory unavailable after shutdown"
    [[ "$processes" != *"$BROKER_CLASS"* ]] || die "root broker process remains after shutdown"
    [[ "$(adb_pixi shell "if test -e $RUNTIME/root-broker.pid; then echo PRESENT; else echo ABSENT; fi" | tr -d '\r')" == ABSENT ]] \
      || die "root broker left a PID receipt after shutdown"
    return_adb_to_shell
    echo "Broker stopped; ADB is non-root; applied changes remain. Turn Rooted debugging off in PiXi settings."
    exit ;;
esac

activate_root_adb
[[ "$(adb_pixi shell getprop ro.product.device | tr -d '\r')" == tokay ]] || die "device is not PiXi/tokay"
[[ "$(adb_pixi shell getenforce | tr -d '\r')" == Enforcing ]] || die "SELinux is not Enforcing"
if [[ "$1" == recover ]]; then
  processes="$(adb_pixi shell ps -A -o ARGS)" || die "process inventory unavailable"
  [[ -n "$processes" && "$processes" != *"$BROKER_CLASS"* ]] || die "root broker is still present"
  [[ "$(adb_pixi shell "if test -L $RUNTIME; then echo UNSAFE; else stat -c '%u:%a' $RUNTIME; fi" | tr -d '\r')" == 0:700 ]] \
    || die "unsafe root runtime"
  [[ "$(adb_pixi shell "if test -L $RUNTIME/root-broker.pid; then echo UNSAFE; else stat -c '%u:%a' $RUNTIME/root-broker.pid; fi" | tr -d '\r')" == 0:600 ]] \
    || die "unsafe stale PID receipt"
  pid="$(adb_pixi shell cat "$RUNTIME/root-broker.pid" | tr -d '\r\n')"
  [[ "$pid" =~ ^[1-9][0-9]{1,8}$ ]] || die "invalid stale PID"
  stamp="$(date +%s)"
  adb_pixi shell "test ! -d /proc/$pid && test \"\$(cat $RUNTIME/root-broker.pid)\" = '$pid' && mv $RUNTIME/root-broker.pid $RUNTIME/root-broker.pid.stale.$pid.$stamp" \
    || die "stale process receipt changed or its PID still exists"
  echo "Archived the confirmed stale PID receipt."
  exit
fi

(cd "$DIST" && sha256sum --status -c SHA256SUMS) || die "release candidate checksum failed"
expected="$(sed -n 's/^controller\.signer\.sha256=//p' "$PROJECT/compat/pixi-private-pins.properties")"
actual="$("$SDK/build-tools/36.0.0/apksigner" verify --print-certs "$APK" \
  | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
[[ "$expected" =~ ^[0-9a-f]{64}$ && "$actual" == "$expected" ]] || die "controller signer does not match the existing paired identity"
badging="$("$SDK/build-tools/36.0.0/aapt2" dump badging "$APK" | sed -n '1p')"
[[ "$badging" == *"name='$CONTROLLER'"* && "$badging" == *"versionCode='4'"* ]] || die "unexpected production APK identity"
processes="$(adb_pixi shell ps -A -o ARGS)" || die "process inventory unavailable"
[[ "$processes" != *"org.nullprotocol.nullgate.broker.NullGateBrokerMain"* ]] || die "legacy broker is still running"
[[ "$processes" != *"$BROKER_CLASS"* ]] || die "root broker already exists; use status"
runtime="$(adb_pixi shell "if test -L $RUNTIME; then echo UNSAFE; elif test -d $RUNTIME; then stat -c '%u:%a' $RUNTIME; elif test -e $RUNTIME; then echo UNSAFE; else echo ABSENT; fi" | tr -d '\r')"
[[ "$runtime" == ABSENT || "$runtime" == 0:700 ]] || die "unsafe root runtime"
if [[ "$runtime" == ABSENT ]]; then adb_pixi shell "umask 077; mkdir $RUNTIME" >/dev/null; fi
[[ "$(adb_pixi shell "if test -e $RUNTIME/root-broker.pid || test -L $RUNTIME/root-broker.pid; then echo PRESENT; else echo ABSENT; fi" | tr -d '\r')" == ABSENT ]] \
  || die "a stale PID receipt needs guarded recovery"
adb_pixi install -r "$APK" >/dev/null || die "production NullGate upgrade failed"
adb_pixi push "$DIST/NullGate-broker.jar" "$RUNTIME/NullGate-broker.jar" >/dev/null
adb_pixi shell "umask 077; CLASSPATH=$RUNTIME/NullGate-broker.jar nohup /system/bin/app_process /system/bin $BROKER_CLASS $expected $CONTROLLER >>$RUNTIME/broker.log 2>&1 </dev/null &" >/dev/null
for attempt in 1 2 3 4 5; do
  if control STATUS; then
    adb_pixi shell am start -W -n "$CONTROLLER/org.nullprotocol.nullgate.RootControlActivity" >/dev/null
    echo "Root broker ready and OFF. Choose app → root ON → make changes → root OFF."
    echo "Keep Rooted debugging enabled until Stop; PiXi ends this broker when ADB returns to non-root."
    exit
  fi
  sleep 1
done
die "root broker did not become ready; runtime evidence was preserved"
