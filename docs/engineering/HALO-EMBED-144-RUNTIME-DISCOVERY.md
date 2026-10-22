# HALO-EMBED-144 — LiteRT-LM runtime discovery

2026-10-22 · Android `main` · Source/API audit only; no device qualification.

## Exact pinned runtime and artifact

- Gradle pins `com.google.ai.edge.litertlm:litertlm-android:0.18.0`.
- The locally resolved AAR SHA-256 was `706cbb8a87739b2818111bf3a19a78b8f7781586d10f80adf3415581fab7595f`.
- The only pinned EmbeddingGemma 2 artifact is `litert-community/embeddinggemma-2-740m-litert-lm`, revision `86b903a293f96d042bb92e1b7af333d94114458f`, 484,622,336 bytes, SHA-256 `e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c`.

## Kotlin API found in the resolved AAR

`ModelInfo.from(path)` exposes `inputModalities()`, `supportedBackends(Modality)`, `minRuntimeVersion()`, and model type. `ModelInfo.Embedding` additionally exposes dimension and signature selection. `Modality` contains TEXT, VISION, AUDIO, and VIDEO; `SupportedModalities` also reports a video flag.

The typed embedding input hierarchy in this AAR contains only `InputData.Text`, `InputData.Image`, and `InputData.Audio`. `EmbeddingEngine.computeEmbedding(List<InputData>, EmbeddingOptions)` and batch variants are present. `EmbeddingEngineConfig` has separate main, vision, and audio backend fields. There is no `InputData.Video` or video-specific embedding input in this pinned Kotlin API. A VIDEO metadata flag therefore does not provide a video embedding call; sampled frames would need a separately designed and versioned image pipeline.

## Existing Halo boundary

`LiteRtEmbeddingProvider` already has text execution and a separate bounded image/WAV `embedMedia` adapter. It checks the installed artifact SHA, embedding task, dimension, and CPU backend support for the requested modality before constructing `EmbeddingEngine`. Image bytes are limited to 4 MiB and 4096 px / 16 MP; WAV bytes are limited to 8 MiB. Requests use a media-specific preprocessing identity, share the native resource lease, and support coroutine cancellation and cleanup. The adapter does not read user files, alter the index, or upload content.

`LocalEmbeddingModalityPolicy` is stricter than the native API: TEXT, IMAGE and AUDIO have consumer source/index/search paths, but image and audio retrieval still lack physical qualification. VIDEO is publisher metadata only. Unknown imported models do not inherit EmbeddingGemma catalog capabilities, and generation-model `supportsVision` stays false.

## Batch A outcome and remaining work

Runtime discovery is complete for the exact pinned library: the API supports text, image, and audio embedding inputs, with per-modality backend metadata, but has no direct video input type. Halo preflights requested image/audio capabilities and backend compatibility before embedding-engine construction. `ModelInfo.from` itself is the SDK metadata-inspection call and may allocate its own native metadata handle; this audit makes no claim that it is allocation-free.

This closes only the A discovery record. Source progress for the bounded Notebook picture-search path is recorded below. Audio/video retrieval, wider consumer integration, physical artifact qualification, and the remaining localization/acceptance matrix are still open; do not mark #35 complete from this audit.

## Source progress — explicit Notebook picture search

At source revision `5670ef36`, the Notebook local-search sheet offers individually selectable image attachments from active notes. Listing reads attachment metadata only; image bytes are opened only for selected `attachment:<noteId>:<attachmentId>` IDs after checking the allowlist, active note, MIME, storage boundary, and persisted content permission.

Picture vectors use a separate atomic IMAGE-only file (`image-index-v1.json`) with pinned model SHA, dimension, normalization, LiteRT runtime, preprocessing identity, and content hash. It stores vectors and source pointers, never image bytes. Text and IMAGE spaces remain separate. Image inputs are bounded to PNG/JPEG, 4 MiB, 4096 px and 16 MP; the provider rechecks current artifact identity, CPU/VISION support, memory admission, cancellation and native lease. The service rechecks the selected attachment and content hash after embedding and again before returning search results. Revoking search consent or removing the source clears its vector entry.

At source revision `f1c8b7e5`, the same explicit Notebook source scope also includes audio attachments on active notes. Selected recordings are decoded locally through the bounded memo decoder, normalized to mono 16 kHz PCM/WAV, and limited to two minutes and 12 MB compressed input; at most eight audio sources may be selected for indexing. Audio vectors use a separate atomic AUDIO-only index, store only source hashes and vectors, and recheck attachment permissions and source hashes around native work and retrieval. No recording bytes or transcript are persisted by this search path. Text queries use `task: search result | query: ...`; image and audio candidates are embedded without text prefixes, following the [EmbeddingGemma 2 model card](https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2). Unsupported image/audio encoder combinations return text-only recovery guidance. Video remains unsupported by the pinned Kotlin input API.

Static localization generation/check and `git diff --check` passed. Source regression cases cover exact image/audio source selection, separate IMAGE/AUDIO index persistence, modality-space rejection, media request identity, and defensive input snapshots; tests were authored but not run under #43. No build, test, CI/Actions, emulator, native inference, or device validation was run. This is source progress, not physical qualification.

## Evidence

- Local resolved artifact: Gradle cache `litertlm-android-0.18.0.aar`; SHA-256 above.
- API surface inspected from `classes.jar` using `javap` on the cached AAR, including `EmbeddingEngine`, `EmbeddingEngineConfig`, `InputData`, `ModelInfo.Embedding`, `ModelInfo`, `Modality`, and `SupportedModalities`.
- Halo source: `providers/litert/src/main/java/com/halo/providers/litert/LiteRtEmbeddingProvider.kt`, `providers/litert/src/main/java/com/halo/providers/litert/models/LocalEmbeddingModalityPolicy.kt`, and `providers/litert/src/main/java/com/halo/providers/litert/models/LiteRtModelCatalog.kt`.
- Upstream release note: [LiteRT-LM v0.18.0](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.18.0).
