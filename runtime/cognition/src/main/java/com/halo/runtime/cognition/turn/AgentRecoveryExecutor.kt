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
import com.halo.runtime.cognition.ui.*
import com.halo.runtime.cognition.agent.ToolArgumentCanonicalizer
import com.halo.runtime.cognition.agent.ToolInvocationId
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.isTerminal

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
    private val uiGenerationValid: (String) -> Boolean = { false },
    private val uiRecoveryObserver: suspend (UiTransactionRecord) -> UiRecoveryObservation? = { null },
) {
    suspend fun loadRun(runId: AgentRunId) = agentRunStore?.get(runId)
    private val bindingMutex = kotlinx.coroutines.sync.Mutex()
    suspend fun bindConversation(runId: AgentRunId, conversationId: String, explicitRebind: Boolean = false): Boolean {
        bindingMutex.lock()
        try {
            val existing = loadRun(runId)?.let { com.halo.runtime.cognition.continuity.RunContinuityResolver.conversationId(it) }
            if (existing != null && !explicitRebind) return existing == conversationId
            return agentRunCoordinator?.recordStep(AgentStep.ContinuityBound(AgentStepId(UUID.randomUUID().toString()), runId, Instant.now(), conversationId)) == true
        } finally { bindingMutex.unlock() }
    }

    suspend fun executeRecovery(runId: AgentRunId): RecoveryExecutionResult {
        if (agentRunStore == null || agentRunCoordinator == null) return RecoveryExecutionResult.Noop
        var run = agentRunStore.get(runId) ?: return RecoveryExecutionResult.Noop
        if (run.state.isTerminal) return RecoveryExecutionResult.Noop
        if (run.parentRunId != null) return RecoveryExecutionResult.Blocked("Child recovery is owned by its parent")
        if (run.steps.filterIsInstance<AgentStep.RunPrepared>().any { it.recoveryPolicy == RunRecoveryPolicy.NO_RESUME }) return RecoveryExecutionResult.Noop
        val resolvedCalls = run.steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>().map { it.toolCallId }.toSet() +
            run.steps.filterIsInstance<AgentStep.ToolRejected>().map { it.toolCallId }.toSet()
        val pendingUi = run.steps.filterIsInstance<AgentStep.ProviderTurnCompleted>().lastOrNull()?.toolCalls.orEmpty()
            .filter { it.toolName.startsWith("halo_ui_") && it.toolName != "halo_ui_wait" && it.toolCallId !in resolvedCalls }
        var validUiContinuation = false
        for (call in pendingUi) {
            val args = ToolArgumentCanonicalizer.parseToArgumentsMap(call.argumentsJson)
            val claimed = run.steps.filterIsInstance<AgentStep.ToolExecutionClaimed>().any { it.toolCallId == call.toolCallId }
            if (!claimed && uiGenerationValid(args["snapshotId"]?.toString().orEmpty())) { validUiContinuation = true; continue }
            val previous = run.steps.filterIsInstance<AgentStep.UiTransactionRecorded>().lastOrNull { it.transaction.toolCallId == call.toolCallId }?.transaction
            val observed = previous?.let { uiRecoveryObserver(it) }
            val knownReceipt = previous?.receipt ?: observed?.receipt
            val truth = knownReceipt?.actionTruth ?: if (claimed) ActionTruth.UNKNOWN else ActionTruth.FAILED
            val output = "status=" + if (truth == ActionTruth.CONFIRMED) "confirmed_success change=observation_reconciled next=USE_FRESH_SNAPSHOT" else
                "observation_required actionTruth=$truth next=READ_SCREEN"
            val invocation = run.steps.filterIsInstance<AgentStep.ToolInvocationPrepared>().firstOrNull { it.toolCallId == call.toolCallId }?.invocationId
                ?: ToolInvocationId("${runId.value}:ui-recovery:${call.toolCallId}")
            agentRunStore.appendStep(AgentStep.ToolOutcomeRecorded(AgentStepId(UUID.randomUUID().toString()), runId, Instant.now(),
                invocation, call.toolCallId, call.toolName, truth == ActionTruth.CONFIRMED, output, truth, "OBSERVATION_REQUIRED"))
            if (previous != null) agentRunStore.appendStep(AgentStep.UiTransactionRecorded(AgentStepId(UUID.randomUUID().toString()), runId, Instant.now(),
                previous.copy(phase = UiWorkflowPhase.WAITING_FOR_MODEL, observation = observed?.snapshot?.observation(), receipt = knownReceipt)))
        }
        if (pendingUi.isNotEmpty()) run = agentRunStore.get(runId) ?: run

        val classifiedPlan = recoveryPlanner.plan(
            run = run,
            catalog = toolRegistry,
            capabilities = CapabilitySnapshot(),
        )
        val plan = if (validUiContinuation && classifiedPlan is RecoveryPlan.MissingContext && classifiedPlan.requirement == "OBSERVATION_REQUIRED")
            RecoveryPlan.ContinueAfterToolResult(runId, run.steps.filterIsInstance<AgentStep.ProviderTurnCompleted>().lastOrNull()?.turnId, emptySet())
            else classifiedPlan

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
                RecoveryExecutionResult.CompleteResponse(runId, InteractionId(run.interactionId.value), UiWorkflowProjector.summary(run) ?: plan.responseText, run.steps.filterIsInstance<AgentStep.ResponseProduced>().lastOrNull()?.responseId ?: plan.commitId)
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
                    metadata = mapOf("resumedRunId" to runId.value, "uiGenerationPreserved" to validUiContinuation.toString()),
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
                    metadata = mapOf("resumedRunId" to runId.value, "uiGenerationPreserved" to validUiContinuation.toString()),
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
