package com.halo.app.ui.screens.study

import com.halo.app.data.study.StudySessionRecord
import com.halo.sdk.runtime.study.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The visible learning path follows learner work rather than treating hints as scores. */
class StudyFlowGuideTest {
    @Test fun startsWithExploreAndPrioritizesAnOwnAttempt() {
        val initial = StudyUiState(loading = false)
        assertEquals(StudyLearningPhase.EXPLORE, studyLearningPhase(initial))
        assertTrue(initial.copy(draft = "My answer").canSubmitAttempt)
    }

    @Test fun feedbackAndFollowUpBelongToTryPhase() {
        val explanation = StudyTurn(id = "turn-1", stage = TutorStage.ASK, text = "Try a small example")
        val afterExplanation = StudyUiState(loading = false, turns = listOf(explanation))
        assertEquals(StudyLearningPhase.TRY, studyLearningPhase(afterExplanation))
        assertEquals(StudyLearningPhase.REVIEW, studyLearningPhase(afterExplanation, reviewing = true))
    }

    @Test fun submissionIsDisabledWhileBusyOrLoading() {
        val ready = StudyUiState(loading = false, draft = "An attempt")
        assertTrue(ready.canSubmitAttempt)
        assertFalse(ready.copy(isGenerating = true).canSubmitAttempt)
        assertFalse(ready.copy(isSubmitting = true).canSubmitAttempt)
        assertFalse(ready.copy(loading = true).canSubmitAttempt)
        assertFalse(ready.copy(draft = "").canSubmitAttempt)
    }

    @Test fun completedSessionIsNotShownAsActivePractice() {
        val completed = StudySessionRecord(
            id = "lesson-1",
            goal = "Fractions",
            languages = StudyLanguagePreferences(),
            sourceObjectIds = emptyList(),
            sourceRevision = null,
            conversationId = null,
            runId = null,
            interactionId = null,
            tutorStage = TutorStage.EXPLAIN,
            lifecycle = StudySessionLifecycle.COMPLETED,
            currentExerciseId = null,
            solutionRevealed = false,
            completedAtEpochMs = 1L,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
        val state = StudyUiState(loading = false, session = completed, draft = "Try again")
        assertEquals(StudyLearningPhase.COMPLETE, studyLearningPhase(state))
        assertFalse(state.canSubmitAttempt)
    }
}
