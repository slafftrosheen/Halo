# HeyCyan Action Codes

The following action codes represent the primary logical commands in the HeyCyan protocol as defined in `LargeDataHandler.java`.

| Hex | Decimal | Symbol | Description |
|-----|---------|--------|-------------|
| `0x2E` | 46 | `ACTION_BT_MAC_Protocol` | Request/Response for Bluetooth MAC address. |
| `0x40` | 64 | `ACTION_SYNC_TIME` | Sync time with the device. |
| `0x41` | 65 | `ACTION_GLASSES_CONTROL` | Send hardware control commands (e.g. photo, video). |
| `0x42` | 66 | `ACTION_GLASSES_BATTERY` | Query or receive battery status. |
| `0x43` | 67 | `ACTION_DEVICE_INFO` | Query or receive device info. |
| `0x44` | 68 | `ACTION_DEVICE_AI_VOICE` | AI Voice communication state. |
| `0x45` | 69 | `ACTION_DEVICE_HEART_BEAT` | Keep-alive or health check. |
| `0x46` | 70 | `ACTION_DEVICE_WEAR` | In-ear/wear detection status. |
| `0x47` | 71 | `ACTION_DEVICE_WEAR_SUPPORT` | Query wear detection capability. |
| `0x48` | 72 | `ACTION_VOICE_STATUS` | Voice status/config. |
| `0x49` | 73 | `ACTION_BT_CONNECT` | Bluetooth connection negotiation/handshake. |
| `0x51` | 81 | `ACTION_VOLUME_CONTROL` | Set/query volume level. |
| `0x52` | 82 | `ACTION_SPEAK_SOUND_SWITCH` | Audio routing or speaker toggle. |
| `0x59` | 89 | `ACTION_GPT_UPLOAD` | Live audio stream (Opus packets) from device. |
| `0x73` | 115 | `ACTION_DEVICE_DATA_REPORTING` | Asynchronous hardware events (buttons, triggers). |
| `0xFC` | -4 (252) | `ACTION_OTA_SOC` | OTA firmware update stream. |
| `0xFD` | -3 (253) | `ACTION_PICTURE_THUMBNAILS` | Thumbnail transfer over BLE. |

*(Extracted from `com.oudmon.ble.base.communication.LargeDataHandler`)*
