package com.halo.runtime.cognition.tools

/**
 * RUNTIME-017 capability fabric.
 *
 * A [Capability] is a semantic unit of function that a host, device or provider
 * advertises to the Runtime. Runtime code reasons in these terms only; hosts and
 * drivers translate them into platform mechanics (Android intents, Linux services,
 * wearable transports). No Android or vendor imports may appear here.
 */

data class Capability(
    val id: CapabilityId,
    val displayName: String,
    val description: String,
    val category: ToolCategory,
    val keywords: Set<String> = emptySet(),
    /** CapabilityIds this capability composes from. */
    val dependsOn: Set<CapabilityId> = emptySet(),
    /** Generic availability observation, populated by the advertising adapter. */
    val availability: CapabilityAvailability = CapabilityAvailability.AVAILABLE,
    /** Evidence annotation, e.g. which host/driver advertised this instance. */
    val source: String = "",
)

enum class CapabilityAvailability {
    AVAILABLE,
    UNAVAILABLE,
    DEGRADED,
    UNKNOWN,
}

/**
 * Immutable catalogue of advertised semantic capabilities.
 *
 * Capability availability is deliberately independent from tool registration:
 * a capability may exist without an executable tool yet, and a tool may exist
 * while the capability is temporarily unavailable. Discovery joins the two.
 */
class CapabilityCatalogue(initial: Collection<Capability> = emptyList()) {

    private val _capabilities = LinkedHashMap<CapabilityId, Capability>().apply {
        initial.forEach { put(it.id, it) }
    }

    val capabilities: List<Capability>
        get() = _capabilities.values.toList()

    fun capability(id: CapabilityId): Capability? = _capabilities[id]

    fun capabilitiesIn(category: ToolCategory): List<Capability> =
        _capabilities.values.filter { it.category == category }

    fun available(): List<Capability> =
        _capabilities.values.filter { it.availability == CapabilityAvailability.AVAILABLE }

    fun publish(capability: Capability) {
        _capabilities[capability.id] = capability
    }

    fun setAvailability(id: CapabilityId, availability: CapabilityAvailability) {
        val current = _capabilities[id] ?: return
        _capabilities[id] = current.copy(availability = availability)
    }

    fun remove(id: CapabilityId) {
        _capabilities.remove(id)
    }
}

/**
 * Abstraction over the origin of a capability advertisement. Hosts and device
 * drivers implement this to publish what their platform can actually do.
 */
interface CapabilityPublisher {
    /** Stable scope id for this publisher, e.g. the host or driver name. */
    val scope: String

    fun publishInto(catalogue: CapabilityCatalogue)
}
