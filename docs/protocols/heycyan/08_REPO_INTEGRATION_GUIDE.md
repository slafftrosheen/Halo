> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# Repository Integration Guide

Recommended Halo protocol module boundaries:

```text
RawHeyCyanFrame
    ↓
HeyCyanWireFrameCodec
    ├── decode()
    ├── encode()
    └── validateCrc()

HeyCyanGlassesControl
    ├── photo()
    ├── videoStart()
    ├── videoStop()
    ├── recordStart()
    ├── recordStop()
    └── mediaCount()

HeyCyanDeviceReportDecoder
    ↓
semantic response models
```

Keep these separate:

1. raw BLE transport
2. wire framing
3. semantic command payloads
4. device-report payloads
5. high-level Halo actions

The wire codec must preserve unknown payload bytes.

Unknown device-report payloads should remain inspectable instead of being dropped.

Do not replace the vendor SDK transport in production until physical TX captures prove Halo's independent encoder is byte-identical for the relevant operation.
