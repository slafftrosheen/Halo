package com.halo.engines.execution.service

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.ICapabilityRegistry
import com.halo.runtime.cognition.Task
import java.util.concurrent.CopyOnWriteArrayList

class CapabilityRegistry : ICapabilityRegistry {
    private val registered = CopyOnWriteArrayList<RegisteredCapability>()

    @Synchronized
    override fun register(capability: Capability) {
        require(capability.id.isNotBlank()) { "Capability ID must not be blank" }
        require(capability.metadata.id == capability.id) {
            "Capability metadata ID must match capability ID: ${capability.id}"
        }
        require(registered.none { it.capability.id == capability.id }) {
            "Capability ID is already registered: ${capability.id}"
        }
        registered += RegisteredCapability(capability, registered.size.toLong())
    }

    @Synchronized
    override fun unregister(id: String): Boolean {
        val registration = registered.firstOrNull { it.capability.id == id } ?: return false
        return registered.remove(registration)
    }

    fun get(id: String): Capability? = registered.firstOrNull { it.capability.id == id }?.capability

    override fun capabilities(): List<Capability> = registered
        .sortedWith(compareByDescending<RegisteredCapability> { it.capability.metadata.priority }
            .thenBy { it.registrationOrder })
        .map { it.capability }

    override suspend fun resolve(task: Task): Capability? {
        return capabilities().firstOrNull { it.supports(task) }
    }

    private data class RegisteredCapability(
        val capability: Capability,
        val registrationOrder: Long
    )
}
