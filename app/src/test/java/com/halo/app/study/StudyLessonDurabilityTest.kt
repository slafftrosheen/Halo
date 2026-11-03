package com.halo.app.study

import android.app.Application
import com.halo.app.ui.screens.study.LearningJourney
import com.halo.app.ui.screens.study.StudyLessonStore
import com.halo.app.ui.screens.study.StudyTurn
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.TutorStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A write acknowledged by Storage must be readable; no E2E disk/device claim is inferred. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class StudyLessonDurabilityTest {
    private class StorageFixture : Storage {
        val data = mutableMapOf<String, Any>()
        var dropWrites = false
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) {
            if (!dropWrites) {
                if (value == null) data.remove(key) else data[key] = value
            }
        }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }

    @Test fun silentlyDroppedTurnWriteCannotClaimSavedLessonOrNotifyObserver() = runBlocking {
        val storage = StorageFixture().also { it.dropWrites = true }
        val lessons = StudyLessonStore(storage)
        try {
            lessons.save("lesson", listOf(StudyTurn(id = "turn", stage = TutorStage.EXPLAIN, text = "Evidence")))
            fail("Unconfirmed tutor reply was reported as saved")
        } catch (_: IllegalStateException) { }
        assertTrue(lessons.load("lesson").isEmpty())
    }

    @Test fun silentlyDroppedJourneyWriteCannotConfirmLearningProgress() = runBlocking {
        val storage = StorageFixture().also { it.dropWrites = true }
        val lessons = StudyLessonStore(storage)
        try {
            lessons.saveJourney("lesson", LearningJourney(priorKnowledge = "Learner's baseline"))
            fail("Unconfirmed journey was reported as saved")
        } catch (_: IllegalStateException) { }
        assertNull(lessons.loadJourney("lesson"))
    }

    @Test fun confirmedTurnsAndProgressRestoreThroughFreshStoreInstance() = runBlocking {
        val storage = StorageFixture()
        val original = StudyLessonStore(storage)
        original.save("lesson", listOf(StudyTurn(id = "turn", stage = TutorStage.EXPLAIN, text = "Grounded note")))
        original.saveJourney("lesson", LearningJourney(priorKnowledge = "My baseline"))
        val reopened = StudyLessonStore(storage)
        assertEquals(listOf("turn"), reopened.load("lesson").map { it.id })
        assertEquals("Grounded note", reopened.load("lesson").single().text)
        assertEquals("My baseline", reopened.loadJourney("lesson")?.priorKnowledge)
    }
}
