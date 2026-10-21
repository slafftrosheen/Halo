# HALO #40 — sourced lookup, paid-media consent and cost controls

Date: 2026-10-21. Original package base: `0513fb53d30583948c3e1d019896fbd7bbce82b4`. Integration base: `3bd7d934095c54ce956ce06bf98281a1ef325b61`.

**Delivery state:** source integration of the previously unpublished #40 package, retaining the intervening Ink v2 and curated-catalogue commits. The package paths do not overlap those two commits. This is implementation completion, not release or provider qualification. The physical/live-billing checklist below remains open under #43 when #40 is closed.

**Validation:** source inspection and structural checks only. No Gradle, Kotlin compilation, tests, CI/Actions, live-key paid request, phone install or visual acceptance was run. Test source is not a passing test report.

## Implemented source

### Explicit sourced web lookup

The Home composer gains a Search with sources action. The ordinary Send/voice path is unchanged and does not silently enable a paid search service. The dedicated dialog accepts an editable, voice-capable query and sends **only that query**, not conversation history, memories, notebook sources or attachments.

`GroundedWebSearchService` uses the user's existing protected OpenRouter key. `OpenRouterOnlineClient` sends one explicit non-streaming `openrouter/free` request with the `web` plugin, `engine=exa`, `mode=auto`, and at most five requested search results. It supplies no tools, never executes generated actions, never upgrades to a paid text model, and has no application retry/fallback. Redirects and OkHttp connection retries are disabled for this new transport. The user sees that web search is separately chargeable even when the text route is free.

The result is accepted only after a completed response with valid API `url_citation` annotations. Titles and HTTPS links are bounded and deduplicated. Plain model-authored URLs are not promoted to trusted clickable sources. Missing annotations, incomplete output or returned tool calls fail the sourced-result contract; this does **not** imply that the provider did not charge. The UI shows original source links and the local retrieval timestamp, explicitly not a publication timestamp. Copy includes answer, original links and retrieval time. This is source attribution, not an independent fact-check.

Lookup is an explicit user action, **not an automatic current-events classifier or a new autonomous tool executor**. Existing conversational tool authorization remains unchanged.

### Separate spending consent

`OneShotConsent` issues a process-local, single-use challenge with a two-minute lifetime. It binds the approved operation to its exact query/prompt, provider/model, language or reference-image data and the credential observed during preflight. Cancellation, changed input/key, expiration and replay fail closed. Tokens are never restored/exported. Dispatch rechecks current privacy, Kids and local-only policy. Integration review added a second check after durable reservation persistence, plus a final credential/request fingerprint check, so settings changed during that write cannot silently reuse an old approval. The dialog requires explicit acknowledgement that the final provider charge can be unknown or exceed a local reservation.

Every Workspace image generation, regeneration and preview edit now goes through this review. Existing/direct `WorkspaceImageService.generate` callers without a grant cannot send a generation request. It remains a draft until Save; original assets are preserved. Remembering the last model is best-effort after a successful response, so a preference-write failure does not discard the paid-for image. Source changes retain the existing provider adapters, so this is an application admission guard, not a new billing/idempotency guarantee for those vendors' underlying transports.

`PaidMediaPolicy` adds an independent, default-off **feature-level** opt-in for potentially paid remote image/audio/video understanding. This is deliberately distinct from one-shot image-generation approval and from cloud privacy permission. Exact/pinned target resolution, normal routing, actual conversational/standalone dispatch and Vision Lab check the gate. Revocation is re-read before a new dispatch. On-device media and ordinary text do not require this grant. The existing optional, currently uninstantiated `AudioModelTranscriber` also fails closed unless its future host supplies the separate grant callback. No audio-model feature is enabled by this batch.

### Costs and limits

`OnlineOperationLedger` records bounded local metadata: operation ID, UTC time, feature, provider/model identifiers, reservation and an optional reported charge. It stores no prompt, query, source URL, raw media or API key. App storage uses checked synchronous persistence; operations reserve before dispatch. Multiple screen/service instances serialize reservations through a common lock.

The local UTC-month and per-request limits cover **only this phone's new web-search and image-generation operations**. They gate reservations, **not final charges**. They do not cover chat, vision, speech, other devices or activity outside Halo. UI states these exclusions and recommends a dedicated provider key with provider-native limits. There is no claim of a guaranteed dollar ceiling.

`usage.cost`, when returned by OpenRouter, replaces the search reservation. Image adapters do not report authoritative cost; their reservation remains explicitly unconfirmed. Timeout, cancellation, no-source responses and process interruption never turn an attempted operation into a fabricated zero charge. A final receipt-write failure retains the earlier reservation rather than discarding a paid-for returned result. Unknown charges cannot currently be reconciled per image request automatically; this is shown as uncertainty, not an invoice.

A manual Check OpenRouter usage action uses `GET /api/v1/key`. Provider totals, remaining limit and BYOK accounting are separate from local estimates and include their check time. They can cover other apps sharing that key. No polling or background provider request is added. Costs are reachable from search, image approval and the existing usage screen. New UI copy uses 61 matching EN/RU/LV resources; no unsupported Polish UI claim.

## Network/trust review for ADR-0001

| Boundary | Operator / payload | Authority and retention |
| --- | --- | --- |
| `/api/v1/chat/completions` + Exa web plugin | OpenRouter and its selected Exa search integration; query and a language instruction; BYO key in HTTPS header | Per-request approval, query-only; vendor retention is not controlled or deleted by Halo |
| `/api/v1/key` | OpenRouter; authenticated key metadata lookup | Explicit button; response shown as a timestamped snapshot, not a local invoice |
| Existing image endpoints | User-selected image vendor; prompt and optionally exact reference image | New single-use spending approval plus existing source/Kids/privacy checks; unknown final charge disclosed |
| Local journal | User device only; bounded metadata listed above | No Halo developer server; no network telemetry added; Play Data Safety/vendor-retention review remains a release gate |

Local-only blocks the new online requests. No Halo proxy, account, new remote service, cloud storage, schema migration, model download or dependency is introduced.

## Source cases and review

29 authored source cases cover ledger recreation, conservative cancellation accounting, invalid/corrupt settings, shared budget admission, exact/expired/replayed consent, metadata-only source parsing, unsafe links, incomplete/tool responses, no-I/O preflight/dismissal, changed credentials/query, Kids/local-only changes, rejected budgets, single-use dispatched search, charged no-source failures, unapproved images, and remote-media opt-in/revocation. Three integration cases specifically revoke access or change credentials during reservation persistence and require zero transport/provider calls. These cases have **not been run**. The earlier commit's 18 free-routing cases are unchanged.

Structural checks: matching resource keys and format placeholders across three locales; all new resource references resolve; source delimiter/whitespace checks; no malformed replacement records. These checks do not establish Kotlin type correctness, Android rendering or runtime behavior.

## Required owner acceptance (carry into #43 when closing source implementation)

1. Build/install under the owner's separate QA procedure; run new and existing routing/media cases.
2. With a dedicated low-limit real key, approve one sourced lookup. Check the requested free model, API annotations, retrieval time, reported cost and provider usage. Test missing sources, 401/402/429, offline, timeout and cancellation without hidden retry or paid fallback.
3. Verify cancel/no acknowledgement/expired/changed/replayed approval sends **no inference request**. Exercise double taps, concurrent images/search, a zero local limit, persistence failure and process death after dispatch.
4. Run generation/regeneration/edit through Notebook and Canvas. Confirm exact provider/model/reference preview, preserved original, separate approval every time, no false free receipt and a usable retained result if receipt persistence fails.
5. Check remote-media opt-in off/on/revoked at ordinary, pinned, direct and Vision Lab boundaries. Existing Kids/source/permission gates must remain effective; no cloud request under local-only.
6. Inspect small screens, large fonts, keyboard and voice input in EN/RU/LV; confirm source links and copied provenance. Review vendor processing/retention and release Data Safety wording.

No real-key or phone acceptance is inferred from source integration. Source closure, when explicitly requested, must keep this checklist open in the alpha gate rather than falsely marking it passed.

## Official API contract references reviewed

- https://openrouter.ai/docs/guides/features/plugins/web-search — web plugin configuration, independent search pricing and URL citation metadata.
- https://openrouter.ai/docs/api/reference/limits — authenticated key usage/limit data.
- https://openrouter.ai/docs/cookbook/administration/usage-accounting — response usage/cost reporting.
- https://openrouter.ai/tool/web-search — current search fee reference. The displayed USD 0.007 is a dated planning estimate, not a live quote or provider-enforced cap.
