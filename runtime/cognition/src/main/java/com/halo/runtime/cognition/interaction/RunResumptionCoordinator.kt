package com.halo.runtime.cognition.interaction

import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.RunRecoveryPolicy
import com.halo.runtime.cognition.agent.isTerminal

/**
 * RUNTIME-019 single host entry point for resuming a durable run. Wraps the
 * atomic R-018 claim ([com.halo.runtime.cognition.agent.AgentRunStore.claimResume])
 * plus the session resume path. The UI never resumes execution directly;
 * hosts call this only after an [InteractionResponseHandler] accepted a
 * response (or through an equivalent controlled path, e.g. recovery retry).
 */
interface RunResumptionCoordinator {
    suspend fun requestResume(runId: AgentRunId): ResumeRequestResult
}

sealed interface ResumeRequestResult {
    /** Resume dispatched. */
    data object Requested : ResumeRequestResult

    /** Another caller already resumed this run; nothing to do. */
    data object AlreadyResumed : ResumeRequestResult

    /** Run is not in a resumable state for this path. */
    data class NotEligible(val reason: String) : ResumeRequestResult
}

/**
 * Default implementation: checks durable eligibility (NO_RESUME policy,
 * waiting state) and delegates to the session resume, whose internal
 * [AgentRunStore.claimResume] is the single atomic first-wins gate. The
 * coordinator deliberately does NOT pre-claim: the session owns the claim.
 */
class DefaultRunResumptionCoordinator(
    private val agentRunStore: AgentRunStore,
    private val resume: (runId: AgentRunId) -> Unit,
) : RunResumptionCoordinator {

    override suspend fun requestResume(runId: AgentRunId): ResumeRequestResult {
        val run = agentRunStore.get(runId)
            ?: return ResumeRequestResult.NotEligible("Unknown run")
        if (run.state.isTerminal) return ResumeRequestResult.NotEligible("Run is terminal")
        val prepared = run.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()
        if (prepared?.recoveryPolicy == RunRecoveryPolicy.NO_RESUME) {
            return ResumeRequestResult.NotEligible("Run policy is NO_RESUME")
        }
        when (run.state) {
            AgentRunState.WAITING_FOR_APPROVAL,
            AgentRunState.WAITING_FOR_CLARIFICATION,
            -> Unit
            else -> return ResumeRequestResult.NotEligible("Run is not waiting for input (state=${run.state})")
        }
        resume(runId)
        return ResumeRequestResult.Requested
    }
}
