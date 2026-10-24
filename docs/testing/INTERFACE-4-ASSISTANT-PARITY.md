# Interface 4.0 assistant parity — navigation and source context

Integrated test execution and exact device/artifact results: [verification ledger](INTERFACE-4-VERIFICATION.md). Sub-batch source notes below retain their original evidence boundaries.

Issue: #62. Contract version: 1, 2026-10-24. This report supplements the screen/action inventory and `AI-CONTROLLABLE-TOOL-VERIFICATION-TEMPLATE.md`; it does not attest 100% spoken/provider acceptance.

## Canonical input and execution owners

`HomeViewModel.submitText` publishes a TEXT/USER observation with `SpeechRecognizedPayload`. Final recognized speech supplies the same payload through the speech pipeline. `SpeechRule.match` resolves exact `NativeCommandParser` commands before optional model classification. `CognitiveSession` owns both routes. `NativeCapabilityDispatcher` maps navigation at confidence >= 0.8 to `halo_open_screen` / `host.assistant.screen`, then uses the existing `SkillExecutionService`, `ToolRegistry`, authorizer and turn executor. No additional session, executor or cloud fallback is introduced.

`NativeHaloScreenDestination` is the shared closed native parser/dispatcher allowlist. Android's `HaloScreenCatalog` remains the actual host destination catalog and `AssistantTools` schema/discovery source. `HaloAssistantScreenParityTest` guards that every deterministic assistant destination exists in that catalog. `HaloScreenLauncher` and the shell own dispatch/rendering. A successful launch returns **DISPATCHED**, explicitly stating that rendering is unconfirmed. `halo_list_screens` returns **ACKNOWLEDGED** discovery. Both descriptors now have version-1 `ToolOperability`, TEXT/VOICE_FINAL channels and a bounded TEXT result contract; existing authorization, timeout and replay policy remain in force. Navigation has no selected source/version arguments because it does not mutate a document or convey source access.

## Navigation matrix

Every listed command below is supported as typed input and as an STT-final transcript. This means a shared semantic path; actual microphone recognition remains NOT_RUN. The enum source includes explicit EN/RU/LV/PL imperative phrases for all 24 destinations. For example Workspace accepts `open workspace`, `открой рабочее пространство`, `atver darbvietu`, `otwórz obszar roboczy`. Historical English Notebook aliases continue to resolve to the unchanged `notebook` route/data identity.

| Human destination | Example text / STT-final transcript | Exact screen argument | Result / owner |
|---|---|---|---|
| Home | open halo home | home | DISPATCHED / shell |
| Halo Screen | open halo screen | screen | DISPATCHED / Home Screen presentation |
| Workspace | open workspace | notebook | DISPATCHED / Workspace |
| Study | open study | study | DISPATCHED / Study |
| Studio | open studio | studio | DISPATCHED / Studio |
| File tools | open file tools | file_tools | DISPATCHED / File tools |
| Devices | open halo devices | devices | DISPATCHED / Devices |
| Settings | open halo settings | settings | DISPATCHED / Settings |
| Activity | open halo activity | activity | DISPATCHED / Activity |
| Skills | open halo skills | skills | DISPATCHED / Skills |
| System check | open halo system check | system_check | DISPATCHED / existing diagnostic view |
| Saved conversations | open halo conversations | notebook/conversations | DISPATCHED / Workspace conversations |
| Memory | open halo memory | notebook/memory | DISPATCHED / Workspace memory |
| Notifications | open halo notifications | notifications | DISPATCHED / existing notification view |
| Intelligence | open halo intelligence | ai_settings | DISPATCHED / Intelligence settings |
| Voice | open halo voice settings | voice_settings | DISPATCHED / Voice settings |
| Permissions | open halo permissions | permissions | DISPATCHED / permission explanations, no grant |
| Integrations | open halo integrations | integrations | DISPATCHED / Integrations |
| Automation | open halo automation | automation | DISPATCHED / Automation |
| Storage and backup | open halo storage and backup | storage_backup | DISPATCHED / Storage settings |
| Speech input | open halo speech input | settings/voice/input | DISPATCHED / STT settings |
| Speech output | open halo speech output | settings/voice/output | DISPATCHED / TTS settings |
| Providers | open halo providers | settings/intelligence/providers | DISPATCHED / provider settings, no dispatch consent |
| Models | open halo models | model_browser | DISPATCHED / model browser, no download |

The full host catalog remains discoverable using `halo_list_screens`. The deterministic phrase allowlist intentionally excludes developer sandbox/Speech Lab routes, automatic pairing, continuation routes requiring context, and arbitrary deep/object IDs. Bare `open settings` retains the existing Android settings meaning. Unknown or compound requests do not become a single exact Halo destination. A Canvas requires an actual selected/resolved board; no invented parameter-free Canvas route is introduced.

## Functional operations and context preservation

Navigation is separate from functional parity. These existing semantic owners remain available through the same registered tools and policy gates. Natural-language operation examples are requests for the existing tool-capable path; they are not new deterministic slot parsers or a promise that a missing model can resolve arbitrary writes.

| Area / example typed or STT-final request | Existing typed operations / canonical owner | Source and result contract |
|---|---|---|
| Workspace: create a note containing the requested literal content | `halo_create_empty_note`, `halo_create_note`, `halo_search_notebook`, `halo_get_workspace_object`; NotebookRepository / WorkspaceRepository | Real persisted object IDs; explicit content, no invented source selection |
| Notebook: edit the selected note block | `halo_note_get`, `halo_note_*`; NotebookDocumentRepository / NotebookUseCases | Exact observed note/editor/source versions; recovery drafts, ambiguity and stale versions stop writes; destructive actions retain approval |
| Canvas: add text to this board / arrange selected cards | `halo_workspace_canvases`, `halo_canvas_get`, `halo_canvas_*`, `halo_workspace_insert`; NotebookCanvasRepository / WorkspaceContinuity | Resolve exact board/card/source IDs and current board version first; card selection is bounded; no guessed members or pen simulation |
| Study: resume the selected session / save my literal answer | `halo_study_sessions`, `halo_study_state`, `halo_study_select_exercise`, `halo_study_submit_attempt`, `halo_study_card_*`; StudyUseCases | Exact session/exercise/card versions; attempt receipt is not grading; delete/accept/review preserve learner authority |
| Studio: edit this project / export a PNG | `halo_studio_current`, `halo_studio_get`, `halo_studio_edit/save/export/insert`; StudioService / StudioProjectRepository | Attached project reference, exact project revision, independent export identity; stale writes stop; AI draft only opens the existing sharing/spend review |
| File tools: edit this text / transform this saved image | `halo_file_tool_capabilities`, `halo_text_*`, `halo_image_*`, `halo_pdf_*`, `halo_file_attach_canvas`; WorkspaceFileTools | Exact active readable source/version, current Canvas version for insertion, durable output IDs; system URI grants remain human-owned |
| Settings: use Largest text / make dark-theme accent Lilac / reduce motion / switch app language to Latvian | Existing `halo_get_preferences`, `halo_set_preferences`; AssistantPreferences, AppearancePreferences, PalettePreferences, VisualPreferences, AppLanguagePreferences | Font2.0 matches the visible Largest choice; exact light/dark palette, closed accent/tone/language enums, supplied flags only; palette disk readback and language owner readback before CONFIRMED; no Android settings or processing-mode changes |
| Mode profiles: read Private settings / edit the exact observed local routing or narrow saved Power context | `halo_get_mode_profile`, `halo_edit_mode_profile`; PrivacyModeStore and the existing Graph.saveModeProfile callback | Exact global revision and SHA256 profile snapshot required; protected write uses existing parental/save/prepare policy; raw credentials/endpoints/activation and acknowledgement fields are absent; expanded remote sharing/provider recipient changes require existing human disclosure review rather than treating tool approval as consent |

`AndroidInteractionContextAdapter.selectWorkspace` verifies ACTIVE lifecycle, object AI access, current version and readability before publishing explicit conversation-bound context, then checks the version again. `WorkspaceContextStore` keeps exact ink selection references and resolves the canonical source transactionally. Merely navigating to a page does not silently promote its documents into assistant/cloud context. Global route/context menus reveal the existing Tools catalog rather than executing a hidden second action engine.

The explicit **Use this page as context** bridge uses `CurrentPageContextBinding` for current note/Canvas/file/File tools object arguments only. It validates the exact page family and object identity, compares the source version snapshotted for that page action, and invokes the same adapter selection owner. The shell cancels its pending job on page/argument changes; the helper and adapter check coroutine cancellation before publishing focus. Source errors remain visible failures. Studio retains its attached project owner; Study is excluded because the existing `selectStudy` includes a navigation side effect. Existing PDF restrictions remain in force.

For Canvas, the adapter now reads bounded text and linked-item references from `WorkspaceContextStore.contentFor`, rather than calling the incompatible Study-material resolver. The bound focus retains exact board identity and `canvas:` version, and each turn rechecks policy/source revision. The model must still read `halo_canvas_get` before choosing exact card IDs or editing. Selecting the entire board does not imply selected cards, recognize strokes, include linked documents or authorize cloud transfer. A separately selected ink region is not silently converted into whole-board context.

`halo_canvas_current` additionally queries the actual attached Canvas editor through `CurrentCanvasContextOwner`, analogous to Studio's current-project binding. The ViewModel projects only board/selected/visible IDs and an opaque view identity when loaded, available and free of pending edits/drafts/recovery. The owner checks the ACTIVE canonical board/version and IDs and rereads the projection before returning `canvasId`, `version`, bounded `selectedNodeIds` and an ephemeral `selectionToken`; no card body is returned. Missing or ambiguous simultaneous views fail rather than choosing one. This is active-view metadata, separate from explicitly pinned Workspace content.

Selection-sensitive text/move/connect/tag/duplicate/arrange/delete/ungroup/clear-sketch tools accept that optional token alongside their existing explicit IDs and board version. Before writes they validate the exact view, current source revision and selected targets; bulk operations require the complete observed selection, while a single-card operation may target an explicitly observed member. They recheck view identity immediately before the existing Room transaction commits; observed selection changes cause rollback. Existing explicit pinned-ID/version calls remain compatible without a token. Token authority is ephemeral, expires on detach/new attachment/view changes, and is never restored after process death. After any edit reread the board/current selection; never replay old selection authority. All effects still use NotebookCanvasRepository and existing approval/privacy rules.

Studio's attached-project and ink-selection owners use lifetime tokens. A concrete gap was fixed: an old ink session could publish its unchanged stroke selection after an external edit, recording the external project's latest revision as though that session owned it. `StudioInkSession.persistedRevision` now advances only with its own writer commits; `publishSelection` requires that exact revision before publishing. The original frozen digest anchor remains intact, allowing the input session's own queued ink saves. `selectedInk` still checks current project revision and active owner and clears stale selection. No reference or ink content is newly transferred to a model.

## Exceptions and qualification boundaries

- Raw pen/finger gestures, Android permission dialogs, secure OS flows, Bluetooth pairing and hardware-specific actions retain their existing human/platform owner. Use supported semantic edit/query operations or open the correct setup view; navigation does not grant permission, connect hardware or attest physical success.
- Provider-dependent generation, tutoring, transcription, paid media and external effects retain their existing Private/Power, Kids, content, consent, spending and confirmation gates. Selecting Power, changing pages or displaying animation does not authorize them. Unknown/uncertain writes are never replayed to manufacture a success receipt.
- Attached Studio context, explicit Workspace references and explicit current-page source binding are concrete bindings; unrelated visible UI selections must still be resolved by their existing owner. This report does not claim every page's transient selection is automatically published to the Orb.
- Per-page functions not changed by this batch retain their existing owner/contracts; their full interactive inventory and qualification belongs to the integrated Interface 4.0 report. Navigation coverage alone cannot close #62 or #54.

## Regression and actual evidence

Regression sources were authored before the corresponding production edits, with the integrated build/test checkpoint delegated to the root implementation batch as required by the issue's large-batch policy:

- `HaloScreenNavigationTest`: primary routes in all four supported languages, every registered phrase, unique phrases, explanations/negation, object/unsafe route rejection and historical Android settings behavior.
- `HaloScreenInputParityTest`: every exact phrase resolves identically from TEXT/USER and AUDIO/MICROPHONE final payloads with no classifier call. Synthetic payloads do not prove recognition.
- `NativeCapabilityDispatcherTest`: shared closed destination mapping, unchanged confidence threshold, typed/microphone request equality, all aliases through existing executor/authorizer/provider-independent fixtures, existing disabled/locked/cancellation checks retained.
- `HaloAssistantScreenParityTest`: deterministic destination IDs must belong to the actual host catalog.
- `AssistantToolsTest`: registered navigation operability review must return no problems; each exact destination reaches the bounded host dispatch owner with DISPATCHED/render-unconfirmed truth and unchanged NEVER_REPLAY.
- `StudioLocalWorkflowTest`: old-session publication rejected after an external edit, legitimate session revision accepted without breaking digest anchors, selection invalidation and detach behavior.
- `StudioInkSessionTest`: only the input writer's committed revision advances its publication authority; external edits do not advance it.
- `CurrentPageContextBindingTest`: exact page/version/identity selection, unsupported route and stale-source rejection, pending source read cancelled before publication.
- `ContextAdapterTest`: canonical Canvas context identity/text, unchanged no-image boundary, source-version invalidation and AI-excluded source rejection.
- `AppPreferenceParityTest`: every visible appearance/accessibility/language field reaches the existing owners, font2.0 and exact light/dark palette persist, malformed/unsupported fields reject before any partial application.
- `ModeProfileToolsTest`: exact revision/hash edit/readback, no activation, durable restoration, stale write rejection, closed non-secret schema and remote expansion requiring disclosure review. The final root callback must compare expected revision inside the canonical owner's edit transaction.
- `CurrentCanvasContextOwnerTest`: metadata-only current view, real board version/selected IDs, stale-selection write rejection with no mutation, compatible explicit pinned-ID edits, successful guarded edit through the existing owner, replay/missing/ambiguous-view rejection, and changed projection during discovery.

Batch E1 adds `AppPreferenceOperability.forName` contracts for preference/profile query/edit tools. Profile edits use `NEVER_REPLAY` and explicit protected-write approval at the central descriptor/authorizer. These metadata and schema additions are source work pending the next integrated gate; no actual spoken or remote workflow is inferred from these test sources. A failed multi-owner app-settings update reports UNKNOWN completion with instructions to reread because earlier fields may already have persisted; it does not invent cross-store rollback. Profile write callback/readback failure similarly preserves uncertain completion and prohibits automatic replay.

Profile-sharing expansion is an explicit protected-consent exception: general tool approval cannot acknowledge the separate Full Power disclosure, grant paid-media consent or select a new remote speech recipient. The nearest supported typed operations are exact `halo_get_mode_profile`, safe narrowing/local `halo_edit_mode_profile`, and `halo_open_screen(screen=ai_settings)` to reach the existing profile/disclosure UI. A prohibited expansion returns PERMISSION_REQUIRED without saving it; it is not reported Ready/changed. Profile input never accepts raw endpoints, credentials, acknowledgement, paid-consent or activation fields. Provider/speech choices are selections only, never evidence that a model is installed or a voice is audible.

This sub-batch ran `git diff --check` successfully and a source-level ID comparison found 24 deterministic destinations, all present in `HaloScreenCatalog`. It ran **zero Gradle/build/install attempts**. Test execution/compile results belong to the root integrated gate and must be recorded there before claiming PASS. Native device typed behavior, actual spoken recognition (EN/RU/LV/PL), TalkBack/large-font interaction and permitted remote-provider workflows remain **NOT_RUN in this sub-batch**. #43 physical qualification remains separate.

| Workflow | Typed on device | Spoken on device | Typed permitted Power | Spoken permitted Power | Durable readback |
|---|---|---|---|---|---|
| Open Workspace and create a literal note | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Read exact Canvas version and edit selected cards | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Resolve attached Studio project and export exact revision | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

## Voice configuration semantic operations

`halo_get_voice_configuration` and `halo_set_voice_configuration` adapt the existing speech-language, VoiceExperience, synthesis-fallback, Kokoro voice, ReadAloud, scoped-control and Wear-nearby owners. The write schema covers locale; wearable phone fallback; spoken/semantic interruption and sensitivity; early streaming and response style; connected-audio and Wear-nearby preference; synthesis fallback; the installed Kokoro pack's exact voice ID; scoped speech-control action/reset; reading speed, Follow words and automatic answer reading. It requires the exact fresh revision/snapshot, foreground/grown-up authority and central explicit approval/NEVER_REPLAY policy.

Query returns installed/compatible recognition choices and synthesis **catalogue** IDs separately. Catalogue membership is not setup, readiness, playback or cloud permission. Missing optional owners produce null values/empty choices and reject corresponding mutations. Kokoro choices are bounded to the actual installed 11/103-voice pack and checked against exact pack version/current voice. Locale and VoiceExperience mutations use same-owner expected-prestate guards shared with UI updates. Target readback is mandatory; partial, failed or ignored multi-owner writes report UNKNOWN and require a fresh read instead of replay.

The Voice snapshot epoch is process-local metadata, not a durable settings revision or restored approval. It hashes actual owner settings/choices plus the supplied existing processing-policy revision. Configuration changes or process replacement invalidate the requested edit. Reading-speed configuration uses a preference-only method on the existing ReadAloud controller, so it never starts/restarts playback. Scoped controls retain only their existing speech-time allowlist; no generated mapping or idle player is created. Wear-nearby start/stop calls the existing bridge after persistence and does not establish connection or qualification. No configuration operation starts a microphone, probe, test phrase, remote provider request or paid-media consent.

Actual spoken recognition, audible output, headset button behavior, secure OS permissions and human hearing confirmation remain separate acceptance. Speech-model inventory install/remove/pause belongs to the existing acquisition owner: these configuration IDs do not claim to perform it or substitute the pinned starter workflow.

Regression sources added before the integrated gate: `VoiceConfigurationToolsTest` (snapshot/readback/stale/invalid/unavailable/foreground/Kids/ignored-write checks), `VoiceSettingsOwnerParityTest` (canonical expected-prestate and durable VoiceExperience/Kokoro preferences), and `SpeechLocaleExpectedSelectionTest` (same DataStore owner preserves the winner after stale edits). This sub-batch ran no Gradle/build/install; actual test execution, native voice, hardware and provider qualification remain with the integrated report and are not inferred from test sources.

## Active Canvas reviewed-media and export workflows

The seven `CanvasWorkflowTools` operations use an ephemeral `CanvasWorkflowBridge` lease to the **existing CanvasEditorViewModel**, whose ordinary UI callbacks delegate to the same awaitable canonical methods. There is no alternate store, media engine or semantic edit executor. `halo_canvas_workflow_status` reads the actual board version, busy state and existing review-draft token/hash/count/source identifiers after source-policy checks; it never exposes transcript/card bodies or image/audio bytes. The attached editor must be unique. Detach/navigation cancels in-flight work, and missing/ambiguous editors reject. Recovery of an ephemeral review token across process death is not claimed.

| UI action | Typed operation | Canonical owner and actual result |
| --- | --- | --- |
| Save image color palette | `halo_canvas_image_palette` | Existing NotebookAssetRepository local extractor and editor create the real note/reference card/edge; board/file version and source access are checked before and after extraction and before commit. |
| Transcribe saved recording | `halo_canvas_transcribe_file` | Existing LocalMemoTranscriber attempts the exact local saved file; source/board revisions, source policy and cancellation are rechecked. Only the existing visible review draft is produced; nothing is automatically saved. Missing model/source rejects, without cloud fallback or microphone capture. |
| Correct review text | `halo_canvas_edit_reviewed_text` | Existing visible draft accepts bounded literal user text at exact token/hash; the returned new hash invalidates the old save request. |
| Discard review text | `halo_canvas_discard_reviewed_text` | Clears only the exact existing draft; source media and persisted cards remain intact. |
| Save reviewed transcript/OCR | `halo_canvas_save_reviewed_text` | Existing NotebookCanvasRepository.saveReviewedText preserves source provenance and persists the real Inbox note, card and edge. Exact draft token/hash, original card text, source-file version and board version are guarded. |
| Export board / Study from board | `halo_canvas_export_note` | Existing editor exporter creates and reads back the actual note using canonical current linked-source versions. Returns note ID and exact Note/Study setup target with `navigationTruth=NOT_DISPATCHED` and `studyStarted=false`. Existing workspace open handles a separate navigation request; no tutor inference or cloud consent is inferred. |

All mutations require a scoped interaction plus current foreground/grown-up admission, central explicit approval and NEVER_REPLAY policy. Admission and view/draft guards run again inside the existing Room optimistic transaction before effects commit. A detach/revocation/stale draft rolls back derivative note/card/edge writes together. Explicit source IDs and revisions identify the requested source; merely showing or selecting a card never launches extraction, recognition, export or context binding. Uncertain writes require readback rather than replay. OS file-picker grants, raw freehand drawing, physical recording/audio quality, actual spoken invocation and audible/native-model qualification remain separate acceptance; no Ready or physical PASS follows from these adapters.

Regression sources: `CanvasWorkflowToolsTest` covers closed metadata contracts, exact draft guards, scoped/grown-up admission, missing/ambiguous leases and cancellation; `CanvasWorkflowPersistenceTest` uses the real editor/repositories for persisted export, stale draft hashes, source-linked reviewed save, policy/stale-board rejection and transactional rollback after lease failure. This sub-batch ran source checks only; the parent integrated gate owns execution evidence.
