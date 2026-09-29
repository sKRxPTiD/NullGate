#!/usr/bin/env bash
set -euo pipefail

PROJECT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
DIST="$PROJECT/dist/root-session-candidate"
ADB_BIN="${ADB_BIN:-adb}"
SERIAL="${NULLGATE_SERIAL:-54110DLAQ0043W}"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
CONTROLLER="org.nullprotocol.nullgate.rootcandidate"
RUNTIME="/data/local/tmp/nullgate-root"
BROKER_CLASS="org.nullprotocol.nullgate.broker.RootBrokerMain"
APK="$DIST/NullGate-prototype-debug.apk"

die() { echo "NullGate root candidate: $*" >&2; exit 1; }
adb_pixi() { "$ADB_BIN" -s "$SERIAL" "$@"; }
[[ "$SERIAL" =~ ^[A-Za-z0-9._:-]+$ ]] || die "invalid device serial"
[[ "${1:-}" == start || "${1:-}" == status || "${1:-}" == off || "${1:-}" == stop || "${1:-}" == recover ]] \
  || die "usage: bash device/nullgate-root-candidate.sh {start|status|off|stop|recover}"
[[ "$(adb_pixi get-state 2>/dev/null)" == device ]] || die "PiXi is unavailable"

control() {
  local apk_path
  apk_path="$(adb_pixi shell pm path "$CONTROLLER" | tr -d '\r' | sed -n 's/^package://p')"
  [[ "$apk_path" == /data/app/*/base.apk && "$apk_path" != *"'"* && "$apk_path" != *$'\n'* ]] \
    || die "candidate controller is not installed as one base APK"
  adb_pixi shell "run-as $CONTROLLER /system/bin/sh -c 'CLASSPATH=$apk_path exec /system/bin/app_process /system/bin org.nullprotocol.nullgate.RootControlMain $1'"
}

case "$1" in
  status) control STATUS; exit ;;
  off) control OFF; exit ;;
  stop) control SHUTDOWN; echo "Root broker stopped. Applied app changes remain."; exit ;;
esac

[[ "$(adb_pixi shell getprop ro.product.device | tr -d '\r')" == tokay ]] || die "device is not PiXi/tokay"
[[ "$(adb_pixi shell id | tr -d '\r')" == uid=0\(* ]] || die "starting the broker requires rooted ADB"
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
  echo "Archived the confirmed stale PID receipt; the broker can be started again."
  exit
fi
(cd "$DIST" && sha256sum --status -c SHA256SUMS) || die "candidate artifact checksum failed"
expected="$(sed -n 's/^controller\.signer\.sha256=//p' "$PROJECT/compat/pixi-private-pins.properties")"
actual="$("$SDK/build-tools/36.0.0/apksigner" verify --print-certs "$APK" \
  | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
[[ "$expected" =~ ^[0-9a-f]{64}$ && "$actual" == "$expected" ]] || die "candidate signer does not match the paired identity"
badging="$("$SDK/build-tools/36.0.0/aapt2" dump badging "$APK" | sed -n '1p')"
[[ "$badging" == *"name='$CONTROLLER'"* && "$badging" == *"versionCode='4'"* ]] || die "unexpected candidate APK"
processes="$(adb_pixi shell ps -A -o ARGS)" || die "process inventory unavailable"
[[ "$processes" != *"org.nullprotocol.nullgate.broker.NullGateBrokerMain"* ]] || die "the legacy broker is still running"
[[ "$processes" != *"$BROKER_CLASS"* ]] || die "root broker already exists; use status"
state="$(adb_pixi shell "if test -L $RUNTIME; then echo UNSAFE; elif test -d $RUNTIME; then stat -c '%u:%a' $RUNTIME; elif test -e $RUNTIME; then echo UNSAFE; else echo ABSENT; fi" | tr -d '\r')"
[[ "$state" == ABSENT || "$state" == 0:700 ]] || die "unsafe root runtime"
if [[ "$state" == ABSENT ]]; then adb_pixi shell "umask 077; mkdir $RUNTIME" >/dev/null; fi
[[ "$(adb_pixi shell "if test -e $RUNTIME/root-broker.pid || test -L $RUNTIME/root-broker.pid; then echo PRESENT; else echo ABSENT; fi" | tr -d '\r')" == ABSENT ]] \
  || die "a previous root PID receipt needs inspection"
adb_pixi install -r "$APK" >/dev/null || die "candidate controller installation failed"
adb_pixi push "$DIST/NullGate-broker.jar" "$RUNTIME/NullGate-broker.jar" >/dev/null
adb_pixi shell "umask 077; CLASSPATH=$RUNTIME/NullGate-broker.jar nohup /system/bin/app_process /system/bin $BROKER_CLASS $expected $CONTROLLER >>$RUNTIME/broker.log 2>&1 </dev/null &" >/dev/null
for attempt in 1 2 3 4 5; do
  if control STATUS; then
    adb_pixi shell am start -W -n "$CONTROLLER/org.nullprotocol.nullgate.RootControlActivity" >/dev/null
    echo "Root broker ready. Choose app → root ON → open app → make changes → root OFF."
    exit
  fi
  sleep 1
done
die "root broker did not become ready; runtime evidence was preserved"
