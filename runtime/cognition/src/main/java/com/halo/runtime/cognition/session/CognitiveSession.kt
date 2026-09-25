package com.halo.runtime.cognition.session

import com.halo.runtime.cognition.agent.*
import com.halo.cognition.language.InteractionId
import com.halo.sdk.provider.ToolCall
import com.halo.runtime.cognition.tools.ToolId
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
import com.halo.runtime.cognition.tools.ToolFailureKind
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
import com.halo.runtime.cognition.tools.ToolExposurePlanner
import com.halo.runtime.cognition.tools.ToolResourceAcquisition
import com.halo.runtime.cognition.tools.ToolResourceCoordinator
import com.halo.sdk.runtime.resource.ResourceAcquireResult
import com.halo.sdk.runtime.resource.ResourceHolder
import com.halo.sdk.runtime.resource.ResourceHolderKind
import com.halo.sdk.runtime.resource.ResourceLease

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
    private val agentRunCoordinator: AgentRunCoordinator? = null,
    private val assistantContexts: com.halo.runtime.cognition.context.AssistantContextRepository? = null,
    private val toolDiscovery: com.halo.runtime.cognition.tools.ToolDiscoveryService? = null,
    private val agentRunStore: com.halo.runtime.cognition.agent.AgentRunStore? = null,
    private val toolResourceCoordinator: ToolResourceCoordinator? = null,
    private val toolExposurePlanner: ToolExposurePlanner? = null,
    private val toolAvailabilityResolver: com.halo.runtime.cognition.tools.ToolAvailabilityResolver? = null,
) {
    private val _state = MutableStateFlow<CognitiveSessionState>(CognitiveSessionState.Idle)
    val state: StateFlow<CognitiveSessionState> = _state.asStateFlow()
    private var activeJob: Job? = null
    @Volatile private var activeInteractionId: String? = null

    private val processedInterpretationIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val observerScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    /**
     * RUNTIME-018: durable resume entry point. Uses the same provider router,
     * tool registry, authorizer and context machinery as [submit]; never a
     * parallel execution path.
     *
     * Flow: load run -> assemble checkpoint -> recovery planner -> atomic resume
     * claim -> execute the appropriate continuation. Startup never calls this
     * implicitly: resumption is always explicit (user or host policy).
     */
    fun resume(runId: AgentRunId, responseDelivery: ResponseDelivery = ResponseDelivery.VISUAL_ONLY) {
        activeJob?.cancel()
        activeJob = scope.launch {
            val run = loadRun(runId) ?: return@launch
            if (run.state.isTerminal) return@launch
            val interactionId = InteractionId(run.interactionId.value)
            val requestId = UUID.randomUUID().toString()
            try {
                val checkpoint = recovery.checkpointAssembler.assemble(run)
                val plan = recovery.recoveryPlanner.plan(
                    run,
                    catalog = toolRegistry,
                    capabilities = com.halo.runtime.cognition.agent.recovery.CapabilitySnapshot(),
                )

                // Conservative default: NO_RESUME runs are never auto-resumed here.
                val runPrepared = run.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()
                if (runPrepared?.recoveryPolicy == RunRecoveryPolicy.NO_RESUME) {
                    return@launch
                }

                // Atomic claim: exactly one surface may resume this run. A
                // lost claim is benign — another surface already resumed it —
                // and must never fail the run.
                val expected = when (run.state) {
                    AgentRunState.WAITING_FOR_APPROVAL -> AgentRunState.WAITING_FOR_APPROVAL
                    AgentRunState.WAITING_FOR_CLARIFICATION -> AgentRunState.WAITING_FOR_CLARIFICATION
                    AgentRunState.WAITING_FOR_CAPABILITY -> AgentRunState.WAITING_FOR_CAPABILITY
                    AgentRunState.WAITING_FOR_RESOURCE -> AgentRunState.WAITING_FOR_RESOURCE
                    else -> AgentRunState.SUSPENDED
                }
                val claimed = agentRunCoordinator?.claimResume(runId, expected) == true
                if (!claimed) return@launch

                eventBus.publish(
                    com.halo.runtime.cognition.agent.recovery.AgentRecoveryStarted(
                        runId = runId.value, interactionId = run.interactionId.value,
                        recoveryKind = plan::class.simpleName ?: "Unknown"
                    )
                )
                agentRunCoordinator?.transition(runId, AgentRunState.EXECUTING)

                when (plan) {
                    is com.halo.runtime.cognition.agent.recovery.RecoveryPlan.CompleteFromRecordedResponse ->
                        commitFinalResponse(runId, interactionId, requestId, plan.responseText, responseDelivery)

                    is com.halo.runtime.cognition.agent.recovery.RecoveryPlan.RetryProviderTurn ->
                        resumeProviderTurn(runId, interactionId, responseDelivery)

                    is com.halo.runtime.cognition.agent.recovery.RecoveryPlan.ContinueAfterToolResult ->
                        resumeProviderTurn(runId, interactionId, responseDelivery)

                    is com.halo.runtime.cognition.agent.recovery.RecoveryPlan.RetryTool ->
                        resumeToolInvocation(runId, interactionId, requestId, plan.invocation, plan.useOriginalIdempotencyKey, responseDelivery)

                    is com.halo.runtime.cognition.agent.recovery.RecoveryPlan.ReconcileTool ->
                        reconcileToolInvocation(runId, plan.invocation)

                    is com.halo.runtime.cognition.agent.recovery.RecoveryPlan.AwaitingInput -> {
                        // Request remains pending; nothing auto-executes.
                        eventBus.publish(
                            com.halo.runtime.cognition.agent.recovery.AgentRecoveryWaitingForInput(
                                runId = runId.value, interactionId = run.interactionId.value,
                                inputRequestId = plan.request.id.value,
                                inputKind = plan.request::class.simpleName ?: "Input"
                            )
                        )
                    }

                    is com.halo.runtime.cognition.agent.recovery.RecoveryPlan.MissingCapability,
                    is com.halo.runtime.cognition.agent.recovery.RecoveryPlan.MissingContext -> {
                        agentRunCoordinator?.transition(runId, AgentRunState.WAITING_FOR_CAPABILITY, "Missing capability or context")
                        eventBus.publish(
                            com.halo.runtime.cognition.agent.recovery.AgentRecoveryBlocked(
                                runId = runId.value, interactionId = run.interactionId.value,
                                reason = "Capability or context unavailable"
                            )
                        )
                    }

                    is com.halo.runtime.cognition.agent.recovery.RecoveryPlan.UncertainExternalAction,
                    is com.halo.runtime.cognition.agent.recovery.RecoveryPlan.UnsupportedLegacyState,
                    com.halo.runtime.cognition.agent.recovery.RecoveryPlan.NothingToDo -> {
                        agentRunCoordinator?.transition(runId, AgentRunState.INTERRUPTED, "Recovery cannot continue safely")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[HALO-RESUME-FAIL] runId=${runId.value} type=${e.javaClass.simpleName} message=${e.message}")
                runId.let { agentRunCoordinator?.fail(it, e.message ?: "Recovery failed") }
                _state.value = CognitiveSessionState.Failed(mapErrorMessage(e))
            }
        }
    }

    private suspend fun loadRun(runId: AgentRunId): AgentRun? = agentRunStore?.get(runId)

    private suspend fun continuationRequestText(runId: AgentRunId): String? {
        val run = agentRunStore?.get(runId) ?: return null
        val continuation = com.halo.runtime.cognition.agent.recovery.AgentContinuationAssembler.fromJournal(run)
        return continuation.messages.lastOrNull {
            it.role == com.halo.runtime.cognition.agent.recovery.AgentContinuationRole.USER
        }?.text
    }

    private fun continuationRequestTextBlocking(runId: AgentRunId): String? =
        kotlinx.coroutines.runBlocking { continuationRequestText(runId) }

    private val recovery: RecoveryComponents by lazy {
        RecoveryComponents(
            checkpointAssembler = com.halo.runtime.cognition.agent.recovery.AgentCheckpointAssembler(),
            recoveryPlanner = com.halo.runtime.cognition.agent.recovery.DefaultAgentRecoveryPlanner(),
        )
    }

    private inner class RecoveryComponents(
        val checkpointAssembler: com.halo.runtime.cognition.agent.recovery.AgentCheckpointAssembler,
        val recoveryPlanner: com.halo.runtime.cognition.agent.recovery.AgentRecoveryPlanner,
    )

    private fun resumeProviderTurn(
        runId: AgentRunId,
        interactionId: InteractionId,
        responseDelivery: ResponseDelivery,
    ) {
        // Continuation reconstructs the model transcript from the journal; the
        // provider target recorded on the interrupted turn is preferred at routing
        // time via run metadata (never silently rerouted across providers).
        val text = runCatching { continuationRequestTextBlocking(runId) }.getOrNull().orEmpty()
        if (text.isBlank()) {
            scope.launch {
                agentRunCoordinator?.transition(runId, AgentRunState.INTERRUPTED, "Continuation request text unavailable")
            }
            return
        }
        // Re-enter the normal submit path, but journal against the ORIGINAL run id.                    pendingRunIdForResume = runId
        submit(
            request = AiRequest(text = text, priority = InferencePriority.INTERACTIVE),
            responseDelivery = responseDelivery,
            metadata = mapOf("interactionId" to interactionId.value, "resumedRunId" to runId.value),
        )
        pendingRunIdForResume = null
    }

    private fun resumeToolInvocation(
        runId: AgentRunId,
        interactionId: InteractionId,
        requestId: String,
        invocation: com.halo.runtime.cognition.agent.ToolInvocation,
        useOriginalIdempotencyKey: Boolean,
        responseDelivery: ResponseDelivery,
    ) {
        scope.launch {
            val arguments: Map<String, Any> = runCatching {
                ToolArgumentCanonicalizer.parseToArgumentsMap(invocation.argumentsJson)
            }.getOrElse { emptyMap() }
                .filterValues { it != null }
                .mapValues { it.value!! }

            val call = com.halo.sdk.provider.ToolCall(
                invocation.toolCallId,
                invocation.toolId.value,
                arguments,
            )
            val descriptor = toolRegistry?.descriptor(invocation.toolId.value)

            if (descriptor != null) {
                when (val availability = toolAvailabilityResolver?.checkAvailability(descriptor)) {
                    is com.halo.runtime.cognition.tools.ToolAvailability.MissingCapabilities -> {
                        agentRunCoordinator?.transition(
                            runId,
                            AgentRunState.WAITING_FOR_CAPABILITY,
                            "Missing capabilities: " + availability.missing.joinToString { it.value },
                        )
                        return@launch
                    }
                    is com.halo.runtime.cognition.tools.ToolAvailability.Disabled,
                    is com.halo.runtime.cognition.tools.ToolAvailability.Degraded -> {
                        agentRunCoordinator?.transition(
                            runId,
                            AgentRunState.INTERRUPTED,
                            "Tool is no longer available for safe recovery",
                        )
                        return@launch
                    }
                    else -> Unit
                }
            }

            var lease: ResourceLease? = null
            val result = try {
                if (descriptor != null && toolResourceCoordinator != null) {
                    val holder = ResourceHolder(
                        id = invocation.invocationId.value,
                        interactionId = interactionId.value,
                        runId = runId.value,
                        toolInvocationId = invocation.invocationId.value,
                        kind = ResourceHolderKind.TOOL,
                    )
                    when (val acquisition = toolResourceCoordinator.prepareAndAcquire(descriptor, holder)) {
                        is ToolResourceAcquisition.Granted -> {
                            lease = acquisition.lease
                            // This invocation is already durably claimed. Replaying it is
                            // permitted only because the RecoveryPlanner selected RetryTool
                            // from the persisted replay policy.
                            toolRegistry.executeAuthorized(ToolAuthorization.Authorized(call))
                        }

                        is ToolResourceAcquisition.Blocked -> {
                            agentRunCoordinator?.recordStep(
                                AgentStep.ResourceBlocked(
                                    id = newStepId(),
                                    runId = runId,
                                    createdAt = Instant.now(),
                                    invocationId = invocation.invocationId,
                                    toolId = invocation.toolId.value,
                                    resourceIds = acquisition.plan.resourcePlan.claims.map {
                                        it.key.resourceId.value
                                    },
                                    endpointIds = acquisition.plan.resourcePlan.claims.map {
                                        it.key.endpointId.value
                                    },
                                    blockingLeaseIds = (acquisition.acquireResult as? ResourceAcquireResult.Busy)
                                        ?.conflicts?.map { it.heldLeaseId.value }.orEmpty(),
                                )
                            )
                            agentRunCoordinator?.transition(
                                runId,
                                AgentRunState.WAITING_FOR_RESOURCE,
                                "Required resource is currently busy",
                            )
                            return@launch
                        }

                        is ToolResourceAcquisition.CapabilityMissing -> {
                            agentRunCoordinator?.transition(
                                runId,
                                AgentRunState.WAITING_FOR_CAPABILITY,
                                "Required device capability is unavailable",
                            )
                            return@launch
                        }
                    }
                } else {
                    toolRegistry?.executeAuthorized(ToolAuthorization.Authorized(call))
                }
            } finally {
                lease?.release()
            }

            recordToolOutcome(runId, invocation, result)
            resumeProviderTurn(runId, interactionId, responseDelivery)
        }
    }

    private fun reconcileToolInvocation(
        runId: AgentRunId,
        invocation: com.halo.runtime.cognition.agent.ToolInvocation,
    ) {
        scope.launch {
            val executor = toolRegistry?.executorFor(invocation.toolId.value)
            val reconciliation = (executor as? com.halo.runtime.cognition.tools.RecoverableToolExecutor)
                ?.reconcile(invocation)
                ?: com.halo.runtime.cognition.tools.ToolReconciliation.Unknown("Tool does not support reconciliation")
            when (reconciliation) {
                is com.halo.runtime.cognition.tools.ToolReconciliation.Completed -> {
                    recordToolOutcome(runId, invocation, reconciliation.result)
                    // Outcome journaled; continue the conversation.
                    resumeProviderTurn(runId, InteractionId(invocation.interactionId.value), ResponseDelivery.VISUAL_ONLY)
                }
                is com.halo.runtime.cognition.tools.ToolReconciliation.NotExecuted ->
                    // Verified never executed: the pending invocation can be safely retried.
                    resumeToolInvocation(runId, InteractionId(invocation.interactionId.value),
                        invocation.toolCallId, invocation, useOriginalIdempotencyKey = true,
                        responseDelivery = ResponseDelivery.VISUAL_ONLY)
                is com.halo.runtime.cognition.tools.ToolReconciliation.StillRunning,
                is com.halo.runtime.cognition.tools.ToolReconciliation.Unknown ->
                    agentRunCoordinator?.transition(runId, AgentRunState.INTERRUPTED,
                        (reconciliation as? com.halo.runtime.cognition.tools.ToolReconciliation.Unknown)?.reason
                            ?: "Tool is still running externally")
            }
        }
    }

    private suspend fun recordToolOutcome(
        runId: AgentRunId,
        invocation: com.halo.runtime.cognition.agent.ToolInvocation,
        result: ToolExecutionResult?,
    ) {
        when (result) {
            is ToolExecutionResult.Success -> agentRunCoordinator?.recordStep(
                AgentStep.ToolOutcomeRecorded(AgentStepId(UUID.randomUUID().toString()), runId, Instant.now(),
                    invocation.invocationId, invocation.toolCallId, invocation.toolId.value, true,
                    result.output, result.actionTruth, null))
            is ToolExecutionResult.Rejected -> agentRunCoordinator?.recordStep(
                AgentStep.ToolFailed(AgentStepId(UUID.randomUUID().toString()), runId, Instant.now(),
                    invocation.toolCallId, invocation.toolId.value, result.reason))
            is ToolExecutionResult.Failed -> if (!result.completionUnknown) agentRunCoordinator?.recordStep(
                AgentStep.ToolFailed(AgentStepId(UUID.randomUUID().toString()), runId, Instant.now(),
                    invocation.toolCallId, invocation.toolId.value, result.message))
            null -> Unit
        }
    }

    private suspend fun commitFinalResponse(
        runId: AgentRunId,
        interactionId: InteractionId,
        requestId: String,
        responseText: String,
        responseDelivery: ResponseDelivery,
    ) {
        val run = agentRunStore?.get(runId)
        val commitId = "${runId.value}:$requestId"
        conversationStore.appendIfAbsent(commitId, run?.request.orEmpty(), responseText,
            mapOf("interactionId" to interactionId.value, "requestId" to requestId))
        agentRunCoordinator?.recordStep(
            AgentStep.ResponseCommitted(AgentStepId(UUID.randomUUID().toString()), runId, Instant.now(),
                commitId, interactionId.value))
        _state.value = CognitiveSessionState.Completed(AiResponse(text = responseText, providerId = "recovery", modelId = null, finishReason = "stop"))
        eventBus.publish(CognitiveCompleted(requestId = requestId, response = responseText, interactionId = interactionId.value))
        eventBus.publish(
            com.halo.runtime.cognition.agent.recovery.AgentRecoveryCompleted(
                runId = runId.value, interactionId = interactionId.value, recoveryKind = "CompleteFromRecordedResponse"
            )
        )
    }

    @Volatile private var activeRequestText: String? = null

    /**
     * RUNTIME-018: set by [resume] before reusing the normal submit path so the
     * resumed turn journals against the ORIGINAL durable run id.
     */
    @Volatile private var pendingRunIdForResume: AgentRunId? = null
    @Volatile private var pendingRunId: AgentRunId? = null

    /**
     * RUNTIME-019: the durable input request the active turn suspended on, if
     * any. Cleared when the turn ends; the journal remains authoritative.
     */
    @Volatile var pendingRunIdInput: AgentInputRequest? = null
        private set
    @Volatile private var journaledTurnId: com.halo.runtime.cognition.agent.ProviderTurnId? = null

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
                                val fallbackPolicy = if (outputTarget is SpeechOutputTarget.Device) {
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
        val fallbackPolicy = if (outputTarget is SpeechOutputTarget.Device) {
            SpeechOutputFallback.DISALLOW_PHONE
        } else {
            SpeechOutputFallback.ALLOW_PHONE
        }
        val effectivePolicy = policy ?: defaultPolicyProvider()
        val newInteractionId = metadata["interactionId"] ?: origin?.captureId ?: UUID.randomUUID().toString()
        val interruptedInteractionId = activeInteractionId
        activeJob?.cancel()
        activeInteractionId = newInteractionId
        activeRequestText = request.text
        pendingRunId = pendingRunIdForResume
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

                val assembly = contextAssembler.assemble(
                    policy = targetPolicy,
                    capabilities = capabilities,
                    request = com.halo.runtime.cognition.context.ContextAssemblyRequest(
                        interactionId = interactionId.value,
                        allowGenericFallback = false,
                    ),
                )
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

                /*
                 * RUNTIME-018: when a run owns this turn, the provider turn is
                 * journaled BEFORE inference so recovery can distinguish "started but
                 * never completed" from "completed". No provider internals persist.
                 */
                val durableRunId = pendingRunId ?: runId
                if (durableRunId != null) {
                    val turnId = com.halo.runtime.cognition.agent.ProviderTurnId("$durableRunId:$requestId")
                    agentRunCoordinator?.recordStep(
                        AgentStep.ProviderTurnStarted(
                            newStepId(), durableRunId, Instant.now(),
                            turnId = turnId,
                            providerId = target.providerId,
                            modelId = target.modelId,
                            requestIdentity = ToolArgumentCanonicalizer.sha256Hex(
                                (request.text ?: "") + ":" + (request.image?.contentHashCode() ?: 0)),
                            exposedToolIds = context.availableTools.map { it.name }.toSet(),
                            requiredCapabilities = capabilities.map { it.name }.toSet(),
                        )
                    )
                    journaledTurnId = turnId
                }

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
                            if (it.speechRoute is SpeechInputRoute.Device) com.halo.sdk.host.ObservationSource.HeyCyanGlasses
                            else com.halo.sdk.host.ObservationSource.AndroidPhoneMic, it.sourceDeviceId)
                    }, speechOutputTarget = outputTarget, speechOutputFallback = fallbackPolicy, sourceCaptureId = origin?.captureId))
                    runId?.let {
                        agentRunCoordinator?.transition(it, AgentRunState.PLANNING)
                        agentRunCoordinator?.recordStep(AgentStep.PlanCreated(newStepId(), it, Instant.now(), requestId, "Execute requested tools"))
                        agentRunCoordinator?.transition(it, AgentRunState.EXECUTING)
                    }
                    /*
                     * RUNTIME-018: persist the completed provider response BEFORE any
                     * tool executes or the answer publishes. This is the recovery
                     * point if the process dies one line later.
                     */
                    runId?.let { rid ->
                        agentRunCoordinator?.recordStep(
                            AgentStep.ProviderTurnCompleted(
                                newStepId(), rid, Instant.now(),
                                turnId = journaledTurnId
                                    ?: com.halo.runtime.cognition.agent.ProviderTurnId("$rid:$requestId"),
                                assistantText = response.text,
                                toolCalls = response.toolCalls.map { call ->
                                    val canonical = ToolArgumentCanonicalizer.canonicalize(call.arguments)
                                    com.halo.runtime.cognition.agent.ProviderToolCallRecord(
                                        toolCallId = call.id,
                                        toolName = call.functionName,
                                        argumentsJson = canonical,
                                    )
                                },
                                finishReason = response.finishReason.orEmpty(),
                            )
                        )
                    }
                    val loopResult = executeToolLoop(
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
                    if (loopResult == null) {
                        /*
                         * RUNTIME-019: the run suspended awaiting durable user
                         * input (journal holds InputRequested, state is
                         * WAITING_FOR_APPROVAL). Resolution resumes it; no
                         * completion bookkeeping happens on this turn.
                         */
                        return@launch
                    }
                    response = loopResult
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
                        /*
                         * RUNTIME-018: idempotent commit. Process death can land after
                         * the response is recorded but before/after the conversation
                         * write; a resumed task must not duplicate the turn.
                         */
                        val commitId = "${runId?.value ?: "n Run"}:$requestId"
                        val turn = conversationStore.appendIfAbsent(
                            commitId = commitId,
                            userText = userText,
                            assistantText = responseText,
                            metadata = metadata + mapOf("interactionId" to interactionId.value, "requestId" to requestId, "provider" to target.providerId, "model" to (target.modelId ?: ""))
                        )
                        runId?.let { rid ->
                            agentRunCoordinator?.recordStep(
                                AgentStep.ResponseCommitted(newStepId(), rid, Instant.now(), commitId, interactionId.value))
                        }
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
                    eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Cancelled(interactionId.value, "Interaction cancelled")))
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
                pendingRunId = null
                pendingRunIdInput = null
                journaledTurnId = null
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
    ): AiResponse? {
        val authorizationScopeId = interactionId.value
        try {
        var response = initialResponse
        var currentContext = initialContext
        var currentExposure = toolExposurePlanner?.fromDefinitions(initialContext.availableTools)
        var round = 0
        var totalToolCalls = 0
        var hasUnconfirmedAction = false
        val executedCallFingerprints = mutableMapOf<String, Int>()

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

                val currentSnapshotHash = assistantContexts?.screenFor(interactionId.value)?.hashCode() ?: 0
                val fingerprint = "$currentSnapshotHash:${toolCall.functionName}:${toolCall.arguments}"
                val count = (executedCallFingerprints[fingerprint] ?: 0) + 1
                executedCallFingerprints[fingerprint] = count
                if (count > 2) {
                    throw AiProviderException(
                        AiErrorType.GENERATION_FAILED,
                        "Loop detected: repeating identical action '${toolCall.functionName}' without UI state change. Clarification required."
                    )
                }
                /*
                 * RUNTIME-018: canonical argument persistence. "{}" placeholders are
                 * gone: the invocation record carries the exact canonical arguments
                 * and digest used for identity, approval matching and recovery.
                 */
                val canonicalArguments = ToolArgumentCanonicalizer.canonicalize(toolCall.arguments)
                val argumentsDigest = ToolArgumentCanonicalizer.digest(canonicalArguments)
                val invocationId = ToolInvocationId(UUID.randomUUID().toString())
                val idempotencyKey = ToolInvocation.idempotencyKey(
                    runId ?: AgentRunId("ephemeral"), toolCall.id, com.halo.runtime.cognition.tools.ToolId(toolCall.functionName), argumentsDigest)
                val replaySafety = toolRegistry?.descriptor(toolCall.functionName)?.security?.replaySafety
                    ?: com.halo.runtime.cognition.tools.ReplaySafety.NEVER_REPLAY
                val descriptorVersion = 1
                runId?.let {
                    agentRunCoordinator?.recordStep(
                        AgentStep.ToolInvocationPrepared(
                            newStepId(), it, Instant.now(), invocationId, toolCall.id, toolCall.functionName,
                            canonicalArguments, argumentsDigest, idempotencyKey, descriptorVersion,
                            replaySafety.name,
                        )
                    )
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

                /*
                 * RUNTIME-017: discovery only changes schema exposure, never the
                 * authorization surface. Expanded tools still pass the same skill/
                 * policy gates as directly-exposed tools; the registry is not mutated.
                 */
                val isDiscoveryCall = toolCall.functionName ==
                    com.halo.runtime.cognition.tools.ToolDiscoveryService.DISCOVER_TOOL
                val toolIsEnabled = workspaceReadAllowed && (isDiscoveryCall || (toolRegistry?.let { registry ->
                    skillRegistry?.enabledTools(registry.definitions())?.any {
                        it.name == toolCall.functionName || it.id == toolCall.functionName
                    } ?: true
                } ?: false))
                val descriptor = toolRegistry?.descriptor(toolCall.functionName)
                val availability = if (!isDiscoveryCall && descriptor != null) {
                    toolAvailabilityResolver?.checkAvailability(descriptor)
                        ?: com.halo.runtime.cognition.tools.ToolAvailability.Available
                } else {
                    com.halo.runtime.cognition.tools.ToolAvailability.Available
                }
                val availabilityReason = when (availability) {
                    com.halo.runtime.cognition.tools.ToolAvailability.Available -> null
                    is com.halo.runtime.cognition.tools.ToolAvailability.Disabled -> availability.reason
                    is com.halo.runtime.cognition.tools.ToolAvailability.MissingCapabilities ->
                        "Missing capabilities: " + availability.missing.joinToString { it.value }
                    is com.halo.runtime.cognition.tools.ToolAvailability.Degraded -> availability.reason
                }

                if (availabilityReason != null) {
                    if (runId != null &&
                        availability is com.halo.runtime.cognition.tools.ToolAvailability.MissingCapabilities
                    ) {
                        agentRunCoordinator?.transition(
                            runId,
                            AgentRunState.WAITING_FOR_CAPABILITY,
                            availabilityReason,
                        )
                        return null
                    }
                    toolMessages.add(
                        AIMessage(
                            Role.TOOL,
                            "Tool unavailable: $availabilityReason",
                            toolCallId = toolCall.id,
                        )
                    )
                    continue
                }

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
                    runId?.let {
                        agentRunCoordinator?.recordStep(
                            AgentStep.ToolFailed(
                                newStepId(), it, Instant.now(),
                                toolCall.id, toolCall.functionName, reason,
                            )
                        )
                    }
                    eventBus.publish(
                        ToolCallRejected(
                            requestId = requestId,
                            toolCallId = toolCall.id,
                            toolId = toolCall.functionName,
                            reason = reason,
                        )
                    )
                    toolMessages.add(
                        AIMessage(
                            Role.TOOL,
                            "Tool execution rejected: $reason",
                            toolCallId = toolCall.id,
                        )
                    )
                    continue
                }

                /*
                 * RUNTIME-019: durable authorization first. Interactive approval
                 * journals the request and suspends the run; UI never owns execution.
                 */
                val previouslyApproved = runId != null &&
                    hasApprovedApproval(runId, toolCall.functionName, argumentsDigest)
                val durableDecision = if (runId != null && !previouslyApproved) {
                    (toolAuthorizer as? com.halo.runtime.cognition.tools.PolicyToolAuthorizer)?.authorizeDurable(
                        toolCall = toolCall,
                        runId = runId,
                        interactionId = interactionId,
                        invocationId = invocationId,
                        argumentsDigest = argumentsDigest,
                    ) { UUID.randomUUID().toString() }
                } else {
                    null
                }
                val authorization = when {
                    previouslyApproved -> ToolAuthorization.Authorized(toolCall)
                    durableDecision is com.halo.runtime.cognition.tools.AuthorizationDecision.Authorized ->
                        ToolAuthorization.Authorized(toolCall)
                    durableDecision is com.halo.runtime.cognition.tools.AuthorizationDecision.Rejected ->
                        ToolAuthorization.Rejected(toolCall, durableDecision.reason)
                    durableDecision is com.halo.runtime.cognition.tools.AuthorizationDecision.NeedsUserInput -> {
                        val request = durableDecision.request
                        val waitingRunId = runId ?: return null
                        agentRunCoordinator?.recordStep(
                            AgentStep.InputRequested(newStepId(), waitingRunId, Instant.now(), request.id, request)
                        )
                        agentRunCoordinator?.transition(waitingRunId, AgentRunState.WAITING_FOR_APPROVAL)
                        pendingRunIdInput = request
                        eventBus.publish(
                            ToolCallRejected(
                                requestId = requestId,
                                toolCallId = toolCall.id,
                                toolId = toolCall.functionName,
                                reason = "Waiting for your approval",
                            )
                        )
                        return null
                    }
                    else -> toolAuthorizer.authorize(toolCall, authorizationScopeId)
                }

                when (authorization) {
                    is ToolAuthorization.Rejected -> {
                        runId?.let {
                            agentRunCoordinator?.recordStep(
                                AgentStep.ToolFailed(
                                    newStepId(), it, Instant.now(),
                                    toolCall.id, toolCall.functionName, authorization.reason,
                                )
                            )
                        }
                        eventBus.publish(
                            ToolCallRejected(
                                requestId = requestId,
                                toolCallId = toolCall.id,
                                toolId = toolCall.functionName,
                                reason = authorization.reason,
                            )
                        )
                        toolMessages.add(
                            AIMessage(
                                Role.TOOL,
                                "Tool execution rejected: ${authorization.reason}",
                                toolCallId = toolCall.id,
                            )
                        )
                        continue
                    }

                    is ToolAuthorization.Authorized -> eventBus.publish(
                        ToolCallAuthorized(
                            requestId = requestId,
                            toolCallId = toolCall.id,
                            toolId = toolCall.functionName,
                        )
                    )
                }

                val authorized = authorization as ToolAuthorization.Authorized

                suspend fun executeClaimed(): ToolExecutionResult {
                    if (runId != null) {
                        /*
                         * The durable execution claim is written only AFTER all
                         * required resources have been granted and immediately
                         * BEFORE the executor may cause side effects. A resource
                         * wait therefore never masquerades as an uncertain action.
                         */
                        val claimed = agentRunCoordinator?.recordStep(
                            AgentStep.ToolExecutionClaimed(
                                newStepId(), runId, Instant.now(),
                                invocationId, toolCall.id, toolCall.functionName,
                                idempotencyKey, replaySafety.name,
                            )
                        ) == true
                        check(claimed) {
                            "Tool execution already claimed; completion must be reconciled before retry"
                        }
                    }
                    return toolRegistry.executeAuthorized(authorized)
                }

                var leaseToRelease: ResourceLease? = null
                val result = try {
                    if (descriptor != null && toolResourceCoordinator != null) {
                        val holder = ResourceHolder(
                            id = invocationId.value,
                            interactionId = interactionId.value,
                            runId = runId?.value,
                            toolInvocationId = invocationId.value,
                            kind = ResourceHolderKind.TOOL,
                        )
                        when (val acquisition = toolResourceCoordinator.prepareAndAcquire(descriptor, holder)) {
                            is ToolResourceAcquisition.Granted -> {
                                leaseToRelease = acquisition.lease
                                executeClaimed()
                            }

                            is ToolResourceAcquisition.Blocked -> {
                                if (runId != null) {
                                    agentRunCoordinator?.recordStep(
                                        AgentStep.ResourceBlocked(
                                            id = newStepId(),
                                            runId = runId,
                                            createdAt = Instant.now(),
                                            invocationId = invocationId,
                                            toolId = toolCall.functionName,
                                            resourceIds = acquisition.plan.resourcePlan.claims.map {
                                                it.key.resourceId.value
                                            },
                                            endpointIds = acquisition.plan.resourcePlan.claims.map {
                                                it.key.endpointId.value
                                            },
                                            blockingLeaseIds = (acquisition.acquireResult as? ResourceAcquireResult.Busy)
                                                ?.conflicts
                                                ?.map { it.heldLeaseId.value }
                                                .orEmpty(),
                                        )
                                    )
                                    agentRunCoordinator?.transition(
                                        runId,
                                        AgentRunState.WAITING_FOR_RESOURCE,
                                        "Required resource is currently busy",
                                    )
                                    return null
                                }
                                ToolExecutionResult.Failed(
                                    toolCallId = toolCall.id,
                                    toolId = toolCall.functionName,
                                    message = "Required resource is currently busy",
                                    failureKind = ToolFailureKind.RESOURCE_BUSY,
                                )
                            }

                            is ToolResourceAcquisition.CapabilityMissing -> {
                                if (runId != null) {
                                    agentRunCoordinator?.transition(
                                        runId,
                                        AgentRunState.WAITING_FOR_CAPABILITY,
                                        "Required device capability is unavailable",
                                    )
                                    return null
                                }
                                ToolExecutionResult.Failed(
                                    toolCallId = toolCall.id,
                                    toolId = toolCall.functionName,
                                    message = "Required device capability is unavailable",
                                    failureKind = ToolFailureKind.RESOURCE_UNAVAILABLE,
                                )
                            }
                        }
                    } else {
                        executeClaimed()
                    }
                } finally {
                    leaseToRelease?.release()
                }

                when (result) {
                    is ToolExecutionResult.Success -> {
                        hasUnconfirmedAction = hasUnconfirmedAction || result.actionTruth != ActionTruth.CONFIRMED
                        runId?.let {
                            /* Persist the outcome BEFORE the result reaches the provider. */
                            agentRunCoordinator?.recordStep(
                                AgentStep.ToolOutcomeRecorded(newStepId(), it, Instant.now(), invocationId,
                                    toolCall.id, toolCall.functionName, true,
                                    result.output.take(ToolExecutionLimits.MAX_TOOL_OUTPUT_CHARS),
                                    result.actionTruth, null)
                            )
                        }
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
                        if (isDiscoveryCall && toolExposurePlanner != null) {
                            val rawList = toolCall.arguments["toolIds"] as? List<*>
                            val requestedToolIds = rawList?.filterIsInstance<String>() ?: emptyList()
                            val activeExp = currentExposure ?: toolExposurePlanner.initialExposure()
                            val newExposure = toolExposurePlanner.expand(activeExp, requestedToolIds)
                            currentExposure = newExposure
                            currentContext = currentContext.copy(
                                availableTools = toolExposurePlanner.effectiveDefinitions(newExposure, toolDiscovery?.catalogueSummary().orEmpty())
                            )
                        }
                    }

                    is ToolExecutionResult.Rejected -> {
                        runId?.let {
                            agentRunCoordinator?.recordStep(
                                AgentStep.ToolOutcomeRecorded(newStepId(), it, Instant.now(), invocationId,
                                    toolCall.id, toolCall.functionName, false, result.reason,
                                    ActionTruth.FAILED, ToolFailureKind.VALIDATION.name))
                        }
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
                        if (!result.completionUnknown) runId?.let {
                            agentRunCoordinator?.recordStep(
                                AgentStep.ToolOutcomeRecorded(newStepId(), it, Instant.now(), invocationId,
                                    toolCall.id, toolCall.functionName, false, result.message,
                                    ActionTruth.FAILED, result.failureKind.name))
                        }
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

    /**
     * RUNTIME-019: true when the journal holds an approval request for exactly
     * this tool + canonical arguments digest that was resolved Approved (and
     * not later superseded by a newer unresolved request). Evidence only —
     * never model prose — so re-emitted identical calls do not re-prompt.
     */
    private suspend fun hasApprovedApproval(
        runId: AgentRunId,
        toolName: String,
        argumentsDigest: String,
    ): Boolean {
        val run = agentRunStore?.get(runId) ?: return false
        var pendingMatch: String? = null
        var approved = false
        for (step in run.steps) {
            when (step) {
                is AgentStep.InputRequested -> {
                    val approval = step.input as? AgentInputRequest.Approval
                    if (approval != null &&
                        approval.toolId.value == toolName &&
                        approval.argumentsDigest == argumentsDigest
                    ) {
                        pendingMatch = step.requestId.value
                        approved = false
                    }
                }
                is AgentStep.InputResolved -> if (step.requestId.value == pendingMatch) {
                    approved = step.resolution == "Approved"
                    pendingMatch = null
                }
                else -> Unit
            }
        }
        return approved
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
