# Adaptive interaction / plan / web mining — RUNTIME-059 (MINING-P2-001)

Status: **SOURCE COMPLETE / BUILD NOT RUN / TESTS NOT RUN / DEVICE ACCEPTANCE NOT RUN.**

This batch extends Halo's existing Runtime-owned `InteractionSurface` system with bounded, versioned
structured presentation, adds plan projection, hardens web reading with an optional DOM-settle
fallback, and prepares optional capability packs and multimodal audio contracts. **A2UI was not
adopted as a second surface system**, and no second planner, browser automation, or runtime ownership
was taken from any reference.

## Reference repositories

| Reference | What was harvested | What was rejected |
| --- | --- | --- |
| [kotlin-adk](https://github.com/rdsciv/kotlin-adk) (`adk-core/.../a2ui`, `adk-compose/A2uiRenderer.kt`, `AgentSurface.kt`) | versioned structured UI messages, a **closed** component catalog, streaming-safe accumulation, a host renderer registry, and the create/update/delete-surface concept (mapped onto Halo projection events with monotonic revisions) | its durable-state model, arbitrary action dispatch, generic unrestricted model-generated Compose, and its LiteRT module |
| [Android-Agent `harness-improve`](https://github.com/Rickeal-Boss/Android-Agent/tree/harness-improve) (`agent/plan/AgentPlanStore.kt`) | visible plan steps and PENDING / IN_PROGRESS / COMPLETED progress projection | its plan store. Halo's `ExecutionPlan`, `Task`/`TaskState` and `AgentStep` journal remain the only plan truth; the agentic path derives **only verified** phases from journal evidence |
| [Brownie](https://github.com/natanloterio/Brownie) (`browser/BrowserService.kt`) | the DOM-settle concept: observe mutations, require a quiet window, then extract bounded meaningful content | unrestricted autonomous browsing; the dynamic path is one isolated, ephemeral WebView behind an explicit contract |
| [voice-agent](https://github.com/ChaseDreamInfinity/voice-agent) (`voice/SentenceExtractor.kt`, `memory/embeddings`) | the audio-model compatibility lesson that a container may be required, and the optional-embedding capability shape | forced speaker routing, mandatory embedding downloads, direct audio→agent answer |

## Architecture added

- **Presentation catalog** (`Presentation.kt`): a closed, versioned node set (`Text`, `Heading`,
  `Caption`, `KeyValue`, `Badge`, `Status`, `Progress`, `BulletList`, `Checklist`, `Comparison`,
  `ImageReference`, `Divider`, `Row`, `Column`, `Card`, `ChoiceGroup`). No generic container, no
  callback, no Intent, no URL fetch, no tool name is representable.
- **Validator** (`PresentationValidator.kt`): depth 6, 120 nodes, 6 000 text characters, 20 list
  items, 12×5 comparison bounds, 6 choices, 24 000-character payload cap. Structurally invalid input is
  **rejected with bounded reasons**, never truncated.
- **Templates** (`PresentationTemplates.kt`): `KEY_VALUE_SUMMARY`, `COMPARISON`, `CHECKLIST`,
  `SEARCH_RESULTS`, `STATUS`, `CHOICE`, plus deterministic result adapters (device status, model
  status, web result, memory results, MCP discovery). A 0.6B model supplies a flat directive, not a
  nested tree.
- **Plan projection** (`PlanPresentationProjector.kt`): `ExecutionPlan`/`Task` and journal evidence →
  checklist; `progressLine()` yields `3 / 4 steps complete · Waiting for approval` for collapsed rows.
- **Response carrier + projection** (`AssistantPresentation.kt`): `AssistantPresentation(text,
  structured, provenance)` and `SurfaceCreated`/`SurfaceUpdated`/`SurfaceRemoved` with a monotonic
  revision that **rejects stale updates**.
- **Codec + metadata** (`PresentationCodec.kt`, `PresentationMetadata.kt`): a dependency-free bounded
  codec so a committed response can carry a rebuildable presentation in existing turn metadata
  (`presentationSchemaVersion`, `presentationPayload`, `presentationSummary`) — no second surface
  database and no extra Room migration. `contextSafeText` documents that the payload is never fed back
  into model context.
- **Tool** (`halo_present`): flat directive → deterministic expansion → hard validation → ephemeral
  surface revision → tiny machine result. `READ_ONLY`, `BOOTSTRAP`, and the `halo.presentation` skill
  manifest grants no external ability.
- **Choice authority** (`PresentationChoiceAuthority`): a `ChoiceGroup` resolves only an unresolved
  durable `AgentInputRequest.Clarification` whose option ids match, read from the journal.
- **Web hardening** (`DynamicPageExtractor`, `PublicPagePolicy`): static extraction stays primary;
  when static text is below 200 characters an opt-in isolated WebView may run one Halo-owned
  extraction script after a ~550 ms DOM quiet window under a 6 s hard deadline, with file/content
  access, DOM storage, cookies, autofill and password saving disabled, `about:blank` + `destroy()`
  teardown, and public-network re-validation on every navigation and subresource.
- **Optional capability packs** (`OptionalRuntimeCapability`, `BuiltInOptionalCapabilities`): semantic
  memory, smart interruption, vision and audio understanding, all reporting **Not installed** because
  Halo has verified no artifact. Rendered in the Skills capability card.
- **Audio preparation** (`AudioContext`, `SpeechInterpretationStrategy`, `Pcm16Wav`): a portable
  asset-referencing audio context, a two-pass strategy contract that keeps the transcript as the
  semantic boundary, and a PCM16→WAV wrapper for a future multimodal adapter. `DEDICATED_STT` remains
  the default and Sherpa is untouched.

## Commit path, hosts and workspace handoff

- **Commit bridge** (`PresentationCommitRegistry`): the ONLY process-local state in this feature. A
  validated presentation is *staged* against its surface id by `halo_present`, *promoted* into run
  scope only when `DefaultToolTurnExecutor` observes that exact call succeed, and *consumed once* by
  `DefaultCognitiveResponseCommitter`, which merges `PresentationMetadata` into the committed turn.
  Entries expire (10 min) and both maps are bounded (8), so a run that never commits cannot leak a card
  into a later conversation. There is no second durable surface store: the durable facts are the
  journal step and the committed turn.
- **Provenance (Part F)**: the committer adds bounded, content-free identity keys to the same metadata
  (`presentationRunId`, `presentationInteractionId`, `presentationProviderId`, `presentationModelId`,
  `presentationCreatedAt`) only when a payload was actually committed, so expanded Activity/debug detail
  can identify the producer without a nested store and without exposing it prominently.
- **Brain backup**: turn metadata is already part of the snapshot, so a committed card rides along
  with the conversation it belongs to. Ephemeral progress surfaces are never persisted at all.
- **Home restore**: `HomeResponseProjector` decodes the payload from conversation truth, so a card
  survives process death; `HomeUiState.structuredPresentation` renders `StructuredPresentationRenderer`
  above the assistant's own text, which stays the spoken and persisted answer. A newer schema version
  decodes to `null` and the turn simply renders as prose.
- **Activity**: `AgentRun.toStory()` attaches a projected plan checklist and a compact
  `progressLine()` (`3 / 4 steps complete · Waiting for approval`) when the journal evidences at least
  two execution phases. Steps can only read COMPLETED from recorded success, so model prose can never
  mark work finished.
- **Notebook handoff**: saving a structured answer is an explicit host action on the response card
  ("Save to Notebook"). The note body carries the text fallback and the bounded payload rides in the
  object's existing provenance metadata; `NotebookStructuredResults` decodes it for display in the
  read-only note view. The note stays an ordinary editable document — the presentation schema is never
  merged into the Markdown document schema — and unsupported/corrupt payloads degrade to plain text.
- **Working Set (Part P)**: Halo already had `WorkingSetEntry`, `WorkspaceContextSource`, the Working
  Set sheet and `ContextSource.WorkingSet` priority in `DefaultContextAssembler`. No parallel model was
  added for this batch; the projector reuses what exists.
- **System Check**: a passive "Interaction presentation" section (renderer availability, schema
  version, live surface count, structured-response support) plus an explicitly tapped preview of fixed
  sample components. No probe, model call or network request happens because the screen opened.

## Deferred (recorded, not dropped)

- A full `halo_fetch_url` presentation card binding (the `webResult` adapter exists) and
  Intelligence-screen separation of language models from capability models beyond the Skills card.
- Enabling rendered dynamic extraction by default for any host that has not opted in.
- Charts, tables, Canvas, HTML and any generic external component catalog (deliberately out of scope).

## Verification status

Nothing here was built, compiled, linted, tested, installed or run on a device. Every entry in this
batch is production source plus documentation. The acceptance list in
the batch request is a later pass; source-level uncertainty is recorded in
[CURRENT-STATE.md](../../CURRENT-STATE.md) under RUNTIME-059.
