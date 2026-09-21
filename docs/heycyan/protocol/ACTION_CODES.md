# HeyCyan action identifiers in current code

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

Source: [HeyCyanCommandCatalog](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/protocol/HeyCyanCommandCatalog.kt).
Constants alone do not establish executable payloads or device support.

| Identifier | Hex |
|---|---|
| BT MAC | 0x2E |
| Sync time | 0x40 |
| Glasses control | 0x41 |
| Battery | 0x42 |
| Device info | 0x43 |
| AI voice | 0x44 |
| Heartbeat | 0x45 |
| Wear | 0x46 |
| Wear support | 0x47 |
| Voice status | 0x48 |
| BT connect | 0x49 |
| Volume | 0x51 |
| Sound switch | 0x52 |
| GPT upload | 0x59 |
| Device reporting | 0x73 |
| OTA SOC | 0xFC |
| Thumbnails | 0xFD |

Earlier 0x48/0x49 voice-status/BT-connect values did not match this source catalog.
These are Halo's current constants, not an independent vendor/hardware validation.
