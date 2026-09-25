package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolDefinition

class ToolExposurePlanner(
    private val registry: ToolRegistry,
    private val availabilityResolver: ToolAvailabilityResolver? = null,
) {

    fun initialExposure(): ToolExposureSet {
        val bootstrap = registry.descriptors()
            .filter { it.visibility == ToolVisibility.BOOTSTRAP }
            .filter { availabilityResolver?.isAvailable(it) != false }
            .map { it.id }
            .toSet()

        return ToolExposureSet(
            bootstrap = bootstrap,
            discovered = emptySet(),
            pinned = emptySet(),
        )
    }

    fun fromDefinitions(definitions: List<ToolDefinition>): ToolExposureSet {
        val exposed = definitions.mapNotNull { definition ->
            registry.descriptor(definition.name)?.id
                ?: registry.descriptor(definition.id)?.id
        }.toSet()

        val bootstrap = registry.descriptors()
            .filter { it.visibility == ToolVisibility.BOOTSTRAP }
            .filter { availabilityResolver?.isAvailable(it) != false }
            .map { it.id }
            .toSet()

        return ToolExposureSet(
            bootstrap = bootstrap,
            pinned = exposed - bootstrap,
        )
    }

    fun expand(current: ToolExposureSet, requestedIds: List<String>): ToolExposureSet {
        val newDiscovered = mutableSetOf<ToolId>()
        for (raw in requestedIds) {
            val id = raw.trim()
            if (id.isEmpty() || id == ToolDiscoveryService.DISCOVER_TOOL) continue
            val descriptor = registry.descriptor(id) ?: continue
            if (descriptor.visibility == ToolVisibility.DISCOVERABLE) {
                if (availabilityResolver?.isAvailable(descriptor) != false) {
                    newDiscovered.add(descriptor.id)
                }
            }
        }
        return current.copy(discovered = current.discovered + newDiscovered)
    }

    fun effectiveDefinitions(
        exposure: ToolExposureSet,
        discoverySummary: String = "",
    ): List<ToolDefinition> {
        val definitions = mutableListOf<ToolDefinition>()

        definitions.add(ToolDiscoveryService.discoveryToolDefinition(discoverySummary))

        for (toolId in exposure.effective) {
            if (toolId.value == ToolDiscoveryService.DISCOVER_TOOL) continue
            val descriptor = registry.descriptor(toolId.value) ?: continue
            if (availabilityResolver?.isAvailable(descriptor) != false) {
                definitions.add(descriptor.definition)
            }
        }
        return definitions
    }
}
