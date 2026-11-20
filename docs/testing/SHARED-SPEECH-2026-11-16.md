# Shared OpenRouter speech acceptance — 2026-11-16

Worktree based on `257a0136349f933783eb64d96aa726c2c4206359`. Changes remain uncommitted.
Status: desktop gates, installation, live generated speech/transcription, actual user microphone recognition, A2 playback and native model configuration passed. ChatGPT sign-in, account-specific model discovery and completed subscription inference also passed after the response-format correction. No issue closure implied.

## Scope and contracts

- OpenRouter STT uses the existing protected `openrouter_api_key`, a discovered transcription model, and the dedicated multipart `audio/transcriptions` endpoint. PCM capture is wrapped as WAV with its actual rate/channels; inputs are bounded to two minutes and responses to 1 MiB. No OEM recognizer, inferred subscription entitlement, extra credential, or conversation/audio-completion substitute.
- Discovery uses `models?output_modalities=transcription`; only models advertising transcription are selectable. Language is supplied only as a two-letter code; automatic recognition omits it. Confidence is left unknown when not returned.
- The existing Local/Power profiles own `speech_stt_provider` and `openrouter_stt_model`. Automatic stays local; a selected OpenRouter recognizer operates in Power. Local mode resolves an installed local recognizer and the final remote policy independently blocks network dispatch.
- `RecognitionCaptureContext` is pinned before microphone capture, including diagnostic capture. Pre-roll from a different provider/revision is discarded. Local/missing/stale authority cannot become a new remote grant at recognition time. Existing cancellation reaches the actual HTTP call. Local memo transcription explicitly rechecks its local delegate under the recognition lifecycle lock.
- Gemini TTS live evidence on the phone showed HTTP 400: MP3 is unsupported for the chosen Gemini model. The three documented Gemini TTS model IDs now request PCM and use Google's specified 24 kHz, mono, signed 16-bit little-endian format. Unknown models retain MP3 decoding; JSON, unexpected MIME types, containers and incomplete PCM samples are rejected.
- A prior ChatGPT trace observed DNS failure during the browser handoff. The host now waits for foreground/unlocked adult authority before exchanging the callback code; the subsequent real sign-in succeeded. Existing timeout and Local/family cancellation remain. Added diagnostics contain enum stage names only, never callback URLs, credentials, claims or response bodies.

Primary contracts: [OpenRouter transcription](https://openrouter.ai/docs/guides/overview/multimodal/stt), [OpenRouter speech](https://openrouter.ai/docs/guides/overview/multimodal/tts), [Google audio formats](https://ai.google.dev/gemini-api/docs/speech-generation), [OpenAI token-sharing contract](https://developers.openai.com/siwc/token-sharing-open-source).
ChatGPT subscription audio/transcription is not advertised; the STT option uses OpenRouter billing.

## Canonical owners and assistant parity

`OpenRouterRecognitionControl` is shared by Listening settings and the runtime-registered `OpenRouterRecognitionTools` executor. It saves through `PrivacyModeStore`, reloads the existing provider graph and verifies profile readback.

| Operation | Descriptor | Contract |
|---|---|---|
| Read settings and cached models | `halo_openrouter_transcription_status` | Closed `offset` schema, 50 entries/page, bounded names/IDs, revision and catalog source version, no key values or network |
| Discover supported models | `halo_openrouter_transcription_models` | Empty closed schema, existing key, Power policy, no audio capture/upload |
| Select Power recognizer | `halo_openrouter_transcription_configure` | Exact model ID, profile revision and catalog source version; rejects stale/unknown selections; confirmed only after readback |

Operability version 1, capability `host.speech.openrouter.transcription`, JSON closed results; EDIT for selection and QUERY for reads/discovery. Discovery and mutation require explicit approval and NEVER_REPLAY. Foreground adult guards apply before owner access. A failed/uncertain save requires a new read; it is not replayed. Process restart reads the durable mode profile. Local/Power switching is a separate existing operation; these tools do not enable Power themselves. Typed and STT-final intents use the same registered executor through CognitiveSession.

`OpenRouterRecognitionToolsTest` exercises real descriptor registration and `ToolOperabilityReview`, stale-source/Local rejection, shared-owner readback, and secret/unknown-argument rejection. All three tests passed; the registered-feature review returned no problems.

| Workflow | Desktop typed contract | Native UI | Actual spoken operation | Durable phone readback |
|---|---|---|---|---|
| Discover transcription models | PASS | PASS | NOT_RUN | In-memory catalog |
| Save Power transcription model | PASS | PASS | NOT_RUN | PASS after app restart |
| Return to Local recognition | PASS | PASS | NOT_RUN | Local selection shown; Power restored |

## Verification

- First provider gate exposed a multipart test that incorrectly assumed no intervening headers. The assertion now accepts valid multipart headers while verifying exact model/language values and WAV bytes.
- Before the sign-in host change, 47 OpenRouter tests, 35 local recognizer tests and 26 speech-engine tests passed. The subsequent full gate includes core tests, focused app tests, account tests, and both APK builds.
- Account diagnostics initially displaced a trailing test-clock callback; regression tests caught invalid fixture-time validation. Constructor ordering was corrected without weakening token expiry checks.
- Full gate `.gradle/issue-audit/shared-stt-tts-gates.log`: **BUILD SUCCESSFUL, 27m40s**, 587 tasks. **1,026 tests passed**, zero failures/errors: 852 core (179 suites), 47 OpenRouter, 35 Sherpa STT, 7 OpenAI account/subscription, 85 focused app tests (15 suites). Both APKs assembled. Localization generation/check passed for 4,789 strings in each language.
- Final follow-up package gate `.gradle/issue-audit/shared-speech-package.log`: **BUILD SUCCESSFUL, 3m34s**, 491 tasks; 47 OpenRouter regressions passed again and both APKs assembled. Buffered STT metadata reports no streaming; the native test's compiled JUnit method returns void.
- Installed both APKs with `install -r`, retaining app data. Main SHA-256 **d51fdf1fd899dcf5d03472e2aae7bcd4986243d8047e64ef5060a4a62bc6ef3f** matched the installed phone package; test SHA-256 **20d0422d73a17bc19de9d8bc76707e26b0671325bfc87e292af16d3706dc116e**. Phone update time 2026-11-16 11:44:58.
- Native `OpenRouterSpeechPhysicalTest`: **PASS, 1 test, 23.853 seconds**, log `.gradle/issue-audit/phone/shared-speech-native.txt`. Actual Gemini synthesis returned 176,640 PCM bytes, 24 kHz mono. Actual `openai/gpt-4o-mini-transcribe` discovery and transcription matched the fixed generated phrase. Both requests used the same protected OpenRouter credential. No microphone, playback or saved-setting changes occurred in this fixture.
- Native Listening UI loaded the transcription catalog, selected OpenAI GPT-4o Mini Transcribe and confirmed save. Voice settings independently showed OpenRouter recognition **Ready**, with the existing Russian language and OpenRouter speech output. Both selections survived force-stop/reopening after a Local/Power round trip.
- Actual user microphone test **PASS**: phone capture 5,780 ms, 16 kHz mono, recognition stage RESULT, no error, transcript gate Accepted. The requested non-private Russian phrase was recognized as “Это проверка голоса Хейло.” Evidence: `.gradle/issue-audit/phone/stt-mic-wait-1.xml`. This verifies real STT, not spoken assistant execution of the configuration tools above.
- Actual Gemini/Kore playback **PASS** through A2: requested and detected output both `A2_F974`, playback completed. User separately confirmed hearing the phrase through the glasses. Evidence: `speech-a2-result-1.xml` / `speech-a2-result-2.xml`. The one-shot test did not change saved output routing.
- Local switch showed automatic on-device recognition and phone voice, rather than either OpenRouter provider. Its current local recognition profile reports **Not installed**; no successful Local microphone recognition is claimed. Power was restored and showed both OpenRouter providers after restart (`voice-local-status.xml`, `power-restored.xml`, `power-restart-speech.xml`).
- First ChatGPT retest was interrupted by USB disconnection before starting OAuth. After reconnection, the user approved the browser flow and returned to Halo. At 12:42 local time, the content-free trace reached REGISTERED → TOKEN_EXCHANGE → TOKEN_RECEIVED → VERIFY_IDENTITY → IDENTITY_VERIFIED → CONNECTED. Native account UI confirmed connection; its connection check passed and loaded **eight** account-specific subscription models. GPT-5.6-Luna was selected for the fixed response check. No API key was used by this provider.
- Subscription response acceptance initially **FAILED**: actual HTTP 200 omitted Content-Type, and the existing strict SSE MIME check rejected it before parsing. Confirmed by `.gradle/issue-audit/halo-account-debug-7.log` (`EXPECTED_EVENT_STREAM`, `RESPONSE_STATUS 200`, `RESPONSE_MEDIA_TYPE ABSENT`), without token/body inspection. The new regression reproduced that exact rejection before the fix. The provider now explicitly requests SSE, permits an absent MIME header and still requires bounded valid events plus `response.completed`; explicitly incompatible MIME, JSON/HTML bodies, partial streams and missing terminal events remain rejected.
- Follow-up `.gradle/issue-audit/chatgpt-stream-gates.log`: **BUILD SUCCESSFUL, 2m21s**, 491 tasks; **nine** account/provider tests passed (four account, five response), including both new response-format regressions. The earlier 1,026-test gate remains the core/app/speech baseline; those unchanged modules were not rerun for this provider-only body change.
- Final main APK SHA-256 **800d9d28e84b7d5fdd9048825a3473a57bedd54658a067f7c3f652f6dc5dcc64**, installed with `-r` and verified against the phone package at 12:56:12 local time. Test APK unchanged. Connected account, eight cached models and selected `gpt-5.6-luna` survived replacement and process restart.
- Final native subscription connection and response check **PASS**: `account-fixed-response-1.xml` shows “Model response proven”, connected account and GPT-5.6-Luna. The existing owner sent only `Reply exactly HALO`, with empty context and no tools/history, through `ChatGptSubscriptionProvider`; success requires a nonempty completed response. This proves this model's subscription inference, not every listed model or actual spoken assistant-tool execution. ChatGPT is now the Power chat default. No OpenAI API-key fallback was used.
- `account-final-speech-retained.xml` confirms OpenRouter recognition **Ready** and OpenRouter speech output remain selected. All temporary debugger helpers finished/detached; ADB forward listing is empty. No OAuth attempt or microphone recording remains active.

Physical A2 video/audio transfer acceptance and the unknown physical-original retention status remain documented separately in `A2-MEDIA-REFRESH-2026-11-13.md`. This work does not delete files or clear app data.
