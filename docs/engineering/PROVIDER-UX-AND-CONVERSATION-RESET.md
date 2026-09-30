# P8 provider UX and conversation reset

Source batch: P8 / PROVIDER-UX-001, 2026-09-30. Baseline after fetch and fast-forward pull: `940da597ee6c4db1f41d73d7c50b408ede497461`. The supplied historical SHA was not current local main. The PRODUCT-P0 foundation is preserved.

General conversational answering is a core Halo capability and must not depend on tool availability.

## Execution ownership

ConversationIntent and QuestionIntent resolve to DirectResponse with typed ToolUseMode.NONE, CHAT role and explicit route metadata. Typed Home observations and speech share the interpretation collector. Image attachments retain visual acquisition and privacy requirements. The planner keeps conversation, memory, relevant workspace, images and streaming; its anchored action-word fallback is removed. No knowledge-prefix classifier is added.

Existing deterministic actions retain their owners. Other recognized action intents use Agentic with REQUIRED tools and AGENTIC role. Mixed utterances follow the interpreter's executable intent. SearchIntent retains the existing explicit search path.

The source defect was InterpretationRouteResolver mapping ConversationIntent and QuestionIntent to Agentic. Older planner defaults made that tool-oriented; PRODUCT-P0 removed default tool exposure but left the shared route. P8 makes the distinction explicit at the intent boundary.

NONE assembly returns zero tool definitions before registry discovery. Tool budgeting counts actual schemas only. LiteRT's phone-tool preface is empty without schemas. OpenRouter and compatible serializers retain conditional tools and automatic tool choice only when tools exist. Text-only models can answer directly; initial plain text commits normally. Actions still require confirmed receipts and continuations preserve tool pairing and target.

## Configuration, selection and proof

Registered inference providers drive Provider Center. No second provider list, credential database or router is added. Detail has separate save/remove, connection check, metadata refresh, generation check, Use as default, Assign role and Browse actions.

Generation proof is separate from authentication and catalogue readiness. The explicit bounded probe uses Reply exactly HALO, eight output tokens, empty context, no tools/history and no streaming publication. It does not change selection. It may incur a small account charge. Only exact HALO without tool calls proves that check. Proof is process-local and invalidated by configuration changes; nothing probes on page entry.

Passive provider flows project runtime errors over old successful connection checks. The existing router persists non-secret last-model preferences per provider. Explicit missing-auth routing retains its existing no-fallback behavior.

Model Browser remains lazy and narrow, adds Audio filtering and labels unconfigured models. Selecting one opens setup rather than selecting an unusable target; Back returns to the browser. Intelligence remains a small hub. Context & Privacy now has a narrow ViewModel/page independent of model catalogues. Advanced/runtime controls remain separate. Voice links to a dedicated Speech Output page.

## Speech

The SDK PCM contract, Android adaptation, selector, registry, verified playback and protected ElevenLabs module are reused. Runtime has no ElevenLabs dependency. Automatic selection remains Android system voice; cloud speech is opt-in.

ElevenLabs uses POST /v1/text-to-speech/{voice_id}?output_format=pcm_24000 with xi-api-key and text/model/voice settings JSON. PCM is mono signed 16-bit little-endian at 24 kHz. Discovery retains bounded current v2 voice pagination and v1/models. The safe 24-hour cache contains IDs/names and optional categories.

Voice style persists validated stability/similarity in 0..1 and speed in 0.7..1.2. The voice picker searches cached names/categories. Explicit health authenticates with model metadata without speech; synthesis proof is separate. SDK speech error codes cover configuration, authentication, rate, network, voice/model availability, synthesis, invalid audio and cancellation.

Responses accept at most 24,000 sanitized characters, segmented to at most 4,000 characters per HTTP call at whitespace where practical without breaking surrogate pairs. PCM is bounded to 32 MiB total and 16 MiB per response. Cancellation stops current and future calls. Synthesis completes before playback, so Android fallback does not splice a partially spoken cloud voice. A response wrapper retains fallback once chosen. Synthesis-provider fallback never changes output target or phone-fallback policy.

Settings tests explicitly speak Halo voice test. to PHONE. Normal speech retains origin affinity and the verified host AudioTrack pipeline.

## Privacy and diagnostics

Only final TtsSanitizer output reaches cloud TTS. Stored keys never enter UI drafts or saved state. Existing Keystore storage, migration, batch protection and backup guards remain authoritative. No key, OAuth token, prompt, tool argument or memory payload is added to diagnostics.

Diagnostics add DIRECT_RESPONSE/AGENTIC mode, bounded tool count/reason, generation proof and connection check time. Session failures preserve known executing provider/model; their log boundary omits exception messages and stack traces. System Check separates inference and speech. No connection, generation or speech probe was executed during implementation.

## Deferred validation

Pure production source editing: no Gradle, compilation, build, lint, tests, adb, emulator, APK, benchmark or device acceptance. Later checks must cover Compose navigation/layout, real authentication and model access, typed/spoken equivalence, recovery, cancellation, wearable route evidence and fallback. A working model can fail the exact-response probe by not following the tiny instruction; that alone does not prove the account is unusable.

Deferred: voice cloning, speech-to-speech, ElevenLabs agents/WebSockets, automatic cloud activation or key import, billing dashboards, arbitrary TTS plugins, fuzzy freshness/search detection, another router or speech runtime.

Official references: [create speech](https://elevenlabs.io/docs/api-reference/text-to-speech/convert), [voice search](https://elevenlabs.io/docs/api-reference/voices/search), [voice settings](https://elevenlabs.io/docs/api-reference/voices/settings/get).
