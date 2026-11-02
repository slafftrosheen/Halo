# Issue #58 — workflow save and binding integrity (2026-11-02)

**Status:** source-only changes on `main`; Gradle/JUnit/physical device/provider, typed and spoken workflows **NOT_RUN** in this GitHub connector session. No whole-issue acceptance is claimed.

## Audited failure and changes

`ProcedureToolExecutor.saveOnce` previously used `LearnedProcedureStore.upsert` after checking the generated proposal ID. An ID collision with an existing workflow overwrote a separately reviewed recipe; a concurrent insert between the preflight read and upsert had the same result. Two pending in-memory proposals with the same ID also replaced one another's review. Deterministic IDs reproduce these cases even though UUID collisions are normally rare.

`ProcedureContracts.bind` accepted persisted input bindings overlapping reviewed literal arguments and silently defaulted undeclared input types to string. This admitted corrupted/stale saved templates before ordinary registry validation.

- Added fail-closed `LearnedProcedureStore.insertIfAbsent`, implemented atomically in both the production SharedPreferences store and the in-memory store.
- Proposals refuse duplicate pending IDs. Saving rejects existing saved IDs and races; it never overwrites a reviewed recipe through the proposal path.
- Restored typed bindings refuse literal collisions and undeclared named inputs before showing the preview; ordinary argument/schema revalidation remains mandatory.
- No new execution mechanism, authority, grants, provider or background worker.

## Regression sources (written; NOT_RUN)

Three tests in `ProcedureAdmissionTest`: existing saved ID collision, race between preflight and commit, and pending proposal collision. One test in `ProcedureWorkflowToolsTest`: corrupted saved template rejection while valid binding remains functional.

Local gate:
```bash
./gradlew :app:testDebugUnitTest --tests 'com.halo.app.di.tools.ProcedureAdmissionTest' --tests 'com.halo.app.di.tools.ProcedureWorkflowToolsTest'
```

Then perform the #58 five typed/three spoken workflows and the restart/replay/approval matrix on a real Android phone, distinguishing source contract tests from physical evidence. Issue #58 remains open.
