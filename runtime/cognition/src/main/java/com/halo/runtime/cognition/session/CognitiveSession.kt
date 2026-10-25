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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.halo.runtime.cognition.continuity.*
import com.halo.cognition.language.ConversationIntent
import com.halo.cognition.language.QuestionIntent
import com.halo.cognition.language.SystemFactIntent
import com.halo.runtime.cognition.agent.recovery.AgentRecoveryBlocked
import com.halo.runtime.cognition.agent.recovery.AgentRecoveryCompleted
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.blackboard.InteractionOrigin
import com.halo.runtime.cognition.blackboard.toObservationOrigin
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
    private val pendingInputResponse: suspend (com.halo.runtime.cognition.blackboard.Interpretation) -> Boolean = { false },
    private val continuity: InteractionContinuityCoordinator? = null,
    private val voicePolicy: () -> com.halo.sdk.runtime.VoiceResponsePolicy = { com.halo.sdk.runtime.VoiceResponsePolicy() },
    private val selectedContext: (String?) -> List<ContextReference> = { emptyList() },
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
            runStore = agentRunStore,
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
    val activeInteractionIds get() = activeTurnCoordinator.activeTurn.map { it?.interactionId?.value }.distinctUntilChanged()
    private val cancelledInteractions = linkedSetOf<String>()
    private fun isCancelled(id: String?) = synchronized(cancelledInteractions) { id != null && id in cancelledInteractions }
    fun ownsInteraction(id: String) = activeTurnCoordinator.activeTurn.value?.let { it.interactionId.value == id && it.job.isActive } == true

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
                // A malformed observation or unavailable admission dependency must not
                // permanently cancel the only Blackboard interpreter collector.
                try {
                if (isCancelled(interpretation.metadata["interactionId"])) return@collect
                if (interpretation.metadata["clarificationOnly"] == "true") return@collect

                val intent = interpretation.intent
                val responseDelivery = interpretation.source.defaultResponseDelivery()

                if (pendingInputResponse(interpretation)) return@collect
                if (intent is SystemFactIntent) {
                    // Local facts follow the SAME active-turn and response-commit contract as
                    // provider turns. Otherwise a late clock result can outlive Stop/Cancel,
                    // overwrite a newer answer, or bypass the conversation/TTS receipt.
                    val identity = InteractionId(
                        interpretation.metadata["interactionId"] ?: interpretation.origin?.captureId
                            ?: UUID.randomUUID().toString()
                    )
                    val requestId = UUID.randomUUID().toString()
                    val conversationAtSubmission =
                        interpretation.metadata["conversationId"] ?: conversationStore.activeConversationId()
                    val job = scope.launch(start = CoroutineStart.LAZY) {
                        val ownedJob = coroutineContext[Job]!!
                        try {
                            conversationStore.awaitReady()
                            val boundConversation = conversationAtSubmission ?: conversationStore.activeConversationId()
                            if (boundConversation != null) {
                                require(conversationStore.isConversationAvailable(boundConversation)) {
                                    "Conversation unavailable"
                                }
                            }
                            eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Preparing(identity.value)))
                            eventBus.publish(CognitiveRequestStarted(requestId = requestId, interactionId = identity.value))
                            val fact = factResolver.resolve(intent)
                            val text = fact?.text ?: "This device fact is not available right now."
                            if (fact != null) {
                                eventBus.publish(RuntimeFactResolved(
                                    transactionId = requestId,
                                    factType = intent::class.java.simpleName,
                                    valueSource = "system_clock"
                                ))
                            }
                            val response = AiResponse(
                                text = text, providerId = "local",
                                modelId = "fact-resolver", finishReason = "stop"
                            )
                            responseCommitter.commit(CognitiveResponseCommit(
                                runId = null, interactionId = identity, requestId = requestId,
                                userText = interpretation.transcript, response = response,
                                responseDelivery = responseDelivery,
                                outputTarget = com.halo.sdk.runtime.responseTarget(interpretation.origin?.speechRoute),
                                outputFallback = com.halo.sdk.runtime.fallbackFor(
                                    interpretation.origin?.speechRoute, voicePolicy().wearablePhoneFallback
                                ),
                                sourceCaptureId = interpretation.origin?.captureId,
                                metadata = mapOf("inputSurface" to if (interpretation.origin?.speechRoute is SpeechInputRoute.Device)
                                    "WEARABLE" else "PHONE"),
                                conversationId = boundConversation,
                            ))
                            _state.value = CognitiveSessionState.Completed(response)
                            eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Completed(identity.value)))
                        } catch (cancelled: CancellationException) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Cancelled(
                                    identity.value, "Local answer cancelled"
                                )))
                            }
                            throw cancelled
                        } catch (error: Exception) {
                            val message = "The requested device fact could not be read."
                            if (activeTurnCoordinator.activeTurn.value?.job === ownedJob)
                                _state.value = CognitiveSessionState.Failed(message)
                            eventBus.publish(CognitiveFailed(
                                requestId = requestId, interactionId = identity.value,
                                message = message, stage = "LOCAL_FACT", type = error.javaClass.simpleName
                            ))
                            eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Failed(identity.value, message)))
                        } finally {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                activeTurnCoordinator.clear(identity, ownedJob)
                            }
                        }
                    }
                    launchTracked(identity, job)
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
                        conversationStore.awaitReady()
                        val identity = InteractionId(interpretation.metadata["interactionId"] ?: interpretation.origin?.captureId ?: UUID.randomUUID().toString())
                        val bound = interpretation.copy(metadata = interpretation.metadata +
                            mapOf("interactionId" to identity.value) +
                            listOfNotNull((interpretation.metadata["conversationId"] ?: conversationStore.activeConversationId())?.let { "conversationId" to it }).toMap())
                        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                            val ownedJob = coroutineContext[Job]!!
                            try {
                                _state.value = CognitiveSessionState.ExecutingTool("Native action")
                                val requestId = "native:${identity.value}"
                                eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Preparing(identity.value)))
                                eventBus.publish(CognitiveRequestStarted(requestId = requestId, interactionId = identity.value))
                                val result = nativeDispatcher.dispatch(bound)
                                val text = result?.responseText() ?: (intent as? com.halo.cognition.language.NativeClarificationIntent)?.reason ?: "This action needs a precise target or value."
                                val terminal = result == null || result.result is com.halo.runtime.cognition.turn.ToolTurnResult.Completed ||
                                    result.result is com.halo.runtime.cognition.turn.ToolTurnResult.RunStopped
                                publishRouteResponse(text,
                                    bound, responseDelivery, result?.request?.interactionId ?: identity.value,
                                    terminal = terminal, boundRunId = result?.runId,
                                    requestId = requestId, requestAlreadyStarted = true)
                                // A waiting run is not a completed action. The response can be
                                // presented or spoken while Activity retains its pending task.
                                if (terminal) eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Completed(identity.value)))
                                if (activeTurnCoordinator.activeTurn.value?.job === ownedJob)
                                    _state.value = if (terminal) CognitiveSessionState.Completed(
                                        AiResponse(text, providerId = "native")
                                    ) else CognitiveSessionState.Idle
                            } catch (cancelled: CancellationException) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                    eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Cancelled(identity.value, "Native action stopped; review its recorded outcome before repeating")))
                                }
                                throw cancelled
                            } catch (error: Exception) {
                                if (activeTurnCoordinator.activeTurn.value?.job === ownedJob)
                                    _state.value = CognitiveSessionState.Failed("Native action ended. Review its recorded outcome before repeating.")
                                eventBus.publish(CognitiveFailed(requestId = "native:${identity.value}", interactionId = identity.value,
                                    message = "Native action ended. Review its recorded outcome before repeating.", stage = "NATIVE_EXECUTION", type = error.javaClass.simpleName))
                                eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Failed(identity.value,
                                    "Native action ended. Review its recorded outcome before repeating.")))
                            } finally {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                    if (!ownedJob.isActive && (activeTurnCoordinator.activeTurn.value == null || activeTurnCoordinator.activeTurn.value?.job === ownedJob))
                                        _state.value = CognitiveSessionState.Idle
                                    activeTurnCoordinator.clear(identity, ownedJob)
                                }
                            }
                        }
                        launchTracked(identity, job)
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
                    // Do not start a second model/tool turn while an earlier speech turn
                    // owns the runtime; equally, do not silently lose the user's request.
                    // The failure belongs to the rejected interaction, never the active one.
                    val rejectedId = interpretation.metadata["interactionId"] ?:
                        interpretation.origin?.captureId ?: UUID.randomUUID().toString()
                    val reason = "Halo is finishing another request. Try again when it completes."
                    eventBus.publish(CognitiveFailed(
                        requestId = "busy:$rejectedId", interactionId = rejectedId,
                        message = reason, stage = "TURN_ADMISSION", type = "BUSY"
                    ))
                    eventBus.publish(InteractionTurnChanged(
                        state = InteractionTurnState.Failed(rejectedId, reason)
                    ))
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
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // One bad request is an isolated failure, not a dead observer.
                    val id = interpretation.metadata["interactionId"] ?: interpretation.origin?.captureId
                    val reason = "Halo could not process this request. Try again."
                    eventBus.publish(CognitiveFailed(
                        requestId = "admission:$knowledgeId", interactionId = id,
                        message = reason, stage = "INTENT_DISPATCH", type = error.javaClass.simpleName,
                    ))
                    if (id != null) eventBus.publish(InteractionTurnChanged(
                        state = InteractionTurnState.Failed(id, reason)
                    ))
                }
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
        boundRunId: AgentRunId? = null,
        requestId: String? = null,
        requestAlreadyStarted: Boolean = false,
    ) {
        conversationStore.awaitReady()
        val run = boundRunId?.let { agentRecoveryExecutor.loadRun(it) }
        val conversation = run?.let { RunContinuityResolver.conversationId(it) }
            ?: interpretation.metadata["conversationId"] ?: conversationStore.activeConversationId()
        val route = run?.let { RunContinuityResolver.delivery(it) }
            ?: ContinuityDelivery(com.halo.sdk.runtime.responseTarget(interpretation.origin?.speechRoute),
                com.halo.sdk.runtime.fallbackFor(interpretation.origin?.speechRoute, voicePolicy().wearablePhoneFallback), responseDelivery)
        val responseRequestId = requestId ?: boundRunId?.let { "native:${it.value}:response" } ?: UUID.randomUUID().toString()
        val identity = InteractionId(interactionId ?: interpretation.origin?.captureId ?: UUID.randomUUID().toString())
        if (!requestAlreadyStarted)
            eventBus.publish(CognitiveRequestStarted(requestId = responseRequestId, interactionId = identity.value))
        responseCommitter.commit(CognitiveResponseCommit(boundRunId, identity, responseRequestId, interpretation.transcript,
            AiResponse(text, providerId = "native"), route.delivery, route.target, route.fallback, interpretation.origin?.captureId,
            mapOf("inputSurface" to if (interpretation.origin?.speechRoute is SpeechInputRoute.Device) "WEARABLE" else "PHONE"),
            conversationId = conversation, terminal = terminal))
        // A nonterminal tool result is a durable handoff: the response committer
        // deliberately does not synthesize its text. Say the pending requirement
        // without falsely completing the tool run or leaking wearable audio to phone.
        if (!terminal && route.delivery == ResponseDelivery.SPEECH_AND_VISUAL) {
            val spoken = TtsSanitizer.sanitize(text)
            if (spoken.isNotBlank()) eventBus.publish(TtsRequested(
                text = spoken, transactionId = responseRequestId, target = route.target,
                fallback = route.fallback, sourceCaptureId = interpretation.origin?.captureId,
                interactionId = identity.value,
            ))
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
        if (isCancelled(newInteractionId)) return
        val resumedRunIdStr = metadata["resumedRunId"]
        val resumedRunId = if (resumedRunIdStr != null) AgentRunId(resumedRunIdStr) else null
        val interactionId = InteractionId(newInteractionId)
        val requestId = UUID.randomUUID().toString()

        // Register before execution. Starting eagerly here races the separate coordinator update:
        val conversationAtSubmission = metadata["conversationId"] ?: conversationStore.activeConversationId()
        val contextAtSubmission = selectedContext(conversationAtSubmission)
        // a fast failure/completion can clear nothing, after which an already-finished Job is
        // installed as the active turn and every later speech request is dropped as occupied.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                conversationStore.awaitReady()
                val boundConversation = conversationAtSubmission ?: conversationStore.activeConversationId()
                if (boundConversation != null) require(conversationStore.isConversationAvailable(boundConversation)) { "Conversation unavailable" }
                continuity?.bind(boundConversation, interactionId.value, RunContinuityResolver.endpoint(com.halo.sdk.runtime.responseTarget(origin?.speechRoute), origin?.toObservationOrigin()))
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
                    metadata = metadata + ("inputSurface" to when {
                        origin?.speechRoute is SpeechInputRoute.Device -> "WEARABLE"
                        origin?.toObservationOrigin()?.source == ObservationSource.AndroidAssist -> "ANDROID_ASSISTANT"
                        else -> "PHONE"
                    }),
                    runId = resumedRunId,
                    voicePolicy = voicePolicy(),
                    sourceCaptureId = origin?.captureId,
                    conversationId = boundConversation,
                    mode = CognitiveTurnMode.FRESH,
                    contextReferences = contextAtSubmission,
                )

                val commit = turnExecutor.execute(turnRequest) { _state.value = it }
                responseCommitter.commit(commit)

                _state.value = CognitiveSessionState.Completed(commit.response)
                if (resumedRunId == null) {
                    eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Completed(interactionId.value)))
                }

            } catch (e: CancellationException) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { turnExecutor.interrupt(interactionId) }
                if (resumedRunId == null) eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Cancelled(interactionId.value, "Interaction cancelled")))
                if (activeTurnCoordinator.activeTurn.value?.job === this.coroutineContext[Job]) _state.value = CognitiveSessionState.Idle
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
                _state.value = CognitiveSessionState.Failed(message, aiEx?.partialText, conversationAtSubmission,
                    interactionId.value, requestId)
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
                val ownedJob = this.coroutineContext[Job]!!
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    activeTurnCoordinator.clear(interactionId, ownedJob)
                    if (!ownedJob.isActive) continuity?.end(interactionId.value)
                }
            }
        }
        
        scope.launch { launchTracked(interactionId, job) }
    }

    private val launchMutex = Mutex()
    private suspend fun launchTracked(interactionId: InteractionId, job: Job, resume: Boolean = false, automatic: Boolean = false) {
        launchMutex.withLock {
            if (isCancelled(interactionId.value)) { job.cancel(); return }
            val active = activeTurnCoordinator.activeTurn.value
            if (resume && active != null && (active.interactionId == interactionId || automatic)) {
                job.cancel(); return
            }
            val replaced = activeTurnCoordinator.replace(interactionId, job)
            if (replaced != null && replaced.interactionId != interactionId) {
                eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Interrupted(replaced.interactionId.value, interactionId.value)))
            }
            job.start()
        }
    }

    fun resume(runId: AgentRunId, responseDelivery: ResponseDelivery = ResponseDelivery.VISUAL_ONLY,
        continueInCurrentConversation: Boolean = false, automatic: Boolean = false) {
        scope.launch {
            val original = agentRecoveryExecutor.loadRun(runId) ?: return@launch
            if (original.state.isTerminal || original.steps.filterIsInstance<AgentStep.RunPrepared>().any { it.recoveryPolicy == RunRecoveryPolicy.NO_RESUME }) return@launch
            conversationStore.awaitReady()
            val previous = RunContinuityResolver.conversationId(original)
            val bound = if (continueInCurrentConversation || previous == null) conversationStore.activeConversationId() else previous
            if (bound == null || !conversationStore.isConversationAvailable(bound)) {
                eventBus.publish(AgentRecoveryBlocked(runId.value, original.interactionId.value, "Conversation unavailable. Continue in current conversation explicitly or cancel."))
                return@launch
            }
            if (automatic && previous == null) return@launch
            if (bound != previous && !agentRecoveryExecutor.bindConversation(runId, bound, continueInCurrentConversation)) return@launch
            val run = agentRecoveryExecutor.loadRun(runId) ?: return@launch
            val route = RunContinuityResolver.delivery(run)
            val inputModality = RunContinuityResolver.inputModality(run)
            val inputSurface = when (run.origin?.source) {
                ObservationSource.HeyCyanGlasses -> "WEARABLE"
                ObservationSource.AndroidAssist -> "ANDROID_ASSISTANT"
                else -> "PHONE"
            }
            val originalOrigin = com.halo.runtime.cognition.blackboard.InteractionOrigin(
                source = if (inputModality == com.halo.runtime.cognition.turn.InteractionInputModality.VOICE) com.halo.sdk.runtime.Source.MICROPHONE else com.halo.sdk.runtime.Source.USER,
                sourceDeviceId = run.origin?.hostId, captureId = run.sourceCaptureId, observationOrigin = run.origin,
                speechRoute = RunContinuityResolver.inputRoute(run))
            val interactionId = run.interactionId
            val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                continuity?.restore(run)
                if (nativeDispatcher?.ownsRun(runId) == true) {
                    val nativeResult = nativeDispatcher.resume(runId) ?: return@launch
                    val interpretation = com.halo.runtime.cognition.blackboard.Interpretation(
                        metadata = mapOf("interactionId" to nativeResult.request.interactionId),
                        origin = nativeDispatcher.resumeOrigin(runId))
                    publishRouteResponse(nativeResult.responseText(), interpretation,
                        if (interpretation.origin?.speechRoute is SpeechInputRoute.Device) ResponseDelivery.SPEECH_AND_VISUAL else responseDelivery,
                        nativeResult.request.interactionId, terminal = nativeResult.result is ToolTurnResult.Completed || nativeResult.result is ToolTurnResult.RunStopped,
                        boundRunId = nativeResult.runId)
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
                            userText = run.request,
                            response = AiResponse(text = result.responseText, providerId = "recovery", modelId = null, finishReason = "stop"),
                            responseDelivery = route.delivery,
                            outputTarget = route.target,
                            outputFallback = route.fallback,
                            sourceCaptureId = run.sourceCaptureId,
                            metadata = mapOf("inputSurface" to when (run.origin?.source) {
                                ObservationSource.HeyCyanGlasses -> "WEARABLE"
                                ObservationSource.AndroidAssist -> "ANDROID_ASSISTANT"
                                else -> "PHONE"
                            }),
                            conversationId = bound
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
                            responseDelivery = route.delivery, conversationId = bound, origin = originalOrigin,
                            sourceCaptureId = run.sourceCaptureId, outputTarget = route.target, outputFallback = route.fallback,
                            metadata = result.request.metadata + mapOf("inputSurface" to inputSurface, "inputModality" to inputModality.name)
                        )
                        eventBus.publish(CognitiveRequestStarted(requestId = turnRequest.requestId, interactionId = turnRequest.interactionId.value))
                        val commit = turnExecutor.execute(turnRequest) { _state.value = it }.copy(userText = run.request)
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
                            responseDelivery = route.delivery,
                            origin = originalOrigin,
                            conversationId = bound, sourceCaptureId = run.sourceCaptureId,
                            outputTarget = route.target, outputFallback = route.fallback,
                            metadata = mapOf("resumedRunId" to runId.value, "inputSurface" to inputSurface, "inputModality" to inputModality.name),
                            runId = runId,
                            mode = CognitiveTurnMode.CONTINUE_AFTER_TOOL
                        )
                        val commit = turnExecutor.execute(turnRequest) { _state.value = it }.copy(userText = run.request)
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
                            responseDelivery = route.delivery,
                            origin = originalOrigin,
                            conversationId = bound, sourceCaptureId = run.sourceCaptureId,
                            outputTarget = route.target, outputFallback = route.fallback,
                            metadata = mapOf("resumedRunId" to runId.value, "inputSurface" to inputSurface, "inputModality" to inputModality.name),
                            runId = runId,
                            mode = CognitiveTurnMode.CONTINUE_AFTER_TOOL
                        )
                        val commit = turnExecutor.execute(turnRequest) { _state.value = it }.copy(userText = run.request)
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
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { turnExecutor.interrupt(interactionId) }
                if (activeTurnCoordinator.activeTurn.value?.job === this.coroutineContext[Job]) _state.value = CognitiveSessionState.Idle
                throw e
            } catch (e: Exception) {
                println("[HALO-RESUME-FAIL] type=${e.javaClass.simpleName}")
                _state.value = CognitiveSessionState.Failed(mapErrorMessage(e))
            } finally {
                val ownedJob = this.coroutineContext[Job]!!
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { activeTurnCoordinator.clear(interactionId, ownedJob) }
            }
            }
            launchTracked(interactionId, job, resume = true, automatic = automatic)
        }
    }

    private fun mapErrorMessage(e: Exception): String {
        if (e is com.halo.sdk.runtime.multimodal.MediaAcquisitionException) return e.message ?: "Image unavailable. Reattach the original image."
        if (e is AiProviderException) {
            if (e.partialText != null) return "Response interrupted. The text below is incomplete; automatic retry was not attempted."
            if (e.type == AiErrorType.UNSUPPORTED_CAPABILITY && e.message?.startsWith("VISION_") == true)
                return "The selected vision model cannot analyze this image. Configure vision; your image and question are retained."
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
                AiErrorType.PROVIDER_UNAVAILABLE -> "The provider is temporarily unavailable. Your question and context are kept; try again later."
                AiErrorType.INVALID_REQUEST -> "The provider rejected the request or its parameters. Review the selected model configuration before retrying."
                AiErrorType.MODEL_LOAD_FAILED, AiErrorType.MODEL_NOT_READY -> "The selected model is not ready. Open model settings to check its files and initialization."
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

    /** Cancels only this interaction, including an observation still waiting for admission. */
    fun cancelInteraction(id: String) {
        synchronized(cancelledInteractions) {
            cancelledInteractions.add(id)
            while (cancelledInteractions.size > 128) cancelledInteractions.remove(cancelledInteractions.first())
        }
        scope.launch {
            val owned = ownsInteraction(id)
            activeTurnCoordinator.cancel(InteractionId(id), "Cancelled by user")
            if (!owned) eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Cancelled(id, "Cancelled before acceptance")))
            if (owned && activeTurnCoordinator.activeTurn.value == null) _state.value = CognitiveSessionState.Idle
        }
    }

    fun clearConversation() {
        val captured = conversationStore.activeConversationId() ?: return
        scope.launch { conversationStore.clear(captured) }
    }

    private companion object {
        const val MAX_PROCESSED_INTERPRETATIONS = 500
    }
}
