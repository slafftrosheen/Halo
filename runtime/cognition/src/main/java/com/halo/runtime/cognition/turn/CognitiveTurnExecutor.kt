package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunCoordinator
import com.halo.runtime.cognition.agent.AgentRunGuardRegistry
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentRunKind
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.AgentStepId
import com.halo.runtime.cognition.agent.RunGuardDecision
import com.halo.runtime.cognition.agent.RunGuardMessages
import com.halo.runtime.cognition.agent.ProviderTurnId
import com.halo.runtime.cognition.agent.ProviderToolCallRecord
import com.halo.runtime.cognition.agent.RunRecoveryPolicy
import com.halo.runtime.cognition.agent.ToolArgumentCanonicalizer
import com.halo.runtime.cognition.agent.AgentRequest
import com.halo.runtime.cognition.tools.LocalModelToolPolicy
import com.halo.runtime.cognition.tools.ToolExposureContext
import com.halo.runtime.cognition.tools.ToolExposureDirective
import com.halo.runtime.cognition.tools.ToolExposureGate
import com.halo.runtime.cognition.tools.ToolExposurePlanner
import com.halo.runtime.cognition.tools.ToolExposureSet
import com.halo.runtime.cognition.tools.ToolLoopLimits
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.ProviderTurnKind
import com.halo.sdk.runtime.CognitiveRequestStarted
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.SpeechInputRoute
import java.time.Instant
import java.util.UUID

class CognitiveTurnExecutor(
    private val preparer: CognitiveTurnPreparer,
    private val providerExecutor: ProviderTurnExecutor,
    private val toolExecutor: ToolTurnExecutor,
    private val agentRunCoordinator: AgentRunCoordinator?,
    private val agentRunStore: AgentRunStore?,
    private val eventBus: EventBus,
    /** AGENTIC-001: re-plans tool exposure between tool rounds. */
    private val toolExposurePlanner: ToolExposurePlanner? = null,
    private val toolExposureGate: ToolExposureGate? = null,
    private val limits: ToolLoopLimits = ToolLoopLimits(),
    /**
     * MINING-P1-001 bounded-run guard. Shared with [toolExecutor] through the registry so the
     * provider loop and the tool loop cannot disagree about how long this run has left.
     */
    private val runGuards: AgentRunGuardRegistry? = null,
    private val payloadStore: com.halo.runtime.cognition.context.ContentPayloadStore? = null,
) {
    private fun newStepId() = AgentStepId(UUID.randomUUID().toString())

    private suspend fun executeProvider(
        target: com.halo.sdk.provider.AiExecutionTarget,
        request: com.halo.sdk.provider.AiRequest,
        context: AiContext,
        requestId: String,
        interactionId: InteractionId,
        workload: com.halo.sdk.provider.AiWorkloadSpec,
        runId: AgentRunId?,
    ): ProviderTurnResult {
        val boundedContext = com.halo.runtime.cognition.context.CognitiveContextPlanner().fitProviderContext(
            (target.model.contextLength ?: 8192L).coerceIn(1, Int.MAX_VALUE.toLong()).toInt(), request, context)
        val guard = runId?.let { runGuards?.guardFor(it, interactionId) }
        suspend fun stopped(stop: RunGuardDecision.Stop): ProviderTurnResult {
            agentRunCoordinator?.recordStep(guard!!.toJournalStep(stop))
            return ProviderTurnResult.Response(AiResponse(text = RunGuardMessages.userMessage(stop),
                providerId = target.providerId, modelId = target.modelId), target)
        }
        if (guard != null) {
            val durable = runId?.let { agentRunStore?.get(it) }
            if (durable?.parentRunId != null && durable.steps.count { it is AgentStep.ProviderTurnStarted } >
                com.halo.runtime.cognition.agent.AgentRunLimits.DELEGATED.maxToolRounds + 1) {
                return stopped(RunGuardDecision.Stop(com.halo.runtime.cognition.agent.RunGuardReason.TOO_MANY_TOOL_ROUNDS,
                    com.halo.runtime.cognition.agent.RunGuardEvidence(guard.currentToolRounds)))
            }
            val chars = (if (request.appendTextAsUserMessage) request.text.orEmpty().length.toLong() else 0) + boundedContext.conversation.sumOf { it.content.length.toLong() } +
                boundedContext.availableTools.sumOf { it.description.length.toLong() + it.parameters.toString().length }
            guard.tokens.noteEstimatedPrompt((chars + 3) / 4)
            (guard.beforeRound() as? RunGuardDecision.Stop)?.let { return stopped(it) }
        }
        val outcome = if (guard == null) providerExecutor.execute(target, request, boundedContext, requestId, interactionId, workload)
        else {
            val result = kotlinx.coroutines.withTimeoutOrNull(guard.remainingMs.coerceAtLeast(1)) {
                providerExecutor.execute(target, request, boundedContext, requestId, interactionId, workload)
            }
            result ?: return stopped(RunGuardDecision.Stop(com.halo.runtime.cognition.agent.RunGuardReason.DEADLINE_EXCEEDED,
                com.halo.runtime.cognition.agent.RunGuardEvidence(guard.currentToolRounds, elapsedMs = guard.elapsedMs)))
        }
        if (guard != null && outcome is ProviderTurnResult.Response) {
            guard.tokens.noteProviderReported(outcome.response.metrics?.inputTokens?.toLong(), outcome.response.metrics?.outputTokens?.toLong())
            val decision = guard.onGenerationDelta(outcome.response.text)
            guard.onGenerationCompleted()
            if (decision is RunGuardDecision.Stop) return stopped(decision)
        }
        if (outcome is ProviderTurnResult.Response && runId != null && agentRunStore?.get(runId)?.parentRunId != null && outcome.response.text.length > 2048) {
            val stored = payloadStore?.put(com.halo.runtime.cognition.context.PayloadOwner(
                com.halo.runtime.cognition.context.PayloadOwnerKind.AGENT_RUN, runId.value), outcome.response.text.toByteArray(),
                com.halo.runtime.cognition.context.PayloadClassification.CHILD_AGENT_RESULT,
                com.halo.runtime.cognition.context.ContextTrust.EXTERNAL_UNTRUSTED, "child:${runId.value}", remoteAllowed = workload.allowRemote)
            val ref = (stored as? com.halo.runtime.cognition.context.PayloadOutcome.Value)?.value
            return outcome.copy(response = outcome.response.copy(text = outcome.response.text.take(1800) +
                (ref?.let { "\nAdditional findings: payload=${it.id}; sha256=${it.sha256}; bytes=${it.byteCount}" }
                    ?: "\nAdditional findings omitted by payload policy."), payloadRefId = ref?.id))
        }
        return outcome
    }

    private fun trace(event: String, fields: List<Pair<String, Any?>>) {
        val rendered = fields.joinToString(" ") { (key, value) -> "$key=${value ?: "none"}" }
        println("[HALO_$event] $rendered")
    }

    suspend fun execute(
        turn: CognitiveTurnRequest,
        onStateChanged: suspend (CognitiveSessionState) -> Unit = {}
    ): CognitiveResponseCommit {
        onStateChanged(CognitiveSessionState.PreparingContext)
        val prepared = preparer.prepare(turn)

        var activeRunId = turn.runId
        val createDurableRunIfNeeded = suspend {
            if (activeRunId == null && agentRunCoordinator != null) {
                val created = agentRunCoordinator.start(
                    AgentRequest(
                        interactionId = turn.interactionId,
                        text = turn.request.text.orEmpty(),
                        sourceCaptureId = turn.sourceCaptureId,
                        kind = AgentRunKind.INTERACTIVE,
                    )
                )
                activeRunId = created
            }
            activeRunId
        }

        onStateChanged(CognitiveSessionState.Reasoning(prepared.target.providerId, prepared.target.modelId))
        val turnId = ProviderTurnId(UUID.randomUUID().toString())

        var currentContext = prepared.context
        var currentRequest = prepared.providerRequest
        var currentTarget = prepared.target
        val recoveryRun = turn.runId?.let { agentRunStore?.get(it) }
        val pendingResponse = if (turn.mode == CognitiveTurnMode.CONTINUE_AFTER_TOOL) recoveryRun?.steps
            ?.filterIsInstance<AgentStep.ProviderTurnCompleted>()?.lastOrNull()?.takeIf { completed ->
                completed.toolCalls.any { call -> recoveryRun.steps.none { step -> when (step) {
                    is AgentStep.ToolOutcomeRecorded -> step.toolCallId == call.toolCallId
                    is AgentStep.ToolRejected -> step.toolCallId == call.toolCallId
                    is AgentStep.ToolCompleted -> step.toolCallId == call.toolCallId
                    is AgentStep.ToolFailed -> step.toolCallId == call.toolCallId
                    else -> false
                } } }
            } else null
        if (pendingResponse != null) {
            val replayedIds = pendingResponse.toolCalls.map { it.toolCallId }.toSet()
            // Reconstructed pending declarations are replaced by the original response and its newly resolved atomic tool group below.
            currentContext = currentContext.copy(conversation = currentContext.conversation.filterNot { message ->
                message.toolCalls.any { it.id in replayedIds } || message.toolCallId in replayedIds
            })
        }

        if (turn.mode == CognitiveTurnMode.CONTINUE_AFTER_TOOL) {
            currentRequest = currentRequest.copy(
                appendTextAsUserMessage = false,
                turnKind = ProviderTurnKind.TOOL_CONTINUATION,
            )
        }

        if (turn.mode == CognitiveTurnMode.FRESH || turn.mode == CognitiveTurnMode.RECOVER_PROVIDER ||
            turn.mode == CognitiveTurnMode.CONTINUE_AFTER_TOOL
        ) {
            val durableRunId = createDurableRunIfNeeded()
            if (durableRunId != null && pendingResponse == null) {
                agentRunCoordinator?.recordStep(
                    AgentStep.ProviderTurnStarted(
                        id = newStepId(),
                        runId = durableRunId,
                        createdAt = Instant.now(),
                        turnId = turnId,
                        providerId = prepared.target.providerId,
                        modelId = prepared.target.modelId,
                        requestIdentity = ToolArgumentCanonicalizer.sha256Hex(
                            (currentRequest.text ?: "") + ":" + (currentRequest.image?.contentHashCode() ?: 0)
                        ),
                        exposedToolIds = currentContext.availableTools.map { it.name }.toSet(),
                        requiredCapabilities = prepared.requiredCapabilityIds,
                        contextRequirements = prepared.contextRequirementIds,
                        toolUseMode = prepared.workload.inference.toolUseMode.name,
                        workloadKind = prepared.workload.inference.kind.name,
                    )
                )
            }

            trace("PROVIDER_TURN_CONTEXT", listOf(
                "interactionId" to turn.interactionId.value,
                "runId" to durableRunId?.value,
                "round" to 0,
                "turnKind" to currentRequest.turnKind,
                "historyMessages" to currentContext.conversation.size,
                "historyRoles" to currentContext.conversation.joinToString(",") { it.role.name },
                "toolMessages" to currentContext.conversation.count { it.role == Role.TOOL },
                "exposedToolCount" to currentContext.availableTools.size,
                "outgoingUserChars" to if (currentRequest.appendTextAsUserMessage) currentRequest.text.orEmpty().length else 0,
                "continuation" to (currentRequest.turnKind == ProviderTurnKind.TOOL_CONTINUATION),
            ))
            val outcome = if (pendingResponse != null) ProviderTurnResult.Response(AiResponse(
                text = pendingResponse.assistantText, providerId = currentTarget.providerId, modelId = currentTarget.modelId,
                toolCalls = pendingResponse.toolCalls.map { call -> com.halo.sdk.provider.ToolCall(call.toolCallId, call.toolName,
                    ToolArgumentCanonicalizer.parseToArgumentsMap(call.argumentsJson).mapValues { it.value ?: "" }) }), currentTarget)
            else executeProvider(
                target = prepared.target,
                request = currentRequest,
                context = currentContext,
                requestId = turn.requestId,
                interactionId = turn.interactionId,
                workload = prepared.workload.inference,
                runId = durableRunId,
            )

            var initialResponse = when (outcome) {
                is ProviderTurnResult.HandoffStarted -> {
                    return CognitiveResponseCommit(
                        runId = durableRunId,
                        interactionId = turn.interactionId,
                        requestId = turn.requestId,
                        userText = turn.request.text.orEmpty(),
                        response = AiResponse(text = "HANDOFF_STARTED", providerId = outcome.providerId, modelId = outcome.modelId),
                        responseDelivery = turn.responseDelivery,
                        outputTarget = turn.origin?.speechRoute?.let { route ->
                            if (route is SpeechInputRoute.Device) {
                                SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind)
                            } else SpeechOutputTarget.Phone
                        } ?: SpeechOutputTarget.Phone,
                        outputFallback = turn.origin?.speechRoute?.let { route ->
                            if (route is SpeechInputRoute.Device) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE
                        } ?: SpeechOutputFallback.ALLOW_PHONE,
                        sourceCaptureId = turn.sourceCaptureId,
                        metadata = turn.metadata,
                    )
                }
                is ProviderTurnResult.Response -> {
                    currentTarget = outcome.target ?: currentTarget
                    outcome.response
                }
            }

            if (durableRunId != null && pendingResponse == null) {
                agentRunCoordinator?.recordStep(
                    AgentStep.ProviderTurnCompleted(
                        id = newStepId(),
                        runId = durableRunId,
                        createdAt = Instant.now(),
                        turnId = turnId,
                        assistantText = initialResponse.text,
                        toolCalls = initialResponse.toolCalls.map {
                            ProviderToolCallRecord(it.id, it.functionName, ToolArgumentCanonicalizer.canonicalize(com.halo.runtime.cognition.tools.journalToolArguments(it)))
                        },
                        finishReason = initialResponse.finishReason ?: "stop", payloadRefId = initialResponse.payloadRefId
                    )
                )
            }

            if (prepared.workload.inference.toolUseMode == com.halo.sdk.provider.ToolUseMode.NONE && initialResponse.toolCalls.isNotEmpty())
                throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Tools are disabled for this turn")
            if (prepared.workload.inference.toolUseMode == com.halo.sdk.provider.ToolUseMode.REQUIRED &&
                currentRequest.turnKind != ProviderTurnKind.TOOL_CONTINUATION && initialResponse.toolCalls.isEmpty())
                throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "The requested action was not executed")
            if (initialResponse.toolCalls.isEmpty()) {
                if (prepared.workload.inference.toolUseMode == com.halo.sdk.provider.ToolUseMode.REQUIRED && !hasConfirmedAction(durableRunId))
                    initialResponse = initialResponse.copy(text = "I could not confirm completion of the requested action.", payloadRefId = null)
                if (durableRunId != null) {
                    agentRunCoordinator?.recordStep(
                        AgentStep.ResponseProduced(
                            id = newStepId(),
                            runId = durableRunId,
                            createdAt = Instant.now(),
                            responseId = turn.requestId,
                            text = initialResponse.text, payloadRefId = initialResponse.payloadRefId,
                        )
                    )
                }
                return CognitiveResponseCommit(
                    runId = durableRunId,
                    interactionId = turn.interactionId,
                    requestId = turn.requestId,
                    userText = turn.request.text.orEmpty(),
                    response = initialResponse,
                    responseDelivery = turn.responseDelivery,
                    outputTarget = turn.origin?.speechRoute?.let { route ->
                        if (route is SpeechInputRoute.Device) {
                            SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind)
                        } else SpeechOutputTarget.Phone
                    } ?: SpeechOutputTarget.Phone,
                    outputFallback = turn.origin?.speechRoute?.let { route ->
                        if (route is SpeechInputRoute.Device) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE
                    } ?: SpeechOutputFallback.ALLOW_PHONE,
                    sourceCaptureId = turn.sourceCaptureId,
                    metadata = turn.metadata,
                )
            }

            trace("ACTION_ROUTE", listOf(
                "interactionId" to turn.interactionId.value,
                "intent" to "UNCLASSIFIED_OR_COMPOSITE",
                "owner" to "AGENTIC",
                "executor" to "ToolTurnExecutor",
            ))

            // Loop over tool calls if present
            if (currentRequest.appendTextAsUserMessage && !currentRequest.text.isNullOrBlank()) {
                currentContext = currentContext.copy(conversation = currentContext.conversation + AIMessage(Role.USER, currentRequest.text.orEmpty()))
            }
            var response = initialResponse
            val workspaceReadAllowed = turn.policy.includeWorkspace
            var round = 0

            /*
             * AGENTIC-001 exposure carries across rounds. A tool discovered (or a skill
             * loaded) in round N is what round N+1 receives, and the model can never
             * execute a tool that was not exposed to it.
             */
            var exposure: ToolExposureSet = prepared.toolExposure ?: ToolExposureSet()
            var activatedSkills: Set<com.halo.sdk.runtime.skills.SkillId> = emptySet()
            trace(
                "TOOL_EXPOSURE_INITIAL",
                listOf(
                    "interactionId" to turn.interactionId.value,
                    "provider" to prepared.target.providerId,
                    "model" to prepared.target.modelId,
                    "protocol" to prepared.toolProtocol,
                    "tools" to prepared.context.availableTools.joinToString(",") { it.name },
                    "count" to prepared.context.availableTools.size,
                ),
            )

            while (response.toolCalls.isNotEmpty()) {
                round++
                /*
                 * MINING-P1-001: the run-level ceiling is consulted before each round. A guard stop
                 * here ends the run with recovery copy rather than a bare "failure".
                 */
                if (durableRunId != null && runGuards != null) {
                    val guard = runGuards.guardFor(durableRunId, turn.interactionId)
                    val decision = guard.beforeRound()
                    if (decision is RunGuardDecision.Stop) {
                        agentRunCoordinator?.recordStep(guard.toJournalStep(decision))
                        trace(
                            "AGENT_RUN_GUARD",
                            listOf(
                                "interactionId" to turn.interactionId.value,
                                "runId" to durableRunId.value,
                                "round" to round,
                                "reason" to decision.reason.name,
                                "evidence" to decision.evidence.summary,
                            ),
                        )
                        return CognitiveResponseCommit(
                            runId = durableRunId,
                            interactionId = turn.interactionId,
                            requestId = turn.requestId,
                            userText = turn.request.text.orEmpty(),
                            response = AiResponse(
                                text = RunGuardMessages.userMessage(decision),
                                providerId = currentTarget.providerId,
                                modelId = currentTarget.modelId,
                            ),
                            responseDelivery = turn.responseDelivery,
                            outputTarget = turn.origin?.speechRoute?.let { route ->
                                if (route is SpeechInputRoute.Device) {
                                    SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind)
                                } else SpeechOutputTarget.Phone
                            } ?: SpeechOutputTarget.Phone,
                            outputFallback = turn.origin?.speechRoute?.let { route ->
                                if (route is SpeechInputRoute.Device) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE
                            } ?: SpeechOutputFallback.ALLOW_PHONE,
                            sourceCaptureId = turn.sourceCaptureId,
                            metadata = turn.metadata,
                        )
                    }
                }
                if (round > limits.maxToolRounds) {
                    trace(
                        "AGENT_COMPLETED",
                        listOf(
                            "interactionId" to turn.interactionId.value,
                            "rounds" to round,
                            "outcome" to "max_tool_rounds_reached",
                        ),
                    )
                    response = response.copy(
                        text = response.text.ifBlank {
                            "I stopped after ${limits.maxToolRounds} tool steps to avoid going in circles. " +
                                "Here is what I have so far."
                        },
                        toolCalls = emptyList(),
                        finishReason = "tool_round_limit",
                    )
                    break
                }

                val durableRun = createDurableRunIfNeeded()
                val exposedToolIds = currentContext.availableTools.map { it.name }.toSet()
                durableRun?.let { id -> agentRunStore?.get(id)?.let { run ->
                    agentRunStore.update(run.copy(pinnedProviderId = currentTarget.providerId,
                        pinnedModelId = currentTarget.modelId, pinnedTargetLocal = currentTarget.model.executionLocation == com.halo.sdk.provider.AiExecutionLocation.ON_DEVICE))
                } }
                val toolCalls = response.toolCalls.map { call ->
                    if (call.functionName == "halo_set_alarm" &&
                        (call.arguments["timeExpression"] as? String).isNullOrBlank() &&
                        !turn.request.text.isNullOrBlank()
                    ) {
                        // Preserve the user's temporal expression when a model discovered the
                        // alarm schema but omitted the required argument on its next turn.
                        call.copy(arguments = call.arguments + ("timeExpression" to turn.request.text.orEmpty()))
                    } else call
                }
                val toolResult = toolExecutor.execute(
                    runId = durableRun,
                    interactionId = turn.interactionId,
                    requestId = turn.requestId,
                    toolCalls = toolCalls,
                    workspaceReadAllowed = workspaceReadAllowed,
                    exposedToolIds = exposedToolIds,
                )

                when (toolResult) {
                    is ToolTurnResult.WaitingForInput -> {
                        return CognitiveResponseCommit(
                            runId = durableRun,
                            interactionId = turn.interactionId,
                            requestId = turn.requestId,
                            userText = turn.request.text.orEmpty(),
                            response = AiResponse(text = "Waiting for approval", providerId = currentTarget.providerId, modelId = currentTarget.modelId),
                            responseDelivery = turn.responseDelivery,
                            outputTarget = turn.origin?.speechRoute?.let { route ->
                                if (route is SpeechInputRoute.Device) {
                                    SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind)
                                } else SpeechOutputTarget.Phone
                            } ?: SpeechOutputTarget.Phone,
                            outputFallback = turn.origin?.speechRoute?.let { route ->
                                if (route is SpeechInputRoute.Device) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE
                            } ?: SpeechOutputFallback.ALLOW_PHONE,
                            sourceCaptureId = turn.sourceCaptureId,
                            metadata = turn.metadata,
                        )
                    }
                    is ToolTurnResult.WaitingForResource -> {
                        return CognitiveResponseCommit(
                            runId = durableRun,
                            interactionId = turn.interactionId,
                            requestId = turn.requestId,
                            userText = turn.request.text.orEmpty(),
                            response = AiResponse(text = "Resource busy", providerId = currentTarget.providerId, modelId = currentTarget.modelId),
                            responseDelivery = turn.responseDelivery,
                            outputTarget = turn.origin?.speechRoute?.let { route ->
                                if (route is SpeechInputRoute.Device) {
                                    SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind)
                                } else SpeechOutputTarget.Phone
                            } ?: SpeechOutputTarget.Phone,
                            outputFallback = turn.origin?.speechRoute?.let { route ->
                                if (route is SpeechInputRoute.Device) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE
                            } ?: SpeechOutputFallback.ALLOW_PHONE,
                            sourceCaptureId = turn.sourceCaptureId,
                            metadata = turn.metadata,
                        )
                    }
                    is ToolTurnResult.WaitingForCapability -> {
                        return CognitiveResponseCommit(
                            runId = durableRun,
                            interactionId = turn.interactionId,
                            requestId = turn.requestId,
                            userText = turn.request.text.orEmpty(),
                            response = AiResponse(text = "Capability missing", providerId = currentTarget.providerId, modelId = currentTarget.modelId),
                            responseDelivery = turn.responseDelivery,
                            outputTarget = turn.origin?.speechRoute?.let { route ->
                                if (route is SpeechInputRoute.Device) {
                                    SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind)
                                } else SpeechOutputTarget.Phone
                            } ?: SpeechOutputTarget.Phone,
                            outputFallback = turn.origin?.speechRoute?.let { route ->
                                if (route is SpeechInputRoute.Device) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE
                            } ?: SpeechOutputFallback.ALLOW_PHONE,
                            sourceCaptureId = turn.sourceCaptureId,
                            metadata = turn.metadata,
                        )
                    }
                    is ToolTurnResult.RunStopped -> {
                        // The durable RunGuardTriggered step was journaled by the tool executor.
                        trace(
                            "AGENT_COMPLETED",
                            listOf(
                                "interactionId" to turn.interactionId.value,
                                "runId" to durableRun?.value,
                                "rounds" to round,
                                "outcome" to "guard_stop",
                                "reason" to toolResult.reason.name,
                            ),
                        )
                        return CognitiveResponseCommit(
                            runId = durableRun,
                            interactionId = turn.interactionId,
                            requestId = turn.requestId,
                            userText = turn.request.text.orEmpty(),
                            response = AiResponse(
                                text = toolResult.message,
                                providerId = currentTarget.providerId,
                                modelId = currentTarget.modelId,
                            ),
                            responseDelivery = turn.responseDelivery,
                            outputTarget = turn.origin?.speechRoute?.let { route ->
                                if (route is SpeechInputRoute.Device) {
                                    SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind)
                                } else SpeechOutputTarget.Phone
                            } ?: SpeechOutputTarget.Phone,
                            outputFallback = turn.origin?.speechRoute?.let { route ->
                                if (route is SpeechInputRoute.Device) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE
                            } ?: SpeechOutputFallback.ALLOW_PHONE,
                            sourceCaptureId = turn.sourceCaptureId,
                            metadata = turn.metadata,
                        )
                    }

                    is ToolTurnResult.Completed -> {
                        currentContext = continueToolConversation(currentContext, response, toolResult.toolMessages)

                        /*
                         * Re-plan exposure from what the tools actually declared, then
                         * rebuild the provider-visible schemas. Without this step a
                         * successful halo_discover_tools or halo_use_skill would only be
                         * guidance the next request never sees.
                         */
                        val planner = toolExposurePlanner
                        if (planner != null && turn.delegatedInput == null) {
                            val outputs = toolResult.toolMessages.filter { message -> response.toolCalls.any { call ->
                                call.id == message.toolCallId && call.functionName in setOf("halo_discover_tools", "halo_use_skill")
                            } }.map { it.content }
                            val newlyActivated = outputs
                                .flatMap { ToolExposureDirective.parseSkills(it) }
                                .map { com.halo.sdk.runtime.skills.SkillId(it) }
                                .toSet() - activatedSkills
                            if (newlyActivated.isNotEmpty()) {
                                activatedSkills = activatedSkills + newlyActivated
                                trace(
                                    "TOOL_DISCOVERY_RESULT",
                                    listOf(
                                        "interactionId" to turn.interactionId.value,
                                        "skills" to activatedSkills.joinToString(",") { it.value },
                                    ),
                                )
                            }

                            val exposureContext = ToolExposureContext(
                                policy = turn.policy,
                                isLocalTarget = currentTarget.model.executionLocation == com.halo.sdk.provider.AiExecutionLocation.ON_DEVICE,
                                activatedSkillIds = activatedSkills,
                            )
                            var expanded = planner.expandFromToolOutputs(
                                current = exposure,
                                toolOutputs = outputs,
                                gate = toolExposureGate,
                                exposureContext = exposureContext,
                                localPolicy = prepared.localToolPolicy,
                            )
                            val payloadAvailable = durableRun?.let { id -> agentRunStore?.get(id)?.steps
                                ?.filterIsInstance<AgentStep.ToolOutcomeRecorded>()?.any { step ->
                                    step.payloadRefId != null && response.toolCalls.any { it.id == step.toolCallId }
                                } } == true
                            if (payloadAvailable) expanded = planner.expand(expanded, listOf("halo_read_payload"),
                                toolExposureGate, exposureContext, prepared.localToolPolicy)
                            if (expanded != exposure) {
                                exposure = expanded
                                trace(
                                    "TOOL_EXPOSURE_EXPANDED",
                                    listOf(
                                        "interactionId" to turn.interactionId.value,
                                        "tools" to exposure.effective.joinToString(",") { it.value },
                                        "count" to exposure.effective.size,
                                    ),
                                )
                            }

                            val plannedDefinitions = planner.effectiveDefinitions(
                                exposure = exposure,
                                discoverySummary = "",
                                localPolicy = prepared.localToolPolicy,
                            )
                            currentContext = currentContext.copy(availableTools = plannedDefinitions)
                            trace(
                                "PROVIDER_TURN_TOOLS",
                                listOf(
                                    "interactionId" to turn.interactionId.value,
                                    "round" to round,
                                    "protocol" to prepared.toolProtocol,
                                    "tools" to plannedDefinitions.joinToString(",") { it.name },
                                    "count" to plannedDefinitions.size,
                                ),
                            )
                        }

                        val roundTurnId = ProviderTurnId(UUID.randomUUID().toString())
                        val roundRequestId = UUID.randomUUID().toString()
                        eventBus.publish(CognitiveRequestStarted(requestId = roundRequestId, interactionId = turn.interactionId.value))

                        if (durableRun != null) {
                            agentRunCoordinator?.recordStep(
                                AgentStep.ProviderTurnStarted(
                                    id = newStepId(),
                                    runId = durableRun,
                                    createdAt = Instant.now(),
                                    turnId = roundTurnId,
                                    providerId = currentTarget.providerId,
                                    modelId = currentTarget.modelId,
                                    requestIdentity = ToolArgumentCanonicalizer.sha256Hex(
                                        (currentRequest.text ?: "") + ":" + (currentRequest.image?.contentHashCode() ?: 0)
                                    ),
                                    exposedToolIds = currentContext.availableTools.map { it.name }.toSet(),
                                    requiredCapabilities = prepared.requiredCapabilityIds,
                                    contextRequirements = prepared.contextRequirementIds,
                        toolUseMode = prepared.workload.inference.toolUseMode.name,
                        workloadKind = prepared.workload.inference.kind.name,
                                )
                            )
                        }

                        trace("PROVIDER_TURN_CONTEXT", listOf(
                            "interactionId" to turn.interactionId.value,
                            "runId" to durableRun?.value,
                            "round" to round,
                            "turnKind" to ProviderTurnKind.TOOL_CONTINUATION,
                            "historyMessages" to currentContext.conversation.size,
                            "historyRoles" to currentContext.conversation.joinToString(",") { it.role.name },
                            "toolMessages" to currentContext.conversation.count { it.role == Role.TOOL },
                            "exposedToolCount" to currentContext.availableTools.size,
                            "outgoingUserChars" to 0,
                            "continuation" to true,
                        ))
                        val roundOutcome = executeProvider(
                            target = currentTarget,
                            request = currentRequest.copy(
                                modelId = currentTarget.modelId,
                                appendTextAsUserMessage = false,
                                turnKind = ProviderTurnKind.TOOL_CONTINUATION,
                                publishStreamingTokens = prepared.turn.request.publishStreamingTokens && prepared.workload.inference.toolUseMode != com.halo.sdk.provider.ToolUseMode.REQUIRED,
                            ),
                            context = currentContext,
                            requestId = roundRequestId,
                            interactionId = turn.interactionId,
                            workload = prepared.workload.inference,
                            runId = durableRun,
                        )

                        response = when (roundOutcome) {
                            is ProviderTurnResult.Response -> {
                                currentTarget = roundOutcome.target ?: currentTarget
                                roundOutcome.response
                            }
                            is ProviderTurnResult.HandoffStarted -> throw AiProviderException(
                                AiErrorType.INVALID_RESPONSE,
                                "Handoff started during tool loop"
                            )
                        }

                        if (durableRun != null) {
                            agentRunCoordinator?.recordStep(
                                AgentStep.ProviderTurnCompleted(
                                    id = newStepId(),
                                    runId = durableRun,
                                    createdAt = Instant.now(),
                                    turnId = roundTurnId,
                                    assistantText = response.text,
                                    toolCalls = response.toolCalls.map {
                                        ProviderToolCallRecord(it.id, it.functionName, ToolArgumentCanonicalizer.canonicalize(com.halo.runtime.cognition.tools.journalToolArguments(it)))
                                    },
                                    finishReason = response.finishReason ?: "stop", payloadRefId = response.payloadRefId
                                )
                            )
                        }
                    }
                }
            }

            val durableRun = createDurableRunIfNeeded()
            if (prepared.workload.inference.toolUseMode == com.halo.sdk.provider.ToolUseMode.REQUIRED) {
                val confirmed = hasConfirmedAction(durableRun)
                if (!confirmed) response = response.copy(text = "I could not confirm completion of the requested action.", payloadRefId = null)
            }
            if (durableRun != null) {
                agentRunStore?.get(durableRun)?.let { run ->
                    com.halo.runtime.cognition.ui.UiWorkflowProjector.summary(run)?.let { summary ->
                        response = response.copy(text = summary, payloadRefId = null)
                    }
                }
                agentRunCoordinator?.recordStep(
                    AgentStep.ResponseProduced(
                        id = newStepId(),
                        runId = durableRun,
                        createdAt = Instant.now(),
                        responseId = turn.requestId,
                        text = response.text, payloadRefId = response.payloadRefId,
                    )
                )
            }

            return CognitiveResponseCommit(
                runId = durableRun,
                interactionId = turn.interactionId,
                requestId = turn.requestId,
                userText = turn.request.text.orEmpty(),
                response = response,
                responseDelivery = turn.responseDelivery,
                outputTarget = turn.origin?.speechRoute?.let { route ->
                    if (route is SpeechInputRoute.Device) {
                        SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind)
                    } else SpeechOutputTarget.Phone
                } ?: SpeechOutputTarget.Phone,
                outputFallback = turn.origin?.speechRoute?.let { route ->
                    if (route is SpeechInputRoute.Device) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE
                } ?: SpeechOutputFallback.ALLOW_PHONE,
                sourceCaptureId = turn.sourceCaptureId,
                metadata = turn.metadata,
            )
        } else {
            // CONTINUE_AFTER_TOOL
            val durableRun = activeRunId ?: throw IllegalStateException("RunId must exist for CONTINUE_AFTER_TOOL")
            var response = AiResponse(text = "", toolCalls = emptyList(), providerId = prepared.target.providerId, modelId = prepared.target.modelId)
            
            // Re-run provider with updated context
            val roundTurnId = ProviderTurnId(UUID.randomUUID().toString())
            val roundRequestId = UUID.randomUUID().toString()
            eventBus.publish(CognitiveRequestStarted(requestId = roundRequestId, interactionId = turn.interactionId.value))

            agentRunCoordinator?.recordStep(
                AgentStep.ProviderTurnStarted(
                    id = newStepId(),
                    runId = durableRun,
                    createdAt = Instant.now(),
                    turnId = roundTurnId,
                    providerId = prepared.target.providerId,
                    modelId = prepared.target.modelId,
                    requestIdentity = ToolArgumentCanonicalizer.sha256Hex(
                        (currentRequest.text ?: "") + ":" + (currentRequest.image?.contentHashCode() ?: 0)
                    ),
                    exposedToolIds = currentContext.availableTools.map { it.name }.toSet(),
                    requiredCapabilities = prepared.requiredCapabilityIds,
                    contextRequirements = prepared.contextRequirementIds,
                        toolUseMode = prepared.workload.inference.toolUseMode.name,
                        workloadKind = prepared.workload.inference.kind.name,
                )
            )

            val roundOutcome = executeProvider(
                target = currentTarget,
                request = currentRequest.copy(
                    modelId = currentTarget.modelId,
                    appendTextAsUserMessage = false,
                    turnKind = ProviderTurnKind.TOOL_CONTINUATION,
                ),
                context = currentContext,
                requestId = roundRequestId,
                interactionId = turn.interactionId,
                workload = prepared.workload.inference,
                runId = durableRun,
            )

            response = when (roundOutcome) {
                is ProviderTurnResult.Response -> {
                    currentTarget = roundOutcome.target ?: currentTarget
                    roundOutcome.response
                }
                is ProviderTurnResult.HandoffStarted -> throw AiProviderException(
                    AiErrorType.INVALID_RESPONSE,
                    "Handoff started during continuation"
                )
            }

            agentRunCoordinator?.recordStep(
                AgentStep.ProviderTurnCompleted(
                    id = newStepId(),
                    runId = durableRun,
                    createdAt = Instant.now(),
                    turnId = roundTurnId,
                    assistantText = response.text,
                    toolCalls = response.toolCalls.map {
                        ProviderToolCallRecord(it.id, it.functionName, ToolArgumentCanonicalizer.canonicalize(com.halo.runtime.cognition.tools.journalToolArguments(it)))
                    },
                    finishReason = response.finishReason ?: "stop", payloadRefId = response.payloadRefId
                )
            )

            agentRunStore?.get(durableRun)?.let { run ->
                com.halo.runtime.cognition.ui.UiWorkflowProjector.summary(run)?.let { summary ->
                    response = response.copy(text = summary, payloadRefId = null)
                }
            }
            agentRunCoordinator?.recordStep(
                AgentStep.ResponseProduced(
                    id = newStepId(),
                    runId = durableRun,
                    createdAt = Instant.now(),
                    responseId = turn.requestId,
                    text = response.text, payloadRefId = response.payloadRefId,
                )
            )

            return CognitiveResponseCommit(
                runId = durableRun,
                interactionId = turn.interactionId,
                requestId = turn.requestId,
                userText = turn.request.text.orEmpty(),
                response = response,
                responseDelivery = turn.responseDelivery,
                outputTarget = turn.origin?.speechRoute?.let { route ->
                    if (route is SpeechInputRoute.Device) {
                        SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind)
                    } else SpeechOutputTarget.Phone
                } ?: SpeechOutputTarget.Phone,
                outputFallback = turn.origin?.speechRoute?.let { route ->
                    if (route is SpeechInputRoute.Device) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE
                } ?: SpeechOutputFallback.ALLOW_PHONE,
                sourceCaptureId = turn.sourceCaptureId,
                metadata = turn.metadata,
            )
        }
    }

    private suspend fun hasConfirmedAction(runId: AgentRunId?): Boolean {
        val technical = setOf("halo_discover_tools", "halo_use_skill", "halo_present", "halo_read_payload", "halo_delegate", "halo_think")
        return runId?.let { agentRunStore?.get(it)?.steps }?.any { step -> when(step) {
            is AgentStep.ToolOutcomeRecorded -> step.success && step.actionTruth == com.halo.runtime.cognition.agent.ActionTruth.CONFIRMED && step.toolId !in technical
            is AgentStep.ToolCompleted -> step.success && step.actionTruth == com.halo.runtime.cognition.agent.ActionTruth.CONFIRMED && step.toolId !in technical
            else -> false
        } } == true
    }
}

internal fun continueToolConversation(
    context: AiContext,
    response: AiResponse,
    toolMessages: List<AIMessage>,
): AiContext = context.copy(
    conversation = buildList {
        addAll(context.conversation)
        if (response.text.isNotBlank() || response.toolCalls.isNotEmpty()) {
            add(AIMessage(Role.ASSISTANT, response.text, toolCalls = response.toolCalls))
        }
        addAll(toolMessages)
    },
)
