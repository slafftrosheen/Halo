# HeyCyan Generic Wire Framing & Notification Field Map

> [!IMPORTANT]
> **SUPERSEDED HISTORICAL INTERPRETATIONS**:
> Early research labeled incoming frames as pseudo-families (`BC73_0E`, `BC73_08`, `BC73_05`, `BC73_02`, `BC59_28`) by treating bytes 2 as a "subtype byte". Decompiled vendor AAR bytecode disassembly (`LargeDataHandler`, `GlassModelControl`, `CRC16`) proves that bytes 2–3 are a 16-bit Little-Endian payload length, bytes 4–5 are CRC16 LE, and byte 1 is the Action byte (`0x73` = `ACTION_DEVICE_DATA_REPORTING`, `0x41` = `ACTION_GLASSES_CONTROL`).

## 1. Verified Generic Wire Frame

All frames follow the verified vendor wire format:

```text
+--------+--------+---------------+--------------+--------------------+
| 0xBC   | Action | Length (2B LE)| CRC16 (2B LE)| Payload (N Bytes)  |
+--------+--------+---------------+--------------+--------------------+
| Byte 0 | Byte 1 | Bytes 2..3    | Bytes 4..5   | Bytes 6..(6+N-1)   |
+--------+--------+---------------+--------------+--------------------+
```

- **Byte 0**: Always `0xBC` magic header.
- **Byte 1**: Action byte (`0x41` = TX Control, `0x73` = RX Device Data Reporting).
- **Bytes 2..3**: Payload length LE ($N$).
- **Bytes 4..5**: CRC16 LE calculated over payload bytes (polynomial `0xA001`, initial `0xFFFF`).
- **Bytes 6..**: Raw payload ($N$ bytes).

---

## 2. Notification Field Observations (Action `0x73`)

### 14-Byte Payload Notifications ($N = 14$, Former `BC73_0E`)

Header: `BC 73 0E 00 ...` (Length = `0x000E` = 14 bytes LE).

Swipe / Touch examples (Length = 14 bytes):
```text
BC 73 0E 00 5B 6C 12 01 00 10 01 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 AB 63 12 01 00 10 02 02 00 0F 0F 03 00 10 0A 03
```

Panel Double-Tap example:
```text
BC 73 0E 00 9B 78 12 01 00 10 05 02 00 0F 0F 03 00 10 0A 01
```

- Payload length: 14 bytes.
- CRC16 LE: bytes 4–5 (`0x6C5B`, `0x63AB`, `0x789B`).

### 8-Byte Payload Notifications ($N = 8$, Former `BC73_08`)

Header: `BC 73 08 00 ...` (Length = `0x0008` = 8 bytes LE).

Camera Photo example:
```text
BC 73 08 00 0A C7 01 07 00 05 00 03 00 01
```

AI Photo example:
```text
BC 73 08 00 FF 06 01 07 00 06 00 04 00 01
```

- Payload length: 8 bytes.
- CRC16 LE: bytes 4–5 (`0xC70A`, `0x06FF`).

### 5-Byte Payload Notifications ($N = 5$, Former `BC73_05`)

Header: `BC 73 05 00 ...` (Length = `0x0005` = 5 bytes LE).

AI Double-Press example:
```text
BC 73 05 00 9D 66 02 00 1C 02 00
```

- Payload length: 5 bytes.
- CRC16 LE: bytes 4–5 (`0x669D`).

### 2-Byte Payload Notifications ($N = 2$, Former `BC73_02`)

Header: `BC 73 02 00 ...` (Length = `0x0002` = 2 bytes LE).

Examples:
```text
BC 73 02 00 47 59 0B 23
BC 73 02 00 06 9B 0B 24
```

- Payload length: 2 bytes.
- CRC16 LE: bytes 4–5 (`0x5947`, `0x9B06`).

---

## 3. High-Rate Stream Observations (Action `0x59`, Length $N = 40$)

Header: `BC 59 28 00 ...` (Length = `0x0028` = 40 bytes LE).

Repeated high-rate frames observed during audio recording activity. Payload length is 40 bytes per frame.

> [!CAUTION]
> Do not decode `BC 59 28` as PCM or Opus without physical real-device capture confirmation.

---

## 4. Evidence Levels

1. **VENDOR-DOCUMENTED**: SDK guide and header definitions.
2. **VENDOR-IMPLEMENTATION-VERIFIED**: Reconstructed from decompiled vendor AAR bytecode disassembly.
3. **PHYSICAL-CAPTURE-VERIFIED**: Observed in real-device BLE captures.
4. **HARDWARE-VERIFIED**: Confirmed by live physical device TX/RX execution.
5. **CORRELATED / INFERRED**: Unverified operational hypotheses.
