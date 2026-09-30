package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiExecutionTarget
import com.halo.sdk.provider.AiGenerationMetrics
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiFailureDetails
import com.halo.sdk.provider.Recoverability
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AiWorkloadSpec
import com.halo.sdk.provider.StreamingToolCallAssembler
import com.halo.sdk.provider.StreamToolCallException
import com.halo.sdk.runtime.CognitiveTokenReceived
import com.halo.sdk.runtime.EventBus
import com.halo.runtime.provider.response.AiResponseValidator
import com.halo.sdk.provider.validateAgainst
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ProviderTurnResult {
    data class Response(
        val response: AiResponse,
        val target: AiExecutionTarget? = null,
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

    suspend fun execute(
        target: AiExecutionTarget,
        request: AiRequest,
        context: AiContext,
        requestId: String,
        interactionId: InteractionId,
        workload: AiWorkloadSpec,
    ): ProviderTurnResult = execute(target, request, context, requestId, interactionId)
}

class DefaultProviderTurnExecutor(
    private val eventBus: EventBus,
    private val providerRouter: AiProviderRouter? = null,
) : ProviderTurnExecutor {
    private val _streamingText = MutableStateFlow<String?>(null)
    override val streamingText = _streamingText.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override suspend fun execute(
        target: AiExecutionTarget,
        request: AiRequest,
        context: AiContext,
        requestId: String,
        interactionId: InteractionId,
    ): ProviderTurnResult {
        val startedAtMs = System.currentTimeMillis()
        request.validateAgainst(target.providerCapabilities)
        val mode = request.toolUseMode ?: com.halo.sdk.provider.ToolUseMode.OPTIONAL
        val canUseTools = AiCapability.TOOLS in target.effectiveCapabilities
        val outboundContext = if (mode == com.halo.sdk.provider.ToolUseMode.NONE || !canUseTools) context.copy(availableTools = emptyList()) else context
        if (mode == com.halo.sdk.provider.ToolUseMode.REQUIRED && outboundContext.availableTools.isEmpty())
            throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "Required actions are unavailable for this target")
        println("[HALO_PROVIDER_REQUEST_SHAPE] provider=${target.providerId} model=${target.modelId} toolMode=$mode toolsIncluded=${outboundContext.availableTools.isNotEmpty()} toolCount=${outboundContext.availableTools.size} historyMessages=${outboundContext.conversation.size} image=${request.image != null}")
        val provider = target.provider
        val expectedModelId = target.modelId
        
        val response = if (target.providerCapabilities.contains(AiCapability.STREAMING)) {
            var completedResponse: AiResponse? = null
            val accumulatedText = StringBuilder()
            val toolCalls = StreamingToolCallAssembler()
            if (request.publishStreamingTokens) _streamingText.value = ""
            try {
                coroutineScope {
                    val events = provider.stream(request, outboundContext).produceIn(this)
                    var receivedAnyEvent = false
                    while (true) {
                        val timeoutMs = if (receivedAnyEvent) INTER_EVENT_TIMEOUT_MS else FIRST_EVENT_TIMEOUT_MS
                        val next = withTimeoutOrNull(timeoutMs) { events.receiveCatching() }
                            ?: throw streamTimeout(accumulatedText.isNotEmpty(), toolCalls.hasDeltas(), timeoutMs)
                        next.exceptionOrNull()?.let { throw it }
                        if (next.isClosed) break
                        receivedAnyEvent = true
                        val event = next.getOrThrow()
                        when (event) {
                            is AiStreamEvent.Started ->
                                if (request.publishStreamingTokens) _streamingText.value = ""

                            is AiStreamEvent.Token -> {
                                accumulatedText.append(event.text)
                                if (request.publishStreamingTokens) {
                                    _streamingText.value = accumulatedText.toString()
                                    eventBus.publish(
                                        CognitiveTokenReceived(requestId = requestId, token = event.text, interactionId = interactionId.value)
                                    )
                                }
                            }

                            is AiStreamEvent.Completed -> completedResponse = event.response
                            is AiStreamEvent.Failed -> {
                                val visible = accumulatedText.isNotEmpty() || toolCalls.hasDeltas()
                                val failure = event.error
                                if (visible && failure is AiProviderException) {
                                    throw AiProviderException(
                                        type = failure.type,
                                        message = failure.message ?: "Provider stream failed after partial output",
                                        cause = failure,
                                        details = failure.details.copy(
                                            providerCode = "STREAM_FAILED_AFTER_OUTPUT",
                                            recoverability = Recoverability.PERMANENT,
                                        ),
                                    )
                                }
                                throw failure
                            }
                            is AiStreamEvent.ToolCallDelta -> toolCalls.appendDelta(event)
                        }
                    }
                    events.cancel()
                }
            } finally {
                if (request.publishStreamingTokens) _streamingText.value = null
            }
            val completed = completedResponse ?: throw AiProviderException(
                AiErrorType.INVALID_RESPONSE,
                "Provider stream ended without Completed event"
            )
            val assembledCalls = try {
                toolCalls.assemble(requestId)
            } catch (error: StreamToolCallException) {
                throw AiProviderException(AiErrorType.INVALID_RESPONSE, error.message ?: "Invalid streamed tool call", error)
            }
            val effectiveCalls = if (completed.toolCalls.isNotEmpty()) completed.toolCalls else assembledCalls
            if (completed.finishReason.equals("tool_calls", ignoreCase = true) && effectiveCalls.isEmpty()) {
                throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Provider finished with tool_calls but supplied no complete tool call")
            }
            if (assembledCalls.isEmpty() || completed.toolCalls.isNotEmpty()) completed else completed.copy(
                toolCalls = assembledCalls,
                finishReason = "tool_calls"
            )
        } else {
            provider.generate(request, outboundContext)
        }

        val validatedResponse = AiResponseValidator.validate(
            provider = provider,
            capabilities = target.providerCapabilities,
            expectedModelId = expectedModelId,
            response = response
        )
        val validated = if (validatedResponse.metrics == null) {
            validatedResponse.copy(
                metrics = AiGenerationMetrics(
                    startedAtMs = startedAtMs,
                    completedAtMs = System.currentTimeMillis(),
                )
            )
        } else {
            validatedResponse
        }

        val isHandoff = validated.finishReason.equals("handoff", ignoreCase = true) ||
            validated.text.equals("HANDOFF_STARTED", ignoreCase = true)
        return if (isHandoff) {
            ProviderTurnResult.HandoffStarted(
                providerId = provider.id,
                modelId = expectedModelId
            )
        } else {
            ProviderTurnResult.Response(validated, target)
        }
    }

    override suspend fun execute(
        target: AiExecutionTarget,
        request: AiRequest,
        context: AiContext,
        requestId: String,
        interactionId: InteractionId,
        workload: AiWorkloadSpec,
    ): ProviderTurnResult {
        println("[HALO_TURN_MODE] interactionId=${interactionId.value} mode=${if (workload.toolUseMode == com.halo.sdk.provider.ToolUseMode.NONE) "DIRECT_RESPONSE" else "AGENTIC"} workloadKind=${workload.kind} toolUseMode=${workload.toolUseMode} provider=${target.providerId} model=${target.modelId}")
        return try {
            execute(target, request, context, requestId, interactionId)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (!workload.allowFallback || request.turnKind == com.halo.sdk.provider.ProviderTurnKind.TOOL_CONTINUATION) throw error
            val providerCode = (error as? AiProviderException)?.details?.providerCode
            if (providerCode == "STREAM_IDLE_AFTER_OUTPUT" ||
                providerCode == "STREAM_FAILED_AFTER_OUTPUT" ||
                !fallbackEligible(error)
            ) throw error

            val fallback = providerRouter?.resolveFallbackExecutionTarget(
                request = request,
                workload = workload,
                failedProviderId = target.providerId,
            ) ?: throw error

            println("[HALO_TURN_MODE] interactionId=${interactionId.value} mode=${if (workload.toolUseMode == com.halo.sdk.provider.ToolUseMode.NONE) "DIRECT_RESPONSE" else "AGENTIC"} workloadKind=${workload.kind} toolUseMode=${workload.toolUseMode} provider=${fallback.providerId} model=${fallback.modelId}")
            execute(
                target = fallback,
                request = request.copy(modelId = fallback.modelId),
                context = context,
                requestId = requestId,
                interactionId = interactionId,
            )
        }
    }

    private fun fallbackEligible(error: Throwable): Boolean =
        error is AiProviderException && error.type in setOf(
            AiErrorType.PROVIDER_UNAVAILABLE,
            AiErrorType.MODEL_UNAVAILABLE,
            AiErrorType.MODEL_LOAD_FAILED,
            AiErrorType.MODEL_NOT_READY,
            AiErrorType.GENERATION_FAILED,
            AiErrorType.EMPTY_RESPONSE,
            AiErrorType.TIMEOUT,
            AiErrorType.NETWORK_UNAVAILABLE,
            AiErrorType.NETWORK_ERROR,
            AiErrorType.LOCAL_MODEL_UNAVAILABLE,
            AiErrorType.UNKNOWN_ERROR,
        )

    private fun streamTimeout(partialTextVisible: Boolean, toolCallStarted: Boolean, timeoutMs: Long): AiProviderException {
        val externallyVisible = partialTextVisible || toolCallStarted
        return AiProviderException(
            type = AiErrorType.TIMEOUT,
            message = if (externallyVisible) {
                "Provider stream became idle after partial output; automatic retry is unsafe"
            } else {
                "Provider stream produced no progress for $timeoutMs ms"
            },
            details = AiFailureDetails(
                providerCode = if (externallyVisible) "STREAM_IDLE_AFTER_OUTPUT" else "STREAM_IDLE_BEFORE_OUTPUT",
                recoverability = if (externallyVisible) Recoverability.PERMANENT else Recoverability.RETRY_IMMEDIATELY,
            ),
        )
    }

    private companion object {
        const val FIRST_EVENT_TIMEOUT_MS = 30_000L
        const val INTER_EVENT_TIMEOUT_MS = 120_000L
    }
}
