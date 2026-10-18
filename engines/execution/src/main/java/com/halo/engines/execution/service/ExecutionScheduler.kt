package com.halo.engines.execution.service

import com.halo.runtime.cognition.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Executes a validated dependency graph. Prerequisites always finish before
 * their parent is dispatched, and parallel branches share a small execution
 * budget rather than starting an unbounded number of phone/device actions.
 *
 * No attempt is made to replay an action after timeout: completion is unknown.
 */
class ExecutionScheduler(
    private val capabilityRegistry: CapabilityRegistry,
    private val retryPolicy: RetryPolicy = DefaultRetryPolicy()
) {
    suspend fun executeTaskGraph(
        task: Task,
        beforeDispatch: suspend (Task) -> Unit = {},
        afterDispatch: suspend (Task, Outcome) -> Unit = { _, _ -> },
    ): Outcome = coroutineScope {
        validateGraph(task)?.let { reason ->
            return@coroutineScope Outcome.PermanentFailure(reason)
        }
        // Shared across all nested dependencies, not one semaphore per node.
        // This caps actual concurrent capability invocations, while allowing
        // prerequisite computation/dispatch preparation to proceed independently.
        executeNode(task, Semaphore(MAX_CONCURRENT_ACTIONS), beforeDispatch, afterDispatch)
    }

    private suspend fun executeNode(
        task: Task,
        permits: Semaphore,
        beforeDispatch: suspend (Task) -> Unit,
        afterDispatch: suspend (Task, Outcome) -> Unit,
    ): Outcome = coroutineScope {
        if (task.dependencies.isNotEmpty()) {
            if (task.isParallel) {
                val results = task.dependencies.map { dep ->
                    async { executeNode(dep, permits, beforeDispatch, afterDispatch) }
                }.awaitAll()
                val firstFailure = results.firstOrNull {
                    it !is Outcome.Success && it !is Outcome.Dispatched
                }
                if (firstFailure != null) return@coroutineScope firstFailure
            } else {
                for (dep in task.dependencies) {
                    val result = executeNode(dep, permits, beforeDispatch, afterDispatch)
                    if (result !is Outcome.Success && result !is Outcome.Dispatched) {
                        return@coroutineScope result
                    }
                }
            }
        }

        val capability = capabilityRegistry.resolve(task)
            ?: return@coroutineScope Outcome.PermanentFailure(
                "No capability found for task ${task::class.simpleName}"
            )

        permits.withPermit {
            // Pre-dispatch journaling is inside the same admission gate as
            // execution, so a cancelled queued branch never claims an action.
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
            result
        }
    }

    /**
     * Validate *before any side effects*. Reusing an ID in distinct branches
     * would dispatch a physical action twice; reject it rather than assuming
     * that executing one node is an idempotent operation.
     */
    private fun validateGraph(root: Task): String? {
        val visiting = mutableSetOf<UUID>()
        val visited = mutableSetOf<UUID>()
        fun walk(task: Task, depth: Int): String? {
            if (depth > MAX_DEPTH) return "Task dependency graph exceeds maximum depth"
            if (task.id in visiting) return "Task dependency cycle detected"
            if (!visited.add(task.id)) return "Task dependency is duplicated"
            if (visited.size > MAX_NODES) return "Task dependency graph exceeds maximum size"
            visiting.add(task.id)
            for (dependency in task.dependencies) {
                walk(dependency, depth + 1)?.let { return it }
            }
            visiting.remove(task.id)
            return null
        }
        return walk(root, 0)
    }

    private companion object {
        const val MAX_CONCURRENT_ACTIONS = 2
        const val MAX_NODES = 128
        const val MAX_DEPTH = 32
    }
}
