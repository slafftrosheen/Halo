# AI providers and sessions

Status: CURRENT SOURCE MAP. Reviewed 2026-09-21.

[AI.kt](../../sdk/provider-sdk/src/main/java/com/halo/sdk/provider/AI.kt) separates
AiProvider metadata from inference and handoff contracts. AiRequest contains
text/image/audio/video fields; actual modality support is provider/model-specific.
AiResponse carries text/tool calls/metadata. AiStreamEvent represents streamed data and outcomes.

[DefaultAiProviderRouter](../../engines/ai/src/main/java/com/halo/engines/ai/AiProviderRouter.kt)
is the routing implementation. Read its policy/capability checks; do not assume automatic
local/cloud fallback or that all models support every request field.

| Adapter | Boundary |
|---|---|
| OpenRouter | Network inference, discovery, streaming and tool-call parsing/serialization |
| Ollama | Configured endpoint; currently advertises TEXT and STREAMING, not TOOLS |
| Native llama.cpp | Native inference; model metadata reports supportsTools=false |
| Android assistant | ACTION_ASSIST handoff; custom prompt extras are not guaranteed Android APIs; no inference result |
| Sherpa / Android TTS | Speech providers, not conversational LLM substitutes |

Ollama's local metadata does not mean on-phone: its endpoint may be another machine.
External assistant handoff does not establish local processing by that assistant.

Local llama.cpp discovery is now an asynchronous inventory boundary with immutable snapshots and a
fingerprint-keyed in-memory GGUF metadata cache. `listModels()` consumes that cache rather than
reopening GGUF headers. Qwen3 0.6B Q4_K_M and 1.7B Q4_K_M are catalogued as opt-in validation
candidates; neither is the default until the existing Android binding and chat behavior are tested.
Streaming native output has a conservative repeated-suffix guard before response/TTS completion.

[CognitiveSession](../../runtime/cognition/src/main/java/com/halo/runtime/cognition/session/CognitiveSession.kt)
already performs bounded tool rounds, authorization and result feedback.
The app ToolRegistry is initialized with echo and current time; general Android/wearable
capability tools are not thereby exposed.

[InMemoryToolAuthorizer](../../runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/InMemoryToolAuthorizer.kt)
checks registry validation/registration, not complete user-confirmation/Android permission
or physical-safety policy. Context sharing policy exists in session assembly but is not
a mandatory global network firewall. The older AIEngine supplies an empty availableTools
list in both policy branches. These integration gaps are [planned work](../14-roadmap/NEXT-PHASE-2027.md).
