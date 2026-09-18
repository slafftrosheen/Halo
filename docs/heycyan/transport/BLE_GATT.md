> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](../../protocols/heycyan/IMPLEMENTATION_STATUS.md).

# HeyCyan BLE GATT Structure

The HeyCyan hardware protocol utilizes multiple BLE GATT services for communication, separating "legacy" (fixed 16-byte) commands from "large data" (BC framing) streams.

## 1. Large Data Service (Serial Port)
This is the primary service for all modern operations, including Audio (Opus) streaming, photo thumbnails, and BC-framed commands. Used by `LargeDataHandler`.

* **Service UUID**: `de5bf728-d711-4e47-af26-65e3012a5dc7`
* **Write Characteristic**: `de5bf72a-d711-4e47-af26-65e3012a5dc7` (Write Without Response)
* **Notify Characteristic**: `de5bf729-d711-4e47-af26-65e3012a5dc7` (Notify)

All data sent to/from this service uses the `0xBC` wire framing with the CRC16 payload checksum.

## 2. Legacy Command Service (Nordic UART variant)
This service handles older commands (such as time sync, legacy touch, etc.). Used by `CommandHandle`.

* **Service UUID**: `6e40fff0-b5a3-f393-e0a9-e50e24dcca9e`
* **Write Characteristic**: `6e400002-b5a3-f393-e0a9-e50e24dcca9e`
* **Read/Notify Characteristic**: `6e400003-b5a3-f393-e0a9-e50e24dcca9e`

All data sent to this service uses the strict 16-byte fixed-length payload format with an 8-bit checksum.

## 3. Device Info Service
Standard BLE Device Information service.

* **Service UUID**: `0000180a-0000-1000-8000-00805f9b34fb`
* **Hardware Revision**: `00002a27-0000-1000-8000-00805f9b34fb`
* **Firmware Revision**: `00002a26-0000-1000-8000-00805f9b34fb`
* **Software Revision**: `00002a28-0000-1000-8000-00805f9b34fb`
