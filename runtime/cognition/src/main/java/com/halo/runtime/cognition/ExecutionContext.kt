package com.halo.runtime.cognition

import com.halo.sdk.runtime.RuntimeContext
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant

// Basic interface to represent capability registry without circular dependencies
interface ICapabilityRegistry {
    fun register(capability: Capability)

    fun unregister(id: String): Boolean

    suspend fun resolve(task: Task): Capability?

    fun capabilities(): List<Capability> = emptyList()
}

// CancellationToken stub
interface CancellationToken {
    val isCancelled: Boolean
    fun checkCancellation() {
        if (isCancelled) throw CancellationException()
    }
}

data class ExecutionContext(
    val plan: ExecutionPlan,
    val runtime: RuntimeContext,
    val capabilities: ICapabilityRegistry,
    val cancellation: CancellationToken,
    val startedAt: Instant
)
