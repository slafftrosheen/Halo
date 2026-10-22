# HALO-INTENT-149 — Local AI intent routing and tool-channel separation

2026-10-19 · Android `main` · Source committed, **no Gradle build, automated tests or alpha-phone verification in this batch**. GitHub is storage; no CI/Actions invoked.

## Problem

The final `SpeechRule` used regex/deterministic matches for most routed voice and typed observations. `DefaultAiWorkloadPlanner` takes tool-use decisions from explicit routing metadata, so missed action intents became direct text answers, while broad `open`/search phrase matches could route noncommands as actions. `LiteRtLocalProvider.stream` also exposed prompt-mediated tool JSON fragments as `AiStreamEvent.Token` before parsing the complete tool result; the UI and TTS could show/speak raw action envelopes.

## Implemented

1. **Small local classifier** (`LocalIntentModelRouter`): Qwen3 0.6B INT4 is the default *candidate* if downloaded, SHA-verified and usable. The UI permits another installed SMALL generative LiteRT model or disabling AI intent. No cloud fallback, credentials, tool definitions, workspace memory, screen, images or chat history are sent. This model receives only one final utterance plus a compact EN/RU/LV/PL classification instruction.
2. **Conservative protocol**: strictly one whole label (`ACTION`, `ANSWER`, `UNCERTAIN`), 384-character input limit, 12-token output limit, 2-second cutoff, no tools, disabled thinking, temperature 0. Explicit native commands and explanation/model-only requests continue to bypass this advisory gate. No half-written ASR invokes it (`InterpretationRule.previewMatch` used by partial preview).
3. **Routing**: `SpeechRule` converts ACTION into `ToolActionIntent`, retaining existing SCREEN_OPERATION / WORKSPACE_LOOKUP hints when applicable. The model cannot supply tool IDs, parameters, Android permissions or authorization. Those remain with canonical tool exposure/permission/confirmation/exec gates. ANSWER and UNCERTAIN stay nonactionable; ambiguous questions cannot fall through a broad prefix regex. Missing, failed, nonqualifying or slow local models fall back to prior rules.
4. **Prompt-tool output**: for LiteRT `HALO_PROMPT` turns with exposed tools, stream tokens are withheld until the entire response is parsed. Valid parsed action envelopes produce tool calls through the structured provider result; only genuinely visible prose is emitted as text. Malformed, unexposed and partial envelope JSON is not shown/spoken as a reply. Parser now rejects trailing JSON garbage and oversized (>16 KiB) envelopes. Native tool-calling channel is unchanged.
5. **User setup**: On-device Models → **Understand requests locally**. Default on, effective only with a verified installed SMALL model; device-only classification. The panel chooses among installed compatible candidates, no benchmarks required to merely select one. Product strings translated in reviewed EN/RU/LV source and generated catalogue/XML. Polish UI locale is selectable with a partial catalogue for this settings panel and language picker; remaining screens still fall back to English and whole-app Polish localization is incomplete.

## Safety boundaries

- Action classification is *routing advice*, not permission.
- Do not convert arbitrary model JSON/prose into structured tool calls; only an actual verified provider tool protocol can make a call.
- No action from PARTIAL transcripts; no side effects or file reads from classifier.
- Native deterministic commands remain direct, owned by native capability dispatcher.
- The two heavy LiteRT stages are serialized by existing provider arbitration. Distinct intent/chat models may incur unload/load latency. If timeout/unavailable, fallback is deterministic, not remote.
- If a user explicitly asks about how to use a tool, it must not execute it.
- `UNCERTAIN` returns a nonactionable answer route; no implicit execution.

## Tests present as source but NOT run

- `SmallIntentSpeechRuleTest`: ACTION/ANSWER/UNCERTAIN, native precedence, partial-ASR bypass, screen operation hint preservation and fallback.
- `LocalIntentModelRouterTest`: strict whole-label parsing, rejects JSON/envelopes/label-with-explanation.
- `HaloPromptToolProtocolTest`: rejects trailing non-JSON and oversized envelopes.

## Alpha phone acceptance before claiming complete

1. EN/RU/LV/PL transcription → intent: direct instructions, questions about instructions, negated/quoted commands, ambiguous polite requests, composite app workflows.
2. Route evidence: app open, settings, alarm and flashlight use host; Gmail search+tap+type uses bounded screen tool route; tutorial queries remain conversation; unexposed tools are rejected; user approval remains required.
3. Compare installed Qwen3 0.6B and optional Qwen2 0.5B: correct labels, warm/cold percentiles, memory pressure, cancellation, low-end phones, offline behavior, TTS first audio impact. Disable toggle and remove selected model while app stays open.
4. Verify HALO_PROMPT agentic streaming never displays raw `<halo_tool_call>` or arguments, including partial streams, JSON failures and tool continuation.
5. Verify unrelated ordinary streaming chat remains token-by-token. Tools and native authorization unchanged.
6. Run compile and source tests **only when owner/alpha testing phase authorizes it**.

## Remaining work / future refinements

- Intent-confidence calibration and disambiguation presentation for UNCERTAIN rather than simply a nonactionable question response.
- Benchmark and persist per-device `INTENT` classification eligibility/latency before selecting an automatic non-default model.
- Tune same-model warm affinity to prevent cold Qwen intent loading ahead of a fast local chat turn.
- Translate any new product/tool-recovery strings and finish Polish whole-UI localization.
- Native endpoint/action round-trip acceptance for all relevant phone skills; not inferred from these source commits.
