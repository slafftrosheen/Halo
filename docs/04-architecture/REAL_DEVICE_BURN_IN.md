# Real-device burn-in acceptance

Status: READY FOR EXECUTION. Source preparation reviewed 2026-09-18.
This document defines the exact manual scenarios to run on an Android phone and optional hardware.
It is not evidence that any scenario has passed until a dated result is recorded against a commit.

## Record before each run

Record:
- Git commit SHA.
- APK/build variant.
- Phone model and Android version.
- HeyCyan firmware/device name if glasses are involved.
- AI provider/model in use.
- Whether Whisper Tiny is installed.
- Whether microphone/Bluetooth permissions were granted before launch.

Use Logcat rather than any in-app developer console.

Recommended filters:

```bash
adb logcat -c
adb logcat -v time HaloStartup:I HaloAutoConnect:I RealHeyCyanDriver:I HALO-TX-TRACE:I HaloAudio:I '*:S'
```

For failures, repeat once with unfiltered Logcat and preserve the timestamped section around the failure.

## A. Core Android lifecycle

### A1. Clean cold start without microphone permission

Setup:
1. Revoke Halo microphone permission in Android settings.
2. Force-stop Halo.
3. Launch Halo.

Expected:
- App reaches Home; it must not stop at a mandatory permission page.
- Home shows that microphone access is off.
- Notebook, Activity, Devices and Settings remain usable.
- Keyboard text submission remains available.
- Tapping a voice action requests microphone permission rather than starting capture.
- Logcat reaches `HaloStartup ... runtime.start.complete`.

Fail if:
- App cannot reach Home.
- Runtime reports startup failure solely because RECORD_AUDIO is denied.
- Voice tap silently does nothing.

### A2. Grant microphone while app is alive

Starting from A1:
1. Tap `Grant microphone access`.
2. Grant permission in the Android dialog.
3. Return to Home.

Expected:
- Permission recovery card disappears without app restart.
- If Whisper is installed, voice action becomes usable immediately.
- If Whisper is not installed, Home shows the Voice setup action instead.
- No second process-level runtime is created.

### A3. Rotation during idle Home

1. Open Home and wait until state is Ready.
2. Rotate portrait -> landscape -> portrait.

Expected:
- App does not reinitialize/dispose runtime engines.
- Current provider and recovery states remain coherent.
- No duplicate speech/TTS or device connection attempt is triggered.
- `HaloRuntime.start()` converges idempotently.

Fail if UI becomes blank, duplicated, stuck in startup, or runtime components report disposal/restart races.

### A4. Background / foreground

1. Open Home.
2. Send app to background for at least 30 seconds.
3. Change network while backgrounded if practical.
4. Return to Halo.

Expected:
- Home resumes without full runtime restart.
- Microphone permission state is refreshed.
- Provider diagnostics refresh on resume/network transition.
- Existing completed response remains readable.

### A5. Process recreation

1. Open a Notebook note and type unsaved text long enough for the editor's recovery/autosave path.
2. Force-stop Halo from Android settings.
3. Relaunch Halo and reopen that note.

Expected:
- Persisted note content/recovery behavior matches Notebook contract.
- Runtime performs a clean process boot.
- No stale physical-device session is presented as READY unless transport reconnect succeeds.

## B. Voice capability

### B1. Whisper missing

1. Remove/rename the local Whisper Tiny model or use a clean install without it.
2. Open Home.
3. Tap voice.

Expected:
- Halo does not invoke Android/OEM cloud SpeechRecognizer as a hidden fallback.
- Home routes to Voice setup.
- Voice screen reports model missing and offers installation.

### B2. Install Whisper without restarting app

1. From B1, install Whisper Tiny from Voice settings.
2. Stay in the same app process.
3. Return to Home.

Expected:
- ReloadableSpeechRecognitionProvider availability flips to ready.
- Home no longer shows `Voice input needs setup`.
- Voice capture can start without app restart.

### B3. Start / stop / rapid re-tap

1. Start voice capture.
2. Stop it before speaking.
3. Start again.
4. Speak a short phrase.
5. During TTS output, tap voice again.

Expected:
- One microphone owner only.
- Stop releases active capture.
- Buffered recognition is not duplicated.
- Tapping during speaking interrupts output then starts listening.
- Logcat `HaloAudio MIC_START/MIC_STOP` pairs sensibly; no runaway repeated starts.

## C. Provider recovery

### C1. Provider unconfigured

1. Select OpenRouter with an empty API key, or another deliberately unconfigured provider.
2. Return Home.

Expected:
- Home shows `AI provider needs attention`.
- `Open Intelligence` navigates to the real configuration surface.
- Keyboard UI remains usable; routing policy is not arbitrarily overridden by Home.

### C2. OpenRouter recovery

1. Enter a valid OpenRouter key.
2. Tap `Save and refresh`.
3. Tap `Check connection`.

Expected:
- Diagnostics and model list refresh in the same process.
- Provider status changes according to actual backend response.
- Home reflects the new status after returning.

### C3. Ollama offline -> online

1. Select Ollama and configure a reachable local endpoint.
2. Stop Ollama or disconnect the network.
3. Observe Home recovery state.
4. Restore network/Ollama without restarting Halo.

Expected:
- Network transition triggers a debounced diagnostics refresh.
- Status recovers when endpoint becomes reachable.
- No parallel storm of diagnostics requests is visible.

## D. Bluetooth prerequisite recovery

### D1. Bluetooth permission denied

1. Revoke Nearby devices/Bluetooth permissions.
2. Open Devices -> Add device.

Expected:
- Pairing shows `Bluetooth access needed`.
- Scan and Connect are blocked.
- `Grant access` invokes the Android permission dialog.
- Rest of Halo remains usable.

### D2. Bluetooth permission grant with radio already on

Starting from D1 with Bluetooth already enabled:
1. Grant permissions.
2. Return to Pairing.

Expected:
- Platform monitor explicitly refreshes adapter state even though no radio state broadcast occurs.
- Pairing becomes `Ready to scan` without app restart.

### D3. Bluetooth off -> on while Pairing remains visible

1. Start on Pairing with Bluetooth on.
2. Turn Bluetooth off from Quick Settings.
3. Do not navigate away from Pairing.
4. Turn Bluetooth back on.

Expected:
- Pairing changes to `Bluetooth is off` immediately after platform state change.
- Active scan is cancelled.
- When Bluetooth returns, Pairing becomes ready again without leaving the screen.
- No duplicate scanner is created.

## E. HeyCyan session and controls

### E1. Clean pair and registry identity

1. Pair the BW-AG1 Pro from Add device.
2. Wait for READY.
3. Return to Devices.

Expected:
- Saved record resolves as `DeviceKind.HEYCYAN_GLASSES`.
- Display name is the device/user-facing name, not the string `HEYCYAN_GLASSES`.
- Device detail route opens HeyCyanControlScreen.

### E2. Upgrade migration for previously broken identity

Use an install containing a record from the earlier constructor-order bug if available.

Expected after startup:
- Record is migrated from the bad shape
  `deviceType=<Bluetooth name>, displayName=HEYCYAN_GLASSES`
  to canonical `deviceType=HEYCYAN_GLASSES`.
- Device remains address-identical and opens the correct HeyCyan route.

### E3. Auto-connect after process start

1. Save glasses with Auto-connect ON.
2. Force-stop Halo.
3. Keep Bluetooth on and glasses powered.
4. Relaunch Halo.

Expected:
- Phone runtime starts normally.
- Optional device coordinator detects the saved opt-in and constructs HeyCyan host.
- Logcat includes `HaloAutoConnect AUTOCONNECT_COORDINATOR_START`.
- Session reaches READY without opening Devices first.

### E4. Glasses off -> on with Auto-connect

1. Start from a saved Auto-connect ON record.
2. Power glasses off and wait for disconnect/error.
3. Power glasses back on / make them available.
4. If Android Bluetooth radio itself remains on, use normal driver reconnect evidence; if radio is toggled off/on, the coordinator must explicitly retry.

Expected:
- Session must never remain visually READY while transport is gone.
- Reconnect attempts are serialized by DeviceSessionManager.
- READY is shown only after session handshake/state sync completes.

### E5. Auto-connect toggle ON

1. Set Auto-connect OFF.
2. Disconnect glasses.
3. Set Auto-connect ON from device detail.

Expected:
- Toggle persistence updates immediately.
- Coordinator performs an immediate retry for that device; no app restart or Bluetooth toggle required.
- Logcat includes `AUTOCONNECT_TRIGGER ... source=user_toggle`.

### E6. Photo

1. Ensure session state is READY.
2. Tap Take photo once.

Expected code path:
`DevicesViewModel -> HeyCyanSession -> HeyCyanCapabilityController ->
DefaultHeyCyanCommandExecutor -> HeyCyanGattCommandTransport -> GATT write`.

Expected evidence:
- `HALO-TX-TRACE` shows requested command and GATT TX.
- UI enters capture transition and does not accept duplicate capture taps.
- Success requires the corresponding device completion/state evidence; a write alone is not sufficient acceptance.

### E7. Video start / stop

1. READY session.
2. Start video.
3. Wait for recording state.
4. Stop video.

Expected:
- Start/stop controls are gated by actual ready/recording transitions.
- `HALO-TX-TRACE` contains the verified start/stop command transmissions.
- Unsupported/contradictory state requests are rejected visibly, not silently.

### E8. Audio record start / stop

Same structure as E7.

Expected:
- Video/audio mutual-exclusion checks are respected.
- No UI state claims recording unless device/session state supports it.

### E9. Media count

Trigger media inventory from the currently exposed flow.

Expected:
- Semantic operation reaches `GetMediaInventory`.
- Verified BLE command path is used.
- Result must come from device response/state, not from a hardcoded count.

Note: `HeyCyanEmbodimentTransport.send(String/ByteArray)` is currently not the semantic
command path and remains unsupported. Current accepted controls use the controller ->
evidence-backed GATT command executor path above. Do not wire generic raw-send UI to those stubs.

## F. BuddyBot auto-connect

### F1. Auto-connect OFF

1. Save/configure BuddyBot.
2. Leave Auto-connect OFF.
3. Restart Halo and change Wi-Fi/network.

Expected:
- Network callbacks must not construct/connect BuddyBot solely because a saved record exists.

### F2. Auto-connect ON

1. Enable Auto-connect.
2. Disconnect network/BuddyBot.
3. Restore network.

Expected:
- Immediate toggle-on performs one retry.
- Later network transition performs a debounced retry.
- Session manager prevents overlapping connect attempts.
- SafetySupervisor remains the final command authority after reconnect.

## Pass criteria

A burn-in pass is accepted only if:
- No app restart is required for recoverable permission, network, Bluetooth or speech-model changes.
- UI state never claims READY/recording/connected without corresponding runtime/session state.
- No visible control resolves to a no-op.
- No duplicate microphone, GATT or WebSocket ownership is observed.
- Failures leave the app navigable and expose a recovery action where one exists.
- Every hardware command acceptance includes both UI/session evidence and wire/device evidence.

Record failures as:
`scenario -> timestamp -> observed UI -> relevant Logcat -> expected behavior -> reproduction rate`.
