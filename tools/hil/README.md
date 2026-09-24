# Halo HIL (hardware-in-the-loop) suite

Status: CURRENT. Everything here is **opt-in**. The normal PR gate never waits for a phone, and
no script in this directory runs as part of `./gradlew test`, `check` or `haloVerify`.

## What requires what

| Layer | Command | Requires |
| --- | --- | --- |
| Automatic, hardware-free | `./gradlew haloVerify` | nothing; runs static analysis + every unit test layer |
| Hermetic emulator | `.github/workflows/android-emulator.yml` | an emulator only; deterministic instrumentation, no credentials, no cloud |
| Phone, no glasses | `HALO_HIL_LOCAL_AI=true tools/hil/run_local_ai.sh cold\|preload` | one lab phone |
| Phone + glasses | `HALO_HIL_HEYCYAN=true tools/hil/run_heycyan_roundtrip.sh` | one lab phone + connected HeyCyan glasses |

Layer switches (all default to `false`, so CI cannot enable hardware work implicitly):

```text
HALO_HIL_HEYCYAN          HeyCyan transport / voice round trip
HALO_HIL_LOCAL_AI         local on-device inference without glasses
HALO_HIL_WEARABLE_AUDIO   wearable audio routing checks
HALO_HIL_VISION           AI thumbnail / vision freshness checks
HALO_HIL_PRIVATE_DIAGNOSTICS  collect richer artifacts locally (never uploaded)
```

Enabling a layer is always an explicit environment change, on a machine that has the hardware.

## Target selection

Every script resolves its target through `hil_resolve_serial`:

* `HALO_HIL_SERIAL` names the exact device, or
* exactly one online adb device exists.

With two or more devices attached the scripts **refuse to guess** and abort. There is no
"first connected device" fallback anywhere in this suite.

## Scripts

```text
preflight.sh              establish identity: serial, commit, Bluetooth state/permissions,
                          bonded glasses, selected provider/model. Run this first.
install.sh                build and install the debug APK on the resolved serial.
run_instrumentation.sh    run instrumentation on the resolved serial, with the infra-only retry.
collect_diagnostics.sh    collect safe diagnostics (and private ones only when opted in).
run_heycyan_roundtrip.sh  voice -> STT -> local AI -> wearable TTS round trip.
run_local_ai.sh           cold vs preload latency, glasses not required.
```

## Diagnostics: safe vs private

`build/hil/diagnostics-safe/` is produced by default and is the only thing that may be attached to
a bug report. It may contain timestamps, state transitions, provider/model ids, BLE operation
names, packet lengths, error codes, latencies and device transport status.

`build/hil/diagnostics-private/` is produced **only** when `HALO_HIL_PRIVATE_DIAGNOSTICS=true` and
may contain raw speech transcripts, captured images, notification text or app file listings. It is
never uploaded automatically and never committed. `build/` is the only place these land.

## Vendor app independence

The official HeyCyan-style app (`com.eyevue.glassapp`, `com.glasssutdio.wear` by default, override
with `HALO_HIL_VENDOR_APP_PACKAGES`) can remain active and make a transfer test look successful
when Halo did not actually own it. Independent-protocol tests force-stop it first and record
whether it was installed. A run where the vendor app was present cannot claim that Halo solely
owned the transfer.

## What software can and cannot assert

Automated evidence: one `interactionId` spanning capture -> STT -> provider -> TTS, wearable
output target, phone fallback disallowed, playback started, no phone speech route observed.

Human confirmation is still required for "audio came from the glasses" and "the phone stayed
silent" until audio loopback instrumentation is trustworthy. The scripts ask for that
confirmation explicitly rather than inferring it.

## Infrastructure retry policy

`tools/ci/run_instrumentation_with_retry.sh` retries **at most once**, and only when the failure
looks like a broken harness (instrumentation process disconnected, adb/emulator hiccup, install
failure, timeout) *and* there is no product evidence. Failing JUnit results or assertion failures
are never retried.
