# Provider Architecture

## STABILIZATION-PROVIDER-001 - 2026-09-30

**IMPLEMENTED / UNVALIDATED.** Provider implementations now own all registered setup descriptors, including forwarded preset descriptors. ProviderControlRepository owns setup/proof; AiProviderRouter owns targets and last models; catalogue storage owns metadata. SpeechSelectionRepository and SpeechSynthesisRegistry independently own persistent TTS choice and pinned PCM synthesis.

See [source map and deferred acceptance](../engineering/PROVIDER-CONVERSATION-STABILIZATION.md). Build, tests and device acceptance were not run. Earlier dated sections below are historical.


## P8 provider UX and conversation reset — 2026-09-30

General conversational answering is a core Halo capability and does not depend on tool availability. DirectResponse/CHAT is separate from Agentic actions; configuration, authentication, model discovery and generation proof are separate. Speech uses the selected PCM provider, protected ElevenLabs opt-in, phone-only settings tests and host-owned route policy. See [implementation and deferred acceptance](../engineering/PROVIDER-UX-AND-CONVERSATION-RESET.md). Earlier sections retain historical context.


## PRODUCT-P0-001 — source update, 2026-09-30

The existing AI router and selection repository remain canonical. ProviderControlRepository owns setup projections; the shared catalogue gains provider-scoped refresh. ToolUseMode derives from workload requirements and is enforced in context assembly and provider send. SpeechSynthesisRegistry is independent of AI routing; portable PCM providers feed verified playback.

See the [control-plane source map](../engineering/PROVIDER-CONTROL-PLANE.md) for ownership, official contracts, bounds, security and the later QA plan. Build/tests/device acceptance: not run. Earlier dated descriptions below remain historical.

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

## Remote provider fabric (RUNTIME-055)

Halo now separates three concepts for every remote provider:

1. Provider identity — who operates the service
2. Wire protocol — how Halo talks to it
3. Authentication method — how Halo obtains authorization

This avoids one bespoke HTTP stack per vendor. The shared
`OpenAiCompatibleTransport` handles chat completions and responses, SSE
streaming, tool-call parsing, HTTP error mapping, rate-limit parsing and model
list parsing. `ConfiguredOpenAiCompatibleProvider` configures identity, display
name, base endpoint, protocol, credential reference, model discovery mode,
static model hints, capabilities, access metadata and optional headers.

Presets built on the fabric:

- groq — API key, free tier / pay-as-you-go, OpenAI chat completions
- cerebras — API key, free tier, OpenAI chat completions
- opencode.zen — API key, mixed free/paid, OpenAI chat completions
- xai — API key, pay-as-you-go, OpenAI compatible / responses
- companion.opencode — OpenCode companion, user-operated endpoint, inference only
- custom.openaicompatible — user-defined OpenAI-compatible endpoint

Gemini keeps official Google OAuth through `GeminiOAuthCoordinator`. xAI
subscription auth is a coordinator skeleton enabled only when Halo has its own
client configuration; it must not ship someone else's OAuth client identity.

OpenCode companion stores only endpoint + local auth credential. Halo never sees
ChatGPT/xAI/Zen/OpenRouter credentials. Companion tools (shell/fs/MCP/browser/
phone) are disabled for normal Halo inference turns.

Transport security: credentials may only be sent over HTTPS or explicitly trusted
local transport (loopback, LAN, Tailscale, *.local). Public cleartext HTTP
returns `INSECURE_CREDENTIAL_TRANSPORT`.

Free-tier metadata is per-model from current remote catalog, not provider-wide
marketing. Free presence is REMOTE_LIVE evidence.

Secret migration V2 expands the keystore-protected credential set and removes
plaintext after verified write. Brain snapshot export strips secret keys and
restored remote providers are returned as NEEDS_AUTH when credentials are absent.

UI: System Check exposes per-provider auth state, connect/test/refresh actions
and provider matrix. Provider rows show auth state without auto-selecting the
provider on connect.
# MINING-P3-001 — multimodal perception and capability packs (2026-09-30)

ModelInputCapabilityReporting supplies model-specific capabilities. AiModel.executionLocation distinguishes on-device, LAN and remote execution for media policy. LiteRT loads the requested role's model and configures a single image only for declared vision artifacts. Vision evidence is DECLARED / PROBE_REQUIRED / PROVEN / FAILED, keyed by artifact fingerprint, runtime and image contract; a user-triggered synthetic HALO/42 probe supplies process-scoped evidence. Provider image serializers share MIME selection. LiteRT does not advertise operational audio support until an audio backend exists. No new catalog artifact/download is introduced.
