# Alpha-W1 Hardware Testing Protocol

This document tracks the physical hardware testing sequence for the HeyCyan integration.

## Status

**Current Target**: A coherent device stack capable of exercising every implemented subsystem independently.
**Not Target**: 100% HeyCyan functionality (not all SDK operations are verified on hardware yet).

## Test Sequence

- `[x]` **TEST 0 — Static/software gate**
  - Verify build succeeds, tests pass, and working tree is clean.
  - *Status*: Build succeeded with `:app:compileDebugKotlin`.
- `[/]` **TEST 1 — App boot**
  - Verify DI graph initializes, `HeyCyanHost` exists, Runtime starts without crash.
  - *Status*: App deployed to device. Checking logs for successful initialization.
- `[ ]` **TEST 2 — Bluetooth permission**
  - Verify BLE scanning discovers the HeyCyan advertisement (address, name, RSSI, etc).
- `[ ]` **TEST 3 — BLE connection**
  - Verify connection lifecycle (Scanning -> Connecting -> Connected -> Discovering -> Subscribing -> Ready).
- `[ ]` **TEST 4 — GATT inventory**
  - Dump services, characteristics, descriptors, properties, MTU. Save as `HEY-CYAN-HARDWARE-BASELINE-001`.
- `[ ]` **TEST 5 — Notification/indication test**
  - Press physical button. Verify raw packet RX and decoded `HeyCyanEvent`.
- `[ ]` **TEST 6 — Device state**
  - Verify `HeyCyanDeviceState` (battery, wear state, connection state).
- `[ ]` **TEST 7 — Button event**
  - Press button. Verify `ButtonEvent.Pressed` / `Released` on `EventBus`.
- `[ ]` **TEST 8 — RFCOMM connection**
  - Connect RFCOMM. Send known idempotent command (`GetBattery`, `GetVersion`).
- `[ ]` **TEST 9 — RFCOMM golden packet**
  - Verify exact transmitted bytes against source-derived golden vector.
- `[ ]` **TEST 10 — RFCOMM fragmentation**
  - Verify packet reassembly (split at boundaries, multiple packets per read).
- `[ ]` **TEST 11 — Camera: TAKE PHOTO**
  - Send `TakePhoto`. Verify device event and media inventory changes.
- `[ ]` **TEST 12 — Photo transfer**
  - `SynchronizeMedia`. Verify Wi-Fi connection, download, and local atomic commit.
- `[ ]` **TEST 13 — Synchronization idempotency**
  - `SynchronizeMedia` again. Verify no duplicate downloads (`new = 0, alreadyPresent = 1`).
- `[ ]` **TEST 14 — Video**
  - `StartVideo` -> wait -> `StopVideo`. Verify MP4 created and downloaded via `SynchronizeMedia`.
- `[ ]` **TEST 15 — Audio**
  - `StartAudioRecording` -> record -> `StopAudioRecording`. Verify OPUS created and downloaded via `SynchronizeMedia`.
- `[ ]` **TEST 16 — Wi-Fi failure**
  - Interrupt Wi-Fi during `SynchronizeMedia`. Verify graceful failure without fake success.
- `[ ]` **TEST 17 — Download interruption**
  - Kill app during download. Verify partial file discarded or properly resumed on restart.
- `[ ]` **TEST 18 — BLE disconnect recovery**
  - Turn off glasses/move out of range. Verify `Disconnected` state and pending operations fail. Verify clean reconnection.
- `[ ]` **TEST 19 — RFCOMM disconnect recovery**
  - Simulate RFCOMM drop. Verify pending requests fail/timeout gracefully.
- `[ ]` **TEST 20 — Non-idempotent timeout safety**
  - Simulate lost response for `TakePhoto`. Verify `UnknownOutcome`, no retry, no duplicate photos.
- `[ ]` **TEST 21 — Runtime integration**
  - Execute "Take a photo" via voice/button through full cognitive loop to device execution and Timeline.
- `[ ]` **TEST 22 — Passive listening return**
  - Verify runtime returns to `Passive Listening` state cleanly after operation.
- `[ ]` **TEST 23 — Full Alpha-W1 sequence**
  - Execute end-to-end boot -> BLE -> Passive Listening -> Event -> Interpretation -> Execution -> Response -> Passive Listening.

## Reporting Template

```text
TEST ID:
DEVICE:
FIRMWARE:
HALO BUILD:
DATE:

INPUT:
EXPECTED:

OBSERVED:

BLE:
RFCOMM:
Wi-Fi:

RAW EVENT:
DECODED EVENT:

COMMAND:
RAW TX:
RAW RX:

DEVICE STATE BEFORE:
DEVICE STATE AFTER:

MEDIA BEFORE:
MEDIA AFTER:

RESULT:
PASS / FAIL / UNKNOWN

IF FAILURE:
LIKELY LAYER:
```
