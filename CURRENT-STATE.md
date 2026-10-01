# Halo current state

## SPEECH-QUALITY-002 — Real STT Probe + Speech Lab — 2026-10-01

**IMPLEMENTED / COMPILED / UNIT VERIFIED.** Shared-owner processed PCM diagnostics, real selected-model STT probing, and an in-memory Speech Lab with serial same-sample cold/warm comparisons, optional Whisper fixed/auto, WER/CER, private exports, and provider-neutral execution metrics. All 191 tests pass, including the unfiltered 143-test app suite; :app:assembleDebug and git diff --check pass. The six earlier app-test failures are fixed. Physical-device quality remains unvalidated. See [implementation and hardware acceptance guide](docs/engineering/SPEECH-QUALITY-002.md).

## SPEECH-QUALITY-001 — 2026-10-01

Local STT now has descriptor-driven Tiny/Base/Small multilingual Whisper and experimental Omnilingual 300M. Base is the recommended general-purpose option; downloads remain explicit. Latviešu (`lv`), pinned verified artifacts, persisted model selection, phone/external VAD quality, STT provenance and an isolated in-memory self-test are implemented. Speech/provider/observer and focused app checks pass; :app:assembleDebug succeeds. The six database migration and accessibility ownership test failures recorded for this batch were subsequently repaired in SPEECH-QUALITY-002; the full 143-test app suite now passes. Physical-device quality is unvalidated. See [implementation and hardware test guide](docs/engineering/SPEECH-QUALITY-001.md).

## DOGFOOD-001 — Integration Truth + First-Run Reliability — 2026-09-30

**IMPLEMENTED / COMPILED / UNIT VERIFIED.**
Integration stabilization batch for provider failure diagnostics, direct-chat single retry, LiteRT StrictMode main-thread preflight elimination, speech rejection observability & user-visible feedback, build provenance, and transaction log prefix cleanup.
- **Provider Failure Truth:** `HALO_PROVIDER_FAILURE` telemetry added with normalized `errorType`, `httpStatus`, `providerCode`, `recoverability`, `retryAfterMs`. Provider Diagnostics / System Check displays sanitized last failure summary.
- **Safe Direct-Chat Retry:** Bounded same-target retry (max 2 total attempts) for `DIRECT_RESPONSE` (`ToolUseMode.NONE` + `INITIAL` turn + `RETRY_IMMEDIATELY` + zero partial output). No automatic retries for auth, rate-limit, invalid request, context overflow, or tool turns. Telemetry: `HALO_PROVIDER_ATTEMPT`, `HALO_PROVIDER_RETRY`, `HALO_PROVIDER_SUCCESS`, `HALO_PROVIDER_FAILURE`.
- **LiteRT StrictMode Fix:** `OnDeviceModelsScreen` renders immutable state flow. `StatFs` preflight computed on `Dispatchers.IO` once per state update. Zero main-thread disk reads.
- **LiteRT Telemetry:** `setDeveloperBackend` changed to `HALO_LITERT_BACKEND_OVERRIDE`. `HALO_LITERT_SELECT` reserved for active target selection.
- **Speech Rejection Observability & User Notice:** `SpeechTranscriptGate` exposes repetition metrics (`tokenCount`, `normalizedChars`, `uniqueTokenRatio`, `dominantPhraseRatio`, `repeatedRuns`, `repetitionSource`). Character-ngram detector hardened for short natural human repetitions ("no no no", "yes yes yes", "very very good", "wait wait wait", "thank you thank you"). Rejections rendered as lightweight, non-modal UI notice on Home screen.
- **Build Provenance:** `HALO_BUILD` emitted at startup (`versionName`, `versionCode`, `variant`, `commit`, `dirty`) and exported in diagnostics report.
- **Clean Transaction Logs:** `ConsoleLogger.formatTag` prevents `[HALO-HALO-TXN]` duplicate prefixing.

See [DOGFOOD-001 documentation](docs/engineering/DOGFOOD-001-INTEGRATION-TRUTH.md).

---

## STABILIZATION-PROVIDER-001 - 2026-09-30

**IMPLEMENTED / UNVALIDATED.** Provider-owned descriptors now back the single Provider Center; legacy Intelligence credential/endpoint projections are removed. Intelligence and Voice are bounded control centers. Cached model browsing, conservative DirectResponse/action interpretation, readable recovery, explicit-role fallback gates and sticky speech fallback are source implemented.

See [source map and deferred acceptance](docs/engineering/PROVIDER-CONVERSATION-STABILIZATION.md). Build, tests and device acceptance were not run. Earlier dated sections below are historical.


## P8 provider UX and conversation reset — 2026-09-30

General conversational answering is a core Halo capability and does not depend on tool availability. DirectResponse/CHAT is separate from Agentic actions; configuration, authentication, model discovery and generation proof are separate. Speech uses the selected PCM provider, protected ElevenLabs opt-in, phone-only settings tests and host-owned route policy. See [implementation and deferred acceptance](docs/engineering/PROVIDER-UX-AND-CONVERSATION-RESET.md). Earlier sections retain historical context.


## PRODUCT-P0-001 — source update, 2026-09-30

Provider configuration and model browsing have separate destinations. Intelligence is a short hub, ordinary turns use NONE tools, and ElevenLabs shares portable PCM and verified audio routing. Fresh-install inference/STT/TTS defaults remain local. Actual source baseline: 58c2b2a6cc791d1f1b86aa32cc82fa842e16e150.

See the [control-plane source map](docs/engineering/PROVIDER-CONTROL-PLANE.md) for ownership, official contracts, bounds, security and the later QA plan. Build/tests/device acceptance: not run. Earlier dated descriptions below remain historical.

Status: CURRENT SOURCE MAP. Provider review updated 2026-09-24.
Source review is correlated with the supplied 2026-09-21 device trace. RUNTIME-048 is source-complete but uncompiled and has not been unit- or physical-device-verified.

RUNTIME-048 source addition (2026-09-28): Android assistant enrollment now treats `RoleManager` ownership and the exact active `VOICE_INTERACTION_SERVICE` component as separate truths, exposes OEM Default Assistant settings after an unsuccessful role request, and records bounded enrollment/runtime/recognizer lifecycle diagnostics. `VoiceInteractionSession` and `ACTION_ASSIST` converge through one invocation coordinator. AI selection now has one persisted `(providerId, modelId)` authority; legacy provider model keys are migration input only. Explicit routing stays on the selected provider after failure. System Check reports the selected target without generating on open and offers a separate bounded **Run AI probe** action. LiteRT distinguishes verified-on-disk, selected-cold, resident, and generation-proven states, keys generation proof to artifact fingerprint plus LiteRT-LM 0.17.1, and exposes bounded staged failure diagnostics. SOURCE COMPLETE / COMPILED: NO / UNIT VERIFIED: NO / PHYSICAL DEVICE VERIFIED: NO.

RUNTIME-048 continuation (2026-09-28): legacy provider-specific model keys are now guarded by a one-time migration marker and are excluded from new Brain exports/import authority. Normal cognitive turns and explicit probes both feed content-free last-execution evidence (provider, model, success, backend, latency, failure stage and diagnostics ID). LiteRT acquisition failures now report DOWNLOAD, HASH_VERIFY, INVENTORY or CANCEL alongside engine/conversation/generation/teardown stages. Assistant runtime transitions reject uncorrelated events without an interaction ID and return to idle after correlated speech completion. SOURCE COMPLETE / COMPILED: NO / UNIT VERIFIED: NO / PHYSICAL DEVICE VERIFIED: NO.

RUNTIME-049 / LITERT-MATRIX-003 source addition (2026-09-29): the LiteRT-LM **0.17.1** development model catalog now exposes four real, byte-pinned text artifacts (`qwen3-0.6b-dynamic-int4` control, `qwen3-1.7b-dynamic-int4`, `qwen2.5-1.5b-instruct-q8`, `deepseek-r1-distill-qwen-1.5b-q8`) plus one gated `GATED_IMPORT_ONLY` identity (`gemma3-1b-it-int4`) that is catalogued for import canonicalization but is never offered to the direct downloader and is never mirrored. `LiteRtCatalogModel` gained access/tier/maturity, per-artifact capability flags (unproven support stays false: no artifact claims tools/vision/audio) and a `LiteRtRuntimeProfile` (preferred backend, CPU-fallback permission, known/default context, sampling and max-output defaults, bounded context ladder) resolved as explicit request -> user preference -> catalog profile -> Halo default. A per-model AUTO/GPU/CPU developer override is selectable from the Model Browser and persisted by model id; explicit GPU never silently CPU-fallbacks and explicit CPU never initializes GPU. Runtime hardening: a bounded `4096 -> 2048 -> 1024` context ladder is used only for AUTO memory/resource allocation failures (never for corrupt artifacts, unsupported ops, ABI errors or unknown exceptions), conversation creation and first-token are independently visible failure stages, engine-init/conversation-creation durations are recorded, and internal `<think>` reasoning is separated from user-visible text so it can never reach TTS while raw evidence is retained. Large downloads are resumable (partial metadata beside `.part`, HTTP Range with 206 validation, restart on ignored ranges, whole-file SHA verification, partials preserved across network loss/cancel/restart and deleted on hash mismatch, oversized partial, changed pin or explicit discard) behind a `StatFs` preflight that refuses to start without artifact + overhead + 512 MB reserve. Per-model failure memory is keyed by model + artifact fingerprint + backend so one artifact's GPU failure cannot poison another model or a newly downloaded revision. Development runners added: two-prompt smoke test, serial model matrix (never two resident, one failure does not abort the rest, no TTS), upgraded benchmark telemetry (artifact SHA/bytes, requested/effective backend and context, engine-init and conversation durations, TTFT, labelled *estimated* tokens/sec, peak Java heap), and an optional synthetic-tool capability probe. New events: HALO_LITERT_SELECT / LOAD_START / LOAD_READY / CONVERSATION_READY / FIRST_TOKEN / GENERATION_COMPLETE / GENERATION_FAILED / UNLOAD / SWITCH / RESUME / VERIFY / CONTEXT_DOWNSHIFT / THINKING / TOOL_PROBE. See docs/08-providers/LITERT-DEVELOPMENT-MATRIX.md. Model roles (FAST/REASONING/AGENTIC/VISION) and 3.5-5 GB multimodal artifacts are explicitly NOT in this batch. SOURCE COMPLETE / COMPILED: NO / UNIT VERIFIED: NO / PHYSICAL DEVICE VERIFIED: NO.

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

## RUNTIME-050 / AGENTIC-PHONE-001 — local agentic execution and phone actions

The local text path was physically verified (Qwen answers on a real phone). This batch makes
the *action* path real and stops silent drops. See `docs/testing/AGENTIC-PHONE-ACCEPTANCE.md`
for the evidence ladder and the physical checklist.

- `CognitiveSession` no longer returns after logging "BYPASSING LLM". Every recognized intent
  resolves through an explicit `InterpretationExecutionRoute`: `LocalFact`,
  `DeterministicAction(executorId)`, `Agentic` or `Unsupported`. Intents the deterministic
  goal/plan/execution pipeline already owns are named, not assumed; anything else is submitted
  to the agentic path, and an unactionable but recognized intent now produces a user-visible
  answer instead of disappearing. Traces: `HALO_INTENT_CLASSIFIED`, `HALO_EXECUTION_ROUTE`.
- Escaped-interpolation bugs fixed: `AlarmTools` returned the literal text
  `Clarification required: ${plan.prompt}` and `CommunicationTools` returned the literal
  `Found ${result.name} at ...` and opened `tel:$phoneNumber`. Alarm clarifications now carry
  the resolver's real question, contact lookups interpolate, and the dial intent carries the
  resolved number (`Uri.parse`, not `Uri.fromParts`, which would percent-encode a leading `+`).
- Machine-readable tool results: every result Halo hands a model now begins with
  `tool_result: status=... action_truth=... reason_code=...`. Rejections keep a real category —
  a disabled AccessibilityService reports `PERMISSION_REQUIRED`, not `INVALID_ARGUMENTS`.
- Local tool exposure is bounded. `halo_discover_tools` (now searchable by plain words) and
  `halo_use_skill` (now an actually registered tool) declare `halo_exposure_add=` /
  `halo_skill_activated=`, and the Runtime re-plans exposure before the next provider turn.
  A 0.6B model starts with three schemas, never the whole registry.
- Tool-call capability is split honestly: `nativeToolCallingSupported` (only ever set by an
  observed probe) versus `haloToolLoopSupported`. LiteRT artifacts default to a bounded
  `HALO_PROMPT` protocol — one envelope format, JSON only, exposed names only, bounded calls,
  no prose heuristics — so Qwen 0.6 is agentically useful without claiming a native channel.
- `DefaultToolTurnExecutor` releases its resource lease on success, rejection, executor
  failure, timeout and cancellation (the old comment claiming `ResourceLease` had no release
  was wrong), and enforces loop guards: identical tool+argument digests stop after
  `maxRepeatedIdenticalCalls`, and a tool that was never exposed cannot execute.
- Skills split: `halo.phone.actions` (deterministic Android actions), `halo.phone.screen`
  (accessibility fallback) and `halo.phone` as the router/entry skill.

Verification: `:runtime:cognition:test`, `:providers:litert:testDebugUnitTest`,
`:host-android:test`, `:engines:ai:test`, `:app:testDebugUnitTest` and
`:app:compileDebugKotlin` all pass. No physical-phone run has been performed by this batch.

## RUNTIME-052 — action execution truth and continuation

Deterministic and agentic actions remain separate execution owners. A classified single-step
intent compiles to one deterministic plan; an agentic request executes through the tool loop, and
`HALO_ACTION_ROUTE` identifies the sole owner. `DevicePayload.parameters` now survive generic plan
compilation. Android external handoffs are `DISPATCHED`, not confirmed success or interrupted work.

Tool continuation is an explicit provider turn kind. It reconstructs the original request,
assistant tool call, durable tool outcome, and exposed schemas from the AgentRun journal, stays on
the recorded provider/model, and sends only an internal continuation trigger. Recovered
continuation never replays a completed side effect. Deterministic responses use the same durable
conversation store and idempotent commit boundary as agentic responses. Source complete; build,
unit, and physical-phone verification were intentionally not run in this patching pass.

## RUNTIME-056 — native Android ability expansion

**BUILD VERIFIED / UNIT VERIFIED / PHYSICAL DEVICE NOT RUN.** Mined from the OneBox agent tool
catalogue in the local reference archive (`docs/engineering/REFERENCE-HARVEST.md`). The runtime,
host, provider and journal boundaries are unchanged: new abilities are Android host executors
behind the existing portable `ToolDescriptor` fabric, and no Runtime module imports Android.

New phone abilities (all Android APIs Halo already had permission for; no new dependency):

- **Device insight** — `halo_device_info`, `halo_battery_status`, `halo_network_status`. Observation
  only. A value the platform will not answer is reported as `unknown` with the reason; the
  Wi-Fi network name is withheld unless location access is granted, and metering/validation
  come from the active network's reported capabilities.
- **Clipboard** — `halo_clipboard_read` (SENSITIVE, never replayed) and `halo_clipboard_write`
  (REVERSIBLE). Copied text is framed as `UNTRUSTED_CLIPBOARD_CONTENT`. Android only grants clipboard
  reads to the focused app, which is reported as a distinct, non-retryable outcome rather than
  presented as an empty clipboard.
- **Checksum** — `halo_checksum` over user text or a file **already imported into the workspace**,
  MD5/SHA-1/SHA-256, with an optional expected value. There is deliberately no arbitrary-path mode:
  Halo has no general filesystem capability and this must not become one. A mismatch is a successful
  measurement, not a tool failure.
- **Web reading** — `halo_fetch_url` returns the readable text or the links of one public page.
  Bounded by construction: http/https only, manual redirect following so **every hop is re-validated**,
  refusal of loopback/link-local/private/`.local` targets by literal *and* by resolved address,
  a 512 KB body ceiling, text media types only, and no credentials of any kind. Page text is framed
  as `UNTRUSTED_WEB_CONTENT`.
- **Device controls** — `halo_ringer` (reads the sound mode back and reports `DISPATCHED` when the
  platform did not apply the request) and `halo_brightness` (requires Android's modify-system-settings
  access; reports `WRITE_SETTINGS_REQUIRED` with the exact grant path otherwise).
- **Timers** — `halo_timer` dispatches a countdown to the system clock app and reports `DISPATCHED`;
  Halo does not own the timer and never claims it is counting down.
- **Settings catalogue** — `halo_open_settings` is now device truth: a section is offered to a model
  only when an installed activity answers that intent, package-scoped screens are addressed with their
  package (`app`, `notifications`, `write_settings`), and known-but-absent screens report
  `SETTINGS_SECTION_UNAVAILABLE` instead of opening something else.

Host descriptor dispatch correction (defect found while wiring the new families): every executor kind
used to fall through to a default descriptor, and `ToolRegistry`'s first-wins catalogue therefore
stored whichever kind was registered first for *every* host tool. Android settings, volume, media,
alarms, contacts and the entire accessibility family were catalogued as `NAVIGATION` +
`EXTERNAL_SIDE_EFFECT` + `NEVER_REPLAY`, and descriptors declaring `ConfirmationPolicy.ALWAYS`
(for example `halo_ui_set_text`) lost their mandatory confirmation to a user policy that may be
`ALWAYS_ALLOW`. Each kind now claims an exact definition or returns nothing, and an unclaimed
definition falls back to the registry's conservative descriptor. The same correction is covered by
`HostToolDescriptorDispatchTest`.

Provider selection repair (pre-existing defect the full-suite run exposed): `AiSelectionRepository`
marked the legacy migration complete on every start, including starts with no legacy value present,
so a retired `provider.llamacpp.native` id restored *after* that marker was set (backup restore,
earlier process, copied preferences) could never be repaired and stayed selected forever. A retired
provider id is now repaired on every start; the marker still guards the one-time legacy
provider-specific model-key lookup it was introduced for.

UI mirroring: the new host capabilities (`host.device.inspect`, `host.battery.read`,
`host.network.read`, `host.clipboard.read`, `host.clipboard.write`, `host.web.fetch`,
`host.timer.set`, `host.ringer.control`, `host.screen.brightness`, `host.settings.open`) are
published by the Android capability publisher and labelled in the Skills screen. Every new ability
ships a validated built-in skill manifest (`halo.device.info`, `halo.clipboard`, `halo.web`, plus
`halo.device`, `halo.files`, `halo.reminders`, `halo.phone.actions` updates) with runtime guidance.
`halo.reminders` previously declared three tools that are registered nowhere and now names real
actions. System Check's Phone section no longer prints hardcoded "Available" rows: it lists the
phone abilities this build registers with the availability the Runtime resolver reports, naming the
missing setup step.

Verification actually performed: `:engines:ai:test`, `:runtime:cognition:test` and
`:app:testDebugUnitTest` pass, including 31 descriptor and native-ability tests. A full `test` run
also exercises `:providers:litert`, where `LiteRtModelDownloaderTest` has one timing-sensitive case
that failed once and passed on rerun. No physical-phone run was performed.

## RUNTIME-057 / MINING-P0-001 — LiteRT execution truth + native tool qualification + resource observability + audio route continuity

**SOURCE COMPLETE / BUILD NOT RUN / TESTS NOT RUN / PHONE ACCEPTANCE NOT RUN.** Edited from
`faca1ae45cb1fc3ac8d8f7dee5836244e6deebc6`. Pure production source + UI wiring; no Gradle, no compile,
no test, no device run was performed for this entry.

### Why

Halo had substantial LiteRT machinery but no way to answer "where exactly did local inference fail?"
without reading thousands of log lines. `SystemCheckViewModel` even had two buttons that both called
`providerRouter.execute()` while implying one was lower-level. Native tool support was inferable from
catalog metadata rather than observed on the device artifact. `NativeResourceCoordinator` had begun
separating residency from execution leases but the distinction was not observable. Interaction audio
route affinity was correct in practice but implicit, and `HaloAudioSessionCoordinator.finishInput()`
cleared the interaction's audio state when only the microphone capture ended.

### Corrected semantics (real defects, not refactors)

1. `LiteRtLocalProvider.toolCallProtocolFor()` returned `ToolCallProtocol.NATIVE` whenever a catalog
descriptor declared `supportsTools`. A catalog boolean is a **declaration**; it is not device/runtime
proof, and the method's own contract said so. `NATIVE` is now returned only for a proven
artifact/runtime/schema key; a declared-native but unproven artifact honestly falls back to
`HALO_PROMPT`, which is what the router actually needs.
2. `HaloAudioSessionCoordinator.finishInput()` set the session to `Idle` when capture ended, which is
correct for capture-scoped state but erased the interaction's route binding. Capture end, STT,
cognition, provider load and inference all happen after the microphone lease is released and must keep
the same output target.
3. Residency was being reported with an execution claim. `mark(RESIDENT)` released the
`ResourceLeaseManager` lease but left `executionLeaseHeld` unobservable, so a warm ASR/LLM looked
indistinguishable from an active one in the UI and in exported diagnostics.

### Direct control probe (LEVEL 1)

`LiteRtControlProbeRunner` (`providers:litert`) runs model file → `Engine.initialize()` →
`Conversation` → `Reply exactly HALO_OK` (≤ 8 output tokens, greedy sampler, no tools, no history, no
memory, no system instruction) → bounded completion → teardown. It involves no `AiProviderRouter`,
cognition, memory, tools, agent loop or speech, so "raw LiteRT works" and "Halo provider works" stay
separately falsifiable. `LiteRtControlProbeResult` records per-stage status/duration plus
`outputNonEmpty` / `outputMatchedExpected` only — never generated text.

Engine-config rules were not duplicated: `LiteRtEngineConfigFactory` is now the single owner of backend
mapping, `EngineConfig` construction, content-addressed cache paths, the bounded context ladder, the
ABI preflight and memory-failure classification, consumed by both `LiteRtRuntime` and the probe.
Resource safety: a probe is `BLOCKED` while any heavyweight native resource is ACQUIRING/LOADING/IN_USE,
and `BLOCKED` while a model is merely warm RESIDENT (System Check then offers the existing unload path)
rather than silently loading a second engine. `controlProbePassed` is a separate diagnostic fact and
never promotes generation-proven state or selects anything.

### Native tool qualification (LEVEL 2/3)

`LiteRtToolQualification` (`NOT_APPLICABLE`, `DECLARED`, `PROBE_REQUIRED`, `PROBING`, `NATIVE_PROVEN`,
`PROMPT_MEDIATED`, `FAILED_LAST_PROBE`) is LiteRT implementation detail and is not added to
provider-sdk. Proof is keyed `<sha256>:litertlm-0.17.1:halo-openapi-v1`, so stale proof cannot survive
a runtime or tool-schema change. `LiteRtNativeToolCallValidator` rejects a call with a missing function
name or incomplete arguments and logs `HALO_LITERT_TOOL_CALL_REJECTED` instead of executing it; the
runtime records content-free channel observation (emitted/accepted/rejected, duplicate suppression,
call-id completeness, argument completeness, paired responses, unpaired model calls after trimming).
`LiteRtToolProbeResult` no longer stores raw argument values or the generated response: it stores field
counts, a digest over sorted argument **key names**, and structural booleans. `automaticToolCalling =
false` and `HaloOpenApiTool.execute()` throwing loudly are unchanged, now with an explicit
`SECURITY INVARIANT` comment at the `ConversationConfig` construction site.

### Resource observability (LEVEL 3)

`NativeResourceSnapshot` now carries `executionLeaseHeld`, `executionResourceId`, `executionWaitMs`,
`executionAcquiredAtMs`, `lastTransitionAtMs` and `operationId`, plus a truthful `executing` flag.
`RESIDENT` therefore means "loaded/cached with `executionLeaseHeld = false`" and `IN_USE` means "an
active compute lease is held". `executingKinds()`/`isExecuting()` were added *alongside* `activeKinds()`,
whose residency semantics (used by model-storage cleanup) are deliberately unchanged. Contention was
not "fixed" by raising the 1000 ms wait, sharing compute.heavy, unloading after every request, adding
sleeps or disabling the lease manager.

### Audio route continuity (LEVEL 6)

`InteractionAudioAffinity` / `InteractionAudioRouteState` in runtime-sdk formalise
`interaction route lifetime > capture lifetime > utterance lifetime`. The binding begins when the
interaction's input/origin is established (`ListeningStarted`), survives `InputLease.release()`,
STT, cognition, provider load, inference and TTS preparation, and ends only when the interaction
finishes, fails terminally or the host tears down. For a wearable-origin input the intended output is
the originating wearable with phone fallback disabled; phone-origin interactions keep Android's own
communication-route selection (no forced built-in speaker). The coordinator that owns the audio leases
owns this record, so there is no second router. `SpeechOutputRouteController`'s HeyCyan path and the
`DISALLOW_PHONE` handoff refusal are unchanged: a wearable route failure still reports
`ROUTE_UNAVAILABLE` and never becomes an audible phone fallback.

### System Check (LEVEL 0–4, differential)

New "Local AI execution truth" section with independently reported rows: Artifact
(`VERIFIED`/`UNVERIFIED`/`CORRUPT`), Raw LiteRT control (`NOT RUN`/`PASSED`/`FAILED`/`BLOCKED`),
Halo local provider (`COLD`/`LOADING`/`RESIDENT`/`FAILED`), Generation proof
(`PROVEN GPU|CPU`/`FAILED <stage>`/`NOT RUN`), Tool channel (qualification label), Native resource
(`RELEASED`/`RESIDENT executionLeaseHeld=false`/`IN USE`/`WAITING`), Backend and Context
(requested/effective). Actions are explicit and never chained: **Run control probe**, **Verify**
(provider path), **Probe tools**, **Run router probe**, **Run agentic probe**; a `BLOCKED` control
probe offers the existing unload. Stream K cleanup: `runLocalLoop()` was removed rather than kept,
because it executed the router layer while pretending to be lower-level.

### UI changes by screen

- **System Check** — the ladder above, plus monospace per-stage detail (stage, status, ms, bounded
detail, exception class). No prompts or generated text are ever displayed.
- **Model Browser** — "Direct runtime probe" added as the lowest-level action and labelled distinctly
from "Verify model" and "Probe tool channel"; compact truth indicators Artifact / Selected / Resident
/ Execution / Backend / Generation / Tools; `toolsDeclared=` now marks catalog metadata as a
declaration, and the honest qualification state is shown instead of it.
- **Intelligence** — a concise "On-device runtime" card (model, Resident·backend, native-tool
qualification) and a "Last local failure" card showing stage + time only; deep timings stay in System
Check.
- **Android Assistant** — an "Audio continuity" subsection (Origin, Intended output, Actual endpoint,
Fallback, Route state) fed by the audio session coordinator's affinity flow. Display labels only; no
MAC addresses, and it configures nothing.
- **Diagnostics export** — new `--- LOCAL LITERT ---`, `--- NATIVE RESOURCES ---` and
`--- AUDIO CONTINUITY ---` sections, all content-free (states, counts, digests, wait times, labels).

### Deliberately not adopted from the references

- Android-Agent `harness-improve` `NativeToolBridge`: adopted the *invariants* (schema transport,
  `automaticToolCalling = false`, fail-loud execution, app-side execution ownership, call/result
  history pairing, probe truth, channel-drift visibility, conversation/preface diagnostics). Their
  Agent architecture was not copied; Halo's `AgentStep` journal remains execution truth.
- Voice Agent: adopted "audio mode/routing belongs to the interaction session, not one mic capture".
  Its forced built-in speaker was **not** adopted — Halo preserves wearable/headset affinity.
- Minimal ADK + LiteRT sample (no root LICENSE in the inspected tree): used only as a conceptual
  reference for how small a direct control path can be. No ADK dependency, no
  `MANAGE_EXTERNAL_STORAGE`, no provider-architecture replacement, no LiteRT version change (0.17.1).
- Prompt-echo detection is observation-only in this batch: it emits
  `HALO_LITERT_PROMPT_ECHO generation=<id> matchedChars>=64` (threshold, never the matched text) and
  sets `promptEchoSuspected`. Actual breaker policy is deferred to the P1 bounded-agent batch.

### Open items for the validation pass

Compilation, unit tests and the physical acceptance ladder
(`docs/08-providers/LITERT-DEVELOPMENT-MATRIX.md`) are unrun. Source-level uncertainty: exact
litertlm 0.17.1 `SamplerConfig`/`ConversationConfig` parameter types at the new probe call site;
`LocalModelInventoryState.Ready` lookup used by the non-suspend diagnostics snapshot; and the
`SystemCheckItem.detail` monospace rendering on narrow devices.

## RUNTIME-058 / MINING-P1-001 — bounded agent + memory pull + MCP + voice continuity + procedures

Status: **SOURCE COMPLETE / BUILD NOT RUN / TESTS NOT RUN / DEVICE ACCEPTANCE NOT RUN.**
Baseline: `faca1ae4` (main had not advanced; all newer work preserved). Identifier: **RUNTIME-058**
(057 was MINING-P0-001). Full reference/decision record:
[ANDROID-AGENT-VOICE-MCP-MINING.md](docs/engineering/ANDROID-AGENT-VOICE-MCP-MINING.md).

### Bounded agent execution

- `AgentRunLimits` (interactive 90 s · routine 240 s · system 45 s, per-kind, never one global magic
  number) + `AgentRunGuard` + `AgentRunGuardRegistry`: one guard per run, shared by the provider loop
  and the tool loop so they cannot disagree about the remaining budget.
- Reasons are typed: `DEADLINE_EXCEEDED`, `TOOL_FAILURE_STREAK`, `TOOL_OSCILLATION`,
  `GENERATION_REPETITION`, `TOKEN_BUDGET_EXCEEDED`, `THERMAL_LIMIT`, `TOO_MANY_TOOL_ROUNDS`,
  `REPEATED_TOOL_CALL`. No exception-message substring matching anywhere.
- `ToolOscillationDetector` recognises `A A A A` and bounded rotations up to 4 calls, storing only
  `toolId` + an arguments *digest*. `GenerationRepetitionDetector` only trips on a repeated,
  normalized, identical window (≥ 24 chars, ≥ 3 times), so normal writing style is never a loop.
- `ToolTurnResult.RunStopped` is a deliberate stop, not a failure: the durable
  `AgentStep.RunGuardTriggered` step carries reason/round/tool/digest/elapsed/summary and Activity
  renders "Task stopped", with spoken recovery copy such as "I stopped because I was repeating the
  same actions." Existing `ToolLoopLimits` still applies.
- Thermal: portable `ThermalStateSource` + `ThermalPolicy` in runtime-sdk; `AndroidThermalStateSource`
  maps `PowerManager` status in the host only. MODERATE reduces automatic background preference,
  SEVERE blocks new expensive background work, CRITICAL/EMERGENCY stop local heavyweight generation at
  a safe boundary and release idle native resources. **No** cloud fallback: `LOCAL_ONLY` stays
  `LOCAL_ONLY`, and an explicit local selection returns thermal-unavailable instead.
- `AgentRunTokenAccounting`/`AgentRunTokenLedger` keep `estimatedInputTokens` and
  `reportedInputTokens`/`reportedOutputTokens` separate forever; unreported output is never treated as
  zero, and estimated is never presented as reported.
- `EphemeralToolApprovalGrantStore`: identity = scope + tool id + canonical arguments digest + security
  mode. `set_volume(level=40)` can never approve `level=100`; denials are never cached; and
  `ToolApprovalReusePolicy` refuses reuse for `ConfirmationPolicy.ALWAYS`, `SENSITIVE`, `DESTRUCTIVE`
  and `NEVER_REPLAY`. The durable approval journal is still authoritative.

### Memory pull

- New portable contract `MemoryRetriever`/`MemorySearchResult` with **separate** `lexicalScore`,
  `semanticScore` (null when absent), `recencyScore` and `finalScore`, plus optional
  `MemoryEmbeddingProvider`/`MemorySemanticIndex` and a shared `LexicalMemoryScorer`.
- `RoomMemoryRetriever` queries at SQL level with `LIMIT`ed candidate pools (no schema change) and is
  now the single implementation used by context assembly (`BoundedMemoryContextSource`), by
  `halo_search_memory` and (next) the Memory UI, so relevance cannot mean two different things.
- Semantic search reports `NOT_INSTALLED` (no embeddings are faked from hashes). Existing cloud memory
  policy (`allowCloudMemories`) still governs whether retrieved memory may reach a remote provider.
- `halo_search_memory` now returns bounded ids, snippets, kind and relevance and frames memory as
  background data, never instructions.

### MCP capability fabric

- `McpTransport`/`HttpMcpTransport` (MCP streamable HTTP: JSON-RPC POST plus `text/event-stream`
  framing, `Mcp-Session-Id` and `MCP-Protocol-Version` headers, 20 s call ceiling), `McpClient`
  (`initialize` → `notifications/initialized` → `tools/list` → `tools/call`, bounded pagination, honest
  version negotiation that refuses an unsupported protocol version), `McpIntegrationManager`.
- Namespacing: canonical id `mcp.<serverId>.<remoteToolName>` (sanitized), remote name stored
  separately; never registered globally. `ToolRegistry` gained a bounded, removable **dynamic overlay**
  (`registerDynamic`/`unregisterDynamic`) so a disabled or removed server makes its tools unavailable on
  the next lookup — this is the same registry, not a second one.
- Security: a remote server supplies name/description/schema only. Risk defaults to `SENSITIVE`
  regardless of trust level; trust never implies execution approval. Results are bounded, marked
  `UNTRUSTED_MCP_CONTENT`, and can never be read as new system instructions. Cancellation propagates, so
  a cancelled run leaves no orphan call. Credentials are resolved from the existing SecretStore by
  reference and are never stored in the MCP tables (new Room v19 `mcp_servers`/`mcp_tools`).
- No connection is made at startup or merely because Settings opened.

### Learned procedures and routines

- `LearnedProcedure` (+ `ProcedureStep`, `ProcedureOrigin`, usage/staleness) is deliberately **not** a
  Skill: it is an instruction-only recipe over *existing* capabilities. `halo_propose_procedure`
  rejects unknown capability ids, `halo_save_procedure` is `ConfirmationPolicy.ALWAYS` so Halo's
  existing durable approval surface is what persists it, and `halo_use_procedure` returns steps and
  records usage — each step still passes current policy at execution time, so a procedure can never
  bypass a later permission change. Storage is a small bounded document, not a second database.
- `halo_create_routine` bridges onto the existing `RoutineRepository` + `AndroidRoutineScheduler` +
  `RoutineExecutor.validate`, rejects anything that cannot run unattended *before* saving, and reports
  SAVED / SCHEDULED / BLOCKED / SCHEDULE_FAILED separately.

### Notification reply

- `NotificationReplyRegistry` holds live `Notification.Action` objects in memory only (bounded, TTL'd);
  the listener service populates it on post/connect and drops entries on removal or disconnect, and the
  platform objects are never persisted. `NotificationReplyDispatcher` fills every declared RemoteInput
  (plus `setResultsSource` on API 28+) and returns `DISPATCHED` — never "sent", "delivered" or "read".
- `halo_reply_notification` is `SENSITIVE` + `ConfirmationPolicy.ALWAYS` + `NEVER_REPLAY`, and
  `halo_replyable_notifications` is the read-only listing that makes keys addressable. Only
  RemoteInput-compatible replies are reachable; no arbitrary PendingIntent can be fired, and
  notification text remains data that cannot instruct Halo.

### Voice continuity

- `BargeInEvidence` gained `sustainedSpeechMs`, `semanticInterruptProbability`,
  `semanticClassification` and `sourceDeviceId` (all defaulted, so existing call sites compile).
  `BargeInPolicy.decide(evidence, outputActive, config)` keeps physical activation unconditional, then
  uses an *installed* semantic classifier when the user enabled it, then falls back to the existing
  voiced-speech heuristic with a sustained-speech threshold so a short backchannel need not cut Halo off.
  `BargeInSemanticClassifier` is optional; absence reports "Not installed" and changes nothing else.
- `StreamingSpeechChunker` refuses any non-`TERMINAL_RESPONSE` source, so tool requests, tool results,
  reasoning and approval payloads can never be spoken; `TtsSanitizer` is applied to every chunk after
  it is cut, with sentence/clause/CJK/dash boundaries and `minWords`/`minChars`/`maxBufferedChars`.

### UI

- **Skills** now opens with a "What Halo can do now" summary (agent safety, thermal, memory search and
  semantic availability, learned procedures, MCP servers/tools, barge-in, streaming speech, replyable
  notifications), plus routine-creation and procedure tools appearing through the ordinary descriptor
  catalogue.
- New/changed producible tools are all descriptor-backed (`ROUTINE`, `PROCEDURE`, `NOTIFICATION_REPLY`
  kinds), so System Check's Phone abilities and the exposure planner see the same truth.

### Deliberately deferred (recorded, not dropped)

SKILL.md foreign-frontmatter normalisation (Part H); discovery source metadata in
`halo_discover_tools` results (Part M); voice settings surfaces for interruption mode and streaming
playback (K3/L3); a notifications detail screen showing "Reply supported" (J5 — Halo has no such
screen yet); Activity rendering beyond `RunGuardTriggered` (Part O); enabling streaming speech by
default. See [ANDROID-AGENT-VOICE-MCP-MINING.md](docs/engineering/ANDROID-AGENT-VOICE-MCP-MINING.md).

### Open items for the validation pass

Compilation, unit tests, lint and the physical acceptance list are unrun. Source-level uncertainty:
Kotlin exhaustiveness of the new `AgentStep.RunGuardTriggered` / `ToolTurnResult.RunStopped` variants
across every `when` in the tree; the `ToolTurnResult.RunStopped` branch added to
`CognitiveTurnExecutor` uses the same nullable `durableRun` as its neighbours; `JSONObject(Map)`
construction in the MCP call path; the Room v19 schema export
(`app/schemas/.../19.json`) which Room will generate on the first build; two `when` branches in
`HaloToolDescriptors` that must keep returning `null` for unowned tool names; and the new
`RoomMemoryRetriever` LIKE-escape behaviour against real stored memory text.

## RUNTIME-059 / MINING-P2-001 — adaptive interaction surfaces + plan projection + web hardening

Status: **SOURCE COMPLETE / BUILD NOT RUN / TESTS NOT RUN / DEVICE ACCEPTANCE NOT RUN.**
Baseline: `faca1ae4`. Identifier: **RUNTIME-059** (057 = P0, 058 = P1). Full record:
[ADAPTIVE-INTERACTION-MINING.md](docs/engineering/ADAPTIVE-INTERACTION-MINING.md).

### Structured presentation (extends InteractionSurface; not A2UI)

- `InteractionContent.Presentation` + `InteractionSurfaceKind.PRESENTATION` + a closed, versioned
  `PresentationNode` catalog (Text, Heading, Caption, KeyValue, Badge, Status, Progress, BulletList,
  Checklist, Comparison, ImageReference, Divider, Row, Column, Card, ChoiceGroup) with
  `HALO_PRESENTATION_SCHEMA_V1`. No generic container, no callback, no Intent, no remote URL, no tool
  name is representable.
- `PresentationValidator` enforces depth 6, 120 nodes, 6 000 text characters, 20 list items, 12×5
  comparison bounds, 6 choices and a 24 000-character payload cap, and **rejects with bounded reasons**
  instead of truncating a UI tree. `InteractionSurfaceValidator.validate` takes a
  `ChoiceBindingResolver`, so an unbound or mismatched `ChoiceGroup` fails validation by default.
- `PresentationTemplates` implements KEY_VALUE_SUMMARY, COMPARISON, CHECKLIST, SEARCH_RESULTS, STATUS
  and CHOICE plus deterministic adapters for device status, model status, web results, memory results
  and MCP discovery — a 0.6B model supplies a flat directive, never a nested tree.
- `PlanPresentationProjector` reads `ExecutionPlan`/`Task`/`TaskState` and, for the agentic path,
  derives **only journal-verified** checklist phases (`ToolOutcomeRecorded(success)` → COMPLETED, an
  unmatched `ToolExecutionClaimed` → ACTIVE, an unresolved `InputRequested` → PENDING). A model cannot
  mark a failed action complete, and no second plan truth exists.
- `AssistantPresentation(text, structured, provenance)` is the commit carrier; text stays required (TTS,
  voice-only hosts, Notebook fallback, model context). `PresentationCodec` + `PresentationMetadata`
  persist a validated payload in existing committed-turn metadata (`presentationSchemaVersion`,
  `presentationPayload`, `presentationSummary`) — no second surface database and no extra migration;
  `contextSafeText` documents that serialized UI is never fed back into context.
- `PresentationSurfaceProjection` handles SurfaceCreated/Updated/Removed with monotonic revisions and
  rejects stale updates, bounded to 8 active surfaces.
- `halo_present` is the only model-facing path (no `<a2ui-json>` parsing out of assistant prose): flat
  directive → deterministic expansion → hard validation → ephemeral surface → tiny machine result.
  `READ_ONLY`, `BOOTSTRAP`, `halo.presentation` skill manifest, and zero external authority.
  `PresentationChoiceAuthority` binds a `ChoiceGroup` only to an unresolved durable
  `AgentInputRequest.Clarification` with matching option ids.
- Rendering: `StructuredPresentationRenderer` (FULL/COMPACT) branches on presentation nodes only, with
  Compose semantics for status (content description), progress (`progressBarRangeInfo`), checklist
  (`stateDescription`), choice (`selectableGroup`) and linear comparison order — and words alongside
  color so color is never the only signal. `InteractionSurfaceRenderer` gained the PRESENTATION branch
  and always shows the text fallback when the schema is newer than the renderer.
- `halo_fetch_url` gained `mode=summary` (title, domain, readable chars, link count, extraction mode,
  content type, fetched-at) plus `extraction=STATIC|DYNAMIC` on the existing modes. Untrusted framing
  (`UNTRUSTED_WEB_CONTENT`) is unchanged and page text still never becomes instructions.

### Web reading hardening

`DynamicPageExtractor` (+ `AndroidDynamicPageExtractor`) is opt-in and only consulted when static
readable text is under 200 characters. One isolated ephemeral WebView per extraction: JavaScript on
for rendering, Halo-owned extraction script only, file/content access, DOM storage, cookies, autofill
and password saving disabled, D-Bus-free teardown to `about:blank` then `destroy()`, public-network
re-validation on every navigation and subresource, a ~550 ms MutationObserver quiet window, and a 6 s
hard deadline. `DYNAMIC_CONTENT_REQUIRED` / `DYNAMIC_EXTRACTION_FAILED` are reported as notes rather
than silently pretending the page was read.

### Optional capability packs and audio preparation

`OptionalRuntimeCapability` + `BuiltInOptionalCapabilities` describe semantic memory, smart
interruption, vision and audio understanding — all **Not installed**, because Halo has verified no
artifact metadata for any of them. They render in the Skills capability card (including when nothing
is installed) and explicitly distinguish capability models from chat models. `AudioContext`
(asset-referencing, no raw bytes in durable contracts), `SpeechInterpretationStrategy`
(`DEDICATED_STT` default, `AUDIO_MODEL_TRANSCRIBE_THEN_REASON` defined but not enabled, transcript as
the semantic boundary) and `Pcm16Wav` prepare a future multimodal adapter without touching Sherpa.

### Commit path, hosts and workspace handoff

- `PresentationCommitRegistry` is the only process-local state: a validated presentation is staged by
  `halo_present`, **promoted only when `DefaultToolTurnExecutor` observes that call succeed**, and
  consumed once by `DefaultCognitiveResponseCommitter`, which merges `PresentationMetadata` into the
  committed turn. TTL 10 minutes, 8 entries, both maps bounded — a run that never commits cannot leak
  a card into a later conversation, and no second durable surface store exists.
- Home restores the card from conversation truth (`HomeResponseProjector` decodes the payload into
  `HomeUiState.structuredPresentation`), renders it above the assistant's text, and offers an explicit
  "Save to Notebook" action on eligible cards. Text remains the spoken, persisted and text-only-host
  answer; a newer schema decodes to null and the turn renders as prose.
- Activity attaches a projected plan checklist and a compact `3 / 4 steps complete · Waiting for
  approval` line when the journal evidences at least two execution phases. COMPLETED can only come from
  a recorded success, never from model prose.
- Notebook: a saved structured answer keeps its text fallback as the note body and the bounded payload
  in the object's **existing** provenance metadata; `NotebookStructuredResults` decodes it for a
  read-only card in the note view, and the note stays an ordinary editable document (no schema merge,
  no new table, no migration). Corrupt or future payloads degrade to plain text.
- Working Set (Part P) already existed (`WorkingSetEntry`, `WorkspaceContextSource`, the Working Set
  sheet, `ContextSource.WorkingSet` priority). Nothing parallel was added.
- System Check gained a passive "Interaction presentation" section (renderer, schema version, live
  surface count, structured-response support) and an explicitly tapped preview built from fixed sample
  components (`PresentationPreviewCatalog`). Opening the screen still probes nothing.

### Deferred (recorded, not dropped)

The Intelligence screen's language-vs-capability model split beyond the Skills card, a full
`halo_fetch_url` card binding (the `webResult` adapter exists), and any default rendering of dynamic
extraction. See the mining document.

### Open items for the validation pass

Compilation, tests, lint and the acceptance list are unrun. Source-level uncertainty: Kotlin
exhaustiveness for the new `InteractionContent.Presentation` and
`InteractionSurfaceKind.PRESENTATION` members across renderers and validators; `PresentationCodec`
round-tripping (the `%25` decode ordering and nested child records) after a real build; Compose API
availability for `progressBarRangeInfo`, `selectableGroup` and `LinearProgressIndicator(progress = {})`
at the project's Compose version; `RemoteInput` import parity between the reply registry and the
WebView extractor's `handler.postDelayed` teardown when a coroutine is cancelled mid-load; and whether
`halo_fetch_url`'s `summary` mode schema enum change affects the existing descriptor dispatch tests.

Added during the commit-path pass (same status, unrun): `PresentationCommitRegistry`'s `surface_id=`
parsing against the real `halo_present` result envelope; the committer consuming presentation metadata
for every non-handoff commit path; `ConversationTurn.metadata` round-tripping the payload through
`RoomConversationStore`'s JSON column; `WorkspaceContextDao.provenanceMetadata` returning the same JSON
shape `SaveResponse` writes; and Compose imports for the preview card, Activity checklist and note
read view.
# MINING-P3-001 — multimodal perception and capability packs (2026-09-30)

The MINING-P3-001 source batch adds typed media references, ephemeral capture retention, shared image preparation, origin-aware visual acquisition, model-specific vision qualification and optional capability-pack status. Home image attachments, HeyCyan visual questions and policy-gated Assistant screenshots enter existing cognition/provider routing. No build, tests or device acceptance were run for this batch. Built-in LiteRT catalog entries remain text-only; declaring an imported artifact vision-capable does not prove inference. See [implementation accounting](docs/engineering/MULTIMODAL-PERCEPTION-MINING.md).

## MINING-P4-001 — long-horizon cognition (2026-09-30)

Source implementation extends the existing cognitive executor, context assembler, AgentRun journal and Notebook Working Set. Large read-only tool outputs use bounded app-private payload bodies with Room ownership metadata; child runs reuse the same executor and recovery planner with pinned targets and read-only depth-one policy. Every provider boundary checks estimated context against the pinned model window. Completed conversation history can be compacted through FAST with a deterministic fallback; durable history is preserved.

Home shows existing P2 checklist and child status; Activity nests children; Notebook exposes selected-reference token estimates and cloud eligibility; Intelligence controls delegation/compaction; System Check exposes passive budgets/storage/counts and explicit payload GC. Explicit research saves include bounded factual source evidence in Notebook and portable backup.

Status: source wired; basic source checks only. Build, tests and device acceptance were not run. These additions are not claims of verified Android/Room/provider/device behavior. See [implementation and acceptance ledger](docs/engineering/LONG-HORIZON-AGENT-MINING.md). Earlier historical validation claims apply only to their named batches.

## MINING-P5-001 — semantic phone operator (2026-09-30)

Source now follows OBSERVE → TARGET → PREFLIGHT → AUTHORIZE → DISPATCH → SETTLE → VERIFY.
Exact UI revision checks, typed targets/intents/receipts, checked-state setter, bounded search/wait,
durable transaction projection and conservative recovery reuse the existing host/tools/AgentStep/P1 path.
Home, Activity, semantic approvals, Skills, Assistant, System Check, sandbox and content-free diagnostics
are wired. Actual baseline is `12a31947a732790f1e6f8418c5b5aade831aa321`; database version 20 is preserved.
[Implementation, file inventory and uncertainties](docs/engineering/SEMANTIC-PHONE-OPERATOR.md).
Build, tests and device acceptance NOT RUN. Native phone capabilities remain preferred.

## MINING-P6-001 — proactive automation runtime (2026-09-30)

P6 adds the portable event fabric, baseline-safe battery/charging/network/device/app/notification triggers, coherent conditions, descriptor automation opt-ins and reviewed canonical grants. Production attention is now Room-backed with claimed delivery receipts, typed initiative and centralized disruption budgets. Database advances to v21; legacy definitions are retained but disabled until reviewed save. Only offline notification digest is opted in. Full-screen Automations, passive System Check, preview-only sandbox, pending Home/Activity and diagnostics are wired. Source baseline was cfc63991; no build/tests/device checks were run.

See [Proactive automation runtime](docs/engineering/PROACTIVE-AUTOMATION-RUNTIME.md).

## MINING-P7-001 — personal intelligence and reflection (2026-09-30)

P7 personal intelligence source is implemented: canonical Room memory provenance/confidence/usage/scope, post-commit bounded episodes and auxiliary REFLECTION receipts, Inbox-only inferred proposals, explicit confirmed write/correction/forget tools, safe reviewed procedure/routine suggestions, and Brain schema 2. Learning defaults to Explicit requests only; optional local FAST reflection is off. Semantic encoder and capability artifacts are not installed. Room 21→22 migration is written; generated v22 schema, build, tests and device acceptance are not run.

See [P7 implementation ledger](docs/engineering/PERSONAL-INTELLIGENCE-REFLECTION.md).
