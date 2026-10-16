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

## Batch C — dependency-aware execution

**IMPLEMENTED — NOT BUILT — NOT TESTED — PHYSICAL ACCEPTANCE PENDING**

Modules: runtime SDK plan/stage/lease/telemetry contracts; cognition turn preparation/execution; speech and interpretation engines; app host wiring; transient provider execution profiles and LiteRT load-policy handling.

The portable scheduler bounds plans and stages, checks dependencies/cycles, sorts priorities, waits on completed children rather than polling, enforces relative deadlines and propagates parent cancellation. Scheduler-owned resource claims use the existing ResourceLeaseManager. Native stages declare provider-owned leases and do not reacquire a competing lease around the JNI owner. Every stage reports content-free monotonic wait/duration and bounded failure codes into a 256-event timeline. No graph is persisted as replayable authorization.

The actual cognition path schedules preparation and pinned provider turns. Preparation can overlap independent context acquisition and an explicitly admitted selected-model preparation callback. Unadmitted preparation is SKIPPED, not a successful warmup. Model/voice/resource identity remains pinned to existing execution objects; canonical native dispatch, tool turns, run guards, continuations and journal recovery remain authoritative. Direct native command handling still precedes the conversation path.

Only live recognizers' genuine partial hypotheses receive a bounded advisory pass through existing deterministic rules. Revisions replace hints, no transcript enters the new telemetry, no LLM/semantic rule is called, and no tool authority is issued. Final recognition remains the only authoritative utterance. Buffered recognizers emit no invented partials.

New workload preferences are read only when explicitly configured. Context/output/time bounds and transient CPU/GPU/residency requests are attached to the pinned provider request. Native tuning keys remain intact. Manual GPU requests reject an unavailable or mismatched effective backend. Routing/privacy continues to inherit the existing policy unless a user explicitly requests a tighter workload policy. Legacy provider/cloud/role selections are not rewritten by presets.

The preparation policy requires exact task/environment evidence, normal pressure, known headroom, a sufficiently large RAM tier, acceptable thermal state, no active microphone and no likely direct command. It keeps useful residents instead of churning them. Until D supplies valid evidence and an explicit user opt-in, speculative native initialization stays disabled. Sentence TTS and original StreamingVoiceResponse reconciliation from A remain the sole speech path; existing `streamingSpeechEnabled` controls early final-answer speech, and heavy native execution exclusion remains with the mobile policy.

Limits: this is selective overlap, not unconditional CPU/GPU parallel execution. The scheduler does not replace capture/playback lifecycle owners or journal recovery. Android background, route loss and barge-in still cancel through existing owners; new stage work is a child of that cancellation. No process-death speculative work is resumed. Native-call cancellation responsiveness and overlap regressions are unverified.

Owner acceptance: dependency failures, queue limits, supersession, partial revision/ambiguity, private context, changing settings during a turn, provider/route failure before and after audio, no repeated prefix/tool effect, resource contention and memory/thermal pressure. Next dependency: D connects evidence persistence, conservative preparation/adaptive consumption and manual diagnostics.

## Batch D — manual diagnostics and evidence consumption

**IMPLEMENTED — NOT BUILT — NOT TESTED — PHYSICAL ACCEPTANCE PENDING**

Published preceding batches: A `6abc36fdd43eab3909a444f261d6d7ffe1c77f3b`, B `d4332f3b7f3cfc188307fee9a7f1a22d5103293c`, C `e611d34306f2798d2557dfe65a374c24f1ae7245`. D's actual commit identifier is supplied in the final handoff/Git history.

Files grouped by module: app `ai/WorkloadLabController`, `WorkloadQualificationStore`, `WorkloadPreparationController`, semantic mutation serialization; app existing Intelligence settings plus `WorkloadDiagnosticsPanel`, workload policy controls, speech telemetry and routed playback instrumentation; EN/LV/RU localization catalogue/resources; LiteRT explicit metadata inspection, load/reuse action and temporary embedding-owner cleanup; AI router optional qualified automatic target; runtime cognition preparation callback; runtime SDK background resource admission; provider SDK pure adaptive ranking. The manual acceptance matrix is in `docs/testing/HALO-AI-133-MANUAL-ACCEPTANCE.md`.

The existing Intelligence advanced diagnostics now expose user-initiated artifact verification, native metadata inspection, fresh CPU initialization/load-or-reuse, short text output, existing multi-turn role suite, actual embedding, selected-source indexing and retrieval. Existing speech, audio, vision and tool diagnostics remain their specialized owners. Entry/model selection performs no probes or downloads. Cached installed/native/task-proof/resident/executing status remains separate. Private output/retrieval previews are transient and cleared on panel disposal; explicit safe report export uses an allowlist.

Qualification records are additive, bounded and versioned, binding exact artifact, runtime/epoch, coarse device/Android, backend, task, native tuning, workload profile and memory/concurrency configuration. Full existing role-suite correctness and warm stability are required; short fixture checks never create a task pass. Historical failures supersede older successes for the same identity. Both adaptive automatic-role consumption and selected-model speculation default off. They require current matching evidence, native compatibility, known headroom, normal pressure and acceptable thermal state. Explicit role/default choices remain unchanged. Background preparation uses the existing owner lock and fail-fast background lease; no automatic remote work or downloads.

Kokoro last-operation instrumentation separates real native initialization, reuse and cache hits, admission wait, first validated PCM, synthesis time, PCM duration and synthesis real-time factor. Existing routed playback records PCM readiness, first route-verified write callback and completion, without claiming acoustic audibility. Load/reuse duration is labeled loadOrReuseMs, not engineInitMs. Embedding reports actual init/generation/batch/dimension and separately labeled estimated memory. Missing measurements are NOT_MEASURED; no synthetic token throughput, GPU memory or operation-placement proof is exported. Stage timeline remains bounded and content-free. These are implemented observation points; none was executed during this assignment.

Preserved behavior: original role storage, cloud/local selection, specialized provider settings, installed packs/voice defaults, Android TTS fallback consent, wearable route proof, authoritative source documents, tool approvals, dispatcher/journals and DevKit navigation. No database schema migration, dependency upgrade, CI change or automated validation. Source localization generation only wrote existing catalogue/resource files.

Known source-level limitations: Supertonic pack acquisition/host qualification remains gated; INT8 remains unavailable pending independent qualification. Specialized voice policies still use their existing owners rather than every generic preset. Semantic context-segment APIs and manual retrieval are implemented, but automatic conversational insertion is not wired across private-context/remote-fallback ownership. Learned intent classification is optional/unbound. Persisted task-proof production currently consumes existing LLM role suites; specialized speech/STT/vision/embedding semantic qualification remains with owner evidence. True audibility/end-of-speech latency, acoustic chunk delays, aggregated fallback frequency and observed peak/GPU memory are not newly measured. No all-device performance claim or universal automatic calibration is made.

Source architecture assessment only: portable workload contracts/resolver/ranking/scheduler and semantic index are separated from Android/JNI/Compose/storage adapters; composition root supplies integration. Framework-independent rules and persistence/delivery boundaries are represented in source, but compilation, module dependency graphs and runtime replacement have not been verified. The application diagnostic controller still combines UI-oriented actions and concrete provider adapters; this is an outer-layer integration tradeoff, not a claim of fully decoupled calibration use cases.

Physical acceptance pending: every row of the owner matrix, including 4/6/8/high RAM, supported EN/LV/RU/PL, exact endpoint routing, resource cancellation, thermal pressure, index privacy/invalidation, process recovery and no repeated side effects. Next dependency: owner-authorized build/test and alpha qualification; do not treat this source handoff as release acceptance.

## Batch E — voice model choice and native residency consistency

**IMPLEMENTED — NOT BUILT — NOT TESTED — PHYSICAL ACCEPTANCE PENDING**

Source-only follow-up after A–D. The Kokoro screen exposed both the new multiple-version manager and the old multilingual-only download widget, obscuring the explicit English/multilingual choice. The version download actions now appear directly in the manager, with existing progress/Pause; the legacy duplicate download and removal actions are removed. Manual import, per-pack selection, voice switching, private PCM cache, audio routing and fallback remain present.

Manual imports now share acquisition serialization with publisher downloads and removals. The provider records the downloading state before acquisition. Changing the selected pack or disabling warm residency updates the native resource coordinator when the engine is torn down. Version changes between sentence chunks preserve the active response's pinned native engine; a new request uses the selected version. No native inference, compilation, unit testing, device test or performance comparison was run. Owner acceptance remains required for active-stream version switches, warm disable, imports/downloads, and playback routing.
