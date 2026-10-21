package com.halo.app.automation

import com.halo.app.di.tools.HaloToolDescriptors
import com.halo.app.ui.screens.settings.*
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.tools.*
import com.halo.runtime.cognition.watch.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import com.halo.app.data.mcp.McpToolEntity
import com.halo.app.mcp.McpToolAdapter
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class EverydayAutomationTest {
    private val now = Instant.parse("2026-10-07T12:00:00Z")
    private val message = RoutineMessageTool()
    private fun call(text: String) = ToolCall("test", RoutineMessageTool.ID, mapOf("text" to text))

    @Test fun messageIsBoundedPlainText() = runBlocking {
        listOf("", "  \n ", "x".repeat(241)).forEach { assertTrue(message.execute(call(it)) is ToolExecutionResult.Rejected) }
        listOf("x", "x".repeat(240), "  Network is back  ", "<b>text</b>", "intent://hello", "{\"tool\":\"halo_open_app\"}").forEach {
            assertEquals(it.trim(), (message.execute(call(it)) as ToolExecutionResult.Success).output)
        }
        assertTrue(message.execute(ToolCall("test", RoutineMessageTool.ID, mapOf("text" to "ok", "action" to "anything"))) is ToolExecutionResult.Rejected)
        assertTrue(message.execute(ToolCall("test", RoutineMessageTool.ID, mapOf("text" to 12))) is ToolExecutionResult.Rejected)
    }

    @Test fun ordinaryRegistryEnforcesClosedSchema() {
        val registry = ToolRegistry(listOf(message), message.definitions().mapNotNull(HaloToolDescriptors::routineMessage))
        assertNull(registry.validate(call("Approved")))
        assertNotNull(registry.validate(ToolCall("t", RoutineMessageTool.ID, emptyMap())))
        assertNotNull(registry.validate(call("x".repeat(241))))
        assertNotNull(registry.validate(ToolCall("t", RoutineMessageTool.ID, mapOf("text" to "ok", "extra" to true))))
    }

    @Test fun onlyAuditedActionsEnterRoutineCatalogue() {
        fun definition(id: String) = ToolDefinition(id, id, id, emptyMap())
        val digest = definition("halo_notification_digest")
        val dangerous = listOf(
            HaloToolDescriptors.application(definition("halo_open_app")),
            HaloToolDescriptors.device(definition("halo_volume")),
            HaloToolDescriptors.device(definition("halo_flashlight")),
            HaloToolDescriptors.alarm(definition("halo_timer")),
            HaloToolDescriptors.notificationReply(definition("halo_reply_notification")))
        dangerous.forEach { assertNotNull(it); assertEquals(AutomationPermission.DISALLOWED, it!!.automation.permission) }
        val remote = McpToolAdapter.toDescriptor(McpToolEntity("mcp.server.tool", "server", "tool", "Remote tool",
            "Server-supplied prose", "{\"type\":\"object\"}", "SENSITIVE", true, 1L))
        assertEquals(AutomationPermission.DISALLOWED, remote.automation.permission)
        assertEquals(ToolRiskClass.SENSITIVE, remote.security.risk)
        val definitions = listOf(message.definitions().single(), digest) + dangerous.map { it!!.definition } + remote.definition
        val executor = object : ToolExecutor {
            override fun definitions() = definitions
            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = error("Catalogue must not execute")
        }
        val descriptors = listOfNotNull(HaloToolDescriptors.routineMessage(definitions[0]), HaloToolDescriptors.notificationDigest(digest)) + dangerous.filterNotNull()
        val registry = ToolRegistry(listOf(executor), descriptors + remote)
        assertEquals(setOf("halo_routine_message", "halo_notification_digest"), routineActionDescriptors(registry).map { it.name }.toSet())
        routineActionDescriptors(registry).forEach {
            assertEquals(AutomationPermission.ALLOWED_WITH_ROUTINE_GRANT, it.automation.permission)
            assertTrue(it.automation.argumentsPersistable)
            assertTrue(it.automation.backgroundCompatible)
            assertFalse(it.automation.requiresModel)
            assertEquals(ToolRiskClass.READ_ONLY, it.security.risk)
            assertEquals(ConfirmationPolicy.NONE, it.security.confirmation)
            assertEquals(ReplaySafety.SAFE, it.security.replaySafety)
        }
    }

    @Test fun templatesAreUnapprovedDrafts() {
        RoutineTemplate.entries.forEach { template ->
            val draft = template.buildDraft(now, ZoneId.of("Europe/Riga"))
            assertNull(draft.originalId)
            assertFalse(draft.routine.enabled)
            assertNull(draft.routine.grantDigest)
            assertNull(draft.routine.lastRunAt)
            assertNull(draft.routine.nextRunAt)
            assertEquals(1, draft.routine.actions.size)
            assertTrue(draft.routine.name.length in 1..120)
            assertEquals(RoutineDeliveryPolicy.AndroidNotification, draft.routine.delivery)
            if (template != RoutineTemplate.DEVICE) assertEquals(64, RoutineArguments.grant(draft.routine).length)
        }
        val device = RoutineTemplate.DEVICE.buildDraft(now).routine
        assertEquals("", (device.trigger as RoutineTrigger.DeviceConnected).deviceId)
        val digest = RoutineTemplate.DIGEST.buildDraft(now, ZoneId.of("Europe/Riga")).routine
        assertEquals(LocalTime.of(18, 0), (digest.trigger as RoutineTrigger.AtTime).localTime)
        assertEquals(480L, (RoutineArguments.decode(digest.actions.single().argumentsJson)["window_minutes"] as Number).toLong())
    }

    @Test fun duplicateClearsAuthorityAndRetainsAllConditions() {
        val source = RoutineTemplate.NETWORK.buildDraft(now).routine.copy(enabled = true, grantDigest = "abc",
            lastRunAt = now, nextRunAt = now.plusSeconds(100), minimumIntervalSeconds = 900,
            conditions = listOf(RoutineCondition.BatteryAbove(40), RoutineCondition.BatteryAbove(50), RoutineCondition.ProviderAvailable("unknown")))
        val copy = RoutineDraft.duplicate(source, now.plusSeconds(60))
        assertNotEquals(source.id, copy.routine.id)
        assertEquals("Network is back copy", copy.routine.name)
        assertFalse(copy.routine.enabled)
        assertNull(copy.routine.grantDigest)
        assertNull(copy.routine.lastRunAt)
        assertNull(copy.routine.nextRunAt)
        assertNull(copy.originalId)
        assertEquals(source.conditions, copy.routine.conditions)
        assertEquals(source.actions, copy.routine.actions)
        assertEquals(source.trigger, copy.routine.trigger)
        assertEquals(source.delivery, copy.routine.delivery)
        assertEquals(source.minimumIntervalSeconds, copy.routine.minimumIntervalSeconds)
    }

    @Test fun changingMessageRequiresAnotherReviewEvenWhenOff() {
        val routine = RoutineTemplate.NETWORK.buildDraft(now).routine
        val granted = routine.copy(grantDigest = RoutineArguments.grant(routine))
        assertEquals(RoutineUiStatus.PAUSED, routineUiStatus(granted))
        val changed = granted.copy(actions = listOf(messageAction("Different approved message")))
        assertNotEquals(granted.grantDigest, RoutineArguments.grant(changed))
        assertEquals(RoutineUiStatus.NEEDS_REVIEW, routineUiStatus(changed, needsSetup = true))
        assertEquals(RoutineUiStatus.NEEDS_SETUP, routineUiStatus(granted, needsSetup = true))
        assertEquals(RoutineUiStatus.BLOCKED, routineUiStatus(granted, blocked = true))
    }

    @Test fun runNowUsesSavedGrantAndManualOccurrenceOnly() = runBlocking {
        val repository = FakeRepository()
        val scheduler = FakeScheduler()
        val invocations = mutableListOf<Triple<RoutineId, Instant, Boolean>>()
        val commands = RoutineCommands(repository, scheduler) { id, at, manual -> invocations += Triple(id, at, manual); "Completed" }
        val draft = RoutineTemplate.NETWORK.buildDraft(now).routine
        try { commands.runNow(draft.id); fail("Unsaved draft executed") } catch (_: IllegalArgumentException) { }
        repository.upsert(draft)
        try { commands.runNow(draft.id); fail("Unreviewed definition executed") } catch (_: IllegalArgumentException) { }
        repository.upsert(draft.copy(grantDigest = RoutineArguments.grant(draft)))
        assertEquals("Completed", commands.runNow(draft.id))
        assertEquals(1, invocations.size)
        assertEquals(draft.id, invocations.single().first)
        assertTrue(invocations.single().third)
        assertTrue(scheduler.events.isEmpty())
    }

    @Test fun deletionRemovesAndCancelsWithoutExecution() = runBlocking {
        val repository = FakeRepository()
        val scheduler = FakeScheduler()
        val saved = RoutineTemplate.DIGEST.buildDraft(now).routine
        repository.upsert(saved)
        val commands = RoutineCommands(repository, scheduler) { _, _, _ -> error("Delete must not execute") }
        commands.delete(saved.id)
        assertNull(repository.get(saved.id))
        assertEquals(listOf(saved.id), scheduler.events)
    }

    @Test fun humanMappingsPreserveTruthAndExactSelection() {
        val labels = AutomationLabels(devices = mapOf("device1" to "My glasses"), apps = mapOf("com.example" to "Example"))
        assertEquals("Battery falls below 20%", RoutineTrigger.BatteryCrossedBelow(20).humanLabel())
        assertEquals("My glasses connects", RoutineTrigger.DeviceConnected("device1").humanLabel(labels))
        assertEquals("Example opens", RoutineTrigger.AppStarted("com.example").humanLabel(labels))
        assertTrue(RoutineTrigger.DeviceConnected("missing").humanLabel(labels).contains("unavailable"))
        assertEquals("Completion uncertain", RoutineRunState.INTERRUPTED.humanLabel())
        assertEquals("Conditions not met", RoutineRunState.SKIPPED.humanLabel())
        assertEquals("59 seconds", humanInterval(59))
        assertEquals("1 minute", humanInterval(60))
        assertEquals("Waiting", WatchStatus.ACTIVE.humanLabel())
        assertEquals("Internet is available", WatchCondition.NetworkAvailable.humanLabel())
        val linked = com.halo.runtime.cognition.agent.AgentRunId("linked")
        val watch = ContinuationWatch(ContinuationWatchId("watch"), null, null, WatchCondition.NetworkAvailable,
            WatchBehavior.ReconcileRun(linked), WatchStatus.ACTIVE, now, null, null)
        assertEquals(linked, watch.linkedRunId())
        assertTrue(RoutineDeliveryPolicy.WearableSpeech("device1").humanLabel(labels).contains("no phone-speaker fallback"))
        assertEquals("Notification dispatched", deliveryResultLabel("DISPATCHED"))
        assertEquals("Delivery not confirmed", deliveryResultLabel("COMPLETION_UNKNOWN"))
        val scheduled = RoutineTemplate.DIGEST.buildDraft(now).routine.copy(enabled = true, nextRunAt = now.plusSeconds(3600))
        assertTrue(scheduled.nextRunLabel().contains("Next"))
        assertEquals("Schedule pending", scheduled.copy(nextRunAt = null).nextRunLabel())
    }

    private class FakeRepository : RoutineRepository {
        val records = mutableMapOf<RoutineId, Routine>()
        override fun observeRoutines() = flowOf(records.values.toList())
        override suspend fun get(id: RoutineId) = records[id]
        override suspend fun upsert(routine: Routine) { records[routine.id] = routine }
        override suspend fun setEnabled(id: RoutineId, enabled: Boolean) { records[id] = records.getValue(id).copy(enabled = enabled) }
        override suspend fun delete(id: RoutineId) { records.remove(id) }
    }
    private class FakeScheduler : RoutineScheduler {
        val events = mutableListOf<RoutineId>()
        override suspend fun schedule(routine: Routine) = RoutineScheduleResult.EventDriven
        override suspend fun cancel(routineId: RoutineId) { events += routineId }
        override suspend fun reconcile() = Unit
    }
}
