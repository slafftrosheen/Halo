package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.device.*
import com.halo.sdk.runtime.resource.*
import java.util.UUID

data class ToolResourcePlan(
    val descriptor: ToolDescriptor,
    val capabilityResolutions: List<CapabilityResolution>,
    val resourcePlan: ResourcePlan,
    val leaseRequest: ResourceLeaseRequest,
)

sealed interface ToolResourceAcquisition {
    data class Granted(
        val lease: ResourceLease,
        val plan: ToolResourcePlan,
    ) : ToolResourceAcquisition

    data class Blocked(
        val plan: ToolResourcePlan,
        val acquireResult: ResourceAcquireResult,
    ) : ToolResourceAcquisition

    data class CapabilityMissing(
        val missingRequirements: List<CapabilityRequirement>,
    ) : ToolResourceAcquisition
}

class ToolResourceCoordinator(
    private val capabilityResolver: DeviceCapabilityResolver,
    private val resourcePlanner: ResourcePlanner,
    private val resourceLeaseManager: ResourceLeaseManager,
    private val runtimeDeviceSource: RuntimeDeviceSource? = null,
) {

    suspend fun prepareAndAcquire(
        descriptor: ToolDescriptor,
        holder: ResourceHolder,
        availableDevices: List<RuntimeDevice> = emptyList(),
        waitPolicy: ResourceWaitPolicy = ResourceWaitPolicy.FailFast,
        yieldHandler: ResourceYieldHandler? = null,
    ): ToolResourceAcquisition {
        val devicesToUse = availableDevices.ifEmpty { runtimeDeviceSource?.devices?.value.orEmpty() }

        // 1. Device capability requirements from descriptor
        val deviceCapRequirements = descriptor.requirements
            .filterIsInstance<ToolRequirement.Capability>()
            .map { toolCap ->
                CapabilityRequirement(
                    capabilityKind = toolCap.id.value,
                    affinity = CapabilityAffinity.ANY,
                    allowFallback = true,
                )
            }

        // 2. Resolve endpoint device capabilities
        val resolutions = deviceCapRequirements.map { req ->
            capabilityResolver.resolve(req, devicesToUse)
        }

        val missing = resolutions.filter { it.selectedDevice == null && !it.requirement.capabilityKind.startsWith("host.") && !it.requirement.capabilityKind.startsWith("workspace.") }
        if (missing.isNotEmpty()) {
            return ToolResourceAcquisition.CapabilityMissing(missing.map { it.requirement })
        }

        // 3. Plan resources: combine explicit descriptor resource requirements + device cap resources
        val basePlan = resourcePlanner.plan(resolutions)
        val explicitClaims = descriptor.resources.map { req ->
            ResourceClaim(
                key = ResourceKey(req.resourceId, ResourceEndpointId("host:primary")),
                access = req.access,
            )
        }
        val allClaims = basePlan.claims + explicitClaims

        val leaseRequest = ResourceLeaseRequest(
            requestId = "tool:${holder.toolInvocationId ?: holder.id}",
            holder = holder,
            claims = allClaims,
            priority = ResourcePriority.INTERACTIVE,
            waitPolicy = waitPolicy,
            preemption = ResourcePreemptionPolicy.COOPERATIVE,
        )

        val toolPlan = ToolResourcePlan(
            descriptor = descriptor,
            capabilityResolutions = resolutions,
            resourcePlan = basePlan.copy(claims = allClaims),
            leaseRequest = leaseRequest,
        )

        if (allClaims.isEmpty()) {
            val emptyLease = NoOpResourceLease(leaseRequest)
            return ToolResourceAcquisition.Granted(emptyLease, toolPlan)
        }

        return when (val acquireResult = resourceLeaseManager.acquire(leaseRequest, yieldHandler)) {
            is ResourceAcquireResult.Granted -> ToolResourceAcquisition.Granted(acquireResult.lease, toolPlan)
            else -> ToolResourceAcquisition.Blocked(toolPlan, acquireResult)
        }
    }

    private class NoOpResourceLease(
        override val request: ResourceLeaseRequest
    ) : ResourceLease {
        override val leaseId: ResourceLeaseId = ResourceLeaseId("noop:${UUID.randomUUID()}")
        override val acquiredAtMs: Long = System.currentTimeMillis()
        override val isReleased: Boolean = true
        override suspend fun release() {}
    }
}
