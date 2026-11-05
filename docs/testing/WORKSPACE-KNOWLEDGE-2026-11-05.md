# Workspace, Organizer and reference libraries — validation record

Status: implementation is undergoing actual-failure correction. Gate 03 compiled production and tests successfully, then completed 1,090 app cases across 238 suites: 1,056 passed, 34 failed, zero errors/skips. Shared strict-schema integration gaps account for many failures; compact navigation and stale Notes observations also need correction. The next full coding batch addresses those failures before another single-worker build/test run. No new APK has been installed. Earlier issue #63–65 evidence does not validate this redesign.

Approved specification: [workspace knowledge design](../superpowers/specs/2026-11-05-workspace-knowledge-design.md). User additionally requested source images for news and materials. No paid image generation is authorized.

## Features and shared owners

| Feature | Canonical UI/assistant owner | Registered operations |
|---|---|---|
| Organizer, Sources and complete readback | Existing Organizer/Collector/Brief repositories | Existing Everyday and Organizer-operation contracts v2; snapshot-pinned JSON/text views and five-row query pages |
| Reference libraries | ReferenceLibraryRepository; CollectorSource owns selection, mute, approval and version | halo_library_catalogue/query/get/import_review/import_action/download |
| Full Wikipedia articles | ResearchArticleService; CollectorCoordinator and public governed transport | halo_research_article, existing source_get/action |
| Rich-note copy | WorkspaceTextDocuments and NotebookDocumentRepository | halo_text_rich_copy; original literal file remains unchanged |
| Source images | MaterialImageRepository; CollectorSource and exact article/library generation | halo_material_image_query/fetch; publisher metadata is not visual recognition |
| Menus and insertion | Existing live ShellPageProjection and note commands | Presentation changes grant no authority; native Insert/Style/Undo use the existing document owner |

Library file selection remains a human Android document-picker operation. Tools cannot supply arbitrary file paths or URLs. Import confirmation requires an observed review token. Downloads and image saving require adult Power and external-effect approval; import/search use local data. Uncertain writes never replay. Local readers render owned image files only and make no network requests. Images retain independent source/rights references and do not inherit an article's text license. MedlinePlus licensed imagery is excluded.

Imports preserve the previous active generation until atomic publication. They use one worker, bounded compressed/expanded/entry/storage budgets, durable checkpoints and explicit resume. Source/family/mode/restore changes stop disclosure or publication. XML entities, archive traversal, unbounded expansion and arbitrary executable rendering are unsupported.

## Authored regression coverage, not yet run

Unicode discovery/domain coverage; menu roles/current-page leases/large text; rich copy and direct heading insertion/Enter; real Registry escaped-text reconstruction and stale snapshots; real Room Unicode FTS, failed replacement and interrupted/cancelled/resumed imports; migration preservation/FTS cascade; selected Wikipedia sections/revisions/tables/image metadata; local-zero-network image reads and source revocation during image transfer. Registered-feature review is asserted through `ToolRegistry.operabilityReport` in the feature fixtures; execution output is pending.

## Separate acceptance gates

| Workflow | Typed source fixture | Native phone | Actual spoken intent | Permitted remote provider | Durable phone readback |
|---|---|---|---|---|---|
| Create heading/paragraph and preserve literal original | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Import/search/read exact local library evidence | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Retrieve selected full article and save its images | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

Build/test commands, counts, source identity, APK hash/signature, install verification, inspected phone renders and restored phone settings will be recorded after the complete coding batch. No issue closure, merge or provider acceptance is claimed.

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
