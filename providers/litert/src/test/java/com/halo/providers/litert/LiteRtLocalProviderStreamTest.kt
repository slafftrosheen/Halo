package com.halo.providers.litert

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelInventoryState
import com.halo.providers.litert.models.LocalModelValidation
import com.halo.providers.litert.models.ModelInventoryRefreshReason
import com.halo.providers.litert.runtime.LocalModelSession
import com.halo.providers.litert.runtime.LocalAgenticQualification
import com.halo.providers.litert.runtime.LocalRoleProbeVerdict
import com.halo.sdk.provider.ToolCallProtocol
import com.halo.sdk.provider.AiReasoningMode
import com.halo.sdk.provider.ToolUseMode
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.LocalInferenceRuntime
import com.halo.sdk.provider.LocalModelFormat
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeGenerationResult
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.provider.RuntimeLoadResult
import com.halo.sdk.provider.LocalModelSessionState
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.delay

@RunWith(RobolectricTestRunner::class)
class LiteRtLocalProviderStreamTest {

    private class FakeRuntime : LocalInferenceRuntime {
        val configs = mutableListOf<RuntimeGenerationConfig>()
        val loadedIds = mutableListOf<String>()
        var lastLoadBackend: LocalComputeBackend? = null
        override val format = LocalModelFormat.LITERT_LM
        override val state = MutableStateFlow(LocalModelSessionState.IDLE)
        override val loadedModelId: String? = "test_model"

        override fun isLoaded(): Boolean = true

        override suspend fun loadModel(modelId: String, modelPath: String, config: RuntimeLoadConfig): Result<RuntimeLoadResult> {
            loadedIds += modelId
            lastLoadBackend = config.computeBackend
            return Result.success(RuntimeLoadResult(LocalComputeBackend.CPU, 0, config.contextSize, null))
        }

        override suspend fun generate(config: RuntimeGenerationConfig, onToken: suspend (String) -> Unit): Result<RuntimeGenerationResult> {
            configs += config
            state.value = LocalModelSessionState.INFERRING
            delay(10)
            onToken("Hello")
            delay(10)
            onToken(" world")
            state.value = LocalModelSessionState.IDLE
            return Result.success(RuntimeGenerationResult("Hello world", emptyList(), 2, 10, 100.0, 20L))
        }

        override suspend fun cancelGeneration() {
            state.value = LocalModelSessionState.IDLE
        }

        override suspend fun unloadModel(): Result<Unit> {
            return Result.success(Unit)
        }
    }

    private class FakeInventory(ids: List<String> = listOf("test_model"), thinking: Boolean = false) : LocalModelInventoryRepository {
        override val inventoryState = MutableStateFlow(
            LocalModelInventoryState.Ready(
                ids.map { id ->
                    LocalModelDescriptor(
                        id = id,
                        displayName = "Test",
                        path = "path",
                        bytes = 100L,
                        modifiedAt = 0L,
                        sha256 = "hash",
                        contextWindowTokens = 4096,
                        supportsVision = false,
                        supportsThinking = thinking,
                        supportsTools = false,
                        validation = LocalModelValidation.VERIFIED_ARTIFACT,
                    )
                },
                0L
            )
        )
        override suspend fun verifyPhysicalArtifact(modelId: String) = resolveModel(modelId)
        override suspend fun refresh(reason: ModelInventoryRefreshReason) {}
        override suspend fun resolveModel(modelId: String): LocalModelDescriptor? {
            return (inventoryState.value as LocalModelInventoryState.Ready).models.firstOrNull { it.id == modelId }
        }
        override fun invalidate(reason: String) {}
    }

    private class FakeStorage : Storage {
        override fun getString(key: String, defaultValue: String?): String? = when (key) {
            "active_ai_provider" -> "provider.litert.local"
            "active_ai_model" -> "test_model"
            else -> defaultValue
        }
        override fun putString(key: String, value: String?) {}
        override fun getInt(key: String, defaultValue: Int): Int = defaultValue
        override fun putInt(key: String, value: Int) {}
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun putBoolean(key: String, value: Boolean) {}
        override fun remove(key: String) {}
        override fun clear() {}
    }


    @Test fun exactArtifactThinkingPolicyAndNoneToolsReachAllProviderBoundaries() = runTest {
        val runtime = FakeRuntime()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(listOf("test_model", "B"), thinking = true),
            storage = FakeStorage(), nativeResourceCoordinator = null, modelSession = LocalModelSession({ runtime }, backgroundScope))
        val context = AiContext(availableTools = listOf(ToolDefinition("probe", "probe", "synthetic", emptyMap())))
        for (mode in AiReasoningMode.entries) {
            val request = AiRequest(text = "test", modelId = "B", toolUseMode = ToolUseMode.NONE, reasoningMode = mode, reasoningTokenBudget = 1000)
            provider.generate(request, context)
            provider.stream(request, context).toList()
            runtime.configs.takeLast(2).forEach { config ->
                assertTrue(config.availableTools.isEmpty())
                assertTrue(config.systemInstruction?.contains("halo_tool_call") != true)
                assertEquals(when(mode) { AiReasoningMode.DEFAULT -> null; AiReasoningMode.DISABLED -> false; AiReasoningMode.ENABLED -> true }, config.thinkingEnabled)
                assertEquals(if (mode == AiReasoningMode.ENABLED) 64 else null, config.thinkingTokenBudget)
            }
        }
        assertTrue(runtime.loadedIds.all { it == "B" })
    }
    @Test fun diagnosticBoundaryAlsoStripsNoneToolsAndDisablesThinking() = runTest {
        val runtime = FakeRuntime()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(thinking = true),
            storage = FakeStorage(), nativeResourceCoordinator = null, modelSession = LocalModelSession({ runtime }, backgroundScope))
        provider.runRoleQualification("test_model", agentic = { descriptor, generate ->
            generate(AiRequest(text = "test", modelId = descriptor.id, toolUseMode = ToolUseMode.NONE, reasoningMode = AiReasoningMode.DISABLED),
                AiContext(availableTools = listOf(ToolDefinition("probe", "probe", "synthetic"))))
            val config = runtime.configs.last()
            assertTrue(config.availableTools.isEmpty())
            assertTrue(config.systemInstruction?.contains("halo_tool_call") != true)
            assertEquals(false, config.thinkingEnabled)
            LocalAgenticQualification(LocalRoleProbeVerdict.NOT_APPLICABLE, ToolCallProtocol.NONE)
        })
    }
    @Test fun nonThinkingArtifactIgnoresUnsupportedOverride() = runTest {
        val runtime = FakeRuntime()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(),
            storage = FakeStorage(), nativeResourceCoordinator = null, modelSession = LocalModelSession({ runtime }, backgroundScope))
        provider.generate(AiRequest(text = "test", reasoningMode = AiReasoningMode.ENABLED), AiContext())
        assertEquals(null, runtime.configs.single().thinkingEnabled)
        assertEquals(null, runtime.configs.single().thinkingTokenBudget)
    }

    @Test fun explicitBenchmarkUsesTheExistingCpuSafeAutoGate() = runTest {
        val runtime = FakeRuntime()
        val provider = LiteRtLocalProvider(
            context = ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(),
            storage = FakeStorage(), nativeResourceCoordinator = null,
            modelSession = LocalModelSession({ runtime }, backgroundScope))
        val result = provider.benchmarkModel("test_model").getOrThrow()
        assertEquals(LocalComputeBackend.CPU, runtime.lastLoadBackend)
        assertEquals(LocalComputeBackend.AUTO, result.requestedBackend)
        assertEquals(LocalComputeBackend.CPU, result.effectiveBackend)
        assertEquals(4096, result.effectiveContextTokens)
    }

    @Test
    fun `stream events are emitted from channelFlow without flow invariant violations`() = runTest {
        val runtime = FakeRuntime()
        val session = LocalModelSession({ runtime }, backgroundScope)
        
        val provider = LiteRtLocalProvider(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            inventoryRepository = FakeInventory(),
            storage = FakeStorage(),
            nativeResourceCoordinator = null,
            modelSession = session
        )

        val request = AiRequest(text = "test")
        val events = provider.stream(request, AiContext.EMPTY).toList()

        assertEquals(4, events.size)
        assertTrue(events[0] is AiStreamEvent.Started)
        assertTrue(events[1] is AiStreamEvent.Token && (events[1] as AiStreamEvent.Token).text == "Hello")
        assertTrue(events[2] is AiStreamEvent.Token && (events[2] as AiStreamEvent.Token).text == " world")
        assertTrue(events[3] is AiStreamEvent.Completed)
        assertEquals("Hello world", (events[3] as AiStreamEvent.Completed).response.text)
    }
    @Test fun routedLocalModelOverridesConfiguredDefaultAndReusesOnlySameResident() = runTest {
        val runtime = FakeRuntime()
        val inventory = FakeInventory(listOf("test_model", "model-B", "model-C"))
        val provider = LiteRtLocalProvider(
            context = ApplicationProvider.getApplicationContext(), inventoryRepository = inventory,
            storage = FakeStorage(), nativeResourceCoordinator = null,
            modelSession = LocalModelSession({ runtime }, backgroundScope))
        assertTrue(inventory.resolveModel("model-C")?.supportsTools == false)
        assertTrue(com.halo.sdk.provider.AiCapability.TOOLS in provider.capabilitiesForModel("model-C"))
        assertEquals(com.halo.sdk.provider.ToolCallProtocol.HALO_PROMPT, provider.toolCallProtocol("model-C"))
        for (model in listOf("test_model", "model-B", "model-B", "test_model", "model-C")) {
            provider.stream(AiRequest(text = "hello", modelId = model), AiContext.EMPTY).toList()
        }
        assertEquals(listOf("test_model", "model-B", "test_model", "model-C"), runtime.loadedIds)
    }

    @Test fun exactReadinessDoesNotDependOnOrRewriteMissingConfiguredModel() = runTest {
        val runtime = FakeRuntime()
        val provider = LiteRtLocalProvider(
            context = ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(listOf("model-B")),
            storage = FakeStorage(), nativeResourceCoordinator = null,
            modelSession = LocalModelSession({ runtime }, backgroundScope))
        assertEquals(com.halo.sdk.provider.ProviderStatus.State.MODEL_MISSING, provider.getStatus().state)
        val exact = provider.statusForModel("model-B")
        assertTrue(exact.readiness is com.halo.sdk.provider.ProviderReadiness.ConfiguredCold)
        assertEquals(com.halo.sdk.provider.ProviderStatus.State.MODEL_MISSING, provider.status.value.state)
        assertTrue(runtime.loadedIds.isEmpty())
    }

}
