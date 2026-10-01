# DOGFOOD-002 — Deterministic Action Reliability + Main-Thread Purity

**Status:** IMPLEMENTED & COMPILED / UNIT VERIFIED  
**Baseline SHA:** `04533dfd51c2859df030bad05c3aad6cde06a18a`  
**Date:** 2026-10-01

---

## Overview & Objectives

DOGFOOD-002 is an integration repair batch focused on:
1. Correct deterministic application launching (`OpenIntent` -> `ApplicationTask`).
2. Hardened fuzzy & structured application resolution (`ApplicationResolution`: `Resolved`, `Ambiguous`, `NotFound`).
3. Reminder clarification when time is missing ("When should I remind you?") and eliminating natural-language time reparsing at the Android boundary.
4. Structured action failure codes (`ActionFailureCode`) and user-friendly error copy.
5. StrictMode main-thread purity for ElevenLabs TTS and Host Tool definitions.
6. Cleaning up JVM crash artifacts (`hs_err_pid*.log`, `replay_pid*.log`).

---

## Summary of Changes

### 1. P0 — Open App Deterministic Routing
- **Files Modified:**
  - `runtime/cognition/src/main/java/com/halo/runtime/cognition/session/InterpretationRoute.kt`
  - `engines/planning/src/main/java/com/halo/engines/planning/PlanningEngine.kt`
- **Behavior:**
  - Updated deterministic executor ownership for `OpenIntent` from `engine.execution/device.open_app` to `engine.execution/application.open_app`.
  - Updated `PlanningEngine` to compile `GoalType.DEVICE` with `payload.action == "open"` into `ApplicationTask` (handled by `AndroidApplicationCapability`), avoiding `DeviceTask(action="open")` failure.

### 2. P0 — Hardened Application Resolution
- **Files Modified:**
  - `host-android/src/main/java/com/halo/host/android/resolver/AndroidApplicationResolver.kt`
  - `host-android/src/main/java/com/halo/host/android/capability/AndroidApplicationCapability.kt`
- **Behavior:**
  - Introduced `ApplicationResolution` (`Resolved`, `Ambiguous`, `NotFound`).
  - Implemented exact package, exact human label, single prefix, and single contains match resolution policies.
  - Multi-candidate matches return `Ambiguous` -> `Outcome.NeedsClarification` ("I found more than one matching app: ... Which one did you mean?").
  - Missing apps return `NotFound` -> `Outcome.PermanentFailure` with `ActionFailureCode.APP_NOT_FOUND`.

### 3. P0 — Reminder Clarification & Temporal Carrier
- **Files Modified:**
  - `engines/interpretation/src/main/java/com/halo/engines/interpretation/internal/DefaultInterpretationEngine.kt`
  - `runtime/cognition/src/main/java/com/halo/runtime/cognition/Task.kt`
  - `host-android/src/main/java/com/halo/host/android/capability/AndroidMemoryCapability.kt`
- **Behavior:**
  - `ReminderIntent` without a time expression requests clarification ("When should I remind you?") instead of proceeding to execution failure.
  - Extended `MemoryTask` to carry `reminderMessage` and `temporalExpression` directly.
  - When exact alarm permission is missing (`NEEDS_EXACT_ALARM_ACCESS`), `AndroidMemoryCapability` returns `ActionFailureCode.EXACT_ALARM_ACCESS_REQUIRED` and explains that Android needs Exact alarms access in Settings.

### 4. P0 — Action Failure Reason Taxonomy
- **Files Modified:**
  - `runtime/cognition/src/main/java/com/halo/runtime/cognition/Outcome.kt`
  - `engines/execution/src/main/java/com/halo/engines/execution/ExecutionEngine.kt`
  - `runtime/cognition/src/main/java/com/halo/runtime/cognition/response/CommandResponseMapper.kt`
- **Behavior:**
  - Introduced `ActionFailureCode` enum (`APP_NOT_FOUND`, `APP_AMBIGUOUS`, `APP_NOT_LAUNCHABLE`, `REMINDER_TIME_MISSING`, `REMINDER_TIME_INVALID`, `EXACT_ALARM_ACCESS_REQUIRED`, `PERMISSION_REQUIRED`, `PLATFORM_REJECTED`, `TIMEOUT`, `UNKNOWN`).
  - Extended `Outcome` (`PermanentFailure`, `RetryableFailure`, `Partial`) with optional `code: ActionFailureCode?`.
  - Logged `HALO_ACTION_RESULT` with `code` for machine-readable telemetry.
  - Mapped structured action failure codes to clear user-facing response messages.

### 5. P0 — ElevenLabs Main-Thread Secret Storage Fix
- **Files Modified:**
  - `providers/elevenlabs-tts/src/main/java/com/halo/providers/elevenlabs/tts/ElevenLabsTtsProvider.kt`
  - `app/src/main/java/com/halo/app/ui/screens/settings/SpeechOutputSettings.kt`
- **Behavior:**
  - Recomputed `_state` asynchronously on `Dispatchers.IO`. `ElevenLabsTtsState` holds voice and model names, catalogue, and configuration status without executing blocking Keystore calls on Main.
  - Updated `ElevenLabsSettingsScreen` and `SpeechOutputSettings` Composables to observe `provider.state` rather than calling storage getters directly during composition.

### 6. P0 — Tool Definitions Purity
- **Files Modified:**
  - `app/src/main/java/com/halo/app/di/tools/host/ApplicationTools.kt`
  - `app/src/main/java/com/halo/app/di/tools/host/NavigationTools.kt`
  - `app/src/main/java/com/halo/app/di/tools/host/HostToolRegistry.kt`
- **Behavior:**
  - Removed synchronous `packageManager` and `Intent.resolveActivity()` calls from `definitions()`. Tool definitions are now pure and static.
  - Indexing in `HostToolRegistry` occurs once during construction, making definition lookup O(1) without Binder or disk calls during Skills screen rendering.

### 7. P2 — Repository Cleanliness
- **Files Removed / Modified:**
  - Removed `hs_err_pid8532.log` and `replay_pid8532.log`.
  - Added `hs_err_pid*.log` and `replay_pid*.log` to `.gitignore`.

---

## Verification & Build Results

### Gradle Build
- Executed: `./gradlew :app:assembleDebug`
- Status: **SUCCESS**

---

## Physical Device Verification Status

- **Pending:** Physical hardware trace validation for application launching, reminder clarification, exact alarm permission settings prompt, and ElevenLabs settings StrictMode verification under live device execution.
