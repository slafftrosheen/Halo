# AI providers and sessions

Status: CURRENT SOURCE MAP. Reviewed 2026-09-24.

[AI.kt](../../sdk/provider-sdk/src/main/java/com/halo/sdk/provider/AI.kt) separates provider metadata,
inference and handoff contracts. [DefaultAiProviderRouter](../../engines/ai/src/main/java/com/halo/engines/ai/AiProviderRouter.kt)
receives the Android host's designated `Providers.inference` backend as the clean-install default.
That backend is native llama.cpp. Registration order no longer makes Ollama the implicit default;
a valid persisted user selection still takes precedence.

| Adapter | Role / boundary | Setup |
|---|---|---|
| Native llama.cpp | Default on-device inference; TEXT + STREAMING | Download/select an app-managed GGUF; no server/key |
| Ollama | Optional HTTP inference server; TEXT + STREAMING | Configure reachable endpoint and select a model advertised by Ollama |
| OpenRouter | Optional cloud inference, discovery, streaming and tool-call parsing | Configure API key; default router is `openrouter/free` |
| Android assistant | ACTION_ASSIST handoff only | Configure Android system assistant; never treated as an inference result |
| Sherpa | Local speech recognition | Install supported speech model from Voice setup |
| Android TTS | Speech synthesis | Uses Android TTS engine; output routing is separate |

Full setup: [Provider setup](../08-providers/SETUP.md).

Ollama's local classification does not mean on-phone: its endpoint may be another machine on the LAN.
The `10.0.2.2:11434` endpoint is an emulator-only development convenience. OpenRouter is explicit
network/cloud inference.

Local llama.cpp discovery uses app-managed inventory and cached GGUF metadata. Download integrity is
separate from metadata enrichment: a complete parseable GGUF is not deleted merely because Halo's
lightweight reader cannot project `general.architecture`. Native llama.cpp is the authoritative
compatibility check at model load time. Memory admission may reduce context size or reject a load
when Android memory headroom is insufficient.

[CognitiveSession](../../runtime/cognition/src/main/java/com/halo/runtime/cognition/session/CognitiveSession.kt)
performs bounded tool rounds, authorization and result feedback. Tool capability remains
provider/model-specific. Native llama.cpp and Halo's Ollama adapter currently do not advertise
TOOLS merely because upstream engines may support tool formats.

Provider diagnostics are on-demand. Startup restores persisted local state without probing every
network provider.
