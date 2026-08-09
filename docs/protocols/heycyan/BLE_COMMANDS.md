# HeyCyan BLE Commands — PR-016

**Source:** SDK_FORENSICS.md forensic analysis
**Status:** Documented from reference implementations (CyanBridge `MemoMindPacketEncoder`, SDK README)

---

## Command Encoding Structure

Based on CyanBridge `MemoMindPacketEncoder.kt` and SDK README:

```
[HEADER=0xAA][LENGTH][COMMAND_ID][PAYLOAD...][CHECKSUM]
```

- **HEADER**: 0xAA (start byte)
- **LENGTH**: Total packet length including header, length, command, payload, checksum
- **COMMAND_ID**: Single byte identifying the command
- **PAYLOAD**: Command-specific data (variable length)
- **CHECKSUM**: Simple sum or XOR of preceding bytes (exact algorithm UNKNOWN)

---

## Verified Commands (from CyanBridge `MemoMindPacketEncoder`)

### 1. Set Device Mode

**Command ID:** `0x01` (inferred)

**Payload:** Single byte mode value

| Mode Constant | Byte Value | Operation | Evidence |
|---------------|------------|-----------|----------|
| `QCOperatorDeviceModePhoto` | `0x01` | Take photo | CyanBridge |
| `QCOperatorDeviceModeVideo` | `0x02` | Start video recording | CyanBridge |
| `QCOperatorDeviceModeVideoStop` | `0x03` | Stop video recording | CyanBridge |
| `QCOperatorDeviceModeAudio` | `0x04` | Start audio recording | CyanBridge |
| `QCOperatorDeviceModeAudioStop` | `0x05` | Stop audio recording | CyanBridge |
| `QCOperatorDeviceModeAIPhoto` | `0x06` | AI image generation | CyanBridge |
| `QCOperatorDeviceModeTransfer` | `0x07` | Enable WiFi hotspot | CyanBridge |

**Full packet example (Photo):**
```
AA 04 01 01 [CHECKSUM]
    ↓  ↓  ↓  ↓
  HDR LEN CMD PAYLOAD
```

### 2. Get Device Version Info

**Command ID:** `0x10` (inferred)

**Payload:** None (empty)

**Response:** Version strings (HW, FW, WiFi HW, WiFi FW)

### 3. Get Device MAC Address

**Command ID:** `0x11` (inferred)

**Payload:** None

**Response:** MAC address string

### 4. Setup Device DateTime

**Command ID:** `0x12` (inferred)

**Payload:** Timestamp bytes (format UNKNOWN)

### 5. Get Device Battery

**Command ID:** `0x13` (inferred)

**Payload:** None

**Response:** Event type 0x01 with battery level + charging flag

### 6. Get Device Media Counts

**Command ID:** `0x14` (inferred)

**Payload:** None

**Response:** Event type 0x02 with photo/video/audio counts

### 7. Open WiFi (Transfer Mode)

**Command ID:** `0x07` (same as mode Transfer)

**Payload:** None (uses mode command)

**Response:** Event type 0x05 with SSID + password

---

## Command Definitions for Halo (Verified Only)

```kotlin
enum class HeyCyanVerifiedCommand(val commandId: Byte, val payload: ByteArray) {
    // Mode commands (single byte payload)
    TAKE_PHOTO        (0x01, byteArrayOf(0x01)),
    START_VIDEO       (0x01, byteArrayOf(0x02)),
    STOP_VIDEO        (0x01, byteArrayOf(0x03)),
    START_AUDIO       (0x01, byteArrayOf(0x04)),
    STOP_AUDIO        (0x01, byteArrayOf(0x05)),
    AI_PHOTO          (0x01, byteArrayOf(0x06)),
    ENABLE_TRANSFER   (0x01, byteArrayOf(0x07)),

    // Query commands (no payload)
    GET_VERSION       (0x10, byteArrayOf()),
    GET_MAC_ADDRESS   (0x11, byteArrayOf()),
    SET_DATETIME      (0x12, byteArrayOf()), // payload format UNKNOWN
    GET_BATTERY       (0x13, byteArrayOf()),
    GET_MEDIA_COUNTS  (0x14, byteArrayOf()),

    // Note: Exact command IDs are INFERRED from CyanBridge structure
    // Actual values require live capture validation
}
```

---

## Response Strategy

Every command MUST define:

| Command | Response Characteristic | Matcher | Timeout | Decoding |
|---------|------------------------|---------|---------|----------|
| Mode change | Notification | Event type matches command | 5s | Mode change event |
| Get Version | Notification | Event type 0x03 | 5s | 4 strings |
| Get MAC | Notification | Event type (TBD) | 5s | MAC string |
| Get Battery | Notification | Event type 0x01 | 3s | Level + charging |
| Get Media | Notification | Event type 0x02 | 3s | 3 counts |
| Get Version | Notification | Event type 0x03 | 5s | 4 strings |
| WiFi Credentials | Notification | Event type 0x05 | 5s | SSID + password |

---

## Evidence Sources

| Command | CyanBridge Source | SDK README |
|---------|-------------------|------------|
| Set Mode | `MemoMindPacketEncoder.kt` mode byte mapping | Lines 98-104 |
| Get Version | Not explicit in CyanBridge | Line 91 |
| Get MAC | Not explicit in CyanBridge | Line 92 |
| Set DateTime | Not explicit in CyanBridge | Line 93 |
| Get Battery | `MemoMindGattClient` event 0x01 | Line 94 |
| Get Media | `MemoMindGattClient` event 0x02 | Line 95 |
| WiFi Credentials | `GlassesWiFiHandler` line 64-66 | Line 103 |

---

## Confidence Levels

| Command | Confidence | Notes |
|---------|------------|-------|
| Mode byte values (0x01-0x07) | HIGH | Direct from CyanBridge source |
| Command packet structure | MEDIUM | Inferred from CyanBridge, needs capture |
| Exact command IDs (0x01, 0x10, etc.) | LOW | Not explicitly in source, inferred |
| Response event types (0x01-0x05) | HIGH | Direct from CyanBridge event parsing |
| Response decoding | MEDIUM | Format described, byte layout needs capture |

---

## Unknowns

1. **Exact command IDs** - Only mode byte values are explicit in CyanBridge
2. **Checksum algorithm** - Not visible in source
3. **Full packet framing** - Header/length/checksum positions inferred
4. **Set DateTime payload format** - Not documented
5. **Multi-packet commands** (AI Photo) - Chunking protocol unknown
6. **Command acknowledgment format** - "Required response format" mentioned but not specified
7. **Error response codes** - Fail blocks return mode integer, not structured error

---

## Halo Implementation Notes

For PR-017, implement only **verified commands** with evidence:

```kotlin
// In HeyCyanDeviceController (PR-017)
suspend fun takePhoto(): Result<Unit> = sendVerifiedCommand(HeyCyanVerifiedCommand.TAKE_PHOTO)
suspend fun getBattery(): Result<BatteryState> = sendVerifiedCommandWithResponse(HeyCyanVerifiedCommand.GET_BATTERY)
suspend fun getVersion(): Result<DeviceVersion> = sendVerifiedCommandWithResponse(HeyCyanVerifiedCommand.GET_VERSION)
suspend fun enableTransferMode(): Result<WiFiCredentials> = sendVerifiedCommandWithResponse(HeyCyanVerifiedCommand.ENABLE_TRANSFER)
```

**Do NOT implement:**
- AI Photo (protocol UNKNOWN)
- Raw byte writes
- Any command without evidence reference