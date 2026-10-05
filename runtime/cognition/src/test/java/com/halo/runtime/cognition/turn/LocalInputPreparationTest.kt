package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.context.*
import com.halo.runtime.cognition.workload.DefaultAiWorkloadPlanner
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.ResponseDelivery
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LocalInputPreparationTest {
    private class RecordingProvider(
        override val id: String = "fake.provider",
        override val displayName: String = "Fake Provider",
        override val isLocal: Boolean = true,
        val shouldFail: Boolean = false,
        val responseText: String = "Paris."
    ) : AiInferenceProvider {
        val capturedContexts = mutableListOf<AiContext>()
        val capturedRequests = mutableListOf<AiRequest>()

        override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.STREAMING)

        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            capturedRequests.add(request)
            capturedContexts.add(context)
            if (shouldFail) throw AiProviderException(AiErrorType.GENERATION_FAILED, "Simulated error")
            return AiResponse(text = responseText, providerId = id)
        }

        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
            capturedRequests.add(request)
            capturedContexts.add(context)
            if (shouldFail) {
                emit(AiStreamEvent.Started)
                emit(AiStreamEvent.Failed(AiProviderException(AiErrorType.GENERATION_FAILED, "Stream error")))
                return@flow
            }
            emit(AiStreamEvent.Started)
            emit(AiStreamEvent.Token(responseText))
            emit(AiStreamEvent.Completed(AiResponse(text = responseText, providerId = id)))
        }

        override suspend fun listModels() = emptyList<AiModel>()
    }

    private class FakeRouter(private val provider: AiInferenceProvider) : AiProviderRouter {
        override val selectedProviderId = MutableStateFlow<String?>(provider.id)
        override val selectedModelId = MutableStateFlow<String?>("test-model")
        override fun selectProvider(id: String) {}
        override fun selectModel(modelId: String?) {}
        override fun getAllProviders() = listOf(provider)
        override suspend fun getEligibleProvider(request: AiRequest) = provider
        override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget {
            val modelId = request.modelId ?: selectedModelId.value
                ?: throw AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "No model selected")
            val model = AiModel(
                id = modelId,
                displayName = "Test Model",
                providerId = provider.id,
                inputCapabilities = setOf(AiCapability.TEXT, AiCapability.IMAGE),
                contextLength = 4096,
                executionLocation = AiExecutionLocation.ON_DEVICE,
                supportsStreaming = true,
                supportsTools = true
            )
            return AiExecutionTarget(
                provider = provider,
                model = model,
                providerCapabilities = provider.getCapabilities()
            )
        }
        override suspend fun execute(request: AiRequest, context: AiContext) = provider.generate(request, context)
        override fun start() {}
        override fun stop() {}
    }


    @Test fun typedHomeKeepsTextBudgetsAndDoesNotPrewarmVoice() = runTest {
        val budgets = mutableListOf<Int?>()
        var voiceHints = 0
        val assembler = object : ContextAssembler {
            override suspend fun assemble(policy: AiContextPolicy, capabilities: Set<AiCapability>) = ContextAssemblyResult(AiContext())
            override suspend fun assemble(policy: AiContextPolicy, capabilities: Set<AiCapability>, request: ContextAssemblyRequest): ContextAssemblyResult {
                budgets += request.performanceBudget?.maxConversationTokens
                return ContextAssemblyResult(AiContext())
            }
        }
        val preparer = DefaultCognitiveTurnPreparer(DefaultAiWorkloadPlanner(), FakeRouter(RecordingProvider()), null,
            assembler, null, localVoiceNextUse = { _, _ -> voiceHints++ })
        val home = CognitiveTurnRequest(InteractionId("home"), "home", AiRequest(text = "what is RAM?"),
            AiContextPolicy.CONSERVATIVE, ResponseDelivery.SPEECH_AND_VISUAL, null, mapOf("input" to "keyboard"))
        repeat(2) {
            val prepared = preparer.prepare(home)
            assertEquals(AiModelRole.CHAT, prepared.workload.inference.preferredRole)
            assertEquals(256, prepared.providerRequest.maxOutputTokens)
        }
        assertEquals(listOf(1024, 1024), budgets)
        assertEquals(0, voiceHints)
        val spoken = preparer.prepare(home.copy(metadata = mapOf("inputModality" to "VOICE"), responseDelivery = ResponseDelivery.VISUAL_ONLY))
        assertEquals(AiModelRole.FAST, spoken.workload.inference.preferredRole)
        assertEquals(128, spoken.providerRequest.maxOutputTokens)
        assertEquals(512, budgets.last())
        assertEquals(1, voiceHints)
    }
}
