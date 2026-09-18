> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# HeyCyan Protocol Evidence Correlation

This document tracks physically verified correlations between Halo commands and observed device behavior.

## 1. Photo Capture Pipeline

| Step | Data / Observation | Status |
| :--- | :--- | :--- |
| **Semantic** | `TakePhoto` | Triggered via Protocol Lab UI |
| **Transport** | `HeyCyanGattCommandTransport` | Verified: `de5bf72a-d711-4e47-af26-65e3012a5dc7` |
| **Wire Frame** | `BC 41 03 00 10 50 02 01 01` | Verified (Action 0x41, Mode 0x01) |
| **ACK Response** | `BC 41 05 00 0C 4C 02 01 01 FF FF` | Verified: Matches command payload |
| **State Change** | `BC 73 08` report increment | **Strong Correlation** |

### BC 73 reporting correlation (PHOTO)
The following sequence was observed during repeated PHOTO operations:
1. Initial: `BC 73 08 ... 01 13 00 ...` (Count: 0x13)
2. Photo #1 -> `BC 73 08 ... 01 14 00 ...` (Count: 0x14)
3. Photo #2 -> `BC 73 08 ... 01 15 00 ...` (Count: 0x15)
4. Photo #3 -> `BC 73 08 ... 01 16 00 ...` (Count: 0x16)

**Conclusion**: `BC 73 08` payload index 1 (byte 7 of frame) is a monotonically increasing media counter incremented by the device upon successful photo capture.

## 2. Video Recording Toggle

The device uses a **single wire command** for both starting and stopping video.

| Operation | TX Wire Frame | Observed ACK Response |
| :--- | :--- | :--- |
| **Start Video** | `BC 41 03 00 50 51 02 01 02` | `BC 41 05 00 FC 4C 02 01 02 FF FF` |
| **Stop Video** | `BC 41 03 00 50 51 02 01 02` | `BC 41 05 00 3D CD 02 01 02 FF 02` |

**Conclusion**: The glasses track internal recording state and return different result codes (`FF FF` vs `FF 02`) to indicate the new state.

## 3. Audio Recording Toggle

Similar to video, audio uses a toggle mechanism.

| Operation | TX Wire Frame | Observed Response |
| :--- | :--- | :--- |
| **Audio Toggle** | `BC 41 03 00 D0 53 02 01 04` | `BC 41 28 00 3A 2E 02 01 04 ... <Device Name ASCII>` |

**Observation**: The audio toggle triggers a 40-byte control info response containing the device name and serial. Start/Stop distinction is not yet proven for this command.
