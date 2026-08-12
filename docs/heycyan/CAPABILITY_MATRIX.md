# HeyCyan Capability Matrix

This matrix maps the required features of the Halo cognitive runtime to the extracted capabilities of the HeyCyan hardware protocol.

| Capability | Supported | Description |
|------------|-----------|-------------|
| **Voice Activation (Wake)** | ✅ Yes | Hardware trigger pull generates `BC 73` (Payload `0x03`). Handled as an asynchronous notification. |
| **Voice Deactivation (Sleep)** | ✅ Yes | Hardware trigger release generates `BC 73` (Payload `0x0A`). |
| **Audio Capture (Mic)** | ✅ Yes | Glasses stream raw Opus frames (40 bytes each) over `BC 59` after Voice Activation. |
| **Audio Playback (Speaker)** | ⚠️ Partial | Host can switch audio routing to glasses (`BC 82`), but playback happens via Classic Bluetooth A2DP/HFP, not BLE data. |
| **Media Transfer (Photos/Videos)** | ✅ Yes | Media manifest and files are served over an HTTP server hosted on the glasses Wi-Fi (`/files/media.config`, `/files/...`). |
| **Volume Control** | ✅ Yes | Host can read and set music, call, and system volumes via `BC 81`. |
| **Wear Detection** | ✅ Yes | Host can query support (`BC 71`) and set/read status (`BC 70`). |
| **Battery Status** | ✅ Yes | Queried via Legacy `0x42` (66). |
| **Time Sync** | ✅ Yes | Synchronized via `0x40` (64). |
| **OTA / Firmware Updates** | ⚠️ Unknown | Handled by `ACTION_OTA_SOC` (`0xFC`) and Nordic DFU UUIDs, out of scope for core Halo runtime. |
