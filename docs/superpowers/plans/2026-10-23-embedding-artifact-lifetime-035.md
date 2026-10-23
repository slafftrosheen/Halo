# Embedding artifact lifetime #35

Base main `9ba1ac35fb32f922807c1bccfc56df57876d75a0`. Existing WorkspaceSemanticService media sources/index/query and exact pinned runtime policy remain authoritative. No new retrieval engine, source scan, model recommendation or cloud route.

- [x] Expose existing exact verified artifact read lease through the inventory boundary, fail closed when unavailable.
- [x] Hold that pin through native metadata/embedding engine lifetime; retain engine/metadata and pin together on failed teardown and release only after actual close.
- [x] Apply an exact physical pin to manual native artifact inspection, preserving unverified imports and update canonical callers.
- [x] Author isolated actual-manager file mutation/native adapter lifetime/cancellation/failed-close regressions and source review before gates.
- [x] Run focused provider/app compilation/tests, publish verified source and keep physical cross-modal/model/language/Polish acceptance separate and OPEN.

Read-only review found cancellation during coordinator admission could strand a compute lease. Outer cleanup now covers admission; regression uses the real generic lease manager with a blocked coordinator callback. Failed native close still retains the session and file pin together. Focused provider and app gates passed 34 cases, plus Android test source compilation. No APK assembly or device install in this batch.
