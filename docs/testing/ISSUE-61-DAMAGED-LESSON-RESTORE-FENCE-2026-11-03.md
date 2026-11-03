# Issue #61 — damaged persisted lesson recovery fence (2026-11-03)

**Source-only GitHub delivery**: Kotlin owner, three regression tests and handoff document committed to `main`. Gradle/JUnit, APK/ADB, real storage failure, microphone and provider E2E are **NOT_RUN**.

## Identified defect

The existing `StudyLessonStore.load` attempted JSON parsing inside `runCatching { ... }.getOrDefault(emptyList())`, including a silent 600,000-character truncation and a silent 30-turn truncation. `loadJourney` similarly interpreted corrupt JSON as a missing journey. A later valid-looking save could replace the unreadable original with a new short turn list or reset learner baseline, causing **irreversible local data loss without a truthful recovery event**.

## Code delivery

- Strict bounded `readSavedTurns` and `readSavedJourney` now reject malformed or unsupported retained records instead of representing them as empty, and keep the original bytes untouched.
- `save` and `saveJourney` check that previously saved data is readable **before** replacing it. The existing readback/confirmed-commit guard remains in force. An explicitly absent record still starts as empty/new.
- The Study ViewModel already catches restoration failure and shows a failure notice. The new #61 tutor save-error guard prevents accepting a new unsaved reply, so an unreadable saved history can no longer be silently overwritten by retry.
- No new database, unsolicited recovery rewrite, or model/remote dispatch was added. Explicit user-assisted recovery or a later migration is still necessary for genuinely damaged records.

## Regression source — NOT_RUN

`StudyLessonDurabilityTest` adds three cases: malformed history refuses load and overwrite; an oversized stored record is not truncated/overwritten; malformed saved journey refuses load and overwrite. Existing saved/confirmed round-trip cases remain.

Local verification command:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.halo.app.study.StudyLessonDurabilityTest' --tests 'com.halo.app.study.StudyTutorPersistenceFailureTest'
```

Device acceptance: saved note and Study output after process death, storage-denial diagnostic, and a controlled corrupted-history recovery without destroying saved raw data. The previously observed **strict source-only semantic hallucination remains FAIL**, and actual Kids/voice gates remain open. Do not close #61 from this patch.

Follow-up storage-boundary guard: new journey JSON is now checked against the same 200,000-character size cap required for restore, **before** committing it. An oversized new journey is rejected without persisting a record the reader would later refuse. One additional regression test is authored; still NOT_RUN.
