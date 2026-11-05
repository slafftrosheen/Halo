# Workspace, Organizer and reference libraries — validation record

Status: production and regression sources in progress; this new batch has **not yet been compiled, tested or installed**. Earlier issue #63–65 test/device evidence does not validate these changes.

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
