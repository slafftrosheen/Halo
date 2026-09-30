package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.skills.SkillRegistry
import com.halo.sdk.provider.ToolCallProtocol
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.skills.SkillId

/**
 * Machine-readable exposure directive.
 *
 * A tool result that widens the next provider turn says so with one stable token
 * instead of burying the fact in prose:
 *
 * ```
 * halo_exposure_add=halo_set_alarm,halo_volume
 * ```
 *
 * Both the model and the Runtime read the same line, so expansion is testable and
 * never depends on parsing English.
 */
object ToolExposureDirective {
    const val PREFIX = "halo_exposure_add="

    /** A successfully loaded skill activates its own tools for the next turn. */
    const val SKILL_PREFIX = "halo_skill_activated="

    fun emit(toolIds: Collection<String>): String =
        PREFIX + toolIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    fun emitSkill(skillId: String): String = SKILL_PREFIX + skillId.trim()

    fun parse(text: String): List<String> = parseValues(text, PREFIX)

    fun parseSkills(text: String): List<String> = parseValues(text, SKILL_PREFIX)

    private fun parseValues(text: String, prefix: String): List<String> =
        text.lineSequence()
            .filter { it.trimStart().startsWith(prefix) }
            .flatMap { line ->
                line.trimStart().removePrefix(prefix).split(',')
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
}

class ToolExposurePlanner(
    private val registry: ToolRegistry,
    private val availabilityResolver: ToolAvailabilityResolver? = null,
    private val skillRegistry: SkillRegistry? = null,
) {
    /** Default children receive public/read-only reads, never discovery or user-data-wide searches. */
    fun delegatedDefinitions(): List<ToolDefinition> = registry.descriptors().filter {
        it.security.risk == ToolRiskClass.READ_ONLY && it.security.confirmation == ConfirmationPolicy.NONE &&
            availabilityResolver?.isAvailable(it) != false &&
            (it.name in setOf("halo_fetch_url", "halo_read_payload") || it.name.startsWith("mcp."))
    }.take(8).map { it.definition }

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

    /**
     * Bootstrap exposure for a constrained local model: only the local-safe group,
     * never the whole registry.
     */
    fun localBootstrapExposure(
        policy: LocalModelToolPolicy,
        protocol: ToolCallProtocol,
    ): ToolExposureSet {
        val allowed = LocalModelToolPolicy.forProtocol(protocol)
        val bootstrap = registry.descriptors()
            .filter { descriptor -> allowed.allows(descriptor.name) }
            .filter { availabilityResolver?.isAvailable(it) != false }
            .map { it.id }
            .toSet()
        return ToolExposureSet(bootstrap = bootstrap)
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

    /**
     * Expand discovery: new tools plus the dependencies they need, re-checked
     * against availability and the exposure gate. The ToolRegistry is never mutated.
     */
    fun expand(
        current: ToolExposureSet,
        requestedIds: List<String>,
        gate: ToolExposureGate? = null,
        exposureContext: ToolExposureContext = ToolExposureContext(),
        localPolicy: LocalModelToolPolicy? = null,
    ): ToolExposureSet {
        val newDiscovered = mutableSetOf<ToolId>()
        val queue = ArrayDeque<String>()
        requestedIds.forEach { queue.addLast(it) }
        var processed = 0

        while (queue.isNotEmpty() && processed < MAX_EXPANSION_CANDIDATES) {
            processed++
            val raw = queue.removeFirst().trim()
            if (raw.isEmpty() || raw == ToolDiscoveryService.DISCOVER_TOOL) continue
            val descriptor = registry.descriptor(raw) ?: continue
            if (descriptor.id in current.effective) continue
            if (descriptor.visibility != ToolVisibility.DISCOVERABLE) continue
            if (availabilityResolver?.isAvailable(descriptor) == false) continue
            if (localPolicy != null && !localPolicy.mayExpose(descriptor.name)) continue
            if (gate != null && gate.evaluate(descriptor, exposureContext) is ToolExposureDecision.Deny) continue
            newDiscovered.add(descriptor.id)
            descriptor.dependencies.forEach { queue.addLast(it.value) }
        }

        return current.copy(discovered = current.discovered + newDiscovered)
    }

    /**
     * Expand from the tool results of the previous round. Only ids a tool result
     * explicitly declared with [ToolExposureDirective.PREFIX] are considered, so a
     * model cannot widen its own exposure by asking for it in prose.
     */
    fun expandFromToolOutputs(
        current: ToolExposureSet,
        toolOutputs: List<String>,
        gate: ToolExposureGate? = null,
        exposureContext: ToolExposureContext = ToolExposureContext(),
        localPolicy: LocalModelToolPolicy? = null,
    ): ToolExposureSet {
        val declared = toolOutputs.flatMap { ToolExposureDirective.parse(it) }
        if (declared.isEmpty()) return current
        return expand(current, declared, gate, exposureContext, localPolicy)
    }

    /**
     * Skill activation: a successfully loaded skill makes its own tools exposable,
     * which is exactly what the gate's `activatedSkillIds` check models.
     */
    fun activateSkill(
        current: ToolExposureSet,
        skillId: SkillId,
        gate: ToolExposureGate? = null,
        exposureContext: ToolExposureContext = ToolExposureContext(),
        localPolicy: LocalModelToolPolicy? = null,
    ): ToolExposureSet {
        val skill = skillRegistry?.get(skillId) ?: return current
        val activated = exposureContext.activatedSkillIds + skillId
        return expand(
            current = current,
            requestedIds = skill.tools.toList(),
            gate = gate,
            exposureContext = exposureContext.copy(activatedSkillIds = activated),
            localPolicy = localPolicy,
        )
    }

    fun effectiveDefinitions(
        exposure: ToolExposureSet,
        discoverySummary: String = "",
        localPolicy: LocalModelToolPolicy? = null,
    ): List<ToolDefinition> {
        val definitions = mutableListOf<ToolDefinition>()

        val includeDiscovery = localPolicy?.allows(ToolDiscoveryService.DISCOVER_TOOL) != false
        if (includeDiscovery) {
            definitions.add(ToolDiscoveryService.discoveryToolDefinition(discoverySummary))
        }

        val budget = localPolicy?.maxExposedSchemas ?: Int.MAX_VALUE
        for (toolId in exposure.effective) {
            if (definitions.size >= budget) break
            if (toolId.value == ToolDiscoveryService.DISCOVER_TOOL) continue
            // mayExpose, not allows: membership in the planned exposure set is what
            // distinguishes an activated tool (a loaded skill's, or a discovered one)
            // from one Halo never planned to show.
            if (localPolicy != null && !localPolicy.mayExpose(toolId.value)) continue
            val descriptor = registry.descriptor(toolId.value) ?: continue
            if (availabilityResolver?.isAvailable(descriptor) == false) continue
            definitions.add(descriptor.definition)
        }
        return definitions
    }

    private companion object {
        /** Bounds dependency chasing so one expansion can never enumerate the registry. */
        const val MAX_EXPANSION_CANDIDATES = 64
    }
}
