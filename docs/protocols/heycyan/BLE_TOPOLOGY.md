> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# HeyCyan BLE Topology — PR-016

**Source:** SDK_FORENSICS.md forensic analysis
**Status:** Documented from reference implementations

---

## Services

| Service | UUID | Type | Purpose | Evidence |
|---------|------|------|---------|----------|
| Primary Control | `7905FFF0-B5CE-4E99-A40F-4B1E122D00D0` | Primary | Command/response/notification | SDK README, `QCSDKSERVERUUID1` |
| Secondary (Nordic UART) | `6e40fff0-b5a3-f393-e0a9-e50e24dcca9e` | Secondary | Alternative data path | SDK README, `QCSDKSERVERUUID2` |

---

## Characteristics (Inferred from Binary)

| Service | Characteristic | UUID | Properties | Purpose | Confidence |
|---------|----------------|------|------------|---------|------------|
| Primary | Command Write | Embedded in binary | WRITE, WRITE_NO_RESPONSE | Send commands | MEDIUM |
| Primary | Notification | Embedded in binary | NOTIFY, INDICATE | Responses, events, status | MEDIUM |
| Primary | Data Transfer | Embedded in binary | NOTIFY | Large payloads (AI images) | MEDIUM |
| Secondary | UART TX | Nordic UART standard | WRITE_NO_RESPONSE | Alt write path | HIGH (standard) |
| Secondary | UART RX | Nordic UART standard | NOTIFY | Alt notify path | HIGH (standard) |

**Note:** Exact characteristic UUIDs are not exposed in SDK headers; they are embedded in the AAR/framework binaries. CyanBridge implementation uses service discovery to find them at runtime.

---

## Discovery Filter

Both iOS and Android scan for these service UUIDs:

```kotlin
// Android (CyanBridge)
val SERVICE_UUIDS = listOf(
    UUID.fromString("7905FFF0-B5CE-4E99-A40F-4B1E122D00D0"),
    UUID.fromString("6e40fff0-b5a3-f393-e0a9-e50e24dcca9e")
)
```

```objective-c
// iOS (QCCentralManager.m)
NSArray<CBUUID*>* uuids = @[
    [CBUUID UUIDWithString:QCSDKSERVERUUID1],
    [CBUUID UUIDWithString:QCSDKSERVERUUID2]
];
```

---

## Connection Parameters

| Parameter | Value | Source |
|-----------|-------|--------|
| Requested MTU | 517 | CyanBridge `MemoMindGattClient.kt` |
| Scan Timeout | 15s (iOS), configurable (Android) | iOS `QCBleDefaultTimeout` |
| Connect Timeout | 6s (iOS), 8s (Android RFCOMM) | iOS `QCBleDefaultConnectTimeout` |
| Auto-reconnect | Yes (unless explicit unbind) | iOS `QCCentralManager`, CyanBridge |

---

## Characteristic Discovery Order

1. Primary Service (`7905FFF0-...`)
   - Find WRITE characteristic → Command characteristic
   - Find NOTIFY/INDICATE characteristic → Notification characteristic
   - Find NOTIFY characteristic → Data Transfer characteristic
2. Secondary Service (`6e40fff0-...`)
   - Nordic UART TX (WRITE_NO_RESPONSE)
   - Nordic UART RX (NOTIFY)

---

## CCCD Setup

For each characteristic with NOTIFY or INDICATE:
1. `setCharacteristicNotification(characteristic, true)`
2. Get CCCD descriptor (`00002902-0000-1000-8000-00805f9b34fb`)
3. Write `ENABLE_NOTIFICATION_VALUE` (0x0001) or `ENABLE_INDICATION_VALUE` (0x0002)
4. Sequential write (one at a time)

**Evidence:** iOS `QCCentralManager` → `QCSDKManager.addPeripheral` → CCCD writes; CyanBridge `MemoMindGattClient.writeNextDescriptor`

---

## Confidence Levels

| Item | Confidence | Notes |
|------|------------|-------|
| Service UUIDs | HIGH | Explicit in SDK README |
| Characteristic UUIDs | MEDIUM | In binaries, not headers |
| Properties | HIGH | Standard BLE patterns |
| MTU | HIGH | Explicit in CyanBridge code |
| CCCD Sequence | HIGH | Explicit in both SDKs |

---

## Unknowns

- Exact characteristic UUIDs (require binary decompilation or live capture)
- Whether Primary Service has additional characteristics
- Whether Data Transfer characteristic uses different UUID or same as Notification
