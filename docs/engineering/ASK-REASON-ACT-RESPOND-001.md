# ASK-REASON-ACT-RESPOND-001 — single owned request through user-visible result

**Date:** 2026-10-09  
**Status:** SOURCE INTEGRATED DIRECTLY INTO `main`; NEW REGRESSIONS AUTHORED; ANDROID/DEVICE ACCEPTANCE PENDING.  
**Baseline:** `2c330fb63c28b0eeeefbbead64bcc65053c87141` (after PRODUCT-RUNTIME-STABILITY-AUDIT).  
**Policy:** No GitHub Actions. No model/provider switching, automatic cloud escape, new privileges, or new execution engine.

## Goal and execution ownership

The goal is one trustworthy interaction:

`User input → Observation/Interpretation → route/workload → optional approved action → canonical response commit → conversation and output route → user-visible receipt`.

This is a *vertical integration and reliability pass*, not a claim that every model or Android device now works. There is still one interpreter, one cognitive active-turn coordinator, the existing capability/skill authorization boundary, existing durable action journals, one response committer, and the existing speech route owner.

| Stage | Real owner | Guarantee strengthened this pass |
| --- | --- | --- |
| Ask | Home text submission / speech recognition / Blackboard `InterpretationKnowledge` | Busy concurrent speech input no longer disappears silently; failed interpretation admission does not kill the only Blackboard consumer |
| Reason | `CognitiveTurnPreparer`, `ProviderTurnExecutor` and local fact resolver | Local date/time is tracked like a turn; already-visible stream tokens survive an empty final response body |
| Act | `NativeCapabilityDispatcher`, skill authorizer and durable `AgentRunCoordinator` | Native request startup has one request identity/receipt; waiting action is **not** signaled as completed |
| Respond | `DefaultCognitiveResponseCommitter`, Home receipt tracker and Android assistant state | Nonterminal approval/input requests show a review state; only terminal responses generate completion events; pending speech prompts retain the selected wearable output/fallback policy |

## Applied source changes

### 1. Local factual answers use the same tracked turn lifecycle

`runtime/cognition/.../session/CognitiveSession.kt` previously started `SystemFactIntent` work in an untracked `scope.launch` and manually published `CognitiveCompleted`/`AssistantResponsePublished`/`TtsRequested`. This bypassed the active turn, cancellation, canonical commit idempotence and unified conversation routing.

Now the local fact is launched `LAZY` through `launchTracked`, resolves a stable interaction and request ID, records `RuntimeFactResolved` only for real facts, commits via `CognitiveResponseCommitter`, uses `responseTarget`/`fallbackFor`, and emits terminal state only after commit. Cancellations do not append conversation turns or speak late answers. Resolver failure gets an explicit error; an unsupported local fact produces an explicit unavailable response instead of vanishing.

### 2. Provider streams commit exactly what was visible

`ProviderTurnExecutor.kt`: when a provider emits user-visible `Token` events but the final `Completed` response has blank text and no tool calls/handoff, the already-visible text is retained as the committed answer. Tool calls still follow their structured assembly path; the stream/partial-output retry guard remains unchanged. A final response without visible text or actionable tool calls is still rejected by `AiResponseValidator`, rather than invented.

### 3. Native actions do not claim success during approval handoff

`CognitiveSession.kt`: native initiation emits one `CognitiveRequestStarted` identity, not separate start and response identities. For `WaitingForInput`, `WaitingForCapability`, or `WaitingForResource` tool outcomes, it does not publish `InteractionTurnState.Completed`. A pending result still delivers an audible instruction for speech-originated requests using the **recorded** target and fallback; with `DISALLOW_PHONE` it must not be rerouted to the phone speaker.

`CognitiveResponseCommitter.kt`: only terminal responses emit `CognitiveCompleted`/`InferenceObserved(success=true)`. A nonterminal response does not append a fake final conversation turn, and its `AssistantResponsePublished` carries `continuationPending=true`.

### 4. User interface and native assistant show what happens next

`TextSubmissionState.kt`: new `WAITING_FOR_INPUT` phase is not a busy generator, not success, and not an automatic retry request. Home displays a localized review label and a real **Open in Activity** button wired by `AppShell.kt` to the existing Activity route.

`AssistantInvocationCoordinator.kt`: pending interactions survive speech prompt playback as `WAITING_FOR_INPUT`; speaking a prompt is not proof the underlying action executed. This temporary state is cleared when closing/superseding the invocation. Existing Android assistant foreground/cancellation/permission contracts remain unchanged.

### 5. Avoid invisible intake drops and dead interpreter streams

When a recognized speech/typed observation is rejected because another turn owns the runtime, `CognitiveSession` publishes an interaction-scoped `TURN_ADMISSION` failure and visible failure state instead of a log-only drop. A bad interpretation/admission dependency raises a scoped `INTENT_DISPATCH` failure while the Blackboard collector keeps processing later observations. No concurrent tool transaction is silently started.

## Added/extended regression coverage (authored; not executed here)

- `CognitiveSessionSpeechSerializationTest`: local fact commits/publishes/speaks exactly once without a model; delayed local fact suppressed on cancellation; concurrent speech admission produces explicit busy feedback.
- `ProviderTurnExecutorTest`: preserve streaming visible text when `Completed.text` is empty.
- `CognitiveSessionToolTest`: wearable-origin question → tool-capable reasoning → authorized tool → committed conversation reply → routed speech with `DISALLOW_PHONE`.
- `TextSubmissionStateTest`: pending native reply shows review and does not become retryable success.
- `CognitiveResponseCommitterLifecycleTest`: pending action emits visible handoff without false completed/voice event, later terminal commit delivers exactly one history + speech receipt, duplicate terminal commit is inert.

Other tests in the repository already cover provider role/policy pinning, tool authorization/denial, action outcome evidence, run recovery, speech stream eligibility, audio route states and history integrity, but **these new changes have not run the complete local suite yet**.

## Exact acceptance gate — before declaring this slice release-ready

**Development machine, serial and without GitHub Actions:**

```sh
./gradlew :sdk:runtime-sdk:test :runtime:cognition:test :app:testDebugUnitTest :app:assembleDebug
```

If that succeeds, install the resulting debug APK and capture the installed source SHA and Android/firmware versions.

| Case | Success evidence | Failure signal to capture |
| --- | --- | --- |
| A01 typed Home direct chat | Exactly one accepted turn, stream and final reply, single conversation turn, no TTS | UI stuck processing, blank reply, duplicate turn |
| A02 voice direct chat | STT transcript source/capture matches answer; route status visible | Incorrect mic source, unanswered recognized text |
| A03 local time/date | No provider request; one local fact response, optional speech | Late fact after cancel, missing conversation turn |
| A04 tool-enabled phone command | Permission/approval gate, action truth, no repeat on retry | Dispatched presented as confirmed, duplicate side effect |
| A05 authorization denied | No tool execution, actionable visible rejection | Action proceeds or response disappears |
| A06 approval needed | Home shows review + Open in Activity; no terminal success receipt | “Completed” shown before user approves |
| A07 partial provider failure | Partial text retained, no silent replay of side-effecting tool | Duplicate side effect, fake final |
| A08 streaming final text blank | Already-visible text saved and spoken once | Blank history, blank speech |
| A09 cancel/supersede | Old fact/model/tool no longer commits or speaks | Late response replaces newer user turn |
| A10 busy speech | New request receives scoped admission failure, current run continues | Silent dropped utterance |
| A11 HeyCyan hardware | Glasses microphone → approved action → reply audible *only in glasses* when fallback disallowed | Phone-speaker leak, unverified A2DP endpoint |
| A12 process loss/restore | Durable run reopens with correct waiting/terminal evidence and original conversation | Duplicate tool replay, wrong conversation |
| A13 language toggle | EN/RU/LV interface switches mid-wait, no draft/permission loss | Pending status untranslated, navigation broken |
| A14 remote/offline provider | No hidden remote fallback, accurate network/error state | Silent cloud use, stale model switch |

**Hardware acceptance remains indispensable** for live Android role changes, source-bound HeyCyan audio, LiteRT local model startup, native phone control, and actual TTS playback. A successful unit test or Compose state cannot certify a BLE or audible result.

## Forward direction

1. Run compilation and fix any actual Kotlin/Android regressions first; do not start a new feature wave.
2. Test the vertical slice on the development Samsung with a known local LiteRT model and one explicitly chosen remote provider. Capture event correlation with redacted request IDs, not raw prompts/credentials.
3. Verify actions that enter `WAITING_FOR_INPUT` through Activity, denial and approval, and ensure the same durable run resumes once without replay.
4. Do the full HeyCyan Bluetooth/offline/phone-fallback-negative test; acceptance is **pending**, not assumed.
5. Only after these gates prioritize latency profiling and additional hardware controls. FEATURE-073 watch microphone/PTT remains separate, unimplemented work.

**Verification boundary:** GitHub commits and file content were checked using the repository interface. This tool environment cannot run Gradle, install Android APKs, or exercise the attached glasses. The code is integrated; build, tests and real-device success are still unverified.
