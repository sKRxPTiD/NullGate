#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
OUT_DIR="$BASE_DIR/theme-client/build-test"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
mapfile -d '' -t test_sources < <(find "$BASE_DIR/theme-client/test" -name '*.java' -print0)
javac --release 8 -d "$OUT_DIR" \
  "$BASE_DIR/theme-client/src/org/nullprotocol/nullgate/themeclient/ThemeClientStatePolicy.java" \
  "${test_sources[@]}"
java -cp "$OUT_DIR" org.nullprotocol.nullgate.themeclient.ThemeClientStatePolicyTest
