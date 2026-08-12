# HeyCyan Protocol Implementation Status

**Date:** 2026-08-12  
**Batch:** PR-017B (Behavioral Protocol Corpus Integration)

---

## 1. Verified from Vendor Documentation

### SDK Commands (Semantic Only)
- `PHOTO` (02 01 01)
- `VIDEO_START` (02 01 02)
- `VIDEO_STOP` (02 01 03)
- `RECORD_START` (02 01 08)
- `RECORD_STOP` (02 01 0C)
- `AI_THUMBNAIL` (02 01 06)
- `MEDIA_COUNT` (02 04)

### Notification Fields (SDK Family 0x01..0x06)
- **0x01 Battery**: level (byte 1), charging (byte 2)
- **0x02 Media Counts**: photo/video/audio (bytes 1-6, UInt16 LE)
- **0x03 Version**: HW/FW/WiFi strings (length-prefixed)
- **0x04 Mode**: current work mode (byte 1)
- **0x05 WiFi**: SSID/password (length-prefixed)
- **0x12 Volume**: music/call/system min/max/current (bytes 8-19)

---

## 2. Observed from Captures

### Packet Families (Header BC)
- `BC 73 05`: Discrete events (AI double-press)
- `BC 73 08`: Discrete command/state events (Camera, AI long-press)
- `BC 73 0E`: Ordered control parameters (Swipes, Double-tap)
- `BC 73 02`: Short event frames (AI/Recording interaction)
- `BC 59 28`: High-rate long frame data stream (likely Audio/Sensor)

---

## 3. Structurally Decoded

### BC 73 0E (20 bytes)
- `unknown04/05`: bytes 4-5 (candidate checksum/sequence)
- `candidateAction`: byte 10 (changes with swipes)
- `unknown19`: byte 19 (differs between swipe and double-tap)

---

## 4. Correlated but Unverified

- **Photo Action** → `BC 73 08` family
- **AI Action** → `BC 73 08` family
- **Recording Start** → `BC 59 28` stream start
- **Swipe Gesture** → `BC 73 0E` family (advancing byte 10)
- **Double Tap** → `BC 73 0E` family (byte 19 == 0x01)
- **AI Double Press** → `BC 73 05` family

---

## 5. Still Unknown

- **TX Wire Framing**: Exact header/length/checksum for outgoing commands.
- **Checksum Algorithm**: Algorithm for bytes 4-5 in BC families.
- **Sequence Fields**: Whether the device uses per-packet monotonic counters.
- **Touch Semantics**: Exact mapping of `candidateAction` values to gesture vectors.
- **BC 59 28 Codec**: Audio encoding (Opus/PCM/etc.) and sample rate.
- **Handshake**: Whether an application-level "Hello" packet is required.
