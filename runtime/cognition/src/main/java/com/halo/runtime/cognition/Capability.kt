package com.halo.runtime.cognition

data class CapabilityMetadata(
    val id: String,
    val priority: Int = 0,
    val permissions: Set<String> = emptySet()
)

interface Capability {
    val id: String

    /** Optional metadata used by deterministic registry resolution and diagnostics. */
    val metadata: CapabilityMetadata
        get() = CapabilityMetadata(id = id)
    
    suspend fun supports(task: Task): Boolean
    suspend fun execute(task: Task): Outcome
}
