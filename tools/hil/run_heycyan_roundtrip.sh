#!/usr/bin/env bash
# HeyCyan voice round trip, as much of it automated as software can honestly assert:
#
#   glasses connected -> physical voice capture -> STT -> local model -> response -> wearable TTS
#
# Software evidence proves: one interactionId spans the turn, the output route is wearable with
# phone fallback disallowed, and playback started. Only a human can confirm that the audio was
# actually audible from the glasses and that the phone stayed silent; the script asks for that
# confirmation instead of pretending to measure it.
#
# Requires HALO_HIL_HEYCYAN=true.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

hil_require_mode HALO_HIL_HEYCYAN
hil_require_tools
hil_prepare_dirs

SERIAL="$(hil_resolve_serial)"
APP_PACKAGE="${HALO_HIL_APP_PACKAGE:-com.halo.app}"
hil_info "HeyCyan round trip on $SERIAL"

# --- independence: the vendor app must not be able to make this look successful --------------
VENDOR_PACKAGES="${HALO_HIL_VENDOR_APP_PACKAGES:-$HIL_VENDOR_PACKAGES_DEFAULT}"
VENDOR_WAS_PRESENT="none"
for pkg in $VENDOR_PACKAGES; do
  if hil_package_installed "$SERIAL" "$pkg"; then
    VENDOR_WAS_PRESENT="$pkg"
    hil_warn "force-stopping vendor app $pkg so the result reflects Halo's own protocol handling"
    hil_adb "$SERIAL" shell am force-stop "$pkg"
  fi
done
hil_safe_log heycyan-roundtrip.txt "vendor_app_present=$VENDOR_WAS_PRESENT"
if [ "$VENDOR_WAS_PRESENT" != "none" ]; then
  hil_warn "vendor app was installed for this run: Halo does not solely own the transfer"
fi

# --- fresh evidence window -------------------------------------------------------------------
hil_record_stage "roundtrip.start" "serial=$SERIAL commit=$(git rev-parse HEAD)"
hil_adb "$SERIAL" logcat -c 2>/dev/null || true

hil_info "launching Halo"
hil_adb "$SERIAL" shell monkey -p "$APP_PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true

cat <<'INSTRUCTIONS'

--- human steps -------------------------------------------------------------------------------
1. Confirm the glasses show as connected in Halo.
2. Make sure the selected provider/model is the one recorded in preflight.
3. Press and hold the glasses' voice button and speak one short question, for example:
       "What is the battery level?"
4. Release and wait for the answer.
5. Note whether the answer was audible from the glasses and whether the phone stayed silent.

INSTRUCTIONS
read -r -p "[hil] press Enter after the interaction completes... " _

hil_adb "$SERIAL" logcat -d -v threadtime > "$HIL_SAFE_DIR/roundtrip-logcat.txt" 2>/dev/null || true

# --- software assertions ---------------------------------------------------------------------
INTERACTIONS="$(grep -oE 'interactionId=[A-Za-z0-9-]+' "$HIL_SAFE_DIR/roundtrip-logcat.txt" | sort -u | wc -l | tr -d ' ')"
ROUTE="$(grep -oE 'TtsStarted[^\n]*target=SpeechOutputTarget\.[A-Za-z]+' "$HIL_SAFE_DIR/roundtrip-logcat.txt" | tail -1 || true)"
PHONE_FALLBACK="$(grep -cE 'TtsStarted[^\n]*target=SpeechOutputTarget\.Phone' "$HIL_SAFE_DIR/roundtrip-logcat.txt" || true)"
DISALLOW="$(grep -cE 'DISALLOW_PHONE' "$HIL_SAFE_DIR/roundtrip-logcat.txt" || true)"

{
  printf 'interaction_ids_observed=%s\n' "$INTERACTIONS"
  printf 'last_tts_route=%s\n' "${ROUTE:-none}"
  printf 'phone_route_events=%s\n' "$PHONE_FALLBACK"
  printf 'disallow_phone_events=%s\n' "$DISALLOW"
} | tee -a "$HIL_SAFE_DIR/heycyan-roundtrip.txt"

read -r -p "[hil] was the answer audible from the glasses? (y/n) " AUDIBLE
read -r -p "[hil] did the phone stay silent? (y/n) " PHONE_SILENT

printf 'audible_from_glasses=%s\nphone_remained_silent=%s\n' "$AUDIBLE" "$PHONE_SILENT" \
  | tee -a "$HIL_SAFE_DIR/heycyan-roundtrip.txt"

RESULT="pass"
if [ "$INTERACTIONS" -lt 1 ]; then
  hil_error "no interactionId was observed; the turn did not start"
  RESULT="fail"
fi
if ! printf '%s' "$ROUTE" | grep -q 'Wearable'; then
  hil_error "last TTS route was not a wearable route"
  RESULT="fail"
fi
if [ "$PHONE_FALLBACK" -ne 0 ]; then
  hil_error "a phone speech route was observed during a wearable interaction"
  RESULT="fail"
fi
if [ "$AUDIBLE" != "y" ] || [ "$PHONE_SILENT" != "y" ]; then
  hil_error "human confirmation failed"
  RESULT="fail"
fi

hil_info "round trip result: $RESULT"
hil_record_stage "roundtrip.$RESULT"
[ "$RESULT" = "pass" ] || exit 1
