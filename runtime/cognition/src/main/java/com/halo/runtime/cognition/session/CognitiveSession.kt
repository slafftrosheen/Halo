package com.halo.runtime.cognition.session

import com.halo.runtime.cognition.agent.*
import com.halo.cognition.language.InteractionId
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.halo.cognition.language.ConversationIntent
import com.halo.cognition.language.QuestionIntent
import com.halo.cognition.language.SystemFactIntent
import com.halo.runtime.cognition.agent.recovery.AgentRecoveryBlocked
import com.halo.runtime.cognition.agent.recovery.AgentRecoveryCompleted
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.blackboard.InteractionOrigin
import com.halo.runtime.cognition.blackboard.InterpretationKnowledge
import com.halo.runtime.cognition.conversation.ConversationStore
import com.halo.runtime.cognition.facts.RuntimeFactResolver
import com.halo.runtime.cognition.facts.DefaultRuntimeFactResolver
import com.halo.runtime.cognition.turn.*
import com.halo.runtime.cognition.context.*
import com.halo.runtime.cognition.workload.*
import com.halo.runtime.cognition.tools.*
import com.halo.runtime.cognition.skills.SkillRegistry
import com.halo.runtime.cognition.agent.recovery.DefaultAgentRecoveryPlanner
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.sdk.provider.InferencePriority
import com.halo.sdk.provider.Recoverability
import com.halo.sdk.runtime.AssistantResponsePublished
import com.halo.sdk.runtime.CognitiveCompleted
import com.halo.sdk.runtime.CognitiveFailed
import com.halo.sdk.runtime.CognitiveRequestStarted
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.InteractionTurnChanged
import com.halo.sdk.runtime.InteractionTurnState
import com.halo.sdk.runtime.InferenceObserved
import com.halo.sdk.runtime.ObservationOrigin
import com.halo.sdk.runtime.ResponseDelivery
import com.halo.sdk.runtime.RuntimeFactResolved
import com.halo.sdk.runtime.SpeechInputRoute
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.TtsRequested
import com.halo.sdk.runtime.defaultResponseDelivery
import com.halo.runtime.cognition.agent.AgentRunGuardRegistry
import com.halo.runtime.cognition.vision.InteractionMediaBinder

class CognitiveSession(
    private val eventBus: EventBus,
    private val conversationStore: ConversationStore,
    private val scope: CoroutineScope,
    private val blackboard: Blackboard = DefaultBlackboard(),
    private val defaultPolicyProvider: () -> AiContextPolicy = { AiContextPolicy.CONSERVATIVE },
    private val factResolver: RuntimeFactResolver = DefaultRuntimeFactResolver(),
    private val activeTurnCoordinator: ActiveTurnCoordinator,
    private val agentRecoveryExecutor: DefaultAgentRecoveryExecutor,
    private val turnExecutor: CognitiveTurnExecutor,
    private val responseCommitter: CognitiveResponseCommitter,
    private val routeResolver: InterpretationRouteResolver = InterpretationRouteResolver(),
    private val nativeDispatcher: com.halo.runtime.cognition.native.NativeCapabilityDispatcher? = null,
    private val interactionMediaBinder: InteractionMediaBinder? = null,
    private val voicePolicy: () -> com.halo.sdk.runtime.VoiceResponsePolicy = { com.halo.sdk.runtime.VoiceResponsePolicy() },
) {
    constructor(
        eventBus: EventBus,
        providerRouter: AiProviderRouter,
        conversationStore: ConversationStore,
        contextAssembler: ContextAssembler,
        toolRegistry: ToolRegistry? = null,
        toolAuthorizer: ToolAuthorizer? = null,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
        blackboard: Blackboard = DefaultBlackboard(),
        defaultPolicyProvider: () -> AiContextPolicy = { AiContextPolicy.CONSERVATIVE },
        factResolver: RuntimeFactResolver = DefaultRuntimeFactResolver(),
        skillRegistry: SkillRegistry? = null,
        agentRunCoordinator: AgentRunCoordinator? = null,
        assistantContexts: AssistantContextRepository? = null,
        toolDiscovery: ToolDiscoveryService? = null,
        agentRunStore: AgentRunStore? = null,
        toolResourceCoordinator: ToolResourceCoordinator? = null,
        toolExposurePlanner: ToolExposurePlanner? = null,
        toolExposureGate: ToolExposureGate? = null,
        toolAvailabilityResolver: ToolAvailabilityResolver? = null,
        workloadPlanner: AiWorkloadPlanner = DefaultAiWorkloadPlanner(),
        contextAcquisitionCoordinator: ContextAcquisitionCoordinator? = null,
        /** MINING-P1-001: shared so the provider loop and tool loop share one run budget. */
        runGuards: AgentRunGuardRegistry = AgentRunGuardRegistry(),
        interactionMediaBinder: InteractionMediaBinder? = null,
    ) : this(
        eventBus = eventBus,
        conversationStore = conversationStore,
        scope = scope,
        blackboard = blackboard,
        defaultPolicyProvider = defaultPolicyProvider,
        factResolver = factResolver,
        activeTurnCoordinator = DefaultActiveTurnCoordinator(eventBus),
        agentRecoveryExecutor = DefaultAgentRecoveryExecutor(
            agentRunStore = agentRunStore,
            agentRunCoordinator = agentRunCoordinator,
            toolRegistry = toolRegistry,
            eventBus = eventBus,
            recoveryPlanner = DefaultAgentRecoveryPlanner(),
        ),
        turnExecutor = CognitiveTurnExecutor(
            preparer = DefaultCognitiveTurnPreparer(
                workloadPlanner = workloadPlanner,
                providerRouter = providerRouter,
                contextAcquisitionCoordinator = contextAcquisitionCoordinator,
                contextAssembler = contextAssembler,
                agentRunStore = agentRunStore,
                toolExposurePlanner = toolExposurePlanner,
            ),
            providerExecutor = DefaultProviderTurnExecutor(eventBus, providerRouter),
            toolExecutor = DefaultToolTurnExecutor(
                toolRegistry = toolRegistry,
                toolAuthorizer = toolAuthorizer,
                toolResourceCoordinator = toolResourceCoordinator,
                agentRunCoordinator = agentRunCoordinator,
                agentRunStore = agentRunStore,
                eventBus = eventBus,
                runGuards = runGuards,
            ),
            agentRunCoordinator = agentRunCoordinator,
            agentRunStore = agentRunStore,
            eventBus = eventBus,
            toolExposurePlanner = toolExposurePlanner,
            toolExposureGate = toolExposureGate,
            runGuards = runGuards,
        ),
        responseCommitter = DefaultCognitiveResponseCommitter(
            conversationStore = conversationStore,
            agentRunCoordinator = agentRunCoordinator,
            eventBus = eventBus,
        ),
        routeResolver = InterpretationRouteResolver(),
        nativeDispatcher = null,
        interactionMediaBinder = interactionMediaBinder,
    )
    private val _state = MutableStateFlow<CognitiveSessionState>(CognitiveSessionState.Idle)
    val state: StateFlow<CognitiveSessionState> = _state.asStateFlow()

    private val processedInterpretationIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val observerScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    @Volatile var pendingRunIdInput: AgentInputRequest? = null
        private set

    init {
        observerScope.launch {
            blackboard.stream().collect { knowledge ->
                val payload = knowledge.payload as? InterpretationKnowledge ?: return@collect
                val knowledgeId = knowledge.id.value

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
                                    is SpeechInputRoute.Device ->
                                        SpeechOutputTarget.Device(
                                            deviceId = route.deviceId,
                                            platformEndpointId = route.platformEndpointId,
                                            displayName = route.displayName,
                                            kind = route.kind
                                        )
                                    SpeechInputRoute.Phone -> SpeechOutputTarget.Phone
                                    null -> SpeechOutputTarget.Phone
                                }
                                val fallbackPolicy = com.halo.sdk.runtime.fallbackFor(interpretation.origin?.speechRoute, voicePolicy().wearablePhoneFallback)
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

                /*
                 * AGENTIC-001 explicit routing.
                 *
                 * A recognized actionable intent must name what will act on it. The old
                 * code returned after logging "BYPASSING LLM", which is only valid when a
                 * deterministic executor actually consumes the intent; now that decision
                 * is a value, it is traced, and everything the deterministic pipeline does
                 * not own is conversational unless positive action evidence requires tools.
                 */
                val route = routeResolver.resolve(intent)
                println(
                    "[HALO_INTENT_CLASSIFIED] knowledgeId=$knowledgeId " +
                        "intent=${intent::class.simpleName} confidence=${interpretation.confidence}"
                )
                println(
                    "[HALO_EXECUTION_ROUTE] knowledgeId=$knowledgeId intent=${intent::class.simpleName} " +
                        "route=${route::class.simpleName} " +
                        when (route) {
                            is InterpretationExecutionRoute.DeterministicAction -> "executor=${route.executorId}"
                            is InterpretationExecutionRoute.DirectResponse -> "mode=DIRECT_RESPONSE"
                            is InterpretationExecutionRoute.Agentic -> "mode=AGENTIC hint=${route.hint != null}"
                            is InterpretationExecutionRoute.Unsupported -> "reason=${route.reason}"
                            InterpretationExecutionRoute.LocalFact -> "resolver=fact-resolver"
                        }
                )

                println("[HALO_CONVERSATION_ROUTE] intentType=${intent::class.simpleName} confidence=${interpretation.confidence} " +
                    "route=${if (route is InterpretationExecutionRoute.DirectResponse) "DIRECT_RESPONSE" else route::class.simpleName} " +
                    "toolUseMode=${if (route is InterpretationExecutionRoute.DirectResponse) "NONE" else if (route is InterpretationExecutionRoute.Agentic) "REQUIRED" else "HOST"} " +
                    "preferredRole=${if (route is InterpretationExecutionRoute.DirectResponse) "CHAT" else if (route is InterpretationExecutionRoute.Agentic) "AGENTIC" else "HOST"}" +
                    ((route as? InterpretationExecutionRoute.Agentic)?.let { " reason=${it.hint ?: "ACTION_INTENT"}" } ?: ""))

                if (route is InterpretationExecutionRoute.Unsupported) {
                    publishRouteResponse(
                        text = route.reason,
                        interpretation = interpretation,
                        responseDelivery = responseDelivery,
                        interactionId = interpretation.metadata["interactionId"],
                    )
                    return@collect
                }

                if (route is InterpretationExecutionRoute.DeterministicAction) {
                    if (nativeDispatcher?.owns(intent) == true) {
                        val result = nativeDispatcher.dispatch(interpretation)
                        publishRouteResponse(result?.responseText() ?: (intent as? com.halo.cognition.language.NativeClarificationIntent)?.reason ?: "This action needs a precise target or value.",
                            interpretation, responseDelivery, result?.request?.interactionId ?: interpretation.metadata["interactionId"],
                            terminal = result == null || result.result is com.halo.runtime.cognition.turn.ToolTurnResult.Completed || result.result is com.halo.runtime.cognition.turn.ToolTurnResult.RunStopped)
                        return@collect
                    }
                    // The deterministic engine is already collecting this intent from the
                    // blackboard; the named executor owns the outcome.
                    return@collect
                }

                val text = interpretation.transcript.trim()
                if (text.isEmpty()) return@collect

                val isOccupied = activeTurnCoordinator.activeTurn.value != null
                if (isOccupied) {
                    println("[HALO-TXN] speech knowledgeId=$knowledgeId -> DROPPED (active job running)")
                    return@collect
                }

                println(
                    "[HALO-TXN] speech knowledgeId=$knowledgeId textLength=${text.length} " +
                        "intent=${intent::class.simpleName} delivery=$responseDelivery -> SUBMITTING TO LLM"
                )
                val routeHint = (route as? InterpretationExecutionRoute.Agentic)?.hint
                submit(
                    request = AiRequest(text = text, priority = InferencePriority.INTERACTIVE,
                        toolUseMode = if (route is InterpretationExecutionRoute.DirectResponse) com.halo.sdk.provider.ToolUseMode.NONE else com.halo.sdk.provider.ToolUseMode.REQUIRED),
                    responseDelivery = responseDelivery,
                    origin = interpretation.origin,
                    metadata = interpretation.metadata + mapOf(
                        "executionRoute" to if (route is InterpretationExecutionRoute.DirectResponse) "DIRECT_RESPONSE" else "AGENTIC",
                        "requiresTools" to (route !is InterpretationExecutionRoute.DirectResponse).toString(),
                    ) + (if (route is InterpretationExecutionRoute.Agentic) mapOf("preferredRole" to "AGENTIC") else emptyMap()) +
                        (routeHint?.let { mapOf("agenticHint" to it) } ?: emptyMap())
                )
            }
        }
    }

    /**
     * A mrouted-but-unactionable intent still has to reach the user. Silent drops are
     * the failure mode this batch exists to remove.
     */
    private suspend fun publishRouteResponse(
        text: String,
        interpretation: com.halo.runtime.cognition.blackboard.Interpretation,
        responseDelivery: ResponseDelivery,
        interactionId: String?,
        terminal: Boolean = true,
    ) {
        val requestId = UUID.randomUUID().toString()
        eventBus.publish(CognitiveRequestStarted(requestId = requestId, interactionId = interactionId))
        if (terminal) eventBus.publish(CognitiveCompleted(requestId = requestId, response = text, interactionId = interactionId))
        eventBus.publish(
            AssistantResponsePublished(
                text = text,
                delivery = responseDelivery,
                transactionId = requestId,
            )
        )
        if (responseDelivery == ResponseDelivery.SPEECH_AND_VISUAL) {
            val outputTarget = when (val route = interpretation.origin?.speechRoute) {
                is SpeechInputRoute.Device -> SpeechOutputTarget.Device(
                    deviceId = route.deviceId,
                    platformEndpointId = route.platformEndpointId,
                    displayName = route.displayName,
                    kind = route.kind,
                )
                SpeechInputRoute.Phone -> SpeechOutputTarget.Phone
                null -> SpeechOutputTarget.Phone
            }
            eventBus.publish(
                TtsRequested(
                    text = text,
                    transactionId = requestId,
                    target = outputTarget,
                    fallback = com.halo.sdk.runtime.fallbackFor(interpretation.origin?.speechRoute, voicePolicy().wearablePhoneFallback),
                    sourceCaptureId = interpretation.origin?.captureId,
                    interactionId = interactionId,
                    timestamp = System.currentTimeMillis(),
                )
            )
        }
    }

    fun submit(
        request: AiRequest,
        policy: AiContextPolicy? = null,
        responseDelivery: ResponseDelivery = ResponseDelivery.VISUAL_ONLY,
        origin: InteractionOrigin? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val effectivePolicy = policy ?: defaultPolicyProvider()
        val newInteractionId = metadata["interactionId"] ?: origin?.captureId ?: UUID.randomUUID().toString()
        val resumedRunIdStr = metadata["resumedRunId"]
        val resumedRunId = if (resumedRunIdStr != null) AgentRunId(resumedRunIdStr) else null
        val interactionId = InteractionId(newInteractionId)
        val requestId = UUID.randomUUID().toString()

        // Register before execution. Starting eagerly here races the separate coordinator update:
        // a fast failure/completion can clear nothing, after which an already-finished Job is
        // installed as the active turn and every later speech request is dropped as occupied.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (resumedRunId == null) {
                    eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Preparing(interactionId.value)))
                }
                eventBus.publish(CognitiveRequestStarted(requestId = requestId, interactionId = interactionId.value))

                val turnRequest = CognitiveTurnRequest(
                    interactionId = interactionId,
                    requestId = requestId,
                    request = request,
                    policy = effectivePolicy,
                    responseDelivery = responseDelivery,
                    origin = origin,
                    metadata = metadata,
                    runId = resumedRunId,
                    voicePolicy = voicePolicy(),
                    sourceCaptureId = origin?.captureId,
                    mode = CognitiveTurnMode.FRESH
                )

                val commit = turnExecutor.execute(turnRequest) { _state.value = it }
                responseCommitter.commit(commit)

                _state.value = CognitiveSessionState.Completed(commit.response)
                if (resumedRunId == null) {
                    eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Completed(interactionId.value)))
                }

            } catch (e: CancellationException) {
                if (resumedRunId == null) eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Cancelled(interactionId.value, "Interaction cancelled")))
                _state.value = CognitiveSessionState.Idle
                throw e
            } catch (e: Exception) {
                val reasoning = _state.value as? CognitiveSessionState.Reasoning
                val failedProviderId = reasoning?.providerId
                val failedModelId = reasoning?.modelId
                val message = listOfNotNull(failedProviderId, failedModelId).joinToString(" · ").let { target ->
                    if (target.isBlank()) mapErrorMessage(e) else "$target: ${mapErrorMessage(e)}"
                }
                val aiEx = (e as? AiProviderException) ?: (e.cause as? AiProviderException)
                val errorType = aiEx?.type?.name ?: e.javaClass.simpleName
                val httpStatus = aiEx?.details?.httpStatus
                val providerCode = aiEx?.details?.providerCode
                val recoverability = aiEx?.details?.recoverability?.name
                println(
                    "[HALO-COGNITION-FAIL] requestId=$requestId interactionId=${interactionId.value} " +
                        "type=${e.javaClass.simpleName} errorType=$errorType httpStatus=$httpStatus " +
                        "providerCode=$providerCode recoverability=$recoverability " +
                        "provider=$failedProviderId model=$failedModelId"
                )
                _state.value = CognitiveSessionState.Failed(message)
                eventBus.publish(CognitiveFailed(
                    requestId = requestId,
                    message = message,
                    stage = "SESSION_EXECUTION",
                    type = e.javaClass.simpleName,
                    providerId = failedProviderId,
                    modelId = failedModelId,
                    userRecoverable = (e as? AiProviderException)?.details?.recoverability !in setOf(
                        null,
                        Recoverability.PERMANENT,
                        Recoverability.UNKNOWN,
                    ),
                    interactionId = interactionId.value,
                ))
                eventBus.publish(InferenceObserved(
                    requestId = requestId,
                    providerId = failedProviderId ?: "unknown",
                    requestedModelId = null,
                    resolvedModelId = failedModelId,
                    success = false,
                ))
                if (resumedRunId == null) eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Failed(interactionId.value, message)))
            } finally {
                activeTurnCoordinator.clear(interactionId, this.coroutineContext[Job]!!)
            }
        }
        
        scope.launch {
            val replaced = activeTurnCoordinator.replace(interactionId, job)
            if (replaced != null && replaced.interactionId != interactionId) {
                eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Interrupted(replaced.interactionId.value, interactionId.value)))
            }
            job.start()
        }
    }

    fun resume(runId: AgentRunId, responseDelivery: ResponseDelivery = ResponseDelivery.VISUAL_ONLY) {
        scope.launch {
            try {
                if (nativeDispatcher?.ownsRun(runId) == true) {
                    val nativeResult = nativeDispatcher.resume(runId) ?: return@launch
                    val interpretation = com.halo.runtime.cognition.blackboard.Interpretation(
                        metadata = mapOf("interactionId" to nativeResult.request.interactionId),
                        origin = nativeDispatcher.resumeOrigin(runId))
                    publishRouteResponse(nativeResult.responseText(), interpretation,
                        if (interpretation.origin?.speechRoute is SpeechInputRoute.Device) ResponseDelivery.SPEECH_AND_VISUAL else responseDelivery,
                        nativeResult.request.interactionId, terminal = nativeResult.result is ToolTurnResult.Completed || nativeResult.result is ToolTurnResult.RunStopped)
                    return@launch
                }
                when (val result = agentRecoveryExecutor.executeRecovery(runId)) {
                    is RecoveryExecutionResult.Noop -> Unit
                    is RecoveryExecutionResult.Blocked -> {
                        eventBus.publish(
                            AgentRecoveryBlocked(
                                runId.value, runId.value, result.reason // Assuming interactionId fallback
                            )
                        )
                    }
                    is RecoveryExecutionResult.CompleteResponse -> {
                        // Complete it via Committer
                        val commit = CognitiveResponseCommit(
                            runId = runId,
                            interactionId = result.interactionId,
                            requestId = result.responseId,
                            userText = "",
                            response = AiResponse(text = result.responseText, providerId = "recovery", modelId = null, finishReason = "stop"),
                            responseDelivery = responseDelivery,
                            outputTarget = SpeechOutputTarget.Phone,
                            outputFallback = SpeechOutputFallback.ALLOW_PHONE,
                            sourceCaptureId = null,
                            metadata = emptyMap()
                        )
                        responseCommitter.commit(commit)
                        _state.value = CognitiveSessionState.Completed(commit.response)
                        eventBus.publish(
                            AgentRecoveryCompleted(
                                runId.value, result.interactionId.value, "CompleteFromRecordedResponse"
                            )
                        )
                    }
                    is RecoveryExecutionResult.StartTurn -> {
                        val turnRequest = result.request.copy(
                            responseDelivery = responseDelivery
                        )
                        eventBus.publish(CognitiveRequestStarted(requestId = turnRequest.requestId, interactionId = turnRequest.interactionId.value))
                        val commit = turnExecutor.execute(turnRequest) { _state.value = it }
                        responseCommitter.commit(commit)
                        _state.value = CognitiveSessionState.Completed(commit.response)
                        eventBus.publish(
                            AgentRecoveryCompleted(
                                runId.value, turnRequest.interactionId.value, turnRequest.mode.name
                            )
                        )
                    }
                    is RecoveryExecutionResult.RetryTool -> {
                        val req = AiRequest(text = "", priority = InferencePriority.INTERACTIVE)
                        val turnRequest = CognitiveTurnRequest(
                            interactionId = result.invocation.interactionId,
                            requestId = UUID.randomUUID().toString(),
                            request = req,
                            policy = defaultPolicyProvider(),
                            responseDelivery = responseDelivery,
                            origin = null,
                            metadata = mapOf("resumedRunId" to runId.value),
                            runId = runId,
                            mode = CognitiveTurnMode.CONTINUE_AFTER_TOOL
                        )
                        val commit = turnExecutor.execute(turnRequest) { _state.value = it }
                        responseCommitter.commit(commit)
                        _state.value = CognitiveSessionState.Completed(commit.response)
                        eventBus.publish(
                            AgentRecoveryCompleted(
                                runId.value, turnRequest.interactionId.value, "RetryTool"
                            )
                        )
                    }
                    is RecoveryExecutionResult.ReconcileTool -> {
                        val req = AiRequest(text = "", priority = InferencePriority.INTERACTIVE)
                        val turnRequest = CognitiveTurnRequest(
                            interactionId = result.invocation.interactionId,
                            requestId = UUID.randomUUID().toString(),
                            request = req,
                            policy = defaultPolicyProvider(),
                            responseDelivery = responseDelivery,
                            origin = null,
                            metadata = mapOf("resumedRunId" to runId.value),
                            runId = runId,
                            mode = CognitiveTurnMode.CONTINUE_AFTER_TOOL
                        )
                        val commit = turnExecutor.execute(turnRequest) { _state.value = it }
                        responseCommitter.commit(commit)
                        _state.value = CognitiveSessionState.Completed(commit.response)
                        eventBus.publish(
                            AgentRecoveryCompleted(
                                runId.value, turnRequest.interactionId.value, "ReconcileTool"
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                _state.value = CognitiveSessionState.Idle
                throw e
            } catch (e: Exception) {
                println("[HALO-RESUME-FAIL] runId=${runId.value} type=${e.javaClass.simpleName} message=${e.message}")
                _state.value = CognitiveSessionState.Failed(mapErrorMessage(e))
            } finally {
                // Interaction ID might not be easily accessible here, clear by job
            }
        }
        
        scope.launch {
            // For resume, we could generate interactionId or get it from the run
            // We bypass activeTurnCoordinator replace for simplicity in resume wrapper
            // unless we want to track it
        }
    }

    private fun mapErrorMessage(e: Exception): String {
        if (e is AiProviderException) {
            return when (e.type) {
                AiErrorType.AUTHENTICATION_FAILED -> "Provider authentication needs attention. Check its credentials."
                AiErrorType.RATE_LIMITED -> e.details.retryAfterMs?.let { retryAfter ->
                    val seconds = ((retryAfter + 999L) / 1_000L).coerceAtLeast(1L)
                    "Provider is rate limited. Try again in about $seconds seconds."
                } ?: "Provider is rate limited. Retry shortly."
                AiErrorType.SESSION_CONFLICT -> "The model is currently in use by another session."
                AiErrorType.MODEL_NOT_SELECTED -> "Select a model for the chosen provider. Download it first when using on-device intelligence."
                AiErrorType.MODEL_NOT_FOUND -> "The selected model is unavailable for this provider."
                AiErrorType.LOCAL_MODEL_UNAVAILABLE -> "The selected local model is unavailable."
                AiErrorType.NETWORK_UNAVAILABLE, AiErrorType.NETWORK_ERROR -> "No network connection, or the provider could not be reached."
                AiErrorType.TIMEOUT -> "The AI provider did not respond in time."
                AiErrorType.GENERATION_FAILED -> "The AI failed to generate a response."
                AiErrorType.INVALID_RESPONSE -> "The AI provider returned an invalid response."
                AiErrorType.MODEL_UNAVAILABLE -> "That model is unavailable."
                AiErrorType.UNSUPPORTED_CAPABILITY -> "That model can’t perform this type of request. For actions, choose a tool-capable model and enable the required capability."
                else -> "An unexpected intelligence error occurred. Open provider settings for recovery."
            }
        }
        val message = e.message.orEmpty()
        return when {
            message.contains("401", ignoreCase = true) -> "Invalid API key."
            message.contains("403", ignoreCase = true) -> "Access denied."
            message.contains("429", ignoreCase = true) -> "Provider rate limit exceeded."
            message.contains("Unable to resolve host", ignoreCase = true) -> "Cannot reach provider."
            else -> "An unexpected intelligence error occurred."
        }
    }

    fun cancel() {
        scope.launch {
            activeTurnCoordinator.cancel("Cancelled by user")
            _state.value = CognitiveSessionState.Idle
        }
    }

    fun clearConversation() {
        scope.launch { conversationStore.clear() }
    }

    private companion object {
        const val MAX_PROCESSED_INTERPRETATIONS = 500
    }
}
