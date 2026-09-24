package com.halo.runtime.cognition.agent

import com.halo.cognition.language.InteractionId
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
)

interface AgentRunCoordinator {
    val activeRuns: StateFlow<List<AgentRun>>
    suspend fun start(request: AgentRequest): AgentRunId
    suspend fun recordStep(step: AgentStep): Boolean
    suspend fun transition(runId: AgentRunId, state: AgentRunState, reason: String? = null)
    suspend fun resume(runId: AgentRunId) = transition(runId, AgentRunState.EXECUTING)
    suspend fun complete(runId: AgentRunId) = transition(runId, AgentRunState.COMPLETED)
    suspend fun fail(runId: AgentRunId, reason: String) = transition(runId, AgentRunState.FAILED, reason)
    suspend fun cancel(runId: AgentRunId, reason: String) = transition(runId, AgentRunState.CANCELLED, reason)
    suspend fun reconcileInterruptedRuns()
}

class DefaultAgentRunCoordinator(
    private val store: AgentRunStore,
    scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : AgentRunCoordinator {
    private val mutex = Mutex()
    private var reconciled = false
    override val activeRuns = store.observeRuns().map { runs -> runs.filterNot { it.state.isTerminal } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private suspend fun reconcile() {
        if (reconciled) return
        store.interruptedRuns().forEach { run ->
            val now = Instant.now(clock)
            store.update(run.copy(state = AgentRunState.INTERRUPTED, updatedAt = now, completedAt = now,
                failureReason = if (run.hasUnknownAction) "External action completion is unknown; it will not be replayed."
                else "Execution interrupted by process restart."))
        }
        reconciled = true
    }

    override suspend fun reconcileInterruptedRuns() = mutex.withLock { reconcile() }

    override suspend fun start(request: AgentRequest): AgentRunId = mutex.withLock {
        reconcile()
        val id = AgentRunId(newId())
        val now = Instant.now(clock)
        store.create(AgentRun(id, request.interactionId, request.text, AgentRunState.CREATED,
            request.origin, now, now, startedAt = now, speechOutputTarget = request.speechOutputTarget,
            speechOutputFallback = request.speechOutputFallback, sourceCaptureId = request.sourceCaptureId))
        id
    }

    override suspend fun recordStep(step: AgentStep): Boolean = mutex.withLock {
        val run = requireNotNull(store.get(step.runId)) { "Unknown run ${step.runId.value}" }
        check(!run.state.isTerminal) { "Cannot append to ${run.state}" }
        store.appendStep(step)
    }

    override suspend fun transition(runId: AgentRunId, state: AgentRunState, reason: String?) = mutex.withLock {
        val run = requireNotNull(store.get(runId)) { "Unknown run ${runId.value}" }
        if (run.state == state) return@withLock
        if (run.state.isTerminal) return@withLock
        val allowed = when (run.state) {
            AgentRunState.CREATED -> setOf(AgentRunState.PLANNING)
            AgentRunState.PLANNING -> setOf(AgentRunState.EXECUTING, AgentRunState.WAITING_FOR_CLARIFICATION, AgentRunState.WAITING_FOR_CAPABILITY)
            AgentRunState.EXECUTING -> setOf(AgentRunState.COMPLETED, AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CAPABILITY, AgentRunState.SUSPENDED)
            AgentRunState.WAITING_FOR_CLARIFICATION -> setOf(AgentRunState.PLANNING, AgentRunState.EXECUTING)
            AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CAPABILITY, AgentRunState.SUSPENDED -> setOf(AgentRunState.EXECUTING)
            else -> emptySet()
        } + setOf(AgentRunState.CANCELLED, AgentRunState.FAILED, AgentRunState.INTERRUPTED)
        require(state in allowed) { "Invalid transition ${run.state} -> $state" }
        val now = Instant.now(clock)
        val finalState = when {
            state == AgentRunState.FAILED && run.hasUnknownAction -> AgentRunState.INTERRUPTED
            state == AgentRunState.COMPLETED && run.hasUnknownAction -> AgentRunState.INTERRUPTED
            state == AgentRunState.COMPLETED && run.steps.any { it is AgentStep.ToolFailed || (it is AgentStep.ToolCompleted && !it.success) } -> AgentRunState.FAILED
            else -> state
        }
        val finalReason = reason ?: when (finalState) {
            AgentRunState.INTERRUPTED -> "External action completion is unknown; it will not be replayed."
            AgentRunState.FAILED -> "One or more actions failed"
            else -> null
        }
        store.update(run.copy(state = finalState, updatedAt = now,
            completedAt = now.takeIf { finalState.isTerminal }, failureReason = finalReason))
    }
}
