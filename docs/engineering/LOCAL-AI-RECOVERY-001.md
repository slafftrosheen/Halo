# LOCAL-AI-RECOVERY-001

Base: `587b97f25293af56d63220cd40b3b8be3b1bcb43`.

Ordinary Qwen3 conversation previously inherited the model template's default
thinking policy. Filtering reasoning from visible output did not disable its
computation. This repair carries provider-neutral reasoning intent from exact
role/target capability into the pinned LiteRT-LM SDK's real `ThinkingConfig`.
No model-name heuristics or `/no_think` prompt prefix is used.

## Policy

| Exact target | Role/path | SDK policy |
| --- | --- | --- |
| On-device, thinking-capable | CHAT / FAST / AGENTIC / VISION / unspecified | Off, budget 0 |
| On-device, thinking-capable | REASONING | On, budget 64 |
| Other targets | Any production role | Model default |
| Thinking-capable | Explicit verify / smoke / benchmark / direct control / tool probe | Off |

Both exact pinned Qwen3 artifacts advertise thinking support. Non-thinking
artifacts receive no thinking override. Provider requests cap enabled budgets at
64; the lower runtime boundary retains the generic SDK mapping for future callers.
`ToolUseMode.NONE` strips schemas and prompt-mediated action grammar at all three
provider generation boundaries. Existing NATIVE and HALO_PROMPT agentic loops
remain distinct and their initial and continuation requests explicitly disable
thinking. Role assignments and router policy remain user-owned.

## Evidence compatibility and privacy

Performance compatibility epoch is 2; old profile rows are retained but cannot
calibrate this generation policy. Role probe suite is 2; v1 rows remain history
and cannot recommend current configuration. No Room migration or deletion.

FAST and CHAT retain fixed prompts, limits 32/64 and temperature zero. REASONING
still grades only the visible final answer against 142 / Chris / 90, with 192
output tokens. Thinking-capable reasoning uses a separate non-greedy sampler
(temperature 0.6, topP 0.95, topK 20), and a 64-token reasoning budget. Scoring and
normalization are unchanged. Diagnostic traffic bypasses production EWMA.

Runtime starts report thinking policy/budget and residency. Completion reports
reasoning character count, including the SDK's separated channels, without raw
reasoning. Model Details exposes the policy in existing diagnostics. Local
interactive deadline diagnostics report model/backend/intent/elapsed time and
whether a visible token was observed; the 90-second guard remains unchanged.
Exports contain no prompt, response, reasoning, tool arguments or device ID.

## Upstream references inspected

- [Qwen3 LiteRT guide](https://github.com/google-ai-edge/LiteRT-LM/blob/main/models/qwen3/README.md)
- [Qwen3 template](https://github.com/google-ai-edge/LiteRT-LM/blob/main/models/qwen3/chat_template.jinja)
- [SDK config](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Config.kt)
- [SDK conversation](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt)
- [Native thinking contract](https://github.com/google-ai-edge/LiteRT-LM/blob/main/runtime/conversation/thinking_config.h)
- [Qwen's non-greedy thinking guidance](https://huggingface.co/Qwen/Qwen3-0.6B)

The locally pinned Android 0.17.1 AAR was inspected with `javap`; it exposes
`ThinkingConfig(Boolean, Int)` and nullable `ConversationConfig.thinkingConfig`.
No dependency upgrade was necessary.

## Validation — 2026-10-07

The required serial gate completed after targeted compile/test-runtime repairs
and resuming the remaining tasks following a host interruption. Final results:

| Task | Tests | Failures / errors / skips |
| --- | ---: | --- |
| :sdk:provider-sdk:test | 26 | 0 / 0 / 0 |
| :sdk:runtime-sdk:test | 44 | 0 / 0 / 0 |
| :engines:ai:test | 75 | 0 / 0 / 0 |
| :runtime:cognition:test | 212 | 0 / 0 / 0 |
| :runtime:observer:test | 11 | 0 / 0 / 0 |
| :providers:litert:testDebugUnitTest | 118 | 0 / 0 / 0 |
| :providers:sherpa-stt:testDebugUnitTest | 18 | 0 / 0 / 0 |
| :app:testDebugUnitTest | 217 | 0 / 0 / 0 |
| Total | 721 | 0 / 0 / 0 |

`:app:assembleDebug` succeeded. LiteRT host tests use Java 21 to load the pinned
SDK's Java 21 bytecode; Android compilation remains at Java 17. Exact catalogue
capability assertions and thinking-capable agentic fixtures are also checked by
a targeted regression run. No physical inference was run for this recovery batch.

Physical acceptance remains pending: the user elected to run it manually on
2026-10-07. No old physical result is presented as evidence for this build. Do not
close the batch's physical acceptance or select its following batch until that
one-pass report is available.

## One-pass physical acceptance — pending user execution

Use the recovery APK on the development phone. Record its source version and APK
SHA-256. Capture initial CHAT selection, routing policy, all role bindings and
installed artifacts before any test. No model acquisition, GPU, general device
smoke, or Benchmark is needed. Preserve history and restore original settings.

1. Select exact `qwen3-0.6b-dynamic-int4` for CHAT. In one fresh conversation send:
   - `Reply with exactly HALO_CHAT_OK and nothing else.`
   - `What is 17 multiplied by 8? Reply with only the number.`
   - `Remember COBALT-47.`
   - `What code word did I ask you to remember?`
   Capture each visible final answer, visible TTFT, total duration, thinking
   policy, reasoningChars, cold/warm and exact resolved model. CHAT must log OFF.

2. Start a fresh conversation and send these five turns sequentially:
   - `What is RAM in a computer? Answer briefly.`
   - `Why does more RAM help when several applications are open?`
   - `Does that mean more RAM always makes the CPU faster?`
   - `Give me one practical example using a phone.`
   - `Summarize what we just discussed in two sentences.`
   Record final output, TTFT and duration per turn, context retention, deadlines,
   model availability and any cloud fallback. Never collect hidden reasoning.

3. Send `Open Android Settings.` once. Record the actual foreground package and
   Halo's terminal action outcome. Return to Halo. Run Qwen's existing full
   synthetic tool/continuation probe with thinking OFF and capture all phase
   verdicts (especially initial call and continuation).

4. On-device → Run installed role matrix ONCE. Use only currently installed,
   eligible artifacts; do not run three separate per-model suites. Copy Halo's
   own current-device results verbatim. Per model record artifact SHA prefix,
   backend, context/threads/GPU layers, loadMs, cold visible TTFT, every warm TTFT,
   warm median, FAST verdict/sample count, CHAT x/3, REASONING x/3, reasoningChars,
   AGENTIC verdict/protocol/initial/continuation, VISION, pressure, stable and
   bounded failure code. Thinking-capable FAST/CHAT/AGENTIC must be OFF;
   REASONING must be ON with budget 64. Non-thinking artifacts use Model default.

5. Open Model Roles and record actual Configured / Measured / Suggested /
   Confidence for CHAT, FAST, REASONING, AGENTIC and VISION. Include suggested
   modelId and candidate modelIds. Confidence enums are only NONE,
   SINGLE_MEASURED or COMPARATIVE. Do not apply suggestions.

6. Switch CHAT Qwen → SmolLM2 135M → SmolLM2 360M → Qwen. Use one fixed short
   text request per target and capture `HALO_ROLE_TARGET_RESOLVED` for each exact
   provider/model. Preserve all explicit role bindings and routing policy.

7. Force-stop and relaunch once. Confirm suite-v2 evidence persists, suggestions
   reconstruct, CHAT selection and role bindings persist. Restore the original
   configuration and compare it exactly with the baseline. Old v1 rows remain
   history; they must not recommend current suite-v2 configuration.

One report must include all per-model values, all nine Qwen CHAT turns, safe
Settings effect, synthetic tool phases, matrix export, role projection, switching
and persistence. State deadline / cloud fallback / crash / ANR counts for this
acceptance window and reasoning character counts separately for CHAT/REASONING.
Do not infer zero for an unobserved counter. State a precise remaining defect if
conversation fails. No repeated micro-test loop or second evidence audit.

Note: performance-profile compatibility epoch is 2. The separate role-evidence
compatibility epoch remains 1; its changed generation policy is identified by
role probe suite version 2. Both are exact identity fields, not confidence scores.
