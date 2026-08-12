# HeyCyan Control Commands

The SDK exposes several specific hardware control and configuration commands through `LargeDataHandler`. These commands are framed as BC packets with the specified Action ID and payload.

## Known Commands

| Action ID | Hex | Name | Payload Example | Description |
|-----------|-----|------|-----------------|-------------|
| 64 | `0x40` | `ACTION_SYNC_TIME` | `01` | Time synchronization with the device. |
| 68 | `0x44` | `ACTION_DEVICE_AI_VOICE` | Write: `02 01` (Wake) or `02 00` (Sleep)<br>Read: `01 00` | Controls or queries the AI voice system wake state. |
| 69 | `0x45` | `ACTION_DEVICE_HEART_BEAT` | `[type] 01` | Sends a keep-alive/heartbeat to the device. |
| 70 | `0x46` | `ACTION_DEVICE_WEAR` | Write: `02 [1/0]`<br>Read: `01 00` | Sets or queries wear detection (in-ear) status. |
| 71 | `0x47` | `ACTION_DEVICE_WEAR_SUPPORT`| `01 00` | Queries if the hardware supports wear detection. |
| 72 | `0x48` | `ACTION_VOICE_STATUS` | `02 [status]` | Sets voice playback state. |
| 73 | `0x49` | `ACTION_BT_CONNECT` | `02 01` | Commands the glasses to open/enable Classic Bluetooth. |
| 81 | `0x51` | `ACTION_VOLUME_CONTROL` | `02 01 [minMusic] [maxMusic] [currMusic] 02 [minCall] [maxCall] [currCall] 03 [sysMin] [sysMax] [sysCurr] [type]` | Configures volume limits and current levels for music, calls, and system sounds. |
| 82 | `0x52` | `ACTION_SPEAK_SOUND_SWITCH` | `02 02` (Phone) or `02 01` (Glasses) | Routes audio to either the phone or the glasses. |
| 252 | `0xFC` | `ACTION_OTA_SOC` | WiFi URL Payload | Sends firmware OTA information or IP commands to the SOC. |

*(Extracted from `LargeDataHandler.java`)*
