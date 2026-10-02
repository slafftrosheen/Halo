package com.halo.app.data.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.blackboard.InteractionOrigin
import com.halo.sdk.runtime.Source
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

    @Test fun `dogfood005 journal steps survive reopen without interrupting the run`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "agent-dogfood005.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        val now = Instant.ofEpochMilli(2000)
        val run = AgentRun(
            AgentRunId("dogfood-run"), InteractionId("dogfood-interaction"), "Native volume",
            AgentRunState.EXECUTING, createdAt = now, updatedAt = now, startedAt = now,
        )
        var db = open()
        try {
            var store = RoomAgentRunRepository(db)
            store.create(run)
            val authority = DirectUserCommandAuthority(
                interactionId = run.interactionId,
                toolId = "halo_volume",
                argumentsDigest = "digest",
                origin = InteractionOrigin(Source.USER, sourceDeviceId = "phone"),
                issuedAt = now,
            )
            assertTrue(store.appendStep(AgentStep.DirectCommandAuthorityRecorded(
                AgentStepId("authority"), run.id, now, authority,
            )))
            assertTrue(store.appendStep(AgentStep.ExternalConditionRegistered(
                AgentStepId("watch-registered"), run.id, now, "watch", "NetworkAvailable",
                targetId = "network", presentationText = "Internet is back",
            )))
            assertTrue(store.appendStep(AgentStep.ExternalConditionSatisfied(
                AgentStepId("watch-satisfied"), run.id, now, "watch", "NetworkAvailable", "network",
            )))
            assertTrue(store.appendStep(AgentStep.ExternalConditionExpired(
                AgentStepId("watch-expired"), run.id, now, "expired-watch", "ProviderAvailable",
            )))
            assertTrue(store.appendStep(AgentStep.ExternalConditionCancelled(
                AgentStepId("watch-cancelled"), run.id, now, "cancelled-watch", "user",
            )))

            db.close()
            db = open()
            store = RoomAgentRunRepository(db)
            val restored = requireNotNull(store.get(run.id))
            assertEquals(AgentRunState.EXECUTING, restored.state)
            assertEquals(
                listOf(
                    AgentStep.DirectCommandAuthorityRecorded::class,
                    AgentStep.ExternalConditionRegistered::class,
                    AgentStep.ExternalConditionSatisfied::class,
                    AgentStep.ExternalConditionExpired::class,
                    AgentStep.ExternalConditionCancelled::class,
                ),
                restored.steps.map { it::class },
            )
            assertFalse(restored.steps.filterIsInstance<AgentStep.ToolFailed>().any { it.toolId == "legacy" })
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
