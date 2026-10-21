package com.halo.app.automation

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.agent.RoomAgentRunRepository
import com.halo.app.data.routine.*
import com.halo.app.di.tools.HaloToolDescriptors
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.attention.*
import com.halo.runtime.cognition.device.*
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.tools.*
import com.halo.runtime.kernel.resource.DefaultResourceLeaseManager
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RoutineTrialExecutionTest {
    private class Harness : AutoCloseable {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val repository = RoomRoutineRepository(db.routineDao())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = RoomAgentRunRepository(db)
        var calls = 0
        var deliveries = 0
        var publications = 0
        var allowed = true
        var fail = false
        var suspendAction = false
        val entered = CompletableDeferred<Unit>()
        val tool = object : ToolExecutor {
            val message = RoutineMessageTool()
            override fun definitions() = message.definitions()
            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
                calls++
                entered.complete(Unit)
                if (suspendAction) awaitCancellation()
                return if (fail) ToolExecutionResult.Failed(toolCall.id, RoutineMessageTool.ID, "Failure") else message.execute(toolCall)
            }
        }
        val attention = AttentionController(object : AttentionContextProvider {
            override fun snapshot() = AttentionContext()
            override val changes = emptyFlow<Unit>()
        }, DefaultAttentionPolicyEngine(), InMemoryAttentionQueue(), object : AttentionDeliveryCoordinator {
            override suspend fun deliver(request: AttentionRequest, decision: AttentionDecision): DeliveryReceipt {
                deliveries++
                error("Trials must not submit delivery")
            }
        }, scope)
        val executor = RoutineExecutor(db, repository, DefaultAgentRunCoordinator(store, scope), store,
            ToolRegistry(listOf(tool), tool.definitions().mapNotNull(HaloToolDescriptors::routineMessage)),
            { AutomationEnvironmentSnapshot(Instant.now(), batteryPercent = 80, networkAvailable = true, screenInteractive = true) },
            attention, { allowed }, { ToolResourceCoordinator(DefaultDeviceCapabilityResolver(), DefaultResourcePlanner(), DefaultResourceLeaseManager()) },
            publishScreenResult = { _, _, _ -> publications++ })
        val now = Instant.now()
        val draft = Routine(RoutineId("saved"), "Draft", false, RoutineTrigger.NetworkAvailable,
            actions = listOf(RoutineAction(RoutineMessageTool.ID, "{\"text\":\"Useful result\"}")),
            createdAt = now, updatedAt = now, delivery = RoutineDeliveryPolicy.AndroidNotification)
        override fun close() { scope.cancel(); db.close() }
    }

    @Test fun trialRecordsActualResultWithoutSavingSchedulingOrDelivering() = runBlocking {
        Harness().use { h ->
            h.repository.upsert(h.draft)
            val before = h.repository.get(h.draft.id)
            val result = requireNotNull(h.executor.executeTrial(h.draft.copy(name = "Unsaved edit"), "trial:one"))
            assertEquals(RoutineRunState.COMPLETED, result.state)
            assertEquals(1, result.actionsCompleted)
            assertEquals("TRIAL", result.source)
            assertEquals("TRIAL_RECORDED", result.deliveryOutcome)
            assertEquals("Useful result", result.reason)
            assertNotEquals(h.draft.id, result.routineId)
            assertEquals(before, h.repository.get(h.draft.id))
            assertEquals(1, h.repository.all().size)
            assertEquals(0, h.deliveries)
            assertEquals(0, h.publications)
            h.executor.executeTrial(h.draft, "trial:one")
            assertEquals(1, h.calls) // The durable occurrence claim prevents replay.
        }
    }

    @Test fun unmetConditionAndPermissionRevocationDoNotDispatch() = runBlocking {
        Harness().use { h ->
            val result = requireNotNull(h.executor.executeTrial(h.draft.copy(conditions = listOf(RoutineCondition.BatteryAbove(99))), "trial:condition"))
            assertEquals(RoutineRunState.SKIPPED, result.state)
            assertEquals(0, result.actionsCompleted)
            assertEquals(0, h.calls)
            h.allowed = false
            try { h.executor.executeTrial(h.draft, "trial:denied"); fail("Permission must reject") }
            catch (_: IllegalArgumentException) { }
            assertEquals(0, h.calls)
            assertTrue(h.repository.all().isEmpty())
        }
    }

    @Test fun failedRetryDoesNotOverwriteSuccessfulTrialEvidence() = runBlocking {
        Harness().use { h ->
            h.executor.executeTrial(h.draft, "trial:success")
            h.fail = true
            val failed = requireNotNull(h.executor.executeTrial(h.draft, "trial:failure"))
            assertEquals(RoutineRunState.FAILED, failed.state)
            assertEquals(RoutineRunState.COMPLETED, h.executor.runResult("trial:success")!!.state)
            assertEquals(0, h.deliveries)
        }
    }

    @Test fun cancellationAfterDispatchIsRecordedAsUncertainAndNeverReplayed() = runBlocking {
        Harness().use { h ->
            h.suspendAction = true
            val job = launch { h.executor.executeTrial(h.draft, "trial:cancel") }
            withTimeout(10000) { h.entered.await() }
            job.cancelAndJoin()
            val result = requireNotNull(h.executor.runResult("trial:cancel"))
            assertEquals(RoutineRunState.INTERRUPTED, result.state)
            assertEquals(0, result.actionsCompleted)
            assertNotNull(result.finishedAt)
            h.executor.executeTrial(h.draft, "trial:cancel")
            assertEquals(1, h.calls)
        }
    }
}
