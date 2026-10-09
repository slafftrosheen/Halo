package com.halo.app.study

import com.halo.app.ui.screens.study.StudyLessonStore
import com.halo.app.ui.screens.study.StudyTurn
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.TutorStage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class StudyLessonCountTest {
    @Test fun repliesCountWithoutGradingAndStayCorrectAcrossRestoreAndDeletion() = runBlocking {
        val storage = MemoryStorage()
        val store = StudyLessonStore(storage)
        val counts = mutableListOf<Int>()
        val observer = launch { store.observeSavedResponseCount("lesson").collect { counts.add(it) } }
        try {
            withTimeout(5000) { while (counts.lastOrNull() != 0) delay(10) }
            store.save("lesson", listOf(StudyTurn("reply", TutorStage.HINT, "Try drawing the two groups."),
                StudyTurn("failed", TutorStage.HINT, "", failure = "Unavailable")))
            withTimeout(5000) { while (counts.lastOrNull() != 1) delay(10) }
            assertEquals(1, StudyLessonStore(storage).observeSavedResponseCount("lesson").first())
            assertEquals(0, store.observeSavedResponseCount("other").first())
            store.delete("lesson")
            withTimeout(5000) { while (counts.lastOrNull() != 0) delay(10) }
            assertEquals(listOf(0, 1, 0), counts)
        } finally { observer.cancelAndJoin() }
    }
    private class MemoryStorage : Storage {
        private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if(value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
