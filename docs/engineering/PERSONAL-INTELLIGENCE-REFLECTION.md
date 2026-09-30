# Personal intelligence and reflection — MINING-P7-001

Date: 2026-09-30. Actual implementation baseline: `5a1578ed35e496b3a479caf669ca6c91a2ee7792`, locally merged P6 main. The older request reference is not the implementation baseline. Status: source implemented and inspected only. No build, compilation, tests, lint, emulator, APK, adb, device acceptance, model download, benchmark or CI repair was run.

## One persistence truth

`halo.db` and `RoomMemoryRepository` remain the canonical durable personal-memory owner. Runtime contains portable bounded contracts, policies, ranking inputs and temporary interaction notes. It owns no competing memory database, JSON fact store or profile file. The old v5 `memories` table is an import archive: active Working Set remember/correct/forget operations write canonical `memory_records`; new Brain export does not duplicate legacy memory. P1 `LearnedProcedureStore` owns accepted reusable procedures, P6 Room routines own reviewed automation, and P4 conversation compaction remains conversation context rather than personal memory.

`MemoryKind` and `MemoryScope` are canonical Runtime enums, with app aliases for historical call sites. `WorldStateMemory` remains transient. `SkillMemory` is historical skill context, never an alternative procedure execution owner. LearningContext resolves actual Episode/Reflection domain types. Personal profile is the existing memory projection, not another persistence model.

## Post-commit lifecycle and receipts

The application starts one bounded terminal-root observer after automation bootstrap. It reads at most 30 candidate root IDs, checks durable `ResponseCommitted` for normal completed turns, resolves its exact committed conversation turn, and atomically claims `run:<id>` once per policy version (currently 1). Routine runs use their own durable journal evidence. Child/delegated runs do not independently learn personal facts. Greetings, one simple model answer without tool/reference work, and empty interactions produce no episode. Eligible reference tasks, tool work, multi-turn agent work and routines may produce one bounded episode.

`AgentStep.ContextUsed` is a durable metadata receipt for object and memory IDs actually admitted to provider context. The assembler records IDs only after nonblank material fits policy/budget; omitted references are not attributed. Explicit/Working Set memory references also obey conversation/cloud memory policy and current scope, and count usage only when admitted. Legacy WorkspaceContextSource implementations without stable bindings may provide content but no invented IDs. The journal codec and Activity title support the new variant. The receipt carries no bodies, prompts, raw arguments, transcript or hidden reasoning. Usage metadata and provenance-recording errors cannot fail the user's turn.

EpisodeBuilder merges canonical ToolOutcomeRecorded receipts with legacy ToolCompleted mirrors by tool-call ID, preserving ordered distinct requests. A confirmed action counts once; unknown/unconfirmed outcomes never become success. Failure, blocked, partial, cancellation and interruption remain distinct historical outcomes. No failed or unknown episode participates in a successful workflow pattern. Public operational summary is capped at 1,200 characters, with at most 12 tool identities, 20 object IDs and 12 memory IDs. An episode is a historical summary, never a current personal fact.

Reflection is a real portable result with `RuntimeStage.REFLECTION`. The coordinator creates this result and emits content-free `PersonalReflectionCompleted` as an auxiliary EventBus receipt after durable status/proposal writes. It does not append a late root trace StageExecuted or change a completed AgentRun. RuntimeValidator's root response-commit stage ordering remains intact. Startup PENDING reflection claims become SKIPPED; learning never replays user actions.

## Policy and resources

Learning modes: OFF, EXPLICIT_ONLY (conservative default), SUGGEST. Explicit requests are enabled by default; suggestion preferences are present but require SUGGEST. Optional model reflection defaults off. OFF prevents new observer episodes/model suggestions and durable model tools; existing manual Notebook operations remain available. Conversation memory use is the existing independent policy toggle.

Deterministic episodes and repeated-pattern checks come first. Model reflection requires SUGGEST, memory suggestions enabled, opt-in model reflection, a substantial episode with at least three tool identities, no foreground interaction/speech, battery known above 10%, and no thermal limit. Missing metadata is not proof of readiness. It uses local FAST TEXT only, no tool schemas, child agents, remote inference, fallback or user token stream. Resolve is bounded to 5 seconds, inference to 15 seconds, output to 512 tokens/6,000 characters, JSON depth to six, candidates to three, candidate text to 600 characters, evidence to 12 exact supplied references. Only the bounded committed user statement (maximum 1,000 characters), operational episode and optional bounded conflicts enter this request. No whole conversation scan or second extraction pass for every turn.

The shared compute lease has BACKGROUND priority, FailFast waiting and cooperative preemption. A new active foreground root cancels the model child; lease release is in finally. Sampler temperature zero is requested through an explicit provider support contract; LiteRT advertises it and uses topK one. Unsupported providers skip the optional path. Provider/model response identity is checked against the resolved local target. Deterministic sampling still requires later native acceptance and does not guarantee identical hardware results. Auxiliary failure is FAILED/SKIPPED reflection, never failed user-task truth.

## Personal memory writes and proposals

Origin distinguishes USER_EXPLICIT, USER_CORRECTION, MODEL_INFERRED, EPISODE_DERIVED, WORKSPACE_DERIVED and IMPORT. User acceptance is a separate boolean and timestamp. Accepted inference keeps its inference origin/confidence (at most .95); it never turns into confidence 1 merely because it reached Room. Explicit user statement/correction may be 1. Historical records whose origin cannot be recovered migrate to IMPORT, unverified, confidence unknown instead of retaining fabricated certainty. Evidence includes typed opaque run, turn, workspace, episode or approved user-action identities. Manual writes receive a user-action reference when no source object exists.

`halo_propose_memory` can only create a reviewable Inbox candidate. `halo_remember`, `halo_update_memory`, `halo_forget_memory` have ALWAYS confirmation, non-replayable writes and exact IDs for modification/deletion. The root-run executor boundary rejects child durable writes/proposals. Searches expose full exact identifiers; ambiguous conflicts require a user decision rather than choosing a fuzzy first hit. Success wording follows actual repository success. Schemas and repository validations bound content and scope independently.

Project conclusions require PROJECT scope and an actual active project target; they cannot quietly become GLOBAL. Scoped SQL filters apply before LIMIT. Project scope for reflection derives from admitted project memory evidence, not a post-run Working Set guess. Workspace Object and Device scope remain vocabulary; currently explicit memory tools offer global/project/workspace choices, and Device recall requires a selected scope target. CONVERSATION notes are ephemeral and never durable facts. `InteractionWorkingMemory` retains at most 32 owners, 16 notes each, 80-character keys and 512-character bodies, cleared for terminal runs/children and absent from Brain export.

Exact normalized repetition reinforces evidence count/confirmation time without a content revision. Same kind/scope/recognized subject with different content is a conflict; temperature-unit preferences share a subject. Unknown semantic equivalence is not guessed. Acceptance rechecks evidence and conflicts in the transaction, supports one explicitly selected conflict replacement, preserves revision history and updates the same Workspace object. Explicit correction creates USER_CORRECTION provenance and supersedes associated old/new subject proposals; forgetting trashes the Workspace object, detaches Working Set and supersedes matching pending learning. This prevents those stale pending proposals from restoring forgotten data. A future explicit new remember remains possible. No substring correction detector autonomously overwrites facts; typed tool/UI operations are the correction signal in this batch.

## Retrieval and retention

One RoomMemoryRetriever supplies P1 search, context and memory tools. Current ACTIVE, valid records only, current scopes before SQL LIMIT, bounded per-term queries, lexical+recency+confidence ranking with kind intent, relevant scope and evidence reinforcement boosts. Optional embeddings are retained as contracts: no encoder is installed, vectors are never fabricated and semantic availability is NOT_INSTALLED. Kind-specific context uses the same bounded retrieval path; compatibility fallback queries at most 60 scoped candidates rather than materializing the table. At most five context memories are attached subject to context policy and budget. Retrieved text is data, never instructions.

Usage counts update on actual context admission or explicit search/read, never arbitrary scans. Exact memory reads enforce ACTIVE, current scope, validUntil and secret exclusion. Episode search is explicitly historical, bounded to 30 recent eligible rows then five results; exact episode reads preserve historical framing. Episodic retrieval does not feed a current-fact prompt dump. Imported or later missing source links remain opaque historical provenance and do not imply current source availability.

Automatic retention prunes only episodes older than 90 days that are low importance and never referenced. Recent projection is 30, pattern window 30 days/30 episodes. User-kept or used episodes are protected; confirmed memory is never auto-forgotten. Memory validUntil is nullable, excluded from current retrieval after expiry and labeled Possibly outdated in UI. High usage prevents episode deletion rather than silently rewriting current facts.

## Suggestions and attention

PersonalSuggestionEngine recognizes repeated successful safe tool sequences, repeated successful safe manual routines and verified missing-capability evidence. Workflow signatures hash ordered tool identities only, never raw/private argument values. Three recent successes are required; existing enabled P1 procedure sequences are suppressed. Procedure review lets the user set a name/description and inspect known ordered steps before saving through the existing store; no arguments, secrets or side effects are copied. Run-time execution still uses ordinary tool permissions and confirmation.

Automation suggestions open the existing P6 editor with the actual routine, require explicit trigger/definition/grant review, and become accepted only after save. No trigger is inferred, no routine created or enabled automatically. Capability adapter uses P3 registry state plus nonblank fingerprinted artifact references and artifactAvailable evidence, with bounded recent actual rejection/search history. Setup review opens Intelligence settings; no download occurs. Current P3 descriptors contain no verified artifact references, so no install recommendation is emitted. Semantic memory and native multimodal qualification remain unavailable until genuine artifacts/adapters are provided.

One shared Inbox budget: at most 20 pending, three new suggestions per rolling day, 30-day cooldown, stronger evidence required for reconsideration. Pending/accepted duplicates and never-again fingerprints are suppressed. Dismissal state persists. Repeated pending duplicates currently preserve the original bounded evidence set; they do not inflate confidence. Expiry archives unreviewed proposals after 30 days. Learned memories are never automatically promoted. Ordinary suggestions use the P6 PersonalSuggestion source and silent Notebook Inbox delivery. Balanced/Active Home shows at most one passive count; Quiet remains Inbox only. No speech, modal, notification permission request or spontaneous interruption is introduced.

## UI and diagnostics

Notebook Memory shows Confirmed (including explicitly labeled imported/unverified records), Suggestions and Episodes. Cards use the existing Workspace visual system; reviews are inline. Memory detail shows kind/scope, source/origin, confidence category, timestamps, evidence, actual usage, staleness and bounded revision history, with existing edit/forget actions. Episode cards show date/outcome, public summary, unknown completion, available reference actions and Keep in Brain. Home opens Inbox; Activity shows pending count and auxiliary reflection state without blocking Thinking/Acting. Settings includes mode, explicit requests, memory/procedure/automation suggestion toggles, model opt-in and existing conversation memory use. System Check displays Room truth, policy, counts and honest semantic unavailability; its reflection policy preview is local static copy and persists no memory.

Content-free logs: HALO_EPISODE_CREATED, HALO_REFLECTION_DECISION, HALO_MEMORY_PROPOSAL, HALO_MEMORY_CONFLICT, HALO_MEMORY_ACCEPTED, HALO_SUGGESTION_CREATED, HALO_SUGGESTION_DISMISSED. Aggregate in-memory diagnostics expose pending counts by kind, recent episode count and last reflection status/mode; DiagnosticsExport reads these without a main-thread database query. No private text, prompt, hidden reasoning, raw arguments or credential enters these logs.

## Schema and Brain compatibility

Room 21 → 22 migration is registered. memory_records gains origin, evidenceJson, sourceEpisodeId, evidenceCount, lastConfirmedAt, usageCount, lastUsedAt, validUntil, fingerprint, userConfirmed. It clears unknowable historical confidence. Existing IDs/content/status/revision history stay canonical. Added tables in the SAME database: personal_episodes, reflection_states, personal_suggestions (unique fingerprint). New table and column SQL was source-reviewed against entity fields. Generated Room v22 schema is deliberately not produced because build/code generation is prohibited; it requires the later authorized validation pass.

Brain archive schema 2 adds optional memory metadata and three JSONL entries for user-kept episodes, terminal suggestion/dismissal decisions and reflection claim/status metadata. Adapter maps and checksummed export/import boundaries include all three. Pending inferred content/learning Workspace rows, temporary notes, prompts, scratchpads and raw reflection output are excluded. Existing ordinary conversation/AgentRun backup behavior remains its existing owner; P7 introduces no new raw-transcript export. Forgotten memory/tombstones and existing revisions remain in canonical backup. Saved episodes and terminal decisions are bounded to 1,000 each; reflection statuses to 1,000. Old schema-1 archives read missing optional entries as empty and recover conservative origin/confidence defaults; unverified IMPORT confidence is normalized to unknown on restore, and inferred restored confidence stays at most .95. Legacy memories are mapped to canonical mem_<id>, not copied into an active competing table.

Restore preserves IDs and origin/usage/validity/evidence/dismissal metadata, normalizes interrupted PENDING reflection to SKIPPED, idempotently claims saved episodes/statuses and deduplicates terminal decisions by fingerprint. Duplicate-on-import remaps episode Workspace/memory scope references and typed memory Workspace/conversation-turn evidence. A historical source omitted by the archive remains unavailable; no future evidence is invented. Pending candidates are never silently accepted on import. Settings allowlist carries learning preferences/cloud-memory policy; invalid mode is discarded. Existing staged restore/checksum/path safety and reviewed conflict policies remain the boundary.

## Privacy and uncertainties

Remote memory access requires existing cloud-memory permission; optional reflection is always local even when a cloud provider is selected. Secret lexical patterns and long account/card number patterns reject durable writes/proposals and hide legacy secrets from current memory recall. Inferred SENSITIVE, SECRET or UNKNOWN output is rejected; conservative relationship candidates require user acceptance. Declared category plus lexical checks are defense in depth, not a proof that a model cannot misclassify a sensitive fact. No durable classifier model or relationship profile inference is added. Explicit user-approved sensitive facts remain possible; credentials belong in secure configuration, never memory.

Source complete does not mean compiled or device-proven. Room migration/generated adapters, Compose navigation/layout/accessibility, native sampler support, cancellation/preemption, migration/restore conflict behavior and device thermals require later authorized acceptance. No embedding artifact, mandatory extraction model, automatic profile, implicit relationship learning, autonomous procedure/routine creation, guessed temporal automation trigger or online reflection is included. Semantic conflict equivalence beyond conservative normalized subjects, richer explicit correction intent interpretation, a scored synthetic episode preview and broad model artifact installation remain later work. Capability setup emits nothing without verified P3 artifact evidence.

## Inspected reference concepts

Primary sources were read for design comparison only; no foreign implementation was copied:

- [Voice-agent MemoryStore](https://github.com/ChaseDreamInfinity/voice-agent/blob/main/android/app/src/main/java/io/github/chasedreaminfinity/voiceagent/memory/MemoryStore.kt), [EpisodeSummarizer](https://github.com/ChaseDreamInfinity/voice-agent/blob/main/android/app/src/main/java/io/github/chasedreaminfinity/voiceagent/memory/EpisodeSummarizer.kt), [EmbeddingIndex](https://github.com/ChaseDreamInfinity/voice-agent/blob/main/android/app/src/main/java/io/github/chasedreaminfinity/voiceagent/memory/embeddings/EmbeddingIndex.kt): fact/episode/session separation and optional semantic indexing. Rejected mandatory embeddings, automatic extraction per trivial turn, source-owned storage and fabricated availability.
- [Android-Agent MemorySearch](https://github.com/Rickeal-Boss/Android-Agent/blob/harness-improve/core-agent/src/main/java/com/rickeal/agent/core/agent/memory/MemorySearch.kt), [MemoryTools](https://github.com/Rickeal-Boss/Android-Agent/blob/harness-improve/core-agent/src/main/java/com/rickeal/agent/core/agent/memory/MemoryTools.kt), [AgentMemory](https://github.com/Rickeal-Boss/Android-Agent/blob/harness-improve/core-agent/src/main/java/com/rickeal/agent/core/agent/memory/AgentMemory.kt): bounded pull, explicit writes/corrections and real side effects. Rejected full memory prompt dump and alternate MemoryRepository ownership.
- [Brownie SkillStore](https://github.com/natanloterio/Brownie/blob/master/core/memory/src/main/java/me/loteiro/brownie/core/memory/SkillStore.kt): origin/usage/staleness signals. Rejected foreign file-backed procedure truth and autonomous behavior creation; Halo P1 store remains the owner.

## Basic checks and later acceptance

Basic checks are source import/constructor-argument scanning, static call-site/journal/adapter/SQL-field review, UTF-8/file-integrity checks, JSON/XML parsing and git diff whitespace checks. These are not compilation, tests or executed migration simulations. Source scan identifies generated BuildConfig as expected; Compose Row/Column/Card named-argument noise is a scanner limitation. Later acceptance must generate v22 Room schema, compile modules/generated adapters, exercise upgrade/restore/rollback and old archive defaults, review malformed proposal/evidence bounds and scope isolation, prove no autonomous promotion/grant/download, verify cancellation and local-only routing on native devices, and visually accept Notebook/Home/Activity/Settings in light/dark/large font. Those commands were not run in this batch.

## Production inventory

### Changed production files

- `app/src/main/java/com/halo/app/HaloApplication.kt`
- `app/src/main/java/com/halo/app/attention/AndroidAttentionHost.kt`
- `app/src/main/java/com/halo/app/backup/BrainArchiveModels.kt`
- `app/src/main/java/com/halo/app/backup/BrainImportRepository.kt`
- `app/src/main/java/com/halo/app/backup/BrainSnapshot.kt`
- `app/src/main/java/com/halo/app/backup/ZipBrainExporter.kt`
- `app/src/main/java/com/halo/app/data/AppDatabase.kt`
- `app/src/main/java/com/halo/app/data/RoomConversationStore.kt`
- `app/src/main/java/com/halo/app/data/WorkspaceContextStore.kt`
- `app/src/main/java/com/halo/app/data/agent/AgentRunDao.kt`
- `app/src/main/java/com/halo/app/data/agent/AgentRunRepository.kt`
- `app/src/main/java/com/halo/app/data/attention/RoomAttentionQueue.kt`
- `app/src/main/java/com/halo/app/data/memory/MemoryContextSource.kt`
- `app/src/main/java/com/halo/app/data/memory/MemoryRecordDao.kt`
- `app/src/main/java/com/halo/app/data/memory/MemoryRecordEntity.kt`
- `app/src/main/java/com/halo/app/data/memory/MemoryRepository.kt`
- `app/src/main/java/com/halo/app/data/memory/RoomMemoryRetriever.kt`
- `app/src/main/java/com/halo/app/data/routine/RoutineDao.kt`
- `app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt`
- `app/src/main/java/com/halo/app/di/tools/HaloToolDescriptors.kt`
- `app/src/main/java/com/halo/app/di/tools/MemoryToolExecutor.kt`
- `app/src/main/java/com/halo/app/diagnostics/DiagnosticsExport.kt`
- `app/src/main/java/com/halo/app/ui/screens/activity/ActivityScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/home/HomeScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/notebook/NotebookScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/notebook/NotebookViewModel.kt`
- `app/src/main/java/com/halo/app/ui/screens/notebook/WorkspaceObjectDetailScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/AutomationScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SettingsViewModel.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SystemCheckScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SystemCheckViewModel.kt`
- `app/src/main/java/com/halo/app/ui/shell/AppShell.kt`
- `app/src/main/java/com/halo/app/ui/state/RuntimeUiFormatters.kt`
- `providers/litert/src/main/java/com/halo/providers/litert/LiteRtLocalProvider.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunJournal.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/attention/AttentionDomain.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/attention/AttentionPolicyEngine.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/DefaultContextAssembler.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/MemoryContextSource.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/WorkspaceContextSource.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/memory/Memory.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/memory/MemoryRetrieval.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/skills/SkillInstructionSource.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/skills/SkillRegistry.kt`
- `sdk/provider-sdk/src/main/java/com/halo/sdk/provider/AI.kt`
- `sdk/provider-sdk/src/main/java/com/halo/sdk/provider/AiInferenceProvider.kt`

### New production files

- `app/src/main/java/com/halo/app/data/memory/PersonalLearningRepository.kt`
- `app/src/main/java/com/halo/app/data/memory/PersonalMemoryCodec.kt`
- `app/src/main/java/com/halo/app/data/memory/PersonalMemoryPersistence.kt`
- `app/src/main/java/com/halo/app/learning/LocalReflectionModel.kt`
- `app/src/main/java/com/halo/app/learning/ReflectionCoordinator.kt`
- `app/src/main/java/com/halo/app/ui/screens/notebook/PersonalMemorySection.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/LearningSettingsScreen.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/memory/EpisodeBuilder.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/memory/InteractionWorkingMemory.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/memory/PersonalMemory.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/memory/PersonalMemoryDiagnostics.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/PersonalReflectionCompleted.kt`
