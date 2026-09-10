package com.halo.runtime.cognition.session

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import com.halo.runtime.cognition.context.ContextAssembler
import com.halo.runtime.cognition.conversation.ConversationStore
import com.halo.runtime.cognition.tools.InMemoryToolAuthorizer
import com.halo.runtime.cognition.tools.ToolAuthorization
import com.halo.runtime.cognition.tools.ToolAuthorizer
import com.halo.runtime.cognition.tools.ToolExecutionLimits
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages the lifecycle of a single cognitive interaction.
 */
class CognitiveSession(
    private val eventBus: EventBus,
    private val providerRouter: AiProviderRouter,
    private val conversationStore: ConversationStore,
    private val contextAssembler: ContextAssembler,
    private val toolRegistry: ToolRegistry? = null,
    private val toolAuthorizer: ToolAuthorizer = InMemoryToolAuthorizer(),
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<CognitiveSessionState>(CognitiveSessionState.Idle)
    val state: StateFlow<CognitiveSessionState> = _state.asStateFlow()

    private var activeJob: Job? = null

    init {
        scope.launch {
            eventBus.observe(SpeechRecognized::class.java).collect { speech ->
                if (activeJob?.isActive == true) {
                    println("[CognitiveSession] Ignoring speech during active AI generation")
                    return@collect
                }
                val text = speech.text.trim()
                if (text.isNotEmpty()) {
                    println("[CognitiveSession] Auto-submitting recognized speech")
                    submit(AiRequest(text = text))
                }
            }
        }
    }

    fun submit(request: AiRequest, policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE) {
        println("[CognitiveSession] submit request")
        activeJob?.cancel()
        activeJob = scope.launch {
            val requestId = UUID.randomUUID().toString()
            try {
                val finalRequest = request

                // 1. Resolve Target & Provider once (Immutable for entire request)
                val provider = providerRouter.getEligibleProvider(finalRequest)
                val target = AiExecutionTarget(
                    providerId = provider.id,
                    modelId = finalRequest.modelId ?: providerRouter.selectedModelId.value
                )
                val requestForExecution = finalRequest.copy(modelId = target.modelId)

                // 2. Preparing Context
                println("[CognitiveSession] Preparing context")
                _state.value = CognitiveSessionState.PreparingContext
                eventBus.publish(CognitiveRequestStarted(requestId = requestId))

                val assemblyResult = contextAssembler.assemble(policy, provider.getCapabilities())
                val context = assemblyResult.context
                eventBus.publish(
                    ContextAssembled(
                        requestId = requestId,
                        conversationCount = context.conversation.size,
                        recentEventCount = context.recentEvents.size,
                        memoryCount = context.memories.size,
                        toolCount = context.availableTools.size,
                        omissions = assemblyResult.omissions.size
                    )
                )

                // 3. Reasoning
                println("[CognitiveSession] Provider: ${target.providerId}, Model: ${target.modelId}")
                _state.value = CognitiveSessionState.Reasoning(target.providerId, target.modelId)
                eventBus.publish(CognitiveReasoningStarted(
                    requestId = requestId,
                    providerId = target.providerId,
                    modelId = target.modelId
                ))

                val initialResponse = executeDirect(provider, requestForExecution, context, requestId)

                // 4. Handle Tool Calls Loop against same Provider target
                val response = if (initialResponse.toolCalls.isNotEmpty()) {
                    executeToolLoop(requestId, requestForExecution, context, initialResponse, provider, policy)
                } else {
                    initialResponse
                }

                _state.value = CognitiveSessionState.Completed(response)
                eventBus.publish(CognitiveCompleted(requestId = requestId, response = response.text))

                val responseText = response.text.trim()
                val userText = requestForExecution.text?.trim().orEmpty()
                if (responseText.isNotBlank() && responseText != "HANDOFF_STARTED") {
                    if (userText.isNotBlank()) {
                        val turn = conversationStore.append(
                            userText = userText,
                            assistantText = responseText
                        )
                        eventBus.publish(
                            ConversationTurnCompleted(
                                turnId = turn.id,
                                userText = turn.userText,
                                assistantText = turn.assistantText
                            )
                        )
                    }
                    eventBus.publish(TtsRequested(text = responseText, timestamp = System.currentTimeMillis()))
                }
                println("[CognitiveSession] Generation completed")
            } catch (e: CancellationException) {
                _state.value = CognitiveSessionState.Idle
                throw e
            } catch (e: Exception) {
                println("[CognitiveSession] Session failed: ${e.message}")
                val message = mapErrorMessage(e)
                _state.value = CognitiveSessionState.Failed(message)
                eventBus.publish(CognitiveFailed(requestId = requestId, message = message))
            }
        }
    }

    private suspend fun executeDirect(
        provider: AiProvider,
        request: AiRequest,
        context: AiContext,
        requestId: String
    ): AiResponse {
        val supportsStreaming = provider.getCapabilities().contains(AiCapability.STREAMING)
        val response = if (supportsStreaming) {
            println("[CognitiveSession] Starting streaming")
            var completedResponse: AiResponse? = null
            var accumulatedText = ""

            provider.stream(request, context)
                .collect { event ->
                    when (event) {
                        is AiStreamEvent.Started -> {
                            _state.value = CognitiveSessionState.Streaming("")
                        }
                        is AiStreamEvent.Token -> {
                            accumulatedText += event.text
                            _state.value = CognitiveSessionState.Streaming(accumulatedText)
                            eventBus.publish(CognitiveTokenReceived(requestId = requestId, token = event.text))
                        }
                        is AiStreamEvent.Completed -> {
                            completedResponse = event.response
                        }
                        is AiStreamEvent.Failed -> throw event.error
                        is AiStreamEvent.ToolCallDelta -> {}
                    }
                }

            val streamResp = completedResponse
                ?: throw AiProviderException(
                    AiErrorType.INVALID_RESPONSE,
                    "Provider stream ended without Completed event"
                )

            if (streamResp.text.isBlank() && streamResp.toolCalls.isEmpty()) {
                throw AiProviderException(
                    AiErrorType.EMPTY_RESPONSE,
                    "Provider stream completed with no usable response"
                )
            }
            streamResp
        } else {
            println("[CognitiveSession] Executing one-shot")
            val resp = provider.generate(request, context)
            if (resp.text.isBlank() && resp.toolCalls.isEmpty()) {
                throw AiProviderException(
                    AiErrorType.EMPTY_RESPONSE,
                    "Provider returned no usable response"
                )
            }
            resp
        }

        AiResponseValidator.validate(provider, response)
        return response
    }

    private suspend fun executeToolLoop(
        requestId: String,
        initialRequest: AiRequest,
        initialContext: AiContext,
        initialResponse: AiResponse,
        provider: AiProvider,
        policy: AiContextPolicy
    ): AiResponse {
        var response = initialResponse
        var currentContext = initialContext
        var round = 0
        var totalToolCalls = 0

        while (response.toolCalls.isNotEmpty()) {
            if (++round > ToolExecutionLimits.MAX_TOOL_ROUNDS) {
                throw AiProviderException(
                    AiErrorType.GENERATION_FAILED,
                    "Maximum tool execution rounds (${ToolExecutionLimits.MAX_TOOL_ROUNDS}) exceeded"
                )
            }

            _state.value = CognitiveSessionState.ExecutingTool(response.toolCalls.first().functionName)

            val toolMessages = mutableListOf<AIMessage>()
            val callsToExecute = response.toolCalls.take(ToolExecutionLimits.MAX_TOOL_CALLS_PER_ROUND)

            for (toolCall in callsToExecute) {
                if (++totalToolCalls > ToolExecutionLimits.MAX_TOTAL_TOOL_CALLS) {
                    throw AiProviderException(
                        AiErrorType.GENERATION_FAILED,
                        "Maximum total tool calls (${ToolExecutionLimits.MAX_TOTAL_TOOL_CALLS}) exceeded"
                    )
                }

                eventBus.publish(
                    ToolCallRequested(
                        requestId = requestId,
                        toolCallId = toolCall.id,
                        toolId = toolCall.functionName
                    )
                )

                if (!policy.includeAvailableTools || toolRegistry == null) {
                    eventBus.publish(
                        ToolCallRejected(
                            requestId = requestId,
                            toolCallId = toolCall.id,
                            toolId = toolCall.functionName,
                            reason = "Tools disabled by runtime policy"
                        )
                    )
                    toolMessages.add(
                        AIMessage(
                            role = Role.TOOL,
                            content = "Tool execution rejected: Tools disabled by runtime policy"
                        )
                    )
                    continue
                }

                // 1. ToolRegistry Validation
                val validationFailure = toolRegistry.validate(toolCall)
                if (validationFailure != null) {
                    eventBus.publish(
                        ToolCallRejected(
                            requestId = requestId,
                            toolCallId = toolCall.id,
                            toolId = toolCall.functionName,
                            reason = validationFailure.reason
                        )
                    )
                    toolMessages.add(
                        AIMessage(
                            role = Role.TOOL,
                            content = "Tool execution rejected: ${validationFailure.reason}"
                        )
                    )
                    continue
                }

                // 2. ToolAuthorizer Authorization
                val authResult = toolAuthorizer.authorize(toolCall)
                if (authResult is ToolAuthorization.Rejected) {
                    eventBus.publish(
                        ToolCallRejected(
                            requestId = requestId,
                            toolCallId = toolCall.id,
                            toolId = toolCall.functionName,
                            reason = authResult.reason
                        )
                    )
                    toolMessages.add(
                        AIMessage(
                            role = Role.TOOL,
                            content = "Tool execution rejected: ${authResult.reason}"
                        )
                    )
                    continue
                }

                // 3. Emit ToolCallAuthorized ONLY after real authorization
                eventBus.publish(
                    ToolCallAuthorized(
                        requestId = requestId,
                        toolCallId = toolCall.id,
                        toolId = toolCall.functionName
                    )
                )

                // 4. Execute
                val result = toolRegistry.execute(toolCall)

                when (result) {
                    is ToolExecutionResult.Success -> {
                        eventBus.publish(
                            ToolCallCompleted(
                                requestId = requestId,
                                toolCallId = toolCall.id,
                                toolId = toolCall.functionName,
                                success = true
                            )
                        )
                        toolMessages.add(
                            AIMessage(
                                role = Role.TOOL,
                                content = result.output.take(ToolExecutionLimits.MAX_TOOL_OUTPUT_CHARS)
                            )
                        )
                    }
                    is ToolExecutionResult.Rejected -> {
                        eventBus.publish(
                            ToolCallRejected(
                                requestId = requestId,
                                toolCallId = toolCall.id,
                                toolId = toolCall.functionName,
                                reason = result.reason
                            )
                        )
                        toolMessages.add(
                            AIMessage(
                                role = Role.TOOL,
                                content = "Tool execution rejected: ${result.reason}"
                            )
                        )
                    }
                    is ToolExecutionResult.Failed -> {
                        eventBus.publish(
                            ToolCallCompleted(
                                requestId = requestId,
                                toolCallId = toolCall.id,
                                toolId = toolCall.functionName,
                                success = false
                            )
                        )
                        toolMessages.add(
                            AIMessage(
                                role = Role.TOOL,
                                content = "Tool execution failed: ${result.message}"
                            )
                        )
                    }
                }
            }

            val updatedConversation = currentContext.conversation + AIMessage(Role.ASSISTANT, response.text) + toolMessages
            currentContext = currentContext.copy(conversation = updatedConversation)

            _state.value = CognitiveSessionState.Reasoning(provider.id, response.modelId)
            // Follow-up generation directly against same provider without re-entering router
            response = executeDirect(provider, initialRequest, currentContext, requestId)
        }

        return response
    }

    private fun mapErrorMessage(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            e is AiProviderException -> msg
            msg.contains("401") -> "Invalid API key"
            msg.contains("403") -> "Access denied"
            msg.contains("429") -> "Provider rate limit exceeded"
            msg.contains("Unable to resolve host") -> "Cannot reach provider"
            else -> "Intelligence provider failed: ${e.localizedMessage}"
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        _state.value = CognitiveSessionState.Idle
    }

    fun clearConversation() {
        scope.launch {
            conversationStore.clear()
        }
    }
}
