package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiExecutionTarget
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.runtime.CognitiveTokenReceived
import com.halo.sdk.runtime.EventBus
import com.halo.runtime.provider.response.AiResponseValidator
import com.halo.sdk.provider.validateAgainst
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ProviderTurnResult {
    data class Response(
        val response: AiResponse,
    ) : ProviderTurnResult

    data class HandoffStarted(
        val providerId: String,
        val modelId: String,
    ) : ProviderTurnResult
}

interface ProviderTurnExecutor {
    val streamingText: StateFlow<String?>

    suspend fun execute(
        target: AiExecutionTarget,
        request: AiRequest,
        context: AiContext,
        requestId: String,
        interactionId: InteractionId,
    ): ProviderTurnResult
}

class DefaultProviderTurnExecutor(
    private val eventBus: EventBus
) : ProviderTurnExecutor {
    private val _streamingText = MutableStateFlow<String?>(null)
    override val streamingText = _streamingText.asStateFlow()

    override suspend fun execute(
        target: AiExecutionTarget,
        request: AiRequest,
        context: AiContext,
        requestId: String,
        interactionId: InteractionId,
    ): ProviderTurnResult {
        request.validateAgainst(target.providerCapabilities)
        val provider = target.provider
        val expectedModelId = target.modelId
        
        val response = if (target.providerCapabilities.contains(AiCapability.STREAMING)) {
            var completedResponse: AiResponse? = null
            val accumulatedText = StringBuilder()
            _streamingText.value = ""
            provider.stream(request, context).collect { event ->
                when (event) {
                    is AiStreamEvent.Started ->
                        _streamingText.value = ""

                    is AiStreamEvent.Token -> {
                        accumulatedText.append(event.text)
                        _streamingText.value = accumulatedText.toString()
                        eventBus.publish(
                            CognitiveTokenReceived(requestId = requestId, token = event.text, interactionId = interactionId.value)
                        )
                    }

                    is AiStreamEvent.Completed -> completedResponse = event.response
                    is AiStreamEvent.Failed -> throw event.error
                    is AiStreamEvent.ToolCallDelta -> Unit
                }
            }
            _streamingText.value = null
            completedResponse ?: throw AiProviderException(
                AiErrorType.INVALID_RESPONSE,
                "Provider stream ended without Completed event"
            )
        } else {
            provider.generate(request, context)
        }

        val validated = AiResponseValidator.validate(
            provider = provider,
            capabilities = target.providerCapabilities,
            expectedModelId = expectedModelId,
            response = response
        )

        val isHandoff = validated.finishReason.equals("handoff", ignoreCase = true) ||
            validated.text.equals("HANDOFF_STARTED", ignoreCase = true)
        return if (isHandoff) {
            ProviderTurnResult.HandoffStarted(
                providerId = provider.id,
                modelId = expectedModelId
            )
        } else {
            ProviderTurnResult.Response(validated)
        }
    }
}
