package com.halo.app.data.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AgentRunRepositoryTest {
    @Test fun `ordered history identity and failure survive database reopen`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "agent-restart.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        val now = Instant.ofEpochMilli(1000)
        val run = AgentRun(AgentRunId("run"), InteractionId("interaction"), "Alarm", AgentRunState.EXECUTING,
            createdAt = now, updatedAt = now, startedAt = now)
        val dispatch = AgentStep.ToolDispatched(AgentStepId("dispatch"), run.id, now, "call", "alarm", "logical-action")
        var db = open()
        try {
            var store = RoomAgentRunRepository(db)
            store.create(run)
            assertTrue(store.appendStep(dispatch))
            assertFalse(store.appendStep(dispatch.copy(id = AgentStepId("duplicate"))))
            store.appendStep(AgentStep.ToolFailed(AgentStepId("failure"), run.id, now, "call", "alarm", "denied"))
            store.update(requireNotNull(store.get(run.id)).copy(state = AgentRunState.FAILED, completedAt = now, failureReason = "denied"))
            db.close()
            db = open()
            store = RoomAgentRunRepository(db)
            val restored = store.observeRuns().first().single()
            assertEquals(run.interactionId, restored.interactionId)
            assertEquals(listOf("dispatch", "failure"), restored.steps.map { it.id.value })
            assertEquals("denied", restored.failureReason)
            assertEquals(AgentRunState.FAILED, restored.state)
            assertFalse(restored.hasUnknownAction)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
