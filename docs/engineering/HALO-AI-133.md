# HALO-AI-133 implementation handoff

## Batch A — local speech

**IMPLEMENTED — NOT BUILT — NOT TESTED — PHYSICAL ACCEPTANCE PENDING**

Base: `995b14e4`. Source-only work, following the coding-only specification and [Issue 33](https://github.com/slafftrosheen/Halo/issues/33). No validation commands, native initialization, model transfer, tests or builds were executed by Codex.

Modules: provider SDK speech pack/segment contracts; app speech pack registry, Kokoro lifecycle/storage, routed PCM player, synthesis registry, provider factory and existing speech settings. EN/LV/RU catalogue/resources updated as source generation, not validation.

Kokoro keeps independently content-addressed installed packs and the existing `kokoro_pack_version` selected pointer. Download/import verifies structure and content without synthesizing, selecting or deleting an older pack. Explicit selection rehashes content off the UI thread. Removal and synthesis share the provider mutex; active segmented responses pin their pack against deletion. The archive and both extraction copies are included in storage admission. Legacy selected directories remain readable.

The optional ejectable native engine uses the existing NativeResourceCoordinator execution lease and idle/memory-pressure machinery; on-demand remains the default. Native calls are serialized and cleanup waits for calls to finish. Completed sentences flow through a rendezvous channel into the existing routed player; no Sherpa callback is presented as sample streaming. Provider, version, voice and settings are pinned for the response; PCM cache identity includes the pack hash/settings. Existing final-answer prefix reconciliation remains authoritative. There is no speech from reasoning/tool payloads.

Kokoro English 0.19 and multilingual 1.1 retain existing pinned archive hashes/URLs. New INT8 imports remain blocked from selection pending separate non-silent/audible qualification. Supertonic has a separate CPU provider using the actual pinned API, but its host-selected qualified-pack callback defaults to unavailable; no trusted publisher pack hash or qualified Latvian/Russian/Polish voice is invented. Generic ONNX import remains unsupported. Automatic Supertonic acquisition/qualification is deliberately absent; a future host must supply a verified pack and task-specific evidence. Candidate languages are candidates, not acceptance claims.

Pinned API sources reviewed: [Sherpa Tts.kt v1.13.8](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/kotlin-api/Tts.kt), [Supertonic example](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/kotlin-api-examples/test_supertonic_tts.kt). No dependency upgrade.

Owner acceptance: legacy migration, failed/resumed imports, two installed versions, switch during playback, warm/critical-memory eviction, cancellation/late chunks, exact endpoint routing, speed/voice/cache identity, non-silent PCM and audible language/voice quality. Existing logs from AI-132 do not qualify this implementation.

Next dependency: Batch B adds workload assignment/resolution and independent embeddings; no role values have been changed by A.

## Batch B — workload fabric, embeddings and retrieval

**IMPLEMENTED — NOT BUILT — NOT TESTED — PHYSICAL ACCEPTANCE PENDING**

Modules: provider SDK workload/embedding-space contracts; AI engine pure resolver/layered intent boundary; LiteRT native embedding provider; cognition bounded semantic index; app private index storage, authoritative source adapters, additive policy preferences and existing Model roles settings.

The eleven workloads map CHAT/FAST/THINK/ACTION/VISION to existing stored roles without renaming them. Assignments and execution profiles are separate. Exact environment/task/backend evidence is distinct from installed/native-readable/resident/executing status. Explicit targets never silently fall back. Automatic policy rejects missing proof, unsupported backends, unknown RAM admission and private remote work. Returned backend policy is not a claim of observed backend execution. The optional classifier grants no authority; existing deterministic parsing, NativeCapabilityDispatcher, DirectUserCommandAuthority and tool journals remain the action path. No second classifier is loaded.

The embedding adapter uses pinned 0.18.0 `EmbeddingEngine`, `InputData.Text`, native normalization and actual vectors, validating exact installed SHA, native embedding task, dimension and finite values. It does not generate text, truncate dimensions or assume device-specific exports work. CPU is the initial execution path. It refuses a resident LiteRT conflict and memory pressure/unknown headroom rather than creating a second resident native engine. Native cleanup precedes lease release, including cancellation. The shared Supertonic idle-release callback was tightened to refuse eviction during an active call.

The index is explicit opt-in and local-only: at most 64 selected sources, 512 chunks, one million vector values and 32 MiB private sidecar data. It incrementally reuses unchanged chunks and atomically publishes complete updates. Space identity includes hash, dimensions, normalization, runtime and preprocessing. Notebook projections, Canvas owned text, accepted Study cards/goals, and explicitly selected text/plain or Markdown note attachments have authoritative source adapters. Unsupported extraction formats are not indexed. Source edits/deletions invalidate derived chunks via a conflated Room observer; query checks current ownership/version again after embedding. Context segments carry source/version provenance, untrusted-data semantics and `remoteAllowed=false`. No Room schema migration or original-document edits.

Limits: this is one private `personal` namespace; it does not invent multi-user workspace ownership absent from the current schema. PDF/image extraction must first produce supported authoritative text through existing extraction flows. Legacy conversation routing remains authoritative; the new execution preferences are additive inputs for workload consumers, not competing writes to native tuning. Batch C connects orchestration; Batch D supplies explicit diagnostics/index actions. No automatic indexing, classification, model loading or benchmarking on startup/settings entry.

Source reference: [LiteRT EmbeddingEngine 0.18.0](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/EmbeddingEngine.kt) and [EmbeddingConfig](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/EmbeddingConfig.kt). Alpha acceptance must cover each exact installed export, special-token/vector space behavior, permissions/deletion races, interrupted publication and index rebuilds after identity changes. No embedding execution was performed by Codex.

Next dependency: C consumes these workload and context boundaries while retaining the existing execution/approval authority.
