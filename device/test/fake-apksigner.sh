#!/usr/bin/env bash
set -euo pipefail
cert="$(tr -d '\r\n' < "${FAKE_CERT_FILE:?}")"
[[ "${FAKE_SCENARIO:-}" == wrong-signer ]] && cert="$(printf 'b%.0s' {1..64})"
echo "Signer #1 certificate SHA-256 digest: $cert"
