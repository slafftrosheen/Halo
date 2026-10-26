# Issue 62 — Workspace and Settings migration

Latest delivery status and exact source/APK/test evidence: [integration ledger](ISSUE-62-INTERFACE-DELIVERY-2026-10-25.md). Checkpoint-specific sections below are historical review records; they do not override that ledger or establish final phone acceptance.

2026-10-25. Source changes against `d5fe5943c8c3e689c49b8f7513823f6e50054556`. This is an implementation inventory, not physical acceptance.

## Workspace action preservation

Workspace now opens the real Library immediately. Search, filters, collections, sorting, selection, bulk actions, trash undo, source rows and object context controls retain the existing NotebookViewModel and repository callbacks. Suite selection only changes the shell action projection. It does not clear the query, collection, sort, selection or saved object.

| Previous surface | Current human path | Existing owner retained |
| --- | --- | --- |
| Open Library / hub search | Workspace working area, immediately | NotebookViewModel query/filter/sort |
| New note tile | Workspace shortcut or Office Tools → New note | createNote |
| New Canvas tile | Halo Studio → New canvas | createCanvas |
| File Tools tile | Office Tools → Office Tools | existing `file_tools` callback |
| Studio tile | Halo Studio → Graphics editor | existing Studio callback |
| Notes / Files / Canvas library tiles | Library filters; Office Notes / Studio Canvases shortcuts | existing filters and object IDs |
| Import / Quick capture tiles | Workspace actions; Import also inside Office Tools | InboxCaptureDialog, original import owner |
| Study / Memory rows | Independent Workspace shell actions | onOpenStudy / openMemoryHub |
| Saved chats / working set / related notes overflow | Workspace shell actions | same filter, WorkingSetSheet, NotebookLocalSearchSheet |
| Due practice / recent lesson / memory suggestions | Corresponding scoped shell actions when actual records exist | exact session ID / original Memory hub |
| Collections / Inbox | Library collection controls / Inbox shell action | original collection and inbox owner |

The suite hierarchy is navigation only. No database migration, renamed route ID, new editor owner or automatic AI action was introduced. Existing Studio AI controls remain in their existing editor. This batch does not claim new PDF editing, Note-to-PDF, connected mail or image services. Existing file processing remains under its existing owner; independent offline Studio operation requires the separate Studio journey qualification.

## Settings before/after map

| Before | After | Route / owner |
| --- | --- | --- |
| How halo answers | Brain, first-level peer | `ai_settings` / INTELLIGENCE |
| Voice & reading | Voice & reading, first-level peer with current status | `voice_settings` / VOICE |
| Vision discoverable through technical tools | Vision, first-level peer; existing model setup, camera/photo permission and image privacy links | MODEL_ROLES / PERMISSIONS / PRIVACY; Vision Lab remains developer gated |
| Appearance / Language | Personalization rows | existing stored preferences; inline child surface |
| Mode profiles / shared requests / Permissions / Memory / Storage | Expandable Privacy group, search still returns individual rows | original five destinations |
| Nearby / Notifications / Android assistant / Kids | Expandable Everyday group | original owners; Nearby and Kids render inline |
| Connections / Skills / Automation | Expandable Connections group | INTEGRATIONS / SKILLS / AUTOMATION |
| Check-up | Expandable Help group | SYSTEM_CHECK |
| Guided setup / About / search | Shell actions; search input in utility band and results in working area | original setup callback / About page / pure search |
| Your halo hero | Removed redundant summary | Brain and Voice rows show their actual summary values |
| Advanced | Last section, original route | developer switch and explicit confirmation retained |

Appearance, Language, Nearby, Kids and About now render within the existing Settings working area instead of a fullscreen native dialog covering the global Orb. Back remains explicit through the shell and Android Back. Advanced and standalone hosts retain navigation fallback. Settings notification delivery remains separate from the global bell inbox. Memory is a link to its existing owner.

Search aliases retain “How halo answers” and add Brain, Vision, photo, image, camera and OCR. EN/RU/LV/PL labels are generated from reviewed `ui.tsv`; no persisted profile IDs change. Local/Power enforcement stays with the global owner, not this page.

## Specialist review and fixes

- Library previously required a second opening step, showed duplicate tool cards and spent the first viewport on promotions. Removed that layer and moved actual callbacks into scoped suite actions.
- Settings previously placed a header, search/menu chrome, redundant identity card and all groups before useful discovery. Root now has three capability peers, two personalization rows and expandable secondary groups. Search returns real child rows directly.
- Native modal Settings children previously obscured the shared shell. Inline children preserve the one Orb and fixed navigation. Search is projected to the shell utility band and standalone tests retain a local fallback.
- Suite changes are included in projection source identity; old projections must reject invocation. Existing regression now checks that Office/Studio switching preserves query/sort and rejects stale callbacks.

## Evidence boundary

Source inspection and translation generation completed. Existing targeted Compose regressions were adapted for the intentional Library-first and Brain changes. Builds/tests are owned by the integrated gate; no build was run in this subtask. This initial source-only validation statement is historical; the dated current evidence section below records subsequent gate and physical checks. Unlisted physical and permission/parent journeys remain unqualified. No assertion of issue closure or complete assistant operability is made by this map.


## Full current route source inventory

Snapshot during the integration batch. Every `composable` declaration in AppShell is listed below; loop variables represent multiple routes (`skillRoute`: skills / advanced skill packages, `checkRoute`: check-up / technical diagnostics, `voiceRoute`: voice output / advanced voices). Parameterized IDs stay unchanged. A header token may already be guarded for standalone use; a projection in a file may belong to a different composable in that file. These are review leads, not runtime pass/fail results. The historical parity document describes the prior upper-overflow design and must not be treated as proof of the new zone contract.

| Route declaration | Actual screen source signals |
| --- | --- |
| `mini_halo` | `MiniHaloScreen` (no common header token; no page projection in file) |
| `guided_setup` | `HaloSetupScreen` (no common header token; no page projection in file) |
| `Screen.Home.route` | `KidsModeScreen` (no common header token; no page projection in file)<br>`HaloScreen` (no common header token; projection in file)<br>`HomeScreen` (no common header token; projection in file) |
| `study?captureSource={captureSource}&goal={goal}&sourceVersion={sourceVersion}` | `StudyHomeScreen` (header present; projection in file) |
| `activity?runId={runId}` | `ActivityScreen` (header present; projection in file) |
| `Screen.Notebook.route` | `NotebookScreen` (no common header token; projection in file) |
| `notebook/note/{noteId}` | `NoteEditorScreen` (header present; projection in file) |
| `notebook/study/{sessionId}?review={review}` | `StudyScreen` (header present; projection in file) |
| `notebook/canvas/{canvasId}?nodeId={nodeId}` | `renderPage` (no common header token; no page projection in file)<br>`CanvasEditorScreen` (header present; projection in file) |
| `notebook/file/{objectId}` | `WorkspaceObjectDetailScreen` (no common header token; no page projection in file) |
| `workspace_handoff` | Shell-local content / inspect route block |
| `file_tools?objectId={objectId}` | Shell-local content / inspect route block |
| `studio?projectId={projectId}&sourceId={sourceId}&attachmentId={attachmentId}&canvasId={canvasId}&sourceVersion={sourceVersion}&handoffId={handoffId}` | Shell-local content / inspect route block |
| `notebook/memory?section={section}&noteId={noteId}` | `MemoryHubScreen` (no common header token; projection in file) |
| `notebook/memory/{objectId}` | `WorkspaceObjectDetailScreen` (no common header token; no page projection in file) |
| `Screen.Devices.route` | `DevicesScreen` (header present; projection in file) |
| `device/{deviceId}` | `BuddyBotControlScreen` (no common header token; no page projection in file)<br>`HeyCyanControlScreen` (no common header token; no page projection in file) |
| `integrations` | `ConnectedAppsScreen` (header present; no page projection in file) |
| `advanced/servers` | `IntegrationsScreen` (header present; no page projection in file) |
| `advanced/servers/{serverId}` | `McpServerScreen` (header present; no page projection in file) |
| `skillRoute` | `SkillsScreen` (header present; no page projection in file) |
| `workload_diagnostics` | Shell-local content / inspect route block |
| `advanced_settings` | `SettingsScreen` (header present; projection in file) |
| `runtime_status` | `RuntimeScreen` (header present; no page projection in file) |
| `checkRoute` | `CheckUpScreen` (header present; no page projection in file)<br>`TechnicalReportScreen` (header present; no page projection in file)<br>`SystemCheckScreen` (header present; no page projection in file) |
| `automation_sandbox` | `AutomationSandboxScreen` (no common header token; no page projection in file) |
| `phone_control_sandbox` | `PhoneControlSandboxScreen` (header present; no page projection in file) |
| `notebook/conversations` | `ConversationsListScreen` (no common header token; no page projection in file) |
| `notebook/conversation/{conversationId}` | `ConversationDetailScreen` (no common header token; no page projection in file) |
| `Screen.Settings.route` | `SettingsScreen` (header present; projection in file) |
| `automation?routineId={routineId}` | `AutomationScreen` (no common header token; no page projection in file) |
| `automation_review/{routineId}/{suggestionId}` | `AutomationScreen` (no common header token; no page projection in file) |
| `memory_learning` | `LearningSettingsScreen` (no common header token; no page projection in file) |
| `notifications` | `NotificationInboxScreen` (header present; projection in file) |
| `notifications/{notificationKey}` | `NotificationDetailScreen` (header present; no page projection in file) |
| `notification_settings` | `NotificationSettingsScreen` (header present; no page projection in file) |
| `ai_settings` | `IntelligenceScreen` (header present; no page projection in file) |
| `local_audio_lab` | `LocalAudioLabScreen` (header present; no page projection in file) |
| `vision_lab` | `VisionLabScreen` (header present; no page projection in file) |
| `provider_setup` | `ProviderCenterScreen` (header present; no page projection in file) |
| `settings/intelligence/providers` | `ProviderCenterScreen` (header present; no page projection in file) |
| `settings/intelligence/providers/{providerId}` | `ProviderDetailScreen` (header present; no page projection in file) |
| `settings/intelligence/images/{providerId}` | `ImageModelsScreen` (header present; no page projection in file) |
| `model_browser` | `ModelBrowserScreen` (header present; no page projection in file) |
| `settings/intelligence/models?providerId={providerId}&role={role}` | `ModelBrowserScreen` (header present; no page projection in file) |
| `settings/shared-requests` | `SharedAnswerHistoryScreen` (no common header token; no page projection in file) |
| `settings/intelligence/usage` | `IntelligenceUsageScreen` (header present; no page projection in file) |
| `settings/intelligence/roles` | `ModelRolesScreen` (header present; no page projection in file) |
| `settings/intelligence/on-device` | `OnDeviceModelsScreen` (header present; no page projection in file) |
| `local_model_lab` | `OnDeviceModelsScreen` (header present; no page projection in file) |
| `settings/intelligence/context` | `ContextPrivacyScreen` (header present; no page projection in file) |
| `settings/voice/input` | `SpeechRecognitionSettingsScreen` (no common header token; no page projection in file) |
| `settings/voice/speech-lab` | `SpeechLabScreen` (header present; no page projection in file) |
| `voiceRoute` | `SpeechOutputSettings` (header present; no page projection in file) |
| `settings/intelligence/policy` | `IntelligencePolicyScreen` (no common header token; no page projection in file) |
| `settings/voice/elevenlabs` | `ElevenLabsSettingsScreen` (header present; no page projection in file) |
| `assistant_diagnostics` | `AndroidAssistantScreen` (header present; no page projection in file) |
| `android_assistant` | `AndroidAssistantScreen` (header present; no page projection in file) |
| `voice_settings` | `VoiceSettingsScreen` (no common header token; no page projection in file) |
| `permissions` | `PermissionsScreen` (no common header token; no page projection in file) |
| `storage_backup` | `StorageBackupScreen` (no common header token; no page projection in file) |
| `discovery` | `PairingScreen` (header present; no page projection in file) |

### Historical owner/chrome review findings

The initial inventory found nested Settings headers without page projections. A follow-on coding batch now adds root-level `SettingsChildZone` bindings to Brain, Voice, listening, speech output, ElevenLabs, Permissions, mode profiles, model browser/roles, provider list/detail, image models, on-device models, intelligence policy/usage, Android assistant, connected apps, Skills, Check-up/technical report, notification preferences, Storage, Advanced and the diagnostic lab screens. These bind the exact navigation route template and entry ID, preserve existing callback owners, project the title into the utility band and retain original headers only for standalone hosts. Bindings live outside lazy-list headers so scrolling cannot dispose the only navigation action. Provider detail retains the selected provider name; technical model and dispatch-history variants retain their distinct titles.

This is navigation/context presentation. Exact setting values and typed setting-write owner coverage still require separate review; a Back action is not evidence that every field is available to Halo. Form controls, credentials and OS request buttons remain with their original owners. External/native permission dialogs remain intentional system-owned exceptions. Automation/routine and other exceptional subpages were individually reviewed and migrated in the subsequent integrated batch. No build or physical qualification is inferred from this source migration.

The review identified unrelated New note/Find/Workspace defaults on unmatched routes and the need to admit exact child page routes. The shell owner reports that fallback and exact-route matching are corrected in the concurrent integration batch. File Tools and Memory object-family guards remain part of the separate nested-editor integration review.

At initial review, compact Orb host width104dp combined with a centered56dp Orb and right-aligned48dp menu gave overlapping touch bounds; tablet inline expansion could grow the footer by440dp. Bell counts also summed historical review links and notifications, and root highlighting differed between bell subviews. Findings were sent to the shell owner, who reports source corrections for nonoverlapping controls, modal disclosure, pending-identity filtering/deduplication and preserved prior-root highlighting. Subsequent installed05 checks confirmed fixed root navigation and retained Completed filter across bell tabs; other original findings require the scoped evidence below, not a blanket rendered acceptance claim.


## Historical semantic audit before checkpoint04 integration

The earlier route/header inventory is a historical pre-migration snapshot. The 28 Settings-child and remaining ordinary-screen batches have now been applied. At that audit, the newly planned adapters were not yet integrated. The following gap table records that historical finding, not current implementation status; the resolution table below supersedes it. This review did not run builds, phone tests or speech tests. Retaining a touch control and publishing its UI action ID does not establish natural-language operation: `CurrentScreenTools` deliberately cannot invoke UI callbacks.

| Area | Verified existing owner coverage | Concrete gap / next action |
| --- | --- | --- |
| Appearance / Language | `host/AppPreferencesActions` backs `halo_get_preferences` / `halo_set_preferences`: appearance mode, fontScale, startScreen, palette/accent/tone/background, contrast, motion, guideLight, language. | No claim that these fields are absent. Settings root/inline surface context should list these canonical tools, then test exact current owner readback. |
| Voice / learning / provider checks | `host/VoiceConfigurationTools`, `LearningConfigurationTools` -> `LearningConfigurationOwner`, `SpeechInventoryTools`, `ProviderConfigurationTools` -> `ProviderControlRepository.requestProviderCheck` already provide typed reads/writes with policy/version admission. | `SettingsChildZone` supplies only navigation actions and empty canonical tool IDs. Ordinary `BindShellChildPage` learning also advertises no canonical tools. Add route-specific existing tool discovery; do not equate Back metadata with configuration coverage. Provider selection/model/role edits require separate inventory from provider checks (the latter do not select). |
| Library presentation | `NotebookViewModel.setQuery`, `setFilter`, `setSort`, `toggleSelection`, `clearSelection`, `selectCollection` own the actual visible list. | Shell actions expose only some filters. No typed adapter found for setting the actual current Library query/sort/selection. `halo_search_notebook` returns search results rather than changing this view. Requires a scoped current-view owner, fresh selection/readback, not a fabricated persisted setting. |
| Library collections / favorites / batch lifecycle | `NotebookViewModel.createCollection`, `deleteSelectedCollection`, `addToCollection`, `removeFromSelectedCollection`, `toggleFavorite`, `applyToSelection` use the existing Workspace owner. Individual note archive/trash/restore exist via `WorkspaceNoteActions` and `NotebookUseCases`. | No canonical collection/favorite tools found in registered app tool sources; selected mixed-object batch operations are not equivalent to individual-note lifecycle tools. Inventory exact object types and owner version/selection guards before adding adapters. |
| Saved chats | `ConversationsScreen` keeps query as local Compose state; `RoomConversationStore.trashSavedConversation` / `restoreSavedConversation` own deletion/undo; continuation is the existing navigation callback. | Clear-search/Continue are touch carousel actions only. `LongTaskToolExecutor.halo_search_conversations` searches the current conversation; it is not saved-chat library search or trash/restore. Add explicit owner-level saved-chat operations plus scoped continuation admission if parity is required. |
| Routines / notifications / saved devices | `RoutineManagementTools` supports list/get/edit/enable/delete/run/status; `NotificationReplyTools` and `NotificationSettingsToolExecutor` retain fingerprint/version checks; `SavedDeviceToolExecutor` supports saved-device connect/disconnect/autoconnect/forget. | Applied child helper currently advertises no existing canonical tools. Bind exact route-specific known tools and verify actual receipt/readback; a projected Edit or Reply entry does not execute the typed operation. Routine suggestion approval is not implied by save-existing support. |
| MCP servers | `McpSettingsService.configuration/save/setEnabled/setToolEnabled/connect/refreshTools/remove` owns actual service changes; Integrations/McpServer UI call their view models. | No typed MCP configuration tool found in app registration sources. Current Add/Edit/details only navigate/open forms. Non-secret enable/refresh/tool toggles need bounded owner adapters if required; credential values must remain explicitly entered through secure existing form ownership. |

Supported deliberate boundaries: Android grants/pickers/Bluetooth-enable dialogs remain user/OS-owned; MCP/provider bearer tokens and keys remain secure credential-entry fields, not ordinary assistant text arguments; BuddyBot hold-to-arm/joystick/emergency stop and hardware capture/playback require existing physical authorization and truthful device receipts. Those exceptions do not excuse missing ordinary list/search/configuration coverage. The scoped semantic accessibility tools are an explicitly governed fallback, not evidence that every native typed owner has been mapped or every screen tested.

This is an actionable partial source audit, not an exhaustive field-by-field parity PASS. Next verification must exercise current-view lifecycle, stale selection/version rejection, and real owner effects across the listed areas.


### Integrated discovery follow-up

The applied `staged-semantic-discovery` batch adds exact-route canonical tool ID mapping to both child helpers using existing registered preference, provider check, voice, speech inventory, local setup, learning, notification, routine and saved-device executors. It adds actual provider detail route IDs and actual rendered routine/review IDs; existing device IDs remain scoped. It does not guess a model ID from a list, map UI display revisions to canonical source versions, or make UI callbacks callable. All 38 mapped tool names were checked against the actual executor sources and registrations; these source mappings are integrated. Current gate outcomes are recorded below. Appearance/Language adds the existing write tool alongside its read tool. Subsequent integrated Library, saved-chat, MCP and local-role adapters address the specific safe operations listed below.


## Historical checkpoint09 integration record (2026-10-26)

All prior staged batches described here are now applied: ordinary-screen chrome/context, exact-route canonical tool discovery, Local/Power UI copy, Library view/organization, saved chats, MCP settings and local model roles. The initial route inventory and historical gap table above are retained as review history. Neither is an up-to-date list of missing implementations.

| Historical gap | Integrated source resolution | Remaining boundary |
| --- | --- | --- |
| Settings current-page discovery | `SettingsChildZone` and `BindShellChildPage` publish exact-route existing canonical tool IDs; actual provider/routine/device IDs are scoped; unavailable true source versions remain null. Appearance/Language exposes existing preference read/write. | Metadata does not invoke a callback or grant authority; each typed owner still validates its own fresh version and authorization. |
| Local/Power terminology | Control explanation/hints, Context privacy and mode-profile headings/consent copy are aligned in EN/RU/LV/PL. | Internal PRIVATE/CLOUD IDs, stored profiles, mode owner and exact consent meaning remain intact. |
| Library current view | `NotebookLibrarySession` binds the resumed root Library to real `NotebookViewModel`; typed read/set covers query/filter/sort/collection filter/explicit visible selection and reports requested versus settled rendered state. | Local foreground grown-up admission only; Memory navigation is separate. No hidden source attachment or arbitrary UI callback invocation. |
| Collections/favorites | Typed organization reads and one-use writes go through existing VM/repository/Room owner with exact source/favorite/collection/membership comparison, transaction readback and lifecycle admission. | Mixed-object batch CONTEXT/archive/trash/restore/Undo/purge are not implemented by this organization adapter. Existing touch controls and specialized per-object owners remain; this is a residual typed parity boundary, not a blanket completed-action claim. |
| Saved chats | Typed list/search/get/trash/restore/open use `RoomConversationStore` and guarded lifecycle/navigation owners; exact chat IDs and snapshots remain authoritative. | Source/version, scope, destructive approval, stale/replay and post-dispatch disclosure boundaries still apply. Saved content and spoken continuation acceptance require separate runtime/device evidence. |
| MCP settings | `McpSettingsTools` reads bounded non-secret metadata and performs guarded server enable, tool enable and already-connected discovery refresh through `McpSettingsService`/`McpIntegrationManager`. Exact token comparison, write and readback share the existing manager lock; registry synchronization remains with the service. | No implicit connect, credentials, endpoint/trust changes, new server or remote tool calls. All writes preserve ALWAYS/NEVER_REPLAY; refresh preserves connected-service remote policy. Revoked access withholds mutation metadata; post-dispatch exceptions remain UNKNOWN. |
| Model roles/defaults | Existing `ModeProfileTools` remains the default provider/model/routing owner. `ProviderLocalRoleActions` and typed local-role tools add non-CHAT explicit eligible installed local targets, disable and safe-local inheritance using the actual repository/router. | Explicit local FAST/other roles may coexist with configured remote CHAT, preserving CHAT/provider/policy unchanged. Inherit still requires a safe local default; AUTOMATIC, remote target expansion and CHAT role mutation are excluded from this adapter. No generation/download/testing or measured qualification is inferred. |

Deliberate manual/system boundaries remain: Android permission grants, file pickers and Bluetooth-enable flows; secure provider/MCP credentials and remote-disclosure expansion; physical hold-to-arm/joystick/emergency controls; device pairing, audio/camera effects and provider-paid actions retain their existing guarded owners and physical/provider acceptance. These boundaries do not convert residual ordinary-action gaps into PASS.

### Current evidence and outstanding gates

Checkpoint08 full app unit suite: **2,135 tests; 2,134 passed; 1 failed**. The failure is `AssistantToolsTest` exact manifest discovery equality: its fixture omitted the real `CurrentScreenTools` owner while the production graph and manifest include `halo_current_screen`. Checkpoint09 is running with the fixture correction, Home/short-IME repairs and sheet locale changes. Its outcome is **PENDING**. Checkpoint08 was **not installed** on the phone; source fixes are not physical acceptance.

Installed05 physical evidence reported by the integration owner:

- Fixed root navigation works; Office Tools retains the visible, usable Library body. Landscape Library scrolling reaches Items89 and the first Saved chat; initial edge clipping was ordinary viewport clipping, with no layout repair required.
- Bell Activities → Notifications → Activities preserves the Completed filter.
- Language selections EN/RU/LV/PL were exercised. The RU200 footer defect has a source correction in08; installation and physical recheck remain pending.
- Theme Cancel restored the original values. Apply Sage/Paper/Mist survived reopen. Dark toggle worked and Light was restored.
- The agent-created `QA62-interface-note` saved and reopened with exact body `QA62-note-body-check`; no existing user objects were changed. The observed “1 words” wording has a staged EN/RU/LV/PL quantity-resource correction; it is not yet an installed fix.

Installed05 native instrumentation group is **NOT_RUN**. Voice testing failed/deferred; there is no spoken/STT-final, audible-output or paid-provider qualification. TalkBack, unlisted physical journeys, hardware effects and full issue closure remain unqualified. The observations above prove only their named interactions on installed05, not every setting, typed action or device state. This reconciliation changed documentation only; no source, test, build or device action was performed.

## Delivery recovery update

Checkpoint12 full app regression passed **2,150/2,150**, zero failures/errors/skips, with both APKs built. The main action list now uses only the current page actions; fixed destinations and standalone/Mini compatibility retain their original owners. Duplicate Note/Canvas utility actions are removed only when the identical enabled carousel action is available; compact/large-font headers and unique read-aloud controls remain. Library search has a persistent accessible input label. Saved-chat fixture collectors stop before database teardown, preserving every original version/data assertion.

An existing read-only headless Android17/API37 emulator is being used for independent silent checks. Setup permissions, provider keys, model downloads and device audio were skipped through actual optional UI. It exposed inherited scroll between setup stages; the only production change after full12 keys the saveable scroll state by setup stage while preserving form composition/drafts. Checkpoint13 recompiles both APKs; emulator verification is pending. This is not a fresh phone or human speech qualification. The owner confirmed the phone is unavailable for now.

Final coding checkpoint: full12 passed2,150 cases with zero failures/errors/skips;13 rebuilt both APKs after only the setup scroll-state key change. The owner then deferred further tests. Temporary emulator stopped; final phone/native/human acceptance remains pending. See the authoritative delivery ledger and machine-readable manifest for exact source/APK attribution.
