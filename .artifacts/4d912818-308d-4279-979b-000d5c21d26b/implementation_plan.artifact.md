# Plan - HeyCyan TX Wire Capture Infrastructure (PR-018)

Building forensic instrumentation to capture and analyze exact BLE TX writes produced by the existing HeyCyan integration.

## User Review Required

> [!IMPORTANT]
> I will instrument the `HeyCyanGattClient.write` method to capture the exact bytes sent to the glasses. This instrumentation will feed into a new `HeyCyanProtocolCapture` system that can group TX and RX frames into "Transactions" for easier analysis.

## Proposed Changes

### Documentation

#### [MODIFY] [IMPLEMENTATION_STATUS.md](file:///C:/Users/Slaff/Halo/docs/protocols/heycyan/IMPLEMENTATION_STATUS.md)
- Correct notification classification: Move 0x01..0x06 from "Verified from Vendor Documentation" to "Inferred from Source" if they are not in the official PDF.
- Update test status: "206 protocol/driver tests PASS, 1 pre-existing media-sync test FAIL".

---

### Driver & Protocol

#### [MODIFY] [RawHeyCyanFrame.kt](file:///C:/Users/Slaff/Halo/drivers/heycyan/src/main/java/com/halo/drivers/heycyan/codec/RawHeyCyanFrame.kt)
- Add optional fields for `operation` (e.g., "PHOTO") and `writeType` (WRITE, WRITE_NO_RESPONSE).

#### [NEW] [HeyCyanTransaction.kt](file:///C:/Users/Slaff/Halo/drivers/heycyan/src/main/java/com/halo/drivers/heycyan/codec/HeyCyanTransaction.kt)
- Model to group one or more TX frames with subsequent RX frames.
- Supports human-readable "forensic dump" format.

#### [NEW] [HeyCyanProtocolCapture.kt](file:///C:/Users/Slaff/Halo/drivers/heycyan/src/main/java/com/halo/drivers/heycyan/codec/HeyCyanProtocolCapture.kt)
- Singleton-like manager to enable/disable "Correlated Capture Mode".
- Collects `RawHeyCyanFrame` instances and organizes them into `HeyCyanTransaction` based on time/operation window.

#### [MODIFY] [HeyCyanGattClient.kt](file:///C:/Users/Slaff/Halo/drivers/heycyan/src/main/java/com/halo/drivers/heycyan/ble/HeyCyanGattClient.kt)
- Instrument `write()` to report the write type and capture the payload before sending it to the Android Bluetooth stack.

#### [MODIFY] [RealHeyCyanDriver.kt](file:///C:/Users/Slaff/Halo/drivers/heycyan/src/main/java/com/halo/drivers/heycyan/RealHeyCyanDriver.kt)
- Pass the current semantic operation (if known) to the capture system during a command execution.

---

### Test Hooks

#### [NEW] [HeyCyanCaptureHooks.kt](file:///C:/Users/Slaff/Halo/drivers/heycyan/src/main/java/com/halo/drivers/heycyan/debug/HeyCyanCaptureHooks.kt)
- Internal API to trigger standard operations (Photo, Record Start, etc.) specifically for capture purposes.

## Verification Plan

### Automated Tests
- `RawHeyCyanFrameTest`: Verify field preservation and equality.
- `HeyCyanTransactionTest`: Verify TX/RX correlation logic.
- `HeyCyanCaptureTest`: Verify JSONL serialization of frames.

### Manual Verification
1. Open Protocol Lab.
2. Enable "Forensic Capture".
3. Trigger a "Photo" via capture hook.
4. Verify Logcat shows the transaction dump with exact TX bytes and any resulting notifications.
5. Export capture and verify JSONL format.
