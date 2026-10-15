package com.halo.app.ui.screens.study

import com.halo.sdk.runtime.study.StudySessionLifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyCompletionFxTest {
    @Test fun onlyActiveToDurablyCompletedCanBloom() {
        val active = StudySessionLifecycle.ACTIVE
        val completed = StudySessionLifecycle.COMPLETED
        assertTrue(shouldCelebrateStudyCompletion(active, completed))
        assertFalse(shouldCelebrateStudyCompletion(null, completed))
        assertFalse(shouldCelebrateStudyCompletion(completed, completed))
        assertFalse(shouldCelebrateStudyCompletion(active, active))
        assertFalse(shouldCelebrateStudyCompletion(active, StudySessionLifecycle.ARCHIVED))
    }
}
