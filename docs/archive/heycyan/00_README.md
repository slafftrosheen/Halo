> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# Halo HeyCyan Reverse-Engineering Repository Package

Status: PR-019 evidence package / v0.2 — 2026-08-12.

This package collects the currently useful HeyCyan protocol evidence discovered from:

1. Vendor Android AAR `glasses_sdk_20250723_v01.aar`.
2. Vendor SDK headers (`QCSDKCmdCreator.h`, `QCSDKManager.h`, `OdmBleConstants.h`, `QCVolumeInfoModel.h`, `QCDFU_Utils.h`).
3. Chinese Android SDK development guide.
4. Manual Halo BLE captures and logs.
5. Existing Halo/CyanBridge transfer research.

## Evidence labels

- VERIFIED_VENDOR: directly established from vendor SDK implementation or header/source material.
- VERIFIED_CAPTURE: directly observed in Halo captures.
- CORRELATED: operation and packet family are associated by capture context, but semantic meaning is not independently proven.
- INFERRED: plausible interpretation requiring a controlled experiment.

Do not promote CORRELATED/INFERRED facts to protocol constants without evidence.

## Most important verified finding

The vendor AAR contains the actual framing implementation in `LargeDataHandler.addHeader()`:

```text
byte 0     0xBC
byte 1     action
byte 2..3  payload length, little-endian
byte 4..5  CRC16(payload), little-endian
byte 6..   payload
```

CRC16 implementation:

```text
initial CRC = 0xFFFF
polynomial = 0xA001
LSB-first processing
CRC over payload only
```

Vendor constants:

```text
ACTION_SYNC_TIME             = 0x40
ACTION_GLASSES_CONTROL       = 0x41
ACTION_GLASSES_BATTERY       = 0x42
ACTION_DEVICE_INFO           = 0x43
ACTION_DEVICE_AI_VOICE       = 0x44
ACTION_DEVICE_HEART_BEAT     = 0x45
ACTION_DEVICE_WEAR           = 0x46
ACTION_DEVICE_WEAR_SUPPORT   = 0x47
ACTION_BT_MAC_PROTOCOL       = 0x48
ACTION_OTA_SOC               = 0x49
ACTION_GPT_UPLOAD            = 0x59
ACTION_PICTURE_THUMBNAILS    = 0xFD
ACTION_VOICE_STATUS          = 0x48
ACTION_BT_CONNECT             = 0x49
ACTION_VOLUME_CONTROL        = 0x51
ACTION_SPEAK_SOUND_SWITCH    = 0x52
ACTION_GPT_UPLOAD            = 0x59
ACTION_DEVICE_DATA_REPORTING = 0x73
ACTION_OTA_SOC               = 0xFC
ACTION_PICTURE_THUMBNAILS    = 0xFD
```

Vendor GATT UUIDs in `LargeDataHandler`:

```text
Service:        de5bf728-d711-4e47-af26-65e3012a5dc7
Notify:         de5bf729-d711-4e47-af26-65e3012a5dc7
Write:          de5bf72a-d711-4e47-af26-65e3012a5dc7
```

The vendor write request uses `WRITE_TYPE_NO_RESPONSE`.

## Golden semantic control payloads

The Android SDK guide documents the following `glassesControl()` payloads:

```text
PHOTO          02 01 01
VIDEO_START    02 01 02
VIDEO_STOP     02 01 03
RECORD_START   02 01 08
RECORD_STOP    02 01 0C
AI             02 01 06 <size> <size> 02
MEDIA_COUNT    02 04
```

For the first six fixed commands, the vendor framing produces the golden TX frames documented in `01_WIRE_PROTOCOL_VERIFIED.md`.

## Important correction to earlier analysis

A capture such as:

```text
BC 73 0E 00 9B 78 12 01 00 10 05 02 00 0F 0F 03 00 10 0A 01
```

is NOT a `BC73 subtype 0x0E` frame.

It is:

```text
BC | 73 | 0E 00 | 9B 78 | 14-byte payload
```

where:

```text
0x73 = device-data-reporting action
0x000E = payload length
0x789B = CRC value when interpreted little-endian
payload = 12 01 00 10 05 02 00 0F 0F 03 00 10 0A 01
```

This changes the interpretation of all previous `BC 73 0E` analysis.
