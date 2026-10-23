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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@RunWith(RobolectricTestRunner::class)
class LiteRtLocalProviderStreamTest {
    @Test fun coldAdvisoryNeverInitializesOrTearsDownANativeEngine() = runTest {
        val runtime = FakeRuntime()
        val storage = FakeStorage()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(),
            storage = storage, modelSession = LocalModelSession({ runtime }, backgroundScope))
        val writes = storage.writes.toList()
        val response = withContext(Dispatchers.Default) { withTimeout(1_000) {
            provider.tryGenerateAdvisory("test_model", "Open camera", "Reply with one label")
        } }
        org.junit.Assert.assertNull(response)
        assertTrue(runtime.loadedIds.isEmpty())
        assertTrue(runtime.configs.isEmpty())
        assertEquals(0, runtime.unloads)
        assertEquals(writes, storage.writes)
    }

    @Test fun advisoryDoesNotWaitForOrRefreshAnUnavailableInventory() = runTest {
        val runtime = FakeRuntime()
        val inventory = FakeInventory()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = inventory,
            storage = FakeStorage(), modelSession = LocalModelSession({ runtime }, backgroundScope))
        assertTrue(async(Dispatchers.Default) { provider.loadModelIfNeeded("test_model") }.await())
        assertTrue(!provider.inferenceBusyForIntent())
        val resolves = inventory.resolveCalls
        inventory.inventoryState.value = LocalModelInventoryState.Loading
        org.junit.Assert.assertNull(provider.tryGenerateAdvisory("test_model", "Open camera", "Reply with one label"))
        assertEquals(resolves, inventory.resolveCalls)
        assertTrue(runtime.configs.isEmpty())
        assertEquals(0, runtime.unloads)
    }

    @Test fun incompleteNativeReadinessIsNotAnAdvisoryEngine() = runTest {
        val runtime = FakeRuntime()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(),
            storage = FakeStorage(), modelSession = LocalModelSession({ runtime }, backgroundScope))
        assertTrue(async(Dispatchers.Default) { provider.loadModelIfNeeded("test_model") }.await())
        assertTrue(!provider.inferenceBusyForIntent())
        runtime.nativeReady = false
        org.junit.Assert.assertNull(provider.tryGenerateAdvisory("test_model", "Open camera", "Reply with one label"))
        assertEquals(listOf("test_model"), runtime.loadedIds)
        assertTrue(runtime.configs.isEmpty())
        assertEquals(0, runtime.unloads)
    }

    @Test fun advisoryPreservesHybridResidencyWithoutGeneratingOrResettingIt() = runTest {
        val runtime = FakeRuntime()
        val inventory = FakeInventory(ids = listOf("hybrid"))
        val original = (inventory.inventoryState.value as LocalModelInventoryState.Ready).models.single()
        inventory.inventoryState.value = LocalModelInventoryState.Ready(listOf(original.copy(
            path = "my_LFM2.5-1.2B-Instruct_int4.litertlm")), 1)
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = inventory,
            storage = FakeStorage(), modelSession = LocalModelSession({ runtime }, backgroundScope))
        assertTrue(async(Dispatchers.Default) { provider.loadModelIfNeeded("hybrid") }.await())
        assertTrue(!provider.inferenceBusyForIntent())
        val loads = runtime.loadedIds.toList()
        val unloads = runtime.unloads
        org.junit.Assert.assertNull(provider.tryGenerateAdvisory("hybrid", "Open camera", "Reply with one label"))
        assertEquals(loads, runtime.loadedIds)
        assertEquals(unloads, runtime.unloads)
        assertTrue(runtime.configs.isEmpty())
        assertEquals("hybrid", provider.residentModelIdForIntent())
    }

    @Test fun changedArtifactOrUnverifiedMetadataCannotReloadTheResidentModelForAdvice() = runTest {
        val runtime = FakeRuntime()
        val inventory = FakeInventory()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = inventory,
            storage = FakeStorage(), modelSession = LocalModelSession({ runtime }, backgroundScope))
        assertTrue(async(Dispatchers.Default) { provider.loadModelIfNeeded("test_model") }.await())
        assertTrue(!provider.inferenceBusyForIntent())
        val original = (inventory.inventoryState.value as LocalModelInventoryState.Ready).models.single()
        val loads = runtime.loadedIds.toList()
        val unloads = runtime.unloads
        for (changed in listOf(original.copy(path = "replaced-path"), original.copy(sha256 = "replacement-hash"),
            original.copy(validation = LocalModelValidation.UNVERIFIED), original.copy(supportsText = false))) {
            inventory.inventoryState.value = LocalModelInventoryState.Ready(listOf(changed), 1)
            org.junit.Assert.assertNull(provider.tryGenerateAdvisory("test_model", "Open camera", "Reply with one label"))
            assertEquals(loads, runtime.loadedIds)
            assertEquals(unloads, runtime.unloads)
            assertTrue(runtime.configs.isEmpty())
        }
    }

    @Test fun advisoryCannotReloadSameIdWithADifferentResidentConfiguration() = runTest {
        val runtime = FakeRuntime()
        val inventory = FakeInventory()
        val session = LocalModelSession({ runtime }, backgroundScope)
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = inventory,
            storage = FakeStorage(), modelSession = session)
        val descriptor = requireNotNull(inventory.resolveModel("test_model"))
        val expectedConfig = provider.loadConfigFor(descriptor)
        assertTrue(session.loadModel("test_model", descriptor.path, config = provider.loadConfigFor(descriptor)
            .copy(contextSize = if (expectedConfig.contextSize == 1024) 2048 else 1024,
                computeBackend = LocalComputeBackend.CPU, visionEnabled = false, audioEnabled = false),
            artifactSha256 = descriptor.sha256).isSuccess)
        val loads = runtime.loadedIds.toList()
        val unloads = runtime.unloads
        org.junit.Assert.assertNull(provider.tryGenerateAdvisory("test_model", "Open camera", "Reply with one label"))
        assertEquals(loads, runtime.loadedIds)
        assertEquals(unloads, runtime.unloads)
        assertTrue(runtime.configs.isEmpty())
    }
    @Test fun invalidAdvisoryInputCannotLoadOrGenerateAModel() = runTest {
        val runtime = FakeRuntime()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(),
            storage = FakeStorage(), modelSession = LocalModelSession({ runtime }, backgroundScope))
        for ((model, text, instruction) in listOf(Triple("", "Open camera", "Label"),
            Triple("test_model", "x", "Label"), Triple("test_model", "Open camera", "x".repeat(8001)))) {
            var rejected = false
            try { provider.tryGenerateAdvisory(model, text, instruction) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
        assertTrue(runtime.loadedIds.isEmpty())
        assertTrue(runtime.configs.isEmpty())
    }
    @Test fun advisoryNeverQueuesBehindActualConversationalGeneration() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val runtime = FakeRuntime().apply { generationEntered = entered; generationRelease = release }
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(),
            storage = FakeStorage(), modelSession = LocalModelSession({ runtime }, backgroundScope))
        val chat = async(Dispatchers.Default) { provider.generate(AiRequest(text = "Chat", modelId = "test_model"), AiContext.EMPTY) }
        try {
            withContext(Dispatchers.Default) { withTimeout(5_000) { entered.await() } }
            val advice = withContext(Dispatchers.Default) { withTimeout(1_000) {
                provider.tryGenerateAdvisory("test_model", "Open camera", "Reply with one label")
            } }
            org.junit.Assert.assertNull(advice)
            assertEquals(1, runtime.configs.size)
        } finally { release.complete(Unit); chat.await() }
    }

    @Test fun advisoryKeepsDifferentResidentChatWithoutNativeGenerationOrPreferenceWrites() = runTest {
        val runtime = FakeRuntime()
        val storage = FakeStorage()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(),
            inventoryRepository = FakeInventory(ids = listOf("chat", "intent")), storage = storage,
            modelSession = LocalModelSession({ runtime }, backgroundScope))
        provider.generate(AiRequest(text = "Chat", modelId = "chat"), AiContext.EMPTY)
        val writes = storage.writes.toList()
        val loaded = runtime.loadedIds.toList()
        org.junit.Assert.assertNull(provider.tryGenerateAdvisory("intent", "Open camera", "Reply with one label"))
        assertEquals(loaded, runtime.loadedIds)
        assertEquals(writes, storage.writes)
        assertEquals(1, runtime.configs.size)
        assertEquals("chat", provider.residentModelIdForIntent())
    }

    @Test fun admittedAdvisoryUsesCanonicalBoundedTextOnlyGenerationAndActualIdentity() = runTest {
        val runtime = FakeRuntime().apply { nativeThinking = true }
        val inventory = FakeInventory(thinking = true)
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = inventory,
            storage = FakeStorage(), modelSession = LocalModelSession({ runtime }, backgroundScope))
        assertTrue(async(Dispatchers.Default) { provider.loadModelIfNeeded("test_model") }.await())
        assertTrue(!provider.inferenceBusyForIntent())
        val resolves = inventory.resolveCalls
        val response = requireNotNull(provider.tryGenerateAdvisory("test_model", "Open camera", "Reply with one label"))
        assertEquals(provider.id, response.providerId)
        assertEquals("test_model", response.resolvedModelId)
        val config = runtime.configs.single()
        assertEquals(12, config.maxTokens)
        assertTrue(config.availableTools.isEmpty())
        assertEquals(false, config.thinkingEnabled)
        assertEquals(0.0, config.temperature, 0.0)
        assertEquals(listOf("test_model"), runtime.loadedIds)
        assertEquals(0, runtime.unloads)
        assertEquals(resolves, inventory.resolveCalls)
    }

    @Test fun cancelledAdvisoryReleasesCanonicalMutexForNextRequest() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val runtime = FakeRuntime().apply { generationEntered = entered; generationRelease = release }
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(),
            storage = FakeStorage(), modelSession = LocalModelSession({ runtime }, backgroundScope))
        assertTrue(async(Dispatchers.Default) { provider.loadModelIfNeeded("test_model") }.await())
        assertTrue(!provider.inferenceBusyForIntent())
        val advice = async(Dispatchers.Default) { provider.tryGenerateAdvisory("test_model", "Open camera", "Reply with one label") }
        withContext(Dispatchers.Default) { withTimeout(5_000) { entered.await() } }
        advice.cancel()
        advice.join()
        assertTrue(advice.isCancelled)
        runtime.generationEntered = null
        runtime.generationRelease = null
        assertTrue(provider.tryGenerateAdvisory("test_model", "Open camera", "Reply with one label") != null)
    }
    @Test fun roleDiagnosticOverrideIsTransientAndRecordsRequestedConfiguration() = runTest {
        val runtime = FakeRuntime()
        val storage = FakeStorage()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(),
            storage = storage, modelSession = LocalModelSession({ runtime }, backgroundScope))
        val artifact = requireNotNull(FakeInventory().resolveModel("test_model"))
        val before = provider.loadConfigFor(artifact)
        val writesBefore = storage.writes.toList()
        val rows = provider.runRoleQualification("test_model", agentic = { _, generate ->
            val response = generate(AiRequest(text = "test", modelId = "test_model", toolUseMode = ToolUseMode.NONE), AiContext.EMPTY)
            assertTrue(response.metrics != null)
            LocalAgenticQualification(LocalRoleProbeVerdict.NOT_RUN, ToolCallProtocol.NONE)
        }, diagnosticConfig = before.copy(computeBackend = LocalComputeBackend.CPU, contextSize = 1024, useModelDefaultContext = false))
        assertEquals(LocalComputeBackend.CPU, rows.single().requestedBackend)
        assertEquals(1024, rows.single().contextTokens)
        assertEquals(before, provider.loadConfigFor(artifact))
        assertEquals(writesBefore, storage.writes)
    }
    @Test fun missingArtifactIsNotMisreportedAsUnsupportedTextInEitherExecutionPath() = runTest {
        val runtime = FakeRuntime()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(),
            inventoryRepository = FakeInventory(ids = emptyList()), storage = FakeStorage(),
            modelSession = LocalModelSession({ runtime }, backgroundScope))
        val request = AiRequest(text = "Hello", modelId = "missing")
        try {
            provider.generate(request, AiContext.EMPTY)
            org.junit.Assert.fail("Missing model must not initialize")
        } catch (error: com.halo.sdk.provider.AiProviderException) {
            assertEquals(com.halo.sdk.provider.AiErrorType.MODEL_NOT_FOUND, error.type)
        }
        val failure = provider.stream(request, AiContext.EMPTY).toList().filterIsInstance<AiStreamEvent.Failed>().single()
        assertEquals(com.halo.sdk.provider.AiErrorType.MODEL_NOT_FOUND,
            (failure.error as com.halo.sdk.provider.AiProviderException).type)
        assertTrue(runtime.loadedIds.isEmpty())
        assertTrue(runtime.configs.isEmpty())
    }

    @Test fun failedAdditionalFixtureDoesNotBecomeAToolProtocolFailure() = runTest {
        val runtime = FakeRuntime().apply { failPrompt = "fixture failure" }
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(),
            storage = FakeStorage(), modelSession = LocalModelSession({ runtime }, backgroundScope))
        val rows = provider.runRoleQualification("test_model", diagnosticProbes = { descriptor, generate ->
            val result = runCatching { generate(AiRequest(text = "fixture failure", modelId = descriptor.id), AiContext.EMPTY) }
            assertTrue(result.isFailure)
        }, agentic = { _, _ -> LocalAgenticQualification(LocalRoleProbeVerdict.PASS, ToolCallProtocol.NATIVE,
            initialToolPass = true, continuationPass = true) })
        assertEquals(LocalRoleProbeVerdict.PASS, rows.single().agentic?.verdict)
    }

    private class FakeRuntime : LocalInferenceRuntime {
        val configs = mutableListOf<RuntimeGenerationConfig>()
        val loadedIds = mutableListOf<String>()
        var unloads = 0
        var nativeReady = true
        var lastLoadBackend: LocalComputeBackend? = null
        var emptyResponse = false
        var nativeThinking = false
        var failPrompt: String? = null
        var generationEntered: CompletableDeferred<Unit>? = null
        var generationRelease: CompletableDeferred<Unit>? = null
        override val format = LocalModelFormat.LITERT_LM
        override val state = MutableStateFlow(LocalModelSessionState.IDLE)
        override val loadedModelId: String? = "test_model"

        override fun isLoaded(): Boolean = nativeReady

        override suspend fun loadModel(modelId: String, modelPath: String, config: RuntimeLoadConfig): Result<RuntimeLoadResult> {
            // Simulate native metadata in this fake runtime; catalog flags alone are not proof.
            com.halo.providers.litert.runtime.LiteRtModelPreflight.rememberThinkingSupport(modelPath, 100L, 0L, nativeThinking)
            loadedIds += modelId
            lastLoadBackend = config.computeBackend
            return Result.success(RuntimeLoadResult(LocalComputeBackend.CPU, 0, config.contextSize, null))
        }

        override suspend fun generate(config: RuntimeGenerationConfig, onToken: suspend (String) -> Unit): Result<RuntimeGenerationResult> {
            configs += config
            generationEntered?.complete(Unit)
            generationRelease?.await()
            if (config.prompt == failPrompt) return Result.failure(IllegalStateException("SYNTHETIC_FIXTURE_FAILURE"))
            if (emptyResponse) return Result.success(RuntimeGenerationResult("", emptyList(), 0, 0L, 0.0, 1L))
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
            unloads++
            return Result.success(Unit)
        }
    }

    private class FakeInventory(ids: List<String> = listOf("test_model"), thinking: Boolean = false,
        audio: Boolean = false, vision: Boolean = false) : LocalModelInventoryRepository {
        var resolveCalls = 0
        override val inventoryState = MutableStateFlow<LocalModelInventoryState>(
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
                        supportsVision = vision,
                        supportsAudio = audio,
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
            resolveCalls++
            return (inventoryState.value as LocalModelInventoryState.Ready).models.firstOrNull { it.id == modelId }
        }
        override fun invalidate(reason: String) {}
    }

    private class FakeStorage : Storage {
        val writes = mutableListOf<String>()
        override fun getString(key: String, defaultValue: String?): String? = when (key) {
            "active_ai_provider" -> "provider.litert.local"
            "active_ai_model" -> "test_model"
            else -> defaultValue
        }
        override fun putString(key: String, value: String?) { writes += key }
        override fun getInt(key: String, defaultValue: Int): Int = defaultValue
        override fun putInt(key: String, value: Int) { writes += key }
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun putBoolean(key: String, value: Boolean) { writes += key }
        override fun remove(key: String) {}
        override fun clear() {}
    }


    @Test fun mediaAndCapabilityWiringReachBothGenerationPaths() = runTest {
        val runtime = FakeRuntime()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(),
            inventoryRepository = FakeInventory(audio = true), storage = FakeStorage(),
            nativeResourceCoordinator = null, modelSession = LocalModelSession({ runtime }, backgroundScope))
        val request = AiRequest(text = "Transcribe", audio = byteArrayOf(1, 2),
            modelId = "test_model", toolUseMode = ToolUseMode.NONE)
        assertTrue(com.halo.sdk.provider.AiCapability.AUDIO in provider.capabilitiesForModel("test_model"))
        assertTrue(com.halo.sdk.provider.AiCapability.AUDIO in provider.listModels().single().inputCapabilities)
        provider.generate(request, AiContext.EMPTY)
        provider.stream(request, AiContext.EMPTY).toList()
        assertEquals(2, runtime.configs.size)
        runtime.configs.forEach {
            org.junit.Assert.assertArrayEquals(request.audio, it.audioBytes)
            org.junit.Assert.assertArrayEquals(request.image, it.imageBytes)
            assertTrue(it.availableTools.isEmpty())
        }
        assertTrue(provider.modelSession.requestedLoadConfig?.audioEnabled == true)
        assertTrue(provider.modelSession.requestedLoadConfig?.visionEnabled == false)
        val audioIdentity = provider.localExecutionIdentity("test_model")
        provider.generate(AiRequest(text = "Hello", modelId = "test_model"), AiContext.EMPTY)
        assertTrue(provider.modelSession.requestedLoadConfig?.audioEnabled == false)
        org.junit.Assert.assertNotEquals(audioIdentity, provider.localExecutionIdentity("test_model"))
        assertEquals(2, runtime.loadedIds.size)
    }

    @Test fun imageEncoderCanUseGpuIndependentlyOfCpuTextBackend() = runTest {
        val runtime = FakeRuntime()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(),
            inventoryRepository = FakeInventory(vision = true), storage = FakeStorage(),
            nativeResourceCoordinator = null, modelSession = LocalModelSession({ runtime }, backgroundScope))
        val request = AiRequest(text = "Describe", modelId = "test_model", image = byteArrayOf(1))
        provider.generate(request, AiContext.EMPTY)
        assertEquals(LocalComputeBackend.CPU, runtime.lastLoadBackend)
        assertTrue(provider.modelSession.requestedLoadConfig?.visionEnabled == true)
        org.junit.Assert.assertArrayEquals(request.image, runtime.configs.single().imageBytes)
    }

    @Test fun textOnlyModelRejectsAudioBeforeLoading() = runTest {
        val runtime = FakeRuntime()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(),
            inventoryRepository = FakeInventory(), storage = FakeStorage(), nativeResourceCoordinator = null,
            modelSession = LocalModelSession({ runtime }, backgroundScope))
        try {
            provider.generate(AiRequest(text = "Transcribe", audio = byteArrayOf(1)), AiContext.EMPTY)
            org.junit.Assert.fail("Audio must not be silently dropped")
        } catch (expected: com.halo.sdk.provider.AiProviderException) {
            assertEquals(com.halo.sdk.provider.AiErrorType.UNSUPPORTED_CAPABILITY, expected.type)
        }
        assertTrue(runtime.loadedIds.isEmpty())
        assertTrue(runtime.configs.isEmpty())
    }

    @Test fun exactArtifactThinkingPolicyAndNoneToolsReachAllProviderBoundaries() = runTest {
        val runtime = FakeRuntime().apply { nativeThinking = true }
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
    @Test fun emptyStreamIsReportedAsFailureInsteadOfBlankCompletion() = runTest {
        val runtime = FakeRuntime().apply { emptyResponse = true }
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(),
            inventoryRepository = FakeInventory(),
            storage = FakeStorage(), nativeResourceCoordinator = null,
            modelSession = LocalModelSession({ runtime }, backgroundScope))
        val events = provider.stream(AiRequest(text = "hello", modelId = "test_model"), AiContext.EMPTY).toList()
        assertTrue(events.any { it is AiStreamEvent.Started })
        assertTrue(events.any { it is AiStreamEvent.Failed })
        assertTrue(events.none { it is AiStreamEvent.Completed })
    }

    @Test fun diagnosticBoundaryAlsoStripsNoneToolsAndDisablesThinking() = runTest {
        val runtime = FakeRuntime().apply { nativeThinking = true }
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
    @Test fun catalogThinkingClaimCannotEnableAnUnsupportedNativeControl() = runTest {
        val runtime = FakeRuntime()
        val provider = LiteRtLocalProvider(ApplicationProvider.getApplicationContext(), inventoryRepository = FakeInventory(thinking = true),
            storage = FakeStorage(), nativeResourceCoordinator = null, modelSession = LocalModelSession({ runtime }, backgroundScope))
        provider.generate(AiRequest(text = "test", reasoningMode = AiReasoningMode.ENABLED), AiContext.EMPTY)
        assertEquals(null, runtime.configs.single().thinkingEnabled)
        assertEquals(null, runtime.configs.single().thinkingTokenBudget)
        assertEquals(false, provider.nativeThinkingSupported("test_model"))
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

        assertTrue(events.first() is AiStreamEvent.Started)
        assertTrue(events.last() is AiStreamEvent.Completed)
        assertEquals(1, events.count { it is AiStreamEvent.Started })
        assertEquals(1, events.count { it is AiStreamEvent.Completed })
        // The prompt-tool markup guard may buffer/coalesce native chunks.
        assertTrue(events.drop(1).dropLast(1).all { it is AiStreamEvent.Token })
        assertEquals("Hello world", events.filterIsInstance<AiStreamEvent.Token>().joinToString("") { it.text })
        assertEquals("Hello world", (events.last() as AiStreamEvent.Completed).response.text)
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
