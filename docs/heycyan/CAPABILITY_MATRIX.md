# HeyCyan capability entry point

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

Use [current implementation status](../protocols/heycyan/IMPLEMENTATION_STATUS.md).
The previous Yes/Partial matrix combined vendor possibilities with Halo support and
contained decimal-to-hex transcription errors.

The actual catalog labels battery/version/MAC/media inventory/Wi-Fi/BLE entries implemented,
photo/video/audio recording/media download limited, date/time and AI photo blocked, and
live input unsupported. Those metadata labels are not physical verification and can be stale:
live input code exists elsewhere. Resolve the contradiction, not the wording alone.

Use HeyCyanCommandCatalog for action bytes: volume=0x51, sound switch=0x52,
wear=0x46, wear support=0x47. Time-sync action=0x40 does not establish a valid payload.
