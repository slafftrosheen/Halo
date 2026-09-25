package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.skills.SkillRegistry

sealed interface ToolAvailability {
    data object Available : ToolAvailability
    data class Disabled(val reason: String) : ToolAvailability
    data class MissingCapabilities(val missing: Set<CapabilityId>) : ToolAvailability
    data class Degraded(val reason: String) : ToolAvailability
}

class ToolAvailabilityResolver(
    private val registry: ToolRegistry,
    private val skillRegistry: SkillRegistry? = null,
    private val catalogueSource: CapabilityCatalogueSource? = null,
) {

    fun isAvailable(descriptor: ToolDescriptor): Boolean {
        return checkAvailability(descriptor) is ToolAvailability.Available
    }

    fun checkAvailability(descriptor: ToolDescriptor): ToolAvailability {
        // 1. Skill policy check
        if (skillRegistry != null) {
            val skillPolicy = skillRegistry.approvalPolicyForTool(descriptor.name)
            if (skillPolicy == ToolApprovalPolicy.DENY) {
                return ToolAvailability.Disabled("Disabled by skill policy")
            }
        }

        // 2. Required capability check
        val requiredCaps = descriptor.requirements.filterIsInstance<ToolRequirement.Capability>()
        if (requiredCaps.isNotEmpty() && catalogueSource != null) {
            val snapshot = catalogueSource.snapshot.value
            val availableCapIds = snapshot.capabilities
                .filter { it.availability == CapabilityAvailability.AVAILABLE }
                .map { it.id }
                .toSet()

            val missing = requiredCaps.map { it.id }.filterNot { it in availableCapIds }.toSet()
            if (missing.isNotEmpty()) {
                return ToolAvailability.MissingCapabilities(missing)
            }
        }

        return ToolAvailability.Available
    }
}
