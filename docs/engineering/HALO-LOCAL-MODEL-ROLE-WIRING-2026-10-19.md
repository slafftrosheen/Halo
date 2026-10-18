# HALO local model roles, vision, TTS and embeddings — implementation audit

Date: 2026-10-19. Branch: `main`.
Source of truth: `halo-devkit-v1.4/IMPLEMENTATION.md`, BrandBook v2.3 Round 2.
Issue: https://github.com/slafftrosheen/Halo/issues/33

## Actual provider and role ownership

| Runtime duty | Implementation | Selection and evidence | Limits |
| --- | --- | --- | --- |
| Wake + VAD | Existing native speech capture/detector settings | Speech runtime, not an LLM role | No automatic cloud, classifier optional |
| STT | Reloadable speech recognition provider, Sherpa/Android path | Speech Input + `SpeechProviderSelector.sttSelection` | Per-provider recognition and languages must be physically tested |
| Partial intent / native action | Deterministic command path and advisory classifier | Runtime intent/action policy, NEVER execute tool on provisional STT text | No separate neural intent model required |
| FAST / CHAT / THINK / ACTION | LiteRT/remote `AiRoleBinding` and `AiProviderRouter` | Explicit capability, model and provider evidence | Tool calls remain permission-gated; THINK depends on proven support |
| VISION | LiteRT .litertlm VLM, or verified remote image-capable model | Actual image input modality, synthetic recognition probe, privacy policy, pinned artifact SHA and LiteRT version | LiteRT image encoder requires GPU, but text decoder may be CPU or GPU |
| EMBED | Standalone LiteRT `EmbeddingEngine`, text / normalized vectors | Explicit local EMBED model + native embedding dimension, SHA and CPU capability; opted-in selected workspace source IDs | Current Android adapter is CPU text-only; image/audio embeddings NOT wired |
| TTS Android | `AndroidTTSProvider`, common PCM routing | Explicit provider selector / default | Device voice availability varies |
| Kokoro | `KokoroSpeechProvider` using Sherpa ONNX CPU | Explicit version, voice, language and warm settings; pack+voice+speed+thread count pinned for response | Current published packs EN / EN+ZH; Latvian/Russian/Polish are NOT Kokoro capabilities |
| Supertonic 3 | `SupertonicSpeechProvider`, Sherpa ONNX INT8 CPU, official archive/import | Explicit pack and voice/language approval; shared PCM output | Official model supports 31 languages, but consumer preview UI focuses EN/LV/RU/PL; real audio still needs phone proof |
| Tool/retrieval use | Existing Notebook, Canvas, Study structured tools + `WorkspaceSemanticService` | Tool approvals and explicit local semantic source consent remain separate | Embedding returns excerpts, never edits files or authorizes actions |

## Corrected local vision onboarding (including imported .litertlm)

1. **Install/import**: catalogue artifacts remain publisher-verified by pinned bytes/SHA. Arbitrary `.litertlm` imports remain explicitly `UNVERIFIED` provenance even when their physical SHA is known; do not call them catalogue-verified.
2. **Inspect native task**: in Workload diagnostics select imported artifact, explicitly run `Inspect native task metadata`. It queries actual LiteRT `ModelInfo.Llm.inputModalities()`; for unverified imports only, a SHA-rechecked sidecar capability flag is reconciled with native image capability. No model generation/probe/benchmark runs on screen entry.
3. **Vision Lab**: an image-capable but previously unqualified VLM **is allowed to run the explicit random-token visual probe**. It is not allowed to process ordinary/private images until successful proof. The candidate must really read the synthetic image and return the expected text, with no tools.
4. **Persist exact proof**: successful local probe is associated with artifact fingerprint and `litertlm-<version>:halo-image-v1`. `LiteRtLocalProvider.listModels()` restores proof after process restart. Physical checksum comparison occurs before accepting new proof.
5. **Backend truth**: LiteRT preflight requires the image encoder on GPU, independently of the text-generation backend. A CPU text decoder with GPU image encoder is valid; text backend `CPU` is not evidence of CPU image inference.
6. **Privacy**: user image bytes are prepared once through `ImageInputPreparer`, bounded by existing policies, and transmitted only to the selected target if its capability and local/cloud source policy permit. The random diagnostic image never counts as a user-image approval.
7. **Cold load**: explicit local VLM probes permit up to 420 seconds rather than prematurely failing known slow cold starts; cloud probes retain 60 seconds. Timeouts are not proof that a blocking native engine has physically stopped.

## Kokoro / Supertonic ownership

The provider factory registers both PCM providers with the same Android speech selection registry. The generic output coordinator owns route selection, audio focus, foreground lease, stop/barge-in, audio device and `TtsStarted` evidence. The provider must only synthesize PCM. Supertonic pins model fingerprint, selected language, speaker ID, CPU threads, steps and rate into the request and requires explicit audible approval before regular output. Kokoro now additionally pins CPU thread count alongside version/voice/language/speed for sentence-continuous output. Runtime settings changes do not retune an in-flight response.

Supertonic upstream references:
- https://github.com/k2-fsa/sherpa/blob/master/docs/source/onnx/tts/supertonic.rst
- https://github.com/k2-fsa/sherpa/blob/master/docs/source/onnx/javascript-api/examples/tts_supertonic.rst

The developer-facing claim of 31 Supertonic languages comes from Sherpa documentation, **not** 31 physically verified Halo phone tests. Kokoro's EN/EN+ZH pack inventory is distinct. Version, pronunciation, target audio route and non-English speech quality remain alpha verification tasks.

## Embedding -> private retrieval -> local chat

1. Explicitly inspect an installed embedding artifact's actual LiteRT `ModelInfo.Embedding` task, native CPU compatibility and vector dimension. Manual import can be inspected without changing its provenance.
2. In Workload diagnostics, choose **EMBED** and explicitly select the artifact as the embedding role. Persist its SHA-256 and native dimension; this does not change CHAT/FAST/THINK/VISION assignments.
3. Separately enable semantic indexing and choose exact Notebook, Canvas, Study or plain-text attachment IDs; run the explicit `Index selected local sources` action with the selected EMBED model/dimension. There is no automatic discovery of private files and no background full-device scrape.
4. On a **fresh tool-free on-device** conversational turn, if both semantic opt-in and EMBED binding exist, Halo attempts a bounded local query before speculative LLM preparation. Retrieval checks per-source permission/version at query time, and returns at most three small untrusted reference excerpts.
5. If attached, remote/provider fallback is disabled for that turn. Excerpts are treated as untrusted reference data, not system prompts, user authorization, tool commands or durable memories. Retrieval unavailable, corrupted index, memory-pressure failure or timeout means no private snippets; ordinary on-device conversation continues.
6. The vector-space identity includes model SHA, vector dimension, runtime, normalization and preprocessing. EmbeddingGemma 2 document/query prefixes are applied asymmetrically (`task: search result | text:` versus `task: search query | text:`) under new `embeddinggemma2-search-asymmetric-v1` index identity. Legacy/plain-text vectors cannot be silently reused as compatible. Other embedding models retain existing plain text processing unless explicitly qualified.
7. Physical model artifacts, native resources, Room state and local semantic index remain on device. No Cloud API embeddings or automatic model downloads were added.

LiteRT EmbeddingGemma 2 instruction reference: https://developers.google.com/edge/litert-lm/embedding_models (documentation dated 2026-10-06).

## Tests and acceptance limits

**No Gradle build, unit test, instrumentation, native model run, CI or deployment was performed in this source-only batch.** Tests were authored for first-probe local vision qualification and native vector-space preprocessing; they have not been executed.

Before closing issue #33, alpha testers need to verify:
- On-device VLM installed from catalogue AND imported manually, native inspection, Vision Lab success/failure, actual pixel interpretation, CPU-text/GPU-vision backend report, restart after proof, no image leakage to unapproved cloud.
- Supertonic EN/LV/RU/PL with 10 voices, version/voice switch during speaking, barge-in, resume, audio route disconnect, low memory; repeat Kokoro EN versus EN+ZH including pinned threads, accurate speech speed and pack removal.
- EMBED native task resolution and dimensions, explicit local index build, selected source removal, incremental refresh, EmbeddingGemma 2 asymmetric vector reuse protection, query before LLM prewarm, bounded fallback on OOM/timeouts, private context never arriving at a remote provider.
- All FAST/CHAT/THINK/ACTION/VISION/STT/EMBED/TTS role selections across provider/model switch, incompatible backend rejection, no silent modality promotion, multilingual settings and actual physical readiness.
- Full app compile, source regression suites and screen-level BrandBook compliance on real phones.

**Not implemented/verified by this batch:** arbitrary CPU image inference (LiteRT native VLM image encoding still needs GPU), multimodal image/audio embedding operations, automatic first-run model pack selection, complete 31-language speech playback validation, device-qualified CPU/GPU speed improvements, universal GPU/NPU delegate support or production-grade semantic relevance ranking. Do not describe those as completed.
