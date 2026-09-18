> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](../../protocols/heycyan/IMPLEMENTATION_STATUS.md).

# SDK Class Map (Hardware Entry Points)

## Core Connection Management
- `com.oudmon.ble.base.bluetooth.BleOperateManager`
  - Core BLE operations. Scans, connects, reads/writes characteristics.
  - Maintains `LocalWriteRequest` and `ICommandResponse` notify arrays.
  - Exposes `enableNotifyRequest`.

## Main Protocol Command Layer
- `com.oudmon.ble.base.communication.LargeDataHandler`
  - Central singleton managing logical commands.
  - Defines the core `ACTION_*` protocol bytes.
  - Handles GATT UUIDs:
    - Service: `de5bf728-d711-4e47-af26-65e3012a5dc7`
    - Notify: `de5bf729-d711-4e47-af26-65e3012a5dc7`
    - Write: `de5bf72a-d711-4e47-af26-65e3012a5dc7`

## Notification Listeners
- `com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyListener`
  - Registered to handle `ACTION_DEVICE_DATA_REPORTING` (115 / 0x73).
- `com.oudmon.ble.base.communication.responseImpl.InnerCameraNotifyListener`
  - Registered for camera notifications.
- `com.oudmon.ble.base.communication.responseImpl.DeviceSportNotifyListener`
  - Registered for sport notifications (possibly step counter?).

## Utilities
- `com.oudmon.ble.base.communication.utils.CRC16`
  - CRC algorithm implementation.
- `com.oudmon.ble.base.communication.utils.ByteUtil`
- `com.oudmon.qc_utils.bytes.DataTransferUtils`
