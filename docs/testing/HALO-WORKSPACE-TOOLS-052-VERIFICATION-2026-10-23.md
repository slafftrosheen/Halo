# Workspace file tools — issue #52

Local File tools reuses canonical Notebook documents, managed Workspace files, Canvas image/PDF renderers, Halo Ink and existing Study/Canvas destinations. TXT and Markdown retain exact literal text in a canonical CODE block; CSV cell/row/column commands update that same revision-checked document. UTF-8 exports preserve literal bytes and publish new managed files with source revision/block provenance. Existing rich-note exports explicitly project TXT/Markdown. A bounded live-session undo/redo history stops at external revision changes.

Image rotation/crop/mirror/resize and PNG/JPEG conversion reuse the current image owner. Batches process one image at a time, with durable per-item Notebook receipts: saved output and receipt update commit in one Room transaction. Partial/cancelled/interrupted receipts never replay work. Collage preserves exact source links. Source versions are checked under the file owner's copy/relink lock and again when outputs publish; previews dismiss when their selected sources change.

PDF annotations are source-linked canonical Notebook documents with portable vector Ink, recovery drafts and exact immutable PDF versions. Page extraction and annotated exports produce clearly labeled raster PNG images, retaining the original PDF. Native PDF text editing/merge/split/reorder and faithful DOCX/XLSX roundtrips remain In development. No new external editing dependency or backend was introduced.

Manual controls and eighteen shared assistant actions call the same use cases. Closed action arguments, exact revisions, source versions, runtime policies and Workspace provenance apply to writes and reads. File tools opens from Home Tools, Workspace and file details; outputs can reopen or go to Canvas/Study/Studio where applicable. The UI displays actual Ready/Limited/In development states and has matching EN/LV/RU strings.

Archive format 4 preserves raw canonical note blocks and format markers, including unfinished CSV, larger editor content and unknown future block types. Backup restoration does not impose export syntax/size limits. Recognized references remap when duplicating; opaque malformed receipt text remains recoverable. Older archives without blocks retain the existing migration path.

## Source review and validation

Base main: 92370f6cd173affe1b9800fdc3b97a9e05559589, freshly fetched with 0/0 divergence before source freeze. All production and focused regression sources were authored before the first #52 build. Read-only review found and corrected a missing optional Ink dialog parameter and backup validation rejecting unfinished CSV. Follow-up review found no further concrete issue in those fixes. Static resource checks: 93 identical localized keys, all 91 referenced keys resolve.

Focused desktop sources cover strict UTF-8, CSV quoting/bounds, literal export/reopen, empty files, stale/range conflicts, revision-bound history, Canvas references, batch partial/cancelled/interrupted receipts, publication rollback, image conversion/composition, backup preservation and shared action boundaries. Native layout fixtures cover EN phone, LV 200% portrait and RU 200% short landscape. An isolated real-device workflow covers native PDF rendering/Ink/page PNG and exact text import/export, plus large-text table dismissal.

Both app and instrumentation APKs assembled. The first attempt found a missing FILE_TOOLS icon mapping in HomeToolCatalog; that production mapping was fixed before app assembly. Subsequent changes were regression fixtures only: the native PDF writer requires explicit try/finally closing, an existing editor test needed a named callback, synthetic media needed a real active source note, and desktop fixtures explicitly use an isolated Application. The production APK was not rebuilt for fixture corrections.

The final desktop run passed **36 tests / 8 classes**, with zero failures/errors/skips, in **1m 59s**. It includes actual managed-file/receipt rollback, stale-write rejection, literal files reopening/exporting/attaching to Canvas and resolving in Study, and rejection of trashed Study inputs. All three native EN/LV/RU layout renders were visually inspected. Done/Close remain reachable at large text; long table content scrolls.

Built app APK: **287,274,279 bytes**, SHA256 `a0718cac91c98301842ef3696aeaf234dd7ceb673e0cb470bbd862a123e69e02`. This is a built artifact, not an installed-device claim. The phone was available when source work began, then disappeared from ADB before deployment. The two newly compiled native phone tests have **NOT RUN**; native PDF annotation/page-image acceptance and large-text table device interaction remain pending. No #52 APK has been installed and no user Workspace or OS preference changes were made.

Runtime verification passed **6 tests / 3 classes**, zero failures/errors/skips, in **1m 6s**. It covers Workspace result categories and native skill ownership/approval boundaries. Issue #52 must remain open while its native acceptance is pending; continue independent backlog coding under the user's phone-later instruction.

## Remaining qualification

Native PDF/Office format-preserving editing is unavailable. Full navigation/interruption matrices, tablet/stylus/eraser behavior, accessibility services and sustained performance need their own evidence under release issue #43. Connected phone availability alone is not acceptance. All device fixtures use synthetic cache files and isolated Room databases, without live providers, user documents or OS preference changes.

## Current native offline acceptance — 2026-10-24

[Current report and exact installed/source identities](HALO-CANVAS-CLEAR-054-VERIFICATION-2026-10-24.md) supersede the earlier NOT_RUN/install status. Five native Workspace cases passed in14.385s on f01fe673/API35 with installed app/test readback matching. The strengthened file fixture now creates/edits/exports/imports each initial literal format (TXT/Markdown/CSV), closes an actual unique file-backed database, reconstructs all readers and verifies saved content/bytes, Canvas references and Study material resolution. It also verifies persisted PDF vector annotations/raster page output, JPEG rotation/dimensions and preserved PNG/PDF originals. RU200% table Done remains reachable. Existing desktop36-case file/action/publication/backup evidence and shared typed owners remain applicable; no unsupported native PDF/Office capability was added.

The initial supported toolkit acceptance is complete against source, desktop and native offline-owner evidence. Future faithful Office/native-PDF editing remains explicitly unavailable; full release navigation/performance/accessibility belongs to #43, while actual spoken/permitted Power workflows remain #54 acceptance. Database restart is not an OS process-kill result; Study material resolution is not saved Study-session restart. No user data or global preference changes.
