#!/usr/bin/env bash
# Runs the instrumentation suite on the explicit lab device.
#
# This is the hardware-attached equivalent of the hermetic emulator layer. It stays opt-in: the
# normal PR gate never waits for a glasses phone.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

hil_require_tools
hil_prepare_dirs

SERIAL="$(hil_resolve_serial)"
hil_info "running instrumentation on $SERIAL"

hil_record_stage "instrumentation.start" "serial=$SERIAL commit=$(git rev-parse HEAD)"

# Pin every adb and Gradle-driven device interaction to the chosen serial; a stray emulator must
# never receive the test run.
export ANDROID_SERIAL="$SERIAL"
set +e
"$HIL_ROOT/tools/ci/run_instrumentation_with_retry.sh" "$@"
status=$?
set -e

if [ "$status" -eq 0 ]; then
  hil_record_stage "instrumentation.passed"
else
  hil_record_stage "instrumentation.failed" "status=$status"
fi

hil_info "results: app/build/reports/androidTests/connected/"
exit "$status"
