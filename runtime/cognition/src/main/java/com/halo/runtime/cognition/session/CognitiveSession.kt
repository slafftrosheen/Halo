package com.halo.runtime.cognition.session

import com.halo.runtime.cognition.agent.*
import com.halo.cognition.language.InteractionId
import java.time.Instant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.halo.cognition.language.ConversationIntent
import com.halo.cognition.language.QuestionIntent
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.blackboard.InteractionOrigin
import com.halo.runtime.cognition.blackboard.InterpretationKnowledge
import com.halo.runtime.cognition.context.ContextAssembler
import com.halo.runtime.cognition.context.ContextAssemblyResult
import com.halo.runtime.cognition.conversation.ConversationStore
import com.halo.runtime.cognition.tools.ToolAuthorization
import com.halo.runtime.cognition.tools.ToolAuthorizer
import com.halo.runtime.cognition.tools.ToolExecutionLimits
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.runtime.cognition.skills.SkillRegistry
import com.halo.runtime.provider.response.AiResponseValidator
import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiExecutionOutcome
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.sdk.provider.InferencePriority
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.validateAgainst
import com.halo.sdk.runtime.AssistantResponsePublished
import com.halo.sdk.runtime.CognitiveCompleted
import com.halo.sdk.runtime.CognitiveFailed
import com.halo.sdk.runtime.CognitiveReasoningStarted
import com.halo.sdk.runtime.CognitiveRequestStarted
import com.halo.sdk.runtime.CognitiveTokenReceived
import com.halo.sdk.runtime.ContextAssembled
import com.halo.sdk.runtime.ConversationTurnCompleted
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.InteractionTurnChanged
import com.halo.sdk.runtime.InteractionTurnState
import com.halo.sdk.runtime.ResponseDelivery
import com.halo.sdk.runtime.RuntimeFactResolved
import com.halo.sdk.runtime.SpeechInputRoute
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.ToolCallAuthorized
import com.halo.sdk.runtime.ToolCallCompleted
import com.halo.sdk.runtime.ToolCallRejected
import com.halo.sdk.runtime.ToolCallRequested
import com.halo.sdk.runtime.TtsRequested
import com.halo.sdk.runtime.defaultResponseDelivery
import java.util.Collections
import java.util.UUID
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

/**
 * Manages one immutable conversational cognitive transaction at a time.
 *
 * Structured/action interpretations are owned by deterministic reasoning and
 * execution. ConversationIntent and QuestionIntent are owned here. Provider/model
 * selection happens once per request and tool rounds never return to the router.
 */
import com.halo.runtime.cognition.facts.RuntimeFactResolver
import com.halo.runtime.cognition.facts.DefaultRuntimeFactResolver
import com.halo.cognition.language.SystemFactIntent

class CognitiveSession(
    private val eventBus: EventBus,
    private val providerRouter: AiProviderRouter,
    private val conversationStore: ConversationStore,
    private val contextAssembler: ContextAssembler,
    private val toolRegistry: ToolRegistry? = null,
    private val toolAuthorizer: ToolAuthorizer? = null,
    private val scope: CoroutineScope,
    private val blackboard: Blackboard = DefaultBlackboard(),
    private val defaultPolicyProvider: () -> AiContextPolicy = { AiContextPolicy.CONSERVATIVE },
    private val factResolver: RuntimeFactResolver = DefaultRuntimeFactResolver(),
    private val skillRegistry: SkillRegistry? = null,
    private val agentRunCoordinator: AgentRunCoordinator? = null
) {
    private val _state = MutableStateFlow<CognitiveSessionState>(CognitiveSessionState.Idle)
    val state: StateFlow<CognitiveSessionState> = _state.asStateFlow()
    private var activeJob: Job? = null
    @Volatile private var activeInteractionId: String? = null

    private val processedInterpretationIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val observerScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    init {
        observerScope.launch {
            blackboard.stream().collect { knowledge ->
                val payload = knowledge.payload as? InterpretationKnowledge ?: return@collect
                val knowledgeId = knowledge.id.value

                // Blackboard is append-only. Submit each interpretation exactly once.
                if (!processedInterpretationIds.add(knowledgeId)) return@collect
                if (processedInterpretationIds.size > MAX_PROCESSED_INTERPRETATIONS) {
                    processedInterpretationIds.clear()
                    processedInterpretationIds.add(knowledgeId)
                }

                val interpretation = payload.interpretation
                if (interpretation.metadata["clarificationOnly"] == "true") return@collect

                val intent = interpretation.intent
                val responseDelivery = interpretation.source.defaultResponseDelivery()

                if (intent is SystemFactIntent) {
                    scope.launch {
                        val fact = factResolver.resolve(intent)
                        if (fact != null) {
                            println("[HALO-TXN] speech knowledgeId=$knowledgeId intent=${intent::class.simpleName} -> RESOLVED_LOCALLY")
                            
                            val interactionIdStr = interpretation.metadata["interactionId"]
                            val requestId = UUID.randomUUID().toString()
                            eventBus.publish(CognitiveRequestStarted(requestId = requestId, interactionId = interactionIdStr))
                            eventBus.publish(
                                RuntimeFactResolved(
                                    transactionId = requestId,
                                    factType = intent::class.java.simpleName,
                                    valueSource = "system_clock"
                                )
                            )
                            _state.value = CognitiveSessionState.Completed(AiResponse(text = fact.text, providerId = "local", modelId = "fact-resolver", finishReason = "stop"))
                            eventBus.publish(CognitiveCompleted(requestId = requestId, response = fact.text, interactionId = interactionIdStr))
                            eventBus.publish(
                                AssistantResponsePublished(
                                    text = fact.text,
                                    delivery = responseDelivery,
                                    transactionId = requestId
                                )
                            )
                            if (responseDelivery == ResponseDelivery.SPEECH_AND_VISUAL) {
                                val outputTarget = when (val route = interpretation.origin?.speechRoute) {
                                    is SpeechInputRoute.Wearable ->
                                        SpeechOutputTarget.Wearable(
                                            deviceId = route.deviceId,
                                            displayName = route.displayName
                                        )
                                    SpeechInputRoute.Phone -> SpeechOutputTarget.Phone
                                    null -> SpeechOutputTarget.Phone
                                }
                                val fallbackPolicy = if (outputTarget is SpeechOutputTarget.Wearable) {
                                    SpeechOutputFallback.DISALLOW_PHONE
                                } else {
                                    SpeechOutputFallback.ALLOW_PHONE
                                }
                                eventBus.publish(
                                    TtsRequested(
                                        text = fact.text,
                                        transactionId = requestId,
                                        target = outputTarget,
                                        fallback = fallbackPolicy,
                                        sourceCaptureId = interpretation.origin?.captureId,
                                        interactionId = interactionIdStr,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                    return@collect
                }

                if (intent !is ConversationIntent && intent !is QuestionIntent) {
                    println(
                        "[HALO-TXN] speech knowledgeId=$knowledgeId " +
                            "intent=${intent::class.simpleName} -> BYPASSING LLM (deterministic execution)"
                    )
                    return@collect
                }

                val text = interpretation.transcript.trim()
                if (text.isEmpty()) return@collect

                if (activeJob?.isActive == true) {
                    println("[HALO-TXN] speech knowledgeId=$knowledgeId -> DROPPED (active job running)")
                    return@collect
                }

                println(
                    "[HALO-TXN] speech knowledgeId=$knowledgeId textLength=${text.length} " +
                        "intent=${intent::class.simpleName} delivery=$responseDelivery -> SUBMITTING TO LLM"
                )
                submit(
                    request = AiRequest(text = text, priority = InferencePriority.INTERACTIVE),
                    responseDelivery = responseDelivery,
                    origin = interpretation.origin,
                    metadata = interpretation.metadata
                )
            }
        }
    }

    fun submit(
        request: AiRequest,
        policy: AiContextPolicy? = null,
        responseDelivery: ResponseDelivery = ResponseDelivery.VISUAL_ONLY,
        origin: InteractionOrigin? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val outputTarget = when (val route = origin?.speechRoute) {
            is SpeechInputRoute.Wearable ->
                SpeechOutputTarget.Wearable(
                    deviceId = route.deviceId,
                    displayName = route.displayName
                )
            SpeechInputRoute.Phone -> SpeechOutputTarget.Phone
            null -> SpeechOutputTarget.Phone
        }
        val fallbackPolicy = if (outputTarget is SpeechOutputTarget.Wearable) {
            SpeechOutputFallback.DISALLOW_PHONE
        } else {
            SpeechOutputFallback.ALLOW_PHONE
        }
        val effectivePolicy = policy ?: defaultPolicyProvider()
        val newInteractionId = metadata["interactionId"] ?: origin?.captureId ?: UUID.randomUUID().toString()
        val interruptedInteractionId = activeInteractionId
        activeJob?.cancel()
        activeInteractionId = newInteractionId
        activeJob = scope.launch {
            val interactionId = InteractionId(newInteractionId)
            val requestId = UUID.randomUUID().toString()
            var runId: AgentRunId? = null
            var resolvedProviderId: String? = null
            var resolvedModelId: String? = null
            try {
                interruptedInteractionId?.takeIf { it != interactionId.value }?.let {
                    eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Interrupted(it, interactionId.value)))
                }
                eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Preparing(interactionId.value)))
                _state.value = CognitiveSessionState.PreparingContext
                eventBus.publish(CognitiveRequestStarted(requestId = requestId, interactionId = interactionId.value))

                val target = providerRouter.resolveExecutionTarget(request)
                resolvedProviderId = target.providerId
                resolvedModelId = target.modelId
                val provider = target.provider
                val finalRequest = request.copy(modelId = target.modelId)
                val capabilities = target.providerCapabilities
                val targetPolicy = effectivePolicy.copy(includeWorkspace = effectivePolicy.includeWorkspace &&
                    (target.provider.isLocal || effectivePolicy.allowCloudWorkspace))

                val assembly = contextAssembler.assemble(policy = targetPolicy, capabilities = capabilities)
                val context = assembly.context
                val requestWithContext = bindAssembledContext(finalRequest, assembly, responseDelivery)

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
                        modelId = target.modelId,
                        interactionId = interactionId.value,
                    )
                )

                var response = when (
                    val outcome = executeProviderRequest(
                        provider = provider,
                        capabilities = capabilities,
                        expectedModelId = target.modelId,
                        request = requestWithContext,
                        context = context,
                        requestId = requestId,
                        interactionId = interactionId.value,
                    )
                ) {
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
                        eventBus.publish(CognitiveCompleted(requestId = requestId, response = "", interactionId = interactionId.value))
                        return@launch
                    }
                }

                if (response.toolCalls.isNotEmpty()) {
                    runId = agentRunCoordinator?.start(AgentRequest(interactionId, request.text ?: "", origin?.let {
                        com.halo.sdk.runtime.ObservationOrigin(
                            if (it.speechRoute is SpeechInputRoute.Wearable) com.halo.sdk.host.ObservationSource.HeyCyanGlasses
                            else com.halo.sdk.host.ObservationSource.AndroidPhoneMic, it.sourceDeviceId)
                    }, speechOutputTarget = outputTarget, speechOutputFallback = fallbackPolicy, sourceCaptureId = origin?.captureId))
                    runId?.let {
                        agentRunCoordinator?.transition(it, AgentRunState.PLANNING)
                        agentRunCoordinator?.recordStep(AgentStep.PlanCreated(newStepId(), it, Instant.now(), requestId, "Execute requested tools"))
                        agentRunCoordinator?.transition(it, AgentRunState.EXECUTING)
                    }
                    response = executeToolLoop(
                        runId = runId,
                        interactionId = interactionId,
                        requestId = requestId,
                        target = target,
                        request = requestWithContext,
                        initialContext = context,
                        initialResponse = response,
                        provider = provider,
                        capabilities = capabilities,
                        policy = targetPolicy
                    )
                }

                response = AiResponseValidator.validate(
                    provider = provider,
                    capabilities = capabilities,
                    expectedModelId = target.modelId,
                    response = response
                )
                runId?.let {
                    agentRunCoordinator?.recordStep(AgentStep.ResponseProduced(newStepId(), it, Instant.now(), requestId, response.text))
                    agentRunCoordinator?.complete(it)
                }
                val responseText = response.text.trim()
                val userText = finalRequest.text?.trim().orEmpty()
                if (responseText.isNotBlank()) {
                    if (userText.isNotBlank()) {
                        val turn = conversationStore.append(
                            userText = userText,
                            assistantText = responseText,
                            metadata = metadata + mapOf("interactionId" to interactionId.value, "requestId" to requestId, "provider" to target.providerId, "model" to (target.modelId ?: ""))
                        )
                        eventBus.publish(
                            ConversationTurnCompleted(
                                turnId = turn.id,
                                userText = turn.userText,
                                assistantText = turn.assistantText
                            )
                        )
                    }

                    _state.value = CognitiveSessionState.Completed(response)
                    eventBus.publish(CognitiveCompleted(requestId = requestId, response = response.text, interactionId = interactionId.value, metrics = response.metrics?.toRuntimeMetrics()))
                    eventBus.publish(
                        AssistantResponsePublished(
                            text = responseText,
                            delivery = responseDelivery,
                            transactionId = requestId
                        )
                    )
                    if (responseDelivery == ResponseDelivery.SPEECH_AND_VISUAL) {
                        eventBus.publish(
                            TtsRequested(
                                text = responseText,
                                transactionId = requestId,
                                target = outputTarget,
                                fallback = fallbackPolicy,
                                sourceCaptureId = origin?.captureId,
                                interactionId = interactionId.value,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                } else {
                    _state.value = CognitiveSessionState.Completed(response)
                    eventBus.publish(CognitiveCompleted(requestId = requestId, response = response.text, interactionId = interactionId.value, metrics = response.metrics?.toRuntimeMetrics()))
                }
                eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Completed(interactionId.value)))
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    runId?.let { agentRunCoordinator?.cancel(it, "Interaction cancelled") }
                    eventBus.publish(CognitiveFailed(requestId = requestId, message = "Interaction cancelled", interactionId = interactionId.value))
                }
                if (activeJob === kotlinx.coroutines.currentCoroutineContext()[Job]) _state.value = CognitiveSessionState.Idle
                throw e
            } catch (e: Exception) {
                val message = mapErrorMessage(e)
                println(
                    "[HALO-COGNITION-FAIL] requestId=$requestId interactionId=${interactionId.value} " +
                        "provider=${resolvedProviderId ?: "unresolved"} model=${resolvedModelId ?: "unresolved"} " +
                        "type=${e.javaClass.simpleName} message=${e.message ?: "none"}"
                )
                e.printStackTrace()
                runId?.let { agentRunCoordinator?.fail(it, message) }
                _state.value = CognitiveSessionState.Failed(message)
                eventBus.publish(CognitiveFailed(
                    requestId = requestId,
                    message = message,
                    stage = "SESSION_EXECUTION",
                    type = e.javaClass.simpleName,
                    userRecoverable = (e as? AiProviderException)?.details?.recoverability !in setOf(
                        null,
                        com.halo.sdk.provider.Recoverability.PERMANENT,
                        com.halo.sdk.provider.Recoverability.UNKNOWN,
                    ),
                    interactionId = interactionId.value,
                ))
                eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Failed(interactionId.value, message)))
            } finally {
                if (activeInteractionId == interactionId.value) activeInteractionId = null
            }
        }
    }

    private fun bindAssembledContext(
        request: AiRequest,
        assembly: ContextAssemblyResult,
        responseDelivery: ResponseDelivery
    ): AiRequest {
        var result = request
        if (responseDelivery == ResponseDelivery.SPEECH_AND_VISUAL && !request.text.isNullOrBlank()) {
            val styleInstruction = "[Instruction: Respond conversationally and concisely in 1-2 short sentences. Do not say 'an AI language model'.]\n\n"
            result = result.copy(text = styleInstruction + request.text)
        }
        val image = assembly.image ?: return result
        if (result.image != null) return result
        return result.copy(image = image.imageBytes, mimeTypes = listOf(image.mimeType))
    }

    private suspend fun executeProviderRequest(
        provider: AiInferenceProvider,
        capabilities: Set<AiCapability>,
        expectedModelId: String,
        request: AiRequest,
        context: AiContext,
        requestId: String,
        interactionId: String,
    ): AiExecutionOutcome {
        request.validateAgainst(capabilities)
        val response = if (capabilities.contains(AiCapability.STREAMING)) {
            var completedResponse: AiResponse? = null
            val accumulatedText = StringBuilder()
            provider.stream(request, context).collect { event ->
                when (event) {
                    is AiStreamEvent.Started ->
                        _state.value = CognitiveSessionState.Streaming("")

                    is AiStreamEvent.Token -> {
                        accumulatedText.append(event.text)
                        _state.value = CognitiveSessionState.Streaming(accumulatedText.toString())
                        eventBus.publish(
                            CognitiveTokenReceived(requestId = requestId, token = event.text, interactionId = interactionId)
                        )
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
        runId: AgentRunId?,
        interactionId: InteractionId,
        requestId: String,
        target: com.halo.sdk.provider.AiExecutionTarget,
        request: AiRequest,
        initialContext: AiContext,
        initialResponse: AiResponse,
        provider: AiInferenceProvider,
        capabilities: Set<AiCapability>,
        policy: AiContextPolicy
    ): AiResponse {
        val authorizationScopeId = interactionId.value
        try {
        var response = initialResponse
        var currentContext = initialContext
        var round = 0
        var totalToolCalls = 0
        var hasUnconfirmedAction = false

        while (response.toolCalls.isNotEmpty()) {
            round++
            if (round > ToolExecutionLimits.MAX_TOOL_ROUNDS) {
                throw AiProviderException(
                    AiErrorType.GENERATION_FAILED,
                    "Maximum tool execution rounds (${ToolExecutionLimits.MAX_TOOL_ROUNDS}) exceeded"
                )
            }
            if (response.toolCalls.size > ToolExecutionLimits.MAX_TOOL_CALLS_PER_ROUND) {
                throw AiProviderException(
                    AiErrorType.INVALID_RESPONSE,
                    "Provider returned ${response.toolCalls.size} tool calls; " +
                        "maximum is ${ToolExecutionLimits.MAX_TOOL_CALLS_PER_ROUND}"
                )
            }

            val calls = response.toolCalls
            _state.value = CognitiveSessionState.ExecutingTool(calls.first().functionName)
            val toolMessages = mutableListOf<AIMessage>()

            for (toolCall in calls) {
                totalToolCalls++
                if (totalToolCalls > ToolExecutionLimits.MAX_TOTAL_TOOL_CALLS) {
                    throw AiProviderException(
                        AiErrorType.GENERATION_FAILED,
                        "Maximum total tool calls (${ToolExecutionLimits.MAX_TOTAL_TOOL_CALLS}) exceeded"
                    )
                }
                runId?.let {
                    agentRunCoordinator?.recordStep(AgentStep.ToolRequested(newStepId(), it, Instant.now(), toolCall.id, toolCall.functionName, "{}"))
                }
                eventBus.publish(
                    ToolCallRequested(
                        requestId = requestId,
                        toolCallId = toolCall.id,
                        toolId = toolCall.functionName
                    )
                )

                val workspaceReadAllowed = toolCall.functionName !in com.halo.runtime.cognition.tools.WorkspaceToolAccess.readTools ||
                    policy.includeWorkspace
                val toolIsEnabled = workspaceReadAllowed && (toolRegistry?.let { registry ->
                    skillRegistry?.enabledTools(registry.definitions())?.any {
                        it.name == toolCall.functionName || it.id == toolCall.functionName
                    } ?: true
                } ?: false)
                val invalidArguments = toolRegistry?.validate(toolCall)
                if (!policy.includeAvailableTools || toolRegistry == null || toolAuthorizer == null ||
                    !toolIsEnabled || invalidArguments != null) {
                    val reason = when {
                        !policy.includeAvailableTools -> "Tools disabled by runtime policy"
                        toolRegistry == null -> "Tool registry unavailable"
                        !workspaceReadAllowed -> "Private workspace access is disabled for this provider"
                        !toolIsEnabled -> "Skill unavailable or disabled"
                        invalidArguments != null -> invalidArguments.reason
                        else -> "Tool authorization service unavailable"
                    }
                    runId?.let { agentRunCoordinator?.recordStep(AgentStep.ToolFailed(newStepId(), it, Instant.now(), toolCall.id, toolCall.functionName, reason)) }
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
                            Role.TOOL,
                            "Tool execution rejected: $reason",
                            toolCallId = toolCall.id
                        )
                    )
                    continue
                }

                val authorization = toolAuthorizer.authorize(toolCall, authorizationScopeId)
                when (authorization) {
                    is ToolAuthorization.Rejected -> {
                        runId?.let { agentRunCoordinator?.recordStep(AgentStep.ToolFailed(newStepId(), it, Instant.now(), toolCall.id, toolCall.functionName, authorization.reason)) }
                        eventBus.publish(
                            ToolCallRejected(
                                requestId = requestId,
                                toolCallId = toolCall.id,
                                toolId = toolCall.functionName,
                                reason = authorization.reason
                            )
                        )
                        toolMessages.add(
                            AIMessage(
                                Role.TOOL,
                                "Tool execution rejected: ${authorization.reason}",
                                toolCallId = toolCall.id
                            )
                        )
                        continue
                    }

                    is ToolAuthorization.Authorized -> {
                        eventBus.publish(
                            ToolCallAuthorized(
                                requestId = requestId,
                                toolCallId = toolCall.id,
                                toolId = toolCall.functionName
                            )
                        )
                    }
                }

                val authorized = authorization as ToolAuthorization.Authorized
                if (runId != null) {
                    val claimed = agentRunCoordinator?.recordStep(AgentStep.ToolDispatched(newStepId(), runId,
                        Instant.now(), toolCall.id, toolCall.functionName, "${interactionId.value}:${toolCall.id}")) == true
                    check(claimed) { "Tool dispatch already recorded; completion must be reconciled before retry" }
                }
                when (val result = toolRegistry.executeAuthorized(authorized)) {
                    is ToolExecutionResult.Success -> {
                        hasUnconfirmedAction = hasUnconfirmedAction || result.actionTruth != ActionTruth.CONFIRMED
                        runId?.let { agentRunCoordinator?.recordStep(AgentStep.ToolCompleted(newStepId(), it, Instant.now(), toolCall.id, toolCall.functionName, result.output.take(ToolExecutionLimits.MAX_TOOL_OUTPUT_CHARS), true, result.actionTruth)) }
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
                                Role.TOOL,
                                result.output.take(ToolExecutionLimits.MAX_TOOL_OUTPUT_CHARS),
                                toolCallId = toolCall.id
                            )
                        )
                    }

                    is ToolExecutionResult.Rejected -> {
                        runId?.let { agentRunCoordinator?.recordStep(AgentStep.ToolFailed(newStepId(), it, Instant.now(), toolCall.id, toolCall.functionName, result.reason)) }
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
                                Role.TOOL,
                                "Tool execution rejected: ${result.reason}",
                                toolCallId = toolCall.id
                            )
                        )
                    }

                    is ToolExecutionResult.Failed -> {
                        hasUnconfirmedAction = hasUnconfirmedAction || result.completionUnknown
                        if (!result.completionUnknown) runId?.let { agentRunCoordinator?.recordStep(AgentStep.ToolFailed(newStepId(), it, Instant.now(), toolCall.id, toolCall.functionName, result.message)) }
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
                                Role.TOOL,
                                "Tool execution failed: ${result.message}",
                                toolCallId = toolCall.id
                            )
                        )
                    }
                }
            }

            currentContext = currentContext.copy(
                conversation = buildList {
                    addAll(currentContext.conversation)
                    if (response.text.isNotBlank() || response.toolCalls.isNotEmpty()) {
                        add(
                            AIMessage(
                                Role.ASSISTANT,
                                response.text,
                                toolCalls = response.toolCalls
                            )
                        )
                    }
                    addAll(toolMessages)
                }
            )

            val roundRequestId = UUID.randomUUID().toString()
            eventBus.publish(CognitiveRequestStarted(requestId = roundRequestId, interactionId = interactionId.value))
            response = when (
                val outcome = executeProviderRequest(
                    provider = provider,
                    capabilities = capabilities,
                    expectedModelId = target.modelId,
                    request = request.copy(modelId = target.modelId),
                    context = currentContext,
                    requestId = roundRequestId,
                    interactionId = interactionId.value,
                )
            ) {
                is AiExecutionOutcome.Response -> outcome.response
                is AiExecutionOutcome.HandoffStarted -> throw AiProviderException(
                    AiErrorType.INVALID_RESPONSE,
                    "Assistant handoff cannot occur during a tool execution transaction"
                )
            }
        }
        return if (hasUnconfirmedAction) response.copy(text = "The action was dispatched, but I could not confirm its completion. I will not repeat it automatically.") else response
        } finally {
            toolAuthorizer?.clearScope(authorizationScopeId)
        }
    }

    private fun newStepId() = AgentStepId(UUID.randomUUID().toString())

    private fun mapErrorMessage(e: Exception): String {
        if (e is AiProviderException) {
            return when (e.type) {
                AiErrorType.AUTHENTICATION_FAILED -> "Provider authentication needs attention. Check its credentials."
                AiErrorType.RATE_LIMITED -> e.details.retryAfterMs?.let { retryAfter ->
                    val seconds = ((retryAfter + 999L) / 1_000L).coerceAtLeast(1L)
                    "Provider is rate limited. Try again in about $seconds seconds."
                } ?: "Provider is rate limited. Retry shortly."
                AiErrorType.SESSION_CONFLICT -> "The model is currently in use by another session."
                AiErrorType.MODEL_NOT_SELECTED -> "Select and download a local model before using local intelligence."
                AiErrorType.MODEL_NOT_FOUND, AiErrorType.LOCAL_MODEL_UNAVAILABLE -> "The selected local model is unavailable."
                AiErrorType.NETWORK_UNAVAILABLE -> "The selected provider cannot be reached because the network is unavailable."
                AiErrorType.TIMEOUT -> "The selected provider timed out."
                else -> e.message ?: "AI provider request failed"
            }
        }
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

    private companion object {
        const val MAX_PROCESSED_INTERPRETATIONS = 500
    }
}
