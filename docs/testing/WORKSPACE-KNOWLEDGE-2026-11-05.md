# Workspace, Organizer and reference libraries — validation record

Status: code is pushed, the affected desktop checks passed and the APK is built and signature-verified. Gate 05 passed 34/34 cases across five suites after correcting the three remaining failures. The preceding broader Gate 04 result was 1,094 passes out of 1,097 cases on its identified source; it is not described as a fresh full-suite pass on the later commit. The phone is not detected, so update installation and native/voice/provider acceptance remain pending.

Approved specification: [workspace knowledge design](../superpowers/specs/2026-11-05-workspace-knowledge-design.md). User additionally requested source images for news and materials. No paid image generation is authorized.

## Features and shared owners

| Feature | Canonical UI/assistant owner | Registered operations |
|---|---|---|
| Organizer, Sources and complete readback | Existing Organizer/Collector/Brief repositories | Existing Everyday and Organizer-operation contracts; snapshot-pinned JSON/text views and five-row query pages |
| Reference libraries | ReferenceLibraryRepository; CollectorSource owns selection, mute, approval and version | halo_library_catalogue/query/get/import_review/import_action/download |
| Full Wikipedia articles | ResearchArticleService; CollectorCoordinator and public governed transport | halo_research_article, existing source_get/action |
| Rich-note copy | WorkspaceTextDocuments and NotebookDocumentRepository | halo_text_rich_copy; original literal file remains unchanged |
| Source images | MaterialImageRepository; CollectorSource and exact article/library generation | halo_material_image_query/fetch; publisher metadata is not visual recognition |
| Menus and insertion | Existing live ShellPageProjection and note commands | Presentation changes grant no authority; native Insert/Style/Undo use the existing document owner |

Library file selection remains a human Android document-picker operation. Tools cannot supply arbitrary file paths or URLs. Import confirmation requires an observed review token. Downloads and image saving require adult Power and external-effect approval; import/search use local data. Uncertain writes never replay. Local readers render owned image files only and make no network requests. Images retain independent source/rights references and do not inherit an article's text license. MedlinePlus licensed imagery is excluded.

Imports preserve the previous active generation until atomic publication. They use one worker, bounded compressed/expanded/entry/storage budgets, durable checkpoints and explicit resume. Source/family/mode/restore changes stop disclosure or publication. XML entities, archive traversal, unbounded expansion and arbitrary executable rendering are unsupported.

## Desktop regression coverage

Unicode discovery/domain coverage; menu roles/current-page leases/large text; rich copy and direct heading insertion/Enter; real Registry escaped-text reconstruction and stale snapshots; real Room Unicode FTS, failed replacement and interrupted/cancelled/resumed imports; migration preservation/FTS cascade; selected Wikipedia sections/revisions/tables/image metadata; local-zero-network image reads and source revocation during image transfer. Registered-feature review is asserted through `ToolRegistry.operabilityReport` in the executed fixtures. Exact broader/focused scopes and failures are recorded below.

## Separate acceptance gates

| Workflow | Typed source fixture | Native phone | Actual spoken intent | Permitted remote provider | Durable phone readback |
|---|---|---|---|---|---|
| Create heading/paragraph and preserve literal original | PASS · Gate 04 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Import/search/read exact local library evidence | PASS · Gate 05 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Retrieve selected full article and save its images | PASS · parser/cache pieces; live retrieval pending | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

Build/test counts, source identity and APK verification are recorded below. Native installation, inspected phone renders, actual spoken intent and permitted remote-provider acceptance require their own evidence. No issue closure or merge is claimed.

## Validation attempts

- Gate 01, source `cb3a737dc`: one core/worker/fork, no production task exclusions. Runtime 87 cases / 21 suites: 86 passed, one failed, zero errors/skips. The failure was the explicit reviewed-tool set assertion in `PersonalConfigurationPolicyTest`; the individual new-tool exposure/provenance assertions passed. Android tasks did not start after this failure.
- Generated catalogue check passed with 5,133 entries per language. The broader Polish check found an existing missing date-format hint. The hint was added; the subsequent Polish check passed for 6,351 Android strings, with 5,137 generated entries per language.
- Follow-up coding also handles cancelled unconfirmed previews (resume returns REVIEWING, requires confirmation), serialized download-to-preview publication and safe orphan cleanup, blank optional CSV metadata, distinct clock/Workspace/reference discovery domains, and saveable per-section Organizer scroll state. New regressions are authored; their execution is pending.
- The read-only review's six important findings were addressed in the coherent source fix batch. The FTS migration regression now reopens through Room; execution remains pending. Room's generated content-option spelling and raw option comparison were checked against [AndroidX compiler source](https://android.googlesource.com/platform/frameworks/support/+/f2e05c341382db64d127118a13451dcaa554b702/room/compiler/src/main/kotlin/androidx/room/vo/FtsOptions.kt) and [runtime source](https://android.googlesource.com/platform/frameworks/support/+/2d5197729d110347604ba154a3006241717b54c8/room/room-runtime/src/androidMain/kotlin/androidx/room/util/FtsTableInfo.android.kt).

- Gate 02, source `b3548c2aa`: Runtime 87/87 passed across 21 suites, zero errors/skips. Android resources and Room generation passed; exported schema 35 includes the quoted FTS content option. App Kotlin compilation failed on an unavailable ProcessLifecycleOwner symbol and missing Libraries/LibraryEntry branches in the typed widget codec. App tests did not run; no new APK was produced. Run ended after 35m14s, with no source/task exclusions.
- The next coherent fix batch reuses the existing foreground authority, completes typed target encoding, adds full occurrence/delivery metadata, and protects queued import/read authority across database restore. Preview fingerprints its sampled stream; publication verifies the exact parsed bytes. Parser resource ownership, bounded JSON preflight/escaped bodies and migrated article refresh validators are covered by new regressions. Library rendering filters stale results against current source/family scope. Note keyboard controls replace the full primary toolbar while the IME is visible. These regressions remain unexecuted until Gate 03.

- Gate 03, clean source `4140c8af13d8`: production Kotlin/Java, resources/Room generation and test compilation passed, without production task exclusions. Runtime reused the unchanged Gate 02 87/87 passing result. App 1,090 cases / 238 suites: 1,056 passed, 34 failed, zero errors/skips. The image fixture's invalid cleanup return type produced one initialization failure; its five intended methods did not execute.
- Failures identified missing strict host-schema `required`/numeric bounds, unsupported host metadata version, rich-copy metadata on the wrong executor path, full occurrence-time field names, compact action fallback, a delayed older Note snapshot replacing clean state, two malformed/timing fixtures, and outdated menu expectations. The complete correction batch retains strict closed schemas, current source/family guards, no-replay behavior and the supported Runtime metadata format. It adds actual image Registry calls, strict read/write-wrapper validation, Notes recreation/filter and compact primary-action coverage.
- After the complete failed test result was saved, APK conversion was deliberately stopped (exit 143 at dexBuilderDebug) to avoid spending more Pi time packaging superseded failing source. No Gate 03 APK is offered for installation. The original phone app/data remain intact.

- Gate 04, source `024fa619489d`, compiled production successfully but was interrupted during test compilation when the desktop turn stopped. No compiler failure or test result was claimed for that interruption. `gate-04-resume` reused completed production tasks on the same clean source, freshly compiled tests and completed 1,097 cases / 238 suites: 1,094 passed, three failed, zero errors/skips. All complete app JUnit XML was archived before a later focused run can replace the output directory.
- Remaining failures were import-review's nested closed union rejecting common snapshot metadata, a positive calendar fixture missing required event end times, and an old image-recovery fixture passing plain storage where a mode-profile owner is required. The correction retains both closed review/history branches and all privacy checks, supplies valid fixture times and uses the same profile-backed settings as production. Image tests use a synthetic in-process provider; no paid request is made.
- The next run targets the changed library review workflow, Everyday calendar/contract regressions, image recovery and the previously unselected family-mode menu regression, alongside the complete required production compilation/APK tasks. Unchanged passing cases retain their explicitly identified Gate 04 source evidence; a full-suite pass on a different commit is not claimed. Obsolete APK conversion was stopped after the failed full result was archived (daemon SIGTERM, launcher exit 1).

## Verified build and published source

Production source: `a873f44ca10feab2f55e8f8743e0504153fe6754`, clean generated source marker `a873f44ca10f`. Published on [`codex/issue-65-studio`](https://github.com/slafftrosheen/Halo/tree/codex/issue-65-studio). No pull request, merge or issue closure was performed.

Gate 05 finished **BUILD SUCCESSFUL in 42m10s**, one core/Gradle worker/test fork, existing 16 GiB swap, no production task exclusions. Production and test compilation passed. The focused app run passed **34/34 cases, five suites, zero failures/errors/skips**: `ReferenceLibraryToolsTest`, `EverydayToolExecutorTest`, `WorkspaceImageRecoveryTest`, `MaterialImageRepositoryTest`, and `FamilyModeMenuVisibilityTest`. This verifies import-review history and exact review snapshots, complete escaped readback and revocation, calendar paging, profile-backed synthetic credit recovery, actual registered image-cache operations, and the global family switch across page/family changes. Runtime reused the unchanged Gate 02 87/87 passing result.

The earlier broader Gate 04 run passed the compact-primary/destination fallback, rich heading/Enter durability, note-view recreation, literal-copy preservation, import/recovery/parser/migration and other related regressions. Its remaining three failures were corrected and exercised by the focused run. Full and focused JUnit evidence is retained separately; all-source full-suite execution on the later commit is not implied.

APK retained locally as `halo-workspace-knowledge-a873f44ca.apk`:

- Package: `com.aistudio.braintool.xkqzps`, version code 2, version `1.0-RC1`.
- Size: 298,521,991 bytes.
- SHA-256: `a58d3f33b349a0cce5afce0706f4d9a12c653001658cc3691565dd3d527a5d6b`.
- APK v2 signature verified. Signing certificate SHA-256: `d016e155446e2d96644e396fcb682282e97a2a816d9592875510469f1b22b737`, matching the previous phone update certificate.
- The expected source marker and reference-library/image-cache classes were verified inside the APK DEX files.

ADB detects no target phone. A reconnection request is pending. The prior installed app, credentials, model, notes, projects and widgets have not been altered by this validation. No paid generation was submitted; image recovery tests use a synthetic in-process provider.
