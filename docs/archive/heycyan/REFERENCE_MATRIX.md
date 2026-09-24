> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# HeyCyan Reference Matrix — PR-016

**Source:** SDK_FORENSICS.md forensic analysis
**Status:** Cross-reference of all features against evidence sources

---

## Feature Coverage Matrix

| Feature | HeyCyanSmartGlassesSDK (iOS/Android) | CyanBridge (Kotlin) | Halo Current | Halo Target | Confidence | Next Action |
|---------|--------------------------------------|---------------------|--------------|-------------|------------|-------------|
| **BLE Discovery** | ✅ Service UUIDs, scan with filter | ✅ `MemoMindBleScanner` | ✅ `HeyCyanBleScanner` | Use documented UUIDs | HIGH | No change |
| **BLE Connection** | ✅ Full sequence (QCCentralManager) | ✅ `MemoMindGattClient` | ✅ `HeyCyanGattClient` | Match CCCD sequence | HIGH | Verify MTU 517 |
| **Service Discovery** | ✅ Implicit in QCSDKManager | ✅ Explicit in GattClient | ✅ `onServicesDiscovered` | Auto-discover all | HIGH | No change |
| **CCCD Setup** | ✅ Sequential writes | ✅ `writeNextDescriptor` | ✅ `onDescriptorWrite` | Sequential, one-at-a-time | HIGH | No change |
| **Get Version** | ✅ `getDeviceVersionInfo` | ✅ `MemoMindPacketEncoder` | ❌ Not implemented | PR-017 | HIGH | Add command |
| **Get MAC Address** | ✅ `getDeviceMacAddress` | ❌ Not explicit | ❌ Not implemented | PR-017 | HIGH | Add command |
| **Set DateTime** | ✅ `setupDeviceDateTime` | ❌ Not explicit | ❌ Not implemented | PR-017 (optional) | MEDIUM | Need payload format |
| **Get Battery** | ✅ `getDeviceBattery` | ✅ Event 0x01 | ❌ Not implemented | PR-017 | HIGH | Add command + decoder |
| **Get Media Counts** | ✅ `getDeviceMedia` | ✅ Event 0x02 | ❌ Not implemented | PR-017 | HIGH | Add command + decoder |
| **Take Photo** | ✅ `setDeviceMode(Photo)` | ✅ Mode 0x01 | ❌ Not implemented | PR-019 | HIGH | Add command |
| **Start Video** | ✅ `setDeviceMode(Video)` | ✅ Mode 0x02 | ❌ Not implemented | PR-019 | HIGH | Add command |
| **Stop Video** | ✅ `setDeviceMode(VideoStop)` | ✅ Mode 0x03 | ❌ Not implemented | PR-019 | HIGH | Add command |
| **Start Audio** | ✅ `setDeviceMode(Audio)` | ✅ Mode 0x04 | ❌ Not implemented | PR-019 | HIGH | Add command |
| **Stop Audio** | ✅ `setDeviceMode(AudioStop)` | ✅ Mode 0x05 | ❌ Not implemented | PR-019 | HIGH | Add command |
| **AI Photo** | ✅ `setDeviceMode(AIPhoto)` | ✅ Mode 0x06 | ❌ Not implemented | PR-019 (later) | MEDIUM | Protocol UNKNOWN |
| **Enable Transfer** | ✅ `openWifiWithMode(Transfer)` | ✅ Mode 0x07, `GlassesWiFiHandler` | ❌ Not implemented | PR-021 | HIGH | Add command + WiFi flow |
| **WiFi Credentials** | ✅ Success block returns SSID/pwd | ✅ Event 0x05, `GlassesWiFiHandler` | ❌ Not implemented | PR-021 | HIGH | Add decoder |
| **IP Discovery** | ✅ Candidate IPs list | ✅ `GlassesWiFiHandler.testIPs` | ❌ Not implemented | PR-021 | HIGH | Port algorithm |
| **Media Manifest** | ✅ `fetchMediaManifest` | ✅ `AlbumDownloader.fetchConfig` | ❌ Not implemented | PR-021 | HIGH | Add HTTP client |
| **File Download** | ✅ Concurrent downloads | ✅ `AlbumDownloader.fetchOne` | ❌ Not implemented | PR-021 | HIGH | Add download |
| **Thumbnail Gen** | ✅ Image/Video/Audio | ❌ Not in CyanBridge | ❌ Not implemented | PR-021 (UI) | MEDIUM | Optional for core |
| **Live Audio** | ❌ NOT SUPPORTED | ✅ RFCOMM only (recorded) | ❌ Not implemented | **Phone mic fallback** | HIGH | Document limitation |
| **Recorded Audio** | ✅ Local record + WiFi transfer | ✅ RFCOMM + WiFi download | ❌ Not implemented | PR-021 (post-facto) | HIGH | Add retrieval |
| **Audio Format** | ❌ Undocumented | ❌ Likely OPUS | ❌ Unknown | PR-020/021 | MEDIUM | Verify on download |
| **RFCOMM Audio** | ❌ Not in SDK | ✅ `MemoMindAudioBridge` | ❌ Not implemented | Not for live loop | HIGH | Document only |
| **Reconnect Logic** | ✅ Auto-reconnect (bound) | ✅ Manual in CyanBridge | ✅ `RealHeyCyanDriver` | Match iOS behavior | HIGH | Verify |
| **Disconnect** | ✅ Explicit + unexpected | ✅ `disconnect()` | ✅ `RealHeyCyanDriver` | Clean state reset | HIGH | No change |

---

## Transport Matrix

| Transport | SDK Support | CyanBridge Support | Halo Support | Purpose |
|-----------|-------------|-------------------|--------------|---------|
| BLE (Primary) | ✅ Full control | ✅ `MemoMindGattClient` | ✅ `HeyCyanGattClient` | Commands, events, config |
| BLE (Secondary/UART) | ✅ Alternative | ❌ Not used | ❌ Not used | Backup/unknown |
| RFCOMM (Classic BT) | ❌ Not in SDK | ✅ `MemoMindRfcommClient` | ✅ `HeyCyanRfcommClient` | Audio (CyanBridge only) |
| WiFi (Hotspot) | ✅ Transfer mode | ✅ `GlassesWiFiHandler` | ❌ Not implemented | Media transfer |
| WiFi Direct/P2P | ❌ Not in iOS SDK | ✅ `WifiP2pController` | ❌ Not implemented | Android media transfer |

---

## Confidence Summary

| Category | Confidence | Notes |
|----------|------------|-------|
| BLE Topology (services) | HIGH | Explicit in SDK README |
| BLE Topology (characteristics) | MEDIUM | In binaries, discovered at runtime |
| Connection Lifecycle | HIGH | Full code in both SDKs |
| Command Mode Bytes | HIGH | Direct from CyanBridge |
| Command Packet Framing | MEDIUM | Inferred, needs capture |
| Response Event Types | HIGH | Direct from CyanBridge parsing |
| Response Payload Layout | MEDIUM | Format described, bytes need capture |
| WiFi Transfer Flow | HIGH | Full implementation in both |
| IP Discovery | HIGH | Explicit candidate list |
| Audio Architecture | HIGH | No live BLE audio; recorded only |
| RFCOMM Audio | HIGH | CyanBridge only, not in SDK |

---

## Priority Implementation Order (PR-017 → PR-022)

| PR | Target | Depends On |
|----|--------|------------|
| PR-017 | Verified BLE Control (Version, Battery, Media, MAC) | BLE connection working |
| PR-018 | Verified BLE Events/State (decoder + mapper) | PR-017 commands working |
| PR-019 | Capabilities (Photo, Video, Audio, Transfer) | PR-017/018 |
| PR-020 | Audio/Media Forensics (done - documented) | Analysis complete |
| PR-021 | Media Transport + Audio Adapter (WiFi + phone mic) | PR-019 Transfer command |
| PR-022 | First Cognitive Loop | PR-021 audio + PR-019 capabilities |

---

## Blocking Unknowns (Require Live Capture or Binary Decompilation)

| Unknown | Impact | Workaround |
|---------|--------|------------|
| Exact characteristic UUIDs | Cannot hardcode; must discover | Runtime discovery (current approach) |
| Full command packet framing | Cannot verify checksum/length | Use CyanBridge encoder as reference |
| Exact response byte layout | Cannot write perfect decoder | Use CyanBridge decoder as reference |
| AI Photo multi-packet protocol | Cannot implement AI Photo | Defer to later PR |
| Audio format parameters | Cannot configure decoder | Assume OPUS 16kHz mono, verify on download |
| WiFi password reliability | Glasses may return wrong pwd | Force "123456789" per CyanBridge |

---

## Halo Architecture Compliance Check

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Runtime knows no Android/HeyCyan SDK | ✅ | Driver boundary maintained |
| No arbitrary raw BLE writes exposed | ✅ | `HeyCyanDeviceController` only |
| Unknown packets preserved | ✅ | `HeyCyanBleEvent.Unknown` |
| HeyCyan types don't leak to Runtime | ⚠️ | Mapper needed (PR-018) |
| CapabilityRouter for commands | ⚠️ | PR-019 target |
| AudioSource abstraction | ⚠️ | Phone mic + recorded (PR-021) |
| Frozen runtime not redesigned | ✅ | No runtime changes in PR-016 |

---

## File References

| Document | Path |
|----------|------|
| SDK Forensics | `docs/protocols/heycyan/SDK_FORENSICS.md` |
| BLE Topology | `docs/protocols/heycyan/BLE_TOPOLOGY.md` |
| BLE Lifecycle | `docs/protocols/heycyan/BLE_LIFECYCLE.md` |
| BLE Commands | `docs/protocols/heycyan/BLE_COMMANDS.md` |
| BLE Events | `docs/protocols/heycyan/BLE_EVENTS.md` |
| Audio Architecture | `docs/protocols/heycyan/AUDIO_ARCHITECTURE.md` |
| WiFi Media | `docs/protocols/heycyan/WIFI_MEDIA.md` |
| Reference Matrix | `docs/protocols/heycyan/REFERENCE_MATRIX.md` (this file) |

---

## Acceptance for PR-016 Complete

- [x] SDK artifacts identified
- [x] SDK implementation traced
- [x] CyanBridge implementation traced
- [x] BLE services documented
- [x] BLE characteristics documented
- [x] Connection lifecycle documented
- [x] Commands documented
- [x] Response/event formats documented
- [x] Audio architecture documented
- [x] Wi-Fi/media architecture documented
- [x] Contradictions identified
- [x] Unknowns explicitly listed
- [x] No speculative production protocol code added
- [x] Tests/build remain green (no code added in PR-016)

---

**PR-016 Status: COMPLETE — Ready for PR-017**
