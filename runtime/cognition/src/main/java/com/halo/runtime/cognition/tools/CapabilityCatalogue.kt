package com.halo.runtime.cognition.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CapabilitySourceKind {
    RUNTIME,
    HOST,
    DEVICE,
    WORKSPACE,
    PROVIDER,
}

enum class CapabilityAvailability {
    AVAILABLE,
    NEED_SETUP,
    DEGRADED,
    BLOCKED,
    UNAVAILABLE,
    UNKNOWN,
}

data class CapabilityInstance(
    val id: CapabilityId,
    val displayName: String,
    val description: String,
    val category: ToolCategory,
    val sourceKind: CapabilitySourceKind = CapabilitySourceKind.HOST,
    val ownerId: String? = null,
    val endpointId: String? = null,
    val availability: CapabilityAvailability = CapabilityAvailability.AVAILABLE,
    val unavailableReason: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)

/** @deprecated Legacy compatibility wrapper replaced by [CapabilityInstance]. */
data class Capability(
    val id: CapabilityId,
    val displayName: String,
    val description: String,
    val category: ToolCategory,
    val keywords: Set<String> = emptySet(),
    val dependsOn: Set<CapabilityId> = emptySet(),
    val availability: CapabilityAvailability = CapabilityAvailability.AVAILABLE,
    val source: String = "",
) {
    fun toInstance(scope: String = source): CapabilityInstance = CapabilityInstance(
        id = id,
        displayName = displayName,
        description = description,
        category = category,
        sourceKind = if (scope.contains("device", ignoreCase = true)) CapabilitySourceKind.DEVICE else CapabilitySourceKind.HOST,
        ownerId = scope,
        endpointId = scope,
        availability = availability,
    )
}

data class CapabilityCatalogueSnapshot(
    val capabilities: List<CapabilityInstance>,
    val revision: Long = 0L,
)

interface CapabilityCatalogueSource {
    val snapshot: StateFlow<CapabilityCatalogueSnapshot>
}

class CapabilityCatalogue(initial: Collection<Capability> = emptyList()) : CapabilityCatalogueSource {

    private val lock = Any()
    private val scopeMap = mutableMapOf<String, MutableMap<CapabilityId, CapabilityInstance>>()
    private var revision = 0L

    private val _snapshot = MutableStateFlow(CapabilityCatalogueSnapshot(emptyList(), 0L))
    override val snapshot: StateFlow<CapabilityCatalogueSnapshot> = _snapshot.asStateFlow()

    init {
        if (initial.isNotEmpty()) {
            val defaultMap = mutableMapOf<CapabilityId, CapabilityInstance>()
            initial.forEach { cap ->
                val inst = cap.toInstance()
                defaultMap[inst.id] = inst
            }
            scopeMap["default"] = defaultMap
            updateSnapshotLocked()
        }
    }

    val capabilities: List<CapabilityInstance>
        get() = _snapshot.value.capabilities

    fun capability(id: CapabilityId): CapabilityInstance? =
        _snapshot.value.capabilities.firstOrNull { it.id == id }

    fun capabilitiesIn(category: ToolCategory): List<CapabilityInstance> =
        _snapshot.value.capabilities.filter { it.category == category }

    fun available(): List<CapabilityInstance> =
        _snapshot.value.capabilities.filter { it.availability == CapabilityAvailability.AVAILABLE }

    fun publish(instance: CapabilityInstance, scope: String = instance.ownerId ?: "default") = synchronized(lock) {
        val map = scopeMap.getOrPut(scope) { mutableMapOf() }
        map[instance.id] = instance
        updateSnapshotLocked()
    }

    fun publish(capability: Capability) {
        publish(capability.toInstance(), capability.source.ifBlank { "default" })
    }

    fun replaceScope(scope: String, instances: List<CapabilityInstance>) = synchronized(lock) {
        val map = mutableMapOf<CapabilityId, CapabilityInstance>()
        instances.forEach { map[it.id] = it }
        scopeMap[scope] = map
        updateSnapshotLocked()
    }

    fun setAvailability(id: CapabilityId, availability: CapabilityAvailability) = synchronized(lock) {
        for (map in scopeMap.values) {
            val current = map[id]
            if (current != null) {
                map[id] = current.copy(availability = availability)
            }
        }
        updateSnapshotLocked()
    }

    fun remove(id: CapabilityId) = synchronized(lock) {
        for (map in scopeMap.values) {
            map.remove(id)
        }
        updateSnapshotLocked()
    }

    fun removeScope(scope: String) = synchronized(lock) {
        scopeMap.remove(scope)
        updateSnapshotLocked()
    }

    private fun updateSnapshotLocked() {
        revision++
        val all = scopeMap.values.flatMap { it.values }
            .distinctBy { it.id }
        _snapshot.value = CapabilityCatalogueSnapshot(all, revision)
    }
}

interface CapabilityPublisher {
    val scope: String
    fun publishInto(catalogue: CapabilityCatalogue)
}
