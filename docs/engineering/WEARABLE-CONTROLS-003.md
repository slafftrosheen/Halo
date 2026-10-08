# FEATURE-074 / WEARABLE-CONTROLS-003

Status: PARTIAL IMPLEMENTATION ON `codex/feature-074-accessory-controls`. Not merged or accepted end to end.

## Dependency gate

Fetched all origin refs on 2026-10-08. FEATURE-072 is local commit `8014911b`; origin/main remains `bb27bc57`. FEATURE-073 and `WearPushToTalkController` are absent from every fetched branch and working tree. A1 requires a separate branch until these integration dependencies land. No watch recorder, ChannelClient replacement, or simulated PTT owner was introduced.

## Implemented composition

`AccessoryControlEvent.Button` carries opaque endpoint identity, connection epoch, source event identity, origin and evidence. Legacy WearableHost buttons remain compatible. HeyCyan preserves every ButtonType in its generic stream: single/double/long classifications retain identity but remain OBSERVED pending protocol evidence; UNKNOWN remains unassignable. Existing voice/camera classes retain their existing support boundary. Event identity and timestamp originate at the vendor event, not each subscriber. The current verified decoder does not emit ButtonPressed; a camera mapping row appears only after a verified delivery in the current connection, never merely from the enum declaration. Native voice notifications still use the existing bridge.

The portable interpreter accepts only verified classified events or reliable raw edges, deduplicates source IDs, rejects stale/future/old-epoch input and cancels pending gestures on disconnect. Timing defaults are 350 ms double, 650 ms long, 40 ms debounce. Classified vendor events bypass timing. A double mapping delays single resolution; a hold suppresses its release tap. The long classification resolves on release; no raw-edge adapter is currently advertised to end users.

One Storage-backed mapping repository stores bounded version-1 JSON device overrides and class defaults. Opaque stable managed IDs may persist; connection-only overrides stay in process memory and expire on disconnect. Invalid schema/action/selection documents fail closed. Reserved inputs and volume take system precedence. Device mappings precede class mappings; the safe default is SystemDefault. Saving requires delivered controls, a live endpoint, and an available action.

The dispatcher accepts only the typed action allowlist and returns EXECUTED, OPENED_FOR_REVIEW, BLOCKED, UNAVAILABLE, IGNORED or FAILED. It has no tool executor, arbitrary intent/command, or approval entrypoint. Slow actions are not queued; disconnect cancels outstanding dispatches and a fresh epoch rejects previous input. Ordinary controls cannot approve a confirmation or execute a routine.

## Existing action owners

| Action | Current behavior |
| --- | --- |
| TalkToHalo | Native Android assistant / HeyCyan voice boundaries remain authoritative. Configurable capture activation is unavailable; no phone microphone substitution. |
| StopSpeaking | Calls HaloSpeechOutputCoordinator.stop; no cognitive cancellation, tool replay, or undo. |
| RepeatLastAnswer | Replays only the last committed visible answer with its known exact speech route/fallback, through TtsSanitizer/TtsRequested. Waits for speech completion/failure/cancellation. No inference or tools. Visual-only answers without a speech policy are unavailable. |
| TakePhoto | Uses confirmed HeyCyan AI-thumbnail transfer and LocalCaptureStore validation/storage. A BLE write is insufficient. Saves the confirmed thumbnail image; does not claim full-resolution capture. Control captures suppress the store's inference publication. |
| CaptureAndAsk | Requires verified local vision before it is offered. Captures and saves first, rechecks vision, then attaches the saved Workspace image to Home for an explicit fresh question. No automatic provider request; new captures retain LOCAL_ONLY policy. Missing vision preserves the photo and returns UNAVAILABLE. |
| OpenRecentCapture | Opens the exact existing capture note. |
| CreateNote | Opens Workspace and invokes its existing quick-note flow. |
| OpenStudy | Opens Study; does not generate a lesson. |
| OpenCurrentInteraction | Opens Home's existing interaction renderer; no response submission. |
| ContinueOnThisDevice | Uses existing InteractionContinuityCoordinator and exact connected managed endpoint; no new run. |
| ReviewRoutine | Validates the existing routine and opens its detail; never invokes RoutineExecutor. |
| SystemDefault / DoNothing | No dispatcher side effects. |

## Native media and UI

Optional platform MediaSession ownership is limited to actual speech playback and explicit opt-in. It is released when speech stops. The default creates no idle session. There is no manifest media-button receiver, permanent media service, global key interception, or fake Media3 player. Halo's existing TTS/AudioTrack playback has no Media3 Player; the platform session delegates stop/repeat to the existing speech owner. Media delivery has a connection-only session identity: it does not pretend a key can be attributed to a specific headphone model.

Settings offers SystemDefault, StopSpeaking, RepeatLastAnswer and DoNothing during speech, plus reset. Devices shows Android-managed headset assistant/media/volume behavior and connection-only identity where applicable. Connected HeyCyan Controls offers supported camera rows, device/class mapping, existing routine selection, reset, and foreground-only Identify. Voice remains native-only to preserve the existing single capture owner. Unverified touch/swipe is displayed as not assignable. Identify observes normalized input; native hardware behavior still applies.

System Check reads endpoint/mapping counts, current owner category and last content-free event/action/outcome. Control logs include only origin/control/gesture/evidence/action/outcome. No MAC, raw packets, audio, image, transcript, routine arguments or credentials are logged. No Room migration or new assistant/runtime is added.

## Validation and outstanding acceptance

See [the single controls physical report](../testing/WEARABLE-CONTROLS-PHYSICAL-003.md). Software gate results are recorded separately from actual hardware observations. No FEATURE-072 physical suite or AI benchmark is repeated.

Outstanding integration requirements: FEATURE-073 watch foreground button metadata and PTT press/release/cancel ownership; held-PTT mapping pinning; configurable TalkToHalo through a shared capture activation hook; physical HeyCyan controls; watch reserved-button and cross-device acceptance. The existing watch app has no physical key interception and therefore keeps power/home/launcher behavior with Wear OS, but this is not proof of the requested new watch-controls implementation.

Merge to main is withheld under A1. Completing the missing owner integration and physical acceptance is necessary before marking FEATURE-074 complete.

## References inspected

- [Wear OS physical buttons](https://developer.android.com/training/wearables/user-input/physical-buttons)
- [Media3 control and playback ownership](https://developer.android.com/media/media3/session/control-playback)
- [android/wear-os-samples](https://github.com/android/wear-os-samples)
- [androidx/media](https://github.com/androidx/media)

## Following development

After integration and acceptance, FEATURE-075 / CONTEXTUAL-CAPTURE-001 should connect confirmed capture to Ask, Study, Note, and explicitly approved Remember workflows using existing Workspace, verified vision/OCR capability routing, memory approval and interaction continuity contracts.

## Required validation matrix

| Required check | Evidence / remaining gate |
| --- | --- |
| 1 Single once | Portable interpreter regression |
| 2 Double suppresses single | Portable interpreter regression |
| 3 Long suppresses release tap | Portable interpreter regression |
| 4 Debounce | Portable interpreter regression |
| 5 Vendor classification is not reinterpreted | Portable interpreter regression |
| 6 Unknown not assignable | Interpreter and policy regressions |
| 7 Device beats class | Mapping regression |
| 8 Class beats generic | Policy regression |
| 9 Reserved cannot override | Policy regression |
| 10 Malformed persistence | Schema/action regression |
| 11 Session ID not durable | Repository regression |
| 12 Talk exactly one capture | Configurable owner hook not implemented; native owner retained |
| 13 Voice boundary plus button one capture | Native bridge retained; combination acceptance not claimed |
| 14 Stop does not replay or undo | Existing speech stop owner; physical acceptance open |
| 15 Repeat zero inference/tools | Replay event regression; only TtsRequested submitted |
| 16 Capture ask waits for confirmed image | ConfirmedControlCapture regression |
| 17 No vision preserves photo | ConfirmedControlCapture regression |
| 18 Continuity no new run | Existing continuity suite plus owner delegation |
| 19 Routine review no execution | Allowlist/review-only dispatcher; hardware acceptance open |
| 20 Idle media no ownership | Scoped session lifecycle regression |
| 21 Media only during active speech | Scoped session lifecycle regression |
| 22 Reserved watch keys rejected | Missing watch integration; no new key interception |
| 23 PTT existing recorder and release | BLOCKED on FEATURE-073 |
| 24 Disconnect cancels pending | Interpreter epoch/pending regressions and action cancellation |
| 25 High-risk tap cannot approve | Action allowlist contains no approval or routine execution |
| 26 Stale epochs rejected | Interpreter and endpoint regressions |
| 27 Held PTT remapping | BLOCKED on FEATURE-073 |
| 28 Existing phone/HeyCyan speech | Existing speech/driver unit gate; hardware HeyCyan NOT_AVAILABLE |

## Final software validation (2026-10-08)

912 unit tests passed with zero failures, errors or skipped tests across host SDK, runtime SDK, cognition, speech, Android host, HeyCyan and app. Phone debug, instrumentation and Wear debug APKs assembled successfully. The final app gate included strict integer schema validation and initialization of the scoped media owner from the existing speech startup path, including background speech. Serial Gradle used one worker, no daemon/parallel and 2 GB heaps.

Installation retry could not start: ADB reports device `f01fe673` not found and an empty device inventory. The new consolidated physical suite has not run. This preserves the single-suite requirement; Sony acoustic, button, idle music, volume and assistant observations remain unverified. FEATURE-073 integration and verified HeyCyan configurable-control ingestion remain open. No main merge is permitted under A1 until dependencies land.
