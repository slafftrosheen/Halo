# COLMI A2 — HeyCyan integration and HIL qualification

Status: **implementation in progress / physical validation NOT RUN**. Updated 2026-11-09 for [#66](https://github.com/slafftrosheen/Halo/issues/66). This replaces the prior BW-AG1 Pro device as Halo's primary available HeyCyan test target. The BW-AG1 Pro is **not available for physical test** and must not gate this task.

## Source versus device evidence

- Manufacturer COLMI A2 product description confirms the **HeyCyan companion app**, Jieli + Allwinner hardware, camera, Bluetooth audio and Wi-Fi media synchronization. Source: https://www.colmismart.com/colmi-a2-smart-glasses-high-tech-wearable-from-china-suppliers-with-advanced-features-factory-direct-product/
- This is **app-level compatibility evidence only**, **not** a verified BLE advertising name, GATT service map, firmware version, command payload/response, media URL or Android READY state on the user's specific glasses.
- COLMI A2 remains an explicit *candidate* for the existing HEYCYAN_GLASSES transport. Halo must probe the exact primary service `de5bf728-d711-4e47-af26-65e3012a5dc7` and command write `de5bf72a-...` / notify `de5bf729-...` endpoints with appropriate properties + subscribed CCCD **before** enabling HeyCyan commands. If absent, fail *unsupported profile*, not *paired*.
- A2 may advertise a different BLE name than its retail model. Collect the real name, UUID list and firmware before adding any new alias. Never guess a vendor MAC prefix or expand auto-connect to nearby unrelated devices.
- The A2 microphone/speaker expose Android audio endpoints that are distinct from the BLE control channel. The system must require an explicitly associated endpoint before selecting glasses playback or capture.

## Code already present before #66

- `drivers/heycyan`: `RealHeyCyanDriver`, `HeyCyanGattClient`, codec, command execution, audio input, diagnostics and Wi-Fi media sync.
- `app`: `PairingViewModel`, `HeyCyanAudioPairingCoordinator`, `HeyCyanCaptureCoordinator`, device registry and saved-device tools.
- `docs/protocols/heycyan/IMPLEMENTATION_STATUS.md`: existing integration state and risks. The P2P downloader uses network-specific sockets to preserve ordinary phone connectivity.

## 2026-11-09 implementation work

- Device-family classification is now explicit: COLMI A2 and known legacy HeyCyan identities map to the existing family; foreign Glasses names never silently become HeyCyan.
- BLE scanner accumulates repeated advertisement/scan-response evidence and updates the pairing list if names, vendor data or UUIDs arrive later; RSSI-only noise is suppressed.
- The pairing UI distinguishes COLMI A2/HeyCyan candidates from unverified peers (advertisement is never proof of actual protocol).
- The GATT client validates the vendor service, exact write + notify characteristics, required properties, notify CCCD and completion of descriptor writes prior to `DriverState.Ready`. This stops foreign GATT devices from being accepted merely because Android connected.
- Runtime capability publishing requires **SessionState.READY**, not an intermediate CONNECTED/HANDSHAKE state.

## COLMI A2 physical HIL matrix — ALL NOT RUN until real-device receipts

1. **Discovery, without HeyCyan app active:** power on A2; capture advertisements over at least 25 seconds, including subsequent scan responses; store observed BLE name(s), UUIDs, RSSI, firmware/OS version, no identifier in shared logs. A2 may have separate Classic and BLE identities.
2. **Pairing/READY:** tap selected A2 explicitly; capture GATT generation, services, exact command/notify/CCCD, actual READY or specific failure. Keep unrelated Bluetooth accessories present to prove target isolation.
3. **Battery/version/storage:** request safe reads; record exact response bytes, UUID, timestamp, acknowledgment mapping. Check cancellation and stale notifications cannot satisfy later commands.
4. **Camera:** manual photo, then AI thumbnail; save one consented sample locally and inspect decoded dimensions/content. Avoid sending imagery to a cloud model in Private mode.
5. **Video/audio:** start/stop recording with visible indicators; confirm actual device response/state and file inventory. Do not claim success from GATT write only.
6. **Wi-Fi transfer:** P2P discovery + BLE transfer request `02 01 04`, wait for glasses-reported IP, HTTP `/files/media.config`, download JPG/MP4/OPUS, verify media integrity, duplicate sync, cancellation, retry, no use of phone's `192.168.49.1` as glasses IP. Preserve the phone's normal internet route.
7. **Audio route:** explicit BLE-to-Classic association, live mic/STT, TTS on glasses (exact endpoint), route loss and recovery; never fall back silently to unrelated headset.
8. **Lifecycle:** BLE off/on, glasses off/on, home/background/foreground, process death, denied permission, another paired audio device, vendor HeyCyan app running concurrently, Wi-Fi/VPN.
9. **Failure evidence:** protocol mismatch must remain unpaired, a delayed first BLE notify must not be mistaken for total failure, repeated GATT failures must use bounded teardown/backoff.
10. Record each result in `docs/hardware/WEARABLE_HIL_MATRIX.md` when obtained, with Android/build SHA, A2 firmware, actual pass/fail/not-run and sanitized trace path.

No destructive commands, unbind, factory reset or OTA/DFU are permitted during this qualification. Only mock/unit and source reasoning is possible in a GitHub-only session without the physical glasses/Android build runner. Do not mark physical checks PASS prematurely.
