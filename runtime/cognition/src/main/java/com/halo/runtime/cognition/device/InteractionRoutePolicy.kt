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

        if (currentDevice?.connected == true) {
            return HandoffDecision.Stay
        }

        // Simplistic fallback for testability/stubbing: Require handoff to local phone if origin is lost
        return HandoffDecision.Required("local_phone", HandoffReason.ORIGIN_DISCONNECTED)
    }
}
