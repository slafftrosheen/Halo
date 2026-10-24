# #39: HALO-MODELS: verified catalog, curated packs and device-aware onboarding

Original: https://github.com/slafftrosheen/Halo/issues/39 · updated 2026-10-24T07:16:11Z. Full source is retained in source-issues.json.

No source/fixture pass automatically qualifies physical, spoken, provider, platform or store acceptance.

### I39-P01: Catalog truth

Inspect each displayed candidate against exact SHA/bytes/revision/format/quantization/provenance/license/source and actual native backend. Compare Compatible/Tested/Recommended/limitation badges by exact device, role and EN/RU/LV/PL.

**Pass evidence:** No runtime-init-only recommendation, unsupported format, English-only model advertised multilingual, or embedding advertised as generative vision. Update/runtime/artifact changes invalidate positive proof; negative quarantine is retained.

Status: NOT_TESTED. Evidence: pending.

### I39-P02: First-run/recovery

On an isolated tester profile/device, install a functional suggested pack without HF browsing; pause/resume/cancel, interrupt process, test low storage safely, rollback, switch/remove and restart. Include gated/access-denied/token/import errors with test fixtures.

**Pass evidence:** Exact-owned files and shared models survive correctly; no lost unrelated partials or stale receipts; clear recovery and honest space/support limitations. Initial benchmark does not download new models automatically.

Status: NOT_TESTED. Evidence: pending.

### I39-P03: Role/language proof

For each intended recommendation run exact installed role/language/backend fixtures, real STT/VAD and human audible TTS (all claimed languages), intent/tools/conversation, supported embedding/vision. Export source-bound evidence and recreate process before recommendation/apply.

**Pass evidence:** Fixtures and human quality agree; latest same-scope failures/clock rollback/ties supersede prior passes; package/install build identity and role assignments match. Interrupted/unsupported stays NOT_TESTED/UNSUPPORTED.

Status: NOT_TESTED. Evidence: pending.

## Requirement-by-requirement review

- [ ] **I39-R01** (original line 5, Catalog manifest and qualification): - Exact artifact/model SHA, format, quantization, version, provenance/license, source URL, checksum, RAM/storage and actual LiteRT backend support.
- [ ] **I39-R02** (original line 6, Catalog manifest and qualification): - Per-role and per-language qualification (STT/VAD/TTS/intent/tools/conversation/embedding/vision), device profile and reproducible test metadata.
- [ ] **I39-R03** (original line 7, Catalog manifest and qualification): - Separate statuses Compatible, Tested on named hardware, Recommended for role/language, Known limitation; invalidate/requalify on runtime/package changes.
- [ ] **I39-R04** (original line 8, Catalog manifest and qualification): - Seed CANDIDATES pending validation: Qwen3 0.6B intent; Whisper Small STT; VAD; Supertonic default multilingual TTS (user confirms four languages), Kokoro 1.1 alternative; LFM fast EN chat (user reports poor non-EN); EmbeddingGemma for verified text retrieval, multimodal only if native artifact support proven.
- [ ] **I39-R05** (original line 9, Catalog manifest and qualification): - One-click onboarding packs based on device capability/space, pause/resume/verify downloads, cancel/rollback, change model/role, remove, recover after process death. Avoid universal 12GB guarantee; weaker devices can pair local speech/intent with cloud model.
- [ ] **I39-R06** (original line 10, Catalog manifest and qualification): - Friendly UI explains why a model is suggested; don't claim support of Polish or generative vision without proof.
- [ ] **I39-R07** (original line 16, Implementation discipline): - Start from current main, inspect existing components and BrandBook/DevKit, reuse runtime and Compose; no global rewrites.
- [ ] **I39-R08** (original line 17, Implementation discipline): - Keep internal traces/experimental model settings in Advanced → Development.
- [ ] **I39-R09** (original line 18, Implementation discipline): - Code-only batches can commit to main; do not run builds/tests/CI/Actions during coding. Physical validation is owner/alpha tester work; never claim phone-tested from source review.
- [ ] **I39-R10** (original line 19, Implementation discipline): - EN/RU/LV/PL copy subject to actual language support; accessibility and privacy are requirements.
- [ ] **I39-R11** (original line 12, Acceptance): A new user installs functional role pack without technical HF browsing, unsupported formats are rejected honestly, and English-only quality warnings prevent invalid local recommendations.
