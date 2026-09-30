# Provider and conversation stabilization

Batch: STABILIZATION-PROVIDER-001. Date: 2026-09-30.
Status: IMPLEMENTED / UNVALIDATED. Baseline: `6ae06d44e07940f5a2df3c3e1f2944236aeebd8c`.

Fetch, checkout main and fast-forward pull completed; main did not advance. Two pre-existing import fixes in AndroidTTSProvider and ToolTurnExecutor were preserved. This batch remains uncommitted. Additional unrelated workspace edits appeared during implementation and were left in place. No build, compilation, test, lint, emulator, adb, APK installation, benchmark, CI repair or physical acceptance was performed.

## Architecture found before edits

ProviderControlRepository already generated rows from the runtime registry, owned configuration, connection checks, bounded generation checks and provider catalogue refresh. ProviderCenterScreen and ProviderDetailScreen were reachable through AppShell, including the compatibility provider_setup route. AiProviderRouter owned target selection, role bindings and per-provider last-model persistence. ModelBrowserViewModel already performed cached filtering off the composition thread and ModelBrowserScreen used LazyColumn. IntelligenceScreen was already catalogue-free; the remaining gaps were summaries, navigation, raw status terminology and the legacy remote projections in IntelligenceViewModel.

DirectResponse, ToolUseMode.NONE, tool-free workload planning, context assembly gates, provider request-shape diagnostics and OpenRouter's conditional wire tools were already implemented. The interpreter's actual unknown fallback was ConversationIntent; no UnknownIntent existed. The route resolver nevertheless treated every non-conversational, non-owned intent as Agentic. Anchored search parsing could classify an explanatory sentence such as 'Search engines work how?' as a search command.

ElevenLabsTtsProvider, searchable voice/model settings, SpeechSelectionRepository, SpeechSynthesisRegistry and phone previews already existed. The production coordinator already called registry.pin and supplied the pinned provider to generic PCM playback. Android remained the automatic synthesis choice. ElevenLabs secrets were already covered by SecretMigrationV2 and the backup guard.

## Final source ownership and routes

- Provider implementations own ProviderConfigurationReporting descriptors and credential semantics. All currently registered inference provider types now report setup metadata; preset wrappers forward their underlying descriptor. Custom and Companion own endpoint/auth fields. Public endpoint metadata is remote; private/loopback/Tailscale endpoint metadata is local network without DNS probes.
- ProviderControlRepository owns setup projections, configured state, connection/generation proof and catalogue refresh. SecretBackedStorage delegates the protected-key set to SecretMigrationV2.SECRET_KEYS, removing a duplicate list. No secret is restored into a Compose draft. Blank saves preserve stored credentials; replacement and explicit removal remain separate.
- ConfigurationChanged now resets stale status for the original OpenRouter, OpenAI, Anthropic, Gemini and Ollama adapters without fetching models. Save, test and refresh do not select a provider. Only Use for Chat, model selection or role assignment changes routing. Existing per-provider last models remain authoritative and are summarized on provider cards.
- Intelligence is a bounded control center with active target, provider counts, model link, five role summaries, local management, privacy, Voice and System Check. It contains no catalogue. Context/privacy links to advanced settings, including the existing on-device temperature preference.
- Settings -> Intelligence -> Providers -> provider exposes credentials. The provider_setup compatibility route uses the same ProviderCenterScreen. Detail has separate Test connection, Test generation, Refresh models, provider-filtered Browse models, Use for Chat and Assign roles actions. Credential UI covers OpenRouter, OpenAI, Anthropic, Gemini, Groq, Cerebras, xAI, OpenCode Zen, Companion and Custom. Endpoint UI covers Ollama, Companion and Custom. Gemini API-key and Google authorization flows remain present; OAuth is a memory-only access-token session and needs authorization again after process recreation.
- Model Browser shows the entire cached catalogue by default, local search, provider/location/capability filters and All/Free/Paid-account-dependent cost modes. Compact rows show name, provider, capabilities, location and Chat selection. No per-keystroke fetch or status I/O is introduced. Selecting an unconfigured provider opens its setup without saving a broken target. AGENTIC selection remains restricted to concrete tool-capable models.
- Voice root contains input, output and routing summaries. Recognition model/language controls live on settings/voice/input. Output selection and cloud-synthesis fallback live on settings/voice/output. ElevenLabs key, catalogue, voice/model IDs, stability, similarity, speed and fixed phone-only preview live on settings/voice/elevenlabs. Selection requires key plus voice/model choices; credentials alone never opt into cloud speech.
- Home provides exact selected-provider recovery navigation and an 'Ask anything' hint. System Check separates AI providers, Chat/Agentic/Vision selection and speech provider status, using readable proof labels.

## Conversation and execution contracts

| Route | Owner | Workload and tool requirement |
| --- | --- | --- |
| CHAT / DirectResponse | Model answers | CONVERSATION, TEXT, CHAT, NONE for ordinary text |
| AGENTIC | Model and host tool loop | Positive action evidence, AGENTIC, REQUIRED where action is necessary |
| DETERMINISTIC | Named host executor | Existing alarms, opening apps, calls, navigation, media, reminders and explicit search |
| LOCAL FACT | Runtime fact resolver | Local date/time answer, no model and no tools |

The conservative resolver defaults unsupported/unknown non-action language to DirectResponse. Explicit model-only requests and informational prefixes are checked before command parsing. Bounded English action cues identify workspace retrieval, screen operations, flashlight changes and message sending; they do not use loose app/alarm/navigation keywords. Known deterministic ownership remains intact. Machine reasons include ACTION_INTENT, WORKSPACE_LOOKUP and SCREEN_OPERATION, never UNKNOWN_INTENT.

NONE requests receive zero tool definitions, no halo_discover_tools or mandatory skill activation, and existing direct-response framing. Tool capability and turn requirement remain independent. A required action with no usable target/tools produces a supported capability failure; a normal model answer is not rejected for lacking tool capability. HALO_CONVERSATION_ROUTE records intent type, confidence, route, mode, role and action reason without user text. HALO_PROVIDER_REQUEST_SHAPE records toolsIncluded=false and toolCount=0 for direct requests.

OpenRouter/free remains a portable TEXT router, never upgraded to tools. A concrete tool-capable model can be assigned to Agentic independently. Explicit role bindings now also prevent cross-provider generation fallback. Local-only exclusion applies to every role; public Custom/Companion endpoints cannot inherit a local-network declaration.

Provider error mapping uses bounded recovery copy for authentication, rate limits, network failures, unavailable models and unsupported capabilities. Unknown provider exceptions no longer surface their raw message on Home.

## Speech transaction contract

TtsRequested -> HaloSpeechOutputCoordinator -> SpeechSynthesisRegistry.pin -> selected PCM synthesis -> routed AudioTrack playback -> host route evidence.

The selected provider and ElevenLabs voice/model/style are pinned once per spoken response. ElevenLabs completes its bounded internal chunks before host playback. A synthesis fallback becomes sticky Android PCM for the remainder of that pinned response. The incomplete-selection check now runs only before fallback, avoiding repeated rejection of the Android remainder. Speech telemetry no longer includes a voice-ID prefix or spoken-text length. Generic playback already accepted PcmSpeechSynthesisProvider; the stale Android-specific import is removed.

Synthesis fallback and route fallback are independent. Android fallback PCM still uses the original route evidence. DISALLOW_PHONE wearable turns cannot acquire the phone speaker due to a synthesis failure. Settings previews use only 'Halo voice test.', an explicitly named synthesis provider and PHONE output; they have no model/history/memory/tool context and disable synthesis fallback. Automatic remains Android TTS.

## Deferred acceptance matrix (DO NOT RUN IN THIS PASS)

| Setup | Later sequence |
| --- | --- |
| OpenRouter, OpenAI, Anthropic, Gemini API key, Groq, Cerebras, xAI, Zen | Enter key, Save, confirm Configured-not-checked, Test connection, Refresh models, choose model |
| Gemini OAuth | Enter project, authorize Google, Test connection, Refresh models; reauthorize after process recreation |
| Ollama | Enter endpoint, Save, Test connection, Refresh models |
| Companion | Enter endpoint and credential, Save, Test connection, Refresh models |
| Custom | Enter endpoint and None/API key/Bearer auth, Save, Test connection, Refresh models |

For every provider verify blank key save preserves the secret, explicit removal clears it, connection/generation proof clears after reconfiguration, and save/test/refresh never change Chat or other role selections. Generation checks use empty context/history, NONE tools and at most eight output tokens.

With OpenRouter selected, run later: 'Give me an apple pie recipe.', 'Tell me five interesting facts about ancient Rome.', 'Tell me three facts about Latvia.', 'Explain how a transistor works.', 'Explain recursion.', 'Write a short birthday message.', 'Write a limerick.', 'What caused the fall of the Roman Empire?', 'Who was Ada Lovelace?', 'Translate this sentence.', and 'Summarize what I just said.' Expected: DIRECT_RESPONSE / NONE / CHAT, toolsIncluded=false, toolCount=0 and an ordinary answer.

Contrast 'How does an alarm clock work?' with 'Set an alarm for 7 AM.'; 'Explain Spotify.' with 'Open Spotify.'; 'Search engines work how?' with 'Search the web for today's weather.' Explanations must be direct; commands retain deterministic/action ownership. 'Use my notes about X' requires workspace evidence. 'Answer from your model, don't search' must expose no tools. Historical facts must not automatically become web queries. Implicit current/fresh-information requests remain limited by the existing interpreter; explicit web search uses its existing owner.

Later ElevenLabs sequence: Settings -> Voice -> Speech output -> Configure ElevenLabs, enter key, Save, Test connection, Refresh voices/models, choose IDs, select ElevenLabs, preview on PHONE, request a normal spoken answer. For HeyCyan, confirm selected synthesis produces PCM on the HeyCyan route and that DISALLOW_PHONE prevents leakage even when synthesis falls back. Verify sticky provider choice, cancellation and route loss. Android automatic selection must persist independently from Chat.

Kill/relaunch later: verify Chat target, per-provider last models, role bindings, TTS selection and ElevenLabs voice/model IDs. Secrets persist securely, are never displayed and never appear in Brain exports; restore may require authentication. Check clean-install key reachability within Settings -> Intelligence -> Providers -> OpenRouter and Settings -> Voice -> Speech output -> ElevenLabs, without catalogue scrolling.

## Uncertainties and deferred scope

Compilation, Compose layout on real phones, actual provider/account access, OAuth consent and API compatibility, large catalogue behavior, provider reconfiguration races, device audio proof, cancellation and restart persistence require later validation. English anchored cues are conservative source rules, not a general semantic-language parser. Existing secret migration already includes ElevenLabs, so no new migration version was necessary. Existing backup uses an allowlist and records only configured booleans, never credential values. No new inference vendor, TTS provider or major runtime feature was added. CI, hardware acceptance and performance measurement were intentionally deferred.

SOURCE COMPLETE.
BUILD NOT RUN.
TESTS NOT RUN.
DEVICE ACCEPTANCE NOT RUN.
