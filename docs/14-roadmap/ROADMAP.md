# Halo Execution Roadmap

## FEATURE-066 / AUTOMATIONS-002 — everyday automations — 2026-10-07

Automations now presents Routines, Follow-ups and History with grouped trigger
pickers, human schedule/condition/action/delivery controls and explicit reviewed
save choices. The bounded `halo_routine_message` tool returns approved plain text;
existing routine delivery owns notification/speech. Message and notification
digest remain the only audited unattended actions. Phone and MCP actions gain
no automation permission. Templates/duplicates are unapproved drafts, Run now
uses the saved grant and manual occurrence, and delete confirms before removing
the routine and cancelling its schedule. Existing occurrence, journal, watch,
source and WorkManager authorities remain unchanged; no Room migration.

See [implementation and validation](../engineering/AUTOMATIONS-002-EVERYDAY-UX.md).
Automated gate: 466 tests (212 cognition + 254 app), zero failures/errors/skips;
debug APK assembled. Compact phone sanity remains pending: ADB had no connected
device. Automated validation is separate from physical delivery acceptance.

## FEATURE-065 / MCP-UI-001 — integrations control plane — 2026-10-07

Settings → Integrations and Skills → MCP counts open the MCP server list and
server detail routes. Add/edit, enable/disable, explicit Connect/Refresh tools,
per-tool toggles, credential replacement/removal and integration removal reuse
the existing manager, Room tables, SecretStore and single ToolRegistry overlay.
New servers start disabled. Opening or observing configuration performs no MCP
network request. Current connection state comes from live manager state, never
a stored last-connect timestamp. Cached tool metadata remains inspectable.

Server configuration is not execution authority; trust is not permission;
connection is not tool success; tool enabled is not automatically approved.
Bearer credentials never enter Room, settings or Brain export. Settings never
invokes remote tools. Descriptions remain untrusted data; risk is not editable.
Explicit discovery cancellation reaches the existing client/transport and
registry synchronization removes disabled, replaced or removed live surfaces.

See [implementation and validation](../engineering/MCP-UI-001-INTEGRATIONS.md).


## FEATURE-064 / INBOX-001 — actionable private notifications — 2026-10-07

Home and Notification Settings open the secondary Notifications workspace and
notification detail routes. Existing listener ingestion, 24-hour Room history,
and per-app policies remain the authorities. Only ALLOW rows are individually
browsable; SUMMARIZE_ONLY contributes to explicit deterministic digests only.
Search and app filters operate on permitted projections. Live registry actions
expire independently of history; disconnected or restored history cannot imply
replyability. Reply and dismiss require explicit confirmations. Reply success
means DISPATCHED (“Reply handed to app · Delivery not confirmed”); dismissal is
only requested until listener reconciliation. There is no inference, cloud
summary, automatic notification memory or automatic tool execution.

See [INBOX-001](../engineering/INBOX-001-ACTIONABLE-NOTIFICATIONS.md) for boundaries, validation and manual acceptance.


## LOCAL-AI-RECOVERY-001 — bounded local thinking policy — 2026-10-07

Qwen3 CHAT, FAST and AGENTIC now explicitly disable thinking through LiteRT-LM
0.17.1 `ThinkingConfig`; exact thinking-capable REASONING targets receive a
64-token budget. Capability metadata does not enable thinking by itself.
Provider-boundary `ToolUseMode.NONE` removes both native tools and Halo prompt
protocol instructions. Diagnostics use the same explicit policy without updating
production health. Role probe suite v2 and performance compatibility epoch 2
retain older history but exclude it from current recommendations/calibration.
The 90-second interactive deadline is unchanged; its local timeout diagnostic
contains execution metadata only. Validation and the single phone acceptance
are recorded in `docs/engineering/LOCAL-AI-RECOVERY-001.md`.

Automated gate: 721 tests, zero failures/errors/skips; debug APK assembled.
Physical acceptance is pending the user's manual one-pass run; no new phone
performance or conversational-quality claim is made.


## RUNTIME-063 — local role qualification — 2026-10-06

Explicit On-device developer actions now qualify installed exact artifacts by
FAST, CHAT, REASONING, AGENTIC and VISION separately. Cold primer is excluded from
three warm FAST samples; deterministic CHAT/reasoning probes and the existing
exact-target tool continuation produce process/session evidence. Physical identity
is verified before/after; backend drift invalidates evidence. Matrix execution is
serial, cancellable, failure-isolated, and cannot preempt active user inference.
Diagnostic traffic bypasses production EWMA, role/CHAT settings remain user-owned,
and the catalog/download/GPU gates remain unchanged. Developer tests are collapsed;
System Check reads recent evidence without running probes. Copy exports bounded
metadata only. No database migration or automatic configuration.

Full serial automated gate passed: 691 tests, zero failures/errors/skips, debug APK assembled.
Both-phone physical qualification remains pending;
no default-model recommendation or following batch selected.
See [qualification contract and acceptance plan](../engineering/LOCAL-MODEL-ROLE-001.md).

## RUNTIME-062 - exact role capability truth - 2026-10-05

Role execution and browsing share the portable exact-model eligibility evaluator.
CHAT remains the conversation default; FAST, REASONING, AGENTIC and VISION have
central requirements, strict explicit assignments and conservative automatic
ranking behind privacy gates. HALO_PROMPT is a valid Halo tool loop and remains
distinct from proof-gated NATIVE. Cached role queries run no inference or discovery.
Pinned continuations retain their target, FAST adaptation follows the resolved
model, and request correlation uses the real interaction identity. Roles and
System Check expose exact capabilities and human assignment labels.
Combined serial gate passed: 666 tests, zero failures/errors/skips, debug APK built.
Physical role/model acceptance remains pending.
See [RUNTIME-062 acceptance record](../engineering/RUNTIME-062-ROLE-MODEL-FABRIC.md).



## RUNTIME-061 - input truth and live local adaptation - 2026-10-05

Input modality is independent of response delivery. Typed Home requests keep CHAT and
text budgets even when Halo speaks; actual speech selects the voice latency policy.
Normal calibration lookup consumes verified inventory and a bounded process cache;
physical SHA verification belongs to inventory, load, and explicit benchmark boundaries.
One process-scoped tracker separates cold/warm TTFT, applies warm EWMA with sample gates
and hysteresis, and keeps live memory pressure as the final conservative authority.
System Check and model Details distinguish CALIBRATION, LIVE, and EFFECTIVE policy;
System Check can copy a content-free local AI diagnostic snapshot.

The shared model browser now includes the existing downloadable LiteRT catalogue on a
fresh install and updates installed/deleted entries from inventory. Uninstalled entries
open setup rather than becoming inference selections. No models or automatic GPU
promotion were added; no EWMA persistence or Room migration was introduced.
Serial automated gate passed: 649 tests, zero failures, debug APK assembled.
Two-phone physical acceptance remains pending.
See [implementation and acceptance record](../engineering/RUNTIME-061-LIVE-LOCAL-ADAPTATION.md).

## RUNTIME-060 — adaptive local execution — 2026-10-05

Explicit benchmarks now persist per artifact/device/runtime variant. Compatible measured
TTFT and live memory pressure reduce local history/output budgets below the 512/128
voice and 1024/256 text baseline; budgets never expand. Simple fresh spoken provider
turns prefer the existing FAST role. Action, vision, explicit reasoning and pinned
continuation ownership remain intact. Details/System Check expose calibration and
policy; generation diagnostics report actual cold/warm engine residency.

GPU qualification is unchanged, benchmarking remains explicit, and no device-name
heuristics or models are added. Serial automated gate passed: 641 tests, zero failures, and debug APK assembled.
Both-phone physical acceptance remains pending. See [implementation and acceptance record](../engineering/RUNTIME-060-ADAPTIVE-LOCAL-EXECUTION.md).

## DOGFOOD-006 — execution truth and interactive local stabilization — 2026-10-04

Implementation covers durable native-action outcomes, semantic host rejection categories,
fresh exact direct-user authority at the host launch boundary, inference-only history
budgets, bounded pressure recovery, reminder clarification, and notification lifecycle
signals with cached trigger indexes. No new providers, models or UI concepts.

Phone evidence confirms safe AUTO-to-CPU resolution, CPU generation stability, GPU
crash containment and Whisper Small warm decode. CPU interactive latency remains
unacceptable (reported TTFT 23,661 ms with 75 history messages). Local regression
repairs passed the DOGFOOD-006.1 gate: 469 tests, zero failures, and a clean candidate APK.
SOURCE VERIFIED / UNIT VERIFIED / CLEAN CHECKOUT VERIFIED; PHONE VERIFIED: NO.
One uninterrupted phone acceptance session remains required.
RC readiness remains blocked pending that execution-truth and physical acceptance gate.
See [DOGFOOD-006 evidence](../engineering/STABILIZATION-2026-10-04.md).

## PHONE-ASSISTANT-004 - Durable Follow-ups + Event Watches + Continuation Runtime — 2026-10-02

**IMPLEMENTED / COMPILED / UNIT VERIFIED / DEPLOYED.**
Halo now supports provider-independent durable event watches, condition notifications, capability recovery, and `WAITING_FOR_EVENT` task continuations. Matches use transactional claims; side-effects are never replayed.

See [DURABLE-FOLLOWUPS](../engineering/PHONE-ASSISTANT-004-DURABLE-FOLLOWUPS.md) and [CONTINUATION-WATCH-RUNTIME](../engineering/CONTINUATION-WATCH-RUNTIME.md).


## STABILIZATION-PROVIDER-001 - 2026-09-30

**IMPLEMENTED / UNVALIDATED.** STABILIZATION-PROVIDER-001 source implementation precedes further major runtime expansion. Next is later build/test/phone acceptance of existing inference providers, DirectResponse and Android/ElevenLabs speech. Additional inference vendors and TTS vendors remain deferred.

See [source map and deferred acceptance](../engineering/PROVIDER-CONVERSATION-STABILIZATION.md). Build, tests and device acceptance were not run. Earlier dated sections below are historical.


## P8 provider UX and conversation reset — 2026-09-30

General conversational answering is a core Halo capability and does not depend on tool availability. DirectResponse/CHAT is separate from Agentic actions; configuration, authentication, model discovery and generation proof are separate. Speech uses the selected PCM provider, protected ElevenLabs opt-in, phone-only settings tests and host-owned route policy. See [implementation and deferred acceptance](../engineering/PROVIDER-UX-AND-CONVERSATION-RESET.md). Earlier sections retain historical context.


## PRODUCT-P0-001 — source update, 2026-09-30

PRODUCT-P0 temporarily takes priority over further mining waves and is implemented in source. Stabilization remains pending: compiler/build checks, existing test expectations where behavior changed, twelve-provider setup/navigation, OpenRouter chat/action/continuation behavior and ElevenLabs/HeyCyan routing/cancellation/privacy acceptance. These checks were deliberately not executed.

See the [control-plane source map](../engineering/PROVIDER-CONTROL-PLANE.md) for ownership, official contracts, bounds, security and the later QA plan. Build/tests/device acceptance: not run. Earlier dated descriptions below remain historical.

Next user-directed product target (2026-09-28): **Notebook editor + Study companion**.
Follow [RFC 0002, NBS-01–07](../rfc/0002-notebook-editor-and-study.md) and its
[reference/source review](../engineering/NOTEBOOK-STUDY-MINING.md). This is a plan-first
priority update; previous runtime work and validation debt remain recorded below.

Status: EXECUTING. Updated 2026-09-25.

See [NEXT-PHASE-2027.md](NEXT-PHASE-2027.md) for proposed batches and detailed acceptance criteria.

The runtime substrate sequence is now explicit. Each batch is a code-construction pass;
stabilization (compile/test/device/HIL) follows after the substrate exists.

1. ~~**RUNTIME-017 — Portable tool fabric + capability discovery**~~ — LANDED (2026-09-25).
   `ToolDescriptor`/security/execution policy, portable categories, descriptor-driven
   authorization, structured failure kinds + retry rules, `CapabilityCatalogue` + Android
   publisher, `ToolDiscoveryService` + `halo_discover_tools` bounded exposure. Present in
   source; not yet test- or hardware-validated.
2. ~~**RUNTIME-018 — Durable execution journal + recovery planner**~~ — LANDED (2026-09-25).
   Durable `ToolInvocation` identity with canonical arguments + digest + stable idempotency
   keys, append-only execution journal (`ProviderTurnStarted/Completed`,
   `ToolInvocationPrepared`, `ToolExecutionClaimed`, `ToolOutcomeRecorded`,
   `InputRequested/Resolved`, `ResponseCommitted`), `AgentCheckpointAssembler`,
   deterministic `AgentRecoveryPlanner` with `RecoveryExplanation`, reconciliation contract,
   durable approval/clarification requests, `CognitiveSession.resume(runId)` on the normal
   execution path, atomic `claimResume`, idempotent conversation commit, startup recovery
   classification (classify, never surprise-execute), routine `NO_RESUME` preservation.
   IMPLEMENTED / UNVALIDATED: not yet test- or hardware-validated.
3. ~~**RUNTIME-019 — Portable Interaction Surfaces**~~ — LANDED (2026-09-25).
   Runtime-owned semantic interaction protocol in `runtime/cognition/.../interaction/`:
   `InteractionSurface`/`InteractionContent`/`InteractionResponse` contracts (no Android types),
   validator with hard content limits, in-memory surface store projected from the durable journal,
   `AgentInputRequest` → surface factory, journal-derived progress/result projection with
   `ActionTruth`, atomic `resolveInputIfPending` response handler (first valid resolution wins),
   `RunResumptionCoordinator` → `CognitiveSession.resume`. Approvals and clarifications migrated
   onto surfaces (durable requests; digest-matched auto-reauthorization; no CompletableDeferred,
   no 120 s timeout); `ToolPresentation` descriptor metadata replaced the preview name switch;
   Android Compose renderer + presenter/ViewModel, SAF file adapter, voice adapter over the
   existing TTS path with origin-device affinity; ephemeral approval sheet removed.
   IMPLEMENTED / UNVALIDATED: not yet test- or hardware-validated.
4. ~~**RUNTIME-020 — Resource leases + capability affinity + multi-device arbitration**~~ — LANDED (2026-09-25).
   `ResourceLeaseManager`, `ResourceId`/`EndpointId`/`ResourceKey` identities, `ResourceClaim`
   (`SHARED`/`EXCLUSIVE`), priority + wait policies, cooperative yield protocol, StateFlow snapshots.
   `DeviceCapabilityResolver` typed matching + explicit `CapabilityAffinity` (ANY, ORIGIN, HOST,
   SPECIFIC_OWNER), `CapabilityResourceBinding` + `ResourcePlanner`. `ToolResourceCoordinator` +
   `CognitiveSession` pre-execution resource acquisition, journaled blockage (`ResourceBlocked`),
   `WAITING_FOR_RESOURCE` state. Audio session + native compute bridged onto generic resource leases;
   `LeasedStillImageSource` + `LeasedWirelessTransferCapability` generic wrappers; `HeyCyanResourceArbiter`
   preserved inside driver. IMPLEMENTED / UNVALIDATED.
5. ~~**RUNTIME-021 — Provider workload/capability routing + multimodal context fabric**~~ — LANDED + REVIEW-CORRECTED (2026-09-25).
   `AiWorkloadSpec` (`AiWorkloadKind`, `AiFeatureRequirement`), provider/model effective-capability intersection, workload-aware `AiProviderRouter.resolveExecutionTarget`, deterministic `AiWorkloadPlanner`, exact-origin `ContextAcquisitionCoordinator`, portable `VisualContextSource`, `ImageContext` provenance/freshness, approximate `ContextBudget`/`ContextTokenEstimator`, persisted cloud workspace/screen/image policy gates, model-level Ollama/OpenRouter capability truth, and target-pinned tool rounds. Required CURRENT_VIEW context never falls back to a random recent image; transient requirements recover as `MissingContext`. IMPLEMENTED / UNVALIDATED.
6. ~~**STABILIZATION-001 — recovery correctness and Android main-thread I/O hardening**~~ — LANDED (2026-09-25).
   Durable `ToolCallResolution` truth derived from journal events without duplicating state. Formalized `ToolRejectionCode` machine reasons mapped into `ToolOutcomeRecorded` failure tracking. Terminal tool rejections properly feed back into the active provider turn without erroneously generating new user interactions. Provider streaming loop enforced to require explicit `[DONE]` / `finish_reason` or valid completion boundaries, treating mid-stream drops as failures rather than completed responses. Added `RecoveryClaimed` journal steps and a unique `RecoveryFingerprint` analog via `checkClaim` to prevent repeated recovery looping on unchanging evidence. Centralized tool exposure resolution during restarts via `ToolExposurePlanner` filtering. Replaced SharedPreferences synchronous main-thread I/O in `NotificationSettingsScreen`, `LocalCaptureStore`, `StorageBackupViewModel`, and `AndroidHaloVaultRepository` with non-blocking `Dispatchers.IO` coroutine workflows. Added structured `[HALO-DIAGNOSTIC]` traces for `TOOL_CALL_REJECTED`, `PROVIDER_TURN_STARTED`, and `AGENT_RECOVERY_STARTED`. IMPLEMENTED / UNVALIDATED.
7. ~~**RUNTIME-022 — Cognitive Turn Kernel / CognitiveSession Decomposition**~~ — LANDED (2026-09-26).
   Extracted monolithic execution logic from `CognitiveSession` into dedicated `turn/` components (`CognitiveTurnRequest`, `PreparedCognitiveTurn`, `ActiveTurnCoordinator`, `ProviderTurnExecutor`, `ToolTurnExecutor`, `CognitiveTurnPreparer`, `CognitiveResponseCommitter`, and `DefaultAgentRecoveryExecutor`). `CognitiveSession` remains a lightweight public façade delegating turn creation and lifecycle management. Zero Android dependencies in runtime cognition turn kernel. IMPLEMENTED / UNVALIDATED.
8. ~~**RUNTIME-023 — Android Assistant + Native Multimodal AI + Agentic Host Bring-Up**~~ — LANDED (2026-09-26).
   Completed Android Assistant role enrollment strategy checking active `VoiceInteractionService` identity alongside `RoleManager`. Supported multi-file GGUF artifacts (base model + `mmproj` vision projector) in catalog and downloader. Enabled native multimodal image prediction and prompt-mediated local tool execution in `NativeLlamaProvider` / `LlamaCppRuntime`. IMPLEMENTED / UNVALIDATED.
9. ~~**RUNTIME-025 / STABILIZATION-002 — Build-to-Phone System Check + Integration Hardening**~~ — LANDED (2026-09-26).
   Enforced strict approval contract (`invocationId + argumentsDigest` identity, single execution, zero execution on denial). Added `ApprovalContractTest` and `ProviderTurnExecutorTest` suites. Added `SystemCheckScreen` (`runtime/system-check`) and `SystemCheckViewModel` providing diagnostic hardware, local llama.cpp text/vision, STT microphone, audio route, and tool approval probes. Created `PHONE-ACCEPTANCE.md` executable test script. IMPLEMENTED / DEVICE VALIDATION PENDING.
10. ~~**RUNTIME-026 — Android Assistant Enrollment + GGUF Artifact Truth + Native llama.cpp Lifecycle Repair**~~ — LANDED (2026-09-26).
   Fixed Android Assistant enrollment dead-tap issue by supporting candidate fallback intents (`ROLE_ASSISTANT` -> `DEFAULT_APPS_SETTINGS` -> `VOICE_INPUT_SETTINGS` -> `GENERAL_SETTINGS`). Overhauled GGUF artifact classification (`GgufArtifactRole`, `GgufArtifactClassifier`) to filter out imatrix/MTP/shards from model selection. Implemented `mmproj` vision projector pairing and staged image input to native `LlamaHelper.predict`. Serialized native llama.cpp lifecycle to prevent double release and destroyed mutex crashes. Fixed startup StrictMode disk read violations on Main. IMPLEMENTED / PHONE REVALIDATION REQUIRED.
11. ~~**RUNTIME-027 / MIGRATION — Native Local AI Migration: llama.cpp → LiteRT-LM**~~ — LANDED (2026-09-26).
   Completely removed llama.cpp/GGUF native runtime (`providers:llamacpp`) and replaced it with embedded LiteRT-LM (`providers:litert`). Split Ollama into standalone `:providers:ollama` module. Implemented `LiteRtRuntime` and `LiteRtLocalProvider` (`provider.litert.local`) with state/inference mutex separation, persistent model-scoped cache, streaming, cancellation via `Conversation.cancelProcess()`, and GPU-to-CPU fallback. Updated catalog, model inventory, and System Check to `.litertlm` models. Retained legacy `.gguf` files on disk with explicit user cleanup action. IMPLEMENTED / PHONE REVALIDATION REQUIRED.
12. **RUNTIME-029 — LiteRT Production Hardening + Local Model Lifecycle** — PARTIAL; stabilization in progress (2026-09-27). Structured history, native tool-call handoff, native image input, request identity, strict streamed tool-call validation, content-addressed cache paths, and model-file integrity checks are wired. Verified downloadable catalog metadata landed in item 14; accelerator/device qualification remains open.
13. **RUNTIME-030 — Agentic Provider + Phone-Control Hardening** — PARTIAL; source integration in progress (2026-09-27). Provider-issued tool-call identities and complete JSON arguments are required before execution; OpenRouter preserves resolved-model identity and Retry-After state; the runtime distinguishes stream idle-before-output from unsafe idle-after-partial-output; skill bodies load in explicit bounded chunks and custom packages support typed dependency declarations; phone actions return classified receipts with a fresh settled snapshot; snapshot-scoped semantic search and content-free accessibility omission counts are available. Device/provider validation, snapshot continuation, and broader operational telemetry remain open.
   Aligned `litertLm` dependency version to 0.17.1. Rewrote `LiteRtRuntime` using 100% public APIs (`Engine.initialize()`, `engine.createConversation()`), eliminating reflection hacks. Enforced `automaticToolCalling = false` on `ConversationConfig`. Hardened AUTO (GPU -> CPU), GPU-only, and CPU-only backend selection. Integrated `NativeResourceCoordinator.acquire(this)` in `LiteRtLocalProvider` before Engine loading. Refreshed catalog with 64-character SHA-256 integrity checks and Qwen3 0.6B Dynamic INT4 (`litert-community/Qwen3-0.6B`). Added SAF `.litertlm` import, sidecar metadata (`<model>.halo-model.json`), and safe model deletion with fingerprint-scoped cache purging. Added `ConversationSegmenter` for atomic tool-chain context trimming. IMPLEMENTED / PHONE REVALIDATION REQUIRED.
14. **LITERT-RECOVERY-001 — Model acquisition + verified local inference path** — SOURCE COMPLETE / UNIT-VERIFIED / **PHONE ACCEPTANCE NOT YET RUN** (2026-09-27). Scope limited to LiteRT model acquisition, inventory, selection, qualification and runtime bring-up.
   Runtime stays at LiteRT-LM **0.17.1**. The built-in catalog now exposes exactly one downloadable artifact: `qwen3-0.6b-dynamic-int4` ("Qwen 3 0.6B Dynamic INT4") from `litert-community/Qwen3-0.6B` pinned to revision `a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76`, `344671744` bytes, SHA-256 `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856`, 4096 context, text-only, no tools, `minLiteRtLmVersion 0.17.1`.
   **Gemma download is withheld**: the real upstream (`litert-community/Gemma3-1B-IT`) is gated by Gemma terms and Halo has neither a Hugging Face authentication flow nor a gated-license acceptance flow, so it is not offered as a zero-config built-in. No mirror is used to bypass licensing or authentication. SAF manual import stays available.
   Acquisition is OkHttp-based (`LiteRtModelDownloader`) with `Accept: application/octet-stream`, `Accept-Encoding: identity`, `Halo-Android/<version>` and redirect following. `Content-Length` is used for progress only and can be absent or wrong; the byte stream is the integrity authority (`actualBytes == expectedBytes` and `actualSha256 == expectedSha256`). Finalization is ordered (stream -> fsync -> byte count -> SHA -> `.json.part` sidecar -> atomic move with `AtomicMoveNotSupportedException` fallback -> inventory refresh -> require exact resolution -> only then `Installed`), with per-model cancellation, single-flight protection, and an explicit `ModelDownloadFailure` taxonomy.
   Inventory no longer reports every non-empty file as valid: `LocalModelValidation` is derived from the physical bytes and SHA (`VERIFIED_ARTIFACT` / `UNVERIFIED` / `CORRUPT` / `INCOMPATIBLE`), a stale sidecar hash can never override the physical fingerprint, pre-existing stale installs are surfaced as corrupt with a **Re-download** action, and byte-identical SAF imports are canonicalized to the pinned id.
   **Truth rules:** installed != verified; selected != resident; resident != generation-proven. Only `VERIFIED_ARTIFACT` may render as ready to select, and `verifyModel()` (load -> conversation -> "Reply with OK." with <= 8 output tokens -> non-empty text) is the only thing that can promote a model to "Verified on GPU/CPU". Arbitrary SAF imports stay `UNVERIFIED` until qualified. Remaining work: physical-phone acceptance checklist (clean install -> download -> verify -> select -> ask "hello" -> restart) and gated-source Gemma support with a real license flow.
15. **LITERT-HARDEN-002 — Late GPU failure recovery + honest lifecycle** — SOURCE COMPLETE / **UNCOMPILED AT COMMIT TIME / PHONE ACCEPTANCE NOT RUN** (2026-09-27). Harvested from an external LiteRT reference implementation recorded in `docs/engineering/REFERENCE-HARVEST.md`.
   A device may pass `Engine.initialize()` on the GPU and only fail at first inference. `LiteRtRuntime.generate()` now rebuilds once on the CPU when the request asked for `AUTO`, the engine is on the GPU, no token was emitted, the turn was not cancelled, and the residency has not already used its rebuild; the rebuild takes `loadMutex` outside `inferenceMutex` (the same order `loadModel` uses) and reports the original GPU cause if it cannot be installed. Engine close runs in `NonCancellable + Dispatchers.Default` because weight release takes seconds. A cancelled turn returns a failure instead of a completed response. New optional SDK capability `LocalRuntimeBackendReporting` keeps `activeBackend` / `fallbackReason` truthful after a late switch. Open follow-ups for the other routes are listed in the harvest ledger.

16. **RUNTIME-044 / RUNTIME-045 — Provider failure truth + built-in skill declaration ownership** — SOURCE COMPLETE / **NO BUILD OR TEST RUN** (2026-09-28). Harvested from the local reference archives in `build/refs` (never committed, never a dependency). `ContextOverflow` is the single owner of the "request exceeded the model context window" judgement: OpenRouter now reports it as `AiErrorType.CONTEXT_TOO_LARGE` with `Recoverability.PERMANENT` instead of a generic malformed request, making the existing presenter/router paths reachable. `DefaultRetryPolicy.nextDelay` caps growth at 30 s and bounds the exponent, removing a negative-delay overflow at `attempt >= 32`. `BuiltInSkillMerge` resolves the duplicate built-in skill declarations (ten of eleven were declared twice) by keeping the strictest risk at skill and per-tool level and unioning tools/permissions, fixing `halo.phone` shipping `EXTERNAL_SIDE_EFFECT` where the registry said `SENSITIVE`; the `halo.phone` manifest was corrected and four unread `*.yaml` duplicates were deleted. Open follow-ups: RUNTIME-044b (structured retry kinds), RUNTIME-045b (skill instruction document channel), RUNTIME-046 (compacted phone observation) and RUNTIME-047 (local stop sequences).

17. **RUNTIME-048 — AI + Assistant execution consolidation** — SOURCE COMPLETE / **NOT COMPILED / UNIT NOT RUN / PHYSICAL DEVICE NOT VERIFIED** (2026-09-28).
   Android role ownership and exact active-interactor identity are independently reported; OEM settings fallback and enrollment diagnostics are explicit. System and OEM `ACTION_ASSIST` invocations converge on one coordinator with service/session/capture lifecycle state and strict interaction-ID correlation. One persisted provider/model tuple owns selection, legacy per-provider keys are one-time migration inputs only, Brain backup preserves only the canonical tuple, explicit routing never substitutes providers, and the selected target powers the bounded System Check AI probe. Normal cognition and probes expose content-free last-execution evidence. LiteRT now distinguishes artifact verification, cold selection, residency, backend, fingerprint/version-scoped generation proof, and acquisition/engine/generation/teardown failure stages. Remaining acceptance is compilation/unit verification plus the physical Samsung assistant, LiteRT, OpenRouter, Ollama, and HeyCyan route checks.

HeyCyan validation remains an essential engineering track, but it is not the entire roadmap. 

18. **RUNTIME-052 — action truth + tool continuation** — SOURCE COMPLETE / **NO BUILD OR TEST RUN / PHONE ACCEPTANCE REQUIRED** (2026-09-29).
   Device parameters survive plan compilation; one utterance has one deterministic or agentic
   owner; external Android handoffs report `DISPATCHED`; deterministic responses commit to durable
   conversation truth. Initial and tool-continuation provider turns are explicit, recovered loops
   restore journaled history/exposure and remain pinned to the original target without replaying
   completed tools.

19. **RUNTIME-057 / MINING-P0-001 — LiteRT execution truth + native tool qualification + resource observability + audio route continuity** — SOURCE COMPLETE / **BUILD NOT RUN / TESTS NOT RUN / PHONE ACCEPTANCE NOT RUN** (2026-09-30).
   A raw-SDK control probe (`LiteRtControlProbeRunner`) now sits below `LiteRtLocalProvider` and below `AiProviderRouter`, so a field failure can be attributed to a layer instead of "LiteRT broken"; engine-config rules are owned once by `LiteRtEngineConfigFactory` and consumed by both the production runtime and the probe. `NATIVE` tool calling is claimed only for a proven
   `<sha256>:litertlm-<version>:<schema>` key: catalog `supportsTools` is treated as a declaration that makes an artifact eligible for probing and falls back to the prompt-mediated loop, and a malformed emitted call is rejected visibly instead of executing. `NativeResourceSnapshot` now separates residency from execution (`executionLeaseHeld`, resource id, wait, transition time), with `executingKinds()` added beside `activeKinds()` so existing residency consumers are unchanged. Interaction audio affinity is explicit in runtime-sdk (`interaction route lifetime > capture lifetime > utterance lifetime`), survives `InputLease.release()`, and a `DISALLOW_PHONE` wearable route still cannot become an audible phone fallback. Stream K removed the misleading `runLocalLoop()`, which executed the router layer. Harvested from Android-Agent `harness-improve`, the Voice Agent audio-session idea, and a minimal ADK/LiteRT sample (conceptual only).

20. **RUNTIME-058 / MINING-P1-001 — bounded agent + memory pull + MCP capability fabric + voice continuity + procedural skills** — SOURCE COMPLETE / **BUILD NOT RUN / TESTS NOT RUN / DEVICE ACCEPTANCE NOT RUN** (2026-09-30).
   `AgentRunLimits` + `AgentRunGuard` (shared through `AgentRunGuardRegistry`) put a typed, non-string-matching
   ceiling on every run — wall clock, tool rounds, repeated identical calls, failure streak, bounded
   oscillation periods, generation repetition and token budget — and journal a content-free
   `AgentStep.RunGuardTriggered` with user-facing recovery copy ("Task stopped · Repeated the same
   action pattern"), never "Runtime failure". Thermal policy is a portable `ThermalStateSource` with an
   Android `PowerManager` host implementation; it refuses new expensive background work and never forces
   a cloud fallback. Run-scoped `AgentRunTokenAccounting` keeps estimated and provider-reported tokens
   strictly separate, and `EphemeralToolApprovalGrantStore` reuses an approval only for the *exact*
   same tool + canonical arguments + security mode (never for `ALWAYS`, `SENSITIVE` or `NEVER_REPLAY`,
   and never for denials). Memory becomes pull-based: a portable `MemoryRetriever` with honest
   lexical/semantic/recency scoring (semantic = null when no embedding provider is installed) is now
   the single implementation shared by context assembly, `halo_search_memory` and the Memory UI. MCP is
   a real capability fabric: streamable-HTTP `McpTransport`, `McpClient` (`initialize` →
   `notifications/initialized` → `tools/list` → `tools/call`, honest version negotiation),
   `McpIntegrationManager` with credential references into the existing SecretStore, namespaced
   `mcp.<server>.<tool>` ids mapped into the *existing* `ToolDescriptor`/`ToolRegistry` via a bounded,
   removable dynamic overlay, `SENSITIVE` default risk (a server never declares itself safe), bounded
   untrusted results and cancellation on run cancel. Learned procedures are instruction-only recipes
   over existing capabilities: the agent may propose, nothing persists without the user's durable
   approval, and every step still passes current per-tool policy. `halo_create_routine` bridges onto
   the existing routine repository/scheduler/validation and reports SAVED / SCHEDULED / BLOCKED /
   SCHEDULE_FAILED separately. `halo_reply_notification` replies only through a live RemoteInput
   action registry and reports `DISPATCHED`, never "delivered". Barge-in gains sustained-speech and
   optional semantic evidence with physical activation still unconditional, and `StreamingSpeechChunker`
   can only ever speak a terminal user response. Harvested from Android-Agent `harness-improve`,
   ChaseDreamInfinity/voice-agent, Brownie and kotlin-adk; see
   [ANDROID-AGENT-VOICE-MCP-MINING.md](../engineering/ANDROID-AGENT-VOICE-MCP-MINING.md).

21. **RUNTIME-059 / MINING-P2-001 — adaptive interaction surfaces + plan visualization + structured results + workspace handoff + web context hardening** — SOURCE COMPLETE / **BUILD NOT RUN / TESTS NOT RUN / DEVICE ACCEPTANCE NOT RUN** (2026-09-30).
   Halo's existing `InteractionSurface` gains a closed, versioned structured-presentation vocabulary
   (`HALO_PRESENTATION_SCHEMA_V1`) with a hard-bounded validator, deterministic templates
   (KEY_VALUE_SUMMARY, COMPARISON, CHECKLIST, SEARCH_RESULTS, STATUS, CHOICE) and result adapters, so a
   0.6B model can present a card with a flat directive instead of authoring a component tree. A2UI was
   **not** adopted as a second surface system: the catalog has no callback, Intent, URL or tool name, a
   `ChoiceGroup` can only resolve an already durable input request through
   `PresentationChoiceAuthority`, and `halo_present` is `READ_ONLY`. `PlanPresentationProjector`
   projects `ExecutionPlan` and, for the agentic path, only journal-verified checklist phases, so no
   second plan truth and no model-claimed progress exist. Committed responses carry
   `AssistantPresentation(text, structured, provenance)` with the payload in existing turn metadata via
   a dependency-free bounded codec (no second surface database, no extra Room migration, and never fed
   back into model context). Rendering is FULL/COMPACT with real Compose semantics and the text
   fallback always available, including for a newer schema. `halo_fetch_url` reports structured read
   facts and gained an opt-in `DynamicPageExtractor` (isolated ephemeral WebView, no cookies, no file
   or content access, public-network re-validation, MutationObserver quiet window, hard deadline) used
   only when static extraction finds almost no readable text. Optional capability packs and portable
   `AudioContext` / `SpeechInterpretationStrategy` / `Pcm16Wav` prepare future ability models without
   downloading an unverified artifact or touching Sherpa. Harvested from kotlin-adk, Android-Agent
   `harness-improve`, Brownie and voice-agent; see
   [ADAPTIVE-INTERACTION-MINING.md](../engineering/ADAPTIVE-INTERACTION-MINING.md).

All future batches must adhere to the product contract and interaction-origin continuity rules established in the [Vision](../01-vision/VISION.md).
Runtime expresses capabilities, resources, context, tools, interactions and execution; hosts and
drivers translate those abstractions into Android/Linux/Windows/wearable-specific behavior.
# MINING-P3-001 — multimodal perception and capability packs (2026-09-30)

MINING-P3-001 source implementation is complete; stabilization remains unrun. Next acceptance should verify pinned LiteRT-LM 0.17.1 vision configuration, imported vision-artifact compatibility, synthetic probe behavior, EXIF handling, resource cleanup/cancellation, HeyCyan transfer/audio affinity, screen callback ordering and role/privacy fallbacks. Optional semantic-memory, smart-barge-in and audio-transcription packs remain unavailable until actual adapters/artifacts/qualification exist. No video, continuous capture, facial identity, background surveillance, automatic visual memory or optional download is scheduled by this source batch.

## MINING-P4-001 source milestone — 2026-09-30

Implemented source: bounded durable payloads, semantic context budget projection, completed-history compaction, depth-one read-only child AgentRuns with pinned routing and parent-owned recovery, bounded research, explicit Notebook evidence saves and passive UI diagnostics. This milestone does not complete runtime stabilization.

Later acceptance must compile generated Room schema and exercise 19→20 migration, small-window provider protocol, interruption at each child/tool/result commit boundary, missing/forbidden/revised references, quota and atomic-write faults, cancellation/resource release, thermal/local serialization, backup/import without body caches, and Android UI layout/accessibility. These checks are deliberately not run under the source-only request.

Deliberately excluded: another agent/session/planner store, autonomous recursive agents, role voting, unrestricted web browsing, shell/browser automation, implicit memory promotion, cloud sharing overrides and mandatory startup inference probes. [Source ledger](../engineering/LONG-HORIZON-AGENT-MINING.md).

## MINING-P5-001 — delivered in source (2026-09-30)

Semantic phone transaction pipeline, revision validation, semantic approval, checked setter,
action-specific verification, bounded search/wait, durable workflow/recovery/takeover, operator UI,
sandbox and diagnostics are source wired. Existing native capabilities remain first choice.
No build, tests or device acceptance run. Next gate is later stabilization/physical acceptance of
accessibility events, Compose rows/hints, approval expiry, launcher/navigation evidence and crash
boundaries, as recorded in [the ledger](../engineering/SEMANTIC-PHONE-OPERATOR.md).
Coordinate/privileged control, permission/CAPTCHA/payment/password automation and surveillance are
not promoted to roadmap implementation by this batch.

## MINING-P6-001 — proactive automation runtime (2026-09-30)

P6 source implementation lands event fabric and durable attention through existing registered tools. Remaining acceptance is Android/Room/device validation, not assumed success. Future audited safe tools may opt in individually. Deferred: location/SSID/calendar/weather polling, unrestricted prompt jobs, automatic replies/payments/account security, routine DAG/cascades, permanent agent service, behavioral inference, general app/event history and offline transition backfill. Generated v21 Room schema awaits an authorized build.

See [Proactive automation runtime](../engineering/PROACTIVE-AUTOMATION-RUNTIME.md).

## MINING-P7-001 — personal intelligence and reflection (2026-09-30)

P7 source is implemented with basic checks only. Next authorized stabilization must generate Room v22 schema, compile generated Room/Moshi adapters and Compose/navigation, validate old/new Brain archives and scope/provenance remapping, confirm native deterministic sampler/preemption/local-only reflection, and accept UI/device privacy behavior. Semantic artifact installation, broader semantic conflict resolution and richer typed correction intent remain deferred; no build/tests/device commands were run here.

See [P7 implementation ledger](../engineering/PERSONAL-INTELLIGENCE-REFLECTION.md).


## PHONE-ASSISTANT-003 — 2026-10-02

Semantic app bridge implemented: Native → AppFunction → DirectAction → Intent → Accessibility → User. Model reasoning never owns platform authority. External capabilities are bounded, current-app and generation-scoped; approval and durable claims remain in the shared runtime. Dispatch and effect truth are separate, and possible dispatch forbids fallback. Composer launch does not mean message delivery. AndroidX alpha12 / API 37 activity integration is host-owned; scalar external schemas are supported, nested/resource schemas remain excluded. Automated verification passed: 639 tests, zero failures/errors/skips; API 37 debug APK assembled. Audit: 31 skills / 86 tools with zero wiring issues. See docs/testing/SEMANTIC-APP-OPERATOR-ACCEPTANCE.md; physical acceptance remains pending.


## LOCAL-MODEL-ROLE-002

SmolLM2 135M/360M are immutable, inspected text candidates, acquired only explicitly. Schema 25 retains exact device/artifact/runtime/config/suite role evidence. Model Roles separates configured, measured and suggested targets; explicit Use revalidates and uses existing selection authorities. On-device shows current-device history, retest and content-free copy. Evaluation-purpose hints never grant capability or choose roles. No default changes, automatic acquisition, GPU qualification or new residency owner. Two-phone comparison is pending. See docs/engineering/LOCAL-MODEL-ROLE-002.md for pins, policy and physical acceptance.

## FEATURE-067 / VOICE-UX-002 — Conversational voice

Voice settings persist spoken interruption (default on), Quick/Natural/Deliberate
timing, early speech (default on), and Concise/Natural/Detailed spoken answer style.
Smart interruption remains Not installed. Explicit microphone/assistant/wearable
activation always interrupts actual playback; ordinary speech requires a valid
active capture and sustained VAD evidence. Acceptance stops old output and cognition
without stopping successor capture. No microphone monitoring service was added.

The existing speech playback owner streams only exact, fresh, tool-free terminal
responses from streaming-capable targets. It sanitizes complete sentence chunks,
pins synthesis selection and route once, holds focus/lease for the response, and
emits one audible start and one terminal event. Short answers use final-response
playback. A digest reconciles the queued prefix against committed final text; a
mismatch stops rather than replaying. Stop/cancellation clears queued chunks and
suppresses late final requests. Route loss stops remaining audio; visual truth
remains committed. Phone fallback is off for external-origin replies unless the
existing setting explicitly permits handoff. Typed requests remain TEXT.

Voice, Skills and System Check expose configuration and process-only speech/route
status without initiating capture, inference or playback. Cloud speech receives
only sanitized answer chunks, never hidden reasoning, tool data, prompts or input
context. Audio and partial tokens remain process-only; Android TTS remains default.

Automated verification: 585 tests, zero failures/errors/skips; debug APK assembled.
The APK installed and launched on the development phone. One synthetic Home RAM
request resolved the configured OpenRouter target and failed with HTTP 429 before
any token/audio; the prepared stream cancelled. No provider/model/fallback setting
was changed. Physical early speech, microphone interruption/backchannel, Stop
during playback and wearable routing remain unverified; no repeated request was run.
See [implementation notes](../engineering/VOICE-UX-002-CONVERSATIONAL-SPEECH.md).
