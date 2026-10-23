package com.halo.runtime.cognition.interaction

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.tools.ToolId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InteractionSurfaceReconciliationTest {
    private val now = Instant.parse("2026-10-23T00:00:00Z")
    private val id = AgentRunId("note")
    private fun pending(name: String = "approval", runId: AgentRunId = id) = AgentStep.InputRequested(
        AgentStepId("request-$name"), runId, now, AgentInputRequestId(name),
        AgentInputRequest.Approval(AgentInputRequestId(name), runId, now, invocationId = ToolInvocationId("invoke-$name"),
            toolId = ToolId("halo_create_empty_note"), action = "Create a note", risk = "low", argumentsDigest = "digest"))
    private fun resolved(name: String, resolution: AgentInputResolution) = AgentStep.InputResolved(
        AgentStepId("resolved-$name"), id, now, AgentInputRequestId(name), AgentInputResolutions.encode(resolution))
    private fun run(steps: List<AgentStep>, runId: AgentRunId = id) = AgentRun(runId, InteractionId("note"),
        "Create a note", AgentRunState.WAITING_FOR_APPROVAL, createdAt = now, updatedAt = now, steps = steps)

    private class Runs : AgentRunStore {
        val rows = MutableStateFlow<List<AgentRun>>(emptyList())
        override fun observeRuns() = rows
        override suspend fun get(runId: AgentRunId) = rows.value.firstOrNull { it.id == runId }
        override suspend fun nonTerminalRuns() = rows.value.filterNot { it.state.isTerminal }
        override suspend fun create(run: AgentRun) { rows.value += run }
        override suspend fun update(run: AgentRun) { rows.value = rows.value.map { if (it.id == run.id) run else it } }
        override suspend fun appendStep(step: AgentStep): Boolean = error("Projection must not append execution steps")
        override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState): Boolean = error("Projection must not resume execution")
        override suspend fun resolveInputIfPending(requestId: AgentInputRequestId, resolution: AgentInputResolution): Boolean =
            error("Projection must not resolve user input")
    }

    @Test fun resolvingTheLastApprovalBeforeTheDismissCallbackReconcilesWithoutCrashing() = runTest {
        for (resolution in listOf(AgentInputResolution.Approved, AgentInputResolution.Denied,
            AgentInputResolution.Cancelled, AgentInputResolution.Expired)) {
            val runs = Runs()
            val surfaces = InMemoryInteractionSurfaceStore()
            val coordinator = DefaultInteractionSurfaceCoordinator(runs, surfaces, DefaultInteractionSurfaceFactory(), backgroundScope)
            runs.rows.value = listOf(run(listOf(pending())))
            coordinator.rebuild()
            assertNotNull(surfaces.get(InteractionSurfaceIds.forInput(AgentInputRequestId("approval"))))
            runs.rows.value = listOf(run(listOf(pending(), resolved("approval", resolution))))
            coordinator.rebuild()
            assertFalse(surfaces.active.value.any { it.inputRequestId != null })
            coordinator.onInputResolved(AgentInputRequestId("approval"))
            coordinator.rebuild()
            assertEquals(AgentRunState.WAITING_FOR_APPROVAL, runs.rows.value.single().state)
        }
    }

    @Test fun observerKeepsRunningAfterResolutionAndProjectsTheNextRequest() = runTest {
        val runs = Runs()
        val surfaces = InMemoryInteractionSurfaceStore()
        val coordinator = DefaultInteractionSurfaceCoordinator(runs, surfaces, DefaultInteractionSurfaceFactory(), backgroundScope)
        runs.rows.value = listOf(run(listOf(pending())))
        coordinator.start(); runCurrent()
        assertNotNull(surfaces.get(InteractionSurfaceIds.forInput(AgentInputRequestId("approval"))))
        runs.rows.value = listOf(run(listOf(pending(), resolved("approval", AgentInputResolution.Denied))))
        runCurrent()
        assertFalse(surfaces.active.value.any { it.inputRequestId != null })
        runs.rows.value = listOf(run(listOf(pending(), resolved("approval", AgentInputResolution.Denied), pending("next"))))
        runCurrent()
        assertNotNull(surfaces.get(InteractionSurfaceIds.forInput(AgentInputRequestId("next"))))
        assertNull(surfaces.get(InteractionSurfaceIds.forInput(AgentInputRequestId("approval"))))
    }
}
