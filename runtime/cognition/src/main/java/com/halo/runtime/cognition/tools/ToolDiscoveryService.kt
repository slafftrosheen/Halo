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

        /** Bounded result count: a small model must not receive a wall of matches. */
        const val MAX_SEARCH_MATCHES = 8

        /**
         * Schema of the discovery meta-tool with optional bounded domain summary.
         */
        fun discoveryToolDefinition(summary: String = ""): ToolDefinition {
            val baseDesc = "Find the actions Halo can actually perform on this device and make them callable " +
                "for this turn. Search by plain words such as \"alarm\", \"volume\", \"call Alice\" or \"settings\", " +
                "or pass exact tool ids. Always call this before using an action whose schema you were not given."
            val fullDesc = if (summary.isNotBlank()) "$baseDesc\n\n$summary" else baseDesc

            return ToolDefinition(
                id = DISCOVER_TOOL,
                name = DISCOVER_TOOL,
                description = fullDesc,
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf(
                            "type" to "string",
                            "maxLength" to 200,
                            "description" to "Plain words describing what you want to do, for example 'alarm' or 'turn volume down'",
                        ),
                        "toolIds" to mapOf(
                            "type" to "array",
                            "items" to mapOf("type" to "string", "maxLength" to 100),
                            "maxItems" to 8,
                        ),
                    ),
                    "required" to emptyList<String>(),
                    "additionalProperties" to false,
                ),
            )
        }
    }

    /** Compact discovery summary. One bounded line per available discoverable tool. */
    fun catalogueSummary(): String = catalogueSummary(availableCatalogue())

    private fun catalogueSummary(catalogue: List<ToolDescriptor>): String {
        val entries = catalogue.sortedBy { it.name }.take(MAX_CATALOGUE_ENTRIES)
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
     * Searchable discovery: match a plain-language query against the metadata a model
     * can actually reason about (id, title/verb, description, keywords, category).
     * Ranking is deliberately simple and deterministic: name matches first, then
     * keyword matches, then description matches.
     */
    fun search(query: String, limit: Int = MAX_SEARCH_MATCHES): List<ToolDescriptor> {
        val terms = query.lowercase().split(Regex("[^a-z0-9_]+")).filter { it.length >= 2 }
        if (terms.isEmpty()) return emptyList()

        val candidates = availableCatalogue()

        return candidates.mapNotNull { descriptor ->
            val name = descriptor.name.lowercase()
            val keywords = descriptor.keywords.joinToString(" ").lowercase()
            val description = descriptor.definition.description.lowercase()
            val category = descriptor.category.name.lowercase()
            var score = 0
            for (term in terms) {
                if (name.contains(term)) score += 3
                if (keywords.contains(term)) score += 2
                if (description.contains(term)) score += 1
                if (category.contains(term)) score += 1
            }
            if (score == 0) null else descriptor to score
        }
            .sortedWith(compareByDescending<Pair<ToolDescriptor, Int>> { it.second }.thenBy { it.first.name })
            .take(limit)
            .map { it.first }
    }

    /** One bounded line per candidate, stable enough for a small model to copy an id from. */
    fun describe(descriptors: List<ToolDescriptor>): String = descriptors.joinToString("\n") { descriptor ->
        val risk = descriptor.security.risk.name.lowercase()
        "- ${descriptor.name} [${descriptor.category.name.lowercase()}/$risk]: " +
            descriptor.definition.description.lineSequence().first().take(120)
    }

    /**
     * Expose schemas for an explicit id list plus the discovery meta-tool itself.
     */
    fun expand(toolIds: List<String>): List<ToolDefinition> {
        val catalogue = availableCatalogue()
        val expanded = linkedSetOf<ToolDefinition>()
        expanded.add(discoveryToolDefinition(catalogueSummary(catalogue)))
        for (rawId in toolIds) {
            if (expanded.size >= maxExposedSchemasUpperBound) break
            val id = rawId.trim()
            if (id.isEmpty() || id == DISCOVER_TOOL) continue
            val descriptor = catalogue.firstOrNull { it.name == id || it.definition.id == id } ?: continue
            val contract = descriptor.operability
            val definition = if (contract == null) descriptor.definition else descriptor.definition.copy(description =
                descriptor.definition.description + "\nOperation contract v${contract.version}: ${contract.operation.name}; result ${contract.resultEncoding.name}. " +
                    "Result IDs: ${contract.resultIdFields.sorted().joinToString().ifBlank { "see result" }}. " +
                    "Source-version fields: ${contract.sourceVersionArguments.sorted().joinToString().ifBlank { "none" }}. " +
                    "Replay: ${descriptor.security.replaySafety.name}. Typed and voice-final requests use this same action; device/speech success is never implied by this schema.")
            expanded.add(definition)
        }
        return expanded.toList()
    }

    /** Descriptor lookup for discovery callers that only have a plain id. */
    fun registryDescriptorOrNull(toolId: String): ToolDescriptor? =
        availableCatalogue().firstOrNull { it.name == toolId || it.definition.id == toolId }

    /** Initial context makes its discover-first decision and summary from the same scan. */
    fun bootstrapDefinitionOrNull(): ToolDefinition? {
        val catalogue = availableCatalogue()
        return if (catalogue.size > maxExposedSchemasUpperBound)
            discoveryToolDefinition(catalogueSummary(catalogue)) else null
    }

    fun discoveryRecommended(): Boolean = availableCatalogue().size > maxExposedSchemasUpperBound

    private fun availableCatalogue(): List<ToolDescriptor> {
        // ToolRegistry captures its current dynamic sources once for this enumeration.
        // Keep all registered aliases before filtering visibility, matching the host callback.
        val descriptors = registry.descriptors()
        val candidates = descriptors.filter { it.visibility == ToolVisibility.DISCOVERABLE }
        val registeredNames = descriptors.flatMap { listOf(it.definition.id, it.definition.name) }.toSet()
        return availabilityResolver?.availableDescriptors(candidates, registeredNames) ?: candidates
    }
}
