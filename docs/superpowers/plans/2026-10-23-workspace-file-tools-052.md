# Workspace file toolkit #52

Base main 92370f6cd173affe1b9800fdc3b97a9e05559589, pushed/fetched 0/0 clean after #51. User asks for less building and more coding: complete production and regression sources before any #52 build. Phone remains available; #51 installed APK stays until the complete next gate.

## Existing owners and design

Canonical NotebookDocumentRepository commits own TXT/Markdown/CSV editable content as a single bounded CODE block with a closed file-format language marker. This preserves literal Markdown/CSV bytes as editable text, avoiding the rich block codec's lossy-conversion cases. Existing Note editor remains the general text editor; bounded CSV cell actions are adapters onto the same block/revision. Managed exports are new Workspace files with exact note revision/block/source provenance. Imported originals and external permissions remain unchanged; source bytes are pinned through WorkspaceFileRepository before editing. No new document store or Room schema.

Reuse CanvasImageRecipe/CanvasImageTools for transforms and conversion, CanvasStitchSource/CanvasImageStitcher for 2–6 source collage, CanvasPdfPageTools for viewing and explicitly raster page-image extraction, CanvasFileMarkupCodec plus portable Ink for source-linked annotations. Annotation documents are canonical Notebook documents linked to exact PDF versions, not modified PDFs. A closed catalog marks deterministic text/table/image tools Ready, source-linked PDF image operations Limited, and native PDF/Office roundtrips In development. No external editing library, backend or copied upstream architecture.

## Full source batch

- [x] Bounded strict UTF-8/raw text formats and CSV parser/encoder/cell/row/column commands; preserve unknown/unsupported originals and reject malformed input explicitly.
- [x] Canonical note-backed text create/import/read/edit/reopen/CSV/managed export; revision/source checks and exact links; existing Note editor/Study/Canvas attachment paths.
- [x] Existing managed-file owner admits closed text/image formats safely; serial image transforms/batch/collage and bounded source revalidation; durable per-item batch receipts through existing Notebook/Room owner.
- [x] Source-linked PDF annotation note/view/edit and explicitly labeled page PNG exports; preserve vector originals and disclose native editing limits.
- [x] Responsive File tools panel and exact-source navigation from Home Tools/Workspace/file details; CSV controls, previews, errors/cancellation/output/reopen/attach, EN/LV/RU and capability states.
- [x] Shared typed assistant actions for every shipped operation, descriptors/approval/replay policy, source categories and runtime Workspace skill; no UI-only capability.
- [x] Regression sources for exact text/CSV/limits/UTF-8, source links/conflicts/reopen/exports/batch cancellation/receipt ownership/PDF safety, catalog/assistant parity and localized UI, plus isolated real-device workflows. Read-only review and fixes.
- [ ] One integrated gate after source freeze; focused real phone evidence, exact APK identity, push main and issue/release receipts. No Actions.

## Research and qualification

ONEBOX-CANVAS-TOOLS-001..004 inspected as bounded algorithm/UI patterns. Existing Halo crop, composition, page decode and Ink owners cover the first useful set; no upstream code/assets/dependencies copied. Genuine format-preserving PDF merge/split/reorder and DOCX/XLSX remain future capabilities, not raster substitutes. The source/output limits and manual/provider/hardware/device evidence stay distinct. #51 graphics tools remain under Studio; #53 continuity and #54 action parity apply from launch.

Source freeze: production and focused regression sources completed before any #52 Gradle invocation. Review fixed the Ink scope parameter and opaque raw-note backup preservation. Static EN/LV/RU parity: 93 keys, all 91 referenced keys resolve. Fresh origin/main remains 92370f6cd173affe1b9800fdc3b97a9e05559589, 0/0 divergence. Device f01fe673 is available. Validation and phone evidence are pending.

Gate result: both APKs assembled; one production Home Tools icon mapping fixed before final app assembly. Later fixture-only fixes: PDF writer explicit closing, existing ModeToggle callback, isolated desktop Application and active synthetic media source. Final desktop36/8classes PASS1m59, runtime6/3classes PASS1m6. Three localized renders inspected. No further production rebuild. App287274279bytes SHAa0718cac91c98301842ef3696aeaf234dd7ceb673e0cb470bbd862a123e69e02. Phone disappeared before deployment; native tests compiled but NOT_RUN. #52 stays open pending native acceptance; code publication may proceed and backlog continues under phone-later instruction.
