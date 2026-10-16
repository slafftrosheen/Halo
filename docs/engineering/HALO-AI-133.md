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
