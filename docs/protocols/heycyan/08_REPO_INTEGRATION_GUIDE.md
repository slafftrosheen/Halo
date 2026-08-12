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
