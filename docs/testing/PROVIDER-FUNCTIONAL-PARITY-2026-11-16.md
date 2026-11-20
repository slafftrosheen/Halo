# Provider functionality and delegation acceptance

Status (2026-11-20): latest fixes are committed for further verification; their final build, test gate and reinstall are pending. The earlier installed revision passed 860 core tests, 547 selected app tests, 10 ChatGPT tests and 48 OpenRouter tests, with zero failures/errors/skips. These counts do not certify the latest source revision.

Latest acceptance and remaining verification:

- The earlier installed app (SHA256 `dc1e51138fce307f467abc25374e8dd25f6ddaca7556cb6c48b8ea1aad287ddf`) passed isolated ChatGPT workflow/note readback and saved-source image understanding checks. Navigation retained Organizer and Sources without the standalone Halo Screen.
- OpenRouter workflow acceptance exposed nested JSON arrays reaching tool validation without conversion to Kotlin lists. Shared recursive argument decoding and provider regression coverage are now implemented but await the final gate.
- Home action/context classification and ChatGPT saved-account cold readiness were corrected after live checks. These latest fixes have not been reinstalled or accepted on the phone.
- The next build gate was deliberately stopped to include those fixes. Fresh live source downloading was not proven: the selected NASA source remained under its existing refresh backoff.

## Requested scope

Use the selected provider for Halo's existing vision, audio, Workspace, Organizer, Sources and web workflows wherever the provider supports the required operation. All tool-capable providers use the same CognitiveSession, ToolRegistry, functional owners, approval and durable-result contracts. Local remains on-device. Provider limitations must be visible and must not silently become separately billed API fallback.

User steering during the first build (2026-11-16):

- Fetch full readable source articles and images, not only feed headings/metadata. Make retrieved content available to the assistant through bounded, versioned reads and visual analysis; report inaccessible/truncated content truthfully.
- A request to summarize the past day must gather available source text, headings, notification content and other relevant existing local information before summarizing. Use the existing permissions, source selection, privacy/provenance owners and an explicit time interval; do not imply coverage of inaccessible data.
- Nontrivial actions must have a visible step-by-step workflow with current execution progress and outcomes derived from actual receipts, not model promises.
- Remove the standalone Halo Screen ambient page and its entry points. Keep launcher widgets, Mini Halo, Organizer, clocks, briefs, Sources and their functional tools. This does not remove Android screen capture/accessibility capabilities.

Audit evidence for this added scope: FeedParser strips markup/images; ArticleFact has no image representation; PublicHttpCollector fetches the feed only. Brief engine clips article text to 500 characters. AndroidLocalCollectors LOCAL_NOTIFICATIONS currently reads Halo package notifications only. HomeScreen hides active task progress behind task-details expansion. AppShell implements the ambient page via screenMode/screenFull at Home, while Organizer and other related routes use `screen/...` and must remain.

## Findings and work

1. ChatGPT subscription is currently hardcoded text-only. The current official contract supports images/files for compatible models, although audio/video/transcription and hosted file-search/image-generation remain unsupported. Add supported image serialization and consume account model modality metadata; retain capability evidence and unsupported-input rejection.
2. OpenRouter catalogs expose audio/video but the adapter advertises only text/image and serializes no audio/video. Complete bounded serialization using the existing AiRequest fields. Gemini has a similar input-serialization gap. Do not infer all-model support from provider support.
3. The shared OpenAI-compatible Responses serializer uses Chat Completions image and tool-history shapes. Correct this common path and test actual payloads for both wire protocols. Audit model identity/capability metadata across its presets.
4. Halo's grounded search owner is currently OpenRouter-only and available through a UI dialog. Audit a shared functional search operation and provider-backed routes without bypassing existing query/consent/cost/citation controls. Browser launching is not search-result evidence.
5. Workspace, Files, Canvas, Study, Organizer and Sources already have canonical functional owners. Verify tool discovery, native provider calls, continuation, source versions and saved results through the existing execution path; fix transport/discovery gaps rather than creating duplicate owners.

## Evidence boundaries

- Desktop serialization/contract tests, real provider requests, native owner readback and actual spoken tool execution are separate.
- Live accounts available from the preceding acceptance: ChatGPT subscription and OpenRouter. Other provider credentials are not assumed configured; missing live access is NOT_RUN.
- Use generated non-private images/audio and isolated or explicitly labelled QA objects. Keep passwords, tokens and response bodies out of diagnostic logs.
- Reinstall with `adb install -r`, retain account/app data, verify installed APK hash. No GitHub issues are closed from source coverage alone.

## Canonical operations and tool operability

Existing workspace and organizer operations retain their ToolOperability contracts. Any new search/analysis operation must document descriptor IDs, schema version, source-version/consent binding, bounded results, replay behavior and registered-feature review here before completion.

### Implemented changes awaiting this revision's gate

- ChatGPT: image capability and explicit account catalog modality evidence; bounded Responses `input_image` parts, including image-only requests. Missing model metadata stays unknown. No audio/video subscription fallback.
- OpenRouter: encoded audio/video and image-only chat inputs. MIME types are explicit; raw/unknown audio is rejected. Gemini uses the same bounded media preparation and its native inlineData shape. Inline media has a 12 MiB aggregate ceiling, with larger upload workflows left separate.
- Shared compatible transport: correct Responses image/function-call/function-output shapes; tool continuation no longer requires a new user text; unsupported audio/video is rejected instead of silently dropped. Exact remote model IDs are retained, top-level modality/tool metadata is consumed, and catalog rows use the configured provider identity.
- Groq: correct `/openai/v1` base URL. Documented Groq/Cerebras/xAI tool model hints supplement absent catalog flags only. Explicit catalog denial wins; hints do not add models absent from a live catalog.
- Gemini: retain opaque function thought signatures through ToolCall, durable provider records and recovered continuations. Calls from older models without wire IDs receive a runtime correlation ID; that ID is not fabricated as an upstream wire ID. Thought text is not presented as an answer. Metadata is bounded and excluded from ToolCall diagnostic text and tool arguments.

### Grounded web search contract

- Owner shared with manual UI: `GroundedWebSearchService`, `OneShotConsent`, `OpenRouterOnlineClient`, `OnlineOperationLedger`.
- Registered host executor: `GroundedWebSearchTools`; descriptors through `HaloToolDescriptors.webInsight`, in the production HostTools composition. IDs `halo_web_search_prepare` and `halo_web_search`; ToolOperability v1; capability `host.web.fetch`; operation QUERY; TEXT and VOICE_FINAL channels share the runtime.
- Preparation accepts only query (1–2000 chars) and en/ru/lv/pl. It makes no network request or reservation and returns a 120-second process-local `sourceVersion`. Search accepts that exact query/language plus sourceVersion; changes to the credential/query/language invalidate it. Tokens cannot be reconstructed after process death and are consumed before dispatch.
- Search requires ALWAYS confirmation and NEVER_REPLAY. Its grant summary explicitly names OpenRouter and the USD 0.01 reservation, which is not a provider-enforced cap. This is a shared Halo search backend, not a claim that every reasoning provider has native web access. Missing key/Power/budget/access remains a rejection.
- Output is a bounded cited answer (32k chars, at most 10 public HTTPS sources) with a durable spend operation ID, provider, retrieval time and reported cost only when supplied. Web payloads are classified WEB_CONTENT. Content is untrusted evidence, not execution authority. Missing citations/incomplete/network results never become successful browsing; uncertain charges cannot be auto-replayed.
- No local note/source is created by search itself. Saving selected citations uses the existing Workspace/Sources owner and its ordinary revision/approval rules.
- Desktop workflows authored: exact prepare/search/receipt + replay rejection; changed/fabricated credential/query/sourceVersion and Local rejection; uncited result reports uncertain completion. Live/actual spoken checks remain separately recorded below.

### New verification fixtures

- Red gate `.gradle/issue-audit/provider-parity-red.log`: 4 intended failures (two shared transport, ChatGPT modalities, OpenRouter audio/video). Existing tests in those three modules were green.
- `ProviderFunctionalityPhysicalTest`: opt-in real provider, generated red/blue image, and isolated Room Workspace through CognitiveSession. It requires a real create call, a real read call, exact native data and Room reopen readback. No personal notes/history, profile changes, microphone, or external message sends.
- Room regression verifies opaque continuation data survives database reopen separately from tool arguments.

Additional primary references checked: [Groq tools](https://console.groq.com/docs/tool-use/overview), [Groq endpoint](https://console.groq.com/docs/models), [Cerebras tool models](https://inference-docs.cerebras.ai/integrations/code-mcp), [Grok tools](https://docs.x.ai/developers/grok-4-7), [Gemini function calls and signatures](https://ai.google.dev/gemini-api/docs/generate-content/function-calling), [Gemini video inputs](https://ai.google.dev/gemini-api/docs/generate-content/video-understanding).

| Workflow | Desktop | Native owner/UI | Permitted provider | Actual spoken operation | Durable readback |
|---|---|---|---|---|---|
| Image understanding | PASS: bounded wire shapes, saved image identity/revocation | NOT_RUN | NOT_RUN | NOT_RUN | PASS: desktop store reopen |
| Audio/video understanding | PASS: supported wire shapes and explicit rejection | NOT_RUN | NOT_RUN | NOT_RUN | N/A |
| Workspace create/read/edit | PASS: shared owners, plan guard, sharing revisions | NOT_RUN | NOT_RUN | NOT_RUN | PASS: desktop Room reopen |
| Organizer and source workflow | PASS: complete text pages, day context, owner guards | NOT_RUN | NOT_RUN | NOT_RUN | PASS: desktop source store/Room |
| Grounded web search | PASS: consent, revision, citation and replay contracts | NOT_RUN | NOT_RUN | NOT_RUN | PASS: desktop receipts |

References checked 2026-11-16: [ChatGPT supported inputs/tools](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations), [OpenRouter audio](https://openrouter.ai/docs/guides/overview/multimodal/audio), [OpenRouter video](https://openrouter.ai/docs/guides/overview/multimodal/videos), [OpenRouter web search](https://openrouter.ai/docs/guides/features/plugins/web-search).

### Full sources, day context, workflows and navigation (current gate in progress)

- Feed images survive parsing, including inert captions and public HTTPS references. Feed text limit is now 200,000 characters per entry, with the original 2 MiB feed transport limit retained.
- `ArticleContentCollector` enriches the same reserved feed snapshot: readable page text, image references, and downloaded raster images are stored privately and addressed by content hashes. Every additional GET is charged to the original source/global rolling budget. Source version/token, global mode, DNS/redirect boundary and cancellation remain authoritative. Text downloads precede images; a 120-second refresh window and storage limits report partial coverage. Subsequent refreshes can fill gaps. Article HTML never executes and the reader never fetches remote resources.
- `ArticleContentStore` keeps full extracted text separately from bounded feed metadata, capped at 200,000 characters per article. Captured/restricted/empty/limited/unavailable states are distinct. Only JPEG/PNG/WebP assets are accepted by the download owner; the UI decodes with dimensions/sampling bounds. Missing images remain explicit. Unreferenced files age out after a 15-minute in-flight grace interval; this is not a claim of immediate physical erasure when a source is cleared.
- `halo_source_get` now paginates every item and full text. Continuations require matching source/content revisions; article reads expose content coverage, original URL and article retrieval time. `halo_source_image` resolves only a saved image from the exact current source/article, then uses the existing vision router with tools disabled. Local stays local and Power follows existing image/Workspace disclosure. Image analysis is NEVER_REPLAY; source authority is checked again before returning output.
- `halo_day_context` reads a bounded explicit period (maximum 48 hours), with a manifest and versioned pages for Sources, Organizer, Workspace, permitted Notifications, and completed Halo Activity. It returns full content through paged item reads and canonical source/image references. Sources remain selected/enabled/unmuted; notification access, per-app policy and 24-hour/500-row retention are explicit. Notification prose is not copied into feed snapshots. Unknown dates, source gaps and item limits must be disclosed. Children cannot read adult day context.
- These new source/day tools use `EverydayToolExecutor`, canonical repositories, `EverydayReadAuthority`, ToolOperability v1 and the existing `halo.everyday` skill. Typed and STT-final input share the runtime. Registered review and physical/spoken checks are still pending for this revision.
- `halo_workflow_plan` journals an ordered plan through `AgentRunCoordinator`/`AgentRunStore`. Plan steps survive Room reopen and are exposed in Home alongside receipt-derived progress. Recording a plan is not action success or approval. Production runtime rejects non-read actions without a recorded plan and preserves ordinary approval/replay controls. Read-only child agents cannot rewrite the parent's plan. Multi-step remote workflows can use a bounded 5-minute/24-round budget without resetting consumed time/tokens or overriding custom/Local/thermal/replay limits.
- Removed the standalone ambient Halo Screen from the screen catalog, global menu, Home switch, tool composition and bundled skill. Legacy `screen` launches return Home. Organizer, Sources, briefs and clocks retain their routes and gain direct global menu entries. Mini Halo and launcher widgets remain.

First combined gate for this scope: `.gradle/issue-audit/rich-sources-workflow-gates.log` failed on two stale cognition assertions (bootstrap plan identity and day-context provenance), and three corrupt Gradle transform lock headers. The assertions were updated to check the new contract; only the three verified corrupt cache locks were moved aside, retaining backups. No current APK success/installation is claimed from that run.

Second combined gate: `.gradle/issue-audit/rich-sources-workflow-gates-2.log` exposed two damaged compressed compiler cache entries and one remaining old remote-denial assertion. The next run also exposed a missing configuration-cache fingerprint. Both optional Gradle caches are bypassed for subsequent verification; source and app data were preserved.

The fourth gate compiled production and test Kotlin and passed 403 cognition tests. Of 545 selected app tests, 537 passed and eight failed: the retired spoken Screen destination, a bootstrap size assertion, an expected image-analysis confirmation policy, three web-reader fixtures without a global mode, and the branded menu label. These were corrected; packaging was stopped with Gradle's stop command because this was not the final revision. No installation is claimed from it.

The next gate includes publisher article headers/responsive image references, all new EN/RU/LV/PL copy, a real Room day-context regression for full note bodies and permitted/hidden/blocked notifications, canonical note revisions even at equal timestamps, and source-image revision/revocation/uncertain-inference checks. The native provider test now reopens saved article text/images before actual vision inference, and records a plan through the durable CognitiveSession before creating/reading an isolated note. It checks journal persistence, not actual Home plan rendering; UI and spoken evidence remain separate.

The sixth gate passed all 547 selected app tests (93 suites, zero failures/errors/skips), including those added checks. ChatGPT's 10 and OpenRouter's 48 module tests passed. Core XML reports contained 859 tests with one outdated spoken-navigation expectation for the removed `screen` destination. The build process then ended during dex packaging without a completion report; no Java/Gradle process remained and the APK still had its prior timestamp. The navigation test now checks Home and explicitly rejects the retired ambient destination. Gate seven reruns that module and both APK assembly tasks; production sources are unchanged from the passing app tests. This paragraph is intermediate evidence, not an installation claim.

`generate_ui_languages.py --check` passed for all 4,802 EN/RU/LV/PL entries; `git diff --check` reported no whitespace errors. The broader Polish audit still reports preexisting gaps in pairing/peripheral labels and date placeholders outside this change; it is not claimed as a pass.

Gate seven's corrected navigation module passed all 56 tests. Combined current XML reports now show 860 core + 547 app + 10 ChatGPT + 48 OpenRouter tests passing (1,465 total, zero failures/errors/skips). All 17 core report directories correspond to tasks present in the sixth gate; this is not a count of unrelated stored reports. Packaging remains pending at this checkpoint.

### Verified build and installation

- Gate seven finished `BUILD SUCCESSFUL` (10m 8s), including both `assembleDebug` and `assembleDebugAndroidTest`.
- App and repository release APK SHA-256: `dc1e51138fce307f467abc25374e8dd25f6ddaca7556cb6c48b8ea1aad287ddf`.
- Test APK SHA-256: `2bcaca6be6f6b2dcfee3bf3fb91dfc05087dd458f57ad316b7de5edb294b91e6`.
- Original development signer verified. Both APKs installed with replacement (`-r`, test also `-t`), without clearing app data. Installed app bytes read back from phone `f01fe673` match the app digest. Android reports version 1.0-RC1/code 2, updated 2026-11-16 19:54:07 Europe/Riga.
- The first provider test attempt stopped at the device-lock assertion before catalog/inference calls. The user unlocked the phone and the live retry is in progress. This prerequisite failure is not a provider acceptance result.

### Library and saved-history Power access

- The existing `NotebookLibraryTools`, `NotebookOrganizationTools`, `NotebookBatchTools`, and `SavedChatTools` now accept an optional captured disclosure authority. Default construction still denies remote callers; production supplies it only for unlocked adult Power with current Workspace sharing (and conversation sharing for saved chats). Mode/family/policy revisions are checked before use and before returning results. Organization/batch owners also check admission inside their existing source-guarded mutation path; saved chats recheck inside Room operations.
- Same ToolRegistry IDs and ToolOperability v1 owners; no second Library or conversation writer. Existing exact view/source versions, one-use tickets, mutation confirmation, NEVER_REPLAY, active-writer refusal, and explicit CONTEXT selection remain in effect. View tools still require the actual resumed Library; general Workspace tools remain available without it. No new user-facing cloud toggle was added.
- Desktop regressions cover default remote denial, granted Power reads, mid-read revocation, guarded batch denial and fresh admission. Native Power/spoken acceptance remains pending.
- Direct image wire shapes were also checked against [Claude vision](https://platform.claude.com/docs/en/build-with-claude/vision) and [OpenAI image input](https://developers.openai.com/api/docs/guides/images-vision). These adapters reject audio/video explicitly; OpenRouter/Gemini and the configured speech providers own supported audio routes. Subscription capabilities remain separate from API capabilities.
