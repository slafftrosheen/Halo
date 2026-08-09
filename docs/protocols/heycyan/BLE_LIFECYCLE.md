# HeyCyan BLE Connection Lifecycle — PR-016

**Source:** SDK_FORENSICS.md forensic analysis
**Status:** Documented from reference implementations

---

## iOS Lifecycle (QCCentralManager + QCSDKManager)

### 1. Scan Phase
```
QCCentralManager.scan()
    ↓
scanForPeripheralsWithServices([QCSDKSERVERUUID1, QCSDKSERVERUUID2])
    ↓
didDiscoverPeripheral → collect peripherals
    ↓
scanTimeout (15s default) → stopScan
    ↓
didScanPeripherals delegate callback
```

### 2. Connect Phase
```
connect(peripheral)
    ↓
connectPeripheral with options:
  - CBConnectPeripheralOptionNotifyOnConnectionKey = YES
  - CBConnectPeripheralOptionNotifyOnDisconnectionKey = YES
  - (iOS 13+) CBConnectPeripheralOptionEnableTransportBridgingKey = YES
    ↓
reconnectTimer (6s default, repeats)
    ↓
didConnectPeripheral
    ↓
QCSDKManager.addPeripheral(peripheral)
```

### 3. Service Discovery + CCCD (QCSDKManager.addPeripheral)
```
peripheral.discoverServices(nil)
    ↓
didDiscoverServices
    ↓
for each service:
    discoverCharacteristics(nil)
    ↓
for each characteristic:
    if PROPERTY_NOTIFY or PROPERTY_INDICATE:
        setCharacteristicNotification(characteristic, true)
        ↓
        get CCCD descriptor (00002902-0000-1000-8000-00805f9b34fb)
        ↓
        writeDescriptor(CCCD, ENABLE_NOTIFICATION_VALUE or ENABLE_INDICATION_VALUE)
        ↓
        sequential (one at a time via writeNextDescriptor)
    ↓
all CCCD done → state = Ready
    ↓
didReady delegate callback
```

### 4. Connected State
```
QCStateConnected
    ↓
Commands can be sent via QCSDKCmdCreator
    ↓
Notifications received via onCharacteristicChanged
```

### 5. Disconnect / Reconnect
```
Explicit unbind:
  remove()
    ↓
  cancelPeripheralConnection
    ↓
  QCStateUnbind
    ↓
  clear QCLastConnectedIdentifier

Unexpected disconnect:
  didDisconnectPeripheral (not QCStateDisconnecting)
    ↓
  removeAllPeripheral
    ↓
  QCStateConnecting
    ↓
  startToReconnect (uses QCLastConnectedIdentifier)
    ↓
  connect(lastPeripheral) → repeat
```

### 6. State Machine
```
QCStateUnbind
    ↓ (scan + connect)
QCStateConnecting
    ↓ (didConnect + service discovery + CCCD)
QCStateConnected
    ↓ (service discovery done)
QCStateReady
    ↓ (disconnect)
QCStateDisconnecting
    ↓ (complete)
QCStateUnbind / QCStateDisconnected
```

---

## Android Lifecycle (CyanBridge / MemoMindGattClient)

### 1. Scan Phase
```
HeyCyanBleScanner.scan()
    ↓
BluetoothLeScanner.startScan(filters, settings, callback)
    ↓
onScanResult → filter by service UUIDs
    ↓
collect DiscoveredBleDevice
```

### 2. Connect Phase
```
HeyCyanGattClient.connect(macAddress)
    ↓
state = Connecting
    ↓
device.connectGatt(context, false, gattCallback)
    ↓
onConnectionStateChange(STATE_CONNECTED)
    ↓
gatt = created Gatt
    ↓
requestMtu(517)
```

### 3. Service Discovery + CCCD
```
onMtuChanged(517, SUCCESS)
    ↓
discoverServices()
    ↓
onServicesDiscovered(SUCCESS)
    ↓
for each service:
    for each characteristic:
        report via tracer.onDiscoveredCharacteristic
        if PROPERTY_NOTIFY or PROPERTY_INDICATE:
            setCharacteristicNotification(true)
            queue CCCD write
    ↓
writeNextDescriptor (sequential)
    ↓
onDescriptorWrite(SUCCESS)
    ↓
next descriptor...
    ↓
all done → state = Ready
    ↓
tracer.onReady()
```

### 4. Connected State
```
DriverState.Ready
    ↓
Commands via write(characteristic, payload)
    ↓
Notifications via onCharacteristicChanged → tracer.onRx + incomingBytes flow
```

### 5. Disconnect
```
disconnect()
    ↓
gatt.disconnect()
    ↓
awaitDisconnected (5s timeout)
    ↓
clearGatt
    ↓
state = Disconnected
```

### 6. State Machine
```
DriverState.Disconnected
    ↓ (scan)
DriverState.Scanning
    ↓ (connect)
DriverState.Connecting
    ↓ (onConnectionStateChange CONNECTED)
DriverState.Connected (MTU requested)
    ↓ (onMtuChanged)
DriverState.Connected (discovering)
    ↓ (onServicesDiscovered)
DriverState.Connected (CCCD writes)
    ↓ (all CCCD done)
DriverState.Ready
    ↓ (disconnect)
DriverState.Disconnecting
    ↓ (awaitDisconnected)
DriverState.Disconnected
```

---

## Key Differences

| Aspect | iOS | Android |
|--------|-----|---------|
| Scan filter | Service UUIDs at scan time | Post-scan filter |
| MTU request | After connect, before discover | After connect, before discover |
| CCCD write | Sequential via QCSDKManager | Sequential via writeNextDescriptor |
| Reconnect | Automatic (unless unbind) | Manual (CyanBridge logic) |
| State callbacks | Delegate (QCCentralManagerDelegate) | Flow (StateFlow) |
| Peripheral persistence | NSUserDefaults (QCLastConnectedIdentifier) | Not persisted |

---

## Timing Constraints (from SDK)

| Operation | Timeout | Notes |
|-----------|---------|-------|
| Scan | 15s (iOS default) | Configurable |
| Connect | 6s (iOS), 8s (Android RFCOMM) | Repeats on iOS |
| MTU negotiation | Included in connect | 517 requested |
| Service discovery | ~2-5s typical | Android: onServicesDiscovered |
| CCCD writes | Sequential, ~100-500ms each | One at a time |
| Total connect-to-ready | ~10-20s typical | Varies by device |

---

## Error Handling

| Failure Point | iOS Behavior | Android Behavior |
|---------------|--------------|------------------|
| Connect timeout | Retry via reconTimer | Fail, no auto-retry |
| Service discovery fail | didFailConnected delegate | state = Error |
| CCCD write fail | Not explicitly handled | Retry next descriptor |
| Unexpected disconnect | Auto-reconnect (if bound) | Manual reconnect needed |
| MTU request fail | Proceed to discoverServices | Proceed to discoverServices |

---

## Evidence Sources

- `QCCentralManager.m` (iOS scan/connect/disconnect/reconnect)
- `QCSDKManager` (iOS service discovery + CCCD - inferred from delegate flow)
- `MemoMindGattClient.kt` (Android full connect/discover/CCCD sequence)
- `HeyCyanBleScanner.kt` (Android scan)
- `HeyCyanGattClient.kt` (Halo's current implementation - aligned with Android pattern)

---

## Confidence

| Item | Confidence |
|------|------------|
| iOS scan/connect sequence | HIGH |
| iOS service discovery/CCCD | HIGH (delegate flow explicit) |
| Android scan/connect | HIGH |
| Android service discovery/CCCD | HIGH (code explicit) |
| Reconnect logic | HIGH (iOS), MEDIUM (Android) |
| Timing values | HIGH (explicit constants) |