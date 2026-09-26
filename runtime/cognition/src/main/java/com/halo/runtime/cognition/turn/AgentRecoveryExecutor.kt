package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunCoordinator
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.AgentStepId
import com.halo.runtime.cognition.agent.RunRecoveryPolicy
import com.halo.runtime.cognition.agent.recovery.AgentCheckpointAssembler
import com.halo.runtime.cognition.agent.recovery.AgentRecoveryPlanner
import com.halo.runtime.cognition.agent.recovery.AgentRecoveryStarted
import com.halo.runtime.cognition.agent.recovery.CapabilitySnapshot
import com.halo.runtime.cognition.agent.recovery.RecoveryPlan
import com.halo.runtime.cognition.agent.ToolInvocation
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.InferencePriority
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ResponseDelivery
import java.time.Instant
import java.util.UUID

sealed interface RecoveryExecutionResult {
    data object Noop : RecoveryExecutionResult
    data class StartTurn(val request: CognitiveTurnRequest) : RecoveryExecutionResult
    data class CompleteResponse(val runId: AgentRunId, val interactionId: InteractionId, val responseText: String, val responseId: String) : RecoveryExecutionResult
    data class RetryTool(val runId: AgentRunId, val invocation: ToolInvocation, val useOriginalIdempotencyKey: Boolean) : RecoveryExecutionResult
    data class ReconcileTool(val runId: AgentRunId, val invocation: ToolInvocation) : RecoveryExecutionResult
    data class Blocked(val reason: String) : RecoveryExecutionResult
}

class DefaultAgentRecoveryExecutor(
    private val agentRunStore: AgentRunStore?,
    private val agentRunCoordinator: AgentRunCoordinator?,
    private val toolRegistry: ToolRegistry?,
    private val eventBus: EventBus,
    private val recoveryPlanner: AgentRecoveryPlanner,
) {
    suspend fun executeRecovery(runId: AgentRunId): RecoveryExecutionResult {
        if (agentRunStore == null || agentRunCoordinator == null) return RecoveryExecutionResult.Noop
        val run = agentRunStore.get(runId) ?: return RecoveryExecutionResult.Noop

        val plan = recoveryPlanner.plan(
            run = run,
            catalog = toolRegistry,
            capabilities = CapabilitySnapshot(),
        )

        val runPrepared = run.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()
        if (runPrepared?.recoveryPolicy == RunRecoveryPolicy.NO_RESUME) {
            return RecoveryExecutionResult.Noop
        }

        val expected = when (run.state) {
            AgentRunState.WAITING_FOR_APPROVAL -> AgentRunState.WAITING_FOR_APPROVAL
            AgentRunState.WAITING_FOR_CLARIFICATION -> AgentRunState.WAITING_FOR_CLARIFICATION
            AgentRunState.WAITING_FOR_CAPABILITY -> AgentRunState.WAITING_FOR_CAPABILITY
            AgentRunState.WAITING_FOR_RESOURCE -> AgentRunState.WAITING_FOR_RESOURCE
            else -> AgentRunState.SUSPENDED
        }
        val claimed = agentRunCoordinator.claimResume(runId, expected)
        if (!claimed) return RecoveryExecutionResult.Noop

        eventBus.publish(
            AgentRecoveryStarted(
                runId = runId.value, interactionId = run.interactionId.value,
                recoveryKind = plan::class.simpleName ?: "Unknown"
            )
        )
        agentRunCoordinator.recordStep(AgentStep.RecoveryClaimed(
            AgentStepId(UUID.randomUUID().toString()),
            runId, Instant.now(), plan::class.simpleName ?: "Unknown",
            evidenceStepId = run.steps.lastOrNull()?.id?.value
        ))
        agentRunCoordinator.transition(runId, AgentRunState.EXECUTING)

        return when (plan) {
            is RecoveryPlan.CompleteFromRecordedResponse -> {
                RecoveryExecutionResult.CompleteResponse(runId, InteractionId(run.interactionId.value), plan.responseText, "")
            }
            is RecoveryPlan.RetryProviderTurn -> {
                val req =
                    AiRequest(text = "", priority = InferencePriority.INTERACTIVE) // Filled by preparer
                val turn = CognitiveTurnRequest(
                    interactionId = InteractionId(run.interactionId.value),
                    requestId = UUID.randomUUID().toString(),
                    request = req,
                    policy = AiContextPolicy.CONSERVATIVE, // Filled by session
                    responseDelivery = ResponseDelivery.VISUAL_ONLY, // Filled by session
                    origin = null,
                    metadata = mapOf("resumedRunId" to runId.value),
                    runId = runId,
                    mode = CognitiveTurnMode.RECOVER_PROVIDER,
                )
                RecoveryExecutionResult.StartTurn(turn)
            }
            is RecoveryPlan.ContinueAfterToolResult -> {
                val req = com.halo.sdk.provider.AiRequest(text = "", priority = InferencePriority.INTERACTIVE) // Filled by preparer
                val turn = CognitiveTurnRequest(
                    interactionId = InteractionId(run.interactionId.value),
                    requestId = UUID.randomUUID().toString(),
                    request = req,
                    policy = AiContextPolicy.CONSERVATIVE, // Filled by session
                    responseDelivery = ResponseDelivery.VISUAL_ONLY, // Filled by session
                    origin = null,
                    metadata = mapOf("resumedRunId" to runId.value),
                    runId = runId,
                    mode = CognitiveTurnMode.CONTINUE_AFTER_TOOL,
                )
                RecoveryExecutionResult.StartTurn(turn)
            }
            is RecoveryPlan.RetryTool -> {
                RecoveryExecutionResult.RetryTool(runId, plan.invocation, plan.useOriginalIdempotencyKey)
            }
            is RecoveryPlan.ReconcileTool -> {
                RecoveryExecutionResult.ReconcileTool(runId, plan.invocation)
            }
            is RecoveryPlan.MissingContext -> {
                agentRunCoordinator.transition(runId, AgentRunState.WAITING_FOR_CAPABILITY, "Missing context: ${plan.requirement}")
                RecoveryExecutionResult.Blocked("Missing context")
            }
            is RecoveryPlan.MissingCapability -> {
                agentRunCoordinator.transition(runId, AgentRunState.WAITING_FOR_CAPABILITY, "Missing capability: ${plan.capability}")
                RecoveryExecutionResult.Blocked("Missing capability")
            }
            is RecoveryPlan.AwaitingInput -> {
                agentRunCoordinator.transition(runId, AgentRunState.WAITING_FOR_APPROVAL, "Waiting for user input")
                RecoveryExecutionResult.Blocked("Waiting for user input")
            }
            is RecoveryPlan.UncertainExternalAction -> {
                val reason = "External action completion is unknown; it will not be replayed."
                agentRunCoordinator.fail(runId, reason)
                RecoveryExecutionResult.Blocked(reason)
            }
            is RecoveryPlan.UnsupportedLegacyState -> {
                agentRunCoordinator.fail(runId, plan.reason)
                RecoveryExecutionResult.Blocked(plan.reason)
            }
            is RecoveryPlan.NothingToDo -> {
                RecoveryExecutionResult.Noop
            }
        }
    }
}
