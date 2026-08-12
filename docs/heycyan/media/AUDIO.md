# HeyCyan Audio Pipeline

The HeyCyan audio pipeline handles voice interactions with the device via BLE. Based on the SDK analysis, the media representation avoids any Ogg framing and instead relies on raw, concatenated Opus frames.

## Audio Upload Stream (`ACTION_GPT_UPLOAD` / `0x59`)
When the physical trigger is pulled (AI Voice Start, `0x73` payload `0x03`), the glasses begin sending continuous audio frames via `BC` packets.
- **Action Code**: `0x59`
- **Format**: Opus (Raw)
- **Ogg Encapsulation**: None
- **Packet Size**: Exactly 40 bytes per Opus frame.

## Opus Configuration
The SDK processes these audio frames natively:
```java
OpusManager(hasHead=false, packetSize=40)
```
Since the `hasHead` flag is false and the packets are strictly 40 bytes, the stream is constructed simply by concatenating the payloads of the incoming `ACTION_GPT_UPLOAD` frames in order.

## Pipeline Integration (Halo)
In Halo, there is **no need to build an Ogg layer**.
The audio pipeline operates as:
1. `ACTION_DEVICE_DATA_REPORTING` (`0x03`) indicates the stream start.
2. Incoming `ACTION_GPT_UPLOAD` frames are extracted (by stripping the `BC 59 ...` header and CRC).
3. The raw 40-byte payloads are immediately pushed into the decoder or Voice Interaction processing.
4. `ACTION_DEVICE_DATA_REPORTING` (`0x0A`) indicates the stream termination.
