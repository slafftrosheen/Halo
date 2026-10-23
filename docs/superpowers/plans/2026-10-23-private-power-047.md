# Issue 47: independent Private / Full Power profiles

Authority: `docs/04-architecture/PRIVATE-POWER-MODES-IMPLEMENTATION.md`.
Starting main: `c474e35df750ebeb896b3eacc8bacf154c344bd6`.

User sequence: finish implementation and regression sources before executing builds/tests;
then repair failures, validate, push main, and close only with an honest receipt.

- [x] Inventory every shipped outbound owner and profile migration key.
- [x] Extend PrivacyModeStore with versioned, isolated, atomic profile persistence and safe activation.
- [x] Restore profile route/speech/context through existing owners; preserve secure credential ownership.
- [x] Enforce a shared current-policy contract at actual outbound clients, including cancellation and stale work.
- [x] Add first-enable sharing disclosure, two profile panels, route summary, independent spend control and bounded receipts.
- [x] Add migration, corruption, activation, dispatch, cancellation and permission/spending regression sources.
- [x] Complete read-only review and source fixes before the first build/test gate.
- [x] Run serial build/unit/native-render gates: 143 tests, zero failures/errors/skips; six renders. Final device rerun unavailable (ADB disconnected), explicitly tracked in #43.
- [x] Publish verified implementation and evidence on main; final phone/provider qualification is explicitly retained in #43 with the #47 delivery receipt.

## Inventory and migration contract

Existing request owners: OpenAI, Anthropic, Gemini (including auth/discovery), OpenRouter,
Groq, Cerebras, xAI, OpenCode Zen/Companion, custom OpenAI-compatible, Ollama,
ElevenLabs speech, OpenRouter web/account usage, WebInsight fetch, MCP HTTP,
Google Mail reader. Screen/workspace/memory/study/audio/video enter provider requests
through the existing context/route owners and require category checks there as well.
LAN inference is off-device: `isLocal` is not an exemption from Private.

Model acquisition is a separate explicit setup activity (LiteRT/Hugging Face,
Sherpa/Whisper, Kokoro); it must be inventoried and disclosed separately from
AI/context transfer. Android browser/SENDTO/device actions retain their existing
consequential-action and OS boundaries. No duplicate executor or permission guide:
issue 48 already delivered the guide.

Profile keys: active_ai_provider/model, ai_routing_policy, role_* bindings,
ai_last_model_*, workload.execution.v1.*, workload.role.embedding.v1,
speech_stt_provider/tts_provider and policy_* context categories. Endpoint,
credentials, Android grants, Kids preferences, action confirmation and spending
consent/limits remain outside profiles. Known provider IDs/features are explicit;
unknown features/providers fail closed. One snapshot is the commit record; legacy
mode is only a compatibility projection. A legacy Private selection never enables
Full Power. Existing shared settings migrate to the selected profile; the other
profile gets documented defaults. Legacy Cloud keeps its profile but requires the
new informed sharing disclosure before further remote dispatch. Legacy paid-media
consent was automatically written by mode selection and cannot prove spend consent.

Receipts contain bounded provider/operation/category/outcome metadata and nullable
provider-reported cost only. Never prompts, URLs, credentials, images or audio.

### Actual dispatch and setup boundaries

`PrivacyModeStore` owns the shared revision and revocable leases through the SDK
contract. Factory-created provider HTTP clients, hosted speech, web/search/account,
MCP and Mail clients check that authority before DNS and at the actual connection.
Coroutine intent retains the original revision through route resolution and typed
provider calls; changing provider inside an inherited intent is rejected. Redirects
cannot silently grant a second destination. WebView extraction separately acquires
the same authority, checks resource/navigation requests and cancels its coroutine
and destroys the view on revocation. LAN Ollama has no local exemption.

Model acquisition uses separate explicit setup owners: LiteRT `syncHubModels` ->
`syncCatalog` -> Hugging Face metadata, LiteRT artifact download, Sherpa/Whisper
artifact download and Kokoro artifact download. They transfer public model metadata
or artifacts and an optional explicitly supplied hub token, not inference context.
They remain user-requested setup in Private; do not interpret the mode as a global
UID firewall or a prohibition on deliberate model installation. No automatic hub
sync was added. Existing browser/SENDTO and Google SDK account authorization retain
OS ownership; an already-issued Google authorization cannot be recalled by an HTTP
lease. Mode does not grant OS access, child access, action or spending approval.

The new bounded receipt store owns the visible activity page. Legacy bounded answer
history remains a compatibility sink for existing runtime hooks; Clear removes
both stores. HTTP 2xx receipts say Replied, not semantic task success. Actual result
owners may report Completed; costs attach only to their exact HTTP lease and only
when reported by the provider. Unknown costs stay unknown.

Qualification source includes migration/corruption/activation/revision ABA,
category/provider/feature gates, actual HTTP cancellation, error receipts, exact
cost correlation, route fallback and six native disclosure layouts. Android tests
exercise production provider/web/MCP/Mail objects through loopback TLS with synthetic
credentials, and rendered WebView cancellation. They never touch user profiles or
send external provider requests. Live provider quality, real billing and global UID
traffic are separate acceptance and must not be inferred from these fixtures.

Final source review: existing read-only reviewer found no remaining concrete P1/P2
findings after speech cancellation was moved outside its snapshot lock. No validation
was executed during source authoring/review. Source freeze begins at first gate.


### Derived output and recovery closure

Source review found that local inference could turn private Workspace inputs into
ordinary web-tool text or hosted speech. The original admission categories now
travel in an SDK coroutine envelope through tool execution, HTTP/WebView, and
native Gmail activity handoff. The original mode revision is captured before
context preparation. ToolInvocationPrepared stores enum-name metadata and revision
through the existing journal; approval/recovery cannot upgrade them. Historical
unknown origins and changed prepared arguments fail closed for remote dispatch.

Ordinary and streaming speech carry the same metadata through runtime events and
SpeechRequest. Barred/stale/unknown ordinary sources use an offline voice; explicit
code-owned user voice previews retain their deliberate text-only setup route.
Hosted synthesis still checks the actual HTTP boundary. Delegated reference and
child/research result categories conservatively include Workspace/Screen/Memory
because historical child payloads lack complete source distinctions. This may
restrict remote child workflows when any of those categories is disabled; it never
relabels child output as ordinary text. Local child work remains available.

Added source cases cover cross-owner derived queries, early receipt cost ordering,
original authority across approval, Room reopen, historical pending work, native
activity handoff, delegated references/results and ordinary/streamed speech inputs.
The Android fixture also exercises blocked Workspace-derived web and actual
ElevenLabs synthesis, and stale local-tool authority. No live accounts are used.

Final inference-envelope closure: all three provider invocation paths now retain
prepared.remoteDispatchRevision and original conservative context categories around
the actual provider executor. A full executor regression exercises an original
revision under newer ambient authority. Targeted read-only review found no further
concrete issue. Source was frozen before the expanded serial gate was restarted.
