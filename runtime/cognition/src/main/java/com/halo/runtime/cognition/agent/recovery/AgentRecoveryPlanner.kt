package com.halo.runtime.cognition.agent.recovery

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.tools.ToolId

/**
 * RUNTIME-018 deterministic recovery planner.
 *
 * Reads the durable execution journal of a run and produces a [RecoveryPlan].
 * It never executes anything, never calls providers, and contains no host
 * heuristics: given the same run, descriptors and capability snapshot, the plan
 * is identical.
 *
 * Core truth table (see docs):
 *
 * | Evidence                                   | Plan                          |
 * |--------------------------------------------|-------------------------------|
 * | Provider started, no response              | RetryProviderTurn             |
 * | Tool prepared, not claimed                 | Continue normal provider path |
 * | SAFE tool claimed, no result               | RetryTool                     |
 * | Idempotent tool claimed, no result         | RetryTool (original key)      |
 * | Reconcile-required tool claimed, no result | ReconcileTool                 |
 * | Never-replay tool claimed, no result       | UncertainExternalAction       |
 * | Tool outcome recorded, provider follow-up? | ContinueAfterToolResult       |
 * | Final response recorded, not committed     | CompleteFromRecordedResponse  |
 * | Input requested, unresolved                | AwaitingInput                 |
 */
interface AgentRecoveryPlanner {
    fun plan(
        run: AgentRun,
        catalog: com.halo.runtime.cognition.tools.ToolRegistry?,
        capabilities: CapabilitySnapshot,
    ): RecoveryPlan

    fun explain(plan: RecoveryPlan): RecoveryExplanation
}

fun recoveryEvidenceStepId(run: AgentRun): String? =
    run.steps.lastOrNull { it !is AgentStep.RecoveryClaimed }?.id?.value

class DefaultAgentRecoveryPlanner : AgentRecoveryPlanner {

    override fun plan(
        run: AgentRun,
        catalog: com.halo.runtime.cognition.tools.ToolRegistry?,
        capabilities: CapabilitySnapshot,
    ): RecoveryPlan {
        if (run.state.isTerminal) return RecoveryPlan.NothingToDo

        val steps = run.steps

        // 1. Unresolved durable input wins: the run was waiting for a human.
        lastPendingInput(steps)?.let { request ->
            return RecoveryPlan.AwaitingInput(run.id, request)
        }

        // 2. Final response recorded but never committed -> idempotent commit.
        val produced = steps.filterIsInstance<AgentStep.ResponseProduced>().lastOrNull()
        if (produced != null && steps.none { it is AgentStep.ResponseCommitted }) {
            return checkClaim(run, RecoveryPlan.CompleteFromRecordedResponse(
                run.id, produced.text, "${run.id.value}:${produced.responseId}",
            ))
        }

        // A committed response is complete even if a crash happened before the run row became terminal.
        if (steps.any { it is AgentStep.ResponseCommitted }) return RecoveryPlan.NothingToDo

        // 3. Claimed tool outcomes take priority over provider continuation.
        // Unknown truth never becomes success, regardless of the success boolean.
        classifyClaimedTool(run)?.let { return checkClaim(run, it) }

        // 4. Legacy claim (old ToolDispatched) without outcome: never infer safety.
        legacyClaimWithoutOutcome(steps)?.let { toolId ->
            return RecoveryPlan.UnsupportedLegacyState(
                run.id, "Claimed tool $toolId lacks persisted replay policy; cannot infer safe recovery.",
            )
        }

        // 5. Provider turn started but never completed. Provider inference is
        // repeatable only when its required context is durable. CURRENT_VIEW
        // screen/camera context is process-local/transient and must be reacquired.
        openProviderTurn(steps)?.let { turn ->
            val transientRequirement = turn.contextRequirements.firstOrNull { requirement ->
                requirement.contains("CURRENT_VIEW") ||
                    requirement.contains("REQUEST_BYTES") ||
                    requirement.startsWith("SCREEN_TEXT:")
            }
            if (transientRequirement != null) {
                return checkClaim(run, RecoveryPlan.MissingContext(run.id, transientRequirement))
            }
            return checkClaim(run, RecoveryPlan.RetryProviderTurn(
                run.id, turn.turnId, turn.providerId, turn.modelId, turn.exposedToolIds,
            ))
        }

        // 6. A completed provider turn with no tool calls is already a final response.
        // If process death happened before ResponseProduced, commit that exact response.
        lastCompletedProviderTurn(steps)?.let { turn ->
            if (turn.toolCalls.isEmpty()) {
                return checkClaim(run, RecoveryPlan.CompleteFromRecordedResponse(
                    run.id,
                    turn.assistantText,
                    "${run.id.value}:provider:${turn.turnId.value}",
                ))
            }
        }

        // 7. Provider response exists with tool calls.
        lastCompletedProviderTurn(steps)?.let { turn ->
            if (turn.toolCalls.isNotEmpty()) {
                val callIds = turn.toolCalls.map { it.toolCallId }
                val resolutions = resolveToolCalls(callIds, steps)
                
                // If any call is still PENDING, we continue to process them.
                if (resolutions.values.any { it == ToolCallResolution.PENDING }) {
                    return checkClaim(run, RecoveryPlan.ContinueAfterToolResult(run.id, turn.turnId, emptySet()))
                }
                
                if (steps.any { it is AgentStep.ResponseProduced || it is AgentStep.ResponseCommitted }) {
                    // Final response already exists.
                    return RecoveryPlan.NothingToDo
                }

                return checkClaim(run, RecoveryPlan.ContinueAfterToolResult(run.id, turn.turnId, emptySet()))
            }
        }

        // 8. Prepared invocation that was never claimed: no side effect is
        // possible, but authorization/resource admission may also not have
        // completed. Re-enter the normal provider/tool path so those gates run
        // again; never execute a merely prepared call directly.
        preparedButUnclaimed(steps)?.let {
            val turnId = lastCompletedProviderTurn(steps)?.turnId
            return checkClaim(run, RecoveryPlan.ContinueAfterToolResult(run.id, turnId, emptySet()))
        }

        // 9. Anything else non-terminal without journal evidence is legacy/uncertain.
        return if (steps.none { it is AgentStep.RunPrepared }) {
            RecoveryPlan.UnsupportedLegacyState(run.id, "Run has no durable journal evidence.")
        } else {
            RecoveryPlan.UncertainExternalAction(run.id, null, null)
        }
    }
    
    private fun checkClaim(run: AgentRun, plan: RecoveryPlan): RecoveryPlan {
        val evidenceStepId = recoveryEvidenceStepId(run)
        val alreadyClaimed = run.steps.filterIsInstance<AgentStep.RecoveryClaimed>().any {
            it.planKind == plan::class.simpleName && it.evidenceStepId == evidenceStepId
        }
        return if (alreadyClaimed) {
            RecoveryPlan.UncertainExternalAction(run.id, null, "Repeated recovery claim without journal progress")
        } else plan
    }

    override fun explain(plan: RecoveryPlan): RecoveryExplanation = when (plan) {
        is RecoveryPlan.NothingToDo ->
            RecoveryExplanation("Run is already terminal.", null, safeToResume = false, requiresUserInput = false)
        is RecoveryPlan.AwaitingInput ->
            RecoveryExplanation("Waiting for user input recorded before shutdown.", null, safeToResume = true, requiresUserInput = true)
        is RecoveryPlan.RetryProviderTurn ->
            RecoveryExplanation("Provider turn never completed; it can be re-run.", null, safeToResume = true, requiresUserInput = false)
        is RecoveryPlan.ContinueAfterToolResult ->
            RecoveryExplanation("Tool results are journaled; continue the conversation.", null, safeToResume = true, requiresUserInput = false)
        is RecoveryPlan.RetryTool ->
            RecoveryExplanation("Claimed tool is safe to re-execute under its persisted replay policy.", null, safeToResume = true, requiresUserInput = false)
        is RecoveryPlan.ReconcileTool ->
            RecoveryExplanation("External state must be checked before any retry.", "Reconciliation required", safeToResume = false, requiresUserInput = false)
        is RecoveryPlan.CompleteFromRecordedResponse ->
            RecoveryExplanation("Final response was recorded; only the conversation commit remains.", null, safeToResume = true, requiresUserInput = false)
        is RecoveryPlan.MissingContext ->
            RecoveryExplanation("Transient context is gone; it must be reacquired.", "Missing context: ${plan.requirement}", safeToResume = false, requiresUserInput = true)
        is RecoveryPlan.MissingCapability ->
            RecoveryExplanation("A required capability is unavailable.", "Missing capability: ${plan.capability}", safeToResume = false, requiresUserInput = false)
        is RecoveryPlan.UncertainExternalAction ->
            RecoveryExplanation("An external action's completion is unknown; it will not be replayed.", "Outcome unknown", safeToResume = false, requiresUserInput = true)
        is RecoveryPlan.UnsupportedLegacyState ->
            RecoveryExplanation("Historical run lacks durable recovery metadata.", plan.reason, safeToResume = false, requiresUserInput = false)
    }

    // ---- journal helpers ----------------------------------------------------

    private fun lastPendingInput(steps: List<AgentStep>): AgentInputRequest? {
        val requested = mutableMapOf<String, AgentInputRequest>()
        val resolved = mutableSetOf<String>()
        for (step in steps) {
            when (step) {
                is AgentStep.InputRequested -> requested[step.requestId.value] = step.input
                is AgentStep.InputResolved -> resolved.add(step.requestId.value)
                else -> Unit
            }
        }
        return (requested.keys - resolved)
            .maxByOrNull { requested.getValue(it).createdAt.toEpochMilli() }
            ?.let { requested.getValue(it) }
    }

    /** Classifies claimed actions from their journaled identity, not guessed tool semantics. */
    private fun classifyClaimedTool(run: AgentRun): RecoveryPlan? {
        val steps = run.steps
        val preparedByInvocation = steps.filterIsInstance<AgentStep.ToolInvocationPrepared>()
            .associateBy { it.invocationId }
        for (claim in steps.filterIsInstance<AgentStep.ToolExecutionClaimed>()) {
            // Earlier tool-call IDs may be reused by a model across turns. Only a
            // later result may resolve this claim; modern outcomes also match the
            // stable invocation identity rather than the provider's call ID alone.
            val claimIndex = steps.indexOfFirst { it.id == claim.id }
            val later = steps.drop(claimIndex + 1)
            val latest = later.asReversed().firstOrNull { step ->
                when (step) {
                    is AgentStep.ToolOutcomeRecorded -> step.invocationId == claim.invocationId
                    is AgentStep.ToolCompleted -> step.toolCallId == claim.toolCallId
                    is AgentStep.ToolFailed -> step.toolCallId == claim.toolCallId
                    is AgentStep.ToolRejected -> step.toolCallId == claim.toolCallId
                    else -> false
                }
            }
            when (latest) {
                is AgentStep.ToolOutcomeRecorded -> {
                    if (latest.actionTruth == ActionTruth.UNKNOWN)
                        return RecoveryPlan.UncertainExternalAction(run.id, null, claim.toolId)
                    continue
                }
                is AgentStep.ToolCompleted -> {
                    if (latest.actionTruth == ActionTruth.UNKNOWN)
                        return RecoveryPlan.UncertainExternalAction(run.id, null, claim.toolId)
                    continue
                }
                is AgentStep.ToolFailed, is AgentStep.ToolRejected -> continue
                null -> Unit
                else -> continue
            }
            val prepared = preparedByInvocation[claim.invocationId]
                ?: return RecoveryPlan.UnsupportedLegacyState(run.id,
                    "Claimed tool " + claim.toolId + " lacks prepared replay metadata.")
            if (prepared.invocationId != claim.invocationId ||
                prepared.toolCallId != claim.toolCallId ||
                prepared.toolId != claim.toolId ||
                prepared.idempotencyKey != claim.idempotencyKey ||
                prepared.replaySafety != claim.replaySafety
            ) return RecoveryPlan.UnsupportedLegacyState(run.id,
                "Claimed tool " + claim.toolId + " has inconsistent replay metadata.")
            val invocation = ToolInvocation(
                invocationId = prepared.invocationId,
                runId = prepared.runId,
                interactionId = run.interactionId,
                toolCallId = prepared.toolCallId,
                toolId = ToolId(prepared.toolId),
                argumentsJson = prepared.argumentsJson,
                argumentsDigest = prepared.argumentsDigest,
                idempotencyKey = prepared.idempotencyKey,
                descriptorVersion = prepared.descriptorVersion,
                replaySafety = runCatching {
                    com.halo.runtime.cognition.tools.ReplaySafety.valueOf(prepared.replaySafety)
                }.getOrDefault(com.halo.runtime.cognition.tools.ReplaySafety.NEVER_REPLAY),
                createdAt = prepared.createdAt,
            )
            if (invocation.toolId.value.startsWith("halo_ui_") && invocation.toolId.value != "halo_ui_wait")
                return RecoveryPlan.MissingContext(run.id, "OBSERVATION_REQUIRED")
            return when (invocation.replaySafety) {
                com.halo.runtime.cognition.tools.ReplaySafety.SAFE ->
                    RecoveryPlan.RetryTool(run.id, invocation, useOriginalIdempotencyKey = true)
                com.halo.runtime.cognition.tools.ReplaySafety.IDEMPOTENT_WITH_KEY ->
                    RecoveryPlan.RetryTool(run.id, invocation, useOriginalIdempotencyKey = true)
                com.halo.runtime.cognition.tools.ReplaySafety.RECONCILE_BEFORE_RETRY ->
                    RecoveryPlan.ReconcileTool(run.id, invocation)
                com.halo.runtime.cognition.tools.ReplaySafety.NEVER_REPLAY ->
                    RecoveryPlan.UncertainExternalAction(run.id, invocation, invocation.toolId.value)
            }
        }
        return null
    }

    private fun legacyClaimWithoutOutcome(steps: List<AgentStep>): String? {
        val newStyleCalls = steps.filterIsInstance<AgentStep.ToolExecutionClaimed>().map { it.toolCallId }.toSet()
        val outcomes = steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>().map { it.toolCallId }.toSet() +
            steps.filterIsInstance<AgentStep.ToolCompleted>().map { it.toolCallId }.toSet() +
            steps.filterIsInstance<AgentStep.ToolFailed>().map { it.toolCallId }.toSet() +
            steps.filterIsInstance<AgentStep.ToolRejected>().map { it.toolCallId }.toSet()
        return steps.filterIsInstance<AgentStep.ToolDispatched>()
            .firstOrNull { dispatch ->
                dispatch.toolCallId !in newStyleCalls && dispatch.toolCallId !in outcomes
            }?.toolId
    }

    private fun openProviderTurn(steps: List<AgentStep>): AgentStep.ProviderTurnStarted? {
        val started = mutableMapOf<String, AgentStep.ProviderTurnStarted>()
        val completed = mutableSetOf<String>()
        for (step in steps) {
            when (step) {
                is AgentStep.ProviderTurnStarted -> started[step.turnId.value] = step
                is AgentStep.ProviderTurnCompleted -> completed.add(step.turnId.value)
                else -> Unit
            }
        }
        return (started.keys - completed).maxByOrNull { started.getValue(it).createdAt.toEpochMilli() }
            ?.let { started.getValue(it) }
    }

    private fun lastCompletedProviderTurn(steps: List<AgentStep>): AgentStep.ProviderTurnCompleted? =
        steps.filterIsInstance<AgentStep.ProviderTurnCompleted>().lastOrNull()

    private fun preparedButUnclaimed(steps: List<AgentStep>): ToolInvocation? {
        val claimed = steps.filterIsInstance<AgentStep.ToolExecutionClaimed>().map { it.toolCallId }.toSet()
        val claimedLegacy = steps.filterIsInstance<AgentStep.ToolDispatched>().map { it.toolCallId }.toSet()
        val prepared = steps.filterIsInstance<AgentStep.ToolInvocationPrepared>()
            .firstOrNull { it.toolCallId !in claimed && it.toolCallId !in claimedLegacy } ?: return null
        return ToolInvocation(
            invocationId = prepared.invocationId,
            runId = prepared.runId,
            interactionId = InteractionId(""),
            toolCallId = prepared.toolCallId,
            toolId = ToolId(prepared.toolId),
            argumentsJson = prepared.argumentsJson,
            argumentsDigest = prepared.argumentsDigest,
            idempotencyKey = prepared.idempotencyKey,
            descriptorVersion = prepared.descriptorVersion,
            replaySafety = runCatching {
                com.halo.runtime.cognition.tools.ReplaySafety.valueOf(prepared.replaySafety)
            }.getOrDefault(com.halo.runtime.cognition.tools.ReplaySafety.NEVER_REPLAY),
            createdAt = prepared.createdAt,
        )
    }
}
