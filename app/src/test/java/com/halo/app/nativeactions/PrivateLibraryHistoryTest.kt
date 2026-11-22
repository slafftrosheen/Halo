package com.halo.app.nativeactions

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.PersistedConversation
import com.halo.app.data.PersistedTurn
import com.halo.app.data.RoomConversationStore
import com.halo.app.data.agent.AgentRunEntity
import com.halo.app.data.agent.AgentStepEntity
import com.halo.app.data.memory.EpisodeEntity
import com.halo.app.data.memory.ReflectionStateEntity
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.payload.PayloadBodyEntity
import com.halo.app.data.payload.PayloadReferenceEntity
import com.halo.app.data.workspace.PrivateLibraryClaim
import com.halo.app.data.workspace.privateLibraryClaim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class PrivateLibraryHistoryTest {
    @Test fun inactiveThreadAndTerminalRunRestoreCanonicalConversationAndReflection() = runBlocking {
        withDatabase { db ->
            val thread = PersistedConversation("thread", 1, false, "Thread", 9, false)
            val turn = PersistedTurn("turn", thread.id, "Question", "Reply", 8,
                """{"runId":"run","interactionId":"action","commitId":"reply-commit"}""")
            val run = AgentRunEntity("run", "action", "Request", 3, 7, 7, "COMPLETED")
            val step = AgentStepEntity("prepare", run.id, run.interactionId, 1, "RunPrepared", 5,
                """{"conversationId":"thread"}""", null, "RECORDED", 4, null)
            val episode = EpisodeEntity("episode", run.id, """{"turnId":"turn","runId":"run"}""", 8, 2, 3, 10)
            val reflection = ReflectionStateEntity("run:run", 1, "COMPLETED", "DETERMINISTIC", 11)
            db.withTransaction {
                db.notebookDao().insertObject(WorkspaceObjectEntity(thread.id, "CONVERSATION", thread.title, 1, 9,
                    "CONVERSATION", "ACTIVE", false, "READY", "conversation:thread"))
                db.conversationDao().conversation(thread)
                db.conversationDao().insert(turn)
                db.agentRunDao().insertRun(run)
                db.agentRunDao().insertStep(step)
                db.personalMemoryDao().episode(episode)
                db.personalMemoryDao().claim(reflection)
            }
            val history = PrivateLibraryHistory(db)
            val threadRef = PrivateHistoryRef(PrivateHistoryKind.CONVERSATION, thread.id)
            val runRef = PrivateHistoryRef(PrivateHistoryKind.AGENT_RUN, run.id)
            assertEquals(listOf(runRef), history.related(threadRef))
            assertTrue(threadRef in history.related(runRef))
            assertTrue(runCatching { history.capture(threadRef) }.isFailure)

            val (savedThread, savedRun) = db.withTransaction {
                val savedThread = history.capture(threadRef, setOf(run.id))
                val savedRun = history.capture(runRef, setOf(thread.id))
                assertEquals(setOf("turn", "conversation-commit:reply-commit"), history.ownedIds(savedThread))
                assertEquals(setOf("episode"), history.ownedEpisodeIds(savedRun))
                history.withdraw(savedThread)
                history.withdraw(savedRun)
                savedThread to savedRun
            }
            assertNull(db.conversationDao().getConversation(thread.id))
            assertNull(db.agentRunDao().get(run.id))
            assertNull(db.personalMemoryDao().episodeById(episode.id))
            db.withTransaction {
                history.restore(savedThread)
                history.restore(savedRun)
            }
            assertEquals(thread, db.conversationDao().getConversation(thread.id))
            assertEquals(listOf(turn), db.conversationDao().turns(thread.id))
            assertEquals(run, db.agentRunDao().get(run.id)?.run)
            assertEquals(listOf(step), db.agentRunDao().get(run.id)?.steps)
            assertEquals(episode, db.personalMemoryDao().episodeById(episode.id))
            assertEquals(reflection, db.personalMemoryDao().reflection(reflection.id))
        }
    }

    @Test fun terminalRunWithAnOutsidePayloadOwnerCannotBeSealed() = runBlocking {
        withDatabase { db ->
            val run = AgentRunEntity("run", "action", "Request", 2, 4, 4, "COMPLETED")
            db.agentRunDao().insertRun(run)
            db.payloadDao().body(PayloadBodyEntity("hash", 42, 3))
            db.payloadDao().reference(PayloadReferenceEntity("ref", "hash", "AGENT_RUN", run.id,
                "text/plain", "PUBLIC", "USER_PROVIDED", "source", false, 3))
            val ref = PrivateHistoryRef(PrivateHistoryKind.AGENT_RUN, run.id)
            assertTrue(runCatching { PrivateLibraryHistory(db).capture(ref) }.isFailure)
            assertEquals(run, db.agentRunDao().get(run.id)?.run)
        }
    }

    @Test fun confirmedActiveSealDrainsExistingOutputAndRestoresAsSavedChat() = runBlocking {
        withDatabase { db ->
            val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val conversationStore = RoomConversationStore(db, ownerScope)
                conversationStore.awaitReady()
                val initial = requireNotNull(conversationStore.activeConversationId())
                conversationStore.append("Question", "Reply")
                val policies = mutableMapOf(initial to """{"workspace":false,"cloudImages":false}""")
                val history = PrivateLibraryHistory(db, { policies[it] }, conversationStore = conversationStore,
                    writeConversationPolicy = { id, raw -> if (raw == null) policies.remove(id) else policies[id] = raw })
                val ref = PrivateHistoryRef(PrivateHistoryKind.CONVERSATION, initial)
                val snapshot = history.capture(ref)
                val lease = conversationStore.reserveConversationExecution(initial)
                val transition = async(start = CoroutineStart.UNDISPATCHED) {
                    history.withConversationWithdrawal(setOf(initial)) {
                        db.withTransaction {
                            db.privateLibraryDao().putClaims(listOf(PrivateLibraryClaim(privateLibraryClaim(initial), "envelope")))
                            history.withdraw(snapshot)
                        }
                    }
                }
                assertTrue(runCatching { conversationStore.reserveConversationExecution(initial) }.isFailure)
                assertEquals(initial, conversationStore.activeConversationId())
                lease.release()
                transition.await()
                val replacement = requireNotNull(conversationStore.activeConversationId())
                assertTrue(initial != replacement && conversationStore.turns.value.isEmpty())
                assertNull(db.conversationDao().getConversation(initial))
                assertNull(policies[initial])
                db.withTransaction { history.restore(snapshot) }
                assertEquals(replacement, conversationStore.activeConversationId())
                assertEquals(false, db.conversationDao().getConversation(initial)?.active)
                assertEquals("Reply", db.conversationDao().turns(initial).single().assistantText)
                assertEquals("""{"workspace":false,"cloudImages":false}""", policies[initial])
            } finally { ownerScope.cancel() }
        }
    }

    @Test fun abortedActivePrivateChangeLeavesSelectionAndTranscript() = runBlocking {
        withDatabase { db ->
            val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val conversationStore = RoomConversationStore(db, ownerScope)
                conversationStore.awaitReady()
                val initial = requireNotNull(conversationStore.activeConversationId())
                conversationStore.append("Question", "Reply")
                val history = PrivateLibraryHistory(db, conversationStore = conversationStore)
                val snapshot = history.capture(PrivateHistoryRef(PrivateHistoryKind.CONVERSATION, initial))
                assertTrue(runCatching {
                    history.withConversationWithdrawal(setOf(initial)) {
                        db.withTransaction { history.withdraw(snapshot); error("Vault rejected the transaction") }
                    }
                }.isFailure)
                assertEquals(initial, conversationStore.activeConversationId())
                assertEquals(initial, db.conversationDao().active()?.id)
                assertEquals("Reply", conversationStore.turns.value.single().assistantText)
                conversationStore.reserveConversationExecution(initial).release()
            } finally { ownerScope.cancel() }
        }
    }

    private suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try { block(db) } finally { db.close() }
    }
}
