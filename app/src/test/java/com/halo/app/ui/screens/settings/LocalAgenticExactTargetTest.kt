package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.*
import com.halo.providers.litert.runtime.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.halo.engines.ai.DefaultAiProviderRouter
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import com.halo.runtime.cognition.context.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalAgenticExactTargetTest {
    private class MemoryStorage : Storage {
        val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
    private class Provider(val protocol: ToolCallProtocol) : AiInferenceProvider, ModelInputCapabilityReporting, ToolProtocolReporting {
        override val id = "provider.litert.local"
        override val displayName = "Local"
        override val isLocal = true
        override val status = MutableStateFlow(ProviderStatus.READY)
        val requests = mutableListOf<AiRequest>()
        var secondCall = false
        var mismatched = false
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.TOOLS)
        override suspend fun capabilitiesForModel(modelId: String) = getCapabilities()
        override suspend fun toolCallProtocol(modelId: String) = protocol
        override suspend fun listModels() = listOf("A", "B").map { AiModel(it, it, id, getCapabilities(), 4096, false, supportsTools = true, executionLocation = AiExecutionLocation.ON_DEVICE) }
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            requests += request
            assertEquals("B", request.modelId)
            assertEquals(0.0, request.temperatureOverride!!, 0.0)
            assertTrue(request.maxOutputTokens!! <= 192)
            val call = ToolCall("synthetic-call", "halo_probe", mapOf("value" to "HALO_OK"))
            if (request.turnKind == ProviderTurnKind.TOOL_CONTINUATION) {
                assertEquals(listOf(Role.USER, Role.ASSISTANT, Role.TOOL), context.conversation.map { it.role })
                assertEquals(call.id, context.conversation.last().toolCallId)
                assertFalse(request.appendTextAsUserMessage)
                return AiResponse("Done", providerId = id, modelId = if (mismatched) "A" else "B", toolCalls = if (secondCall) listOf(call) else emptyList())
            }
            return AiResponse("", providerId = id, modelId = "B", toolCalls = listOf(call))
        }
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
    }
    private suspend fun exact(protocol: ToolCallProtocol, extraCall: Boolean = false, mismatch: Boolean = false): List<ProbePhaseResult> {
        val storage = MemoryStorage(); val provider = Provider(protocol).apply { secondCall = extraCall; mismatched = mismatch }
        val router = DefaultAiProviderRouter(listOf(provider), storage, defaultInferenceProviderId = provider.id, defaultInferenceModelId = "A")
        router.setRoleBinding(AiRoleBinding(AiModelRole.AGENTIC, provider.id, "A", AiRoleSelection.EXPLICIT))
        router.setRoleBinding(AiRoleBinding(AiModelRole.FAST, provider.id, "B", AiRoleSelection.EXPLICIT))
        router.setRoleBinding(AiRoleBinding(AiModelRole.REASONING, provider.id, "A", AiRoleSelection.EXPLICIT))
        router.setRoleBinding(AiRoleBinding(AiModelRole.VISION, null, null, AiRoleSelection.AUTOMATIC))
        val settings = storage.values.toMap(); val bindings = router.roleBindings.value.toMap()
        val selection = router.selectedProviderId.value to router.selectedModelId.value
        val routing = router.routingPolicy.value
        val health = LocalInferenceRuntimeHealthTracker()
        repeat(5) { health.accept(LocalInferenceObservation(provider.id, "B", "CPU", ttftMs = 3000, success = true, coldStart = false)) }
        val before = health.snapshot(provider.id, "B", "CPU")
        val runner = LocalAgenticProbeRunner(router)
        runner.runProbe(AiModelRef(provider.id, "B"))
        assertEquals(settings, storage.values); assertEquals(bindings, router.roleBindings.value)
        assertEquals(selection, router.selectedProviderId.value to router.selectedModelId.value); assertEquals(routing, router.routingPolicy.value)
        assertEquals(before, health.snapshot(provider.id, "B", "CPU"))
        assertEquals(listOf("B", "B"), provider.requests.map { it.modelId })
        return runner.phases.value
    }
    @Test fun completeProviderSuiteKeepsConfigurationAndLiveHealthAndReusesPromptParser() = runTest {
        val storage = MemoryStorage()
        storage.putString("active_ai_provider", "provider.litert.local")
        storage.putString("active_ai_model", "A")
        val descriptors = listOf("A", "B").map { LocalModelDescriptor(it, it, it, 100, 0, "sha-$it", 1024, false, false, LocalModelValidation.VERIFIED_ARTIFACT) }
        val inventory = object : LocalModelInventoryRepository {
            override val inventoryState = MutableStateFlow<LocalModelInventoryState>(LocalModelInventoryState.Ready(descriptors, 0))
            override suspend fun refresh(reason: ModelInventoryRefreshReason) = Unit
            override suspend fun resolveModel(modelId: String) = descriptors.find { it.id == modelId }
            override fun invalidate(reason: String) = Unit
        }
        val runtime = object : LocalInferenceRuntime {
            override val format = LocalModelFormat.LITERT_LM
            override val state = MutableStateFlow(LocalModelSessionState.IDLE)
            override var loadedModelId: String? = null
            var generations = 0
            override fun isLoaded() = loadedModelId != null
            override suspend fun loadModel(modelId: String, modelPath: String, config: RuntimeLoadConfig): Result<RuntimeLoadResult> {
                assertEquals("B", modelId); assertEquals(LocalComputeBackend.CPU, config.computeBackend)
                loadedModelId = modelId
                return Result.success(RuntimeLoadResult(LocalComputeBackend.CPU, 0, config.contextSize))
            }
            override suspend fun generate(config: RuntimeGenerationConfig, onToken: suspend (String) -> Unit): Result<RuntimeGenerationResult> {
                assertEquals(0.0, config.temperature, 0.0)
                val index = generations++
                val text = when (index) {
                    4 -> "HALO_CHAT_OK"; 5 -> "136"; 6 -> "COBALT-47"
                    7 -> "142"; 8 -> "Chris"; 9 -> "90"
                    10 -> "<halo_tool_call>{\"name\":\"halo_probe\",\"arguments\":{\"value\":\"HALO_OK\"}}</halo_tool_call>"
                    11 -> {
                        assertEquals(Role.TOOL, config.initialMessages.last().role)
                        assertTrue(config.initialMessages.last().toolCallId?.isNotBlank() == true)
                        "Done"
                    }
                    else -> "ok"
                }
                return Result.success(RuntimeGenerationResult(text, tokenCount = 1, coldStart = index == 0, timeToFirstTokenMs = 2900,
                    reasoningText = "private reasoning"))
            }
            override suspend fun cancelGeneration() = Unit
            override suspend fun unloadModel(): Result<Unit> { loadedModelId = null; return Result.success(Unit) }
        }
        val session = LocalModelSession({ runtime }, backgroundScope)
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventory, storage, modelSession = session)
        val router = DefaultAiProviderRouter(listOf(provider), storage, defaultInferenceProviderId = provider.id, defaultInferenceModelId = "A")
        router.setRoleBinding(AiRoleBinding(AiModelRole.AGENTIC, provider.id, "A", AiRoleSelection.EXPLICIT))
        router.setRoleBinding(AiRoleBinding(AiModelRole.FAST, provider.id, "B", AiRoleSelection.EXPLICIT))
        router.setRoleBinding(AiRoleBinding(AiModelRole.REASONING, provider.id, "A", AiRoleSelection.EXPLICIT))
        router.setRoleBinding(AiRoleBinding(AiModelRole.VISION, null, null, AiRoleSelection.AUTOMATIC))
        val before = storage.values.toMap(); val bindings = router.roleBindings.value.toMap(); val policy = router.routingPolicy.value
        val health = LocalInferenceRuntimeHealthTracker()
        repeat(5) { health.accept(LocalInferenceObservation(provider.id, "B", "CPU", ttftMs = 3000, success = true, coldStart = false)) }
        val baseline = health.snapshot(provider.id, "B", "CPU")
        val row = provider.runRoleQualification("B", agentic = { descriptor, generation ->
            val probe = LocalAgenticProbeRunner(router)
            probe.runProbe(AiModelRef(provider.id, descriptor.id), generation, retainProvenance = false)
            assertTrue(probe.phases.value.all { it.result == ProbeResult.PASS })
            LocalAgenticQualification(LocalRoleProbeVerdict.PASS, ToolCallProtocol.HALO_PROMPT, true, true)
        }).single()
        assertEquals(LocalRoleProbeVerdict.PASS, row.fast!!.verdict)
        assertEquals(3, row.chat!!.passed); assertEquals(3, row.reasoning!!.passed)
        assertEquals(LocalRoleProbeVerdict.PASS, row.agentic!!.verdict)
        assertFalse(row.agentic!!.provenanceRetained)
        assertEquals(LocalRoleProbeVerdict.NOT_APPLICABLE, row.vision!!.verdict)
        assertEquals(before, storage.values); assertEquals(bindings, router.roleBindings.value); assertEquals(policy, router.routingPolicy.value)
        assertEquals("A", router.selectedModelId.value); assertEquals(baseline, health.snapshot(provider.id, "B", "CPU"))
        assertNull(session.loadedModelId); assertEquals(12, runtime.generations)
        assertFalse(LocalModelRoleQualification.report(listOf(row), "source").contains("private reasoning"))
    }

    @Test fun haloPromptExactBContinuationPreservesGlobalAAndProductionHealth() = runTest {
        assertTrue(exact(ToolCallProtocol.HALO_PROMPT).all { it.result == ProbeResult.PASS })
    }
    @Test fun nativeUsesSameCompleteProbeAndExactTarget() = runTest {
        assertTrue(exact(ToolCallProtocol.NATIVE).all { it.result == ProbeResult.PASS })
    }
    @Test fun unexpectedSecondToolCallFailsFinalResponse() = runTest {
        assertEquals(ProbeResult.FAIL, exact(ToolCallProtocol.HALO_PROMPT, extraCall = true).last().result)
    }
    @Test fun continuationFromAnotherModelCannotPass() = runTest {
        assertTrue(exact(ToolCallProtocol.HALO_PROMPT, mismatch = true).any { it.result == ProbeResult.FAIL })
    }
}
