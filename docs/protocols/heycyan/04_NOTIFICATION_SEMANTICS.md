> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# Vendor Notification Semantics

The vendor Android SDK documentation dispatches device notifications using a value at `response.loadData[6]` in its response layer. The following meanings are explicitly documented in the SDK material:

```text
0x02  AI image recognition / quick recognition
0x03  microphone / speaking event
0x04  OTA progress
0x05  battery report
0x0C  voice playback pause
0x0D  unbind-app event
0x0E  memory insufficient
0x12  volume-change event
```

The volume event has documented fields:

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
[19] volume mode
```

Important: these indices are response-layer indices from the SDK, not automatically the same thing as raw wire offsets. First strip the vendor wire header and establish the response payload structure before comparing fields.
