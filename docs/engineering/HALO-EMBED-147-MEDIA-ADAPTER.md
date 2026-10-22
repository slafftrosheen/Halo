# HALO-EMBED-147 / HALO-SEARCH-148 — Image/audio native adapters and Notebook permission hardening

2026-10-19 · Android `main` · Source implementation, **NOT device-qualified**.
**GitHub is storage only. No build, Gradle tests, GitHub Actions, CI or deployment were run.**

## New native boundary

The verified LiteRT-LM 0.18 Kotlin API supports `EmbeddingEngine.computeEmbedding(listOf(InputData.Image(bytes)))` and `InputData.Audio(bytes)`, with `visionBackend` and `audioBackend` in `EmbeddingEngineConfig`. Google publishes EmbeddingGemma 2 as three different variants:

| Variant | Upstream model capability | Consumer qualification in Halo |
| --- | --- | --- |
| Text 270M (~165 MB) | Text only | Not pinned in Halo catalog; do not offer as an unverified download |
| Text/Vision 440M (~388 MB) | Text + image | Not pinned in Halo catalog; native device proof pending |
| Full 740M (~485 MB) | Text + image + audio/video | Existing pinned catalog; text semantic search connected; image/audio native adapter source added; media indexing and video pending |

Sources:
- https://developers.google.com/edge/litert-lm/embedding_models
- https://developers.google.com/edge/litert-lm/overview
- https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2
- https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm
- https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm

Changes in `LiteRtEmbeddingProvider`:

- Shared, serialized native execution path for existing text `EmbeddingProvider.embed` and new explicit `embedMedia(LocalMediaEmbeddingRequest)`. Text search retains its original `EmbeddingSpace` identity and `EmbeddingOptions`; no remote or generator fallback.
- Bound image byte inputs (PNG/JPEG up to 4 MiB), WAV inputs (up to 8 MiB), maximum media batch 8, nonblank text, exact model SHA/dimension and a separate `embeddinggemma2-native-media-v1` preprocessing identity.
- Actual model metadata must say `ModelInfo.Embedding`, exact dimension, CPU text backend and, when needed, CPU VISION or AUDIO encoder backend. If CPU image/audio is unavailable it fails closed; no silent GPU promotion.
- Decode-bounds-only Android preflight rejects images larger than 4096px on any side / 16 megapixels before the JNI encoder. No original picture is saved by the native adapter.
- Same heavyweight LiteRT resource lease, memory/pressure guard, cancellation and native engine close/unregister/failed-teardown logic as text embeddings. Adds a conservative media encoder allocation reserve but does not claim measured memory.
- `LocalEmbeddingModalityPolicy` distinguishes publisher modalities, developer native adapter modalities, and consumer modalities. At the time of this original adapter audit, only TEXT was consumer-wired; later Notebook picture and video source progress is recorded below and remains unqualified on physical devices.

Developer-only qualification:

- `WorkloadLabController.comparePrivateImage` computes normalized local image/text embeddings in one native engine run, shows a bounded similarity score but neither exports photo nor vector.
- `WorkloadDiagnosticsPanel` (behind **Settings → Advanced → Developer tools**) presents an explicit system image picker only for selected installed inspected EMBED artifact and nonempty text query. Reads at most 4 MiB in a background coroutine. The picker returns a user-selected URI; its bytes do not enter telemetry.
- Similarity is **not** an image retrieval accuracy verdict, and CPU metadata compatibility is not proof that the image encoder can complete on a given physical phone.
- `LocalMediaEmbeddingSafetyTest` tests requests, limits and preprocessing separation in source only. Device testing remains required.

Notebook privacy/reliability (`HALO-SEARCH-148`):

- Re-selecting a different set of permitted Notebook/Canvas/Study sources revokes the separate `semantic_assistant_context_enabled` permission. This happens synchronously under the same mutation lock that prunes cached data; no implicit consent carryover.
- The Notebook sheet reloads this exact permission state after changing sources, so its switch does not falsely remain on.
- A failed/incomplete index is not presented as ready for search. `readyIndexSize()` checks current embedding model SHA/dimension/preprocessing space and active source revisions/permissions, rather than reporting stale raw chunk totals.
- The added regression assertions cover source-scope revocation and verified current-index count after opt-out.

## Still required before claiming multimodal user support

1. Physical LiteRT 0.18 SDK proof for exact 740M artifact: image encoding CPU path, WAV decoding, text–image similarity, cold/warm performance, resource eviction and cancellation on target phones.
2. Verify potential GPU vision encoder configurations *only* through measured, explicit qualification. CPU might be unavailable for particular artifact/profile.
3. Media source owners: explicit access to images/assets, byte-range secure loaders, size and duration limits, URI grants, lifecycle and on-device-only consent. No Gallery auto-scanning.
4. Cross-modal index schema: record modality, encoder/pipeline/preprocessing/version/dimension/model checksum and source ID/revision, and reject mixed space identities. Existing local text index must not be silently combined with the new raw media vectors.
5. Design Library/Canvas/Study image search and audio memo search results against DevKit. Make original media references clickable. Audio streaming/video frame sampling are not connected.
6. Optional variants 270M and 440M need immutable publisher revision, exact SHA/size and real device support before entry into the direct-download catalog. Do not fabricate hashes.
7. Alpha acceptance: two independent image subjects, similar and unrelated text, a WAV sample, a corrupted PNG, an oversized JPEG, URI revocation mid-run, a second active LiteRT LLM, low memory, Stop/cancel, background/foreground, and search-scope change while active.
8. Update reviewed EN/LV/RU and complete Polish UI localization before marking a four-language consumer launch.

Status: **native image/audio input adapters and one developer-only proof route coded; actual phone pass and cross-modal consumer search not complete**. Keep #35 open.

## Subsequent source progress — Notebook video-frame search

The current source adds an explicit Notebook video-attachment choice and a separately versioned `VIDEO_FRAME` index. The pinned Kotlin API still has no typed `InputData.Video`; Halo decodes a selected short clip locally and submits four interior frames as bounded JPEG `InputData.Image` values. The frame policy requests the decoded frame closest to each of four evenly spaced interior fifth-interval timestamps, resized to a 1024-pixel long edge at JPEG quality 82. Audio tracks are never read. The preprocessing identity is version 2 so vectors created under the older closest-sync policy cannot mix with this policy. The UI discloses the sampling behavior before the user enables local search.

Only active-note attachments with `video/mp4`, `video/webm`, `video/3gpp`, or `video/quicktime` are accepted. The clip is limited to two minutes, 24 MB, 1920 pixels per dimension / 4 MP, and four selected videos. Each JPEG is capped at 1 MiB. Source bytes are hashed with a 24 MB streaming cap, content URI grants and managed-file boundaries are checked, and active owner/selection/hash are rechecked after decoding and native embedding. No video or frame bytes are persisted; the atomic index stores only separate-space vectors, source hashes and pointers. Text queries use the retrieval prefix and are compared with the unit-normalized mean of the four frame vectors.

The new preprocessing identity encodes the frame count, sampling policy, output edge and JPEG quality. `wiredModalities` now includes VIDEO because the consumer source/index/search flow exists, while `nativeAdapterModalities` remains limited to TEXT/IMAGE/AUDIO because the SDK still receives frames as images. This does not claim direct native video input or device quality qualification. Source regression coverage was added for frame policy/aggregation, exact active-note video choices, separate index persistence and modality rejection; tests were **not run** under #43. No build, CI/Actions, native inference or physical validation was run. Keep #35 open for actual artifact/device accuracy and latency, Canvas/Study expansion, broader acceptance and remaining localization.

## Subsequent source progress — Canvas visual find

The Notebook source picker now offers individual PNG/JPEG image files referenced by image nodes on active Canvases. Listing reads only Canvas/node/file metadata; a file is opened only after its exact `canvas-image:<canvasId>:<nodeId>` source is selected and local search is enabled. Active Canvas/node/file ownership is rechecked. Halo-owned copies must remain under the managed workspace root; external content URIs require a persisted read grant. Compressed input is capped at 4 MiB and goes through the existing image decoder bounds checks and IMAGE-only vector index. The index stores hashes, vectors and source pointers, never file bytes; search results reopen the owning Canvas focused on the image node.

Regression coverage was authored for active-board PNG/JPEG query results, source-choice filtering, and Canvas source IDs in the separate image index; it was not run. Static verification passed for localization generation, XML parsing, and `git diff --check`. No build, test, CI/Actions or physical model/device validation was run. This advances Canvas visual find source coverage only; PDF/Study source expansion and physical cross-modal acceptance remain open.

The 64-choice picker limit now samples each source category in turn, preventing a large Notebook list from hiding every Study/media/Canvas choice. A regression case covers Canvas image availability beyond the first 64 workspace objects; it was authored but not run.
