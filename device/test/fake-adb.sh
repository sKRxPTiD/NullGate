#!/usr/bin/env bash
set -euo pipefail

scenario="${FAKE_SCENARIO:-ready}"
if [[ "${1:-}" == get-serialno ]]; then
  [[ "$scenario" != disconnected ]] || exit 1
  echo PIXI_TEST_SERIAL
  exit 0
fi
[[ "${1:-}" == -s && "${2:-}" == PIXI_TEST_SERIAL ]] || {
  echo "fake-adb: request was not pinned to expected serial" >&2
  exit 64
}
shift 2
request="$*"

case "$request" in
  get-state)
    [[ "$scenario" != "disconnected" ]] || exit 1
    echo device
    ;;
  "shell id")
    if [[ "$scenario" == "nonroot" ]]; then
      echo 'uid=2000(shell) gid=2000(shell) groups=1003(graphics)'
    else
      echo 'uid=0(root) gid=0(root) groups=0(root)'
    fi
    ;;
  "shell getprop ro.product.device")
    if [[ "$scenario" == "wrong-device" ]]; then echo 'oriole'; else echo 'tokay'; fi
    ;;
  "shell getprop ro.build.version.release")
    if [[ "$scenario" == "wrong-android" ]]; then echo '15'; else echo '16'; fi
    ;;
  "shell getprop ro.lineage.version")
    if [[ "$scenario" != "not-lineage" ]]; then
      echo '23.2-20260920-NIGHTLY-tokay'
    fi
    ;;
  "shell id -Z")
    if [[ "$scenario" == "wrong-context" ]]; then
      echo 'u:r:shell:s0'
    else
      echo 'u:r:su:s0'
    fi
    ;;
  "shell pm path org.nullprotocol.nullgate")
    [[ "$scenario" != "missing-controller" ]] || exit 0
    echo 'package:/data/app/example/org.nullprotocol.nullgate/base.apk'
    ;;
  "shell getenforce")
    if [[ "$scenario" == "permissive" ]]; then echo Permissive; else echo Enforcing; fi
    ;;
  "shell if test -L /data/local/tmp/nullgate/broker.pid; then exit 2; elif test -e /data/local/tmp/nullgate/broker.pid; then cat /data/local/tmp/nullgate/broker.pid; else echo ABSENT; fi")
    if [[ "$scenario" == running || "$scenario" == stale-pid ]]; then echo 4242
    elif [[ "$scenario" == bad-pid ]]; then echo 0
    else echo ABSENT; fi
    ;;
  "shell tr '\0' '\n' </proc/4242/cmdline")
    if [[ "$scenario" == running ]]; then
      printf '%s\n' app_process org.nullprotocol.nullgate.broker.NullGateBrokerMain
    else
      echo another.process
    fi
    ;;
  "shell kill -0 4242")
    [[ "$scenario" == "running" ]]
    ;;
  "shell find /data/local/tmp/nullgate/leases -maxdepth 1 -type f -name '*.lease' 2>/dev/null | wc -l")
    echo 0
    ;;
  *)
    echo "fake-adb: unexpected request: $request" >&2
    exit 64
    ;;
esac
