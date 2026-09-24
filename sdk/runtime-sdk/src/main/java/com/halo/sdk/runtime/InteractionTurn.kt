package com.halo.sdk.runtime

sealed interface InteractionTurnState {
    val interactionId: String?

    data object Idle : InteractionTurnState { override val interactionId: String? = null }
    data class Listening(override val interactionId: String, val sourceDeviceId: String?) : InteractionTurnState
    data class Transcribing(override val interactionId: String) : InteractionTurnState
    data class Preparing(override val interactionId: String) : InteractionTurnState
    data class Generating(override val interactionId: String, val requestId: String) : InteractionTurnState
    data class Speaking(override val interactionId: String, val outputId: String) : InteractionTurnState
    data class Interrupted(override val interactionId: String, val byInteractionId: String?) : InteractionTurnState
    data class Completed(override val interactionId: String) : InteractionTurnState
    data class Failed(override val interactionId: String, val reason: String) : InteractionTurnState
}

data class BargeInEvidence(
    val physicalActivation: Boolean,
    val localSpeechEnergy: Boolean,
    val localVoicedSpeech: Boolean = false,
    val inputRouteValid: Boolean = false,
    val externalProviderInterrupted: Boolean,
    val timestampMs: Long,
)

enum class BargeInDecision { ACCEPT, STOP_EXTERNAL_ONLY, REJECT_ECHO }

object BargeInPolicy {
    fun decide(evidence: BargeInEvidence, outputActive: Boolean): BargeInDecision = when {
        evidence.physicalActivation -> BargeInDecision.ACCEPT
        outputActive && evidence.localVoicedSpeech && evidence.inputRouteValid -> BargeInDecision.ACCEPT
        evidence.externalProviderInterrupted -> BargeInDecision.STOP_EXTERNAL_ONLY
        else -> BargeInDecision.REJECT_ECHO
    }
}

data class InteractionLatencyTrace(
    val interactionId: String,
    val startedAtMs: Long,
    val captureReadyAtMs: Long? = null,
    val captureReceivedAtMs: Long? = null,
    val speechEndedAtMs: Long? = null,
    val transcriptReadyAtMs: Long? = null,
    val modelReadyAtMs: Long? = null,
    val firstTokenAtMs: Long? = null,
    val generationCompleteAtMs: Long? = null,
    val routeReadyAtMs: Long? = null,
    val playbackStartedAtMs: Long? = null,
) {
    val timeToFirstTokenMs: Long? get() = firstTokenAtMs?.minus(startedAtMs)
    val timeToAudibleMs: Long? get() = playbackStartedAtMs?.minus(startedAtMs)
}

data class ObservationFreshnessPolicy(
    val maxAgeMs: Long,
    val newerObservationSupersedes: Boolean,
) {
    fun isFresh(receivedAtMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - receivedAtMs <= maxAgeMs
}

data class WearableImageObservation(
    val id: String,
    val sourceDeviceId: String,
    val captureRequestedAtMs: Long,
    val deviceReadyAtMs: Long?,
    val exposureAtMs: Long?,
    val receivedAtMs: Long,
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
    val frameSequence: Long,
    val interactionId: String?,
) {
    override fun equals(other: Any?): Boolean = other is WearableImageObservation &&
        id == other.id && sourceDeviceId == other.sourceDeviceId &&
        captureRequestedAtMs == other.captureRequestedAtMs && deviceReadyAtMs == other.deviceReadyAtMs &&
        exposureAtMs == other.exposureAtMs && receivedAtMs == other.receivedAtMs &&
        bytes.contentEquals(other.bytes) && mimeType == other.mimeType && width == other.width &&
        height == other.height && frameSequence == other.frameSequence && interactionId == other.interactionId

    override fun hashCode(): Int = 31 * id.hashCode() + bytes.contentHashCode()
}

data class InteractionTurnChanged(
    override val id: String = java.util.UUID.randomUUID().toString(),
    val state: InteractionTurnState,
    override val timestamp: Long = System.currentTimeMillis(),
) : HaloEvent

data class InteractionLatencyUpdated(
    override val id: String = java.util.UUID.randomUUID().toString(),
    val trace: InteractionLatencyTrace,
    override val timestamp: Long = System.currentTimeMillis(),
) : HaloEvent
