package com.halo.runtime.cognition.device

import com.halo.sdk.runtime.resource.*

data class ResourcePlan(
    val claims: Set<ResourceClaim>,
    val endpointBindings: Map<String, ResourceEndpointId>,
)

interface ResourcePlanner {
    fun plan(
        resolutions: List<CapabilityResolution>,
        defaultAccess: ResourceAccess = ResourceAccess.EXCLUSIVE,
    ): ResourcePlan
}

class DefaultResourcePlanner : ResourcePlanner {
    override fun plan(
        resolutions: List<CapabilityResolution>,
        defaultAccess: ResourceAccess,
    ): ResourcePlan {
        val claims = mutableSetOf<ResourceClaim>()
        val endpointBindings = mutableMapOf<String, ResourceEndpointId>()

        for (resolution in resolutions) {
            val device = resolution.selectedDevice ?: continue
            val endpointId = ResourceEndpointId(device.id)
            val capKind = resolution.requirement.capabilityKind

            endpointBindings[capKind] = endpointId

            val resourceIds = CapabilityResourceBinding.resourcesForCapability(capKind)
            for (resourceId in resourceIds) {
                val access = if (resourceId == HaloResourceIds.DisplayOutput) ResourceAccess.SHARED else defaultAccess
                claims.add(
                    ResourceClaim(
                        key = ResourceKey(resourceId = resourceId, endpointId = endpointId),
                        access = access,
                    )
                )
            }
        }

        return ResourcePlan(claims = claims, endpointBindings = endpointBindings)
    }
}
