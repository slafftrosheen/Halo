# ONEBOX-CANVAS-TOOLS-004 — 2026-10-11

## Goal and upstream references

Batch 004 continues OneBox-inspired Canvas and Notebook work without copying source or assets. Relevant upstream patterns: OneBox `feature/collage-maker` (editable collage arrangements), `feature/image-stitch` (offline composition), and `feature/pdf-tools` (range-based PDF actions). All changes remain within Halo's existing Android host and Notebook storage ownership.

## Added: choose individual image attachments

- Select one to six Canvas cards containing Notebook image attachments or imported image files. Tap **Combine images**.
- A dedicated **Choose images** dialog lists all eligible image attachments of each selected Notebook note, rather than silently taking the first image. A note can supply multiple distinct images, even when the user selects only one Canvas note card.
- Every option shows a local thumbnail, filename/title, caption or source kind, and an accessible full-row checkbox. The list is lazy, with thumbnail decode bounded to a 128-pixel edge. Source lists cap at 24 attachments per note.
- Choose exactly 2–6 image attachments/files, continue to the existing collage preview, reorder the images, and save a new managed Notebook image.
- The source list is queried on demand through the existing Notebook attachment Flow. Each selected image retains its asset ID, attachment ID, path/URI, storage owner and source card ID. The save path resolves each exact attachment through the existing DAO before *and after* image encoding.
- Different attachments from the same Notebook note are now permitted. Identical source asset IDs are rejected. Each distinct source Canvas card is linked once to the new image card; a source note with two images gets a single Canvas edge but individual attachment IDs recorded in the resulting Notebook note.
- Existing app-owned phone/glasses capture URIs, managed files, and durable external image URIs remain in their original source ownership. The app does not duplicate originals to obtain previews or rewrite originals to make a collage.

## Added: featured-image collage

- **Hero collage** joins Horizontal, Vertical and Grid layouts in the existing preview.
- First image occupies a 1024-square feature tile at left; the remaining 1–5 images form a vertical strip on the right.
- Unlike a stretched bitmap, every Hero tile uses bounded center-crop coordinates matched to its cell's aspect ratio. Output still respects the max-edge and 12 MP pixel budget, and preserves the existing PNG/JPEG, gap, and background options.
- Image order is changed using the preview's existing Move up/Move down actions. The preview now preserves chosen layout, output dimensions and format when the image list is reordered.

## Engineering impact

Changed modules:
- `CanvasEditorViewModel`: on-demand asset candidates; multiple attachments per source note; authoritative revalidation and unique per-node Canvas edges.
- `CanvasEditorScreen`: image-picker flow, including a single note card containing multiple images.
- `CanvasStitchSourcePicker`: lazy list, thumbnails and full-row accessible selection.
- `CanvasStitchSource`: optional thumbnail-specific 128-pixel decode budget.
- `CanvasStitchLayout`, `CanvasStitchCrop`, `CanvasImageStitcher`: Hero arrangement and center-cropped rendering.
- `CanvasImageStitchDialog`: Hero controls and stable configuration while reordering.
- New EN/RU/LV strings in `ui.tsv`, the generated catalogue and localized resources.
- `CanvasStitchHeroTest`: 4 authored JVM tests for geometry, crop bounds and output limits.
- `CanvasNotebookStitchTest`: 1 additional authored Robolectric case composing two distinct images from a single Notebook note.

No new Room schema, model, backend API, Android permission, or GitHub Actions.

## Boundaries, verification and follow-ups

These source changes were committed and the GitHub branch/head was checked. **No Gradle compilation, JVM/Robolectric test execution, APK deployment or physical Android acceptance has been run in this session.** Treat the changes as an implementation candidate until these gates pass.

Recommended device checks:
1. One note with two or more PNG/JPEG attachments: choose two from that **single** Canvas card, confirm image source IDs, compose and restart. Also mix an imported image with a managed generated image and an APP_OWNED phone/glasses capture.
2. A note with >24 images: verify the documented 24-per-note candidate cap and responsive scrolling/thumbnail behavior. Revoke an external URI's persisted permission and confirm it cannot silently enter the composition.
3. Change/delete/trash an attachment or its parent note between picker, preview and Save; verify explicit failure and unchanged source files.
4. HERO layout with 2 and 6 extreme aspect ratio sources: first image is large, right strip ordered and center-cropped without distortion; verify transparency, JPEG white matte, and output bounds.
5. Reorder repeatedly after setting gap, background, image format and max-edge; confirm none of those settings resets and exported composition matches preview.
6. Verify source relationships in Notebook and Canvas, rotate/background during preview, user cancellation, accidental double-submit prevention, and EN/RU/LV text and touch targets.
7. Run `testDebugUnitTest` plus `assembleDebug` locally and investigate any Kotlin/Compose integration issue before alpha delivery.

**Deferred:** Text-preserving PDF merge/split/reorder. Halo currently uses Android `PdfRenderer` for page images, not a proper document editing engine; making a raster PDF would discard selectable text, so it is not represented as equivalent. A PDF editor must separately address embedded fonts, annotations, encrypted docs, page box metadata, safe output and failure recovery.
