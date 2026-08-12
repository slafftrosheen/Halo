# Halo HeyCyan Protocol Knowledge Base
Date: 2026-08-12
Rule: raw observations and source-derived facts outrank semantic guesses.

## 1. Transport architecture
BLE is the control plane. Full-resolution media transfer is a Wi-Fi Direct + HTTP data plane recovered from the alternative/vendor app.

Known GLASSES_CONTROL action: 0x41.
Halo-captured GATT:
TX de5bf72a-d711-4e47-af26-65e3012a5dc7
RX de5bf729-d711-4e47-af26-65e3012a5dc7

Authoritative SDK Action Mapping:
- `0x41` (65) = `ACTION_GLASSES_CONTROL` (`BC 41`)
- `0x59` (89) = `ACTION_GPT_UPLOAD` (`BC 59` audio stream)
- `0x73` (115) = `ACTION_DEVICE_DATA_REPORTING` (`BC 73` notifications)
- `0x44` (68) = `ACTION_DEVICE_AI_VOICE`
- `0x48` (72) = `ACTION_VOICE_STATUS`
- `0xFC` (-4) = `ACTION_OTA_SOC`
- `0xFD` (-3) = `ACTION_PICTURE_THUMBNAILS`

Wi-Fi media flow:
1. BLE trigger [02 01 04 01]
2. Wi-Fi Direct
3. GET /files/media.config
4. GET /files/<name>
Observed server: 192.168.49.43 on one unit.
Plain HTTP; local transfer needs no cloud HMAC.
Server may require recognizable okhttp User-Agent and closes connections; use Connection: close/no pooling.
Do not use bare TCP probes. Video manifest may time out during encoding; retry.
Android cleartext HTTP and binding to the P2P Network must be handled.

## 2. GLASSES_CONTROL
Observed frame shape:
BC 41 <length> 00 <CRC16 LE> <payload>

Physical Halo examples:
PHOTO: BC 41 03 00 10 50 02 01 01
VIDEO: BC 41 03 00 50 51 02 01 02
VOICE_ROUTING: BC 41 03 00 90 57 02 01 0B
UNKNOWN: BC 41 03 00 D0 53 02 01 04
MEDIA_COUNT: BC 41 02 00 01 13 02 04

Responses:
PHOTO: BC 41 05 00 0C 4C 02 01 01 FF FF
VIDEO start-like: BC 41 05 00 FC 4C 02 01 02 FF FF
VIDEO stop-like: BC 41 05 00 3D CD 02 01 02 FF 02
VOICE_ROUTING: BC 41 05 00 2C 4E 02 01 0B FF FF
MEDIA_COUNT: BC 41 0B 00 A0 E9 02 04 16 00 0A 00 04 00 01 00 00

VIDEO uses identical TX for start/stop but different responses. Treat it as state-dependent action, not proven SetMode.

## 3. Independent source-derived commands
[02 01 04 01] -> enter Wi-Fi transfer mode; response dataType/workType indicates workType 4.
[02 03] -> P2P IP response; raw IP bytes 10..13.
[02 04] -> media counts: imageCount, videoCount, recordCount.
Response tag 2 -> video angle/duration.
Response tag 6 -> record audio duration.

SDK bug: getP2pIp() reads byte 12 twice and skips byte 13. Parse raw bytes 10..13.

Resolved Ambiguity: Halo sent `02 01 04` which was incorrectly labelled as an audio command, while the independent source uses `02 01 04 01` for Wi-Fi-transfer mode. The `D0 53` byte sequence is just the valid CRC of `02 01 04`. Audio control uses `02 01 0B`.

## 4. Photo
Alternative implementation:
programmatic: [02 01 06 02 02] then [02 01 01], then thumbnail transfer.
Physical button: BLE event 0x02, then pull latest thumbnail; do not issue another shutter.
Halo repeated photo captures produced BC73 state reports with a field progressing 0x13 -> 0x14 -> 0x15 -> 0x16. Strong correlation with media/device state, but exact field semantics need authoritative confirmation.
A confirmed interaction pattern: PHOTO TX -> PHOTO ACK -> ~2s wait -> BC 73 08 00 FE C6 01 17 00 0A 00 04 00 01.

## 5. Video
TX verified:
BC 41 03 00 50 51 02 01 02
Start-like response ends FF FF.
Stop-like response ends FF 02.
During recording, BC 73 02 (e.g. `BC73 02 00 87 5A 0B 26`) frames appear sequentially.
Use Wi-Fi media.config as authoritative media discovery where possible.

## 6. Audio/voice — RESOLVED
Do NOT currently call 02 01 04 ToggleAudio or SetMode(AUDIO). `02 01 04` is UNRESOLVED.
Halo observed:
BC 41 03 00 D0 53 02 01 04
Associated delayed response contains 02 01 04 and ASCII-like device information, not a simple recording ACK.

The real-time streaming audio pipeline (formerly `BC 59 28`) is officially `ACTION_GPT_UPLOAD` (action 0x59). 
It is triggered **autonomously** by the glasses:
1. User touches the glasses frame.
2. Glasses send a `DEVICE_DATA_REPORTING` (0x73) frame with payload `0x03` (Voice activation start).
3. Glasses immediately begin blasting `ACTION_GPT_UPLOAD` (0x59) frames, which are `4B 41` prefixed raw Opus packets.
4. User touches the glasses frame again.
5. Glasses send a `DEVICE_DATA_REPORTING` (0x73) frame with payload `0x0A` (Voice activation end).
6. Glasses stop streaming `ACTION_GPT_UPLOAD`.

For recording media files (.opus on HTTP):
The glasses do not use Ogg containers. They dump the raw 40-byte `4B 41` Opus payloads directly to the `.opus` file on the HTTP server.

To route voice to the phone mic / standard Android SpeechRecognizer instead of using the `BC 59` stream:
- `[02 01 0B]` is sent before mic capture to stop proprietary AI audio stream / prepare phone mic routing. Verified on physical device: TX = BC 41 03 00 90 57 02 01 0B, RX = BC 41 05 00 2C 4E 02 01 0B FF FF.
- Android TTS sends spoken output through Bluetooth SCO audio.

## 7. MEDIA_COUNT
Repeated Halo request:
BC 41 02 00 01 13 02 04
Repeated response:
BC 41 0B 00 A0 E9 02 04 16 00 0A 00 04 00 01 00 00
Three identical observations.
Alternative source says [02 04] contains image/video/record counts, but exact offsets must be established.
BLE count is documented as flaky; Wi-Fi media.config is authoritative for actual media inventory.

## 8. Notifications
Observed:
BC73 08 discrete command/state/device reports
BC73 05 discrete event family
BC73 0E ordered control parameters. Immediately after connection: `12 01 00 10 05 02 00 0F 0F 03 00 10 0A XX` where XX alternates between 01 and 03.
BC73 02 short event frames
`BC 59` — verified real-time device-originated audio stream. Payloads observed at 40-byte fixed transport size, consistently beginning `4B 41`. `0x4B` is structurally compatible with an Opus TOC byte (config 9 / mono / code 3). However, `0x41` equates to `v=1, p=0, M=16` in strict RFC 6716 Code 3, which contradicts the observed 20ms cadence. Therefore, the native Opus framing remains under strict structural validation. Android MediaCodec successfully decodes this stream into 48kHz mono PCM. **The physical trigger is now known to be autonomous: the glasses initiate it after sending a `0x73` payload `0x03` event.**

BC73 02 examples around audio had final byte increasing like 23,24,26,27,28,29,2A; likely sequence/event counter, not proven audio state.

GATT Services:
Besides `de5bf72a` (primary control), other services are exposed (e.g. `ae01`, `4a02`, `ae3b`, `6e400002`, `fee3`) which might carry audio streams, firmware functions, or Wi-Fi provisioning.

Alternative source voice event IDs (Found in 0x73 payloads):
0x02 photo
0x03 voice start (`ACTION_GPT_UPLOAD` stream begins)
0x0A voice end (`ACTION_GPT_UPLOAD` stream stops)
0x01 capture-related event/mode change

## 9. Evidence rules
For every semantic claim retain raw TX, raw RX, UUID, timestamps/order, operation, repeated capture where possible, state before/after, and confidence.
Never replace raw bytes with SDK enum names.
Preserve unknown fields and CRCs.
Never promote an inferred command to a stable semantic without evidence.

## 10. Open questions
- BC73 field meanings.
- BC41 response status fields.
- TX sequence/handshake fields.

## 11. Resolved questions
- **Actual audio-file recording command:** No distinct container is used. The HTTP server serves `.opus` files that are simply concatenated raw `4B 41` payloads.
- **BC59 codec/sample rate:** Opus config 9 (20ms, SILK-only WB), 48kHz mono. Decoded successfully via Android's `c2.android.opus.decoder`.
- **Exact media-count offsets:** `[02 04]` response `BC 41 0B 00 A0 E9 02 04 ...` has `total = bytesToInt(subData, 7, 9)`, `currIndex = bytesToInt(subData, 9, 11)`.
- **Full P2P role/IP behavior:** `groupOwnerAddress` is usually the phone. The BLE-reported IP from notify `0x08` (bytes 7..10) is the correct glasses IP.
- **Trigger for `BC 59` live audio stream:** Autonomous device-originated event. Voice start (`0x03`) begins the stream; Voice end (`0x0A`) stops it.
