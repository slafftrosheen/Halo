# Pending-work consolidation review — 2026-09-30

## Scope and verification

The user requested collecting all uncommitted and unmerged work, reviewing the wiring, and merging
into local `main`. The starting commit was `faca1ae45cb1fc3ac8d8f7dee5836244e6deebc6`.
The pending checkout contained the P0 local-provider/audio hardening, P1 bounded-agent/MCP work,
P2 presentation/procedure/memory/web work, and P3 multimodal/capability-pack work.

The existing modified provider-selection test source is included in the consolidated snapshot.
No tests were added or run during consolidation. No build, compiler, lint, Gradle, emulator, adb,
or device acceptance command was run, following the user's explicit constraint.

Basic checks: Git whitespace validation; Kotlin delimiter inspection; internal-import and data-class
constructor-argument source audits; changed/new JSON parsing; composition-root, lifecycle,
database migration, provider, tool, policy, journal, and UI source review. These checks are source
inspection only and do not prove compilation or runtime behavior.

## Corrections made during review

- Matched the MCP entity index to its migration. Tool refresh now replaces rows transactionally,
  retains user toggles/risk, rejects namespace collisions, and removes stale registry entries.
  Configuration, refresh and registry synchronization share a mutex. Missing bearer credentials
  fail explicitly. HTTP requests cancel with their coroutine, bound response size, and refuse redirects.
- Preserved cancellation during routine creation, LiteRT resource admission, and saved-device
  auto-connect. Terminal agent runs release guards and approval grants. Tool outcomes reach the
  journal before failure-stop handling. Provider rounds enforce deadlines and token/repetition guards.
- Kept LiteRT artifact selection stable across loading, configuration, generation and qualification.
  Developer matrix, benchmark and raw-control actions share that ownership boundary. Idle eviction
  refuses an active operation. Configuration failures now pass through execution cleanup. Internal
  reasoning previews are no longer written to logs.
- Fixed notification reply payload loss caused by replacing RemoteInput's ClipData, and checked
  active notification, listener access and per-app policy before reply dispatch and listing.
- Restored missing Intelligence state fields and corrected the assistant settings StateFlow fallback.
  Presentation choice authority now snapshots suspend-backed durable choices before validation;
  corrected validator result names, bounded decoded trees, and rejected unsupported codecs/control
  characters before rendering persisted presentations.
- Returned complete procedure proposal IDs, bounded proposal lifetime/count, and rejected ambiguous
  abbreviated IDs. Memory retrieval now honors multiple requested scopes and SQL LIKE escaping.
- Made rendered-page extraction explicitly opt-in, implemented real DOM quiet polling, decoded
  JavaScript results correctly, bounded text before transfer, revalidated public destinations,
  prevented callbacks after teardown, and restored the process cookie policy after extraction.
- Accepted valid native zero-argument tool calls while rejecting absent argument objects.
  The blocked raw-control action now accurately labels its unload operation.

## Historical branch reconciliation

After fetching `origin`, no local branch contained unmerged commits and no unresolved index entries
were present. Fourteen remote tracking branches were not ancestors of `main`. They date from earlier
HeyCyan, runtime fabric, workload/context, GGUF and native-provider batches.

The cancellation-only batch branches have patch-equivalent changes already integrated into `main`.
Current code retains canonical HeyCyan capability IDs, persistent auto-connect, GATT/session cleanup,
capability/resource admission, durable recovery, masked input, workload routing, provenance and
remote-sharing gates from the older hardening/runtime work. Current state/roadmap documentation
records RUNTIME-020.1 and RUNTIME-021 as landed and review-corrected. The GGUF/native-local branches
describe an earlier provider architecture; current LiteRT and native-resource code supersede it.

These historical tips are reconciled with an explicit `ours` strategy history merge after committing
the reviewed current implementation. This preserves their commits as ancestors without importing
obsolete source trees or reverting the current architecture. It is a history reconciliation, not
a claim that every historical branch's file contents were copied. Remote branches are retained.
No remote push is part of this operation.

## Remaining acceptance boundaries

- Android compilation, Room-generated code, native LiteRT ABI behavior, model vision qualification,
  Bluetooth hardware, notification delivery, and WebView behavior still require later validation.
- Native audio-model transcription and background thermal scheduling remain prepared boundaries;
  dedicated STT remains the active path. Vision declaration and a passed artifact-specific probe
  remain separate facts. At this review baseline streaming speech was disabled; VOICE-UX-002 now enables exact tool-free terminal streams.
- Dynamic WebView extraction is disabled by default. It shares Android's process WebView facilities;
  destination checks are not a separate browser sandbox or a guarantee against DNS rebinding.
- Presentation preview choice groups are inert; durable clarification/approval remains authoritative.

## Collected files and historical tips

The inventory below is generated from the pre-commit Git source status. Build outputs, ignored local
configuration and credentials are outside this source snapshot.

### Historical tips

| Branch | Reviewed tip | Disposition |
| --- | --- | --- |
| `origin/codex/hcy-027-capability-contract` | `a8a9fb0c0ac78be1e1c1c1d98b5aaeb0ceb983b4` | Current HeyCyan hardening/capability/identity/registry implementation retained |
| `origin/codex/heycyan-integration-hardening-2026-09-10` | `7db20cb702905f929a7c9552b851951596aacc6a` | Current HeyCyan hardening/capability/identity/registry implementation retained |
| `origin/fix/batch-10-session-manager-cancellation` | `1ce03079f28adf9f7f4c5f9885cffab67ccef949` | Patch-equivalent cancellation fixes already integrated |
| `origin/fix/batch-11-lifecycle-cancellation` | `f51a97aa0613fd983e98fe214b022abfb064a551` | Patch-equivalent cancellation fixes already integrated |
| `origin/fix/batch-12-heycyan-session-cancellation` | `abcd4aa26a41ede070df765055789297b6cc95db` | Patch-equivalent cancellation fixes already integrated |
| `origin/fix/batch-6-heycyan-connect-finalization` | `8bb4112b878e7edc56ff44c4b8451b39c79f2c1d` | Patch-equivalent cancellation fixes already integrated |
| `origin/fix/batch-7-heycyan-cancellation-cleanup` | `ae47f87bc780f37f76d5777af8de54059acec5d4` | Current HeyCyan hardening/capability/identity/registry implementation retained |
| `origin/fix/batch-8-pairing-cancellation` | `65074c4b89507602b5572c1ab44e69609715d8e3` | Patch-equivalent cancellation fixes already integrated |
| `origin/fix/batch-9-heycyan-host-cancellation` | `82b2bca2eb17069e7e467ca281731dbaffd50a6a` | Current HeyCyan hardening/capability/identity/registry implementation retained |
| `origin/fix/first-hardening-batch` | `63fef4cd39cc7f465e49b5d993e3e1fb73732ced` | Current HeyCyan hardening/capability/identity/registry implementation retained |
| `origin/fix/local-memory-admission-20260914` | `376c36fa946682956bde7b061fc2cc6df88a5880` | Superseded provider/runtime architecture; preserve current LiteRT implementation |
| `origin/fix/native-local-litert-r26-1` | `9b7af80a4d3f524e81b3c26ae9f94669d75d60fd` | Superseded provider/runtime architecture; preserve current LiteRT implementation |
| `origin/fix/runtime-0201-review` | `346258d203b74b2cddbbaf50a4808738d1f8ebfd` | Runtime/context fabric already landed and review-corrected; preserve current implementation |
| `origin/fix/runtime-021-review` | `00d557b93d87c085335285435144a565cfbf99a0` | Runtime/context fabric already landed and review-corrected; preserve current implementation |

### Pending source inventory (157 files)

- `CURRENT-STATE.md`
- `app/build.gradle.kts`
- `app/src/main/assets/skills/halo.presentation.json`
- `app/src/main/java/com/halo/app/assistant/AssistantRuntimeBridge.kt`
- `app/src/main/java/com/halo/app/audio/HaloAudioSessionCoordinator.kt`
- `app/src/main/java/com/halo/app/audio/HaloSpeechOutputCoordinator.kt`
- `app/src/main/java/com/halo/app/capture/AndroidImageInputPreparer.kt`
- `app/src/main/java/com/halo/app/capture/EphemeralMediaAssetStore.kt`
- `app/src/main/java/com/halo/app/capture/HeyCyanCaptureCoordinator.kt`
- `app/src/main/java/com/halo/app/capture/LocalCaptureStore.kt`
- `app/src/main/java/com/halo/app/capture/MultimodalStatus.kt`
- `app/src/main/java/com/halo/app/capture/RuntimeVisualContextSource.kt`
- `app/src/main/java/com/halo/app/capture/VisualAttachmentStore.kt`
- `app/src/main/java/com/halo/app/capture/VisualAttachmentThumbnail.kt`
- `app/src/main/java/com/halo/app/capture/WorkspaceMediaAssetResolver.kt`
- `app/src/main/java/com/halo/app/data/AppDatabase.kt`
- `app/src/main/java/com/halo/app/data/WorkspaceContextStore.kt`
- `app/src/main/java/com/halo/app/data/agent/AgentRunRepository.kt`
- `app/src/main/java/com/halo/app/data/mcp/McpPersistence.kt`
- `app/src/main/java/com/halo/app/data/memory/MemoryContextSource.kt`
- `app/src/main/java/com/halo/app/data/memory/MemoryRecordDao.kt`
- `app/src/main/java/com/halo/app/data/memory/RoomMemoryRetriever.kt`
- `app/src/main/java/com/halo/app/data/notebook/NotebookStructuredResults.kt`
- `app/src/main/java/com/halo/app/data/notification/NotificationDao.kt`
- `app/src/main/java/com/halo/app/data/procedure/LearnedProcedurePersistence.kt`
- `app/src/main/java/com/halo/app/data/workspace/SendIntentHandler.kt`
- `app/src/main/java/com/halo/app/data/workspace/WorkspaceMediaAiPolicy.kt`
- `app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt`
- `app/src/main/java/com/halo/app/di/tools/HaloToolDescriptors.kt`
- `app/src/main/java/com/halo/app/di/tools/MemoryToolExecutor.kt`
- `app/src/main/java/com/halo/app/di/tools/NotificationReplyTools.kt`
- `app/src/main/java/com/halo/app/di/tools/PresentationChoiceAuthority.kt`
- `app/src/main/java/com/halo/app/di/tools/PresentationToolExecutor.kt`
- `app/src/main/java/com/halo/app/di/tools/ProcedureToolExecutor.kt`
- `app/src/main/java/com/halo/app/di/tools/RoutineCreationToolExecutor.kt`
- `app/src/main/java/com/halo/app/di/tools/host/WebInsightTools.kt`
- `app/src/main/java/com/halo/app/diagnostics/DiagnosticsExport.kt`
- `app/src/main/java/com/halo/app/interaction/AndroidInteractionPresenter.kt`
- `app/src/main/java/com/halo/app/interaction/InteractionSurfaceRenderer.kt`
- `app/src/main/java/com/halo/app/interaction/PresentationPreviewCatalog.kt`
- `app/src/main/java/com/halo/app/interaction/StructuredPresentationRenderer.kt`
- `app/src/main/java/com/halo/app/mcp/McpClient.kt`
- `app/src/main/java/com/halo/app/mcp/McpIntegrationManager.kt`
- `app/src/main/java/com/halo/app/mcp/McpProtocol.kt`
- `app/src/main/java/com/halo/app/mcp/McpToolAdapter.kt`
- `app/src/main/java/com/halo/app/mcp/McpTransport.kt`
- `app/src/main/java/com/halo/app/notification/HaloNotificationListenerService.kt`
- `app/src/main/java/com/halo/app/notification/NotificationReplyRegistry.kt`
- `app/src/main/java/com/halo/app/thermal/AndroidThermalStateSource.kt`
- `app/src/main/java/com/halo/app/ui/screens/activity/ActivityScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/devices/HeyCyanControlScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/home/HomeResponseProjection.kt`
- `app/src/main/java/com/halo/app/ui/screens/home/HomeScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/home/HomeViewModel.kt`
- `app/src/main/java/com/halo/app/ui/screens/notebook/NoteEditorScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/notebook/NoteEditorViewModel.kt`
- `app/src/main/java/com/halo/app/ui/screens/notebook/WorkspaceObjectDetailScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/AndroidAssistantScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/CapabilityStatusViewModel.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/IntelligenceScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/IntelligenceViewModel.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/ModelBrowserScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SkillsScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SystemCheckScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SystemCheckViewModel.kt`
- `app/src/main/java/com/halo/app/ui/shell/AppShell.kt`
- `app/src/main/java/com/halo/app/ui/state/RuntimeUiFormatters.kt`
- `app/src/main/java/com/halo/app/web/DynamicPageExtractor.kt`
- `docs/03-design/HALO-WORKSPACE-DESIGN.md`
- `docs/04-architecture/ARCHITECTURE.md`
- `docs/08-providers/ARCHITECTURE.md`
- `docs/08-providers/LITERT-DEVELOPMENT-MATRIX.md`
- `docs/10-features/FEATURES.md`
- `docs/11-ui/UI-IMPLEMENTATION-STATUS.md`
- `docs/12-security/THREAT_MODEL.md`
- `docs/14-roadmap/ROADMAP.md`
- `docs/engineering/ADAPTIVE-INTERACTION-MINING.md`
- `docs/engineering/ANDROID-AGENT-VOICE-MCP-MINING.md`
- `docs/engineering/CONSOLIDATION-REVIEW-2026-09-30.md`
- `docs/engineering/MULTIMODAL-PERCEPTION-MINING.md`
- `docs/engineering/ONEBOX-ZAFIRO-MINING.md`
- `docs/engineering/REFERENCE-HARVEST.md`
- `drivers/heycyan/src/main/java/com/halo/drivers/heycyan/HeyCyanHost.kt`
- `drivers/heycyan/src/main/java/com/halo/drivers/heycyan/thumbnail/HeyCyanThumbnailCaptureCoordinator.kt`
- `engines/ai/src/main/java/com/halo/engines/ai/AiProviderRouter.kt`
- `engines/ai/src/main/java/com/halo/engines/ai/AiSelectionRepository.kt`
- `engines/ai/src/test/java/com/halo/engines/ai/AiSelectionPersistenceTest.kt`
- `gradle/libs.versions.toml`
- `providers/anthropic/src/main/java/com/halo/providers/anthropic/AnthropicProvider.kt`
- `providers/gemini/src/main/java/com/halo/providers/gemini/GeminiProvider.kt`
- `providers/litert/src/main/java/com/halo/providers/litert/LiteRtConversationDiagnostics.kt`
- `providers/litert/src/main/java/com/halo/providers/litert/LiteRtLocalProvider.kt`
- `providers/litert/src/main/java/com/halo/providers/litert/LiteRtToolQualification.kt`
- `providers/litert/src/main/java/com/halo/providers/litert/LiteRtVisionQualification.kt`
- `providers/litert/src/main/java/com/halo/providers/litert/runtime/LiteRtControlProbe.kt`
- `providers/litert/src/main/java/com/halo/providers/litert/runtime/LiteRtEngineConfigFactory.kt`
- `providers/litert/src/main/java/com/halo/providers/litert/runtime/LiteRtNativeToolCall.kt`
- `providers/litert/src/main/java/com/halo/providers/litert/runtime/LiteRtRuntime.kt`
- `providers/litert/src/main/java/com/halo/providers/litert/runtime/LocalModelSession.kt`
- `providers/ollama/src/main/java/com/halo/providers/ollama/OllamaProvider.kt`
- `providers/openai/src/main/java/com/halo/providers/openai/OpenAiProvider.kt`
- `providers/openrouter/src/main/java/com/halo/providers/openrouter/OpenRouterProvider.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunGuard.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunGuardRegistry.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunJournal.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunLimits.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunTokenAccounting.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/EphemeralToolApprovalGrantStore.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/GenerationRepetitionDetector.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/ToolOscillationDetector.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/AssistantContextRepository.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/ContextAcquisitionCoordinator.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/ContextAcquisitionModels.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/ContextAssembler.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/ContextBudget.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/context/DefaultContextAssembler.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/AssistantPresentation.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/InteractionContent.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/InteractionResponseHandler.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/InteractionSurface.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/InteractionSurfaceValidator.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/PlanPresentationProjector.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/Presentation.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/PresentationCodec.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/PresentationCommitRegistry.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/PresentationMetadata.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/PresentationTemplates.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/PresentationValidator.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/memory/MemoryRetrieval.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/procedures/LearnedProcedure.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/session/CognitiveSession.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/ToolRegistry.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/CognitiveResponseCommitter.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/CognitiveTurnExecutor.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/CognitiveTurnPreparer.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/StreamingSpeechChunker.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/ToolTurnExecutor.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/workload/AiWorkloadPlanner.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/workload/AudioModelTranscriber.kt`
- `sdk/driver-sdk/src/main/java/com/halo/sdk/driver/LeasedCapabilities.kt`
- `sdk/provider-sdk/src/main/java/com/halo/sdk/provider/AI.kt`
- `sdk/provider-sdk/src/main/java/com/halo/sdk/provider/AudioContext.kt`
- `sdk/provider-sdk/src/main/java/com/halo/sdk/provider/ConfiguredOpenAiCompatibleProvider.kt`
- `sdk/provider-sdk/src/main/java/com/halo/sdk/provider/LocalInferenceRuntime.kt`
- `sdk/provider-sdk/src/main/java/com/halo/sdk/provider/ModelInputCapabilityReporting.kt`
- `sdk/provider-sdk/src/main/java/com/halo/sdk/provider/OpenAiCompatibleTransport.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/InteractionAudioAffinity.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/InteractionTurn.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/NativeResourceCoordinator.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/Observation.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/capability/OptionalRuntimeCapability.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/capability/RuntimeCapabilityRegistry.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/multimodal/AudioInputPreparer.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/multimodal/MediaAsset.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/multimodal/PcmAudio.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/multimodal/VisualSourceResolver.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/thermal/ThermalState.kt`
