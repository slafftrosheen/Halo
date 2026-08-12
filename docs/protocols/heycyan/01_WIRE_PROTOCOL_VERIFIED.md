# HeyCyan Wire Protocol — Verified Core

## 1. Frame format

Vendor AAR implementation (`LargeDataHandler.addHeader`) constructs a six-byte header followed by payload:

```text
+--------+--------+-------------+-------------+----------------+
| 0xBC   | action | length LE  | CRC16 LE    | payload        |
| 1 byte | 1 byte | 2 bytes    | 2 bytes     | N bytes        |
+--------+--------+-------------+-------------+----------------+
```

Total frame length = `6 + payload.length`.

For non-empty payloads the vendor writes payload length and CRC. For a null/empty payload the implementation writes `0xFF 0xFF` at the CRC positions.

## 2. CRC16

Vendor class: `com.oudmon.ble.base.communication.utils.CRC16`.

Algorithm:

```text
crc = 0xFFFF
polynomial = 0xA001
for each payload byte:
    crc ^= byte & 0xFF
    repeat 8 times:
        if (crc & 1) != 0:
            crc = (crc >> 1) ^ 0xA001
        else:
            crc >>= 1
crc &= 0xFFFF
```

The serialized CRC is little-endian because the vendor uses `DataTransferUtils.shortToBytes()`.

## 3. Action constants

```text
0x40  ACTION_SYNC_TIME
0x41  ACTION_GLASSES_CONTROL
0x42  ACTION_GLASSES_BATTERY
0x43  ACTION_DEVICE_INFO
0x44  ACTION_DEVICE_AI_VOICE
0x45  ACTION_DEVICE_HEART_BEAT
0x46  ACTION_DEVICE_WEAR
0x47  ACTION_DEVICE_WEAR_SUPPORT
0x48  ACTION_BT_MAC_Protocol
0x49  ACTION_OTA_SOC
0x4A  ACTION_GPT_UPLOAD
0x4B  ACTION_PICTURE_THUMBNAILS
0x4C  ACTION_VOICE_STATUS
0x4D  ACTION_BT_CONNECT
0x51  ACTION_VOLUME_CONTROL
0x52  ACTION_SPEAK_SOUND_SWITCH
0x73  ACTION_DEVICE_DATA_REPORTING
```

## 4. BLE endpoints

From `LargeDataHandler`:

```text
Service:
de5bf728-d711-4e47-af26-65e3012a5dc7

Notify:
de5bf729-d711-4e47-af26-65e3012a5dc7

Write:
de5bf72a-d711-4e47-af26-65e3012a5dc7
```

The vendor creates a `WriteRequest.getNoRspInstance(...)`, which sets Android `BluetoothGattCharacteristic` write type to `WRITE_TYPE_NO_RESPONSE`.

## 5. Verified control framing

`ACTION_GLASSES_CONTROL = 0x41`.

Therefore:

```text
BC 41 <payload_length LE> <CRC16(payload) LE> <payload>
```

### PHOTO

Payload:

```text
02 01 01
```

CRC:

```text
0x5010
```

Wire:

```text
BC 41 03 00 10 50 02 01 01
```

### VIDEO START

Payload:

```text
02 01 02
```

CRC:

```text
0x5150
```

Wire:

```text
BC 41 03 00 50 51 02 01 02
```

### VIDEO STOP

Payload:

```text
02 01 03
```

CRC:

```text
0x9191
```

Wire:

```text
BC 41 03 00 91 91 02 01 03
```

### RECORD START

Payload:

```text
02 01 08
```

CRC:

```text
0x56D0
```

Wire:

```text
BC 41 03 00 D0 56 02 01 08
```

### RECORD STOP

Payload:

```text
02 01 0C
```

CRC:

```text
0x95D1
```

Wire:

```text
BC 41 03 00 D1 95 02 01 0C
```

### MEDIA COUNT

Payload:

```text
02 04
```

CRC:

```text
0x1301
```

Wire:

```text
BC 41 02 00 01 13 02 04
```

## 6. AI command

Vendor Android documentation describes:

```text
02 01 06 <thumbnailSize> <thumbnailSize> 02
```

with thumbnail size documented as `0..6`.

The frame should be generated using the same `BC 41 / length / CRC16 / payload` framing, but each size should be validated against an actual vendor-generated capture before being treated as a complete independent-control implementation.
