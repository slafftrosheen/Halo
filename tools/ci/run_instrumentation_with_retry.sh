#!/usr/bin/env bash
# Runs connected instrumentation with at most ONE retry, and only for infrastructure failures.
#
# A retry must never be a way to hide a reproducible product failure. Before retrying we look for
# deterministic product evidence (failing JUnit results, assertion failures) and stop immediately
# when it is present. Retries are limited to symptoms of a broken harness:
#   instrumentation process disconnected, adb/emulator hiccups, install failures, timeouts.

set -uo pipefail

HIL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$HIL_ROOT"

MAX_INFRA_RETRIES="${MAX_INFRA_RETRIES:-1}"
ARTIFACT_DIR="${HALO_ARTIFACT_DIR:-build/ci}"
RESULT_DIR="app/build/outputs/androidTest-results"
LOG_DIR="$ARTIFACT_DIR"
mkdir -p "$LOG_DIR"

# Product failures: the tests ran and something real failed. Never retried.
product_failure_markers() {
  grep -qE \
    'AssertionError|ComparisonFailure|junit\.framework|There were failing tests|expected:<|org\.junit\.Assert|Test failed to run to completion' \
    "$1"
}

# Strongest signal: the result XML says a test failed or errored.
product_results_failed() {
  [ -d "$RESULT_DIR" ] || return 1
  grep -rqE 'failures="[1-9]|errors="[1-9]' "$RESULT_DIR" 2>/dev/null
}

# Infrastructure failures: the harness broke, not the product.
infrastructure_failure_markers() {
  grep -qiE \
    'instrumentation process is not running|Process crashed|INSTRUMENTATION_FAILED|Unable to find instrumentation|no devices/emulators found|device offline|device unauthorized|adb: device|Failed to install|ShellCommandUnresponsiveException|Timeout waiting for|Instrumentation run failed due to' \
    "$1"
}

attempt=0
while :; do
  attempt=$((attempt + 1))
  log="$LOG_DIR/instrumentation-attempt-$attempt.log"
  echo "[ci] instrumentation attempt $attempt -> $log"

  set +e
  ./gradlew :app:connectedDebugAndroidTest --console=plain "$@" 2>&1 | tee "$log"
  status="${PIPESTATUS[0]}"
  set -e

  if [ "$status" -eq 0 ]; then
    echo "[ci] instrumentation passed on attempt $attempt"
    exit 0
  fi

  if product_failure_markers "$log" || product_results_failed; then
    echo "::error::Instrumentation failed with product-level test failures; not retrying."
    exit "$status"
  fi

  if ! infrastructure_failure_markers "$log"; then
    echo "::error::Instrumentation failed for an unrecognized reason; not retrying."
    exit "$status"
  fi

  if [ "$attempt" -gt "$MAX_INFRA_RETRIES" ]; then
    echo "::error::Instrumentation failed after $attempt attempt(s) for infrastructure reasons."
    exit "$status"
  fi

  echo "::warning::Infrastructure failure detected (no product evidence). Retrying once."
  adb kill-server >/dev/null 2>&1 || true
  adb start-server >/dev/null 2>&1 || true
  sleep 5
done
