package com.halo.runtime.cognition.interaction

import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.AgentInputRequest
import com.halo.runtime.cognition.agent.AgentInputRequestId
import com.halo.runtime.cognition.agent.AgentRun
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentStep
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Derives and maintains the active [InteractionSurface] projection from
 * durable execution state.
 *
 * Responsibilities (deliberately narrow):
 *  - observe waiting runs and publish surfaces for their pending input;
 *  - remove surfaces after durable resolution;
 *  - rebuild the projection after process restart;
 *  - project run progress into a stable PROGRESS surface.
 *
 * It does NOT execute tools, invoke providers, or make authorization
 * decisions. The journal ([AgentRunStore]) stays the single durable truth;
 * this coordinator only maintains the renderable projection.
 */
interface InteractionSurfaceCoordinator {
    /** Starts observing the run store and keeping the projection fresh. */
    fun start()

    /** Rebuilds the projection from durable state (process restart / host recreation). */
    suspend fun rebuild()

    /** Removes the surface for a durably resolved input request. */
    suspend fun onInputResolved(requestId: AgentInputRequestId)

    /** Removes the input and progress surfaces of [runId] (cancellation / terminal states). */
    suspend fun onRunTerminal(runId: AgentRunId)

    /** Publishes or updates the run progress surface from execution state. */
    suspend fun onRunProgress(
        runId: AgentRunId,
        label: String,
        phase: InteractionProgressPhase,
        detail: String? = null,
        progress: Float? = null,
        cancellable: Boolean = true,
    )

    /** Removes the progress surface (terminal run or host policy). */
    suspend fun onRunProgressEnded(runId: AgentRunId)
}

class DefaultInteractionSurfaceCoordinator(
    private val agentRunStore: AgentRunStore,
    private val surfaceStore: InteractionSurfaceStore,
    private val surfaceFactory: InteractionSurfaceFactory,
    private val scope: CoroutineScope,
) : InteractionSurfaceCoordinator {

    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            agentRunStore.observeRuns().collect { runs ->
                sync(runs)
            }
        }
    }

    private suspend fun sync(runs: List<AgentRun>) {
        val waiting = runs.filter {
            it.state == AgentRunState.WAITING_FOR_APPROVAL || it.state == AgentRunState.WAITING_FOR_CLARIFICATION
        }
        waiting.forEach { syncRun(it) }
        // Runs no longer waiting lose their input surfaces.
        val waitingIds = waiting.map { it.id }.toSet()
        surfaceStore.active.value
            .filter { it.inputRequestId != null && it.runId != null && it.runId !in waitingIds }
            .forEach { surfaceStore.dismiss(it.id) }
        // RUNTIME-019: progress and result projections derive from run state.
        runs.forEach { deriveProgressAndResult(it) }
    }

    /**
     * Progress phases derive from durable journal state (spec: never arbitrary
     * host progress strings). Result surfaces exist only for runs that executed
     * tools, with [ActionTruth] taken from the journal — never model prose.
     */
    private suspend fun deriveProgressAndResult(run: AgentRun) {
        when (run.state) {
            AgentRunState.PLANNING -> projectProgress(run, InteractionProgressPhase.PREPARING, "Preparing")
            AgentRunState.EXECUTING -> {
                val phases = run.steps
                val hasClaimedTool = phases.any { it is AgentStep.ToolExecutionClaimed } &&
                    phases.none { it is AgentStep.ToolOutcomeRecorded }
                val openTurn = phases.any { it is AgentStep.ProviderTurnStarted } &&
                    phases.none { it is AgentStep.ProviderTurnCompleted }
                when {
                    hasClaimedTool -> projectProgress(run, InteractionProgressPhase.ACTING, "Working")
                    openTurn -> projectProgress(run, InteractionProgressPhase.THINKING, "Thinking")
                    else -> projectProgress(run, InteractionProgressPhase.FINALIZING, "Finishing up")
                }
            }
            AgentRunState.WAITING_FOR_APPROVAL,
            AgentRunState.WAITING_FOR_CLARIFICATION,
            AgentRunState.WAITING_FOR_CAPABILITY,
            AgentRunState.WAITING_FOR_RESOURCE,
            -> projectProgress(
                run,
                InteractionProgressPhase.WAITING,
                if (run.state == AgentRunState.WAITING_FOR_RESOURCE) "Waiting for resource" else "Waiting for you"
            )
            else -> {
                surfaceStore.dismiss(InteractionSurfaceIds.forRunProgress(run.id))
                if (run.state == AgentRunState.COMPLETED) projectResult(run)
            }
        }
    }

    private suspend fun projectProgress(run: AgentRun, phase: InteractionProgressPhase, label: String) {
        onRunProgress(run.id, label = label, phase = phase, cancellable = true)
    }

    private suspend fun projectResult(run: AgentRun) {
        val outcome = run.steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>().lastOrNull()
            ?: run.steps.filterIsInstance<AgentStep.ToolCompleted>().lastOrNull()
            ?: return // Pure conversation runs never produce result surfaces.
        val truth: ActionTruth
        val outcomeIdentity: String
        when (outcome) {
            is AgentStep.ToolOutcomeRecorded -> {
                truth = outcome.actionTruth
                outcomeIdentity = outcome.invocationId.value
            }
            is AgentStep.ToolCompleted -> {
                truth = outcome.actionTruth
                outcomeIdentity = outcome.toolCallId
            }
            else -> return
        }
        val headline = when {
            truth == ActionTruth.CONFIRMED -> "Done"
            truth == ActionTruth.FAILED -> "Couldn't complete the action"
            truth == ActionTruth.UNKNOWN -> "Action sent — result unknown"
            else -> "Action ${truth.name.lowercase().replace('_', ' ')}"
        }
        val id = InteractionSurfaceIds.forToolResult(run.id, outcomeIdentity)
        if (surfaceStore.get(id) != null) return
        val surface = InteractionSurface(
            id = id,
            runId = run.id,
            interactionId = run.interactionId,
            inputRequestId = null,
            kind = InteractionSurfaceKind.RESULT,
            title = null,
            message = null,
            content = InteractionContent.Result(
                headline = headline,
                detail = null,
                truth = truth,
                followUps = listOf(InteractionFollowUp.OpenRunDetails(run.id), InteractionFollowUp.Dismiss),
            ),
            actions = listOf(
                InteractionAction(InteractionActionId("dismiss"), "OK", InteractionActionSemantic.DISMISS),
            ),
            presentation = InteractionPresentationPolicy(urgency = InteractionUrgency.LOW),
            createdAt = Instant.now(),
            expiresAt = null,
            provenance = InteractionSurfaceProvenance(
                source = InteractionSurfaceSource.TOOL_RESULT,
                sourceId = outcomeIdentity,
            ),
        )
        if (InteractionSurfaceValidator.validate(surface) is InteractionSurfaceValidator.Result.Valid) {
            surfaceStore.publish(surface)
        }
    }

    private suspend fun syncRun(run: AgentRun) {
        val pending = pendingRequestsFor(run)
        val currentInputSurfaces = surfaceStore.active.value.filter {
            it.runId == run.id && it.inputRequestId != null
        }
        // Durable requests that no longer resolve to a pending request: remove projection.
        currentInputSurfaces.filter { it.inputRequestId?.value !in pending.map { req -> req.id.value } }
            .forEach { surfaceStore.dismiss(it.id) }
        // New pending requests: publish a validated surface.
        pending
            .filter { req -> currentInputSurfaces.none { it.inputRequestId == req.id } }
            .forEach { req ->
                val surface = surfaceFactory.forInput(req, run)
                if (InteractionSurfaceValidator.validate(surface) is InteractionSurfaceValidator.Result.Valid) {
                    surfaceStore.publish(surface)
                }
            }
        // Surfaces already projected stay untouched. currentInputSurfaces is a
        // pre-dismissal snapshot: resolving the last request legitimately leaves
        // pending empty while that snapshot still contains its removed surface.
    }

    /**
     * Pending durable input for a run, read from the journal: the latest
     * [AgentStep.InputRequested] per request id that has no matching
     * [AgentStep.InputResolved] afterwards.
     */
    private fun pendingRequestsFor(run: AgentRun): List<AgentInputRequest> {
        val requested = linkedMapOf<String, AgentInputRequest>()
        val resolved = mutableSetOf<String>()
        run.steps.forEach { step ->
            when (step) {
                is AgentStep.InputRequested -> requested[step.requestId.value] = step.input
                is AgentStep.InputResolved -> resolved.add(step.requestId.value)
                else -> Unit
            }
        }
        return requested.filterKeys { it !in resolved }.values.toList()
    }

    override suspend fun rebuild() {
        sync(agentRunStore.nonTerminalRuns())
    }

    override suspend fun onInputResolved(requestId: AgentInputRequestId) {
        surfaceStore.dismiss(InteractionSurfaceIds.forInput(requestId))
    }

    override suspend fun onRunTerminal(runId: AgentRunId) {
        surfaceStore.active.value
            .filter { it.runId == runId && it.kind != InteractionSurfaceKind.RESULT }
            .forEach { surfaceStore.dismiss(it.id) }
    }

    override suspend fun onRunProgress(
        runId: AgentRunId,
        label: String,
        phase: InteractionProgressPhase,
        detail: String?,
        progress: Float?,
        cancellable: Boolean,
    ) {
        val now = Instant.now()
        val id = InteractionSurfaceIds.forRunProgress(runId)
        val existing = surfaceStore.get(id)
        val surface = InteractionSurface(
            id = id,
            runId = runId,
            interactionId = null,
            inputRequestId = null,
            kind = InteractionSurfaceKind.PROGRESS,
            title = null,
            message = label,
            content = InteractionContent.Progress(
                label = label,
                phase = phase,
                progress = progress,
                detail = detail,
                cancellable = cancellable,
            ),
            actions = if (cancellable) {
                listOf(InteractionAction(InteractionActionId("cancel-run"), "Stop", InteractionActionSemantic.CANCEL))
            } else {
                emptyList()
            },
            presentation = InteractionPresentationPolicy(
                urgency = InteractionUrgency.LOW,
                preferredModalities = setOf(InteractionModality.VISUAL),
            ),
            createdAt = existing?.createdAt ?: now,
            expiresAt = null,
            provenance = InteractionSurfaceProvenance(
                source = InteractionSurfaceSource.AGENT_RUN,
                sourceId = runId.value,
            ),
        )
        if (existing == null) {
            if (InteractionSurfaceValidator.validate(surface) is InteractionSurfaceValidator.Result.Valid) {
                surfaceStore.publish(surface)
            }
        } else {
            surfaceStore.update(surface)
        }
    }

    override suspend fun onRunProgressEnded(runId: AgentRunId) {
        surfaceStore.dismiss(InteractionSurfaceIds.forRunProgress(runId))
    }
}
