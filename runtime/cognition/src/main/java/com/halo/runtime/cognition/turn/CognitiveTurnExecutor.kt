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
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.Role
import com.halo.sdk.runtime.CognitiveRequestStarted
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import java.time.Instant
import java.util.UUID

class CognitiveTurnExecutor(
    private val preparer: CognitiveTurnPreparer,
    private val providerExecutor: ProviderTurnExecutor,
    private val toolExecutor: ToolTurnExecutor,
    private val agentRunCoordinator: AgentRunCoordinator?,
    private val agentRunStore: AgentRunStore?,
    private val eventBus: EventBus,
) {
    private fun newStepId() = AgentStepId(UUID.randomUUID().toString())

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

        if (turn.mode == CognitiveTurnMode.FRESH || turn.mode == CognitiveTurnMode.RECOVER_PROVIDER) {
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

            val outcome = providerExecutor.execute(
                target = prepared.target,
                request = currentRequest,
                context = currentContext,
                requestId = turn.requestId,
                interactionId = turn.interactionId,
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
                        outputTarget = SpeechOutputTarget.Phone,
                        outputFallback = SpeechOutputFallback.ALLOW_PHONE,
                        sourceCaptureId = turn.sourceCaptureId,
                        metadata = turn.metadata,
                    )
                }
                is ProviderTurnResult.Response -> outcome.response
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
                    outputTarget = SpeechOutputTarget.Phone,
                    outputFallback = SpeechOutputFallback.ALLOW_PHONE,
                    sourceCaptureId = turn.sourceCaptureId,
                    metadata = turn.metadata,
                )
            }

            // Loop over tool calls if present
            var response = initialResponse
            val workspaceReadAllowed = turn.policy.includeWorkspace
            var round = 0

            while (response.toolCalls.isNotEmpty()) {
                round++
                val durableRun = createDurableRunIfNeeded()
                val toolResult = toolExecutor.execute(
                    runId = durableRun,
                    interactionId = turn.interactionId,
                    requestId = turn.requestId,
                    toolCalls = response.toolCalls,
                    workspaceReadAllowed = workspaceReadAllowed,
                )

                when (toolResult) {
                    is ToolTurnResult.WaitingForInput -> {
                        return CognitiveResponseCommit(
                            runId = durableRun,
                            interactionId = turn.interactionId,
                            requestId = turn.requestId,
                            userText = turn.request.text.orEmpty(),
                            response = AiResponse(text = "Waiting for approval", providerId = prepared.target.providerId, modelId = prepared.target.modelId),
                            responseDelivery = turn.responseDelivery,
                            outputTarget = SpeechOutputTarget.Phone,
                            outputFallback = SpeechOutputFallback.ALLOW_PHONE,
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
                            response = AiResponse(text = "Resource busy", providerId = prepared.target.providerId, modelId = prepared.target.modelId),
                            responseDelivery = turn.responseDelivery,
                            outputTarget = SpeechOutputTarget.Phone,
                            outputFallback = SpeechOutputFallback.ALLOW_PHONE,
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
                            response = AiResponse(text = "Capability missing", providerId = prepared.target.providerId, modelId = prepared.target.modelId),
                            responseDelivery = turn.responseDelivery,
                            outputTarget = SpeechOutputTarget.Phone,
                            outputFallback = SpeechOutputFallback.ALLOW_PHONE,
                            sourceCaptureId = turn.sourceCaptureId,
                            metadata = turn.metadata,
                        )
                    }
                    is ToolTurnResult.Completed -> {
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
                                addAll(toolResult.toolMessages)
                            }
                        )

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

                        val roundOutcome = providerExecutor.execute(
                            target = prepared.target,
                            request = currentRequest.copy(modelId = prepared.target.modelId),
                            context = currentContext,
                            requestId = roundRequestId,
                            interactionId = turn.interactionId,
                        )

                        response = when (roundOutcome) {
                            is ProviderTurnResult.Response -> roundOutcome.response
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
                outputTarget = SpeechOutputTarget.Phone,
                outputFallback = SpeechOutputFallback.ALLOW_PHONE,
                sourceCaptureId = turn.sourceCaptureId,
                metadata = turn.metadata,
            )
        } else {
            // CONTINUE_AFTER_TOOL
            val durableRun = activeRunId ?: throw IllegalStateException("RunId must exist for CONTINUE_AFTER_TOOL")
            val toolResult = toolExecutor.execute(
                runId = durableRun,
                interactionId = turn.interactionId,
                requestId = turn.requestId,
                toolCalls = emptyList(), // Durable results are read inside toolExecutor
                workspaceReadAllowed = turn.policy.includeWorkspace,
            )

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
                target = prepared.target,
                request = currentRequest.copy(modelId = prepared.target.modelId),
                context = currentContext,
                requestId = roundRequestId,
                interactionId = turn.interactionId,
            )

            response = when (roundOutcome) {
                is ProviderTurnResult.Response -> roundOutcome.response
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
                outputTarget = SpeechOutputTarget.Phone,
                outputFallback = SpeechOutputFallback.ALLOW_PHONE,
                sourceCaptureId = turn.sourceCaptureId,
                metadata = turn.metadata,
            )
        }
    }
}
