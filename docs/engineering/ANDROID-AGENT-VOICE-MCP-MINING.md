# Android-Agent / Voice Agent / MCP mining — RUNTIME-058 (MINING-P1-001)

Status: **SOURCE COMPLETE / BUILD NOT RUN / TESTS NOT RUN / DEVICE ACCEPTANCE NOT RUN.**

This batch makes the agent runtime bounded, voice-friendly, memory-efficient, extensible,
procedure-aware and capable of integrations without hardcoding every service. It connects five areas
that were previously separate — **voice, agent safety, memory, skills, external capabilities** —
into the existing Runtime architecture. No second framework, runtime, journal, scheduler, provider,
router, resource manager or model database was introduced.

## Reference repositories and branches

| Reference | Branch / path inspected | What was harvested | What was rejected |
| --- | --- | --- | --- |
| Android-Agent (`harness-improve`) | `core-agent/.../breaker/ToolOscillationDetector.kt`, `token/RunTokenLedger.kt`, `thermal/RunThermalGate.kt`, `memory/MemorySearch.kt`, `memory/MemoryTools.kt`, `approval/ToolApprovalCache.kt`, `ContextCompressor.kt` | bounded-run guards (wall-clock, tool-round, failure streak, oscillation, token budget), thermal gating as policy, memory *pull* instead of prompt dumps, short-lived exact-argument approval grants | their `AgentRunner`, agent architecture, memory system and journal. Halo's `AgentStep` journal remains execution truth. |
| Voice Agent (`ChaseDreamInfinity/voice-agent`) | `voice/AudioCaptureEngine.kt`, `VadProcessor.kt`, `SmartTurnClassifier.kt`, `SentenceExtractor.kt`, `RoutingTtsEngine.kt`, `VoiceAudioRouting.kt`, `memory/MemoryStore.kt`, `memory/embeddings` | semantic barge-in *evidence*, interaction/session-owned audio routing, sentence/clause TTS chunking, sticky output per response, tiered memory concepts, optional embedding capability | forcing the built-in speaker. Halo preserves wearable/headset affinity (`DISALLOW_PHONE` for wearable origin). |
| Brownie (`natanloterio/Brownie`) | `core/tools/.../mcp/McpClient.kt`, `McpManagerTool.kt`, `McpRemoteTool.kt`, `McpServerStore.kt`, `SkillsTool.kt`, `AgentSchedulerTool.kt`, `BrownieNotificationListener.kt` | MCP JSON-RPC mechanics (`initialize` → `tools/list` → `tools/call`), namespaced dynamic tool registration, learned-procedure metadata (origin, usage, staleness), agent-authored schedules, notification RemoteInput reply | Brownie's trust model and shell/delegation model. Remote capabilities remain subject to Halo policy; no shell or filesystem delegation. |
| kotlin-adk (`rdsciv/kotlin-adk`) | `adk-skills/.../SkillParser.kt`, `guides/edge-gallery-skills.mdx` | SKILL.md interoperability *concepts* only | its LiteRT module (intentionally incomplete/stubbed) and its skill runtime. |

## Deferred from this batch (explicit)

- SKILL.md foreign-frontmatter normalisation into `SkillImportNormalizer` (Part H): the existing
  `SkillMarkdownPackageParser` remains the canonical importer; broader foreign-frontmatter
  normalisation and explicit rejection/instruction-only import of unknown execution semantics are
  deferred so the change lands with the skills packaging work rather than beside it.
- Tool-discovery source metadata (`BUILT_IN` / `IMPORTED_SKILL` / `MCP` / `LEARNED_PROCEDURE`) in
  `halo_discover_tools` results (Part M).
- Voice settings UI for interruption mode (Part K3) and streaming-playback preference (Part L3):
  the policy, classifier contract and chunker exist in source, but the settings surface is deferred
  with the rest of the voice-flow stabilization.
- Notifications detail UI showing "Reply supported" (Part J5): Halo has no notification detail screen
  yet, so reply capability is reported through `halo_replyable_notifications` and Diagnostics instead.
- Activity rendering for MCP/procedure/routine events beyond the new `RunGuardTriggered` step
  (Part O).
- Enabling streaming speech by default: the chunker refuses non-terminal sources, but terminal-stream
  identity is not yet proven for every provider path, so the feature reports **Off**.

None of these are silently dropped; each is recorded here and in
[UI-IMPLEMENTATION-STATUS](../11-ui/UI-IMPLEMENTATION-STATUS.md).

## Sources of truth preserved

- `automaticToolCalling = false`; `HaloOpenApiTool.execute()` still throws loudly.
- `CognitiveSession`, `AgentRun` / `AgentStep` journal, `AgentRunCoordinator`.
- `NativeResourceCoordinator` and the `ResourceLeaseManager`.
- `AiProviderRouter`, `LocalModelSession`, `LiteRtLocalProvider`.
- `BargeInEvidence` / `BargeInPolicy` (extended, not replaced).
- `TtsSanitizer` (now applied per chunk), `InteractionRoutePolicy`, `InteractionContinuity`.
- `RoutineRepository`, `AndroidRoutineScheduler`, `RoutineExecutor`, `AutomationPermission`.
- `NotificationIngestionBridge`, `HaloNotificationListenerService`.
- `MemoryRepository`, `MemoryRecord`, `MemoryScope`, `MemoryContextSource`, `halo_search_memory`.
- `ToolRegistry` (one bounded dynamic overlay added, not a second registry).

## Verification status

Nothing in this batch was built, tested, linted, assembled, installed or physically validated. The
acceptance list in the batch request is a *later* pass. Source-level uncertainty is recorded in
[CURRENT-STATE.md](../../CURRENT-STATE.md) under RUNTIME-058.
