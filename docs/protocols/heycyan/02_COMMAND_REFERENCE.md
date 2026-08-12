# HeyCyan Command and API Reference

## Android semantic controls

```text
PHOTO          02 01 01
VIDEO_START    02 01 02
VIDEO_STOP     02 01 03
RECORD_START   02 01 08
RECORD_STOP    02 01 0C
AI_THUMBNAIL   02 01 06 <size> <size> 02
MEDIA_COUNT    02 04
```

## Objective-C command surface

`QCSDKCmdCreator.h` exposes commands for:

- set device mode
- open Wi-Fi by mode
- set/get video information
- get device Wi-Fi IP
- get/delete media
- set/get audio information
- battery
- version
- peripheral-free check
- DFU initialization, packet transfer, verification and finalization
- OTA file link
- device time synchronization
- thumbnails
- voice heartbeat/wakeup
- wearing detection
- device configuration
- AI speaking mode
- volume get/set
- Bluetooth status get/set
- MAC address

See `research/evidence/vendor/QCSDKCmdCreator.h` for the original declarations.

## Device modes from vendor header/research

The project evidence identifies these semantic modes:

```text
Photo
Video
VideoStop
Transfer
OTA
AIPhoto
SpeechRecognition
Audio
AudioStop
```

Do not assume every mode value is universal across hardware revisions without testing.

## Volume model

`QCVolumeInfoModel` defines three modes:

```text
0x01 Music
0x02 Call
0x03 System
```

with min/max/current fields for each mode.
