package com.halo.engines.execution.service

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.ICapabilityRegistry
import com.halo.runtime.cognition.Task

class CapabilityRegistry : ICapabilityRegistry {
    private val capabilities = mutableListOf<Capability>()

    fun register(capability: Capability) {
        capabilities.add(capability)
    }

    override suspend fun getCapability(task: Task): Capability? {
        return capabilities.firstOrNull { it.supports(task) }
    }
}
