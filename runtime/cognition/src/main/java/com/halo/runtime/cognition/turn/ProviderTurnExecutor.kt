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
import com.halo.sdk.provider.ProviderTurnKind
import com.halo.sdk.provider.ToolUseMode
import com.halo.sdk.provider.validateAgainst
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    private val observationSink: com.halo.runtime.cognition.context.LocalInferenceObservationSink? = null,
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
        return executeWithRetry(target, request, context, requestId, interactionId)
    }

    private suspend fun executeWithRetry(
        target: AiExecutionTarget,
        request: AiRequest,
        context: AiContext,
        requestId: String,
        interactionId: InteractionId,
    ): ProviderTurnResult {
        var attempt = 1
        while (true) {
            println("[HALO_PROVIDER_ATTEMPT] interactionId=${interactionId.value} requestId=$requestId provider=${target.providerId} model=${target.modelId} attempt=$attempt")
            val attemptStartMs = System.currentTimeMillis()
            var textEmitted = false
            var toolDeltaEmitted = false
            try {
                val result = executeAttempt(
                    target = target,
                    request = request,
                    context = context,
                    requestId = requestId,
                    interactionId = interactionId,
                    onTokenEmitted = { textEmitted = true },
                    onToolDeltaEmitted = { toolDeltaEmitted = true },
                )
                val response = (result as? ProviderTurnResult.Response)?.response
                val resolvedModel = response?.resolvedModelId ?: target.modelId
                val durationMs = response?.metrics?.totalDurationMs ?: (System.currentTimeMillis() - attemptStartMs)
                val firstTokenMs = response?.metrics?.timeToFirstTokenMs
                println(
                    "[HALO_PROVIDER_SUCCESS] interactionId=${interactionId.value} requestId=$requestId " +
                        "provider=${target.providerId} requestedModel=${target.modelId} " +
                        "resolvedModel=$resolvedModel attempt=$attempt " +
                        "firstTokenMs=${firstTokenMs ?: "null"} durationMs=$durationMs " +
                        "finishReason=${response?.finishReason ?: "stop"}"
                )
                if (response != null && target.provider.isLocal) recordObservation(
                    com.halo.runtime.cognition.context.LocalInferenceObservation(target.providerId, resolvedModel,
                        response.executionBackend, firstTokenMs, durationMs, response.metrics?.inputTokens ?:
                            ((request.text.orEmpty().length.toLong() + context.conversation.sumOf { it.content.length.toLong() } + 2) / 3).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        response.metrics?.outputTokens, true, response.coldStart, executionIdentity = (target.provider as? com.halo.sdk.provider.LocalInferenceExecutionReporting)?.localExecutionIdentity(resolvedModel)))
                return result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val partialOutput = textEmitted || toolDeltaEmitted
                val aiEx = error as? AiProviderException
                val canRetry = attempt == 1 && maybeRetrySameTarget(request, error, partialOutput)

                if (canRetry) {
                    val backoffMs = 400L
                    println(
                        "[HALO_PROVIDER_RETRY] interactionId=${interactionId.value} requestId=$requestId " +
                            "provider=${target.providerId} model=${target.modelId} fromAttempt=1 " +
                            "reason=${aiEx?.type?.name ?: "UNKNOWN_ERROR"} backoffMs=$backoffMs"
                    )
                    delay(backoffMs)
                    attempt++
                } else {
                    println(
                        "[HALO_PROVIDER_FAILURE] interactionId=${interactionId.value} requestId=$requestId " +
                            "provider=${target.providerId} requestedModel=${target.modelId} " +
                            "resolvedModel=${target.modelId} errorType=${aiEx?.type?.name ?: error.javaClass.simpleName} " +
                            "httpStatus=${aiEx?.details?.httpStatus} providerCode=${aiEx?.details?.providerCode} " +
                            "recoverability=${aiEx?.details?.recoverability?.name} retryAfterMs=${aiEx?.details?.retryAfterMs} " +
                            "attempt=$attempt partialOutput=$partialOutput"
                    )
                    if (target.provider.isLocal) recordObservation(com.halo.runtime.cognition.context.LocalInferenceObservation(
                        target.providerId, target.modelId, backend = (target.provider as? com.halo.sdk.provider.LocalInferenceExecutionReporting)?.localExecutionBackend(target.modelId), totalMs = System.currentTimeMillis() - attemptStartMs, success = false,
                        outcome = when (aiEx?.type) {
                            AiErrorType.MODEL_LOAD_FAILED -> com.halo.runtime.cognition.context.LocalInferenceObservationOutcome.LOAD_FAILURE
                            AiErrorType.TIMEOUT -> com.halo.runtime.cognition.context.LocalInferenceObservationOutcome.TIMEOUT
                            AiErrorType.SESSION_CONFLICT -> com.halo.runtime.cognition.context.LocalInferenceObservationOutcome.RESOURCE_BUSY
                            AiErrorType.CANCELLED -> com.halo.runtime.cognition.context.LocalInferenceObservationOutcome.CANCELLED
                            else -> com.halo.runtime.cognition.context.LocalInferenceObservationOutcome.GENERATION_FAILURE
                        }, executionIdentity = (target.provider as? com.halo.sdk.provider.LocalInferenceExecutionReporting)?.localExecutionIdentity(target.modelId)))
                    throw error
                }
            }
        }
    }

    private suspend fun recordObservation(observation: com.halo.runtime.cognition.context.LocalInferenceObservation) {
        try { observationSink?.record(observation) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Optional telemetry cannot turn successful inference into a failure. */ }
    }

    private fun maybeRetrySameTarget(
        request: AiRequest,
        error: Throwable,
        partialOutput: Boolean,
    ): Boolean {
        if (partialOutput) return false
        if (request.turnKind == ProviderTurnKind.TOOL_CONTINUATION) return false
        val mode = request.toolUseMode ?: ToolUseMode.OPTIONAL
        if (mode != ToolUseMode.NONE) return false
        if (error !is AiProviderException) return false
        return error.details.recoverability == Recoverability.RETRY_IMMEDIATELY
    }

    @OptIn(FlowPreview::class)
    private suspend fun executeAttempt(
        target: AiExecutionTarget,
        request: AiRequest,
        context: AiContext,
        requestId: String,
        interactionId: InteractionId,
        onTokenEmitted: () -> Unit,
        onToolDeltaEmitted: () -> Unit,
    ): ProviderTurnResult {
        val startedAtMs = System.currentTimeMillis()
        request.validateAgainst(target.providerCapabilities)
        val mode = request.toolUseMode ?: com.halo.sdk.provider.ToolUseMode.OPTIONAL
        val canUseTools = AiCapability.TOOLS in target.effectiveCapabilities
        val outboundContext = if (mode == com.halo.sdk.provider.ToolUseMode.NONE || !canUseTools) context.copy(availableTools = emptyList()) else context
        if (mode == com.halo.sdk.provider.ToolUseMode.REQUIRED && outboundContext.availableTools.isEmpty())
            throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "I can't perform that action yet. Choose a tool-capable model and enable the required capability.")
        println("[HALO_PROVIDER_REQUEST_SHAPE] provider=${target.providerId} model=${target.modelId} toolMode=$mode toolsIncluded=${outboundContext.availableTools.isNotEmpty()} toolCount=${outboundContext.availableTools.size} historyMessages=${outboundContext.conversation.size} image=${request.image != null}")
        val provider = target.provider
        val expectedModelId = target.modelId
        
        var firstTokenAtMs: Long? = null
        val response = if (target.providerCapabilities.contains(AiCapability.STREAMING)) {
            var completedResponse: AiResponse? = null
            val accumulatedText = StringBuilder()
            val toolCalls = StreamingToolCallAssembler()
            if (request.publishStreamingTokens) _streamingText.value = ""
            try {
                provider.stream(request, outboundContext).collect { event ->
                    when (event) {
                        is AiStreamEvent.Started ->
                            if (request.publishStreamingTokens) _streamingText.value = ""

                        is AiStreamEvent.Token -> {
                            if (firstTokenAtMs == null) firstTokenAtMs = System.currentTimeMillis()
                            onTokenEmitted()
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
                        is AiStreamEvent.ToolCallDelta -> {
                            onToolDeltaEmitted()
                            toolCalls.appendDelta(event)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val visible = accumulatedText.isNotEmpty() || toolCalls.hasDeltas()
                if (visible && error is AiProviderException) {
                    throw AiProviderException(
                        type = error.type,
                        message = error.message ?: "Provider stream failed after partial output",
                        cause = error,
                        details = error.details.copy(
                            providerCode = "STREAM_FAILED_AFTER_OUTPUT",
                            recoverability = Recoverability.PERMANENT,
                        ),
                    )
                }
                throw error
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
        val completedAtMs = System.currentTimeMillis()
        val metrics = validatedResponse.metrics
        val metricsWithTtft = if (metrics == null) {
            AiGenerationMetrics(
                startedAtMs = startedAtMs,
                firstTokenAtMs = firstTokenAtMs,
                completedAtMs = completedAtMs,
            )
        } else {
            metrics.copy(
                firstTokenAtMs = metrics.firstTokenAtMs ?: firstTokenAtMs,
                completedAtMs = metrics.completedAtMs ?: completedAtMs,
            )
        }
        val validated = validatedResponse.copy(metrics = metricsWithTtft)

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
