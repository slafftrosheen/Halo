#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KOTLINC="${KOTLINC:-$(command -v kotlinc)}"
KOTLIN_HOME="${KOTLIN_HOME:-$(cd "$(dirname "$(readlink -f "$KOTLINC")")/.." && pwd)}"
COROUTINES_JAR="${COROUTINES_JAR:-$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
SRC="$ROOT/sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime"
"$KOTLINC" "$SRC/embodiment/DeviceDescriptor.kt" "$SRC/wearables/NativeWireProtocols.kt" \
  "$SRC/wearables/NativeWearableCommands.kt" "$SRC/wearables/NativeDeviceDriver.kt" \
  "$SRC/wearables/NativeWearableDriver.kt" "$SRC/wearables/frame/FrameStatusProtocol.kt" \
  "$SRC/wearables/frame/BrilliantFrameDriver.kt" "$ROOT/tools/wearables/FrameStatusChecks.kt" \
  -cp "$COROUTINES_JAR" -include-runtime -d "$TMP/checks.jar"
java -cp "$TMP/checks.jar:$COROUTINES_JAR" checks.FrameStatusChecksKt
