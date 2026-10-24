# #33: Halo runtime: parallel voice pipeline, CPU/GPU orchestration and model residency

Original: https://github.com/slafftrosheen/Halo/issues/33 · updated 2026-10-24T03:03:00Z. Full source is retained in source-issues.json.

No source/fixture pass automatically qualifies physical, spoken, provider, platform or store acceptance.

### I33-P01: Controlled benchmark

Inventory installed artifacts only. For each selected exact artifact/quantization/context/settings, collect five cold and at least thirty warm serial-baseline and Auto runs, plus CPU/GPU manual pins. Record actual effective backend, delegate placement/fallback, load/prefill/decode/TTFT, RSS, thermal, failure and stage trace IDs.

**Pass evidence:** Same-input comparison is reproducible. Compute/report median/p95 end-of-speech to actual first audible audio, full latency and success rate; Auto must improve both median/p95 without correctness loss. Init alone is not GPU placement proof.

Status: NOT_TESTED. Evidence: pending.

### I33-P02: Concurrency/safety

Overlap STT partials, retrieval and useful warmup; issue a final native command; barge in during generation/playback; cancel during admission/load, force eviction/low-memory on a test device, and detach BT/glasses. Repeat local and permitted direct-cloud routes.

**Pass evidence:** Independent stages overlap and dependent/authorized effects serialize. Partial/ambiguous ASR never acts; no duplicate action or stale audio. Native fast path avoids heavy LLM; unsupported backends reject; residency/thermal/memory limits and permissions hold.

Status: NOT_TESTED. Evidence: pending.

### I33-P03: Consumer setup

Install/repair a supported curated role pack through normal setup; inspect Advanced role/backend/residency controls and switch back to consumer defaults.

**Pass evidence:** Role-to-model and model-to-backend remain independent, no prewarm-all policy or RAM-only promise; existing providers/setup work. No CI/Actions are run.

Status: NOT_TESTED. Evidence: pending.

## Requirement-by-requirement review

- [ ] **I33-R01** (original line 5, Proposed runtime design): - Represent each request as a cancellable, dependency-aware execution graph with stage events, deadlines, bounded queues and backpressure.
- [ ] **I33-R02** (original line 6, Proposed runtime design): - Stream STT partial hypotheses on CPU; run lightweight deterministic command matching and optional intent classifier incrementally. Finalize intent only when confidence/utterance boundary permits; prevent premature device actions.
- [ ] **I33-R03** (original line 7, Proposed runtime design): - In parallel, pre-load/warm the candidate reasoning model when useful; only dispatch finalized, authorized requests. GPU LLM is **optional**, selected by measured performance, not by assumption.
- [ ] **I33-R04** (original line 8, Proposed runtime design): - Overlap retrieval/embedding and context preparation where independent; start streaming TTS on stable response chunks while generation continues, with interruption/barge-in and cancellation.
- [ ] **I33-R05** (original line 9, Proposed runtime design): - Use structured tool execution for Notebook/Canvas (create/edit files, formatting, assets); embedding models support retrieval, not document editing. Require confirmations for consequential actions.
- [ ] **I33-R06** (original line 10, Proposed runtime design): - Keep CPU, GPU, potentially NNAPI/NPU and cloud execution as independently selectable backends, subject to actual LiteRT support and device capability.
- [ ] **I33-R07** (original line 13, Model roles / configuration): - Extend existing role assignments: STT, intent, embedding/retrieval, reasoning, tool selection, vision, TTS. Some roles may be rules or platform services rather than neural models.
- [ ] **I33-R08** (original line 14, Model roles / configuration): - Decouple **role → candidate model** from **model → backend**; permit reuse of one model for several roles, prioritized fallback and explicit capability checks.
- [ ] **I33-R09** (original line 15, Model roles / configuration): - First-run onboarding: automatically select a tested, downloadable default pack (no requirement to browse Hugging Face). Curate from models actually benchmarked on test devices, verify model format, provenance/license, checksum, disk/RAM requirements and compatibility.
- [ ] **I33-R10** (original line 16, Model roles / configuration): - Advanced/Developer Settings: manual role/backend pinning, preloading/residency, tuning, profile presets, benchmark data and per-stage trace. Preserve simple consumer defaults.
- [ ] **I33-R11** (original line 17, Model roles / configuration): - Residency manager: memory budget, load/unload policies, warmup, eviction under Android memory pressure, thermal throttling awareness. Do not prewarm every model.
- [ ] **I33-R12** (original line 20, Benchmark and correctness plan): 1. Inventory only models **already downloaded on the test phone**; do not download new models for initial benchmark.
- [ ] **I33-R13** (original line 21, Benchmark and correctness plan): 2. For identical model/quantization/prompt/context/settings, measure cold/warm load, prefill tok/s, decode tok/s, time-to-first-token, peak RSS/memory, CPU/GPU utilization, thermal state and failures.
- [ ] **I33-R14** (original line 22, Benchmark and correctness plan): 3. Confirm actual GPU delegate operation placement/fallback, quantized kernels, KV cache and synchronization overhead. A successful GPU init is not proof that the whole graph runs there.
- [ ] **I33-R15** (original line 23, Benchmark and correctness plan): 4. Compare serial baseline vs concurrent pipeline by end-of-speech → first audio, full voice-to-voice latency, success rate, energy and thermal stability. Concurrent work may **regress** performance due to shared memory/bandwidth.
- [ ] **I33-R16** (original line 24, Benchmark and correctness plan): 5. Test overlapping speech, interruptions, background/foreground, model eviction, device low-memory, BT audio/glasses disconnect and provider failures. Record trace IDs across EventBus, Orchestrator, Speech Runtime and ReasoningEngine.
- [ ] **I33-R17** (original line 27, Implementation phases): - **P0:** instrumentation, reproducible ADB/device benchmark, trace timeline, existing downloaded model inventory.
- [ ] **I33-R18** (original line 28, Implementation phases): - **P1:** stage DAG, safe streaming STT/intent, bounded concurrency, cancellation, CPU fast path.
- [ ] **I33-R19** (original line 29, Implementation phases): - **P2:** adaptive residency and measured backend selection with manual overrides.
- [ ] **I33-R20** (original line 30, Implementation phases): - **P3:** parallel retrieval + chunked streaming TTS; structured Notebook/Canvas tool routing.
- [ ] **I33-R21** (original line 31, Implementation phases): - **P4:** curated one-click model packs and onboarding/developer UX (coordinate with ongoing DevKit v1.2 UI redesign).
- [ ] **I33-R22** (original line 34, Acceptance criteria): - [ ] End-to-end traces show concurrent stages and real per-stage latency.
- [ ] **I33-R23** (original line 35, Acceptance criteria): - [ ] Auto mode improves median/p95 end-of-speech-to-first-audio against measured baseline **without compromising correctness**; report regressions honestly.
- [ ] **I33-R24** (original line 36, Acceptance criteria): - [ ] Manual CPU/GPU assignments and fallback work; unsupported GPU combinations are clearly rejected.
- [ ] **I33-R25** (original line 37, Acceptance criteria): - [ ] Simple voice commands can bypass heavy LLM.
- [ ] **I33-R26** (original line 38, Acceptance criteria): - [ ] Memory/thermal guardrails, cancellation, safe device actions and cloud privacy permissions covered.
- [ ] **I33-R27** (original line 39, Acceptance criteria): - [ ] Existing local/cloud providers and onboarding remain functional.
- [ ] **I33-R28** (original line 40, Acceptance criteria): - [ ] No GitHub Actions or CI; GitHub is code/issue storage only.
- [ ] **I33-R29** (original line 42, Acceptance criteria): **Status:** architecture/backlog; no code changes authorized by this issue.
