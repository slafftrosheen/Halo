#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KOTLINC="${KOTLINC:-$(command -v kotlinc)}"
KOTLIN_HOME="${KOTLIN_HOME:-$(cd "$(dirname "$(readlink -f "$KOTLINC")")/.." && pwd)}"
COROUTINES_JAR="${COROUTINES_JAR:-$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar}"
[[ -f "$COROUTINES_JAR" ]] || { echo "Set COROUTINES_JAR to kotlinx-coroutines-core-jvm.jar" >&2; exit 2; }
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
SRC="$ROOT/sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/wearables"
"$KOTLINC" "$SRC/NativeWireProtocols.kt" "$SRC/NativeWearableCommands.kt" "$SRC/NativeDeviceDriver.kt" "$SRC/NativeWearableDriver.kt" \
  "$ROOT/tools/wearables/NativeSessionChecks.kt" -cp "$COROUTINES_JAR" -include-runtime -d "$TMP/checks.jar"
java -cp "$TMP/checks.jar:$COROUTINES_JAR" checks.NativeSessionChecksKt
