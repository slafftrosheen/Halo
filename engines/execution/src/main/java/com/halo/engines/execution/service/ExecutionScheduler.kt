package com.halo.engines.execution.service

import com.halo.runtime.cognition.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ExecutionScheduler(
    private val retryManager: RetryManager,
    private val resourceLockManager: ResourceLockManager
) {
    suspend fun executeTaskGraph(context: ExecutionContext, task: Task): TaskResult = coroutineScope {
        context.cancellation.checkCancellation()

        // Very basic DAG walker. Proper implementation would use topological sort and manage parallel branches.
        val capability = context.capabilities.getCapability(task) 
            ?: return@coroutineScope TaskResult.Failure("No capability found for task ${task::class.simpleName}")

        val result = retryManager.withRetry(RetryPolicy()) {
            resourceLockManager.withLock(task::class.simpleName ?: "Unknown") {
                capability.execute(task)
            }
        }

        if (result is TaskResult.Success) {
            // Execute dependencies (children)
            if (task.dependencies.isNotEmpty()) {
                if (task.isParallel) {
                    val deferreds = task.dependencies.map { dep ->
                        async { executeTaskGraph(context, dep) }
                    }
                    val results = deferreds.awaitAll()
                    val firstFailure = results.firstOrNull { it is TaskResult.Failure }
                    if (firstFailure != null) {
                        return@coroutineScope firstFailure
                    }
                } else {
                    for (dep in task.dependencies) {
                        val depResult = executeTaskGraph(context, dep)
                        if (depResult is TaskResult.Failure) {
                            return@coroutineScope depResult
                        }
                    }
                }
            }
            return@coroutineScope TaskResult.Success("Graph executed successfully")
        }

        return@coroutineScope result
    }
}
