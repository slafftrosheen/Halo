package com.halo.sdk.runtime

import java.util.UUID
import com.halo.sdk.host.ObservationSource

@JvmInline
value class ObservationId(val value: String)

enum class Modality {
    AUDIO, VIDEO, TEXT, SENSOR, NETWORK, SYSTEM, UNKNOWN
}

enum class Source {
    MICROPHONE, CAMERA, BLUETOOTH, GPS, OS, USER, SYSTEM, ROBOT
}

enum class Freshness {
    FRESH, STALE, UNKNOWN
}

/** Stable physical source identity. Kept separate from modality and legacy Source. */
data class ObservationOrigin(
    val source: ObservationSource,
    val hostId: String? = null
)

interface ObservationPayload

data class Observation(
    val id: ObservationId = ObservationId(UUID.randomUUID().toString()),
    val modality: Modality,
    val source: Source,
    val payload: ObservationPayload,
    val origin: ObservationOrigin? = null,
    val sourceDeviceId: String? = null,
    val freshness: Freshness = Freshness.UNKNOWN,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
)

data class ObservationCreated(
    override val id: String = UUID.randomUUID().toString(),
    val observation: Observation,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class SpeechObservationPayload(
    val transcript: String,
    val language: String
) : ObservationPayload

data class AudioObservationPayload(
    val energy: Float,
    val isSpeechActive: Boolean
) : ObservationPayload

data class RangeObservationPayload(
    val distanceMm: Int,
    val valid: Boolean
) : ObservationPayload

data class SafetyObservationPayload(
    val state: Int,
    val fault: Int,
    val estopped: Boolean,
    val faulted: Boolean
) : ObservationPayload

data class BatteryObservationPayload(
    val levelPercent: Int
) : ObservationPayload

data class MotionObservationPayload(
    val available: Boolean,
    val active: Boolean,
    val mode: Int
) : ObservationPayload

data class ImuObservationPayload(
    val available: Boolean,
    val valid: Boolean,
    val accelXG: Float,
    val accelYG: Float,
    val accelZG: Float
) : ObservationPayload
