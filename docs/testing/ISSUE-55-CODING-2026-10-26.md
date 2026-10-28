# Issue 55 — Android cloud reliability source pass, 2026-10-26

Baseline: `7257b4706`. This is a code-only continuation of the Android acceptance campaign.
No build, test, ADB, cloud/model call, paid action, CI, commit or push was run in this pass.
The live issue and existing 2026-10-24/25 ledgers were read as historical evidence, not rerun evidence.
Issue 55 remains open; no acceptance checkbox is promoted from source inspection.

## Concrete source gaps fixed

The canonical `providers/openrouter/OpenRouterProvider.kt` used blocking OkHttp `execute()`
for auth, catalog, text and streaming, with no coroutine-lifetime cancellation hook covering
headers plus response-body reading. Cancelling a turn could leave the blocked transport alive
until its timeout. The same paths read unrestricted response strings/SSE lines and assembled
tool arguments without an application memory bound before parsing.

`OpenRouterTransportBounds.kt` now provides a scoped cancellation child installed before
transport execution and retained until response consumption finishes. Cancellation calls
`Call.cancel()` while headers or body reads are blocked. Cleanup cancels the child and closes
the response through `use`; socket IO failure during cancellation rethrows cancellation rather
than becoming a synthetic provider error. Existing request/stream/policy owners are reused.

The four canonical text/metadata paths consume bounded bodies. Streaming preserves final
unterminated-line handling, null optional tool fields, incremental Token/ToolCallDelta events,
finish-reason/[DONE] semantics and the existing final response owner. Limits are checked before
appending text/tool fragments. Existing image-payload code keeps its separate bounded transport.

| Resource | New bound |
| --- | --- |
| Text/auth JSON HTTP body | 8 MiB |
| Model catalog HTTP body | 16 MiB; cached JSON is also bounded before parsing |
| HTTP error diagnostic payload | 64 KiB prefix; HTTP status and Retry-After still reach the existing error mapper |
| Individual SSE line | 1 MiB |
| Cumulative SSE text on the wire | 32 MiB, including comments/metadata |
| Final assembled answer | 4 Mi characters |
| Tool calls | 32, with streamed indices 0–31 |
| Arguments per tool | 1 Mi characters |
| All tool arguments in one result | 4 Mi characters |
| Tool call ID / function name | 512 / 240 characters |

Oversized successful payloads fail as `INVALID_RESPONSE` with content-free size-limit text;
they are never truncated into a successful answer or executable partial tool call. Error
payload truncation retains status classification instead of allocating an unlimited server
error body. Legitimate unusually large completions, catalogues or tool batches exceeding
these limits now fail explicitly; callers must use bounded requests or split the workflow.

The production default OpenRouter client also disables connection-failure retries and HTTP/SSL
redirects. This prevents implicit repeat dispatch and cross-endpoint forwarding for the normal
Android factory, which layers its existing dispatch-policy interceptor onto that default client.
No model selection, fallback, pricing consent, Private/Full Power or Kids authority was changed.
No provider credentials or response content are logged by these helpers.

## Requirement trace and remaining acceptance

| Issue-55 workstream | Existing owners inspected / source disposition | Acceptance status in this pass |
| --- | --- | --- |
| P0 cloud faults, Stop and memory pressure (#43/#39) | OpenRouterProvider, ProviderControlRepository, free-routing owner, existing provider error/Retry-After mapping; transport cancellation/bounds fixed above | NOT_RUN: cancellation before/after headers, blocked body/SSE, over-limit body/line/tools, ordinary streaming, HTTP 401/402/429/network/timeout, credential/configuration race and recovery |
| P0 cloud privacy/cost | AndroidProviderFactory applies existing remote dispatch policy; existing OpenRouterFreeRouting keeps automatic free selection constrained; no paid route added | NOT_RUN: Private network denial, exact provider/model capabilities, consent, genuine zero-cost E2E |
| P0 voice (#38/#33) | Existing speech/CognitiveSession and Home projection retained; no microphone/provider runtime was activated | NOT_RUN: genuine 10-turn speech, barge-in/mute/stop/replay, audible TTS, Bluetooth and lifecycle |
| Typed/voice domain actions (#36/#54) | Existing ToolRegistry and domain owners retained; this batch adds no tool or dispatch authorization | NOT_RUN: three actual cross-tool workflows, approval/cancellation/stale/replay/receipts through permitted cloud |
| Workspace/Study/Kids/media (#28/#29/#35) | Existing Room/context/media/privacy owners retained; concurrent issue-60 Screen continuation now rereads exact source records through those owners | NOT_RUN: complete source-to-Study/export, process death, permission denial/recovery, physical ink and attachment journeys |
| Cross-cutting presentation | Existing UI/accessibility/localization owners retained; issue-60 has its own EN/LV/RU/PL source report | NOT_RUN: full 360/412dp/200%/TalkBack/light-dark/tablet matrix |
| Commerce (#42) | No purchase/billing/store-owner action in this source batch | NOT_RUN: Android purchase UX; actual Play owner/transaction acceptance remains separately authorized |

Existing historical PASS/FAIL/BLOCKED reports retain their exact earlier APK/source witnesses.
This pass does not assert they describe the current unbuilt worktree. Free-provider outages or
capability limits remain external nonblocking cases under the issue's explicit closure exception;
they do not become false cloud PASS results. All new transport changes still need the deferred
software and Android acceptance gate before alpha qualification.
