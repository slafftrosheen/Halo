# HeyCyan Protocol Implementation Status

**Date:** 2026-08-12  
**Batch:** PR-018 (TX Wire Capture Infrastructure)

---

## 1. Verified from Vendor Documentation

### Notification Fields (SDK Family 0x01..0x06) - INFERRED FROM SOURCE
- **0x01 Battery**: level (byte 1), charging (byte 2)
- **0x02 Media Counts**: photo/video/audio (bytes 1-6, UInt16 LE)
- **0x03 Version**: HW/FW/WiFi strings (length-prefixed)
- **0x04 Mode**: current work mode (byte 1)
- **0x05 WiFi**: SSID/password (length-prefixed)
- **0x06 AI Image**: chunked transfer

### Documented Notification Types (loadData[6]) - VERIFIED FROM VENDOR PDF
- **0x02**: AI image recognition / Quick recognition
- **0x03**: Microphone / Speaking event
- **0x04**: OTA progress
- **0x05**: Battery report (level, charging)
- **0x0C**: Voice playback pause
- **0x0D**: Unbind-app event
- **0x0E**: Memory insufficient
- **0x12**: Volume-change event (detailed min/max/current fields)

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

- **Photo Action** → `BC 73 08` family
- **AI Action** → `BC 73 08` family
- **Recording Start** → `BC 59 28` stream start
- **Swipe Gesture** → `BC 73 0E` family (advancing byte 10)
- **Double Tap** → `BC 73 0E` family (byte 19 == 0x01)
- **AI Double Press** → `BC 73 05` family

---

## 6. Still Unknown

- **TX Wire Framing**: Exact header/length/checksum for outgoing commands.
- **Checksum Algorithm**: Algorithm for bytes 4-5 in BC families.
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

### Capture Formats
- **Human-Readable**: `HeyCyanTransaction.dump()` format in Logcat.
- **Forensic Logs**: `TX <uuid> <hex>` and `RX <uuid> <hex>` in Logcat.
- **Machine-Readable**: `RawHeyCyanFrame.toJsonl()` for offline analysis.

---

## 8. Test Status

- 206 protocol/driver tests: **PASS**
- 1 pre-existing media-sync test: **FAIL** (`HeyCyanMediaSynchronizerTest`)
