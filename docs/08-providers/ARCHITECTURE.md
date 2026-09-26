# Provider Architecture

Status: CURRENT SOURCE MAP. Reviewed 2026-09-24.

Halo's provider architecture explicitly distinguishes between different classes of integration.

## Provider Categories

### 1. Inference Providers
These are reasoning backends that process conversational turns and tool invocations. They are replaceable, and no single provider is "the brain" of Halo.
- **Native Local** (stable provider ID `provider.llamacpp.native`): Default zero-server local inference provider with two runtime adapters. Curated Android `.litertlm` artifacts use Google LiteRT-LM; imported/advanced `.gguf` artifacts use the llama.cpp wrapper. `LocalModelSession` owns residency and selects `LITERT_LM` vs `GGUF` from the model descriptor. LiteRT prefers GPU and falls back to CPU; GGUF retains base/projector classification and `mmproj` support.
- **Ollama**: Optional LAN HTTP inference server. Supports text and streaming.
- **OpenRouter**: Optional remote/cloud inference.

### 2. Speech Providers
- **Sherpa / Whisper**: Local speech-to-text (STT).
- **Android TTS**: Local text-to-speech synthesis (TTS). Output routing is handled separately from synthesis.

### 3. Host Integrations & External Handoff
- **Android Assistant Role**: This is an OS-level integration through which the user invokes Halo or through which Halo hands off tasks. **It is not an AI provider.** Halo does not receive conversational answers from the Android Assistant.

## Session and Tool Rounding

`CognitiveSession` performs bounded tool rounds, authorization, and result feedback. 

Tool capabilities remain provider/model-specific. Native Local currently explicitly advertises `supportsTools = false`. We do not advertise universal local tool support until a reliable capability bridge exists.

## RUNTIME-021 workload-aware routing

Routing uses the intersection of provider/adapter capability and the selected model's advertised
capability. Provider-wide IMAGE/TOOLS support does not make every model compatible.

- Native Local remains conservative: TEXT + STREAMING by default; model-specific image capability is exposed only when its selected runtime/model descriptor supports it. Local tool support remains disabled until the complete tool-call/result bridge is validated.
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
