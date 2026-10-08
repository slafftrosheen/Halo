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
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AgentRunRepositoryTest {
    @Test fun `legacy wearable endpoint and new accessories decode without schema migration`() {
        val legacy = decodeEndpoint(org.json.JSONObject().put("kind", "WEARABLE").put("deviceId", "managed"))
        assertEquals(com.halo.runtime.cognition.continuity.InteractionEndpoint.ManagedDevice("managed"), legacy)
        val accessory = com.halo.runtime.cognition.continuity.InteractionEndpoint.AudioAccessory("opaque", "USB", com.halo.sdk.runtime.AudioDeviceKind.USB_AUDIO)
        assertEquals(accessory, decodeEndpoint(encodeEndpoint(accessory)))
        val watch = com.halo.runtime.cognition.continuity.InteractionEndpoint.WearOs("nearby", "Watch")
        assertEquals(watch, decodeEndpoint(encodeEndpoint(watch)))
    }
    @Test fun `two endpoint responses resolve exactly once and resume only the winning run`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val store = RoomAgentRunRepository(db); val now = Instant.now(); val id = AgentRunId("input-run")
            val run = AgentRun(id, InteractionId("original"), "q", AgentRunState.WAITING_FOR_CLARIFICATION, createdAt = now, updatedAt = now)
            store.create(run)
            val input = AgentInputRequest.Clarification(AgentInputRequestId("input"), id, now, question = "Continue?", field = "confirmation", expected = ExpectedInput.Confirmation)
            store.appendStep(AgentStep.InputRequested(AgentStepId("request"), id, now, input.id, input))
            val surface = com.halo.runtime.cognition.interaction.DefaultInteractionSurfaceFactory().forInput(input, run)
            val surfaces = com.halo.runtime.cognition.interaction.InMemoryInteractionSurfaceStore(); surfaces.publish(surface)
            var resumed = 0; var moved = 0
            val handler = com.halo.runtime.cognition.interaction.DefaultInteractionResponseHandler(surfaces, store,
                resumeRun = { assertEquals(id, it); resumed++; com.halo.runtime.cognition.interaction.ResumeRequestResult.Requested },
                recordInputEndpoint = { _, _ -> moved++ })
            val response = com.halo.runtime.cognition.interaction.InteractionResponse.Confirmed(surface.id)
            val phone = async { handler.respond(com.halo.runtime.cognition.interaction.InteractionResponseSubmission(response, com.halo.runtime.cognition.continuity.InteractionEndpoint.Phone)) }
            val wearable = async { handler.respond(com.halo.runtime.cognition.interaction.InteractionResponseSubmission(response, com.halo.runtime.cognition.continuity.InteractionEndpoint.WearOs("nearby"))) }
            val results = listOf(phone.await(), wearable.await())
            assertEquals(1, results.count { it == com.halo.runtime.cognition.interaction.InteractionResponseResult.Accepted })
            assertEquals(1, results.count { it == com.halo.runtime.cognition.interaction.InteractionResponseResult.NoLongerPending })
            assertEquals(1, resumed); assertEquals(1, moved)
            assertEquals(1, store.get(id)!!.steps.filterIsInstance<AgentStep.InputResolved>().size)
        } finally { db.close() }
    }

    @Test fun `continuity journal and original route survive reopen without schema migration`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "continuity-journal.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        var db = open()
        val now = Instant.now(); val id = AgentRunId("bound-run")
        try {
            var store = RoomAgentRunRepository(db)
            store.create(AgentRun(id, InteractionId("capture"), "q", AgentRunState.EXECUTING,
                origin = com.halo.sdk.runtime.ObservationOrigin(com.halo.sdk.host.ObservationSource.HeyCyanGlasses, "exact-device"),
                createdAt = now, updatedAt = now, sourceCaptureId = "capture",
                speechOutputTarget = com.halo.sdk.runtime.SpeechOutputTarget.Device("exact-device", null, "Glasses", com.halo.sdk.runtime.AudioDeviceKind.WEARABLE)))
            store.appendStep(AgentStep.RunPrepared(AgentStepId("prepared"), id, now, AgentRunKind.INTERACTIVE, RunRecoveryPolicy.USER_RESUME,
                "conversation-a", com.halo.sdk.runtime.ResponseDelivery.SPEECH_AND_VISUAL))
            store.appendStep(AgentStep.ContinuityBound(AgentStepId("bound"), id, now, "conversation-b"))
            store.appendStep(AgentStep.InteractionHandoffRecorded(AgentStepId("handoff"), id, now,
                com.halo.runtime.cognition.continuity.InteractionEndpoint.ManagedDevice("exact-device"),
                com.halo.runtime.cognition.continuity.InteractionEndpoint.Phone,
                com.halo.runtime.cognition.continuity.InteractionHandoffReason.USER_REQUESTED))
            db.close(); db = open(); store = RoomAgentRunRepository(db)
            val restored = store.get(id)!!
            assertEquals("conversation-b", com.halo.runtime.cognition.continuity.RunContinuityResolver.conversationId(restored))
            assertEquals("exact-device", (restored.speechOutputTarget as com.halo.sdk.runtime.SpeechOutputTarget.Device).deviceId)
            assertEquals("capture", restored.sourceCaptureId)
            assertEquals(com.halo.runtime.cognition.continuity.InteractionEndpoint.Phone, com.halo.runtime.cognition.continuity.RunContinuityResolver.endpoint(restored))
            assertEquals(com.halo.sdk.runtime.SpeechOutputFallback.ALLOW_PHONE, com.halo.runtime.cognition.continuity.RunContinuityResolver.delivery(restored).fallback)
            assertEquals(26, db.openHelper.readableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun `legacy prepared step has no invented conversation ownership`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val store = RoomAgentRunRepository(db); val now = Instant.now(); val id = AgentRunId("legacy")
            store.create(AgentRun(id, InteractionId("i"), "q", AgentRunState.SUSPENDED, createdAt = now, updatedAt = now))
            store.appendStep(AgentStep.RunPrepared(AgentStepId("p"), id, now, AgentRunKind.INTERACTIVE, RunRecoveryPolicy.USER_RESUME))
            assertNull(com.halo.runtime.cognition.continuity.RunContinuityResolver.conversationId(store.get(id)!!))
        } finally { db.close() }
    }

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
