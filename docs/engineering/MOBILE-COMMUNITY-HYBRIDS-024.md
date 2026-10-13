# MOBILE-COMMUNITY-HYBRIDS-024 — LFM2.5 / Granite / Bonsai and engine lifetime

Date: 2026-10-13. Status: implementation committed; new device test and full Gradle build pending.
Related: [4/6/8 GB foundation](MOBILE-LOWRAM-010.md), [Redmi baseline](../testing/LITERT-VOICE-2026-10-13.md).

## Why this pass exists

Small Qwen models are not the entire local-inference ecosystem. Halo now surfaces LFM2.5, IBM Granite and Prism Bonsai in a testable Hub discovery workflow. These names denote **families**, not one interchangeable binary. Precise file revision, runtime, device/driver, CPU/GPU backend, context setting and model quality must be proved before assigning any Halo role.

Hub models are pinned by live Hugging Face metadata (immutable 40-hex revision + file hash + exact length). On-device downloads still enforce artifact integrity. User-initiated import remains supported. A sync result is **not** a runtime or tool-use endorsement.

## What research actually supports

| Family / artifact | Best initial variant | Upstream facts | Halo acceptance |
| --- | --- | --- | --- |
| [LFM2.5 1.2B Instruct](https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct) | `LFM2.5-1.2B-Instruct_int4.litertlm` for CPU (~736 MB); `_int4_gpu` is a **separate export** | CPU variant and GPU export not interchangeable; hybrid short-conv + GQA, up to 4096 context. Pixel 8a CPU int4 ~31 tok/s, from publisher's device run, not Halo. Upstream Aug 4 executor metadata and Sep 21 template updates matter for exact file hash | CPU 1K/2K then qualified GPU variant; model quality, memory, isolation, EN/LV/RU responses unproven on our devices |
| [LFM2.5 2.6B](https://huggingface.co/litert-community/LFM2.5-2.6B) | INT4 ~1.67 GB, only on devices with adequate memory | Bigger thinking model, 4096 context, thought channel; may require long reasoning/output budget | 8GB+ research; not a 4GB conversational default |
| [Granite 4.0-H 1B](https://huggingface.co/litert-community/granite-4.0-h-1b) | `granite-4.0-h-1b_int8.litertlm` ~1.68 GB | Mamba2/attention hybrid, KV nearly flat with context but recurring SSM state; publisher updated selective-scan GPU and fixed start-token metadata on Aug 28; reported Pixel 8a/iPhone success does not imply our Redmi result | CPU smoke first, then cross-conversation state isolation and RAM; OpenCL FP32 activations can use much more GPU memory |
| [Granite 4.2 3B](https://huggingface.co/litert-community/granite-4.2-3b) | INT4 ~2.19 GB only when memory permits | Dense reasoning family; not equivalent to Granite 4.0-H recurrence | High-end experimental, not low-RAM default; gate output quality/thinking |
| [Ternary Bonsai 1.7B](https://huggingface.co/litert-community/Ternary-Bonsai-1.7B) | **`bonsai-1.7b-int2pc-4k-gpu.litertlm`**, per publisher | Executable INT2-per-channel bundle, 4096 context, GPU preferred but tested CPU on Pixel 9 Pro XL. Other 32K/FP16 builds are separate experiments; NPU unavailable. Its storage quantization is not an INT2 native execution guarantee on every driver | Try CPU short smoke first; explicit GPU developer probe after RAM checks; do not equate INT2 size with GPU allocation |
| [1-bit Bonsai 1.7B PoC](https://huggingface.co/litert-community/1Bit-Bonsai-1.7B-PoC) | **None** | Publisher states no released INT1 execution kernel and file will not run | Exact known file/hash excluded from Hub download and blocked in provider and direct raw probes; other Bonsai files unaffected |

Upstream reports to track:
- [LiteRT-LM #3165](https://github.com/google-ai-edge/LiteRT-LM/issues/3165): cross-conversation recurrent-state reuse on Granite 4.0-H/Qwen3.5 hybrids
- [LiteRT-LM #2699](https://github.com/google-ai-edge/LiteRT-LM/issues/2699): OpenCL GPU per-conversation memory retained between create/send/delete
- [LiteRT-LM #3878](https://github.com/google-ai-edge/LiteRT-LM/issues/3878): `ModelInfo.from()` copying large model sections into native memory, an issue when verifying large files on constrained phones
- [LiteRT-LM #3927](https://github.com/google-ai-edge/LiteRT-LM/issues/3927): Qwen3.5's emitted textual tool envelope not recognized as native tool calls

Do not generalize a report about one runtime/driver to every phone. Our release depends on LiteRT-LM **0.18.0**. The Bonsai one-bit PoC is explicitly non-executable today and should not be tested just because it is advertised as a `.litertlm` file.

## What changed in Halo main

1. **Hub source triage.** `LiteRtCommunityModelGuide` identifies explicit known non-runnable Bonsai INT1 and provides guidance for LFM2.5, Granite-H, Qwen3.5 and executable ternary Bonsai. Runtime/provider and raw-probe SHA guards apply to the exact published INT1 fingerprint only.
2. **First-class discovery.** Model Browser offers Find LFM2.5 / Granite / Bonsai; individual details explain architecture warnings and let testers copy the canonical `hf-…` ID used by ADB. The selected provider, model-role routing and download consent stay user-owned.
3. **Production isolation for hybrid recurrence.** For matching LFM2.5, Granite 4.0-H and Qwen3.5 community variants, the LiteRT provider discards any leftover native Engine **before a user turn** (including one left by a developer verification) and releases/unloads the native Engine **after each user-visible turn** (both normal and streamed completion), not merely the Conversation. It costs repeated cold engine initialization but prevents depending on unsafe cross-conversation recurrent-state reuse. Qwen3 ordinary models retain warm residency. A future faster path may be enabled only after the precise device/runtime shows correct state isolation.
4. **Single-engine native endurance.** `LiteRtControlProbeRunner.runResidentEndurance` explicitly loads ONE native Engine, runs 2..10 *separate Conversations*, alternates expected `HALO_OK` / `HALO_READY` responses, then closes each Conversation and finally the Engine. It does not run phone tools or expose model text. The Android instrumentation records per-turn available RAM, process PSS and native heap **after** close and keeps a machine-readable completion/semantic pass result. This is different from `-Cycles`, which loads a fresh Engine every time. A strict sentinel mismatch is a failed **endurance/quality** test, not proof of a native memory defect.
5. **No implicit qualification.** Neither Hub discovery nor the endurance probe sets production GPU qualification, user role binding, or automatically substitutes a model. Explicit GPU still has native crash risk.

### Commands: run after installing both debug APKs on the phone

Build (without GitHub Actions):

```powershell
.\gradlew.bat :sdk:runtime-sdk:test :providers:litert:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-parallel --max-workers=1 --no-daemon --console=plain
```

In Halo, On-device models → Find LFM2.5 / Granite / Bonsai → download/verify the **exact desired file** → Details → Copy model ID. Substitute this `hf-…` ID below. Do not infer the ID from the filename.

First, CPU and smaller context, with fresh engine per cycle:

```powershell
.\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Models hf-COPIED_MODEL_ID -Backend CPU -Cycles 3 -ContextTokens 1024
```

Then explicitly exercise one resident Engine with five distinct Conversations. This intentionally **bypasses Halo's protective post-turn engine isolation**, solely to detect whether the underlying runtime safely supports shared-engine conversation lifetime; it never changes production settings:

```powershell
.\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Models hf-COPIED_MODEL_ID -Backend CPU -ResidentTurns 5 -ContextTokens 1024
```

To capture native failures without counting them as passes: add `-CaptureOnly`. **GPU tests should only follow CPU verification, free-RAM review and explicit operator selection.** Monitor device logcat and system graphics allocations in addition to the process PSS; system GPU memory is not equal to native heap/PSS. One failed turn or rising PSS is an investigation, not proof of a universal driver flaw.

For each family, report model ID/SHA, device RAM, Android version, GPU driver, LiteRT runtime, backend, context, cold init and first-token latency, PSS trend, per-turn semantic agreement, and whether the completed voice pathway (microphone/STT → inference → audible TTS) works in LV/RU/EN.

### Known limitations / safety boundary

- No physical Android acceptance or Gradle compilation has been run in this tool session; committed tests are not the same as tested binaries.
- Loaded weights + KV + native scratch + Metal/OpenCL allocations can exceed the file size substantially. LFM 2.6B/Granite 4.2B should not be treated as low-RAM defaults.
- LFM Open License v1.0 has additional terms, including commercial-use restrictions for organizations above its threshold; inspect original terms before redistribution or bundling. Apache-2.0 on the identified Granite/Bonsai variants does not confer native compatibility.
- Hybrid engine unloading ensures no native recurrent state is carried into a **new** engine. This may slow repeated spoken turns, so real-world latency is a required acceptance signal.
- The exact Bonsai 1-bit SHA guard applies only to the published proof of concept. It is not a rule banning future genuinely executable 1-bit models or ternary builds.
