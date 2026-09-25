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
) {

    suspend fun prepareAndAcquire(
        descriptor: ToolDescriptor,
        holder: ResourceHolder,
        availableDevices: List<RuntimeDevice>,
        waitPolicy: ResourceWaitPolicy = ResourceWaitPolicy.FailFast,
        yieldHandler: ResourceYieldHandler? = null,
    ): ToolResourceAcquisition {
        val capRequirements = descriptor.requirements
            .filterIsInstance<ToolRequirement.Capability>()
            .map { toolCap ->
                CapabilityRequirement(
                    capabilityKind = toolCap.id.value,
                    affinity = CapabilityAffinity.ANY,
                    allowFallback = true,
                )
            }

        val inferredKind = inferCapabilityKindForTool(descriptor.name)
        val allCapRequirements = if (inferredKind != null && capRequirements.none { it.capabilityKind == inferredKind }) {
            capRequirements + CapabilityRequirement(capabilityKind = inferredKind, affinity = CapabilityAffinity.ANY)
        } else {
            capRequirements
        }

        val resolutions = allCapRequirements.map { req ->
            capabilityResolver.resolve(req, availableDevices)
        }

        val missing = resolutions.filter { it.selectedDevice == null }
        if (missing.isNotEmpty()) {
            return ToolResourceAcquisition.CapabilityMissing(missing.map { it.requirement })
        }

        val resourcePlan = resourcePlanner.plan(resolutions)

        val leaseRequest = ResourceLeaseRequest(
            requestId = "tool:${holder.toolInvocationId ?: holder.id}",
            holder = holder,
            claims = resourcePlan.claims,
            priority = ResourcePriority.INTERACTIVE,
            waitPolicy = waitPolicy,
            preemption = ResourcePreemptionPolicy.COOPERATIVE,
        )

        val toolPlan = ToolResourcePlan(
            descriptor = descriptor,
            capabilityResolutions = resolutions,
            resourcePlan = resourcePlan,
            leaseRequest = leaseRequest,
        )

        if (resourcePlan.claims.isEmpty()) {
            val emptyLease = NoOpResourceLease(leaseRequest)
            return ToolResourceAcquisition.Granted(emptyLease, toolPlan)
        }

        return when (val acquireResult = resourceLeaseManager.acquire(leaseRequest, yieldHandler)) {
            is ResourceAcquireResult.Granted -> ToolResourceAcquisition.Granted(acquireResult.lease, toolPlan)
            else -> ToolResourceAcquisition.Blocked(toolPlan, acquireResult)
        }
    }

    private fun inferCapabilityKindForTool(toolName: String): String? {
        val lower = toolName.lowercase()
        return when {
            lower.contains("capture") || lower.contains("photo") || lower.contains("camera") -> "camera.sensor"
            lower.contains("audio_input") || lower.contains("microphone") -> "audio.input"
            lower.contains("speech_output") || lower.contains("tts") -> "audio.output"
            lower.contains("media_transfer") || lower.contains("sync_media") -> "media.transfer"
            else -> null
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
