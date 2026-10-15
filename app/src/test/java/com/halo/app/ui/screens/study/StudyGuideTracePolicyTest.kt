package com.halo.app.ui.screens.study

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyGuideTracePolicyTest {
    @Test fun guideRequiresOneActionThatIsEnabledAndActive() {
        assertTrue(studyGuideTraceEnabled(true, true, true))
        assertFalse(studyGuideTraceEnabled(true, true, false))
        assertFalse(studyGuideTraceEnabled(true, false, true))
        assertFalse(studyGuideTraceEnabled(false, true, true))
    }
}
