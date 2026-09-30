package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.device.CapabilityAffinity
import com.halo.runtime.cognition.device.CapabilityRequirement
import com.halo.runtime.cognition.device.CapabilityResolution
import com.halo.runtime.cognition.device.DeviceCapabilityResolver
import com.halo.runtime.cognition.device.ResourcePlan
import com.halo.runtime.cognition.device.ResourcePlanner
import com.halo.runtime.cognition.device.RuntimeDevice
import com.halo.runtime.cognition.device.RuntimeDeviceSource
import com.halo.sdk.runtime.resource.ResourceAcquireResult
import com.halo.sdk.runtime.resource.ResourceClaim
import com.halo.sdk.runtime.resource.ResourceEndpointId
import com.halo.sdk.runtime.resource.ResourceHolder
import com.halo.sdk.runtime.resource.ResourceKey
import com.halo.sdk.runtime.resource.ResourceLease
import com.halo.sdk.runtime.resource.ResourceLeaseId
import com.halo.sdk.runtime.resource.ResourceLeaseManager
import com.halo.sdk.runtime.resource.ResourceLeaseRequest
import com.halo.sdk.runtime.resource.ResourcePreemptionPolicy
import com.halo.sdk.runtime.resource.ResourcePriority
import com.halo.sdk.runtime.resource.ResourceWaitPolicy
import com.halo.sdk.runtime.resource.ResourceYieldHandler
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

/**
 * Joins endpoint-bearing device capability selection to generic Runtime resource
 * ownership. Host/workspace/provider capability availability is handled by
 * ToolAvailabilityResolver and never faked as a connected RuntimeDevice.
 */
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
        ownerYieldHandler: ResourceYieldHandler? = null,
        priority: ResourcePriority = ResourcePriority.INTERACTIVE,
    ): ToolResourceAcquisition {
        val devicesToUse = availableDevices.ifEmpty { runtimeDeviceSource?.devices?.value.orEmpty() }

        // Only device.* capability requirements need a connected endpoint.
        // host.*, workspace.*, runtime.* and provider.* are semantic admission
        // requirements already checked by ToolAvailabilityResolver.
        val deviceRequirements = descriptor.requirements
            .filterIsInstance<ToolRequirement.Capability>()
            .filter { it.id.value.startsWith("device.") }
            .map { requirement ->
                CapabilityRequirement(
                    capabilityKind = requirement.id.value,
                    affinity = CapabilityAffinity.ANY,
                    allowFallback = true,
                )
            }

        val resolutions = deviceRequirements.map { requirement ->
            capabilityResolver.resolve(requirement, devicesToUse)
        }

        val missing = resolutions.filter { it.selectedDevice == null }
        if (missing.isNotEmpty()) {
            return ToolResourceAcquisition.CapabilityMissing(missing.map { it.requirement })
        }

        val basePlan = resourcePlanner.plan(resolutions)

        // Static resource declarations default to the selected device endpoint
        // when one exists, otherwise the primary host. Dynamic capability-bound
        // resources already carry their exact endpoint through ResourcePlanner.
        val selectedEndpoint = basePlan.endpointBindings.values.firstOrNull()
        val explicitClaims = descriptor.resources.map { requirement ->
            val endpoint = when (requirement.affinity) {
                CapabilityAffinity.HOST -> ResourceEndpointId("host:primary")
                CapabilityAffinity.ORIGIN,
                CapabilityAffinity.SPECIFIC_OWNER,
                CapabilityAffinity.ANY -> selectedEndpoint ?: ResourceEndpointId("host:primary")
            }
            ResourceClaim(
                key = ResourceKey(requirement.resourceId, endpoint),
                access = requirement.access,
            )
        }

        val allClaims = basePlan.claims + explicitClaims
        val resourcePlan = basePlan.copy(claims = allClaims)
        val leaseRequest = ResourceLeaseRequest(
            requestId = "tool:${holder.toolInvocationId ?: holder.id}",
            holder = holder,
            claims = allClaims,
            priority = priority,
            waitPolicy = waitPolicy,
            preemption = ResourcePreemptionPolicy.COOPERATIVE,
        )

        val plan = ToolResourcePlan(
            descriptor = descriptor,
            capabilityResolutions = resolutions,
            resourcePlan = resourcePlan,
            leaseRequest = leaseRequest,
        )

        if (allClaims.isEmpty()) {
            return ToolResourceAcquisition.Granted(NoOpResourceLease(leaseRequest), plan)
        }

        return when (val result = resourceLeaseManager.acquire(leaseRequest, ownerYieldHandler)) {
            is ResourceAcquireResult.Granted -> ToolResourceAcquisition.Granted(result.lease, plan)
            else -> ToolResourceAcquisition.Blocked(plan, result)
        }
    }

    private class NoOpResourceLease(
        override val request: ResourceLeaseRequest,
    ) : ResourceLease {
        override val leaseId: ResourceLeaseId = ResourceLeaseId("noop:${UUID.randomUUID()}")
        override val acquiredAtMs: Long = System.currentTimeMillis()
        override val isReleased: Boolean = true
        override fun release() = Unit
    }
}
