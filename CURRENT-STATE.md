# Halo current state

Status: CURRENT SOURCE MAP. Provider review updated 2026-09-24.
Source review is correlated with the supplied 2026-09-21 device trace. RUNTIME-048 is source-complete but uncompiled and has not been unit- or physical-device-verified.

RUNTIME-048 source addition (2026-09-28): Android assistant enrollment now treats `RoleManager` ownership and the exact active `VOICE_INTERACTION_SERVICE` component as separate truths, exposes OEM Default Assistant settings after an unsuccessful role request, and records bounded enrollment/runtime/recognizer lifecycle diagnostics. `VoiceInteractionSession` and `ACTION_ASSIST` converge through one invocation coordinator. AI selection now has one persisted `(providerId, modelId)` authority; legacy provider model keys are migration input only. Explicit routing stays on the selected provider after failure. System Check reports the selected target without generating on open and offers a separate bounded **Run AI probe** action. LiteRT distinguishes verified-on-disk, selected-cold, resident, and generation-proven states, keys generation proof to artifact fingerprint plus LiteRT-LM 0.17.1, and exposes bounded staged failure diagnostics. SOURCE COMPLETE / COMPILED: NO / UNIT VERIFIED: NO / PHYSICAL DEVICE VERIFIED: NO.

RUNTIME-048 continuation (2026-09-28): legacy provider-specific model keys are now guarded by a one-time migration marker and are excluded from new Brain exports/import authority. Normal cognitive turns and explicit probes both feed content-free last-execution evidence (provider, model, success, backend, latency, failure stage and diagnostics ID). LiteRT acquisition failures now report DOWNLOAD, HASH_VERIFY, INVENTORY or CANCEL alongside engine/conversation/generation/teardown stages. Assistant runtime transitions reject uncorrelated events without an interaction ID and return to idle after correlated speech completion. SOURCE COMPLETE / COMPILED: NO / UNIT VERIFIED: NO / PHYSICAL DEVICE VERIFIED: NO.

[ManualDependencyGraph](app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt) is
the process composition root. [App HaloRuntime](app/src/main/java/com/halo/app/HaloRuntime.kt)
starts the phone Engine/EventBus/Blackboard and speech components. Its source explicitly
excludes the older microkernel/ProcessManager path from normal phone startup.

| Area | Present in code | Limit |
|---|---|---|
| UI | Five primary tabs (Home, Activity, Notebook, Devices, Settings); persistent NB-01 notes/search/lifecycle flow; spatial Halo Presence; System/Light/Dark appearance; reachable controls traced to real actions; shared 48dp/accessibility contracts; Home exposes provider/STT/microphone recovery; pairing exposes Bluetooth permission/radio recovery | Notebook files/memory/conversations/Working Set are not implemented; console/lab/inspector are not AppShell routes; full TalkBack/device acceptance and frame-time performance are not established |
| Presence | Asymmetric rounded-cell renderer driven by HaloRuntimeState; no permanent idle animation; compact signal-cell glyphs in Activity/Notebook/navigation; Android animator-scale zero disables Presence transitions | No measured audio/output envelope yet; low-power behavior beyond platform animation removal is not established |
| Assistant Role | HaloVoiceInteractionSession with Compose-based AssistantSurface, explicit separation of surface hide and interaction cancellation, generic Bluetooth input/output device resolution, and sanitized screen context extraction | Physical acceptance script and Bluetooth communication route continuity require device verification |
| Model tools | Echo, current time, and host actions (app management, volume, flashlight, alarms, media, contacts, dialer, navigation, web browser, settings, and accessibility fallback) registered in HostToolRegistry. RUNTIME-017: every production tool carries a portable `ToolDescriptor` (semantic category, keywords, `ToolSecurityPolicy` risk/confirmation/replay, `ToolExecutionPolicy` timeout/concurrency/retry, requirements, dependencies); `PolicyToolAuthorizer` classifies risk from descriptor metadata instead of tool-name string patterns; `ToolRegistry` enforces declared dependencies and per-tool timeouts clamped to a global ceiling; failures carry structured `ToolFailureKind`; `ToolRetryRules` forbid transparent retry unless replay is SAFE, risk is read-only/reversible and the retry policy explicitly permits the failure kind; `CapabilityCatalogue` + host/device publishers advertise semantic capabilities; `ToolDiscoveryService` + `halo_discover_tools` provide bounded per-turn exposure and discovered schemas become real provider-callable tools on subsequent rounds. RUNTIME-018/019 add durable execution/recovery plus portable human-input surfaces. RUNTIME-020/020.1 add live device resolution and process-wide resource leases before tool side effects. | These foundations are implemented but intentionally UNVALIDATED in this coding phase; provider/tool/resource/device interactions still require the planned compile/test/HIL stabilization pass. Legacy runs without digest/replay metadata classify conservatively and are never auto-replayed. |
| Providers | Embedded LiteRT-LM (`provider.litert.local`) is the designated clean-install/default on-device inference provider running packaged `.litertlm` models on GPU/CPU; Ollama is optional LAN HTTP inference (`provider.ollama`); OpenRouter is optional cloud inference (`openrouter`); Android assistant is handoff-only; Sherpa and Android TTS own speech. | Model compatibility is proven by the LiteRT native Engine at load time; Ollama/OpenRouter require explicit setup. |
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
start selected local-model loading while capture is in progress, but only when LiteRT-LM is
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
`runtime/cognition/.../interaction/`. Handlers now implement `InteractionSurfaceHandler` (Speech,
Routine, Notebook, Intent).

RUNTIME-021 source addition (2026-09-25): capabilities logic enhanced for routing decisions.
Introduced `AiWorkloadSpec`, context assemblies, `VisualContextSource`, and explicit policy
gates to evaluate local vs. cloud sharing and transient capability needs.

STABILIZATION-001 source addition (2026-09-25): recovery planner semantics heavily revised to rely
on explicit outcome evidence (e.g. `ToolCallResolution`) and journal `RecoveryClaimed` steps. Tool rejections
now follow a stable reason-code path, flowing back into the active turn instead of spawning
new generic interactions. `AiInferenceProvider` streams enforce absolute `[DONE]` bounds. Fixed
Android synchronous-IO (shared preferences, `DocumentFile`, content-resolvers) occurring on Main.

RUNTIME-022 source addition (2026-09-26): extracted cognitive turn execution kernel from `CognitiveSession` into `turn/` package (`CognitiveTurnRequest`, `PreparedCognitiveTurn`, `ActiveTurnCoordinator`, `ProviderTurnExecutor`, `ToolTurnExecutor`, `CognitiveTurnPreparer`, `CognitiveResponseCommitter`, `DefaultAgentRecoveryExecutor`). `CognitiveSession` is reduced to a public façade.

RUNTIME-023 source addition (2026-09-26): Android Assistant role enrollment upgraded to verify active `VoiceInteractionService` identity alongside `RoleManager`. Local model catalog supports multi-file GGUF artifacts (base model + `mmproj` projector). Native `LlamaCppRuntime` supports projector loading and multimodal image prediction. Prompts format and parse `<halo_tool_call>` envelopes for prompt-mediated local tool execution.

RUNTIME/UI-024 source addition (2026-09-26): Consolidated presence visuals onto `HaloSignalFace` (outer semantic state halo + inner graphite OLED pixel display). Introduced unified grid-based `HaloGlyph` vector pixel pictogram system (`HaloGlyph`, `HaloGlyphs`, `HaloGlyphRenderer`, `HaloIcon`). Replaced old `HaloOrb`, `HaloStateOrb`, and spatial-cell renderers. Migrated primary navigation, Settings categories, Devices, and launcher icon onto the unified glyph identity. Supersedes 2026-09-18 spatial-cell direction.

RUNTIME-025 / STABILIZATION-002 source addition (2026-09-26): Hardened end-to-end execution path and added `ApprovalContractTest` and `ProviderTurnExecutorTest` contract suites. Added `SystemCheckScreen` (`runtime/system-check`) and `SystemCheckViewModel` providing diagnostic hardware, local model, microphone, audio route, and tool approval probes. Created `PHONE-ACCEPTANCE.md` executable test script.

RUNTIME-026 source addition (2026-09-26): Fixed Android Assistant enrollment dead-tap issue by implementing candidate fallback intents (`ROLE_ASSISTANT` -> `DEFAULT_APPS_SETTINGS` -> `VOICE_INPUT_SETTINGS` -> `GENERAL_SETTINGS`). Introduced `GgufArtifactClassifier` and `GgufArtifactRole` to filter out imatrix/MTP/shards from selectable model choices. Wired `mmproj` vision projector pairing and staged image input into native `LlamaHelper.predict`. Serialized native llama.cpp lifecycle in `LlamaCppRuntime` to prevent double releases and native mutex crashes. Deferred startup storage directory creation and preference initialization off the Main thread to eliminate StrictMode disk read violations.

RUNTIME-027 / MIGRATION source addition (2026-09-26): Completely removed llama.cpp/GGUF native runtime (`providers:llamacpp`) and replaced it with embedded LiteRT-LM 0.17.1 (`providers:litert`). Split Ollama into standalone `:providers:ollama` module. Implemented `LiteRtRuntime` and `LiteRtLocalProvider` (`provider.litert.local`) with state/inference mutex separation, persistent model-scoped cache, streaming, cancellation via `Conversation.cancelProcess()`, and GPU-to-CPU fallback. Updated catalog, model inventory, and System Check to `.litertlm` models. Retained legacy `.gguf` files on disk with explicit user cleanup action.

RUNTIME-030 / LITERT-RECOVERY-001 source addition (2026-09-27): Repaired the on-device model acquisition and verified-inference path. LiteRT-LM runtime stays at **0.17.1**. The built-in catalog now exposes **exactly one** downloadable artifact: `qwen3-0.6b-dynamic-int4` ("Qwen 3 0.6B Dynamic INT4") from `litert-community/Qwen3-0.6B`, pinned to immutable revision `a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76`, `344671744` bytes, SHA-256 `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856`. Downloading moved to an OkHttp downloader with an explicit failure taxonomy (`ModelDownloadFailure`), a strict finalize ordering (stream -> fsync -> byte count -> SHA -> sidecar `.json.part` -> atomic move with `AtomicMoveNotSupportedException` fallback -> refresh -> require exact resolution -> only then `Installed`), per-model cancellation, and single-flight protection. Artifact validation is derived from the physical file: `LocalModelValidation` (`VERIFIED_ARTIFACT` / `UNVERIFIED` / `CORRUPT` / `INCOMPATIBLE`) replaces the previous blanket `valid = true`. Truth rules now enforced: installed != verified, selected != resident, resident != generation-proven. Gemma is **withheld** (upstream `litert-community/Gemma3-1B-IT` is license-gated and Halo has no HF auth or gated-license acceptance flow; no mirrors will be added). Arbitrary SAF `.litertlm` imports remain `UNVERIFIED` until qualified, and a byte-identical import of the pinned artifact is canonicalized to `qwen3-0.6b-dynamic-int4` instead of creating a duplicate. The previous catalog entries were unusable by construction: the Qwen pin used SHA-256 of the empty string with a rounded `345_000_000` byte count, the Gemma pin used SHA-256 of the literal `test`, and the downloader rejected responses whenever the CDN's `Content-Length` differed from the expected size. Model selection was also wrong: the Model Browser called the generic `selectModel()`, which bound a LiteRT model ID to whatever provider was selected (e.g. OpenRouter). Model Browser now calls `selectLocalModel()`, which uses `router.selectTarget()` as the single writer for `active_ai_provider`, `active_ai_model` and `litert_model`. Load failures preserve their native cause (`LiteRtLoadDiagnostics` + `LiteRtModelLoadException` with both GPU and CPU causes), GPU and CPU use separate cache directories, non-tool models never receive tool schemas, an arm64-v8a ABI preflight runs before native load, unknown-context imports use the model default instead of a forced 4096, and an explicit `verifyModel()` qualification probe backs a Model Browser **Verify** action. UNIT-VERIFIED / PHONE ACCEPTANCE NOT YET RUN — do not describe this as "LiteRT fixed" until the physical-phone checklist in the task record passes.

RUNTIME-031 / LITERT-HARDEN-002 source addition (2026-09-27): Completes the accelerator-fallback contract for the on-device route, harvested from an external reference implementation. `LiteRtRuntime.generate()` now recovers from a **late GPU failure**: some devices (notably emulators and older GPUs) pass `Engine.initialize()` and only fail at first inference. The runtime retries once on the CPU when the request asked for `AUTO`, the resident engine is on the GPU, no token was emitted, the turn was not cancelled, and the residency has not already consumed its single rebuild. The rebuild runs under `loadMutex` and outside `inferenceMutex`, matching `loadModel`'s lock order so it cannot invert, and preserves the original GPU cause in a `LiteRtModelLoadException` when it cannot be installed. Engine teardown now runs in `withContext(NonCancellable + Dispatchers.Default)` because releasing weights takes seconds and a cancelled caller could otherwise abort the native close. A cancelled turn no longer reports completion: `cancelRequested` is re-checked after the stream ends, so partial output is never presented as a finished response. A new optional SDK capability, `LocalRuntimeBackendReporting`, lets a runtime republish its effective accelerator and fallback reason after load; `LocalModelSession` re-reads it after every generation, so provider status and the model browser cannot keep claiming GPU after a CPU rebuild. Harvest ledger and the open gap list for the OpenRouter, agentic-skill and phone-control routes: `docs/engineering/REFERENCE-HARVEST.md`. UNCOMPILED AT COMMIT TIME (no build was run for this batch) — verify with a build before trusting it.

RUNTIME-029 source addition (2026-09-26): Hardened LiteRT-LM 0.17.1 runtime using 100% public APIs (`Engine.initialize()`, `engine.createConversation()`), eliminating reflection hacks. Enforced `automaticToolCalling = false` on `ConversationConfig`. Hardened AUTO (GPU -> CPU), GPU-only, and CPU-only backend selection. Integrated `NativeResourceCoordinator.acquire(this)` in `LiteRtLocalProvider` before Engine loading. Refreshed catalog with 64-character SHA-256 integrity checks and Qwen3 0.6B Dynamic INT4 (`litert-community/Qwen3-0.6B`). Added SAF `.litertlm` import, sidecar metadata (`<model>.halo-model.json`), and safe model deletion with fingerprint-scoped cache purging. Added `ConversationSegmenter` for atomic tool-chain context trimming.
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
snapshotting).

RUNTIME-020.1 source addition (2026-09-25): Runtime fabric integration and UI convergence pass.
Discovered tool expansion converts `halo_discover_tools` calls into real callable provider schemas for
subsequent turn execution (`ToolExposureSet` + `ToolExposurePlanner`), removing fake
`halo_tool_catalogue` pseudo-tools and persisting exposure across recovery. `CapabilityCatalogue`
exposes a live, observable `StateFlow<CapabilityCatalogueSnapshot>` with typed `CapabilityInstance`s.
`AndroidCapabilityPublisher` refreshes host capabilities dynamically into `"host:primary"`.
`RuntimeDeviceCapabilityBridge` connects saved/active devices into `CapabilityCatalogue` and implements
`RuntimeDeviceSource` for live resource resolution in `ToolResourceCoordinator`. `ToolAvailabilityResolver`
checks skill policies and capability availability before tool discovery and execution. `ToolDescriptor`
declares explicit `ResourceRequirement`s. Resource arbitration in `DefaultResourceLeaseManager` uses
owner-registered cooperative yield handlers; lease releases are non-blocking. Centralized UI formatting
(`RuntimeUiFormatters`, `RuntimeUiProjection`) powers Home (durable waiting states, real route names),
Activity (semantic step titles, resource blockage, human action truth), Skills (live capability
availability, tool presentation titles, risk copy), Devices (generic host section, runtime capability
chips, live active resource leases), and Settings → Runtime status screen (`RuntimeScreen`).
`InteractionSurfaceRenderer` renders `COMPACT` mode inline cards inside `AssistantSurface` without
`AlertDialog` popups overlaying the assistant screen.
IMPLEMENTED / UNVALIDATED: no test or hardware validation performed in this pass.

RUNTIME-021 source addition (2026-09-25): Workload-aware provider routing and multimodal context fabric pass.
Inverted `CognitiveSession` dependency ordering so workload planning (`AiWorkloadPlanner`) and typed context
acquisition (`ContextAcquisitionCoordinator`) run BEFORE provider target resolution. `AiWorkloadSpec` declares
required/optional capabilities (`TEXT`, `IMAGE`, `TOOLS`, `STREAMING`), minimum context window, and remote
context allowance. `AiExecutionTarget` exposes `effectiveCapabilities` derived from provider capabilities,
model capabilities, and Halo adapter support. `AiProviderRouter` validates execution targets against
workload requirements before generation and respects routing policy (`EXPLICIT`, `LOCAL_ONLY`,
`PREFERRED_WITH_FALLBACK`, `AUTOMATIC`). `ImageContext` carries provenance attributes (`assetId`, `interactionId`,
`originDeviceId`, `source`, `trust`, `freshness`). `AiContextPolicy` enforces cloud context gates
(`allowCloudImages`, `allowCloudScreenContext`, `allowCloudWorkspace`). Target provider/model remains
immutable across tool execution rounds of a cognitive transaction.
IMPLEMENTED / UNVALIDATED: no test or hardware validation performed in this pass.

Additional recent source additions: The codebase now includes structured `AiFailureDetails`, provider retry/cooldown behavior, `LOCAL_ONLY` routing, unified error presentation, detailed interaction turn lifecycles, barge-in evidence tracking, observation freshness and supersession, explicit LiteRT readiness/preload stages, latency tracing, and HeyCyan arbitration and thumbnail decoding work. As with the rest of the file, these are present in code but not yet validated as physically accepted on real hardware.

See [architecture](docs/04-architecture/ARCHITECTURE.md),
[providers](docs/08-providers/ARCHITECTURE.md),
[persistence](docs/04-architecture/DATABASE.md),
[security](docs/12-security/THREAT_MODEL.md),
[HeyCyan](docs/protocols/heycyan/IMPLEMENTATION_STATUS.md) and
[audit ledger](docs/engineering/DOCUMENTATION-AUDIT.md).

Read [release readiness](docs/13-engineering/README.md) separately. Code presence is not measured
latency, power consumption, passing tests or physical acceptance.


### RUNTIME-020.1 review correction — 2026-09-25

A direct post-agent review corrected several integration gaps before merge: the obsolete
`catalogueSummaryDefinition()` call was removed; the discovery service/registry lazy cycle was
broken; per-turn exposure now preserves schemas already supplied to the provider; capability
catalogue snapshots preserve multiple owners for the same semantic capability; device capability
IDs are distinct from resource IDs and are matched exactly after narrow legacy normalization;
connected HeyCyan/BuddyBot sessions publish semantic `device.*` capabilities; host/device
capability state is refreshed on foreground and permission/radio changes.

Resource arbitration now uses one process-wide ownership universe with one state lock, no
same-run/same-interaction exclusivity bypass, owner-registered cooperative yield handlers and
non-suspending lease release. Tool execution records `ToolExecutionClaimed` only after required
capabilities/resources are admitted, so a `WAITING_FOR_RESOURCE` run does not falsely imply an
external side effect. Recovery reacquires resources before replaying an already-claimed SAFE tool;
a merely prepared/unclaimed tool re-enters the normal provider/authorization path instead of being
executed directly.

Home, Skills and Devices now consume the same durable task/capability/resource projections already
used by Activity and Settings → Runtime. This remains **IMPLEMENTED / UNVALIDATED**; no test or
hardware acceptance is claimed by this correction pass.

### RUNTIME-021 review correction — 2026-09-25

The post-agent review corrected the initial RUNTIME-021 skeleton into one coherent execution path.

- Workload planning now carries the active context-sharing policy before provider selection. Required
  screen/workspace/image context can forbid remote inference without provider-name heuristics.
- Provider routing resolves from the planned workload before optional hardware capture. Required
  visual workloads therefore reject text-only models before a camera is touched; optional images are
  not captured for a target that cannot consume them.
- `AiExecutionTarget.effectiveCapabilities` is the provider/adapter × model intersection. Explicit
  targets stay explicit; AUTOMATIC/PREFERRED routing may choose another compatible model/target.
- Context acquisition now preserves exact interaction/origin provenance and can request a fresh
  origin-device still image through a portable `VisualContextSource`. Android host composition maps
  connected HeyCyan to `HeyCyanStillImageSource` behind the generic Runtime camera lease while the
  driver arbiter remains authoritative below it.
- Acquired screen/image/reference candidates are now authoritative input to
  `DefaultContextAssembler`; the assembler no longer performs a second unrelated "latest image"
  search for RUNTIME-021 turns. CURRENT_VIEW freshness remains 10 seconds, RECENT_CAPTURE 60 seconds,
  and explicit REFERENCE context is not age-expired.
- Context budgeting is explicitly approximate (`ContextTokenEstimator` / `ContextBudget`) and
  prioritizes required current context and explicit references ahead of older conversation history.
- Cloud workspace/screen/image gates are persisted and exposed in Intelligence UI. Unimplemented
  Android background still capture is no longer advertised as an available host capability.
- Ollama now inspects model metadata through `/api/show`, advertises model vision only from returned
  capability metadata, serializes image chat input, and deliberately keeps tools disabled until Halo's
  Ollama adapter implements the complete tool-call/result round trip.
- Durable provider-turn metadata records workload-required AI capabilities and metadata-only context
  requirements. Recovery classifies CURRENT_VIEW/screen/request-bound image context as
  `MissingContext` rather than silently substituting unrelated recent data.

This remains **IMPLEMENTED / UNVALIDATED**. The coding phase did not claim compile-suite, provider,
phone, HeyCyan, latency, battery or HIL acceptance; those move to STABILIZATION.

### RUNTIME-044 / RUNTIME-045 — provider failure truth + skill declaration ownership — 2026-09-28

**IMPLEMENTED / UNVALIDATED** (no build or test was run in this pass; verification was source-level).
Harvested from the local reference archives recorded in
[docs/engineering/REFERENCE-HARVEST.md](docs/engineering/REFERENCE-HARVEST.md).

- `AiErrorType.CONTEXT_TOO_LARGE` had a presenter string and a router rejection but **no producer**, so
every OpenAI-compatible context-overflow response was reported as a generic malformed request. The new
provider-neutral `ContextOverflow` owner now decides that classification from an authoritative provider
code or a narrow prose fallback, and OpenRouter maps a recognized `400` to `CONTEXT_TOO_LARGE` with
`Recoverability.PERMANENT`. The failure is actionable (compact context or pick a larger-context model)
while replaying the identical request stays wrong.
- `DefaultRetryPolicy.nextDelay` doubled without bound and applied `1 shl (attempt - 1)` directly, which
overflowed into a negative `Int` from `attempt >= 32`. Growth is now capped at 30 s with a bounded
exponent; the recorded 100/200/400/800/1600 ms sequence is unchanged.
- Ten of eleven built-in skills were declared twice (Runtime registry + shipped asset manifest) and the
loader resolved duplicates last-wins, so the asset always won. For `halo.phone` the asset declared
`EXTERNAL_SIDE_EFFECT` while the registry declared `SENSITIVE`, meaning Halo shipped the **weaker**
classification of its highest-risk capability. New `BuiltInSkillMerge` keeps the strictest declared risk
at skill and per-tool level and unions tools/permissions, so neither document can weaken the other.
`halo.phone.json` was corrected to `SENSITIVE` and four unread `assets/skills/*.yaml` duplicates were
deleted.
- Correction: the RUNTIME-038…043 "delivered" record was verified against source and is accurate; the
stale OPEN/PARTIAL rows in the harvest ledger were the wrong side of that discrepancy.

Remaining open on these routes: RUNTIME-044b (structured retry kinds instead of reason-text matching),
RUNTIME-045b (skill instruction document channel), RUNTIME-047 (local stop sequences).

### RUNTIME-046 — compacted phone observation — 2026-09-28

**IMPLEMENTED / UNVALIDATED** (no build or test was run in this pass; verification was source-level).

Halo's screen projection was the thin part of the phone route: one loose substring chain over widget
class names that never consulted the parent, raw pixel bounds on every node, no container collapse and
no account of what a scrollable had hidden. Unchanged and still inherited from the earlier work:
per-action consent, redaction, opaque snapshot-scoped tokens, settled snapshots, receipts, blocked
packages and the untrusted-content framing.

- Roles come from `AccessibilityProjectionRules`, a pure object with an exact class table, then a
bounded suffix table (specific-first: `ToggleButton` before `Button`, `AutoCompleteTextView` before
`TextView`), then platform flags. `TAB`, `CHIP`, `TOOLBAR` and `DIALOG` are now expressible roles. A
name that merely *contains* a role word no longer claims it, and a row whose parent is a `LIST` is a
`LIST_ITEM` instead of an anonymous container.
- Inert containers (non-interactive, no text, no description, not selected or focused) are collapsed
while their children are still emitted, and are counted as `collapsed_container`. They were nodes a
model could learn nothing from that still consumed budget.
- A scrollable's pruned off-screen rows are summarized on the scrollable node itself
(`hiddenChildCount` + up to three redacted previews) instead of only being counted globally, so a
model can decide to scroll rather than assume the list ended. Projection is transparent: an off-screen
recycler wrapper promoted away its label, so descendants of a dropped unlabeled node still count.
- Each node carries a `region` (screen thirds: `top-left`, `center`, `bottom-right`) in place of raw
bounds in model-visible text. Bounds remain on the contract as evidence and as the fallback when the
display size is unknown; no action ever used coordinates.
- `ScreenContextSnapshot.inspectability` distinguishes a projection with content from an empty or
root-only tree and from a tree that exposes no labelled or interactive node at all. `halo_read_screen`
now returns `status=unreadable_surface next=REPORT_TO_USER` with a do-not-retry advisory instead of
presenting a blank screen as an empty one.
- Capture failures are no longer one message. New sealed `ScreenCaptureOutcome` separates
`NoActiveWindow` (transient, retry once), `PackageBlocked` (permanent, with the block reason from
`AccessibilitySafetyPolicy.blockReason`) and `Captured`, and the disabled service reports
`ACCESSIBILITY_DISABLED`. Previously "cannot be read" and "blocked by policy" were the same string.
- Tokens remain derived from the node, its true tree path and its *intrinsic* role only: parent-derived
roles are display information, so a token cannot change because a list gained or lost a wrapper.

Composition is unchanged: node/depth/character budgets, redaction and the per-action consent model.
UNCOMPILED AND UNTESTED AT COMMIT TIME — a build and the physical-phone checklist are still required
before this is called verified; the rule tests added for the new object have never been executed.
