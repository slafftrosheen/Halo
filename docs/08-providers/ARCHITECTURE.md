# Provider Architecture

Status: CURRENT SOURCE MAP. Reviewed 2026-09-24.

Halo's provider architecture explicitly distinguishes between different classes of integration.

## Provider Categories

### 1. Inference Providers
These are reasoning backends that process conversational turns and tool invocations. They are replaceable, and no single provider is "the brain" of Halo.
- **Native llama.cpp**: Default local inference path. Supports text and streaming. Requires a downloaded GGUF model.
- **Ollama**: Optional LAN HTTP inference server. Supports text and streaming.
- **OpenRouter**: Optional remote/cloud inference.

### 2. Speech Providers
- **Sherpa / Whisper**: Local speech-to-text (STT).
- **Android TTS**: Local text-to-speech synthesis (TTS). Output routing is handled separately from synthesis.

### 3. Host Integrations & External Handoff
- **Android Assistant Role**: This is an OS-level integration through which the user invokes Halo or through which Halo hands off tasks. **It is not an AI provider.** Halo does not receive conversational answers from the Android Assistant.

## Session and Tool Rounding

`CognitiveSession` performs bounded tool rounds, authorization, and result feedback. 

Tool capabilities remain provider/model-specific. Native llama.cpp currently explicitly advertises `supportsTools = false`. We do not advertise universal local tool support until a reliable capability bridge exists.
