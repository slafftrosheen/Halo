package com.halo.app.nativeactions

import android.app.Application
import com.halo.app.ai.IntentQualificationFixture.MemoryStorage
import com.halo.app.data.study.StudyAttemptEntity
import com.halo.app.data.study.StudyFeedbackEntity
import com.halo.app.data.study.StudyPracticeItemEntity
import com.halo.app.data.study.StudyReviewEventEntity
import com.halo.app.data.study.StudySessionEntity
import com.halo.app.ui.screens.study.StudyLessonStore
import com.halo.app.ui.screens.study.StudyPrivateSidecars
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class PrivateStudySessionPayloadTest {
    @Test fun exactLessonPayloadRoundTripsAllOwnedDataAndLegacyValues() {
        val session = StudySessionEntity(id = "lesson", goal = "Graph reading", assignmentLanguage = "lv", explanationLanguage = "ru",
            strongestLanguage = null, sourceObjectIdsJson = "[\"note\"]", sourceRevision = 6, conversationId = null, runId = null,
            interactionId = null, tutorStage = "HINT", lifecycle = "COMPLETED", currentExerciseId = "exercise", solutionRevealed = true,
            completedAtEpochMs = 19, schemaVersion = 1, createdAtEpochMs = 1, updatedAtEpochMs = 21,
            sourcesJson = "[{\"objectId\":\"note\",\"revision\":\"version\"}]", learnerLevel = "ADULT",
            supportStyle = "GUIDED", knowledgePolicy = "SOURCE_ONLY")
        val attempt = StudyAttemptEntity("attempt", session.id, "exercise", "My working", "ANSWER", "[\"HINT\"]", "once", 9L)
        val feedback = StudyFeedbackEntity("feedback", session.id, attempt.id, "generation", "UNCERTAIN", "Overview", "Next", null, "[]", 10L)
        val card = StudyPracticeItemEntity("card", session.id, "Q", "A", "source-note", 6L, false, 2, 15L, 3,
            knownStreak = 2, wrongStreak = 1, mastered = true, userEdited = true, createdAtEpochMs = 4L, lastReviewedAtEpochMs = 12L,
            sourceVersion = "working-version", sourceLocator = "page 1")
        val review = StudyReviewEventEntity("review", card.id, session.id, true, 1, 2, 15L, 12L, "KNOWN")
        val payload = PrivateStudySessionPayload(session, listOf(attempt), listOf(feedback), listOf(card), listOf(review),
            StudyPrivateSidecars("[{\"id\":\"turn\"}]", "{\"summaryNoteId\":\"summary\"}", "{\"draft\":\"work\"}", false),
            linkedSetOf("note", "source-note", "summary"))
        assertEquals(payload, PrivateStudySessionPayload.decode(JSONObject(payload.encode().toString())))
    }

    @Test fun sidecarTransitionFollowsThePublicCommitOrRollback() = runBlocking {
        val storage = MemoryStorage()
        var sessionExists = true
        val lessons = StudyLessonStore(storage, sessionExists = { sessionExists })
        val before = lessons.withPrivateLibrarySidecars { lessons.capturePrivateSidecars("lesson") }
        assertTrue(runCatching {
            lessons.withPrivateLibrarySidecars {
                lessons.withdrawPrivateSidecars("lesson", before)
                error("SQLite rolled back")
            }
        }.isFailure)
        assertFalse(lessons.withPrivateLibrarySidecars { lessons.capturePrivateSidecars("lesson").deleted })

        lessons.withPrivateLibrarySidecars {
            lessons.withdrawPrivateSidecars("lesson", before)
            sessionExists = false
        }
        assertTrue(lessons.withPrivateLibrarySidecars { lessons.capturePrivateSidecars("lesson").deleted })
        lessons.withPrivateLibrarySidecars {
            lessons.restorePrivateSidecars("lesson", before)
            sessionExists = true
        }
        assertEquals(before, lessons.withPrivateLibrarySidecars { lessons.capturePrivateSidecars("lesson") })
    }
}
