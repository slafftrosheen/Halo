# PHONE-ASSISTANT-001 — OneBox capability harvest and Android 17 compatibility

Status: implemented, compiled, and unit verified on 2026-10-01. Physical API 26/API 37 verification is pending; no connected device or installed emulator image was available.

See [the complete 106-tool capability ledger](PHONE-ASSISTANT-001-LEDGER.md). The original inventory below is retained as the audit seed, not the final registration count.

## Request

Extract every applicable phone-control and assistant capability from the supplied OneBox source, implement missing capabilities in Halo, and prove they are connected to discovery, authorization, execution, and assistant interaction. Support current public Android APIs and commands. Fix every failure encountered during the work, including pre-existing build and test failures. Do not stop at an inventory or add tool definitions without working executors.

## Source and baseline

Use `OneBox-main (1).zip` and the existing extraction at `build/refs/OneBox-main`. Previous harvest records pin wangzhishou/OneBox commit `8948af05801114d2871a208899f30aa5f2d2566d`; verify the archive identity against `docs/engineering/NOTEBOOK-STUDY-MINING.md` before attributing changes. Preserve existing local edits, including the completed speech work.

Read `ONEBOX-ZAFIRO-MINING.md`, `REFERENCE-HARVEST.md`, `SEMANTIC-PHONE-OPERATOR.md`, and `docs/testing/AGENTIC-PHONE-ACCEPTANCE.md` first. Avoid duplicating earlier harvests.

Observed baseline: app compileSdk 37 / targetSdk 36 / minSdk 26; host-android compileSdk 36. Installed SDK platforms include 37.0. Halo already provides 17 phone-action tools and nine semantic screen tools, native assistant service/session, recognition service, assist-context extraction, and screenshot handling. Existing global screen actions expose back and home.

OneBox `discover_apps` discovers its internal navigation pages. `navigate_app_screen` navigates those pages. Theme, night mode, font scale, and start entry are app preferences until source evidence proves otherwise. Do not advertise these as unrestricted OS controls. OneBox visual touch execution is not a substitute for Halo's cross-app semantic safety and verification pipeline. No VoiceInteractionService, VoiceInteractionSession, or ROLE_ASSISTANT references were found in the supplied OneBox source; native assistant enhancements therefore require Android platform evidence rather than invented source attribution.

## Required deliverables

1. Commit-ready capability ledger covering every inventoried source tool below and relevant supporting services, skills, registries, manifest declarations, and dispatchers. Each row must record source behavior, Halo equivalent, current status, implementation decision, Android/API/permission constraints, and test evidence. Classify each as already covered, implemented in this batch, outside phone/assistant scope, or unavailable with a specific platform reason. Reconcile dynamically registered tools and bundled skills as well as filenames.
2. Implement all applicable missing capabilities. Audit internal navigation, app preference controls, installed-app discovery/launch, device/network information, speech read/stop, browser/navigation, system settings and panels, and visual task orchestration. Inspect files/pickers, clipboard, sharing, contacts, dialing, media, alarms/timers, and local memory for useful phone workflows. Keep cloud, game, finance, and image/PDF processing domains explicitly accounted for without treating them as OS control.
3. Extend native Android assistant behavior where useful and publicly supported: role eligibility/request flow, service readiness/shutdown, session show/hide/destroy, microphone cancellation, assist context/screenshots, recognition-service lifecycle, and VoiceInteractor request handling. Audit command, confirmation, option selection, completion/abort, and cancellation callbacks. Implement supported requests with bounded typed contracts; explicitly finish unsupported requests. Never route vendor command strings directly to privileged execution.
4. Audit all currently public accessibility global actions, node actions, gesture and screenshot APIs. Offer supported actions based on API level and `getSystemActions()` where available, not a static promise. Preserve snapshot freshness, exact target tokens, package policy, durable authorization, transient sensitive text, bounded search, and outcome verification. Return dispatched/unconfirmed when an effect cannot be verified; no fabricated success. Handle unavailable services and user takeover clearly.
5. Audit the latest stable Android behavior using official documentation at implementation time. Android 17/API 37 is the current reviewed baseline. Align compile SDKs where new APIs are consumed; adopt targetSdk 37 after addressing target-specific changes and testing dependencies. Preserve API 26 compatibility with guards and resolvable intent fallbacks. Verify public API availability rather than using hidden APIs, shell/root assumptions, or signature-only permissions.
6. Exercise the complete path from a spoken/native-assistant request through skill discovery, policy authorization, dispatch, receipt, and assistant response. Both local and cloud providers must use the same capability contracts and execution boundary.

## Wiring requirements

For every new tool verify: executor implementation; HostToolRegistry and ManualDependencyGraph registration; canonical tool metadata and parameter validation; per-tool risk, availability, required permissions and durable approval rules; skill asset inclusion; discovery/on-device allowlist and provider serialization; preflight; journal/idempotency/resource ownership where applicable; typed result envelope and accurate action truth; same-provider continuation; user-visible failure and recovery.

App-local preferences require a real settings repository and an observable effect. Native tools take precedence over screen automation. Screen orchestration must use existing observe → target → preflight → authorize → dispatch → settle → verify behavior. Do not transplant OneBox raw-coordinate execution as a parallel authorization bypass. No new executable capability may be declared usable when its Android service, role, permission, or handler is absent.

## Android 17 audit

Review assistant audio stream and assistant conversation mode; audio focus/capture and foreground-service restrictions; the public contact picker and contacts-provider restrictions; accessibility text-change reporting; local-network permissions where network tools use LAN access; URI grant behavior for shares/pickers; background activity launches; sensitive notification/message access; and advanced-protection restrictions. Determine applicability from actual Halo code. APIs reserved for privileged assistants must be marked unavailable to ordinary installations unless documented eligibility is met.

Official references (recheck during execution):
- https://developer.android.com/about/versions/17/summary
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/reference/android/service/voice/VoiceInteractionService
- https://developer.android.com/reference/android/service/voice/VoiceInteractionSession
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- https://developer.android.com/reference/android/app/VoiceInteractor

## Acceptance

- No applicable source capability lacks a ledger disposition; no registered tool lacks metadata, a skill/discovery route, authorization, an executor, and useful failure handling.
- Meaningful tests cover discovery-to-execution wiring, denied permission/role, unsupported API/action, unresolved intent, stale UI observation, cancellation, sensitive text restoration, and dispatch versus verified effects. Verify every added assistant request completes or cancels exactly once and shares the existing interaction runtime.
- Run affected host/runtime/app tests and debug APK assembly. Fix every encountered failure and rerun the relevant checks. Do not weaken assertions or suppress errors to make checks pass.
- API 26 and API 37 emulator/device smoke checks where available: assistant selection/invocation, speech start/stop, context/screenshot availability, representative native phone commands, semantic screen actions, and denied/unavailable conditions. If hardware/emulators are unavailable, retain explicit pending evidence; compilation does not establish device behavior.
- Deliver capability ledger, SDK/behavior audit, test results, APK path, and remaining platform restrictions. Update CURRENT-STATE.md and acceptance documentation with verified facts.

## Source tool inventory

This filename/name inventory is an audit seed, not proof that each tool controls the phone or that all registrations are exhausted. Inspect source behavior and registration before mapping it.

| Source file under builtin | Tool name |
|---|---|
| `AddBookkeepingRecordTool.kt` | `add_bookkeeping_record` |
| `AddLocalAiEngineTool.kt` | `add_local_ai_engine` |
| `AddLocalAiModelTool.kt` | `add_local_ai_model` |
| `AddWatermarkTool.kt` | `add_watermark` |
| `AskUserTool.kt` | `ask_user` |
| `Base64Tool.kt` | `base64_tool` |
| `BrowseFilesTool.kt` | `browse_files` |
| `browser/BrowserExecuteJsTool.kt` | `browser_execute_js` |
| `browser/BrowserNavigateTool.kt` | `browser_navigate` |
| `browser/BrowserReadPageTool.kt` | `browser_read_page` |
| `browser/BrowserScreenshotTool.kt` | `browser_screenshot` |
| `ChecksumTool.kt` | `checksum_tool` |
| `cloud/BrowseCloudFilesTool.kt` | `browse_cloud_files` |
| `cloud/DiscoverCloudConnectionsTool.kt` | `discover_cloud_connections` |
| `cloud/ManageCloudConnectionsTool.kt` | `manage_cloud_connections` |
| `cloud/ManageCloudFilesTool.kt` | `manage_cloud_files` |
| `cloud/ReadCloudFileTool.kt` | `read_cloud_file` |
| `cloud/SearchCloudFilesTool.kt` | `search_cloud_files` |
| `cloud/UploadToCloudTool.kt` | `upload_to_cloud` |
| `CompressImageTool.kt` | `compress_image` |
| `ConvertImageFormatTool.kt` | `convert_image_format` |
| `CreateNoteTool.kt` | `create_note` |
| `CreatePromptTool.kt` | `create_prompt` |
| `DiscoverAppsTool.kt` | `discover_apps` |
| `DiscoverToolsTool.kt` | `discover_tools` |
| `EditExifTool.kt` | `edit_exif` |
| `EditFileTool.kt` | `edit_file` |
| `FontScaleSettingTool.kt` | `font_scale_setting` |
| `GenerateImageTool.kt` | `generate_image` |
| `GetCurrentTimeTool.kt` | `get_current_time` |
| `GetDeviceInfoTool.kt` | `get_device_info` |
| `GetWeatherTool.kt` | `get_weather` |
| `GlobFilesTool.kt` | `glob_files` |
| `GrepFilesTool.kt` | `grep_files` |
| `LimitResizeTool.kt` | `limit_resize_image` |
| `ManageBookmarkTool.kt` | `manage_bookmark` |
| `ManageChessTool.kt` | `manage_chess` |
| `ManageFilesTool.kt` | `manage_files` |
| `ManageGomokuTool.kt` | `manage_gomoku` |
| `ManageTodoTool.kt` | `manage_todo` |
| `ManageXiangqiTool.kt` | `manage_xiangqi` |
| `MemoryGetTool.kt` | `memory_get` |
| `MemoryWriteTool.kt` | `memory_write` |
| `NavigateAppScreenTool.kt` | `navigate_app_screen` |
| `network/CheckUrlTool.kt` | `check_url` |
| `network/DownloadFileTool.kt` | `download_file` |
| `network/FetchWebpageTool.kt` | `fetch_webpage` |
| `network/GetNetworkInfoTool.kt` | `get_network_info` |
| `network/SearchWebTool.kt` | `search_web` |
| `network/SendHttpRequestTool.kt` | `send_http_request` |
| `NightModeSettingTool.kt` | `night_mode_setting` |
| `pdf/ConvertImagesToPdfTool.kt` | `convert_images_to_pdf` |
| `pdf/ConvertPdfToImagesTool.kt` | `convert_pdf_to_images` |
| `pdf/OpenPdfPreviewTool.kt` | `open_pdf_preview` |
| `PickFilesTool.kt` | `pick_files` |
| `PickFolderTool.kt` | `pick_folder` |
| `ProcessFileTool.kt` | `process_file` |
| `QueryBookkeepingRecordsTool.kt` | `query_bookkeeping_records` |
| `QueryBookkeepingSummaryTool.kt` | `query_bookkeeping_summary` |
| `ReadBarcodeFromImageTool.kt` | `read_barcode_from_image` |
| `ReadMultipleFilesTool.kt` | `read_multiple_files` |
| `ReadTextAloudTool.kt` | `read_text_aloud` |
| `ShowEffectTool.kt` | `show_effect` |
| `StartEntrySettingTool.kt` | `start_entry_setting` |
| `StatFileTool.kt` | `stat_file` |
| `StopReadingTool.kt` | `stop_reading` |
| `SwitchAiModelTool.kt` | `switch_ai_model` |
| `ThemeSettingTool.kt` | `theme_setting` |
| `UseSkillTool.kt` | `use_skill` |
| `ViewNoteTool.kt` | `view_note` |
| `visual/ActOnUiTool.kt` | `act_on_ui` |
| `visual/AutomateUiTaskTool.kt` | `automate_ui_task` |
| `visual/ScreenshotUiTool.kt` | `screenshot_ui` |
| `WebpTool.kt` | `webp_tool` |
| `WorkspaceRootsTool.kt` | `workspace_roots` |
