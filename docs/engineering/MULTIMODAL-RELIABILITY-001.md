# Batch 078 / MULTIMODAL-RELIABILITY-001

Verified starting main: `0120e2da05fdebae0c8dbdfab2d83a015c11835f`, 2026-10-09. Batch 077 user-confirmed photo preview/Save, Workspace AI and Gemma comparison remain historical acceptance of that installed build, not automatic acceptance of Batch 078.

## Runtime and UX changes

- Voice capture snapshots the originating conversation and preserves capture/interaction identity through the portable SpeechInputTurn, speech bridge and interpretation metadata. Home binds its tap-time conversation; Assistant snapshots its invocation conversation; other host activations bind at acquisition. Duplicate recognition is admitted once. Live recognition snapshots identity, ignores cancelled/late/duplicate final callbacks and retains ownership until provider cleanup returns. Buffered decode preserves its original turn rather than mutable input state.
- Physical input stop is claimed once by the existing capture owner, including failed startup cleanup. Busy capture attempts publish a recoverable rejection without ending the existing turn. Exact Stop during transcription cancels the original capture and queued cognition; a cancellation receipt returns speech presentation to Idle. Closed capture receipts cannot reopen it with a late transcript. Existing native recognizer cleanup remains authoritative.
- Home keeps speech failures visible until dismissed or retried. Existing quality rejection evidence is explained, real transcripts can be edited as text, and accepted confidence below 0.55 is held for transcript review before interpretation. Unknown confidence and ordinary accepted queries remain direct. Explicit text submission retains the existing phone action authorization contracts; uncertain speech does not grant action authority.
- Voice Settings offers one-time Test audio output using the selected TTS and existing HaloSpeechOutputCoordinator/RoutedSpeechPlaybackProvider. Android default, phone and exact eligible accessories are distinct choices. The test never invokes inference or persists routing. Requested target, verified routed endpoint, playback completion, Stop/failure and explicit human audible confirmation are separate states. Exact tests prohibit phone fallback. Devices retains independent input/output capability labels and links to the audio test. Home retains speech playback failures while keeping the conversation response.
- STT settings observe actual resolved model/readiness rather than only a preference or constructed delegate. Missing, Loading, Installed, Ready and Failed are distinguished; Ready requires the existing resident recognizer. Replacement closes and waits for the previous native delegate before construction; initialization/cleanup failures cannot retain a falsely available closed delegate. Language preparation can recover after failure. Existing persisted language settings normalize locale tags and retain intentional auto/lv/en/ru selections. Installed models are initialized by explicit recognition, not automatically downloaded or benchmarked.
- Capture preflight checks readable source, preparation budgets, global image policy, object policy, selected vision capability/locality and exact local qualification. Explicitly attached image context survives successful answers for follow-up until Remove, Clear or expiry, as well as failed inference. Model configuration links open the existing role configuration owner. Generated extraction remains unverified content.
- Vision Lab checks current capability, privacy and exact local qualification without changing production selection. Historical remote probe timestamps remain diagnostic records but no longer promote a changed provider/model into permanent image eligibility. Current authoritative capability metadata still supports normal photo use without a mandatory Lab run.
- Partial streamed text is carried with its existing provider failure into a conversation/interaction/request-bound incomplete Home response. It is not committed as a completed answer and cannot expose stale Save actions. Missing stream completion and generic transport failure also preserve received text. The existing one-retry restriction remains: no retry after tokens/tool deltas, tool continuation or possible side effects. Home hides Retry after received output. Existing text transport and explicit role/model selection remain intact.

## Changed production modules and ownership

| Module | Main affected owners |
| --- | --- |
| sdk/runtime-sdk | SpeechInputTurn, StartListeningWithActivation |
| sdk/provider-sdk | AiProviderException partial text, CognitiveSessionState failure identity |
| engines/speech | AudioPerceptionEngine, AudioPipeline |
| runtime/speech | SpeechPerceptionBridge, SpeechSessionManager |
| providers/sherpa-stt | ReloadableSpeechRecognitionProvider |
| engines/ai | AiProviderRouter, AiModelCatalogRepository qualification projection |
| runtime/cognition | ProviderTurnExecutor, CognitiveSession, VisionLab |
| app | AssistantRuntimeBridge, HostGraph/ManualDependencyGraph wiring, HaloSpeechOutputCoordinator, AndroidInteractionContextAdapter, ContextualCaptureController, Home/Devices/Voice Settings/Vision Lab and navigation |

No new inference/capture/playback owner, MediaSession, conversation store, Room schema, provider/model installation, watch microphone/PTT, BLE protocol or CI workflow. Runtime retains orchestration and platform owners retain capture, routing, focus and resource cleanup. Provider bindings and cloud authorization are not changed by testing.

## Local validation

The final serial regression suite passed **892 tests**, zero failures/errors/skips: sdk/provider-sdk 26; sdk/runtime-sdk 52; engines/speech 21; runtime/speech 5; providers/sherpa-stt 19; engines/ai 79; runtime/cognition 244; app 426; host-android 20. The Android debug APK assembled successfully. Final gate log: batch-078-final-validation-3.log, BUILD SUCCESSFUL in 5m 30s (501 tasks). Some unchanged tasks reused Gradle outputs; all listed suite XML results have zero failures/errors/skips. Targeted new/extended regressions cover late cancelled decode, exact voice conversation metadata and duplicate/uncertain recognition, cancelled session recovery, STT construction failure and selection recovery, unavailable exact output/Stop/user confirmation, successful-image follow-up, historical vision proof rejection, partial output without retry and incomplete response conversation identity.

Resolved first-pass compilation issues: output test state was placed before remaining imports; local qualification callback needed its suspend contract. The diagnostic fixture also expected normal input attempts during a diagnostic to fail silently; it now asserts an exact INPUT_BUSY rejection without starting production capture, and verifies one physical stop rather than allowing repeated stops. The app regression compilation also exposed missing event imports in the new partial-output test; those imports were corrected. The final gate rebuilds the final source tree before delivery. Logs are retained in the chat output directory, outside the repository.

Serial local command (one worker, no parallel execution, 2 GB heap):

```powershell
.\gradlew.bat :sdk:provider-sdk:test :sdk:runtime-sdk:test :engines:speech:test :runtime:speech:test :providers:sherpa-stt:testDebugUnitTest :engines:ai:test :runtime:cognition:test :app:testDebugUnitTest :host-android:testDebugUnitTest :app:assembleDebug -I .artifacts/wearable-controls-physical-003/serial-tests.gradle --no-parallel --max-workers=1 --no-daemon '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8'
```

## Physical acceptance and remaining limits

Samsung SM-G990B2 was available over ADB earlier in the batch, but the final delivery check lists no connected devices. Reconnection was requested. Sony WH-CH520 was previously reported available; its current output must be tested by the user. No HeyCyan or Wear OS microphone acceptance is claimed. Batch 078 installation and genuine user-triggered speech/output/photo checks remain pending. Automated tests and APK installation do not establish audible output, recognition language accuracy or visual answer grounding.

## Delivery and Batch 079

Source commit **61ecf3cc90e5553a6b01c47ccaa67744acb1fe8e** is fast-forward integrated and pushed to main. The clean delivery APK was assembled from that commit: HALO_GIT_COMMIT=61ecf3cc90e5, HALO_GIT_DIRTY=false. Metadata build passed in 3m 30s (447 tasks), log batch-078-clean-apk.log. APK: app/build/outputs/apk/debug/app-debug.apk; SHA-256: 91F025FD149E568C1215CEBF68B8E01E5567C231C842646EAA012E1C7E827259. A copy is retained as Halo-batch-078-61ecf3cc-debug.apk in the chat output directory. The final ADB device list is empty, so installation and the physical acceptance pass could not run. No unverified acoustic, Latvian recognition or photo-answer result is claimed. Batch 079 should address only failures observed in the compact real-device pass, then broader everyday recovery acceptance if needed. FEATURE-073 watch microphone/PTT remains a separate explicitly scoped batch with actual watch hardware.
