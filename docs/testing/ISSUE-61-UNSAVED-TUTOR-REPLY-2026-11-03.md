# Issue #61 — reject unsaved generated lesson progress (2026-11-03)

This is a code-only commit. Gradle, Robolectric/JUnit, APK, ADB, real voice, provider and physical storage-loss tests remain **NOT_RUN**; do not infer native acceptance.

## Reproduced source defect

The 2 November `StudyLessonStore.save` change correctly requires a stored-string readback. The consumer `StudyViewModel.requestTutorTurn` still caught that failure but proceeded to append the generated reply to the lesson, clear the learner's submitted draft, complete a branch and save feedback. The reply and branch could therefore appear successful in the current process despite being absent on restart. For source-only Study, an uncommitted model answer must not become progress.

## Implementation

- If a non-null lesson store refuses a model reply write, show an explicit not-saved notice, preserve the exact learner draft, retain previous turns, do not update the branch as explored and return **before** practice/feedback/handoff.
- Move optional branch progress after successful response storage. No new retry loop is introduced; user-initiated retry remains available and model-generated content is not mislabeled as retained.
- Existing source version checks, generation identity, cancellation and stored-session ownership remain intact. The optional no-store preview host keeps its prior non-durable behavior.

## Regression sources added — NOT_RUN

`StudyTutorPersistenceFailureTest` injects a Storage adapter that silently drops only lesson-history writes (while preserving draft/recovery writes). It checks that a tutor reply does not advance turns, remove the question or claim a saved response. A control case confirms that successful persistence still exposes the reply and clears only the submitted question.

Run locally:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.halo.app.study.StudyTutorPersistenceFailureTest' --tests 'com.halo.app.study.StudyLessonDurabilityTest'
```

Finish with the issue #61 native selected-passage → three tutor turns → reviewed practice → save/restart, including a storage-denial/failure where safely reproducible. Strict semantic source-only truth (the earlier 'surface tension' hallucination) and actual voice/Kids acceptance remain OPEN.

Follow-up test hygiene: the regression fixture uses the canonical `kotlinx.coroutines.ExperimentalCoroutinesApi` annotation and joins cancelled ViewModel jobs before database teardown. This does not change production behavior or represent an executed test.
