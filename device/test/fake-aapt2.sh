#!/usr/bin/env bash
set -euo pipefail
[[ "${1:-}" == dump && "${2:-}" == badging && -n "${3:-}" ]] || exit 64
package=org.nullprotocol.nullgate
[[ "$3" == *NullGate-test-client-debug.apk ]] && package=org.nullprotocol.nullgate.testclient
version=1
[[ "${FAKE_SCENARIO:-}" == wrong-local-package ]] && package=org.nullprotocol.impostor
[[ "${FAKE_SCENARIO:-}" == wrong-local-version ]] && version=999
printf "package: name='%s' versionCode='%s' versionName='0.1'\n" "$package" "$version"
