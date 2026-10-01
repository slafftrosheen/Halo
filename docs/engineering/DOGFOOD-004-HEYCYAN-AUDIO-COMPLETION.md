# DOGFOOD-004 — HeyCyan Audio Completion + Wearable Turn Latency

**Status:** IMPLEMENTED / COMPILED / UNIT VERIFIED  
**Baseline SHA:** `a10d8d4b4030bd17bd5ac7124d968d0bc21677d5`  
**Date:** 2026-10-01  
**Physical Target:** Samsung SM-G990B2 / Android 16 (Dogfood Trace)

---

## Overview & Objectives

DOGFOOD-004 addresses the speech output completion loop for HeyCyan smart glasses (`glasses speak → Halo hears → Halo thinks → Halo answers IN THE GLASSES`) and wearable turn latency optimization:
1. P0 — Fix HeyCyan Companion association ownership, idempotency, and recovery of existing Android associations.
2. P0 — Finish actual Bluetooth bonding (`createBond()`) and log privacy-safe pairing state telemetry (`HALO_HEYCYAN_AUDIO_STATE`).
3. P0 — Fix speech output route state machine: explicitly handle `Bonding` and `Pending` states (never allow `Bonding` to fall through to `RouteAudioToGlasses`).
4. P0 — Best-effort wearable audio prewarm during STT + cognition latency (`HALO_AUDIO_PREWARM_BEGIN` / `DONE`).
5. P0 — Verify endpoint with ~5s bounded window for Classic Bluetooth media profile activation. Preserve `DISALLOW_PHONE` no-leak contract.
6. P0 — Fix HeyCyan UI truth and remove Compose main-thread SharedPreferences disk IO.
7. P1 — Sherpa ASR 90s warm residency grace override (`WARM_EJECTABLE` + `idleGraceMillis = 90_000L`) and refresh on `EXPECT_ASR` hint.
8. P1 — Fix first-token TTFT measurement in `ProviderTurnExecutor` and emit `HALO_VOICE_TURN_TIMING` end-to-end voice latency summary.

---

## Summary of Changes

### 1. HeyCyan Companion Association Ownership & CDM Recovery
- **Files:** [`HeyCyanAudioPairingCoordinator.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/app/src/main/java/com/halo/app/audio/HeyCyanAudioPairingCoordinator.kt)
- **Refactoring:**
  - Implemented unified idempotent `adoptAssociationInternal` guarded by `adoptMutex` and association ID/MAC deduplication. Processing the same association twice produces 1 repository write, 1 bond decision, 0 second association, and 0 duplicate pairing flow.
  - Implemented recovery of existing Android system associations (`findMatchingSystemAssociation`) prior to launching new CDM UI.
  - Automatically cleans up stale duplicate CDM associations for the same physical device MAC address without unbonding the Bluetooth device.
  - Idempotent `requestAssociation()` checks current state: if `Discovering`, `AwaitingUserConfirmation`, `Bonding`, `Associated`, or `AudioConnected`, redundant system association UI requests are suppressed or refreshed.
  - Android 13+ (API 33+) Activity result `EXTRA_ASSOCIATION` parsing and API 36 `onFailure(error: CharSequence?)` handling.
  - Added reset capability `resetAssociation()` to clear local pairing/CDM records without unbonding BLE.

### 2. Bluetooth Bonding & Telemetry
- **Files:** [`HeyCyanAudioPairingCoordinator.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/app/src/main/java/com/halo/app/audio/HeyCyanAudioPairingCoordinator.kt)
- **Behavior:**
  - Performs explicit Bluetooth bond check (`BOND_BONDED` -> `Associated`, `BOND_NONE` -> `Bonding` + `createBond()`).
  - Added privacy-safe `HALO_HEYCYAN_AUDIO_STATE` transition logging with fields: `state`, `associationId`, `bonded`, `endpointType`, `reason`, `source`.

### 3. Speech Output Route State Machine & Prewarm
- **Files:** [`SpeechOutputRouteController.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/app/src/main/java/com/halo/app/audio/SpeechOutputRouteController.kt), [`HaloSpeechOutputCoordinator.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/app/src/main/java/com/halo/app/audio/HaloSpeechOutputCoordinator.kt)
- **Behavior:**
  - Corrected `prepareRoute`: `Unknown`, `NotAssociated`, `Discovering`, `AwaitingUserConfirmation`, and `Bonding` return `SpeechRoutePreparation.Pending`. `Bonding` is prevented from falling through to `RouteAudioToGlasses`. `Failed` returns `SpeechRoutePreparation.Unavailable`.
  - Increased endpoint verification window timeout from 2s to 5s (`timeoutMs = 5_000L`).
  - Added best-effort wearable prewarm on `ListeningStarted` with source `HeyCyanGlasses`. Prewarm restores association, enables Classic Bluetooth, and refreshes endpoints during STT + cognition latency without sending route commands while the user speaks.
  - Emitted `HALO_AUDIO_PREWARM_BEGIN`, `HALO_AUDIO_PREWARM_STATE`, and `HALO_AUDIO_PREWARM_DONE` telemetry.
  - Strictly preserved `SpeechOutputFallback.DISALLOW_PHONE` contract (failed glasses route fails visibly with `TtsFailed` rather than leaking speech to phone speaker).

### 4. UI Truth & Compose Main-Thread Disk IO Elimination
- **Files:** [`HeyCyanControlScreen.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/app/src/main/java/com/halo/app/ui/screens/devices/HeyCyanControlScreen.kt), [`DevicesScreen.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/app/src/main/java/com/halo/app/ui/screens/devices/DevicesScreen.kt), [`ManualDependencyGraph.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt)
- **Behavior:**
  - Moved `ai_thumbnail_quality` preference ownership to `DevicesViewModel` on `Dispatchers.IO`. Removed all direct `SharedPreferences` reads/writes from `HeyCyanControlScreen` Composable.
  - Updated Audio and Halo Speech UI rows to render state truth (`AudioConnected`, `Associated`, `Bonding`, `AwaitingUserConfirmation`, `NotAssociated`, `Failed`).
  - Audio buttons reflect state action: `Connect audio`, `Connecting audio…`, `Repair audio`, `Reconnect audio`, `Reset audio association`.

### 5. Sherpa ASR 90s Warm Grace & First-Token Telemetry
- **Files:** [`NativeResourceCoordinator.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/NativeResourceCoordinator.kt), [`SherpaOfflineSpeechRecognitionProvider.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/providers/sherpa-stt/src/main/java/com/halo/providers/sherpa/stt/SherpaOfflineSpeechRecognitionProvider.kt), [`ProviderTurnExecutor.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/ProviderTurnExecutor.kt)
- **Behavior:**
  - Added `idleGraceMillis: Long? = null` override to `WorkloadResourcePolicy`. Set Sherpa ASR default policy to `WARM_EJECTABLE` with `idleGraceMillis = 90_000L` (90s).
  - Updated `NativeResourceCoordinator.hint(EXPECT_ASR)` to refresh warm expiry for resident ASR without triggering re-allocation if released.
  - Measured executor-observed TTFT (`timeToFirstTokenMs`) on first streamed token in `ProviderTurnExecutor` when provider metrics omit it.
  - Added `HALO_VOICE_TURN_TIMING` end-to-end voice turn latency summary telemetry.

---

## Verification & Build Results

### Automated Tests
- `./gradlew :sdk:runtime-sdk:test` (35 passed)
- `./gradlew :providers:sherpa-stt:test` (18 passed)
- `./gradlew :host-android:test` (18 passed)
- `./gradlew :drivers:heycyan:test` (206 passed)
- `./gradlew :app:testDebugUnitTest` (168 passed)
- **All 445 unit tests PASSED 100%.**

### Gradle Build & Deployment
- Executed: `./gradlew :app:assembleDebug` -> **SUCCESS**
- Deployed to emulator/device `emulator-5554` via `deploy(ANDROID_APP, app, DEFAULT_ACTIVITY, RUN)`.

---

## Physical Hardware Acceptance Protocol

After installing the APK on the SM-G990B2 physical device:
1. Open the HeyCyan control screen.
2. Confirm audio progresses cleanly through: `NotAssociated` -> approval -> `Bonding` -> `Associated` -> `AudioConnected` with exactly ONE system association.
3. Speak through glasses: *"What is the capital of Latvia?"*
4. Confirm telemetry trace:
   - `HALO-SPEECH_INPUT_ROUTE source=HeyCyanGlasses`
   - `HALO_AUDIO_PREWARM_BEGIN`
   - `HALO_STT_DONE`
   - `HEYCYAN_VENDOR_ROUTE ... GLASSES result=success`
   - `AUDIO_ROUTE_READY target=wearable`
   - Audible speech plays through glasses speakers without phone handset leak.
5. Ask second question within 90s: confirm `resident=true`, `loadMs=0` (saving ~4s STT reload latency).
6. Open/close HeyCyan screen multiple times: confirm 0 Halo-owned StrictMode disk violations.
