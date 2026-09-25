#!/usr/bin/env bash
set -euo pipefail

state_file="${FAKE_STATE_FILE:?}"
scenario="${FAKE_SCENARIO:-ready}"
cert_file="${FAKE_CERT_FILE:?}"
broker_file="${FAKE_BROKER_FILE:?}"
# Test-owned state file contains only these numeric flags.
installed=0 runtime=0 broker=0 running=0 pid_receipt=0 leases=0 unknown=0 test_client=0
[[ -f "$state_file" ]] && source "$state_file"

save() {
  printf 'installed=%s runtime=%s broker=%s running=%s pid_receipt=%s leases=%s unknown=%s test_client=%s\n'     "$installed" "$runtime" "$broker" "$running" "$pid_receipt" "$leases" "$unknown" "$test_client" > "$state_file"
}

if [[ "${1:-}" == get-serialno ]]; then
  [[ "$scenario" != disconnected ]] || exit 1
  echo PIXI_TEST_SERIAL
  exit 0
fi
[[ "${1:-}" == -s && "${2:-}" == PIXI_TEST_SERIAL ]] || {
  echo "fake-adb: serial was not pinned" >&2; exit 64;
}
shift 2

if [[ "${1:-}" == pull ]]; then
  [[ "$installed" == 1 || "$test_client" == 1 || "${2:-}" == */broker.log ]] || exit 1
  printf 'test artifact\n' > "$3"
  exit 0
fi
if [[ "${1:-}" == push ]]; then
  [[ "$runtime" == 1 ]] || exit 1
  broker=1; save; exit 0
fi
if [[ "${1:-}" == install ]]; then
  [[ "$scenario" != install-fail ]] || exit 1
  install_target="${3:-${2:-}}"
  if [[ "$install_target" == *NullGate-test-client-debug.apk ]]; then
    test_client=1
  else
    installed=1
  fi
  save; echo Success; exit 0
fi
if [[ "${1:-}" == uninstall ]]; then
  installed=0; save; echo Success; exit 0
fi

request="$*"
case "$request" in
  "shell pm list packages --user 0 org.nullprotocol.nullgate")
    if [[ "$installed" == 1 ]]; then echo package:org.nullprotocol.nullgate; fi ;;
  "shell pm list packages --user 0 org.nullprotocol.nullgate.testclient")
    if [[ "$test_client" == 1 ]]; then echo package:org.nullprotocol.nullgate.testclient; fi ;;
  "shell ps -A -o ARGS")
    [[ "$scenario" != inventory-fail ]] || exit 1
    echo ARGS
    if [[ "$running" == 1 ]]; then echo org.nullprotocol.nullgate.broker.NullGateBrokerMain; fi ;;
  "shell if test -d /proc/4242; then echo PRESENT; else echo ABSENT; fi")
    [[ "$scenario" != proc-fail ]] || exit 1
    if [[ "$running" == 1 || "$scenario" == reused-pid ]]; then echo PRESENT; else echo ABSENT; fi ;;
  get-state)
    [[ "$scenario" != disconnected ]] || exit 1; echo device ;;
  "shell getprop ro.product.device")
    [[ "$scenario" == wrong-device ]] && echo oriole || echo tokay ;;
  "shell getprop ro.build.version.release")
    [[ "$scenario" == wrong-android ]] && echo 15 || echo 16 ;;
  "shell getprop ro.lineage.version")
    [[ "$scenario" == not-lineage ]] || echo 23.2-20260920-NIGHTLY-tokay ;;
  "shell id")
    [[ "$scenario" == nonroot ]] && echo 'uid=2000(shell)' || echo 'uid=0(root) gid=0(root)' ;;
  "shell id -Z")
    [[ "$scenario" == wrong-context ]] && echo u:r:shell:s0 || echo u:r:su:s0 ;;
  "shell getenforce")
    [[ "$scenario" == permissive ]] && echo Permissive || echo Enforcing ;;
  "shell pm path org.nullprotocol.nullgate")
    [[ "$installed" == 1 ]] && echo package:/data/app/test/org.nullprotocol.nullgate/base.apk
    true ;;
  "shell pm path org.nullprotocol.nullgate.testclient")
    [[ "$test_client" == 1 ]] && echo package:/data/app/test/org.nullprotocol.nullgate.testclient/base.apk
    true ;;
  "shell if test -L /data/local/tmp/nullgate; then echo SYMLINK; elif test -d /data/local/tmp/nullgate; then stat -c 'DIR:%u:%a' /data/local/tmp/nullgate; elif test -e /data/local/tmp/nullgate; then echo OTHER; else echo ABSENT; fi")
    if [[ "$scenario" == runtime-symlink ]]; then echo SYMLINK
    elif [[ "$scenario" == unsafe-runtime ]]; then echo DIR:2000:777
    elif [[ "$runtime" == 1 ]]; then echo DIR:0:700
    else echo ABSENT; fi ;;
  "shell umask 077; mkdir /data/local/tmp/nullgate && chown 0:0 /data/local/tmp/nullgate && chmod 0700 /data/local/tmp/nullgate")
    [[ "$runtime" == 0 && "$scenario" != mkdir-fail ]] || exit 1
    runtime=1; save ;;
  "shell chown 0:0 /data/local/tmp/nullgate/NullGate-broker.jar && chmod 0400 /data/local/tmp/nullgate/NullGate-broker.jar")
    [[ "$broker" == 1 ]] ;;
  "shell sha256sum /data/local/tmp/nullgate/NullGate-broker.jar")
    if [[ "$scenario" == hash-mismatch ]]; then printf '%064d  file\n' 0
    else sha256sum "$broker_file"; fi ;;
  shell\ umask\ 077\;\ CLASSPATH=/data/local/tmp/nullgate/NullGate-broker.jar\ app_process\ /\ org.nullprotocol.nullgate.broker.NullGateBrokerMain\ *)
    [[ "$scenario" != launch-fail ]] || exit 1
    [[ "$scenario" != require-theme-mode || "$request" == *" SYSTEM_THEME_V1 "* ]] || exit 1
    [[ "$scenario" != reject-theme-mode || "$request" != *" SYSTEM_THEME_V1 "* ]] || exit 1
    [[ "$scenario" != require-colorblendr-mode || "$request" == *" COLORBLENDR_SHIZUKU_V1 "* ]] || exit 1
    running=1; pid_receipt=1; save ;;
  "shell if test -L /data/local/tmp/nullgate/broker.pid; then echo SYMLINK; elif test -f /data/local/tmp/nullgate/broker.pid; then cat /data/local/tmp/nullgate/broker.pid; elif test -e /data/local/tmp/nullgate/broker.pid; then echo OTHER; else echo ABSENT; fi")
    if [[ "$scenario" == pid-symlink ]]; then echo SYMLINK
    elif [[ "$scenario" == bad-pid ]]; then echo 0
    elif [[ "$pid_receipt" == 1 ]]; then echo 4242
    else echo ABSENT; fi ;;
  "shell if test -L /data/local/tmp/nullgate/broker.pid; then echo SYMLINK; elif test -f /data/local/tmp/nullgate/broker.pid; then stat -c 'FILE:%u:%a' /data/local/tmp/nullgate/broker.pid; else echo OTHER; fi")
    [[ "$pid_receipt" == 1 ]] && echo FILE:0:600 || echo OTHER ;;
  "shell rm -f /data/local/tmp/nullgate/broker.pid")
    pid_receipt=0; save ;;
  "shell tr '\0' '\n' </proc/4242/cmdline")
    [[ "$running" == 1 && "$scenario" != stale-pid ]] || exit 1
    printf '%s\n' app_process org.nullprotocol.nullgate.broker.NullGateBrokerMain ;;
  "shell sed -n 's/^Uid:[[:space:]]*//p' /proc/4242/status")
    [[ "$running" == 1 ]] || exit 1
    [[ "$scenario" == wrong-process-uid ]] && printf '2000\t2000\t2000\t2000\n'       || printf '0\t0\t0\t0\n' ;;
  "shell kill -TERM 4242")
    [[ "$running" == 1 && "$scenario" != term-fail ]] || exit 1
    running=0
    [[ "$scenario" == stale-receipt ]] || pid_receipt=0
    save ;;
  "shell test -d /proc/4242")
    [[ "$running" == 1 ]] ;;
  "shell if test -L /data/local/tmp/nullgate/leases; then echo SYMLINK; elif test -d /data/local/tmp/nullgate/leases; then find /data/local/tmp/nullgate/leases -mindepth 1 -maxdepth 1 -print; elif test -e /data/local/tmp/nullgate/leases; then echo OTHER; else echo ABSENT; fi")
    if [[ "$scenario" == lease-symlink ]]; then echo SYMLINK
    elif [[ "$leases" == 1 ]]; then echo /data/local/tmp/nullgate/leases/test.lease
    else echo ABSENT; fi ;;
  "shell if test -L /data/local/tmp/nullgate/leases; then echo SYMLINK; elif test -d /data/local/tmp/nullgate/leases; then find /data/local/tmp/nullgate/leases -mindepth 1 -maxdepth 1 -printf '%f:%y\\n'; elif test -e /data/local/tmp/nullgate/leases; then echo OTHER; else echo ABSENT; fi")
    if [[ "$scenario" == marker-symlink ]]; then echo SYMLINK
    elif [[ "$leases" == 1 ]]; then echo lease_0000000001.lease:f
    else echo ABSENT; fi ;;
  "shell stat -c 'FILE:%u:%a' /data/local/tmp/nullgate/leases/lease_0000000001.lease")
    [[ "$scenario" != marker-unsafe ]] && echo FILE:0:600 || echo FILE:2000:777 ;;
  "shell cat /data/local/tmp/nullgate/leases/lease_0000000001.lease")
    if [[ "$scenario" == marker-invalid ]]; then echo invalid-marker
    else printf 'lease=lease_0000000001\nexpiresElapsed=999999\n'; fi ;;
  "shell rm -f /data/local/tmp/nullgate/leases/lease_0000000001.lease")
    leases=0; save ;;
  "shell if test -L /data/local/tmp/nullgate/broker.log; then echo SYMLINK; elif test -f /data/local/tmp/nullgate/broker.log; then stat -c 'FILE:%u:%a' /data/local/tmp/nullgate/broker.log; elif test -e /data/local/tmp/nullgate/broker.log; then echo OTHER; else echo ABSENT; fi")
    if [[ "$scenario" == log-symlink ]]; then echo SYMLINK
    elif [[ "$broker" == 1 ]]; then echo FILE:0:600
    else echo ABSENT; fi ;;
  "shell find /data/local/tmp/nullgate -mindepth 1 -maxdepth 1 -printf '%f\n'")
    [[ "$runtime" == 1 ]] || exit 1
    [[ "$broker" == 1 ]] && printf '%s\n' NullGate-broker.jar broker.log
    [[ "$leases" == 1 ]] && echo leases
    [[ "$unknown" == 1 ]] && echo unexpected
    true ;;
  "shell test ! -e /data/local/tmp/nullgate/leases || rmdir /data/local/tmp/nullgate/leases")
    [[ "$leases" == 0 ]] ;;
  "shell rm -f /data/local/tmp/nullgate/NullGate-broker.jar /data/local/tmp/nullgate/broker.log && rmdir /data/local/tmp/nullgate")
    [[ "$scenario" != cleanup-fail && "$running" == 0 && "$leases" == 0 && "$unknown" == 0 ]] || exit 1
    runtime=0; broker=0; save ;;
  *) echo "fake-adb: unexpected request: $request" >&2; exit 64 ;;
esac
