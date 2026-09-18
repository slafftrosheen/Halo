# AI providers and sessions

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

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

[CognitiveSession](../../runtime/cognition/src/main/java/com/halo/runtime/cognition/session/CognitiveSession.kt)
already performs bounded tool rounds, authorization and result feedback.
The app ToolRegistry is initialized with echo and current time; general Android/wearable
capability tools are not thereby exposed.

[InMemoryToolAuthorizer](../../runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/InMemoryToolAuthorizer.kt)
checks registry validation/registration, not complete user-confirmation/Android permission
or physical-safety policy. Context sharing policy exists in session assembly but is not
a mandatory global network firewall. The older AIEngine supplies an empty availableTools
list in both policy branches. These integration gaps are [planned work](../14-roadmap/NEXT-PHASE-2027.md).
