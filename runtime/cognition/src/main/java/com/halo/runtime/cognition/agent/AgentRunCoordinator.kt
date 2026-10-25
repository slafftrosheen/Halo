package com.halo.runtime.cognition.agent

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.recovery.AgentCheckpoint
import com.halo.runtime.cognition.agent.recovery.AgentCheckpointAssembler
import com.halo.runtime.cognition.agent.recovery.AgentRecoveryPlanner
import com.halo.runtime.cognition.agent.recovery.CapabilitySnapshot
import com.halo.runtime.cognition.agent.recovery.DefaultAgentRecoveryPlanner
import com.halo.runtime.cognition.agent.recovery.RecoveryPlan
import com.halo.sdk.runtime.ObservationOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class AgentRequest(
    val interactionId: InteractionId, val text: String, val origin: ObservationOrigin? = null,
    val speechOutputTarget: com.halo.sdk.runtime.SpeechOutputTarget? = null,
    val speechOutputFallback: com.halo.sdk.runtime.SpeechOutputFallback = com.halo.sdk.runtime.SpeechOutputFallback.DISALLOW_PHONE,
    val sourceCaptureId: String? = null,
    val kind: AgentRunKind = AgentRunKind.INTERACTIVE,
    val conversationId: String? = null,
    val responseDelivery: com.halo.sdk.runtime.ResponseDelivery? = null,
    val inputModality: com.halo.runtime.cognition.turn.InteractionInputModality? = null,
    val inputRoute: com.halo.sdk.runtime.SpeechInputRoute? = null,
)

/** Outcome of startup recovery classification for one run. */
sealed interface RecoveryClassification {
    data class Recoverable(val plan: RecoveryPlan) : RecoveryClassification
    data class WaitingInput(val plan: RecoveryPlan) : RecoveryClassification
    data class Unrecoverable(val reason: String) : RecoveryClassification
}

interface AgentRunCoordinator {
    val activeRuns: StateFlow<List<AgentRun>>
    suspend fun start(request: AgentRequest): AgentRunId
    suspend fun recordStep(step: AgentStep): Boolean
    suspend fun transition(runId: AgentRunId, state: AgentRunState, reason: String? = null)
    suspend fun resume(runId: AgentRunId) = transition(runId, AgentRunState.EXECUTING)
    suspend fun complete(runId: AgentRunId) = transition(runId, AgentRunState.COMPLETED)
    suspend fun fail(runId: AgentRunId, reason: String) = transition(runId, AgentRunState.FAILED, reason)
    suspend fun cancel(runId: AgentRunId, reason: String) = transition(runId, AgentRunState.CANCELLED, reason)

    /**
     * RUNTIME-018: classify every non-terminal run using the recovery planner.
     * Startup must classify, never surprise-execute: runs become SUSPENDED /
     * WAITING_* states; the caller (UI, session.resume) decides whether to resume.
     * Terminalizing happens only for genuinely unrecoverable runs.
     *
     * Runs carrying [RunRecoveryPolicy.NO_RESUME] (routines) keep their
     * conservative behavior: they are never offered as resumable.
     */
    suspend fun prepareOutstandingRunsForRecovery(
        capabilities: CapabilitySnapshot = CapabilitySnapshot(),
    ): Map<AgentRunId, RecoveryClassification>

    /**
     * Atomic resume claim; false when another surface already resumed the run.
     */
    suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState): Boolean
    suspend fun claimRecovery(
        runId: AgentRunId,
        expectedState: AgentRunState,
        claim: AgentStep.RecoveryClaimed,
    ): Boolean {
        if (!claimResume(runId, expectedState)) return false
        return recordStep(claim)
    }

    suspend fun checkpoint(runId: AgentRunId): AgentCheckpoint?

    /** @deprecated Conservative interim design replaced by recovery classification. */
    @Deprecated("Replaced by prepareOutstandingRunsForRecovery", ReplaceWith("prepareOutstandingRunsForRecovery()"))
    suspend fun reconcileInterruptedRuns() {
        prepareOutstandingRunsForRecovery()
    }
}

class DefaultAgentRunCoordinator(
    private val store: AgentRunStore,
    scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val checkpointAssembler: AgentCheckpointAssembler = AgentCheckpointAssembler(),
    private val recoveryPlanner: AgentRecoveryPlanner = DefaultAgentRecoveryPlanner(),
) : AgentRunCoordinator {
    private val mutex = Mutex()
    private var classified = false
    override val activeRuns = store.observeRuns().map { runs -> runs.filter { !it.state.isTerminal && it.parentRunId == null } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun prepareOutstandingRunsForRecovery(
        capabilities: CapabilitySnapshot,
    ): Map<AgentRunId, RecoveryClassification> = mutex.withLock {
        val classifications = mutableMapOf<AgentRunId, RecoveryClassification>()
        for (run in store.nonTerminalRuns()) {
            // A phone-script parent only owns durable progress and user decisions;
            // it has no executable operation to replay after process death.
            // Mark it interrupted rather than running generic provider recovery.
            if (com.halo.runtime.cognition.native.NativeWorkflowJournal.parentMarker(run) != null) {
                val now = Instant.now(clock)
                store.update(run.copy(state = AgentRunState.INTERRUPTED,
                    updatedAt = now, completedAt = now,
                    failureReason = "Phone workflow interrupted. Review the screen and issue the remaining steps afresh."))
                classifications[run.id] = RecoveryClassification.Unrecoverable(
                    "Phone workflow requires fresh user instruction; no automatic replay")
                continue
            }
            // Native phone-script step arguments may contain expired snapshot
            // tokens or transient, redacted text. The process-local continuation
            // authority was lost on restart, including for final-step approvals.
            // These runs are inspectable as evidence but never replayable.
            if (com.halo.runtime.cognition.native.NativeWorkflowJournal.marker(run) != null) {
                val now = Instant.now(clock)
                store.update(run.copy(state = AgentRunState.INTERRUPTED,
                    updatedAt = now, completedAt = now,
                    failureReason = "Phone script step interrupted. Reobserve the app before requesting another action."))
                classifications[run.id] = RecoveryClassification.Unrecoverable(
                    "Scripted phone step lost its live approval/observation context")
                continue
            }
            if (run.parentRunId != null) {
                val parent = store.get(run.parentRunId)
                val response = run.steps.filterIsInstance<AgentStep.ResponseProduced>().lastOrNull()
                val now = Instant.now(clock)
                when {
                    run.steps.any { it is AgentStep.RunGuardTriggered } -> store.update(run.copy(state = AgentRunState.FAILED,
                        updatedAt = now, completedAt = now, failureReason = "CHILD_FAILED"))
                    response != null -> store.update(run.copy(state = AgentRunState.COMPLETED, updatedAt = now, completedAt = now))
                    parent == null || parent.state.isTerminal -> store.update(run.copy(state = AgentRunState.CANCELLED,
                        updatedAt = now, completedAt = now, failureReason = "CHILD_CANCELLED"))
                    else -> moveSafely(run, AgentRunState.SUSPENDED)
                }
                // Parent owns replay admission and explicit context reconstruction. No autonomous startup inference.
                continue
            }
            // Routine executions keep their conservative no-resume semantics.
            val isNoResume = run.steps.filterIsInstance<AgentStep.RunPrepared>().any {
                it.recoveryPolicy == RunRecoveryPolicy.NO_RESUME
            } || run.interactionId.value.startsWith("routine:")
            if (isNoResume) {
                val now = Instant.now(clock)
                store.update(run.copy(state = AgentRunState.INTERRUPTED, updatedAt = now,
                    completedAt = now,
                    failureReason = run.failureReason ?: "Unattended routine run is never resumed automatically."))
                classifications[run.id] = RecoveryClassification.Unrecoverable("NO_RESUME recovery policy")
                continue
            }
            val plan = recoveryPlanner.plan(run, catalog = null, capabilities = capabilities)
            val classification = when (plan) {
                is RecoveryPlan.NothingToDo -> {
                    val committed = run.steps.any { it is AgentStep.ResponseCommitted }
                    val now = Instant.now(clock)
                    store.update(run.copy(
                        state = if (committed) AgentRunState.COMPLETED else AgentRunState.INTERRUPTED,
                        updatedAt = now,
                        completedAt = now,
                        failureReason = if (committed) null else "Run has no recoverable unfinished work.",
                    ))
                    RecoveryClassification.Unrecoverable(
                        if (committed) "Run response was already committed" else "No recoverable unfinished work"
                    )
                }
                is RecoveryPlan.AwaitingInput -> {
                    val waiting = when (plan.request) {
                        is AgentInputRequest.Approval -> AgentRunState.WAITING_FOR_APPROVAL
                        is AgentInputRequest.Clarification -> AgentRunState.WAITING_FOR_CLARIFICATION
                    }
                    moveSafely(run, waiting)
                    RecoveryClassification.WaitingInput(plan)
                }
                is RecoveryPlan.RetryProviderTurn,
                is RecoveryPlan.ContinueAfterToolResult,
                is RecoveryPlan.RetryTool,
                is RecoveryPlan.CompleteFromRecordedResponse,
                is RecoveryPlan.ReconcileTool,
                is RecoveryPlan.MissingContext,
                is RecoveryPlan.MissingCapability -> {
                    val target = when (plan) {
                        is RecoveryPlan.MissingCapability -> AgentRunState.WAITING_FOR_CAPABILITY
                        is RecoveryPlan.MissingContext -> AgentRunState.WAITING_FOR_CAPABILITY
                        else -> AgentRunState.SUSPENDED
                    }
                    moveSafely(run, target)
                    RecoveryClassification.Recoverable(plan)
                }
                is RecoveryPlan.UncertainExternalAction -> {
                    if (run.state != AgentRunState.INTERRUPTED) {
                        // Unrecoverable/uncertain: conservative terminalization with truthful reason.
                        val now = Instant.now(clock)
                        store.update(run.copy(state = AgentRunState.INTERRUPTED, updatedAt = now, completedAt = now,
                            failureReason = "External action completion is unknown; it will not be replayed."))
                    }
                    RecoveryClassification.Unrecoverable("External action outcome unknown")
                }
                is RecoveryPlan.UnsupportedLegacyState -> {
                    if (run.state != AgentRunState.INTERRUPTED) {
                        val now = Instant.now(clock)
                        store.update(run.copy(state = AgentRunState.INTERRUPTED, updatedAt = now, completedAt = now,
                            failureReason = plan.reason))
                    }
                    RecoveryClassification.Unrecoverable(plan.reason)
                }
            }
            classifications[run.id] = classification
        }
        classified = true
        classifications
    }

    private suspend fun moveSafely(run: AgentRun, target: AgentRunState) {
        if (run.state == target || run.state.isTerminal) return
        val now = Instant.now(clock)
        runCatching { store.update(run.copy(state = target, updatedAt = now)) }
    }

    /**
     * @deprecated Old startup behavior terminalized every non-terminal run.
     * Kept only as a fallback for callers that have not migrated; it now
     * delegates to recovery classification.
     */
    @Deprecated("Replaced by prepareOutstandingRunsForRecovery", ReplaceWith("prepareOutstandingRunsForRecovery()"))
    override suspend fun reconcileInterruptedRuns() {
        prepareOutstandingRunsForRecovery()
    }

    override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState): Boolean =
        store.claimResume(runId, expectedState)

    override suspend fun claimRecovery(
        runId: AgentRunId,
        expectedState: AgentRunState,
        claim: AgentStep.RecoveryClaimed,
    ): Boolean = store.claimRecovery(runId, expectedState, claim)

    override suspend fun checkpoint(runId: AgentRunId): AgentCheckpoint? =
        store.get(runId)?.let { checkpointAssembler.assemble(it) }

    override suspend fun start(request: AgentRequest): AgentRunId = mutex.withLock {
        val id = AgentRunId(newId())
        val now = Instant.now(clock)
        val recoveryPolicy = when (request.kind) {
            AgentRunKind.INTERACTIVE, AgentRunKind.SYSTEM, AgentRunKind.NATIVE -> RunRecoveryPolicy.USER_RESUME
            AgentRunKind.ROUTINE -> RunRecoveryPolicy.NO_RESUME
        }
        store.create(AgentRun(id, request.interactionId, request.text, AgentRunState.CREATED,
            request.origin, now, now, startedAt = now, speechOutputTarget = request.speechOutputTarget,
            speechOutputFallback = request.speechOutputFallback, sourceCaptureId = request.sourceCaptureId))
        store.appendStep(AgentStep.RunPrepared(AgentStepId("$id:prepared"), id, now,
            request.kind, recoveryPolicy, request.conversationId, request.responseDelivery,
            request.inputModality, request.inputRoute))
        id
    }

    override suspend fun recordStep(step: AgentStep): Boolean = mutex.withLock {
        val run = store.get(step.runId) ?: return@withLock false
        if (run.state.isTerminal) return@withLock false
        store.appendStep(step)
    }

    override suspend fun transition(runId: AgentRunId, state: AgentRunState, reason: String?) = mutex.withLock {
        val run = requireNotNull(store.get(runId)) { "Unknown run ${runId.value}" }
        if (run.state == state) return@withLock
        if (run.state.isTerminal) {
            if (state.isTerminal) {
                println("[HALO-AGENT-RUN-IDEMPOTENT] Terminal run ${run.id.value} in state ${run.state} ignored late terminal transition request to $state (reason: $reason)")
                return@withLock
            } else if (run.state == AgentRunState.INTERRUPTED) {
                println("[HALO-AGENT-RUN-RESUME] Resuming interrupted run ${run.id.value} to $state")
            } else {
                check(!run.state.isTerminal) { "Cannot transition terminal run ${run.id.value} from ${run.state} to $state" }
            }
        }
        val allowed = when (run.state) {
            AgentRunState.CREATED -> setOf(AgentRunState.PLANNING, AgentRunState.EXECUTING, AgentRunState.COMPLETED)
            AgentRunState.PLANNING -> setOf(AgentRunState.EXECUTING, AgentRunState.COMPLETED, AgentRunState.WAITING_FOR_CLARIFICATION, AgentRunState.WAITING_FOR_CAPABILITY, AgentRunState.WAITING_FOR_RESOURCE, AgentRunState.WAITING_FOR_EVENT)
            AgentRunState.EXECUTING -> setOf(AgentRunState.COMPLETED, AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CAPABILITY, AgentRunState.WAITING_FOR_RESOURCE, AgentRunState.WAITING_FOR_EVENT, AgentRunState.SUSPENDED)
            AgentRunState.WAITING_FOR_CLARIFICATION -> setOf(AgentRunState.PLANNING, AgentRunState.EXECUTING, AgentRunState.COMPLETED)
            AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CAPABILITY, AgentRunState.WAITING_FOR_RESOURCE, AgentRunState.WAITING_FOR_EVENT, AgentRunState.SUSPENDED, AgentRunState.INTERRUPTED -> setOf(AgentRunState.EXECUTING, AgentRunState.PLANNING, AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CLARIFICATION, AgentRunState.COMPLETED)
            else -> emptySet()
        } + setOf(AgentRunState.CANCELLED, AgentRunState.FAILED, AgentRunState.INTERRUPTED)
        require(state in allowed) { "Invalid transition ${run.state} -> $state" }
        val now = Instant.now(clock)
        val claimedCallIds = run.steps.mapNotNull { step ->
            when (step) {
                is AgentStep.ToolDispatched -> step.toolCallId
                is AgentStep.ToolExecutionClaimed -> step.toolCallId
                else -> null
            }
        }.toSet()
        val claimedFailure = run.steps.any { step ->
            when (step) {
                is AgentStep.ToolFailed -> step.toolCallId in claimedCallIds
                is AgentStep.ToolCompleted -> !step.success && step.toolCallId in claimedCallIds
                is AgentStep.ToolOutcomeRecorded -> !step.success && step.toolCallId in claimedCallIds
                else -> false
            }
        }
        val finalState = when {
            state == AgentRunState.FAILED && run.hasUnknownAction -> AgentRunState.INTERRUPTED
            state == AgentRunState.COMPLETED && run.hasUnknownAction -> AgentRunState.INTERRUPTED
            state == AgentRunState.COMPLETED && claimedFailure -> AgentRunState.FAILED
            else -> state
        }
        val finalReason = reason ?: when (finalState) {
            AgentRunState.INTERRUPTED -> "External action completion is unknown; it will not be replayed."
            AgentRunState.FAILED -> "One or more actions failed"
            else -> null
        }
        store.update(run.copy(state = finalState, updatedAt = now,
            completedAt = now.takeIf { finalState.isTerminal }, failureReason = finalReason))
        if (finalState.isTerminal && run.parentRunId == null) {
            store.children(run.id).filterNot { it.state.isTerminal }.forEach { child ->
                store.update(child.copy(state = AgentRunState.CANCELLED, updatedAt = now, completedAt = now,
                    failureReason = "CHILD_CANCELLED"))
            }
        }
    }
}
