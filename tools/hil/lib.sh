#!/usr/bin/env bash
# Shared helpers for the Halo hardware-in-the-loop (HIL) suite.
#
# Everything in tools/hil/ is opt-in. Nothing here may be invoked by the normal
# automatic gate (`./gradlew haloVerify`, `test`, `check`, or PR CI).

set -euo pipefail

HIL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$HIL_ROOT"

HIL_DIR="$HIL_ROOT/build/hil"
HIL_SAFE_DIR="$HIL_DIR/diagnostics-safe"
HIL_PRIVATE_DIR="$HIL_DIR/diagnostics-private"

# vendor app that can mask protocol problems: if it is running, a "successful" transfer may not
# have been Halo's. Recorded, and force-stopped for independence tests.
HIL_VENDOR_PACKAGES_DEFAULT="com.eyevue.glassapp com.glasssutdio.wear"

hil_info()  { printf '[hil] %s\n' "$*"; }
hil_warn()  { printf '[hil][warn] %s\n' "$*" >&2; }
hil_error() { printf '[hil][error] %s\n' "$*" >&2; }
hil_die()   { hil_error "$*"; exit 1; }

hil_require_cmd() {
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || hil_die "required command not found: $cmd"
  done
}

hil_require_tools() {
  hil_require_cmd adb git
  [ -x "$HIL_ROOT/gradlew" ] || hil_die "gradlew not found or not executable at $HIL_ROOT"
}

# Layer switches. All default to false so CI can never enable hardware work implicitly.
hil_mode_enabled() {
  local name="$1"
  local value="${!name:-false}"
  case "$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

hil_require_mode() {
  local name="$1"
  hil_mode_enabled "$name" || hil_die "$name is not enabled (set $name=true to run this layer)"
}

hil_private_diagnostics_enabled() {
  hil_mode_enabled HALO_HIL_PRIVATE_DIAGNOSTICS
}

hil_prepare_dirs() {
  mkdir -p "$HIL_SAFE_DIR"
  if hil_private_diagnostics_enabled; then
    mkdir -p "$HIL_PRIVATE_DIR"
    hil_warn "private diagnostics enabled: $HIL_PRIVATE_DIR must never be uploaded automatically"
  fi
}

# Establish identity before anything touches hardware. Never falls back to "the first device":
# a wrong target silently produces meaningless results.
hil_resolve_serial() {
  local serial="${HALO_HIL_SERIAL:-}"
  if [ -n "$serial" ]; then
    hil_adb_require_online "$serial" || hil_die "HALO_HIL_SERIAL=$serial is not an online adb device"
    printf '%s' "$serial"
    return 0
  fi

  local devices
  devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
  local count
  count="$(printf '%s\n' "$devices" | grep -c . || true)"
  if [ "$count" -eq 0 ]; then
    hil_die "no online adb device; set HALO_HIL_SERIAL explicitly"
  fi
  if [ "$count" -ne 1 ]; then
    hil_error "multiple adb devices are connected; refusing to guess:"
    printf '  %s\n' $devices >&2
    hil_die "set HALO_HIL_SERIAL to the exact lab device serial"
  fi
  printf '%s' "$devices"
}

hil_adb_require_online() {
  local serial="$1"
  [ -n "$(adb -s "$serial" get-state 2>/dev/null | grep -x device || true)" ]
}

hil_adb() {
  local serial="$1"
  shift
  adb -s "$serial" "$@"
}

hil_write_identity() {
  local serial="$1"
  local out_file="$HIL_SAFE_DIR/preflight.txt"
  {
    printf 'recorded_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'serial=%s\n' "$serial"
    printf 'commit=%s\n' "$(git rev-parse HEAD 2>/dev/null || echo unknown)"
    printf 'commit_subject=%s\n' "$(git log -1 --pretty=%s 2>/dev/null || echo unknown)"
    if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
      printf 'working_tree=dirty\n'
    else
      printf 'working_tree=clean\n'
    fi
    printf 'android_release=%s\n' "$(hil_adb "$serial" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
    printf 'android_sdk=%s\n' "$(hil_adb "$serial" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
    printf 'device_model=%s\n' "$(hil_adb "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  } | tee "$out_file"
}

hil_package_installed() {
  local serial="$1" pkg="$2"
  hil_adb "$serial" shell pm list packages 2>/dev/null | tr -d '\r' | grep -qx "package:$pkg"
}

# Safe diagnostics: state, timings, ids and error codes. Never transcripts, photos or keys.
hil_safe_log() {
  hil_prepare_dirs
  local name="$1"
  shift
  printf '%s\n' "$*" >> "$HIL_SAFE_DIR/$name"
}

hil_record_stage() {
  hil_safe_log stages.log "$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ) stage=$1 detail=${2:-}"
}
