package com.halo.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationContinuityTest {
    @Test fun crossChatMemoryUsesNewestLiveUserEvidenceAndDropsTrashedSource() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val store = RoomConversationStore(db, backgroundScope)
            store.awaitReady()
            val first = requireNotNull(store.activeConversationId())
            store.appendIfAbsent(first, "first-units", "I prefer Celsius.", "Got it.")
            store.newConversation()
            val second = requireNotNull(store.activeConversationId())
            val override = store.appendIfAbsent(second, "new-units", "I now prefer Fahrenheit.", "Got it.")
            val oldTime = System.currentTimeMillis() - 60_000
            val dao = db.conversationDao()
            dao.insert(dao.turns(first).single().copy(timestamp = oldTime))
            store.newConversation()
            val active = requireNotNull(store.activeConversationId())
            val history = RoomConversationHistoryRetriever(db) { id, local ->
                local && db.notebookDao().getWorkspaceObject(id)?.lifecycle == "ACTIVE"
            }
            val hit = history.referenceOtherChats(active, "What's the forecast?", true).single()
            assertEquals(override.id, hit.turnId)
            assertTrue(hit.snippet.contains("Fahrenheit"))
            assertTrue(history.stillReferenceable(hit, true))
            assertTrue(history.referenceOtherChats(active, "What's the forecast?", false).isEmpty())
            dao.archive(second, System.currentTimeMillis())
            assertFalse(history.stillReferenceable(hit, true))
            val remaining = history.referenceOtherChats(active, "What is the temperature?", true).single()
            assertEquals(first, remaining.conversationId)
            assertTrue(remaining.snippet.contains("Celsius"))
            assertNull(spokenPreference("I prefer brief answers in this chat."))
        } finally {
            db.close()
        }
    }

    @Test fun startingNewChatKeepsPriorMessagesAndClearingOnlyEmptiesTheChosenChat() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val store = RoomConversationStore(db, backgroundScope)
            store.awaitReady()
            val first = requireNotNull(store.activeConversationId())
            val original = store.appendIfAbsent(first, "first-reply", "First question", "First reply")

            store.newConversation()
            val second = requireNotNull(store.activeConversationId())
            assertNotEquals(first, second)
            assertTrue(store.turns.value.isEmpty())
            assertEquals(listOf(original.id), store.turnsForConversation(first).map { it.id })
            val other = store.appendIfAbsent(second, "second-reply", "Second question", "Second reply")

            assertTrue(store.activateExistingConversation(first))
            store.clear(first)
            assertEquals(first, store.activeConversationId())
            assertTrue(store.turns.value.isEmpty())
            assertEquals(listOf(other.id), store.turnsForConversation(second).map { it.id })
        } finally {
            db.close()
        }
    }

    @Test fun targetedCommitAndGlobalExactDedupNeverStealActiveConversation() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val store = RoomConversationStore(db, backgroundScope); store.awaitReady()
            val a = store.activeConversationId()!!
            store.newConversation(); val b = store.activeConversationId()!!
            val first = store.appendIfAbsent(a, "run:%_quoted\"", "question A", "answer A")
            assertEquals(a, first.conversationId); assertTrue(store.turns.value.isEmpty())
            val duplicate = store.appendIfAbsent(b, "run:%_quoted\"", "wrong", "wrong")
            assertEquals(first.id, duplicate.id); assertEquals(b, store.activeConversationId())
            assertEquals(b, db.conversationDao().active()!!.id)
            val current = store.appendIfAbsent(b, "current", "question B", "answer B")
            store.clear(a)
            assertTrue(store.turnsForConversation(a).isEmpty())
            assertEquals(listOf(current.id), store.turns.value.map { it.id })
            assertEquals(b, store.activeConversation.value)
            assertFalse(store.activateExistingConversation("missing")); assertNull(db.conversationDao().getConversation("missing"))
            db.conversationDao().archive(a, System.currentTimeMillis())
            assertFalse(store.isConversationAvailable(a)); assertFalse(store.activateExistingConversation(a))
            assertTrue(runCatching { store.appendIfAbsent(a, "new", "q", "a") }.isFailure)
        } finally { db.close() }
    }
}
