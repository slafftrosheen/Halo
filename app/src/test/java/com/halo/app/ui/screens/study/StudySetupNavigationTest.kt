package com.halo.app.ui.screens.study

import com.halo.sdk.runtime.study.StudyKnowledgePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySetupNavigationTest {
    @Test fun ungroundedTopicLessonUsesGeneralKnowledgeInsteadOfClaimingASource() {
        assertEquals(StudyKnowledgePolicy.SOURCE_PLUS_GENERAL,
            studyKnowledgePolicyForSources(false, StudyKnowledgePolicy.SOURCE_ONLY))
        assertEquals(StudyKnowledgePolicy.SOURCE_ONLY,
            studyKnowledgePolicyForSources(true, StudyKnowledgePolicy.SOURCE_ONLY))
        assertEquals(StudyKnowledgePolicy.SOURCE_PLUS_GENERAL,
            studyKnowledgePolicyForSources(true, StudyKnowledgePolicy.SOURCE_PLUS_GENERAL))
    }

    @Test fun sourceFirstCanAdvanceFromBlankGoalToMaterialPicker() {
        assertTrue(studySetupCanContinue(step = 0, hasLearningInput = false, busy = false))
        assertTrue(studySetupCanOpenStep(0, 1, hasLearningInput = false, busy = false))
    }

    @Test fun blankGoalAndNoSourcesCannotBypassMaterialSelectionOrStart() {
        for (step in 1..3) assertFalse(studySetupCanContinue(step, false, false))
        assertFalse(studySetupCanOpenStep(0, 2, false, false))
        assertFalse(studySetupCanOpenStep(1, 2, false, false))
        assertFalse(studySetupCanOpenStep(2, 3, false, false))
    }

    @Test fun goalOrSelectedMaterialAllowsStepProgressAndStart() {
        for (step in 0..3) assertTrue(studySetupCanContinue(step, true, false))
        for (step in 0..2) assertTrue(studySetupCanOpenStep(step, step + 1, true, false))
    }

    @Test fun backNavigationRemainsAvailableAndBusyStateBlocksChanges() {
        assertTrue(studySetupCanOpenStep(3, 0, false, false))
        assertFalse(studySetupCanOpenStep(3, 4, true, false))
        assertFalse(studySetupCanContinue(0, true, true))
        assertFalse(studySetupCanOpenStep(1, 0, true, true))
        assertFalse(studySetupCanOpenStep(0, -1, true, false))
    }
}
