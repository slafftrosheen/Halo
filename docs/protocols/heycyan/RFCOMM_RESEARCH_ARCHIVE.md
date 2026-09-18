> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# HeyCyan RFCOMM Protocol Research Archive

> [!NOTE]
> **Status**: Archived / Non-Production.
> Removed from runtime in HCY-028.
> Authoritative production control transport for HeyCyan smart glasses is BLE GATT (`de5bf728-d711-4e47-af26-65e3012a5dc7`).
> This document preserves the external reverse-engineering research and candidate framing hypotheses for future reference.

---

## 1. Candidate RFCOMM Service UUIDs

The research phase identified three candidate Bluetooth Classic / RFCOMM channels:

| Channel Name | UUID | Role / Hypothesis |
| :--- | :--- | :--- |
| **Primary** | `00001101-0000-1000-8000-00805f9b34fb` | Standard Bluetooth Serial Port Profile (SPP) UUID |
| **Extra** | `00002026-0000-1000-8000-00805f9b34fb` | Secondary vendor-specific channel |
| **Record** | `00002024-0000-1000-8000-00805f9b34fb` | Media/Audio recording transfer candidate |

---

## 2. Candidate Packet Framing (MemoMind Framing)

The legacy RFCOMM framing model observed in the reference reverse-engineering code followed this structure:

```text
+--------+--------+--------+--------+--------+------------------+--------+
| Prefix | Prefix | Length | Length | Command| Payload (0..N)   | Check  |
|  0xFA  |  0x00  | (MSB)  | (LSB)  | 1 byte | N bytes          | 1 byte |
+--------+--------+--------+--------+--------+------------------+--------+
```

### Fields:
1. **Magic Prefix**: 2 bytes: `0xFA`, `0x00`.
2. **Packet Length**: 2 bytes big-endian (unsigned 16-bit integer). Represents the total packet size from magic prefix through checksum byte inclusive (`total_len = 2 (prefix) + 2 (length) + 1 (command) + N (payload) + 1 (checksum) = N + 6`).
3. **Command Byte**: 1 byte operation opcode.
4. **Payload**: 0 to N bytes.
5. **Checksum**: 1 byte arithmetic sum of all preceding bytes modulo 256:
   $$\text{Checksum} = \left( \sum_{i=0}^{\text{len}-2} \text{byte}[i] \right) \pmod{256}$$

### Golden Vectors:
- **GetBattery (Command 0x13, empty payload)**:
  `FA 00 00 06 13 13`
  - Total length = 6 (`00 06`)
  - Checksum = $(0xFA + 0x00 + 0x00 + 0x06 + 0x13) \pmod{256} = 275 \pmod{256} = 19 = 0x13$
- **SetMode Photo (Command 0x01, payload 0x01)**:
  `FA 00 00 07 01 01 03`
  - Total length = 7 (`00 07`)
  - Checksum = $(0xFA + 0x00 + 0x00 + 0x07 + 0x01 + 0x01) \pmod{256} = 259 \pmod{256} = 3 = 0x03$

---

## 3. Candidate Command Opcode Map

The research mapped high-level commands to the following RFCOMM opcodes:

| Operation | Command Code | Payload | Notes |
| :--- | :--- | :--- | :--- |
| `TakePhoto` | `0x01` | `[0x01]` | Set device mode to Photo |
| `StartVideo` | `0x01` | `[0x02]` | Set device mode to Video Recording |
| `StopVideo` | `0x01` | `[0x00]` | Set device mode to Idle |
| `StartRecording` | `0x01` | `[0x03]` | Set device mode to Audio Recording |
| `StopRecording` | `0x01` | `[0x00]` | Set device mode to Idle |
| `EnterWifiTransferMode` | `0x07` | `[]` | Trigger Wi-Fi AP / Transfer mode |
| `GetDeviceInfo` | `0x10` | `[]` | Query version / info |
| `GetBattery` | `0x13` | `[]` | Query battery level & state |
| `GetMediaInventory` | `0x14` | `[]` | Query photo / video / audio counts |

---

## 4. Reason for Removal from Runtime

1. **Unverified for Production**: Live hardware capture analysis demonstrated that the HeyCyan smart glasses communicate primarily and reliably over BLE GATT (`de5bf728-d711-4e47-af26-65e3012a5dc7`).
2. **Protocol Divergence**: Having a secondary RFCOMM fallback with divergent command encoders introduced state divergence and untested transport fallback loops.
3. **Architectural Separation**: Wi-Fi transfer and media synchronization are owned by `HeyCyanMediaSynchronizer` (HTTP over local Wi-Fi), while real-time control is owned by `HeyCyanCommandExecutor` (BLE GATT). RFCOMM had no active production consumer.
