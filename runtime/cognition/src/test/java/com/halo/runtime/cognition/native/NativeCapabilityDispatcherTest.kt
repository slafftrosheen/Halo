package com.halo.runtime.cognition.native

import com.halo.cognition.language.*
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.blackboard.Interpretation
import com.halo.runtime.cognition.skills.*
import com.halo.runtime.cognition.tools.*
import com.halo.runtime.cognition.turn.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import com.halo.sdk.runtime.skills.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NativeCapabilityDispatcherTest {
    private val commands = linkedMapOf("Set a timer for two minutes" to "halo_timer", "Open Spotify" to "halo_open_app", "Lower volume" to "halo_volume",
        "Turn off the flashlight" to "halo_flashlight", "Mute the phone" to "halo_ringer",
        "Set brightness to forty percent" to "halo_brightness", "Open Bluetooth settings" to "halo_open_settings",
        "Share hello" to "halo_share_text", "Read clipboard" to "halo_clipboard_read",
        "Copy hello" to "halo_clipboard_write", "Show battery level" to "halo_battery_status",
        "Show network status" to "halo_network_status", "Show device info" to "halo_device_info")

    @Test fun nativeMatrixHasSameExecutionAcrossChatSelectionsAndNoProviderJournal() = runTest {
        val storage = MemoryStorage()
        val store = Store()
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val calls = mutableListOf<ToolCall>()
        val executor = object : ToolExecutor {
            override fun definitions() = commands.values.map { ToolDefinition(it, it, "Native test action") }
            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
                calls += toolCall
                return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Observed execution", ActionTruth.DISPATCHED)
            }
        }
        val registry = ToolRegistry(listOf(executor), executor.definitions().map {
            ToolDescriptor(ToolId(it.id), it, ToolCategory.HOST_CONTROL, security = ToolSecurityPolicy.READ_ONLY)
        })
        val skills = DefaultSkillRegistry(storage, listOf(SkillDescriptor(SkillId("native"), "Native", "Test", SkillCategory.DEVICE, tools = commands.values.toSet())))
        val authorizer = object : ToolAuthorizer { override suspend fun authorize(toolCall: ToolCall) = ToolAuthorization.Authorized(toolCall) }
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) {}
            override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
            override fun events(): Flow<HaloEvent> = emptyFlow()
        }
        val delegate = DefaultToolTurnExecutor(registry, authorizer, null, coordinator, store, bus)
        var locked = false
        val service = SkillExecutionService(skills, registry, ToolAvailabilityResolver(registry, skills), delegate, isLocked = { locked }, coordinator = coordinator)
        val dispatcher = NativeCapabilityDispatcher(service, coordinator, store)
        for (provider in listOf("provider.litert.local", "gemini_503", "openai", "openrouter/concrete", "openrouter/free")) {
            storage.putString("active_ai_provider", provider)
            val before = calls.size
            for ((command, expected) in commands) {
                val intent = if (command == "Open Spotify") OpenIntent else requireNotNull(NativeCommandParser.parse(command))
                val entities = if (command == "Open Spotify") listOf(ApplicationEntity("Spotify", 1f)) else emptyList()
                val result = requireNotNull(dispatcher.dispatch(Interpretation(command, intent = intent, entities = entities, confidence = 1f)))
                assertEquals(expected, calls.last().functionName)
                assertEquals(AgentRunState.COMPLETED, store.get(result.runId)?.state)
                assertFalse(store.get(result.runId)!!.steps.any { it is AgentStep.ProviderTurnStarted })
                assertTrue(dispatcher.ownsRun(result.runId))
                assertNull(dispatcher.resume(result.runId)) // No completed action can be replayed.
            }
            assertEquals(commands.size, calls.size - before)
        }
        val before = calls.size
        service.check(SkillId("native")); SkillRuntimeAudit(skills, registry, service).inspect()
        assertEquals(before, calls.size)
        skills.setEnabled(SkillId("native"), false)
        val denied = dispatcher.dispatch(Interpretation("Read clipboard", intent = ClipboardReadIntent, confidence = 1f))!!
        assertEquals(before, calls.size)
        assertEquals(AgentRunState.FAILED, store.get(denied.runId)?.state)
        assertTrue(store.get(denied.runId)!!.steps.any { it is AgentStep.ToolRejected })
        skills.setEnabled(SkillId("native"), true)
        locked = true
        val unlockRequired = dispatcher.dispatch(Interpretation("Read clipboard", intent = ClipboardReadIntent, confidence = 1f))!!
        assertEquals(before, calls.size)
        assertTrue(unlockRequired.responseText().contains("REQUIRES_UNLOCK"))
    }

    private class Store : AgentRunStore {
        val rows = MutableStateFlow<List<AgentRun>>(emptyList())
        override fun observeRuns() = rows
        override suspend fun get(runId: AgentRunId) = rows.value.find { it.id == runId }
        override suspend fun create(run: AgentRun) { rows.value += run }
        override suspend fun update(run: AgentRun) { rows.value = rows.value.map { if (it.id == run.id) run else it } }
        override suspend fun appendStep(step: AgentStep): Boolean {
            val run = get(step.runId) ?: return false
            if (run.steps.any { it.id == step.id }) return false
            update(run.copy(steps = run.steps + step)); return true
        }
        override suspend fun nonTerminalRuns() = rows.value.filterNot { it.state.isTerminal }
        override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState): Boolean {
            val run = get(runId) ?: return false
            if (run.state != expectedState || run.state.isTerminal) return false
            update(run.copy(state = AgentRunState.EXECUTING)); return true
        }
        override suspend fun resolveInputIfPending(requestId: AgentInputRequestId, resolution: AgentInputResolution) = false
    }
    private class MemoryStorage : Storage {
        val data = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }
}
