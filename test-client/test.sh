#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
OUT_DIR="$BASE_DIR/test-client/build-test"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
javac --release 8 -d "$OUT_DIR" \
  "$BASE_DIR/common/src/org/nullprotocol/nullgate/protocol/ExternalClientContract.java" \
  "$BASE_DIR/test-client/src/org/nullprotocol/nullgate/testclient/TestClientResponsePolicy.java" \
  "$BASE_DIR/test-client/test/org/nullprotocol/nullgate/testclient/TestClientResponsePolicyTest.java"
java -cp "$OUT_DIR" org.nullprotocol.nullgate.testclient.TestClientResponsePolicyTest
