> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# HeyCyan SDK API Parity Audit

**Sources analyzed:**
- HeyCyanSmartGlassesSDK (local: `C:\tmp\HeyCyanSmartGlassesSDK`)
- CyanBridge / Alternative-HeyCyan-App-and-SDK (local: `C:\tmp\Alternative-HeyCyan-App-and-SDK`)
- PR-016 forensic documentation

**Date:** 2026-08-09
**Status:** Complete initial audit

---

## Executive Summary

| Source | Type | Platform | Completeness |
|--------|------|----------|--------------|
| HeyCyanSmartGlassesSDK | Native SDK (AAR + Framework) | Android + iOS | HIGH - Production SDK |
| CyanBridge | Reverse-engineered implementation | Android (Kotlin) | HIGH - Full app implementation |
| Halo (current) | Device driver | Android | PARTIAL - BLE only |

---

## 1. Device Lifecycle Operations

| SDK Operation | HeyCyanSmartGlassesSDK | CyanBridge | Halo Current | Status | Evidence |
|---------------|------------------------|------------|--------------|--------|----------|
| scan | ✅ `QCCentralManager.scan()` | ✅ `MemoMindBleScanner` | ✅ `HeyCyanBleScanner` | IMPLEMENTED | SDK README, `MemoMindBleScanner.kt` |
| connect | ✅ `QCCentralManager.connect()` | ✅ `MemoMindGattClient.connect()` | ✅ `HeyCyanGattClient.connect()` | IMPLEMENTED | `QCCentralManager.m`, `MemoMindGattClient.kt` |
| disconnect | ✅ `QCCentralManager.remove()` | ✅ `MemoMindGattClient.disconnect()` | ✅ `HeyCyanGattClient.disconnect()` | IMPLEMENTED | `QCCentralManager.m`, `MemoMindGattClient.kt` |
| unexpected disconnect handling | ✅ Auto-reconnect | ✅ Manual reconnect | ❌ Partial | PARTIAL | `QCCentralManager.m` lines 212-230, `MemoMindGattClient.kt` |
| reconnect | ✅ Auto-reconnect with persisted ID | ✅ Explicit reconnect logic | ❌ Not implemented | NOT YET | `QCCentralManager.m` `startToReconnect()` |
| bonding/pairing | ❓ Not explicit in SDK | ❓ Not explicit | ❌ Not implemented | NOT YET | Not documented |
| ready state | ✅ `QCStateConnected` → `Ready` | ✅ `DriverState.Ready` | ✅ `DriverState.Ready` | IMPLEMENTED | Both SDKs |

---

## 2. Device Information Operations

| SDK Operation | HeyCyanSmartGlassesSDK | CyanBridge | Halo Current | Status | Evidence |
|---------------|------------------------|------------|--------------|--------|----------|
| getDeviceVersionInfo | ✅ `QCSDKCmdCreator.getDeviceVersionInfo` | ✅ `MemoMindPacketEncoder` (inferred) | ❌ | NOT YET | SDK README line 91, `MemoMindPacketEncoder.kt` |
| getDeviceMacAddress | ✅ `QCSDKCmdCreator.getDeviceMacAddress` | ❌ Not explicit | ❌ | NOT YET | SDK README line 92 |
| setupDeviceDateTime | ✅ `QCSDKCmdCreator.setupDeviceDateTime` | ❌ Not explicit | ❌ | NOT YET | SDK README line 93 |
| getDeviceBattery | ✅ `QCSDKCmdCreator.getDeviceBattery` | ✅ Event 0x01 | ❌ | NOT YET | SDK README line 94, `MemoMindGattClient.kt` |
| getDeviceMedia | ✅ `QCSDKCmdCreator.getDeviceMedia` | ✅ Event 0x02 | ❌ | NOT YET | SDK README line 95, `MemoMindGattClient.kt` |

---

## 3. Camera/Photo Operations

| SDK Operation | HeyCyanSmartGlassesSDK | CyanBridge | Halo Current | Status | Evidence |
|---------------|------------------------|------------|--------------|--------|----------|
| take photo | ✅ `QCOperatorDeviceModePhoto` (0x01) | ✅ Mode 0x01 | ❌ | NOT YET | SDK README line 99, `MemoMindPacketEncoder.kt` |
| AI photo | ✅ `QCOperatorDeviceModeAIPhoto` (0x06) | ✅ Mode 0x06 | ❌ | BLOCKED | SDK README line 104, `MemoMindPacketEncoder.kt` |
| photo result/errors | ✅ Success/fail blocks | ❌ Not explicit | ❌ | NOT YET | SDK README example |

---

## 4. Video Operations

| SDK Operation | HeyCyanSmartGlassesSDK | CyanBridge | Halo Current | Status | Evidence |
|---------------|------------------------|------------|--------------|--------|----------|
| start video | ✅ `QCOperatorDeviceModeVideo` (0x02) | ✅ Mode 0x02 | ❌ | NOT YET | SDK README line 100, `MemoMindPacketEncoder.kt` |
| stop video | ✅ `QCOperatorDeviceModeVideoStop` (0x03) | ✅ Mode 0x03 | ❌ | NOT YET | SDK README line 101, `MemoMindPacketEncoder.kt` |
| video state | ❓ Not explicit | ❌ Not explicit | ❌ | NOT YET | Not documented |
| video availability | ❓ Via `getDeviceMedia` | ❓ Via Event 0x02 | ❌ | NOT YET | Indirect |

---

## 5. Audio Operations

| SDK Operation | HeyCyanSmartGlassesSDK | CyanBridge | Halo Current | Status | Evidence |
|---------------|------------------------|------------|--------------|--------|----------|
| start audio recording | ✅ `QCOperatorDeviceModeAudio` (0x04) | ✅ Mode 0x04 | ❌ | NOT YET | SDK README line 102, `MemoMindPacketEncoder.kt` |
| stop audio recording | ✅ `QCOperatorDeviceModeAudioStop` (0x05) | ✅ Mode 0x05 | ❌ | NOT YET | SDK README line 103, `MemoMindPacketEncoder.kt` |
| recording state | ✅ `recordingAudio` flag | ✅ `MemoMindAudioBridge` | ❌ | NOT YET | `MemoMindAudioBridge.kt` |
| live microphone (BLE) | ❌ NOT SUPPORTED | ❌ NOT SUPPORTED | ❌ | UNSUPPORTED_BY_DEVICE | Forensic analysis |
| recorded audio retrieval | ✅ Via WiFi transfer | ✅ `MemoMindAudioBridge` + WiFi | ❌ | NOT YET | `WIFI_TRANSFER_ARCHITECTURE.md`, `MemoMindAudioBridge.kt` |
| audio format | ❓ Not documented | Likely OPUS (`.opus`) | ❓ | UNKNOWN | `AlbumDownloader.kt` filters `.opus` |

**Critical Finding:** The device does NOT provide live microphone PCM over BLE. Audio is recorded locally on glasses and transferred via WiFi afterward.

---

## 6. WiFi/Media Transfer Operations

| SDK Operation | HeyCyanSmartGlassesSDK | CyanBridge | Halo Current | Status | Evidence |
|---------------|------------------------|------------|--------------|--------|----------|
| enable transfer mode | ✅ `openWifiWithMode(Transfer)` | ✅ Mode 0x07 + `GlassesWiFiHandler` | ❌ | NOT YET | SDK README line 103, `GlassesWiFiHandler.m` |
| WiFi connection | ✅ `NEHotspotConfiguration` (iOS) | ✅ `WifiP2pController` (Android) | ❌ | NOT YET | `GlassesWiFiHandler.m`, `WifiP2pController.kt` |
| IP discovery | ✅ Candidate IP list | ✅ `testIPs` method | ❌ | NOT YET | `GlassesWiFiHandler.m` lines 172-176 |
| media manifest | ✅ `fetchMediaManifest` | ✅ `AlbumDownloader.fetchConfig` | ❌ | NOT YET | `GlassesWiFiHandler.m`, `AlbumDownloader.kt` |
| file download | ✅ Concurrent downloads | ✅ `AlbumDownloader.fetchOne` | ❌ | NOT YET | `GlassesMediaDownloader.m`, `AlbumDownloader.kt` |
| thumbnail generation | ✅ Image/Video/Audio | ❌ Not in CyanBridge | ❌ | NOT YET | `GlassesMediaDownloader.m` |

---

## 7. Media Management

| SDK Operation | HeyCyanSmartGlassesSDK | CyanBridge | Halo Current | Status | Evidence |
|---------------|------------------------|------------|--------------|--------|----------|
| media counts (photo/video/audio) | ✅ `getDeviceMedia` | ✅ Event 0x02 | ❌ | NOT YET | SDK README, `MemoMindGattClient.kt` |
| media listing | ✅ Via manifest | ✅ `AlbumDownloader` | ❌ | NOT YET | `AlbumDownloader.kt` |
| media download | ✅ Concurrent HTTP | ✅ `AlbumDownloader.fetchOne` | ❌ | NOT YET | `GlassesMediaDownloader.m`, `AlbumDownloader.kt` |
| media deletion | ❓ Not documented | ❓ Not in CyanBridge | ❌ | NOT YET | Not documented |
| media synchronization | ❓ Not explicit | ❌ Not in CyanBridge | ❌ | NOT YET | Not documented |
| thumbnail/preview | ✅ Image/Video/Audio | ❌ Not in CyanBridge | ❌ | NOT YET | `GlassesMediaDownloader.m` |

---

## 8. AI/Image Operations

| SDK Operation | HeyCyanSmartGlassesSDK | CyanBridge | Halo Current | Status | Evidence |
|---------------|------------------------|------------|--------------|--------|----------|
| AI photo trigger | ✅ `QCOperatorDeviceModeAIPhoto` (0x06) | ✅ Mode 0x06 | ❌ | BLOCKED | SDK README line 104 |
| AI image transfer | ✅ Multi-packet protocol | ❌ Incomplete in CyanBridge | ❌ | BLOCKED | SDK README line 152, `MemoMindGattClient.kt` |
| chunk reassembly | ✅ Delegate `didReceiveAIChatImageData` | ❌ Not implemented | ❌ | BLOCKED | SDK README line 168 |
| completion handling | ✅ Success/fail blocks | ❌ Not explicit | ❌ | BLOCKED | SDK README |

---

## 9. Transport Protocols

| Transport | HeyCyanSmartGlassesSDK | CyanBridge | Halo Current | Status |
|-----------|------------------------|------------|--------------|--------|
| BLE (Primary) | ✅ Full control | ✅ `MemoMindGattClient` | ✅ `HeyCyanGattClient` | IMPLEMENTED |
| BLE (Secondary/UART) | ✅ Nordic UART | ❌ Not used | ❌ | NOT YET |
| RFCOMM (Classic BT) | ❌ Not in SDK | ✅ `MemoMindRfcommClient` | ✅ `HeyCyanRfcommClient` | IMPLEMENTED (CyanBridge only) |
| WiFi (Hotspot) | ✅ Transfer mode | ✅ `GlassesWiFiHandler`/`WifiP2pController` | ❌ | NOT YET |

---

## 10. Events/Notifications

| Event Type | HeyCyanSmartGlassesSDK | CyanBridge | Halo Current | Status | Evidence |
|------------|------------------------|------------|--------------|--------|----------|
| Battery | ✅ `getDeviceBattery` response | ✅ Event 0x01 | ❌ | NOT YET | `MemoMindGattClient.kt` |
| Media Counts | ✅ `getDeviceMedia` response | ✅ Event 0x02 | ❌ | NOT YET | `MemoMindGattClient.kt` |
| Version Info | ✅ `getDeviceVersionInfo` response | ✅ Event 0x03 (inferred) | ❌ | NOT YET | SDK README |
| Mode Changed | ✅ `setDeviceMode` fail block | ✅ Event 0x04 | ❌ | NOT YET | SDK README line 178 |
| WiFi Credentials | ✅ `openWifiWithMode` success | ✅ Event 0x05 | ❌ | NOT YET | `GlassesWiFiHandler.m` |
| AI Image Data | ✅ Multi-packet | ✅ Event 0x06 | ❌ | BLOCKED | SDK README line 152 |
| Error/Ack | ✅ Required response format | ❌ Not explicit | ❌ | UNKNOWN | SDK README line 147 |

---

## 11. Device State/Mode Management

| State/Mode | HeyCyanSmartGlassesSDK | CyanBridge | Halo Current | Status |
|------------|------------------------|------------|--------------|--------|
| `QCOperatorDeviceModePhoto` | 0x01 | 0x01 | ❌ | READY |
| `QCOperatorDeviceModeVideo` | 0x02 | 0x02 | ❌ | READY |
| `QCOperatorDeviceModeVideoStop` | 0x03 | 0x03 | ❌ | READY |
| `QCOperatorDeviceModeAudio` | 0x04 | 0x04 | ❌ | READY |
| `QCOperatorDeviceModeAudioStop` | 0x05 | 0x05 | ❌ | READY |
| `QCOperatorDeviceModeAIPhoto` | 0x06 | 0x06 | ❌ | BLOCKED |
| `QCOperatorDeviceModeTransfer` | 0x07 | 0x07 | ❌ | READY |

State machine:
```
QCStateUnbind → QCStateConnecting → QCStateConnected → QCStateReady
                    ↓ (disconnect)           ↓ (disconnect)
               QCStateDisconnecting → QCStateDisconnected/Unbind
```

---

## 12. Media Types

| Type | Value | Extensions | Thumbnail | Halo Status |
|------|-------|------------|-----------|-------------|
| Photo | 1 | .jpg, .png, .heic | Image frame | NOT YET |
| Video | 2 | .mov, .mp4, .m4v | First frame | NOT YET |
| Audio | 3 | .opus | Orange placeholder | NOT YET |

---

## 13. Constraints/Limitations

| Constraint | Source | Impact |
|------------|--------|--------|
| Cannot record video and audio simultaneously | SDK README line 176 | Mutex required in Halo |
| Glasses may return wrong WiFi password | CyanBridge `GlassesWiFiHandler.m` line 66 | Force "123456789" |
| AI Photo protocol incomplete | CyanBridge + SDK docs | Cannot implement yet |
| Live BLE microphone not supported | Forensic analysis | Use phone mic for live loop |
| Characteristic UUIDs in binaries only | SDK README | Runtime discovery required |
| Checksum algorithm unknown | Forensic analysis | Placeholder implementation |

---

## 13. Summary Statistics

| Category | Total Operations | Implemented in Halo | Not Yet | Blocked | Unsupported |
|----------|------------------|---------------------|---------|---------|-------------|
| Device Lifecycle | 6 | 3 | 3 | 0 | 0 |
| Device Information | 5 | 0 | 5 | 0 | 0 |
| Camera/Photo | 3 | 0 | 2 | 1 | 0 |
| Video | 3 | 0 | 3 | 0 | 0 |
| Audio | 5 | 0 | 4 | 0 | 1 |
| WiFi/Media Transfer | 6 | 0 | 6 | 0 | 0 |
| Media Management | 5 | 0 | 5 | 0 | 0 |
| AI/Image | 4 | 0 | 1 | 3 | 0 |
| Transport | 4 | 2 | 2 | 0 | 0 |
| Events | 7 | 0 | 6 | 1 | 0 |
| **TOTAL** | **48** | **5** | **37** | **5** | **1** |

---

## 14. Next Actions

1. **Immediate:** Implement BLE Foundation (PR-017) - command system, event system, device controller
2. **Next:** Device State operations - battery, version, MAC, media counts (PR-018)
3. **Then:** Device Operations - photo, video, audio recording, modes (PR-019)
4. **Then:** Media - inventory, recorded audio, media abstraction (PR-020)
5. **Then:** WiFi Transport - transfer mode, IP discovery, downloads (PR-021)
6. **Finally:** Full integration - capabilities, diagnostics, cognitive loop (PR-022)

**Blocked until resolved:**
- AI Photo (protocol incomplete)
- Live BLE microphone (unsupported by device - use phone mic)

**Unknowns requiring live capture/decompilation:**
- Exact characteristic UUIDs
- Full command packet framing/checksum
- Exact response payload byte layouts
- AI Photo multi-packet protocol
- Checksum algorithm
