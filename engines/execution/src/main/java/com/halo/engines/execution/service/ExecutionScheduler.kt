package com.halo.engines.execution.service

import com.halo.runtime.cognition.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ExecutionScheduler(
    private val capabilityRegistry: CapabilityRegistry
) {
    suspend fun executeTaskGraph(task: Task): Outcome = coroutineScope {
        val capability = capabilityRegistry.resolve(task)
            ?: return@coroutineScope Outcome.Failure("No capability found for task ${task::class.simpleName}")

        val result = capability.execute(task)

        if (result is Outcome.Success) {
            if (task.dependencies.isNotEmpty()) {
                if (task.isParallel) {
                    val deferreds = task.dependencies.map { dep ->
                        async { executeTaskGraph(dep) }
                    }
                    val results = deferreds.awaitAll()
                    val firstFailure = results.firstOrNull { it is Outcome.Failure }
                    if (firstFailure != null) {
                        return@coroutineScope firstFailure
                    }
                } else {
                    for (dep in task.dependencies) {
                        val depResult = executeTaskGraph(dep)
                        if (depResult is Outcome.Failure) {
                            return@coroutineScope depResult
                        }
                    }
                }
            }
            return@coroutineScope Outcome.Success("Graph executed successfully")
        }

        return@coroutineScope result
    }
}
