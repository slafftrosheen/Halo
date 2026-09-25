# Halo current state

Status: CURRENT SOURCE MAP. Provider review updated 2026-09-24.
Source review is correlated with the supplied 2026-09-21 device trace. No post-fix build, instrumented test, benchmark, or hardware rerun is claimed.

[ManualDependencyGraph](app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt) is
the process composition root. [App HaloRuntime](app/src/main/java/com/halo/app/HaloRuntime.kt)
starts the phone Engine/EventBus/Blackboard and speech components. Its source explicitly
excludes the older microkernel/ProcessManager path from normal phone startup.

| Area | Present in code | Limit |
|---|---|---|
| UI | Five primary tabs (Home, Activity, Notebook, Devices, Settings); persistent NB-01 notes/search/lifecycle flow; spatial Halo Presence; System/Light/Dark appearance; reachable controls traced to real actions; shared 48dp/accessibility contracts; Home exposes provider/STT/microphone recovery; pairing exposes Bluetooth permission/radio recovery | Notebook files/memory/conversations/Working Set are not implemented; console/lab/inspector are not AppShell routes; full TalkBack/device acceptance and frame-time performance are not established |
| Presence | Asymmetric rounded-cell renderer driven by HaloRuntimeState; no permanent idle animation; compact signal-cell glyphs in Activity/Notebook/navigation; Android animator-scale zero disables Presence transitions | No measured audio/output envelope yet; low-power behavior beyond platform animation removal is not established |
| Assistant Role | HaloVoiceInteractionSession with Compose-based AssistantSurface, explicit separation of surface hide and interaction cancellation, generic Bluetooth input/output device resolution, and sanitized screen context extraction | Physical acceptance script and Bluetooth communication route continuity require device verification |
| Model tools | Echo, current time, and host actions (app management, volume, flashlight, alarms, media, contacts, dialer, navigation, web browser, settings, and accessibility fallback) registered in HostToolRegistry. RUNTIME-017: every production tool carries a portable `ToolDescriptor` (semantic category, keywords, `ToolSecurityPolicy` risk/confirmation/replay, `ToolExecutionPolicy` timeout/concurrency/retry, requirements, dependencies); `PolicyToolAuthorizer` classifies risk from descriptor metadata instead of tool-name string patterns; `ToolRegistry` enforces declared dependencies and per-tool timeouts clamped to a global ceiling; failures carry structured `ToolFailureKind`; `ToolRetryRules` forbid transparent retry unless replay is SAFE, risk is read-only/reversible and the retry policy explicitly permits the failure kind; `CapabilityCatalogue` + `AndroidCapabilityPublisher` advertise semantic host capabilities (launch/web/navigation/dialer/contacts/alarms/notification-listener/volume/flashlight/audio/workspace); `ToolDiscoveryService` + `halo_discover_tools` meta-tool provide bounded per-turn exposure so normal turns need not carry every schema. Discovery never mutates the registry and never widens authorization. RUNTIME-018: agent runs are durable executions — `ToolInvocation` identity with canonical arguments + SHA-256 digest + stable idempotency keys, append-only execution journal (`ProviderTurnStarted/Completed`, `ToolInvocationPrepared`, `ToolExecutionClaimed`, `ToolOutcomeRecorded`, `InputRequested/Resolved`, `ResponseCommitted`), `CognitiveSession.resume(runId)` reusing the normal execution path behind an atomic `claimResume` CAS, deterministic journal-only `AgentRecoveryPlanner`, durable approval/clarification requests resolved through portable interaction surfaces (RUNTIME-019), idempotent `appendIfAbsent` conversation commit, and startup `prepareOutstandingRunsForRecovery()` that classifies (SUSPENDED/WAITING_*) instead of terminalizing; routine runs keep `NO_RESUME` | Descriptors are declared in the app composition root (`HaloToolDescriptors`); device-capability resolution at execution time and resource leases are not established; discovery/recovery are source-complete but not exercised end-to-end on hardware; legacy runs without digest/replay metadata classify as `UnsupportedLegacyState` and are never auto-replayed |
| Providers | Native llama.cpp is the designated clean-install/default inference provider; Ollama is optional LAN HTTP inference; OpenRouter is optional cloud inference with `openrouter/free` as its default router; Android assistant is handoff-only; Sherpa and Android TTS own speech. GGUF download integrity no longer depends on Halo projecting `general.architecture`. | Model compatibility is still proven by the native loader at load time; Ollama/OpenRouter require explicit setup; Qwen3 Android binding/chat-template behavior still needs physical acceptance. |
| Speech input | Physical HeyCyan microphone capture was validated in the supplied 2026-09-21 run with zero packet/frame drops in sampled turns; external buffers receive whole-capture analysis with labeled Silero/energy estimation; low-gain `SIMPLE_ENERGY` captures are no longer rejected solely by the Silero voiced-ratio threshold | Threshold/corpus acceptance remains manual; post-fix acceptance still requires a fresh hardware run |
| Persistence | Room Memory plus NB-01 workspace-object/note/recovery-draft tables, local note search and lifecycle management; in-memory cognitive/conversation stores | Notebook storage is not cognitive memory; Room-backed CognitiveSession and remaining Notebook object types are not established |
| Reminders | Exact-alarm adapter with simple duration parsing | Complete CRUD, robust ambiguity, permission/reboot recovery not established |
| Notifications/routines/privacy | Named modules/contracts | Operational summarizer, full automation engine and universal network firewall not established |
| Plugins | In-process registration/capability contribution/dispatch | No security sandbox or downloadable plugin installer established |
| Devices | Persisted identities/session handling; HeyCyan/BuddyBot paths; callback-serialized GATT physically validated; CompanionDeviceManager audio association, exact BLE-control-to-Classic-audio identity mapping, process-level association restoration, exact-address route verification, and strict synthesize-to-PCM/AudioTrack playback are implemented. Wearable-origin LLM and deterministic command responses both preserve the wearable target with phone fallback disabled. Recording/media intent completion now requires device mode/media-count evidence; missing evidence becomes `UNCONFIRMED` | Physical HeyCyan speaker output remains unvalidated on the current post-fix build; the next run must prove the glasses are audible and the phone speaker stays silent |
| Performance | Benchmark build type, Macrobenchmark module, Baseline Profile generator, startup/frame metrics, reportFullyDrawn and debug StrictMode are present. Storage/model/secret migration is now asynchronous, WorkManager is lazily initialized, appearance persistence is worker-dispatched, and provider diagnostics/assistant discovery are kept off Main | The supplied pre-fix trace contained a startup ANR and a 133-frame stall; no post-fix benchmark or device rerun has yet demonstrated those regressions are cleared |
| Credentials | Runtime storage is wrapped by `SecretBackedStorage`; OpenRouter credential migration moves the legacy key into Android Keystore-backed secret storage | Keystore migration and backup/restore behavior still require device acceptance across upgrade/process-recreation scenarios |
| UI verification | Robolectric/Roborazzi dependencies; light/dark/1.30x-font snapshots; appearance, interaction, Bluetooth permission mapping, platform mic-state, provider projection, canonical pairing identity, auto-connect selection/migration and reloadable-STT tests | Golden images/tests have not been executed in this source-only pass; whole-screen/device-specific coverage is incomplete |
| CI | Workflow declares lint/test/build/upload | Current success not established; detekt/ktlint configuration mismatch |

Source-only follow-up on 2026-09-24: the HeyCyan transfer path now carries the BLE-decoded
device IP into live device state, prefers that endpoint for media sync, and scopes transfer HTTP to
the glasses Android `Network` instead of process-wide routing. Wearable voice activation can also
start native llama.cpp model loading while capture is in progress, but only when native llama.cpp is
the selected inference provider and the native-resource budget permits concurrent heavyweight
models. Neither change is counted as hardware or latency acceptance until rerun on the physical
glasses/phone pair.

RUNTIME-017 source addition (2026-09-25): the portable tool fabric landed in
`runtime/cognition/.../tools/` — `ToolDescriptor`/`ToolSecurityPolicy`/`ToolExecutionPolicy`/
`ToolRequirement`, `CapabilityCatalogue`/`CapabilityPublisher`, `ToolDiscoveryService`/
`ToolDiscoveryExecutor` and `ToolRetryRules` — with descriptor-driven authorization, dependency
validation and per-tool execution policy in `ToolRegistry`. Android advertises semantic host
capabilities through `AndroidCapabilityPublisher`; no new runtime tool layer imports Android.
Present in code only; not exercised by tests or hardware in this pass.

RUNTIME-019 source addition (2026-09-25): portable interaction surfaces landed in
`runtime/cognition/.../interaction/` — `InteractionSurface`/`InteractionContent`/`InteractionResponse`
(Runtime-owned semantic contracts with no Android imports), `InteractionSurfaceValidator` with hard
content limits, `InteractionSurfaceStore` (in-memory projection; the journal stays the only durable
truth), `InteractionSurfaceFactory` (durable `AgentInputRequest` → surface),
`InteractionSurfaceCoordinator` (journal-derived projection incl. progress/result phases with
`ActionTruth` from the journal), `DefaultInteractionResponseHandler` (atomic
`resolveInputIfPending` CAS on the Room journal; first valid resolution wins) and
`DefaultRunResumptionCoordinator` delegating to `CognitiveSession.resume`. Approvals flow
tool call → durable `AgentInputRequest.Approval` → CONFIRMATION surface → typed response →
resume; the same digest re-emitted after resolution auto-reauthorizes, a changed digest requires a
fresh approval. Clarifications journal durable requests while keeping the temporal interpretation
logic. Android is only the first renderer: `AndroidInteractionPresenter`/`InteractionSurfaceViewModel`
(selected via deterministic priority), `InteractionSurfaceRenderer` branching on `InteractionContent`
only, CONFIRMATION/CHOICE/TEXT/TEMPORAL/PROGRESS/RESULT/ERROR composables, SAF-backed
`AndroidFileSelectionAdapter`, and `VoiceInteractionSurfaceAdapter` reusing the existing TTS path
with origin-device affinity. The ephemeral approval sheet, `DurableApprovalResolver`, the 120-second
global timeout and the `PolicyToolAuthorizer.generatePreview` tool-name switch are removed; risk
copy comes from `ToolPresentation` metadata on descriptors. IMPLEMENTED / UNVALIDATED: no test or
hardware validation in this pass.

RUNTIME-018 source addition (2026-09-25): durable execution journal and recovery planner landed
in `runtime/cognition/.../agent/` (`ToolInvocation`, `AgentRunJournal`, `AgentInputRequest`) and
`.../agent/recovery/` (`RecoveryPlan`, `AgentRecoveryPlanner`, `AgentCheckpointAssembler`,
`AgentContinuation`, recovery events). `ToolDispatched` is superseded by `ToolExecutionClaimed`
(persisted before execution; legacy records decode conservatively as claims); provider turns and
tool outcomes are journaled before the next dependency; approvals and clarifications are durable
journal records instead of live `CompletableDeferred`s; startup classifies outstanding runs
instead of terminalizing them; conversation commits are idempotent. IMPLEMENTED / UNVALIDATED:
no test, compile, or hardware validation was performed in this coding pass.

RUNTIME-020 source addition (2026-09-25): portable resource lease fabric landed in
`sdk/runtime-sdk/.../resource/` (`ResourceId`, `ResourceEndpointId`, `ResourceLeaseId`,
`ResourceKey`, `HaloResourceIds`, `ResourceHolder`, `ResourceAccess`, `ResourceClaim`,
`ResourcePriority`, `ResourceWaitPolicy`, `ResourcePreemptionPolicy`, `ResourceYieldHandler`,
`ResourceLeaseRequest`, `ResourceConflict`, `ResourceAcquireResult`, `ResourceLeaseManager`) and
`runtime/kernel/.../resource/DefaultResourceLeaseManager` (atomic claim bundle acquisition,
shared/exclusive collision matrix per `ResourceKey`, cooperative yield requests invoked outside
manager lock, wait loop with coroutine cancellation safety, idempotent release, StateFlow
snapshotting). Capabilities resolve into resource plans via `DeviceCapabilityResolver` (typed
matching, explicit `CapabilityAffinity`, candidate ordering), `CapabilityResourceBinding`, and
`ResourcePlanner`. Scoped resource leases are acquired centrally around tool execution via
`ToolResourceCoordinator` and `CognitiveSession`, with resource blockage journaled as
`AgentStep.ResourceBlocked` and run state transitioned to `WAITING_FOR_RESOURCE` (distinct from
`WAITING_FOR_CAPABILITY`). `HaloAudioSessionCoordinator` and `NativeResourceCoordinator` bridge
onto the generic resource manager. `LeasedStillImageSource` and `LeasedWirelessTransferCapability`
provide device-neutral leased wrappers. `HeyCyanResourceArbiter` remains inside the driver.
IMPLEMENTED / UNVALIDATED: no test, compile, or hardware validation in this pass.

Additional recent source additions: The codebase now includes structured `AiFailureDetails`, provider retry/cooldown behavior, `LOCAL_ONLY` routing, unified error presentation, detailed interaction turn lifecycles, barge-in evidence tracking, observation freshness and supersession, explicit local-inference readiness/preload stages, latency tracing, HeyCyan arbitration and thumbnail decoding work, and significant `llama.cpp` runtime improvements. As with the rest of the file, these are present in code but not yet validated as physically accepted on real hardware.

See [architecture](docs/04-architecture/ARCHITECTURE.md),
[providers](docs/08-providers/ARCHITECTURE.md),
[persistence](docs/04-architecture/DATABASE.md),
[security](docs/12-security/THREAT_MODEL.md),
[HeyCyan](docs/protocols/heycyan/IMPLEMENTATION_STATUS.md) and
[audit ledger](docs/engineering/DOCUMENTATION-AUDIT.md).

Read [release readiness](docs/13-engineering/README.md) separately. Code presence is not measured
latency, power consumption, passing tests or physical acceptance.
