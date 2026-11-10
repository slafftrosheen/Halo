#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KOTLINC="${KOTLINC:-$(command -v kotlinc)}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
"$KOTLINC" "$ROOT/sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/ink/InkInputContract.kt" \
  "$ROOT/tools/ink/InkInputContractChecks.kt" -include-runtime -d "$TMP/checks.jar"
java -jar "$TMP/checks.jar"
