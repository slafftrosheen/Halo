# HALO — MINING-P4-001 long-horizon agent mining

Date: 2026-09-30. Baseline: `8f29bc37341f1ed83463efad9a175f14ae667f4f`.

Source implementation is wired through Halo's existing systems. This is a source review ledger, not runtime acceptance. No build, compilation, tests, lint, emulator, device, benchmarks or inference probe was run. No tests were added.

## Ownership and reused systems

AgentRunStore/Room journal own execution truth. CognitiveTurnExecutor, DefaultCognitiveTurnPreparer and ProviderTurnExecutor remain the provider/tool loop. DefaultAgentRecoveryPlanner owns replay classification. P1 guards/resource leases and normal provider routing remain admission authorities. Existing context planner/assembler, ConversationStore, Working Set, memory retrieval, ToolRegistry exposure/authorization, Notebook/provenance, BrainSnapshot and P2 presentation projector are reused. Host DI uses lazy callbacks to avoid a registry/coordinator/executor cycle.

## Payloads and retention

ContentPayloadStore is portable. FileContentPayloadStore writes SHA-256 bodies under app-private `files/payloads/<digest-prefix>/<digest>` with immutable content, same-directory temporary file, fsync, readback digest and rename before the Room reference transaction. Digest deduplication saves body storage; scoped UUID references separately carry classification, provenance, trust and remote policy. Defaults: 4 MiB/body, 8 MiB/distinct bodies per owner, 64 MiB physical files, 4096 references, 24-hour unreferenced grace. Store reads cap at 8 KiB; the model tool caps at 4 KiB.

ToolRegistry archives successful read-only outputs larger than 4096 characters before the existing result budgeter can truncate them. The compact untrusted result carries an extract/reference/hash/bytes; ToolOutcomeRecorded stores ref/hash/bytes. Large child text is similarly archived before journaling, and ProviderTurnCompleted/ResponseProduced carry the optional reference. Storage failure preserves a bounded extract with an explicit omission, never a fabricated complete result.

Reads require current RunToolScope owner authorization, target-sharing permission, valid range, existing bounded file and matching digest. References can be retained by another owner only from an authorized source owner, within destination quota. Child input/output evidence retains references rather than copying files. Terminal journals retain ownership for recorded-result reuse; collection does not discard terminal results merely because a run ended.

Explicit GC releases references whose owner rows were physically removed, marks the time of last reference removal, and collects bodies only after the full grace interval and a guarded no-reference SQL delete. A new retain clears the clock. Orphan temporary/body files are collected only after grace. Crash between file rename and metadata commit leaves an orphan rather than a dangling successful reference; DB deletion before failed file removal can leave a later-collectible orphan. Portable backup excludes bodies and metadata; restored Android DB metadata without files produces PAYLOAD_MISSING. Saved Notebook truth is independent of those caches.

## Context segments, budget and compaction

ContextSegmentKind covers SYSTEM_STABLE, CURRENT_REQUEST, RECENT_CONVERSATION, WORKING_SET, MEMORY, RETRIEVED_DOCUMENT, TOOL_RESULT and CHILD_AGENT_RESULT. Content can be inline text, payload, workspace or memory reference with trust/revision/remote provenance. This is a provider-context projection, not another persisted conversation store.

The pinned model window is authoritative. Output, framing, schemas and structured auxiliary context are reserved first; explicit request and atomic tool groups are accounted. Every provider invocation, recovery and continuation calls fitProviderContext. Selected Working Set outranks older ordinary chat; bounded memory is assembled before completed history and injected once. Groups containing assistant tool calls and their exact matching result IDs remain atomic. Orphan, duplicate or incomplete tool result groups fail the context boundary; unfinished durable work remains in the journal for tool recovery. Required content that cannot fit returns CONTEXT_BUDGET_EXCEEDED rather than exceeding the window. Estimates are conservative text estimates, not provider-tokenizer measurements; inspector totals describe categories, some already included in system reservation, and must not be summed twice.

ContextCompactor operates on completed durable ConversationTurns only. Above 70% of its allocated completed-history budget it summarizes older turns, keeps four recent complete pairs, includes bounded open questions/commitments/object-memory references and records source turn IDs/time/target metadata. FAST inference honors normal routing/privacy with fallback disabled; a short bounded timeout or unavailable inference uses deterministic excerpts and records COMPACTION_UNAVAILABLE. Summaries are USER reference data, never SYSTEM authority; no hidden reasoning or original-history rewrite. The original current request remains in tool continuation context. Metadata is transient; there is no second durable summary/history truth.

Auxiliary compaction input is capped against the FAST target's own context window before dispatch. Compaction and child requests suppress visible token publication in the shared provider executor; their intermediate text cannot replace the parent's answer stream. Parent synthesis retains normal visible streaming.

## Children, policy and routing

AgentRun persists parentRunId, rootRunId, delegatedRole, delegationDepth, pinnedProviderId, pinnedModelId, pinnedTargetLocal and host-approved delegatedInputRefs. Existing roots default to depth zero; children use deterministic parent/tool-task IDs and depth one. Admission is atomic in Room, with four children per root. Coordinator infrastructure allows two global slots and one local slot; research topics run sequentially by default. A local slot wraps the same executor; existing resource leases remain authoritative for physical engine ownership.

Fixed role map: SUMMARIZE/EXTRACT → FAST; RESEARCH/COMPARE/ANALYZE → REASONING. Normal provider resolution is pinned before work. Child workloads disable hidden fallback, obey LOCAL_ONLY and allow remote only when all explicit references permit it. Model execution location, rather than provider branding, decides locality; severe local thermal state blocks admission.

Children receive at most eight host-validated selected workspace/revision references and/or current-parent-owned payload references. They do not inherit parent history, acquisition, global memory snapshots, raw screen/notification/device state or grants. Tool exposure is bounded to currently available read-only, no-confirmation URL/payload/MCP capabilities; central execution rejects recursion, history search and non-read-only tools even if a model invents a call. Requests for action become NEEDS_PARENT_INPUT with proposed parent capability, not permission.

Central DELEGATED defaults are 45 seconds, four tool rounds, one repeated identical call, two failure streak, 12,000 estimated cumulative prompt tokens and 2,000 output tokens. Parent remaining deadline further bounds a child. Durable provider-start counts prevent restart from resetting the child round ceiling; in-process token/elapsed ledgers are conservative runtime guards rather than separately restored token accounting. Root terminal transition cascades child cancellation; the running child also watches its parent's durable lifecycle and uses structured cancellation.

## Recovery and bounded research

Completed/terminal child IDs reuse recorded DelegationResultRecorded and response evidence without new inference. Unfinished children are suspended at startup and resumed only through parent replay with stored role/target/reference pins. Missing, archived, revised or policy-forbidden references block admission truthfully. Generic root recovery cannot independently commit a child into conversation history.

Existing recovery plans reconstruct provider responses/results. Pending declared tool calls are routed before another provider invocation; the original canonical invocation/idempotency metadata is reused. Already recorded outcomes are returned without execution. Previously claimed work is replayable only when the original digest matches and descriptor risk is read-only with SAFE replay; uncertain side effects remain blocked. Guard failures are never converted into completed child results merely because response text was recorded.

ResearchTaskCoordinator orchestrates up to two bounded RESEARCH objectives, guiding available source discovery, collection, reading and comparison. Parent synthesis is the ordinary provider continuation; there is no separate synthesis runner or phase-state database. Evidence comes from explicit inputs and actual successful URL/MCP journal results, with stable source reference, retrieval time, revision where available, digest/ref and bounded excerpt. Model-mentioned URLs alone do not become factual fetched evidence. Child results/evidence are bounded and durable. Source coverage depends on configured read capabilities and provided URLs; this batch does not add unrestricted web search or autonomous browsing.

Home's explicit Save to Notebook stores final synthesis plus bounded source text and ObjectProvenance (root/child IDs and evidence). Existing Note/brain backup exports that truth independently of payload cache availability. No implicit memory promotion occurs.

## User surfaces and diagnostics

Working Set displays individual/total text estimates, effective cloud eligibility, source and Open/Remove. Selected memory eligibility uses both workspace and cloud-memory permission. Context transparency passively shows the last composition. Home projects existing P2 plan/checklist and child state. Activity nests children under roots with role/target/status/duration/tool/evidence counts. System Check passively reads payload stats, budgets and durable child counts; Collect is explicit GC. Intelligence adds delegation/compaction toggles and reuses FAST/REASONING selection.

Content-free diagnostics cover payload stored/read/GC metadata, context reservations/segments/compaction, child admission/recovery/status/counts/targets and failures. No source body, objective, private prompt or summary is added to DiagnosticsExport. Durable execution journals and deliberately saved user workspace content retain their established content semantics.

## Migration and compatibility

AppDatabase version 19→20: payload_bodies (digest primary key, byte count, creation and nullable unreferenced clock), payload_refs (opaque ID + owner-kind + owner-ID composite key, body FK RESTRICT, owner/body indices), nullable child links/role/pins/reference metadata, depth default zero, parent/root indices. Migration is registered in the existing builder. Optional journal/BrainSnapshot fields preserve old decoding/import compatibility. Generated Room schema export is not produced by hand; existing repository exports are already incomplete, and compiler/migration acceptance is deferred under the no-build/no-test instruction.

## Reference inspection and decisions

Only concepts were harvested; no external code/dependency/asset was copied.

- [Android-Agent ContextCompressor](https://raw.githubusercontent.com/Rickeal-Boss/Android-Agent/refs/heads/harness-improve/core-agent/src/main/java/com/rickeal/agent/core/agent/ContextCompressor.kt): atomic window tool groups inform projection. Its inspected current implementation does not establish a wired summarizing compressor.
- [Android-Agent SegmentedHistoryStore](https://raw.githubusercontent.com/Rickeal-Boss/Android-Agent/refs/heads/harness-improve/core-agent/src/main/java/com/rickeal/agent/core/agent/history/SegmentedHistoryStore.kt): explicitly unwired; rejected as another history truth.
- [Android-Agent ContentAddressedPool](https://raw.githubusercontent.com/Rickeal-Boss/Android-Agent/refs/heads/harness-improve/core-agent/src/main/java/com/rickeal/agent/core/agent/history/ContentAddressedPool.kt): digest/atomic-body concepts adapted behind Halo ownership/quota contracts.
- [Android-Agent AskSubagentTool](https://raw.githubusercontent.com/Rickeal-Boss/Android-Agent/refs/heads/harness-improve/core-agent/src/main/java/com/rickeal/agent/core/agent/subagent/AskSubagentTool.kt) and [BuiltInSubagents](https://raw.githubusercontent.com/Rickeal-Boss/Android-Agent/refs/heads/harness-improve/core-agent/src/main/java/com/rickeal/agent/core/agent/subagent/BuiltInSubagents.kt): parent-scoped bounded children/roles adapted to existing Halo executor.
- [Android-Agent SubagentSessionStore](https://raw.githubusercontent.com/Rickeal-Boss/Android-Agent/refs/heads/harness-improve/core-agent/src/main/java/com/rickeal/agent/core/agent/subagent/SubagentSessionStore.kt): separate storage deliberately rejected.
- [Brownie ConversationManager](https://raw.githubusercontent.com/natanloterio/Brownie/refs/heads/master/core/agent/src/main/java/me/loteiro/brownie/core/agent/ConversationManager.kt): bounded history concepts adopted; summary promotion to SYSTEM deliberately rejected.
- [Brownie SessionSearchIndex](https://raw.githubusercontent.com/natanloterio/Brownie/refs/heads/master/core/memory/src/main/java/me/loteiro/brownie/core/memory/SessionSearchIndex.kt): bounded snippets adapted to existing current-conversation Room turns, without an extra index/session truth.

Deferred/rejected: independent agent runner or planner, recursive/autonomous agents, voting/swarm orchestration, shell/browser automation, unbounded contexts or tool schemas, implicit memory promotion, source privacy override, fabricated citations, mandatory startup model probes and reference code vendoring.

## Basic checks and later acceptance

Basic checks cover whitespace/diff integrity, repository inventory/ancestry, internal imports, named constructor arguments, JSON parsing, source ownership/DI/journal mappings and migration declarations. Constructor scanning has known Compose name-collision false positives and does not substitute for Kotlin/Room compilation. No runtime correctness or performance acceptance is claimed.

Later stabilization: generated Room schema/migration acceptance; small-window and provider tokenizer/protocol behavior; process death at every provider/tool/child/evidence boundary; repeated parent replay and target pins; authorization and removed/revised/remote-forbidden references; I/O/fsync/rename/quota/GC fault injection; root cancellation and physical resource release; local thermal gating; backup/import without caches; Android layout/accessibility. Reference branches are mutable snapshots, and conceptual reference behavior is not proof of Halo runtime behavior.

## Repository reconciliation

One worktree was present. Baseline already reconciled historical branches. A read-only origin fetch found every remote branch tip an ancestor of the reviewed baseline and no remote-only main commits. Existing uncommitted changes were confined to this batch. Final delivery commits this source/doc batch and fast-forwards local main; no remote push is part of this source-only delivery.

## Production files changed

- `app/src/main/java/com/halo/app/backup/BrainSnapshot.kt`
- `app/src/main/java/com/halo/app/data/AppDatabase.kt`
- `app/src/main/java/com/halo/app/data/RoomConversationStore.kt`
- `app/src/main/java/com/halo/app/data/WorkspaceContextStore.kt`
- `app/src/main/java/com/halo/app/data/agent/AgentRunDao.kt`
- `app/src/main/java/com/halo/app/data/agent/AgentRunEntity.kt`
- `app/src/main/java/com/halo/app/data/agent/AgentRunRepository.kt`
- `app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt`
- `app/src/main/java/com/halo/app/diagnostics/DiagnosticsExport.kt`
- `app/src/main/java/com/halo/app/notebook/WorkspaceUseCases.kt`
- `app/src/main/java/com/halo/app/ui/screens/activity/ActivityScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/home/HomeScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/home/HomeViewModel.kt`
- `app/src/main/java/com/halo/app/ui/screens/notebook/NotebookScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/notebook/NotebookViewModel.kt`
- `app/src/main/java/com/halo/app/ui/screens/notebook/WorkingSetSheet.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/IntelligenceScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SystemCheckScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SystemCheckViewModel.kt`
- `app/src/main/java/com/halo/app/ui/shell/AppShell.kt`
- `app/src/main/java/com/halo/app/ui/state/RuntimeUiFormatters.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRun.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunCoordinator.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunGuardRegistry.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunJournal.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunLimits.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunStore.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/CognitiveContextPlanner.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/DefaultContextAssembler.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/LocalModelToolPolicy.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/ToolDescriptor.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/ToolExecutionResult.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/ToolExposurePlanner.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/ToolRegistry.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/AgentRecoveryExecutor.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/CognitiveResponseCommitter.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/CognitiveTurn.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/CognitiveTurnExecutor.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/CognitiveTurnPreparer.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/ProviderTurnExecutor.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/ToolTurnExecutor.kt`
- `sdk/provider-sdk/src/main/java/com/halo/sdk/provider/AI.kt`
- `sdk/provider-sdk/src/main/java/com/halo/sdk/provider/AiWorkload.kt`

## New production files

- `app/src/main/java/com/halo/app/data/RoomConversationHistoryRetriever.kt`
- `app/src/main/java/com/halo/app/data/payload/FileContentPayloadStore.kt`
- `app/src/main/java/com/halo/app/data/payload/PayloadPersistence.kt`
- `app/src/main/java/com/halo/app/di/tools/LongTaskToolExecutor.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/ContentPayloadStore.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/ContextCompactor.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/ContextSegment.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/conversation/ConversationHistoryRetriever.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/delegation/DelegatedTask.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/delegation/DelegatedTaskCoordinator.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/delegation/LongTaskDiagnostics.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/delegation/ResearchTaskCoordinator.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/RunToolScope.kt`

## Documentation updated

- `CURRENT-STATE.md`
- `docs/04-architecture/ARCHITECTURE.md`
- `docs/10-features/FEATURES.md`
- `docs/11-ui/UI-IMPLEMENTATION-STATUS.md`
- `docs/12-security/THREAT_MODEL.md`
- `docs/14-roadmap/ROADMAP.md`
- `docs/engineering/REFERENCE-HARVEST.md`
- `docs/engineering/LONG-HORIZON-AGENT-MINING.md` (new)
