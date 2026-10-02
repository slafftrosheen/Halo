# Agentic phone acceptance (RUNTIME-050 / AGENTIC-PHONE-001)

Local Qwen → tool discovery → tool call → authorization → Android action → structured
tool result → same Qwen continuation → user response.

Source reference for harvested concepts: `wangzhishou/OneBox` at
`8948af05801114d2871a208899f30aa5f2d2566d`. What was taken and what was deliberately
rejected is recorded at the bottom of this file.

## Evidence ladder

Never conflate these. A model that initialises is not a model that can act.

| Level | Means |
| --- | --- |
| CATALOGUED | metadata exists with pinned revision and SHA |
| COMPILED | the module builds |
| UNIT VERIFIED | the assertions in this repo pass |
| LOCAL TOOL LOOP VERIFIED | a prompt produced a validated tool call, Halo executed it and the model continued |
| PHYSICAL PHONE VERIFIED | the outcome happened on a real device, observed by the user |

## The loop as implemented

1. **Exposure.** A provider that declares how a Halo tool loop runs (`ToolProtocolReporting`)
   gets a bounded allowlist: `halo_discover_tools`, `halo_use_skill`, `halo_current_time`
   always; phone actions and screen tools only after discovery or skill activation.
   A 4096-token model is never handed the whole registry.
2. **Discovery.** `halo_discover_tools` is searchable (`query`) as well as exact (`toolIds`).
   Its result declares `halo_exposure_add=...`, and the Runtime re-plans exposure before the
   next provider turn. Availability, security and the allowlist are re-applied first.
3. **Skill loading.** `halo_use_skill` emits `halo_skill_activated=...` plus its tool ids, so a
   successful load changes the next request instead of being guidance nobody sees.
4. **Tool call.** Native tool calls when the artifact proved them; otherwise
   `<halo_tool_call>{"name":...,"arguments":{...}}</halo_tool_call>` parsed at the adapter
   boundary. Only exposed names, only a JSON object, bounded calls, no prose heuristics.
5. **Authorization.** Unchanged: per-call, durable, may become `WAITING_FOR_APPROVAL`.
6. **Execution.** Exactly once, with a resource lease that is always released, and an
   idempotency digest that refuses a repeated identical side effect.
7. **Result.** Every result begins with `tool_result: status=... action_truth=... reason_code=...`.
   `DISPATCHED` is never presented as `CONFIRMED`.
8. **Continuation.** The result returns to the same provider/model and run; no silent cloud
   fallback under LOCAL_ONLY.

## Actions and their status

| Utterance | Action | Compile | Unit | Physical |
| --- | --- | --- | --- | --- |
| "Set an alarm for 7 AM" | `halo_set_alarm` | yes | alarm clarification + truth |
| "Open Calculator" | `halo_list_apps` → `halo_open_app` | yes | app listing |
| "Turn the volume down" | `halo_volume` | yes | rejection taxonomy |
| "Call Alice" | `halo_find_contact` → `halo_dial_contact` | yes | dial URI + interpolation |
| "Open Bluetooth settings" | `halo_open_settings(section=bluetooth)` | yes | settings map |
| "What is on this screen?" | `halo_read_screen` | yes | semantic snapshot + redaction |
| "Press the Settings button" | `halo_read_screen` → `halo_ui_click` | yes | token freshness |

"Compile" and "Unit" are true in this repository. **Every row still needs physical-phone
verification**; nothing here has been run on a device by this batch.

## Physical checklist

1. Local mode: "What is 2+2?" → Qwen answers.
2. Local mode: "Set an alarm for 7 AM" → action or explicit approval → system alarm intent
   dispatched → Qwen's answer matches `action_truth=DISPATCHED`.
3. "Open calculator" → app discovery → launch dispatched.
4. "Turn the volume down" → volume changes.
5. "Call Alice" → lookup → clarification if ambiguous → approval → dialer URI with the number.
6. "Open Bluetooth settings" → Bluetooth settings surface.
7. Accessibility on: "Read this screen" → semantic snapshot.
8. Accessibility on: "Press Settings" → snapshot → opaque token → approval → click.
9. Accessibility off: one structured setup requirement, no `halo_read_screen` loop.
10. Kill Halo with an approval pending: the request recovers and does not execute twice.
11. Repeat a tool request after success: no duplicate side effect.
12. Under LOCAL_ONLY: no remote provider traffic.

## OneBox harvest

Kept as concepts: tool metadata independent of executor lifetime, first-turn bootstrap tools,
dynamic discovery that expands the *next* turn, dependency expansion, on-device tool
filtering, per-session loop state, serialization of side-effecting tools.

Deliberately not transplanted: the Hilt graph, database schema, screen routing, coordinate
visual automation and tool interfaces. Halo needs cross-app control, and its semantic
snapshot with opaque tokens, freshness and redaction is stronger than raw coordinate
clicking. Accessibility is never used for work a dedicated Android capability can do.

## RUNTIME-052 action checks

- Deterministic alarm: “Set an alarm for 6 AM tomorrow” reaches Clock once, reports
  `DISPATCHED`, never claims confirmation, and its response survives Home/Activity navigation.
- Tool discovery: the next provider history is USER, ASSISTANT tool call, TOOL result; the original
  request is not appended again, and the newly exposed action can be called.
- Recovery: after a durable tool outcome, restart and continue on the same provider/model without
  replaying the side effect.
- Clarification: missing input enters `WAITING_FOR_CLARIFICATION`; the answer resumes the same run.
- Android Assistant: a visual-only published response completes the invocation without waiting for
  TTS.


## PHONE-ASSISTANT-001 physical acceptance — pending

Automated checks pass (338 tests and debug assembly, 2026-10-01); no adb device or emulator image was available. Run this checklist on API 26 and API 37 before claiming physical compatibility.

- Select Halo as assistant, invoke it over an app, capture supported assist context, then hide/reinvoke rapidly. Recording must stop; a late callback must not cancel the successor. Secure/unsupported screenshots must remain unavailable.
- Speak a request using the selected local and cloud provider. Discover halo.assistant and halo.phone_actions, approve a sensitive action, and verify dispatch/receipt/response on the same provider. Repeat after denying contacts, microphone, accessibility, and API 37 local-network grants.
- Exercise VoiceInteractor confirmation, option selection, capabilities, stop, completion, abort, and cancellation. Each request completes or cancels once; unsupported commands cannot execute phone tools.
- Exercise Android SpeechRecognizer against Halo's recognition service: selected language/model, explicit stop, cancel, concurrent microphone ownership, unavailable model, unsupported external audio, and teardown. No transcript should start a conversation or survive as retained PCM.
- Open an allowlisted Halo page; change appearance/font/start page; restart and verify persistence. Explicit navigation must win over the saved start page. Read and stop shared speech output; background read requests must fail clearly.
- On API 29+, discover an app's native direct actions, approve one, then verify token expiration, invalidation and session hide prevent replay. App labels are untrusted; receipt stays dispatched until an external effect is proven. API 26 must report this capability unavailable.
- Enable accessibility and test semantic long press, back/home, notifications, quick settings and available lock/screenshot/media/menu actions. Unsupported or unadvertised global actions must disappear. Freshness, package policy, approval and takeover checks must still apply; arbitrary visual changes must not imply success.
- On API 37 verify assistant volume changes against the observed stream and foreground-service lifetime across listening, processing, playback and dismissal; test local-network permission denial/regrant and resolver-backed settings/launch behavior.

Record device/API, assistant role, granted services, provider/model, exact receipts, observed effects, and failures. Fix failures before marking this checklist complete.


## PHONE-ASSISTANT-002

Native actions use shared host skill execution independently of CHAT. Compound actions use AGENTIC; images use verified VISION candidates. Configure these roles independently in Intelligence, then use Vision Lab for explicit image capability probing under the current privacy policy. Physical and live-provider checks remain pending. See [host-first runtime](../engineering/PHONE-ASSISTANT-002-HOST-FIRST-RUNTIME.md), [Vision evidence](../engineering/VISION-ROLE-AND-CAPABILITY-EVIDENCE.md), [native acceptance](../testing/NATIVE-ASSISTANT-PHYSICAL-ACCEPTANCE.md) and [Vision acceptance](../testing/VISION-LAB-ACCEPTANCE.md).
