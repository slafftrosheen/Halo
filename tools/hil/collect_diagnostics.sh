#!/usr/bin/env bash
# Collects diagnostics from the lab device.
#
# SAFE (default, build/hil/diagnostics-safe): state transitions, provider/model ids, operation
# names, packet lengths, error codes and timings. This is what may be attached to a bug report.
#
# PRIVATE (opt-in via HALO_HIL_PRIVATE_DIAGNOSTICS=true, build/hil/diagnostics-private): raw
# speech transcripts, captured images, full notification text. Never uploaded automatically and
# never committed.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

hil_require_tools
hil_prepare_dirs

SERIAL="$(hil_resolve_serial)"
hil_info "collecting safe diagnostics from $SERIAL"

# --- safe: logcat filtered to Halo's operational vocabulary ---------------------------------
SAFE_TAGS='Halo|HeyCyan|AiProviderRouter|CognitiveSession|SpeechOutput|TtsStarted|InteractionTurn|InteractionLatency'
hil_adb "$SERIAL" logcat -d -v threadtime 2>/dev/null \
  | grep -E "$SAFE_TAGS" \
  | grep -vE 'transcript|"text":|api_key|Authorization|Bearer ' \
  > "$HIL_SAFE_DIR/logcat-safe.txt" || true

# --- safe: structured evidence extracted from the same log ----------------------------------
{
  printf '# interactionId values observed\n'
  grep -oE 'interactionId=[A-Za-z0-9-]+' "$HIL_SAFE_DIR/logcat-safe.txt" | sort -u || true
  printf '\n# turn lifecycle transitions\n'
  grep -oE 'InteractionTurnChanged[^\n]*state=[A-Za-z]+\(' "$HIL_SAFE_DIR/logcat-safe.txt" || true
  printf '\n# speech route evidence\n'
  grep -oE 'TtsStarted[^\n]*target=[A-Za-z.]+' "$HIL_SAFE_DIR/logcat-safe.txt" || true
  printf '\n# provider cooldown / rate limit semantics\n'
  grep -oE 'RATE_LIMITED[^\n]*retryAfterMs=[0-9]+' "$HIL_SAFE_DIR/logcat-safe.txt" || true
} > "$HIL_SAFE_DIR/evidence.txt"

# --- safe: device + build identity ----------------------------------------------------------
cp -f "$HIL_DIR/diagnostics-safe/preflight.txt" "$HIL_SAFE_DIR/preflight.txt" 2>/dev/null || true

# --- optional private: raw capture, only with explicit opt-in --------------------------------
if hil_private_diagnostics_enabled; then
  hil_warn "collecting PRIVATE diagnostics (do not upload)"
  hil_adb "$SERIAL" logcat -d -v threadtime > "$HIL_PRIVATE_DIR/logcat-full.txt" 2>/dev/null || true
  hil_adb "$SERIAL" shell run-as "${HALO_HIL_APP_PACKAGE:-com.halo.app}" \
    sh -c 'ls -R files 2>/dev/null' > "$HIL_PRIVATE_DIR/app-files.txt" 2>/dev/null || true
fi

hil_info "safe diagnostics: $HIL_SAFE_DIR"
cat "$HIL_SAFE_DIR/evidence.txt" 2>/dev/null | head -40
