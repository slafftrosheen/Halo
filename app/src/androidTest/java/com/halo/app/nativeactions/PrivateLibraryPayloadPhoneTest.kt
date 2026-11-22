package com.halo.app.nativeactions

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.halo.app.automation.AndroidRoutineScheduler
import com.halo.app.automation.RoutineWorker
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
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateLibraryPayloadPhoneTest {
    @Test fun linkedChatAndRoutineKeepExactResultAcrossDeviceLockAndRestore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = File(context.cacheDir, "private-result-phone-${id()}").apply { check(mkdir()) }
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val session = VaultSession(ByteArray(32)) { }
        val routineId = id()
        val work = WorkManager.getInstance(context)
        val workName = "halo.routine.$routineId"
        try {
            val chat = PersistedConversation(id(), 1, false, "QA receipt chat", 10, false)
            val userRun = id()
            val routineRun = "routine:${id()}"
            val chatOwner = PayloadOwner(PayloadOwnerKind.AGENT_RUN, userRun)
            val routineOwner = PayloadOwner(PayloadOwnerKind.AGENT_RUN, routineRun)
            val payloads = FileContentPayloadStore(db, File(files, "public"))
            val library = PrivateLibraryStore(db, history = PrivateLibraryHistory(db),
                automation = PrivateLibraryAutomation(db, AndroidRoutineScheduler(context, RoomRoutineRepository(db.routineDao())) { }),
                toolPayloads = PrivateLibraryPayloads(db, payloads, File(files, "private")))
            db.notebookDao().insertObject(WorkspaceObjectEntity(chat.id, "CONVERSATION", chat.title, 1, 10,
                "CONVERSATION", "ACTIVE", false, "READY", "conversation:${chat.id}"))
            db.conversationDao().conversation(chat)
            db.agentRunDao().insertRun(AgentRunEntity(userRun, userRun, "QA chat run", 2, 8, 8, "COMPLETED"))
            db.agentRunDao().insertRun(AgentRunEntity(routineRun, routineRun, "QA routine run", 2, 8, 8, "COMPLETED"))
            db.routineDao().insertRestored(RoutineEntity(routineId, "QA routine", true,
                "{}", "[]", "[]", createdAt = 2, updatedAt = 8, lastRunAt = 8, nextRunAt = 20, grantDigest = "qa"))
            db.routineDao().insertRun(RoutineRunEntity(routineRun, routineId, routineRun, 7, "COMPLETED", finishedAt = 8))
            val content = "QA multi-chunk result\n".repeat(30_000).toByteArray()
            val stored = payloads.put(chatOwner, content, PayloadClassification.TOOL_RESULT,
                ContextTrust.EXTERNAL_UNTRUSTED, "qa fixture", false) as PayloadOutcome.Value
            val ref = stored.value
            assertTrue(payloads.retain(chatOwner, ref.id, routineOwner))
            val pointer = JSONObject().put("payloadRefId", ref.id).put("payloadSha256", ref.sha256)
            db.conversationDao().insert(PersistedTurn(id(), chat.id, "QA question", "QA reply", 9,
                JSONObject(pointer.toString()).put("runId", userRun).toString()))
            db.agentRunDao().insertStep(AgentStepEntity(id(), routineRun, routineRun, 1, "ToolExecutionClaimed", 5,
                """{"invocationId":"qa-invocation","toolCallId":"qa-call","toolId":"qa-tool"}""",
                "qa-claim", "AUTHORIZED", 7, null))
            db.agentRunDao().insertStep(AgentStepEntity(id(), routineRun, routineRun, 2, "ToolOutcomeRecorded", 5,
                JSONObject(pointer.toString()).put("invocationId", "qa-invocation").put("toolCallId", "qa-call")
                    .put("toolId", "qa-tool").put("actionTruth", "CONFIRMED").toString(), null, "CONFIRMED", 8, 8))
            val job = OneTimeWorkRequestBuilder<RoutineWorker>().setInitialDelay(1, TimeUnit.DAYS).build()
            work.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, job).result.get(15, TimeUnit.SECONDS)

            val review = library.prepare(session, PrivateLibraryTarget.Conversation(chat.id, chat.title, chat.updatedAt))
            assertTrue("QA routine" in review.titles && review.includesToolResults)
            library.lock(session, review)
            assertNull(db.conversationDao().getConversation(chat.id))
            assertNull(db.routineDao().get(routineId))
            assertNull(payloads.reference(chatOwner, ref.id))
            assertNull(payloads.reference(routineOwner, ref.id))
            assertFalse(payloads.body(ref.sha256).exists())
            assertEquals(WorkInfo.State.CANCELLED, work.getWorkInfoById(job.id).get(15, TimeUnit.SECONDS)?.state)

            val envelope = library.list(session).entries.single()
            assertTrue(library.view(session, envelope.id).items.any { it.note.orEmpty().contains("QA multi-chunk result") })
            library.restore(session, envelope.id)
            assertEquals("QA reply", db.conversationDao().turns(chat.id).single().assistantText)
            val routine = requireNotNull(db.routineDao().get(routineId))
            assertFalse(routine.enabled)
            assertNull(routine.grantDigest)
            assertNull(routine.nextRunAt)
            assertEquals(ref, payloads.reference(chatOwner, ref.id))
            assertEquals(ref, payloads.reference(routineOwner, ref.id))
            assertArrayEquals(content, payloads.body(ref.sha256).readBytes())
            assertTrue(library.list(session).entries.isEmpty())
        } finally {
            session.close()
            db.close()
            work.cancelUniqueWork(workName).result.get(15, TimeUnit.SECONDS)
            files.deleteRecursively()
        }
    }

    private fun id() = UUID.randomUUID().toString()
}
