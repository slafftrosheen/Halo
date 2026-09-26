package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentInputRequest
import com.halo.runtime.cognition.agent.AgentInputResolutions
import com.halo.runtime.cognition.agent.AgentRunCoordinator
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.AgentStepId
import com.halo.runtime.cognition.agent.ToolArgumentCanonicalizer
import com.halo.runtime.cognition.agent.ToolInvocationId
import com.halo.runtime.cognition.agent.ToolRejectionCode
import com.halo.runtime.cognition.tools.AuthorizationDecision
import com.halo.runtime.cognition.tools.PolicyToolAuthorizer
import com.halo.runtime.cognition.tools.ToolAuthorizer
import com.halo.runtime.cognition.tools.ToolAuthorization
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolFailureKind
import com.halo.runtime.cognition.tools.ToolId
import com.halo.runtime.cognition.agent.ToolInvocation
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.runtime.cognition.tools.ToolResourceAcquisition
import com.halo.runtime.cognition.tools.ToolResourceCoordinator
import com.halo.runtime.cognition.tools.ReplaySafety
import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.ToolCall
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ToolCallAuthorized
import com.halo.sdk.runtime.ToolCallCompleted
import com.halo.sdk.runtime.ToolCallRejected
import com.halo.sdk.runtime.ToolCallRequested
import com.halo.sdk.runtime.resource.ResourceLease
import com.halo.sdk.runtime.resource.ResourceAcquireResult
import com.halo.sdk.runtime.resource.ResourceHolder
import com.halo.sdk.runtime.resource.ResourceLeaseManager
import java.time.Instant
import java.util.UUID

sealed interface ToolTurnResult {
    data class Completed(
        val response: AiResponse,
        val hasUnconfirmedAction: Boolean,
        val toolMessages: List<AIMessage>,
    ) : ToolTurnResult

    data class WaitingForInput(
        val request: AgentInputRequest,
    ) : ToolTurnResult

    data class WaitingForResource(
        val runId: AgentRunId,
    ) : ToolTurnResult

    data class WaitingForCapability(
        val runId: AgentRunId,
    ) : ToolTurnResult
}

interface ToolTurnExecutor {
    suspend fun execute(
        runId: AgentRunId?,
        interactionId: InteractionId,
        requestId: String,
        toolCalls: List<ToolCall>,
        workspaceReadAllowed: Boolean,
    ): ToolTurnResult
}

enum class DurableApprovalEvidence { APPROVED, DENIED, CANCELLED, EXPIRED }

class DefaultToolTurnExecutor(
    private val toolRegistry: ToolRegistry?,
    private val toolAuthorizer: ToolAuthorizer?,
    private val toolResourceCoordinator: ToolResourceCoordinator?,
    private val agentRunCoordinator: AgentRunCoordinator?,
    private val agentRunStore: AgentRunStore?,
    private val eventBus: EventBus,
) : ToolTurnExecutor {
    private fun newStepId() = AgentStepId(UUID.randomUUID().toString())

    private suspend fun resolvedApprovalEvidence(
        runId: AgentRunId,
        toolId: String,
        argumentsDigest: String,
    ): DurableApprovalEvidence? {
        val run = agentRunStore?.get(runId) ?: return null
        var evidence: DurableApprovalEvidence? = null
        for (step in run.steps) {
            when (step) {
                is AgentStep.InputRequested -> {
                    val req = step.input as? AgentInputRequest.Approval ?: continue
                    if (req.toolId.value == toolId && req.argumentsDigest == argumentsDigest) {
                        evidence = null // Pending
                    }
                }
                is AgentStep.InputResolved -> {
                    val reqId = step.requestId
                    val reqStep = run.steps.filterIsInstance<AgentStep.InputRequested>().find { it.requestId == reqId }
                    val req = reqStep?.input as? AgentInputRequest.Approval
                    if (req != null && req.toolId.value == toolId && req.argumentsDigest == argumentsDigest) {
                        evidence = when (step.resolution) {
                            "Approved" -> DurableApprovalEvidence.APPROVED
                            "Denied" -> DurableApprovalEvidence.DENIED
                            "Cancelled" -> DurableApprovalEvidence.CANCELLED
                            AgentInputResolutions.EXPIRED -> DurableApprovalEvidence.EXPIRED
                            else -> null
                        }
                    }
                }
                else -> Unit
            }
        }
        return evidence
    }

    private suspend fun persistAndPublishToolRejection(
        runId: AgentRunId?,
        interactionId: InteractionId,
        requestId: String,
        toolCall: ToolCall,
        rejection: RuntimeToolRejection
    ): AIMessage {
        runId?.let {
            agentRunCoordinator?.recordStep(
                AgentStep.ToolRejected(
                    newStepId(), it, Instant.now(),
                    toolCall.id, toolCall.functionName,
                    rejection.reasonCode, rejection.humanReason,
                    rejection.retryable, rejection.requiresUserAction,
                    rejection.modelResult
                )
            )
        }
        println("[HALO-DIAGNOSTIC] event=TOOL_CALL_REJECTED run=${runId?.value ?: "ephemeral"} interaction=${interactionId.value} tool=${toolCall.functionName} reason=\"${rejection.humanReason}\"")
        eventBus.publish(
            ToolCallRejected(
                requestId = requestId,
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                reason = rejection.humanReason,
            )
        )
        return AIMessage(
            Role.TOOL,
            rejection.modelResult,
            toolCallId = toolCall.id
        )
    }

    private suspend fun durableToolResult(runId: AgentRunId, toolCallId: String): AgentStep? {
        val run = agentRunStore?.get(runId) ?: return null
        return run.steps.asReversed().firstOrNull {
            (it is AgentStep.ToolOutcomeRecorded && it.toolCallId == toolCallId) ||
            (it is AgentStep.ToolFailed && it.toolCallId == toolCallId) ||
            (it is AgentStep.ToolRejected && it.toolCallId == toolCallId) ||
            (it is AgentStep.ToolCompleted && it.toolCallId == toolCallId)
        }
    }

    override suspend fun execute(
        runId: AgentRunId?,
        interactionId: InteractionId,
        requestId: String,
        toolCalls: List<ToolCall>,
        workspaceReadAllowed: Boolean,
    ): ToolTurnResult {
        val toolMessages = mutableListOf<AIMessage>()
        var hasUnconfirmedAction = false

        for (toolCall in toolCalls) {
            val toolIsEnabled = toolRegistry?.descriptor(toolCall.functionName) != null

            if (runId != null) {
                val existing = durableToolResult(runId, toolCall.id)
                if (existing != null) {
                    val resultText = when (existing) {
                        is AgentStep.ToolOutcomeRecorded -> existing.output
                        is AgentStep.ToolRejected -> existing.modelResult
                        is AgentStep.ToolCompleted -> existing.resultSummary
                        is AgentStep.ToolFailed -> "tool_result: status=failed reason_code=PERMANENT retryable=false\nreason: " + existing.reason.replace('\n', ' ').take(512)
                        else -> "tool_result: status=failed reason_code=UNKNOWN retryable=false"
                    }
                    toolMessages.add(AIMessage(Role.TOOL, resultText, toolCallId = toolCall.id))
                    continue
                }
            }

            val invalidArguments = toolRegistry?.validate(toolCall)
            if (toolRegistry == null || toolAuthorizer == null || !toolIsEnabled || invalidArguments != null || !workspaceReadAllowed) {
                val rejection = when {
                    toolRegistry == null -> ToolRejectionFactory.create(ToolRejectionCode.TOOL_DISABLED, "Tool registry unavailable")
                    !workspaceReadAllowed -> ToolRejectionFactory.create(ToolRejectionCode.PRIVATE_CONTEXT_BLOCKED, "Private workspace access is disabled for this provider")
                    !toolIsEnabled -> ToolRejectionFactory.create(ToolRejectionCode.TOOL_UNKNOWN, "Skill unavailable or disabled")
                    invalidArguments != null -> ToolRejectionFactory.create(ToolRejectionCode.INVALID_ARGUMENTS, invalidArguments.reason)
                    else -> ToolRejectionFactory.create(ToolRejectionCode.AUTHORIZATION_DENIED, "Tool authorization service unavailable")
                }
                toolMessages.add(persistAndPublishToolRejection(runId, interactionId, requestId, toolCall, rejection))
                continue
            }

            val canonicalArguments = ToolArgumentCanonicalizer.canonicalize(toolCall.arguments)
            val argumentsDigest = ToolArgumentCanonicalizer.digest(canonicalArguments)
            val invocationId = ToolInvocationId(UUID.randomUUID().toString())
            val idempotencyKey = ToolInvocation.idempotencyKey(
                runId ?: AgentRunId("ephemeral"), toolCall.id, ToolId(toolCall.functionName), argumentsDigest
            )
            val replaySafety = toolRegistry.descriptor(toolCall.functionName)?.security?.replaySafety ?: ReplaySafety.NEVER_REPLAY
            val descriptorVersion = 1

            if (runId != null) {
                val run = agentRunStore?.get(runId)
                val claim = run?.steps?.filterIsInstance<AgentStep.ToolExecutionClaimed>()?.find { it.toolCallId == toolCall.id }
                if (claim != null) {
                    hasUnconfirmedAction = true
                    val rejection = ToolRejectionFactory.create(ToolRejectionCode.TOOL_DISABLED, "Previous tool execution was claimed but never completed. It will not be re-executed.", false, true)
                    toolMessages.add(persistAndPublishToolRejection(runId, interactionId, requestId, toolCall, rejection))
                    continue
                }
            }

            if (runId != null) {
                val prepared = agentRunStore?.get(runId)?.steps?.filterIsInstance<AgentStep.ToolInvocationPrepared>()?.find { it.toolCallId == toolCall.id }
                if (prepared == null) {
                    agentRunCoordinator?.recordStep(
                        AgentStep.ToolInvocationPrepared(
                            newStepId(), runId, Instant.now(), invocationId, toolCall.id, toolCall.functionName,
                            canonicalArguments, argumentsDigest, idempotencyKey, descriptorVersion,
                            replaySafety.name,
                        )
                    )
                }
            }

            eventBus.publish(
                ToolCallRequested(
                    requestId = requestId,
                    toolCallId = toolCall.id,
                    toolId = toolCall.functionName
                )
            )

            val approvalEvidence = runId?.let { resolvedApprovalEvidence(it, toolCall.functionName, argumentsDigest) }
            if (approvalEvidence != null && approvalEvidence != DurableApprovalEvidence.APPROVED) {
                val rejection = when (approvalEvidence) {
                    DurableApprovalEvidence.DENIED -> ToolRejectionFactory.create(ToolRejectionCode.USER_REJECTED, "Denied by user")
                    DurableApprovalEvidence.CANCELLED -> ToolRejectionFactory.create(ToolRejectionCode.INTERACTION_CANCELLED, "Cancelled by user")
                    DurableApprovalEvidence.EXPIRED -> ToolRejectionFactory.create(ToolRejectionCode.USER_REJECTED, "Approval request expired", false, true)
                    DurableApprovalEvidence.APPROVED -> error("handled above")
                }
                toolMessages.add(persistAndPublishToolRejection(runId, interactionId, requestId, toolCall, rejection))
                continue
            }

            val previouslyApproved = approvalEvidence == DurableApprovalEvidence.APPROVED
            val durableDecision = if (runId != null && !previouslyApproved) {
                (toolAuthorizer as? PolicyToolAuthorizer)?.authorizeDurable(
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
                durableDecision is AuthorizationDecision.Authorized ->
                    ToolAuthorization.Authorized(toolCall)
                durableDecision is AuthorizationDecision.Rejected ->
                    ToolAuthorization.Rejected(toolCall, durableDecision.reason)
                durableDecision is AuthorizationDecision.NeedsUserInput -> {
                    val request = durableDecision.request
                    val waitingRunId = runId ?: return ToolTurnResult.WaitingForInput(request) // Can't happen if runId is null as durableDecision would be null
                    agentRunCoordinator?.recordStep(
                        AgentStep.InputRequested(newStepId(), waitingRunId, Instant.now(), request.id, request)
                    )
                    agentRunCoordinator?.transition(waitingRunId, AgentRunState.WAITING_FOR_APPROVAL)
                    eventBus.publish(
                        ToolCallRejected(
                            requestId = requestId,
                            toolCallId = toolCall.id,
                            toolId = toolCall.functionName,
                            reason = "Waiting for your approval",
                        )
                    )
                    return ToolTurnResult.WaitingForInput(request)
                }
                else -> toolAuthorizer.authorize(toolCall, interactionId.value)
            }

            when (authorization) {
                is ToolAuthorization.Rejected -> {
                    val rejection = ToolRejectionFactory.create(ToolRejectionCode.AUTHORIZATION_DENIED, authorization.reason)
                    toolMessages.add(persistAndPublishToolRejection(runId, interactionId, requestId, toolCall, rejection))
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

            if (toolResourceCoordinator != null) {
                val descriptor = toolRegistry.descriptor(toolCall.functionName)!!
                val holder = ResourceHolder(interactionId.value, interactionId.value)
                when (val acquisition = toolResourceCoordinator.prepareAndAcquire(descriptor, holder)) {
                    is ToolResourceAcquisition.Granted -> { /* Acquired */ }
                    is ToolResourceAcquisition.Blocked -> {
                        if (runId != null) {
                            agentRunCoordinator?.recordStep(
                                AgentStep.ResourceBlocked(
                                    id = newStepId(),
                                    runId = runId,
                                    createdAt = Instant.now(),
                                    invocationId = invocationId,
                                    toolId = toolCall.functionName,
                                    resourceIds = acquisition.plan.resourcePlan.claims.map { it.key.resourceId.value },
                                    endpointIds = acquisition.plan.resourcePlan.claims.map { it.key.endpointId.value },
                                    blockingLeaseIds = (acquisition.acquireResult as? ResourceAcquireResult.Busy)?.conflicts?.map { it.heldLeaseId.value }.orEmpty(),
                                )
                            )
                            agentRunCoordinator?.transition(
                                runId,
                                AgentRunState.WAITING_FOR_RESOURCE,
                                "Required resource is currently busy",
                            )
                            return ToolTurnResult.WaitingForResource(runId)
                        }
                        val rejection = ToolRejectionFactory.create(ToolRejectionCode.RESOURCE_UNAVAILABLE, "Required resource is currently busy", true)
                        toolMessages.add(persistAndPublishToolRejection(runId, interactionId, requestId, toolCall, rejection))
                        continue
                    }
                    is ToolResourceAcquisition.CapabilityMissing -> {
                        if (runId != null) {
                            agentRunCoordinator?.transition(
                                runId,
                                AgentRunState.WAITING_FOR_CAPABILITY,
                                "Required device capability is unavailable",
                            )
                            return ToolTurnResult.WaitingForCapability(runId)
                        }
                        val rejection = ToolRejectionFactory.create(ToolRejectionCode.CAPABILITY_UNAVAILABLE, "Required device capability is unavailable")
                        toolMessages.add(persistAndPublishToolRejection(runId, interactionId, requestId, toolCall, rejection))
                        continue
                    }
                }
            }

            val result = try {
                if (runId != null) {
                    val claimed = agentRunCoordinator?.recordStep(
                        AgentStep.ToolExecutionClaimed(
                            newStepId(), runId, Instant.now(), invocationId, toolCall.id, toolCall.functionName,
                            idempotencyKey, replaySafety.name
                        )
                    )
                    if (claimed != true) {
                        ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Action already claimed; refusing replay")
                    } else {
                        toolRegistry.executeAuthorized(authorization as ToolAuthorization.Authorized)
                    }
                } else {
                    toolRegistry.executeAuthorized(authorization as ToolAuthorization.Authorized)
                }
            } finally {
                // Leases are usually released by the specific acquire logic or scoped.
                // Assuming toolResourceCoordinator provides a release method if needed, or we ignore it if it is released internally.
                // For now we do not release here since ResourceLease interface doesn't have it and Manager is not injected.
            }

            when (result) {
                is ToolExecutionResult.Success -> {
                    runId?.let {
                        agentRunCoordinator?.recordStep(
                            AgentStep.ToolOutcomeRecorded(newStepId(), it, Instant.now(), invocationId,
                                toolCall.id, toolCall.functionName, true, result.output,
                                ActionTruth.AUTHORIZED, null))
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
                            result.output,
                            toolCallId = toolCall.id
                        )
                    )
                }

                is ToolExecutionResult.Rejected -> {
                    val rejection = ToolRejectionFactory.create(ToolRejectionCode.INVALID_ARGUMENTS, result.reason)
                    toolMessages.add(persistAndPublishToolRejection(runId, interactionId, requestId, toolCall, rejection))
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

        return ToolTurnResult.Completed(
            response = AiResponse(text = "", toolCalls = emptyList(), providerId = "", modelId = ""),
            hasUnconfirmedAction = hasUnconfirmedAction,
            toolMessages = toolMessages,
        )
    }
}
