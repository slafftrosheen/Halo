# AAR Reverse Engineering Notes

## Artifact

`glasses_sdk_20250723_v01.aar`

The AAR contains `classes.jar`, and the relevant implementation classes include:

```text
com.oudmon.ble.base.communication.LargeDataHandler
com.oudmon.ble.base.communication.bigData.bean.GlassModelControl
com.oudmon.ble.base.communication.utils.CRC16
com.oudmon.ble.base.request.WriteRequest
com.oudmon.ble.base.bluetooth.queue.BleConsumer
com.oudmon.ble.base.bluetooth.queue.BleThreadManager
com.oudmon.qc_utils.bytes.DataTransferUtils
```

## `LargeDataHandler.glassesControl`

The implementation constructs a `GlassModelControl` from the supplied payload, calls `getSubData()`, wraps it with `addHeader(65, payload)`, places the resulting bytes in a `BleDataBean`, and queues it through `BleThreadManager`.

`65` decimal is `0x41`, `ACTION_GLASSES_CONTROL`.

## `addHeader`

The method allocates `payload.length + 6` bytes and writes:

```text
0 = 0xBC
1 = action
2..3 = DataTransferUtils.shortToBytes(payload.length)
4..5 = DataTransferUtils.shortToBytes(CRC16.calcCrc16(payload))
6.. = payload
```

`DataTransferUtils.shortToBytes()` writes the low byte first, proving little-endian serialization.

## `WriteRequest`

`getNoRspInstance(service, characteristic)` creates a request with `noRsp=true`.

Its `execute()` method does:

```text
characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
characteristic.setValue(value)
gatt.writeCharacteristic(characteristic)
```

This is the actual Android BLE write path in the vendor AAR.

## `CRC16`

See `research/decompiled/CRC16.javap.txt` for the complete bytecode-derived algorithm.
