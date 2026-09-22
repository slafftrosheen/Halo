package com.halo.runtime.cognition.skills

import com.halo.sdk.runtime.skills.HostPermission
import com.halo.sdk.runtime.skills.SkillAvailability
import com.halo.sdk.runtime.skills.SkillDescriptor

class SkillAvailabilityResolver(
    private val permissionGranted: (HostPermission) -> Boolean = { true },
    private val registeredToolNames: (() -> Set<String>)? = null
) {
    fun resolve(skill: SkillDescriptor): SkillAvailability {
        if (!skill.enabled) return SkillAvailability.Disabled
        skill.requiredPermissions.firstOrNull { !permissionGranted(it) }?.let {
            return SkillAvailability.MissingPermission(it)
        }
        registeredToolNames?.invoke()?.let { registered ->
            skill.tools.firstOrNull { it !in registered }?.let {
                return SkillAvailability.MissingDependency(it)
            }
        }
        return SkillAvailability.Available
    }
}
