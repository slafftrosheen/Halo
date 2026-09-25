package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolDefinition

/**
 * RUNTIME-017 / RUNTIME-020.1 bounded tool discovery.
 *
 * Instead of dumping every enabled tool schema into every model call, the model
 * receives a compact catalogue of discoverable tools and calls [DISCOVER_TOOL]
 * to expand the specific tools it needs for the current turn.
 */
class ToolDiscoveryService(
    private val registry: ToolRegistry,
    private val availabilityResolver: ToolAvailabilityResolver? = null,
    private val maxExposedSchemasUpperBound: Int = ToolExecutionLimits.MAX_TOOL_CALLS_PER_ROUND * 4,
) {

    companion object {
        /** Meta tool the model calls to pull additional tool schemas for the turn. */
        const val DISCOVER_TOOL = "halo_discover_tools"

        private const val MAX_CATALOGUE_ENTRIES = 40

        /**
         * Schema of the discovery meta-tool with optional bounded domain summary.
         */
        fun discoveryToolDefinition(summary: String = ""): ToolDefinition {
            val baseDesc = "Expand one or more tool ids into full callable schemas for this turn. " +
                "Call this before using any tool you have not been given a schema for."
            val fullDesc = if (summary.isNotBlank()) "$baseDesc\n\n$summary" else baseDesc

            return ToolDefinition(
                id = DISCOVER_TOOL,
                name = DISCOVER_TOOL,
                description = fullDesc,
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "toolIds" to mapOf(
                            "type" to "array",
                            "items" to mapOf("type" to "string", "maxLength" to 100),
                            "maxItems" to 8,
                            "minItems" to 1,
                        ),
                    ),
                    "required" to listOf("toolIds"),
                    "additionalProperties" to false,
                ),
            )
        }
    }

    /** Compact discovery summary. One bounded line per available discoverable tool. */
    fun catalogueSummary(): String {
        val entries = registry.descriptors()
            .filter { it.visibility == ToolVisibility.DISCOVERABLE }
            .filter { availabilityResolver?.isAvailable(it) != false }
            .sortedBy { it.name }
            .take(MAX_CATALOGUE_ENTRIES)
        if (entries.isEmpty()) return ""
        return buildString {
            append("Available discoverable actions:")
            append('\n')
            entries.joinTo(this, "\n") { descriptor ->
                "- ${descriptor.name} (${descriptor.category.name.lowercase()})"
            }
        }
    }

    /**
     * Expose schemas for an explicit id list plus the discovery meta-tool itself.
     */
    fun expand(toolIds: List<String>): List<ToolDefinition> {
        val expanded = linkedSetOf<ToolDefinition>()
        expanded.add(discoveryToolDefinition(catalogueSummary()))
        for (rawId in toolIds) {
            if (expanded.size >= maxExposedSchemasUpperBound) break
            val id = rawId.trim()
            if (id.isEmpty() || id == DISCOVER_TOOL) continue
            val descriptor = registry.descriptor(id) ?: continue
            if (descriptor.visibility != ToolVisibility.DISCOVERABLE) continue
            if (availabilityResolver?.isAvailable(descriptor) == false) continue
            expanded.add(descriptor.definition)
        }
        return expanded.toList()
    }

    fun discoveryRecommended(): Boolean =
        registry.descriptors().count {
            it.visibility == ToolVisibility.DISCOVERABLE && availabilityResolver?.isAvailable(it) != false
        } > maxExposedSchemasUpperBound
}
