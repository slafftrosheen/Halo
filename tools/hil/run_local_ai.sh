#!/usr/bin/env bash
# Local AI layer: glasses transport is NOT required.
#
# Measures the two numbers that decide whether model preload is worth its memory cost:
#   interaction start -> first token
#   interaction start -> first audible answer
# Run twice, once cold and once with preload, on the same model, prompt and device.
#
# Requires HALO_HIL_LOCAL_AI=true.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

hil_require_mode HALO_HIL_LOCAL_AI
hil_require_tools
hil_prepare_dirs

SERIAL="$(hil_resolve_serial)"
APP_PACKAGE="${HALO_HIL_APP_PACKAGE:-com.halo.app}"
MODE="${1:-}"

case "$MODE" in
  cold|preload) ;;
  *) hil_die "usage: $0 <cold|preload>" ;;
esac

hil_info "local AI latency run: mode=$MODE on $SERIAL"
hil_record_stage "localai.start" "mode=$MODE serial=$SERIAL commit=$(git rev-parse HEAD)"

# Force a fresh process so "cold" is genuinely cold.
hil_adb "$SERIAL" shell am force-stop "$APP_PACKAGE" >/dev/null 2>&1 || true
hil_adb "$SERIAL" logcat -c 2>/dev/null || true
hil_adb "$SERIAL" shell monkey -p "$APP_PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true

if [ "$MODE" = "preload" ]; then
  hil_info "wait for the model preload to finish before starting the interaction"
fi

cat <<'INSTRUCTIONS'

--- human steps -------------------------------------------------------------------------------
1. Ask one fixed question, for example: "How many days until Friday?"
2. Use the same question for both the cold and preload runs.

INSTRUCTIONS
read -r -p "[hil] press Enter after the answer completes... " _

hil_adb "$SERIAL" logcat -d -v threadtime > "$HIL_SAFE_DIR/localai-$MODE-logcat.txt" 2>/dev/null || true

TTFT="$(grep -oE 'timeToFirstTokenMs=[0-9]+' "$HIL_SAFE_DIR/localai-$MODE-logcat.txt" | tail -1 || true)"
AUDIBLE="$(grep -oE 'InteractionLatencyUpdated[^\n]*playbackStartedAtMs=[0-9]+' "$HIL_SAFE_DIR/localai-$MODE-logcat.txt" | tail -1 || true)"
LOAD="$(grep -oE 'modelReadyAtMs=[0-9]+' "$HIL_SAFE_DIR/localai-$MODE-logcat.txt" | tail -1 || true)"

{
  printf 'mode=%s\n' "$MODE"
  printf 'first_token=%s\n' "${TTFT:-none}"
  printf 'model_ready=%s\n' "${LOAD:-none}"
  printf 'playback=%s\n' "${AUDIBLE:-none}"
  printf 'measured_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} | tee -a "$HIL_SAFE_DIR/localai-latency.txt"

hil_info "recorded. Run the other mode, then compare time-to-first-token and time-to-audible."
