package com.halo.runtime.cognition.session

import com.halo.runtime.cognition.context.ContextAssembler
import com.halo.runtime.cognition.conversation.ConversationStore
import com.halo.runtime.cognition.tools.ToolAuthorization
import com.halo.runtime.cognition.tools.ToolAuthorizer
import com.halo.runtime.cognition.tools.ToolExecutionLimits
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiResponseValidator
import com.halo.sdk.provider.AiStreamEvent
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val scope: CoroutineScope
) {

    private val _state =
        MutableStateFlow<CognitiveSessionState>(CognitiveSessionState.Idle)

    val state: StateFlow<CognitiveSessionState> =
        _state.asStateFlow()

    private var activeJob: Job? = null

    init {
        scope.launch {
            eventBus
                .observe(SpeechRecognized::class.java)
                .collect { speech ->

                    if (activeJob?.isActive == true) {
                        return@collect
                    }

                    val text = speech.text.trim()

                    if (text.isNotEmpty()) {
                        submit(
                            AiRequest(text = text)
                        )
                    }
                }
        }
    }

    fun submit(
        request: AiRequest,
        policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE
    ) {
        activeJob?.cancel()

        activeJob = scope.launch {
            val requestId = UUID.randomUUID().toString()

            try {

                /*
                 * ---------------------------------------------------------
                 * 1. Resolve execution target ONCE
                 * ---------------------------------------------------------
                 */

                val provider =
                    providerRouter.getEligibleProvider(request)

                val modelId =
                    request.modelId
                        ?: providerRouter.selectedModelId.value

                val target = AiExecutionTarget(
                    providerId = provider.id,
                    modelId = modelId
                )

                val finalRequest =
                    if (request.modelId == null && modelId != null) {
                        request.copy(modelId = modelId)
                    } else {
                        request
                    }

                /*
                 * ---------------------------------------------------------
                 * 2. Snapshot provider capabilities
                 * ---------------------------------------------------------
                 */

                val capabilities =
                    provider.getCapabilities()

                /*
                 * ---------------------------------------------------------
                 * 3. Assemble immutable context
                 * ---------------------------------------------------------
                 */

                _state.value =
                    CognitiveSessionState.PreparingContext

                eventBus.publish(
                    CognitiveRequestStarted(
                        requestId = requestId
                    )
                )

                val assembly =
                    contextAssembler.assemble(
                        policy = policy,
                        capabilities = capabilities
                    )

                val context =
                    assembly.context

                eventBus.publish(
                    ContextAssembled(
                        requestId = requestId,
                        conversationCount =
                            context.conversation.size,
                        recentEventCount =
                            context.recentEvents.size,
                        memoryCount =
                            context.memories.size,
                        toolCount =
                            context.availableTools.size,
                        omissions =
                            assembly.omissions.size
                    )
                )

                /*
                 * ---------------------------------------------------------
                 * 4. Initial provider generation
                 * ---------------------------------------------------------
                 */

                _state.value =
                    CognitiveSessionState.Reasoning(
                        providerId = target.providerId,
                        modelId = target.modelId
                    )

                eventBus.publish(
                    CognitiveReasoningStarted(
                        requestId = requestId,
                        providerId = target.providerId,
                        modelId = target.modelId
                    )
                )

                var response =
                    executeProviderRequest(
                        provider = provider,
                        request = finalRequest,
                        context = context,
                        requestId = requestId
                    )

                /*
                 * ---------------------------------------------------------
                 * 5. Tool loop
                 *
                 * IMPORTANT:
                 * provider is the SAME INSTANCE selected above.
                 * target is immutable.
                 * router is NOT called again.
                 * ---------------------------------------------------------
                 */

                if (response.toolCalls.isNotEmpty()) {
                    response =
                        executeToolLoop(
                            requestId = requestId,
                            target = target,
                            request = finalRequest,
                            initialContext = context,
                            initialResponse = response,
                            provider = provider,
                            policy = policy
                        )
                }

                /*
                 * ---------------------------------------------------------
                 * 6. Finalize transaction
                 * ---------------------------------------------------------
                 */

                response =
                    AiResponseValidator.validate(
                        provider = provider,
                        response = response
                    )

                _state.value =
                    CognitiveSessionState.Completed(response)

                eventBus.publish(
                    CognitiveCompleted(
                        requestId = requestId,
                        response = response.text
                    )
                )

                val responseText =
                    response.text.trim()

                val userText =
                    finalRequest.text
                        ?.trim()
                        .orEmpty()

                if (
                    responseText.isNotBlank() &&
                    responseText != "HANDOFF_STARTED"
                ) {

                    if (userText.isNotBlank()) {

                        val turn =
                            conversationStore.append(
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

                    eventBus.publish(
                        TtsRequested(
                            text = responseText,
                            timestamp =
                                System.currentTimeMillis()
                        )
                    )
                }

            } catch (e: CancellationException) {

                _state.value =
                    CognitiveSessionState.Idle

                throw e

            } catch (e: Exception) {

                val message =
                    mapErrorMessage(e)

                _state.value =
                    CognitiveSessionState.Failed(message)

                eventBus.publish(
                    CognitiveFailed(
                        requestId = requestId,
                        message = message
                    )
                )
            }
        }
    }

    /**
     * Executes provider generation without re-entering routing.
     *
     * This method is the ONLY generation path used by the session after
     * the provider has already been resolved.
     */
    private suspend fun executeProviderRequest(
        provider: AiProvider,
        request: AiRequest,
        context: AiContext,
        requestId: String
    ): AiResponse {

        val capabilities =
            provider.getCapabilities()

        return if (
            capabilities.contains(
                AiCapability.STREAMING
            )
        ) {

            var completedResponse: AiResponse? =
                null

            var accumulatedText = ""

            provider
                .stream(
                    request = request,
                    context = context
                )
                .collect { event ->

                    when (event) {

                        is AiStreamEvent.Started -> {
                            _state.value =
                                CognitiveSessionState.Streaming("")
                        }

                        is AiStreamEvent.Token -> {

                            accumulatedText +=
                                event.text

                            _state.value =
                                CognitiveSessionState.Streaming(
                                    accumulatedText
                                )

                            eventBus.publish(
                                CognitiveTokenReceived(
                                    requestId = requestId,
                                    token = event.text
                                )
                            )
                        }

                        is AiStreamEvent.Completed -> {
                            completedResponse =
                                event.response
                        }

                        is AiStreamEvent.Failed -> {
                            throw event.error
                        }

                        is AiStreamEvent.ToolCallDelta -> {
                            // Final tool calls are read from Completed.response.
                        }
                    }
                }

            val response =
                completedResponse
                    ?: throw AiProviderException(
                        AiErrorType.INVALID_RESPONSE,
                        "Provider stream ended without Completed event"
                    )

            AiResponseValidator.validate(
                provider = provider,
                response = response
            )

        } else {

            val response =
                provider.generate(
                    request = request,
                    context = context
                )

            AiResponseValidator.validate(
                provider = provider,
                response = response
            )
        }
    }

    /**
     * Executes the tool loop using the original provider and model.
     */
    private suspend fun executeToolLoop(
        requestId: String,
        target: AiExecutionTarget,
        request: AiRequest,
        initialContext: AiContext,
        initialResponse: AiResponse,
        provider: AiProvider,
        policy: AiContextPolicy
    ): AiResponse {

        var response =
            initialResponse

        var currentContext =
            initialContext

        var round = 0

        var totalToolCalls = 0

        while (response.toolCalls.isNotEmpty()) {

            round++

            if (
                round >
                ToolExecutionLimits.MAX_TOOL_ROUNDS
            ) {
                throw AiProviderException(
                    AiErrorType.GENERATION_FAILED,
                    "Maximum tool execution rounds " +
                        "(${ToolExecutionLimits.MAX_TOOL_ROUNDS}) exceeded"
                )
            }

            val calls =
                response.toolCalls.take(
                    ToolExecutionLimits
                        .MAX_TOOL_CALLS_PER_ROUND
                )

            _state.value =
                CognitiveSessionState.ExecutingTool(
                    calls.first().functionName
                )

            val toolMessages =
                mutableListOf<AIMessage>()

            for (toolCall in calls) {

                totalToolCalls++

                if (
                    totalToolCalls >
                    ToolExecutionLimits.MAX_TOTAL_TOOL_CALLS
                ) {
                    throw AiProviderException(
                        AiErrorType.GENERATION_FAILED,
                        "Maximum total tool calls " +
                            "(${ToolExecutionLimits.MAX_TOTAL_TOOL_CALLS}) exceeded"
                    )
                }

                /*
                 * ---------------------------------------------------------
                 * Requested
                 * ---------------------------------------------------------
                 */

                eventBus.publish(
                    ToolCallRequested(
                        requestId = requestId,
                        toolCallId = toolCall.id,
                        toolId = toolCall.functionName
                    )
                )

                /*
                 * ---------------------------------------------------------
                 * Policy gate
                 * ---------------------------------------------------------
                 */

                if (
                    !policy.includeAvailableTools ||
                    toolRegistry == null ||
                    toolAuthorizer == null
                ) {

                    val reason =
                        when {
                            !policy.includeAvailableTools ->
                                "Tools disabled by runtime policy"

                            toolRegistry == null ->
                                "Tool registry unavailable"

                            else ->
                                "Tool authorization service unavailable"
                        }

                    eventBus.publish(
                        ToolCallRejected(
                            requestId = requestId,
                            toolCallId = toolCall.id,
                            toolId = toolCall.functionName,
                            reason = reason
                        )
                    )

                    toolMessages.add(
                        AIMessage(
                            role = Role.TOOL,
                            content =
                                "Tool execution rejected: $reason"
                        )
                    )

                    continue
                }

                /*
                 * ---------------------------------------------------------
                 * Authorization
                 * ---------------------------------------------------------
                 */

                val authorization =
                    toolAuthorizer.authorize(
                        toolCall
                    )

                when (authorization) {

                    is ToolAuthorization.Rejected -> {

                        eventBus.publish(
                            ToolCallRejected(
                                requestId = requestId,
                                toolCallId = toolCall.id,
                                toolId = toolCall.functionName,
                                reason =
                                    authorization.reason
                            )
                        )

                        toolMessages.add(
                            AIMessage(
                                role = Role.TOOL,
                                content =
                                    "Tool execution rejected: " +
                                        authorization.reason
                            )
                        )

                        continue
                    }

                    is ToolAuthorization.Authorized -> {

                        /*
                         * Authorized event is emitted ONLY AFTER
                         * the authorizer actually approved the call.
                         */
                        eventBus.publish(
                            ToolCallAuthorized(
                                requestId = requestId,
                                toolCallId = toolCall.id,
                                toolId = toolCall.functionName
                            )
                        )
                    }
                }

                /*
                 * ---------------------------------------------------------
                 * Execution
                 * ---------------------------------------------------------
                 */

                val authorized =
                    authorization as ToolAuthorization.Authorized

                val result =
                    toolRegistry.executeAuthorized(
                        authorized
                    )

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
                                content =
                                    result.output.take(
                                        ToolExecutionLimits
                                            .MAX_TOOL_OUTPUT_CHARS
                                    )
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
                                content =
                                    "Tool execution rejected: " +
                                        result.reason
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
                                content =
                                    "Tool execution failed: " +
                                        result.message
                            )
                        )
                    }
                }
            }

            /*
             * Do not create fake empty assistant messages.
             */
            val updatedConversation =
                buildList {

                    addAll(
                        currentContext.conversation
                    )

                    if (response.text.isNotBlank()) {
                        add(
                            AIMessage(
                                role = Role.ASSISTANT,
                                content = response.text
                            )
                        )
                    }

                    addAll(toolMessages)
                }

            currentContext =
                currentContext.copy(
                    conversation =
                        updatedConversation
                )

            /*
             * ---------------------------------------------------------
             * CRITICAL:
             * No providerRouter.execute() here.
             *
             * The same provider instance and modelId are reused.
             * ---------------------------------------------------------
             */

            val followUpRequest =
                request.copy(
                    modelId = target.modelId
                )

            _state.value =
                CognitiveSessionState.Reasoning(
                    providerId = target.providerId,
                    modelId = target.modelId
                )

            response =
                executeProviderRequest(
                    provider = provider,
                    request = followUpRequest,
                    context = currentContext,
                    requestId = requestId
                )
        }

        return response
    }

    private fun mapErrorMessage(
        e: Exception
    ): String {

        if (e is AiProviderException) {
            return e.message
                ?: "AI provider request failed"
        }

        val message =
            e.message.orEmpty()

        return when {

            message.contains(
                "401",
                ignoreCase = true
            ) ->
                "Invalid API key"

            message.contains(
                "403",
                ignoreCase = true
            ) ->
                "Access denied"

            message.contains(
                "429",
                ignoreCase = true
            ) ->
                "Provider rate limit exceeded"

            message.contains(
                "Unable to resolve host",
                ignoreCase = true
            ) ->
                "Cannot reach provider"

            else ->
                "Intelligence provider failed: " +
                    (e.localizedMessage
                        ?: "Unknown error")
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        _state.value =
            CognitiveSessionState.Idle
    }

    fun clearConversation() {
        scope.launch {
            conversationStore.clear()
        }
    }
}
