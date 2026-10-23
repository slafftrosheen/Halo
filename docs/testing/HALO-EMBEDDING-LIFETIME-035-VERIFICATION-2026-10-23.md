# Embedding artifact lifetime verification — 2026-10-23

Base main: `9ba1ac35fb32f922807c1bccfc56df57876d75a0`. Issue #35 stays open.

## Implemented

The existing inventory owner now exposes exact physical and verified artifact read leases. Embedding holds a verified pin across native metadata, initialization, computation and teardown. Manual inspection pins exact valid physical bytes without promoting unverified imports. A failed native close retains both handle and pin for the existing coordinator to retry; callbacks do not re-enter the coordinator lock. Cancellation at the IO result handoff releases an undelivered lease, and cancellation during resource admission unregisters partial compute ownership. Existing input limits, CPU/modality checks, index identities and local source consent remain authoritative.

Canonical Workspace semantic, Workload Lab and Android role-test callers use the same inventory owner. The existing friendly model-not-ready message covers unavailable artifact leases.

## Validation

Production and regression sources preceded the focused gate. Read-only review found an admission-cancellation leak; the corrected path and real-resource-manager regression passed follow-up review.

Provider gate passed 25 cases across NativeArtifactLifetimeTest (11), LiteRtAcquisitionOwnershipTest (5), ModelArtifactFileLocksTest (2), LocalEmbeddingModalityPolicyTest (2), LocalMediaEmbeddingSafetyTest (5), zero failures/errors/skips. Initial gate compiled but one fixture assumed suppressed teardown errors were directly on a cancellation exception; coroutine stack recovery wraps the original as a cause. The fixture now checks the preserved cause chain; the passing rerun took 31 seconds.

App gate passed in 5 minutes 49 seconds: 9 NotebookLocalSearchSourcesTest cases, zero failures/errors/skips, plus successful production and Android test source compilation. Android test source compilation is included because the role-test caller changed. Total: 34 passing desktop cases. No APK assembly or device install is claimed for this batch; current ADB inventory is empty.

## Remaining acceptance

This source ownership fix does not establish real cross-modal similarity, exact-artifact CPU support, accuracy, latency/RSS/low-RAM behavior, provider/model switching, actual spoken flows or Polish UI review. Those #35 physical/localization criteria remain open. Existing image/audio/sampled-video consumer paths are present in source; the original issue description predates them.

Compiled production source trees: app `480323d8d88e65477e38e4906ed136c6dca01297`; LiteRT `faa3995d3e62b52052cdd8b517c6be6cc31453c6`. Raw focused results are in `artifacts/HALO-EMBEDDING-LIFETIME-035-2026-10-23/desktop/`.
