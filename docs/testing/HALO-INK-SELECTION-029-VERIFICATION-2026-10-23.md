# Ink selection handoff verification — 2026-10-23

Base main: `9cb329f44a36de507cce47ca29451cf6b4286a06`. Issue #29 stays open pending acceptance.

## Implemented

The existing saved-bookmark, crop, working-set, Ask and Study owners remain authoritative. Selection promotion now compares the exact expected bookmark in the same Room transaction as canonical source resolution and working-set mutation. Handoffs reject a replaced bookmark, deselected region, edited/deleted source, inactive imported file, unavailable/relinked file or changed file version.

A rendered crop carries its bookmark ID to the existing Ask route. Source checks run after rendering and around image ingest. The source owner validates and executes the synchronous visual-context publication inside the same read transaction, after suspending conversation/media preparation. No newer whole-Canvas or bookmark version can authorize an older crop. Known attachment/context owner IDs allow cancellation cleanup even before a newly allocated reference is delivered; cleanup affects only that owned reservation. Guards are transient host callbacks, never serialized or sent to a provider. Study validates again after image persistence and trashes its own incomplete material if rejected. Sketch rendering rethrows cancellation.

No new input engine, tool, permission, AI route, ordinary UI control or copy. Existing EN/RU/LV source-change/preparation messages are reused. Existing policy/consent and turn-time source-version checks remain in place.

## Source review and regression scope

Production and regression sources were completed before the focused gate. Read-only review found a final-context-publication race after ingest validation; the final transaction guard and transferred-context cleanup fixed it. Follow-up review found no concrete blocker.

Regressions use actual in-memory Room and the ephemeral media store. They cover a still-valid replacement bookmark for the same source/strokes, deselection without automatic reselection, source edit/delete/archive, imported-source lifecycle/readability/version changes, derived-image rejection after ingest, cancellation before ingest result delivery and at final validation. An end-to-end adapter test pauses context transfer, then deselects/replaces/cancels: no visual context or pending attachment may remain, both new reservations must be released, and an unrelated review must survive. Existing crop pixel/region and context/privacy/continuity regressions are included.

Focused app gate passed on the first run in 6 minutes 17 seconds: 37 cases across ContextualCaptureTest (17), ContextAdapterTest (12), CanvasInkSelectionBookmarkTest (6), CanvasInkSelectionCropTest (2), zero failures/errors/skips. Production and unit-test compilation passed. Compiled production tree: `393744d9327a84f33ec4671e77e9998fe1120658`. Raw log/XML results: `artifacts/HALO-INK-SELECTION-029-2026-10-23/desktop/`. No APK assembly or device install in this batch; ADB inventory is empty.

## Remaining acceptance

This is source/desktop correctness evidence, not actual pen pressure/eraser/palm/tablet qualification, real spoken referent recognition, remote-provider acceptance, handwriting/OCR accuracy, or cross-device Ink. Those checks remain open under #28/#29/#30 and #43. The original #29 description predates much of the existing selection and Study integration; no duplicate engine was added.
