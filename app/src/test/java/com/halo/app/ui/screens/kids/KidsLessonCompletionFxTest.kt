package com.halo.app.ui.screens.kids

import com.halo.sdk.runtime.study.StudySessionLifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsLessonCompletionFxTest {
    @Test fun finishOnlyAvailableForActiveNonBusyLesson() {
        assertTrue(kidsLessonCanFinish(StudySessionLifecycle.ACTIVE, busy = false))
        assertFalse(kidsLessonCanFinish(StudySessionLifecycle.ACTIVE, busy = true))
        assertFalse(kidsLessonCanFinish(StudySessionLifecycle.COMPLETED, busy = false))
        assertFalse(kidsLessonCanFinish(null, busy = false))
    }
}
