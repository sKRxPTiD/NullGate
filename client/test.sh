#!/usr/bin/env bash
set -euo pipefail
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
OUT="$BASE_DIR/client/build-test"
rm -rf "$OUT"
mkdir -p "$OUT"
mapfile -d '' -t test_sources < <(find "$BASE_DIR/client/test" -name '*.java' -print0)
javac --release 8 -d "$OUT" \
  "$BASE_DIR/common/src/org/nullprotocol/nullgate/protocol/CapabilityPayload.java" \
  "$BASE_DIR/common/src/org/nullprotocol/nullgate/protocol/ExternalClientContract.java" \
  "$BASE_DIR/src/org/nullprotocol/nullgate/ClientRequestContract.java" \
  "$BASE_DIR/src/org/nullprotocol/nullgate/ExternalActivityOperation.java" \
  "$BASE_DIR/src/org/nullprotocol/nullgate/ExternalLeaseStatePolicy.java" \
  "$BASE_DIR/src/org/nullprotocol/nullgate/ExternalRequestRatePolicy.java" \
  "$BASE_DIR/src/org/nullprotocol/nullgate/ExternalClientRegistry.java" \
  "$BASE_DIR/src/org/nullprotocol/nullgate/ExternalClientPolicy.java" \
  "${test_sources[@]}"
java -cp "$OUT" org.nullprotocol.nullgate.ExternalClientPolicyTest
java -cp "$OUT" org.nullprotocol.nullgate.ExternalLeaseStatePolicyTest
java -cp "$OUT" org.nullprotocol.nullgate.ExternalActivityOperationTest
java -cp "$OUT" org.nullprotocol.nullgate.ExternalRequestRatePolicyTest
