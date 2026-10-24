# Interface 4.0 screen consolidation and parity

Integrated test execution and exact device/artifact results: [verification ledger](INTERFACE-4-VERIFICATION.md). Sub-batch source notes below retain their original evidence boundaries.

Issue #62. This records screen-source changes and canonical owners, following
`AI-CONTROLLABLE-TOOL-VERIFICATION-TEMPLATE.md`. It is not device, STT or provider
acceptance. Shell integration and its route catalog are tracked separately.

## Actual old to new paths

| Surface/action before | Path after consolidation | Existing owner and retained behavior |
|---|---|---|
| Home presence Orb and empty composer microphone | Shell Orb when `LocalShellHaloOrb=true`; standalone hosts retain local Orb/microphone | `HomeViewModel`, `HomeOrbControl`; shell owns foreground lifecycle, Home continues dashboard/catalog refresh on resume |
| Home typed draft Send, busy Stop, retry, attachment/tools menus, history/new conversation | Remain directly in Home | Existing text submission, conversation and capture owners; no draft migration or new dispatch path |
| Screen header Orb, fallback Talk, empty-state Orb | Shell Orb when provided; Screen retains local launcher when shell is hidden, including ambient hosts | Same passed `orbControl` / `onTalk`; `LocalScreenStore` still owns layouts |
| Screen Customize/Apply/Cancel/Add/appearance/new note, card source/open/refresh/hide | Remain in Screen chrome, editor and card sheets | `LocalScreenStore`, existing source repositories and callbacks; previews and stale draft rejection unchanged |
| Mini Orb plus empty editor microphone | One persistent bottom local Orb with compact privacy and destination menu; editor exposes Send only for a nonblank draft | Same shared `HomeViewModel`; header Stop remains, approvals still require Review; explicit close cancels input, navigation/configuration disposal no longer cancels the shared session |
| Notebook and Study header Start Voice Mode | Shell Orb/catalog voice-mode entry; header retained when shell does not own Orb | Same `onStartVoiceMode` callback and shared Home voice owner; continuous Voice Mode must remain separately selectable from one-shot Talk |
| Library Find related notes in header and search card; Saved chats/In chat utility rows | One upper three-dot menu with Find related notes, Saved chats and In chat count | Same local search/working-set sheets and existing CHATS filter; ordinary Library search field, sorting, selection and creation remain direct |
| Devices inline phone details and repeated same audio test on each accessory card | Upper three-dot menu: This phone opens scrollable capability sheet; Test audio output appears only with a visible system accessory | Same capability projection and `onTestAudio` callback; card identity/connection truth retained; Add device stays direct |
| Activity always-visible explanatory subtitle | Upper three-dot Details entry opens plain-language explanation | Existing Activity wording; filters, receipt rows and approval Review remain direct |
| Settings root Guided setup/About rows | Upper three-dot Guided setup/About menu; both remain discoverable through Settings search | Same setup callback/About page and version; Search stays direct and Advanced stays last list row |
| Study Lesson options dropdown below title | Same action set in upper three-dot menu | Same source/progress, read, save summary, finish and delete-confirmation callbacks; generating/comparison, source freshness, saving and completion guards retained |
| New lesson optional age/language controls toggle at step four | Step-four upper three-dot optional-controls toggle | Same `optionsExpanded` state and existing fields; execution location choices stay direct and never default to cloud |
| Studio background/presets/copy/versions/discard/export utility button row | Upper three-dot menu with those exact existing actions | Same callbacks and exact current project; busy disables/dismisses menu, clean draft disables Discard; direct Save, undo/redo, layers/transforms, import/camera/pen and AI proposal remain |
| Kids hero and voice-dock duplicate launchers | Shell Orb projects scoped Kids field target, current signal and Stop; hero/dock central microphone hidden only while shell owns Orb | Existing `openVoice(true)` TOPIC/ANSWER/IDEA/DRAFT selection and `stopActions`; Type instead/Help/Grown-up controls and ordinary field dictation retained |

`LocalShellHaloOrb` defaults to false. Its value is an ownership declaration by
the host, not availability or permission. Only hosts actually rendering the shell
launcher may set it true. Runtime admission, privacy, Kids, permission,
confirmation and provider checks remain with the existing owners.

## Preserved screen action inventory

| Screen | Direct controls preserved | Registered semantic owner/examples already in source |
|---|---|---|
| Notebook / note editor | Create Note/Canvas, import, search, filters/sort, working set, collections, select/batch, favorite, archive/trash/restore, note formatting/blocks/attachments and contextual assistant proposals | `NotebookRepository`, `NotebookDocumentRepository`, `NotebookUseCases`, `NotebookAssetRepository`, `WorkspaceContextStore`; `halo_create_note`, `halo_note_get`, `halo_note_edit_block`, `halo_note_trash`, `halo_note_attachments`, `halo_add_to_working_set` |
| Study home / lesson | Topic/source selection and review, preferences, lesson start/continue, exercise/answer, saved-step question, flashcard review, chapter reading and playback | `StudyUseCases`; `halo_study_with_halo`, `halo_study_state`, `halo_study_submit_attempt`, `halo_study_set_preferences`, `halo_study_card_review` |
| Devices | Explicit discovery, saved-device open/retry, phone capability details, audio output test | Existing `DevicesViewModel`, host/device and audio-pairing owners; discovery remains a raw-only developer workflow, never synthetic assistant connection proof |
| Activity | Filter, expand/collapse steps, Review steps, open source object and Home recovery | Existing Activity and run/approval owners; read-only navigation does not approve an action or certify completion |
| Settings | Existing user list, Advanced boundary, voice/privacy/provider configuration and explicit permission/setup controls | Existing settings repositories and authorization owners; no invented universal settings-write operation |
| Studio / Canvas | Selection, pen gestures, layers, edits, undo/redo, versions, save/export/insert and scoped AI proposal review | `StudioService`, `NotebookCanvasRepository`, `CrossDeviceInkController`; `halo_studio_get`, `halo_studio_edit`, `halo_studio_save`, `halo_studio_export`, `halo_canvas_move`, `halo_ink_selection` |

The list groups existing actions rather than asserting every UI affordance has a
new semantic tool. `WorkspaceOperabilityContracts` and `HaloToolDescriptors` are
the source for actual versioned ToolOperability metadata and schemas. No tool is
registered by this screen consolidation. Existing exact document/source revisions,
stale rejection, results and durable readback requirements still apply.

Canvas and Note editors already have an upper three-dot `EditorWorkspaceHeader`
and native tool sheet. This batch keeps that existing menu and its direct
high-frequency editing dock; adding a second overflow would duplicate ownership.

Text requests and STT-final voice requests continue through the existing
CognitiveSession/ToolRegistry path. Navigation entries only present a destination;
they do not substitute for typed domain operations. Contextual editor Ask halo,
ordinary field dictation and read-aloud remain because they operate on an exact
document/lesson/field rather than duplicating the assistant launcher.

## Regression sources authored before the combined gate

| Source | Meaningful check |
|---|---|
| `ShellOrbConsolidationTest` | Shell hides empty composer Talk, typing still sends the exact draft, busy Stop works and preserves draft; standalone default keeps Talk; status text survives local Orb removal; disabled/focus admission remains enforced |
| `ScreenShellOrbConsolidationTest` | Shell suppresses custom Orb and null-control fallback Talk; layout Apply and Cancel still invoke their original callbacks |
| Existing `HomeComposerInteractionTest`, `HomeComposerPriorityTest` | Existing composer editor, busy state and admission regressions remain applicable |
| `ScreenOverflowInteractionTest` | Each changed screen menu dispatches its exact callback once and dismisses; system audio availability, Study fresh-source/completion/saving guards, optional setup controls, Studio format/copy/version actions and busy/clean-draft admission remain functional |
| `KidsShellOrbBindingTest` | Shell primary uses latest scoped Kids target, active Stop invokes existing cancellation, disposal clears without replay; typed/help dock actions remain while duplicate voice launcher is absent |
| `MiniHaloInteractionTest` | Single local voice Orb, persistent footer below editable draft with long reply, existing busy Stop and review behavior |
| Existing `WorkspaceOperabilityTest`, `ToolOperabilityReviewTest` | Existing registered feature/typed contract review; unchanged source does not itself establish a fresh passing run |

## Current-page Orb callbacks

`ShellPageActions.kt` projects ephemeral page actions into the shell; it does not
create another engine, microphone, authorization system or semantic executor.
Metadata (`id`, title, glyph, enabled) is separate from callbacks. Callback-only
recomposition keeps projection identity stable through `rememberUpdatedState`.
The host supplies `LocalShellPageOwnerFilter` for the current route family and
exact object, so animated outgoing pages cannot overwrite the active page.
Where Navigation owns the page lifecycle, the binder also records that exact
back-stack entry ID for the shell filter, including transitions within one family.

| Page family / object | Actual existing callbacks projected |
|---|---|
| `screen` | Customize, Screen settings, Add card; existing layout/customizing/interaction guards |
| `notebook` | New note, Find related notes, In chat working set; same mutation guard and existing sheets |
| `note` / exact note ID + revision | Outline, Find and replace, More tools; existing editor state/sheets |
| `canvas` / exact board ID + current node/edge identity | Find on canvas, Fit all cards, More tools; same load/missing/node guards; no pen gesture synthesis |
| `studio` / selected project ID + revision | Layers, Versions, Save with current busy/unsaved guards; project list instead exposes the existing New project dialog |
| `study` / saved lesson ID + update/exercise identity | Sources and progress, Listen then explain, Save notes; same generation/comparison/fresh-source/saving guards |
| `study` home/setup | New lesson, Material picker, optional learning settings; existing busy/wizard-step guards and original setup state |
| `settings` / `advanced_settings` | Search, Guided setup, About; the exact Advanced route projects its existing report/model-role routes; a current modal projects its existing Back callback |
| `devices` | This phone details, existing audio test only with a visible accessory, existing Add device navigation and its route/security gates |
| `activity` | Existing Details dialog and requests/completed filters |

Invocation requires the exact still-published owner/source/metadata and enabled
action. A removed, disabled, replaced, disposed or no-longer-eligible action
returns false; it must never fall back to a generic Home request. Disposal only
clears the projection it owns. Callbacks retain the original UI owner and all
domain confirmation/consent paths. Assistant typed/STT domain operations remain
the registered tools documented elsewhere; opening a local page panel is not
proof of assistant functional parity or a completed mutation.

`ShellPageActionsTest` covers callback freshness without metadata loops, exact
source/object invalidation, disabled/removed actions, identity-safe disposal,
and simultaneous outgoing/incoming page filtering. `SettingsPageActionsTest`
drives real Settings search/About/Back/setup callbacks through the projection
and verifies stale modal ownership cannot dispatch. Root owns shell/menu mapping
and the combined gate; device, spoken and remote-provider acceptance is not
inferred from these source tests.

## Batch E3: notification and saved-device operations

These adapters implement existing settings/device operations through the same
owners as the UI. They do not add a runtime, hardware qualification, discovery,
pairing, security approval or notification-content reader.

| Existing UI operation | Registered semantic adapter IDs | Canonical owner and exact source guard |
|---|---|---|
| Saved device discovery/detail | `halo_saved_devices`, `halo_saved_device_get` | `SavedDeviceActions` over the existing `DeviceRegistry`/`DeviceSessionManager`; bounded 64-row pages, exact device ID and process-local `saved-device:` source token |
| Global glasses thumbnail quality 0–5 | `halo_device_preferences`, `halo_device_thumbnail_quality` | `SavedDeviceActions`, also used by `DevicesViewModel.setThumbnailQuality`; integral range and exact `device-preferences:` token, persistence readback required; no photo capture |
| Retry/connect/disconnect saved device | `halo_saved_device_retry`, `halo_saved_device_connect`, `halo_saved_device_disconnect` | Same reconnect coordinator and existing session, selected saved ID/version and session identity checked; retry retains supported-kind/auto-connect/in-progress guards; dispatch never certifies connection/disconnection |
| Auto-connect preference / forget saved device | `halo_saved_device_autoconnect`, `halo_saved_device_forget` | Same UI-factored owner; exact saved ID/version, persisted preference or removal readback; enabling retains existing reconnect request, forget retains disconnect-then-remove sequence; no OS unpairing/certificate/security automation |
| Notification app access policy | `halo_notification_settings_apps`, `halo_notification_policy_get`, `halo_notification_policy_set` | `NotificationSettingsAppSource` uses the same launchable-app scope as Settings; exact package/install-version plus `RoomNotificationRepository` mutex/CAS source token; existing BLOCK deletion and HIDE_CONTENT redaction retained |
| Digest delivery channel / selected wearable | `halo_digest_delivery_get`, `halo_digest_delivery_set` | `DigestDeliverySettingsRepository` atomic compare-and-set of both fields; exact `digest-delivery:` token; wearable additionally requires saved endpoint ID/version, and other channels require an empty wearable target; configured does not mean audible/delivered |

All mutations require a nonblank authorized interaction and `canConfigure` adult
configuration gate. The existing registered authorizer supplies durable ALWAYS
approval and NEVER_REPLAY policy; the separate direct-user shortcut flag is
intentionally not required by sensitive approved operations. Descriptor/privacy,
notification category, Kids, OS permissions and remote-content gates remain
root integration responsibilities. Model arguments cannot grant any of them.
Read-only settings discovery returns bounded references/preferences rather than
notification bodies. App labels are untrusted data.

Source tokens are checked again by the owner under its mutex. Device tokens are
consumed before host effects, including uncertain requests; device/policy tokens
expire across owner restart. Unexpected post-write/host exceptions return
completion-unknown failures and require fresh readback rather than blind replay.
OS notification access, exact-alarm access, Bluetooth pairing and any physical
permission/security confirmation remain human/OS flows.

Regression sources authored before the E checkpoint:

- `SavedDeviceToolExecutorTest`: exact ID/version, current interaction/adult gate,
  approved context with direct-user shortcut false, integral quality/persistence,
  same-owner auto-connect/forget, supported session/retry guards, bounded discovery,
  consumed tokens, restart, uncertain transport and truthful dispatched results.
- `NotificationSettingsToolExecutorTest`: exact installed-app/version/policy,
  all four access policies, closed schemas, current approved interaction, exact
  wearable identity/version, local channel choices, no speech/fallback and unknown
  post-write outcome/readback.
- `NotificationSettingsRepositoryCasTest`: real repository policy CAS plus existing
  redaction/purge behavior, restart rejection, atomic digest field persistence and
  source invalidation by the existing UI repository methods.
- `DeviceNotificationOperabilityTest`: actual central descriptor resolution for
  all 14 adapters, both input channels, version guards, bounded result metadata,
  and ALWAYS/NEVER_REPLAY for each mutation.

No Gradle, physical device, spoken STT or remote-provider run is claimed by this
section. Root owns registration/context forwarding, centralized policy and the
combined checkpoint. Real typed/spoken workflows (policy change/readback, digest
target save/readback, saved-device preference/retry/readback) remain NOT_RUN until
that evidence is attached; dispatched hardware effects remain distinct from
physical readiness and audible/delivered acceptance.

## Existing automation management

`RoutineManagementActions` is shared by `RoutinesViewModel` and
`RoutineManagementTools`. It reuses the same repository, scheduler,
`RoutineExecutor.validateForSave`/validation/grant copy and occurrence executor.
No alternate automation executor, tool vocabulary or constituent approval path
is introduced.

| Existing control | Semantic operation |
|---|---|
| List/read saved routines, including unavailable definitions | `halo_routines_list`, `halo_routine_get` |
| Enable/disable | `halo_routine_set_enabled` |
| Edit/save existing routine | `halo_routine_save_existing` |
| Delete and cancel schedule | `halo_routine_delete` |
| Run now through existing occurrence owner | `halo_routine_run_now` |
| Recorded run status/counts | `halo_routine_run_status` |

Every mutation names an exact saved ID and `routine:` source token. Repository
comparison/update/removal uses the exact raw row in a Room transaction, including
the grant and schedule/run fields. Tokens also expire after owner restart or a
claimed manual run. Read-only discovery does not auto-disable malformed rows;
those rows can be disabled/deleted but never enabled, edited or executed.
The editor shares the command owner, including atomic learning-suggestion review,
future one-time trigger checks, existing action grants and scheduler outcomes.

Read responses stay within the normal tool output budget: get defaults to summary
and section lengths, with exact `trigger`, `conditions`, `actions` or `delivery`
text pages of at most 320 UTF-16 characters and surrogate-safe boundaries. A
caller supplies the same source token while assembling pages; changed sources
are rejected. There is no silently truncated JSON. Edit/save accepts requested
fields only and preserves omitted fields exactly before full canonical schema,
automation-policy and grant validation; large routine renames do not require
round-tripping the full recipe.

The existing registry authorizer owns ALWAYS approval and NEVER_REPLAY policy.
Current foreground/grown-up configuration access and a nonblank authorized
interaction are still required; the direct-user shortcut flag is not forged.
Enabling/running requires an existing matching grant. An explicit approved edit
uses the same grant review/save path as the editor. RoutineExecutor retains every
constituent action's automation eligibility, current permission/privacy policy,
resource and execution limits; this adapter cannot approve a sensitive, paid or
external action that the canonical routine policy forbids.

Run-now passes an exact saved-revision `RoutineOccurrence` to the existing engine
and returns dispatch/run identity separately from recorded completion/delivery.
Status queries expose lifecycle/counts, not private action output. Post-write or
dispatch uncertainty requires readback and never blind replay. Deleting preserves
run history and cannot undo effects already dispatched elsewhere.

`RoutineManagementToolsTest` covers no-context/adult gates, approved contexts with
direct-user shortcut false, exact stale/cross-target/Room CAS, full/partial edits,
canonical action policy/grants, malformed sources, consumed run tokens, uncertain
effects, persisted scheduling distinction, bounded Unicode-safe large-definition
paging, and actual centralized ALWAYS/NEVER/both-channel contracts. Sources are
authored before the root checkpoint; no executed Gradle/device/spoken/provider
acceptance is claimed here.

## Exceptions, gaps and acceptance status

- Mini transfer follow-up: the local Orb now binds the native Activity lifecycle,
  so a navigation entry stopping does not release the shared capture. Both external
  Mini expansion callbacks use one per-host transfer marker before launching Main
  and finishing Mini. Only that marked finishing host skips its stop cancellation;
  failed launch clears the marker and cancels capture, while Close, ordinary
  backgrounding and configuration handling retain their existing behavior.
  `ShellSpeechLifecycleTest` adds route-stop, successful transfer and failed-launch
  regressions; `MiniHaloActivityPhysicalTest` adds actual Mini-to-Main navigation
  with a release-boundary observer. These new cases are source only, NOT_RUN here;
  the native test does not establish real microphone/provider continuity.
  Main also adopts a weakly referenced STARTED/RESUMED Mini on receipt of an exact
  known non-Mini screen intent, covering the canonical `HaloScreenLauncher` path
  from Application context. Adoption marks and finishes the visible source before
  its stop; stopped/finishing Mini, absent/unknown routes and Mini intents cannot
  qualify. `MiniHaloHostContextTest` covers the eligibility matrix, and the native
  transfer test includes the canonical launcher path (also NOT_RUN here).

- Build and test execution are owned by the root combined gate. New tests are
  authored here; this document does not claim their execution result.
- Physical-device dark/light 360/412dp, 200% text, IME/rotation, Back/navigation,
  accessibility focus and shell-to-Mini lifecycle acceptance: NOT_RUN here.
- Three real workflows (create/read a note; submit/read back Study progress;
  edit/export/read back a Studio project) through typed device, spoken device,
  permitted Power typed and permitted Power spoken routes: NOT_RUN here.
- New permission requests, external security/pairing and physical pen gestures
  retain human/OS/hardware gates. A discovery screen is not connection proof;
  audio output test is not a successful heard-speech confirmation.
- Native sheet focus and ambient/locked-screen visibility must be checked with
  shell route visibility. Modal content must not be obscured by the shell Orb.
- Menu relocations preserve existing domain capabilities; About/Details only
  reveal existing explanatory content and never infer action completion. Canvas
  already had upper overflow, so its existing sheet and frequent controls remain.
- Kids shell voice routes to scoped field input, not Home conversation submission.
  Saving behavior continues to rely on existing project cancellation/commit owners;
  native in-flight save interaction and lifecycle handoff still require testing.
- Mini uses the shared destination catalog, including Screen. The shell handles
  an external Mini Activity handoff before resolving the in-app Screen alias. Kids navigation must retain the shell whitelist
  and parent gate; no extra destination is inferred from a visible menu.
- No device evidence, actual STT recognition, remote-provider outcome or issue
  closure is inferred from this source consolidation.
