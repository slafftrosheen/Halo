> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# HeyCyan BLE Events — PR-016

**Source:** SDK_FORENSICS.md forensic analysis
**Status:** Documented from reference implementations (CyanBridge `MemoMindGattClient`, SDK README)

---

## Event Structure

All events are received via the **Notification characteristic** of the Primary Service (`7905FFF0-...`).

Based on CyanBridge `MemoMindGattClient.kt`, events are identified by the first byte of the notification payload:

```
[EVENT_TYPE][PAYLOAD...]
```

---

## Verified Event Types

| Event Type | Name | Payload Format | Source |
|------------|------|----------------|--------|
| `0x01` | Battery | `[level: UInt8, charging: Bool]` | CyanBridge, README |
| `0x02` | Media Counts | `[photo: UInt16, video: UInt16, audio: UInt16, type: UInt8]` | CyanBridge, README |
| `0x03` | Version Info | `[hw_len][hw_str][fw_len][fw_str][wifi_hw_len][wifi_hw_str][wifi_fw_len][wifi_fw_str]` | CyanBridge, README |
| `0x04` | Mode Change | `[current_mode: UInt8]` | CyanBridge, README |
| `0x05` | WiFi Credentials | `[ssid_len][ssid_bytes][pwd_len][pwd_bytes]` | CyanBridge `GlassesWiFiHandler` |
| `0x06` | AI Image Data | Multi-packet, chunked | CyanBridge, README |
| `0xFF` | Error / Ack | `[error_code: UInt8]` | Inferred |

---

## Event Details

### 1. Battery Event (0x01)

**Trigger:** Response to `GET_BATTERY` command, also periodic updates

**Payload:**
```
Byte 0: 0x01 (event type)
Byte 1: battery_level (0-100)
Byte 2: charging_flag (0 = not charging, 1 = charging)
```

**Decoded:**
```kotlin
data class BatteryEvent(
    val level: Int,      // 0-100
    val isCharging: Boolean
)
```

**Evidence:** CyanBridge `MemoMindGattClient` parsing; README line 165

---

### 2. Media Counts Event (0x02)

**Trigger:** Response to `GET_MEDIA_COUNTS` command, also on media change

**Payload:**
```
Byte 0:  0x02 (event type)
Bytes 1-2: photo_count (UInt16, little-endian)
Bytes 3-4: video_count (UInt16, little-endian)
Bytes 5-6: audio_count (UInt16, little-endian)
Byte 7:  media_type (enum, purpose UNKNOWN)
```

**Decoded:**
```kotlin
data class MediaCountsEvent(
    val photoCount: Int,
    val videoCount: Int,
    val audioCount: Int,
    val mediaType: Int
)
```

**Evidence:** CyanBridge `MemoMindGattClient` parsing; README line 166

---

### 3. Version Info Event (0x03)

**Trigger:** Response to `GET_VERSION` command

**Payload:** Length-prefixed strings
```
Byte 0:  0x03 (event type)
Byte 1:  hw_version_length
Bytes 2..: hw_version_string (UTF-8)
Next:    fw_version_length
Next:    fw_version_string (UTF-8)
Next:    wifi_hw_version_length
Next:    wifi_hw_version_string (UTF-8)
Next:    wifi_fw_version_length
Next:    wifi_fw_version_string (UTF-8)
```

**Decoded:**
```kotlin
data class VersionEvent(
    val hardwareVersion: String,
    val firmwareVersion: String,
    val wifiHardwareVersion: String,
    val wifiFirmwareVersion: String
)
```

**Evidence:** CyanBridge; README line 153 (returns 4 version strings)

---

### 4. Mode Change Event (0x04)

**Trigger:** After `SET_MODE` command, also on mode change failure

**Payload:**
```
Byte 0: 0x04 (event type)
Byte 1: current_mode (matches mode byte values 0x01-0x07)
```

**Decoded:**
```kotlin
data class ModeChangeEvent(
    val currentMode: HeyCyanMode  // 0x01=Photo, 0x02=Video, 0x03=VideoStop, 0x04=Audio, 0x05=AudioStop, 0x06=AIPhoto, 0x07=Transfer
)
```

**Evidence:** CyanBridge; README line 178 ("fail blocks return current device mode")

---

### 5. WiFi Credentials Event (0x05)

**Trigger:** Response to `ENABLE_TRANSFER` (mode 0x07)

**Payload:**
```
Byte 0:  0x05 (event type)
Byte 1:  ssid_length
Bytes 2..: ssid_bytes (UTF-8)
Next:    password_length
Next:    password_bytes (UTF-8)
```

**Decoded:**
```kotlin
data class WiFiCredentialsEvent(
    val ssid: String,
    val password: String
)
```

**Important:** CyanBridge `GlassesWiFiHandler.m` line 66 forces password to `"123456789"` because "glasses return wrong one".

**Evidence:** CyanBridge `GlassesWiFiHandler.m` lines 64-68; SDK `openWifiWithMode` success block

---

### 6. AI Image Data Event (0x06)

**Trigger:** After `AI_PHOTO` command (mode 0x06)

**Payload:** Multi-packet chunked transfer
```
Packet 1:
  Byte 0: 0x06 (event type)
  Byte 1: total_chunks (UInt8)
  Byte 2: chunk_index (UInt8)
  Bytes 3..: image_data_chunk

Packet N:
  Same format, chunk_index increments
```

**Reassembly:** Collect all chunks by index, concatenate.

**Evidence:** CyanBridge; README line 152 ("multi-packet protocol")

---

### 7. Error/Ack Event (0xFF)

**Trigger:** Command failure or acknowledgment

**Payload:**
```
Byte 0: 0xFF (event type)
Byte 1: error_code (0 = success/ack, non-zero = error)
```

**Evidence:** Inferred from "Acknowledgment: Required response format" in README

---

## Halo Event Definitions

```kotlin
sealed class HeyCyanBleEvent {
    data class Battery(val level: Int, val isCharging: Boolean) : HeyCyanBleEvent()
    data class MediaCounts(val photos: Int, val videos: Int, val audios: Int, val type: Int) : HeyCyanBleEvent()
    data class Version(
        val hardware: String,
        val firmware: String,
        val wifiHardware: String,
        val wifiFirmware: String
    ) : HeyCyanBleEvent()
    data class ModeChanged(val mode: HeyCyanMode) : HeyCyanBleEvent()
    data class WiFiCredentials(val ssid: String, val password: String) : HeyCyanBleEvent()
    data class AIImageChunk(val totalChunks: Int, val index: Int, val data: ByteArray) : HeyCyanBleEvent()
    data class AIImageComplete(val fullImage: ByteArray) : HeyCyanBleEvent()
    data class Error(val code: Int) : HeyCyanBleEvent()
    data class Unknown(
        val eventType: Int,
        val rawPayload: ByteArray,
        val characteristic: UUID
    ) : HeyCyanBleEvent()
}

enum class HeyCyanMode(val byteValue: Byte) {
    PHOTO(0x01),
    VIDEO(0x02),
    VIDEO_STOP(0x03),
    AUDIO(0x04),
    AUDIO_STOP(0x05),
    AI_PHOTO(0x06),
    TRANSFER(0x07)
}
```

---

## Decoder Implementation Pattern

```kotlin
class HeyCyanEventDecoder {
    fun decode(
        payload: ByteArray,
        characteristic: UUID,
        timestamp: Long
    ): HeyCyanBleEvent {
        return when (payload.firstOrNull()) {
            0x01 -> decodeBattery(payload)
            0x02 -> decodeMediaCounts(payload)
            0x03 -> decodeVersion(payload)
            0x04 -> decodeModeChange(payload)
            0x05 -> decodeWiFiCredentials(payload)
            0x06 -> decodeAIImageChunk(payload)
            0xFF -> HeyCyanBleEvent.Error(payload.getOrElse(1) { 0 }.toInt())
            else -> HeyCyanBleEvent.Unknown(payload[0].toInt(), payload, characteristic)
        }
    }
    // ... decoder methods
}
```

---

## Confidence Levels

| Event | Confidence | Notes |
|-------|------------|-------|
| Battery (0x01) | HIGH | Direct from CyanBridge parsing |
| Media Counts (0x02) | HIGH | Direct from CyanBridge parsing |
| Version (0x03) | HIGH | Format described in SDK |
| Mode Change (0x04) | HIGH | Direct from CyanBridge parsing |
| WiFi Credentials (0x05) | HIGH | Direct from CyanBridge `GlassesWiFiHandler` |
| AI Image (0x06) | MEDIUM | Multi-packet format inferred |
| Error/Ack (0xFF) | LOW | Inferred only |

---

## Unknowns

1. **Exact payload byte layouts** - Need live capture to verify endianness, string encoding
2. **Additional event types** - May exist beyond 0x01-0x06
3. **Periodic vs solicited events** - Some events may be pushed periodically (battery)
4. **Event sequencing** - Whether events have sequence numbers
5. **Error code meanings** - Beyond "non-zero = error"

---

## Evidence Sources

- `MemoMindGattClient.kt` - event parsing logic
- `GlassesWiFiHandler.m` - WiFi credentials event
- `QCSDKCmdCreator` success/fail blocks - response expectations
- SDK README lines 152-178 - documented events

---

## Halo Mapping (PR-018)

Events map to generic `DeviceContext`:

| HeyCyan Event | Halo DeviceContext Field |
|---------------|--------------------------|
| Battery | `batteryLevel`, `isCharging` |
| MediaCounts | `mediaCounts` |
| Version | `deviceInfo.versions` |
| ModeChanged | `currentMode` (transient) |
| WiFiCredentials | `transferCredentials` |
| AIImageComplete | `lastAIImage` |
| Unknown | Preserved in `protocolUnknownEvents` |

**Do NOT leak** `HeyCyanBleEvent` or `HeyCyanMode` into Runtime. Map at driver boundary.
