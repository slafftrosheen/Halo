#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KOTLINC="${KOTLINC:-$(command -v kotlinc)}"
KOTLIN_HOME="${KOTLIN_HOME:-$(cd "$(dirname "$(readlink -f "$KOTLINC")")/.." && pwd)}"
COROUTINES_JAR="${COROUTINES_JAR:-$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
SRC="$ROOT/sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/wearables/media"
MEDIA="$ROOT/drivers/heycyan/src/main/java/com/halo/drivers/heycyan/media"
WIFI="$ROOT/drivers/heycyan/src/main/java/com/halo/drivers/heycyan/wifi"
"$KOTLINC" "$SRC"/*.kt "$MEDIA"/{HeyCyanMedia,HeyCyanMediaManifest,HeyCyanMediaConfigParser,MediaItem,MediaType,MediaDownloadModels}.kt \
  "$MEDIA"/repository/*.kt "$MEDIA/sync/MediaDownloadStrategy.kt" \
  "$WIFI"/{DefaultHeyCyanMediaApi,HeyCyanMediaApi,HeyCyanWifiConnection,HeyCyanIpDiscovery}.kt \
  "$ROOT/tools/wearables/DeviceMediaChecks.kt" -cp "$COROUTINES_JAR" -include-runtime -d "$TMP/checks.jar"
java -cp "$TMP/checks.jar:$COROUTINES_JAR" checks.DeviceMediaChecksKt
