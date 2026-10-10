# ONEBOX-CANVAS-TOOLS-001 — 2026-10-10

Reference: wangzhishou/OneBox. Inspected image-cutting, format-conversion, image-stitch, palette-tools and existing modules. Ideas adapted; no outside source or assets copied.

## Implemented

From a selected Canvas image file, choose Card actions > Image tools for offline preview, rotation left/right, horizontal mirror, center-square crop, resize to maximum edge 512/1024/2048/4096, and PNG/JPEG output. A derived image is a new Notebook note with a managed image attachment, a Canvas card and a source connection. The imported original and existing annotations are unchanged. JPEG uses a white background where pixels are transparent.

Card actions > Extract color palette creates a source-linked Notebook note containing up to six dominant HEX colors sampled locally. Transparent pixels are excluded. No AI or network is invoked for either operation. New labels have EN/RU/LV translations.

### Architecture

CanvasImageRecipe owns portable input validation and dimensions. CanvasImageTools handles bounded Android bitmap decode/preview/export. CanvasColorPalette performs deterministic color binning. NotebookAssetRepository stores derived image bytes as a new managed attachment. CanvasEditorViewModel owns source-version checks and saves; CanvasEditorScreen and CanvasLocalImageToolsDialog own presentation. No new provider, permission, SDK dependency, or parallel repository.

### Limits

PNG/JPEG only; center-square crop only; max output edge 4096 and output 20 MB. Source decode is bounded to 4096 pixels per edge and 12 megapixels, preview to 640 pixels per edge. EXIF orientation and metadata are not explicitly preserved in the derived output. Imported Canvas image FILE cards are supported; attachments embedded in pre-existing Notebook notes are a future target. If the Canvas is removed during a save, a completed derived Notebook item may remain in Inbox.

### OneBox candidates deferred

Multi-image collage/stitch/stacking; PDF merge, split, page ordering; arbitrary crop; selective EXIF stripping/preservation; palette harmonies; ZIP and batch conversion. These need their own scoped UX and storage safety review. Halo's existing sketch, ink, OCR and AI image generation owners remain in place.

### Verification

10 new JVM test cases were added for image recipes and color extraction. They have NOT been executed. GitHub source updates verified, but full Gradle compilation, APK assembly and real Android acceptance are pending. No GitHub Actions were run or changed.

Device acceptance: test PNG alpha/JPEG white matte, large/portrait/wide images, rotation/crop combinations, undo/close, offline behavior, restart persistence, source-version conflict, cancellation near asset commit, Canvas deletion after asset save, and EN/RU/LV strings. Verify that originals never change.
