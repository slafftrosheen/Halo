# #28 drawing draft identity and identical-content replacement cleanup

Base: fetched main `f5610e490708eeb0ae60a6ddffb3a9b182adc206`, clean0/0. Production and regression source completed and read-only review clear before validation. No CI/Actions.

## Problem and change

The existing Room compare-and-clear checked the complete decoded drawing draft. However, two separately emitted editor drafts with the same source and ink were equal. An old Save/Cancel could therefore consume a newer identical draft (including an A→B→A content sequence).

`CanvasDrawingDraft` now creates an opaque UUID receipt per new editor emission and persists it in `canvas-drawing:v2`. Existing sketch and annotation callbacks already construct new snapshots; annotation source rebinding copies retain the receipt. The same transactional cleanup now compares identity as well as all existing source/payload fields. A retry of the same emitted snapshot stays idempotent.

Legacy `canvas-drawing:v1` remains readable and encodable without an identity. Decode never mints a new receipt for legacy bytes, so repeated recovery reads compare consistently. Missing/malformed v2 receipts and unknown versions fail closed. Existing source matching, file-version checks, quotas and ink formats remain authoritative. No Room migration, store, user-facing copy, new tool, approval path or runtime owner.

Scope: this identifies newly emitted draft snapshots, not every repository write or every source-node mutation. Kotlin copies retain the original receipt. It does not establish a monotonic source revision protocol or solve every possible source-content ABA cycle.

## Regression coverage

Focused classes: `CanvasDrawingDraftRepositoryTest`, `CanvasEditorPersistenceTest`, `InkV2Test`, `CanvasDailyRepositoryTest`. Added cases cover persisted v2 identity, deterministic legacy decoding, malformed/missing/unknown receipt format, separately emitted identical-content replacements, legacy/current conflict, exact-owner cleanup, actual ViewModel stale Save/Cancel and file-backed Room close/reopen. Existing v1/v2 ink and saved editor behavior are included.

Native `CanvasDraftReceiptDeviceTest#identicalReplacementKeepsItsIdentityAcrossDatabaseReopen` uses a unique disposable file-backed database, the production repository/codec and an actual close/reopen. It also checks legacy/current annotation cleanup. It does not touch the user's main Workspace or preferences. This is Android storage-owner reconstruction, not OS process death, human pen input or end-to-end spoken acceptance.

## Results

First gate24040: **FAIL in6m5s**, 56 cases /2 fixture failures (no errors/skips). Production/test compilation completed, but APK assembly did not run after the failing unit task. Raw failure results are retained. The new file-backed Robolectric test generated a Windows temporary SQLite path beyond the supported length; shortening the test method and database names preserves the same assertions. An older CanvasDaily markup fixture had no readable WorkspaceFileEntity despite the current production guard; it now first asserts missing-source rejection, then supplies the expected readable source row before its unchanged stale-overwrite checks. No production guard was relaxed. Read-only review of both fixture corrections: clear.

The39 passing cases in CanvasEditorPersistenceTest(14) and InkV2Test(25) have unchanged production/test inputs after those fixture-only corrections. Only the two affected classes (17 cases) are rerun with APK assembly; this avoids repeating unaffected passing coverage.

Final gate56869: **PASS in3m26s**, 511 tasks (14 executed /497 up-to-date), both production and test APKs assembled. Corrected classes passed7+10 cases, zero failures/errors/skips. Combined with the39 unaffected passing cases above, all56 focused cases are covered without double-counting the earlier15 passes inside the rerun classes. Production compiled output was reused after fixture-only corrections. Serial Gradle, bundled Android Studio JBR, no daemon/build cache/parallel tasks and3GiB heap.

Native58246: **PASS**, one Android case in0.213s, on `f01fe673` /23124RA7EO /API35. The real file-backed Room database was closed/reopened; the newer receipt survived, the old equal-content receipt could not clear it, its actual receipt could, and legacy/current annotation receipts remained distinct. User Workspace and saved settings were not touched. MainActivity resumed afterward.

Both installed package readbacks matched:

| APK | SHA-256 | Bytes |
|---|---|---:|
| Production | `f48801cdd8858d29d5b6e5b00275aca3fd82013bcdf713223b7a09a5bb334a20` | 288504360 |
| Android test | `85e75c33b942a4cac9c266634a8694f94be23090d3900bc841416d3d3877e441` | 1963640 |

Six changed compiled-input file hashes were recorded before the final gate and verified unchanged before installation/publication. Failed first-gate logs/XML and initial hashes, final gate/results/source hashes, exact APK readback identities, native output and launch evidence: [artifacts](artifacts/HALO-DRAWING-RECEIPT-028-2026-10-24/). No unrelated model, speech or embedding checks were repeated.

## Remaining #28 acceptance

Keep #28 open for compatible physical pressure/eraser/palm hardware, tablet/accessibility/rotation, long-session performance and full process-death UI recovery. Existing typed Canvas operations remain on the same canonical owner; this internal cleanup fix adds no assistant action. The original sketch strokes, source files and unrelated/newer draft snapshots are preserved by the existing guards plus the receipt comparison.
