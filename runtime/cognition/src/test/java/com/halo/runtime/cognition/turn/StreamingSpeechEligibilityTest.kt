package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.context.ContextAcquisitionPlan
import com.halo.runtime.cognition.workload.CognitiveWorkloadPlan
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.*
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
