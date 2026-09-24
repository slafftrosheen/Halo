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

data class InteractionInputRoute(val deviceId: String?, val capabilityKind: String)
data class InteractionOutputRoute(val deviceId: String?, val capabilityKind: String)

interface InteractionRoutePolicy {
    fun resolveInput(interactionId: InteractionId, originDeviceId: String?): InteractionInputRoute
    fun resolveOutput(interactionId: InteractionId, originDeviceId: String?): InteractionOutputRoute
    fun evaluateHandoff(continuity: InteractionContinuity, availableDevices: List<RuntimeDevice>): HandoffDecision
}

class DefaultInteractionRoutePolicy : InteractionRoutePolicy {
    override fun resolveInput(interactionId: InteractionId, originDeviceId: String?): InteractionInputRoute {
        return InteractionInputRoute(originDeviceId, "AudioInput")
    }

    override fun resolveOutput(interactionId: InteractionId, originDeviceId: String?): InteractionOutputRoute {
        return InteractionOutputRoute(originDeviceId, "AudioOutput")
    }

    override fun evaluateHandoff(continuity: InteractionContinuity, availableDevices: List<RuntimeDevice>): HandoffDecision {
        val currentDevice = continuity.originDeviceId
            ?.let { id -> availableDevices.firstOrNull { it.id == id } }

        if (currentDevice?.connected == true && currentDevice.capabilities.any { it is DeviceCapability.AudioOutput }) {
            return HandoffDecision.Stay
        }

        val reason = if (currentDevice?.connected == true) HandoffReason.CAPABILITY_UNAVAILABLE else HandoffReason.ORIGIN_DISCONNECTED
        if (!continuity.allowHandoff) return HandoffDecision.Unavailable(reason)
        val target = availableDevices.firstOrNull { it.connected && it.capabilities.any { cap -> cap is DeviceCapability.AudioOutput } }
            ?: return HandoffDecision.Unavailable(reason)
        return HandoffDecision.Required(target.id, HandoffReason.EXPLICIT_USER_REQUEST)
    }
}
