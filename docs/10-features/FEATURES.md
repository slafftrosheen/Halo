# Feature requirements and implementation boundary

Status: CURRENT SOURCE MAP / PLANNED. Reviewed 2026-09-24.

| Feature | Current code | Remaining target |
|---|---|---|
| Notebook | Implemented AppShell route (NB-01 notes/search/lifecycle flow). | Expand to full inbox, conversation, and files integration; implement explicit Working Set and robust local search. |
| Personal memory | Wired in-memory cognitive stores; declared Room Memory entity/DAO. | Reconcile in-memory vs Room architectures. Implement durable, searchable, editable, and exportable facts with provenance. |
| Reminders | Wired `AndroidMemoryCapability` exact-alarm scheduling with simple duration parser. | Handle scheduling permissions gracefully, implement CRUD, reboot/timezone recovery, and complex ambiguity resolution. |
| Automation | Declared named engine module/contracts. | Target: Persisted IF/THEN time/location/device triggers and auditable bounded execution. |
| Notification summarization | Declared empty `NotificationListener` class in `host-android`. | Target: Registered listener service, scoped permission, intelligent filtering, and contextual summary delivery. |
| Agentic tools | Wired `CognitiveSession` loop; implemented basic echo/time tools. | Target: Typed bridges to complex phone/device capabilities beyond basic utilities. |

*Definitions:*
- **Implemented**: Type exists and has a reachable execution path in the current composition.
- **Wired**: Configured in DI or system startup, but might have incomplete capability.
- **Declared**: Type/interface exists but is not wired or reachable.
- **Tested**: Covered by automated tests.
- **Hardware-verified**: Proven on physical devices.
- **Target**: Intended future behavior.

## RUNTIME-058 / MINING-P1-001 status

| Feature | Current state |
| --- | --- |
| Bounded agent runs | **Implemented + wired** (`AgentRunLimits`, `AgentRunGuard`, shared `AgentRunGuardRegistry`, `AgentStep.RunGuardTriggered`). Not built, tested or device-verified. |
| Thermal governor | **Implemented + wired** (`ThermalStateSource`/`ThermalPolicy` in runtime-sdk, `AndroidThermalStateSource` in the host, gate injected into the run guard). |
| Run-scoped token accounting | **Implemented + wired** (`AgentRunTokenAccounting`; estimated and reported tokens stay separate). |
| Exact approval grants | **Implemented + wired** (`EphemeralToolApprovalGrantStore`, policy-bounded; never for `ALWAYS`/`SENSITIVE`/`NEVER_REPLAY`, never for denials). |
| Memory pull + bounded search | **Implemented + wired** (`MemoryRetriever`, `RoomMemoryRetriever`, shared with context assembly and `halo_search_memory`). Semantic search **declared** (no embedding provider installed). |
| MCP capability fabric | **Implemented** (`McpTransport`, `McpClient`, `McpIntegrationManager`, namespaced tools in the existing registry, Room v19). **Wired** into the composition root; no MCP screen yet. |
| Learned procedures | **Implemented + wired** (propose/save/use, bounded document store, no capability escalation). |
| Model-callable routines | **Implemented + wired** (`halo_create_routine` over the existing repository/scheduler/validation). |
| Notification reply | **Implemented + wired** (live RemoteInput registry, `DISPATCHED` truth, `SENSITIVE`+`ALWAYS`). No notification detail screen yet. |
| Barge-in 2.0 | **Implemented + wired policy** (sustained speech + optional semantic class; physical activation unconditional). Semantic classifier: **declared, not installed**. |
| Streaming speech | **Declared + wired contract** (`StreamingSpeechChunker` refuses non-terminal sources). **Disabled** by default until terminal-response identity is proven per provider path. |
| Capability overview UI | **Implemented + wired** (Skills summary card). |

Status vocabulary for this table is Halo's own: **Implemented** = type exists with a reachable path;
**Wired** = configured in the composition root; **Declared** = type/interface exists but is not
installed or enabled; **Disabled** = intentionally inert. Nothing here is "tested" or
"hardware-verified", and the batch's own status line is
**SOURCE COMPLETE / BUILD NOT RUN / TESTS NOT RUN / DEVICE ACCEPTANCE NOT RUN**.

## RUNTIME-059 / MINING-P2-001 status

| Feature | Current state |
| --- | --- |
| Structured presentation catalog | **Implemented + wired** (`Presentation.kt`, closed and versioned). Not built or device-verified. |
| Presentation validation | **Implemented + wired** (`PresentationValidator`: depth, node, text, list, comparison and choice ceilings; bounded rejection reasons). |
| `halo_present` | **Implemented + wired** (`READ_ONLY`, `BOOTSTRAP`, `halo.presentation` skill). Presentation only — it cannot execute anything. |
| Deterministic templates + result adapters | **Implemented** (`KEY_VALUE_SUMMARY`, `COMPARISON`, `CHECKLIST`, `SEARCH_RESULTS`, `STATUS`, `CHOICE`; device/model/web/memory/MCP adapters). Card binding for web results still deferred. |
| Committed response carrier | **Implemented + wired** (`PresentationCommitRegistry` → `PresentationMetadata` in existing turn metadata; no new table, no migration). |
| Home rendering | **Implemented + wired** (structured card above the text fallback, explicit "Save to Notebook" action). |
| Android Assistant rendering | **Implemented** (COMPACT density through the existing surface renderer; comparisons degrade honestly). |
| Activity plan summary | **Implemented + wired** (projected checklist + `3 / 4 steps complete · Waiting for approval`). |
| Notebook handoff | **Implemented + wired** (text fallback as note body, bounded payload in existing provenance metadata, read-only original view). |
| Working Set | **Already existed** (`WorkingSetEntry`, Working Set sheet, `ContextSource.WorkingSet` priority). Nothing parallel added. |
| Dynamic web fallback | **Implemented + wired, opt-in** (`DynamicPageExtractor`); static extraction stays primary. |
| Optional capability packs | **Implemented + wired** (semantic memory, smart interruption, vision, audio understanding — all Not installed). |
| Audio preparation | **Declared** (`AudioContext`, `SpeechInterpretationStrategy`, `Pcm16Wav`). `DEDICATED_STT` remains the default and Sherpa is untouched. |
| System Check presentation section | **Implemented + wired** (passive rows + static component preview). |

Status vocabulary is unchanged: **Implemented** = type exists with a reachable path; **Wired** =
configured in the composition root; **Declared** = exists but not installed/enabled; **Disabled** =
intentionally inert. Nothing here is tested or hardware-verified.
# MINING-P3-001 — multimodal perception and capability packs (2026-09-30)

Home supports one explicit camera/shared/Notebook image attachment, thumbnail, question, remove and explicit Save to Notebook. AI capture alone remains ephemeral. HeyCyan has a visual-question action through normal cognition and wearable delivery. Assistant uses semantic screen extraction first; screenshot fallback is opt-in, local-vision-only and fails closed on missing package/screen evidence or sensitive omissions. PCM16/WAV and bounded audio transcription utilities are prepared; dedicated STT remains the normal voice path. Video, surveillance, recognition of people and always-on visual memory are deferred.

## Long tasks and bounded research — MINING-P4-001

Source-wired tools: `halo_delegate_task` (five fixed roles, explicit selected references and optional run-owned payload IDs), `halo_research` (up to two bounded topics), `halo_read_payload` (owner-authorized 4 KiB ranges), and `halo_search_conversations` (local-only current-conversation excerpts). Parent synthesis uses the ordinary provider continuation. Research source collection uses available URL/MCP read capabilities; general browser automation and unrestricted search are outside this batch.

Children are read-only, depth one, bounded by the central DELEGATED P1 limits, pinned to normal FAST/REASONING routing, and limited to four children per root, two globally active slots, one local slot. Research topics run sequentially by default. Side-effect/approval requests become parent input rather than inherited grants.

Explicit Save to Notebook stores final synthesis and bounded source evidence/provenance. Saving does not promote cognitive memory. Working Set, context inspector, Home progress, Activity children, Intelligence toggles and System Check diagnostics are wired to existing state. All claims here concern source implementation; build/tests/device acceptance remain unrun. [Details](../engineering/LONG-HORIZON-AGENT-MINING.md).
