package com.halo.app.ui.screens.study

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySetupGeometryTest {
    @Test fun progressSegmentsNeverBypassSourceOrGoalValidation() {
        assertTrue(studySetupCanOpenStep(0, 0, false, false))
        assertTrue(studySetupCanOpenStep(0, 1, false, false))
        assertFalse(studySetupCanOpenStep(0, 2, false, false))
        assertFalse(studySetupCanOpenStep(1, 2, false, false))
        assertTrue(studySetupCanOpenStep(1, 2, true, false))
        assertFalse(studySetupCanOpenStep(1, 2, true, true))
    }
}
