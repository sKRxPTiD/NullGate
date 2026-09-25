#!/usr/bin/env bash
set -euo pipefail
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
OUT="$BASE_DIR/client/build-test"
rm -rf "$OUT"
mkdir -p "$OUT"
javac --release 8 -d "$OUT" \
  "$BASE_DIR/common/src/org/nullprotocol/nullgate/protocol/CapabilityPayload.java" \
  "$BASE_DIR/common/src/org/nullprotocol/nullgate/protocol/ExternalClientContract.java" \
  "$BASE_DIR/src/org/nullprotocol/nullgate/ClientRequestContract.java" \
  "$BASE_DIR/src/org/nullprotocol/nullgate/ExternalLeaseStatePolicy.java" \
  "$BASE_DIR/src/org/nullprotocol/nullgate/ExternalClientPolicy.java" \
  $(find "$BASE_DIR/client/test" -name '*.java' -print)
java -cp "$OUT" org.nullprotocol.nullgate.ExternalClientPolicyTest
java -cp "$OUT" org.nullprotocol.nullgate.ExternalLeaseStatePolicyTest
