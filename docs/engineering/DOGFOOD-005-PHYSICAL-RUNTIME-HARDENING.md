# DOGFOOD-005 — Physical Runtime Hardening

**Status:** IMPLEMENTED / COMPILED / UNIT VERIFIED / DEPLOYED  
**Baseline SHA:** `db5563e38d4f575b5957ce46dfe7c5936b2f90a9`  
**Date:** 2026-10-02  

---

## 1. Overview & Objectives

DOGFOOD-005 is a physical-device evidence-driven convergence batch focused on:
1. P0 — Removing full capability refresh from the native action hot path.
2. P0 — Direct User Command Authority for safe direct command execution without secondary approval dialogs.
3. P0 — Fixing `WAITING_FOR_EVENT` state machine and run resumption transitions.
4. P0 — One exact image binding authority (`InteractionMediaBinder`) for visual inputs.
5. P0 — Removing Halo-owned main-thread filesystem IO and ensuring Compose UI graph purity.
6. P1 — Native action timing telemetry (`HALO_NATIVE_ACTION_TIMING`).
7. P1 — Reproducible dogfood builds with `HALO_SOURCE_ID` (`commit+dirty`).

---

## 2. Summary of Changes

### 1. Host Capability State Source & Preflight Optimization
- **Files:** [`AndroidCapabilityPublisher.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/app/src/main/java/com/halo/app/di/AndroidCapabilityPublisher.kt), [`HostToolRegistry.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/app/src/main/java/com/halo/app/di/tools/host/HostToolRegistry.kt), [`SkillExecutionService.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/runtime/cognition/src/main/java/com/halo/runtime/cognition/skills/SkillExecutionService.kt)
- **Behavior:**
  - Introduced `HostCapabilityStateSource` interface and `CapabilityRefreshReason` enum in `AndroidCapabilityPublisher`.
  - Removed synchronous `refreshCapabilities()` calls from `SkillExecutionService.execute()` and `check()`. `publishInto` uses cached `_state.value`.
  - Fixed `HostToolRegistry` index regression by building the `toolIndex` and `allDefinitions` ONCE during initialization instead of rebuilding `executorMap()` on every call.

### 2. Direct User Command Authority
- **Files:** [`DirectUserCommandAuthority.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/DirectUserCommandAuthority.kt), [`NativeCapabilityDispatcher.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/runtime/cognition/src/main/java/com/halo/runtime/cognition/native/NativeCapabilityDispatcher.kt), [`AgentRunJournal.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunJournal.kt)
- **Behavior:**
  - Added `DirectUserCommandAuthority` model and `DirectCommandAuthorityRecorded` step in the execution journal.
  - Recorded direct user command authority on native command dispatch, allowing high-confidence user requests (`open_app`, `timer`, `volume`, `brightness`, `ringer`, `open_settings`, `media_control`) to execute immediately without duplicate secondary confirmation.

### 3. State Machine & Run Resumption Fixes
- **Files:** [`AgentRunCoordinator.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunCoordinator.kt)
- **Behavior:**
  - Updated transition rules in `DefaultAgentRunCoordinator` to allow `WAITING_FOR_EVENT` -> `EXECUTING`, `WAITING_FOR_EVENT` -> `COMPLETED`, `INTERRUPTED` -> `EXECUTING`, `INTERRUPTED` -> `WAITING_FOR_APPROVAL`.
  - Terminal runs in state `INTERRUPTED` or `COMPLETED` cleanly ignore late duplicate terminal transitions without crashing background coroutines with `IllegalStateException`.

### 4. Vision Pipeline & Image Binding
- **Files:** [`InteractionMediaBinder.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/runtime/cognition/src/main/java/com/halo/runtime/cognition/vision/InteractionMediaBinder.kt), [`HomeViewModel.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/app/src/main/java/com/halo/app/ui/screens/home/HomeViewModel.kt)
- **Behavior:**
  - Implemented `DefaultInteractionMediaBinder` to validate, bind image asset references, transfer ownership in `MediaAssetStore`, and register in `AssistantContextRepository`.
  - Refactored `HomeViewModel` to bind image attachments via `InteractionMediaBinder` and submit standard requests where `CognitiveTurnPreparer` prepares image content.

### 5. Main-Thread Storage Purity & Build Traceability
- **Files:** [`HaloStorageLayout.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/app/src/main/java/com/halo/app/storage/HaloStorageLayout.kt), [`app/build.gradle.kts`](file:///C:/Users/slavd/HaloEco/Halo-main/app/build.gradle.kts), [`HaloApplication.kt`](file:///C:/Users/slavd/HaloEco/Halo-main/app/src/main/java/com/halo/app/HaloApplication.kt)
- **Behavior:**
  - Thread-safe volatile caching in `haloStorageLayout(context)` guarantees that directory layout creation occurs on `Dispatchers.IO` during storage bootstrap, eliminating main-thread `filesDir` disk reads during Compose navigation/rendering.
  - Added `HALO_SOURCE_ID` build field (`commit+dirty`) logged on application startup.
  - Added `HALO_NATIVE_ACTION_TIMING` telemetry in `NativeCapabilityDispatcher`.

---

## 3. Verification & Test Summary

### Automated Tests
- `:sdk:runtime-sdk:test` (36 passed)
- `:runtime:cognition:test` (172 passed)
- `:engines:interpretation:test` (8 passed)
- `:engines:reasoning:test` (7 passed)
- `:host-android:test` (20 passed)
- `:app:testDebugUnitTest` (179 passed)
- **Total: 422 unit tests passed (100% success rate).**

### Build & Deployment
- `./gradlew :app:assembleDebug` -> **SUCCESS**
- Deployed package `com.aistudio.braintool.xkqzps` to device `emulator-5554`.
