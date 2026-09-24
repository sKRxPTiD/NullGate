#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
HELPER="$BASE_DIR/device/nullgate-device-v2.sh"
FAKE_ADB="$BASE_DIR/device/test/fake-adb-v2.sh"
FAKE_SIGNER="$BASE_DIR/device/test/fake-apksigner.sh"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf -- "$TEST_DIR"' EXIT
STATE="$TEST_DIR/state"

set_state() {
  printf 'installed=%s runtime=%s broker=%s running=%s pid_receipt=%s leases=%s unknown=%s\n'     "${1:-0}" "${2:-0}" "${3:-0}" "${4:-0}" "${5:-0}" "${6:-0}" "${7:-0}" > "$STATE"
}

run_helper() {
  local scenario="$1" action="$2"
  FAKE_STATE_FILE="$STATE" FAKE_SCENARIO="$scenario"     FAKE_CERT_FILE="$BASE_DIR/dist/controller-cert-sha256.txt"     FAKE_BROKER_FILE="$BASE_DIR/dist/NullGate-broker.jar"     NULLGATE_SERIAL=PIXI_TEST_SERIAL NULLGATE_MUTATION_TOKEN=NULLGATE_MARKER_TEST_V1     NULLGATE_LOG_DIR="$TEST_DIR/logs"     ADB_BIN="$FAKE_ADB" APKSIGNER_BIN="$FAKE_SIGNER" "$HELPER" "$action"
}

run_theme_helper() {
  local scenario="$1" action="$2"
  FAKE_STATE_FILE="$STATE" FAKE_SCENARIO="$scenario" \
    FAKE_CERT_FILE="$BASE_DIR/dist/controller-cert-sha256.txt" \
    FAKE_BROKER_FILE="$BASE_DIR/dist/NullGate-broker.jar" \
    NULLGATE_SERIAL=PIXI_TEST_SERIAL NULLGATE_MUTATION_TOKEN=NULLGATE_SYSTEM_THEME_V1 \
    NULLGATE_LOG_DIR="$TEST_DIR/logs" ADB_BIN="$FAKE_ADB" APKSIGNER_BIN="$FAKE_SIGNER" \
    "$HELPER" "$action"
}

run_colorblendr_helper() {
  local scenario="$1" action="$2"
  FAKE_STATE_FILE="$STATE" FAKE_SCENARIO="$scenario" \
    FAKE_CERT_FILE="$BASE_DIR/dist/controller-cert-sha256.txt" \
    FAKE_BROKER_FILE="$BASE_DIR/dist/NullGate-broker.jar" \
    NULLGATE_SERIAL=PIXI_TEST_SERIAL NULLGATE_MUTATION_TOKEN=NULLGATE_COLORBLENDR_SHIZUKU_V1 \
    NULLGATE_LOG_DIR="$TEST_DIR/logs" ADB_BIN="$FAKE_ADB" APKSIGNER_BIN="$FAKE_SIGNER" \
    "$HELPER" "$action"
}

expect_failure() {
  local scenario="$1" action="$2" expected="$3" output
  if output="$(run_helper "$scenario" "$action" 2>&1)"; then
    echo "expected failure: $scenario $action" >&2; exit 1
  fi
  [[ "$output" == *"$expected"* ]] || {
    echo "unexpected failure for $scenario $action: $output" >&2; exit 1;
  }
}

set_state
if output="$(FAKE_STATE_FILE="$STATE" FAKE_SCENARIO=ready   FAKE_CERT_FILE="$BASE_DIR/dist/controller-cert-sha256.txt"   FAKE_BROKER_FILE="$BASE_DIR/dist/NullGate-broker.jar"   NULLGATE_SERIAL=PIXI_TEST_SERIAL ADB_BIN="$FAKE_ADB" APKSIGNER_BIN="$FAKE_SIGNER"   "$HELPER" install-controller 2>&1)"; then
  echo "mutation authorization was not required" >&2; exit 1
fi
[[ "$output" == *"device writes are locked"* ]]

set_state 1
if output="$(run_helper ready deploy-system-theme 2>&1)"; then
  echo "marker token enabled system-theme mode" >&2; exit 1
fi
[[ "$output" == *"system-theme acknowledgement required"* ]]

set_state 1
if output="$(run_helper ready deploy-colorblendr-shizuku 2>&1)"; then
  echo "marker token enabled ColorBlendr compatibility mode" >&2; exit 1
fi
[[ "$output" == *"ColorBlendr compatibility acknowledgement required"* ]]

set_state 1
run_colorblendr_helper require-colorblendr-mode deploy-colorblendr-shizuku >/dev/null
running="$(run_colorblendr_helper ready status)"
[[ "$running" == *"verified root broker process PID 4242"* ]]
run_helper ready stop >/dev/null
run_helper ready cleanup >/dev/null
run_helper ready verify-clean >/dev/null

set_state 1
run_theme_helper require-theme-mode deploy-system-theme >/dev/null
running="$(run_theme_helper ready status)"
[[ "$running" == *"verified root broker process PID 4242"* ]]
run_helper ready stop >/dev/null
run_helper ready cleanup >/dev/null
run_helper ready verify-clean >/dev/null

set_state 1
run_helper reject-theme-mode deploy >/dev/null
run_helper ready stop >/dev/null
run_helper ready cleanup >/dev/null

set_state 1
expect_failure disconnected preflight "not connected"
expect_failure wrong-device preflight "not PiXi/tokay"
expect_failure wrong-android preflight "unexpected Android"
expect_failure not-lineage preflight "LineageOS 23.2"
expect_failure nonroot preflight "not active"
expect_failure wrong-context preflight "unexpected rooted-ADB"
expect_failure permissive preflight "SELinux must remain Enforcing"
expect_failure wrong-signer preflight "signer does not match"

set_state
run_helper ready verify-clean >/dev/null
run_helper ready install-controller >/dev/null
run_helper ready preflight >/dev/null
run_helper ready deploy >/dev/null
running="$(run_helper ready status)"
[[ "$running" == *"verified root broker process PID 4242"* ]]
run_helper ready stop >/dev/null
run_helper ready cleanup >/dev/null
run_helper ready verify-clean >/dev/null

set_state 1
expect_failure hash-mismatch deploy "digest mismatch"
set_state 1 1 1 1 1
expect_failure stale-pid status "state is UNKNOWN"
set_state 1 1 1 1 1
expect_failure wrong-process-uid status "state is UNKNOWN"
set_state 1 1 1 0 0 1
expect_failure ready cleanup "lease artifacts remain"
set_state 1 1 1 0 0 0 1
expect_failure ready cleanup "unexpected runtime entry"
set_state 1 1 1 0 0
expect_failure cleanup-fail cleanup "runtime cleanup failed"
set_state 1 1 1 1 1
run_helper stale-receipt stop >/dev/null
[[ "$(cat "$STATE")" == "installed=1 runtime=1 broker=1 running=0 pid_receipt=0 leases=0 unknown=0" ]]
set_state 1 1 1 1 1
expect_failure term-fail stop "SIGTERM failed"
set_state 1
expect_failure launch-fail deploy "launch command failed"
set_state 1
expect_failure mkdir-fail deploy "could not create fresh private runtime"
set_state 1
expect_failure runtime-symlink deploy "requires an absent runtime"
set_state 1 1 1 1 1
expect_failure ready recover-marker-runtime "broker is still running"
set_state 1 1 1 0 1 1
run_helper ready recover-marker-runtime >/dev/null
run_helper ready verify-clean >/dev/null
set_state 1 1 1 0 1 1
expect_failure marker-invalid recover-marker-runtime "content failed validation"
[[ "$(cat "$STATE")" == "installed=1 runtime=1 broker=1 running=0 pid_receipt=1 leases=1 unknown=0" ]]
set_state 1 1 1 0 1 1
expect_failure marker-unsafe recover-marker-runtime "ownership or mode"
set_state 1 1 1 0 1 1
expect_failure marker-symlink recover-marker-runtime "unsafe lease directory"

set_state 1 1 1 1 0
expect_failure ready cleanup "broker is still running"
set_state 1 0 0 1 0
expect_failure ready verify-clean "broker is still running"
set_state 1 1 1 0 1 1
expect_failure inventory-fail recover-marker-runtime "UNKNOWN"
[[ "$(cat "$STATE")" == "installed=1 runtime=1 broker=1 running=0 pid_receipt=1 leases=1 unknown=0" ]]
expect_failure proc-fail recover-marker-runtime "UNKNOWN"
[[ "$(cat "$STATE")" == "installed=1 runtime=1 broker=1 running=0 pid_receipt=1 leases=1 unknown=0" ]]
expect_failure reused-pid recover-marker-runtime "recorded PID still exists"
[[ "$(cat "$STATE")" == "installed=1 runtime=1 broker=1 running=0 pid_receipt=1 leases=1 unknown=0" ]]
set_state 1 1 1 1 1
expect_failure proc-fail stop "UNKNOWN"
echo "NullGate device harness scenarios passed, including six additional final-review regressions"
