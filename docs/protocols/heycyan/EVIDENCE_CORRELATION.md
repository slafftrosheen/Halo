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

### BC 73 reporting correlation
The following sequence was observed during repeated PHOTO operations:
1. Initial: `BC 73 08 ... 01 13 00 ...` (Count: 0x13)
2. Photo #1 -> `BC 73 08 ... 01 14 00 ...` (Count: 0x14)
3. Photo #2 -> `BC 73 08 ... 01 15 00 ...` (Count: 0x15)
4. Photo #3 -> `BC 73 08 ... 01 16 00 ...` (Count: 0x16)

**Conclusion**: `BC 73 08` payload index 1 (byte 7 of frame) is a monotonically increasing media counter incremented by the device upon successful photo capture.

## 2. Candidate Media Counters
- **Field**: `BC 73 08` offset 7.
- **Hypothesis**: Photo Count (or Total Media Count).
- **Confidence**: HIGH.
