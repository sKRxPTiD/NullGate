#!/usr/bin/env bash
set -euo pipefail

BROKER_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
TEST_OUT="$BROKER_DIR/build-test"
rm -rf "$TEST_OUT"
mkdir -p "$TEST_OUT"
mapfile -d '' -t common_sources < <(find "$BROKER_DIR/../common/src" -name '*.java' -print0)
mapfile -d '' -t broker_sources < <(find "$BROKER_DIR/src" "$BROKER_DIR/test" -name '*.java' -print0)
javac --release 8 -d "$TEST_OUT" "${common_sources[@]}" "${broker_sources[@]}"
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.BrokerEngineTest
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.BrokerProtocolTest
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.VerifiedCallerResolverTest
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.TargetGateTest
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.AdapterRegistryTest
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.AdapterLifecycleTest
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.EphemeralMarkerRecordTest
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.ShizukuSessionAdapterTest
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.ShizukuBrokerIntegrationTest
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.SystemThemeSeedAdapterTest
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.BoundedInputTest
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.BoundedProcessRunnerTest
java -cp "$TEST_OUT" org.nullprotocol.nullgate.broker.SecurityRegressionTest
