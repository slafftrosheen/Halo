# Issue #58 — reviewed workflow preview compare-and-set (2026-11-02)

Source-only follow-up. **Gradle/JUnit, APK/ADB, real model and spoken workflows NOT_RUN** in this connected GitHub pass. No full issue acceptance or session replay proof claimed.

## Defect

The saved-workflow `halo_use_procedure` tool read, validated and prepared a reviewed recipe, then unconditionally called `LearnedProcedureStore.recordUsage(id, time)`. A concurrent UI edit/disable/delete could replace that row after the read but before the usage update. The tool could then count use on a different recipe and return stale instructions. Although individual tool execution still goes through canonical policies, the workflow receipt and reviewed version would no longer be coherent.

## Fix

- `LearnedProcedureStore.recordUsageIfCurrent(expected, time)` uses the existing compare-and-set contract to update usage metadata **only** if the whole reviewed row is unchanged. Unsupported stores fail closed.
- The procedure tool now refuses the preview if the row changed at receipt time. It never executes steps, retries an unknown write or creates new authority.
- All `InMemoryLearnedProcedureStore` CRUD and CAS methods share the same lock used by `insertIfAbsent`. Production `SharedPreferencesLearnedProcedureStore` transactions now share one process-wide mutex for all instances pointing to the same preferences document (single Android process; not cross-process safety).
- Normal usage updates do not change `updatedAt` or the reviewed version fingerprint. An independent edit may require the user to read a new preview.

## Authored regression tests — NOT_RUN

`ProcedureAdmissionTest` includes an interleaved review edit during usage CAS (must reject without stale preview or wrong usage increment), and repeat same-version previews (must count twice without changing review version).

**Local gate:** `./gradlew :app:testDebugUnitTest --tests 'com.halo.app.di.tools.ProcedureAdmissionTest' --tests 'com.halo.app.di.tools.ProcedureWorkflowToolsTest'`.

Still open: approval, replay, process death and five typed/three real final-STT workflows, including the device-visible preview and receipts.
