# #35: HALO-EMBED-144: native EmbeddingGemma 2 multimodal retrieval and consumer integration

Original: https://github.com/slafftrosheen/Halo/issues/35 · updated 2026-10-23T23:41:12Z. Full source is retained in source-issues.json.

No source/fixture pass automatically qualifies physical, spoken, provider, platform or store acceptance.

### I35-P01: Native capability

For the exact installed supported SHA/runtime, inspect native embedding task/modalities/backend/encoder packaging before allocation. Test invalid/imported/unsupported modality/backend requests and cancellation during admission/close.

**Pass evidence:** Only actual supported native combinations allocate; unknown/imported capability stays untrusted; text-only fallback is honest; resource/physical-artifact leases release on cancellation and failed close reconciles.

Status: NOT_TESTED. Evidence: pending.

### I35-P02: Multimodal quality

Prelabel positive/negative text-image, image-image, text-audio and text-video fixtures. Run supported native requests on exact bytes and report similarity ranking, latency/RSS, cold/warm behavior and low-memory/model-switch cases.

**Pass evidence:** Expected positives beat declared hard negatives; report actual quality and failures. Unsupported inputs remain explicitly unqualified, never generative vision or a fabricated vector. Raw input never silently uploads.

Status: NOT_TESTED. Evidence: pending.

### I35-P03: Index/consumer

Opt in a local working set; exercise Notebook picture search, Canvas visual find, Study retrieval and voice-memo search. Change encoder/SHA/dimension/normalization/preprocessing/frame policy; reindex, delete/revoke source and retry.

**Pass evidence:** Incompatible spaces never mix; source permissions/deletion are honored; safe migration/reindex and bounded decoders/frames. Consumer flows have genuine retrieval evidence, not only a diagnostic engine pass.

Status: NOT_TESTED. Evidence: pending.

### I35-P04: Privacy/languages

Repeat offline, permission denial and interruption; inspect EN/RU/LV/PL copy and phone/tablet/large-text UI.

**Pass evidence:** No cloud fallback or unsupported language/vision promise; clear local-search benefits and truthful limitations. Metrics stay in Advanced.

Status: NOT_TESTED. Evidence: pending.

## Requirement-by-requirement review

- [ ] **I35-R01** (original line 9, Execution batches): - [ ] **A — Runtime discovery:** inspect exact `ModelInfo.Embedding` modalities, supported backends, encoder packaging and `EmbeddingEngine` image/audio/video input APIs in the Android runtime actually pinned by Halo. Reject unsupported modality/backend combinations before native allocation. Keep imported/unknown artifacts untrusted.
- [ ] **I35-R02** (original line 10, Execution batches): - [ ] **B — Typed payloads:** add local source-scoped image, audio and sampled video-frame embedding requests, permissions, decoders, size limits, cancellation and native memory leases. No raw cloud fallback; no silent content uploads. Support text-only mode on smaller phones.
- [ ] **I35-R03** (original line 11, Execution batches): - [ ] **C — Index correctness:** version modality/preprocessing/encoder identities with SHA, dimension, normalization, crop/sample/frame policy and index schema. Do not mix incompatible spaces; support safe migration/reindex and source deletion/revocation.
- [ ] **I35-R04** (original line 12, Execution batches): - [ ] **D — Consumer flows:** Notebook image/picture semantic search, Canvas visual find, Study source retrieval and voice memo search through the same explicit opt-in local working set. UI uses user benefits, not vector/debug jargon; move probes/metrics into Developer Tools.
- [ ] **I35-R05** (original line 13, Execution batches): - [ ] **E — Physical acceptance:** verify real cross-modal similarity on pinned artifacts: text→image, image→image, text→audio, text→video, accuracy/latency/memory, low RAM, cancellation, model switching, permissions and offline mode. Local-only guarantees documented.
- [ ] **I35-R06** (original line 14, Execution batches): - [ ] **F — Localization:** reviewed EN/RU/LV/PL product copy where supported; do not mark Polish UI complete until full localization exists.
