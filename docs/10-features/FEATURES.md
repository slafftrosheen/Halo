# Feature requirements and implementation boundary

## FEATURE-071 / CONTINUITY-001 — 2026-10-08

Phone-hosted conversations now bind each fresh turn and durable run to an exact existing conversation; background commits never activate another conversation. Explicit Continue on… changes delivery only, preserving run/interaction/origin and exact wearable no-phone-fallback policy. Recovery is tracked, missing conversations require explicit rebind, and pending cross-surface input uses the existing atomic first-wins handler. Home projects recent capture metadata into an exact Notebook link; System Check stays passive. No migration, new device owner, driver protocol, inference owner or automatic connection is added. Process death preserves journal truth without surprise execution. Automated and physical acceptance status are recorded in the [implementation report](../engineering/CONTINUITY-001-PHONE-WEARABLE.md).


## FEATURE-070 / DAILY-ASSISTANT-001 — 2026-10-08

Home is conversation-first with a deterministic Today projection from existing Attention, agent journal, Inbox, Study, learning, routine and device/audio owners. One primary and at most three secondary items link to exact existing owners; a bottom sheet shows the rest. Private notification/suggestion bodies stay out of Home. Daily owns presentation priority only, with no inference, domain writes, scheduler, polling loop or Room migration. Active task progress remains on the existing Halo card. Automated gate passed: 604 tests (runtime SDK 48, cognition 216, app 340), zero failures/errors/skips; debug APK assembled. APK installation and process launch succeeded on the development phone. Keyguard remained active with the screen off, so the single manual feature pass is NOT RUN.

See [Daily Assistant implementation](../engineering/DAILY-ASSISTANT-001.md).

## FEATURE-069 — source-grounded Study (2026-10-08)

Study now chooses/imports up to four Workspace sources, resolves block-aware notes and bounded UTF-8 text, and sends one explicitly active image/PDF page only to exact verified vision targets under Workspace/cloud privacy policy. PDF support is lazy page-image rendering; PDF text extraction and OCR are not installed. Learner level, support style, independent languages and explicit source-only/general policy persist with per-source versions. Citations follow actual S1–S4 answer markers; model feedback remains unverified. Practice generation produces bounded, source-located drafts requiring Keep, and review preserves original provenance. Material/settings live in a lesson sheet; topic-only lessons remain available.

See [implementation, limits and validation](../engineering/STUDY-SOURCES-002.md). Older source-only/design statements below are historical context.


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


## STABILIZATION-PROVIDER-001 - 2026-09-30

**IMPLEMENTED / UNVALIDATED.** Provider setup and normal conversational responses are source implemented/unvalidated. Model Browser has cached local search, provider/location/capability and three cost modes. Home has selected-provider recovery. ElevenLabs uses the existing selected-PCM transaction path; Android remains automatic.

See [source map and deferred acceptance](../engineering/PROVIDER-CONVERSATION-STABILIZATION.md). Build, tests and device acceptance were not run. Earlier dated sections below are historical.


## P8 provider UX and conversation reset — 2026-09-30

General conversational answering is a core Halo capability and does not depend on tool availability. DirectResponse/CHAT is separate from Agentic actions; configuration, authentication, model discovery and generation proof are separate. Speech uses the selected PCM provider, protected ElevenLabs opt-in, phone-only settings tests and host-owned route policy. See [implementation and deferred acceptance](../engineering/PROVIDER-UX-AND-CONVERSATION-RESET.md). Earlier sections retain historical context.


## PRODUCT-P0-001 — source update, 2026-09-30

Provider Control Center covers twelve registered inference providers, with separate detail/model/role/local/policy/usage destinations. General conversation does not require tools. ElevenLabs TTS adds explicit voice/model discovery, persistent speech selection and optional Android synthesis fallback on the same audio route.

See the [control-plane source map](../engineering/PROVIDER-CONTROL-PLANE.md) for ownership, official contracts, bounds, security and the later QA plan. Build/tests/device acceptance: not run. Earlier dated descriptions below remain historical.

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
| MCP capability fabric | **Implemented** (`McpTransport`, `McpClient`, `McpIntegrationManager`, namespaced tools in the existing registry, Room v19). **Wired** into the composition root; Integrations list/detail and credential/tool controls are wired. |
| Learned procedures | **Implemented + wired** (propose/save/use, bounded document store, no capability escalation). |
| Model-callable routines | **Implemented + wired** (`halo_create_routine` over the existing repository/scheduler/validation). |
| Notification reply | **Implemented + wired** (live RemoteInput registry, `DISPATCHED` truth, `SENSITIVE`+`ALWAYS`). Inbox/detail reply and dismissal confirmations are wired; external delivery remains unconfirmed. |
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

## MINING-P5-001 — phone screen control (2026-09-30)

Screen fallback gains semantic target previews, exact-revision rejection, deterministic set-checked,
action-specific postconditions, filtered ranked find, up-to-three-scroll search and bounded read-only
wait. Native contact/reply/dial/share and other phone tools remain preferred. Typing a draft and sending
it are separate operations. A dispatched action may remain unconfirmed; that distinction appears in
responses/TTS and Activity. [Full source contract](../engineering/SEMANTIC-PHONE-OPERATOR.md).
These features are source implemented; builds/tests/device acceptance have not been run.

## MINING-P6-001 — proactive automation runtime (2026-09-30)

Automations now support app foreground, exact-app notification, battery crossing, charging and validated network transitions as well as device/time/once rules. BatteryAbove works with unknown distinct from false. Only descriptor-opted-in background/persistable actions can run; initial catalog is offline notification digest. Per-definition delivery defaults silent. Event grouping, cooldown, grant digest and durable attention receipts are implemented. Runtime-only reachability is explicit.

See [Proactive automation runtime](../engineering/PROACTIVE-AUTOMATION-RUNTIME.md).

## MINING-P7-001 — personal intelligence and reflection (2026-09-30)

P7 implements Memory & learning modes, confirmed-memory provenance/revisions/staleness, bounded historical episodes, explicit memory tools, inline Inbox proposals and reviewed procedure/manual-routine suggestions. Ordinary learning is silent; no automatic memory promotion, behavior creation or capability download. Current retrieval is lexical; optional model reflection is local FAST and opt-in.

See [P7 implementation ledger](../engineering/PERSONAL-INTELLIGENCE-REFLECTION.md).

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

## FEATURE-068 / MEMORY-UX-002 — Personal Memory control center

Workspace includes Memory at `notebook/memory`, with saved/pending counts, the
existing conversation-memory policy, local debounced shared lexical retrieval,
kind/scope filters and browse sorting. Opening the hub does not mark memory used.
Expired records remain browsable as Needs review; current search/context retrieval
keeps existing expiry and scope rules. Semantic retrieval remains not installed.

Manual creation and note promotion review content and exact source revision before
conflict detection. Reinforcement and conflicting replacement require explicit
review; secrets are rejected through existing repository contracts. Imported
confirmation reinforces approval while preserving origin, content, scope,
confidence and expiry. Correction/forgetting use canonical revision/tombstone,
Workspace trash and Working Set detachment paths. No new schema or memory owner.

The existing detail screen presents human provenance, scope, actual usage, genuine
Workspace source links and collapsed history. Approval is separate from original
source; usage is context admission, not causal proof. Suggestions remain pending
until accepted through PersonalLearningRepository. Procedures, automations and
capability setup keep their existing review destinations. Episodes are Learning
history, distinct from saved facts and transient working memory.

Learning modes remain Off / Explicit only / Suggest, with Explicit only the default
and local reflection opt-in. Memory use is independent; cloud permission and
retrieval budgets/ranking are unchanged. Notebook documents remain separate.
Automated gate: 216 cognition + 283 app tests (499 total), zero failures/errors/skips;
debug APK assembled. ADB had no connected device; the single manual Memory sanity
pass remains pending. No physical acceptance is inferred from the automated gate.
See docs/engineering/MEMORY-UX-002-CONTROL-CENTER.md for validation and limitations.

## FEATURE-072 / WEARABLE-INTERACTION-002 — 2026-10-08

FEATURE-072 adds generic Bluetooth Classic/BLE, wired/USB audio projection and exact playback, HostDefault, independent handoff channels and native nearby Wear OS surfaces. Source/build support and hardware acceptance are separate.

See `docs/engineering/WEARABLE-INTERACTION-002.md` and `docs/testing/WEARABLE-ACCESS-PHYSICAL-001.md`.

## FEATURE-074 controls - 2026-10-08

FEATURE-074 partial source: evidenced controls, safe device/class mappings, scoped media stop/repeat, confirmed thumbnail photo saving, explicit capture-question staging, Workspace/Study/navigation and continuity delegation. Native configurable TalkToHalo and watch PTT/button integration are not complete. Physical acceptance and main merge remain open.

See [implementation and open integration gates](../engineering/WEARABLE-CONTROLS-003.md).

## FEATURE-075 / CONTEXTUAL-CAPTURE-001 — 2026-10-09

One app-layer capture review coordinates existing temporary/Workspace media, exact Ask binding, visual capture-note Study sources, idempotent Save and note attachment, constrained extraction and editable Memory review. Ordinary capture performs no inference. Temporary image cloud sharing is explicit and all existing global/model/object privacy gates remain in force. Command dispatch, receipt, saving, analysis, verification and memory approval remain distinct. Software gate: 681 tests passed and APK installed. Physical acceptance is pending user observation; FEATURE-073 is absent, so integration to main is withheld. See [implementation and evidence](../engineering/CONTEXTUAL-CAPTURE-001.md).
