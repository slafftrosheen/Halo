# PHONE-ASSISTANT-004 — Durable Follow-ups + Event Watches + Continuation Runtime

**Status:** IMPLEMENTED / COMPILED / UNIT VERIFIED / DEPLOYED  
**Baseline SHA:** `8dcd5adfacd1a0b17d411959fb5709b3d23576ef`  
**Date:** 2026-10-02  

---

## 1. Product Outcome & Concept

PHONE-ASSISTANT-004 introduces the missing product concept: **"I am waiting for something."**

Target Architecture:
```text
User / AgentRun -> ContinuationWatch -> AutomationFabric -> bounded platform signal -> WatchMatcher -> durable match claim -> (notify user / mark run resumable / safely reconcile state)
```

No permanent background agent services, no periodic model polling, no cloud polling, and no LLM wake-ups on timer events.

---

## 2. Key Architecture & Boundaries

- **Separation of Routines and Continuations:**
  - *Routine:* "When X happens, perform these pre-authorized background actions."
  - *Continuation Watch:* "I am waiting for X because of an existing user intention/task."
- **ContinuationWatch Model:**
  - `ContinuationWatchId`, `runId`, `interactionId`, `condition`, `behavior`, `status` (`ACTIVE`, `MATCH_CLAIMED`, `SATISFIED`, `EXPIRED`, `CANCELLED`, `FAILED`), `createdAt`, `expiresAt`, `deduplicationKey`, `matchMode` (`NEXT_TRANSITION`, `CURRENT_OR_NEXT`), `presentationText`.
- **Supported Conditions:**
  - `NetworkAvailable`, `NetworkLost`
  - `BatteryBelow(percent)`, `BatteryAbove(percent)`
  - `DeviceConnected(deviceId)`, `DeviceDisconnected(deviceId)`
  - `AppForegrounded(packageName)`
  - `NotificationFromApp(packageName)`
  - `ProviderAvailable(providerId)`
  - `CapabilityAvailable(capabilityId)`
- **Signals & AutomationFabric Fan-out:**
  - Shared signal stream from `AutomationFabric` drives both `RoutineTriggerMatcher` and `ContinuationWatchMatcher`.
  - Added `ProviderAvailabilitySignalSource` and `CapabilityAvailabilitySignalSource`.
- **Durable Room Storage & Migration 22 -> 23:**
  - Table `continuation_watches` with `id`, `runId`, `interactionId`, `conditionType`, `conditionJson`, `behaviorType`, `behaviorJson`, `presentationText`, `status`, `createdAt`, `expiresAt`, `deduplicationKey`, `matchMode`, `matchedAt`, `claimState`.
  - Migration `MIGRATION_22_23` tested in `ContinuationWatchMigrationTest`.
- **Match Claim & Side-Effect Safety:**
  - Transactional claim (`claimWatchTransaction` with `claimState = 'CLAIMED'`) ensures at-most-once side-effect semantics.
  - Never automatically replays claimed external actions (`ActionTruth.DISPATCHED`).
- **`WAITING_FOR_EVENT` State & Journal Steps:**
  - `AgentRunState.WAITING_FOR_EVENT` added to `AgentRun`.
  - Journal steps: `ExternalConditionRegistered`, `ExternalConditionSatisfied`, `ExternalConditionExpired`, `ExternalConditionCancelled`.
- **Provider-Independent Creation:**
  - Semantic watch intents (`NotifyWhenNetworkAvailableIntent`, `NotifyWhenBatteryBelowIntent`, `NotifyWhenDeviceConnectedIntent`, `NotifyWhenAppStartedIntent`, `NotifyWhenNotificationFromAppIntent`, `CancelWatchIntent`, `ListWatchesIntent`).
  - Creation occurs without an LLM when matching deterministic intent rules.
- **Tools & Skill:**
  - Skill `halo.followups` ("Follow-ups") in `assets/skills/halo.followups.json`.
  - Tools: `halo_create_watch`, `halo_list_watches`, `halo_cancel_watch`.

---

## 3. Verification & Test Summary

### Unit Tests
- `:sdk:runtime-sdk:test` (36 passed)
- `:runtime:cognition:test` (172 passed)
- `:engines:interpretation:test` (8 passed)
- `:engines:reasoning:test` (7 passed)
- `:host-android:test` (20 passed)
- `:app:testDebugUnitTest` (179 passed)
- **Total: 422 unit tests passed (100% success rate).**

### Build & Deployment
- `./gradlew :app:assembleDebug` -> SUCCESS
- Deployed package `com.aistudio.braintool.xkqzps` to device `emulator-5554`.
