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
    suspend fun executeTaskGraph(task: Task): Outcome = coroutineScope {

        /*
         * 1. Execute dependencies first (they are prerequisites).
         */
        if (task.dependencies.isNotEmpty()) {
            if (task.isParallel) {
                val deferreds = task.dependencies.map { dep ->
                    async { executeTaskGraph(dep) }
                }
                val results = deferreds.awaitAll()
                val firstFailure = results.firstOrNull { 
                    it !is Outcome.Success && it !is Outcome.Partial 
                }
                if (firstFailure != null) {
                    return@coroutineScope firstFailure
                }
            } else {
                for (dep in task.dependencies) {
                    val depResult = executeTaskGraph(dep)
                    if (depResult !is Outcome.Success && depResult !is Outcome.Partial) {
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

        /*
         * 3. Execute with bounded retry.
         */
        var attempts = 0
        var result: Outcome = Outcome.PermanentFailure("Initialization failed")

        while (true) {
            attempts++
            result = try {
                withTimeout(5.seconds) {
                    capability.execute(task)
                }
            } catch (e: TimeoutCancellationException) {
                Outcome.RetryableFailure("Capability execution timed out after 5 seconds")
            } catch (e: CancellationException) {
                return@coroutineScope Outcome.Cancelled("Execution cancelled")
            } catch (e: Exception) {
                Outcome.RetryableFailure(e.message ?: "Execution failed with exception")
            }

            if (result is Outcome.Success) {
                break
            }

            if (retryPolicy.shouldRetry(attempts, result)) {
                delay(retryPolicy.nextDelay(attempts, result))
            } else {
                break
            }
        }

        return@coroutineScope result
    }
}