# HeyCyan Audio Architecture — PR-016

**Source:** SDK_FORENSICS.md forensic analysis
**Status:** Documented from reference implementations

---

## Executive Summary

**The HeyCyan glasses do NOT provide live microphone audio over BLE.**

Audio is **recorded locally on the glasses**, stored as files, then transferred via the WiFi media transfer path after recording stops.

This is a critical architectural finding for Halo: **no live `AudioSource` from glasses over BLE**.

---

## What the SDK Documents

| Feature | Description | Evidence |
|---------|-------------|----------|
| Audio Recording | Start/Stop via `QCOperatorDeviceModeAudio` / `AudioStop` | README lines 24, 102-103 |
| Audio Files | Stored on device, counted via `getDeviceMedia` | README line 29 |
| Audio Transfer | Retrieved via WiFi transfer (same as photos/video) | `WIFI_TRANSFER_ARCHITECTURE.md`, `AlbumDownloader.kt` |
| Audio Format | Not documented in SDK | **UNKNOWN** |
| Live Streaming | Not mentioned | **NOT SUPPORTED** |

---

## What CyanBridge Reveals

### `MemoMindAudioBridge.kt`
- Implements audio handling for CyanBridge
- Uses **RFCOMM (Classic Bluetooth)** for audio
- **NOT** BLE characteristic streaming
- References Bluetooth HFP/SCO or custom RFCOMM profile

### `MemoMindRfcommClient.kt`
- Establishes RFCOMM connections to glasses
- Multiple RFCOMM channels attempted
- Audio streaming appears to use RFCOMM channel

### `MemoMindConstants.kt`
- Defines RFCOMM UUIDs for audio
- Separate from BLE service UUIDs

---

## Audio Recording Flow (Verified)

```
1. BLE: setDeviceMode(Audio) → 0x04
   ↓
2. Glasses start recording locally
   ↓
3. User presses button / timeout / BLE: setDeviceMode(AudioStop) → 0x05
   ↓
4. Glasses stop recording, save file (likely OPUS)
   ↓
5. File appears in media counts (getDeviceMedia)
   ↓
6. WiFi Transfer:
   - BLE: setDeviceMode(Transfer) → 0x07
   - Get WiFi credentials (Event 0x05)
   - Connect to hotspot
   - Download media.config
   - Download audio file (HTTP GET)
   ↓
7. Decode audio file (format UNKNOWN, likely OPUS)
```

---

## Audio File Format (Inferred)

| Property | Value | Evidence |
|----------|-------|----------|
| Container | Likely OPUS in OGG or raw OPUS | `AlbumDownloader.kt` filters `.opus` extension |
| Sample Rate | UNKNOWN | Not documented |
| Channels | UNKNOWN (likely mono) | Not documented |
| Bitrate | UNKNOWN | Not documented |
| Duration Limit | UNKNOWN | Not documented |

**File naming pattern (inferred from CyanBridge):**
```
AUD_YYYYMMDD_HHMMSS.opus
```

---

## Live Audio: NOT AVAILABLE

### What Does NOT Exist

| Capability | Status | Evidence |
|------------|--------|----------|
| Live PCM over BLE | NOT SUPPORTED | No audio characteristic in BLE topology |
| Live PCM over RFCOMM | NOT IMPLEMENTED in SDK | CyanBridge uses RFCOMM but for recorded audio transfer |
| Bluetooth HFP (Headset Profile) | NOT DOCUMENTED | No HFP references in SDK |
| Bluetooth A2DP Source | NOT DOCUMENTED | No A2DP references in SDK |
| Real-time microphone stream | NOT AVAILABLE | No streaming characteristic |

### Implication for Halo

**First cognitive loop MUST use phone microphone for live audio.**

The glasses can only provide:
1. Recorded audio files (after-the-fact)
2. Button events (trigger recording start/stop)

---

## Halo Architecture Decision

### PR-020/021 Audio Path

```
COGNITIVE LOOP (live):
  Phone Mic → AudioSource → Halo Runtime
      ↓
  Response → Phone Speaker / TTS

RECORDED AUDIO RETRIEVAL (post-facto):
  Glasses Button → Record → WiFi Transfer → Decode → Halo
```

### AudioSource Implementation

```kotlin
// PR-021: Phone microphone fallback (PRIMARY for live loop)
class PhoneMicrophoneAudioSource : AudioSource {
    private val recorder = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        // ... configure
    }
}

// PR-021: Glasses recorded audio (SECONDARY, post-facto)
class HeyCyanRecordedAudioSource : AudioSource {
    // Downloads recorded audio files via WiFi transfer
    // NOT live - for post-processing only
}
```

---

## Confidence Levels

| Finding | Confidence |
|---------|------------|
| No live BLE audio | HIGH (no audio characteristic in topology) |
| Local recording on glasses | HIGH (SDK documents Audio Start/Stop modes) |
| WiFi transfer for audio | HIGH (same path as photos/video) |
| Audio format = OPUS | MEDIUM (`.opus` extension in manifest) |
| RFCOMM for audio in CyanBridge | HIGH (explicit in `MemoMindAudioBridge`) |
| No HFP/A2DP in SDK | HIGH (no references) |

---

## Unknowns

1. **Exact audio format** (OPUS parameters: sample rate, channels, bitrate)
2. **Maximum recording duration**
3. **Storage capacity for audio on glasses**
4. **Whether audio can be recorded simultaneously with video** (README says no: "Cannot record video and audio simultaneously")
5. **Audio quality settings** (if configurable)
6. **Whether CyanBridge RFCOMM audio is live streaming or file transfer**

---

## Evidence Sources

- SDK README lines 24, 29, 102-103, 176
- `WIFI_TRANSFER_ARCHITECTURE.md` (media transfer includes audio)
- `AlbumDownloader.kt` (downloads audio files)
- CyanBridge `MemoMindAudioBridge.kt`
- CyanBridge `MemoMindRfcommClient.kt`
- CyanBridge `MemoMindConstants.kt` (RFCOMM UUIDs)
- BLE_TOPOLOGY.md (no audio characteristic)

---

## Halo Next Steps

| PR | Action |
|----|--------|
| PR-020 | Document this architecture in AUDIO_ARCHITECTURE.md (done) |
| PR-021 | Implement `PhoneMicrophoneAudioSource` as primary live audio |
| PR-021 | Implement `HeyCyanRecordedAudioSource` for post-facto retrieval |
| PR-022 | Use phone microphone for first cognitive loop |
| PR-022 | Document that glasses live audio is NOT available |

---

**Conclusion:** The glasses are a **recording device with WiFi file transfer**, not a live audio peripheral. Halo's first wearable cognitive loop will use the phone's microphone for live audio input.