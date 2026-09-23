package com.halo.runtime.cognition.agent

import com.halo.cognition.language.InteractionId
import com.halo.sdk.runtime.ObservationOrigin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID

data class AgentRequest(
    val interactionId: InteractionId,
    val text: String,
    val origin: ObservationOrigin? = null
)

interface AgentRunCoordinator {
    val activeRuns: StateFlow<List<AgentRun>>
    suspend fun start(request: AgentRequest): AgentRunId
    suspend fun recordStep(step: AgentStep)
    suspend fun resume(runId: AgentRunId)
    suspend fun cancel(runId: AgentRunId, reason: String)
    suspend fun reconcileInterruptedRuns()
}

class DefaultAgentRunCoordinator : AgentRunCoordinator {
    private val mutableActiveRuns = MutableStateFlow<List<AgentRun>>(emptyList())
    override val activeRuns: StateFlow<List<AgentRun>> = mutableActiveRuns.asStateFlow()

    override suspend fun start(request: AgentRequest): AgentRunId {
        val runId = AgentRunId(UUID.randomUUID().toString())
        val now = Instant.now()
        val run = AgentRun(
            id = runId,
            interactionId = request.interactionId,
            request = request.text,
            state = AgentRunState.CREATED,
            origin = request.origin,
            createdAt = now,
            updatedAt = now,
            startedAt = now
        )
        mutableActiveRuns.value = mutableActiveRuns.value + run
        return runId
    }

    override suspend fun recordStep(step: AgentStep) {
        val currentList = mutableActiveRuns.value
        mutableActiveRuns.value = currentList.map { run ->
            if (run.id == step.runId) {
                run.copy(
                    steps = run.steps + step,
                    updatedAt = Instant.now(),
                    currentStepId = step.id
                )
            } else run
        }
    }

    override suspend fun resume(runId: AgentRunId) {
        mutableActiveRuns.value = mutableActiveRuns.value.map { run ->
            if (run.id == runId) run.copy(state = AgentRunState.EXECUTING, updatedAt = Instant.now())
            else run
        }
    }

    override suspend fun cancel(runId: AgentRunId, reason: String) {
        mutableActiveRuns.value = mutableActiveRuns.value.map { run ->
            if (run.id == runId) run.copy(state = AgentRunState.CANCELLED, failureReason = reason, updatedAt = Instant.now())
            else run
        }
    }

    override suspend fun reconcileInterruptedRuns() {
        mutableActiveRuns.value = mutableActiveRuns.value.map { run ->
            if (run.state in setOf(AgentRunState.EXECUTING, AgentRunState.PLANNING, AgentRunState.CREATED)) {
                run.copy(
                    state = AgentRunState.INTERRUPTED,
                    failureReason = "This action may have been started before Halo restarted.",
                    updatedAt = Instant.now()
                )
            } else run
        }
    }
}
