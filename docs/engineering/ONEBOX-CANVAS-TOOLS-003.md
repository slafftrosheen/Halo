# ONEBOX-CANVAS-TOOLS-003 — 2026-10-11

References (ideas only, no copied code):
- OneBox feature/image-stitch AndroidImageCombiner and feature/collage-maker CollageMakerComponent: adjustable arrangements and explicit source reordering.
- OneBox feature/pdf-tools PagesSelectionParser: familiar comma/range page selection. Halo's stricter parser rejects duplicate, invalid, out-of-bound, or >6-page selections instead of silently expanding arbitrary input.
- The existing Halo NotebookAssetRepository, CanvasEditorViewModel, WorkspaceFileRepository, and Android PdfRenderer remain the owners of bytes, state, source identity, and rendering.

## What changed

### Compose existing Notebook images

Canvas **Select cards -> Combine images** now accepts 2–6 selected cards containing either:
- Imported Workspace image files (existing Batch 002 path); or
- The first IMAGE attachment of an existing Notebook note, including generated-image notes and phone/glasses capture notes.

Inputs are represented by sealed CanvasStitchSource WorkspaceImage/NoteImage variants, not by fabricating WorkspaceFile records. A managed image is read from its actual stored file; app-owned capture content URIs and persisted external document URIs are accessed through Android ContentResolver.

Before and after composition, the save coordinator re-resolves each imported file by URI/version or Notebook image by note lifecycle, attachment ID, asset ID, storage location, declared byte count, type and MIME. Missing/replaced sources fail rather than silently substituting a different image. No edits touch the source bytes; new image data is saved through the established managed Notebook attachment transaction and linked to each source Canvas node. Distinct source owners are currently required, so one Notebook note can contribute only one selected image.

The preview now permits moving each image up or down without rearranging Canvas board cards. This order is used in both the preview and final output.

### Extract 1–6 PDF pages together

**Card actions -> Extract PDF pages** accepts page selections such as `1,3-5` or `5,2-3`, preserving explicit input order. Invalid pages, repeated page numbers, reversed ranges, more than six pages, or malformed input are rejected by CanvasPdfPageSelection.

The chosen first page is shown as preview. Saving renders all selected pages in order, one at a time, to bounded PNG attachments in a single new Notebook note; this note is linked to the original PDF card. The source PDF is read-only, never rewritten. Each attached image has a page-number caption. This is *raster page extraction*, not PDF editing, OCR, page deletion, merge/split, or text-layer preservation.

When a later page fails after earlier pages were committed, the partial Notebook item is left recoverable in Inbox rather than silently discarding saved pages. The interface reports failure, not success. Fully successful operations create one new Canvas image-card link. Android's PdfRenderer may reject encrypted/password-protected documents.

### UX and translations

The user-facing PDF range labels, statuses, pluralized entry point and image-order instructions are available in EN/RU/LV; existing Move up/Move down translations are reused. No extra service, model, API, database schema, permission, or GitHub Actions.

## Tests and acceptance

New test cases authored (NOT executed):
- CanvasPdfPageSelectionTest: valid ranges, ordering, bounds, duplicates, invalid values, and >6 pages (3 cases).
- CanvasNotebookStitchTest: managed-note image composition with pixel assertions, preservation of original files, and rejection of duplicate assets (2 cases).

Source-only verification does not prove that an APK compiles or that every Android content URI remains accessible. Full Gradle build, unit/Robolectric tests and phone/glasses device acceptance remain outstanding.

Run targeted unit tests and assembleDebug locally, then on device validate:
1. Mix an imported image, a generated Notebook image, and phone/HeyCyan capture; preview, reorder, save, reopen and verify source Canvas links and untouched originals.
2. Revoke URI permission, delete one attachment, move source note to Trash, or modify an imported file after opening preview. Observe clear refusal and no false success.
3. Compose 2–6 images with transparent/white backgrounds, portrait/landscape dimensions, file-pool limits, and PNG/JPEG.
4. Select PDF pages `1,3-5`, `5,2-3`, duplicates, invalid/out-of-bounds, all six allowed, and seven rejected. Confirm final note contains exactly the selected page images in order and source link/page annotations.
5. Force failure after at least one page export, restart, locate partial note in Inbox, confirm no PDF mutation and no inaccessible attachments.
6. Confirm EN/RU/LV labels, tablet/phone layout, preview cancellation, and offline behavior.

Deferred for future batches: selecting multiple attachments from a single note, PDF-to-PDF split/merge/reorder with text layer preservation, draggable collage presets, ZIP batch export, and managed multi-image ingestion from a folder.
