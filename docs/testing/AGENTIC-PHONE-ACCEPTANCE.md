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
