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

`LocalEmbeddingModalityPolicy` is stricter than the native API: TEXT is the only consumer-wired modality. IMAGE and AUDIO have adapter source but no source-index/search workflow or physical proof. VIDEO is publisher metadata only. Unknown imported models do not inherit EmbeddingGemma catalog capabilities, and generation-model `supportsVision` stays false.

## Batch A outcome and remaining work

Runtime discovery is complete for the exact pinned library: the API supports text, image, and audio embedding inputs, with per-modality backend metadata, but has no direct video input type. Halo preflights requested image/audio capabilities and backend compatibility before embedding-engine construction. `ModelInfo.from` itself is the SDK metadata-inspection call and may allocate its own native metadata handle; this audit makes no claim that it is allocation-free.

This closes only the A discovery record. The `embedMedia` executor contract still needs review against full source-scoped permissions, model provenance, decoder correctness, index schema, consumer flows, physical artifact qualification, and localization in batches B–F. Do not expose image/audio/video search to users or mark #35 complete from this audit.

## Evidence

- Local resolved artifact: Gradle cache `litertlm-android-0.18.0.aar`; SHA-256 above.
- API surface inspected from `classes.jar` using `javap` on the cached AAR, including `EmbeddingEngine`, `EmbeddingEngineConfig`, `InputData`, `ModelInfo.Embedding`, `ModelInfo`, `Modality`, and `SupportedModalities`.
- Halo source: `providers/litert/src/main/java/com/halo/providers/litert/LiteRtEmbeddingProvider.kt`, `providers/litert/src/main/java/com/halo/providers/litert/models/LocalEmbeddingModalityPolicy.kt`, and `providers/litert/src/main/java/com/halo/providers/litert/models/LiteRtModelCatalog.kt`.
- Upstream release note: [LiteRT-LM v0.18.0](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.18.0).
