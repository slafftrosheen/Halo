package com.halo.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.di.tools.host.SavedChatTools
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SavedChatToolsTest {
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
        AppDatabase::class.java).allowMainThreadQueries().build()

    @Test fun exactVersionDetectsSameTimestampContentChangeAndPreservesRecoverableTurns() = runTest {
        val db = database()
        try {
            val store = RoomConversationStore(db, backgroundScope); store.awaitReady()
            val id = requireNotNull(store.activeConversationId())
            store.append("literal %_ query", "original", mapOf("privateMetadata" to "never-return"))
            val before = store.savedChat(id, "ACTIVE")
            store.newConversation(); val active = store.activeConversationId()
            val original = db.conversationDao().turns(id).single()
            // Bypasses a timestamp-based revision shortcut: the same row/time has new bytes.
            db.conversationDao().insert(original.copy(assistantText = "changed"))
            val changed = store.savedChat(id, "ACTIVE")
            assertNotEquals(before.version, changed.version)
            assertTrue(runCatching { store.trashSavedConversation(id, before.version) }.exceptionOrNull() is SavedChatAdmissionException)
            assertTrue(store.isConversationAvailable(id))
            assertEquals(listOf(id), store.listSavedChats("ACTIVE", "%_", 0, 20).chats.map { it.id })
            assertTrue(store.trashSavedConversation(id, changed.version))
            assertEquals(active, store.activeConversationId())
            assertTrue(store.listSavedChats("ACTIVE", "", 0, 20).chats.none { it.id == id })
            val trashed = store.savedChat(id, "TRASHED")
            assertEquals("changed", trashed.turns.single().assistantText)
            assertNotEquals(changed.version, trashed.version)
            assertTrue(runCatching { store.restoreSavedConversation(id, changed.version) }.exceptionOrNull() is SavedChatAdmissionException)
            assertTrue(store.restoreSavedConversation(id, trashed.version))
            assertEquals(active, store.activeConversationId())
            assertEquals(original.copy(assistantText = "changed"), db.conversationDao().turns(id).single())
        } finally {
            // Eager Room stateIn collectors must stop before their database closes.
            withContext(NonCancellable) {
                try { backgroundScope.coroutineContext.job.cancelAndJoin() }
                finally { db.close() }
            }
        }
    }

    @Test fun activeWriterAndRevokedAdmissionNeverChangeLifecycle() = runTest {
        val db = database()
        try {
            val store = RoomConversationStore(db, backgroundScope); store.awaitReady()
            val id = requireNotNull(store.activeConversationId()); store.append("question", "answer")
            val saved = store.savedChat(id, "ACTIVE")
            assertTrue(runCatching { store.trashSavedConversation(id, saved.version) }.exceptionOrNull() is SavedChatAdmissionException)
            assertEquals(id, store.activeConversationId())
            store.newConversation()
            assertTrue(runCatching { store.trashSavedConversation(id, saved.version) { error("Access revoked") } }.isFailure)
            assertTrue(store.isConversationAvailable(id))
            assertEquals(saved.version, store.savedChat(id, "ACTIVE").version)
        } finally {
            // Eager Room stateIn collectors must stop before their database closes.
            withContext(NonCancellable) {
                try { backgroundScope.coroutineContext.job.cancelAndJoin() }
                finally { db.close() }
            }
        }
    }

    @Test fun boundedTranscriptPagesRequireVersionAndNeverReturnTurnMetadata() = runTest {
        val db = database()
        try {
            val store = RoomConversationStore(db, backgroundScope); store.awaitReady()
            val id = requireNotNull(store.activeConversationId())
            repeat(3) { store.append("question $it", "x".repeat(1400), mapOf("secret" to "PRIVATE_METADATA")) }
            val first = store.savedChat(id, "ACTIVE", limit = 1)
            assertTrue(first.turns.single().truncated); assertEquals(1000, first.turns.single().assistantText.length)
            assertTrue(runCatching { store.savedChat(id, "ACTIVE", offset = 1, limit = 1) }.isFailure)
            val second = store.savedChat(id, "ACTIVE", first.version, offset = 1, limit = 1)
            assertNotEquals(first.turns.single().id, second.turns.single().id)
            val tools = SavedChatTools(store, { true })
            val result = withContext(RunToolScope(AgentRunId("saved-read"))) {
                tools.execute(ToolCall("get", SavedChatTools.GET, mapOf("conversationId" to id, "lifecycle" to "ACTIVE")))
            } as ToolExecutionResult.Success
            assertFalse(result.output.contains("PRIVATE_METADATA"))
            assertFalse(result.output.contains("secret"))
            assertEquals(3, JSONObject(result.output).getJSONArray("turns").length())
        } finally {
            // Eager Room stateIn collectors must stop before their database closes.
            withContext(NonCancellable) {
                try { backgroundScope.coroutineContext.job.cancelAndJoin() }
                finally { db.close() }
            }
        }
    }

    @Test fun typedCommandsDenyRemoteAndMissingAuthorityThenReadBackExactLifecycle() = runTest {
        val db = database()
        try {
            val store = RoomConversationStore(db, backgroundScope); store.awaitReady()
            val id = requireNotNull(store.activeConversationId()); store.append("saved subject", "private body")
            val snapshot = store.savedChat(id, "ACTIVE"); store.newConversation()
            val writer = store.activeConversationId()
            var permitted = true
            var opened: Pair<String, String>? = null
            val tools = SavedChatTools(store, { permitted }) { openedId, version -> opened = openedId to version; true }
            val get = ToolCall("get", SavedChatTools.GET, mapOf("conversationId" to id, "lifecycle" to "ACTIVE"))
            assertTrue(tools.execute(get) is ToolExecutionResult.Rejected)
            assertTrue(withContext(RunToolScope(AgentRunId("remote"), false)) { tools.execute(get) } is ToolExecutionResult.Rejected)
            withContext(RunToolScope(AgentRunId("local"))) {
                permitted = false; assertTrue(tools.execute(get) is ToolExecutionResult.Rejected); permitted = true
                val open = tools.execute(ToolCall("open", SavedChatTools.OPEN, mapOf("conversationId" to id, "version" to snapshot.version))) as ToolExecutionResult.Success
                assertEquals(ActionTruth.DISPATCHED, open.actionTruth); assertFalse(JSONObject(open.output).getBoolean("continued"))
                assertEquals(id to snapshot.version, opened); assertEquals(writer, store.activeConversationId())
                val trash = ToolCall("trash", SavedChatTools.TRASH, mapOf("conversationId" to id, "version" to snapshot.version))
                assertTrue(tools.execute(trash) is ToolExecutionResult.Rejected)
                val result = tools.execute(trash, ToolExecutionContext(directUserAuthority = true)) as ToolExecutionResult.Success
                assertEquals(ActionTruth.CONFIRMED, result.actionTruth)
                assertEquals("TRASHED", JSONObject(result.output).getString("lifecycle"))
                assertEquals(writer, store.activeConversationId())
                val stale = tools.execute(trash, ToolExecutionContext(directUserAuthority = true))
                assertTrue(stale is ToolExecutionResult.Rejected)
                assertEquals(1, db.conversationDao().turns(id).size)
            }
        } finally {
            // Eager Room stateIn collectors must stop before their database closes.
            withContext(NonCancellable) {
                try { backgroundScope.coroutineContext.job.cancelAndJoin() }
                finally { db.close() }
            }
        }
    }
}
