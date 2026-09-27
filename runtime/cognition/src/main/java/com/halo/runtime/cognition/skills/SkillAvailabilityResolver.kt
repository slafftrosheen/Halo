package com.halo.runtime.cognition.skills

import com.halo.sdk.runtime.skills.HostPermission
import com.halo.sdk.runtime.skills.SkillAvailability
import com.halo.sdk.runtime.skills.SkillDescriptor
import com.halo.sdk.runtime.skills.SkillRequirement

class SkillAvailabilityResolver(
    private val permissionGranted: (HostPermission) -> Boolean = { true },
    private val registeredToolNames: (() -> Set<String>)? = null,
    /** Null means the host cannot currently evaluate this requirement. */
    private val requirementSatisfied: ((SkillRequirement) -> Boolean?)? = null,
) {
    fun resolve(skill: SkillDescriptor): SkillAvailability {
        if (!skill.enabled) return SkillAvailability.Disabled
        skill.requiredPermissions.firstOrNull { !permissionGranted(it) }?.let {
            return SkillAvailability.MissingPermission(it)
        }
        registeredToolNames?.invoke()?.let { registered ->
            val explicitlyRequired = skill.requirements
                .filterIsInstance<SkillRequirement.ToolRegistered>()
                .map { it.toolId }
                .toSet()
            val allRequiredTools = skill.tools + explicitlyRequired
            val missing = allRequiredTools - registered
            if (missing.isNotEmpty()) {
                return if (missing.size == allRequiredTools.size) SkillAvailability.MissingDependency(missing.joinToString())
                else SkillAvailability.PartiallyAvailable(missing)
            }
        }
        skill.requirements.firstOrNull { requirement ->
            requirement !is SkillRequirement.ToolRegistered &&
                requirement !is SkillRequirement.Permission &&
                requirementSatisfied?.invoke(requirement) == false
        }?.let { return SkillAvailability.MissingDependency(it.toString()) }
        return SkillAvailability.Available
    }
}
