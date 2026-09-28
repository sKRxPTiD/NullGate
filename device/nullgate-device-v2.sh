#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ADB_BIN="${ADB_BIN:-adb}"
APKSIGNER_BIN="${APKSIGNER_BIN:-${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/36.0.0/apksigner}"
AAPT2_BIN="${AAPT2_BIN:-${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/36.0.0/aapt2}"
APK="$BASE_DIR/dist/NullGate-prototype-debug.apk"
TEST_CLIENT_APK="$BASE_DIR/dist/NullGate-test-client-debug.apk"
THEME_CLIENT_APK="$BASE_DIR/dist/NullGate-theme-client-debug.apk"
BROKER="$BASE_DIR/dist/NullGate-broker.jar"
CERT_FILE="$BASE_DIR/dist/controller-cert-sha256.txt"
CHECKSUM_FILE="$BASE_DIR/dist/SHA256SUMS"
PACKAGE="org.nullprotocol.nullgate"
TEST_CLIENT_PACKAGE="org.nullprotocol.nullgate.testclient"
THEME_CLIENT_PACKAGE="org.nullprotocol.nullgate.themeclient"
CONTROLLER_VERSION_CODE="3"
TEST_CLIENT_VERSION_CODE="1"
THEME_CLIENT_VERSION_CODE="1"
BROKER_CLASS="org.nullprotocol.nullgate.broker.NullGateBrokerMain"
DEVICE_DIR="/data/local/tmp/nullgate"
DEVICE_BROKER="$DEVICE_DIR/NullGate-broker.jar"
MUTATION_TOKEN="NULLGATE_MARKER_TEST_V1"
THEME_MUTATION_TOKEN="NULLGATE_SYSTEM_THEME_V1"
COLORBLENDR_MUTATION_TOKEN="NULLGATE_COLORBLENDR_SHIZUKU_V1"
TEST_CLIENT_MUTATION_TOKEN="NULLGATE_TEST_CLIENT_V1"
THEME_CLIENT_MUTATION_TOKEN="NULLGATE_THEME_CLIENT_V1"
CONTROLLER_RECORD_RECOVERY_TOKEN="NULLGATE_CONTROLLER_RECORD_RECOVERY_V1"
SERIAL="${NULLGATE_SERIAL:-}"
LOG_DIR="${NULLGATE_LOG_DIR:-$BASE_DIR/device/logs}"

die() { echo "NullGate: $*" >&2; exit 1; }
note() { echo "NullGate: $*"; }

case "${1:-}" in
  preflight|status|verify-clean|install-controller|install-test-client|install-theme-client|deploy|deploy-system-theme|deploy-colorblendr-shizuku|stop|cleanup|recover-marker-runtime|recover-system-theme-runtime|recover-controller-record) ;;
  *) die "usage: $0 {preflight|status|verify-clean|install-controller|install-test-client|install-theme-client|deploy|deploy-system-theme|deploy-colorblendr-shizuku|stop|cleanup|recover-marker-runtime|recover-system-theme-runtime|recover-controller-record}" ;;
esac

command -v "$ADB_BIN" >/dev/null 2>&1 || [[ -x "$ADB_BIN" ]] || die "ADB executable is unavailable"
[[ -x "$AAPT2_BIN" ]] || die "aapt2 executable is unavailable"
case "$1" in
  install-controller|deploy|stop|cleanup|recover-marker-runtime)
    [[ "${NULLGATE_MUTATION_TOKEN:-}" == "$MUTATION_TOKEN" ]] || die "device writes are locked; marker-test acknowledgement required"
    [[ -n "$SERIAL" ]] || die "device writes require an explicit NULLGATE_SERIAL"
    ;;
  deploy-system-theme|recover-system-theme-runtime)
    [[ "${NULLGATE_MUTATION_TOKEN:-}" == "$THEME_MUTATION_TOKEN" ]] || die "device writes are locked; system-theme acknowledgement required"
    [[ -n "$SERIAL" ]] || die "device writes require an explicit NULLGATE_SERIAL"
    ;;
  deploy-colorblendr-shizuku)
    [[ "${NULLGATE_MUTATION_TOKEN:-}" == "$COLORBLENDR_MUTATION_TOKEN" ]] || die "device writes are locked; ColorBlendr compatibility acknowledgement required"
    [[ -n "$SERIAL" ]] || die "device writes require an explicit NULLGATE_SERIAL"
    ;;
  install-test-client)
    [[ "${NULLGATE_MUTATION_TOKEN:-}" == "$TEST_CLIENT_MUTATION_TOKEN" ]] || die "device writes are locked; test-client acknowledgement required"
    [[ -n "$SERIAL" ]] || die "device writes require an explicit NULLGATE_SERIAL"
    ;;
  install-theme-client)
    [[ "${NULLGATE_MUTATION_TOKEN:-}" == "$THEME_CLIENT_MUTATION_TOKEN" ]] || die "device writes are locked; theme-client acknowledgement required"
    [[ -n "$SERIAL" ]] || die "device writes require an explicit NULLGATE_SERIAL"
    ;;
  recover-controller-record)
    [[ "${NULLGATE_MUTATION_TOKEN:-}" == "$CONTROLLER_RECORD_RECOVERY_TOKEN" ]] || die "device writes are locked; controller-record recovery acknowledgement required"
    [[ -n "$SERIAL" ]] || die "device writes require an explicit NULLGATE_SERIAL"
    ;;
esac
if [[ -z "$SERIAL" ]]; then
  SERIAL="$("$ADB_BIN" get-serialno 2>/dev/null)" || die "PiXi is not connected through ADB"
fi
[[ "$SERIAL" =~ ^[A-Za-z0-9._:-]+$ && "$SERIAL" != unknown ]] || die "invalid or ambiguous ADB serial"
mkdir -p "$BASE_DIR/device/.locks"
exec 9>"$BASE_DIR/device/.locks/$SERIAL.lock"
flock -n 9 || die "another NullGate helper is using this device"
adb_device() { "$ADB_BIN" -s "$SERIAL" "$@"; }

require_mutation_authorization() {
  local expected="${1:-$MUTATION_TOKEN}"
  [[ "${NULLGATE_MUTATION_TOKEN:-}" == "$expected" ]] || die "device writes are locked; the reviewed mutation token is required"
}

require_artifacts() {
  [[ -s "$APK" && -s "$TEST_CLIENT_APK" && -s "$THEME_CLIENT_APK" && -s "$BROKER" && -s "$CERT_FILE" && -s "$CHECKSUM_FILE" ]]     || die "artifacts missing; run build.sh"
  (cd "$BASE_DIR/dist" && sha256sum --status -c SHA256SUMS)     || die "artifact checksum verification failed"
  local cert
  cert="$(tr -d '\r\n' < "$CERT_FILE")"
  [[ "$cert" =~ ^[0-9a-f]{64}$ ]] || die "controller signer digest is malformed"
}

require_connected() {
  [[ "$(adb_device get-state 2>/dev/null)" == device ]] || die "PiXi is not connected through ADB"
}

require_no_broker() {
  local processes
  processes="$(adb_device shell ps -A -o ARGS)" || die "process inventory unavailable; state is UNKNOWN"
  [[ -n "$processes" ]] || die "empty process inventory; state is UNKNOWN"
  [[ "$processes" != *"$BROKER_CLASS"* ]] || die "broker is still running; stop before cleanup or deployment"
}

platform_preflight() {
  require_connected
  local codename release lineage identity context enforcement
  codename="$(adb_device shell getprop ro.product.device | tr -d '\r')"
  [[ "$codename" == tokay ]] || die "connected device is not PiXi/tokay: $codename"
  release="$(adb_device shell getprop ro.build.version.release | tr -d '\r')"
  [[ "$release" == 16 ]] || die "unexpected Android release: $release"
  lineage="$(adb_device shell getprop ro.lineage.version | tr -d '\r')"
  [[ "$lineage" == 23.2-* ]] || die "build does not identify itself as LineageOS 23.2"
  identity="$(adb_device shell id | tr -d '\r')"
  [[ "$identity" == uid=0\(* ]] || die "rooted debugging is not active; adb shell is not UID 0"
  context="$(adb_device shell id -Z | tr -d '\r')"
  [[ "$context" == u:r:su:s0 ]] || die "unexpected rooted-ADB SELinux context: $context"
  enforcement="$(adb_device shell getenforce | tr -d '\r')"
  [[ "$enforcement" == Enforcing ]] || die "SELinux must remain Enforcing"
}

runtime_state() {
  adb_device shell "if test -L $DEVICE_DIR; then echo SYMLINK; elif test -d $DEVICE_DIR; then stat -c 'DIR:%u:%a' $DEVICE_DIR; elif test -e $DEVICE_DIR; then echo OTHER; else echo ABSENT; fi"     | tr -d '\r'
}

require_private_runtime() {
  local state
  state="$(runtime_state)" || die "runtime state is UNKNOWN"
  [[ "$state" == DIR:0:700 ]] || die "unsafe runtime state: $state"
}

installed_apk_path() {
  local package_name="${1:-$PACKAGE}" output count path
  output="$(adb_device shell pm path "$package_name" | tr -d '\r')" || return 1
  count="$(printf '%s\n' "$output" | sed -n 's/^package://p' | wc -l | tr -d '[:space:]')"
  [[ "$count" == 1 ]] || return 1
  path="$(printf '%s\n' "$output" | sed -n 's/^package://p')"
  [[ "$path" == /data/app/*/base.apk ]] || return 1
  printf '%s\n' "$path"
}

verify_installed_signer() {
  verify_package_signer "$PACKAGE" controller
}

installed_version_code() {
  local package_name="$1" version
  version="$(adb_device shell dumpsys package "$package_name" \
    | sed -n 's/^[[:space:]]*versionCode=\([0-9][0-9]*\).*/\1/p' \
    | head -1 | tr -d '\r')" || return 1
  [[ "$version" =~ ^[0-9]+$ ]] || return 1
  printf '%s\n' "$version"
}

verify_package_signer() {
  local package_name="$1" label="$2" path temp_dir installed_copy expected actual
  path="$(installed_apk_path "$package_name")" || die "$label is not installed as one base APK"
  temp_dir="$(mktemp -d)"
  installed_copy="$temp_dir/controller.apk"
  if ! adb_device pull "$path" "$installed_copy" >/dev/null; then
    rm -rf -- "$temp_dir"
    die "could not copy installed $label for signer verification"
  fi
  expected="$(tr -d '\r\n' < "$CERT_FILE")"
  actual="$("$APKSIGNER_BIN" verify --print-certs "$installed_copy"     | sed -n 's/^Signer #[0-9][0-9]* certificate SHA-256 digest: //p')"
  rm -rf -- "$temp_dir"
  [[ "$actual" =~ ^[0-9a-f]{64}$ && "$actual" == "$expected" ]]     || die "installed $label signer does not match the controller pin"
}

verify_local_apk_signer() {
  local apk="$1" label="$2" expected actual
  expected="$(tr -d '\r\n' < "$CERT_FILE")"
  actual="$("$APKSIGNER_BIN" verify --print-certs "$apk" \
    | sed -n 's/^Signer #[0-9][0-9]* certificate SHA-256 digest: //p')"
  [[ "$actual" =~ ^[0-9a-f]{64}$ && "$actual" == "$expected" ]] \
    || die "local $label signer does not match the controller pin"
}

verify_local_apk_identity() {
  local apk="$1" expected_package="$2" expected_version="$3" label="$4"
  local badging package_name version_code
  badging="$("$AAPT2_BIN" dump badging "$apk" | sed -n '1p')" || die "could not inspect local $label identity"
  package_name="$(printf '%s\n' "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
  version_code="$(printf '%s\n' "$badging" | sed -n "s/^package: .* versionCode='\([^']*\)'.*/\1/p")"
  [[ "$package_name" == "$expected_package" && "$version_code" == "$expected_version" ]] || die "local $label package identity or version is not the reviewed build"
}

broker_pid() {
  adb_device shell "if test -L $DEVICE_DIR/broker.pid; then echo SYMLINK; elif test -f $DEVICE_DIR/broker.pid; then cat $DEVICE_DIR/broker.pid; elif test -e $DEVICE_DIR/broker.pid; then echo OTHER; else echo ABSENT; fi"     | tr -d '\r\n'
}

validated_stale_pid_receipt() {
  local pid process_state pid_state
  pid="$(broker_pid)" || die "PID state is UNKNOWN"
  if [[ "$pid" == ABSENT ]]; then
    printf 'ABSENT\n'
    return
  fi
  [[ "$pid" =~ ^[1-9][0-9]{0,8}$ && "$pid" -gt 1 ]] \
    || die "unsafe PID receipt; manual inspection required"
  process_state="$(adb_device shell "if test -d /proc/$pid; then echo PRESENT; else echo ABSENT; fi")" \
    || die "process inspection failed; state is UNKNOWN"
  [[ "$process_state" == ABSENT ]] \
    || die "recorded PID still exists; preserve receipt for inspection"
  pid_state="$(adb_device shell "if test -L $DEVICE_DIR/broker.pid; then echo SYMLINK; elif test -f $DEVICE_DIR/broker.pid; then stat -c 'FILE:%u:%a' $DEVICE_DIR/broker.pid; else echo OTHER; fi" | tr -d '\r')"
  [[ "$pid_state" == FILE:0:600 ]] || die "unsafe stale PID receipt: $pid_state"
  printf '%s\n' "$pid"
}

remove_validated_stale_pid_receipt() {
  local expected_pid="$1" current_pid process_state pid_state
  if [[ "$expected_pid" == ABSENT ]]; then
    return 0
  fi
  require_no_broker
  process_state="$(adb_device shell "if test -d /proc/$expected_pid; then echo PRESENT; else echo ABSENT; fi")" \
    || die "process inspection failed before PID receipt removal; state is UNKNOWN"
  [[ "$process_state" == ABSENT ]] \
    || die "recorded PID appeared during recovery; preserve receipt for inspection"
  current_pid="$(broker_pid)" || die "PID state changed during recovery"
  [[ "$current_pid" == "$expected_pid" ]] || die "PID receipt changed during recovery"
  pid_state="$(adb_device shell "if test -L $DEVICE_DIR/broker.pid; then echo SYMLINK; elif test -f $DEVICE_DIR/broker.pid; then stat -c 'FILE:%u:%a' $DEVICE_DIR/broker.pid; else echo OTHER; fi" | tr -d '\r')"
  [[ "$pid_state" == FILE:0:600 ]] || die "stale PID receipt became unsafe: $pid_state"
  adb_device shell "rm -f $DEVICE_DIR/broker.pid" >/dev/null \
    || die "PID receipt removal failed"
}

verify_process() {
  local pid="$1" cmdline uid_line
  [[ "$pid" =~ ^[1-9][0-9]{0,8}$ && "$pid" -gt 1 ]] || return 1
  cmdline="$(adb_device shell "tr '\\0' '\\n' </proc/$pid/cmdline" | tr -d '\r')" || return 1
  printf '%s\n' "$cmdline" | grep -Fxq "$BROKER_CLASS" || return 1
  uid_line="$(adb_device shell "sed -n 's/^Uid:[[:space:]]*//p' /proc/$pid/status" | tr -d '\r')" || return 1
  [[ "$uid_line" == 0$'\t'0$'\t'0$'\t'0 || "$uid_line" == "0 0 0 0" ]] || return 1
}

status() {
  platform_preflight
  local state pid
  state="$(runtime_state)" || die "runtime state is UNKNOWN"
  if [[ "$state" == ABSENT ]]; then
    note "runtime absent; broker not staged"
    return
  fi
  [[ "$state" == DIR:0:700 ]] || die "unsafe runtime state: $state"
  pid="$(broker_pid)" || die "PID state is UNKNOWN"
  if [[ "$pid" == ABSENT ]]; then
    note "private runtime present; no PID receipt; effects remain unverified"
    return 2
  elif verify_process "$pid"; then
    note "verified root broker process PID $pid on serial $SERIAL"
  else
    die "PID receipt does not prove a live NullGate root process; state is UNKNOWN"
  fi
}

preflight() {
  require_artifacts
  platform_preflight
  verify_installed_signer
  [[ "$(installed_version_code "$PACKAGE")" == "$CONTROLLER_VERSION_CODE" ]] \
    || die "broker deployment requires reviewed controller version $CONTROLLER_VERSION_CODE"
  note "preflight passed: serial $SERIAL; tokay; Android 16; LineageOS 23.2; UID 0; u:r:su:s0; Enforcing; signer pinned"
}

install_controller() {
  require_mutation_authorization
  require_artifacts
  platform_preflight
  verify_local_apk_identity "$APK" "$PACKAGE" "$CONTROLLER_VERSION_CODE" controller
  verify_local_apk_signer "$APK" controller
  require_no_broker
  [[ "$(runtime_state)" == ABSENT ]] || die "controller installation requires an absent broker runtime"
  local package_output
  package_output="$(adb_device shell pm list packages --user 0 "$PACKAGE" | tr -d '\r')"
  if [[ -n "$package_output" ]]; then
    installed_apk_path >/dev/null || die "controller package exists in an unsupported or ambiguous layout"
    verify_installed_signer
    adb_device install -r "$APK" >/dev/null || die "controller update failed"
    verify_installed_signer
    note "matching controller updated and signer verified"
    return
  fi
  adb_device install "$APK" >/dev/null || die "controller installation failed"
  verify_installed_signer
  note "controller installed and signer verified"
}

install_test_client() {
  require_mutation_authorization "$TEST_CLIENT_MUTATION_TOKEN"
  require_artifacts
  platform_preflight
  verify_local_apk_identity "$TEST_CLIENT_APK" "$TEST_CLIENT_PACKAGE" "$TEST_CLIENT_VERSION_CODE" "test client"
  verify_local_apk_signer "$TEST_CLIENT_APK" "test client"
  verify_installed_signer
  require_no_broker
  [[ "$(runtime_state)" == ABSENT ]] || die "test-client installation requires an absent broker runtime"
  local package_output
  package_output="$(adb_device shell pm list packages --user 0 "$TEST_CLIENT_PACKAGE" | tr -d '\r')"
  if [[ -n "$package_output" ]]; then
    installed_apk_path "$TEST_CLIENT_PACKAGE" >/dev/null \
      || die "test-client package exists in an unsupported or ambiguous layout"
    verify_package_signer "$TEST_CLIENT_PACKAGE" "test client"
    adb_device install -r "$TEST_CLIENT_APK" >/dev/null || die "test-client update failed"
  else
    adb_device install "$TEST_CLIENT_APK" >/dev/null || die "test-client installation failed"
  fi
  verify_package_signer "$TEST_CLIENT_PACKAGE" "test client"
  note "paired test client installed and signer verified; no broker was launched"
}

install_theme_client() {
  require_mutation_authorization "$THEME_CLIENT_MUTATION_TOKEN"
  require_artifacts
  platform_preflight
  verify_local_apk_identity "$THEME_CLIENT_APK" "$THEME_CLIENT_PACKAGE" "$THEME_CLIENT_VERSION_CODE" "theme client"
  verify_local_apk_signer "$THEME_CLIENT_APK" "theme client"
  verify_installed_signer
  [[ "$(installed_version_code "$PACKAGE")" == "$CONTROLLER_VERSION_CODE" ]] \
    || die "theme-client installation requires the reviewed controller version $CONTROLLER_VERSION_CODE"
  require_no_broker
  [[ "$(runtime_state)" == ABSENT ]] || die "theme-client installation requires an absent broker runtime"
  local package_output
  package_output="$(adb_device shell pm list packages --user 0 "$THEME_CLIENT_PACKAGE" | tr -d '\r')"
  if [[ -n "$package_output" ]]; then
    installed_apk_path "$THEME_CLIENT_PACKAGE" >/dev/null \
      || die "theme-client package exists in an unsupported or ambiguous layout"
    verify_package_signer "$THEME_CLIENT_PACKAGE" "theme client"
    adb_device install -r "$THEME_CLIENT_APK" >/dev/null || die "theme-client update failed"
  else
    adb_device install "$THEME_CLIENT_APK" >/dev/null || die "theme-client installation failed"
  fi
  verify_package_signer "$THEME_CLIENT_PACKAGE" "theme client"
  note "paired theme client installed and signer verified; no broker was launched"
}

deploy_with_mode() {
  local broker_mode="$1"
  case "$broker_mode" in
    SYSTEM_THEME_V1) require_mutation_authorization "$THEME_MUTATION_TOKEN" ;;
    COLORBLENDR_SHIZUKU_V1) require_mutation_authorization "$COLORBLENDR_MUTATION_TOKEN" ;;
    "") require_mutation_authorization "$MUTATION_TOKEN" ;;
    *) die "unknown broker deployment mode" ;;
  esac
  preflight
  require_no_broker
  local state host_hash remote_hash pid attempt
  state="$(runtime_state)" || die "runtime state is UNKNOWN"
  [[ "$state" == ABSENT ]] || die "deployment requires an absent runtime, found: $state"
  adb_device shell "umask 077; mkdir $DEVICE_DIR && chown 0:0 $DEVICE_DIR && chmod 0700 $DEVICE_DIR"     >/dev/null || die "could not create fresh private runtime"
  require_private_runtime
  adb_device push "$BROKER" "$DEVICE_BROKER" >/dev/null || die "broker transfer failed"
  adb_device shell "chown 0:0 $DEVICE_BROKER && chmod 0400 $DEVICE_BROKER" >/dev/null     || die "could not secure broker artifact"
  host_hash="$(sha256sum "$BROKER" | awk '{print $1}')"
  remote_hash="$(adb_device shell "sha256sum $DEVICE_BROKER" | awk '{print $1}' | tr -d '\r')"
  [[ "$remote_hash" == "$host_hash" ]] || die "device broker digest mismatch"
  local cert
  cert="$(tr -d '\r\n' < "$CERT_FILE")"
  if [[ -n "$broker_mode" ]]; then
    adb_device shell "umask 077; CLASSPATH=$DEVICE_BROKER app_process / $BROKER_CLASS $cert $broker_mode >$DEVICE_DIR/broker.log 2>&1 </dev/null &" >/dev/null || die "broker launch command failed"
  else
    adb_device shell "umask 077; CLASSPATH=$DEVICE_BROKER app_process / $BROKER_CLASS $cert >$DEVICE_DIR/broker.log 2>&1 </dev/null &" >/dev/null || die "broker launch command failed"
  fi
  pid=ABSENT
  for attempt in 1 2 3 4 5 6 7 8 9 10; do
    pid="$(broker_pid 2>/dev/null || true)"
    if [[ "$pid" != ABSENT ]] && verify_process "$pid"; then
      note "${broker_mode:-marker-test} broker launched and verified as root PID $pid"
      return
    fi
    sleep 0.2
  done
  die "broker did not become verifiably ready; preserve runtime and log for diagnosis"
}

deploy() { deploy_with_mode ""; }

deploy_system_theme() {
  [[ "${NULLGATE_MUTATION_TOKEN:-}" == "$THEME_MUTATION_TOKEN" ]] || die "system-theme acknowledgement required"
  deploy_with_mode "SYSTEM_THEME_V1"
}

deploy_colorblendr_shizuku() {
  [[ "${NULLGATE_MUTATION_TOKEN:-}" == "$COLORBLENDR_MUTATION_TOKEN" ]] || die "ColorBlendr compatibility acknowledgement required"
  deploy_with_mode "COLORBLENDR_SHIZUKU_V1"
}

stop_broker() {
  require_mutation_authorization
  platform_preflight
  require_private_runtime
  local pid attempt process_state
  pid="$(broker_pid)" || die "PID state is UNKNOWN"
  [[ "$pid" != ABSENT ]] || { note "no PID receipt; refusing to infer cleanup"; return 2; }
  verify_process "$pid" || die "PID receipt is stale or mismatched; refusing to signal"
  verify_process "$pid" || die "process identity changed before signal"
  adb_device shell "kill -TERM $pid" >/dev/null || die "SIGTERM failed"
  for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
    process_state="$(adb_device shell "if test -d /proc/$pid; then echo PRESENT; else echo ABSENT; fi")" || die "process inspection failed; state is UNKNOWN"
    if [[ "$process_state" == ABSENT ]]; then
      require_no_broker
      verify_no_leases
      local pid_state pid_content
      pid_content="$(broker_pid)" || die "process exited but PID receipt is unreadable"
      if [[ "$pid_content" != ABSENT ]]; then
        pid_state="$(adb_device shell "if test -L $DEVICE_DIR/broker.pid; then echo SYMLINK; elif test -f $DEVICE_DIR/broker.pid; then stat -c 'FILE:%u:%a' $DEVICE_DIR/broker.pid; else echo OTHER; fi" | tr -d '\r')"
        [[ "$pid_state" == FILE:0:600 ]] || die "process exited but PID receipt is unsafe: $pid_state"
        [[ "$pid_content" == "$pid" ]] || die "process exited but PID receipt changed"
        adb_device shell "rm -f $DEVICE_DIR/broker.pid" >/dev/null || die "could not remove verified stale PID receipt"
      fi
      note "broker exited; PID receipt and lease markers absent"
      return
    fi
    sleep 0.2
  done
  die "broker did not stop cleanly; no SIGKILL was sent"
}

archive_log() {
  local log_state archive_dir stamp archive_file remote_hash
  log_state="$(adb_device shell "if test -L $DEVICE_DIR/broker.log; then echo SYMLINK; elif test -f $DEVICE_DIR/broker.log; then stat -c 'FILE:%u:%a' $DEVICE_DIR/broker.log; elif test -e $DEVICE_DIR/broker.log; then echo OTHER; else echo ABSENT; fi" | tr -d '\r')"
  [[ "$log_state" == ABSENT ]] && return
  [[ "$log_state" == FILE:0:600 ]] || die "unsafe broker log state: $log_state"
  archive_dir="$LOG_DIR"
  mkdir -p "$archive_dir"
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  remote_hash="$(adb_device shell "sha256sum $DEVICE_DIR/broker.log" | awk '{print $1}' | tr -d '\r')" \
    || die "could not hash broker log; runtime preserved"
  [[ "$remote_hash" =~ ^[0-9a-f]{64}$ ]] || die "invalid broker log hash; runtime preserved"
  archive_file="$(mktemp "$archive_dir/broker-$SERIAL-$stamp-XXXXXX.log")" \
    || die "could not reserve broker log archive; runtime preserved"
  adb_device pull "$DEVICE_DIR/broker.log" "$archive_file" >/dev/null \
    || die "could not preserve broker log"
  [[ "$(sha256sum "$archive_file" | awk '{print $1}')" == "$remote_hash" ]] \
    || die "broker log archive hash mismatch; runtime preserved"
  note "broker log archived under device/logs"
}

verify_no_leases() {
  local result
  result="$(adb_device shell "if test -L $DEVICE_DIR/leases; then echo SYMLINK; elif test -d $DEVICE_DIR/leases; then find $DEVICE_DIR/leases -mindepth 1 -maxdepth 1 -print; elif test -e $DEVICE_DIR/leases; then echo OTHER; else echo ABSENT; fi" | tr -d '\r')"
  [[ "$result" == ABSENT || -z "$result" ]] || die "lease artifacts remain; refusing cleanup: $result"
}

cleanup_runtime() {
  local expected_token="${1:-$MUTATION_TOKEN}"
  require_mutation_authorization "$expected_token"
  platform_preflight
  require_private_runtime
  require_no_broker
  local pid entries
  pid="$(broker_pid)" || die "PID state is UNKNOWN"
  [[ "$pid" == ABSENT ]] || die "PID receipt remains; stop or reconcile the broker first"
  verify_no_leases
  archive_log
  entries="$(adb_device shell "find $DEVICE_DIR -mindepth 1 -maxdepth 1 -printf '%f\\n'" | tr -d '\r')"
  while IFS= read -r entry; do
    [[ -z "$entry" || "$entry" == "NullGate-broker.jar" || "$entry" == "broker.log" || "$entry" == "leases" ]]       || die "unexpected runtime entry; refusing cleanup: $entry"
  done <<< "$entries"
  adb_device shell "test ! -e $DEVICE_DIR/leases || rmdir $DEVICE_DIR/leases" >/dev/null     || die "lease directory is not empty"
  adb_device shell "rm -f $DEVICE_BROKER $DEVICE_DIR/broker.log && rmdir $DEVICE_DIR" >/dev/null     || die "runtime cleanup failed"
  [[ "$(runtime_state)" == ABSENT ]] || die "runtime still exists after cleanup"
  note "runtime removed after verified zero-leases check"
}

recover_marker_runtime() {
  require_mutation_authorization
  platform_preflight
  require_private_runtime
  require_no_broker
  local pid marker_list marker marker_path file_state content lease_id expiry entries entry
  local -a validated_markers=()
  pid="$(validated_stale_pid_receipt)"

  marker_list="$(adb_device shell "if test -L $DEVICE_DIR/leases; then echo SYMLINK; elif test -d $DEVICE_DIR/leases; then find $DEVICE_DIR/leases -mindepth 1 -maxdepth 1 -printf '%f:%y\\n'; elif test -e $DEVICE_DIR/leases; then echo OTHER; else echo ABSENT; fi" | tr -d '\r')"
  [[ "$marker_list" != SYMLINK && "$marker_list" != OTHER ]] \
    || die "unsafe lease directory; manual inspection required"
  if [[ "$marker_list" != ABSENT && -n "$marker_list" ]]; then
    while IFS= read -r marker; do
      [[ "$marker" =~ ^([A-Za-z0-9_-]{16,128}\.lease):f$ ]] \
        || die "unexpected lease artifact; manual inspection required: $marker"
      marker="${BASH_REMATCH[1]}"
      marker_path="$DEVICE_DIR/leases/$marker"
      file_state="$(adb_device shell "stat -c 'FILE:%u:%a' $marker_path" | tr -d '\r')"
      [[ "$file_state" == FILE:0:600 ]] || die "unsafe marker ownership or mode: $marker"
      content="$(adb_device shell "cat $marker_path" | tr -d '\r')"
      lease_id="${marker%.lease}"
      expiry="$(printf '%s\n' "$content" | sed -n 's/^expiresElapsed=//p')"
      [[ "$content" == "lease=$lease_id"$'\n'"expiresElapsed=$expiry" \
          && "$expiry" =~ ^[0-9]{1,19}$ ]] \
        || die "marker content failed validation: $marker"
      validated_markers+=("$marker_path")
    done <<< "$marker_list"
  fi
  entries="$(adb_device shell "find $DEVICE_DIR -mindepth 1 -maxdepth 1 -printf '%f\\n'" | tr -d '\r')" || die "runtime inventory failed"
  while IFS= read -r entry; do
    [[ -z "$entry" || "$entry" == broker.pid || "$entry" == NullGate-broker.jar || "$entry" == broker.log || "$entry" == leases ]] || die "unexpected runtime entry; refusing recovery"
  done <<< "$entries"
  require_no_broker
  for marker_path in "${validated_markers[@]}"; do
    adb_device shell "rm -f $marker_path" >/dev/null || die "marker removal failed"
  done
  remove_validated_stale_pid_receipt "$pid"
  cleanup_runtime
}

verify_clean() {
  platform_preflight
  require_no_broker
  local state
  state="$(runtime_state)" || die "runtime state is UNKNOWN"
  [[ "$state" == ABSENT ]] || die "runtime is not clean: $state"
  note "runtime absent; no NullGate device artifacts found at the managed path"
}

recover_system_theme_runtime() {
  require_mutation_authorization "$THEME_MUTATION_TOKEN"
  platform_preflight
  require_private_runtime
  require_no_broker
  local pid receipt_state temp_dir receipt kind snapshot current entries entry archived_receipt receipt_hash remote_receipt_hash
  pid="$(validated_stale_pid_receipt)"
  receipt_state="$(adb_device shell "if test -L $DEVICE_DIR/theme.snapshot; then echo SYMLINK; elif test -f $DEVICE_DIR/theme.snapshot; then stat -c 'FILE:%u:%a:%s' $DEVICE_DIR/theme.snapshot; elif test -e $DEVICE_DIR/theme.snapshot; then echo OTHER; else echo ABSENT; fi" | tr -d '\r')"
  [[ "$receipt_state" =~ ^FILE:0:600:([0-9]{1,5})$ ]] || die "theme recovery receipt is absent or unsafe: $receipt_state"
  (( BASH_REMATCH[1] <= 16400 )) || die "theme recovery receipt exceeds safety bound"
  verify_no_leases
  entries="$(adb_device shell "find $DEVICE_DIR -mindepth 1 -maxdepth 1 -printf '%f\n'" | tr -d '\r')" \
    || die "runtime inventory failed"
  while IFS= read -r entry; do
    [[ -z "$entry" || "$entry" == broker.pid || "$entry" == NullGate-broker.jar \
        || "$entry" == broker.log || "$entry" == leases || "$entry" == theme.snapshot ]] \
      || die "unexpected runtime entry; refusing theme recovery: $entry"
  done <<< "$entries"
  require_no_broker
  remote_receipt_hash="$(adb_device shell "sha256sum $DEVICE_DIR/theme.snapshot" | awk '{print $1}' | tr -d '\r')" \
    || die "could not hash theme recovery receipt"
  [[ "$remote_receipt_hash" =~ ^[0-9a-f]{64}$ ]] || die "invalid theme recovery receipt hash"
  temp_dir="$(mktemp -d)"
  receipt="$temp_dir/theme.snapshot"
  adb_device pull "$DEVICE_DIR/theme.snapshot" "$receipt" >/dev/null || { rm -rf -- "$temp_dir"; die "could not preserve theme recovery receipt"; }
  [[ "$(sha256sum "$receipt" | awk '{print $1}')" == "$remote_receipt_hash" ]] \
    || { rm -rf -- "$temp_dir"; die "theme recovery receipt transfer hash mismatch; restoration refused"; }
  kind="$(sed -n '1p' "$receipt")"
  snapshot="$(sed '1d' "$receipt")"
  if [[ "$kind" == NULL && -z "$snapshot" ]]; then
    adb_device shell "cmd settings delete secure theme_customization_overlay_packages" >/dev/null || { rm -rf -- "$temp_dir"; die "theme deletion recovery failed"; }
    current="$(adb_device shell settings get secure theme_customization_overlay_packages | tr -d '\r')"
    [[ "$current" == null ]] || { rm -rf -- "$temp_dir"; die "theme deletion recovery was not verified"; }
  elif [[ "$kind" == VALUE && -n "$snapshot" && "$snapshot" != *"'"* ]]; then
    command -v jq >/dev/null || { rm -rf -- "$temp_dir"; die "jq is required to validate theme recovery JSON"; }
    printf '%s' "$snapshot" | jq -e 'type == "object"' >/dev/null || { rm -rf -- "$temp_dir"; die "theme recovery receipt is not a JSON object"; }
    adb_device shell "cmd settings put secure theme_customization_overlay_packages '$snapshot'" >/dev/null || { rm -rf -- "$temp_dir"; die "theme restoration command failed"; }
    current="$(adb_device shell settings get secure theme_customization_overlay_packages | tr -d '\r')"
    [[ "$current" == "$snapshot" ]] || { rm -rf -- "$temp_dir"; die "theme restoration was not exact"; }
  else
    rm -rf -- "$temp_dir"; die "theme recovery receipt format is invalid"
  fi
  mkdir -p "$LOG_DIR"
  receipt_hash="$(sha256sum "$receipt" | awk '{print $1}')"
  archived_receipt="$(mktemp "$LOG_DIR/theme-recovery-$SERIAL-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX.snapshot")" \
    || { rm -rf -- "$temp_dir"; die "theme recovery archive could not be reserved; device receipt preserved"; }
  cp -- "$receipt" "$archived_receipt" \
    || { rm -rf -- "$temp_dir"; die "theme recovery archive could not be written; device receipt preserved"; }
  [[ "$(sha256sum "$archived_receipt" | awk '{print $1}')" == "$receipt_hash" ]] \
    || { rm -rf -- "$temp_dir"; die "theme recovery archive failed hash verification; device receipt preserved"; }
  rm -rf -- "$temp_dir"
  adb_device shell "rm -f $DEVICE_DIR/theme.snapshot" >/dev/null || die "could not remove verified theme recovery receipt"
  remove_validated_stale_pid_receipt "$pid"
  cleanup_runtime "$THEME_MUTATION_TOKEN"
  note "theme restored exactly from the root-owned receipt and runtime removed"
}

recover_controller_record() {
  require_mutation_authorization "$CONTROLLER_RECORD_RECOVERY_TOKEN"
  platform_preflight
  verify_installed_signer
  [[ "$(installed_version_code "$PACKAGE")" == "$CONTROLLER_VERSION_CODE" ]] \
    || die "controller-record recovery requires reviewed controller version $CONTROLLER_VERSION_CODE"
  require_no_broker
  [[ "$(runtime_state)" == ABSENT ]] \
    || die "controller-record recovery requires an absent broker runtime"

  local expected_record_hash="${NULLGATE_EXPECTED_RECORD_SHA256:-}"
  local expected_theme_hash="${NULLGATE_EXPECTED_THEME_SHA256:-}"
  local expected_lease_id="${NULLGATE_EXPECTED_LEASE_ID:-}"
  local expected_client_package="${NULLGATE_EXPECTED_CLIENT_PACKAGE:-}"
  [[ "$expected_record_hash" =~ ^[0-9a-f]{64}$ ]] \
    || die "controller-record recovery requires the exact record SHA-256"
  [[ "$expected_theme_hash" =~ ^[0-9a-f]{64}$ ]] \
    || die "controller-record recovery requires the exact restored-theme SHA-256"
  [[ "$expected_lease_id" =~ ^[A-Za-z0-9_-]{16,128}$ ]] \
    || die "controller-record recovery requires the exact lease ID"
  [[ "$expected_client_package" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$ ]] \
    || die "controller-record recovery requires the exact client package"

  local app_uid record_path backup_path app_state prefs_state record_state backup_state
  local current_theme current_theme_hash remote_hash temp_dir record_copy archived_copy
  local recorded_expiry now_elapsed_millis
  app_uid="$(adb_device shell "cmd package list packages -U $PACKAGE" \
    | sed -n "s/^package:$PACKAGE uid://p" | tr -d '\r')"
  [[ "$app_uid" =~ ^[1-9][0-9]{3,8}$ ]] || die "controller UID is unavailable or ambiguous"
  record_path="/data/user/0/$PACKAGE/shared_prefs/external_client_leases.xml"
  backup_path="$record_path.bak"

  adb_device shell "am force-stop $PACKAGE" >/dev/null \
    || die "controller could not be stopped for record recovery"
  [[ -z "$(adb_device shell "pidof $PACKAGE" | tr -d '\r[:space:]')" ]] \
    || die "controller process remains live; record recovery refused"
  require_no_broker
  [[ "$(runtime_state)" == ABSENT ]] \
    || die "broker runtime appeared during controller-record recovery"

  app_state="$(adb_device shell "if test -L /data/user/0/$PACKAGE; then echo SYMLINK; elif test -d /data/user/0/$PACKAGE; then stat -c 'DIR:%u:%g:%a' /data/user/0/$PACKAGE; else echo OTHER; fi" | tr -d '\r')"
  prefs_state="$(adb_device shell "if test -L /data/user/0/$PACKAGE/shared_prefs; then echo SYMLINK; elif test -d /data/user/0/$PACKAGE/shared_prefs; then stat -c 'DIR:%u:%g:%a' /data/user/0/$PACKAGE/shared_prefs; else echo OTHER; fi" | tr -d '\r')"
  record_state="$(adb_device shell "if test -L $record_path; then echo SYMLINK; elif test -f $record_path; then stat -c 'FILE:%u:%g:%a:%s' $record_path; elif test -e $record_path; then echo OTHER; else echo ABSENT; fi" | tr -d '\r')"
  backup_state="$(adb_device shell "if test -L $backup_path; then echo SYMLINK; elif test -e $backup_path; then echo PRESENT; else echo ABSENT; fi" | tr -d '\r')"
  [[ "$app_state" == "DIR:$app_uid:$app_uid:700" ]] \
    || die "controller data directory is unsafe: $app_state"
  [[ "$prefs_state" == "DIR:$app_uid:$app_uid:771" ]] \
    || die "controller preference directory is unsafe: $prefs_state"
  [[ "$record_state" =~ ^FILE:$app_uid:$app_uid:660:([1-9][0-9]{0,4})$ \
      && "${BASH_REMATCH[1]}" -le 16384 ]] \
    || die "controller recovery record is absent or unsafe: $record_state"
  [[ "$backup_state" == ABSENT ]] \
    || die "controller recovery backup exists; manual inspection required"

  current_theme="$(adb_device shell settings get secure theme_customization_overlay_packages | tr -d '\r')"
  current_theme_hash="$(printf '%s' "$current_theme" | sha256sum | awk '{print $1}')"
  [[ "$current_theme_hash" == "$expected_theme_hash" ]] \
    || die "current theme does not match the independently recorded restored baseline"
  remote_hash="$(adb_device shell "sha256sum $record_path" | awk '{print $1}' | tr -d '\r')"
  [[ "$remote_hash" == "$expected_record_hash" ]] \
    || die "controller recovery record changed or does not match the reviewed hash"

  temp_dir="$(mktemp -d)"
  record_copy="$temp_dir/external_client_leases.xml"
  adb_device pull "$record_path" "$record_copy" >/dev/null \
    || { rm -rf -- "$temp_dir"; die "controller recovery record could not be archived"; }
  chmod 0600 "$record_copy"
  [[ "$(sha256sum "$record_copy" | awk '{print $1}')" == "$expected_record_hash" ]] \
    || { rm -rf -- "$temp_dir"; die "archived controller record hash mismatch"; }
  grep -Fqx "    <string name=\"lease_id\">$expected_lease_id</string>" "$record_copy" \
    || { rm -rf -- "$temp_dir"; die "controller record lease ID does not match"; }
  grep -Fqx "    <string name=\"client_package\">$expected_client_package</string>" "$record_copy" \
    || { rm -rf -- "$temp_dir"; die "controller record client package does not match"; }
  grep -Eq '^    <string name="phase">(RECONCILING|UNKNOWN|CLEANUP_FAILED)</string>$' "$record_copy" \
    || { rm -rf -- "$temp_dir"; die "controller record is not in a recoverable uncertain phase"; }
  recorded_expiry="$(sed -n 's/^    <long name="expires" value="\([0-9][0-9]*\)" \/>$/\1/p' "$record_copy")"
  [[ "$recorded_expiry" =~ ^[0-9]{1,19}$ ]] \
    || { rm -rf -- "$temp_dir"; die "controller record expiry is missing or malformed"; }
  now_elapsed_millis="$(adb_device shell cat /proc/uptime \
    | awk '{printf "%.0f\n", $1 * 1000}' | tr -d '\r')"
  [[ "$now_elapsed_millis" =~ ^[0-9]{1,19}$ && "$recorded_expiry" -lt "$now_elapsed_millis" ]] \
    || { rm -rf -- "$temp_dir"; die "controller record has not reached its elapsed deadline"; }

  mkdir -p "$LOG_DIR"
  archived_copy="$LOG_DIR/controller-record-$SERIAL-$(date -u +%Y%m%dT%H%M%SZ)-$expected_record_hash.xml"
  cp -- "$record_copy" "$archived_copy" \
    || { rm -rf -- "$temp_dir"; die "controller recovery archive could not be written"; }
  chmod 0600 "$archived_copy" 2>/dev/null || true
  [[ "$(sha256sum "$archived_copy" | awk '{print $1}')" == "$expected_record_hash" ]] \
    || { rm -rf -- "$temp_dir"; die "controller recovery archive failed hash verification"; }
  rm -rf -- "$temp_dir"

  require_no_broker
  [[ "$(runtime_state)" == ABSENT ]] \
    || die "broker runtime appeared before controller-record removal"
  [[ "$(adb_device shell "sha256sum $record_path" | awk '{print $1}' | tr -d '\r')" == "$expected_record_hash" ]] \
    || die "controller recovery record changed before removal"
  adb_device shell "rm -f $record_path" >/dev/null \
    || die "verified controller recovery record could not be removed"
  [[ "$(adb_device shell "if test -e $record_path || test -e $backup_path; then echo PRESENT; else echo ABSENT; fi" | tr -d '\r')" == ABSENT ]] \
    || die "controller recovery record remains after removal"
  note "verified stale controller record archived and removed; broker runtime remained absent"
}

case "$1" in
  preflight) preflight ;;
  status) status ;;
  verify-clean) verify_clean ;;
  install-controller) install_controller ;;
  install-test-client) install_test_client ;;
  install-theme-client) install_theme_client ;;
  deploy) deploy ;;
  deploy-system-theme) deploy_system_theme ;;
  deploy-colorblendr-shizuku) deploy_colorblendr_shizuku ;;
  stop) stop_broker ;;
  cleanup) cleanup_runtime ;;
  recover-marker-runtime) recover_marker_runtime ;;
  recover-system-theme-runtime) recover_system_theme_runtime ;;
  recover-controller-record) recover_controller_record ;;
esac
