> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# Vendor AAR Reverse Engineering & Verified HeyCyan Protocol Findings

## Provenance

The primary authoritative reference for the HeyCyan wire protocol is derived from the supplied vendor SDK AAR artifact:

```text
research/evidence/vendor/glasses_sdk_20250723_v01.aar
```

Disassembled bytecodes and javap symbol dumps are preserved in:

```text
research/decompiled/
```

This research and implementation was performed for reverse engineering and interoperability purposes under contract and constitution rules.

---

## Decompiled Bytecode Evidence

### 1. Header Assembly: `LargeDataHandler.addHeader`
Vendor class: `com.oudmon.ble.base.communication.LargeDataHandler`

Method disassembly establishes the 6-byte header construction:
- Byte 0: `0xBC` (Frame start marker)
- Byte 1: `action` (Protocol action byte)
- Bytes 2..3: `length` (16-bit Little-Endian payload byte count)
- Bytes 4..5: `crc16` (16-bit Little-Endian payload CRC16 checksum)
- Bytes 6..: `payload` (Raw payload bytes)

### 2. CRC16 Checksum Algorithm: `CRC16.calcCrc16`
Vendor class: `com.oudmon.ble.base.communication.utils.CRC16`

- Initial CRC value: `0xFFFF`
- Polynomial: `0xA001`
- Calculation Scope: **Calculated over PAYLOAD ONLY** (excluding header bytes `0xBC`, `action`, `length`, or `crc`).
- Serialization: Little-Endian byte order via `DataTransferUtils.shortToBytes()`.

### 3. Action Identifiers
Key vendor action constants:
- `0x41`: `ACTION_GLASSES_CONTROL` (Host -> Glasses control commands)
- `0x73`: `ACTION_DEVICE_DATA_REPORTING` (Glasses -> Host notifications & status reports)
- `0x40`: `ACTION_SYNC_TIME`
- `0x42`: `ACTION_GLASSES_BATTERY`
- `0x43`: `ACTION_DEVICE_INFO`
- `0x51`: `ACTION_VOLUME_CONTROL`

### 4. GATT Services and Characteristics
From `LargeDataHandler`:
- **Service UUID**: `de5bf728-d711-4e47-af26-65e3012a5dc7`
- **RX Characteristic (Notify)**: `de5bf729-d711-4e47-af26-65e3012a5dc7`
- **TX Characteristic (Write / Write No Response)**: `de5bf72a-d711-4e47-af26-65e3012a5dc7`

---

## Verified Golden Command Vectors (`ACTION_GLASSES_CONTROL = 0x41`)

| Command | Action | Payload | Payload Length | CRC16 (LE) | Complete Wire Frame |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **PHOTO** | `0x41` | `02 01 01` | 3 (`03 00`) | `0x5010` (`10 50`) | `BC 41 03 00 10 50 02 01 01` |
| **VIDEO START** | `0x41` | `02 01 02` | 3 (`03 00`) | `0x5150` (`50 51`) | `BC 41 03 00 50 51 02 01 02` |
| **VIDEO STOP** | `0x41` | `02 01 03` | 3 (`03 00`) | `0x9191` (`91 91`) | `BC 41 03 00 91 91 02 01 03` |
| **RECORD START**| `0x41` | `02 01 08` | 3 (`03 00`) | `0x56D0` (`D0 56`) | `BC 41 03 00 D0 56 02 01 08` |
| **RECORD STOP** | `0x41` | `02 01 0C` | 3 (`03 00`) | `0x95D1` (`D1 95`) | `BC 41 03 00 D1 95 02 01 0C` |
| **MEDIA COUNT** | `0x41` | `02 04` | 2 (`02 00`) | `0x1301` (`01 13`) | `BC 41 02 00 01 13 02 04` |

---

## Superseded Interpretations

- **Former Pseudo-Family Hypothesis**: Previous documentation referred to `BC 73 0E` as a "subtype 0E packet family".
- **Verified Correction**: `0x73` is `ACTION_DEVICE_DATA_REPORTING`, and `0E 00` is the little-endian payload length (14 bytes). The packet structure is:
  ```text
  BC 73 <length: 0E 00> <CRC16 LE> <payload: 14 bytes>
  ```
