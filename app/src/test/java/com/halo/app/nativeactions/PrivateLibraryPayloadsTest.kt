package com.halo.app.nativeactions

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.halo.app.automation.AndroidRoutineScheduler
import com.halo.app.data.AppDatabase
import com.halo.app.data.PersistedConversation
import com.halo.app.data.PersistedTurn
import com.halo.app.data.agent.AgentRunEntity
import com.halo.app.data.agent.AgentStepEntity
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.payload.FileContentPayloadStore
import com.halo.app.data.routine.RoomRoutineRepository
import com.halo.app.data.routine.RoutineEntity
import com.halo.app.data.routine.RoutineRunEntity
import com.halo.runtime.cognition.context.ContextTrust
import com.halo.runtime.cognition.context.PayloadClassification
import com.halo.runtime.cognition.context.PayloadOutcome
import com.halo.runtime.cognition.context.PayloadOwner
import com.halo.runtime.cognition.context.PayloadOwnerKind
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class PrivateLibraryPayloadsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun chatAndRoutineLockRestoreExactToolResultAliasesAndPreserveIndependentCopies() = runBlocking {
        withDatabase { db ->
            val chat = PersistedConversation(id(), 1, false, "Receipt chat", 10, false)
            db.conversationDao().conversation(chat)
            db.notebookDao().insertObject(WorkspaceObjectEntity(chat.id, "CONVERSATION", chat.title, 1, 10,
                "CONVERSATION", "ACTIVE", false, "READY", "conversation:${chat.id}"))
            val primary = completedRun(db, "Primary tool run")
            val linked = completedRun(db, "Linked tool run")
            val independent = completedRun(db, "Independent run")
            val public = FileContentPayloadStore(db, folder.newFolder("public"))
            val private = PrivateLibraryPayloads(db, public, folder.newFolder("private"))
            val context = ApplicationProvider.getApplicationContext<Context>()
            val scheduler = AndroidRoutineScheduler(context, RoomRoutineRepository(db.routineDao())) { }
            val library = PrivateLibraryStore(db, history = PrivateLibraryHistory(db), toolPayloads = private,
                automation = PrivateLibraryAutomation(db, scheduler))
            val routineId = id()
            db.routineDao().insertRestored(RoutineEntity(routineId, "Linked automation", true, "{}", "[]", "[]",
                createdAt = 2, updatedAt = 8, lastRunAt = 8, nextRunAt = 20, grantDigest = "test grant"))
            db.routineDao().insertRun(RoutineRunEntity(linked.id, routineId, linked.id, 7, "COMPLETED", finishedAt = 8))
            val session = VaultSession(ByteArray(32)) { }
            val sharedBytes = "Shared result".toByteArray()
            val shared = public.putResult(primary, sharedBytes)
            assertTrue(public.retain(primary, shared.id, linked))
            val other = public.putResult(independent, sharedBytes)
            val exclusive = public.putResult(primary, "Long result\n".repeat(40_000).toByteArray())
            val metadata = JSONObject().put("runId", primary.id).put("payloadRefId", exclusive.id)
                .put("payloadSha256", exclusive.sha256).toString()
            db.conversationDao().insert(PersistedTurn(id(), chat.id, "Find receipt", "I found it", 9, metadata))
            val run = requireNotNull(db.agentRunDao().get(primary.id)).run
            db.agentRunDao().insertStep(AgentStepEntity(id(), run.id, run.interactionId, 1, "ResponseProduced", 5,
                JSONObject().put("text", "Stored result").put("payloadRefId", shared.id).toString(), null, "RECORDED", 9, 9))

            try {
                val review = library.prepare(session, PrivateLibraryTarget.Conversation(chat.id, chat.title, chat.updatedAt))
                assertTrue(review.includesToolResults)
                assertTrue("Linked tool run" in review.titles)
                assertTrue("Linked automation" in review.titles)
                assertFalse("Independent run" in review.titles)
                library.lock(session, review)
                val envelope = library.list(session).entries.single()
                assertNull(public.reference(primary, shared.id))
                assertNull(public.reference(linked, shared.id))
                assertNull(public.reference(primary, exclusive.id))
                assertNull(db.routineDao().get(routineId))
                assertFalse(public.body(exclusive.sha256).exists())
                assertEquals(other, public.reference(independent, other.id))
                assertTrue(library.view(session, envelope.id).items.any { it.note.orEmpty().contains("Long result") })
                assertTrue(public.put(primary, sharedBytes, PayloadClassification.TOOL_RESULT,
                    ContextTrust.EXTERNAL_UNTRUSTED, "late writer", false) is PayloadOutcome.Failed)

                library.restore(session, envelope.id)
                assertEquals(shared, public.reference(primary, shared.id))
                assertEquals(shared, public.reference(linked, shared.id))
                assertEquals(exclusive, public.reference(primary, exclusive.id))
                assertEquals(other, public.reference(independent, other.id))
                val restoredRoutine = requireNotNull(db.routineDao().get(routineId))
                assertFalse(restoredRoutine.enabled)
                assertNull(restoredRoutine.grantDigest)
                assertNull(restoredRoutine.nextRunAt)
                assertEquals(metadata, db.conversationDao().turns(chat.id).single().metadata)
                assertTrue(library.list(session).entries.isEmpty())
            } finally { session.close() }
        }
    }

    @Test fun interruptedLockRestoresCanonicalBytesFromTheMoveJournal() = runBlocking {
        withDatabase { db ->
            val owner = completedRun(db, "Tool run")
            val public = FileContentPayloadStore(db, folder.newFolder("public"))
            val backing = folder.newFolder("private")
            val adapter = PrivateLibraryPayloads(db, public, backing)
            val session = VaultSession(ByteArray(32)) { }
            val result = public.putResult(owner, "Exact tool output".toByteArray())
            try {
                adapter.withAccess {
                    assertTrue(runCatching {
                        db.withTransaction {
                            val entries = adapter.capture(setOf(owner))
                            adapter.withdraw(session, id(), entries)
                            assertFalse(public.body(result.sha256).exists())
                            error("Interrupted before Room committed")
                        }
                    }.isFailure)
                }
                PrivateLibraryPayloads(db, public, backing).recover()
                assertTrue(public.body(result.sha256).exists())
                assertEquals(result, public.reference(owner, result.id))
                assertTrue(backing.listFiles().orEmpty().isEmpty())
            } finally { session.close() }
        }
    }

    private suspend fun FileContentPayloadStore.putResult(owner: PayloadOwner, bytes: ByteArray) =
        (put(owner, bytes, PayloadClassification.TOOL_RESULT, ContextTrust.EXTERNAL_UNTRUSTED,
            "original tool", false) as PayloadOutcome.Value).value

    private suspend fun completedRun(db: AppDatabase, title: String): PayloadOwner {
        val runId = id()
        db.agentRunDao().insertRun(AgentRunEntity(runId, id(), title, 2, 8, 8, "COMPLETED"))
        return PayloadOwner(PayloadOwnerKind.AGENT_RUN, runId)
    }

    private fun id(): String = UUID.randomUUID().toString()

    private suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try { block(db) } finally { db.close() }
    }
}
