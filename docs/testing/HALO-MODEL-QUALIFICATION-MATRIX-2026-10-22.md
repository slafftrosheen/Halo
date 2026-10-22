# Halo starter model artifact and qualification matrix — 2026-10-22

**Artifact identity baseline:** `6214f80236ff83649938385528ab36de2dec4a9f` (`main`).
**Status:** catalog identity inventory; qualification is NOT TESTED for release.
This document distinguishes declared metadata and installation integrity from
native compatibility, user-facing quality, and device/language qualification.

## Starter bundle artifacts

| Role | Exact source identity | Format / quantization | Runtime and backend evidence | License/provenance evidence | Qualification status |
| --- | --- | --- | --- | --- | --- |
| Intent candidate | `litert-community/Qwen3-0.6B@a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76` / `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` / 344,671,744 bytes / SHA-256 `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856` | LiteRT-LM; `dynamic_wi4b32_afp32`; 4,096-token declared context | Minimum LiteRT-LM `0.17.1`; catalog profile AUTO with CPU fallback. No release device/backend matrix is attached to this inventory. | Immutable Hugging Face revision and file SHA are in `LiteRtModelCatalog`. `license` is unset in the catalog model; upstream license review is required before making a distribution claim. | Intent-only candidate. Not a conversational answer model, not selected by setup, and no current EN/RU/LV/PL intent quality matrix is recorded here. |
| Speech recognition | `csukuangfj/sherpa-onnx-whisper-small@8f3c18b358db4d1f2fc1eae49d75cd20989e4309` / `small-encoder.int8.onnx` 112,442,483 bytes SHA-256 `4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9`; `small-decoder.int8.onnx` 262,226,114 bytes SHA-256 `acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee`; `small-tokens.txt` 816,730 bytes SHA-256 `b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126` | ONNX INT8 encoder/decoder plus tokens; total 375,485,327 bytes | Sherpa STT owner; source does not record a per-device backend or performance qualification in this matrix. | Immutable Hugging Face revision, per-file byte sizes and SHA values are in `SherpaSpeechModelCatalog`. A model license is not encoded in `ModelSpec`; upstream license review remains required. | Catalog declares the Whisper language tag set, including EN/LV/RU/PL; this is not measured recognition quality. Device, cold/warm latency, memory, and language/action quality are NOT TESTED here. |
| Voice activity detection | `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx` / `silero_vad.onnx` 643,854 bytes / SHA-256 `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6` | ONNX; quantization is not declared in the Halo source entry | Sherpa VAD owner; no device/backend or voice-activity quality result is attached to this matrix. | Release URL and SHA are in `SherpaSpeechModelCatalog`; immutable release revision and license are not encoded there. Verify the upstream license and provenance before distribution claims. | Optional VAD candidate; install integrity is distinct from detection quality. NOT TESTED across device, noise, language, or microphone routes. |
| Speech output | Supertone Supertonic 3 INT8 official archive: `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2` / 128,774,318 bytes / archive SHA-256 `82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427`. Expected members: `duration_predictor.int8.onnx`, `text_encoder.int8.onnx`, `vector_estimator.int8.onnx`, `vocoder.int8.onnx`, `tts.json`, `unicode_indexer.bin`, `voice.bin`. | ONNX INT8 pack; archive SHA and package manifest checks | Sherpa ONNX `1.13.8`; source records package runtime but no device/backend performance matrix here. | `SpeechPackRegistry` labels licensing `pack-specific`, not a verified per-file grant. Confirm the upstream license/redistribution terms for the archive and contained assets. | EN/LV/RU/PL are declared package languages and voice IDs exist. No audible voice/language quality or routing qualification is recorded here; the installer does not select a voice. |

## Qualification matrix

| Artifact / role | EN | LV | RU | PL | Device/backend | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Qwen3 intent | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | No current role matrix for a named device/backend is attached here. | Candidate only; not a conversation recommendation. |
| Whisper Small STT | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | No current recognition/latency/memory matrix for a named device is attached here. | Language tags are declarations, not quality proof. |
| Silero VAD | N/A | N/A | N/A | N/A | No current microphone/noise/device matrix is attached here. | Language-independent component; end-to-end voice-path acceptance is NOT TESTED. |
| Supertonic 3 TTS | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | No current audible output/backend matrix for a named device is attached here. | Do not select or recommend before user audition and qualification. |

## Invalidation and evidence rules

- Artifact changes to repository, immutable revision, filename/member set, byte
  count, SHA-256, format, quantization, role, declared languages, or access/license
  metadata must invalidate the corresponding qualification and require review.
  Automated invalidation across every speech/embedding role is not proven by this
  source inventory and remains an implementation/owner audit item.
- Runtime version, native package, backend, device identity, Android version,
  execution settings, concurrency policy, or qualification-suite version changes
  invalidate environment-specific evidence. The app's workload qualification
  identity already includes artifact SHA, LiteRT-LM runtime, device fingerprint,
  Android API and execution settings for recorded LiteRT workload proofs; it does
  not create speech quality evidence for the artifacts above.
- `VERIFIED` file integrity, successful download, engine load, declared language
  tags, or candidate installation must never be relabeled as `TESTED` or
  `RECOMMENDED` for a role, language, backend, or device.
- The starter bundle does not include a conversational LLM. It installs acquisition
  candidates for intent and speech; it neither selects them nor establishes a
  complete offline conversation profile.

## Upstream license and provenance audit — 2026-10-22

This is an artifact-level source review, not legal advice or a release clearance.
The license of a runtime repository does not automatically grant rights to the
model weights it hosts or converts. All candidates stay unqualified for
redistribution until the exact downloaded artifact lineage and required notices
are reviewed.

| Artifact | Official upstream evidence | Finding for the pinned Halo artifact | Release follow-up |
| --- | --- | --- | --- |
| Qwen3 0.6B LiteRT-LM | The pinned [`litert-community/Qwen3-0.6B` model page](https://huggingface.co/litert-community/Qwen3-0.6B/tree/a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76) declares Apache-2.0. | The immutable catalog revision and `.litertlm` SHA identify the exact LiteRT conversion; the publisher page supplies a license declaration. Halo does not bundle the weights in the APK. | Keep the model page/license notice reachable in the acquisition flow and verify the packaged conversion still carries the applicable upstream notices before any bundled redistribution. |
| Whisper Small ONNX | OpenAI says its Whisper code and model weights are released under MIT in the [project README](https://github.com/openai/whisper) and [license](https://github.com/openai/whisper/blob/main/LICENSE). The exact pinned [Sherpa ONNX model repository revision](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/tree/8f3c18b358db4d1f2fc1eae49d75cd20989e4309) exposes the encoder, decoder and token files but no license file or license metadata. | MIT is verified for OpenAI's original Whisper weights. The pinned Sherpa conversion repository does not state its own model-export license or explicitly map these three files to a specific OpenAI source checkpoint. Do not treat Sherpa's Apache-2.0 code license as proof that this converted model package is cleared. | Obtain and retain confirmation of the conversion lineage and applicable model license for the exact ONNX/token files, plus the notices required for redistribution. |
| Silero VAD ONNX | The [Silero VAD project](https://github.com/snakers4/silero-vad) publishes the model under MIT and links its [license text](https://github.com/snakers4/silero-vad/blob/master/LICENSE). Sherpa documents the model's [release download](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx). | Halo pins the downloaded bytes by SHA-256, but the Sherpa `asr-models` asset URL does not pin an immutable release revision or identify the exact source export commit. The MIT declaration is upstream evidence, not proof of the export chain. | Confirm that the exact Sherpa ONNX file is covered by Silero's MIT grant and preserve its notice; record immutable source/export provenance alongside the SHA. |
| Supertonic 3 INT8 Sherpa archive | Supertone's [Supertonic 3 model page](https://huggingface.co/Supertone/supertonic-3) declares OpenRAIL-M for model weights; its [license file](https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE) distinguishes model terms from the MIT-licensed sample code. The model metadata currently resolves to revision `3cadd1ee6394adea1bd021217a0e650ede09a323`. | The Halo archive hash pins the Sherpa-hosted tarball, but the pack record does not pin a Supertone model revision or provide a file-by-file mapping from that archive to the upstream weights. OpenRAIL-M includes use-based restrictions and additional distribution conditions; it is not interchangeable with the upstream code's MIT license. | Obtain the Sherpa pack's source revision/quantization provenance and verify the applicable OpenRAIL-M obligations and required user notices for the exact archive before distribution or recommendation. |

### Audit outcome

- Qwen has a declared upstream Apache-2.0 license at the pinned publisher revision.
- Silero's upstream project declares MIT, but the exact Sherpa ONNX export lineage still needs confirmation.
- OpenAI's original Whisper weights have an MIT license, while the exact pinned Sherpa conversion package has no license declaration or source-checkpoint mapping. This remains unresolved for the packaged conversion.
- Supertonic model weights are OpenRAIL-M; the Sherpa archive's exact upstream revision and derivative mapping remain unresolved. The MIT code license does not clear these weights.
- The Whisper conversion and Supertonic repackaging are release-blocking provenance questions. No artifact here is approved for bundling or Play distribution by this source audit.
- Preserve upstream license files/notices and expose applicable use restrictions before acquisition. A reviewer must approve the final distribution and user-notice plan; this table is not that approval.

## Remaining acceptance

The catalog/source now records the upstream license evidence and unresolved
conversion provenance above, but this is not legal or release clearance. Current
reproducible device/runtime/role/language runs for the full bundle are also absent.
Owner verification must attach exact app/runtime revision, device/RAM,
artifact SHA, backend, language, test-set version, cold/warm timings, memory,
audibility/recognition result, and non-sensitive failure evidence. No build, test,
native inference, download, or physical acceptance was run to create this matrix.
Keep issue #39 open until those proofs and fresh-install/recovery acceptance exist.
