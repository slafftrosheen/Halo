# Provider Architecture
## RUNTIME-062 - exact role capability truth - 2026-10-05

Role execution and browsing share the portable exact-model eligibility evaluator.
CHAT remains the conversation default; FAST, REASONING, AGENTIC and VISION have
central requirements, strict explicit assignments and conservative automatic
ranking behind privacy gates. HALO_PROMPT is a valid Halo tool loop and remains
distinct from proof-gated NATIVE. Cached role queries run no inference or discovery.
Pinned continuations retain their target, FAST adaptation follows the resolved
model, and request correlation uses the real interaction identity. Roles and
System Check expose exact capabilities and human assignment labels.
Combined serial gate passed: 666 tests, zero failures/errors/skips, debug APK built.
Physical role/model acceptance remains pending.
See [RUNTIME-062 acceptance record](../engineering/RUNTIME-062-ROLE-MODEL-FABRIC.md).



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


## PHONE-ASSISTANT-002

Native actions use shared host skill execution independently of CHAT. Compound actions use AGENTIC; images use verified VISION candidates. Configure these roles independently in Intelligence, then use Vision Lab for explicit image capability probing under the current privacy policy. Physical and live-provider checks remain pending. See [host-first runtime](../engineering/PHONE-ASSISTANT-002-HOST-FIRST-RUNTIME.md), [Vision evidence](../engineering/VISION-ROLE-AND-CAPABILITY-EVIDENCE.md), [native acceptance](../testing/NATIVE-ASSISTANT-PHYSICAL-ACCEPTANCE.md) and [Vision acceptance](../testing/VISION-LAB-ACCEPTANCE.md).


## PHONE-ASSISTANT-003 — 2026-10-02

Semantic app bridge implemented: Native → AppFunction → DirectAction → Intent → Accessibility → User. Model reasoning never owns platform authority. External capabilities are bounded, current-app and generation-scoped; approval and durable claims remain in the shared runtime. Dispatch and effect truth are separate, and possible dispatch forbids fallback. Composer launch does not mean message delivery. AndroidX alpha12 / API 37 activity integration is host-owned; scalar external schemas are supported, nested/resource schemas remain excluded. Automated verification passed: 639 tests, zero failures/errors/skips; API 37 debug APK assembled. Audit: 31 skills / 86 tools with zero wiring issues. See docs/testing/SEMANTIC-APP-OPERATOR-ACCEPTANCE.md; physical acceptance remains pending.


## LOCAL-MODEL-ROLE-002

SmolLM2 135M/360M are immutable, inspected text candidates, acquired only explicitly. Schema 25 retains exact device/artifact/runtime/config/suite role evidence. Model Roles separates configured, measured and suggested targets; explicit Use revalidates and uses existing selection authorities. On-device shows current-device history, retest and content-free copy. Evaluation-purpose hints never grant capability or choose roles. No default changes, automatic acquisition, GPU qualification or new residency owner. Two-phone comparison is pending. See docs/engineering/LOCAL-MODEL-ROLE-002.md for pins, policy and physical acceptance.
