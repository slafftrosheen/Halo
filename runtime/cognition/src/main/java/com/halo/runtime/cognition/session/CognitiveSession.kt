package com.halo.runtime.cognition.session

import com.halo.cognition.language.ConversationIntent
import com.halo.cognition.language.QuestionIntent
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.blackboard.InterpretationKnowledge
import com.halo.runtime.cognition.context.ContextAssembler
import com.halo.runtime.cognition.context.ContextAssemblyResult
import com.halo.runtime.cognition.conversation.ConversationStore
import com.halo.runtime.provider.response.AiResponseValidator
import com.halo.runtime.cognition.tools.ToolAuthorization
import com.halo.runtime.cognition.tools.ToolAuthorizer
import com.halo.runtime.cognition.tools.ToolExecutionLimits
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiExecutionOutcome
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.validateAgainst
import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.runtime.CognitiveCompleted
import com.halo.sdk.runtime.CognitiveFailed
import com.halo.sdk.runtime.CognitiveReasoningStarted
import com.halo.sdk.runtime.CognitiveRequestStarted
import com.halo.sdk.runtime.CognitiveTokenReceived
import com.halo.sdk.runtime.ContextAssembled
import com.halo.sdk.runtime.ConversationTurnCompleted
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.SpeechRecognized
import com.halo.sdk.runtime.ToolCallAuthorized
import com.halo.sdk.runtime.ToolCallCompleted
import com.halo.sdk.runtime.ToolCallRejected
import com.halo.sdk.runtime.ToolCallRequested
import com.halo.sdk.runtime.TtsRequested
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages one immutable cognitive transaction at a time.
 *
 * Provider/model selection happens once per request.
 * Tool rounds never return to the router.
 */
class CognitiveSession(
    private val eventBus: EventBus,
    private val providerRouter: AiProviderRouter,
    private val conversationStore: ConversationStore,
    private val contextAssembler: ContextAssembler,
    private val toolRegistry: ToolRegistry? = null,
    private val toolAuthorizer: ToolAuthorizer? = null,
    private val scope: CoroutineScope,
    private val blackboard: Blackboard = DefaultBlackboard()
) {
    private val _state = MutableStateFlow<CognitiveSessionState>(CognitiveSessionState.Idle)
    val state: StateFlow<CognitiveSessionState> = _state.asStateFlow()
    private var activeJob: Job? = null

    private val observerScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    init {
        observerScope.launch {
            blackboard.stream().collect { knowledge ->
                if (knowledge.payload is InterpretationKnowledge) {
                    val interpretation = (knowledge.payload as InterpretationKnowledge).interpretation
                    val intent = interpretation.intent
                    
                    // Only process conversational intents through the generic LLM session.
                    // Structured intents (Play, Navigate, etc.) are handled by deterministic reasoning.
                    if (intent !is ConversationIntent && 
                        intent !is QuestionIntent) {
                        return@collect
                    }

                    val text = interpretation.transcript.trim()
                    if (text.isEmpty()) {
                        return@collect
                    }

                    if (activeJob?.isActive == true) {
                        return@collect
                    }

                    submit(
                        AiRequest(text = text)
                    )
                }
            }
        }
    }

    fun submit(request: AiRequest, policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE) {
        activeJob?.cancel()
        activeJob = scope.launch {
            val requestId = UUID.randomUUID().toString()
            try {
                val target = providerRouter.resolveExecutionTarget(request)
                val provider = target.provider
                val finalRequest = request.copy(modelId = target.modelId)
                val capabilities = target.providerCapabilities

                _state.value = CognitiveSessionState.PreparingContext
                eventBus.publish(CognitiveRequestStarted(requestId = requestId))

                val assembly = contextAssembler.assemble(policy = policy, capabilities = capabilities)
                val context = assembly.context
                val requestWithContext = bindAssembledContext(finalRequest, assembly)

                eventBus.publish(
                    ContextAssembled(
                        requestId = requestId,
                        conversationCount = context.conversation.size,
                        recentEventCount = context.recentEvents.size,
                        memoryCount = context.memories.size,
                        toolCount = context.availableTools.size,
                        omissions = assembly.omissions.size
                    )
                )

                _state.value = CognitiveSessionState.Reasoning(target.providerId, target.modelId)
                eventBus.publish(
                    CognitiveReasoningStarted(
                        requestId = requestId,
                        providerId = target.providerId,
                        modelId = target.modelId
                    )
                )

                var response = when (val outcome = executeProviderRequest(
                    provider = provider,
                    capabilities = capabilities,
                    expectedModelId = target.modelId,
                    request = requestWithContext,
                    context = context,
                    requestId = requestId
                )) {
                    is AiExecutionOutcome.Response -> outcome.response
                    is AiExecutionOutcome.HandoffStarted -> {
                        _state.value = CognitiveSessionState.Completed(
                            AiResponse(
                                text = "",
                                providerId = outcome.providerId,
                                modelId = outcome.modelId,
                                finishReason = "handoff"
                            )
                        )
                        eventBus.publish(CognitiveCompleted(requestId = requestId, response = ""))
                        return@launch
                    }
                }

                if (response.toolCalls.isNotEmpty()) {
                    response = executeToolLoop(
                        requestId = requestId,
                        target = target,
                        request = requestWithContext,
                        initialContext = context,
                        initialResponse = response,
                        provider = provider,
                        capabilities = capabilities,
                        policy = policy
                    )
                }

                response = AiResponseValidator.validate(
                    provider = provider,
                    capabilities = capabilities,
                    expectedModelId = target.modelId,
                    response = response
                )
                _state.value = CognitiveSessionState.Completed(response)
                eventBus.publish(CognitiveCompleted(requestId = requestId, response = response.text))

                val responseText = response.text.trim()
                val userText = finalRequest.text?.trim().orEmpty()
                if (responseText.isNotBlank()) {
                    if (userText.isNotBlank()) {
                        val turn = conversationStore.append(userText = userText, assistantText = responseText)
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
            } catch (e: CancellationException) {
                _state.value = CognitiveSessionState.Idle
                throw e
            } catch (e: Exception) {
                val message = mapErrorMessage(e)
                _state.value = CognitiveSessionState.Failed(message)
                eventBus.publish(CognitiveFailed(requestId = requestId, message = message))
            }
        }
    }

    private fun bindAssembledContext(request: AiRequest, assembly: ContextAssemblyResult): AiRequest {
        val image = assembly.image ?: return request
        if (request.image != null) return request
        return request.copy(image = image.imageBytes, mimeTypes = listOf(image.mimeType))
    }

    private suspend fun executeProviderRequest(
        provider: AiInferenceProvider,
        capabilities: Set<AiCapability>,
        expectedModelId: String,
        request: AiRequest,
        context: AiContext,
        requestId: String
    ): AiExecutionOutcome {
        request.validateAgainst(capabilities)
        val response = if (capabilities.contains(AiCapability.STREAMING)) {
            var completedResponse: AiResponse? = null
            val accumulatedText = StringBuilder()
            provider.stream(request, context).collect { event ->
                when (event) {
                    is AiStreamEvent.Started -> _state.value = CognitiveSessionState.Streaming("")
                    is AiStreamEvent.Token -> {
                        accumulatedText.append(event.text)
                        _state.value = CognitiveSessionState.Streaming(accumulatedText.toString())
                        eventBus.publish(CognitiveTokenReceived(requestId = requestId, token = event.text))
                    }
                    is AiStreamEvent.Completed -> completedResponse = event.response
                    is AiStreamEvent.Failed -> throw event.error
                    is AiStreamEvent.ToolCallDelta -> Unit
                }
            }
            completedResponse ?: throw AiProviderException(
                AiErrorType.INVALID_RESPONSE,
                "Provider stream ended without Completed event"
            )
        } else {
            provider.generate(request, context)
        }

        val validated = AiResponseValidator.validate(
            provider = provider,
            capabilities = capabilities,
            expectedModelId = expectedModelId,
            response = response
        )

        val isHandoff = validated.finishReason.equals("handoff", ignoreCase = true) ||
            validated.text.equals("HANDOFF_STARTED", ignoreCase = true)
        return if (isHandoff) {
            AiExecutionOutcome.HandoffStarted(
                providerId = provider.id,
                modelId = expectedModelId
            )
        } else {
            AiExecutionOutcome.Response(validated)
        }
    }

    private suspend fun executeToolLoop(
        requestId: String,
        target: com.halo.sdk.provider.AiExecutionTarget,
        request: AiRequest,
        initialContext: AiContext,
        initialResponse: AiResponse,
        provider: AiInferenceProvider,
        capabilities: Set<AiCapability>,
        policy: AiContextPolicy
    ): AiResponse {
        var response = initialResponse
        var currentContext = initialContext
        var round = 0
        var totalToolCalls = 0

        while (response.toolCalls.isNotEmpty()) {
            round++
            if (round > ToolExecutionLimits.MAX_TOOL_ROUNDS) {
                throw AiProviderException(AiErrorType.GENERATION_FAILED, "Maximum tool execution rounds (${ToolExecutionLimits.MAX_TOOL_ROUNDS}) exceeded")
            }
            if (response.toolCalls.size > ToolExecutionLimits.MAX_TOOL_CALLS_PER_ROUND) {
                throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Provider returned ${response.toolCalls.size} tool calls; maximum is ${ToolExecutionLimits.MAX_TOOL_CALLS_PER_ROUND}")
            }

            val calls = response.toolCalls
            _state.value = CognitiveSessionState.ExecutingTool(calls.first().functionName)
            val toolMessages = mutableListOf<AIMessage>()

            for (toolCall in calls) {
                totalToolCalls++
                if (totalToolCalls > ToolExecutionLimits.MAX_TOTAL_TOOL_CALLS) {
                    throw AiProviderException(AiErrorType.GENERATION_FAILED, "Maximum total tool calls (${ToolExecutionLimits.MAX_TOTAL_TOOL_CALLS}) exceeded")
                }
                eventBus.publish(ToolCallRequested(requestId = requestId, toolCallId = toolCall.id, toolId = toolCall.functionName))

                if (!policy.includeAvailableTools || toolRegistry == null || toolAuthorizer == null) {
                    val reason = when {
                        !policy.includeAvailableTools -> "Tools disabled by runtime policy"
                        toolRegistry == null -> "Tool registry unavailable"
                        else -> "Tool authorization service unavailable"
                    }
                    eventBus.publish(ToolCallRejected(requestId = requestId, toolCallId = toolCall.id, toolId = toolCall.functionName, reason = reason))
                    toolMessages.add(AIMessage(Role.TOOL, "Tool execution rejected: $reason", toolCallId = toolCall.id))
                    continue
                }

                val authorization = toolAuthorizer.authorize(toolCall)
                when (authorization) {
                    is ToolAuthorization.Rejected -> {
                        eventBus.publish(ToolCallRejected(requestId = requestId, toolCallId = toolCall.id, toolId = toolCall.functionName, reason = authorization.reason))
                        toolMessages.add(AIMessage(Role.TOOL, "Tool execution rejected: ${authorization.reason}", toolCallId = toolCall.id))
                        continue
                    }
                    is ToolAuthorization.Authorized -> {
                        eventBus.publish(ToolCallAuthorized(requestId = requestId, toolCallId = toolCall.id, toolId = toolCall.functionName))
                    }
                }

                val authorized = authorization as ToolAuthorization.Authorized
                when (val result = toolRegistry.executeAuthorized(authorized)) {
                    is ToolExecutionResult.Success -> {
                        eventBus.publish(ToolCallCompleted(requestId = requestId, toolCallId = toolCall.id, toolId = toolCall.functionName, success = true))
                        toolMessages.add(AIMessage(Role.TOOL, result.output.take(ToolExecutionLimits.MAX_TOOL_OUTPUT_CHARS), toolCallId = toolCall.id))
                    }
                    is ToolExecutionResult.Rejected -> {
                        eventBus.publish(ToolCallRejected(requestId = requestId, toolCallId = toolCall.id, toolId = toolCall.functionName, reason = result.reason))
                        toolMessages.add(AIMessage(Role.TOOL, "Tool execution rejected: ${result.reason}", toolCallId = toolCall.id))
                    }
                    is ToolExecutionResult.Failed -> {
                        eventBus.publish(ToolCallCompleted(requestId = requestId, toolCallId = toolCall.id, toolId = toolCall.functionName, success = false))
                        toolMessages.add(AIMessage(Role.TOOL, "Tool execution failed: ${result.message}", toolCallId = toolCall.id))
                    }
                }
            }

            currentContext = currentContext.copy(
                conversation = buildList {
                    addAll(currentContext.conversation)
                    if (response.text.isNotBlank() || response.toolCalls.isNotEmpty()) {
                        add(AIMessage(Role.ASSISTANT, response.text, toolCalls = response.toolCalls))
                    }
                    addAll(toolMessages)
                }
            )

            response = when (val outcome = executeProviderRequest(
                provider = provider,
                capabilities = capabilities,
                expectedModelId = target.modelId,
                request = request.copy(modelId = target.modelId),
                context = currentContext,
                requestId = requestId
            )) {
                is AiExecutionOutcome.Response -> outcome.response
                is AiExecutionOutcome.HandoffStarted -> throw AiProviderException(
                    AiErrorType.INVALID_RESPONSE,
                    "Assistant handoff cannot occur during a tool execution transaction"
                )
            }
        }
        return response
    }

    private fun mapErrorMessage(e: Exception): String {
        if (e is AiProviderException) return e.message ?: "AI provider request failed"
        val message = e.message.orEmpty()
        return when {
            message.contains("401", ignoreCase = true) -> "Invalid API key"
            message.contains("403", ignoreCase = true) -> "Access denied"
            message.contains("429", ignoreCase = true) -> "Provider rate limit exceeded"
            message.contains("Unable to resolve host", ignoreCase = true) -> "Cannot reach provider"
            else -> "Intelligence provider failed: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        _state.value = CognitiveSessionState.Idle
    }

    fun clearConversation() {
        scope.launch { conversationStore.clear() }
    }
}
