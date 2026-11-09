#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
kotlinc "$ROOT/sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/wearables/NativeWireProtocols.kt" \
  "$ROOT/tools/wearables/NativeProtocolChecks.kt" -include-runtime -d "$OUT/checks.jar"
java -jar "$OUT/checks.jar"
