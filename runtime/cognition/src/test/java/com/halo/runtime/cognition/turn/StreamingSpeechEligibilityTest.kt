package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.context.ContextAcquisitionPlan
import com.halo.runtime.cognition.workload.CognitiveWorkloadPlan
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.asContextElement
import org.junit.Assert.*
import org.junit.Test

class StreamingSpeechEligibilityTest {
    private val provider = object : AiInferenceProvider {
        override val id = "fake"; override val displayName = "Fake"; override val isLocal = true
        override val status = MutableStateFlow(ProviderStatus.READY)
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.STREAMING)
        override suspend fun listModels() = emptyList<AiModel>()
        override suspend fun generate(request: AiRequest, context: AiContext) = AiResponse(text = "answer", providerId = id)
        override fun stream(request: AiRequest, context: AiContext) = emptyFlow<AiStreamEvent>()
    }
    private fun prepared(): PreparedCognitiveTurn {
        val interaction = InteractionId("interaction")
        val request = AiRequest(text = "Explain RAM")
        val turn = CognitiveTurnRequest(interaction, "request", request, AiContextPolicy.CONSERVATIVE,
            ResponseDelivery.SPEECH_AND_VISUAL, null, emptyMap())
        return PreparedCognitiveTurn(turn, CognitiveWorkloadPlan(AiWorkloadSpec(), ContextAcquisitionPlan(interaction, emptyList())),
            AiExecutionTarget(provider, AiModel(id = "model", displayName = "Model", providerId = provider.id, inputCapabilities = setOf(AiCapability.TEXT), contextLength = 4096, supportsStreaming = true, supportsTools = false),
                setOf(AiCapability.TEXT, AiCapability.STREAMING)), AiContext(), request, emptySet(), emptySet())
    }
    @Test fun inferenceUsesOriginalTurnRevisionEvenUnderNewerAmbientAuthority() = kotlinx.coroutines.test.runTest {
        val original = prepared().copy(remoteDispatchRevision = 7L,
            context = AiContext(remoteDataCategories = setOf(RemoteDataCategory.WORKSPACE)))
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) {}
            override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
            override fun events(): Flow<HaloEvent> = emptyFlow()
        }
        var observed: RemoteDispatchProvenance? = null
        val inference = object : ProviderTurnExecutor {
            override val streamingText = MutableStateFlow<String?>(null)
            override suspend fun execute(target: AiExecutionTarget, request: AiRequest, context: AiContext,
                requestId: String, interactionId: InteractionId): ProviderTurnResult {
                observed = RemoteDispatchContext.provenance.get()
                return ProviderTurnResult.Response(AiResponse("synthetic answer", providerId = target.providerId), target)
            }
        }
        val tools = object : ToolTurnExecutor {
            override suspend fun execute(runId: com.halo.runtime.cognition.agent.AgentRunId?, interactionId: InteractionId,
                requestId: String, toolCalls: List<ToolCall>, workspaceReadAllowed: Boolean, exposedToolIds: Set<String>?): ToolTurnResult =
                error("Tool-free turn must not execute tools")
        }
        val executor = CognitiveTurnExecutor(object : CognitiveTurnPreparer {
            override suspend fun prepare(turn: CognitiveTurnRequest) = original
        }, inference, tools, null, null, bus)
        val newer = RemoteDispatchProvenance(emptySet(), 999L)
        kotlinx.coroutines.withContext(RemoteDispatchContext.provenance.asContextElement(newer)) {
            executor.execute(original.turn)
        }
        assertEquals(7L, observed!!.revision)
        assertTrue(RemoteDataCategory.WORKSPACE in observed!!.categories)
        assertNull(RemoteDispatchContext.provenance.get())
    }
    @Test fun exactToolFreeTargetCanStreamSpeech() { assertTrue(prepared().streamingSpeechEligible()) }
    @Test fun toolOptionalAndRequiredNeverStreamSpeech() {
        val base = prepared()
        for (mode in listOf(AiFeatureRequirement.OPTIONAL, AiFeatureRequirement.REQUIRED))
            assertFalse(base.copy(workload = base.workload.copy(inference = base.workload.inference.copy(tools = mode))).streamingSpeechEligible())
    }
    @Test fun visualSettingsCapabilityAndTokenPublicationAreIndependentGates() {
        val base = prepared()
        assertFalse(base.copy(turn = base.turn.copy(responseDelivery = ResponseDelivery.VISUAL_ONLY)).streamingSpeechEligible())
        assertFalse(base.copy(turn = base.turn.copy(voicePolicy = VoiceResponsePolicy(streamingSpeechEnabled = false))).streamingSpeechEligible())
        assertFalse(base.copy(providerRequest = base.providerRequest.copy(publishStreamingTokens = false)).streamingSpeechEligible())
        assertFalse(base.copy(target = base.target.copy(effectiveCapabilities = setOf(AiCapability.TEXT))).streamingSpeechEligible())
    }
    @Test fun continuationsCannotAcquireEarlySpeechAuthority() {
        val base = prepared()
        for (mode in listOf(CognitiveTurnMode.CONTINUE_AFTER_TOOL, CognitiveTurnMode.RECOVER_PROVIDER))
            assertFalse(base.copy(turn = base.turn.copy(mode = mode)).streamingSpeechEligible())
    }
}
