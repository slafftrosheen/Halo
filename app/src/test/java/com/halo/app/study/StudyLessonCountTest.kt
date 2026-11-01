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
    @Test fun escapedAnswerOverflowPreservesThePreviousReadableRecord() = runBlocking {
        val storage = MemoryStorage()
        val store = StudyLessonStore(storage)
        store.saveDraft("lesson", "Kept attempt")
        store.saveCardAnswers("lesson", mapOf("first" to "Kept answer"))
        val previous = store.loadRecovery("lesson")
        assertTrue(runCatching { store.saveCardAnswers("lesson", (1..70).associate { "card-$it" to "\"".repeat(1500) }) }.isFailure)
        assertEquals(previous, StudyLessonStore(storage).loadRecovery("lesson"))
        store.saveDraft("lesson", "Next attempt")
        assertEquals("Next attempt", store.loadRecovery("lesson").draft)
    }
    @Test fun interruptedAttemptKeepsDraftAndSubmissionIdentityAcrossStoreRecreation() = runBlocking {
        val storage = MemoryStorage()
        val first = StudyLessonStore(storage)
        first.saveDraft("lesson", "My attempt")
        assertEquals("attempt-one", first.prepareAttempt("lesson", "My attempt", "attempt-one"))
        first.markPendingTurn("lesson", "old-turn")
        val restored = StudyLessonStore(storage)
        assertEquals("My attempt", restored.loadRecovery("lesson").draft)
        assertEquals("old-turn", restored.loadRecovery("lesson").pendingRequestId)
        assertEquals("attempt-one", restored.prepareAttempt("lesson", "My attempt", "attempt-two"))
        restored.markPendingTurn("lesson", "new-turn")
        restored.clearPendingTurn("lesson", "old-turn")
        assertEquals("new-turn", restored.loadRecovery("lesson").pendingRequestId)
        restored.saveDraft("lesson", "A new attempt")
        assertNull(restored.loadRecovery("lesson").submissionKey)
        restored.delete("lesson")
        restored.clearPendingTurn("lesson", "new-turn")
        assertEquals("", restored.loadRecovery("lesson").draft)
        assertNull(restored.loadRecovery("lesson").pendingRequestId)
    }
    @Test fun practiceAnswersSurviveStoreRecreationWithoutChangingTheAttemptDraft() = runBlocking {
        val storage = MemoryStorage()
        val store = StudyLessonStore(storage)
        store.saveDraft("lesson", "Question for Halo")
        store.saveCardAnswers("lesson", mapOf("card-a" to "My answer", "card-b" to "Another answer"))
        val restored = StudyLessonStore(storage).loadRecovery("lesson")
        assertEquals("Question for Halo", restored.draft)
        assertEquals(mapOf("card-a" to "My answer", "card-b" to "Another answer"), restored.cardAnswers)
        store.saveCardAnswers("lesson", mapOf("card-b" to "Another answer"))
        assertFalse(StudyLessonStore(storage).loadRecovery("lesson").cardAnswers.containsKey("card-a"))
        store.delete("lesson")
        assertTrue(StudyLessonStore(storage).loadRecovery("lesson").cardAnswers.isEmpty())
    }
    @Test fun failedOrUnconfirmedWritesCannotReturnSuccessfulRecoveryAndCorruptionIsNotOverwritten() = runBlocking {
        val storage = MemoryStorage()
        val store = StudyLessonStore(storage)
        store.saveDraft("lesson", "Kept")
        storage.dropWrites = true
        assertTrue(runCatching { store.saveDraft("lesson", "Not kept") }.isFailure)
        assertEquals("Kept", store.loadRecovery("lesson").draft)
        assertTrue(runCatching { store.saveCardAnswers("lesson", mapOf("card" to "Not kept")) }.isFailure)
        storage.dropWrites = false
        storage.putString("study_recovery_v1_lesson", "{broken")
        assertTrue(runCatching { store.saveDraft("lesson", "Replacement") }.isFailure)
        assertEquals("{broken", storage.getString("study_recovery_v1_lesson"))
    }
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
        var dropWrites = false
        private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (dropWrites) return; if(value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
