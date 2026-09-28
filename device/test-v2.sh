#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
HELPER="$BASE_DIR/device/nullgate-device-v2.sh"
FAKE_ADB="$BASE_DIR/device/test/fake-adb-v2.sh"
FAKE_SIGNER="$BASE_DIR/device/test/fake-apksigner.sh"
FAKE_AAPT2="$BASE_DIR/device/test/fake-aapt2.sh"
FAKE_RECORD="$BASE_DIR/device/test/external-client-record.xml"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf -- "$TEST_DIR"' EXIT
STATE="$TEST_DIR/state"

set_state() {
  printf 'installed=%s runtime=%s broker=%s running=%s pid_receipt=%s leases=%s unknown=%s test_client=%s theme_snapshot=%s theme_client=%s\n'     "${1:-0}" "${2:-0}" "${3:-0}" "${4:-0}" "${5:-0}" "${6:-0}" "${7:-0}" "${8:-0}" "${9:-0}" "${10:-0}" > "$STATE"
}

run_helper() {
  local scenario="$1" action="$2"
  FAKE_STATE_FILE="$STATE" FAKE_SCENARIO="$scenario"     FAKE_CERT_FILE="$BASE_DIR/dist/controller-cert-sha256.txt"     FAKE_BROKER_FILE="$BASE_DIR/dist/NullGate-broker.jar"     NULLGATE_SERIAL=PIXI_TEST_SERIAL NULLGATE_MUTATION_TOKEN=NULLGATE_MARKER_TEST_V1     NULLGATE_LOG_DIR="$TEST_DIR/logs"     ADB_BIN="$FAKE_ADB" APKSIGNER_BIN="$FAKE_SIGNER" AAPT2_BIN="$FAKE_AAPT2" "$HELPER" "$action"
}

run_theme_helper() {
  local scenario="$1" action="$2"
  FAKE_STATE_FILE="$STATE" FAKE_SCENARIO="$scenario" \
    FAKE_CERT_FILE="$BASE_DIR/dist/controller-cert-sha256.txt" \
    FAKE_BROKER_FILE="$BASE_DIR/dist/NullGate-broker.jar" \
    NULLGATE_SERIAL=PIXI_TEST_SERIAL NULLGATE_MUTATION_TOKEN=NULLGATE_SYSTEM_THEME_V1 \
    NULLGATE_LOG_DIR="$TEST_DIR/logs" ADB_BIN="$FAKE_ADB" APKSIGNER_BIN="$FAKE_SIGNER" AAPT2_BIN="$FAKE_AAPT2" \
    "$HELPER" "$action"
}

run_colorblendr_helper() {
  local scenario="$1" action="$2"
  FAKE_STATE_FILE="$STATE" FAKE_SCENARIO="$scenario" \
    FAKE_CERT_FILE="$BASE_DIR/dist/controller-cert-sha256.txt" \
    FAKE_BROKER_FILE="$BASE_DIR/dist/NullGate-broker.jar" \
    NULLGATE_SERIAL=PIXI_TEST_SERIAL NULLGATE_MUTATION_TOKEN=NULLGATE_COLORBLENDR_SHIZUKU_V1 \
    NULLGATE_LOG_DIR="$TEST_DIR/logs" ADB_BIN="$FAKE_ADB" APKSIGNER_BIN="$FAKE_SIGNER" AAPT2_BIN="$FAKE_AAPT2" \
    "$HELPER" "$action"
}

run_test_client_helper() {
  local scenario="$1" action="$2"
  FAKE_STATE_FILE="$STATE" FAKE_SCENARIO="$scenario" \
    FAKE_CERT_FILE="$BASE_DIR/dist/controller-cert-sha256.txt" \
    FAKE_BROKER_FILE="$BASE_DIR/dist/NullGate-broker.jar" \
    NULLGATE_SERIAL=PIXI_TEST_SERIAL NULLGATE_MUTATION_TOKEN=NULLGATE_TEST_CLIENT_V1 \
    NULLGATE_LOG_DIR="$TEST_DIR/logs" ADB_BIN="$FAKE_ADB" APKSIGNER_BIN="$FAKE_SIGNER" AAPT2_BIN="$FAKE_AAPT2" \
    "$HELPER" "$action"
}

run_theme_client_helper() {
  local scenario="$1" action="$2"
  FAKE_STATE_FILE="$STATE" FAKE_SCENARIO="$scenario" \
    FAKE_CERT_FILE="$BASE_DIR/dist/controller-cert-sha256.txt" \
    FAKE_BROKER_FILE="$BASE_DIR/dist/NullGate-broker.jar" \
    NULLGATE_SERIAL=PIXI_TEST_SERIAL NULLGATE_MUTATION_TOKEN=NULLGATE_THEME_CLIENT_V1 \
    NULLGATE_LOG_DIR="$TEST_DIR/logs" ADB_BIN="$FAKE_ADB" APKSIGNER_BIN="$FAKE_SIGNER" AAPT2_BIN="$FAKE_AAPT2" \
    "$HELPER" "$action"
}

run_controller_record_recovery() {
  local scenario="$1" record_hash theme_hash
  record_hash="$(sha256sum "$FAKE_RECORD" | awk '{print $1}')"
  theme_hash="$(printf '%s' 'SIMULATED_RESTORED_THEME' | sha256sum | awk '{print $1}')"
  FAKE_STATE_FILE="$STATE" FAKE_SCENARIO="$scenario" \
    FAKE_CERT_FILE="$BASE_DIR/dist/controller-cert-sha256.txt" \
    FAKE_BROKER_FILE="$BASE_DIR/dist/NullGate-broker.jar" FAKE_RECORD_FILE="$FAKE_RECORD" \
    NULLGATE_SERIAL=PIXI_TEST_SERIAL NULLGATE_MUTATION_TOKEN=NULLGATE_CONTROLLER_RECORD_RECOVERY_V1 \
    NULLGATE_EXPECTED_RECORD_SHA256="$record_hash" NULLGATE_EXPECTED_THEME_SHA256="$theme_hash" \
    NULLGATE_EXPECTED_LEASE_ID=a2d90ac5-e36c-4f5b-b26d-44fcd5c0f578 \
    NULLGATE_EXPECTED_CLIENT_PACKAGE=com.drdisagree.colorblendr \
    NULLGATE_LOG_DIR="$TEST_DIR/logs" ADB_BIN="$FAKE_ADB" APKSIGNER_BIN="$FAKE_SIGNER" AAPT2_BIN="$FAKE_AAPT2" \
    "$HELPER" recover-controller-record
}

expect_test_client_failure() {
  local scenario="$1" expected="$2" output
  if output="$(run_test_client_helper "$scenario" install-test-client 2>&1)"; then
    echo "expected test-client installation failure: $scenario" >&2; exit 1
  fi
  [[ "$output" == *"$expected"* ]] || {
    echo "unexpected test-client failure for $scenario: $output" >&2; exit 1;
  }
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
if output="$(run_helper ready recover-controller-record 2>&1)"; then
  echo "marker token enabled controller-record recovery" >&2; exit 1
fi
[[ "$output" == *"controller-record recovery acknowledgement required"* ]]

set_state 1 0 0 0 0 0 1
run_controller_record_recovery controller-record >/dev/null
[[ "$(cat "$STATE")" == *"unknown=0"* ]]
[[ "$(find "$TEST_DIR/logs" -maxdepth 1 -type f -name 'controller-record-*.xml' | wc -l)" == 1 ]]

for scenario in controller-record-theme-mismatch controller-record-symlink controller-record-unexpired; do
  set_state 1 0 0 0 0 0 1
  before="$(cat "$STATE")"
  if output="$(run_controller_record_recovery "$scenario" 2>&1)"; then
    echo "controller-record recovery accepted $scenario" >&2; exit 1
  fi
  [[ "$(cat "$STATE")" == "$before" ]] || {
    echo "rejected controller-record recovery changed device state: $scenario" >&2; exit 1;
  }
done
set_state 1 1 0 0 0 0 1
before="$(cat "$STATE")"
if output="$(run_controller_record_recovery controller-record 2>&1)"; then
  echo "controller-record recovery accepted an existing runtime" >&2; exit 1
fi
[[ "$(cat "$STATE")" == "$before" ]]
set_state 1 0 0 1 0 0 1
before="$(cat "$STATE")"
if output="$(run_controller_record_recovery controller-record 2>&1)"; then
  echo "controller-record recovery accepted a live broker" >&2; exit 1
fi
[[ "$(cat "$STATE")" == "$before" ]]

set_state 1
if output="$(run_helper ready install-test-client 2>&1)"; then
  echo "marker token enabled test-client installation" >&2; exit 1
fi
[[ "$output" == *"test-client acknowledgement required"* ]]
run_test_client_helper ready install-test-client >/dev/null
[[ "$(cat "$STATE")" == "installed=1 runtime=0 broker=0 running=0 pid_receipt=0 leases=0 unknown=0 test_client=1 theme_snapshot=0 theme_client=0" ]]
set_state 1 1 1
expect_test_client_failure ready "requires an absent broker runtime"
set_state
expect_test_client_failure ready "controller is not installed"

set_state 1
if output="$(run_helper ready install-theme-client 2>&1)"; then
  echo "marker token enabled theme-client installation" >&2; exit 1
fi
[[ "$output" == *"theme-client acknowledgement required"* ]]
run_theme_client_helper ready install-theme-client >/dev/null
[[ "$(cat "$STATE")" == "installed=1 runtime=0 broker=0 running=0 pid_receipt=0 leases=0 unknown=0 test_client=0 theme_snapshot=0 theme_client=1" ]]
run_test_client_helper ready install-test-client >/dev/null
[[ "$(cat "$STATE")" == *"test_client=1 theme_snapshot=0 theme_client=1" ]]
run_theme_client_helper ready install-theme-client >/dev/null
[[ "$(cat "$STATE")" == *"test_client=1 theme_snapshot=0 theme_client=1" ]]
set_state 1 1 1
if output="$(run_theme_client_helper ready install-theme-client 2>&1)"; then
  echo "theme-client installation accepted an existing runtime" >&2; exit 1
fi
[[ "$output" == *"requires an absent broker runtime"* ]]
set_state 1
if output="$(run_theme_client_helper old-controller install-theme-client 2>&1)"; then
  echo "theme-client installation accepted the old controller" >&2; exit 1
fi
[[ "$output" == *"requires the reviewed controller version 3"* ]]
set_state
if output="$(run_theme_client_helper ready install-theme-client 2>&1)"; then
  echo "theme-client installation accepted a missing controller" >&2; exit 1
fi
[[ "$output" == *"controller is not installed"* ]]

for scenario in wrong-local-package wrong-local-version wrong-signer nonroot inventory-fail install-fail; do
  set_state 1
  before="$(cat "$STATE")"
  if output="$(run_theme_client_helper "$scenario" install-theme-client 2>&1)"; then
    echo "theme-client installation accepted $scenario" >&2; exit 1
  fi
  [[ "$(cat "$STATE")" == "$before" ]] || {
    echo "rejected theme-client installation changed device state: $scenario" >&2; exit 1;
  }
done
set_state 1 0 0 1
before="$(cat "$STATE")"
if output="$(run_theme_client_helper ready install-theme-client 2>&1)"; then
  echo "theme-client installation accepted a broker outside the managed runtime" >&2; exit 1
fi
[[ "$output" == *"broker is still running"* && "$(cat "$STATE")" == "$before" ]]
set_state 1
if output="$(run_test_client_helper ready install-theme-client 2>&1)"; then
  echo "test-client token enabled theme-client installation" >&2; exit 1
fi
[[ "$output" == *"theme-client acknowledgement required"* ]]

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

set_state
expect_failure wrong-signer install-controller "local controller signer does not match"
[[ "$(cat "$STATE")" == *"installed=0"* ]]
set_state
expect_failure wrong-local-package install-controller "package identity or version"
[[ "$(cat "$STATE")" == *"installed=0"* ]]
set_state
expect_failure wrong-local-version install-controller "package identity or version"
[[ "$(cat "$STATE")" == *"installed=0"* ]]
set_state 1 1 1
expect_failure ready install-controller "requires an absent broker runtime"

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
[[ "$(cat "$STATE")" == "installed=1 runtime=1 broker=1 running=0 pid_receipt=0 leases=0 unknown=0 test_client=0 theme_snapshot=0 theme_client=0" ]]
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
[[ "$(cat "$STATE")" == "installed=1 runtime=1 broker=1 running=0 pid_receipt=1 leases=1 unknown=0 test_client=0 theme_snapshot=0 theme_client=0" ]]
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
[[ "$(cat "$STATE")" == "installed=1 runtime=1 broker=1 running=0 pid_receipt=1 leases=1 unknown=0 test_client=0 theme_snapshot=0 theme_client=0" ]]
expect_failure proc-fail recover-marker-runtime "UNKNOWN"
[[ "$(cat "$STATE")" == "installed=1 runtime=1 broker=1 running=0 pid_receipt=1 leases=1 unknown=0 test_client=0 theme_snapshot=0 theme_client=0" ]]

for scenario in theme-hash-command-fail theme-hash-empty; do
  set_state 1 1 1 0 0 0 0 0 1
  before="$(cat "$STATE")"
  if output="$(run_theme_helper "$scenario" recover-system-theme-runtime 2>&1)"; then
    echo "theme recovery accepted unavailable hash: $scenario" >&2; exit 1
  fi
  [[ "$output" == *"theme recovery receipt"* ]]
  [[ "$(cat "$STATE")" == "$before" ]]
done
for scenario in log-hash-command-fail log-hash-empty; do
  set_state 1 1 1
  before="$(cat "$STATE")"
  expect_failure "$scenario" cleanup "broker log"
  [[ "$(cat "$STATE")" == "$before" ]]
done
set_state 1 1 1 0 0 0 0 0 1
if output="$(run_theme_helper theme-recovery-broker-before-restore recover-system-theme-runtime 2>&1)"; then
  echo "theme recovery restored while a new broker was live" >&2; exit 1
fi
[[ "$output" == *"broker is still running"* ]]
[[ "$(cat "$STATE")" == *"runtime=1"*"running=1"*"theme_snapshot=1"* ]]
set_state 1 1 1 0 0 0 0 0 1
if output="$(run_theme_helper theme-recovery-broker-appeared recover-system-theme-runtime 2>&1)"; then
  echo "theme recovery accepted a newly live broker" >&2; exit 1
fi
[[ "$output" == *"broker is still running"* ]]
[[ "$(cat "$STATE")" == *"runtime=1"*"running=1"*"theme_snapshot=1"* ]]
set_state 1 1 1
before="$(cat "$STATE")"
stall_started=$SECONDS
expect_failure inventory-stall cleanup "timed out; state is UNKNOWN"
[[ "$(cat "$STATE")" == "$before" ]]
[[ "$((SECONDS - stall_started))" -lt 25 ]] || {
  echo "stalled inventory was not bounded" >&2; exit 1;
}
set_state 1 1 1 0 0 0 0 0 1
before="$(cat "$STATE")"
if output="$(run_theme_helper theme-recovery-transfer-corrupt recover-system-theme-runtime 2>&1)"; then
  echo "corrupted theme receipt transfer was accepted" >&2; exit 1
fi
[[ "$output" == *"transfer hash mismatch"* ]]
[[ "$(cat "$STATE")" == "$before" ]]
set_state 1 1 1 0 0 0 0 0 1
if output="$(run_theme_helper theme-recovery-receipt-changed recover-system-theme-runtime 2>&1)"; then
  echo "changed theme receipt was removed" >&2; exit 1
fi
[[ "$output" == *"receipt changed before removal"* ]]
[[ "$(cat "$STATE")" == *"runtime=1"*"theme_snapshot=1"* ]]
set_state 1 1 1
before="$(cat "$STATE")"
expect_failure log-corrupt cleanup "broker log archive hash mismatch"
[[ "$(cat "$STATE")" == "$before" ]] || {
  echo "corrupted log archive permitted runtime cleanup" >&2; exit 1;
}
set_state 1 1 1 0 0 0 0 0 1
if output="$(PATH="$BASE_DIR/device/test/archive-copy:$PATH" NULLGATE_TEST_FAIL_ARCHIVE=1 run_theme_helper theme-recovery recover-system-theme-runtime 2>&1)"; then
  echo "failed theme recovery archive write was accepted" >&2; exit 1
fi
[[ "$output" == *"archive could not be written"* ]]
[[ "$(cat "$STATE")" == *"runtime=1"*"theme_snapshot=1"* ]]
set_state 1 1 1 0 0 0 0 0 1
if output="$(PATH="$BASE_DIR/device/test/archive-copy:$PATH" NULLGATE_TEST_CORRUPT_ARCHIVE=1 run_theme_helper theme-recovery recover-system-theme-runtime 2>&1)"; then
  echo "corrupted theme recovery archive was accepted" >&2; exit 1
fi
[[ "$output" == *"archive failed hash verification"* ]]
[[ "$(cat "$STATE")" == *"runtime=1"*"theme_snapshot=1"* ]]
set_state 1 1 1 0 0 0 0 0 1
archive_count_before="$(find "$TEST_DIR/logs" -maxdepth 1 -type f -name 'theme-recovery-*.snapshot' | wc -l)"
run_theme_helper theme-recovery recover-system-theme-runtime >/dev/null
run_theme_helper ready verify-clean >/dev/null
set_state 1 1 1 0 0 0 0 0 1
run_theme_helper theme-recovery-null recover-system-theme-runtime >/dev/null
run_theme_helper ready verify-clean >/dev/null
archive_count_after="$(find "$TEST_DIR/logs" -maxdepth 1 -type f -name 'theme-recovery-*.snapshot' | wc -l)"
[[ "$archive_count_after" -eq "$((archive_count_before + 2))" ]] || {
  echo "successive theme recoveries overwrote archived evidence" >&2; exit 1;
}
set_state 1 1 1 0 1 1 0 0 1
run_theme_helper theme-recovery-with-lease recover-system-theme-runtime >/dev/null
run_theme_helper ready verify-clean >/dev/null
[[ "$(cat "$STATE")" == "installed=1 runtime=0 broker=0 running=0 pid_receipt=0 leases=0 unknown=0 test_client=0 theme_snapshot=0 theme_client=0" ]] || {
  echo "interrupted theme recovery did not restore and remove stale lease evidence" >&2; exit 1;
}
for scenario in theme-recovery-marker-invalid theme-recovery-marker-unsafe theme-recovery-lease-symlink; do
  set_state 1 1 1 0 1 1 0 0 1
  before="$(cat "$STATE")"
  if output="$(run_theme_helper "$scenario" recover-system-theme-runtime 2>&1)"; then
    echo "theme recovery accepted unsafe lease evidence: $scenario" >&2; exit 1
  fi
  [[ "$(cat "$STATE")" == "$before" ]] || {
    echo "rejected lease evidence changed device state: $scenario" >&2; exit 1;
  }
done
set_state 1 1 1 0 1 1 0 0 1
if output="$(run_theme_helper theme-recovery-marker-changed recover-system-theme-runtime 2>&1)"; then
  echo "theme recovery removed a changed lease marker" >&2; exit 1
fi
[[ "$output" == *"lease marker changed before removal"* ]]
[[ "$(cat "$STATE")" == *"pid_receipt=1"*"leases=1"*"theme_snapshot=1"* ]] || {
  echo "changed lease marker failure did not preserve recovery evidence" >&2; exit 1;
}
set_state 1 1 1 0 1 0 0 0 1
run_theme_helper theme-recovery recover-system-theme-runtime >/dev/null
run_theme_helper ready verify-clean >/dev/null
set_state 1 1 1 0 1 0 0 0 1
if output="$(run_theme_helper reused-pid recover-system-theme-runtime 2>&1)"; then
  echo "theme recovery accepted a reused recorded PID" >&2; exit 1
fi
[[ "$output" == *"recorded PID still exists"* ]]
[[ "$(cat "$STATE")" == *"pid_receipt=1"*"theme_snapshot=1"* ]]
set_state 1 1 1 0 1 0 0 0 1
if output="$(run_theme_helper pid-symlink recover-system-theme-runtime 2>&1)"; then
  echo "theme recovery accepted a symlink PID receipt" >&2; exit 1
fi
[[ "$output" == *"unsafe PID receipt"* ]]
[[ "$(cat "$STATE")" == *"pid_receipt=1"*"theme_snapshot=1"* ]]
set_state 1 1 1 0 1 0 1 0 1
if output="$(run_theme_helper theme-recovery recover-system-theme-runtime 2>&1)"; then
  echo "theme recovery accepted an unexpected runtime entry" >&2; exit 1
fi
[[ "$output" == *"unexpected runtime entry"* ]]
[[ "$(cat "$STATE")" == *"pid_receipt=1"*"unknown=1"*"theme_snapshot=1"* ]]
set_state 1 1 1 0 0 0 0 0 1
if output="$(run_theme_helper theme-recovery-readback-fail recover-system-theme-runtime 2>&1)"; then
  echo "mismatched theme recovery readback was accepted" >&2; exit 1
fi
[[ "$output" == *"was not exact"* ]]
[[ "$(cat "$STATE")" == *"theme_snapshot=1"* ]]
set_state 1 1 1 0 0 0 0 0 1
if output="$(run_theme_helper theme-recovery-write-fail recover-system-theme-runtime 2>&1)"; then
  echo "failed theme restoration was accepted" >&2; exit 1
fi
[[ "$output" == *"restoration command failed"* ]]
[[ "$(cat "$STATE")" == *"theme_snapshot=1"* ]]
set_state 1 1 1 0 0 0 0 0 1
if output="$(run_helper theme-recovery recover-system-theme-runtime 2>&1)"; then
  echo "marker token enabled theme recovery" >&2; exit 1
fi
[[ "$output" == *"system-theme acknowledgement required"* ]]
set_state 1 1 1 0 0 0 0 0 1
if output="$(run_theme_helper theme-recovery-unsafe recover-system-theme-runtime 2>&1)"; then
  echo "unsafe theme receipt was accepted" >&2; exit 1
fi
[[ "$output" == *"absent or unsafe"* ]]
set_state 1 1 1 0 1 1
expect_failure reused-pid recover-marker-runtime "recorded PID still exists"
[[ "$(cat "$STATE")" == "installed=1 runtime=1 broker=1 running=0 pid_receipt=1 leases=1 unknown=0 test_client=0 theme_snapshot=0 theme_client=0" ]]
set_state 1 1 1 1 1
expect_failure proc-fail stop "UNKNOWN"
echo "NullGate device harness scenarios passed, including locked installs, recovery preservation and review regressions"
