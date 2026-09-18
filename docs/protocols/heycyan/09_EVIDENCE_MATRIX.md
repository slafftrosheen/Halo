> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# Evidence Matrix

| Claim | Evidence | Status |
|---|---|---|
| `0x41` is glasses-control action | Vendor AAR `LargeDataHandler` constant | VERIFIED_VENDOR |
| `0x73` is device-data-reporting action | Vendor AAR constant | VERIFIED_VENDOR |
| Frame starts `BC` | Vendor AAR `addHeader` | VERIFIED_VENDOR |
| Length is 2-byte little-endian | Vendor AAR + `shortToBytes` | VERIFIED_VENDOR |
| CRC is over payload | Vendor AAR `CRC16.calcCrc16(payload)` | VERIFIED_VENDOR |
| CRC polynomial is `0xA001`, initial `0xFFFF` | Vendor AAR CRC16 | VERIFIED_VENDOR |
| BLE service UUID | Vendor AAR | VERIFIED_VENDOR |
| Notify UUID | Vendor AAR | VERIFIED_VENDOR |
| Write UUID | Vendor AAR | VERIFIED_VENDOR |
| Vendor writes with no response | Vendor AAR `WriteRequest` | VERIFIED_VENDOR |
| `02 01 01` is photo semantic command | SDK documentation | VERIFIED_VENDOR |
| `02 01 02` is video start | SDK documentation | VERIFIED_VENDOR |
| `02 01 03` is video stop | SDK documentation | VERIFIED_VENDOR |
| `02 01 08` is recording start | SDK documentation | VERIFIED_VENDOR |
| `02 01 0C` is recording stop | SDK documentation | VERIFIED_VENDOR |
| `02 04` is media-count command | SDK documentation | VERIFIED_VENDOR |
| `BC 73 0E ...` is a 14-byte device-report payload | Frame structure + capture | VERIFIED_VENDOR + VERIFIED_CAPTURE |
| `BC 73 0E` is a special subtype | Earlier interpretation | REJECTED / SUPERSEDED |
| `BC59 28` is audio codec Opus | Current evidence | NOT VERIFIED |
| payload byte 4 of touch report is direction | Current evidence | NOT VERIFIED |
| payload final byte is gesture type | Current evidence | NOT VERIFIED |
