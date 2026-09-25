package com.halo.runtime.cognition.device

import com.halo.cognition.language.InteractionId

enum class HandoffReason {
    ORIGIN_DISCONNECTED,
    CAPABILITY_UNAVAILABLE,
    EXPLICIT_USER_REQUEST,
    PRIVACY_REQUIREMENT,
}

sealed interface HandoffDecision {
    data object Stay : HandoffDecision
    data class Unavailable(val reason: HandoffReason) : HandoffDecision
    data class Offer(val targetDeviceId: String) : HandoffDecision
    data class Required(val targetDeviceId: String, val reason: HandoffReason) : HandoffDecision
}

data class InteractionInputRoute(
    val deviceId: String?,
    val capabilityKind: String = "audio.input",
)

data class InteractionOutputRoute(
    val deviceId: String?,
    val capabilityKind: String = "audio.output",
)

interface InteractionRoutePolicy {
    fun resolveInput(interactionId: InteractionId, originDeviceId: String?): InteractionInputRoute
    fun resolveOutput(interactionId: InteractionId, originDeviceId: String?): InteractionOutputRoute
    fun evaluateHandoff(continuity: InteractionContinuity, availableDevices: List<RuntimeDevice>): HandoffDecision
}

class DefaultInteractionRoutePolicy(
    private val resolver: DeviceCapabilityResolver = DefaultDeviceCapabilityResolver(),
) : InteractionRoutePolicy {

    override fun resolveInput(interactionId: InteractionId, originDeviceId: String?): InteractionInputRoute {
        val req = CapabilityRequirement(
            capabilityKind = "audio.input",
            originDeviceId = originDeviceId,
            affinity = CapabilityAffinity.ORIGIN,
            allowFallback = true,
        )
        val res = resolver.resolve(req, emptyList())
        return InteractionInputRoute(res.selectedDevice?.id ?: originDeviceId, "audio.input")
    }

    override fun resolveOutput(interactionId: InteractionId, originDeviceId: String?): InteractionOutputRoute {
        val req = CapabilityRequirement(
            capabilityKind = "audio.output",
            originDeviceId = originDeviceId,
            affinity = CapabilityAffinity.ORIGIN,
            allowFallback = true,
        )
        val res = resolver.resolve(req, emptyList())
        return InteractionOutputRoute(res.selectedDevice?.id ?: originDeviceId, "audio.output")
    }

    override fun evaluateHandoff(
        continuity: InteractionContinuity,
        availableDevices: List<RuntimeDevice>,
    ): HandoffDecision {
        val currentDeviceId = continuity.originDeviceId
        val originReq = CapabilityRequirement(
            capabilityKind = "audio.output",
            originDeviceId = currentDeviceId,
            affinity = CapabilityAffinity.ORIGIN,
            allowFallback = false,
        )
        val originRes = resolver.resolve(originReq, availableDevices)

        if (originRes.selectedDevice?.connected == true) {
            return HandoffDecision.Stay
        }

        val reason = if (availableDevices.any { it.id == currentDeviceId && it.connected }) {
            HandoffReason.CAPABILITY_UNAVAILABLE
        } else {
            HandoffReason.ORIGIN_DISCONNECTED
        }

        if (!continuity.allowHandoff) return HandoffDecision.Unavailable(reason)

        val fallbackReq = CapabilityRequirement(
            capabilityKind = "audio.output",
            originDeviceId = currentDeviceId,
            affinity = CapabilityAffinity.ANY,
            allowFallback = true,
        )
        val fallbackRes = resolver.resolve(fallbackReq, availableDevices)
        val targetDevice = fallbackRes.selectedDevice ?: return HandoffDecision.Unavailable(reason)

        return HandoffDecision.Required(targetDevice.id, HandoffReason.EXPLICIT_USER_REQUEST)
    }
}
