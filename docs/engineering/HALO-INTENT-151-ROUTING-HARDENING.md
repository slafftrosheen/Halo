# HALO-INTENT-151 — Residency, atomic tool calls and clarification recovery

2026-10-19 · Android `main` · source code committed. No Gradle build, tests, CI or device acceptance performed.

## Why this follow-up exists

HALO-INTENT-149 introduced a bounded on-device Qwen-class intent gate but could trigger LiteRT model switches ahead of a conversational turn. The `HALO_PROMPT` parser could accept an early valid tool call from a response that contained a later malformed or over-budget call, and a prompt model could hallucinate tool markup on a tool-free stream. `UNCERTAIN` silently returned to ordinary chat instead of requesting clarification.

## Changes

1. **No unintended resident model eviction:** `LiteRtLocalProvider.residentModelIdForIntent()` and `inferenceBusyForIntent()` expose content-free snapshots, without loading models or probing the device. `LocalIntentModelRouter` uses a nonblocking mutex and a pure `shouldDefer` policy: skip optional intent inference if another LiteRT model is resident, a native generation is active, or another classifier is running. The normal deterministic route remains available; no remote classifier. The existing 2-second per-turn timeout still applies. Cold classification is best-effort, not a guaranteed low-latency path.
2. **Atomic HALO_PROMPT envelope admission:** one invalid/truncated/unexposed/oversized/excess envelope invalidates the **entire response's** prompt tool batch. No valid-prefix execution; no partial success language or raw JSON. Stray closing markers are invalid. The provider returns a plain, bounded recovery reply instead of treating malformed content as a completed action.
3. **Native and prompt cannot double-execute:** tool calls emitted on a native channel are checked against the tools actually exposed on that turn. When prompt and native channels both contain calls, neither channel executes; the user gets a safe retry response. `ToolUseMode.NONE` drops any structured calls and rejects explicit prompt markup.
4. **Streaming without tool marker leaks:** tool-enabled HALO_PROMPT answers remain buffered to completion; tool-free HALO_PROMPT answers now pass through `HaloToolMarkupStreamGuard`. It holds only a short suffix to prevent partial `<halo_tool_call>` / `</halo_tool_call>` tags from reaching chat/TTS while preserving the remainder of ordinary incremental text. A full response with markers is replaced by the existing tool-unavailable recovery; previously emitted safe, nonmarker prose cannot be retracted.
5. **Intent clarification:** `IntentNeedsClarification` routes `UNCERTAIN` to a nonexecuting response in EN/RU/LV/PL, asking whether the user intended an action or an explanation. No second AI inference or tool permission is granted.
6. **Fallback while a chat model is resident:** conservative, anchored RU/LV/PL device, workspace and screen-workflow cues were added to `ConversationSemantics`. Explanatory questions in these languages explicitly remain conversation, not actions. These are fallback hints only, not a substitute for language-specific speech/intent qualification.

## Source regression cases (not executed)

- `LocalIntentModelRouterTest`: no-chat-eviction eligibility; strict single-label output.
- `HaloPromptToolProtocolTest`: invalid second action revokes entire batch; oversized/extra/partial/orphan markers.
- `HaloToolMarkupStreamGuardTest`: ordinary streaming reconstructs exactly, split open/close tags never streamed.
- `SmallIntentSpeechRuleTest`: `UNCERTAIN` produces localized clarification and preserves deterministic/native precedence.
- `InterpretationRouteTest`: clarification is Unsupported/nonexecuting, not Agentic.
- `PoliteCommandTest`: EN and RU/LV/PL action vs explanation examples.

## Physical acceptance gates

- [ ] Confirm Kotlin/Android compilation and automated regression tests in an explicitly authorized test phase.
- [ ] Qwen3 0.6B installed and warm: final utterance classified while no heavy inference is active, without losing or delaying subsequent tool inference.
- [ ] LFM/other chat model resident: tiny intent gate does **not** evict it; deterministic fallback works.
- [ ] Cold intent model: 2-second bounded attempt fails cleanly, no native resource lease stranded and no ASR/voice deadlock.
- [ ] EN/RU/LV/PL: actionable vs explanatory vs ambiguous examples; please/negation/quoted text/composite Gmail workflows.
- [ ] Tool calls: exposed, unexposed, two valid, valid+invalid, too many, malformed JSON, stray end tag, split streaming tag. Confirm no duplicate side effects, no raw JSON in Activity/TTS/conversation history, and safe recovery.
- [ ] Native control and safety: alarms/timers/settings/flashlight/actions and confirmation policies unaffected; no repeated execution after cancellation.
- [ ] UX: clarification displayed and spoken using origin language; user can answer it naturally on next turn. Full Polish app UI localization remains open.
- [ ] Source metrics: collect only noncontent classification outcomes and timing for benchmark; do not include utterance text/arguments in exported diagnostics.

## Scope and caveats

The intent model **does not itself execute tools**, and a deterministic fallback cannot handle every non-English expression. It does not solve tool generation quality in all LiteRT artifacts or establish a phone performance result. Real device latency and provider behavior must still be qualified in #36. No GitHub Actions.
