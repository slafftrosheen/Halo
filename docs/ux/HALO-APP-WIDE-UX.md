# Halo app-wide UX overhaul

Repository: slafftrosheen/Halo. Started from clean main, synchronized to 42873d108cda89fd439b4bc3e9ae889b0b2c1e7d on 2026-10-12. No GitHub Actions or force-pushes.

## Baseline evidence

Executed `./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-parallel --max-workers=1 --no-daemon -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m"`.

- Original baseline: BUILD FAILED in 2m 29s. UiCatalogue.kt had an unescaped dollar in the guidance format; StudyScreen.kt was missing LaunchedEffect.
- After regeneration and import repair: Kotlin compilation passed; 616 app unit tests ran, 1 failed (HomeComposerInteractionTest.everydaySuggestionPreparesEditableTextWithoutSending). BUILD FAILED in 7m 40s. The fixture placed the composer below an unscrollable welcome panel; production keeps the composer outside its scrollable content.
- Batch A first rerun: compilation passed; 621 tests, 2 failed due to background WorkManager exceptions escaping the production application in Robolectric. Shared guidance tests and repaired Home interaction passed. Plain Application as the test default reduced this to one escaped exception from explicit graph integration tests. Those fixtures now initialize WorkManager synchronously before constructing their graph. Final rerun: BUILD SUCCESSFUL in 3m 42s; 621 app tests, zero failures/errors/skips. Log: `.artifacts/ux-batch-a-final.log`. Integration WorkManager initialization now runs on IO and reuses an existing instance in a shared SDK sandbox. Logs are local `.artifacts/ux-baseline.log`, `.artifacts/ux-baseline-repaired.log`, `.artifacts/ux-batch-a.log`, `.artifacts/ux-batch-a-hermetic.log`, `.artifacts/ux-batch-a-stable.log`.
- Localization: 1870 baseline strings retained. Generator drift repaired by regeneration. `--check` now detects stale generated files without overwriting them; two isolated Python regression tests passed. Shared components add 3 reviewed EN/RU/LV strings.

## Audit findings and priorities

The baseline has severity-4 compilation defects. Initial heuristic score: 3/10 until compilation and tests pass; this is a source audit, not user acceptance. Remaining gaps require per-workflow review before rescoring.

- Study owns a reusable focus outline and lifecycle policy. Extracting these avoids coupling unrelated screens to Study. Shared motion is opt-in; static emphasis is the default for lists. Reduced motion and non-resumed lifecycle states prevent breathing outlines.
- Home already persists per-conversation drafts and keeps its composer outside scrolling content. Voice setup uses the visible word Talk with the more specific action only in accessibility text; setup/review/composer priorities need consolidation.
- Activity already sorts approval items first and preserves attempted/unconfirmed outcomes, but has no actionable route from its empty state and lacks a history filter.
- Notifications contain untranslated status/count/category/age/confirmation strings. Their reply confirmation boundary must remain explicit.
- Notebook exposes duplicated creation routes and its empty state does not distinguish empty collection/filter from a wholly empty workspace. New collection drafts use remember and can disappear on recreation.
- Canvas preserves drawing drafts, persists on stop/leave, and has real undo. Its empty board has no insert action; toolbar height and AI-result review need dedicated small-screen review. Never attach focus decorations to gesture surfaces.
- Devices separate connected/saved entries. Protocol, auto-connect, recording, robot arming and command leases must remain with existing owners.
- Intelligence shows a configured provider alongside an unchecked connection. Guide configuration, testing and model selection from recorded readiness; never equate credentials saved with verified generation.
- Speech recognition mixes useful test controls with capture-profile diagnostics; separate everyday controls from advanced detail. Download percentages must use measured bytes.
- Provider setup has two routes to the same screen; model browser also has two. Preserve aliases for deep links while contextual links use canonical destinations.
- Setup already durably dismisses automatic launch. Reopen intentionally from Settings; do not replace this lifecycle with new flags.
- Storage import/export/restore operations require review of busy-state gating and picker recreation; never save credential/password drafts in Bundle state.
- Routine and MCP screens require complete editing, validation and safety-review workflows; viewing configuration must never dispatch a tool.

## Batch tracking

| Batch | Scope | Status | Validation / remaining work |
|---|---|---|---|
| A | Baseline and shared design | Complete | Compile + 621 app tests passed; 2 Python generation tests passed. Shared Study focus migrated. Physical visual/motion checks pending |
| B | Home, Activity, Notifications | Implemented and unit verified | Compile + 626 app tests passed; physical/provider acceptance pending |
| C | Notebook, Canvas, Memory | Implemented and unit verified | Compile + 629 app tests passed; media and physical acceptance pending |
| D | Devices and discovery | Pending | Safe manual pairing and truthful connection/recording/arming states |
| E | Intelligence and Voice | Pending | Guided setup, model lifecycle, speech tests and output routing |
| F | Settings, setup, tools, integrations | Pending | Canonical owners, backup confirmations and editor validation |
| G | Consolidation and acceptance | Pending | Route checks, narrow/tablet/font/theme/keyboard/motion/device acceptance |

## Destinations

Extracted using `python scripts/audit_ux_routes.py` from actual AppShell composable declarations. Lines refer to the starting snapshot. Pending means neither a completed screen overhaul nor accepted workflow; source reconnaissance alone is insufficient. The inline speech-output route renders SpeechOutputSettings.kt. Sheets and dialogs are audited with their owner screen.

| Destination | AppShell line | Screen implementations | Audit / validation / gaps |
|---|---:|---|---|
| `guided_setup` | 271 | HaloSetupScreen | Pending |
| `home` | 284 | HomeScreen | Implemented; unit verified; physical acceptance pending |
| `study?captureSource={captureSource}` | 298 | StudyHomeScreen | Pending |
| `activity?runId={runId}` | 311 | ActivityScreen | Implemented; unit verified; physical acceptance pending |
| `notebook` | 331 | NotebookScreen | Implemented; unit verified; physical/media acceptance pending |
| `notebook/note/{noteId}` | 351 | NoteEditorScreen | Implemented; unit verified; physical/media acceptance pending |
| `notebook/study/{sessionId}?review={review}` | 382 | StudyScreen | Pending |
| `notebook/canvas/{canvasId}?nodeId={nodeId}` | 414 | CanvasEditorScreen | Implemented; unit verified; physical/media acceptance pending |
| `notebook/file/{objectId}` | 461 | WorkspaceObjectDetailScreen | Implemented; unit verified; physical/media acceptance pending |
| `notebook/memory?section={section}&noteId={noteId}` | 476 | MemoryHubScreen | Implemented; unit verified; physical/media acceptance pending |
| `notebook/memory/{objectId}` | 505 | WorkspaceObjectDetailScreen | Implemented; unit verified; physical/media acceptance pending |
| `devices` | 525 | DevicesScreen | Implemented; unit verified; hardware acceptance pending |
| `device/{deviceId}` | 533 | BuddyBotControlScreen, HeyCyanControlScreen | Implemented; unit verified; hardware acceptance pending |
| `integrations` | 546 | IntegrationsScreen | Pending |
| `integrations/mcp/{serverId}` | 552 | McpServerScreen | Pending |
| `skills` | 558 | SkillsScreen | Pending |
| `runtime_status` | 602 | RuntimeScreen | Pending |
| `system_check` | 611 | SystemCheckScreen | Pending |
| `automation_sandbox` | 647 | AutomationSandboxScreen | Pending |
| `phone_control_sandbox` | 650 | PhoneControlSandboxScreen | Pending |
| `notebook/conversations` | 654 | ConversationsListScreen | Implemented; unit verified; physical/media acceptance pending |
| `notebook/conversation/{conversationId}` | 662 | ConversationDetailScreen | Implemented; unit verified; physical/media acceptance pending |
| `settings` | 689 | SettingsScreen | Pending |
| `automation?routineId={routineId}` | 695 | AutomationScreen | Pending |
| `automation_review/{routineId}/{suggestionId}` | 700 | AutomationScreen | Pending |
| `memory_learning` | 709 | LearningSettingsScreen | Pending |
| `notifications` | 712 | NotificationInboxScreen | Implemented; unit verified; physical acceptance pending |
| `notifications/{notificationKey}` | 718 | NotificationDetailScreen | Implemented; unit verified; physical acceptance pending |
| `notification_settings` | 726 | NotificationSettingsScreen | Pending |
| `ai_settings` | 730 | IntelligenceScreen | Implemented; unit verified; provider/audio acceptance pending |
| `local_audio_lab` | 744 | LocalAudioLabScreen | Audited; existing explicit bounded tests retained; physical/media acceptance pending |
| `vision_lab` | 750 | VisionLabScreen | Audited; existing explicit bounded tests retained; physical/media acceptance pending |
| `provider_setup` | 756 | ProviderCenterScreen | Implemented; unit verified; provider/audio acceptance pending |
| `settings/intelligence/providers` | 760 | ProviderCenterScreen | Implemented; unit verified; provider/audio acceptance pending |
| `settings/intelligence/providers/{providerId}` | 764 | ProviderDetailScreen | Implemented; unit verified; provider/audio acceptance pending |
| `settings/intelligence/images/{providerId}` | 774 | ImageModelsScreen | Implemented; unit verified; provider/audio acceptance pending |
| `model_browser` | 779 | ModelBrowserScreen | Implemented; unit verified; provider/audio acceptance pending |
| `settings/intelligence/models?providerId={providerId}&role={role}` | 783 | ModelBrowserScreen | Implemented; unit verified; provider/audio acceptance pending |
| `settings/intelligence/usage` | 791 | IntelligenceUsageScreen | Implemented; unit verified; provider/audio acceptance pending |
| `settings/intelligence/roles` | 795 | ModelRolesScreen | Implemented; unit verified; provider/audio acceptance pending |
| `settings/intelligence/on-device` | 801 | OnDeviceModelsScreen | Implemented; unit verified; provider/audio acceptance pending |
| `settings/intelligence/context` | 804 | ContextPrivacyScreen | Pending |
| `settings/voice/input` | 808 | SpeechRecognitionSettingsScreen | Implemented; unit verified; provider/audio acceptance pending |
| `settings/voice/speech-lab` | 812 | SpeechLabScreen | Implemented; unit verified; provider/audio acceptance pending |
| `settings/voice/output` | 816 | Inline content | Implemented; unit verified; provider/audio acceptance pending |
| `settings/intelligence/policy` | 822 | IntelligencePolicyScreen | Implemented; unit verified; provider/audio acceptance pending |
| `settings/voice/elevenlabs` | 826 | ElevenLabsSettingsScreen | Implemented; unit verified; provider/audio acceptance pending |
| `android_assistant` | 833 | AndroidAssistantScreen | Pending |
| `voice_settings` | 843 | VoiceSettingsScreen | Implemented; unit verified; provider/audio acceptance pending |
| `permissions` | 852 | PermissionsScreen | Pending |
| `storage_backup` | 860 | StorageBackupScreen | Pending |
| `discovery` | 863 | PairingScreen | Implemented; unit verified; hardware acceptance pending |

52 destinations extracted from AppShell. Aliases remain listed to preserve deep-link contracts.

## Batch A delivery

- Files: shared `ui/design/HaloFocus.kt` and `HaloFeedback.kt`; StudyHaloFx.kt delegates to shared focus and StudyScreen.kt imports LaunchedEffect. Reviewed ui.tsv plus generated UiCatalogue/EN/RU/LV XML. Generator now supports read-only --check; Python regression tests protect escaping and drift. Route audit script and this tracker. Compose guidance tests, pinned-composer interaction fixture, plain Robolectric Application default and explicit IO WorkManager initialization for graph integration fixtures.
- User-visible change: existing Study focus is retained, disabled actions stay unaccented, motion stops unless the destination is resumed and Android allows animation. Other components are ready for the following batches; their existence does not imply those screens have been migrated.
- Tests added: five shared guidance tests (one real click target; disabled follow-up tap; inert empty state until chosen; unknown progress; foreground/reduced-motion/motion opt-in and invalid measurements). Two generator tests. Updated Home interaction verifies editable suggestions with a visible Send control in a scrollable/pinned fixture.
- Exact final Android gate: `./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-parallel --max-workers=1 --no-daemon -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m"` — BUILD SUCCESSFUL in 3m 42s, 621 tests, zero failures/errors/skips. `python scripts/generate_ui_languages.py --check`, `python scripts/test_generate_ui_languages.py`, and `git diff --check` passed.
- Earlier failed gates are retained in local logs rather than omitted from the record. No APK/device acceptance is claimed. ADB lists a 23124RA7EO phone; screenshot review has not been performed for this batch.
- Next: Batch B — make the everyday assistant and activity/reply review paths use the shared attention and recovery vocabulary before expanding to editing and hardware workflows.
- Main commits use [skip ci] because the repository already has a push-triggered Android workflow and this task requires local validation without GitHub Actions. No workflow is changed.

## Batch B delivery

Changed HomeScreen/HomeComposer, ActivityScreen, NotificationInboxScreen, NotificationDetailScreen/ViewModel, AppShell, translations and regression tests. Home retains its editable draft and stable composer, visibly labels voice setup/interruption, prioritizes Stop, review or setup before normal input, and removes duplicate Inbox/review shortcuts. Activity offers saved All/Needs review/In progress/History filters, keeps approvals first and preserves actual terminal/unconfirmed outcomes. Empty Activity opens Home; an empty filter can return to all activity. Notifications use shared status/empty/recovery/progress guidance and localize counts, ages and confirmation/outcome wording without translating source messages. Replies still require explicit confirmation and fresh action identity; dispatch remains a handoff, not confirmed delivery.

Attention: static shared focus on required provider setup and valid reply review only; no timeline breathing. Disabled, running and prerequisite states suppress competing composer emphasis. Regression additions: composer priority (2), history filtering and truthful labels (2), 320 dp dark/2x voice-setup interaction preserving draft and disabled Send (1). Existing tests cover editable suggestions, stable Send/Stop targets, Russian larger labels, reply expiry/action replacement and dispatch truth. Gate: `:app:compileDebugKotlin :app:testDebugUnitTest --no-parallel --max-workers=1 --no-daemon -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m"`, BUILD SUCCESSFUL in 7m 58s; 626 tests, zero failures/errors/skips. Localization --check: 1912 EN/RU/LV keys; git diff --check passed. Log `.artifacts/ux-batch-b.log` retained locally. Live provider/media/reply effects, rotation and physical keyboard/TalkBack/visual acceptance remain open. Next is the dedicated Notebook/Canvas batch for creation, media insertion, context and durable editing.

## Batch C delivery

Changed NotebookScreen, CanvasEditorScreen, InboxCaptureDialog, WorkingSetSheet, NoteEditorScreen, ConversationsScreen, MemoryHubScreen, WorkspaceObjectDetailScreen, AppShell, reviewed translations/resources and NotebookGuidanceTest. Notebook distinguishes create/import/capture, opens the existing document picker explicitly, removes a duplicated note toolbox, explains Notebook/Canvas/Inbox/Memory and storage/sharing, preserves collection drafts, and gives collection/search/trash/Canvas empty states valid next actions. Item counts match visible rows. Collection/batch consequences and menus use formatted translations. Existing selection, archive/trash/restore, favorites, collections and Study handoffs are retained.

Canvas has a clear Add content flow covering text, files, capture, sketches, references and groups; selected cards receive contextual action emphasis, Undo stays visible with real availability, secondary controls scroll horizontally, and selection/pan/zoom guidance stays outside the gesture surface. AI drafts remain explicitly reviewed and applied; drawing, OCR, PDF, image stitching and voice memo owners are retained. Missing Canvas and export states have truthful guidance. Memory text/kind/scope/search and editor visibility survive recreation without saving automatically; memory correction drafts and history disclosure are also saveable. Working-set actions wrap below content rather than squeezing four columns, preserve original sources and show actual cloud policy. Note save labels are localized. Saved-chat search persists and Continue opens the exact conversation in Home. File-load failure gains Retry with plain feedback; failure paths preserve cancellation and avoid raw exception text.

Attention is static and contextual: first note/Canvas or empty-filter recovery; Add content on an empty board; actions for selected cards; empty Memory creation. Board/drawing gesture surfaces receive no focus wrapper. Existing Room/persistence tests cover note documents, card edits/positions/viewport flush on leave, local organization, sketch/markup, image/PDF operations and source retention. Three new guidance tests cover collection/search/trash recovery, Canvas versus note creation, disabled duplicate taps, 320 dp with 2x text and scrollability. Initial gate passed; final gate includes file recovery and restored Memory query: BUILD SUCCESSFUL in 6m 28s, 629 tests, zero failures/errors/skips. Exact gate remains `:app:compileDebugKotlin :app:testDebugUnitTest --no-parallel --max-workers=1 --no-daemon -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m"`. Localization --check: 1957 keys. diff --check passed. Logs `.artifacts/ux-batch-c.log` and `.artifacts/ux-batch-c-final.log` retained locally.

Physical file picker/camera/microphone/AI editing, large boards, full process-death and final screenshot acceptance remain open. This source/unit validation does not establish those effects. Next: Devices for explicit pairing, truthful readiness and safe recovery.

## Batch D delivery

Changed DevicesScreen, PairingScreen, HeyCyanControlScreen, BuddyBotControlScreen, reviewed translations/resources and PairingGuidanceTest/PairingFailureTest. Device lists explain phone use, supported glasses and Android audio accessories, wrap metadata and preserve phone detail disclosure. Pairing guides actual prerequisites, explicit selection, search, connection progress, failure recovery and runtime readiness in a scrollable screen. A transport connection alone never claims ready. Search failure offers explicit retry without displaying raw exceptions. HeyCyan connection controls use a vertical layout; protocol diagnostics start collapsed, nonsecret disclosure survives recreation, and forgetting requires confirmation. Recording and protocol owners remain intact. BuddyBot explains manual control, connection versus motor authority, and reports disconnected arming as Not confirmed. Stop, emergency controls, arming, supervisor and command leases are unchanged.

Attention is static around the safe prerequisite/search/connect step; no scan, connection or command starts from rendering. Five added tests cover explicit permission action at 320 dp/2x text, no premature readiness or duplicate connection, selection/prerequisite recovery, disconnected stale arming, and explicit scan retry with hidden exception details. The first compile failed on a missing import and resources regenerated after Gradle resource preparation; these were corrected before the final gate. Final exact gate: `:app:compileDebugKotlin :app:testDebugUnitTest --no-parallel --max-workers=1 --no-daemon -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m"`, BUILD SUCCESSFUL in 8m 10s; 634 tests, zero failures/errors/skips. Localization --check: 1995 keys; git diff --check passed. Logs retained locally. Real Bluetooth permissions/pairing, HeyCyan recording and BuddyBot physical safety acceptance remain pending. Next: Intelligence and Voice, with verified provider/model readiness and explicit listening/output tests.

## Batch E delivery

Changed IntelligenceScreen/Guidance, ProviderControlScreens, ModelPresentation, ModelBrowserScreen/ViewModel, OnDeviceModelsScreen, ImageModelsScreen, IntelligenceUsageScreen, VoiceSettingsScreen, SpeechOutputSettings, SpeechLabScreen, ProviderDiagnosticsPanel, speech references, reviewed translations/resources and tests. Intelligence presents actual configured/connection/model/reply steps with explicit calls to the existing control owner. Provider-level reply proof is clearly qualified; it never establishes every model or future request. Provider forms preserve nonsecret drafts while secrets remain ephemeral, suppress duplicate saves, require saved changes before testing and confirm credential removal. Protocol metadata starts collapsed. Browser location/capability labels are readable, filters survive same-route reentry and ineligible choices are disabled. Image model lists load only explicitly and have query recovery. Usage scrolls and formats real counters.

On-device browsing controls and models share one scrolling page; filters/disclosures survive recreation, clear-filter recovery exists and unknown download totals no longer imply zero percent. Existing acquisition, validation, loading, failures, tests and runtime ownership remain intact. Role measurements start collapsed. Listening remains separate from synthesis, speech language/model controls disable during testing/downloads, and the visible listening action leads to Finish capture and Cancel. Download bytes are measured and verification is indeterminate. Capture tuning is optional disclosure. English/Russian/Latvian reference phrases remain original test inputs. Output tests preserve Android default versus phone versus exact accessory identity, visible Stop, observed output and explicit heard confirmation. Provider selection/import are disabled during playback; voice pack failures use plain recovery. Speech Lab preserves nonsecret choices but keeps audio ephemeral. Local Audio/Vision Lab were audited and retain explicit bounded tests, source privacy, cancellation and qualified results.

Attention is static on the next valid provider step, first image catalogue load, listening/finish and available playback; no check, generation, audio capture, download or external tool runs from rendering. Four new guidance tests cover 320 dp/dark/2x explicit setup, failed prerequisite ordering, duplicate check suppression, and provider-proof limits. Existing role browser test now verifies same-route filter retention; existing provider/selection/LiteRT/speech/routing tests passed. Exact gate: `:app:compileDebugKotlin :app:testDebugUnitTest --no-parallel --max-workers=1 --no-daemon -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m"`, BUILD SUCCESSFUL in 7m 48s; 638 tests, zero failures/errors/skips. Localization --check: 2083 keys; diff --check passed. Log `.artifacts/ux-batch-e.log` retained locally.

Live provider billing/authentication/generation, downloads/local inference, real speech quality and audible accessory output remain unverified. Some advanced diagnostics, authentication option names and large-text button groups need the final consolidation pass. Next: Settings/setup/data/tools, with safe backup transitions, optional permissions, recoverable automation drafts and explicit integration consent.
