# MOBILE-LOWRAM-010 — LiteRT and voice support on Android 4/6/8 GB devices

Date: 2026-10-13 · Status: source merged to `main`; new Android physical acceptance **not yet run**.

## Scope and priority

Halo is a device-independent cognitive runtime, **not a low-end-Android-first product**. Our local phone lane should nevertheless have a useful, honest baseline at 4 and 6 GB, while 8 GB devices gain GPU acceleration only when independently qualified.

Current dependency is `com.google.ai.edge.litertlm:litertlm-android:0.18.0`. Do not reproduce outdated 0.17.1 guidance from the historical development matrix.

### Implemented in MOBILE-LOWRAM-004 through -009

- `MobileNativeComputePolicy.tier(totalMem)` classifies *measured Android RAM*, not a model number or marketed RAM claim. Its boundaries are deliberately conservative approximate 4/6/8 GB bands, not guarantees.
- Memory-constrained Android hosts already serialized concurrent *active* ASR/LLM/TTS compute. The new idle grace is **3 s on 4 GB**, **10 s on 6 GB**, **30 s on 8 GB or larger**; existing pressure/foreground eviction still has final authority. Residency is not permission to execute.
- LiteRT AUTO now remains CPU-first on 4/6 GB **even with a previously GPU-qualified artifact**; GPU qualification records are kept, and explicit developer GPU runs are still possible. At 8 GB+, the original artifact/runtime/build GPU gate is unchanged.
- These decisions resolve in one shared backend path so status, actual loads and diagnostics agree. No production model/provider selection is changed.
- Model Browser shows tier-specific **voice/STT/LLM/TTS** planning guidance. User language, accessibility and route controls remain separate.
- Device instrumentation now logs available RAM, total RAM, Android low-memory flag, process PSS and native allocated heap at every stage. `-e cycles N` (1..5) repeats complete fresh-engine probes and stops on failure.
- `tools/hil/Run-LiteRtPhoneMatrix.ps1` has a conservative **single-model CPU default**; `-Backend BOTH`, `-ContextTokens 1024/2048/4096/8192`, `-Cycles 1..5` and `-CaptureOnly` are explicit. Context overrides run a raw native probe and **do not** silently rewrite user preferences.
- Any reported model pass remains **probe evidence only**. It does not establish tool-following, conversational quality, stable output audio, GPU safety across devices, or sustained multi-turn throughput.

## Research: October 2026 LiteRT-LM and community reports

The results below are upstream **reports**, not tests conducted on a Halo build. Distinguish model format: a `.tflite` / MediaPipe `.task` artifact cannot be inserted into Halo's `.litertlm` runtime catalog merely because the base weights are Qwen.

| Finding | Source | Consequence for Halo |
| --- | --- | --- |
| Android OpenCL per-conversation allocations are reported to grow across create/generate/delete, OOM after a few turns on a 6 GB device | [LiteRT-LM issue #2699](https://github.com/google-ai-edge/LiteRT-LM/issues/2699) | Qualify **same-resident-engine multi-turn** separately; fresh-engine cycles are not enough. CPU-first AUTO on 4/6 GB pending evidence |
| Some Android GPU engine teardown leaks memory when OpenCL cannot be loaded in a source-built 0.17.x branch | [Issue #3862](https://github.com/google-ai-edge/LiteRT-LM/issues/3862) | Declare OpenCL and capture process death; **not proven** on Halo's Maven 0.18.0 |
| Qwen3.5 0.8B text-only hybrid bundle (~963 MB) has compact KV and a viable CPU path on Pixel 8a; GPU can need drastically more memory than CPU, with long cold init | [LiteRT community model card](https://huggingface.co/litert-community/Qwen3.5-0.8B) | Test **text-only on CPU first**. No automatic GPU or vision claim. Imported artifact must pass SHA/metadata verification and acceptance |
| Qwen3.5 tool responses may appear as plain text without structured `tool_calls` on LiteRT 0.18.0; maintainer reproduced it | [Issue #3927](https://github.com/google-ai-edge/LiteRT-LM/issues/3927) | Keep Halo's explicit prompt-tool protocol and final tool-authorization checks; model-family name is not native-tool proof |
| `Qwen2-0.5B-Instruct.litertlm` (~647 MB) runs very fast on Snapdragon S26 CPU/GPU, but this is a high-end device and different artifact | [Qwen2 community card](https://huggingface.co/litert-community/Qwen2-0.5B-Instruct) | A low-memory challenger to import and qualify later; not worth assuming it beats the *smaller* Qwen3 0.6B (345 MB) in Halo |
| Qwen2.5-0.5B-Instruct repo's published files are mostly `.task` / `.tflite` (CPU benchmark ~1.36 GB RSS for int8 on S24 Ultra) | [Qwen2.5 0.5B repo](https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/tree/main) | **Not** a drop-in `.litertlm` download. Source model only; evaluate conversion instead of adding a fake catalog pin |
| Qwen3 0.6B Q4 was used to pick from ~10 structured UI targets on a 6 GB Galaxy Note 8, with action execution/verification handled outside the model | [r/LocalLLaMA field report](https://www.reddit.com/r/LocalLLaMA/comments/1wapzjg/qwen306b_400_mb_on_a_samsung_note_8_2017_phone/) | Excellent *architectural lesson*: short structured candidate lists, deterministic phone tools and confirmation; **llama.cpp on Android 9, not a LiteRT benchmark** |
| Android GPU is not reliably testable on emulators | [LiteRT-LM issue #1448](https://github.com/google-ai-edge/LiteRT-LM/issues/1448) | Final GPU qualification must occur on the target phone, not emulator |

## Candidate matrix (conservative, actual catalog only)

| Device class | First LLM | Alternative | STT | TTS | Notes |
| --- | --- | --- | --- | --- | --- |
| 4 GB | Qwen3 0.6B dynamic INT4 **CPU** | SmolLM2 360M CPU (quality-unproven) | Android STT / Whisper Tiny CPU | Android TTS | 1K/2K context *experiments*, not hidden defaults; avoid simultaneous resident large models, vision, Kokoro until proven |
| 6 GB | Qwen3 0.6B **CPU** | Gemma 3 1B INT4 (licensed) or Qwen2.5 1.5B Q8 only with memory evidence | Whisper Tiny/Base CPU | Android TTS | GPU only as explicit diagnostic; try 2K before 4K, compare quality and correct actions |
| 8 GB | Qwen3 0.6B CPU or **qualified GPU** | SmolLM2 360M, Qwen2.5 1.5B Q8 CPU, Qwen3.5 0.8B **text-only research import** | Whisper Base / Tiny CPU | Android/Kokoro with separate validation | Do not assume GPU helps: prior Redmi Qwen3.5 VL GPU cold init ~242 s, Qwen1.7 GPU kernel fails |
| More RAM | Existing full model/role strategy | Pinned multimodal where verified | Best qualified provider | Best qualified provider | No low-memory heuristic should silently override full runtime architecture |

### Validation protocol (real 4/6/8 GB Android hardware)

1. Start with a clean boot, an installed and verified Qwen3 0.6B artifact, and no active heavy inference.
2. Run CPU-only diagnostics at **profile**, **1024**, **2048** and **4096**. Reject context values that exceed model metadata. Log cold init, first token, RAM/PSS and native heap. The runner measures *fresh engines* only.
3. Run CPU three-cycle reload test. Watch delta of process PSS across cycles and reconcile against normal cache growth rather than inventing a pass threshold.
4. On 8GB test **GPU** explicitly and only use AUTO GPU if per-artifact three-consecutive-sequence GPU qualification passes. On 4/6GB an explicit developer GPU can be tested, but AUTO deliberately remains CPU-first.
5. Separately test **one resident engine, 5–10 consecutive user conversations** and record PSS after each. A first-turn green result is NOT a memory-leak pass; do not claim same-engine coverage from `-Cycles`.
6. Exercise `microphone → STT → text/intent → LiteRT → Android/Kokoro TTS → correct endpoint` two times, plus interruption and wake/sleep. Confirm audible output as a human; native success is not audio proof.
7. Run chat / reasoning / HALO_PROMPT tool role gates. Keep actual action authorization, result verification and source provenance outside the small model, as with the structured-UI community example.
8. Repeat after upgrades of Android build, LiteRT runtime, model SHA or model context; never reuse another device's GPU proof.

Example Windows commands (ADB attached, APK and androidTest APK installed):

```powershell
.\gradlew.bat :sdk:runtime-sdk:test :providers:litert:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-parallel --max-workers=1 --no-daemon --console=plain
.\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Models qwen3-0.6b-dynamic-int4 -Backend CPU -Cycles 3 -ContextTokens 2048
.\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Models qwen3-0.6b-dynamic-int4 -Backend GPU -Cycles 3 -ContextTokens 2048 -CaptureOnly
```

The second command is the **safe baseline**; the third is an *explicit, potentially crashing experiment*, not automatic qualification. The reporter CSV records whether an instrumentation invocation passed; read JSONL `rawPassed`, backend and model fingerprint before claiming stability. Do not confuse heap/PSS with GPU-resident memory or system RSS.

## Outstanding work, not prematurely claimed

- Repeatable **same-engine multi-conversation** memory test with correct native execution/resource leases and teardown; Android memory-info snapshots alone cannot expose all GPU allocations.
- GPU work-group failure on Redmi Qwen3 1.7B, and failed DeepSeek GPU artifact, need either upstream fix or a *different exact artifact*.
- Proper phone voice-latency profiling and Kokoro multilingual synthesis/route acceptance (prior runner was incomplete/crashed).
- Verify new source builds and real Android execution; no access to an Android SDK or physical 4/6GB phones in this coding session.
- Candidate `Qwen2_0.5B_Instruct.litertlm` and Qwen3.5 0.8B text-only require immutable revision, exact artifact SHA-256, exact bytes, and a successful device probe before **any** new built-in download/catalog entry.
