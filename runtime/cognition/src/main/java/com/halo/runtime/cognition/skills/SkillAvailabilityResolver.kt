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
    fun resolve(skill: SkillDescriptor): SkillAvailability =
        resolve(skill, permissionGranted, registeredToolNames, requirementSatisfied)

    /** One catalogue operation observes one permission/requirement answer and registry view. */
    fun resolveAll(skills: List<SkillDescriptor>, registeredNames: Set<String>): List<SkillDescriptor> {
        val permissions = mutableMapOf<HostPermission, Boolean>()
        val requirements = mutableMapOf<SkillRequirement, Boolean?>()
        val scopedPermissions: (HostPermission) -> Boolean = { permission ->
            permissions.getOrPut(permission) { permissionGranted(permission) }
        }
        // A missing host callback means registration requirements were not host-evaluated.
        // Preserve that contract; the supplied names replace an existing callback only.
        val scopedNames: (() -> Set<String>)? = registeredToolNames?.let { { registeredNames } }
        val scopedRequirements: ((SkillRequirement) -> Boolean?)? = requirementSatisfied?.let { check ->
            { requirement ->
                if (!requirements.containsKey(requirement)) requirements[requirement] = check(requirement)
                requirements[requirement]
            }
        }
        return skills.map { skill ->
            skill.copy(availability = resolve(skill, scopedPermissions, scopedNames, scopedRequirements))
        }
    }

    private fun resolve(skill: SkillDescriptor, permissionGranted: (HostPermission) -> Boolean,
        registeredToolNames: (() -> Set<String>)?, requirementSatisfied: ((SkillRequirement) -> Boolean?)?): SkillAvailability {
        if (!skill.enabled) return SkillAvailability.Disabled
        val perToolPermissions = skill.toolDefinitions.flatMap { it.requiredPermissions }.toSet()
        val missingPermissionTools = skill.toolDefinitions.filter { tool -> tool.requiredPermissions.any { !permissionGranted(it) } }.map { it.toolId }.toSet()
        (skill.requiredPermissions - perToolPermissions).firstOrNull { !permissionGranted(it) }?.let {
            return SkillAvailability.MissingPermission(it)
        }
        registeredToolNames?.invoke()?.let { registered ->
            val explicitlyRequired = skill.requirements
                .filterIsInstance<SkillRequirement.ToolRegistered>()
                .map { it.toolId }
                .toSet()
            val allRequiredTools = skill.tools + explicitlyRequired
            val missing = (allRequiredTools - registered) + missingPermissionTools
            if (missing.isNotEmpty()) {
                return if (missing.size == allRequiredTools.size) SkillAvailability.MissingDependency(missing.joinToString())
                else SkillAvailability.PartiallyAvailable(missing)
            }
        }
        if (missingPermissionTools.isNotEmpty()) return SkillAvailability.PartiallyAvailable(missingPermissionTools)
        skill.requirements.firstOrNull { requirement ->
            requirement !is SkillRequirement.ToolRegistered &&
                requirement !is SkillRequirement.Permission &&
                requirementSatisfied?.invoke(requirement) == false
        }?.let { return SkillAvailability.MissingDependency(it.toString()) }
        return SkillAvailability.Available
    }
}
