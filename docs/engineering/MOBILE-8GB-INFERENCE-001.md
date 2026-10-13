# MOBILE-8GB-INFERENCE-001 — 8 GB Android inference and speech lanes

Date: 2026-10-13 · Repository: Halo main · Status: SOURCE COMMITTED / PHYSICAL ACCEPTANCE AFTER THIS BATCH PENDING

## 1. Actual Redmi evidence (NOT extrapolated to every 8 GB device)

Device: Redmi `23124RA7EO`, Android 15; LiteRT-LM 0.18.0; source: [full report](../testing/LITERT-VOICE-2026-10-13.md) and [JSONL](../testing/artifacts/LITERT-VOICE-2026-10-13/model-diagnostics.jsonl).

| Artifact | CPU model load / first token | GPU load / first token | Meaning |
| --- | --- | --- | --- |
| Qwen3 0.6B dynamic INT4 (345 MB) | 3.5 / 10.8 s | 9.7 / 4.1 s | GPU smoke and separate stability 3/3 pass; prioritize warm interactive turns |
| SmolLM2 135M (143 MB) | 0.9 / 4.3 s | 2.4 / 5.0 s | Too unreliable at trivial instruction following to be Halo's default |
| Qwen3.5 0.8B VL int8 (HF imported ~1.3 GB) | 53.2 / 2.3 s | 241.9 / 5.2 s | TEXT ONLY proof; cold start unsuitable; vision not qualified |
| DeepSeek R1 Distill Qwen 1.5B Q8 (1.8 GB) | 9.7 / 3.2 s | FAIL at first token | CPU-only development candidate; reasoning/final-answer cleanup needed |
| Qwen3 1.7B dynamic INT4 (977 MB) | 8.0 / 17.5 s | FAIL at first token | CPU generates; GPU OpenCL invalid work-group `{1,1,512}` |

Two distinct proofs: SHA-256/metadata integrity, then native generation and provider verification. Neither is sufficient to assign an agentic/voice role: use the existing FAST / CHAT / REASONING / AGENTIC role suite, and test actual user turns.

**Source limitation:** Numeric pairs are short smoke measurements, NOT paired sustained, battery or concurrent benchmarks. Do not extrapolate S26 numbers to this Redmi.

## 2. Best next model experiments (do not auto-install)

| Priority | Candidate | Why | Acquisition / admission |
| --- | --- | --- | --- |
| 1 | Qwen3 0.6B dynamic INT4 | Already proven GPU 3/3, usable small resident chat baseline | Installed pinned artifact; only use GPU via existing per-device qualification |
| 2 | Qwen2.5 1.5B Instruct Q8 | Different instruction-tuned baseline, may outperform Qwen3 0.6B in short precise replies | Already pinned OPEN_DIRECT; CPU-first role tests; memory and quality still unverified on Redmi |
| 3 | SmolLM2 360M Instruct | Low RAM/CPU speed challenger, likely weak on complex instructions | Already pinned OPEN_DIRECT; run role suite before suggesting FAST |
| 4 | Gemma 3 1B IT INT4 | Small chat challenger (~584 MB) | GATED_IMPORT_ONLY, accepted license and verified artifact required; no mirror |
| 5 | Qwen3.5 0.8B **text-only** LiteRT artifact | Hybrid attention reduces KV growth vs a conventional full-attention model; avoids the VL tower | Community artifact ~963 MB; upstream available, but **no exact immutable Halo pin yet** — explicit researcher import and qualification only |
| 6 | Qwen3 1.7B INT8 alternate artifact | Published CPU/XNNPACK measurements outperform its INT4 sibling in prompt processing on another device | ~2.06 GB artifact, not pinned in Halo; device memory acceptance required before cataloguing |
| Investigation only | FunctionGemma 270M mobile actions | Small CPU-specialized action model (~289 MB on upstream S25 benchmark) | GATED license, fine-tuned to a constrained action schema; cannot directly replace Halo's agentic protocol or skip action approvals |
| Not default | Gemma 3n/Gemma 4 2B+; Qwen3.5 0.8B VL GPU | Runtime residency/initialization overhead on this phone, or large peak working sets | Device capability and full modality probing first |

Upstream research:
- [LiteRT Qwen3 0.6B variants](https://huggingface.co/litert-community/Qwen3-0.6B), [Qwen3 1.7B CPU/GPU artifact comparison](https://huggingface.co/litert-community/Qwen3-1.7B)
- [Qwen3.5 0.8B text versus VL / hybrid cache](https://huggingface.co/litert-community/Qwen3.5-0.8B), [Qwen2.5 0.5B benchmark](https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct)
- [FunctionGemma 270M mobile actions + license](https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions)
- [Real-device LiteRT benchmark methodology](https://github.com/john-rocky/edge-llm-bench)
- [Upstream LiteRT context-allocation performance issue #2568](https://github.com/google-ai-edge/LiteRT-LM/issues/2568)
- [Upstream GPU allocation/leak investigation #3862](https://github.com/google-ai-edge/LiteRT-LM/issues/3862)
- [LiteRT Android native OpenCL declarations and init guidance](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md) (Halo already declares libOpenCL/libvndksupport).

Community/forum reports comparing small Qwen models show potential quality improvements with parameter count, not device-specific guarantees: [r/LocalLLaMA small Qwen comparison](https://www.reddit.com/r/LocalLLaMA/comments/1kfrcul).

## 3. Backend architecture for phone and wearable speech

Halo **already uses separate engines**:
1. STT/VAD: Android speech, Sherpa Whisper Tiny/Base/Small or Omnilingual on CPU (separate provider/model from LiteRT LLM).
2. Local text inference: LiteRT 0.18.0, only one resident LLM engine. AUTO resolves CPU unless GPU qualified for this artifact hash + runtime + Android build. Explicit GPU remains a developer-only investigation.
3. TTS: Android TTS on its configured device engine, Sherpa Kokoro CPU or ElevenLabs via user's selected provider. These are NOT model backends selected for LiteRT text inference.
4. HeyCyan/headset audio route is transport/output affinity, **not a model accelerator**. Keep route proof and user-audible confirmation separate.

**New on this batch:** `MobileNativeComputePolicy` makes concurrent active *heavyweight* execution opt-out by default on devices reporting <=9 GiB total RAM (marketed 8 GB class). ASR/TTS/LLM engines may still be **RESIDENT** when their execution lease is released; the policy serializes overlapping ACTIVE compute, not different providers or all idle residency. It preserves the voice-input preemption path. Devices reporting >9 GiB still need NORMAL pressure and >3 GiB free for overlap. Unknown device budget fails closed. This may affect contention/latency and needs a real-device voice loop test.

### Explicit non-goals

- No second simultaneous resident LiteRT model; one native engine at a time is still an invariant.
- No changing user-selected provider/model, no speculative GPU enabling, no background downloads, no model alias trickery and no bare GGUF acceptance.
- No invoking a smaller action model for consequences without Halo approval/receipts and without an independently verified role test.
- No claiming speech language support based on general LLM multilingual ability: Whisper supports LV/RU/EN; current Kokoro pack is EN/ZH only.

## 4. Reliable instrumentation results

`DownloadedModelDiagnosticsTest` previously logged a FAILED raw GPU probe, then reported JUnit `OK (1 test)`. That is **diagnostic capture**, not passing acceptance. It now writes the structured row and **asserts** raw generation and provider verification by default, so failed native generation becomes a failing JUnit invocation. Researchers can opt into `-e captureOnly true` to record known failures without failing the runner. When `-e contextTokens N` is passed, raw probe must pass but provider verification is intentionally skipped (context override must not mutate persisted provider policy).

GPU qualification is STILL its separate 3-success physical stability probe; diagnostic runner success alone never qualifies the accelerator.

## 5. Required physical test ladder before assigning roles

Build on Windows (no GitHub Actions):
```powershell
.\gradlew.bat :sdk:runtime-sdk:test :providers:litert:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-parallel --max-workers=1 --no-daemon --console=plain
```
Check the actual module Gradle path with `gradlew projects` if it differs in this checkout.

For reproducible per-model/per-backend instrumentation (after both APKs are installed), use:

```powershell
.\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Serial YOUR_ADB_SERIAL -Backend BOTH
```

`-Models qwen3-0.6b-dynamic-int4` narrows the sweep; `-CaptureOnly` collects expected GPU failure evidence without passing acceptance. The script stores raw test logs, JSONL (when `adb run-as` can read it), and a verdict CSV under a timestamped directory. A normal sweep fails if any selected model/backend fails, so use explicit model selection for final qualification.

On the Redmi, with `Qwen3 0.6B` installed:
1. Note available RAM/pressure and model fingerprint. Verify raw CPU and GPU probes separately; a failed GPU run must now fail instrumentation.
2. Run `Test GPU for Automatic`: only 3/3 complete GPU generations can qualify it.
3. Measure 3 warm turns per backend, 256 / 512 / 1024 / 2048-token **effective context** where supported; do not claim context settings work until runtime reports effective context.
4. Run role suite with real final-answer extraction; compare Qwen 0.6 vs Qwen2.5 1.5B vs SmolLM2 360M CPU before choosing faster or larger.
5. Complete two full `wearable mic → STT → LLM → TTS → correct output route` loops; confirm the target speaker manually. Check that the new 8 GB active lease serialization doesn't starve speech or cause 1-second ResourceBusy failures. If it does, prioritize a bounded, safe waiting policy over enabling unsafe overlap.
6. Interrupt/barge-in during LLM; compare warm residency and follow-up turn latencies; confirm speech capture can preempt ongoing generation.
7. Reproduce Kokoro multilingual crash separately, collecting tombstone/exit info. The prior runner reported Process crashed; audio acceptance isn't proved.

Trace privacy: no raw microphone audio or user text in diagnostic exports by default.

## 6. Second pass: context controls and evidence identity

Model Browser's expanded model settings now show an explicit **Profile / 1024 / 2048 / 4096 / 8192** context choice (only values not exceeding the pinned artifact's advertised context are shown). Changing the choice does not start inference, download, select a model, or mutate other model preferences. It requires the current model to be unloaded; an installed model uses the selection on the next load. Prior role evidence is invalidated for retesting. This is a developer test control, not a performance recommendation or a background heuristic.

The Redmi-specific suggestion is now only shown as a physical result when **device model AND the exact tested SHA-256** both match the evidence. Future revisions of a similarly named artifact do not inherit an old GPU 3/3 success banner.

### Verification boundary
All changes are source/logic/test additions and do **not** themselves constitute a fresh Android build or Redmi runtime pass. This document captures the prior real physical measurements; the new lease policy and assertions need fresh acceptance.
