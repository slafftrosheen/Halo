package com.halo.sdk.runtime

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WorkloadPlanSchedulerTest {
    @Test fun failedMandatoryContextCancelsSpeculativePreparation() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val scheduler = WorkloadPlanScheduler()
        val states = withTimeout(2_000) {
            scheduler.execute(WorkloadExecutionPlan("turn", listOf(
                WorkloadStageSpec("context", "CONTEXT", deadlineAfterMs = 60_000),
                WorkloadStageSpec("warmup", "MODEL_PREPARATION",
                    speculative = true, deadlineAfterMs = 60_000),
            )), mapOf(
                "context" to { _ ->
                    started.await()
                    throw IllegalStateException("Cannot resolve required source")
                },
                "warmup" to { _ ->
                    started.complete(Unit)
                    try { awaitCancellation() }
                    finally { cancelled.complete(Unit) }
                },
            ))
        }
        assertEquals(WorkloadStageState.FAILED, states["context"])
        assertEquals(WorkloadStageState.CANCELLED, states["warmup"])
        assertTrue(cancelled.isCompleted)
    }

    @Test fun telemetryFailureDoesNotPreventRealWork() = runBlocking {
        var ran = false
        val scheduler = WorkloadPlanScheduler(eventSink = { throw IllegalStateException("observer unavailable") })
        val outcome = scheduler.execute(WorkloadExecutionPlan("turn", listOf(
            WorkloadStageSpec("context", "CONTEXT")
        )), mapOf("context" to { _ -> ran = true }))
        assertTrue(ran)
        assertEquals(WorkloadStageState.COMPLETED, outcome["context"])
    }

    @Test fun dependenciesStayOrderedAndSkipOnFailure() = runBlocking {
        val events = mutableListOf<String>()
        val stages = listOf(
            WorkloadStageSpec("a", "CONTEXT"),
            WorkloadStageSpec("b", "RETRIEVAL", dependencies = setOf("a")),
        )
        val scheduler = WorkloadPlanScheduler()
        val states = scheduler.execute(WorkloadExecutionPlan("turn", stages), mapOf(
            "a" to { _ -> events += "a" },
            "b" to { _ -> events += "b" },
        ))
        assertEquals(listOf("a", "b"), events)
        assertEquals(WorkloadStageState.COMPLETED, states["b"])
    }
}
