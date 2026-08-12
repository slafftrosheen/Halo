# HeyCyan Device Events

The HeyCyan glasses asynchronously report physical interactions (e.g., button presses, touch events) to the host using `ACTION_DEVICE_DATA_REPORTING` (`0x73`).

These are framed as standard `BC` large data packets.

## Known Event Payloads (Action 0x73)

| Payload Byte | Hex | Description |
|--------------|-----|-------------|
| `3` | `0x03` | **AI Voice Start** (Physical trigger pulled/activated) |
| `10` | `0x0A` | **AI Voice Stop** (Physical trigger released/deactivated) |

When the user physically activates the glasses' trigger, the glasses immediately transmit `BC 73` with a payload of `0x03`. They then begin transmitting continuous Opus audio frames via `ACTION_GPT_UPLOAD` (`0x59`). When the trigger condition ends (or when the stream concludes), the glasses transmit `BC 73` with a payload of `0x0A` and stop transmitting audio frames.

*Note: The SDK processes this action using `GlassesDeviceNotifyRsp`, which provides the raw `byte[] loadData` directly to the `deviceNotifyListener` attached to `LargeDataHandler`.*
