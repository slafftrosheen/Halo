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
            val missing = skill.tools - registered
            if (missing.isNotEmpty()) {
                return if (missing.size == skill.tools.size) SkillAvailability.MissingDependency(missing.joinToString())
                else SkillAvailability.PartiallyAvailable(missing)
            }
        }
        return SkillAvailability.Available
    }
}
