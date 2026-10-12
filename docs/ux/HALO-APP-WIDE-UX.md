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
| B | Home, Activity, Notifications | Pending | Everyday input, review, recovery and timeline workflows |
| C | Notebook, Canvas, Memory | Pending | Dedicated editing/media/context/persistence batch |
| D | Devices and discovery | Pending | Safe manual pairing and truthful connection/recording/arming states |
| E | Intelligence and Voice | Pending | Guided setup, model lifecycle, speech tests and output routing |
| F | Settings, setup, tools, integrations | Pending | Canonical owners, backup confirmations and editor validation |
| G | Consolidation and acceptance | Pending | Route checks, narrow/tablet/font/theme/keyboard/motion/device acceptance |

## Destinations

Extracted using `python scripts/audit_ux_routes.py` from actual AppShell composable declarations. Lines refer to the starting snapshot. Pending means neither a completed screen overhaul nor accepted workflow; source reconnaissance alone is insufficient. The inline speech-output route renders SpeechOutputSettings.kt. Sheets and dialogs are audited with their owner screen.

| Destination | AppShell line | Screen implementations | Audit / validation / gaps |
|---|---:|---|---|
| `guided_setup` | 271 | HaloSetupScreen | Pending |
| `home` | 284 | HomeScreen | Pending |
| `study?captureSource={captureSource}` | 298 | StudyHomeScreen | Pending |
| `activity?runId={runId}` | 311 | ActivityScreen | Pending |
| `notebook` | 331 | NotebookScreen | Pending |
| `notebook/note/{noteId}` | 351 | NoteEditorScreen | Pending |
| `notebook/study/{sessionId}?review={review}` | 382 | StudyScreen | Pending |
| `notebook/canvas/{canvasId}?nodeId={nodeId}` | 414 | CanvasEditorScreen | Pending |
| `notebook/file/{objectId}` | 461 | WorkspaceObjectDetailScreen | Pending |
| `notebook/memory?section={section}&noteId={noteId}` | 476 | MemoryHubScreen | Pending |
| `notebook/memory/{objectId}` | 505 | WorkspaceObjectDetailScreen | Pending |
| `devices` | 525 | DevicesScreen | Pending |
| `device/{deviceId}` | 533 | BuddyBotControlScreen, HeyCyanControlScreen | Pending |
| `integrations` | 546 | IntegrationsScreen | Pending |
| `integrations/mcp/{serverId}` | 552 | McpServerScreen | Pending |
| `skills` | 558 | SkillsScreen | Pending |
| `runtime_status` | 602 | RuntimeScreen | Pending |
| `system_check` | 611 | SystemCheckScreen | Pending |
| `automation_sandbox` | 647 | AutomationSandboxScreen | Pending |
| `phone_control_sandbox` | 650 | PhoneControlSandboxScreen | Pending |
| `notebook/conversations` | 654 | ConversationsListScreen | Pending |
| `notebook/conversation/{conversationId}` | 662 | ConversationDetailScreen | Pending |
| `settings` | 689 | SettingsScreen | Pending |
| `automation?routineId={routineId}` | 695 | AutomationScreen | Pending |
| `automation_review/{routineId}/{suggestionId}` | 700 | AutomationScreen | Pending |
| `memory_learning` | 709 | LearningSettingsScreen | Pending |
| `notifications` | 712 | NotificationInboxScreen | Pending |
| `notifications/{notificationKey}` | 718 | NotificationDetailScreen | Pending |
| `notification_settings` | 726 | NotificationSettingsScreen | Pending |
| `ai_settings` | 730 | IntelligenceScreen | Pending |
| `local_audio_lab` | 744 | LocalAudioLabScreen | Pending |
| `vision_lab` | 750 | VisionLabScreen | Pending |
| `provider_setup` | 756 | ProviderCenterScreen | Pending |
| `settings/intelligence/providers` | 760 | ProviderCenterScreen | Pending |
| `settings/intelligence/providers/{providerId}` | 764 | ProviderDetailScreen | Pending |
| `settings/intelligence/images/{providerId}` | 774 | ImageModelsScreen | Pending |
| `model_browser` | 779 | ModelBrowserScreen | Pending |
| `settings/intelligence/models?providerId={providerId}&role={role}` | 783 | ModelBrowserScreen | Pending |
| `settings/intelligence/usage` | 791 | IntelligenceUsageScreen | Pending |
| `settings/intelligence/roles` | 795 | ModelRolesScreen | Pending |
| `settings/intelligence/on-device` | 801 | OnDeviceModelsScreen | Pending |
| `settings/intelligence/context` | 804 | ContextPrivacyScreen | Pending |
| `settings/voice/input` | 808 | SpeechRecognitionSettingsScreen | Pending |
| `settings/voice/speech-lab` | 812 | SpeechLabScreen | Pending |
| `settings/voice/output` | 816 | Inline content | Pending |
| `settings/intelligence/policy` | 822 | IntelligencePolicyScreen | Pending |
| `settings/voice/elevenlabs` | 826 | ElevenLabsSettingsScreen | Pending |
| `android_assistant` | 833 | AndroidAssistantScreen | Pending |
| `voice_settings` | 843 | VoiceSettingsScreen | Pending |
| `permissions` | 852 | PermissionsScreen | Pending |
| `storage_backup` | 860 | StorageBackupScreen | Pending |
| `discovery` | 863 | PairingScreen | Pending |

52 destinations extracted from AppShell. Aliases remain listed to preserve deep-link contracts.

## Batch A delivery

- Files: shared `ui/design/HaloFocus.kt` and `HaloFeedback.kt`; StudyHaloFx.kt delegates to shared focus and StudyScreen.kt imports LaunchedEffect. Reviewed ui.tsv plus generated UiCatalogue/EN/RU/LV XML. Generator now supports read-only --check; Python regression tests protect escaping and drift. Route audit script and this tracker. Compose guidance tests, pinned-composer interaction fixture, plain Robolectric Application default and explicit IO WorkManager initialization for graph integration fixtures.
- User-visible change: existing Study focus is retained, disabled actions stay unaccented, motion stops unless the destination is resumed and Android allows animation. Other components are ready for the following batches; their existence does not imply those screens have been migrated.
- Tests added: five shared guidance tests (one real click target; disabled follow-up tap; inert empty state until chosen; unknown progress; foreground/reduced-motion/motion opt-in and invalid measurements). Two generator tests. Updated Home interaction verifies editable suggestions with a visible Send control in a scrollable/pinned fixture.
- Exact final Android gate: `./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-parallel --max-workers=1 --no-daemon -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m"` — BUILD SUCCESSFUL in 3m 42s, 621 tests, zero failures/errors/skips. `python scripts/generate_ui_languages.py --check`, `python scripts/test_generate_ui_languages.py`, and `git diff --check` passed.
- Earlier failed gates are retained in local logs rather than omitted from the record. No APK/device acceptance is claimed. ADB lists a 23124RA7EO phone; screenshot review has not been performed for this batch.
- Next: Batch B — make the everyday assistant and activity/reply review paths use the shared attention and recovery vocabulary before expanding to editing and hardware workflows.
- Main commits use [skip ci] because the repository already has a push-triggered Android workflow and this task requires local validation without GitHub Actions. No workflow is changed.
