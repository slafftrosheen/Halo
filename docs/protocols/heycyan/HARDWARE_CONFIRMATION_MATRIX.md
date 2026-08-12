# Hardware Confirmation Matrix

Run after the software audit.

| Test | Sequence | Evidence |
|---|---|---|
| Media count | COUNT x3 | BC41 raw request/response |
| Photo | COUNT -> PHOTO -> COUNT | TX/RX + count + media.config |
| Video | COUNT -> VIDEO -> wait -> VIDEO -> COUNT | TX/RX + media.config |
| Audio | COUNT -> AUDIO_ACTION_1 -> COUNT -> AUDIO_ACTION_2 -> COUNT | all BLE frames |
| Voice | physical voice button | 0x03/0x0A + SCO routing |
| Wi-Fi | exact 02 01 04 01 path | BLE + P2P + HTTP |

Record timestamp, raw TX/RX, UUID, operation, state before/after, inventory before/after, confidence.
