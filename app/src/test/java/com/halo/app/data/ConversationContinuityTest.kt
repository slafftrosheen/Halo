package com.halo.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationContinuityTest {
    @Test fun startupDoesNotRestoreTrashedConversationOrItsMessages() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val original = RoomConversationStore(db, seedScope); original.awaitReady()
            val old = original.activeConversationId()!!
            original.append("old question", "old answer")
            seedScope.cancel()
            db.notebookDao().setLifecycle(old, "TRASHED", 1L)
            // Reproduce the persisted stale active flag seen on the phone.
            db.conversationDao().deactivate()
            db.conversationDao().conversation(db.conversationDao().getConversation(old)!!.copy(active = true))
            val restored = RoomConversationStore(db, backgroundScope); restored.awaitReady()
            val current = restored.activeConversationId()!!
            assertNotEquals(old, current)
            assertTrue(restored.isConversationAvailable(current))
            assertTrue(restored.turns.value.isEmpty())
            restored.append("new question", "new answer")
            assertEquals("old answer", db.conversationDao().turns(old).single().assistantText)
            assertEquals("TRASHED", db.notebookDao().getWorkspaceObject(old)!!.lifecycle)
            assertFalse(restored.activateExistingConversation(old))
        } finally { db.close() }
    }

    @Test fun trashingOpenConversationMovesToFreshDestinationWithoutRestoringTrash() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val store = RoomConversationStore(db, backgroundScope); store.awaitReady()
            val old = store.activeConversationId()!!
            store.append("old question", "old answer")
            db.notebookDao().setLifecycle(old, "TRASHED", 1L)
            val current = withContext(Dispatchers.IO) {
                withTimeout(10_000) { store.activeConversation.first { it != null && it != old } }!!
            }
            assertTrue(store.isConversationAvailable(current))
            assertFalse(store.isConversationAvailable(old))
            store.append("new question", "new answer")
            assertEquals(current, db.conversationDao().active()!!.id)
            assertEquals("old answer", db.conversationDao().turns(old).single().assistantText)
            assertEquals(listOf(current), db.conversationDao().observeConversations().first().map { it.id })
        } finally { db.close() }
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
