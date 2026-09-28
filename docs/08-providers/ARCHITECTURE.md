# Provider Architecture

Status: CURRENT SOURCE MAP. Reviewed 2026-09-24.

Halo's provider architecture explicitly distinguishes between different classes of integration.

## Provider Categories

### 1. Inference Providers
These are reasoning backends that process conversational turns and tool invocations. They are replaceable, and no single provider is "the brain" of Halo.
- **On-device LiteRT-LM**: Default local inference path (`provider.litert.local`). Runs packaged `.litertlm` models directly on GPU/CPU via Google's LiteRT-LM engine.
- **Ollama**: Optional LAN HTTP inference server (`provider.ollama`).
- **OpenRouter**: Optional remote/cloud inference (`openrouter`).

### 2. Speech Providers
- **Sherpa / Whisper**: Local speech-to-text (STT).
- **Android TTS**: Local text-to-speech synthesis (TTS). Output routing is handled separately from synthesis.

### 3. Host Integrations & External Handoff
- **Android Assistant Role**: This is an OS-level integration through which the user invokes Halo or through which Halo hands off tasks. **It is not an AI provider.** Halo does not receive conversational answers from the Android Assistant.

## Session and Tool Rounding

`CognitiveSession` performs bounded tool rounds, authorization, and result feedback. 

Tool capabilities remain provider/model-specific. The pinned Qwen LiteRT model explicitly advertises `supportsTools = false`. We do not advertise universal local tool support until a reliable capability bridge exists.

## RUNTIME-021 workload-aware routing

Routing uses the intersection of provider/adapter capability and the selected model's advertised
capability. Provider-wide IMAGE/TOOLS support does not make every model compatible.

- On-device LiteRT-LM remains conservative: TEXT + STREAMING only unless the selected model and adapter actually
  implements more.
- Ollama remains optional local/LAN inference. Halo queries `/api/show` metadata for model
  capabilities and context length. Vision is advertised only when the model metadata reports it and
  the Halo adapter can serialize image chat input. Tool support remains false even if Ollama reports
  a tool-capable model because Halo's Ollama adapter does not yet implement the complete tool
  call/result round trip.
- OpenRouter uses its model metadata for input modalities, tool support, streaming and context
  length; an explicitly selected incompatible model is rejected rather than silently swapped.

Required private context participates in routing policy. If cloud image/screen/workspace sharing is
disabled, a remote target is not eligible for a workload that requires that context. Optional
context never forces provider switching.

RUNTIME-021 is **IMPLEMENTED / UNVALIDATED** pending STABILIZATION.

## Development model matrix (RUNTIME-049)

The built-in LiteRT catalog is no longer a single artifact. Four byte-pinned text models
(`qwen3-0.6b-dynamic-int4`, `qwen3-1.7b-dynamic-int4`, `qwen2.5-1.5b-instruct-q8`,
`deepseek-r1-distill-qwen-1.5b-q8`) plus one gated import-only identity (`gemma3-1b-it-int4`) let Halo
tell a model-specific failure apart from a runtime, GPU-driver, quantization, prompt, or resource
scheduling failure.

Per-artifact runtime profiles (backend, CPU-fallback permission, context, sampling/output defaults)
replace the previous one-size-fits-all engine configuration, and a per-model AUTO/GPU/CPU developer
override is selectable and persisted. Loading, residency and execution are distinct: a resident model
does **not** hold `compute.heavy`, the provider reports `CONFIGURED_COLD` for an installed but
unloaded model (a valid execution target), and `BUSY` while a request is in flight.

Pins, the evidence ladder (CATALOGUED -> DOWNLOAD VERIFIED -> ENGINE VERIFIED -> GENERATION
VERIFIED -> PHYSICAL DEVICE VERIFIED) and the phone acceptance checklist are in
[LITERT-DEVELOPMENT-MATRIX.md](LITERT-DEVELOPMENT-MATRIX.md). Model-role routing
(FAST/REASONING/AGENTIC/VISION) and 3.5-5 GB multimodal artifacts are deliberately deferred.
