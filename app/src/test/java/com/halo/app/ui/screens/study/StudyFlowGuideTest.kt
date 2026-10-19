package com.halo.app.ui.screens.study

import com.halo.app.data.study.StudySessionRecord
import com.halo.sdk.runtime.study.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The visible learning path follows learner work rather than treating hints as scores. */
class StudyFlowGuideTest {
    @Test fun finishedLearningPathDoesNotClaimItIsStillReviewing() {
        assertEquals("Review", studyPathFinalLabel(StudyLearningPhase.REVIEW))
        assertEquals("Complete", studyPathFinalLabel(StudyLearningPhase.COMPLETE))
        assertEquals("Review", studyPathFinalLabel(StudyLearningPhase.EXPLORE))
    }

    @Test fun guidedStepNamesNeverRevealFullSolutionImplicitly() {
        assertEquals("Get a hint", nextGuidanceAction(TutorStage.ASK))
        assertEquals("Explain the idea", nextGuidanceAction(TutorStage.HINT))
        assertEquals("See it another way", nextGuidanceAction(TutorStage.EXPLAIN))
        assertEquals("Check what I know", nextGuidanceAction(TutorStage.ANALOGY))
        assertEquals("Work through a step", nextGuidanceAction(TutorStage.CHECK_ATTEMPT))
        assertEquals(null, nextGuidanceAction(TutorStage.PARTIAL_SOLUTION))
        assertEquals(null, nextGuidanceAction(TutorStage.SOLUTION))
    }

    @Test fun startsWithExploreAndPrioritizesAnOwnAttempt() {
        val initial = StudyUiState(loading = false)
        assertEquals(StudyLearningPhase.EXPLORE, studyLearningPhase(initial))
        assertTrue(initial.copy(draft = "My answer").canSubmitAttempt)
    }

    @Test fun explanationDoesNotAdvanceTheSavedJourneyToPractice() {
        val explanation = StudyTurn(id = "turn-1", stage = TutorStage.ASK, text = "Try a small example")
        val afterExplanation = StudyUiState(loading = false, turns = listOf(explanation))
        assertEquals(StudyLearningPhase.EXPLORE, studyLearningPhase(afterExplanation))
        assertEquals(StudyLearningPhase.TRY, studyLearningPhase(afterExplanation.copy(
            journey = afterExplanation.journey.copy(stage = LearningStage.PRACTICE))))
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
