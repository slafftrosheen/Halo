> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# HeyCyan Transport Architecture

## BLE

The vendor Android AAR uses BLE GATT for the large-data/control channel.

Verified primary endpoints (Android AAR & Real Captures):

```text
Service:  de5bf728-d711-4e47-af26-65e3012a5dc7
Notify:   de5bf729-d711-4e47-af26-65e3012a5dc7
Write:    de5bf72a-d711-4e47-af26-65e3012a5dc7
```

Documented secondary candidate endpoints (iOS Documentation):

```text
Service 1: 7905FFF0-B5CE-4E99-A40F-4B1E122D00D0
Service 2: 6e40fff0-b5a3-f393-e0a9-e50e24dcca9e
```

> [!WARNING]
> Do not merge or replace UUID sets in production code. They are tracked as separate candidate transport profiles for different hardware revisions/firmware versions until physical multi-device captures establish runtime discovery logic.

Vendor `WriteRequest.getNoRspInstance()` selects Android `WRITE_TYPE_NO_RESPONSE`.

The Halo capture logs also show GATT MTU negotiation to 517 and successful notification descriptor writes. The historical RFCOMM research, candidate UUIDs, and packet framing are archived for reference in [RFCOMM_RESEARCH_ARCHIVE.md](file:///c:/Users/slavd/HaloEco/Halo/docs/protocols/heycyan/RFCOMM_RESEARCH_ARCHIVE.md); production runtime operates exclusively over BLE GATT.

## Wi-Fi media transfer

Existing CyanBridge research establishes a hybrid architecture:

```text
BLE control
    -> enter transfer/Wi-Fi mode
Wi-Fi Direct / hotspot
    -> obtain device IP
HTTP
    -> media.config / media files
```

The repository's AGENTS documentation warns that `WifiP2pInfo.groupOwnerAddress` is often the phone (`192.168.49.1`) and should not automatically be used as the glasses HTTP target. Prefer a device-reported IP via BLE notify `0x08` or `BleIpBridge` where available. fileciteturn9file12

The transfer implementation uses `/files/media.config`, JPG/MP4/OPUS media, and network binding where required. fileciteturn9file0turn9file12

## Recording files

Existing repository evidence states that many `.opus` files are raw Opus packets rather than Ogg containers; the official app uses `packetSize=40`. This is useful for media-file decoding but must not be conflated with the live `BC 59 28` BLE stream until proven. fileciteturn9file12
