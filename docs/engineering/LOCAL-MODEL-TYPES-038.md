# LOCAL-MODEL-TYPES-038 — Halo local AI workload contracts (Android first, runtime portable)

2026-10-13 | Source status: committed to `main` | Device/Gradle acceptance for this batch: **NOT RUN**

## Why a single “AI model” type is insufficient

LiteRT-LM 0.18.0 supports text generation, vision/audio-capable generators, and separately embedding models such as EmbeddingGemma 2. Sherpa/ONNX covers ASR, speech activity, keyword spotting, and TTS. Their download formats, native engines, lifetimes, language coverage and success criteria differ. None of these tasks is a substitute for another. In particular a text-capable embedding model is **not** a text generator.

This contract is based on the current Halo implementation and [LiteRT-LM v0.18.0](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.18.0); upstream research is not Halo physical test evidence.

| Task / type | Artifact/runtime owner now | User-facing entry | Actual status and next gate |
| --- | --- | --- | --- |
| Small text chat / FAST / reasoning | LiteRT `.litertlm`, `LiteRtLocalProvider` | On-device Models + roles | Generation probes and role suite; SHA/device/runtime/backend evidence separately. No role inferred from family |
| Tool-specialized models (FunctionGemma, Qwen, etc.) | LiteRT generation, Halo tool approval stays in runtime | Roles / tool probes | NATIVE only after per-artifact proof. HALO_PROMPT stays bounded; never auto-execute model-emitted commands |
| Vision-language / OCR reasoning | LiteRT model with **actually verified** vision encoder | Vision Lab | Verify text/backend first, then image encoder and synthetic visual evidence; neither source name nor file implies vision ready |
| Audio-language understanding | LiteRT model with **actually verified** audio encoder | Local Audio Lab | Verify audio modality, CPU audio executor, route, and actual audio-content reasoning. It is not STT or TTS by default |
| Multimodal embeddings | LiteRT-LM `.litertlm` / EmbeddingGemma 2 (0.18+) | On-device Models: Embeddings | **Download + metadata inspection only**; no embedding executor, vector index, or retrieval routing wired to Halo yet |
| Speech recognition | Sherpa ONNX Whisper Tiny/Base/Small, Omnilingual; Android STT | Speech settings / Speech Lab | Verify signed multi-file packages, language support (EN/RU/LV), start/end speech, recognizer quality, latency |
| Voice activity detection | Silero VAD / existing speech front end | Speech engine | Distinguish VAD hit-rate and false positives from STT transcription quality; no LiteRT chat model |
| Wake / keyword spotter | Sherpa KWS, separate `.onnx` and keywords | Speech setup | Measure sensitivity, false triggers, locale/phoneme constraints; no mandatory AI inference for simple commands |
| Speech synthesis | Kokoro via Sherpa ONNX; Android TTS; optional remote ElevenLabs | Voice output | Actual audible playback, PCM validity, voice/language coverage, interruption/route. Existing local Kokoro pack is EN/ZH, not Latvian/Russian |
| Local image generation / edit | Separate `WorkspaceImageService` provider routing | Image Models | Do not claim a downloaded LiteRT vision model generates images; no standalone local diffusion engine is qualified here |
| Non-`.litertlm` community models | Framework-specific import adapter required | N/A until adapter exists | GGUF, MediaPipe `.task`, ONNX speech, Safetensors and diffusion bundles must **not** be treated as LiteRT LM executables |

## New task-aware catalog boundary (LOCAL-MODEL-TYPES-027 through -037)

- `LiteRtArtifactTask`: `GENERATION`, `EMBEDDING`, `NON_GENERATIVE`. This is source-level **admission**, not native proof. Text/audio/image **input modalities** are orthogonal to the task.
- Hub search now discovers embedding/non-generation `.litertlm` files, retaining immutable revision, SHA and byte count. Known task tags that are not generation fail closed; existing Hub cache decodes safely. Accelerator-specific binaries and non-runnable 1-bit Bonsai are still excluded.
- `LiteRtGenerationAdmission` gates provider loads, selected targets, installed chat-model list, remote-model catalog integration, smoke tests, benchmark, role matrix, GPU/direct generation probes and imported-file user selection. A valid file can be safely stored **without** being exposed as a chat agent.
- Model Browser now filters `Embeddings / search` separately from text/vision/audio/agentic candidates; the non-generation cards have no `Use for conversation`, model GPU or role probes.
- `LocalArtifactInspectionTest` is explicit-only; it reports actual SDK model type, modalities, backend metadata, context, minimum runtime and Android memory around `ModelInfo.from()`. No Engine/Conversation is initialized, no model output or personal content is retained, and it never grants a capability.
- **Native truth wins:** even source-classified `GENERATION` files must still pass the `ModelInfo.Llm` preflight before engine init, and the corresponding provider/role physical checks.

## Pinned EmbeddingGemma 2 experimental artifact

Publisher: [litert-community/embeddinggemma-2-740m-litert-lm](https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm)

- Immutable revision: `86b903a293f96d042bb92e1b7af333d94114458f`
- Filename: `embeddinggemma-2-740m.litertlm`
- LFS SHA-256: `e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c`
- Size: 484,622,336 bytes
- LiteRT minimum in catalog: 0.18.0; model context reported upstream up to 8192, but actual metadata still requires inspection
- Artifact task: `EMBEDDING`; maturity: `EXPERIMENTAL`; zero text-generation/vision/agent capabilities advertised by Halo.
- Multi-modal embedding **as published upstream** supports a shared representation of text, image and audio and Matryoshka dimension changes; Halo has NOT implemented vector inference or semantic retrieval yet.

Evidence: [publisher's exact LFS upload](https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/commit/86b903a293f96d042bb92e1b7af333d94114458f).

## Device acceptance protocol (Windows / Android, no GitHub Actions)

1. Build all modules and both APKs; install debug app and `androidTest` APK on a physical phone. No Android tests were executed while writing this batch.
2. Open On-device Models, download/import model and verify pinned SHA. Avoid a bulk first-time inspection on a low-RAM phone.
3. For ANY installed `.litertlm` model, first inspect *only* metadata, even when it is an embedding/non-generation model:

```powershell
.\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Models embeddinggemma2-740m -InspectOnly
```

4. For a model which metadata identifies as an LLM generator, run CPU smoke and resident endurance separately:

```powershell
.\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Models qwen3-0.6b-dynamic-int4 -Backend CPU -Cycles 3 -ContextTokens 1024
.\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Models qwen3-0.6b-dynamic-int4 -Backend CPU -ResidentTurns 5 -ContextTokens 1024
```

5. Test actual multimodal inputs in Vision Lab/Local Audio Lab; independent test passes for vision and audio. Never mark a model multi-modal-ready solely from repo metadata.
6. Verify local speech outside LiteRT: Whisper STT in Latvian/Russian/English, VAD, wake-word, Kokoro PCM/audio route or Android TTS fallback. Confirm playback **audibly**, including headset/glasses.
7. For 4GB and 6GB phones, keep LiteRT AUTO CPU-first, short context experiments, serialized heavy compute; on 8GB permit GPU only after the **exact artifact/device/runtime/build** probe qualifies it.
8. Re-run after artifact SHA, Android build, native runtime version, audio route or language-model settings change.

Upstream warning: [LiteRT-LM #3878](https://github.com/google-ai-edge/LiteRT-LM/issues/3878) reports `ModelInfo.from()` copying large model sections into native memory, with high temporary RSS for big bundles. This is a reason to make metadata inspection **explicit**, never an automatic per-model loop on 4GB phones.

## Proposed independent embedding runtime (future work, NOT silently added now)

Add a portable `EmbeddingProvider` SDK contract distinct from `AiInferenceProvider`, carrying artifact SHA, input modality, vector dimension, quantization, backend, timestamp and bounded memory profile. Wire LiteRT embedding APIs only after pinning their exact 0.18.0 Kotlin signatures and passing a physical smoke test. Index Notebook/Canvas/Study text and selected media into an explicitly enabled local, deletable versioned index with content fingerprints and provenance. Enforce model/SHA/dimension compatibility; reject vectors from unrelated embedding spaces, invalid or NaN vectors, mismatched dimension truncation, and retrieval without permission. RAG and cross-modal search must keep documents local by default and preserve the existing consent/privacy boundaries. Do not merge this with the conversation LLM's native session lifecycle.

## Not claimed

There is **no** verified build/test run for LOCAL-MODEL-TYPES-027..038 in this session. `ModelInfo` introspection and all new Kotlin source require Gradle and physical phone acceptance. Neither embedding inference nor local diffusion generation has been implemented; the new EmbeddingGemma entry is an explicit artifact/download/metadata-admission step. External author measurements do not confer Halo model qualification.
