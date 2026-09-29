package com.halo.engines.execution.service

import com.halo.runtime.cognition.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.time.Duration.Companion.seconds

class ExecutionScheduler(
    private val capabilityRegistry: CapabilityRegistry,
    private val retryPolicy: RetryPolicy = DefaultRetryPolicy()
) {
    suspend fun executeTaskGraph(
        task: Task,
        beforeDispatch: suspend (Task) -> Unit = {},
        afterDispatch: suspend (Task, Outcome) -> Unit = { _, _ -> },
    ): Outcome = coroutineScope {

        /*
         * 1. Execute dependencies first (they are prerequisites).
         */
        if (task.dependencies.isNotEmpty()) {
            if (task.isParallel) {
                val deferreds = task.dependencies.map { dep ->
                    async { executeTaskGraph(dep, beforeDispatch, afterDispatch) }
                }
                val results = deferreds.awaitAll()
                val firstFailure = results.firstOrNull { 
                    it !is Outcome.Success && it !is Outcome.Dispatched
                }
                if (firstFailure != null) {
                    return@coroutineScope firstFailure
                }
            } else {
                for (dep in task.dependencies) {
                    val depResult = executeTaskGraph(dep, beforeDispatch, afterDispatch)
                    if (depResult !is Outcome.Success && depResult !is Outcome.Dispatched) {
                        return@coroutineScope depResult
                    }
                }
            }
        }

        /*
         * 2. Resolve capability.
         */
        val capability = capabilityRegistry.resolve(task)
            ?: return@coroutineScope Outcome.PermanentFailure(
                "No capability found for task ${task::class.simpleName}"
            )

        // Side effects are dispatched once. A timeout does not prove they did not happen.
        beforeDispatch(task)
        val result = try {
            withTimeout(5.seconds) { capability.execute(task) }
        } catch (e: TimeoutCancellationException) {
            Outcome.Partial("Capability timed out; external completion is unknown")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Partial("Capability threw; external completion is unknown")
        }
        afterDispatch(task, result)

        return@coroutineScope result
    }
}
