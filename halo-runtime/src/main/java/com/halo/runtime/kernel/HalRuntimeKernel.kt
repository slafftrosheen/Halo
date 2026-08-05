package com.halo.runtime.kernel

import com.halo.runtime.registry.ServiceRegistry
import com.halo.runtime.capability.CapabilityGraph

interface HalRuntimeKernel {
    val serviceRegistry: ServiceRegistry
    val capabilityGraph: CapabilityGraph
    
    suspend fun boot()
    suspend fun shutdown()
    
    fun getStatus(): KernelStatus
}

interface KernelStatus {
    val isHealthy: Boolean
    val uptimeMillis: Long
}
