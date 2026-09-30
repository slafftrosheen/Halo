# Current architecture

Status: CURRENT SOURCE MAP. Reviewed 2026-09-21.

## Modules and composition

[settings.gradle.kts](../../settings.gradle.kts) defines app, host-android, runtime/*,
engines/*, sdk/*, drivers/*, providers/*, libraries/* and lab/*.
The old core/domain/application directory tree was not this repository's layout.

[ManualDependencyGraph](../../app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt)
composes InfrastructureGraph, ProviderGraph, HostGraph, EngineGraph and ObserverGraph.
It also creates CognitiveSession with in-memory conversation storage, a context assembler,
ToolRegistry and authorizer. ViewModels are lazy properties of this process graph.

[App HaloRuntime](../../app/src/main/java/com/halo/app/HaloRuntime.kt) initializes/starts
interpretation, reasoning, planning, execution and audio engines, command response,
speech session/bridge and speaker. A mutex serializes lifecycle changes; partial-start
failure runs cleanup. This is source behavior, not proof of every failure scenario.

## Execution paths

- Engine/EventBus/Blackboard is the app's declared production command pipeline.
- CognitiveSession handles provider conversations, streaming, bounded tool rounds and results. Provider streams must be considered failed if disconnected before an explicit [DONE] or finish_reason signal, ensuring incomplete replies aren't committed.
- The app ToolRegistry contains echo and current-time executors. Android capability registration
  is separate and does not automatically expose model tools.
- The older HaloMicrokernel/ProcessManager path remains for compatibility/tooling and is
  explicitly excluded from normal phone startup.

## Portable tool fabric (RUNTIME-017, implemented)

`runtime/cognition/.../tools/` owns the tool/capability substrate. `ToolDefinition`
(provider-sdk) stays the provider-facing function schema; `ToolDescriptor` is Runtime metadata:
semantic `ToolCategory`, keywords, `ToolVisibility`, dependencies, `ToolRequirement`s,
`ToolSecurityPolicy` (`ToolRiskClass`, `ConfirmationPolicy`, `ReplaySafety`) and
`ToolExecutionPolicy` (per-tool timeout clamped to a hard ceiling, `ToolConcurrency`,
`ToolRetryPolicy`, output budget). These types import nothing from Android or any vendor.

- `ToolRegistry` accepts executors plus descriptors, exposes `descriptor(toolName)`, validates
  declared dependencies before schema checks, executes with the descriptor's effective timeout and
  reports structured `ToolFailureKind`s. Unregistered descriptors fall back to a conservative
  SENSITIVE/NEVER_REPLAY profile. The registry is immutable after construction.
- `PolicyToolAuthorizer` classifies risk from descriptor metadata (registry lookup), not from
  tool-name string patterns; the skill layer still provides user approval policy (deny /
  always-allow / ask) and authorization groups.
- `ToolRetryRules` enforce: no transparent automatic retry when confirmation is required, risk is
  beyond reversible, or replay safety is not SAFE; retries only for failure kinds the tool's
  retry policy explicitly declares, bounded to 3 attempts.
- `CapabilityCatalogue`/`CapabilityPublisher` let hosts and drivers advertise semantic
  capabilities (`CapabilityId`s such as `host.application.launch`, `host.web.open`,
  `host.notification.read`, `workspace.read`). Capability availability is independent of tool
  registration; discovery joins the two. `AndroidCapabilityPublisher` is the Android adapter and
  the only place Android intent/permission mechanics appear.
- `ToolDiscoveryService` builds a compact discoverable-tool catalogue and exposes the
  `halo_discover_tools` meta-tool (`ToolDiscoveryExecutor`) that expands requested schemas for the
  current interaction. Discovery never mutates the registry and never widens authorization:
  expanded tools pass the same skill/policy gates. When discovery is recommended,
  `DefaultContextAssembler` exposes only the meta-tool plus the catalogue summary instead of every
  schema.

The phone composition root (`ManualDependencyGraph`, `HaloToolDescriptors`, `DescriptorCatalog`)
declares descriptors for every production tool and wires `capabilityCatalogue`, `toolDiscovery`
and the descriptor-driven authorizer.

## Resource fabric and capability arbitration (RUNTIME-020, IMPLEMENTED / UNVALIDATED)

RUNTIME-020 separates capability resolution ("what can do something") from resource ownership
("who is allowed to use the scarce underlying thing right now").

- **Portable Resource Identities & Claims** (`sdk/runtime-sdk/.../resource/`): `ResourceId`
  (`audio.input`, `camera.sensor`, `compute.inference`, etc.), `ResourceEndpointId` (where the
  resource exists, e.g. `device:abc123`), `ResourceLeaseId`, `ResourceKey`, `ResourceHolder`
  (why Halo owns it), `ResourceClaim` (`SHARED` vs `EXCLUSIVE`), `ResourcePriority`,
  `ResourceWaitPolicy` (`FailFast`, `Wait`, `SuspendRun`), `ResourcePreemptionPolicy` (`NEVER`,
  `COOPERATIVE`), `ResourceYieldHandler`, `ResourceLeaseRequest`, `ResourceConflict`,
  `ResourceAcquireResult`, and `ResourceLeaseManager`.
- **Atomic Resource Lease Manager** (`runtime/kernel/.../resource/DefaultResourceLeaseManager.kt`):
  Thread-safe and coroutine-aware arbitration. Grants all claims in a request atomically or none
  (prevents deadlock); evaluates shared/exclusive collision per `ResourceKey`; executes cooperative
  yield requests outside the arbitration mutex; loops safely during waits without state corruption;
  guarantees idempotent lease release; publishes snapshots and events via `StateFlow`.
- **Capability Affinity & Resolution** (`runtime/cognition/.../device/`):
  `DeviceCapabilityResolver` performs typed matching on `DeviceCapability.kind` (no class
  `simpleName` string matching); respects explicit `CapabilityAffinity` (`ANY`, `ORIGIN`, `HOST`,
  `SPECIFIC_OWNER`); orders candidates deterministically (Origin → Preferred → Permitted Host →
  Permitted Other); enforces `allowFallback`. `CapabilityResourceBinding` and `ResourcePlanner`
  materialize selected devices into `ResourcePlan` claims.
- **Scoped Tool Execution Resources**: `ToolResourceCoordinator` prepares capability/resource plans
  for tool descriptors and acquires leases before execution in `CognitiveSession`. Leases release
  in `finally`. Blocked acquisitions journal `AgentStep.ResourceBlocked` and transition run state to
  `WAITING_FOR_RESOURCE` (distinct from `WAITING_FOR_CAPABILITY`).
- **Subsystem Bridges**:
  - `HaloAudioSessionCoordinator` bridges audio input/output acquisition onto `ResourceLeaseManager`
    with cooperative yield support for barge-in.
  - `NativeResourceCoordinator` maps native compute (`compute.inference`, `compute.asr`,
    `compute.heavy`) onto the generic resource manager while preserving native memory budget/pressure
    governance.
  - `LeasedStillImageSource` and `LeasedWirelessTransferCapability` wrap driver capabilities in
    device-neutral resource leases.
  - Driver-internal arbiters (`HeyCyanResourceArbiter`) remain inside the driver layer for
    physical protocol rules.

## Durable execution and recovery (RUNTIME-018, IMPLEMENTED / UNVALIDATED)

Agent runs are durable executions journaled as append-only `AgentStep` evidence
(`AgentRunJournal.kt`). New journal variants: `RunPrepared` (run kind + recovery policy),
`ProviderTurnStarted`/`ProviderTurnCompleted` (provider-neutral response persisted before tools
run or the answer publishes), `ToolInvocationPrepared` (canonical arguments, SHA-256 arguments
digest, stable idempotency key, persisted descriptor version + replay safety),
`ToolExecutionClaimed` (persisted BEFORE the executor runs; replaces the misleading "dispatched"
claim — it proves ownership, not external dispatch; legacy `ToolDispatched` decodes as a claim),
`ToolOutcomeRecorded` (exact model-facing output persisted BEFORE provider continuation),
`InputRequested`/`InputResolved` (durable approvals/clarifications), `ContinuationPrepared`,
`ResponseCommitted`.

- `ToolInvocation` + `ToolArgumentCanonicalizer` (`ToolInvocation.kt`): Halo-owned invocation
  identity with deterministic canonical JSON (sorted keys, normalized numbers, pure Kotlin) and
  `sha256(run|call|tool|digest)` idempotency keys that never regenerate across process death.
- `CognitiveSession.resume(runId)`: load run → checkpoint → deterministic recovery plan → atomic
  `claimResume` (Room conditional UPDATE, exactly one winner) → continuation through the SAME
  provider/registry/authorizer machinery as `submit`. No parallel orchestrator exists.
- `AgentCheckpointAssembler` derives checkpoints from the journal (no separate checkpoint table).
- `AgentRecoveryPlanner` (pure, deterministic, journal-only) classifies runs into
  `RecoveryPlan`s: retry provider turn, continue after tool result, retry (SAFE/idempotent),
  reconcile, complete-from-recorded-response, awaiting input, missing capability/context,
  uncertain external action, unsupported legacy state. `RecoveryExplanation` exposes an
  inspectable summary for UI.
- `RecoverableToolExecutor.reconcile()` lets tools verify external state; unsupported tools
  answer `Unknown`, forcing conservative recovery.
- Approvals/clarifications are durable `AgentInputRequest`s in the journal with stable request
  ids and an `argumentsDigest` binding; resolutions and resumption flow through the portable
  interaction protocol (RUNTIME-019 section below), and runs can survive process death while
  waiting.
- Conversation commits are idempotent (`ConversationStore.appendIfAbsent` on a stable
  runId/requestId commit id).
- Startup (`HaloApplication`) calls `prepareOutstandingRunsForRecovery()`: it CLASSIFIES runs
  (SUSPENDED/WAITING_*) and only terminalizes genuinely uncertain or legacy runs; it never
  surprise-executes consequential work. Routine runs carry `RunRecoveryPolicy.NO_RESUME` and keep
  their conservative never-resumed semantics.
- Room (`RoomAgentRunRepository`) is only the Android persistence adapter: step schemaVersion 4
  encodes new variants, older versions decode unchanged, unknown newer versions decode as
  conservative legacy evidence. Runtime contracts know nothing about Room or Android.

## Portable interaction surfaces (RUNTIME-019, IMPLEMENTED / UNVALIDATED)

Halo Runtime asks for human interaction; it does not ask Android to show a dialog. The Runtime
owns what is being asked, why, which responses are legal, which execution it belongs to, and
whether the request expired. Hosts own how it looks, where it appears, and how input is collected.

- Contracts (`runtime/cognition/.../interaction/`, zero Android imports):
  `InteractionSurface` (deterministic ids — `input:<requestId>`, `run:<id>:progress`,
  `run:<id>:tool:<invocationId>:result`; schema version; provenance) with semantic kinds
  (CONFIRMATION/CLARIFICATION/CHOICE/TEXT_INPUT/TEMPORAL_INPUT/FILE_SELECTION/PROGRESS/RESULT/
  ERROR — never DIALOG/SHEET), a bounded `InteractionContent` vocabulary, typed
  `InteractionResponse`s, semantic `InteractionAction`s, and presentation policy
  (urgency/modalities incl. optional speech metadata). It is NOT arbitrary generated UI, not a
  component tree, not HTML/Compose serialization, and never an LLM code-execution channel.
- `InteractionSurfaceValidator` enforces hard limits (title 120, message/detail 4 000, choice
  label 160, ≤20 choices, text ≤8 000, ≤6 follow-ups) and kind/content agreement before any
  publication.
- Truth layering: `AgentInputRequest` (journal) is the durable execution state;
  `InteractionSurface` is a renderable projection rebuilt after process death — never two durable
  truths. `InteractionSurfaceCoordinator` derives the projection from run state (input surfaces,
  progress phases PREPARING/THINKING/WAITING/ACTING/TRANSFERRING/FINALIZING, result surfaces
  whose `ActionTruth` comes from the journal, never model prose).
- Resolution: hosts submit typed responses to `DefaultInteractionResponseHandler`, which
  validates them, then journals ONE resolution via the atomic
  `AgentRunStore.resolveInputIfPending` CAS (first valid resolution wins; later answers are
  `NoLongerPending`) and resumes through `RunResumptionCoordinator` → `CognitiveSession.resume`.
  Dismiss only hides a presentation — the durable request stays pending; CANCEL scopes are
  explicit (`INPUT_ONLY` vs `RUN`); progress Stop routes through run cancellation. The UI never
  executes tools. Sensitive text is journaled only as redacted evidence.
- Approval path: tool call → `PolicyToolAuthorizer.authorizeDurable` → durable
  `AgentInputRequest.Approval` (copy from descriptor `ToolPresentation` metadata; no raw
  argument dumps; no secrets) → CONFIRMATION surface → response → resume. A journal-evidenced
  `Approved` resolution for the SAME canonical digest auto-reauthorizes the re-emitted call; a
  changed digest requires a fresh approval. The CompletableDeferred/global-decision/120-second
  approval timeout and the `generatePreview` tool-name switch are gone.
- Clarification path: the interpretation engine journals durable
  `AgentInputRequest.Clarification`s while `DefaultClarificationCoordinator` keeps its temporal
  interpretation logic ("evening" → am/pm expansion). Renderers contain no business logic.
- Android is only the first renderer (`app/.../interaction/`): `AndroidInteractionPresenter` +
  `InteractionSurfaceViewModel` expose deterministic presentation state (input consequential →
  input normal → error → progress → result); `InteractionSurfaceRenderer` branches on
  `InteractionContent` only; file selection opens the real system picker and hands Runtime
  opaque `InteractionFileReference`s; `VoiceInteractionSurfaceAdapter` speaks simple surfaces
  through the EXISTING TTS pipeline with origin-device affinity — no second speech engine. The
  assistant overlay renders the same surface in COMPACT mode; hiding it never resolves the
  durable request. Permission prompts, system-role flows and OS file consent remain native OS
  flows.

HostGraph injects AndroidAudioSource into the phone audio engine. HeyCyan construction is lazy.
An existing HybridAudioSource is not proof it is selected in that production path.

Blocking model/speech discovery is owned by repositories with injected `HaloDispatchers`; settings
ViewModels consume cached flows and launch refreshes. External HeyCyan buffers are analyzed by a
whole-capture Silero estimator when installed, with an explicitly labeled energy fallback. Audio
association is a separate app boundary from the working driver GATT control queue.

## Boundaries and limitations

There are multiple bus/state abstractions, not one SharedFlow for every subsystem.
Providers own native/network adapters; their interfaces are not security sandboxes.
Universal use-case layering, immutable state, privacy interception, persistent recovery
and platform independence are not demonstrated repository-wide properties.

See [UI](../11-ui/UI-ARCHITECTURE.md), [storage](DATABASE.md),
[providers](../08-providers/ARCHITECTURE.md) and [security](../12-security/THREAT_MODEL.md).
Portability beyond Android remains direction, not verified production host support.

## RUNTIME-020.1 integration correction

The post-agent review tightened the implementation boundary between capability, resource and execution:

- Capability catalogue snapshots retain every scoped capability instance; two devices exposing the same semantic capability remain distinct candidates.
- Device capability IDs are semantic (`device.audio.input`, `device.camera.still`, `device.mobility`); scarce resources remain separate (`audio.input`, `camera.sensor`, `mobility.drive`).
- Device resolution uses exact canonical IDs and explicit affinity. Host/workspace capabilities are admitted through `ToolAvailabilityResolver`, not faked as physical devices.
- `ToolExecutionClaimed` is appended only after capability/resource admission and immediately before executor side effects.
- `DefaultResourceLeaseManager` stores the yield callback of the lease owner, invokes it outside the state lock, and releases Runtime ownership synchronously only after the owner has performed platform/driver cleanup.
- A prepared-but-unclaimed invocation never bypasses authorization during recovery; an already-claimed SAFE replay reacquires its required resources first.

These changes are source implementation only and remain unvalidated until the dedicated stabilization pass.

## RUNTIME-021 — workload routing and context fabric

RUNTIME-021 separates four concerns that were previously coupled inside `CognitiveSession`:

1. `AiWorkloadPlanner` describes required inference features and typed context needs.
2. `AiProviderRouter` resolves a provider/model from those requirements using effective
   adapter × model capabilities and the user's routing/privacy policy.
3. `ContextAcquisitionCoordinator` gathers exact interaction/origin data and delegates active
   visual capture to a portable host-provided `VisualContextSource`.
4. `ContextAssembler` budgets and formats those acquired candidates for the pinned target.

A visual request is therefore planned as IMAGE-required before image bytes exist. A text-only model
cannot be selected and then "fixed" by dropping the image. Conversely, optional visual context does
not force a vision model or unnecessary camera capture.

Context identity is explicit. CURRENT_VIEW never falls back to a global latest image. Screen text,
web content, imported documents and captured images remain untrusted content. Remote context sharing
is controlled independently for workspace, screen and images.

The provider/model target is pinned for the cognitive transaction and reused across tool rounds.
Durable journal metadata records requirement identities only; private context bodies/bytes are not
written to the agent journal.

Source implementation is present but remains **UNVALIDATED** pending STABILIZATION.

## Action execution truth

Halo chooses exactly one owner per utterance. Deterministic actions follow interpretation → goal →
plan → capability; complex or unclassified actions follow provider → tool loop. An Android intent
handoff is `DISPATCHED`, while `CONFIRMED` is reserved for state Halo can actually verify.

A tool continuation is not a new user turn. The runtime reconstructs USER → ASSISTANT tool call →
TOOL result plus the recorded exposure set from the AgentRun journal, pins the recorded
provider/model, and invokes the adapter with `TOOL_CONTINUATION`. A recovered continuation uses the
same reconstruction and cannot execute an already recorded tool outcome again.

## Bounded agent execution (RUNTIME-058)

A run is bounded by construction. `AgentRunLimits` supplies per-kind ceilings (interactive, routine,
system) and `AgentRunGuard` enforces them; one `AgentRunGuardRegistry` holds one guard per run so the
provider loop (`CognitiveTurnExecutor`) and the tool loop (`DefaultToolTurnExecutor`) consult the same
counters and the same thermal reading. A guard stop is a *decision*, not an exception: it is journaled
as `AgentStep.RunGuardTriggered` (reason, round, tool id, digest, elapsed, bounded summary) and reaches
the user as recovery copy. Thermal policy is portable (`ThermalStateSource`/`ThermalPolicy` in
runtime-sdk); the Android host maps `PowerManager` into `ThermalSeverity`, and thermal pressure never
silently changes provider — `LOCAL_ONLY` stays `LOCAL_ONLY`. Run-scoped token accounting keeps
estimated and provider-reported tokens as separate facts.

## Capability fabric: MCP, procedures, routines (RUNTIME-058)

Halo has exactly one tool catalogue. MCP servers contribute *dynamic* entries through a bounded,
removable overlay on `ToolRegistry`, namespaced `mcp.<serverId>.<remoteToolName>`; a removed or
disabled server therefore stops being callable on the next lookup. A remote server describes its tool
only — Halo's own `ToolDescriptor` assigns risk, approval policy, replay policy and timeout, and risk
defaults to `SENSITIVE` regardless of trust. Results are bounded untrusted content.

Learned procedures are instruction-only recipes over existing capabilities; they cannot add authority,
and every step they mention still passes the ordinary resolver, policy, approval, resource and journal
path at execution time. Agent-created routines are persisted through the existing routine repository
and scheduler, never through a parallel one.

## Interaction audio and speech (RUNTIME-058)

Audio routing belongs to the interaction, not to one microphone capture. `BargeInEvidence` now carries
sustained-speech duration and an optional semantic class, with physical activation unconditionally
accepted; `BargeInSemanticClassifier` is optional and its absence is reported honestly.
`StreamingSpeechChunker` may only speak `SpeechSourceKind.TERMINAL_RESPONSE`, so tool requests, tool
results, reasoning and approval payloads can never be spoken, and `TtsSanitizer` runs per chunk.

## Structured presentation (RUNTIME-059)

A terminal assistant response may carry `AssistantPresentation(text, structured, provenance)`. Text
stays mandatory: it is the TTS input, the voice-only and text-only fallback, the Notebook fallback and
the only thing a context assembler ever sees — a serialized component tree never enters model
context. The `structured` half is a closed, versioned catalog (`HALO_PRESENTATION_SCHEMA_V1`) with no
generic container and no executable member: it cannot dispatch an Intent, run a tool, grant approval,
change settings, create a routine, send a message or delete memory. A `ChoiceGroup` only resolves an
already durable input request (`PresentationChoiceAuthority`), and an unsupported newer schema version
degrades to text instead of guessing.

Producers are deterministic: `PresentationTemplates` expands a flat model directive, and
`PlanPresentationProjector` projects `ExecutionPlan` / journal evidence so progress can only reflect
verified execution. Persistence rides in existing committed-turn metadata through a bounded codec,
which is why there is no second durable surface store; ephemeral progress lives in
`PresentationSurfaceProjection`, where a stale revision is rejected rather than overwriting newer
state. Rendering is a host concern (`StructuredPresentationRenderer`, FULL/COMPACT), which is what
keeps the same semantic surface portable across Home, Assistant, Notebook, Activity and future hosts.
# MINING-P3-001 — multimodal perception and capability packs (2026-09-30)

MediaAssetRef is portable identity/provenance/lifetime, not a URI or durable payload. Android owns cache and Workspace resolution. Runtime acquires reference context, checks policy and target capabilities, prepares bounded content, then materializes bytes at the provider boundary. Existing workload, role routing, interaction lifecycle, resource leases and response delivery remain authoritative. Explicit references precede acquisition; HeyCyan-origin requests use HeyCyan and fail without phone fallback. Audio-model transcription is a prepared optional first pass returning text, not a second assistant.
