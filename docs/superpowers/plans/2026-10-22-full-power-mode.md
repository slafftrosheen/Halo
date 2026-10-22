# Full Power Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement independently tunable Private and Full Power profiles with a hard local-only boundary, one-time informed mode choice, and content-free evidence for every remote dispatch.

**Architecture:** Keep `PrivacyModeStore` as the authoritative active-mode boundary, add a versioned profile store for the settings that differ by mode, and make every remote-capable service read effective mode policy at dispatch. Compose the mode selector, profile editor, disclosure and receipt history from that state; leave Android grants, provider secrets, Kids restrictions, action approvals, and spending controls in their current owners. The guided Android capability setup is a dependency shared with issue #48 and must not be conflated with Full Power authorization.

**Tech Stack:** Kotlin, Android, Jetpack Compose, existing `Storage` abstraction, coroutines/StateFlow, existing provider and capability APIs, Robolectric/JUnit tests.

**Spec:** `docs/04-architecture/PRIVATE-POWER-MODES-IMPLEMENTATION.md`

## Global Constraints

- User-facing names are **Private** and **Full Power**; Full Power replaces Cloud.
- Private blocks remote AI, hosted speech, paid web operations, cloud image work, and remote context transfer.
- Full Power enables shipped and configured cloud-capable features by default; missing credentials, outages, quotas, and unsupported routes never trigger an undisclosed provider substitution.
- Mode does not grant Android permissions, provider credentials, action approval, purchase consent, or spending consent.
- Each mode has an independent profile; editing one never rewrites the other.
- Before first Full Power dispatch, identify the configured service, eligible data categories, and possible provider charges; later requests show a compact disclosure without a confirmation dialog for every send.
- Receipts contain time, service, operation, data categories, result, and provider-reported cost when available; never retain prompts, message bodies, media, credentials, signed URLs, or guessed costs.
- Capability requests are explained before launch, resumable, OS-controlled, and optional; denial leaves typed chat and Private core use available.

## Review Focus

- A legacy/invalid stored mode must resolve to Private and cannot dispatch remotely.
- A transition to Private during an in-flight remote operation must cancel the actual operation and prevent fallback to another online provider.
- A provider or data-category change must update the disclosure before the next dispatch.
- A cost field absent from a provider result must be recorded/displayed as “cost not reported,” never estimated.
- Enabling Full Power must not silently grant paid-media consent or mutate any Android permission state.

---

### Task 1: Independent mode profiles and safe migration

**Files:**
- Modify: `app/src/main/java/com/halo/app/ai/PrivacyModeStore.kt`
- Modify: `app/src/main/java/com/halo/app/ai/RuntimeAiPolicy.kt`
- Modify: `app/src/main/java/com/halo/app/ai/WorkloadPreferences.kt`
- Create: `app/src/main/java/com/halo/app/ai/ModeProfileStore.kt`
- Test: `app/src/test/java/com/halo/app/ai/PrivacyModeStoreTest.kt`
- Test: `app/src/test/java/com/halo/app/ai/ModeProfileStoreTest.kt`

**Interfaces:** `ModeProfileStore.profile(mode: PrivacyMode): ModeProfile`, `ModeProfileStore.update(mode: PrivacyMode, transform: (ModeProfile) -> ModeProfile): ModeProfile`. `ModeProfile(providerId: String?, modelId: String?, route: ModeRoutePreference, allowLocalFallback: Boolean, contextCategories: Set<ModeDataCategory>, enabledCloudFeatures: Set<CloudFeature>)`; `ModeRoutePreference` is `LOCAL_ONLY` or `CONFIGURED_PROVIDER`. `ModeDataCategory` values: `CONVERSATION`, `DEVICE_STATE`, `RECENT_EVENTS`, `MEMORIES`, `LOCATION`, `IMAGES`, `AUDIO`, `VIDEO`, `SCREEN`, `WORKSPACE`, `TOOLS`. `CloudFeature` values: `REMOTE_CHAT`, `MODEL_TOOLS`, `HOSTED_SPEECH`, `WEB_SEARCH_FETCH`, `IMAGE_UNDERSTANDING`, `IMAGE_GENERATION`, `SCREEN_CONTEXT`, `AUDIO_VIDEO_CONTEXT`, `MEMORY_CONTEXT`, `WORKSPACE_CONTEXT`. None of these types holds secrets.

- [ ] Add failing tests for defaults (Private local-only; Full Power all shipped cloud categories/features enabled), independent edits, active-mode switching, legacy migration, and invalid mode fail-closed behavior.
- [ ] Run `:app:testDebugUnitTest --tests com.halo.app.ai.PrivacyModeStoreTest --tests com.halo.app.ai.ModeProfileStoreTest`; confirm the new behavior is absent.
- [ ] Implement versioned profile serialization with bounded enum/ID parsing and safe defaults; preserve existing provider secrets in their existing store.
- [ ] Remove the mode-to-`PaidMediaPolicy.CONSENT_KEY` write. Keep an explicit, separately owned purchase/spending consent unchanged on mode switches.
- [ ] Update policy projections and workload preferences to read the active profile without mutating the inactive profile.
- [ ] Re-run the focused tests and the existing `RuntimeAiPolicy`/workload preference tests.

### Task 2: Enforce the Private boundary and explicit Full Power routing

**Files:**
- Modify: `app/src/main/java/com/halo/app/ai/RuntimeAiPolicy.kt`
- Modify: `app/src/main/java/com/halo/app/ai/PrivacyModeStore.kt`
- Audit and modify actual dispatch owners under `app/src/main/java/com/halo/app/ai/`, `online/`, `audio/`, `data/workspace/`, and `di/tools/host/` where they can initiate remote work.
- Test: focused policy and dispatch tests alongside each changed owner.

**Interfaces:** `decideRoute(mode: PrivacyMode, profile: ModeProfile, operation: CloudOperation, categories: Set<ModeDataCategory>): EffectiveRouteDecision`. `CloudOperation` values are `REMOTE_CHAT`, `MODEL_TOOLS`, `HOSTED_SPEECH`, `WEB_SEARCH_FETCH`, `IMAGE_UNDERSTANDING`, `IMAGE_GENERATION`, `SCREEN_CONTEXT`, `AUDIO_VIDEO_CONTEXT`, `MEMORY_CONTEXT`, and `WORKSPACE_CONTEXT`. `EffectiveRouteDecision` is `Allowed(providerId: String, modelId: String, disclosure: RouteDisclosure)`, `Local(disclosure: RouteDisclosure)`, or `Rejected(reason: RouteRejection)`. `RouteDisclosure(providerDisplayName: String?, categories: Set<ModeDataCategory>, possibleCharges: Boolean)`. `RouteRejection` values are `PRIVATE_MODE`, `PROFILE_DISABLED`, `CATEGORY_DISABLED`, `PROVIDER_NOT_CONFIGURED`, and `PROVIDER_UNAVAILABLE`. It never returns an implicit alternate online provider.

- [ ] Add failing tests proving Private rejects every remote operation class in the spec, including stale legacy allow-remote flags.
- [ ] Add failing tests proving Full Power uses only its configured, available provider/model and that fallback happens only when its profile explicitly enables the chosen local fallback.
- [ ] Add a cancellation test proving switching to Private cancels an active request and does not retry it through another online route.
- [ ] Replace duplicated `savedPrivacyMode()` checks with the shared route decision at the final dispatch boundary; retain provider, Android-permission, Kids, and action-approval checks in their owners.
- [ ] Run focused tests for each touched dispatch owner and search for unguarded remote dispatch call sites.

### Task 3: One profile editor and clear active-mode UI

**Files:**
- Modify: `app/src/main/java/com/halo/app/ui/components/PrivacyModeControl.kt`
- Modify: `app/src/main/java/com/halo/app/ui/shell/AppShell.kt`
- Create: `app/src/main/java/com/halo/app/ui/screens/settings/ModeProfilesScreen.kt`
- Modify: settings navigation/route owner discovered in `AppShell.kt`.
- Test: Compose/Robolectric tests for selector and profile screen.

**Interfaces:** The screen consumes `ModeProfileStore` state and emits `setActiveMode(mode)` and `updateProfile(mode, transform)`; it displays two distinct profile panels and never writes the inactive profile during mode activation.

- [ ] Add failing UI tests for the Private and Full Power names, active profile summary, independent category/provider edits, and mode changes that do not change Android grants or spending consent.
- [ ] Replace visible “Cloud”/“Power” copy and switch labels with “Full Power”; describe possible online processing and provider charges adjacent to the choice.
- [ ] Add a single Mode profiles destination with separate Private and Full Power panels, provider/model routing, context-category controls, and shipped online feature controls.
- [ ] Show configured service, enabled online capabilities, eligible data categories, and known billing behavior in the active summary.
- [ ] Verify existing mode-control entry points still reach the same active-mode state.

### Task 4: Pre-dispatch disclosure and content-free activity receipts

**Files:**
- Create: `app/src/main/java/com/halo/app/privacy/FullPowerReceipt.kt`
- Create: `app/src/main/java/com/halo/app/privacy/FullPowerActivityStore.kt`
- Modify: dispatch owners from Task 2 to publish outcome metadata.
- Modify: composer/remote-capable feature surfaces to show route disclosure.
- Create: `app/src/main/java/com/halo/app/ui/screens/settings/FullPowerActivityScreen.kt`
- Test: receipt-store privacy, redaction, provider cost handling, clear-history, and disclosure UI.

**Interfaces:** `FullPowerReceipt(timestampMs: Long, providerId: String, operation: CloudOperation, dataCategories: Set<ModeDataCategory>, result: FullPowerRequestResult, reportedCost: String?)`, where `FullPowerRequestResult` is `SUCCESS`, `FAILED`, `CANCELLED`, or `BLOCKED`; `reportedCost` is a provider-reported decimal string or null. `FullPowerActivityStore.record(receipt)`, `entries(): Flow<List<FullPowerReceipt>>`, and `clear()`. The receipt type contains no request-content or secret fields.

- [ ] Add failing tests that serialize receipts without request content, preserve provider-reported cost exactly, and render missing cost as “cost not reported.”
- [ ] Add failing tests for first-dispatch explanation, compact subsequent route disclosure, provider/category changes, and history deletion.
- [ ] Implement bounded local receipt persistence, content-free UI, and clear-history action.
- [ ] Publish outcomes for success, failure, cancellation, and blocked routes without storing request payloads or signed URLs.
- [ ] Verify no provider result without a cost is represented as zero or estimated.

### Task 5: Integrate guided Android capability setup and close acceptance gaps

**Files:**
- Consume: issue #48's completed guided-capability setup, including the request inventory, persisted setup position, and actual Android grant refresh.
- Modify: `app/src/main/java/com/halo/app/ui/onboarding/HaloSetupScreen.kt` and `PermissionsScreen.kt` only as needed to expose one resumable setup entry.
- Test: permission status refresh, denial, “don't ask again,” settings return, and mode-toggle isolation.

- [ ] Confirm the guide covers only capabilities used by the installed build, explains each prompt first, persists its position, and re-reads grants after returning from Android settings.
- [ ] Add an integration test proving a Private/Full Power mode switch never starts the guide or requests permissions.
- [ ] Add acceptance evidence for typed chat after every permission is denied and for the capability-specific degraded routes.
- [ ] Run the Android API 33–35 and supported-locale/large-text device matrix; record any physical-device acceptance not available in the current environment as open.

### Task 6: Regression, documentation, and delivery

**Files:**
- Modify: `docs/04-architecture/PRIVATE-POWER-MODES-IMPLEMENTATION.md` to mark implemented behavior and remaining limitations.
- Add: `docs/testing/FULL-POWER-MODE-VERIFICATION-YYYY-MM-DD.md` with source/build/device evidence.

- [ ] Run focused tests for every changed owner, then the app debug build and relevant regression suites.
- [ ] Search user-facing resources for outdated “Cloud mode” and ambiguous “Power mode” labels; update supported locales using existing localization conventions.
- [ ] Install and launch the validated build on the connected target Android device; verify mode switching, dispatch disclosure, denial/degraded use, receipts, and clearing history.
- [ ] Fetch remote state, commit the complete verified slice, push the intended branch and `main` per the repository delivery convention, then verify `git status` and remote ancestry.
- [ ] Close the related issue only after its acceptance criteria have direct evidence; leave any missing device/provider/locale checks explicitly open.
