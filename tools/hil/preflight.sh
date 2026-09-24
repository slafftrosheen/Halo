#!/usr/bin/env bash
# Establishes identity before any hardware test. Run this first, every session.
#
# It refuses to run against "the first connected device", records the exact commit and phone, and
# verifies the preconditions that make a HeyCyan result meaningful. It never modifies app state.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

HIL_APP_PACKAGE="${HALO_HIL_APP_PACKAGE:-com.halo.app}"

hil_require_tools
hil_prepare_dirs

SERIAL="$(hil_resolve_serial)"
hil_info "target serial: $SERIAL"

hil_write_identity "$SERIAL"

# --- Bluetooth preconditions ---------------------------------------------------------------
BT_STATE="$(hil_adb "$SERIAL" shell settings get global bluetooth_on 2>/dev/null | tr -d '\r')"
if [ "$BT_STATE" != "1" ]; then
  hil_die "Bluetooth is disabled on $SERIAL; enable it before running hardware tests"
fi

if hil_package_installed "$SERIAL" "$HIL_APP_PACKAGE"; then
  hil_info "$HIL_APP_PACKAGE is installed"
  BT_PERMS="$(hil_adb "$SERIAL" shell dumpsys package "$HIL_APP_PACKAGE" 2>/dev/null \
    | grep -oE 'android\.permission\.BLUETOOTH_(CONNECT|SCAN)' | sort -u | tr '\n' ' ' | tr -d '\r')"
  if [ -z "$BT_PERMS" ]; then
    hil_warn "no Bluetooth permissions granted for $HIL_APP_PACKAGE; grant them and re-run preflight"
  else
    hil_info "granted Bluetooth permissions: $BT_PERMS"
  fi
else
  hil_warn "$HIL_APP_PACKAGE is not installed; run tools/hil/install.sh first"
fi

# --- HeyCyan link ---------------------------------------------------------------------------
BONDED="$(hil_adb "$SERIAL" shell dumpsys bluetooth_manager 2>/dev/null \
  | grep -iE '^\s*address:|Bonded devices|name:' | tr -d '\r' | head -20 || true)"
printf '%s\n' "$BONDED" > "$HIL_SAFE_DIR/bonded-devices.txt"
if printf '%s' "$BONDED" | grep -qiE 'address:'; then
  hil_info "bonded Bluetooth devices recorded in $HIL_SAFE_DIR/bonded-devices.txt"
else
  hil_warn "no bonded Bluetooth devices reported; the glasses may not be paired"
fi

EXPECTED_GLASSES="${HALO_HIL_GLASSES_ADDRESS:-}"
if [ -n "$EXPECTED_GLASSES" ]; then
  if printf '%s' "$BONDED" | grep -qi "$EXPECTED_GLASSES"; then
    hil_info "expected glasses address $EXPECTED_GLASSES is bonded"
  else
    hil_die "expected glasses address $EXPECTED_GLASSES is not bonded on $SERIAL"
  fi
else
  hil_warn "HALO_HIL_GLASSES_ADDRESS not set; glasses identity was not verified"
fi

CONNECTED="$(hil_adb "$SERIAL" shell dumpsys bluetooth_manager 2>/dev/null \
  | grep -ciE 'state: connected|Connected devices' || true)"
hil_safe_log preflight-extra.txt "connected_bluetooth_hint=$CONNECTED"

# --- Vendor app independence ---------------------------------------------------------------
VENDOR_PACKAGES="${HALO_HIL_VENDOR_APP_PACKAGES:-$HIL_VENDOR_PACKAGES_DEFAULT}"
VENDOR_PRESENT=""
for pkg in $VENDOR_PACKAGES; do
  if hil_package_installed "$SERIAL" "$pkg"; then
    VENDOR_PRESENT="$VENDOR_PRESENT $pkg"
    hil_warn "vendor app present: $pkg (it must be force-stopped for independence tests)"
  fi
done
hil_safe_log preflight-extra.txt "vendor_apps_present=${VENDOR_PRESENT:-none}"

# --- Selected provider / model -------------------------------------------------------------
EXPECT_PROVIDER="${HALO_HIL_EXPECT_PROVIDER:-}"
EXPECT_MODEL="${HALO_HIL_EXPECT_MODEL:-}"
if [ -n "$EXPECT_PROVIDER" ]; then
  hil_info "expecting provider=$EXPECT_PROVIDER${EXPECT_MODEL:+ model=$EXPECT_MODEL}"
  # A debug build allows reading the persisted selection; release builds must not be relied on.
  observed="$(hil_adb "$SERIAL" shell run-as "$HIL_APP_PACKAGE" \
    sh -c 'cat files/datastore/*.preferences_pb 2>/dev/null | strings' 2>/dev/null | tr -d '\r' || true)"
  printf '%s\n' "$observed" > "$HIL_SAFE_DIR/provider-selection.txt"
  if printf '%s' "$observed" | grep -q "$EXPECT_PROVIDER"; then
    hil_info "persisted selection mentions $EXPECT_PROVIDER"
  else
    hil_warn "could not confirm $EXPECT_PROVIDER in persisted state; confirm it in-app before testing"
  fi
else
  hil_warn "HALO_HIL_EXPECT_PROVIDER not set; selected provider/model was not verified"
fi

hil_info "preflight complete; evidence in $HIL_SAFE_DIR/preflight.txt"
