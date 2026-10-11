# ONEBOX-CANVAS-TOOLS-002 — 2026-10-11

Source mined: https://github.com/wangzhishou/OneBox.
Specific patterns studied: feature/image-stitch (AndroidImageCombiner, StitchMode, CombiningParams), feature/collage-maker, feature/image-stacking, and feature/pdf-tools (page extraction and rendering). No OneBox source or assets copied. The code uses Halo's existing Workspace objects, Canvas references, Notebook managed attachments and Android PDF support.

## Implemented: combine selected images

In an open Canvas, use Select cards, then select 2–6 distinct imported image-file cards. A visible **Combine images** action opens a live, entirely local preview. Available:
- horizontal / vertical / grid (2 columns for 2–4 items, 3 columns for 5–6);
- spacing 0/8/24 pixels, white or transparent background;
- maximum result edge 1024/2048/4096 pixels, and PNG/JPEG output.

The source order is deterministic: Canvas Y then X. The output is bounded to a maximum 12-megapixel bitmap, 4096-pixel edge, and 20 MB encoded bytes. Each source bitmap is decoded one at a time with the existing bounded Canvas image decode path and promptly recycled. JPEG always has white backing; when JPEG is selected, transparent is unavailable.

Confirming creates a fresh Notebook note with a managed image attachment; the note records all source Workspace object IDs and every source card is connected to the resulting Canvas image card. Originals, their annotations, and other selected files are not rewritten. A missing source, changed source version/URI, removed card, invalid image or lost file permission prevents saving. If linking back to Canvas fails after the managed item has saved, the resulting Notebook item can remain in Inbox; users are told to check there before retrying.

Current limitation: multi-select currently targets imported **image-file cards** only. Attached image assets inside pre-existing notes and generated-image cards can be viewed but cannot yet be selected as inputs to composition. Repeated cards referring to the same physical Workspace file are excluded, to avoid accidental duplicates.

## Implemented: extract one PDF page to Notebook

On an imported PDF card, **Card actions → Extract PDF page** opens a preview and a 1-based page selector. Android PdfRenderer reads only the chosen page; on Save, that page is rasterized to bounded PNG with a white background. The result is a new Notebook image, with a source Workspace identifier and page number in the note, and a Canvas connection to the original PDF card. It does not claim to remove/modify/merge PDF pages or to produce a new PDF. The original PDF is unchanged.

Renderer output is limited to 2048-pixel edge and under 12 MP; preview is 640 pixels. Encryption/passwords or provider file descriptors unsupported by Android PdfRenderer are reported as unavailable; no attempt is made to bypass restrictions. This is image extraction, so text inside output is rasterized, not a searchable or editable PDF text layer. Halo's existing Study PDF visual input and OCR capabilities remain separate.

## Architecture and ownership

- app/data/workspace/CanvasStitchLayout.kt — pure Kotlin bounded layout / deterministic placements.
- app/data/workspace/CanvasImageStitcher.kt — Android bitmap composition, single-source resident decode.
- app/data/workspace/CanvasPdfPageGeometry.kt — pure Kotlin bounded PDF page dimensions.
- app/data/workspace/CanvasPdfPageTools.kt — Android PdfRenderer single-page host output.
- app/data/workspace/NotebookAssetRepository.kt — existing managed image storage and image-tool gateways.
- app/ui/screens/notebook/CanvasImageStitchDialog.kt and CanvasPdfPageDialog.kt — user controls and preview.
- CanvasEditorViewModel / CanvasEditorScreen — source-version checks, selection, operation status, source links.
- app/src/main/localization/ui.tsv plus EN/RU/LV generated resources — 23 new translated labels.
- CanvasStitchLayoutTest (5 tests) and CanvasPdfPageGeometryTest (4 tests).

No model or cloud service invoked. No new dependencies or Android permissions. No GitHub Actions modified or triggered.

## Verification and remaining gates

Committed-source checks verified that the layout renderer, UI entry points, managed asset save, source links and translation resources exist. The 9 new JVM tests were authored but **not executed** in this GitHub connector environment. Full Gradle build, APK install and physical Android smoke tests have NOT been run; these changes are a source implementation, not release certification.

Local gate: run app testDebugUnitTest and assembleDebug (and image-related regression suites), then test:
1. Mixed PNG/JPEG, alpha and solid colors, 2/4/6 images, all 3 layouts and gap/background variants.
2. 64-MB-bounded imported image files, extreme aspect ratios, decode failure/large output and cancellation.
3. Save output, restart app, reopen note, confirm image persistence and all source-link relationships; verify source files and annotations stay byte-identical.
4. Modify source or revoke URI permission before pressing Save. Ensure no generated image claims success.
5. Multi-selection mode, changing tabs, deleting a selected node during export and interrupted database save.
6. PDFs with 1/many pages, unusually tall pages, password protection, missing URI permission, and a page number outside valid range.
7. EN/RU/LV text and all disabled/loading states; verify no network/API calls during operations.

Future OneBox candidates: collage presets/drag-to-reorder, note-asset inputs, multiple PDF pages, PDF merge/split/reorder using a proven document-preserving engine, PDF metadata/EXIF with explicit privacy affordances, and bounded batch ZIP export.
