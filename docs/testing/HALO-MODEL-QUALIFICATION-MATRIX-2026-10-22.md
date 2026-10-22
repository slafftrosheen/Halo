# Halo starter model artifact and qualification matrix — 2026-10-22

**Source baseline:** `6214f80236ff83649938385528ab36de2dec4a9f` (`main`).
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

## Remaining acceptance

The catalog and source do not contain a complete upstream license audit for these
artifacts, nor current reproducible device/runtime/role/language runs for the full
bundle. Owner verification must attach exact app/runtime revision, device/RAM,
artifact SHA, backend, language, test-set version, cold/warm timings, memory,
audibility/recognition result, and non-sensitive failure evidence. No build, test,
native inference, download, or physical acceptance was run to create this matrix.
Keep issue #39 open until those proofs and fresh-install/recovery acceptance exist.
