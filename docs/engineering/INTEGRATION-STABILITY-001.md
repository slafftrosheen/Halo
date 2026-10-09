# Batch 077 / INTEGRATION-STABILITY-001

Starting baseline: `05b1dc7311ff06e67b1fbdd0b4d130c986ee2322`, synchronized main and origin/main on 2026-10-09. FEATURE-072 (`8014911b`), FEATURE-074 (`ee8e508d`), FEATURE-075 (`6fdae3bd`) and FEATURE-076 (`2ce2a6f9`) are already integrated ancestors. Their old branch-only statements were stale. Historical acceptance artifacts remain unchanged. FEATURE-073 watch microphone/ChannelClient/PTT is absent; nearby watch presentation does not imply microphone support.

## Confirmed issues and fixes

- Home cleared drafts before request acceptance and had no observable submission receipt. An atomic interaction tracker now exposes submitting, accepted, processing, completed, failed, cancelled and admission timeout. It preserves retry text and ignores late receipts for earlier attempts. The 15-second admission watchdog cannot cancel a healthy owned inference; runtime deadlines remain authoritative.
- Deterministic text actions previously published admission only after dispatch. They now use the existing active-turn coordinator, publish acceptance before dispatch and retain exact conversation binding. Stop cancels that owner; native cancellation records its terminal journal state under NonCancellable. Accepted native actions preserve drafts without a Retry suggestion because dispatch may already have occurred. No automatic retry is introduced.
- Conversation identity could change without emitting a different empty turn list. Room now exposes identity directly. Clear captures its target, context cleanup is scoped to that conversation, and response actions use committed turn identity rather than matching answer text. Follow-up checks the exact source turn.
- A new explicit image could replace an existing selection; callback failure could leak its attachment lease. Serialized attachment admission rejects replacement, repeated selection is idempotent, and failed selection releases only its new lease. Failed/cancelled turns retain the exact selected source for retry. Explicit Remove, Clear and expiry own context cleanup. Saved image references do not receive the temporary-context expiry.
- Keyboard image requests now use visual-only delivery. Existing final media/model/global/object privacy checks remain authoritative. Changed Workspace revisions become unavailable until explicitly updated. Result save validates the original conversation and active destination in its transaction. Generated extraction remains unverified.
- Duplicate final speech and stream preparation could interrupt or restart playback. Bounded receipt deduplication and interaction-scoped stop prevent that. Cancellation cleanup releases foreground/focus/audio leases under NonCancellable. Android input/output share one audio-session resource while endpoint-specific routing and forbidden fallback remain intact.
- Home uses compact presence, a persistent composer, one attachment chooser, context near the composer, conversation history, confirmed Clear and tools behind a menu. Devices command acknowledgement says Requested, not media received. Existing Settings/System Check diagnostics remain passive.

## Ownership

Interpretation and CognitiveSession retain inference/action ownership. The new tracker only projects receipts; it cannot dispatch providers or grant tool authority. Room remains conversation truth. Capture review, Workspace privacy, media leasing, speech routing and Android audio remain their existing owners. Missing optional devices cannot block phone interaction. No new BLE protocol, watch recorder, inference pipeline, automatic connection, schema migration or CI changes.

Affected source files (under existing module source roots):

| Area | Files |
| --- | --- |
| Text and presentation | `HomeViewModel.kt`, `TextSubmissionState.kt`, `HomeResponseProjection.kt`, `HomeResponseCard.kt`, `HomeScreen.kt`, `AppShell.kt` |
| Conversation and context | `RoomConversationStore.kt`, `ConversationStore.kt`, `CognitiveSession.kt`, `ActiveTurnCoordinator.kt`, `NativeCapabilityDispatcher.kt`, `DefaultInterpretationEngine.kt`, `ContextualNextActionProjector.kt`, `AndroidInteractionContextAdapter.kt`, `ContextResultSaveOwner.kt`, `ContextContinuityControls.kt` |
| Capture | `VisualAttachmentStore.kt`, `ContextualCaptureController.kt`, `ContextualCaptureModels.kt` |
| Audio and device truth | `HaloAudioSessionCoordinator.kt`, `HaloSpeechOutputCoordinator.kt`, `ContinuityControls.kt`, `DevicesScreen.kt`, `HeyCyanControlScreen.kt` |
| Regression checks | `TextSubmissionStateTest.kt`, `HomeResponseProjectionTest.kt`, `ConversationContinuityTest.kt`, `ContextAdapterTest.kt`, `ContextualCaptureTest.kt`, `SpeechPlaybackLifecycleTest.kt`, `HaloAudioSessionLeaseTest.kt`, `CognitiveSessionRequestIntegrityTest.kt`, `NativeCapabilityDispatcherTest.kt` |

## Validation and delivery

The pre-final serial gate passed 827 tests (52 runtime SDK, 244 cognition, 12 interpretation, 79 AI, 420 app, 20 Android host), with zero failures/errors/skips, and assembled the debug APK. A subsequent focused native-owner test passed after adding the slow-action lifecycle fix. The final gate will validate that final source tree and its APK before main integration.

Resolved validation failures: initial Kotlin compilation required handling a nullable interaction ID; the existing failed-Save test needed to expect FAILED plus preserved bytes and a recovery message; the slow-native fixture needed to await its real IO execution signal rather than assuming runCurrent drained Dispatchers.IO. These failures are not reported as hardware observations. All Gradle gates use one worker, no parallel execution and a 2 GB heap.

## Physical acceptance and remaining work

Samsung SM-G990B2 is attached over ADB. Previously reported available audio hardware: Sony WH-CH520; no HeyCyan glasses. Current acoustic, camera, text, Study and Workspace behavior requires direct observation. Installing or launching an APK cannot establish those outcomes. The earlier 429 routing fix is part of baseline 05b1dc73; successful live Gemma text/Study/Workspace responses are still unverified, and quota is not asserted as the cause.

Next priorities: complete one phone acceptance pass (text/retry/Stop, exact image preview/privacy/Save, Study/Workspace and Sony output with phone silent), investigate any remaining provider failure with exact request/transport evidence, then implement FEATURE-073 only as a separate explicit watch microphone/PTT batch with actual watch acceptance.
