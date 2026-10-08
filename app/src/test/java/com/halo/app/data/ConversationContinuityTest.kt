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
            assertFalse(store.activateExistingConversation("missing")); assertNull(db.conversationDao().getConversation("missing"))
            db.conversationDao().archive(a, System.currentTimeMillis())
            assertFalse(store.isConversationAvailable(a)); assertFalse(store.activateExistingConversation(a))
            assertTrue(runCatching { store.appendIfAbsent(a, "new", "q", "a") }.isFailure)
        } finally { db.close() }
    }
}
