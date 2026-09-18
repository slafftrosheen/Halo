> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# Vendor SDK Command Reference

Source: Qingcheng/QC Wireless Android glasses SDK usage document, version 1.0.0.

## 1. Connection / notification API

The SDK exposes:

```kotlin
BleScannerHelper.getInstance().scanDevice(context, uuid, callback)
BleScannerHelper.getInstance().stopScan(context)
BleScannerHelper.getInstance().scanTheDevice(context, macAddress, callback)

BleOperateManager.getInstance().connectDirectly(address)
BleOperateManager.getInstance().connectWithScan(address)
BleOperateManager.getInstance().unBindDevice()
BleOperateManager.getInstance().setNeedConnect(boolean)
BleOperateManager.getInstance().setBluetoothTurnOff(boolean)
BleOperateManager.getInstance().disconnect()
```

Device notifications are registered through:

```kotlin
LargeDataHandler.getInstance().addOutDeviceListener(100, deviceNotifyListener)
```

The SDK example dispatches on:

```kotlin
response.loadData[6]
```

## 2. Documented notification types

| `loadData[6]` | Meaning | Documented fields |
|---:|---|---|
| `0x02` | Glasses triggered quick recognition / AI image recognition | `loadData[9] == 0x02` is used in the sample; thumbnail retrieval follows |
| `0x03` | Glasses microphone / speaking event | `loadData[7] == 1` indicates microphone/speaking start |
| `0x04` | OTA progress | `[7]` download progress, `[8]` SoC download progress, `[9]` NOR upgrade progress |
| `0x05` | Battery report | `[7]` battery, `[8]` charging state |
| `0x0C` | Glasses-triggered pause event for voice playback | `[7] == 1` |
| `0x0D` | Unbind-app event | `[7] == 1` |
| `0x0E` | Glasses memory insufficient | no fields documented |
| `0x12` | Volume-change event | detailed field map below |

### Volume notification

The vendor documents:

```text
[8]  music minimum
[9]  music maximum
[10] music current

[12] call minimum
[13] call maximum
[14] call current

[16] system minimum
[17] system maximum
[18] system current
[19] current volume mode
```

This is an important semantic anchor for future packet correlation.

## 3. Documented `glassesControl()` commands

### Photo

```kotlin
glassesControl(byteArrayOf(0x02, 0x01, 0x01))
```

The callback reports the current glasses work mode.

### Video

Start:

```kotlin
glassesControl(byteArrayOf(0x02, 0x01, 0x02))
```

Stop:

```kotlin
glassesControl(byteArrayOf(0x02, 0x01, 0x03))
```

### Audio recording

Start:

```kotlin
glassesControl(byteArrayOf(0x02, 0x01, 0x08))
```

Stop:

```kotlin
glassesControl(byteArrayOf(0x02, 0x01, 0x0C))
```

### AI / image recognition / thumbnail reporting

The documented form is:

```kotlin
glassesControl(
    byteArrayOf(
        0x02,
        0x01,
        0x06,
        thumbnailSize,
        thumbnailSize,
        0x02
    )
)
```

`thumbnailSize` is documented as `0..6`.

### Media counts

```kotlin
LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x04))
```

The callback exposes:

```text
imageCount
videoCount
recordCount
```

and the sample sums them to determine unsynchronized media.

## 4. Work-mode values

The SDK callback interprets `workTypeIng` as:

| Value | Meaning |
|---:|---|
| `1` | Photo mode |
| `2` | Video recording |
| `4` | Transfer mode |
| `5` | OTA mode |
| `6` | Photo mode |
| `7` | AI conversation |
| `8` | Audio recording |

The SDK treats `dataType == 1` and `errorCode == 0` as a successful response before using this work-mode information.

## 5. What is NOT established

The document does not provide the raw BLE wire framing for these calls. It documents the SDK API payload passed to `glassesControl()`.

Therefore:

```text
SDK command: 02 01 01
```

must not automatically be assumed to mean:

```text
BLE packet: 02 01 01
```

The wire-level encapsulation still has to be established from TX captures or SDK implementation.
