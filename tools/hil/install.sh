#!/usr/bin/env bash
# Builds and installs the Halo debug APK on the explicit lab device.
#
# Installs only to the serial chosen by hil_resolve_serial; there is no "first device" fallback.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

hil_require_tools
hil_prepare_dirs

SERIAL="$(hil_resolve_serial)"
hil_info "installing to $SERIAL"

hil_record_stage "install.build"
./gradlew :app:assembleDebug --console=plain | tail -5

APK="$HIL_ROOT/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || hil_die "expected APK not found: $APK"

hil_record_stage "install.push"
hil_adb "$SERIAL" install -r "$APK"

VERSION="$(hil_adb "$SERIAL" shell dumpsys package "${HALO_HIL_APP_PACKAGE:-com.halo.app}" 2>/dev/null \
  | grep -m1 versionName | tr -d '\r' || true)"
hil_safe_log install.txt "installed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ) apk=$APK $VERSION commit=$(git rev-parse HEAD)"
hil_info "installed: $APK ($VERSION)"
