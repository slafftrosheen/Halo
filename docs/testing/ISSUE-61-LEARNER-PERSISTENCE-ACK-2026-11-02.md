# Issue #61 — durable Study turn and journey acknowledgements (2026-11-02)

Source-only implementation checkpoint. The changes, focused test sources and this ledger are committed to `main`; **Gradle/JUnit, APK, phone/ADB, provider output, device restart, spoken journey and TalkBack NOT_RUN** in this connector pass.

## Issue

`StudyLessonStore.updateRecovery` already verifies its persisted recovery representation, but `StudyLessonStore.save` (tutor-turn history) and `saveJourney` (selected depth, baseline, practice/exercises, reflection and saved summary link) previously returned after `Storage.putString` without confirming readback. Production AndroidStorage uses a synchronous commit, but alternate/restored adapters and storage failures are part of the reliability boundary. A silently dropped write could report successful lesson progress and make it appear lost on reopening.

## Fix

- Persist the bounded serialized turn history, re-read its exact stored representation, and only then increment the answer count observer revision.
- Persist the serialized learning journey and compare it to the actual stored string before returning. `StudyViewModel.keepJourney` already catches a failure and shows the user a truthful warning; this strengthens that existing owner instead of inventing a second persistence path.
- Existing recovery/journal and canonical Room session ownership remain unchanged.

## Regression sources authored (NOT_RUN)

`StudyLessonDurabilityTest` has three cases: a write-dropping Storage adapter must be rejected for turn history and journey state; normal saves must restore the exact history and learner baseline using a fresh store instance.

Run locally:
```bash
./gradlew :app:testDebugUnitTest --tests 'com.halo.app.study.StudyLessonDurabilityTest' --tests 'com.halo.app.study.StudyReaderRecoveryTest'
```

Then run the real #61 selected-note source-only → replies → practice → saved summary → force-stop/reopen flow. This patch does **not** solve the separately observed semantic source-only hallucination, which remains FAIL; nor does it establish device durability without executing the tests.
