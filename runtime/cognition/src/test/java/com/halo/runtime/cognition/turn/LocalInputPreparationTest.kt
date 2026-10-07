package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import java.time.Instant
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

    private class FakeRouter(private val provider: AiInferenceProvider, private val fastModel: String? = null, private val thinking: Boolean = false, private val location: AiExecutionLocation = AiExecutionLocation.ON_DEVICE) : AiProviderRouter {
        override val selectedProviderId = MutableStateFlow<String?>(provider.id)
        override val selectedModelId = MutableStateFlow<String?>("test-model")
        override fun selectProvider(id: String) {}
        override fun selectModel(modelId: String?) {}
        override fun getAllProviders() = listOf(provider)
        override suspend fun getEligibleProvider(request: AiRequest) = provider
        override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget {
            val modelId = request.modelId ?: fastModel.takeIf { workload.preferredRole == AiModelRole.FAST } ?: selectedModelId.value
                ?: throw AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "No model selected")
            val model = AiModel(
                id = modelId,
                displayName = "Test Model",
                providerId = provider.id,
                inputCapabilities = setOf(AiCapability.TEXT, AiCapability.IMAGE),
                contextLength = 4096,
                executionLocation = location,
                supportsThinking = thinking,
                supportsStreaming = true,
                supportsTools = true
            )
            return AiExecutionTarget(
                provider = provider,
                model = model,
                providerCapabilities = provider.getCapabilities()
            )
        }
        override suspend fun resolvePinnedExecutionTarget(request: AiRequest, workload: AiWorkloadSpec, providerId: String, modelId: String) =
            resolveExecutionTarget(request.copy(modelId = modelId), workload)
        override suspend fun execute(request: AiRequest, context: AiContext) = provider.generate(request, context)
        override fun start() {}
        override fun stop() {}
    }


    @Test fun exactTargetAndRoleOwnThinkingIntent() = runTest {
        val assembler = object : ContextAssembler {
            override suspend fun assemble(policy: AiContextPolicy, capabilities: Set<AiCapability>) = ContextAssemblyResult(AiContext())
        }
        val turn = CognitiveTurnRequest(InteractionId("thinking"), "home", AiRequest(text = "synthetic"),
            AiContextPolicy.CONSERVATIVE, ResponseDelivery.VISUAL_ONLY, null, emptyMap())
        for (thinking in listOf(true, false)) for (location in AiExecutionLocation.entries) {
            for (role in AiModelRole.entries.map { it as AiModelRole? } + listOf(null)) {
                val planner = object : com.halo.runtime.cognition.workload.AiWorkloadPlanner {
                    override fun plan(request: com.halo.runtime.cognition.workload.WorkloadPlanningRequest): com.halo.runtime.cognition.workload.CognitiveWorkloadPlan {
                        val plan = DefaultAiWorkloadPlanner().plan(request)
                        return plan.copy(inference = plan.inference.copy(preferredRole = role))
                    }
                }
                val prepared = DefaultCognitiveTurnPreparer(planner, FakeRouter(RecordingProvider(), thinking = thinking, location = location), null, assembler, null).prepare(turn)
                val enabled = thinking && location == AiExecutionLocation.ON_DEVICE && role == AiModelRole.REASONING
                val expected = if (!thinking || location != AiExecutionLocation.ON_DEVICE) AiReasoningMode.DEFAULT else if (enabled) AiReasoningMode.ENABLED else AiReasoningMode.DISABLED
                assertEquals(expected, prepared.providerRequest.reasoningMode)
                assertEquals(if (enabled) 64 else null, prepared.providerRequest.reasoningTokenBudget)
            }
        }
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
    @Test fun voiceFastProfileAndCorrelationFollowResolvedModelB() = runTest {
        val lookedUp = mutableListOf<String>()
        val hinted = mutableListOf<String>()
        val provider = RecordingProvider()
        val assembler = object : ContextAssembler {
            override suspend fun assemble(policy: AiContextPolicy, capabilities: Set<AiCapability>) = ContextAssemblyResult(AiContext())
        }
        val preparer = DefaultCognitiveTurnPreparer(DefaultAiWorkloadPlanner(), FakeRouter(provider, "model-B"), null,
            assembler, null, localInferenceProfileSource = LocalInferenceProfileSource { _, model -> lookedUp += model; null },
            localVoiceNextUse = { _, model -> hinted += model })
        val turn = CognitiveTurnRequest(InteractionId("voice-062"), "home", AiRequest(text = "what is RAM?"),
            AiContextPolicy.CONSERVATIVE, ResponseDelivery.VISUAL_ONLY, null, mapOf("inputModality" to "VOICE"))
        val prepared = preparer.prepare(turn)
        assertEquals(AiModelRole.FAST, prepared.workload.inference.preferredRole)
        assertEquals("model-B", prepared.target.modelId)
        assertEquals(listOf("model-B"), lookedUp)
        assertEquals(listOf("model-B"), hinted)
        assertEquals("voice-062", prepared.providerRequest.interactionId)
        assertEquals("model-B", prepared.providerRequest.modelId)
    }

    @Test fun continuationKeepsJournalTargetWhenNextRoleTargetChanges() = runTest {
        val provider = RecordingProvider()
        val runId = AgentRunId("run-062")
        val now = Instant.now()
        val run = AgentRun(runId, InteractionId("pin-062"), "what is RAM?", AgentRunState.EXECUTING,
            createdAt = now, updatedAt = now, steps = listOf(AgentStep.ProviderTurnStarted(
                AgentStepId("turn-062"), runId, now, ProviderTurnId("provider-turn"), provider.id, "model-B",
                "request", emptySet(), setOf("TEXT"), toolUseMode = "OPTIONAL", workloadKind = "TOOL_ASSISTED")))
        val store = object : AgentRunStore {
            override fun observeRuns(): Flow<List<AgentRun>> = flowOf(listOf(run))
            override suspend fun get(runId: AgentRunId) = run
            override suspend fun create(run: AgentRun) = Unit
            override suspend fun appendStep(step: AgentStep) = false
            override suspend fun update(run: AgentRun) = Unit
            override suspend fun nonTerminalRuns() = listOf(run)
            override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState) = false
            override suspend fun resolveInputIfPending(requestId: AgentInputRequestId, resolution: AgentInputResolution) = false
        }
        val router = FakeRouter(provider, "model-C")
        val assembler = object : ContextAssembler {
            override suspend fun assemble(policy: AiContextPolicy, capabilities: Set<AiCapability>) = ContextAssemblyResult(AiContext())
        }
        val preparer = DefaultCognitiveTurnPreparer(DefaultAiWorkloadPlanner(), router, null, assembler, store)
        val turn = CognitiveTurnRequest(InteractionId("pin-062"), "home", AiRequest(text = "continue"),
            AiContextPolicy.CONSERVATIVE, ResponseDelivery.VISUAL_ONLY, null, emptyMap(),
            mode = CognitiveTurnMode.CONTINUE_AFTER_TOOL, runId = runId)
        val prepared = preparer.prepare(turn)
        assertEquals("model-B", prepared.target.modelId)
        assertEquals("model-B", prepared.providerRequest.modelId)
        assertEquals("pin-062", prepared.providerRequest.interactionId)
        assertEquals("model-C", router.resolveExecutionTarget(AiRequest(text = "new"),
            AiWorkloadSpec(preferredRole = AiModelRole.FAST)).modelId)
    }

}
