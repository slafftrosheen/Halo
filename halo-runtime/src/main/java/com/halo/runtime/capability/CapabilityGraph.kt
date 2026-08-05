package com.halo.runtime.capability

interface CapabilityGraph {
    fun registerCapability(capability: Capability)
    fun findBestProvider(requirement: CapabilityRequirement): CapabilityProvider?
}

interface Capability {
    val name: String
    val priority: Int
    val isOffline: Boolean
}

interface CapabilityRequirement {
    val requiredName: String
}

interface CapabilityProvider {
    val capability: Capability
    fun execute(params: Map<String, Any>): Any
}
