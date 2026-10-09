# Batch 077 / INTEGRATION-STABILITY-001

Starting baseline: `05b1dc7311ff06e67b1fbdd0b4d130c986ee2322`, synchronized main and origin/main on 2026-10-09. FEATURE-072 (`8014911b`), FEATURE-074 (`ee8e508d`), FEATURE-075 (`6fdae3bd`) and FEATURE-076 (`2ce2a6f9`) are already integrated ancestors. Their old branch-only statements were stale. Historical acceptance artifacts remain unchanged. FEATURE-073 watch microphone/ChannelClient/PTT is absent; nearby watch presentation does not imply microphone support.

## Confirmed issues and fixes

- Home cleared drafts before request acceptance and had no observable submission receipt. An atomic interaction tracker now exposes submitting, accepted, processing, completed, failed, cancelled and admission timeout. It preserves retry text and ignores late receipts for earlier attempts. The 15-second admission watchdog cannot cancel a healthy owned inference; runtime deadlines remain authoritative.
- Deterministic text actions previously published admission only after dispatch. They now use the existing active-turn coordinator, publish acceptance before dispatch and retain exact conversation binding. Stop cancels that owner; native cancellation records its terminal journal state under NonCancellable. Accepted native actions preserve drafts without a Retry suggestion because dispatch may already have occurred. No automatic retry is introduced.
- Explicit Stop remains reachable after navigating away from the active text conversation; New cancels the exact active text owner. Clear remains scoped to its captured conversation.
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

Production source commits: `3b21f3e137bfe5dad36966a0f85dd9ecb37b31b4` and `dd729cd68d13ba0ef198a05028c7f91b65041618`, fast-forwarded into main and pushed to origin/main. The final serial gate passed 828 tests (52 runtime SDK, 244 cognition, 12 interpretation, 79 AI, 421 app, 20 Android host), zero failures/errors/skips, and assembled the debug APK. Unchanged module results were reused by Gradle where up to date.

```powershell
.\gradlew.bat :sdk:runtime-sdk:test :runtime:cognition:test :engines:interpretation:test :engines:ai:test :app:testDebugUnitTest :host-android:testDebugUnitTest :app:assembleDebug -I .artifacts/wearable-controls-physical-003/serial-tests.gradle --no-parallel --max-workers=1 --no-daemon '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8'
```

`batch-077-committed-validation.log`: BUILD SUCCESSFUL, 11m16s. A subsequent clean-metadata assembly from main passed in 3m16s using `:app:assembleDebug` with the same worker/heap flags. Installed APK identifies `dd729cd68d13`, dirty=false; SHA256 `C40FC7414E57A9DC094BF95E887C160C21579E81C7C694DB625A60C09199EB25`. ADB install returned Success and activity launch succeeded on Samsung SM-G990B2 / Android API 36. Logs and UI captures are retained outside the repository in the chat output directory.

Resolved validation failures: initial Kotlin compilation required handling a nullable interaction ID; the existing failed-Save test needed to expect FAILED plus preserved bytes and a recovery message; the slow-native fixture needed to await its real IO execution signal rather than assuming runCurrent drained Dispatchers.IO. These failures are not reported as hardware observations. All Gradle gates use one worker, no parallel execution and a 2 GB heap.

## Physical acceptance and remaining work

ADB-driven phone checks completed:

- New conversation removed the previous response from the active view without deleting the prior conversation. Synthetic text `Reply with only HALO_TEXT_OK` showed admission, cleared its draft after acceptance, displayed Stop during processing, committed the response and unlocked the composer.
- Exact-result Save reviewed `HALO_TEXT_OK`, then opened the saved Notebook note at revision 1 with Saved locally status.
- Study created a synthetic water-freezing session and Begin returned a live hint/diagnostic question, explicitly marked Unverified suggestion. Returning to Study retained the session for Continue learning.
- Home text and Study used the currently selected OpenRouter `nvidia/nemotron-3-ultra-550b-a55b:free`. Home's first provider attempt returned PROVIDER_UNAVAILABLE; its existing bounded retry succeeded. This does not establish quota as a cause or verify the original Gemma-specific voice/text discrepancy. Provider/model settings were not changed for this pass.

Workspace navigation and local Notebook Save were observed; a separate Workspace AI generation was not verified. Failure recovery, exact cancellation, image privacy/retention and audio cleanup passed the targeted automated regression checks, but that does not establish physical outcomes. On 2026-10-09, after installation of the Batch 077 APK, the user confirmed that the phone photo preview matches their photo and Save works. This is human-reported physical acceptance of preview and Save; it does not verify image analysis or extraction. Sony headphone output with the phone speaker silent still awaits human observation. Previously reported available audio hardware is Sony WH-CH520; HeyCyan is unavailable. No watch microphone/PTT acceptance is claimed. Synthetic validation conversation/note and Study session were retained; historical acceptance artifacts were not rewritten.

Remaining acceptance limits: human headphone/audio confirmation, a direct live Gemma text/Study/Workspace reproduction, and live Workspace AI generation. No unresolved local test/build failures remain.

Next recommended batch: close those specific acceptance gaps and investigate any remaining Gemma failure using exact request/provider/transport evidence, without adding providers or changing routing ownership. FEATURE-073 watch microphone/PTT should follow only as a separate explicitly scoped batch with an actual watch.
