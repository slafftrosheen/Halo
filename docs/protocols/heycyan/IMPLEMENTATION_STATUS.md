# HeyCyan Protocol Implementation Status

**Date:** 2026-08-12  
**Batch:** PR-018 (TX Wire Capture Infrastructure)

---

## 1. Physically Verified from Live Device

### Transport & Framing
- **GATT Command Transport**: Verified writing to `de5bf72a-d711-4e47-af26-65e3012a5dc7`.
- **Wire Frame (0xBC)**: Verified `BC 41 <lenLE> <crcLE> <payload>` framing.
- **CRC16 Algorithm**: Verified `0xA001` polynomial with `0xFFFF` init matches hardware.

### Commands (Action 0x41)
- **Photo Capture (0x01)**: Verified. Device returns ACK and increments internal media counter.
- **Video Toggle (0x02)**: Verified. Single command triggers both start and stop; device returns alternating ACKs.
- **Audio Toggle (0x04)**: Verified. Observed 0x04 trigger and 40-byte control info response.

### Observed Correlations
- **Photo Counter**: `BC 73 08` frame at byte index 7 (payload[1]) increments exactly once per PHOTO command.
- **Video Toggle ACK**: Start returns `... FF FF`, Stop returns `... FF 02`.

---

## 2. Verified from Vendor Documentation

### Notification Fields (SDK Family 0x01..0x06) - INFERRED FROM SOURCE
- **0x01 Battery**: level (byte 1), charging (byte 2)
- **0x02 Media Counts**: photo/video/audio (bytes 1-6, UInt16 LE)
- **0x03 Version**: HW/FW/WiFi strings (length-prefixed)
- **0x04 Mode**: current work mode (byte 1)
- **0x05 WiFi**: SSID/password (length-prefixed)
- **0x06 AI Image**: chunked transfer

---

## 3. Observed from Captures

### Packet Families (Header BC)
- `BC 73 05`: Discrete events (AI double-press)
- `BC 73 08`: Discrete command/state events (Camera, AI long-press)
- `BC 73 0E`: Ordered control parameters (Swipes, Double-tap)
- `BC 73 02`: Short event frames (AI/Recording interaction)
- `BC 59 28`: High-rate long frame data stream (likely Audio/Sensor)

---

## 4. Structurally Decoded

### BC 73 0E (20 bytes)
- `unknown04/05`: bytes 4-5 (candidate checksum/sequence)
- `candidateAction`: byte 10 (changes with swipes)
- `unknown19`: byte 19 (differs between swipe and double-tap)

---

## 5. Correlated but Unverified

- **AI Action** → `BC 73 08` family
- **Recording Start** → `BC 59 28` stream start
- **Swipe Gesture** → `BC 73 0E` family (advancing byte 10)
- **Double Tap** → `BC 73 0E` family (byte 19 == 0x01)
- **AI Double Press** → `BC 73 05` family

---

## 6. Still Unknown

- **Sequence Fields**: Whether the device uses per-packet monotonic counters.
- **Touch Semantics**: Exact mapping of `candidateAction` values to gesture vectors.
- **BC 59 28 Codec**: Audio encoding (Opus/PCM/etc.) and sample rate.
- **Handshake**: Whether an application-level "Hello" packet is required.

---

## 7. Forensic Instrumentation (PR-018)

### TX Capture Path
- Instrument point: `HeyCyanGattClient.write()`
- Metadata captured: Timestamp, Write Type, Characteristic UUID, Raw Bytes.
- Transaction correlation: Enabled via `HeyCyanProtocolCapture`.

### Trace Logs
- Filter: `HALO-TX-TRACE` or `HALO-`
- Provides semantic → dispatch → physical byte visibility.

---

## 8. Test Status

- 238 protocol/driver tests: **PASS** (Includes new `HeyCyanPacketFramerTest`)
