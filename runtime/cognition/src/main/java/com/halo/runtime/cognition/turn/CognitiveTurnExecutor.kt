package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunCoordinator
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentRunKind
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.AgentStepId
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
) {
    private fun newStepId() = AgentStepId(UUID.randomUUID().toString())

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
            if (durableRunId != null) {
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
            val outcome = providerExecutor.execute(
                target = prepared.target,
                request = currentRequest,
                context = currentContext,
                requestId = turn.requestId,
                interactionId = turn.interactionId,
                workload = prepared.workload.inference,
            )

            val initialResponse = when (outcome) {
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

            if (durableRunId != null) {
                agentRunCoordinator?.recordStep(
                    AgentStep.ProviderTurnCompleted(
                        id = newStepId(),
                        runId = durableRunId,
                        createdAt = Instant.now(),
                        turnId = turnId,
                        assistantText = initialResponse.text,
                        toolCalls = initialResponse.toolCalls.map {
                            ProviderToolCallRecord(it.id, it.functionName, ToolArgumentCanonicalizer.canonicalize(it.arguments))
                        },
                        finishReason = initialResponse.finishReason ?: "stop"
                    )
                )
            }

            if (initialResponse.toolCalls.isEmpty()) {
                if (durableRunId != null) {
                    agentRunCoordinator?.recordStep(
                        AgentStep.ResponseProduced(
                            id = newStepId(),
                            runId = durableRunId,
                            createdAt = Instant.now(),
                            responseId = turn.requestId,
                            text = initialResponse.text,
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
                    is ToolTurnResult.Completed -> {
                        currentContext = continueToolConversation(currentContext, response, toolResult.toolMessages)

                        /*
                         * Re-plan exposure from what the tools actually declared, then
                         * rebuild the provider-visible schemas. Without this step a
                         * successful halo_discover_tools or halo_use_skill would only be
                         * guidance the next request never sees.
                         */
                        val planner = toolExposurePlanner
                        if (planner != null) {
                            val outputs = toolResult.toolMessages.map { it.content }
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
                                isLocalTarget = prepared.target.provider.isLocal,
                                activatedSkillIds = activatedSkills,
                            )
                            val expanded = planner.expandFromToolOutputs(
                                current = exposure,
                                toolOutputs = outputs,
                                gate = toolExposureGate,
                                exposureContext = exposureContext,
                                localPolicy = prepared.localToolPolicy,
                            )
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
                        val roundOutcome = providerExecutor.execute(
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
                                        ProviderToolCallRecord(it.id, it.functionName, ToolArgumentCanonicalizer.canonicalize(it.arguments))
                                    },
                                    finishReason = response.finishReason ?: "stop"
                                )
                            )
                        }
                    }
                }
            }

            val durableRun = createDurableRunIfNeeded()
            if (durableRun != null) {
                agentRunCoordinator?.recordStep(
                    AgentStep.ResponseProduced(
                        id = newStepId(),
                        runId = durableRun,
                        createdAt = Instant.now(),
                        responseId = turn.requestId,
                        text = response.text,
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
                )
            )

            val roundOutcome = providerExecutor.execute(
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
                        ProviderToolCallRecord(it.id, it.functionName, ToolArgumentCanonicalizer.canonicalize(it.arguments))
                    },
                    finishReason = response.finishReason ?: "stop"
                )
            )

            agentRunCoordinator?.recordStep(
                AgentStep.ResponseProduced(
                    id = newStepId(),
                    runId = durableRun,
                    createdAt = Instant.now(),
                    responseId = turn.requestId,
                    text = response.text,
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
