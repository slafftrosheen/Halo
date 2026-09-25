package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolDefinition

/**
 * RUNTIME-017 bounded tool discovery.
 *
 * Instead of dumping every enabled tool schema into every model call, the model
 * receives a compact catalogue of discoverable tools and calls [DISCOVER_TOOL]
 * to expand the specific tools it needs for the current turn.
 *
 * Discovery expands exposure per interaction; the global [ToolRegistry] is never
 * mutated. Exposed schemas are a per-turn view assembled by the context assembler.
 *
 * @param maxExposedSchemasUpperBound hard ceiling on schemas exposed per turn
 *   (discovery expansion must stay under this).
 */
class ToolDiscoveryService(
    private val registry: ToolRegistry,
    private val catalogue: CapabilityCatalogue,
    private val maxExposedSchemasUpperBound: Int = ToolExecutionLimits.MAX_TOOL_CALLS_PER_ROUND * 4,
) {

    companion object {
        /** Meta tool the model calls to pull additional tool schemas for the turn. */
        const val DISCOVER_TOOL = "halo_discover_tools"

        /** Compact per-tool line format in the discovery catalogue. */
        const val CATALOGUE_HEADER =
            "Additional tools exist beyond the schemas above. To use one, call halo_discover_tools " +
                "with its id first; you will receive the full schema for the rest of this conversation turn."

        private const val MAX_CATALOGUE_ENTRIES = 40
        private const val MAX_KEYWORDS_PER_ENTRY = 4

        /**
         * Schema of the discovery meta-tool. Always exposed when discovery is active.
         */
        fun discoveryToolDefinition(): ToolDefinition = ToolDefinition(
            id = DISCOVER_TOOL,
            name = DISCOVER_TOOL,
            description = "Expand one or more tool ids into full callable schemas for this turn. " +
                "Call this before using any tool you have not been given a schema for.",
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

    /** Compact discovery summary for context assembly. One bounded line per tool. */
    fun catalogueSummary(): String {
        val entries = registry.descriptors()
            .filter { it.visibility == ToolVisibility.DISCOVERABLE }
            .sortedBy { it.name }
            .take(MAX_CATALOGUE_ENTRIES)
        if (entries.isEmpty()) return ""
        return buildString {
            append(CATALOGUE_HEADER)
            append('\n')
            entries.joinTo(this, "\n") { descriptor ->
                buildString {
                    append("- ").append(descriptor.name)
                    append(" (").append(descriptor.category.name.lowercase()).append(")")
                    descriptor.keywords.take(MAX_KEYWORDS_PER_ENTRY).forEach { keyword ->
                        append(' ').append(keyword)
                    }
                }
            }
        }
    }

    /**
     * Catalogue summary packaged as a zero-argument pseudo-tool definition for
     * context assembly. Never executed; exists so the summary rides in the tool
     * list the provider already renders.
     */
    fun catalogueSummaryDefinition(): ToolDefinition = ToolDefinition(
        id = "halo_tool_catalogue",
        name = "halo_tool_catalogue",
        description = catalogueSummary().ifEmpty { "No additional tools available." },
        parameters = mapOf("type" to "object", "properties" to emptyMap<String, Any>(), "additionalProperties" to false),
    )

    /**
     * Expose schemas for an explicit id list plus the discovery meta-tool itself.
     * Clamped to [maxExposedSchemasUpperBound]; unregistered or hidden ids are skipped.
     */
    fun expand(toolIds: List<String>): List<ToolDefinition> {
        val expanded = linkedSetOf<ToolDefinition>()
        expanded.add(discoveryToolDefinition())
        for (rawId in toolIds) {
            if (expanded.size >= maxExposedSchemasUpperBound) break
            val id = rawId.trim()
            if (id.isEmpty() || id == DISCOVER_TOOL) continue
            val descriptor = registry.descriptor(id) ?: continue
            if (descriptor.visibility != ToolVisibility.DISCOVERABLE) continue
            expanded.add(descriptor.definition)
        }
        return expanded.toList()
    }

    /** True when the registry has more discoverable tools than fit comfortably in a prompt. */
    fun discoveryRecommended(): Boolean =
        registry.descriptors().count { it.visibility == ToolVisibility.DISCOVERABLE } >
            maxExposedSchemasUpperBound
}
