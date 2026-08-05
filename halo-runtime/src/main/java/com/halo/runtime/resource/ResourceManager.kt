package com.halo.runtime.resource

/**
 * The Resource Manager is responsible for allocating and monitoring resources
 * across all engines and modules.
 */
interface ResourceManager {
    suspend fun requestResource(request: ResourceRequest): ResourceAllocation
    fun releaseResource(allocationId: String)
    
    fun getAvailableResources(): SystemResources
}

data class ResourceRequest(
    val requesterId: String,
    val requiredRamMb: Int,
    val cpuPriority: CpuPriority,
    val needsNetwork: Boolean,
    val needsGpu: Boolean
)

enum class CpuPriority {
    LOW, MEDIUM, HIGH, REALTIME
}

sealed class ResourceAllocation {
    data class Granted(val allocationId: String) : ResourceAllocation()
    data class Denied(val reason: String) : ResourceAllocation()
}

data class SystemResources(
    val availableRamMb: Int,
    val batteryLevel: Float,
    val isNetworkAvailable: Boolean
)
