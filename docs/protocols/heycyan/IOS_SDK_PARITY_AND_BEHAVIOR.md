# iOS SDK Parity, Behavioral Map & Multi-Transport Findings

## Overview

Analysis of vendor iOS SDK interfaces (`QCSDKManager`, `GlassesWiFiHandler`, `GlassesMediaDownloader`) provides a second vendor API layer above the verified HeyCyan wire protocol.

This document synthesizes iOS API contracts against Android SDK/AAR bytecodes and physical BLE captures, establishing a **three-way evidence triad**:

```text
               VENDOR SEMANTIC API LAYER
        ┌──────────────────────────────────────┐
        │  iOS: setDeviceMode()               │
        │  Android: glassesControl()           │
        └──────────────────┬───────────────────┘
                           │
                           ▼
               VERIFIED WIRE PROTOCOL
        ┌──────────────────────────────────────┐
        │  BC | ACTION | LEN_LE | CRC16 | DATA │
        └──────────────────┬───────────────────┘
                           │
                           ▼
               PHYSICAL CAPTURE CORPUS
        ┌──────────────────────────────────────┐
        │  BC 73 ... (Device reporting)        │
        │  BC 59 ... (High-rate media stream) │
        └──────────────────────────────────────┘
```

---

## 1. Dual-Platform Semantic Operation Parity

The iOS SDK defines `QCOperatorDeviceMode` constants that map 1:1 with the verified Android `02 01 xx` payload bytes:

| iOS Enum Constant | Android AAR Payload | Semantic Device Action | Halo Wire Vector |
| :--- | :--- | :--- | :--- |
| `QCOperatorDeviceModePhoto` | `02 01 01` | Photo Capture | `BC 41 03 00 10 50 02 01 01` |
| `QCOperatorDeviceModeVideo` | `02 01 02` | Video Start | `BC 41 03 00 50 51 02 01 02` |
| `QCOperatorDeviceModeVideoStop` | `02 01 03` | Video Stop | `BC 41 03 00 91 91 02 01 03` |
| `QCOperatorDeviceModeAudio` | `02 01 08` | Record/Audio Start | `BC 41 03 00 D0 56 02 01 08` |
| `QCOperatorDeviceModeAudioStop` | `02 01 0C` | Record/Audio Stop | `BC 41 03 00 D1 95 02 01 0C` |
| `QCOperatorDeviceModeAIPhoto` | `02 01 06 <sz> <sz> 02` | AI Image Processing | `BC 41 06 00 ...` |

---

## 2. AI Multi-Packet Image Transfer Pipeline

The iOS delegate callback `didReceiveAIChatImageData` reveals the operational pipeline for AI queries:
- **Trigger**: AI Command (`BC 41` payload `02 01 06 ...`).
- **Processing Window**: Device processing latency expected at ~5–10 seconds.
- **Transfer Profile**: Large multi-packet BLE stream typically ranging **100 KB – 2 MB**.
- **Reconstruction**: Streamed fragments are reassembled into standard image binary (JPEG/PNG).

> [!NOTE]
> AI query interaction involves full multi-packet binary reconstruction rather than a simple immediate control acknowledge.

---

## 3. Media Counter Semantics & Auto-Emission

iOS API method `getDeviceMedia()` exposes state query fields:
- Photo Count
- Video Count
- Audio Count
- Device Media Type

**Behavioral Note**: Media count updates are automatically emitted by glasses firmware following completion of photo, video, or audio recording operations. This provides a clear correlation target against `BC 73` device reporting notifications.

---

## 4. Candidate GATT Service UUID Matrix

A notable discrepancy exists between documented iOS SDK GATT UUIDs and physical Android AAR / real device captures:

| Profile Origin | Service UUID | RX (Notify) UUID | TX (Write) UUID | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Android AAR & Real Captures** | `de5bf728-d711-4e47-af26-65e3012a5dc7` | `de5bf729-d711-4e47-af26-65e3012a5dc7` | `de5bf72a-d711-4e47-af26-65e3012a5dc7` | Verified by real hardware captures and AAR bytecode. |
| **iOS Documentation** | `7905FFF0-B5CE-4E99-A40F-4B1E122D00D0` | `6e40fff0-b5a3-f393-e0a9-e50e24dcca9e` | — | Documented alternate / legacy / standardized transport service. |

> [!WARNING]
> Do **not** merge or replace UUIDs in production code. Both must be treated as separate candidate transport profiles (e.g. for different hardware models or firmware revisions) until physical multi-device captures confirm runtime negotiation rules.

---

## 5. Command Scheduling & Pacing Guidelines

iOS SDK implementation details provide execution timing parameters for Halo's command scheduler:
- **Inter-Command Delay**: Recommends **200 ms – 500 ms** delay between consecutive BLE writes.
- **Execution Latency**: Photo execution duration is approximately **200 ms**.
- **Guideline**: Halo command queue must enforce pacing buffers rather than bursting back-to-back writes.

---

## 6. Device Lifecycle & Reconnection State Machine

The iOS SDK manages connection persistence via `QCLastConnectedIdentifier` and an explicit 5-state lifecycle:

```text
[QCStateUnbind]
       │
       ▼
[QCStateConnecting] ──(Auto-reconnect interval ~6s)──┐
       │                                            │
       ▼                                            │
[QCStateConnected]                                   │
       │                                            │
       ▼                                            │
[QCStateDisconnecting]                              │
       │                                            │
       ▼                                            │
[QCStateDisconnected] ──────────────────────────────┘
```

- **Persistence**: Connected device identifier stored in `QCLastConnectedIdentifier`.
- **Auto-Reconnect**: Background reconnection loop fires every ~6 seconds when disconnected.
