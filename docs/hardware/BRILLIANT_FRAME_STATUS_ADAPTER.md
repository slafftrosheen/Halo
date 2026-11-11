# Brilliant Labs Frame — status adapter

Source implementation added 2026-11-11 for #66. **Physical qualification NOT_RUN.** This is the first Frame adapter slice: identity, firmware and battery. It does not provide Frame display, camera, microphone, IMU/tap streams, companion script deployment or Brilliant Labs Halo compatibility.

## Evidence and provenance

Original Kotlin implementation from the manufacturer's protocol documentation; no vendor SDK source is copied or packaged.

- [Frame Bluetooth specification](https://github.com/brilliantlabsAR/docs/blob/236f9b25acd623529985fe48acf23759537accd1/frame/frame-sdk-bluetooth-specs.md), blob `203f48f179f92413a2dfb8d6a2b5c4dfd932de2d`: bonding, service/TX/RX UUIDs, Lua evaluation and print notifications, MTU limit, binary marker and control bytes.
- [Frame Lua API](https://github.com/brilliantlabsAR/docs/blob/236f9b25acd623529985fe48acf23759537accd1/frame/frame-sdk-lua.md), blob `ea536c250548205bed83570f3d156f83b2fbf6ce`: `frame.HARDWARE_VERSION`, `frame.FIRMWARE_VERSION`, `frame.battery_level()` (documented 1–100).
- [Brilliant SDK inventory](https://github.com/brilliantlabsAR/brilliant_sdk/blob/9d17e96ce1b0f1d33d270315a3816d2e8cd0523b/README.md): both Frame and Brilliant Labs Halo use the `frame.*` family. Shared API/UUIDs are insufficient model evidence. The SDK is an architectural reference only; no Python/Flutter/WebBluetooth package becomes an Android runtime dependency.

| Boundary | Implemented behavior |
|---|---|
| Selection | Choose **Brilliant Labs Frame** explicitly for a scanned endpoint. No advertising-name, shared-UUID or generic `Frame` string inference. |
| Transport | Separate one-use Android BLE link, exact UUIDs, serialized ATT writes, confirmed RX CCCD, observed MTU before Lua TX. Never fragments a Lua expression into separate evaluations. |
| Bond | Background reconnect requires an existing Android bond before opening GATT. Explicit pairing may open Android's own pairing flow; Halo cannot approve it. |
| Handshake | Three nonce-correlated reads: hardware must equal `Frame`, bounded firmware, valid battery. Every stage must succeed before READY/save. |
| Status | Cached exact saved-session snapshot, or explicit battery / hardware-and-firmware refresh. Unknown charging remains unknown. |
| Access | Status owner shared by selected-device UI and typed assistant tools; current foreground/unlock/grown-up access and remote disclosure rechecks. |
| Failure | Timeout, cancellation, malformed reply, EOF or link loss retires the link. Late responses cannot satisfy a new operation. |
| Capability | Only BATTERY / DEVICE_INFO. No capture or display capability is published for this adapter. |

## User journey

1. Add glasses; select the exact nearby endpoint and **Brilliant Labs Frame** family.
2. Accept Android pairing if requested. The device must be idle and able to evaluate Lua expressions. A running `main.lua` may prevent responses.
3. Halo reads model, firmware and battery before reporting READY. A `Halo` hardware response is rejected even if UUIDs match.
4. Saved device controls offer battery and information refresh, connect/disconnect and the existing auto-connect preference. Camera, storage, time-sync and recording controls are absent.
5. Assistant: list saved devices, select its exact `deviceId`, call `halo_native_device_status`, then `halo_native_device_refresh` with that status's `sourceVersion` and `operation` = `battery` or `information`.

A refresh version is consumed under the driver workflow lock before writing, including uncertain failures. A new response/reconnect invalidates old versions. No automatic replay of refresh tools. Existing saved-device reconnect remains the common bounded reconnect coordinator; it does not install scripts or reset a busy device.

Halo sends **no** break (`0x03`), reset (`0x04`), `frame.update`, file write, callback registration or persistent Lua setup. It does not interrupt an existing application to obtain READY. The user must decide when their Frame is available. Status is protocol evidence, not hardware certification.

## Local acceptance still needed

| Case | Expected observation | Result |
|---|---|---|
| Idle, unbonded Frame selected explicitly | Android pairing; exact GATT/MTU; three replies; READY; saved identity | NOT_RUN |
| User declines pairing / removes bond | No READY; no unapproved background rebond | NOT_RUN |
| Existing script occupies REPL | Bounded timeout; program intact; no break/reset/file traffic | NOT_RUN |
| Brilliant Labs Halo selected as Frame | Reject hardware identity; no subsequent commands after mismatch | NOT_RUN |
| Low MTU / missing CCCD / missing TX/RX | Fail before status handshake; useful retry guidance | NOT_RUN |
| Fresh battery/info, wrong/stale device token | Selected response only; stale request sends no command | NOT_RUN |
| Bluetooth off, unplug/power loss, cancellation, reconnect | Old link retired; new sourceVersion; no late-reply reuse | NOT_RUN |
| Background, lock, Kids, remote disclosure revoked | Status tool rejected; pending follow-up read does not dispatch | NOT_RUN |
| Process death | Existing saved identity retained; no command or content replay | NOT_RUN |

Record actual Frame firmware and Android host/API, sanitized transport metadata, APK/source commit and result per case. Do not infer qualification from the fake-link checks. Next Frame work requires a deliberate display/script ownership design and separate media/IMU contracts; the current adapter does not expose raw Lua as a substitute.
